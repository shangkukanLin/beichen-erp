package com.beichen.erp.outsource.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 委外加工退货成品明细
 * <p>退货成品出库信息持久化，供审核/取消审核扣减与恢复成品库存。</p>
 */
@Data
@TableName("outsource_return_order_product")
public class OutsourceReturnOrderProduct {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联退货单ID */
    private Long returnOrderId;

    /** 产品ID（关联 product.id） */
    private Long productId;

    /** 产品名称快照 */
    private String productName;

    /** 退货数量 */
    private BigDecimal quantity;

    /** 公司ID */
    private Long companyId;
}
