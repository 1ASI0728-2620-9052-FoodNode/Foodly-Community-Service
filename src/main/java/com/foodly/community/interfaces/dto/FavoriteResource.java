package com.foodly.community.interfaces.dto;

import com.foodly.community.domain.model.Favorite;

/** El favorito conserva su identidad compuesta; no se inventa un ID adicional. */
public record FavoriteResource(String explorerId, String huariqueId) {

    public static FavoriteResource from(Favorite favorite) {
        return new FavoriteResource(favorite.getExplorerId(), favorite.getHuariqueId());
    }
}
