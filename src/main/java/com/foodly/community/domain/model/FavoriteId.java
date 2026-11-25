package com.foodly.community.domain.model;

/** Identidad compuesta: la misma pareja representa siempre el mismo favorito. */
public record FavoriteId(String explorerId, String huariqueId) {

    public FavoriteId {
        explorerId = DomainIdentifiers.requireValid(explorerId, "explorerId");
        huariqueId = DomainIdentifiers.requireValid(huariqueId, "huariqueId");
    }
}
