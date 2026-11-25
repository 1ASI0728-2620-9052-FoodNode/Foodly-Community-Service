package com.foodly.community.domain.repository;

import com.foodly.community.domain.model.Favorite;
import com.foodly.community.domain.model.FavoriteId;

import java.util.List;

/** Puerto de creación y consulta de favoritos mediante su identidad compuesta. */
public interface IFavoriteRepository extends AggregateRepository<Favorite, FavoriteId> {

    /**
     * Inserta atómicamente una pareja nueva. No sobrescribe un favorito existente.
     * Un findById previo no reemplaza la garantía atómica, incluso ante concurrencia.
     * El adaptador MongoDB deberá respaldarla con un índice único compuesto y
     * traducir la violación de ese índice a DuplicateFavoriteException.
     *
     * @throws DuplicateFavoriteException si la pareja explorador-huarique ya existe
     */
    @Override
    Favorite save(Favorite favorite);

    /** Devuelve los favoritos del explorador; lista vacía si no hay, sin orden acordado. */
    List<Favorite> findByExplorerId(String explorerId);

    /**
     * Elimina atómicamente la pareja indicada.
     * @return true si se eliminó un favorito; false si no existía al eliminar
     */
    boolean deleteById(FavoriteId id);
}
