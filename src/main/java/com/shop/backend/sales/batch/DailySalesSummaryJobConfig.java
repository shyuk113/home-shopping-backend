package com.shop.backend.sales.batch;

import com.shop.backend.global.batch.JobResultLoggingListener;
import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DailySalesSummaryJobConfig {

    public static final String JOB_NAME= "dailySalesSummaryJob";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final JdbcTemplate jdbcTemplate;

    @Bean
    public Job dailySalesSummaryJob(Step deleteDailySalesSummaryStep, JobResultLoggingListener jobResultLoggingListener){
        return new JobBuilder(JOB_NAME, jobRepository)
                .validator(new SalesPeriodParametersValidator())
                .listener(jobResultLoggingListener)
                .start(deleteDailySalesSummaryStep)
                .build();
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
