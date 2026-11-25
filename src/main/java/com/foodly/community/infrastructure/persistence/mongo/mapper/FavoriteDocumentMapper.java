package com.foodly.community.infrastructure.persistence.mongo.mapper;

import com.foodly.community.domain.model.Favorite;
import com.foodly.community.infrastructure.persistence.mongo.document.FavoriteDocument;
import org.bson.types.ObjectId;

public final class FavoriteDocumentMapper {

    private FavoriteDocumentMapper() {
    }

    public static FavoriteDocument toDocument(Favorite favorite) {
        return new FavoriteDocument(new ObjectId(), favorite.getExplorerId(), favorite.getHuariqueId());
    }

    public static Favorite toDomain(FavoriteDocument document) {
        return new Favorite(document.explorerId(), document.huariqueId());
    }
}
