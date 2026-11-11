package cn.minims.who.service;

import cn.minims.who.entity.AuditLog;
import cn.minims.who.entity.User;
import cn.minims.who.mapper.AuditLogMapper;
import cn.minims.who.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 审计流水的写入口。设计取舍：
 * <ul>
 *   <li>只记账号级与敏感操作（登录、改密、注销、删除、分享、导出、模板与后台的所有写操作），
 *       简历正文的浏览与逐字段保存不进表——量大且事后价值低。</li>
 *   <li>写日志失败绝不影响主流程：异常只打 warn，业务照常返回。</li>
 *   <li>保留策略由后台配置（天数），未配置时默认 90 天，每天清理一次。</li>
 * </ul>
 */
@Service
public class AuditService {

    /** 动作名统一在这里定义，后台筛选下拉与埋点共用同一份清单。 */
    public static final class Actions {
        public static final String LOGIN = "auth.login";
        public static final String LOGIN_FAILED = "auth.login_failed";
        public static final String REGISTER = "auth.register";
        public static final String PASSWORD_CHANGE = "auth.password_change";
        public static final String EMAIL_CHANGE = "auth.email_change";
        public static final String DELETION_REQUEST = "account.deletion_request";
        public static final String DELETION_CANCEL = "account.deletion_cancel";
        public static final String DELETION_PURGE = "account.deletion_purge";
        public static final String RESUME_CREATE = "resume.create";
        public static final String RESUME_DELETE = "resume.delete";
        public static final String RESUME_PURGE = "resume.purge";
        public static final String SHARE_CREATE = "share.create";
        public static final String SHARE_REVOKE = "share.revoke";
        public static final String PDF_EXPORT = "resume.pdf_export";
        public static final String TEMPLATE_IMPORT = "template.import";
        public static final String TEMPLATE_SHARE = "template.share";
        public static final String TEMPLATE_DELETE = "template.delete";
        public static final String TEMPLATE_EXPORT = "template.export";
        public static final String ADMIN_USER_STATUS = "admin.user.status";
        public static final String ADMIN_USER_PASSWORD = "admin.user.password_reset";
        public static final String ADMIN_USER_DELETION_CANCEL = "admin.user.deletion_cancel";
        public static final String ADMIN_USER_DELETION_PURGE = "admin.user.deletion_purge";
        public static final String ADMIN_TEMPLATE_STATUS = "admin.template.status";
        public static final String ADMIN_TEMPLATE_META = "admin.template.meta";
        public static final String ADMIN_RESUME_PURGE = "admin.resume.purge";
        public static final String ADMIN_AUDIT_PURGE = "admin.audit.purge";
        public static final String ADMIN_SAMPLE = "admin.resume.sample";

        private Actions() {
        }
    }

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final int MAX_DETAIL = 480;
    private static final int MAX_UA = 240;
    private static final int MAX_IP = 60;
    private static final int MAX_ACTOR = 60;

    private final AuditLogMapper auditLogMapper;
    private final UserMapper userMapper;

    /** 保留天数；0 或负数表示不自动清理。由后台设置覆盖。 */
    private volatile int retentionDays = 90;

    public AuditService(AuditLogMapper auditLogMapper, UserMapper userMapper) {
        this.auditLogMapper = auditLogMapper;
        this.userMapper = userMapper;
    }

    public void record(Long actorId, String actorName, String actorRole,
                       String action, String targetType, String targetId, String detail) {
        try {
            AuditLog row = new AuditLog();
            row.setActorId(actorId);
            row.setActorName(cut(actorName == null ? lookupName(actorId) : actorName, MAX_ACTOR));
            row.setActorRole(actorRole == null ? "user" : actorRole);
            row.setAction(action);
            row.setTargetType(targetType);
            row.setTargetId(cut(targetId, MAX_ACTOR));
            row.setDetail(cut(detail, MAX_DETAIL));
            HttpServletRequest request = currentRequest();
            if (request != null) {
                row.setIp(cut(clientIp(request), MAX_IP));
                row.setUserAgent(cut(request.getHeader("User-Agent"), MAX_UA));
            }
            auditLogMapper.insert(row);
        } catch (RuntimeException e) {
            // 审计失败不能挡住业务：登录、删除这些主流程比日志重要。
            log.warn("写审计日志失败（{}）：{}", action, e.getMessage());
        }
    }

    /** 当前登录用户的便捷重载：actorId/actorName 由调用方给，避免在这里反查用户表。 */
    public void recordUser(Long userId, String userName, String action,
                           String targetType, String targetId, String detail) {
        record(userId, userName, "user", action, targetType, targetId, detail);
    }

    /** 管理员操作：actorRole 固定 admin，后台筛选靠这一列区分。 */
    public void recordAdmin(Long adminId, String adminName, String action,
                            String targetType, String targetId, String detail) {
        record(adminId, adminName, "admin", action, targetType, targetId, detail);
    }

    /** 未登录场景（登录失败、注册）也要留痕，actorRole 标 guest。 */
    public void recordGuest(String action, String targetType, String targetId, String detail) {
        record(null, null, "guest", action, targetType, targetId, detail);
    }

    /** 系统动作（定时清除等）：没有操作者，但要能追溯「什么时候清了什么」。 */
    public void recordSystem(String action, String targetType, String targetId, String detail) {
        record(null, "系统任务", "system", action, targetType, targetId, detail);
    }

    public int retentionDays() {
        return retentionDays;
    }

    public void updateRetention(int days) {
        this.retentionDays = Math.max(0, Math.min(days, 3650));
    }

    /** 手动清理：before 为 0 表示清空。返回删除条数。 */
    public int purge(Long before) {
        long limit = before == null ? 0 : before;
        int removed = auditLogMapper.purgeBefore(limit);
        log.info("审计日志已清理 {} 条（before={}）", removed, limit);
        return removed;
    }

    /** 每天凌晨三点按保留天数清理；retentionDays<=0 时跳过。 */
    @Scheduled(cron = "0 0 3 * * *")
    public void scheduledPurge() {
        int days = retentionDays;
        if (days <= 0) {
            return;
        }
        long before = System.currentTimeMillis() - days * 86_400_000L;
        try {
            int removed = auditLogMapper.purgeBefore(before);
            if (removed > 0) {
                log.info("审计日志自动清理 {} 条（保留 {} 天）", removed, days);
            }
        } catch (RuntimeException e) {
            log.warn("审计日志自动清理失败：{}", e.getMessage());
        }
    }

    /** 调用方只给了 userId 时补一次用户名：账号注销后流水里仍有可读的操作者。 */
    private String lookupName(Long actorId) {
        if (actorId == null) {
            return null;
        }
        try {
            User user = userMapper.selectById(actorId);
            return user == null ? null : user.getUsername();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    /** 反向代理下取真实来源：X-Forwarded-For 的第一段。 */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }

    private String cut(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
