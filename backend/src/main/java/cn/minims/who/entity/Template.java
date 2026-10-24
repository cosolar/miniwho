package cn.minims.who.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("templates")
public class Template {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String description;
    private String category;
    private String tags;
    private String config;
    /** 模板包原文（净化后），导出时按字节还原，不让它从展开后的 config 反推。 */
    private String pkg;
    private String namespace;
    private String version;
    private String author;
    /** builtin 由随应用发版的包文件负责；imported 归导入者账号，种子刷新不得覆盖后者。 */
    private String source;
    private Integer api;
    private String checksum;
    /** 导入包的作者；官方包为 null，删除、再导入与分享只认这个归属。 */
    private Long ownerUserId;
    /** private 只出现在导入者的「我的模板」里；public 才进模板中心给本站所有用户选用。 */
    private String visibility;
    /** 仅在 visibility=public 时有意义：0 表示别人只能选用，不能把模板包下载走。 */
    private Integer exportable;
    private Integer status;
    private Integer sortWeight;
    private Integer usageCount;

    @TableField(fill = FieldFill.INSERT)
    private Long createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedAt;
}
