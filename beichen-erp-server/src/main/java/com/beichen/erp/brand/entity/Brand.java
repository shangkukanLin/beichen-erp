package com.beichen.erp.brand.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("brand")
public class Brand {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String brandName;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private Long companyId;
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
