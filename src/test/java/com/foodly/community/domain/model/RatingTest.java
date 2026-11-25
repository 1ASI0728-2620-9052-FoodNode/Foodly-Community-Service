package com.foodly.community.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class RatingTest {

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    void acceptsEveryValidInteger(int value) {
        assertThat(new Rating(value).value()).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 6, 10, Integer.MAX_VALUE})
    void rejectsValuesOutsideTheRange(int value) {
        assertThatIllegalArgumentException().isThrownBy(() -> new Rating(value));
    }

    @Test
    void equalityAndHashCodeDependOnValue() {
        assertThat(new Rating(4)).isEqualTo(new Rating(4)).hasSameHashCodeAs(new Rating(4));
        assertThat(new Rating(4)).isNotEqualTo(new Rating(5));
    }

    @Test
    void isImmutable() {
        assertThat(Modifier.isFinal(Rating.class.getModifiers())).isTrue();
        assertThat(Rating.class.getDeclaredFields())
                .allMatch(field -> Modifier.isPrivate(field.getModifiers())
                        && Modifier.isFinal(field.getModifiers()));
    }
}
