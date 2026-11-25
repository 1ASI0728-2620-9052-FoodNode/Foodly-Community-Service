package com.foodly.community.infrastructure.config;

import com.foodly.community.domain.repository.IFavoriteRepository;
import com.foodly.community.domain.repository.IReviewRepository;
import com.mongodb.client.MongoClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.mongodb.core.MongoOperations;

import static org.assertj.core.api.Assertions.assertThat;

class MongoPersistenceConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(MongoPersistenceConfiguration.class);

    @Test
    void defaultStartupHasNoMongoBeansOrConnections() {
        runner.run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(MongoClient.class)
                .doesNotHaveBean(MongoOperations.class).doesNotHaveBean(IReviewRepository.class)
                .doesNotHaveBean(IFavoriteRepository.class));
    }

    @Test
    void activationWithoutAUriFailsBeforeCreatingAClient() {
        runner.withPropertyValues("community.persistence.mongodb.enabled=true", "spring.data.mongodb.database=foodly-community")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasRootCauseInstanceOf(IllegalStateException.class)
                        .hasStackTraceContaining("MONGODB_URI is required"));
    }

    @Test
    void rejectsTheBusinessDatabaseBeforeCreatingAClient() {
        runner.withPropertyValues("community.persistence.mongodb.enabled=true", "spring.data.mongodb.uri=mongodb://127.0.0.1:1",
                        "spring.data.mongodb.database=foodly_business")
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .hasRootCauseInstanceOf(IllegalStateException.class).hasStackTraceContaining("MONGODB_DATABASE must be foodly-community"));
    }

    @Test
    void rejectsAMismatchedDatabaseInTheUriBeforeCreatingAClient() {
        runner.withPropertyValues("community.persistence.mongodb.enabled=true", "spring.data.mongodb.uri=mongodb://127.0.0.1:1/foodly_business",
                        "spring.data.mongodb.database=foodly-community")
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .hasRootCauseInstanceOf(IllegalStateException.class).hasStackTraceContaining("URI database must match"));
    }
}
