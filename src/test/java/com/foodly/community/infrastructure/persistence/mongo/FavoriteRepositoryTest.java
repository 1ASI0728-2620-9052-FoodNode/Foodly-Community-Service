package com.foodly.community.infrastructure.persistence.mongo;

import com.foodly.community.domain.model.Favorite;
import com.foodly.community.domain.model.FavoriteId;
import com.foodly.community.domain.repository.DuplicateFavoriteException;
import com.foodly.community.domain.repository.RepositoryAccessException;
import com.foodly.community.infrastructure.persistence.mongo.document.FavoriteDocument;
import com.mongodb.client.result.DeleteResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.query.Query;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FavoriteRepositoryTest {

    @Mock private MongoOperations mongo;
    @Mock private IndexOperations indexes;
    private FavoriteRepository repository;
    private final Favorite favorite = new Favorite("explorer", "huarique");

    @BeforeEach
    void setUp() {
        when(mongo.indexOps(FavoriteDocument.class)).thenReturn(indexes);
        repository = new FavoriteRepository(mongo);
    }

    @Test
    void createsAUniqueCompoundIndexBeforeUsingTheAdapter() {
        var index = ArgumentCaptor.forClass(IndexDefinition.class);
        verify(indexes).ensureIndex(index.capture());
        assertThat(index.getValue().getIndexKeys().toJson()).isEqualTo("{\"explorerId\": 1, \"huariqueId\": 1}");
        assertThat(index.getValue().getIndexOptions().getBoolean("unique")).isTrue();
    }

    @Test
    void duplicateKeyIsTranslatedWithoutChangingTheDomainExceptionType() {
        var cause = new DuplicateKeyException("duplicate pair");
        when(mongo.insert(any(FavoriteDocument.class))).thenThrow(cause);
        assertThatThrownBy(() -> repository.save(favorite)).isInstanceOf(DuplicateFavoriteException.class).hasCause(cause);
        verify(mongo, never()).findOne(any(Query.class), eq(FavoriteDocument.class));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1})
    void deletionUsesBothIdentityFieldsAndTheAcknowledgedDeletedCount(long count) {
        when(mongo.remove(any(Query.class), eq(FavoriteDocument.class))).thenReturn(DeleteResult.acknowledged(count));
        assertThat(repository.deleteById(new FavoriteId("explorer", "huarique"))).isEqualTo(count > 0);
        var query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).remove(query.capture(), eq(FavoriteDocument.class));
        assertThat(query.getValue().getQueryObject()).containsEntry("explorerId", "explorer").containsEntry("huariqueId", "huarique");
        verify(mongo, never()).findOne(any(Query.class), eq(FavoriteDocument.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"insert", "findById", "findByExplorer", "delete"})
    void technicalErrorsAreTranslatedWithoutPretendingSuccessOrAbsence(String operation) {
        var cause = new DataAccessResourceFailureException("storage unavailable");
        Runnable action;
        switch (operation) {
            case "insert" -> {
                when(mongo.insert(any(FavoriteDocument.class))).thenThrow(cause);
                action = () -> repository.save(favorite);
            }
            case "findById" -> {
                when(mongo.findOne(any(Query.class), eq(FavoriteDocument.class))).thenThrow(cause);
                action = () -> repository.findById(favorite.getId());
            }
            case "findByExplorer" -> {
                when(mongo.find(any(Query.class), eq(FavoriteDocument.class))).thenThrow(cause);
                action = () -> repository.findByExplorerId("explorer");
            }
            default -> {
                when(mongo.remove(any(Query.class), eq(FavoriteDocument.class))).thenThrow(cause);
                action = () -> repository.deleteById(favorite.getId());
            }
        }
        assertThatThrownBy(action::run).isInstanceOf(RepositoryAccessException.class).hasCause(cause);
    }

    @Test
    void indexFailurePreventsConstructionOfTheAdapter() {
        var cause = new DataAccessResourceFailureException("cannot create unique index");
        when(indexes.ensureIndex(any(IndexDefinition.class))).thenThrow(cause);
        assertThatThrownBy(() -> new FavoriteRepository(mongo)).isInstanceOf(RepositoryAccessException.class).hasCause(cause);
    }
}
