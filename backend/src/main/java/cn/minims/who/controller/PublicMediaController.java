package cn.minims.who.controller;

import cn.minims.who.common.BizException;
import cn.minims.who.entity.Resume;
import cn.minims.who.mapper.ResumeMapper;
import cn.minims.who.service.AvatarService;
import cn.minims.who.service.MediaTokenService;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 签名媒体通道：<img> 标签无法携带 satoken 请求头，分享页与无头浏览器渲染都走这里。
 */
@Tag(name = "分享与公开页")
@RestController
@RequestMapping("/api/public/avatars")
public class PublicMediaController {

    private final MediaTokenService mediaTokenService;
    private final ResumeMapper resumeMapper;
    private final AvatarService avatarService;

    public PublicMediaController(MediaTokenService mediaTokenService, ResumeMapper resumeMapper,
                                AvatarService avatarService) {
        this.mediaTokenService = mediaTokenService;
        this.resumeMapper = resumeMapper;
        this.avatarService = avatarService;
    }

    @GetMapping("/{resumeId}")
    public ResponseEntity<Resource> avatar(@PathVariable long resumeId, @RequestParam("token") String token) {
        mediaTokenService.verify(token, AvatarService.MEDIA_SCOPE, resumeId);
        Resume resume = resumeMapper.selectById(resumeId);
        if (resume == null) {
            throw BizException.notFound("头像不存在");
        }
        return avatarService.file(resume);
    }
}
