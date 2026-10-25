package cn.minims.who.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class Dtos {

    private Dtos() {
    }

    public record LoginRequest(
            @NotBlank(message = "请输入用户名") @Size(max = 50) String username,
            @NotBlank(message = "请输入密码") @Size(max = 100) String password) {
    }

    public record RegisterRequest(
            @NotBlank(message = "请输入用户名")
            @Pattern(regexp = "^[a-zA-Z0-9_]{3,20}$", message = "用户名需为 3-20 位字母、数字或下划线") String username,
            @NotBlank(message = "请输入密码") @Size(min = 6, max = 32, message = "密码长度需为 6-32 位") String password,
            @Size(max = 50) String nickname) {
    }

    public record UserVo(Long id, String username, String nickname, String email,
                         String role, Integer status, Long createdAt) {
    }

    public record LoginResponse(String token, String tokenName, UserVo user, Long resumeId) {
    }

    public record UpdateProfileRequest(
            @Size(max = 50) String nickname,
            @Email(message = "邮箱格式不正确") @Size(max = 100) String email) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "请输入原密码") String oldPassword,
            @NotBlank(message = "请输入新密码") @Size(min = 6, max = 32, message = "密码长度需为 6-32 位") String newPassword) {
    }

    public record CreateResumeRequest(String title, String language, String templateCode, Boolean useSample) {
    }

    public record RenameRequest(@NotBlank(message = "请输入简历名称") @Size(max = 50, message = "简历名称不能超过 50 个字符") String title) {
    }

    public record ResumeSummary(Long id, String title, String language, String templateCode, String templateName,
                                Integer score, Integer completeness, Long updatedAt, String targetJob) {
    }

    public record ResumeDetail(Long id, String title, String language, String templateCode, String templateName,
                               String source, JsonNode content, JsonNode modulePrefs, JsonNode style,
                               Integer contentVersion, Integer completeness, Integer score,
                               Long scoreUpdatedAt, Long createdAt, Long updatedAt, JsonNode avatar) {
    }

    public record WorkbenchResponse(ResumeDetail resume, List<AttachmentVo> attachments) {
    }

    public record SectionSaveRequest(
            @NotNull(message = "模块内容不能为空") JsonNode section,
            @NotNull(message = "缺少内容版本") Integer contentVersion) {
    }

    public record PrefsSaveRequest(
            @NotNull(message = "缺少模块顺序") List<String> order,
            @NotNull(message = "缺少隐藏模块信息") List<String> hidden,
            JsonNode customTitles,
            @NotNull(message = "缺少内容版本") Integer contentVersion) {
    }

    public record ModuleAddRequest(
            @NotNull(message = "模块内容不能为空") JsonNode section,
            @NotNull(message = "缺少内容版本") Integer contentVersion) {
    }

    public record StyleSaveRequest(String templateCode, String filter,
                                   @Min(-2) @Max(2) Integer fontSizeStep,
                                   @Min(-2) @Max(2) Integer spacingStep,
                                   Boolean showBrandFooter) {
    }

    public record SaveResult(int contentVersion, long savedAt, int completeness, int score) {
    }

    public record AttachmentVo(Long id, String fileName, String mime, Long sizeBytes, Long createdAt) {
    }

    public record TemplateVo(String code, String name, String description, String category,
                             List<String> tags, JsonNode config, Integer usageCount,
                             String version, String author, String source, String visibility,
                             Boolean exportable) {
    }

    /** 站内分享：shared 决定是否进模板中心，exportable 决定别人能否把包下载走。官方模板不参与。 */
    public record TemplateShareRequest(@NotNull(message = "请指定是否分享到模板中心") Boolean shared,
                                       Boolean exportable) {
    }

    public record ReportVo(Long id, Long resumeId, Integer contentVersion, int totalScore, String grade,
                           JsonNode dimensions, JsonNode issues, Long createdAt, boolean current) {
    }

    public record ShareCreateRequest(String password, Integer expiresInDays,
                                     Boolean allowDownload, List<String> hiddenFields) {
    }

    public record ShareVo(Long id, String token, String resumeTitle, Long expiresAt, boolean allowDownload,
                          List<String> hiddenFields, boolean hasPassword, Long viewCount, Long createdAt) {
    }

    public record SharePasswordRequest(@NotBlank(message = "请输入访问密码") @Size(max = 32) String password) {
    }

    public record PublicShareVo(String token, String title, String language, String templateCode,
                                JsonNode content, JsonNode style, JsonNode templateConfig,
                                JsonNode template, boolean allowDownload, Long expiresAt, Long updatedAt,
                                String ownerName, boolean brandFooter, JsonNode avatar) {
    }

    public record AvatarVo(boolean present, String shape, String url, Long sizeBytes, Long updatedAt) {
    }

    /** 条目图片（院校 / 公司 logo）：key 存进简历 content，url 由 key 直接推导。 */
    public record ItemMediaVo(String key, String url, Long sizeBytes) {
    }

    public record AvatarShapeRequest(
            @NotBlank(message = "请选择头像形状")
            @Pattern(regexp = "circle|rect", message = "头像形状仅支持 circle 或 rect") String shape) {
    }

    public record PdfStatusVo(boolean available, String engine, String message) {
    }

    public record ExportPayloadVo(String title, String language, String templateCode, String templateName,
                                  JsonNode content, JsonNode style, JsonNode templateConfig, JsonNode avatar,
                                  Long updatedAt) {
    }

    public record FeedbackRequest(
            @NotBlank(message = "请填写反馈内容") @Size(max = 2000, message = "反馈内容不能超过 2000 字") String content,
            @Size(max = 200) String contact) {
    }

    public record AdminUserStatusRequest(@NotNull Integer status) {
    }

    public record AdminTemplateStatusRequest(@NotNull Integer status) {
    }

    public record AdminFeedbackRequest(Integer status, @Size(max = 2000) String reply) {
    }

    public record AdminSampleRequest(@NotNull Boolean sample) {
    }

    /**
     * 全站示例简历。首页与模板中心的预览都读这一份，接口无鉴权，
     * 所以只放渲染版面需要的两个字段，不带用户标识、版面偏好、头像和分享配置。
     */
    public record SampleResume(String templateCode, JsonNode content) {
    }

    /** 页脚统计：累计访问次数与最近 5 分钟内有心跳的在线人数。 */
    public record SiteStatsVo(long visits, int online) {
    }

    /** 访客 id 由前端本地生成，只用于去重与心跳，服务端不落库。 */
    public record SiteVisitRequest(@Size(max = 64, message = "访客标识过长") String visitorId) {
    }
}
