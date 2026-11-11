package cn.minims.who.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.minims.who.entity.User;
import cn.minims.who.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 把登录用户的最后活跃时间落到 {@code users.last_active_at}，供后台用户列表展示。
 * <p>与 {@link OnlineUserService} 分工：那个只在进程内存里判断「是否在线」（5 分钟窗口、重启归零），
 * 这个负责持久化「最后一次什么时候来过」，重启与多实例下仍然可查。
 * <p>每个请求都写库代价太大，同一用户最多 60 秒写一次；写失败只记 debug 日志，绝不影响业务请求。
 */
@Service
public class UserActivityService {

    /** 落库节流：后台按分钟级看活跃时间已经足够，没必要每个请求都刷一次 users 表。 */
    static final long PERSIST_INTERVAL_MS = 60_000L;
    /** 只用于节流，值不需要精确；超过用户总数说明有已删除账号残留，直接清空重建。 */
    private static final int MAX_TRACKED = 20_000;

    private static final Logger log = LoggerFactory.getLogger(UserActivityService.class);

    private final UserMapper userMapper;
    private final Map<Long, Long> persistedAt = new ConcurrentHashMap<>();

    public UserActivityService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /** 每个带有效登录态的请求都会经过这里；未登录直接跳过。 */
    public void touchCurrentUser() {
        if (StpUtil.isLogin()) {
            touch(StpUtil.getLoginIdAsLong());
        }
    }

    void touch(long userId) {
        touch(userId, System.currentTimeMillis());
    }

    void touch(long userId, long now) {
        Long last = persistedAt.get(userId);
        if (last != null && now - last < PERSIST_INTERVAL_MS) {
            return;
        }
        persistedAt.put(userId, now);
        if (persistedAt.size() > MAX_TRACKED) {
            persistedAt.clear();
        }
        persist(userId, now);
    }

    /** 单列更新；独立成包级方法，测试只验证节流与「失败不影响请求」这两个契约。 */
    void persist(long userId, long now) {
        try {
            // 只更新这一列：走 updateById 会顺带刷新 updated_at，把账号信息的修改时间冲掉。
            userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .eq(User::getId, userId)
                    .set(User::getLastActiveAt, now));
        } catch (RuntimeException e) {
            log.debug("更新用户最后活跃时间失败 userId={}: {}", userId, e.getMessage());
        }
    }
}
