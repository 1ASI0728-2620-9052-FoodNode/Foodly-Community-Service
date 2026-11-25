package com.foodly.community.interfaces.dto;

import com.foodly.community.domain.model.Favorite;
import com.foodly.community.domain.model.Rating;
import com.foodly.community.domain.model.Review;
import com.foodly.community.domain.model.ReviewComment;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceMappingTest {

    @Test
    void mapsReviewWithoutExposingValueObjectsAsTransportFields() {
        var created = Instant.parse("2026-11-24T18:00:00Z");
        var review = new Review("review-1", "explorer-1", "huarique-1", new Rating(4), new ReviewComment("  Bueno\n"), created);
        assertThat(ReviewResource.from(review)).isEqualTo(
                new ReviewResource("review-1", "explorer-1", "huarique-1", 4, "  Bueno\n", created));
    }

    @Test
    void mapsFavoriteWithoutInventingAnArtificialIdentifier() {
        assertThat(FavoriteResource.from(new Favorite("explorer-1", "huarique-1")))
                .isEqualTo(new FavoriteResource("explorer-1", "huarique-1"));
    }

    @Test
    void validationKeepsCommentOptionalAndWithoutArbitraryLengthLimit() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(new CreateReviewResource(5, null))).isEmpty();
            assertThat(validator.validate(new CreateReviewResource(5, "texto ".repeat(2_000)))).isEmpty();
            assertThat(validator.validate(new CreateReviewResource(null, null))).isNotEmpty();
            assertThat(validator.validate(new CreateReviewResource(6, null))).isNotEmpty();
        }
    }
}
