package com.beichen.erp.purchase.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 采购换货单明细（退回侧 + 换入侧，2026-09-18 新增）
 * <p>
 * 一行 = 一次换货配对：我方把【退回侧】产品退回供货商（出库），供货商把【换入侧】产品换回我方（入库）。
 * </p>
 * <p>
 * <b>只支持同品换货</b>：换入产品默认与退回产品相同（{@code inProductId} 为空时取 {@code productId}），
 * 换入品质 {@code inQualityType} 与换入数量 {@code inQuantity} 可不同。
 * {@code inProductId} 字段为"将来允许换不同型号"预留（放开前端选品即可，库存/台账逻辑无需改动）。
 * </p>
 * <p>可换量校验只约束<b>退回数量</b>（锚点 {@code purchaseOrderItemId}）。</p>
 */
@Data
@TableName("purchase_exchange_item")
public class PurchaseExchangeItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long exchangeId;

    /** 关联采购单明细ID（可换量校验与追溯的锚点） */
    private Long purchaseOrderItemId;

    // ==================== 退回侧（退给供货商，从我方仓出库） ====================

    /** 退回产品ID（取自采购单明细行） */
    private Long productId;

    /** SKU（展示用，非表字段；明细接口按 productId 批量回填） */
    @TableField(exist = false)
    private String sku;

    /** 退回产品名称（冗余，便于展示） */
    private String productName;

    /** 退回品质等级: A/B/C/DEFECT（默认 DEFECT 不良品，退货换货通常是质量问题） */
    private String qualityType;

    /** 退回数量：可换量校验以此为准 */
    private BigDecimal quantity;

    /** 退回单价（默认取采购单原价） */
    private BigDecimal unitPrice;

    /** 退回金额 = 退回数量 × 退回单价（生成负向应付） */
    private BigDecimal amount;

    // ==================== 换入侧（供货商换回，入我方仓；同品换货默认=退回产品） ====================

    /** 换入产品ID（同品换货为空时取退回产品；预留给"换不同型号"） */
    private Long inProductId;

    /** 换入品质等级: A/B/C/DEFECT（默认 A 规） */
    private String inQualityType;

    /** 换入数量（未指定时默认 = 退回数量） */
    private BigDecimal inQuantity;

    /** 换入单价（未指定时默认 = 退回单价；改为更高值即表示加价换新，差额进应付） */
    private BigDecimal inUnitPrice;

    /** 换入金额 = 换入数量 × 换入单价（生成正向应付） */
    private BigDecimal inAmount;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;

    private LocalDateTime createTime;
}
