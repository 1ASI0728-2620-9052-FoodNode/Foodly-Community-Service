package com.foodly.community.infrastructure.identity;

import com.foodly.community.application.ports.ReviewIdGenerator;

import java.util.UUID;

/** UUID aleatorio del lado servidor; el dominio sigue aceptando identidades opacas. */
public final class UuidReviewIdGenerator implements ReviewIdGenerator {

    @Override
    public String nextId() {
        return UUID.randomUUID().toString();
    }
}
