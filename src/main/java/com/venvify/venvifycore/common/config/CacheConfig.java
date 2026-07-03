package com.venvify.venvifycore.common.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Cache in-process cho endpoint đọc nóng (plan P3 §2.3): discover không-q, categories count,
 * rating host, KPI admin. TTL 60s — KHÔNG invalidate chủ động, sai lệch tối đa 60s chấp nhận.
 * Cache name tạo động theo @Cacheable; đổi sang Redis chỉ khi chạy >1 instance (master §14).
 * (LoginAttemptService dùng Caffeine trực tiếp, không qua manager này — TTL riêng theo lockout.)
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(60))
                .maximumSize(500));
        return manager;
    }
}
