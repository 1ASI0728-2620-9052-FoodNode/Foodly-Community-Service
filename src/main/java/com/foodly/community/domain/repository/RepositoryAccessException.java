package com.foodly.community.domain.repository;

/** Fallo técnico del puerto; los adaptadores deben traducir errores del driver a este tipo. */
public final class RepositoryAccessException extends RuntimeException {

    public RepositoryAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
