package cn.minims.who.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import cn.minims.who.common.BizException;
import cn.minims.who.dto.AuthDtos.DeletionRequest;
import cn.minims.who.dto.AuthDtos.DeletionVo;
import cn.minims.who.entity.EmailCode;
import cn.minims.who.entity.Feedback;
import cn.minims.who.entity.Resume;
import cn.minims.who.entity.ResumeAttachment;
import cn.minims.who.entity.ResumeReport;
import cn.minims.who.entity.ResumeShare;
import cn.minims.who.entity.Template;
import cn.minims.who.entity.User;
import cn.minims.who.entity.UserIdentity;
import cn.minims.who.mapper.EmailCodeMapper;
import cn.minims.who.mapper.FeedbackMapper;
import cn.minims.who.mapper.ResumeAttachmentMapper;
import cn.minims.who.mapper.ResumeMapper;
import cn.minims.who.mapper.ResumeReportMapper;
import cn.minims.who.mapper.ResumeShareMapper;
import cn.minims.who.mapper.TemplateMapper;
import cn.minims.who.mapper.UserIdentityMapper;
import cn.minims.who.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 自助注销：申请后进入宽限期，期内登录可撤销，到期由定时任务把账号连数据一起硬删。
 *
 * <p>之所以留窗口而不是点一下就删：误操作和被人拿了半个 session 是最坏两种情况，而这份数据
 * 删掉就没有第二个来源。申请时界面上会先给一次 {@link AccountExportService} 全量导出，
 * 让用户带着自己的备份离开。
 *
 * <p>清库走的是硬删：{@code resumes.deleted} 带逻辑删除，回收站里的简历同样属于要搬走的数据，
 * 所以这一步用 {@link ResumeMapper} 上的原生 SQL 而不是 BaseMapper。
 */
@Service
public class AccountDeletionService {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionService.class);

    /** 宽限期天数：只在这一处定义，前端要展示的实际清库时间由服务层算好一起返回。 */
    public static final int GRACE_DAYS = 7;
    private static final long GRACE_MILLIS = GRACE_DAYS * 24 * 60 * 60_000L;

    private final UserMapper userMapper;
    private final ResumeMapper resumeMapper;
    private final ResumeAttachmentMapper attachmentMapper;
    private final ResumeShareMapper shareMapper;
    private final ResumeReportMapper reportMapper;
    private final TemplateMapper templateMapper;
    private final FeedbackMapper feedbackMapper;
    private final UserIdentityMapper identityMapper;
    private final EmailCodeMapper codeMapper;
    private final AttachmentStore store;
    private final EmailCodeService emailCodeService;
    private final MailService mailService;

    public AccountDeletionService(UserMapper userMapper, ResumeMapper resumeMapper,
                                 ResumeAttachmentMapper attachmentMapper, ResumeShareMapper shareMapper,
                                 ResumeReportMapper reportMapper, TemplateMapper templateMapper,
                                 FeedbackMapper feedbackMapper, UserIdentityMapper identityMapper,
                                 EmailCodeMapper codeMapper, AttachmentStore store,
                                 EmailCodeService emailCodeService, MailService mailService) {
        this.userMapper = userMapper;
        this.resumeMapper = resumeMapper;
        this.attachmentMapper = attachmentMapper;
        this.shareMapper = shareMapper;
        this.reportMapper = reportMapper;
        this.templateMapper = templateMapper;
        this.feedbackMapper = feedbackMapper;
        this.identityMapper = identityMapper;
        this.codeMapper = codeMapper;
        this.store = store;
        this.emailCodeService = emailCodeService;
        this.mailService = mailService;
    }

    /** 实际清库时间：宽限期只在申请成功那一刻算一次，之后不再随当前时间漂移。 */
    public static Long deletionDeadline(Long requestedAt) {
        return requestedAt == null ? null : requestedAt + GRACE_MILLIS;
    }

    /** 发注销验证码：只服务「没有密码」的第三方账号，且邮箱必须已经过验证码确认。 */
    public void sendCode(long userId) {
        User user = requireUser(userId);
        if (Integer.valueOf(1).equals(user.getHasPassword())) {
            throw BizException.badRequest("该账号可以输入密码确认，无需验证码");
        }
        requireMailable(user);
        emailCodeService.issueDeletionCode(user.getEmail());
    }

    /**
     * 申请注销。
     * <p>刻意不加事务：验证码校验失败抛的是 RuntimeException，卷进事务会把 attempts 记数一起
     * 回滚掉，五次上限就没用了（同 {@link AuthService#registerWithEmail}）。这里只有一条 UPDATE，
     * 事务本来也换不来什么。
     */
    public DeletionVo request(long userId, DeletionRequest request) {
        User user = requireUser(userId);
        if (user.getDeletionRequestedAt() != null) {
            return toVo(user);
        }
        if ("admin".equalsIgnoreCase(user.getRole())) {
            throw BizException.forbidden("管理员账号不能自助注销，请先移交管理员或由其他管理员处理");
        }
        confirm(user, request);
        long now = System.currentTimeMillis();
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getDeletionRequestedAt, now)
                .set(User::getUpdatedAt, now));
        log.info("账号 {} 已申请注销，{} 天后清除", userId, GRACE_DAYS);
        return new DeletionVo(now, deletionDeadline(now), GRACE_DAYS);
    }

    /** 撤销注销：申请期间账号照常可用，所以取消只是把这一列清回 NULL。 */
    public DeletionVo cancel(long userId) {
        User user = requireUser(userId);
        if (user.getDeletionRequestedAt() == null) {
            return new DeletionVo(null, null, GRACE_DAYS);
        }
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getDeletionRequestedAt, null)
                .set(User::getUpdatedAt, System.currentTimeMillis()));
        log.info("账号 {} 已撤销注销申请", userId);
        return new DeletionVo(null, null, GRACE_DAYS);
    }

    /**
     * 到期清库。默认每六小时跑一次，启动后 90 秒先跑一次，避免进程重启窗口里到期账号一直没人管。
     * <p>逐个账号处理且单个失败不阻断其余：一次扫到的都是别人的账号，一个卡住不能把整批停掉。
     * <p>定时任务只在当前进程里跑，这与 Sa-Token 的令牌也只放内存是同一个前提：本部署是单实例。
     */
    @Scheduled(fixedDelayString = "${app.deletion-purge-delay:21600000}",
            initialDelayString = "${app.deletion-purge-initial-delay:90000}")
    public void purgeExpired() {
        long deadline = System.currentTimeMillis() - GRACE_MILLIS;
        List<User> expired = userMapper.selectList(new LambdaQueryWrapper<User>()
                .isNotNull(User::getDeletionRequestedAt)
                .le(User::getDeletionRequestedAt, deadline));
        for (User user : expired) {
            // 申请之后角色可能被人改过，这里再挡一次：管理员账号永远不进硬删。
            if ("admin".equalsIgnoreCase(user.getRole())) {
                log.error("账号 {} 带着管理员角色进了注销队列，已跳过清除，请管理员自行处理", user.getId());
                continue;
            }
            try {
                PurgePlan plan = plan(user.getId());
                execute(plan);
                log.info("账号 {}（{}）已按注销申请清除：{} 份简历、{} 个文件",
                        user.getId(), plan.username(), plan.resumeIds().size(), plan.fileKeys().size());
            } catch (RuntimeException e) {
                log.error("账号 {} 清除失败，将在下一个周期重试：{}", user.getId(), e.toString());
            }
        }
    }

    /**
     * 收集要删的行与文件，先收齐再动手：删到一半才发现某个目录属于别人，就退不回去了。
     * <p>包可见，方便单测直接检查清扫范围。
     */
    PurgePlan plan(long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return new PurgePlan(userId, "已删除", null, List.of(), List.of(), List.of(), false);
        }
        // 连回收站一起看：selectPurgeColumnsByUser 走原生 SQL，不受逻辑删除影响。
        List<Resume> resumes = resumeMapper.selectPurgeColumnsByUser(userId);
        List<Long> resumeIds = resumes.stream().map(Resume::getId).toList();
        List<String> fileKeys = new ArrayList<>();
        for (Resume resume : resumes) {
            fileKeys.addAll(store.listKeys(attachmentsDir(resume.getId())));
            fileKeys.addAll(store.listKeys(avatarsDir(resume.getId())));
            fileKeys.addAll(store.listKeys(mediaDir(resume.getId())));
        }
        // 附件表里可能留着盘上已经没有的行（历史清理失败），反过来也可能有盘上多出的文件；
        // 两个来源都收，fileKeys 只用于删文件，重复项删不掉第二次而已。
        attachmentMapper.selectList(new LambdaQueryWrapper<ResumeAttachment>()
                        .eq(ResumeAttachment::getUserId, userId))
                .forEach(attachment -> fileKeys.add(attachment.getStorageKey()));
        List<String> dirs = new ArrayList<>();
        for (Long resumeId : resumeIds) {
            dirs.add(attachmentsDir(resumeId));
            dirs.add(avatarsDir(resumeId));
            dirs.add(mediaDir(resumeId));
        }
        boolean ownedSample = resumes.stream()
                .anyMatch(resume -> Integer.valueOf(1).equals(resume.getIsSample()));
        return new PurgePlan(userId, user.getUsername(), user.getEmail(), resumeIds,
                fileKeys.stream().distinct().toList(), dirs, ownedSample);
    }

    void execute(PurgePlan plan) {
        if (plan.sampleOwned()) {
            // 示例简历是全站首页的展示数据，删掉后前端退回内置兜底，这里只提醒运维。
            log.warn("账号 {} 的申请里含示例简历，清除后首页将改用内置示例数据", plan.userId());
        }
        shareMapper.delete(new LambdaQueryWrapper<ResumeShare>().eq(ResumeShare::getUserId, plan.userId()));
        reportMapper.delete(new LambdaQueryWrapper<ResumeReport>().eq(ResumeReport::getUserId, plan.userId()));
        attachmentMapper.delete(new LambdaQueryWrapper<ResumeAttachment>()
                .eq(ResumeAttachment::getUserId, plan.userId()));
        feedbackMapper.delete(new LambdaQueryWrapper<Feedback>().eq(Feedback::getUserId, plan.userId()));
        identityMapper.delete(new LambdaQueryWrapper<UserIdentity>().eq(UserIdentity::getUserId, plan.userId()));
        // 只搬走他自己导入的模板包：内置与别人分享的模板不属于这个账号。
        templateMapper.delete(new LambdaQueryWrapper<Template>()
                .eq(Template::getOwnerUserId, plan.userId())
                .eq(Template::getSource, "imported"));
        if (plan.email() != null && !plan.email().isBlank()) {
            // 验证码按邮箱存，不清掉的话同一个邮箱短期内还留着注销码，也重注册不了。
            codeMapper.delete(new LambdaQueryWrapper<EmailCode>().eq(EmailCode::getEmail, plan.email()));
        }
        resumeMapper.hardDeleteByUser(plan.userId());
        userMapper.deleteById(plan.userId());

        plan.fileKeys().forEach(store::deleteQuietly);
        plan.dirs().forEach(store::deleteDirectoryQuietly);
        // 令牌只存在本进程，账号已经不在了也要把它踢掉，免得某个端还拿着旧 token 撞接口。
        StpUtil.logout(plan.userId());
    }

    private void confirm(User user, DeletionRequest request) {
        if (Integer.valueOf(1).equals(user.getHasPassword())) {
            String password = request.password();
            if (password == null || password.isBlank()) {
                throw BizException.badRequest("请输入当前密码");
            }
            if (!BCrypt.checkpw(password, user.getPasswordHash())) {
                throw BizException.badRequest("密码不正确");
            }
            return;
        }
        requireMailable(user);
        emailCodeService.verifyDeletionCode(user.getEmail(), request.code());
    }

    /** 没有密码的账号只能靠邮箱验证码自证：既要是自己的邮箱，也要站点真的能发信。 */
    private void requireMailable(User user) {
        boolean verified = Integer.valueOf(1).equals(user.getEmailVerified());
        if (!verified || user.getEmail() == null || user.getEmail().isBlank()) {
            throw BizException.forbidden("该账号没有可用密码，也没有已验证的邮箱，请联系管理员注销");
        }
        if (!mailService.available()) {
            throw BizException.forbidden("站点未配置邮件服务，第三方登录的账号请联系管理员注销");
        }
    }

    private User requireUser(long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.notFound("账号不存在");
        }
        return user;
    }

    private static String attachmentsDir(long resumeId) {
        return "attachments/" + resumeId;
    }

    private static String avatarsDir(long resumeId) {
        return "avatars/" + resumeId;
    }

    private static String mediaDir(long resumeId) {
        return "media/" + resumeId;
    }

    private DeletionVo toVo(User user) {
        return new DeletionVo(user.getDeletionRequestedAt(),
                deletionDeadline(user.getDeletionRequestedAt()), GRACE_DAYS);
    }

    /** 一次清除的范围：先算清楚要碰哪些行与文件，再一次性动手。 */
    record PurgePlan(long userId, String username, String email, List<Long> resumeIds,
                     List<String> fileKeys, List<String> dirs, boolean sampleOwned) {
    }
}
