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
import cn.minims.who.dto.AdminDtos.AdminSiteConfigRequest;
import cn.minims.who.dto.AdminDtos.AdminSiteConfigVo;
import cn.minims.who.dto.AdminDtos.AdminStatsVo;
import cn.minims.who.dto.AdminDtos.AdminSystemVo;
import cn.minims.who.dto.AdminDtos.AdminTemplateStatusRequest;
import cn.minims.who.dto.AdminDtos.AdminTemplateVo;
import cn.minims.who.dto.AdminDtos.AdminPasswordResetVo;
import cn.minims.who.dto.AdminDtos.AdminUserStatusRequest;
import cn.minims.who.dto.AdminDtos.AdminUserVo;
import cn.minims.who.service.AdminService;
import cn.minims.who.service.HealthService;
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
    private final HealthService healthService;
    private final AuthUtil authUtil;

    public AdminController(AdminService adminService, TemplateService templateService,
                           TemplatePackageCodec codec, HealthService healthService, AuthUtil authUtil) {
        this.adminService = adminService;
        this.templateService = templateService;
        this.codec = codec;
        this.healthService = healthService;
        this.authUtil = authUtil;
    }

    @GetMapping("/stats")
    public ApiResponse<AdminStatsVo> stats() {
        return ApiResponse.ok(adminService.stats());
    }

    /** 运行环境与磁盘水位：路径、容量这些细节只在这个需要管理员的接口里出现。 */
    @GetMapping("/system")
    public ApiResponse<AdminSystemVo> system() {
        return ApiResponse.ok(healthService.adminView());
    }

    @GetMapping("/users")
    public ApiResponse<List<AdminUserVo>> users() {
        return ApiResponse.ok(adminService.users());
    }

    /** 在线状态轮询：只读内存里的在线 id，避免每 60 秒重跑用户列表的磁盘扫描。 */
    @GetMapping("/users/online")
    public ApiResponse<List<Long>> onlineUsers() {
        return ApiResponse.ok(adminService.onlineUserIds());
    }

    @PutMapping("/users/{id}/status")
    public ApiResponse<Void> updateUser(@PathVariable long id,
                                        @Valid @RequestBody AdminUserStatusRequest request) {
        adminService.updateUserStatus(authUtil.userId(), id, request.status());
        return ApiResponse.ok();
    }

    /** 给单个账号重置密码：生成 8 位随机密码，优先发到用户已验证邮箱；没有可用邮箱时返回给管理员转告。 */
    @PutMapping("/users/{id}/password")
    public ApiResponse<AdminPasswordResetVo> resetUserPassword(@PathVariable long id) {
        return ApiResponse.ok(adminService.resetUserPassword(authUtil.userId(), id));
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

    /** 站点外观：目前只有内容宽度，它决定前台页面外壳的最大宽度。 */
    @GetMapping("/site-config")
    public ApiResponse<AdminSiteConfigVo> siteConfig() {
        return ApiResponse.ok(adminService.siteConfig());
    }

    @PutMapping("/site-config")
    public ApiResponse<AdminSiteConfigVo> updateSiteConfig(@RequestBody AdminSiteConfigRequest request) {
        return ApiResponse.ok(adminService.updateSiteConfig(request));
    }
}
