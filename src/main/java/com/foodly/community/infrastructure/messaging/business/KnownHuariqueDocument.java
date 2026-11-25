package com.foodly.community.infrastructure.messaging.business;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

/** Solo existencia conocida y checkpoint; no replica el catálogo o el estado abierto/cerrado. */
@Document("community_known_huariques")
public record KnownHuariqueDocument(@MongoId(FieldType.STRING) String huariqueId, long aggregateVersion,
                                    String lastEventId, String lastEventType, String occurredAt) { }
