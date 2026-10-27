package cn.minims.who.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import cn.minims.who.common.ApiResponse;
import cn.minims.who.common.AuthUtil;
import cn.minims.who.common.RequireAdmin;
import cn.minims.who.dto.AdminDtos.AdminAuthSettingsRequest;
import cn.minims.who.dto.AdminDtos.AdminAuthSettingsVo;
import cn.minims.who.dto.AdminDtos.AdminFeedbackRequest;
import cn.minims.who.dto.AdminDtos.AdminFeedbackVo;
import cn.minims.who.dto.AdminDtos.AdminSampleRequest;
import cn.minims.who.dto.AdminDtos.AdminSampleVo;
import cn.minims.who.dto.AdminDtos.AdminStatsVo;
import cn.minims.who.dto.AdminDtos.AdminTemplateStatusRequest;
import cn.minims.who.dto.AdminDtos.AdminTemplateVo;
import cn.minims.who.dto.AdminDtos.AdminUserStatusRequest;
import cn.minims.who.dto.AdminDtos.AdminUserVo;
import cn.minims.who.service.AdminService;
import cn.minims.who.service.TemplateService;
import cn.minims.who.template.TemplatePackageCodec;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Tag(name = "管理后台")
@RequireAdmin
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final TemplateService templateService;
    private final TemplatePackageCodec codec;
    private final AuthUtil authUtil;

    public AdminController(AdminService adminService, TemplateService templateService,
                           TemplatePackageCodec codec, AuthUtil authUtil) {
        this.adminService = adminService;
        this.templateService = templateService;
        this.codec = codec;
        this.authUtil = authUtil;
    }

    @GetMapping("/stats")
    public ApiResponse<AdminStatsVo> stats() {
        return ApiResponse.ok(adminService.stats());
    }

    @GetMapping("/users")
    public ApiResponse<List<AdminUserVo>> users() {
        return ApiResponse.ok(adminService.users());
    }

    @PutMapping("/users/{id}/status")
    public ApiResponse<Void> updateUser(@PathVariable long id,
                                        @Valid @RequestBody AdminUserStatusRequest request) {
        adminService.updateUserStatus(authUtil.userId(), id, request.status());
        return ApiResponse.ok();
    }

    @GetMapping("/templates")
    public ApiResponse<List<AdminTemplateVo>> templates() {
        return ApiResponse.ok(adminService.templates());
    }

    @GetMapping("/templates/export")
    public ResponseEntity<String> exportTemplates() {
        ArrayNode bundle = templateService.officialPackages();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"miniwho-official-templates.json\"")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8))
                .body(codec.pretty(bundle));
    }

    @PutMapping("/templates/{code}/status")
    public ApiResponse<Void> updateTemplate(@PathVariable String code,
                                            @Valid @RequestBody AdminTemplateStatusRequest request) {
        adminService.updateTemplateStatus(code, request.status());
        return ApiResponse.ok();
    }

    @GetMapping("/feedback")
    public ApiResponse<List<AdminFeedbackVo>> feedback() {
        return ApiResponse.ok(adminService.feedback());
    }

    /** 当前被设为首页示例的那份简历，未设置时 data 为 null。 */
    @GetMapping("/sample")
    public ApiResponse<AdminSampleVo> sample() {
        return ApiResponse.ok(adminService.sampleInfo());
    }

    @PutMapping("/resumes/{id}/sample")
    public ApiResponse<Void> updateSample(@PathVariable long id,
                                          @Valid @RequestBody AdminSampleRequest request) {
        adminService.updateSample(id, Boolean.TRUE.equals(request.sample()));
        return ApiResponse.ok();
    }

    @PutMapping("/feedback/{id}")
    public ApiResponse<Void> updateFeedback(@PathVariable long id,
                                            @Valid @RequestBody AdminFeedbackRequest request) {
        adminService.updateFeedback(id, request);
        return ApiResponse.ok();
    }

    /** 注册 / 登录方式、依赖就绪状态，以及邮件与 GitHub 通道的凭据配置。 */
    @GetMapping("/settings")
    public ApiResponse<AdminAuthSettingsVo> settings() {
        return ApiResponse.ok(adminService.authSettings());
    }

    @PutMapping("/settings")
    public ApiResponse<AdminAuthSettingsVo> updateSettings(
            @Valid @RequestBody AdminAuthSettingsRequest request) {
        return ApiResponse.ok(adminService.updateAuthSettings(request));
    }
}
