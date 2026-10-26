package cn.minims.who.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterTest {

    private static final long WINDOW = 60_000L;

    @Test
    void 允许窗口内未超限的请求() {
        RateLimiter limiter = new RateLimiter(100);
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire("login:1.2.3.4", 5, WINDOW));
        }
        assertFalse(limiter.tryAcquire("login:1.2.3.4", 5, WINDOW));
    }

    @Test
    void 不同键各自计数() {
        RateLimiter limiter = new RateLimiter(100);
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire("login:1.2.3.4", 5, WINDOW));
        }
        assertTrue(limiter.tryAcquire("login:5.6.7.8", 5, WINDOW));
        assertTrue(limiter.tryAcquire("unlock:1.2.3.4", 5, WINDOW));
    }

    @Test
    void 过期窗口重新计数() throws InterruptedException {
        RateLimiter limiter = new RateLimiter(100);
        for (int i = 0; i < 3; i++) {
            assertTrue(limiter.tryAcquire("register:1.2.3.4", 3, 10));
        }
        assertFalse(limiter.tryAcquire("register:1.2.3.4", 3, 10));
        Thread.sleep(20);
        assertTrue(limiter.tryAcquire("register:1.2.3.4", 3, 10));
    }

    @Test
    void 键数量超过硬上限时清空重来() {
        RateLimiter limiter = new RateLimiter(3);
        assertTrue(limiter.tryAcquire("k1", 1, WINDOW));
        assertTrue(limiter.tryAcquire("k2", 1, WINDOW));
        assertTrue(limiter.tryAcquire("k3", 1, WINDOW));
        limiter.evictIfNeeded(WINDOW);
        // 仍然能正常放行，只是旧的计数被清掉了。
        assertTrue(limiter.tryAcquire("k4", 1, WINDOW));
        assertFalse(limiter.tryAcquire("k4", 1, WINDOW));
    }
}
