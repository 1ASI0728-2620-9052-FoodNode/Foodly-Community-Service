package com.foodly.community.infrastructure.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpringSecurityCurrentExplorerTest {

    private final SpringSecurityCurrentExplorer adapter = new SpringSecurityCurrentExplorer();

    @AfterEach
    void clearContext() { SecurityContextHolder.clearContext(); }

    @Test
    void readsOnlyTheAuthenticatedJwtSubject() {
        var jwt = Jwt.withTokenValue("unit-context-placeholder").header("alg", "HS256").subject("explorer-id").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
        assertThat(adapter.explorerId()).contains("explorer-id");
    }

    @Test
    void absenceOfAuthenticationDoesNotInventAnIdentity() {
        assertThat(adapter.explorerId()).isEmpty();
    }

    @Test
    void usernameAuthenticationCannotPretendToBeAJwtExplorer() {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated("spoofed", "", List.of()));
        assertThat(adapter.explorerId()).isEmpty();
    }

    @Test
    void unauthenticatedJwtCannotProvideAnIdentity() {
        var jwt = Jwt.withTokenValue("unit-context-placeholder").header("alg", "HS256").subject("explorer-id").build();
        var authentication = new JwtAuthenticationToken(jwt, List.of());
        authentication.setAuthenticated(false);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        assertThat(adapter.explorerId()).isEmpty();
    }
}
