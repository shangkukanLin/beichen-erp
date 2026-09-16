package com.beichen.erp.outsource.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("outsource_order_product")
public class OutsourceOrderProduct {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long projectId;
    /** 关联产品主数据ID(product.id)，交货/库存落账用主表ID */
    private Long productId;

    /** SKU（展示用，非表字段；明细接口按 productId 批量回填） */
    @TableField(exist = false)
    private String sku;

    private String productName;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    private String remark;
    @TableField(exist = false)
    private List<OutsourceOrderMaterial> materials;
    @TableField(fill = FieldFill.INSERT)
    private Long companyId;
    private LocalDateTime createTime;
}
