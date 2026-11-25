package com.foodly.community.application.ports;

import com.foodly.community.application.errors.AuthenticationRequiredException;

import java.util.Optional;

/**
 * Identidad suministrada por un adaptador de seguridad de confianza, nunca por un
 * campo explorerId del cuerpo, ruta o query HTTP. No autentica ni valida JWT aquí.
 */
@FunctionalInterface
public interface CurrentExplorer {

    /** Identidad local del explorador derivada del principal autenticado; vacío sin autenticación. */
    Optional<String> explorerId();

    default String requireExplorerId() {
        return explorerId().filter(id -> !id.isBlank())
                .orElseThrow(AuthenticationRequiredException::new);
    }
}
