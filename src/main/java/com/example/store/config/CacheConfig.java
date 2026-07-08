package com.example.store.config;

import com.github.benmanes.caffeine.cache.Caffeine;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCacheSpecification("maximumSize=500,expireAfterWrite=30m");
        cacheManager.setCacheNames(List.of("products", "productById", "orderById"));
        cacheManager.registerCustomCache(
                "orderById", Caffeine.newBuilder().maximumSize(1000).build());
        return cacheManager;
    }
}
