package com.shop.backend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;

@Configuration
public class CouponScriptConfig {

    @Bean
    public RedisScript<Long> couponIssueScript(){
        return RedisScript.of(new ClassPathResource("scripts/coupon_issue.lua"), Long.class);
    }
}
