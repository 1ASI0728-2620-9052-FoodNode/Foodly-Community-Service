package com.foodly.community.domain.model;

/** Calificación entera e inmutable, de una a cinco estrellas. */
public record Rating(int value) {

    public Rating {
        if (value < 1 || value > 5) {
            throw new IllegalArgumentException("rating must be between 1 and 5");
        }
    }
}
