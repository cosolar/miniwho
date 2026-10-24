package cn.minims.who.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("resume_shares")
public class ResumeShare {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long resumeId;
    private Long userId;
    private String token;
    private String passwordHash;
    private Long expiresAt;
    private Integer allowDownload;
    private String hiddenFields;
    private Long viewCount;
    private Long revokedAt;

    @TableField(fill = FieldFill.INSERT)
    private Long createdAt;
}
