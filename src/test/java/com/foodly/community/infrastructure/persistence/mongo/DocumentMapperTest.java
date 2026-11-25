package com.foodly.community.infrastructure.persistence.mongo;

import com.foodly.community.domain.model.Favorite;
import com.foodly.community.domain.model.Rating;
import com.foodly.community.domain.model.Review;
import com.foodly.community.domain.model.ReviewComment;
import com.foodly.community.infrastructure.persistence.mongo.document.FavoriteDocument;
import com.foodly.community.infrastructure.persistence.mongo.document.ReviewDocument;
import com.foodly.community.infrastructure.persistence.mongo.mapper.FavoriteDocumentMapper;
import com.foodly.community.infrastructure.persistence.mongo.mapper.ReviewDocumentMapper;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

class DocumentMapperTest {

    @Test
    void reviewMappingPreservesAllFieldsAndFullInstantPrecision() {
        var instant = Instant.parse("2026-11-24T18:00:00.123456789Z");
        var source = new Review("opaque:id", "explorer", "huarique", new Rating(4), new ReviewComment("  sabor\n"), instant);
        var document = ReviewDocumentMapper.toDocument(source);
        assertThat(document).isEqualTo(new ReviewDocument("opaque:id", "explorer", "huarique", 4, "  sabor\n", instant.toString()));
        var restored = ReviewDocumentMapper.toDomain(document);
        assertThat(restored.getReviewId()).isEqualTo(source.getReviewId());
        assertThat(restored.getExplorerId()).isEqualTo(source.getExplorerId());
        assertThat(restored.getHuariqueId()).isEqualTo(source.getHuariqueId());
        assertThat(restored.getRating()).isEqualTo(source.getRating());
        assertThat(restored.getComment()).isEqualTo(source.getComment());
        assertThat(restored.getCreatedAt()).isEqualTo(instant);
    }

    @Test
    void absentCommentUsesTheExistingDomainRepresentation() {
        var restored = ReviewDocumentMapper.toDomain(new ReviewDocument("review", "explorer", "huarique", 3, null, Instant.EPOCH.toString()));
        assertThat(restored.getComment()).isEqualTo(new ReviewComment(""));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 6})
    void invalidStoredRatingsAreRejectedByTheDomain(int rating) {
        assertThatIllegalArgumentException().isThrownBy(() -> ReviewDocumentMapper.toDomain(
                new ReviewDocument("review", "explorer", "huarique", rating, "", Instant.EPOCH.toString())));
    }

    @Test
    void blankStoredIdentityIsRejectedByTheDomain() {
        assertThatIllegalArgumentException().isThrownBy(() -> ReviewDocumentMapper.toDomain(
                new ReviewDocument("review", " ", "huarique", 3, "", Instant.EPOCH.toString())));
    }

    @Test
    void favoriteMappingUsesATechnicalIdWithoutChangingCompositeIdentity() {
        var favorite = new Favorite("explorer", "huarique");
        var document = FavoriteDocumentMapper.toDocument(favorite);
        assertThat(document.id()).isNotNull();
        assertThat(document.explorerId()).isEqualTo("explorer");
        assertThat(document.huariqueId()).isEqualTo("huarique");
        assertThat(FavoriteDocumentMapper.toDomain(document)).isEqualTo(favorite);
        assertThat(FavoriteDocumentMapper.toDomain(new FavoriteDocument(new ObjectId(), "explorer", "huarique")))
                .isEqualTo(favorite);
    }

    @Test
    void invalidStoredFavoriteCannotBecomeADomainAggregate() {
        assertThatIllegalArgumentException().isThrownBy(() -> FavoriteDocumentMapper.toDomain(
                new FavoriteDocument(new ObjectId(), "explorer", " ")));
    }
}
