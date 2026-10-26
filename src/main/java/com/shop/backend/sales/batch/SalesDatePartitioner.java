package com.shop.backend.sales.batch;


import org.springframework.batch.core.partition.Partitioner;
import org.springframework.batch.infrastructure.item.ExecutionContext;


import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

public class SalesDatePartitioner implements Partitioner {

    private static final String FROM_KEY = "from";
    private static final String TO_KEY = "to";

    private final LocalDate from;
    private final LocalDate to;

    public SalesDatePartitioner(LocalDate from, LocalDate to) {
        this.from = from;
        this.to = to;
    }

    @Override
    public Map<String, ExecutionContext> partition(int gridSize){
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        int partitionCount = (int) Math.min(gridSize, days);

        Map<String, ExecutionContext> partitions = new LinkedHashMap<>();
        LocalDate start = from;
        for(int i = 0; i < partitionCount; i++){
            long size = days / partitionCount + (i < days % partitionCount ? 1 : 0);
            LocalDate end = start.plusDays(size-1);

            ExecutionContext context = new ExecutionContext();
            context.putString(FROM_KEY, start.toString());
            context.putString(TO_KEY, end.toString());
            partitions.put(String.format("partition%02d", i), context);

            start = end.plusDays(1);
        }
        return partitions;
    }
}
