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
import cn.minims.who.dto.AdminDtos.AdminTemplateVo;
import cn.minims.who.dto.AdminDtos.AdminUserResumeVo;
import cn.minims.who.dto.AdminDtos.AdminUserVo;
import cn.minims.who.entity.Feedback;
import cn.minims.who.entity.Resume;
import cn.minims.who.entity.Template;
import cn.minims.who.entity.User;
import cn.minims.who.mapper.FeedbackMapper;
import cn.minims.who.mapper.ResumeMapper;
import cn.minims.who.mapper.ResumeReportMapper;
import cn.minims.who.mapper.ResumeShareMapper;
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

    public AdminService(UserMapper userMapper, ResumeMapper resumeMapper, TemplateMapper templateMapper,
                        FeedbackMapper feedbackMapper, ResumeShareMapper shareMapper,
                        ResumeReportMapper reportMapper, TemplateService templateService,
                        AuthSettingsService authSettingsService, SettingsService settingsService,
                        StorageUsageService storageUsage, MailService mailService,
                        OnlineUserService onlineUserService) {
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
                    onlineIds.contains(user.getId())));
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
        return emailDeliverable
                ? new AdminPasswordResetVo("email", null)
                : new AdminPasswordResetVo("manual", password);
    }

    public List<AdminTemplateVo> templates() {
        return templateService.all().stream()
                .map(template -> new AdminTemplateVo(template.getId(), template.getCode(), template.getName(),
                        template.getDescription(), template.getCategory(), template.getTags(), template.getVersion(),
                        template.getAuthor(), template.getSource(), template.getStatus(), template.getUsageCount(),
                        template.getVisibility(), Integer.valueOf(1).equals(template.getExportable()),
                        template.getCreatedAt(), template.getUpdatedAt()))
                .toList();
    }

    @CacheEvict(cacheNames = {"template-active", "template-detail"}, allEntries = true)
    public void updateTemplateStatus(String code, int status) {
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
    }

    /**
     * 示例简历会通过无鉴权接口公开显示，全站只允许一份：设新的自动清掉旧的。
     * 刻意不碰 updated_at，否则管理员点个开关就把别人的简历排序搅乱了。
     */
    @Transactional
    public void updateSample(long resumeId, boolean sample) {
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
