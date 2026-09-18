package com.beichen.erp.purchase.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 采购换货单（进货业务，2026-09-18 新增；**同品换货**，强关联采购单）
 * <p>
 * 业务：把向供货商采购的成品退回供货商，同时换回（同款）良品，一张单管住"出一进"。
 * </p>
 * <p>
 * 审核双向联动库存：
 * <ol>
 *   <li>退回侧：从 {@code warehouseOutId} 按明细 {@code qualityType}（默认不良品 DEFECT）**出库**扣减；</li>
 *   <li>换入侧：向 {@code warehouseInId} 按 {@code inQualityType}（默认 A 规）**入库**增加。</li>
 * </ol>
 * 财务联动：退回侧生成**负向**应付（冲减我们欠供货商的钱），换入侧生成**正向**应付，
 * 两行净额即差价（等价换货净额 0；加价换新则净额为正）。
 * </p>
 * <p>可换量 = 采购单明细数量 − 已退货量(TH-) − 已换退回量(CH-)，只约束**退回数量**。</p>
 */
@Data
@TableName("purchase_exchange")
public class PurchaseExchange {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 换货单号（CH-yyyyMMddNNN） */
    private String code;

    /** 供货商ID */
    private Long supplierId;

    /** 供货商名称（非表字段，查询时按 supplierId 回填） */
    @TableField(exist = false)
    private String supplierName;

    /** 关联采购单ID（强关联，必填） */
    private Long purchaseOrderId;

    /** 关联采购单号（冗余，便于列表展示与检索） */
    private String purchaseOrderCode;

    /** 退回出库仓ID：退回货品从此仓扣减（我方成品仓，货退给供货商） */
    private Long warehouseOutId;

    /** 换入入库仓ID：供货商换回的良品入此仓（我方成品仓，可与退回仓相同） */
    private Long warehouseInId;

    private LocalDate exchangeDate;

    /** 状态：DRAFT / AUDITED / CANCELLED */
    private String status;

    /** 退回侧总金额（明细 quantity × unitPrice 汇总，生成负向应付） */
    private BigDecimal totalReturnAmount;

    /** 换入侧总金额（明细 inQuantity × inUnitPrice 汇总，生成正向应付） */
    private BigDecimal totalInAmount;

    private String remark;

    private Long auditorId;

    private String auditorName;

    private LocalDateTime auditTime;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;

    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
