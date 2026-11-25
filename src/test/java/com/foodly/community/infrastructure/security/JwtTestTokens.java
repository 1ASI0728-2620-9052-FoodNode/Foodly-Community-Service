package com.foodly.community.infrastructure.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/** Productor JJWT 0.13.0 de prueba con claves efímeras; nunca se usa en runtime productivo. */
public final class JwtTestTokens {

    public static final Instant NOW = Instant.parse("2026-11-24T18:00:00Z");
    public static final byte[] KEY = randomKey();
    public static final String EXPLORER = "123e4567-e89b-42d3-a456-426614174000";
    public static final String OTHER = "123e4567-e89b-42d3-a456-426614174001";

    private JwtTestTokens() { }

    public static byte[] randomKey() {
        byte[] key = new byte[64];
        new SecureRandom().nextBytes(key);
        return key;
    }

    public static String encodedKey() {
        return Base64.getEncoder().encodeToString(KEY);
    }

    public static String token(String subject, String role) {
        return token(subject, role, null, false);
    }

    public static String token(String subject, String role, Invalid invalid, boolean strict) {
        var builder = Jwts.builder().subject(subject).claim("role", role);
        if (invalid != Invalid.MISSING_IAT) builder.issuedAt(Date.from(invalid == Invalid.FUTURE_IAT ? NOW.plusSeconds(30) : NOW.minusSeconds(10)));
        if (invalid != Invalid.MISSING_EXP) builder.expiration(Date.from(invalid == Invalid.EXPIRED ? NOW.minusSeconds(1)
                : invalid == Invalid.LONG_LIFETIME ? NOW.plusSeconds(7200) : NOW.plusSeconds(300)));
        if (invalid == Invalid.MISSING_SUB) builder.subject(null);
        if (invalid == Invalid.BLANK_SUB) builder.subject(" ");
        if (invalid == Invalid.MISSING_ROLE) builder.claim("role", null);
        if (invalid == Invalid.BLANK_ROLE) builder.claim("role", " ");
        if (invalid == Invalid.FUTURE_NBF) builder.notBefore(Date.from(NOW.plusSeconds(30)));
        if (strict) {
            if (invalid != Invalid.MISSING_ISS) builder.issuer(invalid == Invalid.WRONG_ISS ? "other-test-issuer" : "test-issuer");
            if (invalid != Invalid.MISSING_AUD) builder.audience().add(invalid == Invalid.WRONG_AUD ? "other-test-audience" : "test-audience").and();
        }
        if (invalid == Invalid.UNSIGNED) return builder.compact();
        byte[] key = invalid == Invalid.WRONG_KEY ? randomKey() : KEY;
        return builder.signWith(Keys.hmacShaKeyFor(key), invalid == Invalid.HS384 ? Jwts.SIG.HS384
                : invalid == Invalid.HS512 ? Jwts.SIG.HS512 : Jwts.SIG.HS256).compact();
    }

    public enum Invalid {
        WRONG_KEY, EXPIRED, MISSING_EXP, MISSING_SUB, BLANK_SUB, MISSING_ROLE, BLANK_ROLE,
        MISSING_IAT, FUTURE_IAT, LONG_LIFETIME, FUTURE_NBF, UNSIGNED, HS384, HS512,
        MISSING_ISS, MISSING_AUD, WRONG_ISS, WRONG_AUD
    }
}
