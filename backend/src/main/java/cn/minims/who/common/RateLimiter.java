package cn.minims.who.common;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内的固定窗口限流器：公开接口没有登录态可依赖，只能按来源 IP + 动作限制频次，
 * 防止登录爆破、分享密码爆破与统计接口被脚本刷量。
 *
 * <p>单实例部署足够用；多实例部署时每个节点各算各的，总阈值按节点数放大，
 * 需要精确全局限流时应换成 Redis 等集中存储。
 */
public class RateLimiter {

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final int maxWindows;

    public RateLimiter(int maxWindows) {
        this.maxWindows = maxWindows;
    }

    /** 返回 true 表示放行；每个键一个独立的滑动起点，窗口内计数达到上限后拒绝。 */
    public boolean tryAcquire(String key, int limit, long windowMillis) {
        long now = System.currentTimeMillis();
        Window window = windows.compute(key, (k, existing) ->
                existing == null || now - existing.start >= windowMillis ? new Window(now) : existing);
        // compute 返回的是新建或沿用对象，这里同步在窗口对象上自增即可，不需要锁整张表。
        synchronized (window) {
            if (now - window.start >= windowMillis) {
                window.start = now;
                window.count = 0;
            }
            if (window.count >= limit) {
                return false;
            }
            window.count++;
            return true;
        }
    }

    /** 键数量有硬上限：满了先清过期窗口，还满就整体清空（限流是防御，不能反过来吃内存）。 */
    public void evictIfNeeded(long windowMillis) {
        if (windows.size() < maxWindows) {
            return;
        }
        long now = System.currentTimeMillis();
        windows.values().removeIf(window -> now - window.start >= windowMillis);
        if (windows.size() >= maxWindows) {
            windows.clear();
        }
    }

    private static final class Window {
        private long start;
        private int count;

        private Window(long start) {
            this.start = start;
        }
    }
}
