package cn.minims.who;

import cn.hutool.crypto.digest.BCrypt;
import cn.minims.who.entity.EmailCode;
import cn.minims.who.entity.User;
import cn.minims.who.mapper.EmailCodeMapper;
import cn.minims.who.mapper.UserMapper;
import cn.minims.who.service.EmailCodeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
 * 验证码登录全链路：验证码 → 登录 → 免原密码改密 → 用新密码再登录。
 * 邮件通道不参与：这里直接往存储里放一枚已知答案的 login 验证码。
 */
@SpringBootTest(properties = {
        "app.seed-demo=true",
        "app.demo-password=code-login-demo",
        "app.admin-password=code-login-admin",
        "app.media-secret=code-login-media-secret",
        "app.pdf.enabled=false",
        "app.captcha.enabled=false",
        "app.rate-limit.trust-forwarded-for=never"
})
@AutoConfigureMockMvc
class LoginCodeFlowTest {

    private static final String CODE = "123456";
    private static final String NEW_PASSWORD = "newpass123";
    private static final Path WORK_DIR = createWorkDir();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private EmailCodeMapper codeMapper;

    @DynamicPropertySource
    static void runtimeProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + WORK_DIR.resolve("code-login.db")
                + "?journal_mode=WAL&busy_timeout=5000&foreign_keys=on");
        registry.add("app.upload-dir", () -> WORK_DIR.resolve("uploads").toString());
    }

    @Test
    void codeLoginThenPasswordChangeWithoutTheOldOne() throws Exception {
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
                .andExpect(jsonPath("$.data.passwordChangeSuggested").value(true))
                .andReturn().getResponse().getContentAsString();
        String token = body.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(put("/api/auth/password-after-code")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isOk());

        // 票据用一次就没了：同一个会话再改一次必须被拒。
        mockMvc.perform(put("/api/auth/password-after-code")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"another123\"}"))
                .andExpect(status().isForbidden());

        // 改完之后新密码立即生效（滑块验证码在测试里关掉了，见类上的 app.captcha.enabled）。
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"demo\",\"password\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isOk());
    }

    private static Path createWorkDir() {
        try {
            return Files.createTempDirectory("miniwho-code-login");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
