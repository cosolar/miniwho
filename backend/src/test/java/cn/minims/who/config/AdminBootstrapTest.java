package cn.minims.who.config;

import cn.minims.who.entity.User;
import cn.minims.who.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 关掉演示数据是正式部署的常见做法：管理员是后台的唯一入口，
 * 不能因为不种 demo 就一个管理员都不建。
 */
@SpringBootTest(properties = {
        "app.seed-demo=false",
        "app.admin-password=bootstrap-admin",
        "app.media-secret=bootstrap-media-secret",
        "app.pdf.enabled=false"
})
class AdminBootstrapTest {

    private static final Path WORK_DIR = createWorkDir();

    @DynamicPropertySource
    static void runtimeProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + WORK_DIR.resolve("bootstrap.db")
                + "?journal_mode=WAL&busy_timeout=5000&foreign_keys=on");
        registry.add("app.upload-dir", () -> WORK_DIR.resolve("uploads").toString());
    }

    @Autowired
    private UserMapper userMapper;

    @Test
    void createsAdminEvenWhenDemoSeedingIsOff() {
        User admin = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, "admin").last("limit 1"));
        assertThat(admin).isNotNull();
        assertThat(admin.getRole()).isEqualTo("admin");
        assertThat(admin.getStatus()).isEqualTo(1);

        assertThat(userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, "demo")))
                .isZero();
    }

    private static Path createWorkDir() {
        try {
            return Files.createTempDirectory("miniwho-bootstrap");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
