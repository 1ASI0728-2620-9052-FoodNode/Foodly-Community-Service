package com.foodly.community.application.commandhandlers;

import com.foodly.community.application.commands.CreateReviewCommand;
import com.foodly.community.application.errors.AuthenticationRequiredException;
import com.foodly.community.application.ports.CurrentExplorer;
import com.foodly.community.application.ports.ReviewIdGenerator;
import com.foodly.community.domain.model.Review;
import com.foodly.community.domain.repository.IReviewRepository;
import com.foodly.community.domain.repository.RepositoryAccessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateReviewCommandHandlerTest {

    private static final Instant NOW = Instant.parse("2026-11-24T18:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final CurrentExplorer CURRENT = () -> Optional.of("authenticated-explorer");

    @Mock private IReviewRepository repository;
    @Mock private ReviewIdGenerator ids;

    @Test
    void createsUsingTrustedIdentityGeneratedIdAndInjectedTime() {
        when(ids.nextId()).thenReturn("opaque-review-id");
        when(repository.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = handler(CURRENT).handle(new CreateReviewCommand("huarique-1", 5, "Muy bueno"));

        var saved = ArgumentCaptor.forClass(Review.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue()).isSameAs(result);
        assertThat(result.getExplorerId()).isEqualTo("authenticated-explorer");
        assertThat(result.getReviewId()).isEqualTo("opaque-review-id");
        assertThat(result.getHuariqueId()).isEqualTo("huarique-1");
        assertThat(result.getRating().value()).isEqualTo(5);
        assertThat(result.getComment().value()).isEqualTo("Muy bueno");
        assertThat(result.getCreatedAt()).isEqualTo(NOW);
        verify(ids).nextId();
        verifyNoMoreInteractions(repository, ids);
    }

    @Test
    void preservesOptionalCommentSemantics() {
        when(ids.nextId()).thenReturn("review-1");
        when(repository.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var result = handler(CURRENT).handle(new CreateReviewCommand("huarique-1", 4, null));
        assertThat(result.getComment().value()).isEmpty();
        verify(repository).save(result);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 6, Integer.MAX_VALUE})
    void rejectsInvalidRatingsBeforeGeneratingOrSaving(int rating) {
        assertThatIllegalArgumentException().isThrownBy(() ->
                handler(CURRENT).handle(new CreateReviewCommand("huarique-1", rating, null)));
        verifyNoInteractions(repository, ids);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n", "\u2003"})
    void delegatesInvalidHuariqueRejectionToDomain(String huariqueId) {
        when(ids.nextId()).thenReturn("review-1");
        assertThatIllegalArgumentException().isThrownBy(() ->
                handler(CURRENT).handle(new CreateReviewCommand(huariqueId, 4, null)));
        verifyNoInteractions(repository);
    }

    @Test
    void doesNotAcceptAnInvalidGeneratedIdentity() {
        when(ids.nextId()).thenReturn(" ");
        assertThatIllegalArgumentException().isThrownBy(() ->
                handler(CURRENT).handle(new CreateReviewCommand("huarique-1", 4, null)));
        verifyNoInteractions(repository);
    }

    @Test
    void missingAuthenticationCannotCreateAReview() {
        assertThatThrownBy(() -> handler(Optional::empty)
                .handle(new CreateReviewCommand("huarique-1", 4, null)))
                .isInstanceOf(AuthenticationRequiredException.class);
        verifyNoInteractions(repository, ids);
    }

    @Test
    void propagatesPersistenceFailureWithoutHidingItsCause() {
        var cause = new IOException("storage unavailable");
        var failure = new RepositoryAccessException("Cannot save review", cause);
        when(ids.nextId()).thenReturn("review-1");
        when(repository.save(any(Review.class))).thenThrow(failure);
        assertThatThrownBy(() -> handler(CURRENT).handle(new CreateReviewCommand("huarique-1", 4, null)))
                .isSameAs(failure).hasCause(cause);
        verify(repository).save(any(Review.class));
        verifyNoMoreInteractions(repository);
    }

    @Test
    void preservesExistingReviewIdentityConflict() {
        var conflict = new IllegalStateException("review identity already exists");
        when(ids.nextId()).thenReturn("review-1");
        when(repository.save(any(Review.class))).thenThrow(conflict);
        assertThatThrownBy(() -> handler(CURRENT).handle(new CreateReviewCommand("huarique-1", 4, null)))
                .isSameAs(conflict);
    }

    private CreateReviewCommandHandler handler(CurrentExplorer current) {
        return new CreateReviewCommandHandler(repository, current, ids, CLOCK);
    }
}
