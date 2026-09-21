package com.beichen.erp.sale.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 销售换货单明细（退回侧 + 换出侧）
 * <p>
 * 一行 = 一次换货配对：客户退回【退回侧】产品，公司发出【换出侧】产品。
 * <p>只支持<b>同品换货</b>：换出产品固定为退回产品（{@code productId}），
 * 换出品质 {@code outQualityType} 与换出数量 {@code outQuantity} 可不同（如退 2 换 1、A 规换 B 规）。
 * 可换量校验只约束<b>退回数量</b>（锚点 {@code saleOrderItemId}），换出属正常出库不受限。
 * 退回侧统一以 PENDING(待分类) 入售后仓，后续走退货整理。</p>
 */
@Data
@TableName("sale_exchange_item")
public class SaleExchangeItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long exchangeId;

    /** 关联销售单明细ID（强关联锚点：可换量校验与追溯的依据） */
    private Long saleOrderItemId;

    // ==================== 退回侧（客户退回，入售后仓） ====================

    /** 退回产品ID（取自销售单明细行） */
    private Long productId;

    /** SKU（展示用，非表字段；明细接口按 productId 批量回填） */
    @TableField(exist = false)
    private String sku;

    /** 退回产品名称（冗余，便于展示） */
    private String productName;

    /** 退回数量：可换量校验以此为准 */
    private BigDecimal quantity;

    /** 原销售单价（冗余，仅用于展示） */
    private BigDecimal unitPrice;

    /** 退回金额 = 退回数量 × 原销售单价（仅用于展示） */
    private BigDecimal amount;

    // ==================== 换出侧（发给客户，从成品仓扣减；只支持同品，产品固定为退回产品） ====================

    /** 换出数量：可与退回数量不等（如退 2 换 1） */
    private BigDecimal outQuantity;

    /** 换出单价：默认取原销售单价，可手工改，仅用于展示与差价参考 */
    private BigDecimal outUnitPrice;

    /** 换出金额 = 换出数量 × 换出单价（仅用于展示） */
    private BigDecimal outAmount;

    /** 换出品质：A/B/C/DEFECT，从换出仓扣减该品质的库存 */
    private String outQualityType;

    // ==================== 逐产品收费（2026-09-21 用户口径「付费要精确到产品上」） ====================

    /**
     * 是否收费：0否 1是（逐产品）。
     * <p>收费由**单据级下沉到明细行**：一行 = 一个产品。单据级 {@code sale_exchange.charge_amount}
     * 改由服务层回写为 **Σ(本表 charge_amount)**、{@code charge_flag} = 任一行收费（列表/详情展示口径不变）。</p>
     * <p>⚠️ 方向：**向客户收取** —— 审核生成一条正向应收（{@code 单号-FEE}，金额 = Σ本表）。</p>
     */
    private Integer chargeFlag;

    /** 收费类型：SERVICE服务费 / DIFF品质差价 / FULL全额货值 / OTHER其他（逐行可不同） */
    private String chargeType;

    /** 该产品收费金额（> 0 才计入应收） */
    private BigDecimal chargeAmount;

    /** 该产品收费说明（前端不逐行填时，取单据级说明作为批量默认） */
    private String chargeReason;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;

    private LocalDateTime createTime;
}
