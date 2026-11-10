package cn.minims.who.common;

import org.springframework.stereotype.Component;

import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 登录失败计数：同一账号 30 分钟内连续输错 3 次密码后，密码入口关闭，改走邮箱验证码登录。
 * <p>只记在内存里：重启即清空，效果是「刚部署完可能需要重新触发」，
 * 比把失败记录写进库、再为它设计清理与多实例同步要划算得多。
 */
@Component
public class LoginGuard {

    public static final int MAX_FAILURES = 3;
    static final long WINDOW_MS = 30 * 60_000L;
    private static final int MAX_TRACKED_USERS = 20_000;

    private final Map<Long, Deque<Long>> failures = new ConcurrentHashMap<>();
    private final long windowMs;

    public LoginGuard() {
        this(WINDOW_MS);
    }

    LoginGuard(long windowMs) {
        this.windowMs = windowMs;
    }

    public void recordFailure(long userId) {
        long now = System.currentTimeMillis();
        evictIfNeeded(now);
        failures.compute(userId, (id, times) -> {
            Deque<Long> deque = times == null ? new ConcurrentLinkedDeque<>() : times;
            prune(deque, now);
            deque.addLast(now);
            return deque;
        });
    }

    public boolean requiresCode(long userId) {
        Deque<Long> times = failures.get(userId);
        if (times == null) {
            return false;
        }
        prune(times, System.currentTimeMillis());
        return times.size() >= MAX_FAILURES;
    }

    /** 登录成功（含验证码通过）后清零：下一次失败重新从 1 数起。 */
    public void recordSuccess(long userId) {
        failures.remove(userId);
    }

    private void prune(Deque<Long> times, long now) {
        Long first;
        while ((first = times.peekFirst()) != null && now - first > windowMs) {
            times.pollFirst();
        }
    }

    /**
     * 只按成功登录清理不够：攻击者可以拿一批账号各错一次，把表撑大。
     * 超过上限时顺手把窗口外的记录扫掉，扫描频率由上界卡住，不会变成每个请求的固定开销。
     */
    private void evictIfNeeded(long now) {
        if (failures.size() < MAX_TRACKED_USERS) {
            return;
        }
        failures.forEach((id, times) -> {
            prune(times, now);
            if (times.isEmpty()) {
                failures.remove(id, times);
            }
        });
    }
}
