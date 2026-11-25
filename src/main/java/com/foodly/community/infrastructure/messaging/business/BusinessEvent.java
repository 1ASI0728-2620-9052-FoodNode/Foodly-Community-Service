package com.foodly.community.infrastructure.messaging.business;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Contrato externo Business v1; no es un evento nuevo del dominio Community. */
public record BusinessEvent(UUID eventId, String type, int schemaVersion, UUID huariqueId,
                            long aggregateVersion, Instant occurredAt) {
    public static final Set<String> TYPES = Set.of("HuariqueRegistrado", "PerfilDelHuariqueActualizado", "EstadoAbiertoCerradoCambiado");
}
