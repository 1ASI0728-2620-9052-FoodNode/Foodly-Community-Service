package com.foodly.community.domain.service;

import com.foodly.community.domain.model.Rating;
import com.foodly.community.domain.model.Review;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class ReputationServiceTest {

    private final ReputationService service = new ReputationService();

    @Test
    void calculatesTheAverageOfValidRatings() {
        var average = service.calculateAverage("huarique-1", List.of(
                review("1", "huarique-1", 1), review("2", "huarique-1", 4), review("3", "huarique-1", 5)));
        assertThat(average).hasValue(new BigDecimal("3.333333333333333333333333333333333"));
    }

    @Test
    void preservesExactTerminatingAveragesWithoutRoundingToStars() {
        assertThat(service.calculateAverage("huarique-1", List.of(
                review("1", "huarique-1", 4), review("2", "huarique-1", 5))))
                .hasValue(new BigDecimal("4.5"));
    }

    @Test
    void aSingleReviewHasItsOwnRatingAsReputation() {
        assertThat(service.calculateAverage("huarique-1", List.of(review("1", "huarique-1", 5))))
                .hasValue(BigDecimal.valueOf(5));
    }

    @Test
    void noReviewsMeansAbsenceRatherThanZero() {
        assertThat(service.calculateAverage("huarique-1", List.of())).isEmpty();
    }

    @Test
    void ignoresReviewsOfOtherHuariques() {
        var average = service.calculateAverage("huarique-1", List.of(
                review("1", "huarique-1", 4), review("2", "huarique-2", 1)));
        assertThat(average).hasValue(BigDecimal.valueOf(4));
    }

    @Test
    void noMatchingReviewsAlsoMeansAbsence() {
        assertThat(service.calculateAverage("huarique-1", List.of(review("1", "huarique-2", 5))))
                .isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n", "\u2003"})
    void rejectsInvalidHuariqueIdentifiers(String huariqueId) {
        assertThatIllegalArgumentException().isThrownBy(() -> service.calculateAverage(huariqueId, List.of()));
    }

    @Test
    void rejectsAMissingCollectionInsteadOfTreatingItAsNoReviews() {
        assertThatNullPointerException().isThrownBy(() -> service.calculateAverage("huarique-1", null));
    }

    @Test
    void rejectsNullEntriesInsteadOfSilentlyChangingTheAverage() {
        assertThatNullPointerException().isThrownBy(() -> service.calculateAverage("huarique-1",
                Arrays.asList(review("1", "huarique-1", 5), null)));
    }

    private Review review(String id, String huariqueId, int rating) {
        return new Review(id, "explorer-1", huariqueId, new Rating(rating), null,
                Instant.parse("2026-11-24T12:00:00Z"));
    }
}
