package com.foodly.community.interfaces.rest;

import com.foodly.community.application.commandhandlers.AddFavoriteCommandHandler;
import com.foodly.community.application.commandhandlers.RemoveFavoriteCommandHandler;
import com.foodly.community.application.commands.AddFavoriteCommand;
import com.foodly.community.application.commands.RemoveFavoriteCommand;
import com.foodly.community.application.errors.AuthenticationRequiredException;
import com.foodly.community.application.errors.FavoriteNotFoundException;
import com.foodly.community.application.queries.GetFavoritesByExplorerQuery;
import com.foodly.community.application.queryhandlers.GetFavoritesByExplorerQueryHandler;
import com.foodly.community.domain.model.Favorite;
import com.foodly.community.domain.model.FavoriteId;
import com.foodly.community.domain.repository.DuplicateFavoriteException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class FavoriteControllerTest extends RestMvcTestSupport {

    private static final String PATH = "/community/favorites/huarique-1";
    @Mock private AddFavoriteCommandHandler add;
    @Mock private RemoveFavoriteCommandHandler remove;
    @Mock private GetFavoritesByExplorerQueryHandler get;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = mvc(new FavoriteController(add, remove, get));
    }

    @Test
    void createsAndReturnsTheFavoriteResource() throws Exception {
        when(add.handle(new AddFavoriteCommand("huarique-1")))
                .thenReturn(new Favorite("trusted-explorer", "huarique-1"));
        mvc.perform(post(PATH)).andExpect(status().isCreated())
                .andExpect(content().json("{\"explorerId\":\"trusted-explorer\",\"huariqueId\":\"huarique-1\"}"));
        verify(add).handle(new AddFavoriteCommand("huarique-1"));
        verifyNoMoreInteractions(add, remove, get);
    }

    @Test
    void mapsDuplicateFavoriteTo409WithoutExposingIdentity() throws Exception {
        when(add.handle(any())).thenThrow(new DuplicateFavoriteException(new FavoriteId("private-explorer", "huarique-1")));
        mvc.perform(post(PATH)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FAVORITE_ALREADY_EXISTS"))
                .andExpect(content().string(not(containsString("private-explorer"))));
    }

    @Test
    void successfulDeletionReturns204WithoutBody() throws Exception {
        mvc.perform(delete(PATH)).andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(remove).handle(new RemoveFavoriteCommand("huarique-1"));
        verifyNoMoreInteractions(add, remove, get);
    }

    @Test
    void missingFavoriteReturns404WithoutExposingItsIdentity() throws Exception {
        doThrow(new FavoriteNotFoundException(new FavoriteId("private-explorer", "huarique-1")))
                .when(remove).handle(any());
        mvc.perform(delete(PATH)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FAVORITE_NOT_FOUND"))
                .andExpect(content().string(not(containsString("private-explorer"))));
    }

    @Test
    void getsOnlyTheCurrentExplorersFavoritesThroughTheQuery() throws Exception {
        when(get.handle(new GetFavoritesByExplorerQuery())).thenReturn(List.of(
                new Favorite("trusted-explorer", "huarique-1"), new Favorite("trusted-explorer", "huarique-2")));
        mvc.perform(get("/community/favorites/me")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].huariqueId").value("huarique-2"));
        verify(get).handle(new GetFavoritesByExplorerQuery());
        verifyNoMoreInteractions(add, remove, get);
    }

    @Test
    void noFavoritesReturnsAnEmptyArray() throws Exception {
        when(get.handle(new GetFavoritesByExplorerQuery())).thenReturn(List.of());
        mvc.perform(get("/community/favorites/me")).andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void missingIdentityReturns401() throws Exception {
        when(get.handle(any())).thenThrow(new AuthenticationRequiredException());
        mvc.perform(get("/community/favorites/me")).andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"));
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "\t"})
    void rejectsBlankIdentifiersBeforeExecutingCommands(String id) throws Exception {
        mvc.perform(post("/community/favorites/{id}", id)).andExpect(status().isBadRequest());
        mvc.perform(delete("/community/favorites/{id}", id)).andExpect(status().isBadRequest());
        verifyNoInteractions(add, remove, get);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"explorerId\":\"spoofed\"}"})
    void rejectsBodiesInsteadOfBindingClientControlledIdentity(String body) throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        mvc.perform(delete(PATH).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(add, remove, get);
    }
}
