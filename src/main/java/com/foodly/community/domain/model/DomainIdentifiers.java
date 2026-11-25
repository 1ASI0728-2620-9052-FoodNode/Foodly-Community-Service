package com.foodly.community.domain.model;

/** Validación local mínima; el formato externo de los identificadores sigue pendiente. */
final class DomainIdentifiers {

    private DomainIdentifiers() {
    }

    static String requireValid(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be null or blank");
        }
        return value;
    }
}
