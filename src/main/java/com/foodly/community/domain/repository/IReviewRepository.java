package com.foodly.community.domain.repository;

import com.foodly.community.domain.model.Review;

import java.util.List;

/** Puerto de creación y consulta; no define edición o eliminación de reseñas. */
public interface IReviewRepository extends AggregateRepository<Review, String> {

    /**
     * Inserta una reseña nueva, sin actualizar una identidad existente.
     * No se restringe el número de reseñas de una pareja explorador-huarique.
     *
     * @throws IllegalStateException si reviewId ya existe; no debe sobrescribirse
     */
    @Override
    Review save(Review review);

    /** Devuelve todas las reseñas del huarique; lista vacía si no hay, sin orden acordado. */
    List<Review> findByHuariqueId(String huariqueId);
}
