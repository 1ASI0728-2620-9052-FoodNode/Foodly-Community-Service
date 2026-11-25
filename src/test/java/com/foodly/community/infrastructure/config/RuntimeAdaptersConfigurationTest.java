package com.foodly.community.infrastructure.config;

import com.foodly.community.application.ports.ReviewIdGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeAdaptersConfigurationTest {

    @Test
    void providesUtcClockAndRandomUuidIdentitiesWithoutExternalServices() {
        new ApplicationContextRunner().withUserConfiguration(RuntimeAdaptersConfiguration.class).run(context -> {
            assertThat(context.getBean(Clock.class).getZone()).isEqualTo(ZoneOffset.UTC);
            var generator = context.getBean(ReviewIdGenerator.class);
            var ids = new HashSet<String>();
            for (int index = 0; index < 1000; index++) {
                String id = generator.nextId();
                assertThat(UUID.fromString(id).version()).isEqualTo(4);
                ids.add(id);
            }
            assertThat(ids).hasSize(1000);
        });
    }

    @Test
    void acceptsDeterministicClockAndGeneratorOverrides() {
        Clock fixed = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
        new ApplicationContextRunner().withBean(Clock.class, () -> fixed)
                .withBean(ReviewIdGenerator.class, () -> () -> "deterministic-id")
                .withUserConfiguration(RuntimeAdaptersConfiguration.class).run(context -> {
                    assertThat(context).hasSingleBean(Clock.class).hasSingleBean(ReviewIdGenerator.class);
                    assertThat(context.getBean(Clock.class)).isSameAs(fixed);
                    assertThat(context.getBean(ReviewIdGenerator.class).nextId()).isEqualTo("deterministic-id");
                });
    }
}
