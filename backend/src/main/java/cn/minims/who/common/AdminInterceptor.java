package cn.minims.who.common;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 识别 {@link RequireAdmin} 并校验 admin 角色。
 *
 * <p>注册在 Sa-Token 的登录拦截器之后，所以到这里用户一定已登录；
 * 未登录仍是 401，登录但非管理员由 NotRoleException 映射为 403。
 *
 * <p>除了注解，还按 {@code /api/admin/**} 前缀兜底：后台目录下新加的控制器
 * 即使漏了注解也必须是管理员，不能靠人记得加。
 */
@Component
public class AdminInterceptor implements HandlerInterceptor {

    static final String ADMIN_PREFIX = "/api/admin/";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 预检请求不带 token，放行后由浏览器发真实请求再校验。
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || !(handler instanceof HandlerMethod method)) {
            return true;
        }
        boolean annotated = method.hasMethodAnnotation(RequireAdmin.class)
                || method.getBeanType().isAnnotationPresent(RequireAdmin.class);
        if (annotated || isAdminPath(request)) {
            StpUtil.checkRole("admin");
        }
        return true;
    }

    private boolean isAdminPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return path.equals("/api/admin") || path.startsWith(ADMIN_PREFIX);
    }
}
