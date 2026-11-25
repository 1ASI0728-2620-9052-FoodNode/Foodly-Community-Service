package com.foodly.community.domain.model;

/**
 * Raíz de agregado identificada por explorador y huarique, sin identificador artificial.
 * La unicidad entre instancias guardadas es responsabilidad del repositorio.
 */
public final class Favorite {

    private final FavoriteId id;

    public Favorite(String explorerId, String huariqueId) {
        this.id = new FavoriteId(explorerId, huariqueId);
    }

    public FavoriteId getId() {
        return id;
    }

    public String getExplorerId() {
        return id.explorerId();
    }

    public String getHuariqueId() {
        return id.huariqueId();
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Favorite favorite && id.equals(favorite.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
