package com.foodly.community.domain.repository;

import com.foodly.community.domain.model.Favorite;
import com.foodly.community.domain.model.FavoriteId;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Doble exclusivo de pruebas: no es adaptador de producción ni prueba MongoDB. */
class InMemoryFavoriteRepositoryContractTest extends FavoriteRepositoryContract {

    @Override
    protected IFavoriteRepository newEmptyRepository() {
        return new InMemoryFavoriteRepository();
    }

    private static final class InMemoryFavoriteRepository implements IFavoriteRepository {

        private final Map<FavoriteId, Favorite> favorites = new ConcurrentHashMap<>();

        @Override
        public Favorite save(Favorite favorite) {
            if (favorites.putIfAbsent(favorite.getId(), favorite) != null) {
                throw new DuplicateFavoriteException(favorite.getId());
            }
            return favorite;
        }

        @Override
        public Optional<Favorite> findById(FavoriteId id) {
            return Optional.ofNullable(favorites.get(id));
        }

        @Override
        public List<Favorite> findByExplorerId(String explorerId) {
            return favorites.values().stream()
                    .filter(favorite -> favorite.getExplorerId().equals(explorerId))
                    .toList();
        }

        @Override
        public boolean deleteById(FavoriteId id) {
            return favorites.remove(id) != null;
        }
    }
}
