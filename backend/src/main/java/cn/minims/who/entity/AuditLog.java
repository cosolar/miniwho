package cn.minims.who.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 账号级与敏感操作的审计流水。只记「谁在什么时候对什么做了什么」，
 * 不记简历正文的浏览与编辑这类高频、低价值的动作，避免表无限膨胀。
 */
@Data
@TableName("audit_logs")
public class AuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作者；未登录的登录失败尝试为空。 */
    private Long actorId;

    /** 操作者名称快照：账号注销后这条流水仍然可读。 */
    private String actorName;

    /** user / admin / system / guest。 */
    private String actorRole;

    /** 见 AuditActions：auth.login / resume.delete / admin.user.ban … */
    private String action;

    /** user / resume / template / share / account / audit。 */
    private String targetType;

    private String targetId;

    /** 人类可读的补充说明，例如「密码错误」「批量清除 3 份」。 */
    private String detail;

    private String ip;

    private String userAgent;

    @TableField(fill = FieldFill.INSERT)
    private Long createdAt;
}
