package cn.minims.who.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import cn.minims.who.common.BizException;
import cn.minims.who.dto.AdminDtos.AdminFeedbackRequest;
import cn.minims.who.dto.AdminDtos.AdminPasswordResetVo;
import cn.minims.who.dto.AdminDtos.AdminAuthSettingsRequest;
import cn.minims.who.dto.AdminDtos.AdminAuthSettingsVo;
import cn.minims.who.dto.AdminDtos.AdminFeedbackVo;
import cn.minims.who.dto.AdminDtos.AdminRecentUserVo;
import cn.minims.who.dto.AdminDtos.AdminSampleVo;
import cn.minims.who.dto.AdminDtos.AdminSiteConfigRequest;
import cn.minims.who.dto.AdminDtos.AdminSiteConfigVo;
import cn.minims.who.dto.AdminDtos.AdminStatsVo;
import cn.minims.who.dto.AdminDtos.AdminTemplateMetaRequest;
import cn.minims.who.dto.AdminDtos.AdminTemplateVo;
import cn.minims.who.dto.AdminDtos.AdminUserResumeVo;
import cn.minims.who.dto.AdminDtos.AdminUserVo;
import cn.minims.who.entity.AuditLog;
import cn.minims.who.entity.Feedback;
import cn.minims.who.entity.Resume;
import cn.minims.who.entity.Template;
import cn.minims.who.entity.User;
import cn.minims.who.mapper.FeedbackMapper;
import cn.minims.who.mapper.ResumeMapper;
import cn.minims.who.mapper.ResumeReportMapper;
import cn.minims.who.mapper.ResumeShareMapper;
import cn.minims.who.mapper.AuditLogMapper;
import cn.minims.who.mapper.TemplateMapper;
import cn.minims.who.mapper.UserMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AdminService {

    private final UserMapper userMapper;
    private final ResumeMapper resumeMapper;
    private final TemplateMapper templateMapper;
    private final FeedbackMapper feedbackMapper;
    private final ResumeShareMapper shareMapper;
    private final ResumeReportMapper reportMapper;
    private final TemplateService templateService;
    private final AuthSettingsService authSettingsService;
    private final SettingsService settingsService;
    private final StorageUsageService storageUsage;
    private final MailService mailService;
    private final OnlineUserService onlineUserService;
    private final AuditService auditService;
    private final AuditLogMapper auditLogMapper;
    private final AccountDeletionService accountDeletionService;
    private final ResumeService resumeService;

    private static final String SETTING_AUDIT_RETENTION = "audit_retention_days";

    public AdminService(UserMapper userMapper, ResumeMapper resumeMapper, TemplateMapper templateMapper,
                        FeedbackMapper feedbackMapper, ResumeShareMapper shareMapper,
                        ResumeReportMapper reportMapper, TemplateService templateService,
                        AuthSettingsService authSettingsService, SettingsService settingsService,
                        StorageUsageService storageUsage, MailService mailService,
                        OnlineUserService onlineUserService, AuditService auditService,
                        AuditLogMapper auditLogMapper, AccountDeletionService accountDeletionService,
                        ResumeService resumeService) {
        this.userMapper = userMapper;
        this.resumeMapper = resumeMapper;
        this.templateMapper = templateMapper;
        this.feedbackMapper = feedbackMapper;
        this.shareMapper = shareMapper;
        this.reportMapper = reportMapper;
        this.templateService = templateService;
        this.authSettingsService = authSettingsService;
        this.settingsService = settingsService;
        this.storageUsage = storageUsage;
        this.mailService = mailService;
        this.onlineUserService = onlineUserService;
        this.auditService = auditService;
        this.auditLogMapper = auditLogMapper;
        this.accountDeletionService = accountDeletionService;
        this.resumeService = resumeService;
        this.auditService.updateRetention(auditRetentionDays());
    }

    // ───────────────── 回收站：用户删除的简历，管理员确认后物理清除 ─────────────────

    /** 回收站列表：按删除时间倒序，带上属主，方便判断该不该清。 */
    public List<cn.minims.who.dto.AdminDtos.AdminDeletedResumeVo> deletedResumes(int limit) {
        int safeLimit = Math.min(Math.max(1, limit), 500);
        List<cn.minims.who.dto.AdminDtos.AdminDeletedResumeVo> rows = new ArrayList<>();
        for (Map<String, Object> row : resumeMapper.selectDeleted(safeLimit)) {
            rows.add(new cn.minims.who.dto.AdminDtos.AdminDeletedResumeVo(
                    asLong(row.get("id")), asLong(row.get("user_id")),
                    asString(row.get("username")), asString(row.get("nickname")),
                    asString(row.get("title")), asString(row.get("template_code")),
                    asLong(row.get("deleted_at")), asLong(row.get("updated_at"))));
        }
        return rows;
    }

    /** 物理清除回收站里的简历：附件、头像、分享与磁盘文件一并删掉，不可恢复。 */
    public int purgeResumes(long adminId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw BizException.badRequest("请选择要清除的简历");
        }
        int removed = 0;
        for (Long id : ids) {
            if (id == null) continue;
            Resume resume = resumeMapper.selectDeletedById(id);
            if (resume == null) continue;
            resumeService.hardDelete(resume);
            removed += 1;
        }
        auditService.recordAdmin(adminId, null, AuditService.Actions.ADMIN_RESUME_PURGE, "resume",
                ids.size() == 1 ? String.valueOf(ids.get(0)) : null,
                "物理清除 " + removed + " 份简历");
        return removed;
    }

    // ───────────────── 审计日志：查看、手动清理、自动清理策略 ─────────────────

    public cn.minims.who.dto.AdminDtos.AdminAuditPageVo auditLogs(Long actorId, String action, Long from, Long to,
                                                                  int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<AuditLog>()
                .eq(actorId != null, AuditLog::getActorId, actorId)
                .eq(action != null && !action.isBlank(), AuditLog::getAction, action)
                .ge(from != null, AuditLog::getCreatedAt, from)
                .le(to != null, AuditLog::getCreatedAt, to)
                .orderByDesc(AuditLog::getId);
        long total = auditLogMapper.selectCount(wrapper);
        List<AuditLog> rows = auditLogMapper.selectList(wrapper.last("limit " + safeSize + " offset " + ((safePage - 1) * safeSize)));
        List<cn.minims.who.dto.AdminDtos.AdminAuditLogVo> items = new ArrayList<>(rows.size());
        for (AuditLog row : rows) {
            items.add(new cn.minims.who.dto.AdminDtos.AdminAuditLogVo(row.getId(), row.getActorId(),
                    row.getActorName(), row.getActorRole(), row.getAction(), row.getTargetType(),
                    row.getTargetId(), row.getDetail(), row.getIp(), row.getUserAgent(), row.getCreatedAt()));
        }
        long today = auditLogMapper.selectCount(new LambdaQueryWrapper<AuditLog>()
                .ge(AuditLog::getCreatedAt, startOfToday()));
        return new cn.minims.who.dto.AdminDtos.AdminAuditPageVo(items, total, safePage, safeSize,
                (long) safePage * safeSize < total, 0, today);
    }

    /** 手动清理：before 为 null 表示清空全部。 */
    public int purgeAudit(long adminId, Long before) {
        int removed = auditService.purge(before);
        auditService.recordAdmin(adminId, null, AuditService.Actions.ADMIN_AUDIT_PURGE, "audit", null,
                before == null ? "清空全部 " + removed + " 条" : "清理 " + removed + " 条");
        return removed;
    }

    public cn.minims.who.dto.AdminDtos.AdminAuditSettingsVo auditSettings() {
        return new cn.minims.who.dto.AdminDtos.AdminAuditSettingsVo(auditRetentionDays());
    }

    public cn.minims.who.dto.AdminDtos.AdminAuditSettingsVo updateAuditSettings(long adminId, int days) {
        int safeDays = Math.max(0, Math.min(days, 3650));
        settingsService.set(SETTING_AUDIT_RETENTION, String.valueOf(safeDays));
        auditService.updateRetention(safeDays);
        auditService.recordAdmin(adminId, null, "admin.audit.settings", "audit", null,
                safeDays == 0 ? "关闭自动清理" : "保留 " + safeDays + " 天");
        return new cn.minims.who.dto.AdminDtos.AdminAuditSettingsVo(safeDays);
    }

    private int auditRetentionDays() {
        String raw = settingsService.get(SETTING_AUDIT_RETENTION);
        if (raw == null || raw.isBlank()) {
            return 90;
        }
        try {
            return Math.max(0, Math.min(Integer.parseInt(raw.trim()), 3650));
        } catch (NumberFormatException e) {
            return 90;
        }
    }

    private long startOfToday() {
        return java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private Long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /** 注册 / 登录方式面板：管理员要看的不是三个孤立开关，而是「开没开 + 还差什么」。 */
    public AdminAuthSettingsVo authSettings() {
        return authSettingsService.adminConfig();
    }

    public AdminAuthSettingsVo updateAuthSettings(AdminAuthSettingsRequest request) {
        authSettingsService.apply(request);
        return authSettingsService.adminConfig();
    }

    public AdminSiteConfigVo siteConfig() {
        return new AdminSiteConfigVo(settingsService.contentWidth(), SettingsService.CONTENT_WIDTH_MIN,
                SettingsService.CONTENT_WIDTH_MAX, SettingsService.CONTENT_WIDTH_DEFAULT);
    }

    /** 越界直接拒绝而不是悄悄夹紧：管理员填了 2000 却看到 1600，会以为是保存坏了。 */
    public AdminSiteConfigVo updateSiteConfig(AdminSiteConfigRequest request) {
        Integer width = request.contentWidth();
        if (width != null) {
            if (width < SettingsService.CONTENT_WIDTH_MIN || width > SettingsService.CONTENT_WIDTH_MAX) {
                throw BizException.badRequest("内容宽度需在 %d–%d px 之间".formatted(
                        SettingsService.CONTENT_WIDTH_MIN, SettingsService.CONTENT_WIDTH_MAX));
            }
            settingsService.set(SettingsService.CONTENT_WIDTH, String.valueOf(width));
        }
        return siteConfig();
    }

    public AdminStatsVo stats() {
        long weekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000;
        long users = userMapper.selectCount(null);
        long resumes = resumeMapper.selectCount(null);
        long shares = shareMapper.selectCount(null);
        long reports = reportMapper.selectCount(null);
        long templates = templateMapper.selectCount(new LambdaQueryWrapper<Template>().eq(Template::getStatus, 1));
        long feedbackPending = feedbackMapper.selectCount(new LambdaQueryWrapper<Feedback>().eq(Feedback::getStatus, 0));
        long newUsers7d = userMapper.selectCount(new LambdaQueryWrapper<User>().ge(User::getCreatedAt, weekAgo));
        long newResumes7d = resumeMapper.selectCount(new LambdaQueryWrapper<Resume>().ge(Resume::getCreatedAt, weekAgo));
        List<AdminRecentUserVo> recentUsers = userMapper.selectList(new LambdaQueryWrapper<User>()
                        .orderByDesc(User::getCreatedAt).last("limit 6"))
                .stream()
                .map(user -> new AdminRecentUserVo(user.getId(), user.getUsername(), user.getNickname(),
                        user.getRole(), user.getCreatedAt()))
                .toList();
        StorageUsageService.SiteUsage scan = storageUsage.scanSite();
        return new AdminStatsVo(users, resumes, shares, reports, templates,
                feedbackPending, newUsers7d, newResumes7d,
                scan.totalBytes(), scan.orphanBytes(), storageUsage.quotaBytes(), recentUsers);
    }

    public List<AdminUserVo> users() {
        // 简历数、存储用量和在线状态本来就是全量算出来的，一次返回给前端由它分批渲染；
        // 按页反复请求只会让每页都重扫一次盘。排序与搜索同样在前端对全量数据做，不会因分批失真。
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                .orderByDesc(User::getCreatedAt));
        // 一条 group by 拿全量简历数，替代逐个 selectCount 的 N+1。
        Map<Long, Long> resumeCounts = new HashMap<>();
        for (Map<String, Object> row : resumeMapper.selectMaps(new QueryWrapper<Resume>()
                .select("user_id", "count(*) as cnt")
                .groupBy("user_id"))) {
            if (row.get("user_id") instanceof Number id && row.get("cnt") instanceof Number cnt) {
                resumeCounts.put(id.longValue(), cnt.longValue());
            }
        }
        List<AdminUserVo> result = new ArrayList<>(users.size());
        // 盘上的用量整个后台共用一次扫描，别按用户各扫一遍。
        StorageUsageService.SiteUsage scan = storageUsage.scanSite();
        Set<Long> onlineIds = new HashSet<>(onlineUserService.onlineUserIds());
        for (User user : users) {
            result.add(new AdminUserVo(user.getId(), user.getUsername(), user.getNickname(), user.getEmail(),
                    Integer.valueOf(1).equals(user.getEmailVerified()),
                    user.getRole(), user.getStatus(), user.getCreatedAt(),
                    resumeCounts.getOrDefault(user.getId(), 0L), scan.userBytes(user.getId()),
                    onlineIds.contains(user.getId()), user.getDeletionRequestedAt()));
        }
        return result;
    }

    /** 在线状态轮询：只读内存，不碰用户列表那条会扫磁盘的路径。 */
    public List<Long> onlineUserIds() {
        return onlineUserService.onlineUserIds();
    }

    /** 后台查看某个账号名下的简历：只回概览，不读取正文与附件。 */
    public List<AdminUserResumeVo> userResumes(long userId) {
        if (userMapper.selectById(userId) == null) {
            throw BizException.notFound("用户不存在");
        }
        List<Resume> resumes = resumeMapper.selectList(new LambdaQueryWrapper<Resume>()
                .eq(Resume::getUserId, userId)
                .orderByDesc(Resume::getUpdatedAt));
        Set<String> codes = new HashSet<>();
        for (Resume resume : resumes) {
            if (resume.getTemplateCode() != null) codes.add(resume.getTemplateCode());
        }
        Map<String, String> templateNames = new HashMap<>();
        if (!codes.isEmpty()) {
            for (Template template : templateMapper.selectList(
                    new LambdaQueryWrapper<Template>().in(Template::getCode, codes))) {
                templateNames.put(template.getCode(), template.getName());
            }
        }
        return resumes.stream()
                .map(resume -> new AdminUserResumeVo(resume.getId(), resume.getTitle(),
                        resume.getTemplateCode(), templateNames.getOrDefault(resume.getTemplateCode(),
                        resume.getTemplateCode()), resume.getCompleteness(), resume.getUpdatedAt()))
                .toList();
    }

    public void updateUserStatus(long adminId, long userId, int status) {
        // 埋点放在方法末尾，失败分支不该留下“已封禁”的记录。
        if (adminId == userId) {
            throw BizException.badRequest("不能冻结当前登录账号");
        }
        if (status != 1 && status != 2) {
            throw BizException.badRequest("账号状态不正确");
        }
        User user = userMapper.selectById(userId);
        if (user == null) throw BizException.notFound("用户不存在");
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getStatus, status)
                .set(User::getUpdatedAt, System.currentTimeMillis()));
        if (status == 2) {
            StpUtil.logout(userId);
        }
        auditService.recordAdmin(adminId, null, AuditService.Actions.ADMIN_USER_STATUS, "user",
                String.valueOf(userId), status == 1 ? "解封账号" : "冻结账号");
    }

    /**
     * 后台重置密码：服务端生成 8 位随机密码。
     * <p>账号有已验证邮箱时，先发信成功再落库；没有可用邮箱时保留旧的兜底路径，
     * 把密码一次性返回给管理员线下转告，避免账号彻底失去找回入口。
     * <p>不给管理员改自己的密码：这条路径会连带踢掉当前会话，改完立刻掉线反而像操作失败，
     * 自己改密码有个人中心那条「验原密码」的正路。
     */
    public AdminPasswordResetVo resetUserPassword(long adminId, long userId) {
        if (adminId == userId) {
            throw BizException.badRequest("请改用个人中心的「修改密码」修改自己的密码");
        }
        User user = userMapper.selectById(userId);
        if (user == null) throw BizException.notFound("用户不存在");
        String password = AdminPasswordNotice.generatePassword();
        boolean emailDeliverable = user.getEmail() != null && !user.getEmail().isBlank()
                && Integer.valueOf(1).equals(user.getEmailVerified());
        if (emailDeliverable) {
            mailService.send(user.getEmail(), AdminPasswordNotice.subject(),
                    AdminPasswordNotice.textBody(user.getEmail(), password),
                    AdminPasswordNotice.htmlBody(user.getEmail(), password));
        }
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getPasswordHash, BCrypt.hashpw(password))
                .set(User::getHasPassword, 1)
                .set(User::getUpdatedAt, System.currentTimeMillis()));
        StpUtil.logout(userId);
        auditService.recordAdmin(adminId, null, AuditService.Actions.ADMIN_USER_PASSWORD, "user",
                String.valueOf(userId), emailDeliverable ? "重置密码并发邮件" : "重置密码并线下转告");
        return emailDeliverable
                ? new AdminPasswordResetVo("email", null)
                : new AdminPasswordResetVo("manual", password);
    }

    public List<AdminTemplateVo> templates() {
        return templateService.all().stream()
                .map(this::toAdminTemplateVo)
                .toList();
    }

    private AdminTemplateVo toAdminTemplateVo(Template template) {
        return new AdminTemplateVo(template.getId(), template.getCode(), template.getName(),
                template.getDescription(), template.getCategory(), template.getTags(), template.getVersion(),
                template.getAuthor(), template.getSource(), template.getStatus(), template.getUsageCount(),
                template.getVisibility(), Integer.valueOf(1).equals(template.getExportable()),
                Integer.valueOf(1).equals(template.getMetaEdited()),
                template.getCreatedAt(), template.getUpdatedAt());
    }

    /**
     * 后台修改社区模板的基础信息（名称/描述/分类/标签）。
     * 官方模板的基础信息随发版下发，后台禁止修改；社区模板保存时打上人工改动标记，
     * 作者重新导入时会整体还原为包里的信息。缓存清掉后模板中心立即可见。
     */
    @CacheEvict(cacheNames = {"template-active", "template-detail"}, allEntries = true)
    public AdminTemplateVo updateTemplateMeta(long adminId, String code, AdminTemplateMetaRequest request) {
        Template template = templateMapper.selectOne(new LambdaQueryWrapper<Template>()
                .eq(Template::getCode, code).last("limit 1"));
        if (template == null) {
            throw BizException.notFound("模板不存在");
        }
        if (!"imported".equals(template.getSource())) {
            throw BizException.badRequest("官方模板的基础信息随发版下发，不能在后台修改");
        }
        String name = request.name() == null ? "" : request.name().trim();
        if (name.isEmpty()) {
            throw BizException.badRequest("请填写模板名称");
        }
        String description = request.description() == null ? "" : request.description().trim();
        String category = request.category() == null ? "" : request.category().trim();
        List<String> tags = request.tags() == null ? List.of() : request.tags().stream()
                .map(tag -> tag == null ? "" : tag.trim())
                .filter(tag -> !tag.isEmpty())
                .toList();
        if (tags.size() > 8) {
            throw BizException.badRequest("标签最多 8 个");
        }
        for (String tag : tags) {
            if (tag.length() > 20) {
                throw BizException.badRequest("单个标签不能超过 20 字");
            }
            if (tag.contains(",")) {
                throw BizException.badRequest("标签不能含逗号");
            }
        }
        templateMapper.update(null, new LambdaUpdateWrapper<Template>()
                .eq(Template::getCode, code)
                .set(Template::getName, name)
                .set(Template::getDescription, description)
                .set(Template::getCategory, category)
                .set(Template::getTags, String.join(",", tags))
                .set(Template::getMetaEdited, 1)
                .set(Template::getUpdatedAt, System.currentTimeMillis()));
        auditService.recordAdmin(adminId, null, AuditService.Actions.ADMIN_TEMPLATE_META, "template",
                code, "修改名称/描述/分类/标签");
        return toAdminTemplateVo(templateMapper.selectOne(new LambdaQueryWrapper<Template>()
                .eq(Template::getCode, code).last("limit 1")));
    }

    @CacheEvict(cacheNames = {"template-active", "template-detail"}, allEntries = true)
    public void updateTemplateStatus(long adminId, String code, int status) {
        if (status != 1 && status != 2) {
            throw BizException.badRequest("模板状态不正确");
        }
        if (templateMapper.selectCount(new LambdaQueryWrapper<Template>().eq(Template::getCode, code)) == 0) {
            throw BizException.notFound("模板不存在");
        }
        templateMapper.update(null, new LambdaUpdateWrapper<Template>()
                .eq(Template::getCode, code)
                .set(Template::getStatus, status)
                .set(Template::getUpdatedAt, System.currentTimeMillis()));
        auditService.recordAdmin(adminId, null, AuditService.Actions.ADMIN_TEMPLATE_STATUS, "template",
                code, status == 1 ? "上架模板" : "下架模板");
    }

    /**
     * 示例简历会通过无鉴权接口公开显示，全站只允许一份：设新的自动清掉旧的。
     * 刻意不碰 updated_at，否则管理员点个开关就把别人的简历排序搅乱了。
     */
    @Transactional
    public void updateSample(long adminId, long resumeId, boolean sample) {
        Resume resume = resumeMapper.selectById(resumeId);
        if (resume == null) {
            throw BizException.notFound("简历不存在");
        }
        if (sample) {
            resumeMapper.update(null, new LambdaUpdateWrapper<Resume>()
                    .eq(Resume::getIsSample, 1)
                    .set(Resume::getIsSample, 0));
        }
        resumeMapper.update(null, new LambdaUpdateWrapper<Resume>()
                .eq(Resume::getId, resumeId)
                .set(Resume::getIsSample, sample ? 1 : 0));
        auditService.recordAdmin(adminId, null, AuditService.Actions.ADMIN_SAMPLE, "resume",
                String.valueOf(resumeId), sample ? "设为全站示例" : "取消示例");
    }

    public AdminSampleVo sampleInfo() {
        Resume resume = resumeMapper.selectOne(new LambdaQueryWrapper<Resume>()
                .eq(Resume::getIsSample, 1)
                .orderByDesc(Resume::getUpdatedAt)
                .last("limit 1"));
        if (resume == null) {
            return null;
        }
        User owner = userMapper.selectById(resume.getUserId());
        return new AdminSampleVo(resume.getId(), resume.getTitle(), resume.getTemplateCode(), resume.getUserId(),
                owner == null ? "" : owner.getNickname(), resume.getUpdatedAt());
    }

    public List<AdminFeedbackVo> feedback() {
        return feedbackMapper.selectList(new LambdaQueryWrapper<Feedback>()
                        .orderByAsc(Feedback::getStatus)
                        .orderByDesc(Feedback::getCreatedAt)
                        .last("limit 200"))
                .stream()
                .map(item -> new AdminFeedbackVo(item.getId(), item.getUserId(), item.getContent(), item.getContact(),
                        item.getStatus(), item.getReply(), item.getCreatedAt(), item.getUpdatedAt()))
                .toList();
    }

    public void updateFeedback(long id, AdminFeedbackRequest request) {
        Feedback feedback = feedbackMapper.selectById(id);
        if (feedback == null) throw BizException.notFound("反馈不存在");
        int status = request.status() == null ? feedback.getStatus() : request.status();
        if (status != 0 && status != 1) {
            throw BizException.badRequest("反馈状态不正确");
        }
        feedbackMapper.update(null, new LambdaUpdateWrapper<Feedback>()
                .eq(Feedback::getId, id)
                .set(Feedback::getStatus, status)
                .set(Feedback::getReply, request.reply() == null ? feedback.getReply() : request.reply())
                .set(Feedback::getUpdatedAt, System.currentTimeMillis()));
    }
}
