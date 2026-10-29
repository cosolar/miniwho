package cn.minims.who.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 管理后台的请求模型与只读视图。
 * 视图只暴露后台页面真正用到的字段：实体的包原文、校验和、归属等内部信息不对外输出，
 * 这样给实体加列时不会被动改变 API 契约。
 */
public final class AdminDtos {

    private AdminDtos() {
    }

    public record AdminStatsVo(long users, long resumes, long shares, long reports, long templates,
                               long feedbackPending, long newUsers7d, long newResumes7d,
                               long storageBytes, long orphanBytes, long quotaBytes,
                               List<AdminRecentUserVo> recentUsers) {
    }

    /**
     * 运行环境自检与磁盘水位，只给后台看：这里可以出现路径与容量数字。
     *
     * @param uploadDir 上传目录绝对路径，运维排查挂载问题时要用
     */
    public record AdminSystemVo(String status, boolean coreOk, List<AdminCheckVo> checks,
                                String uploadDir, long freeBytes, long storageBytes, long orphanBytes,
                                long quotaBytes) {
    }

    public record AdminCheckVo(String name, boolean ok, String detail) {
    }

    public record AdminRecentUserVo(Long id, String username, String nickname, String role, Long createdAt) {
    }

    public record AdminUserVo(Long id, String username, String nickname, String email, Boolean emailVerified,
                              String role, Integer status, Long createdAt, Long resumeCount, Long storageBytes) {
    }

    public record AdminTemplateVo(Long id, String code, String name, String description, String category,
                                  String tags, String version, String author, String source, Integer status,
                                  Integer usageCount, Long createdAt, Long updatedAt) {
    }

    public record AdminFeedbackVo(Long id, Long userId, String content, String contact, Integer status,
                                  String reply, Long createdAt, Long updatedAt) {
    }

    public record AdminSampleVo(Long resumeId, String title, String templateCode, Long ownerId, String owner,
                                Long updatedAt) {
    }

    public record AdminUserStatusRequest(@NotNull Integer status) {
    }

    /** 后台给单个账号设新密码：收不到验证码、又忘了原密码的账号只剩这条兜底路径。 */
    public record AdminUserPasswordRequest(
            @NotBlank(message = "请输入新密码")
            @Size(min = 6, max = 32, message = "密码长度需为 6-32 位") String password) {
    }

    public record AdminTemplateStatusRequest(@NotNull Integer status) {
    }

    public record AdminFeedbackRequest(Integer status, @Size(max = 2000) String reply) {
    }

    public record AdminSampleRequest(@NotNull Boolean sample) {
    }

    /**
     * 后台的注册 / 登录方式面板：除了三个开关，还要回显依赖是否就绪与当前生效的通道配置，
     * 否则管理员只会看到「开关打了又弹回去」，不知道差的是 SMTP 还是 GitHub 凭据。
     */
    public record AdminAuthSettingsVo(boolean usernameRegister, boolean emailRegister, boolean githubLogin,
                                      boolean mailConfigured, boolean githubConfigured,
                                      String githubCallbackUrl, String publicBaseUrl,
                                      AdminMailChannelVo mail, AdminGithubChannelVo github) {
    }

    /**
     * 邮件通道当前生效值。授权码只回「有没有」，不回原文；
     * fromAdmin=false 表示这组值目前来自环境变量，后台填过的项优先。
     */
    public record AdminMailChannelVo(String host, String port, String username, String from, String fromName,
                                     boolean ssl, boolean starttls, boolean passwordSet, boolean fromAdmin) {
    }

    /** GitHub 应用凭据；client secret 同样不回显。 */
    public record AdminGithubChannelVo(String clientId, String publicBaseUrl,
                                       boolean secretSet, boolean fromAdmin) {
    }

    /**
     * 开关与通道配置都可为 null，表示这次不改动该项；
     * 字符串传空串表示清空该项，清空后该项重新回落到环境变量。
     */
    public record AdminAuthSettingsRequest(Boolean usernameRegister, Boolean emailRegister, Boolean githubLogin,
                                           @Size(max = 200) String mailHost, @Size(max = 10) String mailPort,
                                           @Size(max = 200) String mailUsername, @Size(max = 200) String mailPassword,
                                           @Size(max = 200) String mailFrom, @Size(max = 60) String mailFromName,
                                           Boolean mailSsl, Boolean mailStarttls,
                                           @Size(max = 100) String githubClientId,
                                           @Size(max = 200) String githubClientSecret,
                                           @Size(max = 200) String publicBaseUrl) {
    }

    /**
     * 站点外观：当前内容宽度连同可选区间一起回给后台，滑杆的上下限就不必在前端硬编码。
     */
    public record AdminSiteConfigVo(int contentWidth, int minWidth, int maxWidth, int defaultWidth) {
    }

    /** null 表示这次不改动；其余值由服务端按区间校验。 */
    public record AdminSiteConfigRequest(Integer contentWidth) {
    }
}
