package com.beichen.erp.sale.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退货整理单明细
 * <p>每行一个产品：待整理数量 + 分选结果(A/B/C/不良)。校验 qtyA+qtyB+qtyC+qtyDefect == totalQuantity。</p>
 */
@Data
@TableName("return_sort_item")
public class ReturnSortItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 退货整理单ID */
    private Long sortId;

    private Long productId;

    /** SKU（展示用，非表字段；明细接口按 productId 批量回填） */
    @TableField(exist = false)
    private String sku;

    /** 来源售后待整理批次ID（after_sale_pending.id）：唯一追溯锚点，退单/换货退回的货品共用 */
    private Long pendingId;

    // ==================== 来源追溯（非表字段，getItems 按 pendingId 从售后待整理批次回填） ====================

    /** 来源单据类型：SALE_RETURN / SALE_EXCHANGE */
    @TableField(exist = false)
    private String sourceType;

    /** 来源单号 */
    @TableField(exist = false)
    private String sourceCode;

    /** 来源单据ID（sale_return.id / sale_exchange.id，供前端跳转来源单据详情） */
    @TableField(exist = false)
    private Long sourceId;

    /** 来源单据业务日期（退单的退货日期 / 换货的换货日期） */
    @TableField(exist = false)
    private String sourceDate;

    /** [已废弃，保留兼容] 来源销售退货明细ID，追溯统一走 pendingId */
    private Long saleReturnItemId;

    private String productName;

    private String spec;

    private String unit;

    /** 待整理数量(源仓DEFECT库存，只读带出) */
    private BigDecimal totalQuantity;

    /** A规数量 */
    private BigDecimal qtyA;

    /** B规数量 */
    private BigDecimal qtyB;

    /** C规数量 */
    private BigDecimal qtyC;

    /** 不良数量 */
    private BigDecimal qtyDefect;

    private String remark;

    private Long companyId;

    private LocalDateTime createTime;
}
