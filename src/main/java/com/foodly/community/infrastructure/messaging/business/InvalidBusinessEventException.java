package com.foodly.community.infrastructure.messaging.business;

/** Error permanente de contrato; nunca incluye el payload o datos del catálogo. */
public final class InvalidBusinessEventException extends RuntimeException {
    public InvalidBusinessEventException() {
        super("Unsupported or invalid Business event");
    }
}
