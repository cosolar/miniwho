package cn.minims.who.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("users")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String passwordHash;
    private String nickname;
    private String email;
    private String role;
    private Integer status;
    /** 第三方登录建出来的账号只有随机占位哈希，没有可用密码。 */
    private Integer hasPassword;
    /** 邮箱是否走过验证码；GitHub 只关联已验证的邮箱，避免拿别人填错的邮箱接管账号。 */
    private Integer emailVerified;
    /** 自助注销的申请时间；非空表示处于宽限期内待硬删，取消注销时清回 null。 */
    private Long deletionRequestedAt;
    /** 账号头像的存储 key（user-avatars/{id}/avatar.{jpg|png}）；不属于任何一份简历。 */
    private String avatarKey;

    @TableField(fill = FieldFill.INSERT)
    private Long createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedAt;
}
