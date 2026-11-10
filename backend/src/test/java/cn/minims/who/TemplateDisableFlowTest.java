package cn.minims.who;

import cn.hutool.crypto.digest.BCrypt;
import cn.minims.who.entity.User;
import cn.minims.who.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 停用模板的语义：不能被新简历选用，但已经在用的简历要照常渲染；
 * 没人用的停用模板对普通访客不暴露存在性，管理员仍能查看。
 */
@SpringBootTest(properties = {
        "app.seed-demo=true",
        "app.demo-password=disable-demo",
        "app.admin-password=disable-admin",
        "app.media-secret=disable-media-secret",
        "app.pdf.enabled=false",
        "app.captcha.enabled=false",
        "app.rate-limit.trust-forwarded-for=never"
})
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TemplateDisableFlowTest {

    private static final Path WORK_DIR = createWorkDir();
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserMapper userMapper;

    @DynamicPropertySource
    static void runtimeProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + WORK_DIR.resolve("disable.db")
                + "?journal_mode=WAL&busy_timeout=5000&foreign_keys=on");
        registry.add("app.upload-dir", () -> WORK_DIR.resolve("uploads").toString());
    }

    @Test
    @Order(1)
    void unusedDisabledTemplateDisappearsFromCatalogForVisitorsButNotAdmin() throws Exception {
        String admin = login("admin", "disable-admin");
        disable(admin, "bijian");

        mockMvc.perform(get("/api/templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].code", not(hasItem("bijian"))));

        mockMvc.perform(get("/api/templates/bijian"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/templates/bijian").header("satoken", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("bijian"));
    }

    @Test
    @Order(2)
    void inUseDisabledTemplateStillRendersButCannotBeSelected() throws Exception {
        String admin = login("admin", "disable-admin");
        String demo = login("demo", "disable-demo");

        disable(admin, "qinghe");

        // 演示简历正在用 qinghe：配置仍然可读，简历照常打开。
        mockMvc.perform(get("/api/templates/qinghe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("qinghe"));
        mockMvc.perform(get("/api/resumes/1").header("satoken", demo))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/resumes")
                        .header("satoken", demo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"blocked\",\"language\":\"zh\",\"templateCode\":\"qinghe\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("已下架")));

        mockMvc.perform(put("/api/resumes/1/style")
                        .header("satoken", demo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateCode\":\"bijian\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("已下架")));

        // 沿用当前模板（即使它已停用）不属于「新选用」，不能被锁死。
        mockMvc.perform(put("/api/resumes/1/style")
                        .header("satoken", demo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateCode\":\"qinghe\",\"fontSizeStep\":1}"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(3)
    void nonAuthorKeepsUsingDisabledImportedTemplateButCannotPickItAgain() throws Exception {
        String admin = login("admin", "disable-admin");
        String demo = login("demo", "disable-demo");
        ensureViewerUser();
        String viewer = login("viewer", "viewer-demo");

        importSharedPackage(demo);
        mockMvc.perform(put("/api/templates/probe--shared/share")
                        .header("satoken", demo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shared\":true,\"exportable\":true}"))
                .andExpect(status().isOk());

        // 分享是作者的署名决定：管理员能看见状态，但不能代设。
        mockMvc.perform(put("/api/templates/probe--shared/share")
                        .header("satoken", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shared\":false,\"exportable\":false}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/templates").header("satoken", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.code == 'probe--shared')].visibility").value(hasItem("public")));

        byte[] createdBody = mockMvc.perform(post("/api/resumes")
                        .header("satoken", viewer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"uses shared\",\"language\":\"zh\",\"templateCode\":\"probe--shared\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        long resumeId = JSON.readTree(new String(createdBody, java.nio.charset.StandardCharsets.UTF_8))
                .path("data").path("id").asLong();

        disable(admin, "probe--shared");

        // 非作者、模板已停用，但简历还在用：配置照样能读，简历照常打开。
        mockMvc.perform(get("/api/templates/probe--shared").header("satoken", viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("probe--shared"));
        mockMvc.perform(get("/api/resumes/" + resumeId).header("satoken", viewer))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/templates").header("satoken", viewer))
                .andExpect(jsonPath("$.data[*].code", not(hasItem("probe--shared"))));
        mockMvc.perform(post("/api/resumes")
                        .header("satoken", viewer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"blocked\",\"language\":\"zh\",\"templateCode\":\"probe--shared\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("已下架")));

        // 换走可以，换回来不行：停用后它不再是可选项。
        mockMvc.perform(put("/api/resumes/" + resumeId + "/style")
                        .header("satoken", viewer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateCode\":\"zifeng\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/resumes/" + resumeId + "/style")
                        .header("satoken", viewer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateCode\":\"probe--shared\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("已下架")));
    }

    /** 第二个普通账号：用来验证「非作者但已在用」这条分支，注册通道不一定开，直接落库。 */
    private void ensureViewerUser() {
        long exists = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, "viewer"));
        if (exists > 0) {
            return;
        }
        User user = new User();
        user.setUsername("viewer");
        user.setPasswordHash(BCrypt.hashpw("viewer-demo"));
        user.setNickname("旁观者");
        user.setEmail("viewer@miniwho.local");
        user.setRole("user");
        user.setStatus(1);
        user.setHasPassword(1);
        user.setEmailVerified(0);
        userMapper.insert(user);
    }

    /** 作者导入一个只有骨架的最小模板包，用来验证「别人的在用模板」这条分支。 */
    private void importSharedPackage(String authorToken) throws Exception {
        mockMvc.perform(post("/api/templates/import")
                        .header("satoken", authorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "api": 1,
                                  "id": { "namespace": "probe", "code": "shared", "version": "1.0.0" },
                                  "meta": { "name": "共享测试", "description": "停用语义用例",
                                            "category": "通用", "tags": [] },
                                  "theme": { "layout": "classic", "accent": "#123456",
                                             "font": "sans", "sectionStyle": "icon" }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("probe--shared"));
    }

    private void disable(String adminToken, String code) throws Exception {
        mockMvc.perform(put("/api/admin/templates/" + code + "/status")
                        .header("satoken", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":2}"))
                .andExpect(status().isOk());
    }

    private String login(String username, String password) throws Exception {
        // 滑块验证码在这里关掉：它要真拖一次才出票据，本用例关心的是登录之后的模板关卡。
        // 响应头不带 charset，getContentAsString() 会按 ISO-8859-1 解码；中文昵称要按 UTF-8 读回来才能解析。
        byte[] loginBody = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        return JSON.readTree(new String(loginBody, java.nio.charset.StandardCharsets.UTF_8))
                .path("data").path("token").asText();
    }

    private static Path createWorkDir() {
        try {
            return Files.createTempDirectory("miniwho-disable");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
