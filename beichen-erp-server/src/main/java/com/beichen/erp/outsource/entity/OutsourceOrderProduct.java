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
    private String productName;
    private String productSpec;
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
