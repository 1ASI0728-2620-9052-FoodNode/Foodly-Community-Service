package com.foodly.community.infrastructure.persistence.mongo;

import com.foodly.community.domain.model.Rating;
import com.foodly.community.domain.model.Review;
import com.foodly.community.domain.repository.RepositoryAccessException;
import com.foodly.community.infrastructure.persistence.mongo.document.ReviewDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewRepositoryTest {

    @Mock private MongoOperations mongo;
    @Mock private IndexOperations indexes;
    private ReviewRepository repository;

    @BeforeEach
    void setUp() {
        when(mongo.indexOps(ReviewDocument.class)).thenReturn(indexes);
        repository = new ReviewRepository(mongo);
    }

    @Test
    void saveUsesInsertRatherThanAnOverwritingUpsert() {
        when(mongo.insert(any(ReviewDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var review = review();
        assertThat(repository.save(review).getRating()).isEqualTo(review.getRating());
        verify(mongo).insert(any(ReviewDocument.class));
        verify(mongo, never()).save(any(ReviewDocument.class));
    }

    @Test
    void duplicateKeyPreservesTheExistingReviewIdentityConflictContract() {
        var cause = new DuplicateKeyException("duplicate");
        when(mongo.insert(any(ReviewDocument.class))).thenThrow(cause);
        assertThatThrownBy(() -> repository.save(review())).isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(RepositoryAccessException.class).hasCause(cause);
    }

    @ParameterizedTest
    @ValueSource(strings = {"insert", "findById", "findByHuarique"})
    void technicalErrorsAreTranslatedAndPreserveTheirCause(String operation) {
        var cause = new DataAccessResourceFailureException("storage unavailable");
        Runnable action;
        switch (operation) {
            case "insert" -> {
                when(mongo.insert(any(ReviewDocument.class))).thenThrow(cause);
                action = () -> repository.save(review());
            }
            case "findById" -> {
                when(mongo.findOne(any(Query.class), eq(ReviewDocument.class))).thenThrow(cause);
                action = () -> repository.findById("review");
            }
            default -> {
                when(mongo.find(any(Query.class), eq(ReviewDocument.class))).thenThrow(cause);
                action = () -> repository.findByHuariqueId("huarique");
            }
        }
        assertThatThrownBy(action::run).isInstanceOf(RepositoryAccessException.class).hasCause(cause);
    }

    @Test
    void anIndexCreationFailureDoesNotLeaveAnAvailableAdapter() {
        var cause = new DataAccessResourceFailureException("cannot create index");
        when(indexes.ensureIndex(any(IndexDefinition.class))).thenThrow(cause);
        assertThatThrownBy(() -> new ReviewRepository(mongo)).isInstanceOf(RepositoryAccessException.class).hasCause(cause);
    }

    private Review review() {
        return new Review("review", "explorer", "huarique", new Rating(4), null, Instant.EPOCH);
    }
}
