package cn.minims.who.service;

import cn.minims.who.common.BizException;
import org.springframework.stereotype.Service;

/** GitHub 登录的流程编排：开关判断、state 核销、拿身份、交给账号服务落账号。 */
@Service
public class GithubOAuthService {

    public static final String PROVIDER = "github";

    private final GithubOAuthClient client;
    private final OAuthStateStore states;
    private final AuthSettingsService authSettings;
    private final AuthService authService;

    public GithubOAuthService(GithubOAuthClient client, OAuthStateStore states,
                              AuthSettingsService authSettings, AuthService authService) {
        this.client = client;
        this.states = states;
        this.authSettings = authSettings;
        this.authService = authService;
    }

    /** 发起跳转前先发一枚 state，回调时必须被原样还回来。 */
    public String begin() {
        requireEnabled();
        return client.authorizeUrl(states.issue());
    }

    /** 回调：换取身份并按「首登自动建号」策略登录，返回给前端用的 token。 */
    public String complete(String code, String state) {
        requireEnabled();
        // state 先核销再谈别的：无论是重放还是伪造，一枚 state 只允许换一次登录。
        if (!states.consume(state)) {
            throw BizException.badRequest("登录请求已失效，请重新点击 GitHub 登录");
        }
        if (code == null || code.isBlank()) {
            throw BizException.badRequest("GitHub 授权未完成，请重试");
        }
        GithubOAuthClient.GithubProfile profile = client.fetchProfile(code);
        return authService.loginByProvider(PROVIDER, profile.userId(), profile.login(),
                profile.name(), profile.email());
    }

    public String frontLoginUrl() {
        return client.frontLoginUrl();
    }

    private void requireEnabled() {
        if (!authSettings.githubLoginEnabled()) {
            throw BizException.forbidden("管理员未开放 GitHub 登录");
        }
    }
}
