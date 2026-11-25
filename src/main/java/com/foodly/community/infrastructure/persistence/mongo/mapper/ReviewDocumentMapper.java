package com.foodly.community.infrastructure.persistence.mongo.mapper;

import com.foodly.community.domain.model.Rating;
import com.foodly.community.domain.model.Review;
import com.foodly.community.domain.model.ReviewComment;
import com.foodly.community.infrastructure.persistence.mongo.document.ReviewDocument;

import java.time.Instant;

public final class ReviewDocumentMapper {

    private ReviewDocumentMapper() {
    }

    public static ReviewDocument toDocument(Review review) {
        // ISO-8601 conserva la precisión nanosegundo del Instant; BSON Date solo conserva milisegundos.
        return new ReviewDocument(review.getReviewId(), review.getExplorerId(), review.getHuariqueId(),
                review.getRating().value(), review.getComment().value(), review.getCreatedAt().toString());
    }

    public static Review toDomain(ReviewDocument document) {
        return new Review(document.reviewId(), document.explorerId(), document.huariqueId(),
                new Rating(document.rating()), new ReviewComment(document.comment()), Instant.parse(document.createdAt()));
    }
}
