package com.beichen.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("finance_receipt_item")
public class FinanceReceiptItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long receiptId;
    private Long receivableId;
    private String receivableBillNo;
    private BigDecimal thisAmount;
    private String remark;
    @TableField(fill = FieldFill.INSERT) private Long companyId;
    private LocalDateTime createTime;
}
