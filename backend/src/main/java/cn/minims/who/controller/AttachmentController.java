package cn.minims.who.controller;

import cn.minims.who.common.ApiResponse;
import cn.minims.who.common.AuthUtil;
import cn.minims.who.dto.Dtos.AttachmentVo;
import cn.minims.who.service.AttachmentService;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "简历")
@RestController
@RequestMapping("/api")
public class AttachmentController {

    private final AttachmentService attachmentService;
    private final AuthUtil authUtil;

    public AttachmentController(AttachmentService attachmentService, AuthUtil authUtil) {
        this.attachmentService = attachmentService;
        this.authUtil = authUtil;
    }

    @GetMapping("/resumes/{id}/attachments")
    public ApiResponse<List<AttachmentVo>> list(@PathVariable long id) {
        return ApiResponse.ok(attachmentService.list(authUtil.userId(), id));
    }

    @PostMapping("/resumes/{id}/attachments")
    public ApiResponse<AttachmentVo> upload(@PathVariable long id, @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(attachmentService.upload(authUtil.userId(), id, file));
    }

    @DeleteMapping("/attachments/{attachmentId}")
    public ApiResponse<Void> delete(@PathVariable long attachmentId) {
        attachmentService.delete(authUtil.userId(), attachmentId);
        return ApiResponse.ok();
    }

    @GetMapping("/attachments/{attachmentId}/file")
    public ResponseEntity<Resource> file(@PathVariable long attachmentId) {
        return attachmentService.preview(authUtil.userId(), attachmentId);
    }
}
