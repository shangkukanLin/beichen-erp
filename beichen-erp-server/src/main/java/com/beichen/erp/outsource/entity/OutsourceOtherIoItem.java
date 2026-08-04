package com.beichen.erp.outsource.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
@TableName("outsource_other_io_item")
public class OutsourceOtherIoItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long otherIoId;
    @TableField("outsource_material_id")
    private Long materialId;
    /** BOM类型ID（关联 dev_bom_type.id） */
    @TableField("bom_type_id")
    private Long bomTypeId;
    private String unit;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private Long companyId;
}
