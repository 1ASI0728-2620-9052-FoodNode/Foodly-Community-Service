package com.foodly.community.interfaces.rest;

import com.foodly.community.application.commandhandlers.CreateReviewCommandHandler;
import com.foodly.community.application.commands.CreateReviewCommand;
import com.foodly.community.application.errors.AuthenticationRequiredException;
import com.foodly.community.application.queries.GetReviewsByHuariqueQuery;
import com.foodly.community.application.queryhandlers.GetReviewsByHuariqueQueryHandler;
import com.foodly.community.domain.model.Rating;
import com.foodly.community.domain.model.Review;
import com.foodly.community.domain.model.ReviewComment;
import com.foodly.community.domain.repository.RepositoryAccessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ReviewControllerTest extends RestMvcTestSupport {

    private static final String PATH = "/community/huariques/huarique-1/reviews";
    @Mock private CreateReviewCommandHandler create;
    @Mock private GetReviewsByHuariqueQueryHandler get;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = mvc(new ReviewController(create, get));
    }

    @Test
    void createsAndSerializesAReviewResource() throws Exception {
        var command = new CreateReviewCommand("huarique-1", 5, "Excelente");
        when(create.handle(command)).thenReturn(review("review-1", 5, "Excelente"));
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5,\"comment\":\"Excelente\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reviewId").value("review-1"))
                .andExpect(jsonPath("$.explorerId").value("trusted-explorer"))
                .andExpect(jsonPath("$.huariqueId").value("huarique-1"))
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.comment").value("Excelente"))
                .andExpect(jsonPath("$.createdAt").value("2026-11-24T18:00:00Z"));
        verify(create).handle(command);
        verifyNoMoreInteractions(create, get);
    }

    @Test
    void acceptsAnAbsentComment() throws Exception {
        when(create.handle(new CreateReviewCommand("huarique-1", 4, null)))
                .thenReturn(review("review-1", 4, null));
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content("{\"rating\":4}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.comment").value(""));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"rating\":null}", "{\"rating\":0}", "{\"rating\":6}",
            "{\"rating\":-1}", "{\"rating\":1.5}", "{\"rating\":1.0}", "{\"rating\":\"5\"}",
            "{\"rating\":true}", "{\"rating\":[]}", "{\"rating\":{}}", "{\"rating\":2147483648}", "{", "null"})
    void rejectsMissingInvalidAndCoercedRatings(String body) throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(create, get);
    }

    @ParameterizedTest
    @ValueSource(strings = {"explorerId", "reviewId", "createdAt", "other"})
    void rejectsFieldsThatAreNotPartOfTheInputContract(String property) throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5,\"" + property + "\":\"spoofed-identity\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString("spoofed-identity"))));
        verifyNoInteractions(create, get);
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "\t"})
    void rejectsBlankPathIdentifiersBeforeCallingHandlers(String id) throws Exception {
        mvc.perform(post("/community/huariques/{id}/reviews", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":5}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/community/huariques/{id}/reviews", id)).andExpect(status().isBadRequest());
        verifyNoInteractions(create, get);
    }

    @Test
    void returnsAnEmptyArrayWhenNoReviewsExist() throws Exception {
        when(get.handle(new GetReviewsByHuariqueQuery("huarique-1"))).thenReturn(List.of());
        mvc.perform(get(PATH)).andExpect(status().isOk()).andExpect(content().json("[]"));
        verify(get).handle(new GetReviewsByHuariqueQuery("huarique-1"));
        verifyNoMoreInteractions(create, get);
    }

    @Test
    void returnsResourcesRatherThanDomainValueObjects() throws Exception {
        when(get.handle(new GetReviewsByHuariqueQuery("huarique-1")))
                .thenReturn(List.of(review("review-1", 4, "Bueno"), review("review-2", 5, null)));
        mvc.perform(get(PATH)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].rating").value(4))
                .andExpect(jsonPath("$[0].comment").value("Bueno"))
                .andExpect(jsonPath("$[1].comment").value(""));
        verify(get).handle(new GetReviewsByHuariqueQuery("huarique-1"));
        verifyNoMoreInteractions(create, get);
    }

    @Test
    void mapsApplicationAuthenticationFailureTo401() throws Exception {
        when(create.handle(any())).thenThrow(new AuthenticationRequiredException());
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content("{\"rating\":5}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void doesNotExposePersistenceFailureDetails() throws Exception {
        when(get.handle(any())).thenThrow(new RepositoryAccessException("private-storage-details",
                new IOException("private-cause-details")));
        mvc.perform(get(PATH)).andExpect(status().isInternalServerError())
                .andExpect(content().json("{\"status\":500,\"code\":\"INTERNAL_ERROR\",\"message\":\"Internal server error\"}"))
                .andExpect(content().string(not(containsString("private"))))
                .andExpect(jsonPath("$.cause").doesNotExist())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    void sanitizesDomainValidationMessages() throws Exception {
        when(create.handle(any())).thenThrow(new IllegalArgumentException("private-invalid-value"));
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content("{\"rating\":5}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Invalid request"))
                .andExpect(content().string(not(containsString("private"))));
    }

    @Test
    void sanitizesUnexpectedExceptions() throws Exception {
        when(get.handle(any())).thenThrow(new IllegalStateException("private-internal-details"));
        mvc.perform(get(PATH)).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(not(containsString("private"))));
    }

    @Test
    void preserves405ForUnsupportedMethodsWithoutExposingMvcDetails() throws Exception {
        mvc.perform(put(PATH)).andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
        verifyNoInteractions(create, get);
    }

    @Test
    void preserves415ForUnsupportedMediaTypes() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.TEXT_PLAIN).content("private-body"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(content().string(not(containsString("private-body"))));
        verifyNoInteractions(create, get);
    }

    @Test
    void doesNotExposeAnUnrequestedSummaryEndpoint() throws Exception {
        mvc.perform(get(PATH + "/summary")).andExpect(status().isNotFound());
        verifyNoInteractions(create, get);
    }

    private Review review(String id, int rating, String comment) {
        return new Review(id, "trusted-explorer", "huarique-1", new Rating(rating),
                new ReviewComment(comment), Instant.parse("2026-11-24T18:00:00Z"));
    }
}
