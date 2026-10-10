package com.shop.backend.global.batch;

import lombok.extern.slf4j.Slf4j;

import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;

import org.springframework.batch.core.step.StepExecution;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Job 종료 시 소요 시간과 Step별 처리 건수를 남긴다. (Reader/Chunk/Partitioning 비교 실험의 측정값)
 */
@Slf4j
@Component
public class JobResultLoggingListener implements JobExecutionListener {

    @Override
    public void afterJob(JobExecution jobExecution) {
        LocalDateTime end = jobExecution.getEndTime() != null ? jobExecution.getEndTime() : LocalDateTime.now();
        long elapsedMillis = Duration.between(jobExecution.getStartTime(), end).toMillis();

        log.info("[batch] job={} executionId={} status={} elapsed={} ms params={}",
                jobExecution.getJobInstance().getJobName(), jobExecution.getId(),
                jobExecution.getStatus(), elapsedMillis, jobExecution.getJobParameters());
        for(StepExecution step : jobExecution.getStepExecutions()){
            LocalDateTime stepEnd = step.getEndTime() != null ? step.getEndTime() : LocalDateTime.now();
            log.info("[batch]   step={} status={} read={} write={} commit={} rollback={} elapsed={} ms",
                    step.getStepName(), step.getStatus(), step.getReadCount(), step.getWriteCount(),
                    step.getCommitCount(), step.getRollbackCount(),
                    Duration.between(step.getStartTime(), stepEnd).toMillis());
        }

    }
}
