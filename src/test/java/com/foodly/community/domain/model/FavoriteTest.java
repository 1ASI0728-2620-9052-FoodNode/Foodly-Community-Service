package com.foodly.community.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class FavoriteTest {

    @Test
    void createsAValidRelationshipWithCompositeIdentity() {
        var favorite = new Favorite("explorer-1", "huarique-1");
        assertThat(favorite.getExplorerId()).isEqualTo("explorer-1");
        assertThat(favorite.getHuariqueId()).isEqualTo("huarique-1");
        assertThat(favorite.getId()).isEqualTo(new FavoriteId("explorer-1", "huarique-1"));
    }

    @ParameterizedTest
    @MethodSource("invalidIdentifiers")
    void rejectsInvalidRelationshipIdentifiers(String explorerId, String huariqueId) {
        assertThatIllegalArgumentException().isThrownBy(() -> new Favorite(explorerId, huariqueId));
        assertThatIllegalArgumentException().isThrownBy(() -> new FavoriteId(explorerId, huariqueId));
    }

    static Stream<Arguments> invalidIdentifiers() {
        return Stream.of(null, "", " ", "\t\n", "\u2003")
                .flatMap(invalid -> Stream.of(Arguments.of(invalid, "huarique-1"),
                        Arguments.of("explorer-1", invalid)));
    }

    @Test
    void samePairHasTheSameAggregateIdentityAndHashCode() {
        var first = new Favorite("explorer-1", "huarique-1");
        var second = new Favorite("explorer-1", "huarique-1");
        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
        assertThat(first.getId()).isEqualTo(second.getId()).hasSameHashCodeAs(second.getId());
    }

    @Test
    void changingEitherMemberOfThePairChangesIdentity() {
        var favorite = new Favorite("explorer-1", "huarique-1");
        assertThat(favorite).isNotEqualTo(new Favorite("explorer-2", "huarique-1"));
        assertThat(favorite).isNotEqualTo(new Favorite("explorer-1", "huarique-2"));
    }
}
