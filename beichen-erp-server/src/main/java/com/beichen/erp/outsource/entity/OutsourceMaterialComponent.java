package com.beichen.erp.outsource.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
@TableName("outsource_material_component")
public class OutsourceMaterialComponent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parentMaterialId;
    private Long childMaterialId;
    private BigDecimal quantity;
    private BigDecimal lossRate;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private Long companyId;
}
