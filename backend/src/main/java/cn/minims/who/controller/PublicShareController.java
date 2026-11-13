package cn.minims.who.controller;

import cn.minims.who.common.ApiResponse;
import cn.minims.who.dto.ShareDtos.PublicShareVo;
import cn.minims.who.dto.ShareDtos.SharePasswordRequest;
import cn.minims.who.service.ShareService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "分享与公开页")
@RestController
@RequestMapping("/api/public/shares")
public class PublicShareController {

    private final ShareService shareService;

    public PublicShareController(ShareService shareService) {
        this.shareService = shareService;
    }

    /**
     * 公开分享页一律禁止缓存。
     *
     * <p>分享链接的生命周期很短：撤销后立刻失效，同一个链接名又可以马上重新启用。
     * 若响应允许缓存，浏览器会把「链接已失效」那份 410 存下来，重新分享后仍返回旧缓存，
     * 于是访客看到的是已经修好的链接却报失效。成功响应同样不能缓存——撤销后重新分享，
     * 访客不该继续看到上一份内容。
     */
    @GetMapping("/{token}")
    public ResponseEntity<ApiResponse<PublicShareVo>> get(@PathVariable String token) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.ok(shareService.publicShare(token, null)));
    }

    @PostMapping("/{token}/unlock")
    public ResponseEntity<ApiResponse<PublicShareVo>> unlock(@PathVariable String token,
                                                            @Valid @RequestBody SharePasswordRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.ok(shareService.publicShare(token, request.password())));
    }
}
