package com.foodly.community.infrastructure.persistence.mongo;

import com.foodly.community.infrastructure.messaging.business.*;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.Message;

import static com.foodly.community.infrastructure.persistence.mongo.BusinessEventTestData.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BusinessEventConsumerTest {
    final BusinessEventParser parser = new BusinessEventParser(JSON);
    final MongoBusinessEventStore store = mock(MongoBusinessEventStore.class);
    final Channel channel = mock(Channel.class);
    final BusinessEventListener listener = new BusinessEventListener(parser, store, new BusinessEventsProperties(3, 0));

    @ParameterizedTest @ValueSource(strings = {"HuariqueRegistrado", "PerfilDelHuariqueActualizado", "EstadoAbiertoCerradoCambiado"})
    void acceptsRealSpanishTypes(String type) throws Exception {
        var e = event(0);
        var expected = new BusinessEvent(e.eventId(), type, 1, e.huariqueId(), 0, e.occurredAt());
        assertEquals(expected, parser.parse(message(expected)));
    }

    @ParameterizedTest @ValueSource(strings = {"schemaVersion", "eventId", "type", "huariqueId", "aggregateVersion", "occurredAt", "data"})
    void missingEnvelopeFieldsArePermanent(String field) throws Exception {
        var original = message(event(0));
        var tree = (com.fasterxml.jackson.databind.node.ObjectNode) JSON.readTree(original.getBody());
        tree.remove(field);
        assertThrows(InvalidBusinessEventException.class, () -> parser.parse(new Message(JSON.writeValueAsBytes(tree), original.getMessageProperties())));
    }

    @ParameterizedTest @ValueSource(strings = {"{", "[]", "null", "{} {}", "{\"type\":1,\"type\":2}"})
    void malformedJsonRejected(String body) {
        assertThrows(InvalidBusinessEventException.class, () -> parser.parse(new Message(body.getBytes(), new org.springframework.amqp.core.MessageProperties())));
    }

    @ParameterizedTest @ValueSource(ints = {0, 2, -1})
    void unsupportedSchema(int version) throws Exception {
        var e = event(0);
        assertThrows(InvalidBusinessEventException.class, () -> parser.parse(message(new BusinessEvent(e.eventId(), e.type(), version, e.huariqueId(), 0, e.occurredAt()))));
    }

    @Test void rejectsMismatchedRoutingAndMessageIdentity() throws Exception {
        var message = message(event(0));
        message.getMessageProperties().setReceivedRoutingKey("business.Unknown");
        assertThrows(InvalidBusinessEventException.class, () -> parser.parse(message));
        message.getMessageProperties().setReceivedRoutingKey("business.HuariqueRegistrado");
        message.getMessageProperties().setMessageId(java.util.UUID.randomUUID().toString());
        assertThrows(InvalidBusinessEventException.class, () -> parser.parse(message));
    }

    @Test void rejectsSnapshotVersionMismatch() throws Exception {
        var original = message(event(0));
        var tree = JSON.readTree(original.getBody());
        ((com.fasterxml.jackson.databind.node.ObjectNode) tree.get("data")).put("version", 9);
        assertThrows(InvalidBusinessEventException.class, () -> parser.parse(new Message(JSON.writeValueAsBytes(tree), original.getMessageProperties())));
    }

    @ParameterizedTest @ValueSource(strings = {"APPLIED", "DUPLICATE", "OBSOLETE"})
    void ackOnlyAfterStoreReturns(String result) throws Exception {
        var event = event(0);
        when(store.process(event)).thenReturn(MongoBusinessEventStore.Result.valueOf(result));
        listener.onMessage(message(event), channel);
        var ordered = inOrder(store, channel);
        ordered.verify(store).process(event);
        ordered.verify(channel).basicAck(7, false);
        verifyNoMoreInteractions(channel);
    }

    @Test void permanentInvalidDoesNotTouchMongo() throws Exception {
        listener.onMessage(new Message("{".getBytes(), new org.springframework.amqp.core.MessageProperties()), channel);
        verify(channel).basicReject(0, false);
        verifyNoInteractions(store);
    }

    @Test void mongoFailureNacksWithoutAck() throws Exception {
        when(store.process(any())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("test"));
        listener.onMessage(message(event(0)), channel);
        verify(channel).basicNack(7, false, true);
        verifyNoMoreInteractions(channel);
    }

    @Test void finalFailureDeadLetters() throws Exception {
        when(store.process(any())).thenThrow(new IllegalStateException("test"));
        var message = message(event(0));
        message.getMessageProperties().setHeader("x-delivery-count", 2L);
        listener.onMessage(message, channel);
        verify(channel).basicReject(7, false);
        verifyNoMoreInteractions(channel);
    }

    @Test void lostAckIsNotConvertedIntoRejection() throws Exception {
        doThrow(new java.io.IOException("connection lost")).when(channel).basicAck(7, false);
        assertThrows(java.io.IOException.class, () -> listener.onMessage(message(event(0)), channel));
        verify(channel).basicAck(7, false);
        verifyNoMoreInteractions(channel);
    }

    @Test void validatesRetrySettings() {
        assertThrows(IllegalArgumentException.class, () -> new BusinessEventsProperties(0, 0));
        assertThrows(IllegalArgumentException.class, () -> new BusinessEventsProperties(1, -1));
    }
}
