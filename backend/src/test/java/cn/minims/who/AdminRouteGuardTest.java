package cn.minims.who;

import cn.minims.who.common.RequireAdmin;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 后台路由的鉴权约定：{@code /api/admin/**} 下的每个接口都必须显式标 {@link RequireAdmin}。
 *
 * <p>拦截器已经按前缀兜底，漏注解不会真的放行；这条测试守的是「声明与实际一致」，
 * 让新增后台控制器时在编译期就被提醒，而不是等有人去翻拦截器才想起来。
 */
@SpringBootTest(properties = {
        "app.seed-demo=false",
        "app.admin-password=route-guard-admin",
        "app.media-secret=route-guard-media-secret",
        "app.pdf.enabled=false"
})
class AdminRouteGuardTest {

    private static final Path WORK_DIR = createWorkDir();

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @DynamicPropertySource
    static void runtimeProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + WORK_DIR.resolve("routes.db")
                + "?journal_mode=WAL&busy_timeout=5000&foreign_keys=on");
        registry.add("app.upload-dir", () -> WORK_DIR.resolve("uploads").toString());
    }

    @Test
    void everyAdminRouteDeclaresRequireAdmin() {
        List<String> violations = new ArrayList<>();
        int adminRoutes = 0;
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
            HandlerMethod method = entry.getValue();
            for (String pattern : entry.getKey().getPatternValues()) {
                if (!pattern.equals("/api/admin") && !pattern.startsWith("/api/admin/")) {
                    continue;
                }
                adminRoutes++;
                boolean declared = method.hasMethodAnnotation(RequireAdmin.class)
                        || method.getBeanType().isAnnotationPresent(RequireAdmin.class);
                if (!declared) {
                    violations.add(method.getBeanType().getSimpleName() + "#" + method.getMethod().getName()
                            + " -> " + pattern);
                }
            }
        }
        assertThat(adminRoutes).as("没有扫到任何后台路由，测试自身可能失效").isPositive();
        assertThat(violations).as("以下后台接口缺少 @RequireAdmin").isEmpty();
    }

    private static Path createWorkDir() {
        try {
            return Files.createTempDirectory("miniwho-routes");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
