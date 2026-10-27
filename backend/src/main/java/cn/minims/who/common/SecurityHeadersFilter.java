package cn.minims.who.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 站点自身的响应头收口。
 *
 * <p>CSP 只发给页面（非 {@code /api}）：接口响应是 JSON 与文件，加了没有意义，
 * 而附件预览与 PDF 已经各自带着更严格的 {@code sandbox} 策略，不能被这里覆盖。
 *
 * <p>策略里没有 {@code unsafe-eval}，也没有任何内联脚本：构建产物只有一枚
 * {@code <script type="module" src>}。样式必须留 {@code 'unsafe-inline'}，
 * 因为导入模板的 CSS 与 Element Plus 都是在运行时以 {@code <style>} 注入的，
 * 这一项换来的是模板可自定义，代价由服务端的 CSS 净化器兜住。
 */
public class SecurityHeadersFilter extends OncePerRequestFilter {

    static final String PAGE_CSP = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self' 'unsafe-inline'",
            "img-src 'self' data: blob:",
            "font-src 'self' data:",
            "connect-src 'self'",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'self'",
            "worker-src 'self'");

    /** 只在真正走 HTTPS 时发：明文站点发 HSTS 会把用户之后的 http 访问也一起锁死。 */
    static final String HSTS = "max-age=31536000";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/api/")) {
            response.setHeader("Content-Security-Policy", PAGE_CSP);
        }
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "same-origin");
        response.setHeader("X-Frame-Options", "SAMEORIGIN");
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=()");
        if (isHttps(request)) {
            response.setHeader("Strict-Transport-Security", HSTS);
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 反向代理终结 TLS 时容器只知道自己是 http，因此除了直连 TLS 还要认
     * {@code X-Forwarded-Proto}；这里只用来决定要不要发 HSTS，不参与任何鉴权判断。
     */
    private static boolean isHttps(HttpServletRequest request) {
        if (request.isSecure()) {
            return true;
        }
        String proto = request.getHeader("X-Forwarded-Proto");
        return proto != null && "https".equalsIgnoreCase(proto.split(",")[0].trim());
    }
}
