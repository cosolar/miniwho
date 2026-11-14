package cn.minims.who;

import cn.hutool.crypto.digest.BCrypt;
import cn.minims.who.entity.EmailCode;
import cn.minims.who.entity.User;
import cn.minims.who.mapper.EmailCodeMapper;
import cn.minims.who.mapper.UserMapper;
import cn.minims.who.service.EmailCodeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 回归：验证码登录后设置新密码，接口收到的请求体形状不对时不该含糊地报 400。
 *
 * <p>前端把参数写成了 {@code passwordAfterCode: (newPassword) => put(url, { newPassword })}，
 * 调用处传的却是 {@code { newPassword: form.newPassword }}，于是实际发出去的是
 * {@code {"newPassword":{"newPassword":"..."}}}——字符串的位置上放了个对象，
 * Jackson 反序列化失败，接口回 400「请求体格式不正确」，用户看到的是
 * 「保存新密码」按钮一直报格式错误。
 *
 * <p>这个用例要带着登录态跑：鉴权在参数绑定之前，不登录的话两种形状都只会得到 401，
 * 测不到解析这一层。
 */
@SpringBootTest(properties = {
        "app.seed-demo=true",
        "app.demo-password=shape-demo",
        "app.admin-password=shape-admin",
        "app.media-secret=shape-media-secret",
        "app.pdf.enabled=false",
        "app.captcha.enabled=false",
        "app.rate-limit.trust-forwarded-for=never"
})
@AutoConfigureMockMvc
class PasswordAfterCodeBodyShapeTest {

    private static final String CODE = "654321";
    private static final Path WORK_DIR = createWorkDir();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private EmailCodeMapper codeMapper;

    @DynamicPropertySource
    static void runtimeProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + WORK_DIR.resolve("shape.db")
                + "?journal_mode=WAL&busy_timeout=5000&foreign_keys=on");
        registry.add("app.upload-dir", () -> WORK_DIR.resolve("uploads").toString());
    }

    @Test
    void wrongPasswordShapeFailsWithAReadableMessage() throws Exception {
        String token = loginByCode();

        // 前端曾经发出去的形状：newPassword 的位置上是个对象。
        mockMvc.perform(put("/api/auth/password-after-code")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":{\"newPassword\":\"miniwhoCosolar\"}}"))
                .andExpect(status().isBadRequest())
                // 形状不对时 Jackson 反序列化失败，回的是「请求体不是合法 JSON」。
                .andExpect(jsonPath("$.code").value("INVALID_BODY"));
    }

    @Test
    void correctPasswordShapeSucceeds() throws Exception {
        String token = loginByCode();

        mockMvc.perform(put("/api/auth/password-after-code")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"miniwhoCosolar\"}"))
                .andExpect(status().isOk());
    }

    /** 走一遍验证码登录，拿到一个带「建议改密」票据的令牌。 */
    private String loginByCode() throws Exception {
        User demo = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, "demo").last("limit 1"));
        demo.setEmail("demo@example.com");
        demo.setEmailVerified(1);
        userMapper.updateById(demo);

        EmailCode record = new EmailCode();
        record.setEmail("demo@example.com");
        record.setPurpose(EmailCodeService.PURPOSE_LOGIN);
        record.setCodeHash(BCrypt.hashpw(CODE));
        record.setExpiresAt(System.currentTimeMillis() + 10 * 60_000L);
        record.setAttempts(0);
        record.setCreatedAt(System.currentTimeMillis());
        codeMapper.insert(record);

        String body = mockMvc.perform(post("/api/auth/login-by-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"demo\",\"code\":\"" + CODE + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return body.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
    }

    private static Path createWorkDir() {
        try {
            return Files.createTempDirectory("miniwho-shape");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
