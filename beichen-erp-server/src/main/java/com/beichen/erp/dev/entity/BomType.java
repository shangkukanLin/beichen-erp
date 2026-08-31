package com.beichen.erp.dev.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("dev_bom_type")
public class BomType {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String typeName;
    private Integer sortOrder;
    private Integer status;
    /** 是否默认类型：1默认（不可删除） 0自定义 */
    private Integer isDefault;
    @TableField(fill = FieldFill.INSERT)
    private Long companyId;
    private LocalDateTime createTime;
}
