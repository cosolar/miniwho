package cn.minims.who.controller;

import cn.minims.who.common.ApiResponse;
import cn.minims.who.common.AuthUtil;
import cn.minims.who.dto.AuthDtos.ChangeEmailCodeRequest;
import cn.minims.who.dto.AuthDtos.DeletionRequest;
import cn.minims.who.dto.AuthDtos.DeletionVo;
import cn.minims.who.dto.SiteDtos.StorageVo;
import cn.minims.who.service.AccountDeletionService;
import cn.minims.who.service.AccountExportService;
import cn.minims.who.service.AuthService;
import cn.minims.who.service.StorageUsageService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;

@Tag(name = "账号")
@RestController
@RequestMapping("/api/account")
public class AccountController {

    private static final MediaType ZIP = MediaType.parseMediaType("application/zip");

    private final AccountExportService exportService;
    private final AccountDeletionService deletionService;
    private final StorageUsageService storageUsage;
    private final AuthService authService;
    private final AuthUtil authUtil;

    public AccountController(AccountExportService exportService, AccountDeletionService deletionService,
                             StorageUsageService storageUsage, AuthService authService, AuthUtil authUtil) {
        this.exportService = exportService;
        this.deletionService = deletionService;
        this.storageUsage = storageUsage;
        this.authService = authService;
        this.authUtil = authUtil;
    }

    /**
     * 下载本账号的全部数据。
     *
     * <p>用流式响应而不是先落一个临时文件：一次导出最大能到几百 MB，落盘既慢又要额外管
     * 临时文件的删除时机。代价是包开始流出后再出错就只能给半个 zip，所以清单与鉴权都在
     * {@code plan} 里做完，流出的只剩读文件这一步。
     */
    @GetMapping("/export")
    public ResponseEntity<StreamingResponseBody> export() {
        AccountExportService.ExportPlan plan = exportService.plan(authUtil.userId());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(plan.fileName(), StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentType(ZIP)
                .body(output -> exportService.write(plan, output));
    }

    /** 发送注销验证码：只给「没有密码、但绑过已验证邮箱」的第三方账号用。 */
    @PostMapping("/deletion/code")
    public ApiResponse<Void> deletionCode() {
        deletionService.sendCode(authUtil.userId());
        return ApiResponse.ok();
    }

    /** 发送换绑邮箱验证码：发给「新」地址；验证码随保存资料接口一起提交。 */
    @PostMapping("/email-code")
    public ApiResponse<Void> changeEmailCode(@Valid @RequestBody ChangeEmailCodeRequest request) {
        authService.issueChangeEmailCode(authUtil.userId(), request);
        return ApiResponse.ok();
    }

    /**
     * 申请注销：确认通过后进入宽限期，到期由定时任务硬删。
     * <p>接口只做「记下申请时间」这一件事，删数据的一律在定时任务里，避免点一次就不可回头。
     */
    @PostMapping("/deletion")
    public ApiResponse<DeletionVo> requestDeletion(@Valid @RequestBody DeletionRequest request) {
        return ApiResponse.ok(deletionService.request(authUtil.userId(), request));
    }

    /** 撤销注销申请；宽限期内随时可撤销。 */
    @DeleteMapping("/deletion")
    public ApiResponse<DeletionVo> cancelDeletion() {
        return ApiResponse.ok(deletionService.cancel(authUtil.userId()));
    }

    /**
     * 本账号的磁盘用量与上限，个人中心按需拉一次，不塞进 {@code /auth/me}：
     * 用量要扫盘，而 me 是每次进页面都会打的热点接口。
     */
    @GetMapping("/storage")
    public ApiResponse<StorageVo> storage() {
        long userId = authUtil.userId();
        return ApiResponse.ok(new StorageVo(storageUsage.usageBytes(userId), storageUsage.quotaBytes()));
    }
}
