package cn.minims.who.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthStateStoreTest {

    private final OAuthStateStore store = new OAuthStateStore();

    @Test
    void 发出的状态值互不相同且非空() {
        Set<String> states = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            String state = store.issue();
            assertThat(state).isNotBlank();
            states.add(state);
        }
        assertThat(states).hasSize(50);
    }

    @Test
    void 状态值只能核销一次重放直接被拒() {
        String state = store.issue();
        assertThat(store.consume(state)).isTrue();
        assertThat(store.consume(state)).isFalse();
    }

    @Test
    void 没发过的状态值一律不认() {
        assertThat(store.consume(null)).isFalse();
        assertThat(store.consume("")).isFalse();
        assertThat(store.consume("   ")).isFalse();
        assertThat(store.consume("never-issued")).isFalse();
    }
}
