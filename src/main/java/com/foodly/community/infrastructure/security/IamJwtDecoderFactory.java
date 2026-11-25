package com.foodly.community.infrastructure.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.MappedJwtClaimSetConverter;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.spec.SecretKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import com.nimbusds.jwt.SignedJWT;
import java.text.ParseException;

/** Contrato seleccionado por configuración, nunca inferido del token ni con fallback. */
public final class IamJwtDecoderFactory {

    private IamJwtDecoderFactory() {
    }

    public static JwtDecoder create(IamJwtProperties properties, Clock clock) {
        Objects.requireNonNull(clock);
        if (!"HS256".equals(properties.algorithm())) {
            throw new IllegalStateException("Only HS256 is supported");
        }
        boolean current = "iam-current".equals(properties.contract());
        boolean strict = "issuer-audience".equals(properties.contract());
        if (!current && !strict) {
            throw new IllegalStateException("An explicit IAM_JWT_CONTRACT is required");
        }
        if (strict && (blank(properties.issuer()) || blank(properties.audience()))) {
            throw new IllegalStateException("Issuer and audience are required by the selected contract");
        }
        if (current && (!blank(properties.issuer()) || !blank(properties.audience()))) {
            throw new IllegalStateException("iam-current does not accept ignored issuer or audience settings");
        }
        byte[] key;
        try {
            key = Base64.getDecoder().decode(properties.secretBase64() == null ? "" : properties.secretBase64());
        } catch (IllegalArgumentException invalidBase64) {
            throw new IllegalStateException("IAM_SECRET_BASE64 must be valid Base64");
        }
        if (key.length < 32) {
            throw new IllegalStateException("IAM_SECRET_BASE64 must decode to at least 32 bytes");
        }
        var decoder = NimbusJwtDecoder.withSecretKey(new SecretKeySpec(key, "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256).build();
        var standard = MappedJwtClaimSetConverter.withDefaults(Map.of());
        decoder.setClaimSetConverter(claims -> {
            // No permitir que un sub numérico/objeto sea convertido silenciosamente a texto.
            if (!(claims.get("sub") instanceof String) || !(claims.get("role") instanceof String)) {
                throw new BadJwtException("Invalid JWT claims");
            }
            Object audience = claims.get("aud");
            if (audience != null && !(audience instanceof String)
                    && !(audience instanceof Collection<?> values && values.stream().allMatch(String.class::isInstance))) {
                throw new BadJwtException("Invalid JWT claims");
            }
            return standard.convert(claims);
        });
        decoder.setJwtValidator(jwt -> valid(jwt, properties, clock, current)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid JWT", null)));
        return token -> {
            // Nimbus normaliza algunos claims registrados antes del ClaimSetConverter.
            // Este paso solo rechaza tipos inválidos; no autentica con datos sin verificar.
            try {
                var raw = SignedJWT.parse(token).getPayload().toJSONObject();
                if (raw == null || !(raw.get("sub") instanceof String) || !(raw.get("role") instanceof String)
                        || !(raw.get("iat") instanceof Number) || !(raw.get("exp") instanceof Number)) {
                    throw new BadJwtException("Invalid JWT claims");
                }
                if (raw.containsKey("iss") && !(raw.get("iss") instanceof String)
                        || raw.containsKey("nbf") && !(raw.get("nbf") instanceof Number)) {
                    throw new BadJwtException("Invalid JWT claims");
                }
                Object audience = raw.get("aud");
                if (raw.containsKey("aud") && !(audience instanceof String)
                        && !(audience instanceof Collection<?> values && values.stream().allMatch(String.class::isInstance))) {
                    throw new BadJwtException("Invalid JWT claims");
                }
            } catch (ParseException malformed) {
                throw new BadJwtException("Invalid JWT");
            }
            return decoder.decode(token);
        };
    }

    private static boolean valid(Jwt jwt, IamJwtProperties properties, Clock clock, boolean current) {
        var now = clock.instant();
        var issuedAt = jwt.getIssuedAt();
        var expiresAt = jwt.getExpiresAt();
        if (blank(jwt.getSubject()) || !(jwt.getClaim("role") instanceof String role) || role.isBlank()
                || issuedAt == null || expiresAt == null || !expiresAt.isAfter(now)
                || issuedAt.isAfter(now) || !expiresAt.isAfter(issuedAt)
                || Duration.between(issuedAt, expiresAt).compareTo(Duration.ofHours(1)) > 0
                || jwt.getNotBefore() != null && jwt.getNotBefore().isAfter(now)) {
            return false;
        }
        if (current) {
            return !jwt.getClaims().containsKey("iss") && !jwt.getClaims().containsKey("aud");
        }
        return properties.issuer().equals(jwt.getClaimAsString("iss"))
                && jwt.getAudience() != null && jwt.getAudience().contains(properties.audience());
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
