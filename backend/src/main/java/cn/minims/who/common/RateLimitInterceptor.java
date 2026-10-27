package cn.minims.who.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;
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

    private static final Map<Pattern, Rule> RULES = Map.ofEntries(
            Map.entry(Pattern.compile("^/api/auth/login$"), new Rule("login", 10)),
            Map.entry(Pattern.compile("^/api/auth/register$"), new Rule("register", 5)),
            // 验证码按 IP 从严：服务层还按邮箱限，两个维度叠起来才挡得住换邮箱继续刷。
            Map.entry(Pattern.compile("^/api/auth/email-code$"), new Rule("email-code", 5)),
            Map.entry(Pattern.compile("^/api/auth/register-email$"), new Rule("register", 5)),
            // 找回密码两条同档：一条能刷信，一条能猜码，都不能比注册给得更宽。
            Map.entry(Pattern.compile("^/api/auth/password-code$"), new Rule("password-code", 5)),
            Map.entry(Pattern.compile("^/api/auth/password-reset$"), new Rule("password-reset", 5)),
            // 换绑邮箱的发码口在登录态后面，但邮箱维度挡不住换着新地址刷信，IP 这道照发码口的规矩从严。
            Map.entry(Pattern.compile("^/api/account/email-code$"), new Rule("account-email-code", 5)),
            // 回调会驱动一次对 GitHub 的出站请求，不限制就等于把本机当免费代理用。
            Map.entry(Pattern.compile("^/api/auth/oauth/github(/callback)?$"), new Rule("oauth", 20)),
            // 全量导出最贵的一次读：一次能把整个账号搬走，也给攻击者留出拖库的口子。
            Map.entry(Pattern.compile("^/api/account/export$"), new Rule("export", 5)),
            // 注销两条同档：一条能刷信、一条能拿半个 session 猜密码，都是不可回头的那一步。
            Map.entry(Pattern.compile("^/api/account/deletion(/code)?$"), new Rule("deletion", 5)),
            Map.entry(Pattern.compile("^/api/public/shares/[^/]+/unlock$"), new Rule("unlock", 10)),
            Map.entry(Pattern.compile("^/api/feedback$"), new Rule("feedback", 10)),
            Map.entry(Pattern.compile("^/api/public/stats.*"), new Rule("stats", 120))
    );

    private final RateLimiter limiter = new RateLimiter(MAX_TRACKED_IPS);
    private final String trustForwardedFor;

    public RateLimitInterceptor(@Value("${app.rate-limit.trust-forwarded-for:auto}") String trustForwardedFor) {
        this.trustForwardedFor = trustForwardedFor == null ? "auto" : trustForwardedFor.trim().toLowerCase(Locale.ROOT);
    }

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

    /**
     * 限流按哪个地址计桶。
     *
     * <p>{@code X-Forwarded-For} 是客户端可以自己编的头，无条件取第一跳等于把限流形同虚设：
     * 每个请求换一个假 IP 就能永远不被挡。默认只在直连对端本来就是内网/回环地址时相信它，
     * 也就是「前面确实有一台代理」的形态（Docker 网络里的 nginx、1Panel 的转发都算）。
     *
     * <p>站点直接暴露公网时不看这个头，只按真实来源计；
     * 真有代理但来源是公网地址的部署，用 {@code app.rate-limit.trust-forwarded-for=always} 明说。
     */
    String clientIp(HttpServletRequest request) {
        String peer = request.getRemoteAddr();
        String forwarded = firstForwardedFor(request.getHeader("X-Forwarded-For"));
        boolean trust = switch (trustForwardedFor) {
            case "always" -> forwarded != null;
            case "never" -> false;
            default -> forwarded != null && isProxyPeer(peer);
        };
        if (trust) {
            return forwarded;
        }
        return peer == null || peer.isBlank() ? "unknown" : peer;
    }

    private static String firstForwardedFor(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        int comma = header.indexOf(',');
        String first = (comma > 0 ? header.substring(0, comma) : header).trim();
        return first.isEmpty() ? null : first;
    }

    /** 直连对端是不是「本机或内网」，即前面有反向代理的常见形态。 */
    private static boolean isProxyPeer(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(ip);
            if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()) {
                return true;
            }
            byte[] bytes = address.getAddress();
            // IPv6 的 fc00::/7 不在 isSiteLocalAddress 的覆盖范围内，但容器网络里很常见。
            return bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
        } catch (UnknownHostException e) {
            // 地址解析不了就当不可信：宁可多算同一个桶，也不要给伪造头留出空子。
            return false;
        }
    }

    private record Rule(String action, int limit) {
    }
}
