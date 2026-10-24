package cn.minims.who.controller;

import cn.minims.who.common.ApiResponse;
import cn.minims.who.common.AuthUtil;
import cn.minims.who.dto.Dtos.AvatarShapeRequest;
import cn.minims.who.dto.Dtos.AvatarVo;
import cn.minims.who.entity.Resume;
import cn.minims.who.service.AvatarService;
import cn.minims.who.service.ResumeService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "简历")
@RestController
@RequestMapping("/api/resumes")
public class AvatarController {

    private final ResumeService resumeService;
    private final AvatarService avatarService;
    private final AuthUtil authUtil;

    public AvatarController(ResumeService resumeService, AvatarService avatarService, AuthUtil authUtil) {
        this.resumeService = resumeService;
        this.avatarService = avatarService;
        this.authUtil = authUtil;
    }

    @PostMapping("/{id}/avatar")
    public ApiResponse<AvatarVo> upload(@PathVariable long id, @RequestParam("file") MultipartFile file) {
        Resume resume = resumeService.findOwned(authUtil.userId(), id);
        return ApiResponse.ok(avatarService.upload(resume, file));
    }

    @GetMapping("/{id}/avatar")
    public ResponseEntity<Resource> file(@PathVariable long id) {
        return avatarService.file(resumeService.findOwned(authUtil.userId(), id));
    }

    @GetMapping("/{id}/avatar/info")
    public ApiResponse<AvatarVo> info(@PathVariable long id) {
        Resume resume = resumeService.findOwned(authUtil.userId(), id);
        return ApiResponse.ok(avatarService.toVo(resume, null));
    }

    @PutMapping("/{id}/avatar-shape")
    public ApiResponse<AvatarVo> shape(@PathVariable long id, @Valid @RequestBody AvatarShapeRequest request) {
        Resume resume = resumeService.findOwned(authUtil.userId(), id);
        return ApiResponse.ok(avatarService.updateShape(resume, request));
    }

    @DeleteMapping("/{id}/avatar")
    public ApiResponse<AvatarVo> delete(@PathVariable long id) {
        Resume resume = resumeService.findOwned(authUtil.userId(), id);
        avatarService.delete(resume);
        return ApiResponse.ok(avatarService.toVo(resume, null));
    }
}
