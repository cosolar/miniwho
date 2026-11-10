package cn.minims.who;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

/**
 * HTTP 层冒烟：单元用例覆盖的是各自的服务，这里补上只有整条链跑起来才会暴露的问题——
 * 过滤器与拦截器有没有真的挂上、匿名接口能不能进、错误响应的外层信封长什么样。
 */
@SpringBootTest(properties = {
        "app.seed-demo=true",
        "app.demo-password=smoke-demo",
        "app.admin-password=smoke-admin",
        "app.media-secret=smoke-media-secret",
        "app.pdf.enabled=false",
        "app.captcha.enabled=false",
        "app.rate-limit.trust-forwarded-for=never"
})
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HttpSmokeTest {

    /** 一个用例跑完就丢的库与上传目录：冒烟不该碰开发用的 miniwho.db。 */
    private static final Path WORK_DIR = createWorkDir();

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void runtimeProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + WORK_DIR.resolve("smoke.db")
                + "?journal_mode=WAL&busy_timeout=5000&foreign_keys=on");
        registry.add("app.upload-dir", () -> WORK_DIR.resolve("uploads").toString());
    }

    @Test
    @Order(1)
    void healthCheckReportsCoreItemsOnlyAndSkipsCsp() throws Exception {
        // 本用例把服务端 PDF 关了：状态是 DEGRADED，但响应仍是 200。
        // 容器的健康检查只认状态码，关掉导出的部署不该被编排判成不健康。
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DEGRADED"))
                .andExpect(jsonPath("$.data.checks[0].name").value("database"))
                .andExpect(jsonPath("$.data.checks[0].ok").value(true))
                .andExpect(jsonPath("$.data.checks[1].name").value("storage"))
                .andExpect(jsonPath("$.data.checks[1].ok").value(true))
                .andExpect(jsonPath("$.data.checks.length()").value(2))
                .andExpect(header().doesNotExist("Content-Security-Policy"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().doesNotExist("Strict-Transport-Security"));

        // 代理终结 TLS 时容器只知道自己是 http，HSTS 要认 X-Forwarded-Proto。
        mockMvc.perform(get("/api/health").header("X-Forwarded-Proto", "https"))
                .andExpect(status().isOk())
                .andExpect(header().string("Strict-Transport-Security", "max-age=31536000"));
    }

    @Test
    @Order(2)
    void publicEndpointsAreReadableAnonymously() throws Exception {
        mockMvc.perform(get("/api/public/auth-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
    }

    @Test
    @Order(3)
    void privateEndpointReturns401EnvelopeWhenNotLoggedIn() throws Exception {
        mockMvc.perform(get("/api/account/storage"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @Order(4)
    void loggedInUserCanReadStorageUsage() throws Exception {
        String token = login("demo", "smoke-demo");
        mockMvc.perform(get("/api/account/storage").header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usedBytes").value(0))
                .andExpect(jsonPath("$.data.quotaBytes").value(536870912));
    }

    @Test
    @Order(5)
    void onlyAdminCanReadSystemStatus() throws Exception {
        mockMvc.perform(get("/api/admin/system").header("satoken", login("demo", "smoke-demo")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/system").header("satoken", login("admin", "smoke-admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.checks[2].name").value("pdf"))
                .andExpect(jsonPath("$.data.checks[2].ok").value(false))
                .andExpect(jsonPath("$.data.quotaBytes").value(536870912));
    }

    @Test
    @Order(6)
    void neverTrustForwardedHeaderUsesDirectAddress() throws Exception {
        // 同一个进程里所有请求的直连地址都是 127.0.0.1，伪造的 X-Forwarded-For 不该另开桶。
        // 令牌不存在时业务先拒 410，第十一次才会撞到限流。
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/public/shares/smoke-token/unlock")
                            .header("X-Forwarded-For", "203.0.113." + (i + 1))
                            .contentType("application/json")
                            .content("{\"password\":\"x\"}"))
                    .andExpect(status().isGone())
                    .andExpect(jsonPath("$.code").value("SHARE_REVOKED"));
        }
        mockMvc.perform(post("/api/public/shares/smoke-token/unlock")
                        .header("X-Forwarded-For", "198.51.100.7")
                        .contentType("application/json")
                        .content("{\"password\":\"x\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }

    /**
     * 内容宽度是匿名可读的布局参数：默认 1200，越界要拒绝而不是悄悄夹紧，
     * 改完前台立刻能读到新值（HTTPS 缓存只是响应头，不影响服务端状态）。
     */
    @Test
    @Order(7)
    void contentWidthDefaultsTo1200AndRejectsOutOfRange() throws Exception {
        mockMvc.perform(get("/api/public/site-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentWidth").value(1200));

        String token = login("admin", "smoke-admin");
        mockMvc.perform(put("/api/admin/site-config")
                        .header("satoken", token)
                        .contentType("application/json")
                        .content("{\"contentWidth\":2000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mockMvc.perform(put("/api/admin/site-config")
                        .header("satoken", token)
                        .contentType("application/json")
                        .content("{\"contentWidth\":1400}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentWidth").value(1400))
                .andExpect(jsonPath("$.data.minWidth").value(1100));
        mockMvc.perform(get("/api/public/site-config"))
                .andExpect(jsonPath("$.data.contentWidth").value(1400));
    }

    @Test
    @Order(8)
    void userListExposesOnlineStatusAndPollingIsAdminOnly() throws Exception {
        String admin = login("admin", "smoke-admin");
        // 管理员这次请求本身就是一次心跳，所以在线列表里至少有他。
        mockMvc.perform(get("/api/admin/users/online").header("satoken", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0]").isNumber());
        mockMvc.perform(get("/api/admin/users").header("satoken", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].online").isBoolean());
        mockMvc.perform(get("/api/admin/users/online").header("satoken", login("demo", "smoke-demo")))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(9)
    void resumeDataCannotBeImportedAsTemplatePackage() throws Exception {
        String resume = """
                {
                  "api": 1,
                  "id": { "namespace": "qliu1", "code": "resume-agent-v5", "version": "1.1.0" },
                  "meta": { "name": "刘琦 AI开发工程师", "description": "简历内容的结构化副本", "category": "resume" },
                  "theme": { "layout": "photoheader", "accent": "#000000", "font": "sans", "sectionStyle": "icon" },
                  "sectionTitles": { "education": "教育背景" },
                  "basics": { "name": "刘琦", "phone": "15511775170" },
                  "education": [],
                  "skills": [],
                  "internships": [],
                  "projects": []
                }
                """;
        mockMvc.perform(post("/api/templates/import")
                        .header("satoken", login("demo", "smoke-demo"))
                        .contentType("application/json")
                        .content(resume))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TEMPLATE_PACKAGE_INVALID"))
                .andExpect(jsonPath("$.data.problems[0]").value(containsString("简历数据")));
    }

    @Test
    @Order(10)
    void onlyAdminCanPreviewAnotherUsersResume() throws Exception {
        mockMvc.perform(get("/api/admin/resumes/999999").header("satoken", login("demo", "smoke-demo")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/resumes/999999").header("satoken", login("admin", "smoke-admin")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    private String login(String username, String password) throws Exception {
        // 滑块验证码在冒烟里关掉了：它要真拖一次才出票据，而这里的用例关心的是登录之后的事。
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return body.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
    }

    private static Path createWorkDir() {
        try {
            return Files.createTempDirectory("miniwho-smoke");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
