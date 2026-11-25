package com.foodly.community.application.queryhandlers;

import com.foodly.community.application.queries.GetReviewSummaryByHuariqueQuery;
import com.foodly.community.domain.model.Rating;
import com.foodly.community.domain.model.Review;
import com.foodly.community.domain.repository.IReviewRepository;
import com.foodly.community.domain.repository.RepositoryAccessException;
import com.foodly.community.domain.service.ReputationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetReviewSummaryByHuariqueQueryHandlerTest {

    @Mock private IReviewRepository repository;

    @Test
    void calculatesCountAndAverageUsingTheDomainService() {
        var reviews = List.of(review("1", 4), review("2", 5));
        when(repository.findByHuariqueId("huarique-1")).thenReturn(reviews);
        var reputation = spy(new ReputationService());
        var result = new GetReviewSummaryByHuariqueQueryHandler(repository, reputation)
                .handle(new GetReviewSummaryByHuariqueQuery("huarique-1"));
        assertThat(result.huariqueId()).isEqualTo("huarique-1");
        assertThat(result.reviewCount()).isEqualTo(2);
        assertThat(result.averageRating()).hasValue(new BigDecimal("4.5"));
        verify(reputation).calculateAverage("huarique-1", reviews);
        verify(repository).findByHuariqueId("huarique-1");
        verifyNoMoreInteractions(repository, reputation);
    }

    @Test
    void noReviewsHasExplicitZeroCountAndAbsentAverage() {
        when(repository.findByHuariqueId("huarique-1")).thenReturn(List.of());
        var result = handler().handle(new GetReviewSummaryByHuariqueQuery("huarique-1"));
        assertThat(result.reviewCount()).isZero();
        assertThat(result.averageRating()).isEmpty();
        verify(repository).findByHuariqueId("huarique-1");
        verifyNoMoreInteractions(repository);
    }

    @Test
    void doesNotApplyAnAdditionalPresentationRounding() {
        when(repository.findByHuariqueId("huarique-1")).thenReturn(List.of(review("1", 1), review("2", 2), review("3", 2)));
        var result = handler().handle(new GetReviewSummaryByHuariqueQuery("huarique-1"));
        assertThat(result.averageRating()).hasValue(new BigDecimal("1.666666666666666666666666666666667"));
        assertThat(result.reviewCount()).isEqualTo(3);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n", "\u2003"})
    void invalidIdentifierDoesNotReachRepository(String huariqueId) {
        assertThatIllegalArgumentException().isThrownBy(() -> handler().handle(new GetReviewSummaryByHuariqueQuery(huariqueId)));
        verifyNoInteractions(repository);
    }

    @Test
    void storageErrorIsNotMistakenForNoReviews() {
        var failure = new RepositoryAccessException("Cannot read summary", new IOException("storage unavailable"));
        when(repository.findByHuariqueId("huarique-1")).thenThrow(failure);
        assertThatThrownBy(() -> handler().handle(new GetReviewSummaryByHuariqueQuery("huarique-1"))).isSameAs(failure);
        verify(repository).findByHuariqueId("huarique-1");
        verifyNoMoreInteractions(repository);
    }

    private GetReviewSummaryByHuariqueQueryHandler handler() {
        return new GetReviewSummaryByHuariqueQueryHandler(repository, new ReputationService());
    }

    private Review review(String id, int rating) {
        return new Review(id, "explorer-1", "huarique-1", new Rating(rating), null, Instant.EPOCH);
    }
}
