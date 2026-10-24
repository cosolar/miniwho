package cn.minims.who.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import cn.minims.who.common.BizException;
import cn.minims.who.dto.Dtos.AdminFeedbackRequest;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminService {

    private final UserMapper userMapper;
    private final ResumeMapper resumeMapper;
    private final TemplateMapper templateMapper;
    private final FeedbackMapper feedbackMapper;
    private final ResumeShareMapper shareMapper;
    private final ResumeReportMapper reportMapper;
    private final TemplateService templateService;

    public AdminService(UserMapper userMapper, ResumeMapper resumeMapper, TemplateMapper templateMapper,
                        FeedbackMapper feedbackMapper, ResumeShareMapper shareMapper,
                        ResumeReportMapper reportMapper, TemplateService templateService) {
        this.userMapper = userMapper;
        this.resumeMapper = resumeMapper;
        this.templateMapper = templateMapper;
        this.feedbackMapper = feedbackMapper;
        this.shareMapper = shareMapper;
        this.reportMapper = reportMapper;
        this.templateService = templateService;
    }

    public Map<String, Object> stats() {
        long now = System.currentTimeMillis();
        long weekAgo = now - 7L * 24 * 60 * 60 * 1000;
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("users", userMapper.selectCount(null));
        map.put("resumes", resumeMapper.selectCount(null));
        map.put("shares", shareMapper.selectCount(null));
        map.put("reports", reportMapper.selectCount(null));
        map.put("templates", templateMapper.selectCount(new LambdaQueryWrapper<Template>().eq(Template::getStatus, 1)));
        map.put("feedbackPending", feedbackMapper.selectCount(new LambdaQueryWrapper<Feedback>().eq(Feedback::getStatus, 0)));
        map.put("newUsers7d", userMapper.selectCount(new LambdaQueryWrapper<User>().ge(User::getCreatedAt, weekAgo)));
        map.put("newResumes7d", resumeMapper.selectCount(new LambdaQueryWrapper<Resume>().ge(Resume::getCreatedAt, weekAgo)));
        List<User> recent = userMapper.selectList(new LambdaQueryWrapper<User>()
                .orderByDesc(User::getCreatedAt).last("limit 6"));
        List<Map<String, Object>> recentUsers = new ArrayList<>();
        for (User user : recent) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", user.getId());
            item.put("username", user.getUsername());
            item.put("nickname", user.getNickname());
            item.put("role", user.getRole());
            item.put("createdAt", user.getCreatedAt());
            recentUsers.add(item);
        }
        map.put("recentUsers", recentUsers);
        return map;
    }

    public List<Map<String, Object>> users() {
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                .orderByDesc(User::getCreatedAt).last("limit 200"));
        List<Map<String, Object>> result = new ArrayList<>();
        for (User user : users) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", user.getId());
            item.put("username", user.getUsername());
            item.put("nickname", user.getNickname());
            item.put("email", user.getEmail());
            item.put("role", user.getRole());
            item.put("status", user.getStatus());
            item.put("createdAt", user.getCreatedAt());
            item.put("resumeCount", resumeMapper.selectCount(new LambdaQueryWrapper<Resume>()
                    .eq(Resume::getUserId, user.getId())));
            result.add(item);
        }
        return result;
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

    public List<Template> templates() {
        return templateService.all();
    }

    @CacheEvict(cacheNames = {"template-active", "template-detail"}, allEntries = true)
    public void updateTemplateStatus(String code, int status) {
        if (status != 1 && status != 2) {
            throw BizException.badRequest("模板状态不正确");
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

    public Map<String, Object> sampleInfo() {
        Resume resume = resumeMapper.selectOne(new LambdaQueryWrapper<Resume>()
                .eq(Resume::getIsSample, 1)
                .orderByDesc(Resume::getUpdatedAt)
                .last("limit 1"));
        if (resume == null) {
            return null;
        }
        User owner = userMapper.selectById(resume.getUserId());
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("resumeId", resume.getId());
        info.put("title", resume.getTitle());
        info.put("templateCode", resume.getTemplateCode());
        info.put("ownerId", resume.getUserId());
        info.put("owner", owner == null ? "" : owner.getNickname());
        info.put("updatedAt", resume.getUpdatedAt());
        return info;
    }

    public List<Feedback> feedback() {
        return feedbackMapper.selectList(new LambdaQueryWrapper<Feedback>()
                .orderByAsc(Feedback::getStatus)
                .orderByDesc(Feedback::getCreatedAt)
                .last("limit 200"));
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
