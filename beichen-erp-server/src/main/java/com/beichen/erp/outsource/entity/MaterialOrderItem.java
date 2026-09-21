package com.beichen.erp.outsource.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
@TableName("outsource_material_order_item")
public class MaterialOrderItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    @TableField("outsource_material_id")
    private Long materialId;
    /** 物料类型ID（关联 material_type.id） */
    @TableField("material_type_id")
    private Long materialTypeId;
    private String unit;
    private BigDecimal orderQuantity;
    private BigDecimal receivedQuantity;
    private BigDecimal defectReturnedQty;
    /**
     * 送修中数量（2026-09-17）：维修退货单已送修、供应商尚未修好送回的部分。
     * <p>关联订单未完成时，送修审核同时扣减 receivedQuantity（净收料 = 已收 − 送修），
     * 修好「登记维修返回」时回补；本列用于在订单上区分"未收 / 送修中"。</p>
     */
    private BigDecimal repairReturnedQty;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    private String remark;
    /** 逻辑删除标记：0未删除/1已删除 */
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private Long companyId;
}
