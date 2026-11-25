package com.foodly.community.application.commandhandlers;

import com.foodly.community.application.commands.AddFavoriteCommand;
import com.foodly.community.application.errors.AuthenticationRequiredException;
import com.foodly.community.application.ports.CurrentExplorer;
import com.foodly.community.domain.model.Favorite;
import com.foodly.community.domain.model.FavoriteId;
import com.foodly.community.domain.repository.DuplicateFavoriteException;
import com.foodly.community.domain.repository.IFavoriteRepository;
import com.foodly.community.domain.repository.RepositoryAccessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddFavoriteCommandHandlerTest {

    private static final CurrentExplorer CURRENT = () -> Optional.of("authenticated-explorer");
    @Mock private IFavoriteRepository repository;

    @Test
    void addsOnlyForTheAuthenticatedExplorer() {
        var expected = new Favorite("authenticated-explorer", "huarique-1");
        when(repository.save(expected)).thenReturn(expected);
        var result = handler(CURRENT).handle(new AddFavoriteCommand("huarique-1"));
        assertThat(result).isSameAs(expected);
        verify(repository).save(expected);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void preservesAtomicDuplicateDetectionFromTheRepository() {
        var failure = new DuplicateFavoriteException(new FavoriteId("authenticated-explorer", "huarique-1"));
        when(repository.save(any(Favorite.class))).thenThrow(failure);
        assertThatThrownBy(() -> handler(CURRENT).handle(new AddFavoriteCommand("huarique-1")))
                .isSameAs(failure);
        verify(repository).save(new Favorite("authenticated-explorer", "huarique-1"));
        verifyNoMoreInteractions(repository); // No check-then-insert ni reintento de escritura.
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n", "\u2003"})
    void rejectsInvalidHuariqueWithoutWriting(String huariqueId) {
        assertThatIllegalArgumentException().isThrownBy(() -> handler(CURRENT).handle(new AddFavoriteCommand(huariqueId)));
        verifyNoInteractions(repository);
    }

    @Test
    void requiresAuthentication() {
        assertThatThrownBy(() -> handler(Optional::empty).handle(new AddFavoriteCommand("huarique-1")))
                .isInstanceOf(AuthenticationRequiredException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void preservesPersistenceErrors() {
        var failure = new RepositoryAccessException("Cannot save favorite", new IOException("storage unavailable"));
        when(repository.save(any(Favorite.class))).thenThrow(failure);
        assertThatThrownBy(() -> handler(CURRENT).handle(new AddFavoriteCommand("huarique-1"))).isSameAs(failure);
        verify(repository).save(any(Favorite.class));
        verifyNoMoreInteractions(repository);
    }

    private AddFavoriteCommandHandler handler(CurrentExplorer current) {
        return new AddFavoriteCommandHandler(repository, current);
    }
}
