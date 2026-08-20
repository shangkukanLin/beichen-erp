package com.beichen.erp.outsource.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("outsource_order_material")
public class OutsourceOrderMaterial {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productId;
    @TableField("outsource_material_id")
    private Long materialId;
    /** BOM类型ID（关联 dev_bom_type.id） */
    @TableField("bom_type_id")
    private Long bomTypeId;
    private String unit;
    private BigDecimal demandQuantity;
    private BigDecimal lossRate;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private Long companyId;
    private LocalDateTime createTime;
}
