package com.foodly.community.infrastructure.persistence.mongo;

import com.foodly.community.domain.model.Favorite;
import com.foodly.community.domain.model.FavoriteId;
import com.foodly.community.domain.repository.DuplicateFavoriteException;
import com.foodly.community.domain.repository.IFavoriteRepository;
import com.foodly.community.domain.repository.RepositoryAccessException;
import com.foodly.community.infrastructure.persistence.mongo.document.FavoriteDocument;
import com.foodly.community.infrastructure.persistence.mongo.mapper.FavoriteDocumentMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class FavoriteRepository implements IFavoriteRepository {

    public static final String UNIQUE_PAIR_INDEX = "favorites_explorer_huarique_unique";
    private final MongoOperations mongo;

    public FavoriteRepository(MongoOperations mongo) {
        this.mongo = Objects.requireNonNull(mongo);
        try {
            // El adaptador no queda disponible si falla la garantía de unicidad.
            mongo.indexOps(FavoriteDocument.class).ensureIndex(new Index()
                    .on("explorerId", Sort.Direction.ASC).on("huariqueId", Sort.Direction.ASC)
                    .unique().named(UNIQUE_PAIR_INDEX));
        } catch (RuntimeException exception) {
            throw new RepositoryAccessException("Cannot initialize favorite indexes", exception);
        }
    }

    @Override
    public Favorite save(Favorite favorite) {
        Objects.requireNonNull(favorite, "favorite is required");
        var document = FavoriteDocumentMapper.toDocument(favorite);
        try {
            return FavoriteDocumentMapper.toDomain(mongo.insert(document));
        } catch (DuplicateKeyException exception) {
            var conflict = new DuplicateFavoriteException(favorite.getId());
            conflict.initCause(exception);
            throw conflict;
        } catch (RuntimeException exception) {
            throw new RepositoryAccessException("Cannot insert favorite", exception);
        }
    }

    @Override
    public Optional<Favorite> findById(FavoriteId id) {
        Objects.requireNonNull(id, "favoriteId is required");
        try {
            return Optional.ofNullable(mongo.findOne(byPair(id), FavoriteDocument.class))
                    .map(FavoriteDocumentMapper::toDomain);
        } catch (RuntimeException exception) {
            throw new RepositoryAccessException("Cannot read favorite", exception);
        }
    }

    @Override
    public List<Favorite> findByExplorerId(String explorerId) {
        if (explorerId == null || explorerId.isBlank()) {
            throw new IllegalArgumentException("explorerId must not be null or blank");
        }
        try {
            return mongo.find(Query.query(Criteria.where("explorerId").is(explorerId)), FavoriteDocument.class)
                    .stream().map(FavoriteDocumentMapper::toDomain).toList();
        } catch (RuntimeException exception) {
            throw new RepositoryAccessException("Cannot read favorites", exception);
        }
    }

    @Override
    public boolean deleteById(FavoriteId id) {
        Objects.requireNonNull(id, "favoriteId is required");
        try {
            return mongo.remove(byPair(id), FavoriteDocument.class).getDeletedCount() > 0;
        } catch (RuntimeException exception) {
            throw new RepositoryAccessException("Cannot delete favorite", exception);
        }
    }

    private Query byPair(FavoriteId id) {
        return Query.query(Criteria.where("explorerId").is(id.explorerId()).and("huariqueId").is(id.huariqueId()));
    }
}
