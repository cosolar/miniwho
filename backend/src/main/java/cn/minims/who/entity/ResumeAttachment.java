package cn.minims.who.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("resume_attachments")
public class ResumeAttachment {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long resumeId;
    private Long userId;
    private String fileName;
    private String storageKey;
    private String mime;
    private Long sizeBytes;

    @TableField(fill = FieldFill.INSERT)
    private Long createdAt;
}
