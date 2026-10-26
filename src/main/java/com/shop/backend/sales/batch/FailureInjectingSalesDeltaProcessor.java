package com.shop.backend.sales.batch;

import org.springframework.batch.infrastructure.item.ItemProcessor;

import java.time.LocalDate;

/**
 * 실패 후 재시작 시연용 Processor.
 * failOnDate에 해당하는 행을 만나면 예외를 던져, 해당 파티션을 chunk 중간에서 실패시킨다.
 *
 * - 예외가 난 chunk는 트랜잭션이 롤백되어 쓰기와 진행 위치(ExecutionContext) 모두 반영되지 않는다.
 * - 그 전에 커밋된 chunk들은 결과와 진행 위치가 남아, 재시작 시 다음 chunk부터 이어서 처리된다.
 * - failOnDate는 non-identifying 파라미터로 넘겨야 재시작 때 빼고 실행할 수 있다.
 */
public class FailureInjectingSalesDeltaProcessor implements ItemProcessor<SalesRow, SalesDelta> {

    private final LocalDate failOnDate;

    public FailureInjectingSalesDeltaProcessor(LocalDate failOnDate) {
        this.failOnDate = failOnDate;
    }

    @Override
    public SalesDelta process(SalesRow row){
        if(row.salesDate().equals(failOnDate)){
            throw new IllegalStateException("[재시작 시연] " + failOnDate + " 데이터 처리 중 의도적으로 실패");
        }
        return SalesDelta.from(row);
    }
}
