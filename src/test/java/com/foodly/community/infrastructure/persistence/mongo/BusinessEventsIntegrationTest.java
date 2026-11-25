package com.foodly.community.infrastructure.persistence.mongo;

import com.foodly.community.infrastructure.config.BusinessEventsConfiguration;
import com.foodly.community.infrastructure.messaging.business.*;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.support.DefaultMessagePropertiesConverter;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.foodly.community.infrastructure.persistence.mongo.BusinessEventTestData.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BusinessEventsIntegrationTest {
    @TempDir static Path temporary;
    TemporaryMongoServer mongoServer;
    TemporaryRabbitServer rabbitServer;
    MongoClient client;
    MongoTemplate mongo;
    MongoBusinessEventStore store;
    CachingConnectionFactory connections;
    final BusinessEventsProperties settings = new BusinessEventsProperties(3, 10);

    @BeforeAll void start() throws Exception {
        mongoServer = TemporaryMongoServer.startReplicaSet(Files.createDirectory(temporary.resolve("mongo")));
        client = MongoClients.create(mongoServer.uri());
        mongo = new MongoTemplate(client, "foodly-community");
        store = new MongoBusinessEventStore(mongo, Clock.systemUTC());
        rabbitServer = TemporaryRabbitServer.start(Files.createDirectory(temporary.resolve("rabbit")));
        connections = new CachingConnectionFactory(rabbitServer.factory());
        try (var connection = rabbitServer.factory().newConnection(); var channel = connection.createChannel()) {
            // Únicamente el productor de prueba declara el exchange Business en el broker temporal.
            channel.exchangeDeclare(BusinessTopology.EXCHANGE, "topic", true);
        }
        new BusinessTopology(connections, settings);
        System.out.println("BUSINESS EVENTS ISOLATION: " + mongoServer.runtime() + "; real RabbitMQ, no external credentials");
    }

    @BeforeEach void reset() throws Exception {
        mongo.remove(new org.springframework.data.mongodb.core.query.Query(), BusinessInboxDocument.class);
        mongo.remove(new org.springframework.data.mongodb.core.query.Query(), KnownHuariqueDocument.class);
        try (var connection = rabbitServer.factory().newConnection(); var channel = connection.createChannel()) {
            channel.queuePurge(BusinessTopology.QUEUE);
            channel.queuePurge(BusinessTopology.DLQ);
        }
    }

    @AfterAll void stop() throws Exception {
        if (connections != null) connections.destroy();
        if (rabbitServer != null) rabbitServer.close();
        if (client != null) client.close();
        if (mongoServer != null) mongoServer.close();
    }

    @Test void realDeliveryCommitsBeforeAck() throws Exception {
        var event = event(0);
        try (var connection = rabbitServer.factory().newConnection(); var channel = connection.createChannel()) {
            publish(channel, message(event));
            Message received = receive(channel, BusinessTopology.QUEUE);
            var observed = spy(channel);
            doAnswer(call -> {
                assertNotNull(mongo.findById(event.eventId().toString(), BusinessInboxDocument.class));
                assertNotNull(mongo.findById(event.huariqueId().toString(), KnownHuariqueDocument.class));
                channel.basicAck(call.getArgument(0), call.getArgument(1));
                return null;
            }).when(observed).basicAck(anyLong(), anyBoolean());
            listener(store).onMessage(received, observed);
            assertNull(channel.basicGet(BusinessTopology.QUEUE, false));
        }
    }

    @Test void duplicatesSurviveStoreRecreation() {
        var event = event(0);
        assertEquals(MongoBusinessEventStore.Result.APPLIED, store.process(event));
        var restarted = new MongoBusinessEventStore(new MongoTemplate(client, "foodly-community"), Clock.systemUTC());
        assertEquals(MongoBusinessEventStore.Result.DUPLICATE, restarted.process(event));
        assertEquals(1, mongo.getCollection("community_business_inbox").countDocuments());
        assertEquals(1, mongo.getCollection("community_known_huariques").countDocuments());
    }

    @Test void outOfOrderFullSnapshotDoesNotRequireRegistrationFirst() {
        var initial = event(0);
        var newest = version(initial, 8);
        assertEquals(MongoBusinessEventStore.Result.APPLIED, store.process(newest));
        assertEquals(MongoBusinessEventStore.Result.OBSOLETE, store.process(initial));
        assertEquals(MongoBusinessEventStore.Result.OBSOLETE, store.process(version(initial, 7)));
        assertEquals(8, mongo.findById(initial.huariqueId().toString(), KnownHuariqueDocument.class).aggregateVersion());
        assertEquals(3, mongo.getCollection("community_business_inbox").countDocuments());
    }

    @Test void sameVersionCannotOverwriteProjection() {
        var event = event(3);
        store.process(event);
        assertEquals(MongoBusinessEventStore.Result.OBSOLETE, store.process(version(event, 3)));
        assertEquals(event.eventId().toString(), mongo.findById(event.huariqueId().toString(), KnownHuariqueDocument.class).lastEventId());
    }

    @Test void eventIdCollisionIsPermanent() {
        var event = event(0);
        store.process(event);
        assertThrows(InvalidBusinessEventException.class, () -> store.process(new BusinessEvent(event.eventId(), event.type(), 1,
                java.util.UUID.randomUUID(), 0, event.occurredAt())));
    }

    @Test void inboxFailureRollsBackProjection() {
        var failingMongo = spy(mongo);
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("simulated insert failure"))
                .when(failingMongo).insert(any(BusinessInboxDocument.class));
        var failingStore = new MongoBusinessEventStore(failingMongo, Clock.systemUTC());
        var event = event(0);
        assertThrows(org.springframework.dao.DataAccessResourceFailureException.class, () -> failingStore.process(event));
        assertNull(mongo.findById(event.huariqueId().toString(), KnownHuariqueDocument.class));
        assertNull(mongo.findById(event.eventId().toString(), BusinessInboxDocument.class));
        assertEquals(MongoBusinessEventStore.Result.APPLIED, store.process(event));
    }

    @Test void concurrentDuplicatesLeaveOneInboxAndProjection() throws Exception {
        var event = event(0);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var gate = new java.util.concurrent.CountDownLatch(1);
            var jobs = java.util.stream.IntStream.range(0, 2).mapToObj(i -> executor.submit(() -> {
                gate.await();
                for (int attempt = 0; attempt < 5; attempt++) {
                    try { return store.process(event); }
                    catch (org.springframework.dao.DataAccessException | org.springframework.transaction.TransactionException transientConflict) {
                        if (attempt == 4) throw transientConflict;
                        Thread.sleep(50);
                    }
                }
                throw new AssertionError();
            })).toList();
            gate.countDown();
            for (var job : jobs) assertNotNull(job.get(10, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1, mongo.getCollection("community_business_inbox").countDocuments());
            assertEquals(1, mongo.getCollection("community_known_huariques").countDocuments());
        } finally { executor.shutdownNow(); }
    }

    @Test void schemaIncompatibleGoesToRealDlq() throws Exception {
        var e = event(0);
        deadLetters(message(new BusinessEvent(e.eventId(), e.type(), 2, e.huariqueId(), 0, e.occurredAt())));
    }

    @Test void malformedJsonGoesToRealDlq() throws Exception {
        var message = message(event(0));
        deadLetters(new Message("{".getBytes(), message.getMessageProperties()));
    }

    @Test void failuresUseBrokerCountAndExhaustIntoDlq() throws Exception {
        var failing = mock(MongoBusinessEventStore.class);
        when(failing.process(any())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("offline test"));
        try (var connection = rabbitServer.factory().newConnection(); var channel = connection.createChannel()) {
            publish(channel, message(event(0)));
            for (int attempt = 0; attempt < 3; attempt++) {
                var received = receive(channel, BusinessTopology.QUEUE);
                Number count = (Number) received.getMessageProperties().getHeader("x-delivery-count");
                assertEquals(attempt, count == null ? 0 : count.intValue());
                listener(failing).onMessage(received, channel);
            }
            var dead = receive(channel, BusinessTopology.DLQ);
            assertNotNull(dead.getMessageProperties().getHeader("x-death"));
            channel.basicAck(dead.getMessageProperties().getDeliveryTag(), false);
            verify(failing, times(3)).process(any());
            assertEquals(0, mongo.getCollection("community_business_inbox").countDocuments());
        }
    }

    @Test void transientFailureCanRecoverOnRedelivery() throws Exception {
        var failing = mock(MongoBusinessEventStore.class);
        when(failing.process(any())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("one temporary outage"));
        var event = event(0);
        try (var connection = rabbitServer.factory().newConnection(); var channel = connection.createChannel()) {
            publish(channel, message(event));
            listener(failing).onMessage(receive(channel, BusinessTopology.QUEUE), channel);
            listener(store).onMessage(receive(channel, BusinessTopology.QUEUE), channel);
            assertNotNull(mongo.findById(event.eventId().toString(), BusinessInboxDocument.class));
            assertNull(channel.basicGet(BusinessTopology.DLQ, false));
        }
    }

    @Test void retryBudgetSurvivesConsumerChannelRestart() throws Exception {
        var failing = mock(MongoBusinessEventStore.class);
        when(failing.process(any())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("offline test"));
        try (var connection = rabbitServer.factory().newConnection(); var channel = connection.createChannel()) {
            publish(channel, message(event(0)));
            listener(failing).onMessage(receive(channel, BusinessTopology.QUEUE), channel);
            channel.queueDeclarePassive(BusinessTopology.QUEUE); // el broker ya recibió el NACK
        }
        try (var connection = rabbitServer.factory().newConnection(); var channel = connection.createChannel()) {
            var recovered = receive(channel, BusinessTopology.QUEUE);
            assertEquals(1, ((Number) recovered.getMessageProperties().getHeader("x-delivery-count")).intValue());
            listener(failing).onMessage(recovered, channel);
            listener(failing).onMessage(receive(channel, BusinessTopology.QUEUE), channel);
            var dead = receive(channel, BusinessTopology.DLQ);
            channel.basicAck(dead.getMessageProperties().getDeliveryTag(), false);
            verify(failing, times(3)).process(any());
        }
    }

    @Test void crashAfterCommitBeforeAckRecoversWithoutDoubleEffect() throws Exception {
        var event = event(0);
        try (var connection = rabbitServer.factory().newConnection(); var channel = connection.createChannel()) {
            publish(channel, message(event));
            var received = receive(channel, BusinessTopology.QUEUE);
            store.process(new BusinessEventParser(JSON).parse(received));
            // Simula caída entre commit y ACK cerrando el canal sin confirmar.
        }
        try (var connection = rabbitServer.factory().newConnection(); var channel = connection.createChannel()) {
            var redelivered = receive(channel, BusinessTopology.QUEUE);
            assertTrue(redelivered.getMessageProperties().isRedelivered());
            var restarted = new MongoBusinessEventStore(new MongoTemplate(client, "foodly-community"), Clock.systemUTC());
            listener(restarted).onMessage(redelivered, channel);
            assertEquals(1, mongo.getCollection("community_business_inbox").countDocuments());
            assertEquals(1, mongo.getCollection("community_known_huariques").countDocuments());
        }
    }

    @Test void springCompositionStartsManualConsumerAgainstRealBrokerAndReplicaSet() throws Exception {
        new ApplicationContextRunner().withUserConfiguration(BusinessEventsConfiguration.class)
                .withPropertyValues("community.integrations.business-events.enabled=true", "community.integrations.business-events.max-attempts=3",
                        "community.integrations.business-events.retry-delay-ms=10", "community.persistence.mongodb.enabled=true",
                        "spring.rabbitmq.host=127.0.0.1", "spring.rabbitmq.port=" + rabbitServer.factory().getPort(),
                        "spring.rabbitmq.username=" + rabbitServer.factory().getUsername(), "spring.rabbitmq.password=" + rabbitServer.factory().getPassword(),
                        "spring.rabbitmq.virtual-host=/")
                .withBean(MongoTemplate.class, () -> mongo).withBean(Clock.class, Clock::systemUTC).withBean(com.fasterxml.jackson.databind.ObjectMapper.class, () -> JSON)
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    var container = context.getBean(SimpleMessageListenerContainer.class);
                    assertEquals(AcknowledgeMode.MANUAL, container.getAcknowledgeMode());
                    assertTrue(container.isRunning());
                    var event = event(0);
                    try (var connection = rabbitServer.factory().newConnection(); var channel = connection.createChannel()) {
                        publish(channel, message(event));
                        await(() -> mongo.findById(event.eventId().toString(), BusinessInboxDocument.class) != null);
                    }
                });
    }

    @Test void durableQuorumTopologyHasOnlyThreeBusinessBindings() throws Exception {
        // Passive declarations verify durable queue presence; routing tests exercise the actual bindings.
        try (var connection = rabbitServer.factory().newConnection(); var channel = connection.createChannel()) {
            assertNotNull(channel.queueDeclarePassive(BusinessTopology.DLQ));
            for (String type : BusinessEvent.TYPES) {
                var e = event(0);
                publish(channel, message(new BusinessEvent(e.eventId(), type, 1, e.huariqueId(), 0, e.occurredAt())));
                var received = receive(channel, BusinessTopology.QUEUE);
                assertEquals("business." + type, received.getMessageProperties().getReceivedRoutingKey());
                channel.basicAck(received.getMessageProperties().getDeliveryTag(), false);
            }
            channel.basicPublish(BusinessTopology.EXCHANGE, "business.MenuActualizado", null, "{}".getBytes());
            channel.queueDeclarePassive(BusinessTopology.QUEUE); // barrera de canal
            assertNull(channel.basicGet(BusinessTopology.QUEUE, false));
            // Declaración idéntica prueba atributos; Rabbit rechazaría cambios de tipo/durabilidad/argumentos.
            channel.queueDeclare(BusinessTopology.QUEUE, true, false, false, Map.of("x-queue-type", "quorum", "x-delivery-limit", 2,
                    "x-dead-letter-exchange", BusinessTopology.DLX, "x-dead-letter-routing-key", BusinessTopology.DEAD_KEY,
                    "x-dead-letter-strategy", "at-least-once", "x-overflow", "reject-publish"));
        }
    }

    private void deadLetters(Message message) throws Exception {
        try (var connection = rabbitServer.factory().newConnection(); var channel = connection.createChannel()) {
            publish(channel, message);
            listener(store).onMessage(receive(channel, BusinessTopology.QUEUE), channel);
            var dead = receive(channel, BusinessTopology.DLQ);
            assertArrayEquals(message.getBody(), dead.getBody());
            channel.basicAck(dead.getMessageProperties().getDeliveryTag(), false);
            assertEquals(0, mongo.getCollection("community_business_inbox").countDocuments());
        }
    }

    private BusinessEventListener listener(MongoBusinessEventStore repository) {
        return new BusinessEventListener(new BusinessEventParser(JSON), repository, settings);
    }

    private void publish(Channel channel, Message message) throws Exception {
        channel.confirmSelect();
        channel.basicPublish(BusinessTopology.EXCHANGE, message.getMessageProperties().getReceivedRoutingKey(),
                new AMQP.BasicProperties.Builder().messageId(message.getMessageProperties().getMessageId()).deliveryMode(2).contentType("application/json").build(), message.getBody());
        channel.waitForConfirmsOrDie(5000);
    }

    private Message receive(Channel channel, String queue) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        do {
            var response = channel.basicGet(queue, false);
            if (response != null) return new Message(response.getBody(), new DefaultMessagePropertiesConverter()
                    .toMessageProperties(response.getProps(), response.getEnvelope(), "UTF-8"));
            Thread.sleep(20);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("No delivery from isolated queue " + queue);
    }

    private void await(java.util.function.BooleanSupplier ready) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (!ready.getAsBoolean()) {
            if (System.nanoTime() > deadline) throw new AssertionError("Timed out waiting for isolated consumer");
            Thread.sleep(20);
        }
    }
}
