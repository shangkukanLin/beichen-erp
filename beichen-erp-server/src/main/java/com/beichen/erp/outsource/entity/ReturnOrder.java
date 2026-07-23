package com.beichen.erp.outsource.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("outsource_return_order")
public class ReturnOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private Long factoryId;
    private Long orderId;
    private Long warehouseId;
    private LocalDate returnDate;
    private String status;
    private String remark;
    private Long companyId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
