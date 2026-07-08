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
    @TableField(fill = FieldFill.INSERT)
    private Long companyId;
    private Integer builtIn;
    private LocalDateTime createTime;
}
