package com.beichen.erp.sale.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.beichen.erp.material.common.ProductQualityType;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 销售退货单明细表
 * <p>退回商品品质默认"待分类"(PENDING)，待退货整理重新分类。</p>
 */
@Data
@TableName("sale_return_item")
public class SaleReturnItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 退货单ID */
    private Long returnId;

    /** 关联销售单明细ID（可选，用于追溯） */
    private Long saleOrderItemId;

    /** 产品ID */
    private Long productId;

    /** SKU（展示用，非表字段；明细接口按 productId 批量回填） */
    @TableField(exist = false)
    private String sku;

    /** 产品名称（实时查名，不落库） */
    @TableField(exist = false)
    private String productName;

    /** 品质等级：销售退货默认 PENDING(待分类)，退回后待退货整理重新分类 */
    private String qualityType;

    /** 退货数量 */
    private BigDecimal quantity;

    /** 已整理数量（退货整理单审核时累加、反审核时扣回；用于追溯该明细是否已整理完） */
    private BigDecimal sortedQuantity;

    /** 单价 */
    private BigDecimal unitPrice;

    /** 金额 */
    private BigDecimal amount;

    /** 备注 */
    private String remark;

    /** 公司ID */
    private Long companyId;

    public SaleReturnItem() {
        this.qualityType = ProductQualityType.PENDING.getCode();
    }
}
