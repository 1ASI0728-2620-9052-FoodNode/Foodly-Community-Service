package com.foodly.community.application.ports;

import com.foodly.community.application.errors.AuthenticationRequiredException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentExplorerTest {

    @Test
    void preservesTheOpaqueIdentityFromTheTrustedAdapter() {
        CurrentExplorer current = () -> Optional.of(" opaque:explorer ");
        assertThat(current.requireExplorerId()).isEqualTo(" opaque:explorer ");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n", "\u2003"})
    void missingOrInvalidIdentityFailsClosed(String explorerId) {
        CurrentExplorer current = () -> Optional.ofNullable(explorerId);
        assertThatThrownBy(current::requireExplorerId).isInstanceOf(AuthenticationRequiredException.class);
    }
}
