package com.foodly.community.infrastructure.persistence.mongo;

import com.foodly.community.domain.model.Review;
import com.foodly.community.domain.repository.IReviewRepository;
import com.foodly.community.domain.repository.RepositoryAccessException;
import com.foodly.community.infrastructure.persistence.mongo.document.ReviewDocument;
import com.foodly.community.infrastructure.persistence.mongo.mapper.ReviewDocumentMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Adaptador registrado solo por la configuración de persistencia activada. */
public final class ReviewRepository implements IReviewRepository {

    private final MongoOperations mongo;

    public ReviewRepository(MongoOperations mongo) {
        this.mongo = Objects.requireNonNull(mongo);
        try {
            mongo.indexOps(ReviewDocument.class).ensureIndex(new Index()
                    .on("huariqueId", Sort.Direction.ASC).named("reviews_huarique"));
        } catch (RuntimeException exception) {
            throw new RepositoryAccessException("Cannot initialize review indexes", exception);
        }
    }

    @Override
    public Review save(Review review) {
        Objects.requireNonNull(review, "review is required");
        var document = ReviewDocumentMapper.toDocument(review);
        try {
            return ReviewDocumentMapper.toDomain(mongo.insert(document));
        } catch (DuplicateKeyException exception) {
            throw new IllegalStateException("Review identity already exists", exception);
        } catch (RuntimeException exception) {
            throw new RepositoryAccessException("Cannot insert review", exception);
        }
    }

    @Override
    public Optional<Review> findById(String id) {
        requireIdentifier(id);
        try {
            return Optional.ofNullable(mongo.findOne(Query.query(Criteria.where("_id").is(id)), ReviewDocument.class))
                    .map(ReviewDocumentMapper::toDomain);
        } catch (RuntimeException exception) {
            throw new RepositoryAccessException("Cannot read review", exception);
        }
    }

    @Override
    public List<Review> findByHuariqueId(String huariqueId) {
        requireIdentifier(huariqueId);
        try {
            return mongo.find(Query.query(Criteria.where("huariqueId").is(huariqueId)), ReviewDocument.class)
                    .stream().map(ReviewDocumentMapper::toDomain).toList();
        } catch (RuntimeException exception) {
            throw new RepositoryAccessException("Cannot read reviews", exception);
        }
    }

    private void requireIdentifier(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("identifier must not be null or blank");
        }
    }
}
