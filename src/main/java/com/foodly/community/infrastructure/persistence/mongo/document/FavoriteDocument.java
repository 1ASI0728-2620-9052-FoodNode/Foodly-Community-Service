package com.foodly.community.infrastructure.persistence.mongo.document;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** _id es exclusivamente técnico; la identidad de dominio sigue siendo la pareja. */
@Document(collection = "favorites")
public record FavoriteDocument(@Id ObjectId id, String explorerId, String huariqueId) {
}
