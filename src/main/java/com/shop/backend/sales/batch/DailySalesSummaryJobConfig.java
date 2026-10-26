package com.shop.backend.sales.batch;

import com.shop.backend.global.batch.JobResultLoggingListener;
import com.shop.backend.order.domain.OrderStatus;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.batch.infrastructure.item.database.JdbcCursorItemReader;
import org.springframework.batch.infrastructure.item.database.JpaPagingItemReader;
import org.springframework.batch.infrastructure.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.batch.infrastructure.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * orders → daily_sales_summary 일별 매출 집계 Job.
 * 실행 파라미터: from, to (LocalDate, 양 끝 포함), chunkSize (Long, 선택, 기본 1000)
 * */

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DailySalesSummaryJobConfig {

    public static final String JOB_NAME= "dailySalesSummaryJob";
    private static final int FETCH_SIZE = 1_000;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    private final EntityManagerFactory entityManagerFactory;

    // 주문 × 판매자 단위로 읽는다. ORDER BY는 재시작 시 커서 위치를 재현하기 위해 반드시 필요하다.
    private static final String SALES_ROW_SQL= """
            SELECT o.id AS order_id,
            i.seller_id AS seller_id,
            CAST(o.order_time AS date) AS sales_date,
            o.status AS status,
            SUM(oi.quantity) AS quantity,
            SUM(oi.order_price * oi.quantity) AS amount
            FROM orders o
            JOIN order_item oi ON oi.order_id = o.id
            JOIN item i ON i.id = oi.item_id
            WHERE o.order_time >= ? AND o.order_time < ?
            AND o.status IN ('PAID', 'CANCEL')
            GROUP BY o.id, i.seller_id, o.order_time, o.status
            ORDER BY CAST(o.order_time AS date), i.seller_id, o.id
            """;
    /*
    ORDER BY CAST(o.order_time AS date), i.seller_id, o.id
    커서 Reader는 재시작할 때 쿼리를 다시 실행하고 이미 처리한 개수만큼 건너뜀
    순서가 매번 같지 않으면 엉뚱한 행을 건너뛰어 집계가 달라짐
     */

    // SALES_ROW_SQL과 같은 결과를 JPQL로 표현. JpaPagingItemReader가 페이지마다 OFFSET/LIMIT을 붙여 다시 실행한다.
    private static final String SALES_ROW_JPQL = """
            SELECT new com.shop.backend.sales.batch.SalesRow(
                o.id, i.seller.id, cast(o.orderTime as LocalDate), o.status,
                sum(oi.quantity), sum(oi.orderPrice * oi.quantity))
            FROM OrderItem oi
            JOIN oi.order o
            JOIN oi.item i
            WHERE o.orderTime >= :from AND o.orderTime < :to
              AND o.status IN :statuses
            GROUP BY o.id, i.seller.id, o.orderTime, o.status
            ORDER BY cast(o.orderTime as LocalDate), i.seller.id, o.id
            """;


    @Bean
    public Job dailySalesSummaryJob(Step deleteDailySalesSummaryStep, Step aggregateDailySalesStep,JobResultLoggingListener jobResultLoggingListener){
        return new JobBuilder(JOB_NAME, jobRepository)
                .validator(new SalesPeriodParametersValidator())
                .listener(jobResultLoggingListener)
                .start(deleteDailySalesSummaryStep)
                .next(aggregateDailySalesStep)
                .build();
    }

    /**
     * chunkSize, reader를 Job 파라미터로 받기 위해 @JobScope로 Job 실행 시점에 Step을 만든다. (비교 실험용)
     * 세 Reader 빈은 @StepScope 프록시라 주입만으로는 생성되지 않고, 선택된 Reader만 Step 실행 시 만들어진다.
     */
    @Bean
    @JobScope
    public Step aggregateDailySalesStep(
            @Value("#{jobParameters['chunkSize'] ?: 5000}") Long chunkSize,
            @Value("#{jobParameters['reader'] ?: 'cursor'}") String readerType,
            JdbcCursorItemReader<SalesRow> salesRowCursorReader,
            JpaPagingItemReader<SalesRow> salesRowJpaPagingReader,
            NoOffsetSalesRowReader salesRowNoOffsetReader) {
        ItemReader<SalesRow> reader = switch (readerType) {
            case "cursor" -> salesRowCursorReader;
            case "jpa" -> salesRowJpaPagingReader;
            case "nooffset" -> salesRowNoOffsetReader;
            default -> throw new IllegalArgumentException("reader는 cursor | jpa | nooffset 중 하나여야 합니다: " + readerType);
        };
        log.info("[batch] aggregateDailySalesStep reader={} chunkSize={}", readerType, chunkSize);

        return new StepBuilder("aggregateDailySalesStep", jobRepository)
                .<SalesRow, SalesDelta>chunk(chunkSize.intValue())
                .transactionManager(transactionManager)
                .reader(reader)
                .processor(salesDeltaProcessor())
                .writer(new DailySalesSummaryWriter(jdbcTemplate))
                .build();
    }

    @Bean
    @StepScope
    /*
    반환 타입을 ItemReader로 쓰면 프록시가 ItemStream(open/close/진행 위치 저장)을 모름
    그러면 커서가 열리지 않거나 재시작 위치가 저장되지 않음
     */
    public JdbcCursorItemReader<SalesRow> salesRowCursorReader(
            @Value("#{jobParameters['from']}") LocalDate from,
            @Value("#{jobParameters['to']}") LocalDate to){
        return new JdbcCursorItemReaderBuilder<SalesRow>()
                .name("salesRowCursorReader")
                .dataSource(dataSource)
                .sql(SALES_ROW_SQL)
                .queryArguments(from.atStartOfDay(),
                        to.plusDays(1).atStartOfDay())
                .connectionAutoCommit(false)
                .fetchSize(FETCH_SIZE)  //PostgreSQL JDBC 드라이버는 기본 설정에서 쿼리 결과 전체를 한 번에 메모리로 가져오기 때문에 나누어 받도록
                .rowMapper((rs, rowNum)-> new SalesRow(
                        rs.getLong("order_id"),
                        rs.getLong("seller_id"),
                        rs.getObject("sales_date", LocalDate.class),
                        OrderStatus.valueOf(rs.getString("status")),
                        rs.getInt("quantity"),
                        rs.getLong("amount")))
                .build();
    }

    /**
     * OFFSET 페이징 Reader. 페이지마다 JPQL을 다시 실행하고 setFirstResult(OFFSET)로 앞 페이지를 건너뛴다.
     * 페이지 크기는 chunk 크기와 맞춘다. (다르면 chunk 하나에 쿼리가 여러 번 나가거나 남는 행이 생긴다)
     */
    @Bean
    @StepScope
    public JpaPagingItemReader<SalesRow> salesRowJpaPagingReader(
            @Value("#{jobParameters['from']}") LocalDate from,
            @Value("#{jobParameters['to']}") LocalDate to,
            @Value("#{jobParameters['chunkSize'] ?: 5000}") Long chunkSize) {
        return new JpaPagingItemReaderBuilder<SalesRow>()
                .name("salesRowJpaPagingReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString(SALES_ROW_JPQL)
                .parameterValues(Map.of(
                        "from", from.atStartOfDay(),
                        "to", to.plusDays(1).atStartOfDay(),
                        "statuses", List.of(OrderStatus.PAID, OrderStatus.CANCEL)))
                .pageSize(chunkSize.intValue())
                .build();
    }

    @Bean
    @StepScope
    public NoOffsetSalesRowReader salesRowNoOffsetReader(
            @Value("#{jobParameters['from']}") LocalDate from,
            @Value("#{jobParameters['to']}") LocalDate to,
            @Value("#{jobParameters['chunkSize'] ?: 5000}") Long chunkSize) {
        return new NoOffsetSalesRowReader(jdbcTemplate, from, to, chunkSize.intValue());
    }

    @Bean
    public ItemProcessor<SalesRow, SalesDelta> salesDeltaProcessor(){
        return SalesDelta::from;
    }


    @Bean
    public Step deleteDailySalesSummaryStep(Tasklet deleteDailySalesSummaryTasklet){
        return new StepBuilder("deleteDailySalesSummaryStep",
                jobRepository)
                .tasklet(deleteDailySalesSummaryTasklet, transactionManager)
                .build();
    }

    @Bean
    @StepScope
    public Tasklet deleteDailySalesSummaryTasklet(@Value("#{jobParameters['from']}")LocalDate from,
                                                  @Value("#{jobParameters['to']}") LocalDate to){

        return ((contribution, chunkContext) -> {
            int deleted = jdbcTemplate.update(
                    "DELETE FROM daily_sales_summary WHERE sales_date BETWEEN ? AND ?", from , to
            );
            log.info("[batch] daily_sales_summary {} ~ {} 기존 집계 {}건 삭제", from, to, deleted);
            return RepeatStatus.FINISHED;
        });
    }
}
