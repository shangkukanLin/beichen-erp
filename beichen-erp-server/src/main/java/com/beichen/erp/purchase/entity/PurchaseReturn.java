package com.beichen.erp.purchase.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("purchase_return")
public class PurchaseReturn {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private Long supplierId;

    /** 供货商名称（非表字段，查询时按 supplierId 回填） */
    @TableField(exist = false)
    private String supplierName;

    private Long warehouseId;

    /** 关联采购单ID（可选，用于追溯） */
    private Long purchaseOrderId;

    /** 关联采购单号（冗余展示） */
    private String purchaseOrderCode;

    private LocalDate returnDate;

    private String status;

    private BigDecimal totalAmount;

    // ==================== 是否付费（2026-09-21 用户口径：采购退货单/采购换货单都要有，精确到产品） ====================

    /**
     * 是否付费：0否 1是（**派生自明细**：任一行付费即 1）。
     * <p>⚠️ 方向：<b>我们向供货商付费</b> ⇒ 审核生成一条正向应付
     * （source_bill_type = {@code PURCHASE_RETURN_CHARGE}），与退回侧冲减应付分开记账。</p>
     */
    private Integer chargeFlag;

    /** 付费类型：各明细行类型一致时回填该类型，否则为 NULL（详情显示"多类型"）；见 {@code PurchaseChargeType} */
    private String chargeType;

    /** 付费金额 = **Σ 明细行付费**（审核生成一条正向应付） */
    private BigDecimal chargeAmount;

    /** 付费说明（整单共用一句话，会写入台账 remark） */
    private String chargeReason;

    private String remark;

    private Long auditorId;

    private String auditorName;

    private LocalDateTime auditTime;

    /** 制单人（ID + 姓名快照）—— 2026-09-23 全站单据口径：详情页显示「制单人」，由 MetaObjectHandler 自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    @TableField(fill = FieldFill.INSERT)
    private String createByName;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;

    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
