package com.foodly.community.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class ReviewTest {

    private static final Instant CREATED_AT = Instant.parse("2026-11-24T12:00:00Z");

    @Test
    void createsAValidReview() {
        var rating = new Rating(5);
        var comment = new ReviewComment("Excelente sabor");
        var review = new Review("review-1", "explorer-1", "huarique-1", rating, comment, CREATED_AT);

        assertThat(review.getReviewId()).isEqualTo("review-1");
        assertThat(review.getExplorerId()).isEqualTo("explorer-1");
        assertThat(review.getHuariqueId()).isEqualTo("huarique-1");
        assertThat(review.getRating()).isEqualTo(rating);
        assertThat(review.getComment()).isEqualTo(comment);
        assertThat(review.getCreatedAt()).isEqualTo(CREATED_AT);
    }

    @ParameterizedTest
    @MethodSource("invalidIdentifiers")
    void rejectsEveryMissingOrBlankIdentifier(String reviewId, String explorerId, String huariqueId) {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new Review(reviewId, explorerId, huariqueId, new Rating(4), null, CREATED_AT));
    }

    static Stream<Arguments> invalidIdentifiers() {
        return Stream.of(null, "", " ", "\t\n", "\u2003")
                .flatMap(invalid -> Stream.of(
                        Arguments.of(invalid, "explorer-1", "huarique-1"),
                        Arguments.of("review-1", invalid, "huarique-1"),
                        Arguments.of("review-1", "explorer-1", invalid)));
    }

    @Test
    void requiresAValidRatingObject() {
        assertThatNullPointerException().isThrownBy(() ->
                new Review("review-1", "explorer-1", "huarique-1", null, null, CREATED_AT));
    }

    @Test
    void requiresTheCreationInstant() {
        assertThatNullPointerException().isThrownBy(() ->
                new Review("review-1", "explorer-1", "huarique-1", new Rating(4), null, null));
    }

    @Test
    void allowsAReviewWithoutComment() {
        var review = new Review("review-1", "explorer-1", "huarique-1", new Rating(4), null, CREATED_AT);
        assertThat(review.getComment()).isEqualTo(new ReviewComment(""));
    }

    @Test
    void usesOpaqueIdentifiersWithoutInventingFormatsOrNormalizingThem() {
        var review = new Review("review:local", " explorer-1 ", "huarique/externo",
                new Rating(4), null, CREATED_AT);
        assertThat(review.getReviewId()).isEqualTo("review:local");
        assertThat(review.getExplorerId()).isEqualTo(" explorer-1 ");
        assertThat(review.getHuariqueId()).isEqualTo("huarique/externo");
    }

    @Test
    void aggregateEqualityDependsOnIdentityRatherThanAllAttributes() {
        var first = new Review("review-1", "explorer-1", "huarique-1", new Rating(4), null, CREATED_AT);
        var sameIdentity = new Review("review-1", "explorer-1", "huarique-1", new Rating(5), null, CREATED_AT);
        var differentIdentity = new Review("review-2", "explorer-1", "huarique-1", new Rating(4), null, CREATED_AT);
        assertThat(first).isEqualTo(sameIdentity).hasSameHashCodeAs(sameIdentity);
        assertThat(first).isNotEqualTo(differentIdentity);
    }
}
