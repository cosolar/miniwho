package cn.minims.who.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 用户名修改流水：一个月内只允许改 {@code AuthService.USERNAME_CHANGES_PER_MONTH} 次，
 * 配额按自然月统计这张表里当月的行数，不额外维护计数器（改一次插一行，天然可审计）。
 */
@Data
@TableName("username_changes")
public class UsernameChange {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String oldUsername;
    private String newUsername;

    @TableField(fill = FieldFill.INSERT)
    private Long createdAt;
}
