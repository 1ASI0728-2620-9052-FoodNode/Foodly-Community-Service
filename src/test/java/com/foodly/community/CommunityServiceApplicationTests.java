package com.foodly.community;

import com.mongodb.client.MongoClient;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CommunityServiceApplicationTests {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MockMvc mvc;

    @Test
    void contextLoadsWithoutExternalIntegrationBeans() {
        assertThat(context.getBeansOfType(ConnectionFactory.class)).isEmpty();
        assertThat(context.getBeansOfType(JwtDecoder.class)).isEmpty();
        assertThat(context.getBeansOfType(UserDetailsService.class)).isEmpty();
        assertThat(context.getBeansOfType(MongoClient.class)).isEmpty();
        assertThat(context.getBeansOfType(MongoDatabaseFactory.class)).isEmpty();
        assertThat(context.getBeansOfType(MongoOperations.class)).isEmpty();
    }

    @Test
    void healthIsPublicAndDoesNotExposeDetails() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void apiIsDeniedUntilJwtIsImplemented() throws Exception {
        mvc.perform(get("/reviews")).andExpect(status().isForbidden());
        mvc.perform(get("/favorites")).andExpect(status().isForbidden());
        mvc.perform(get("/actuator/env")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void authenticatedUsersCannotBypassInitialClosedPolicy() throws Exception {
        mvc.perform(get("/reviews")).andExpect(status().isForbidden());
    }

    @Test
    void unvalidatedBearerTokenCannotOpenFutureEndpoints() throws Exception {
        mvc.perform(get("/reviews").header("Authorization", "Bearer unvalidated-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void csrfProtectionRemainsActive() throws Exception {
        assertThat(context.getBean(FilterChainProxy.class).getFilterChains().get(0).getFilters())
                .anyMatch(CsrfFilter.class::isInstance);
        mvc.perform(post("/actuator/health")).andExpect(status().isForbidden());
    }
}
