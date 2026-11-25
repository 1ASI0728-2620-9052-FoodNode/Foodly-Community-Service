package com.foodly.community.application.errors;

import com.foodly.community.domain.model.FavoriteId;

/** La pareja solicitada no existía al intentar eliminarla. */
public final class FavoriteNotFoundException extends RuntimeException {

    public FavoriteNotFoundException(FavoriteId id) {
        super("Favorite not found for " + id);
    }
}
