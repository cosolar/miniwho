package cn.minims.who.service;

import cn.minims.who.dto.SiteDtos.SiteStatsVo;
import cn.minims.who.mapper.SiteStatsMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 站点统计：访问次数落库，在线人数只按内存里的心跳窗口计算。
 * <p>这是第一方统计，不发第三方请求、不采集 IP 与 UA；前端只上报一个本地随机访客 id。
 */
@Service
public class SiteStatsService {

    private static final String VISITS_KEY = "visits_total";
    /** 5 分钟内有心跳算在线，对应前端每 60 秒一次的 ping。 */
    private static final long ONLINE_WINDOW_MS = 5 * 60_000L;
    /** 同一访客 6 小时内只计一次访问，避免刷新页面把累计数刷上去。 */
    private static final long VISIT_DEDUPE_MS = 6 * 60 * 60_000L;
    /** 内存里最多跟这么多访客，超出就把过期项清掉，防止被随机 id 撑爆。 */
    private static final int MAX_TRACKED = 20_000;

    private final SiteStatsMapper statsMapper;
    private final Map<String, Long> onlineSeen = new ConcurrentHashMap<>();
    private final Map<String, Long> visitSeen = new ConcurrentHashMap<>();

    public SiteStatsService(SiteStatsMapper statsMapper) {
        this.statsMapper = statsMapper;
    }

    public long visits() {
        Long value = statsMapper.selectValue(VISITS_KEY);
        return value == null ? 0L : value;
    }

    public int online() {
        long now = System.currentTimeMillis();
        return (int) onlineSeen.values().stream()
                .filter(seenAt -> now - seenAt <= ONLINE_WINDOW_MS)
                .count();
    }

    public SiteStatsVo snapshot() {
        return new SiteStatsVo(visits(), online());
    }

    /** 只刷新在线心跳，不改访问计数。 */
    public void touch(String rawVisitorId) {
        String visitorId = normalize(rawVisitorId);
        if (visitorId == null) {
            return;
        }
        track(onlineSeen, visitorId, System.currentTimeMillis(), ONLINE_WINDOW_MS);
    }

    /** 计一次访问，并在同一请求里完成一次心跳。 */
    public void recordVisit(String rawVisitorId) {
        touch(rawVisitorId);
        String visitorId = normalize(rawVisitorId);
        if (visitorId == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!track(visitSeen, visitorId, now, VISIT_DEDUPE_MS)) {
            return;
        }
        incrementVisits(now);
    }

    /**
     * 已在跟踪的访客直接刷新时间戳；新访客超过容量上限时先清过期项，还满就拒绝记录。
     * 上限是防内存被随机 id 撑爆的硬闸，不是精确统计：被灌满后宁可少算极端访客。
     */
    private boolean track(Map<String, Long> map, String visitorId, long now, long ttl) {
        if (map.containsKey(visitorId)) {
            map.put(visitorId, now);
            return true;
        }
        if (map.size() >= MAX_TRACKED) {
            prune(map, now, ttl);
            if (map.size() >= MAX_TRACKED) {
                return false;
            }
        }
        map.put(visitorId, now);
        return true;
    }

    private void incrementVisits(long now) {
        if (statsMapper.increment(VISITS_KEY, now) > 0) {
            return;
        }
        try {
            statsMapper.insertFirst(VISITS_KEY, now);
        } catch (DuplicateKeyException race) {
            // 两个进程同时首插时只有一个成功，另一个补一次自增即可。
            statsMapper.increment(VISITS_KEY, now);
        }
    }

    private void prune(Map<String, Long> map, long now, long ttl) {
        map.entrySet().removeIf(entry -> now - entry.getValue() > ttl);
    }

    private String normalize(String rawVisitorId) {
        if (rawVisitorId == null) {
            return null;
        }
        String value = rawVisitorId.trim();
        if (value.isEmpty()) {
            return null;
        }
        return value.length() > 64 ? value.substring(0, 64) : value;
    }
}
