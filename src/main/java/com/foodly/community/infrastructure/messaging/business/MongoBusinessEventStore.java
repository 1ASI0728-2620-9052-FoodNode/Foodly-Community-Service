package com.foodly.community.infrastructure.messaging.business;

import com.mongodb.ReadConcern;
import com.mongodb.TransactionOptions;
import com.mongodb.WriteConcern;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

/** Inbox global y proyección comparten commit. Una entrega repetida después de commit es inocua. */
public final class MongoBusinessEventStore {
    public enum Result { APPLIED, DUPLICATE, OBSOLETE }
    private final MongoTemplate mongo;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public MongoBusinessEventStore(MongoTemplate mongo, Clock clock) {
        this.mongo = mongo;
        this.clock = clock;
        var hello = mongo.executeCommand(new org.bson.Document("hello", 1));
        if (hello.getString("setName") == null) {
            throw new IllegalStateException("Business event consumption requires MongoDB replica set transactions");
        }
        ensureCollection(KnownHuariqueDocument.class);
        ensureCollection(BusinessInboxDocument.class);
        var options = TransactionOptions.builder().readConcern(ReadConcern.SNAPSHOT).writeConcern(WriteConcern.MAJORITY).build();
        transactions = new TransactionTemplate(new MongoTransactionManager(mongo.getMongoDatabaseFactory(), options));
    }

    public Result process(BusinessEvent event) {
        return transactions.execute(status -> {
            String id = event.eventId().toString();
            String huarique = event.huariqueId().toString();
            var previous = mongo.findById(id, BusinessInboxDocument.class);
            if (previous != null) {
                if (!previous.huariqueId().equals(huarique) || previous.aggregateVersion() != event.aggregateVersion()
                        || !previous.type().equals(event.type())) throw new InvalidBusinessEventException();
                return Result.DUPLICATE;
            }
            var current = mongo.findById(huarique, KnownHuariqueDocument.class);
            var result = current != null && current.aggregateVersion() >= event.aggregateVersion() ? Result.OBSOLETE : Result.APPLIED;
            if (result == Result.APPLIED) {
                mongo.save(new KnownHuariqueDocument(huarique, event.aggregateVersion(), id, event.type(), event.occurredAt().toString()));
            }
            mongo.insert(new BusinessInboxDocument(id, huarique, event.type(), event.aggregateVersion(), result.name(), clock.instant().toString()));
            return result;
        });
    }

    private void ensureCollection(Class<?> document) {
        if (!mongo.collectionExists(document)) {
            try {
                mongo.createCollection(document);
            } catch (RuntimeException concurrentCreation) {
                if (!mongo.collectionExists(document)) throw concurrentCreation;
            }
        }
    }
}
