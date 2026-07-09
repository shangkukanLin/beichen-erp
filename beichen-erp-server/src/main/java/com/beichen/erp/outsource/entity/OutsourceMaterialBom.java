package com.beichen.erp.outsource.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("outsource_material_bom")
public class OutsourceMaterialBom {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parentMaterialId;
    private Long childMaterialId;
    private BigDecimal quantity;
    private BigDecimal lossRate;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private Long companyId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
