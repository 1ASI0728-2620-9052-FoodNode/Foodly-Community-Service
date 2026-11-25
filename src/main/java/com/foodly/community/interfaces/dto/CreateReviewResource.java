package com.foodly.community.interfaces.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Entrada HTTP sin identidad del explorador, ID de reseña ni fecha. */
public record CreateReviewResource(
        @NotNull @Min(1) @Max(5)
        @JsonDeserialize(using = IntegerRatingDeserializer.class) Integer rating,
        String comment) {

    /** Rechaza propiedades desconocidas aunque Jackson globalmente permita ignorarlas. */
    @JsonAnySetter
    public void rejectUnknownProperty(String name, Object value) {
        throw new IllegalArgumentException("Unexpected request field");
    }
}
