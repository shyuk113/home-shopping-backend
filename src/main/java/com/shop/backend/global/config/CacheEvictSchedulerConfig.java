package com.shop.backend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
public class CacheEvictSchedulerConfig {

    @Bean
    public ScheduledExecutorService cacheEvictScheduler() {
        return Executors.newSingleThreadScheduledExecutor();
    }
}
