package com.restaurante.backend.config;

import com.google.common.util.concurrent.RateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApiConfig {

    @Bean
    RateLimiter apiRateLimiter(@Value("${app.rate-limit.permits-per-second}") double permitsPerSecond) {
        return RateLimiter.create(permitsPerSecond);
    }
}