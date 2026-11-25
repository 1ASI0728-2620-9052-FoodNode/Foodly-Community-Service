package com.foodly.community.infrastructure.config;

import com.foodly.community.application.ports.ReviewIdGenerator;
import com.foodly.community.infrastructure.identity.UuidReviewIdGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class RuntimeAdaptersConfiguration {

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock communityClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean(ReviewIdGenerator.class)
    ReviewIdGenerator reviewIdGenerator() {
        return new UuidReviewIdGenerator();
    }
}
