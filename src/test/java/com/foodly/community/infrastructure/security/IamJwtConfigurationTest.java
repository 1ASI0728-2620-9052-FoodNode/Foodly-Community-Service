package com.foodly.community.infrastructure.security;

import com.foodly.community.CommunityServiceApplication;
import com.foodly.community.application.commandhandlers.CreateReviewCommandHandler;
import com.foodly.community.application.ports.CurrentExplorer;
import com.foodly.community.interfaces.rest.ReviewController;
import com.mongodb.client.MongoClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.UncheckedIOException;

import static com.foodly.community.infrastructure.security.JwtTestTokens.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IamJwtConfigurationTest {

    private WebApplicationContextRunner runner() {
        return new WebApplicationContextRunner().withUserConfiguration(CommunityServiceApplication.class)
                .withInitializer(context -> {
                    try {
                        context.getEnvironment().getPropertySources().addLast(new ResourcePropertySource("community-defaults", "classpath:application.properties"));
                    } catch (java.io.IOException exception) {
                        throw new UncheckedIOException(exception);
                    }
                });
    }

    @Test
    void jwtOnlyDoesNotRegisterHandlersOrControllersAndKeepsBusinessOperationsBlocked() {
        runner().withPropertyValues("community.integrations.jwt.enabled=true", "community.persistence.mongodb.enabled=false",
                        "community.integrations.jwt.contract=iam-current", "community.integrations.jwt.secret-base64=" + encodedKey())
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(JwtDecoder.class).hasSingleBean(CurrentExplorer.class)
                            .doesNotHaveBean(MongoClient.class).doesNotHaveBean(CreateReviewCommandHandler.class)
                            .doesNotHaveBean(ReviewController.class);
                    var mvc = MockMvcBuilders.webAppContextSetup(context.getSourceApplicationContext()).apply(springSecurity()).build();
                    mvc.perform(get("/community/favorites/me").header("Authorization", "Bearer " + token(EXPLORER, "GASTRONOMIC_EXPLORER")))
                            .andExpect(status().isForbidden());
                    mvc.perform(get("/actuator/health")).andExpect(status().isOk());
                });
    }

    @Test
    void enablingJwtWithoutAKeyFailsClosed() {
        runner().withPropertyValues("community.integrations.jwt.enabled=true", "community.integrations.jwt.contract=iam-current",
                        "community.integrations.jwt.secret-base64=")
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .hasRootCauseInstanceOf(IllegalStateException.class));
    }

    @Test
    void enablingJwtWithoutAnExplicitContractFailsClosed() {
        runner().withPropertyValues("community.integrations.jwt.enabled=true", "community.integrations.jwt.contract=",
                        "community.integrations.jwt.secret-base64=" + encodedKey())
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .hasRootCauseInstanceOf(IllegalStateException.class));
    }

    @Test
    void enablingBothIntegrationsWithoutAMongoUriDoesNotCreateAPartiallyOpenApplication() {
        runner().withPropertyValues("community.integrations.jwt.enabled=true", "community.persistence.mongodb.enabled=true",
                        "community.integrations.jwt.contract=iam-current", "community.integrations.jwt.secret-base64=" + encodedKey(),
                        "spring.data.mongodb.uri=", "spring.data.mongodb.database=foodly-community")
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .hasRootCauseInstanceOf(IllegalStateException.class));
    }
}
