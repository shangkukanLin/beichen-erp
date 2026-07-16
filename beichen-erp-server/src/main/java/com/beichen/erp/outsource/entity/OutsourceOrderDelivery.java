package com.beichen.erp.outsource.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("outsource_order_delivery")
public class OutsourceOrderDelivery {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long warehouseId;
    private LocalDate deliveryDate;
    private String productName;
    private BigDecimal quantity;
    private String deliveryType;
    private String trackingNo;
    private String remark;
    private String attachUrl;
    private Long companyId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
