package cn.minims.who.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.cors-origins:*}")
    private String corsOrigins;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> SaRouter
                .match("/api/**")
                .notMatch("/api/auth/login", "/api/auth/register", "/api/health",
                        "/api/templates/**", "/api/public/**")
                .check(route -> StpUtil.checkLogin())
        ) {
            // 浏览器预检请求不带 token，必须放行，否则跨域部署时预检会抛 NotLoginException。
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                    throws Exception {
                if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
                    return true;
                }
                return super.preHandle(request, response, handler);
            }
        }).addPathPatterns("/api/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = Arrays.stream(corsOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
        // 自部署的域名/IP 千差万别，默认放行任意来源；认证走 satoken 请求头而非 Cookie，
        // 因此不开 allowCredentials，通配符不会让第三方站点读到登录态数据。
        // 需要收紧时把 MINIWHO_CORS_ORIGINS 设为逗号分隔的具体来源即可。
        if (origins.length == 0 || Arrays.asList(origins).contains("*")) {
            origins = new String[]{"*"};
        }
        registry.addMapping("/api/**")
                .allowedOriginPatterns(origins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Content-Disposition")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
