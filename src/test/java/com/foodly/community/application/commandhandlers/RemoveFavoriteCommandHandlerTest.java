package com.foodly.community.application.commandhandlers;

import com.foodly.community.application.commands.RemoveFavoriteCommand;
import com.foodly.community.application.errors.AuthenticationRequiredException;
import com.foodly.community.application.errors.FavoriteNotFoundException;
import com.foodly.community.application.ports.CurrentExplorer;
import com.foodly.community.domain.model.FavoriteId;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RemoveFavoriteCommandHandlerTest {

    private static final CurrentExplorer CURRENT = () -> Optional.of("authenticated-explorer");
    private static final FavoriteId ID = new FavoriteId("authenticated-explorer", "huarique-1");
    @Mock private IFavoriteRepository repository;

    @Test
    void deletesOnlyTheCurrentExplorersFavoriteWithoutAPreviousRead() {
        when(repository.deleteById(ID)).thenReturn(true);
        handler(CURRENT).handle(new RemoveFavoriteCommand("huarique-1"));
        verify(repository).deleteById(ID);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void absentFavoriteHasAnExplicitError() {
        when(repository.deleteById(ID)).thenReturn(false);
        assertThatThrownBy(() -> handler(CURRENT).handle(new RemoveFavoriteCommand("huarique-1")))
                .isInstanceOf(FavoriteNotFoundException.class);
        verify(repository).deleteById(ID);
        verifyNoMoreInteractions(repository);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n", "\u2003"})
    void invalidHuariqueCannotDeleteAnything(String huariqueId) {
        assertThatIllegalArgumentException().isThrownBy(() -> handler(CURRENT).handle(new RemoveFavoriteCommand(huariqueId)));
        verifyNoInteractions(repository);
    }

    @Test
    void missingIdentityCannotDeleteAnything() {
        assertThatThrownBy(() -> handler(Optional::empty).handle(new RemoveFavoriteCommand("huarique-1")))
                .isInstanceOf(AuthenticationRequiredException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void storageFailureIsNotMisreportedAsAnAbsentFavorite() {
        var failure = new RepositoryAccessException("Cannot delete favorite", new IOException("storage unavailable"));
        when(repository.deleteById(ID)).thenThrow(failure);
        assertThatThrownBy(() -> handler(CURRENT).handle(new RemoveFavoriteCommand("huarique-1"))).isSameAs(failure);
        verify(repository).deleteById(ID);
        verifyNoMoreInteractions(repository);
    }

    private RemoveFavoriteCommandHandler handler(CurrentExplorer current) {
        return new RemoveFavoriteCommandHandler(repository, current);
    }
}
