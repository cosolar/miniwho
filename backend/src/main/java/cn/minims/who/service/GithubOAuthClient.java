package cn.minims.who.service;

import cn.minims.who.common.BizException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * GitHub OAuth 的纯外部通道：拼授权地址、换 token、取身份，不碰账号表。
 * <p>拆成独立一类的理由很实际：「凭据配了没有」这个判断要能被设置面板读取，
 * 而登录流程本身要复用账号服务，合在一起会让两边互相依赖。
 * <p>凭据每次现取（后台填的优先，回落到环境变量），所以管理员在后台改完不必重启。
 */
@Service
public class GithubOAuthClient {

    private static final String AUTHORIZE_URL = "https://github.com/login/oauth/authorize";
    private static final String TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String USER_URL = "https://api.github.com/user";
    private static final String EMAILS_URL = "https://api.github.com/user/emails";
    /** read:user 给姓名，user:email 才有机会拿到邮箱；只用这两个，不申请任何写权限。 */
    private static final String SCOPE = "read:user user:email";

    private final SettingsService settings;
    private final String envClientId;
    private final String envClientSecret;
    private final String envBaseUrl;
    private final RestClient http;

    public GithubOAuthClient(SettingsService settings,
                             @Value("${app.oauth.github.client-id:}") String clientId,
                             @Value("${app.oauth.github.client-secret:}") String clientSecret,
                             @Value("${app.public-base-url:}") String baseUrl,
                             RestClient.Builder builder) {
        this.settings = settings;
        this.envClientId = clientId;
        this.envClientSecret = clientSecret;
        this.envBaseUrl = baseUrl;
        // GitHub 侧偶尔会拖到十几秒，没有超时的话一次登录就能长期占住一个请求线程。
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.http = builder.requestFactory(factory).build();
    }

    public String clientId() {
        return settings.or(SettingsService.GITHUB_CLIENT_ID, envClientId);
    }

    public String clientSecret() {
        return settings.or(SettingsService.GITHUB_CLIENT_SECRET, envClientSecret);
    }

    /** 三项都齐才算可用：回调地址要落在站点对外地址上，GitHub 应用那边才填得出来。 */
    public boolean configured() {
        return notBlank(clientId()) && notBlank(clientSecret()) && publicBaseUrl() != null;
    }

    /** 这套凭据当前是不是后台维护的；false 表示值来自环境变量。 */
    public boolean configuredByAdmin() {
        return settings.present(SettingsService.GITHUB_CLIENT_ID);
    }

    public String callbackUrl() {
        String base = publicBaseUrl();
        return base == null ? null : base + "/api/auth/oauth/github/callback";
    }

    /** 站点对外地址原样给后台看，管理员据此核对是不是忘了填或填了内网地址。 */
    public String publicBaseUrl() {
        String base = settings.or(SettingsService.PUBLIC_BASE_URL, envBaseUrl);
        return notBlank(base) ? trim(base) : null;
    }

    /** 登录完成后把浏览器送回前端的哪个地址；单镜像部署下与站点同源。 */
    public String frontLoginUrl() {
        String base = publicBaseUrl();
        if (base == null) {
            throw fail("站点对外地址未配置");
        }
        return base + "/login";
    }

    public String authorizeUrl(String state) {
        return AUTHORIZE_URL + "?client_id=" + clientId()
                + "&redirect_uri=" + encode(callbackUrl())
                + "&scope=" + encode(SCOPE)
                + "&state=" + encode(state);
    }

    /** 用授权码换 access token，再取回 GitHub 侧的账号身份。 */
    public GithubProfile fetchProfile(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId());
        form.add("client_secret", clientSecret());
        form.add("code", code);
        form.add("redirect_uri", callbackUrl());

        JsonNode token = call(() -> http.post()
                .uri(TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(form)
                .retrieve()
                .body(JsonNode.class));
        String accessToken = token == null ? null : token.path("access_token").asText(null);
        if (accessToken == null || accessToken.isBlank()) {
            throw fail(token == null ? "GitHub 没有返回令牌" : token.path("error_description").asText("授权失败"));
        }

        JsonNode user = call(() -> http.get()
                .uri(USER_URL)
                .accept(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(JsonNode.class));
        if (user == null || !user.path("id").canConvertToLong()) {
            throw fail("未能读取 GitHub 账号信息");
        }
        JsonNode emails = call(() -> http.get()
                .uri(EMAILS_URL)
                .accept(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(JsonNode.class));
        return new GithubProfile(String.valueOf(user.path("id").asLong()),
                user.path("login").asText(null),
                user.path("name").asText(null),
                primaryVerifiedEmail(emails));
    }

    /**
     * /user/emails 只会把用户自己在 GitHub 上验证过的邮箱标成 verified；未验证的一律不要，
     * 这样「按邮箱关联本地账号」命中的永远是两边都确认过的地址。
     */
    private static String primaryVerifiedEmail(JsonNode emails) {
        if (emails == null || !emails.isArray()) {
            return null;
        }
        String fallback = null;
        for (JsonNode item : emails) {
            if (!item.path("verified").asBoolean(false)) {
                continue;
            }
            String email = item.path("email").asText(null);
            if (email == null || email.isBlank()) {
                continue;
            }
            String normalized = email.trim().toLowerCase(Locale.ROOT);
            if (item.path("primary").asBoolean(false)) {
                return normalized;
            }
            fallback = normalized;
        }
        return fallback;
    }

    private static JsonNode call(Supplier<JsonNode> request) {
        try {
            return request.get();
        } catch (RestClientException e) {
            // 不把 GitHub 的原始报错文案直接回显出去，避免被当成探测信息。
            throw new BizException("OAUTH_FAILED", "GitHub 服务暂时不可用，请稍后重试", HttpStatus.BAD_GATEWAY);
        }
    }

    private static BizException fail(String reason) {
        return new BizException("OAUTH_FAILED", "GitHub 登录没有完成：" + reason, HttpStatus.BAD_GATEWAY);
    }

    private static String trim(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** GitHub 侧的账号身份；email 可能为空，用户可以把邮箱设为完全不公开。 */
    public record GithubProfile(String userId, String login, String name, String email) {
    }
}
