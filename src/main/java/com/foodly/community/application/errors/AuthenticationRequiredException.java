package com.foodly.community.application.errors;

/** El contexto de seguridad no proporcionó una identidad de explorador válida. */
public final class AuthenticationRequiredException extends RuntimeException {

    public AuthenticationRequiredException() {
        super("An authenticated explorer is required");
    }
}
