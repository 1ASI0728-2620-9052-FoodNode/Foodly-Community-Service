package com.foodly.community.infrastructure.persistence.mongo;

import com.foodly.community.domain.model.Favorite;
import com.foodly.community.domain.model.FavoriteId;
import com.foodly.community.domain.model.Rating;
import com.foodly.community.domain.model.Review;
import com.foodly.community.domain.model.ReviewComment;
import com.foodly.community.domain.repository.DuplicateFavoriteException;
import com.foodly.community.domain.repository.IFavoriteRepository;
import com.foodly.community.domain.repository.IReviewRepository;
import com.foodly.community.domain.repository.RepositoryAccessException;
import com.foodly.community.domain.repository.FavoriteRepositoryContract;
import com.foodly.community.infrastructure.config.MongoPersistenceConfiguration;
import com.foodly.community.infrastructure.persistence.mongo.document.ReviewDocument;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;

/** MongoDB real y efímero: reutiliza también las cinco pruebas del contrato de favoritos. */
class MongoPersistenceIntegrationTest extends FavoriteRepositoryContract {

    @TempDir static Path directory;
    private static TemporaryMongoServer server;
    private static MongoClient client;
    private MongoTemplate mongo;
    private ReviewRepository reviews;
    private FavoriteRepository favorites;

    @BeforeAll
    static void startMongo() throws Exception {
        server = TemporaryMongoServer.start(directory);
        try {
            client = MongoClients.create(server.uri());
            var version = client.getDatabase("foodly-community").runCommand(new Document("buildInfo", 1)).getString("version");
            Files.writeString(Path.of("target/mongo-integration-runtime.txt"), server.runtime() + "\nMongoDB " + version + "\n");
        } catch (Exception failure) {
            stopMongo();
            throw failure;
        }
    }

    @AfterAll
    static void stopMongo() throws Exception {
        if (client != null) {
            client.close();
        }
        if (server != null) {
            server.close();
        }
    }

    @Override
    protected IFavoriteRepository newEmptyRepository() {
        mongo = new MongoTemplate(client, "foodly-community-it-" + UUID.randomUUID());
        reviews = new ReviewRepository(mongo);
        favorites = new FavoriteRepository(mongo);
        return favorites;
    }

    @AfterEach
    void dropOnlyTheNewTestDatabase() {
        if (mongo != null) {
            if (!mongo.getDb().getName().startsWith("foodly-community-it-")) {
                throw new IllegalStateException("Refusing to drop a database not created by this test");
            }
            mongo.getDb().drop();
        }
    }

    @Test
    void reviewRoundTripPreservesEveryFieldIncludingNanosecondsAndOpaqueStringIdentity() {
        String id = "507f1f77bcf86cd799439011";
        var original = new Review(id, "explorer-1", "huarique-1", new Rating(5),
                new ReviewComment("  Buen sabor\n"), Instant.parse("2026-11-24T18:00:00.123456789Z"));
        reviews.save(original);
        var loaded = reviews.findById(id).orElseThrow();
        assertThat(loaded.getReviewId()).isEqualTo(id);
        assertThat(loaded.getExplorerId()).isEqualTo(original.getExplorerId());
        assertThat(loaded.getHuariqueId()).isEqualTo(original.getHuariqueId());
        assertThat(loaded.getRating()).isEqualTo(original.getRating());
        assertThat(loaded.getComment()).isEqualTo(original.getComment());
        assertThat(loaded.getCreatedAt()).isEqualTo(original.getCreatedAt());
        var raw = mongo.getCollection("reviews").find(new Document("_id", id)).first();
        assertThat(raw).isNotNull();
        assertThat(raw.get("_id")).isInstanceOf(String.class);
        assertThat(raw.getString("createdAt")).isEqualTo(original.getCreatedAt().toString());
    }

    @Test
    void queriesOnlyTheRequestedHuariquesReviews() {
        reviews.save(review("one", "huarique-1", 3));
        reviews.save(review("two", "huarique-1", 5));
        reviews.save(review("other", "huarique-2", 1));
        assertThat(reviews.findByHuariqueId("huarique-1")).extracting(Review::getReviewId)
                .containsExactlyInAnyOrder("one", "two");
        assertThat(reviews.findByHuariqueId("missing")).isEmpty();
        assertThat(reviews.findById("missing")).isEmpty();
    }

    @Test
    void duplicateReviewIdentityCannotOverwriteTheOriginal() {
        reviews.save(review("same", "huarique-1", 3));
        assertThatThrownBy(() -> reviews.save(review("same", "huarique-1", 5)))
                .isInstanceOf(IllegalStateException.class).isNotInstanceOf(RepositoryAccessException.class);
        assertThat(reviews.findById("same").orElseThrow().getRating()).isEqualTo(new Rating(3));
    }

    @Test
    void deletesAnExistingPairAndReturnsFalseWhenItNoLongerExists() {
        var target = new Favorite("explorer-1", "huarique-1");
        var other = new Favorite("explorer-2", "huarique-1");
        favorites.save(target);
        favorites.save(other);
        assertThat(favorites.deleteById(target.getId())).isTrue();
        assertThat(favorites.deleteById(target.getId())).isFalse();
        assertThat(favorites.findById(target.getId())).isEmpty();
        assertThat(favorites.findById(other.getId())).contains(other);
    }

    @Test
    void theCompoundIndexIsUniqueAndProtectsEvenDirectDriverWrites() {
        var indexes = mongo.getCollection("favorites").listIndexes().into(new ArrayList<>());
        var index = indexes.stream().filter(item -> FavoriteRepository.UNIQUE_PAIR_INDEX.equals(item.getString("name")))
                .findFirst().orElseThrow();
        assertThat(index.getBoolean("unique")).isTrue();
        assertThat(index.get("key", Document.class)).isEqualTo(new Document("explorerId", 1).append("huariqueId", 1));
        var raw = new Document("explorerId", "explorer-1").append("huariqueId", "huarique-1");
        mongo.getCollection("favorites").insertOne(raw);
        assertThatThrownBy(() -> mongo.getCollection("favorites").insertOne(
                new Document("explorerId", "explorer-1").append("huariqueId", "huarique-1")))
                .isInstanceOf(MongoWriteException.class)
                .satisfies(failure -> assertThat(((MongoWriteException) failure).getError().getCode()).isEqualTo(11000));
    }

    @Test
    void concurrentInsertionsThroughTwoAdaptersCreateExactlyOneFavorite() throws Exception {
        var secondAdapter = new FavoriteRepository(mongo);
        var sequence = new AtomicInteger();
        var result = concurrently(8, () -> {
            var adapter = sequence.getAndIncrement() % 2 == 0 ? favorites : secondAdapter;
            try {
                adapter.save(new Favorite("explorer-1", "huarique-1"));
                return true;
            } catch (DuplicateFavoriteException expected) {
                return false;
            }
        });
        assertThat(result).filteredOn(Boolean::booleanValue).hasSize(1);
        assertThat(result).filteredOn(value -> !value).hasSize(7);
        assertThat(favorites.findByExplorerId("explorer-1")).hasSize(1);
    }

    @Test
    void concurrentDeletionReportsExactlyOneSuccessfulRemoval() throws Exception {
        var favorite = new Favorite("explorer-1", "huarique-1");
        favorites.save(favorite);
        assertThat(concurrently(2, () -> favorites.deleteById(favorite.getId())))
                .containsExactlyInAnyOrder(true, false);
    }

    @Test
    void invalidStoredReviewCannotBecomeAnInvalidDomainAggregate() {
        mongo.insert(new ReviewDocument("broken", "explorer-1", "huarique-1", 6, "", Instant.EPOCH.toString()));
        assertThatThrownBy(() -> reviews.findById("broken"))
                .isInstanceOf(RepositoryAccessException.class).hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void uniqueIndexInitializationFailsIfStoredDataAlreadyViolatesIt() {
        mongo.indexOps("favorites").dropIndex(FavoriteRepository.UNIQUE_PAIR_INDEX);
        mongo.getCollection("favorites").insertMany(List.of(
                new Document("explorerId", "explorer-1").append("huariqueId", "huarique-1"),
                new Document("explorerId", "explorer-1").append("huariqueId", "huarique-1")));
        assertThatThrownBy(() -> new FavoriteRepository(mongo)).isInstanceOf(RepositoryAccessException.class);
    }

    @Test
    void explicitConfigurationActivatesOnlyPersistenceAgainstTheTemporaryServer() {
        new ApplicationContextRunner().withUserConfiguration(MongoPersistenceConfiguration.class)
                .withPropertyValues("community.persistence.mongodb.enabled=true",
                        "spring.data.mongodb.uri=" + server.uri(), "spring.data.mongodb.database=foodly-community")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(IReviewRepository.class)
                            .hasSingleBean(IFavoriteRepository.class).hasSingleBean(MongoClient.class);
                    assertThat(context.getBean(IFavoriteRepository.class).findByExplorerId("absent")).isEmpty();
                });
    }

    private Review review(String id, String huarique, int rating) {
        return new Review(id, "explorer-1", huarique, new Rating(rating), null, Instant.EPOCH);
    }

    private <T> List<T> concurrently(int count, Supplier<T> action) throws Exception {
        var executor = Executors.newFixedThreadPool(count);
        var ready = new CountDownLatch(count);
        var start = new CountDownLatch(1);
        try {
            var futures = new ArrayList<java.util.concurrent.Future<T>>();
            for (int index = 0; index < count; index++) {
                futures.add(executor.submit((Callable<T>) () -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting for concurrent test start");
                    }
                    return action.get();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var results = new ArrayList<T>();
            for (var future : futures) {
                results.add(future.get(15, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }
}
