package com.foodly.community.domain.model;

import java.time.Instant;
import java.util.Objects;

/** Raíz de agregado inmutable; su igualdad se determina por reviewId. */
public final class Review {

    private final String reviewId;
    private final String explorerId;
    private final String huariqueId;
    private final Rating rating;
    private final ReviewComment comment;
    private final Instant createdAt;

    /** La identidad y el instante son suministrados por el llamador, sin infraestructura. */
    public Review(String reviewId, String explorerId, String huariqueId,
                  Rating rating, ReviewComment comment, Instant createdAt) {
        this.reviewId = DomainIdentifiers.requireValid(reviewId, "reviewId");
        this.explorerId = DomainIdentifiers.requireValid(explorerId, "explorerId");
        this.huariqueId = DomainIdentifiers.requireValid(huariqueId, "huariqueId");
        this.rating = Objects.requireNonNull(rating, "rating is required");
        this.comment = comment == null ? new ReviewComment(null) : comment;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
    }

    public String getReviewId() {
        return reviewId;
    }

    public String getExplorerId() {
        return explorerId;
    }

    public String getHuariqueId() {
        return huariqueId;
    }

    public Rating getRating() {
        return rating;
    }

    public ReviewComment getComment() {
        return comment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Review review && reviewId.equals(review.reviewId);
    }

    @Override
    public int hashCode() {
        return reviewId.hashCode();
    }
}
