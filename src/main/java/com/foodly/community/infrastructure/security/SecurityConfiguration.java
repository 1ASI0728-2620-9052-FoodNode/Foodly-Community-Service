package com.foodly.community.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodly.community.interfaces.dto.ApiErrorResource;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.core.env.Environment;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import java.io.IOException;
import java.util.List;

/** Política cerrada por defecto; API Bearer habilitada solo con JWT y MongoDB explícitos. */
@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, Environment environment,
                                           ObjectProvider<JwtDecoder> decoder, ObjectMapper json) throws Exception {
        boolean ready = environment.getProperty("community.integrations.jwt.enabled", Boolean.class, false)
                && environment.getProperty("community.persistence.mongodb.enabled", Boolean.class, false);
        if (!ready) {
            http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                .anyRequest().denyAll());
            return http.build();
        }
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String role = jwt.getClaimAsString("role");
            return switch (role) {
                case "GASTRONOMIC_EXPLORER", "HUARIQUE_OWNER" -> List.of(new SimpleGrantedAuthority("ROLE_" + role));
                default -> List.of();
            };
        });
        AuthorizationManager<RequestAuthorizationContext> explorerAccess = (authentication, request) -> {
            var principal = authentication.get();
            return new AuthorizationDecision(principal instanceof JwtAuthenticationToken && principal.isAuthenticated()
                    && principal.getAuthorities().stream().anyMatch(authority ->
                    "ROLE_GASTRONOMIC_EXPLORER".equals(authority.getAuthority())));
        };
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Solo API Bearer: no usa autenticación de cookies/sesión. CSRF sigue fuera de /community.
                .csrf(csrf -> csrf.ignoringRequestMatchers("/community/**"))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/community/huariques/*/reviews").access(explorerAccess)
                        .requestMatchers(HttpMethod.GET, "/community/huariques/*/reviews").access(explorerAccess)
                        .requestMatchers(HttpMethod.POST, "/community/favorites/*").access(explorerAccess)
                        .requestMatchers(HttpMethod.DELETE, "/community/favorites/*").access(explorerAccess)
                        .requestMatchers(HttpMethod.GET, "/community/favorites/me").access(explorerAccess)
                        .anyRequest().denyAll())
                .oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt.decoder(decoder.getObject())
                                .jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint((request, response, exception) -> writeError(json, response, 401))
                        .accessDeniedHandler((request, response, exception) -> writeError(json, response, 403)))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> writeError(json, response, 401))
                        .accessDeniedHandler((request, response, exception) -> writeError(json, response, 403)));
        return http.build();
    }

    private void writeError(ObjectMapper json, HttpServletResponse response, int status) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        if (status == 401) {
            response.setHeader("WWW-Authenticate", "Bearer");
        }
        json.writeValue(response.getOutputStream(), new ApiErrorResource(status,
                status == 401 ? "AUTHENTICATION_REQUIRED" : "ACCESS_DENIED",
                status == 401 ? "Authentication required" : "Access denied"));
    }
}
