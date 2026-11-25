package com.foodly.community.domain.repository;

import com.foodly.community.domain.model.FavoriteId;

/** Incumplimiento del contrato de unicidad; no expone excepciones del driver de persistencia. */
public final class DuplicateFavoriteException extends IllegalStateException {

    public DuplicateFavoriteException(FavoriteId id) {
        super("Favorite already exists for " + id);
    }
}
