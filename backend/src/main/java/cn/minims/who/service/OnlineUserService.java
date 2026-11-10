package cn.minims.who.service;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录用户的在线状态：按进程内存里的最近活跃时间算，5 分钟内有过带有效登录态的请求算在线。
 * <p>不落库、不跨实例共享，重启后归零——和 Sa-Token 令牌只放内存是同一个单实例前提。
 * 令牌 7 天有效，所以不能拿「有会话」当在线；反过来，登出、冻结踢下线、令牌过期时
 * 会话立即失效，在线状态也就立刻跟着变，不必等窗口走完。
 */
@Service
public class OnlineUserService {

    /** 5 分钟内没有请求算离线：公开页页脚每 60 秒一次心跳，应用内的操作请求也会刷新。 */
    static final long ONLINE_WINDOW_MS = 5 * 60_000L;
    /** 上限只是防止极端情况下内存无限增长，正常部署里 key 数不会超过用户总数。 */
    private static final int MAX_TRACKED = 20_000;

    private final Map<Long, Long> lastSeen = new ConcurrentHashMap<>();

    /** 每个带有效登录态的请求都刷新一次，公开白名单接口（如页脚 ping）也覆盖得到。 */
    public void touchCurrentUser() {
        if (StpUtil.isLogin()) {
            touch(StpUtil.getLoginIdAsLong(), System.currentTimeMillis());
        }
    }

    void touch(long userId) {
        touch(userId, System.currentTimeMillis());
    }

    void touch(long userId, long now) {
        if (!lastSeen.containsKey(userId) && lastSeen.size() >= MAX_TRACKED) {
            prune(now);
        }
        lastSeen.put(userId, now);
    }

    /** 后台列表一次拿到全部在线 id，避免逐行查询；顺带清掉过期项。 */
    public List<Long> onlineUserIds() {
        return onlineUserIds(System.currentTimeMillis());
    }

    List<Long> onlineUserIds(long now) {
        prune(now);
        List<Long> ids = new ArrayList<>();
        lastSeen.forEach((userId, seenAt) -> {
            if (now - seenAt <= ONLINE_WINDOW_MS && StpUtil.isLogin(userId)) {
                ids.add(userId);
            }
        });
        return ids;
    }

    private void prune(long now) {
        lastSeen.entrySet().removeIf(entry -> now - entry.getValue() > ONLINE_WINDOW_MS);
    }
}
