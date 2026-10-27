package cn.minims.who.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 一次性邮箱验证码：codeHash 存 SHA-256，明文只在发信那一次经过内存。 */
@Data
@TableName("email_codes")
public class EmailCode {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String email;
    private String purpose;
    private String codeHash;
    private Long expiresAt;
    private Integer attempts;

    @TableField(fill = FieldFill.INSERT)
    private Long createdAt;
}
