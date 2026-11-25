package com.foodly.community.infrastructure.security;

import com.foodly.community.application.ports.CurrentExplorer;
import com.foodly.community.infrastructure.config.RuntimeAdaptersConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "community.integrations.jwt", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(IamJwtProperties.class)
@Import(RuntimeAdaptersConfiguration.class)
public class IamJwtConfiguration {

    @Bean
    JwtDecoder communityJwtDecoder(IamJwtProperties properties, Clock clock) {
        return IamJwtDecoderFactory.create(properties, clock);
    }

    @Bean
    CurrentExplorer currentExplorer() {
        return new SpringSecurityCurrentExplorer();
    }
}
