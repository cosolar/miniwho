package cn.minims.who.controller;

import cn.minims.who.common.ApiResponse;
import cn.minims.who.common.AuthUtil;
import cn.minims.who.dto.ShareDtos.ShareCreateRequest;
import cn.minims.who.dto.ShareDtos.ShareVo;
import cn.minims.who.service.ShareService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@Tag(name = "分享与公开页")
@RestController
@RequestMapping("/api")
public class ShareController {

    private final ShareService shareService;
    private final AuthUtil authUtil;

    public ShareController(ShareService shareService, AuthUtil authUtil) {
        this.shareService = shareService;
        this.authUtil = authUtil;
    }

    @PostMapping("/resumes/{id}/shares")
    public ApiResponse<ShareVo> create(@PathVariable long id, @RequestBody ShareCreateRequest request) {
        return ApiResponse.ok(shareService.create(authUtil.userId(), id, request));
    }

    @GetMapping("/resumes/{id}/shares")
    public ApiResponse<List<ShareVo>> active(@PathVariable long id) {
        return ApiResponse.ok(shareService.active(authUtil.userId(), id));
    }

    @DeleteMapping("/shares/{shareId}")
    public ApiResponse<Void> revoke(@PathVariable long shareId) {
        shareService.revoke(authUtil.userId(), shareId);
        return ApiResponse.ok();
    }
}
