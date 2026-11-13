package com.shop.backend.sales.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * 집계 배치가 성공하면 판매자 대시보드 캐시를 비운다.
 * 실패한 배치는 집계 테이블이 일부만 갱신된 상태일 수 있어 캐시를 유지한다(재시작 성공 시 비움).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DashboardCacheEvictListener implements JobExecutionListener {

    private static final String CACHE_NAME = "sellerDashboard";

    private final CacheManager cacheManager;

    @Override
    public void afterJob(JobExecution jobExecution) {
        if (jobExecution.getStatus() != BatchStatus.COMPLETED) {
            log.warn("[cache] 배치 실패({}) → {} 캐시 유지 (executionId={})",
                    jobExecution.getStatus(), CACHE_NAME, jobExecution.getId());
            return;
        }
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            cache.clear();
            log.info("[cache] 배치 완료 → {} 캐시 삭제 (executionId={})", CACHE_NAME, jobExecution.getId());
        }
    }
}
