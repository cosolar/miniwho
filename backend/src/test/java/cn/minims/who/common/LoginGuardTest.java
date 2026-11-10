package cn.minims.who.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoginGuardTest {

    @Test
    void requiresCodeOnlyAfterTheThreshold() {
        LoginGuard guard = new LoginGuard();
        for (int i = 0; i < LoginGuard.MAX_FAILURES - 1; i++) {
            guard.recordFailure(7L);
        }
        assertThat(guard.requiresCode(7L)).isFalse();

        guard.recordFailure(7L);
        assertThat(guard.requiresCode(7L)).isTrue();
    }

    @Test
    void successClearsTheCounter() {
        LoginGuard guard = new LoginGuard();
        for (int i = 0; i < LoginGuard.MAX_FAILURES; i++) {
            guard.recordFailure(7L);
        }
        guard.recordSuccess(7L);
        assertThat(guard.requiresCode(7L)).isFalse();
    }

    @Test
    void failuresOlderThanTheWindowDoNotCount() throws InterruptedException {
        LoginGuard guard = new LoginGuard(20);
        for (int i = 0; i < LoginGuard.MAX_FAILURES - 1; i++) {
            guard.recordFailure(7L);
        }
        Thread.sleep(60);
        assertThat(guard.requiresCode(7L)).isFalse();
    }
}
