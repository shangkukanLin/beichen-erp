package com.beichen.erp.purchase.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("purchase_return_item")
public class PurchaseReturnItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long returnId;

    /** 关联采购单明细ID（可选，用于追溯） */
    private Long purchaseOrderItemId;

    private Long productId;

    /** SKU（展示用，非表字段；明细接口按 productId 批量回填） */
    @TableField(exist = false)
    private String sku;

    /** 产品名称（关联 product，不落库，随明细接口一起返回） */
    @TableField(exist = false)
    private String productName;

    /** 品质等级: A/B/C/DEFECT */
    private String qualityType;

    private BigDecimal quantity;

    private BigDecimal unitPrice;

    private BigDecimal amount;

    // ==================== 逐产品付费（2026-09-21 用户口径：是否付费且精确到产品） ====================

    /**
     * 是否付费：0否 1是（**逐产品**，2026-09-21 新增）。
     * <p>⚠️ 方向：<b>我们向供货商付费</b> ⇒ 审核时并入一条正向应付（我方欠供货商变多）。</p>
     */
    private Integer chargeFlag;

    /** 该产品的付费类型：SERVICE服务费 / DIFF品质差价 / FULL全额货值 / OTHER其他（{@code PurchaseChargeType}） */
    private String chargeType;

    /** 该产品的付费金额（我方付给供货商）；金额 &gt; 0 即视为付费，必须同时给出类型 */
    private BigDecimal chargeAmount;

    /** 该产品的付费说明（默认取整单的「付费说明」） */
    private String chargeReason;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;

    private LocalDateTime createTime;
}
