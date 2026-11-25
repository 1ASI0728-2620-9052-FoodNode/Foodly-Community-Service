package com.foodly.community.application.queryhandlers;

import com.foodly.community.application.errors.AuthenticationRequiredException;
import com.foodly.community.application.ports.CurrentExplorer;
import com.foodly.community.application.queries.GetFavoritesByExplorerQuery;
import com.foodly.community.domain.model.Favorite;
import com.foodly.community.domain.repository.IFavoriteRepository;
import com.foodly.community.domain.repository.RepositoryAccessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetFavoritesByExplorerQueryHandlerTest {

    private static final CurrentExplorer CURRENT = () -> Optional.of("authenticated-explorer");
    @Mock private IFavoriteRepository repository;

    @Test
    void onlyQueriesTheExplorerFromTrustedSecurityContext() {
        var favorite = new Favorite("authenticated-explorer", "huarique-1");
        var stored = new ArrayList<>(List.of(favorite));
        when(repository.findByExplorerId("authenticated-explorer")).thenReturn(stored);
        var result = handler(CURRENT).handle(new GetFavoritesByExplorerQuery());
        stored.clear();
        assertThat(result).containsExactly(favorite);
        assertThatThrownBy(() -> result.clear()).isInstanceOf(UnsupportedOperationException.class);
        verify(repository).findByExplorerId("authenticated-explorer");
        verifyNoMoreInteractions(repository);
    }

    @Test
    void noFavoritesReturnsAnEmptyList() {
        when(repository.findByExplorerId("authenticated-explorer")).thenReturn(List.of());
        assertThat(handler(CURRENT).handle(new GetFavoritesByExplorerQuery())).isEmpty();
        verify(repository).findByExplorerId("authenticated-explorer");
        verifyNoMoreInteractions(repository);
    }

    @Test
    void noIdentityCannotQueryAnyExplorersFavorites() {
        assertThatThrownBy(() -> handler(Optional::empty).handle(new GetFavoritesByExplorerQuery()))
                .isInstanceOf(AuthenticationRequiredException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void preservesReadFailuresRatherThanReturningEmptyFavorites() {
        var failure = new RepositoryAccessException("Cannot read favorites", new IOException("storage unavailable"));
        when(repository.findByExplorerId("authenticated-explorer")).thenThrow(failure);
        assertThatThrownBy(() -> handler(CURRENT).handle(new GetFavoritesByExplorerQuery())).isSameAs(failure);
        verify(repository).findByExplorerId("authenticated-explorer");
        verifyNoMoreInteractions(repository);
    }

    private GetFavoritesByExplorerQueryHandler handler(CurrentExplorer current) {
        return new GetFavoritesByExplorerQueryHandler(repository, current);
    }
}
