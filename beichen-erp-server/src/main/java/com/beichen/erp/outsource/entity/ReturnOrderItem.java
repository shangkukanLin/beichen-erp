package com.beichen.erp.outsource.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
@TableName("outsource_return_order_item")
public class ReturnOrderItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long returnOrderId;
    private String materialName;
    private String materialType;
    private String unit;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    private String remark;
    private Long companyId;
}
