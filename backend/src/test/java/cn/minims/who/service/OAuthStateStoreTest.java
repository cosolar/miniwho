package cn.minims.who.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthStateStoreTest {

    private final OAuthStateStore store = new OAuthStateStore();

    @Test
    void issuedStatesAreUniqueAndNonBlank() {
        Set<String> states = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            String state = store.issue();
            assertThat(state).isNotBlank();
            states.add(state);
        }
        assertThat(states).hasSize(50);
    }

    @Test
    void stateCanOnlyBeConsumedOnce() {
        String state = store.issue();
        assertThat(store.consume(state)).isTrue();
        assertThat(store.consume(state)).isFalse();
    }

    @Test
    void unissuedStateIsRejected() {
        assertThat(store.consume(null)).isFalse();
        assertThat(store.consume("")).isFalse();
        assertThat(store.consume("   ")).isFalse();
        assertThat(store.consume("never-issued")).isFalse();
    }
}
