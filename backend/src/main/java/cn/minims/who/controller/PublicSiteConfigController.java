package cn.minims.who.controller;

import cn.minims.who.common.ApiResponse;
import cn.minims.who.dto.SiteDtos.SiteConfigVo;
import cn.minims.who.service.SettingsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 页面布局要按这个结果决定内容区宽度，所以必须匿名可读。
 * <p>只有一个整数，但浏览器不能缓存：管理员保存宽度后刷新就要看到效果，
 * 带 max-age 的版本会让「保存了却没变」持续半分钟，看起来像功能坏了。
 */
@Tag(name = "分享与公开页")
@RestController
@RequestMapping("/api/public")
public class PublicSiteConfigController {

    private final SettingsService settings;

    public PublicSiteConfigController(SettingsService settings) {
        this.settings = settings;
    }

    @GetMapping("/site-config")
    public ResponseEntity<ApiResponse<SiteConfigVo>> siteConfig() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.ok(new SiteConfigVo(settings.contentWidth())));
    }
}
