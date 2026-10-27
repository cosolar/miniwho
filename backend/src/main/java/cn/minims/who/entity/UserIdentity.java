package cn.minims.who.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 外部渠道身份：一个账号可以有多个 provider，同一 provider 内的 providerUserId 唯一。 */
@Data
@TableName("user_identities")
public class UserIdentity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String provider;
    private String providerUserId;
    private String providerLogin;
    private String email;

    @TableField(fill = FieldFill.INSERT)
    private Long createdAt;
}
