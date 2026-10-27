package cn.minims.who.controller;

import cn.minims.who.common.BizException;
import cn.minims.who.service.GithubOAuthService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * GitHub 登录的两个浏览器跳转入口。
 * <p>这两个接口不是给 XHR 用的：浏览器直接导航进来，所以只能回 302。
 * 令牌用 URL fragment 交回前端——fragment 不进服务器日志、也不被历史记录直接可读，
 * 前端读到手就把它从地址栏抹掉。
 */
@Slf4j
@Tag(name = "认证与账号")
@RestController
@RequestMapping("/api/auth/oauth")
public class OauthController {

    private final GithubOAuthService github;

    public OauthController(GithubOAuthService github) {
        this.github = github;
    }

    @GetMapping("/github")
    public ResponseEntity<Void> begin() {
        try {
            return redirect(github.begin());
        } catch (BizException e) {
            // 开关刚被管理员关掉或 state 表撑满时，浏览器导航同样只能回登录页，JSON 没人看得懂。
            log.info("GitHub 登录发起被拒：{}", e.getMessage());
            return redirect(github.frontLoginUrl() + "#oauth_error=1");
        }
    }

    @GetMapping("/github/callback")
    public ResponseEntity<Void> callback(@RequestParam(name = "code", required = false) String code,
                                         @RequestParam(name = "state", required = false) String state) {
        String token;
        try {
            token = github.complete(code, state);
        } catch (BizException e) {
            // 用户在 GitHub 页面点了取消、或同一个回调地址被重复打开，都只是正常失败，
            // 但浏览器导航没法渲染 JSON，统一送回登录页由前端提示。
            log.info("GitHub 登录回调未完成：{}", e.getMessage());
            return redirect(github.frontLoginUrl() + "#oauth_error=1");
        }
        return redirect(github.frontLoginUrl() + "#satoken=" + token);
    }

    private static ResponseEntity<Void> redirect(String location) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .cacheControl(CacheControl.noStore())
                .location(URI.create(location))
                .build();
    }
}
