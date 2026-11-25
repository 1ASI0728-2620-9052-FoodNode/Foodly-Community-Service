package com.foodly.community.interfaces.rest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Usa la cadena de seguridad real del proyecto, sin reemplazarla ni abrir rutas. */
@SpringBootTest
@AutoConfigureMockMvc
class CommunityRestSecurityTests {

    @Autowired private MockMvc mvc;
    @Autowired private ApplicationContext context;

    @ParameterizedTest
    @MethodSource("businessRequests")
    void productionPolicyRejectsEveryBusinessOperationWithoutAuthentication(HttpMethod method, String path) throws Exception {
        mvc.perform(businessRequest(method, path).with(csrf())).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @MethodSource("businessRequests")
    void evenAMockAuthenticatedUserCannotOpenTheClosedProductionPolicy(HttpMethod method, String path) throws Exception {
        mvc.perform(businessRequest(method, path).with(csrf()).with(user("test-user")))
                .andExpect(status().isForbidden());
    }

    @Test
    void unvalidatedBearerAndExplorerHeadersDoNotAuthenticateRequests() throws Exception {
        mvc.perform(businessRequest(HttpMethod.GET, "/community/favorites/me")
                        .header("Authorization", "Bearer unvalidated-test-value")
                        .header("X-Explorer-Id", "attacker"))
                .andExpect(status().isForbidden());
    }

    @Test
    void noFallbackControllersOrHandlersAreRegisteredWithoutAdapters() {
        assertThat(context.getBeansOfType(ReviewController.class)).isEmpty();
        assertThat(context.getBeansOfType(FavoriteController.class)).isEmpty();
    }

    static Stream<Arguments> businessRequests() {
        return Stream.of(
                Arguments.of(HttpMethod.POST, "/community/huariques/huarique-1/reviews"),
                Arguments.of(HttpMethod.GET, "/community/huariques/huarique-1/reviews"),
                Arguments.of(HttpMethod.POST, "/community/favorites/huarique-1"),
                Arguments.of(HttpMethod.DELETE, "/community/favorites/huarique-1"),
                Arguments.of(HttpMethod.GET, "/community/favorites/me"));
    }

    private MockHttpServletRequestBuilder businessRequest(HttpMethod method, String path) {
        var request = request(method, path);
        if (path.contains("/reviews") && method == HttpMethod.POST) {
            request.contentType(MediaType.APPLICATION_JSON).content("{\"rating\":5}");
        }
        return request;
    }
}
