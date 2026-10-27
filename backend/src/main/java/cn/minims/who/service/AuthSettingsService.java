package cn.minims.who.service;

import cn.minims.who.common.BizException;
import org.springframework.stereotype.Service;

import static cn.minims.who.dto.AdminDtos.AdminAuthSettingsRequest;
import static cn.minims.who.dto.AdminDtos.AdminAuthSettingsVo;
import static cn.minims.who.dto.AdminDtos.AdminGithubChannelVo;
import static cn.minims.who.dto.AdminDtos.AdminMailChannelVo;
import static cn.minims.who.dto.AuthDtos.AuthConfigVo;

/**
 * 「哪些注册 / 登录方式此刻真的能用」的唯一判定点。
 * <p>管理员开关只是意愿，通道（SMTP、GitHub 凭据）就绪才是事实，两者取交集后
 * 才对外公开：否则前台会出现点了必失败的注册入口。
 */
@Service
public class AuthSettingsService {

    private static final String MAIL_MISSING =
            "邮件通道尚未配置，请先在本页填写 SMTP 主机、发件地址与授权码（也可用 MINIWHO_MAIL_* 环境变量）";
    private static final String GITHUB_MISSING =
            "GitHub OAuth 尚未配置，请先在本页填写 Client ID、Client Secret 与站点对外地址"
                    + "（也可用 MINIWHO_GITHUB_* / MINIWHO_PUBLIC_BASE_URL 环境变量）";
    private static final String SMTP_PORT_INVALID = "SMTP 端口要填 1–65535 之间的数字";
    private static final String BASE_URL_INVALID = "站点对外地址要以 http:// 或 https:// 开头";

    private final SettingsService settings;
    private final MailService mailService;
    private final GithubOAuthClient github;

    public AuthSettingsService(SettingsService settings, MailService mailService, GithubOAuthClient github) {
        this.settings = settings;
        this.mailService = mailService;
        this.github = github;
    }

    public boolean usernameRegisterEnabled() {
        return settings.enabled(SettingsService.REGISTER_USERNAME);
    }

    public boolean emailRegisterEnabled() {
        return settings.enabled(SettingsService.REGISTER_EMAIL) && mailService.available();
    }

    public boolean githubLoginEnabled() {
        return settings.enabled(SettingsService.GITHUB_LOGIN) && github.configured();
    }

    /**
     * 自助找回密码只看邮件通道，不看注册开关：管理员完全可以关掉注册但保留重置密码，
     * 否则存量用户一旦忘了密码就只剩「重装系统」这一条路。
     */
    public boolean passwordResetEnabled() {
        return mailService.available();
    }

    /** 换绑邮箱的验证码走同一条 SMTP 通道；通道不在，新地址的归属就无从证明，换绑整体不可用。 */
    public boolean changeEmailEnabled() {
        return mailService.available();
    }

    /** 登录页读的公开配置：只说结论，不暴露通道配到什么程度。 */
    public AuthConfigVo publicConfig() {
        return new AuthConfigVo(usernameRegisterEnabled(), emailRegisterEnabled(),
                githubLoginEnabled(), passwordResetEnabled());
    }

    /** 后台面板：除了开关，还要把依赖状态、当前生效的通道配置和回调地址一起给出，管理员才知道下一步该填什么。 */
    public AdminAuthSettingsVo adminConfig() {
        return new AdminAuthSettingsVo(
                usernameRegisterEnabled(),
                settings.enabled(SettingsService.REGISTER_EMAIL),
                settings.enabled(SettingsService.GITHUB_LOGIN),
                mailService.available(),
                github.configured(),
                github.callbackUrl(),
                github.publicBaseUrl(),
                new AdminMailChannelVo(mailService.host(), String.valueOf(mailService.port()),
                        mailService.username(), mailService.from(), mailService.fromName(),
                        mailService.ssl(), mailService.starttls(), mailService.passwordConfigured(),
                        mailService.configuredByAdmin()),
                new AdminGithubChannelVo(github.clientId(), github.publicBaseUrl(),
                        notBlank(github.clientSecret()), github.configuredByAdmin()));
    }

    /**
     * 传 null 表示不动该项，字符串传空串表示清空该项并回落到环境变量。
     * <p>顺序是「先校验入参，再落凭据，最后判开关」：开关就绪与否要按这次刚存的凭据来判，
     * 否则管理员在同一次保存里填完凭据再打开开关，会被上一条还读不到新值的检查顶回去。
     */
    public void apply(AdminAuthSettingsRequest request) {
        String mailPort = normalizeMailPort(request.mailPort());
        String baseUrl = normalizePublicBaseUrl(request.publicBaseUrl());

        putText(SettingsService.MAIL_HOST, request.mailHost());
        putText(SettingsService.MAIL_USERNAME, request.mailUsername());
        putText(SettingsService.MAIL_PASSWORD, request.mailPassword());
        putText(SettingsService.MAIL_FROM, request.mailFrom());
        putText(SettingsService.MAIL_FROM_NAME, request.mailFromName());
        putBool(SettingsService.MAIL_SSL, request.mailSsl());
        putBool(SettingsService.MAIL_STARTTLS, request.mailStarttls());
        putText(SettingsService.GITHUB_CLIENT_ID, request.githubClientId());
        putText(SettingsService.GITHUB_CLIENT_SECRET, request.githubClientSecret());
        if (mailPort != null) {
            settings.set(SettingsService.MAIL_PORT, mailPort);
        }
        if (baseUrl != null) {
            settings.set(SettingsService.PUBLIC_BASE_URL, baseUrl);
        }

        if (request.usernameRegister() != null) {
            settings.setEnabled(SettingsService.REGISTER_USERNAME, request.usernameRegister());
        }
        if (request.emailRegister() != null) {
            if (request.emailRegister() && !mailService.available()) {
                throw BizException.badRequest(MAIL_MISSING);
            }
            settings.setEnabled(SettingsService.REGISTER_EMAIL, request.emailRegister());
        }
        if (request.githubLogin() != null) {
            if (request.githubLogin() && !github.configured()) {
                throw BizException.badRequest(GITHUB_MISSING);
            }
            settings.setEnabled(SettingsService.GITHUB_LOGIN, request.githubLogin());
        }
    }

    /** 返回入库用的值：null 表示不动，空串表示清空并回落到环境变量。 */
    private String normalizeMailPort(String requested) {
        if (requested == null) {
            return null;
        }
        String port = requested.trim();
        if (port.isEmpty()) {
            return "";
        }
        int parsed;
        try {
            parsed = Integer.parseInt(port);
        } catch (NumberFormatException malformed) {
            throw BizException.badRequest(SMTP_PORT_INVALID);
        }
        if (parsed < 1 || parsed > 65535) {
            throw BizException.badRequest(SMTP_PORT_INVALID);
        }
        return String.valueOf(parsed);
    }

    private String normalizePublicBaseUrl(String requested) {
        if (requested == null) {
            return null;
        }
        String url = requested.trim();
        // 这个地址同时是登录完成后浏览器被送回的地方，填成非本站地址等于开放重定向，只认绝对地址。
        if (!url.isEmpty() && !url.matches("(?i)https?://[^/]+.*")) {
            throw BizException.badRequest(BASE_URL_INVALID);
        }
        return url;
    }

    private void putText(String key, String value) {
        if (value != null) {
            settings.set(key, value);
        }
    }

    private void putBool(String key, Boolean value) {
        if (value != null) {
            settings.set(key, String.valueOf(value));
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
