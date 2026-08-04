package com.beichen.erp.dev.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 图纸文档实体
 * <p>versionCode自动递增，同项目下同名+同类型图纸归为一组</p>
 */
@Data
@TableName("dev_drawing")
public class Drawing {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    /** 文档名称，与docType组合作为版本分组依据 */
    private String docName;
    /** 图纸类型 */
    private String docType;
    /** 文件URL */
    private String fileUrl;
    /** 自动递增的版本号 */
    private Integer versionCode;
    /** 手动填写的版本标注（可选，如V1.0） */
    private String version;
    private String remark;
    private LocalDateTime uploadTime;
}
