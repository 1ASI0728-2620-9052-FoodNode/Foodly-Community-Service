package com.foodly.community.interfaces.dto;

import com.foodly.community.domain.model.Review;

import java.time.Instant;

/** Contrato HTTP de salida separado del agregado y sus objetos de valor. */
public record ReviewResource(String reviewId, String explorerId, String huariqueId,
                             int rating, String comment, Instant createdAt) {

    public static ReviewResource from(Review review) {
        return new ReviewResource(review.getReviewId(), review.getExplorerId(), review.getHuariqueId(),
                review.getRating().value(), review.getComment().value(), review.getCreatedAt());
    }
}
