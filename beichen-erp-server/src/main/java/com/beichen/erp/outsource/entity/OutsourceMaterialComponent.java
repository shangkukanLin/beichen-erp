package com.beichen.erp.outsource.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
@TableName("outsource_material_component")
public class OutsourceMaterialComponent {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("parent_outsource_material_id")
    private Long parentMaterialId;
    @TableField("child_outsource_material_id")
    private Long childMaterialId;
    private BigDecimal quantity;
    private BigDecimal lossRate;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private Long companyId;
}
