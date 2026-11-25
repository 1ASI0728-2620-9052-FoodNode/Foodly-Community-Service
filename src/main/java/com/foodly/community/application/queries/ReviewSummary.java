package com.foodly.community.application.queries;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/** Resultado de aplicación, no DTO REST. Cero reseñas se expresa con count=0 y promedio vacío. */
public record ReviewSummary(String huariqueId, long reviewCount, Optional<BigDecimal> averageRating) {

    public ReviewSummary {
        Objects.requireNonNull(huariqueId);
        Objects.requireNonNull(averageRating);
        if (huariqueId.isBlank() || reviewCount < 0 || (reviewCount == 0) != averageRating.isEmpty()) {
            throw new IllegalArgumentException("Review summary count and average must be consistent");
        }
    }
}
