package cn.minims.who.service;

import cn.minims.who.common.BizException;
import cn.minims.who.dto.AdminDtos.AdminAuthSettingsRequest;
import cn.minims.who.mapper.SiteSettingMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 后台凭据与开关的落库顺序：先校验入参，再存凭据，最后判开关就绪。
 * <p>顺序错两处都会命中真实使用姿势：填完凭据顺手打开开关的那一次保存会被顶回去，
 * 以及端口填错时同一条请求里前面的字段已经悄悄写进库了。
 */
class AuthSettingsServiceTest {

    private final Map<String, String> rows = new HashMap<>();

    private final SiteSettingMapper mapper = new SiteSettingMapper() {
        @Override
        public String selectValue(String key) {
            return rows.get(key);
        }

        @Override
        public int updateValue(String key, String value, long now) {
            if (!rows.containsKey(key)) {
                return 0;
            }
            rows.put(key, value);
            return 1;
        }

        @Override
        public int insertValue(String key, String value, long now) {
            rows.put(key, value);
            return 1;
        }
    };

    private final SettingsService settings = new SettingsService(mapper);
    private final MailService mail = new MailService(settings);
    private final GithubOAuthClient github = new GithubOAuthClient(settings, "", "", "", RestClient.builder());
    private final AuthSettingsService service = new AuthSettingsService(settings, mail, github);

    @Test
    void 填完凭据的同一次保存就能打开对应开关() {
        service.apply(githubChannel("Iv1.client", "secret", "https://who.example.com", true));

        assertThat(github.configured()).isTrue();
        assertThat(settings.enabled(SettingsService.GITHUB_LOGIN)).isTrue();
    }

    @Test
    void 通道没就绪时开关被拒且不落库() {
        assertThatThrownBy(() -> service.apply(githubChannel(null, null, null, true)))
                .isInstanceOf(BizException.class);

        assertThat(settings.enabled(SettingsService.GITHUB_LOGIN)).isFalse();
    }

    @Test
    void 端口不合法时整条请求不写任何字段() {
        assertThatThrownBy(() -> service.apply(new AdminAuthSettingsRequest(null, null, null,
                "smtp.bad.test", "70000", null, null, null, null, null, null, null, null, null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("端口");

        assertThat(rows).isEmpty();
    }

    @Test
    void 对外地址缺协议头时整条请求不写任何字段() {
        assertThatThrownBy(() -> service.apply(githubChannel("Iv1.client", "secret", "who.example.com", null)))
                .isInstanceOf(BizException.class);

        assertThat(rows).isEmpty();
    }

    @Test
    void 传空串表示清空该项并回落到环境变量() {
        service.apply(new AdminAuthSettingsRequest(null, null, null,
                "smtp.qq.com", null, null, null, "me@qq.com", null, null, null, null, null, null));
        assertThat(mail.available()).isTrue();

        service.apply(new AdminAuthSettingsRequest(null, null, null,
                "", null, null, null, null, null, null, null, null, null, null));
        assertThat(settings.present(SettingsService.MAIL_HOST)).isFalse();
        assertThat(mail.available()).isFalse();
    }

    /**
     * 找回密码只认邮件通道，不跟着邮箱注册开关一起关：
     * 关掉注册是为了挡新用户，不该顺带把老用户「忘了密码还能自己改回来」这条路也堵死。
     */
    @Test
    void 关掉邮箱注册也不影响自助找回密码() {
        service.apply(new AdminAuthSettingsRequest(null, false, null,
                "smtp.qq.com", null, null, null, "me@qq.com", null, null, null, null, null, null));

        assertThat(mail.available()).isTrue();
        assertThat(service.emailRegisterEnabled()).isFalse();
        assertThat(service.passwordResetEnabled()).isTrue();
        assertThat(service.publicConfig().passwordReset()).isTrue();
    }

    @Test
    void 邮件通道没配好时登录页不出现找回密码() {
        assertThat(service.passwordResetEnabled()).isFalse();
        assertThat(service.publicConfig().passwordReset()).isFalse();
    }

    private static AdminAuthSettingsRequest githubChannel(String clientId, String secret, String baseUrl,
                                                          Boolean login) {
        return new AdminAuthSettingsRequest(null, null, login,
                null, null, null, null, null, null, null, null, clientId, secret, baseUrl);
    }
}
