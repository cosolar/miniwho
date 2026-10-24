package cn.minims.who.controller;

import cn.minims.who.common.ApiResponse;
import cn.minims.who.dto.Dtos.PublicShareVo;
import cn.minims.who.dto.Dtos.SharePasswordRequest;
import cn.minims.who.service.ShareService;
import jakarta.validation.Valid;
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

    @GetMapping("/{token}")
    public ApiResponse<PublicShareVo> get(@PathVariable String token) {
        return ApiResponse.ok(shareService.publicShare(token, null));
    }

    @PostMapping("/{token}/unlock")
    public ApiResponse<PublicShareVo> unlock(@PathVariable String token,
                                             @Valid @RequestBody SharePasswordRequest request) {
        return ApiResponse.ok(shareService.publicShare(token, request.password()));
    }
}
