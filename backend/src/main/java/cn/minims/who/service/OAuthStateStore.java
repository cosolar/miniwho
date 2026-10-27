package cn.minims.who.service;

import cn.minims.who.common.BizException;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OAuth 的 state：发起跳转时发一枚，回调时必须原样还回来并被一次性核销。
 * <p>放内存是因为这个应用按单实例部署（登录态本身也在同进程的 Sa-Token 里），
 * 上限与过期时间用于防止有人刷发起接口把这枚表撑爆。
 */
@Component
public class OAuthStateStore {

    private static final long TTL_MS = 10 * 60_000L;
    private static final int MAX_PENDING = 10_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, Long> pending = new ConcurrentHashMap<>();

    public String issue() {
        evictExpired();
        if (pending.size() >= MAX_PENDING) {
            throw BizException.tooMany("登录请求太多，请稍后再试");
        }
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        pending.put(state, System.currentTimeMillis());
        return state;
    }

    /** 校验并核销：同一个 state 只能用一次，重放会被拒。 */
    public boolean consume(String state) {
        if (state == null || state.isBlank()) {
            return false;
        }
        Long issuedAt = pending.remove(state);
        return issuedAt != null && System.currentTimeMillis() - issuedAt <= TTL_MS;
    }

    private void evictExpired() {
        if (pending.size() < 64) {
            return;
        }
        long now = System.currentTimeMillis();
        pending.entrySet().removeIf(entry -> now - entry.getValue() > TTL_MS);
    }
}
