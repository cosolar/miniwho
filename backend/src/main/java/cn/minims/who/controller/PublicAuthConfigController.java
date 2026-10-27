package cn.minims.who.controller;

import cn.minims.who.common.ApiResponse;
import cn.minims.who.dto.AuthDtos.AuthConfigVo;
import cn.minims.who.service.AuthSettingsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * 登录页要按这个结果决定渲染哪些入口，所以必须匿名可读。
 * <p>只回三个布尔值，不含通道细节；管理员关掉开关后最多 30 秒在前台生效，
 * 换来的是不必每次开登录页都打一次库。
 */
@Tag(name = "分享与公开页")
@RestController
@RequestMapping("/api/public")
public class PublicAuthConfigController {

    private final AuthSettingsService authSettings;

    public PublicAuthConfigController(AuthSettingsService authSettings) {
        this.authSettings = authSettings;
    }

    @GetMapping("/auth-config")
    public ResponseEntity<ApiResponse<AuthConfigVo>> authConfig() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(30)).cachePrivate())
                .body(ApiResponse.ok(authSettings.publicConfig()));
    }
}
