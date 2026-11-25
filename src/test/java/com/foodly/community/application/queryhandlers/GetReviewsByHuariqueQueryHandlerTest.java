package com.foodly.community.application.queryhandlers;

import com.foodly.community.application.queries.GetReviewsByHuariqueQuery;
import com.foodly.community.domain.model.Rating;
import com.foodly.community.domain.model.Review;
import com.foodly.community.domain.repository.IReviewRepository;
import com.foodly.community.domain.repository.RepositoryAccessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetReviewsByHuariqueQueryHandlerTest {

    @Mock private IReviewRepository repository;

    @Test
    void queriesTheRequestedOpaqueHuariqueIdAndReturnsAnImmutableSnapshot() {
        var review = new Review("review-1", "explorer-1", "opaque:huarique", new Rating(5), null, Instant.EPOCH);
        var stored = new ArrayList<>(List.of(review));
        when(repository.findByHuariqueId("opaque:huarique")).thenReturn(stored);
        var result = handler().handle(new GetReviewsByHuariqueQuery("opaque:huarique"));
        stored.clear();
        assertThat(result).containsExactly(review);
        assertThatThrownBy(() -> result.clear()).isInstanceOf(UnsupportedOperationException.class);
        verify(repository).findByHuariqueId("opaque:huarique");
        verifyNoMoreInteractions(repository);
    }

    @Test
    void noReviewsReturnsAnEmptyList() {
        when(repository.findByHuariqueId("huarique-1")).thenReturn(List.of());
        assertThat(handler().handle(new GetReviewsByHuariqueQuery("huarique-1"))).isEmpty();
        verify(repository).findByHuariqueId("huarique-1");
        verifyNoMoreInteractions(repository);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n", "\u2003"})
    void invalidIdentifierNeverReachesPersistence(String huariqueId) {
        assertThatIllegalArgumentException().isThrownBy(() -> handler().handle(new GetReviewsByHuariqueQuery(huariqueId)));
        verifyNoInteractions(repository);
    }

    @Test
    void storageFailureIsNotTreatedAsAnEmptyList() {
        var failure = new RepositoryAccessException("Cannot read reviews", new IOException("storage unavailable"));
        when(repository.findByHuariqueId("huarique-1")).thenThrow(failure);
        assertThatThrownBy(() -> handler().handle(new GetReviewsByHuariqueQuery("huarique-1"))).isSameAs(failure);
        verify(repository).findByHuariqueId("huarique-1");
        verifyNoMoreInteractions(repository);
    }

    private GetReviewsByHuariqueQueryHandler handler() {
        return new GetReviewsByHuariqueQueryHandler(repository);
    }
}
