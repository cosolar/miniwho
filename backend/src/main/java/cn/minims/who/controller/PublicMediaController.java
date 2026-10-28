package cn.minims.who.controller;

import cn.minims.who.service.AccountAvatarService;
import cn.minims.who.service.AvatarService;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 签名媒体通道：<img> 标签无法携带 satoken 请求头，分享页头像与账号头像都走这里。
 *
 * <p>条目图片（院校 / 公司 logo）也在 {@code /api/public/**} 下，但它不带签名——随机文件名
 * 本身就是能力凭证，见 {@link ItemMediaService}。
 */
@Tag(name = "分享与公开页")
@RestController
@RequestMapping("/api/public")
public class PublicMediaController {

    private final AvatarService avatarService;
    private final AccountAvatarService accountAvatarService;

    public PublicMediaController(AvatarService avatarService, AccountAvatarService accountAvatarService) {
        this.avatarService = avatarService;
        this.accountAvatarService = accountAvatarService;
    }

    @GetMapping("/avatars/{resumeId}")
    public ResponseEntity<Resource> avatar(@PathVariable long resumeId, @RequestParam("token") String token) {
        return avatarService.publicFile(resumeId, token);
    }

    @GetMapping("/user-avatars/{userId}")
    public ResponseEntity<Resource> userAvatar(@PathVariable long userId, @RequestParam("token") String token) {
        return accountAvatarService.publicFile(userId, token);
    }
}
