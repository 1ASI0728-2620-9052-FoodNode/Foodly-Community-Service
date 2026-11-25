package com.foodly.community.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "community.integrations.jwt")
public record IamJwtProperties(String algorithm, String contract, String secretBase64, String issuer, String audience) {

    @Override
    public String toString() {
        return "IamJwtProperties[redacted]";
    }
}
