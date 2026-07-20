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
    private Long materialId;
    private String materialName;
    private String materialType;
    private String unit;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private Long companyId;
}
