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

    /** SKU（展示用，非表字段；明细接口按 productId 批量回填） */
    @TableField(exist = false)
    private String sku;

    @TableField("outsource_material_id")
    private Long materialId;
    /** 物料类型ID（关联 material_type.id） */
    @TableField("material_type_id")
    private Long materialTypeId;
    private String unit;
    private BigDecimal demandQuantity;
    private BigDecimal lossRate;
    /** 供料方：OURS我方供 FACTORY工厂包 */
    private String supplyType;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private Long companyId;
    private LocalDateTime createTime;
}
