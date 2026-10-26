package cn.minims.who.dto;

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
                               List<AdminRecentUserVo> recentUsers) {
    }

    public record AdminRecentUserVo(Long id, String username, String nickname, String role, Long createdAt) {
    }

    public record AdminUserVo(Long id, String username, String nickname, String email, String role,
                              Integer status, Long createdAt, Long resumeCount) {
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

    public record AdminTemplateStatusRequest(@NotNull Integer status) {
    }

    public record AdminFeedbackRequest(Integer status, @Size(max = 2000) String reply) {
    }

    public record AdminSampleRequest(@NotNull Boolean sample) {
    }
}
