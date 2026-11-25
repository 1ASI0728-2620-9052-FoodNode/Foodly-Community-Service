package com.foodly.community.infrastructure.messaging.business;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Message;

import java.time.Instant;
import java.util.UUID;

public final class BusinessEventParser {
    private final ObjectMapper json;

    public BusinessEventParser(ObjectMapper json) {
        this.json = json.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }

    public BusinessEvent parse(Message message) {
        try {
            JsonNode root;
            try (var parser = json.getFactory().createParser(message.getBody())) {
                root = json.readTree(parser);
                if (parser.nextToken() != null) throw new InvalidBusinessEventException();
            }
            if (root == null || !root.isObject() || integer(root, "schemaVersion") != 1) throw new InvalidBusinessEventException();
            UUID eventId = uuid(text(root, "eventId"));
            UUID huariqueId = uuid(text(root, "huariqueId"));
            String type = text(root, "type");
            long version = integer(root, "aggregateVersion");
            Instant occurredAt = Instant.parse(text(root, "occurredAt"));
            var properties = message.getMessageProperties();
            if (!BusinessEvent.TYPES.contains(type) || version < 0
                    || !("business." + type).equals(properties.getReceivedRoutingKey())
                    || !eventId.equals(uuid(properties.getMessageId()))) throw new InvalidBusinessEventException();
            JsonNode data = root.get("data");
            if (data == null || !data.isObject() || !huariqueId.equals(uuid(text(data, "id")))
                    || integer(data, "version") != version) throw new InvalidBusinessEventException();
            return new BusinessEvent(eventId, type, 1, huariqueId, version, occurredAt);
        } catch (Exception invalid) {
            throw new InvalidBusinessEventException();
        }
    }

    private String text(JsonNode root, String field) {
        var value = root.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) throw new InvalidBusinessEventException();
        return value.textValue();
    }

    private long integer(JsonNode root, String field) {
        var value = root.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) throw new InvalidBusinessEventException();
        return value.longValue();
    }

    private UUID uuid(String value) {
        var id = UUID.fromString(value);
        if (!id.toString().equalsIgnoreCase(value)) throw new InvalidBusinessEventException();
        return id;
    }
}
