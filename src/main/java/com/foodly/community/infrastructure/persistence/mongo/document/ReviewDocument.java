package com.foodly.community.infrastructure.persistence.mongo.document;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

/** reviewId se guarda como _id String incluso cuando tiene aspecto de ObjectId. */
@Document(collection = "reviews")
public record ReviewDocument(@MongoId(FieldType.STRING) String reviewId, String explorerId,
                             String huariqueId, Integer rating, String comment, String createdAt) {
}
