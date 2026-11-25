package com.foodly.community.infrastructure.security;

import com.foodly.community.application.ports.CurrentExplorer;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Optional;

/** Solo lee el contexto autenticado del Resource Server; no inspecciona requests o headers. */
public final class SpringSecurityCurrentExplorer implements CurrentExplorer {

    @Override
    public Optional<String> explorerId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt) || !jwt.isAuthenticated()) {
            return Optional.empty();
        }
        return Optional.ofNullable(jwt.getToken().getSubject()).filter(subject -> !subject.isBlank());
    }
}
