package cn.minims.who.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * 公开接口的频次防线：登录/注册/分享解锁这类能直接验证凭据的入口从严，
 * 统计心跳这类正常用户也会高频调用的从宽。
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final long WINDOW_MILLIS = 60_000L;
    private static final int MAX_TRACKED_IPS = 50_000;

    private static final Map<Pattern, Rule> RULES = Map.of(
            Pattern.compile("^/api/auth/login$"), new Rule("login", 10),
            Pattern.compile("^/api/auth/register$"), new Rule("register", 5),
            // 验证码按 IP 从严：服务层还按邮箱限，两个维度叠起来才挡得住换邮箱继续刷。
            Pattern.compile("^/api/auth/email-code$"), new Rule("email-code", 5),
            Pattern.compile("^/api/auth/register-email$"), new Rule("register", 5),
            // 回调会驱动一次对 GitHub 的出站请求，不限制就等于把本机当免费代理用。
            Pattern.compile("^/api/auth/oauth/github(/callback)?$"), new Rule("oauth", 20),
            Pattern.compile("^/api/public/shares/[^/]+/unlock$"), new Rule("unlock", 10),
            Pattern.compile("^/api/feedback$"), new Rule("feedback", 10),
            Pattern.compile("^/api/public/stats.*"), new Rule("stats", 120)
    );

    private final RateLimiter limiter = new RateLimiter(MAX_TRACKED_IPS);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();
        for (Map.Entry<Pattern, Rule> entry : RULES.entrySet()) {
            if (!entry.getKey().matcher(path).matches()) {
                continue;
            }
            Rule rule = entry.getValue();
            limiter.evictIfNeeded(WINDOW_MILLIS);
            if (!limiter.tryAcquire(rule.action() + ":" + clientIp(request), rule.limit(), WINDOW_MILLIS)) {
                throw BizException.tooMany("操作太频繁，请一分钟后再试");
            }
            break;
        }
        return true;
    }

    /** 反向代理场景取 X-Forwarded-For 的第一跳；没有代理头时退回直连地址。 */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }

    private record Rule(String action, int limit) {
    }
}
