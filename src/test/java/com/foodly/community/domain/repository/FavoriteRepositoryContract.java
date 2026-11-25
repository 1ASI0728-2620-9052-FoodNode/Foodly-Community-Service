package com.foodly.community.domain.repository;

import com.foodly.community.domain.model.Favorite;
import com.foodly.community.domain.model.FavoriteId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contrato reutilizable para el futuro adaptador. Una subclase debe proporcionar
 * un repositorio vacío; ejecutarlo con un doble no acredita persistencia real.
 */
public abstract class FavoriteRepositoryContract {

    private IFavoriteRepository repository;

    protected abstract IFavoriteRepository newEmptyRepository();

    @BeforeEach
    void setUp() {
        repository = newEmptyRepository();
    }

    @Test
    void savesAndFindsByCompositeIdentity() {
        var favorite = new Favorite("explorer-1", "huarique-1");
        assertThat(repository.save(favorite)).isEqualTo(favorite);
        assertThat(repository.findById(favorite.getId())).contains(favorite);
    }

    @Test
    void rejectsTheSamePairWithoutOverwritingTheExistingFavorite() {
        var original = new Favorite("explorer-1", "huarique-1");
        repository.save(original);
        assertThatThrownBy(() -> repository.save(new Favorite("explorer-1", "huarique-1")))
                .isInstanceOf(DuplicateFavoriteException.class);
        assertThat(repository.findById(original.getId())).contains(original);
        assertThat(repository.findByExplorerId("explorer-1")).containsExactly(original);
    }

    @Test
    void allowsDifferentPairsAndQueriesOnlyTheRequestedExplorer() {
        var first = new Favorite("explorer-1", "huarique-1");
        var second = new Favorite("explorer-1", "huarique-2");
        var otherExplorer = new Favorite("explorer-2", "huarique-1");
        repository.save(first);
        repository.save(second);
        repository.save(otherExplorer);
        assertThat(repository.findByExplorerId("explorer-1")).containsExactlyInAnyOrder(first, second);
        assertThat(repository.findByExplorerId("explorer-2")).containsExactly(otherExplorer);
    }

    @Test
    void missingIdentityReturnsEmpty() {
        assertThat(repository.findById(new FavoriteId("missing", "huarique-1"))).isEmpty();
    }

    @Test
    void noFavoritesReturnsAnEmptyCollection() {
        assertThat(repository.findByExplorerId("missing")).isEmpty();
    }
}
