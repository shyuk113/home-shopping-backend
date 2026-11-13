package com.shop.backend.global.config;

import java.time.Duration;

import com.shop.backend.sales.application.dto.CachedDailySales;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.BatchStrategies;          // 추가
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;         // 추가
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

@Configuration
@EnableCaching
public class RedisConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {

        PolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.shop.backend")
                .allowIfSubType("java.util")
                .allowIfSubType("org.springframework.cache.support.NullValue")
                .build();

        RedisSerializer<Object> serializer = GenericJacksonJsonRedisSerializer.builder()
                .enableSpringCacheNullValueSupport()
                .enableDefaultTyping(typeValidator)
                .build();

        RedisCacheConfiguration config =
                RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(Duration.ofMinutes(10)) // 캐시 기본 유효기간 10분
                        .serializeValuesWith(
                                RedisSerializationContext.SerializationPair
                                        .fromSerializer(serializer)
                        );

        RedisCacheConfiguration dashboardConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new JacksonJsonRedisSerializer<>(CachedDailySales.class)));   // 타입 고정

        // 캐시 전체 삭제(clear) 시 KEYS 대신 SCAN으로 키를 찾음: KEYS는 Redis 전체를 멈추게 함
        RedisCacheWriter cacheWriter =
                RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory, BatchStrategies.scan(1000));

        return RedisCacheManager.builder(cacheWriter)
                .cacheDefaults(config)                                      // 기존 캐시는 그대로
                .withCacheConfiguration("sellerDashboard", dashboardConfig) // 대시보드 캐시만 별도
                .build();
    }
}
