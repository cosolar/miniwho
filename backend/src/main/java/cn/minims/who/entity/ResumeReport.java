package cn.minims.who.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("resume_reports")
public class ResumeReport {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long resumeId;
    private Long userId;
    private Integer contentVersion;
    private Integer totalScore;
    private String grade;
    private String dimensions;
    private String issues;

    @TableField(fill = FieldFill.INSERT)
    private Long createdAt;
}
