package com.foodly.community.interfaces.rest;

import com.foodly.community.application.commandhandlers.AddFavoriteCommandHandler;
import com.foodly.community.application.commandhandlers.CreateReviewCommandHandler;
import com.foodly.community.application.commandhandlers.RemoveFavoriteCommandHandler;
import com.foodly.community.application.ports.CurrentExplorer;
import com.foodly.community.application.queryhandlers.GetFavoritesByExplorerQueryHandler;
import com.foodly.community.application.queryhandlers.GetReviewsByHuariqueQueryHandler;
import com.foodly.community.domain.model.Favorite;
import com.foodly.community.domain.model.FavoriteId;
import com.foodly.community.domain.model.Review;
import com.foodly.community.domain.repository.IFavoriteRepository;
import com.foodly.community.domain.repository.IReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Controladores y handlers reales, repositorios/identidad mock; no integra IAM ni filtros JWT. */
@ExtendWith(MockitoExtension.class)
class RestIdentityBoundaryTest extends RestMvcTestSupport {

    private static final String EXPLORER = "trusted-explorer";
    @Mock private IReviewRepository reviews;
    @Mock private IFavoriteRepository favorites;
    @Mock(answer = Answers.CALLS_REAL_METHODS) private CurrentExplorer current;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var clock = Clock.fixed(Instant.parse("2026-11-24T18:00:00Z"), ZoneOffset.UTC);
        mvc = mvc(new ReviewController(new CreateReviewCommandHandler(reviews, current, () -> "server-review-id", clock),
                        new GetReviewsByHuariqueQueryHandler(reviews)),
                new FavoriteController(new AddFavoriteCommandHandler(favorites, current),
                        new RemoveFavoriteCommandHandler(favorites, current),
                        new GetFavoritesByExplorerQueryHandler(favorites, current)));
    }

    @Test
    void reviewIdentityComesOnlyFromTheTrustedPortNotHeadersOrQuery() throws Exception {
        when(current.explorerId()).thenReturn(Optional.of(EXPLORER));
        when(reviews.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));
        mvc.perform(post("/community/huariques/huarique-1/reviews")
                        .param("explorerId", "attacker-selected-explorer")
                        .header("X-Explorer-Id", "attacker-selected-explorer")
                        .header("Authorization", "Bearer unvalidated-test-value")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.explorerId").value(EXPLORER))
                .andExpect(jsonPath("$.reviewId").value("server-review-id"));
        var saved = ArgumentCaptor.forClass(Review.class);
        verify(reviews).save(saved.capture());
        assertThat(saved.getValue().getExplorerId()).isEqualTo(EXPLORER);
        verify(current).explorerId();
        verifyNoMoreInteractions(reviews);
        verifyNoInteractions(favorites);
    }

    @Test
    void favoriteIdentityCannotBeSelectedThroughHeadersOrQuery() throws Exception {
        when(current.explorerId()).thenReturn(Optional.of(EXPLORER));
        var expected = new Favorite(EXPLORER, "huarique-1");
        when(favorites.save(expected)).thenReturn(expected);
        mvc.perform(post("/community/favorites/huarique-1").param("explorerId", "attacker")
                        .header("X-Explorer-Id", "attacker"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.explorerId").value(EXPLORER));
        verify(favorites).save(expected);
        verifyNoMoreInteractions(favorites);
        verifyNoInteractions(reviews);
    }

    @Test
    void deletionCannotTargetAnotherExplorersPair() throws Exception {
        when(current.explorerId()).thenReturn(Optional.of(EXPLORER));
        var expected = new FavoriteId(EXPLORER, "huarique-1");
        when(favorites.deleteById(expected)).thenReturn(true);
        mvc.perform(delete("/community/favorites/huarique-1").param("explorerId", "attacker")
                        .header("X-Explorer-Id", "attacker"))
                .andExpect(status().isNoContent());
        verify(favorites).deleteById(expected);
        verifyNoMoreInteractions(favorites);
        verifyNoInteractions(reviews);
    }

    @Test
    void meCannotSelectAnotherExplorersFavorites() throws Exception {
        when(current.explorerId()).thenReturn(Optional.of(EXPLORER));
        when(favorites.findByExplorerId(EXPLORER)).thenReturn(List.of(new Favorite(EXPLORER, "huarique-1")));
        mvc.perform(get("/community/favorites/me").param("explorerId", "attacker")
                        .header("X-Explorer-Id", "attacker"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].explorerId").value(EXPLORER));
        verify(favorites).findByExplorerId(EXPLORER);
        verifyNoMoreInteractions(favorites);
        verifyNoInteractions(reviews);
    }

    @ParameterizedTest
    @MethodSource("identityRequiredRequests")
    void noTrustedIdentityMeans401WithoutRepositoryAccess(HttpMethod method, String path) throws Exception {
        when(current.explorerId()).thenReturn(Optional.empty());
        mvc.perform(requestFor(method, path).header("X-Explorer-Id", "attacker")
                        .header("Authorization", "Bearer unvalidated-test-value"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        verifyNoInteractions(reviews, favorites);
    }

    @Test
    void spoofedBodyIsRejectedBeforeCallingTheTrustedIdentityPort() throws Exception {
        mvc.perform(post("/community/huariques/huarique-1/reviews").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5,\"explorerId\":\"attacker\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/community/favorites/huarique-1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"explorerId\":\"attacker\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(current, reviews, favorites);
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> identityRequiredRequests() {
        return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of(HttpMethod.POST, "/community/huariques/huarique-1/reviews"),
                org.junit.jupiter.params.provider.Arguments.of(HttpMethod.POST, "/community/favorites/huarique-1"),
                org.junit.jupiter.params.provider.Arguments.of(HttpMethod.DELETE, "/community/favorites/huarique-1"),
                org.junit.jupiter.params.provider.Arguments.of(HttpMethod.GET, "/community/favorites/me"));
    }

    private MockHttpServletRequestBuilder requestFor(HttpMethod method, String path) {
        var request = request(method, path);
        if (path.contains("/reviews")) {
            request.contentType(MediaType.APPLICATION_JSON).content("{\"rating\":5}");
        }
        return request;
    }
}
