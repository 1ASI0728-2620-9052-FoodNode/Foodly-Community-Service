package com.foodly.community.domain.repository;

import java.util.Optional;

/**
 * Contrato mínimo de persistencia independiente de la tecnología.
 * Los repositorios específicos lo tiparán cuando existan agregados e identidades.
 * No establece consultas, transacciones ni políticas de actualización.
 * Los errores técnicos de los adaptadores se expresan con RepositoryAccessException,
 * preservando su causa; los conflictos de dominio conservan sus tipos específicos.
 *
 * @param <T> tipo del agregado
 * @param <ID> tipo de su identidad
 */
public interface AggregateRepository<T, ID> {

    Optional<T> findById(ID id);

    T save(T aggregate);
}
