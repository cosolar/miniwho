package cn.minims.who.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("resumes")
public class Resume {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String title;
    private String language;
    private String templateCode;
    private String source;
    private String content;
    private String modulePrefs;
    private String style;
    private String avatarKey;
    private String avatarShape;
    private Integer contentVersion;
    private Integer completeness;
    private Integer score;
    private Long scoreUpdatedAt;
    private Integer lifecycleStatus;
    private Integer isSample;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private Long createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedAt;
}
