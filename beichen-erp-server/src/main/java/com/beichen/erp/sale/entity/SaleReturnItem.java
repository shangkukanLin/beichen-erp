package com.beichen.erp.sale.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 销售退货单明细表
 * <p>退回商品均为不良品，qualityType 固定为 DEFECT。</p>
 */
@Data
@TableName("sale_return_item")
public class SaleReturnItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 退货单ID */
    private Long returnId;

    /** 产品ID */
    private Long productId;

    /** 产品名称（实时查名，不落库） */
    @TableField(exist = false)
    private String productName;

    /** 品质等级：销售退货固定为 DEFECT(不良品) */
    private String qualityType;

    /** 退货数量 */
    private BigDecimal quantity;

    /** 单价 */
    private BigDecimal unitPrice;

    /** 金额 */
    private BigDecimal amount;

    /** 备注 */
    private String remark;

    /** 公司ID */
    private Long companyId;

    public SaleReturnItem() {
        this.qualityType = "DEFECT";
    }
}
