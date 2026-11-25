package com.foodly.community.infrastructure.config;

import com.foodly.community.infrastructure.messaging.business.*;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BusinessEventsConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(BusinessEventsConfiguration.class);

    @Test void disabledByDefaultCreatesNoInfrastructure() {
        runner.run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(ConnectionFactory.class).doesNotHaveBean(MongoBusinessEventStore.class);
        });
    }

    @Test void explicitFalseIgnoresIncompleteSettings() {
        runner.withPropertyValues("community.integrations.business-events.enabled=false").run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(ConnectionFactory.class);
        });
    }

    @Test void enabledWithoutMongoFailsBeforeBrokerConnection() {
        runner.withPropertyValues("community.integrations.business-events.enabled=true").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseMessage("Business consumer requires explicitly enabled MongoDB persistence");
        });
    }

    @Test void missingRabbitCredentialsDoesNotUseGuestFallback() {
        runner.withPropertyValues("community.integrations.business-events.enabled=true", "community.persistence.mongodb.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseMessage("Explicit RabbitMQ connection settings are required");
                });
    }

    @Test void standaloneMongoRejectedBeforeCollectionsOrTransactions() {
        var mongo = mock(MongoTemplate.class);
        when(mongo.executeCommand(any(Document.class))).thenReturn(new Document("isWritablePrimary", true));
        assertThrows(IllegalStateException.class, () -> new MongoBusinessEventStore(mongo, Clock.systemUTC()));
        verify(mongo).executeCommand(any(Document.class));
        verifyNoMoreInteractions(mongo);
    }
}
