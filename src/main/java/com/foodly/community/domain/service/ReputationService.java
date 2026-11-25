package com.foodly.community.domain.service;

import com.foodly.community.domain.model.Review;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

/** Cálculo puro del promedio: sin repositorio, estado almacenado ni dependencias de Spring. */
public final class ReputationService {

    /**
     * Promedia solo las reseñas del huarique indicado, sin umbral mínimo.
     * La suma es exacta; la división usa DECIMAL128 (34 cifras, HALF_EVEN).
     * No aplica un redondeo de presentación ni almacena el resultado.
     *
     * @return vacío cuando no existen reseñas del huarique; nunca cero como ausencia
     */
    public Optional<BigDecimal> calculateAverage(String huariqueId, Collection<Review> reviews) {
        if (huariqueId == null || huariqueId.isBlank()) {
            throw new IllegalArgumentException("huariqueId must not be null or blank");
        }
        Objects.requireNonNull(reviews, "reviews is required");
        BigDecimal sum = BigDecimal.ZERO;
        long count = 0;
        for (Review review : reviews) {
            Objects.requireNonNull(review, "reviews must not contain null");
            if (huariqueId.equals(review.getHuariqueId())) {
                sum = sum.add(BigDecimal.valueOf(review.getRating().value()));
                count++;
            }
        }
        return count == 0 ? Optional.empty()
                : Optional.of(sum.divide(BigDecimal.valueOf(count), MathContext.DECIMAL128));
    }
}
