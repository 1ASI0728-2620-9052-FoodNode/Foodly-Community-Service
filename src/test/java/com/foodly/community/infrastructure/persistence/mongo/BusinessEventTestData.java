package com.foodly.community.infrastructure.persistence.mongo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodly.community.infrastructure.messaging.business.*;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

final class BusinessEventTestData {
    static final ObjectMapper JSON = new ObjectMapper();
    static BusinessEvent event(long version) {
        return new BusinessEvent(UUID.randomUUID(), "HuariqueRegistrado", 1, UUID.randomUUID(), version, Instant.parse("2026-11-24T12:00:00Z"));
    }
    static BusinessEvent version(BusinessEvent event, long version) {
        return new BusinessEvent(UUID.randomUUID(), "PerfilDelHuariqueActualizado", 1, event.huariqueId(), version, event.occurredAt());
    }
    static Message message(BusinessEvent event) throws Exception {
        var properties = new MessageProperties();
        properties.setMessageId(event.eventId().toString());
        properties.setReceivedRoutingKey("business." + event.type());
        properties.setDeliveryTag(7);
        return new Message(JSON.writeValueAsBytes(Map.of("eventId", event.eventId().toString(), "type", event.type(),
                "schemaVersion", event.schemaVersion(), "huariqueId", event.huariqueId().toString(),
                "aggregateVersion", event.aggregateVersion(), "occurredAt", event.occurredAt().toString(),
                "data", Map.of("id", event.huariqueId().toString(), "version", event.aggregateVersion()))), properties);
    }
}
