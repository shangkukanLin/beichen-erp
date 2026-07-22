package com.beichen.erp.outsource.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
@TableName("outsource_order_close_report_item")
public class CloseReportItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long reportId;
    @TableField("outsource_material_id")
    private Long materialId;
    private String materialName;
    private String materialType;
    private String unit;
    private BigDecimal deliveredQuantity;
    private BigDecimal returnedQuantity;
    private BigDecimal goodReturnQty;
    private BigDecimal defectReturnQty;
    private BigDecimal shippedQuantity;
    private BigDecimal targetYieldRate;
    private BigDecimal actualYieldRate;
    private BigDecimal yieldLoss;
    private BigDecimal excessLossQty;
    private BigDecimal materialPrice;
    private BigDecimal factoryRetainQty;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private Long companyId;
}
