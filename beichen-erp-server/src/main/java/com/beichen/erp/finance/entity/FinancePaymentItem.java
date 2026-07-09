package com.beichen.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("finance_payment_item")
public class FinancePaymentItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long paymentId;
    private Long payableId;
    private String payableBillNo;
    private BigDecimal thisAmount;
    private String remark;
    @TableField(fill = FieldFill.INSERT) private Long companyId;
    private LocalDateTime createTime;
}
