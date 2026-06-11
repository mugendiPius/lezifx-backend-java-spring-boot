package com.lezifx.trading.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        // --- payoutRates: 5-second TTL (existing) ---
        CaffeineCache payoutRates = new CaffeineCache("payoutRates",
                Caffeine.newBuilder()
                        .expireAfterWrite(5, TimeUnit.SECONDS)
                        .maximumSize(500)
                        .recordStats()
                        .build());

        // --- darajaTokens: 55-minute TTL (new for Module 6) ---
        // Daraja OAuth tokens are valid for ~1 hour; we cache for 55 min
        // to allow a safe buffer before expiry.
        CaffeineCache darajaTokens = new CaffeineCache("darajaTokens",
                Caffeine.newBuilder()
                        .expireAfterWrite(55, TimeUnit.MINUTES)
                        .maximumSize(100)
                        .recordStats()
                        .build());

        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(payoutRates, darajaTokens));
        return manager;
    }
}