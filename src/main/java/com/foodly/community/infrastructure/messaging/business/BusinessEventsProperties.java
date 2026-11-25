package com.foodly.community.infrastructure.messaging.business;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("community.integrations.business-events")
public record BusinessEventsProperties(int maxAttempts, long retryDelayMs) {
    public BusinessEventsProperties {
        if (maxAttempts < 1 || retryDelayMs < 0) throw new IllegalArgumentException("Invalid Business consumer retry settings");
    }
}
