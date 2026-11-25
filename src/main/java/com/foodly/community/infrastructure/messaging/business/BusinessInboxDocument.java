package com.foodly.community.infrastructure.messaging.business;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

@Document("community_business_inbox")
public record BusinessInboxDocument(@MongoId(FieldType.STRING) String eventId, String huariqueId,
                                    String type, long aggregateVersion, String outcome, String processedAt) { }
