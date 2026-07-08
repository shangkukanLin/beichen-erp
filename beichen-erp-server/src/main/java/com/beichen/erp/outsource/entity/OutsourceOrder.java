package com.beichen.erp.outsource.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("outsource_order")
public class OutsourceOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private Long factoryId;
    private LocalDate planStartDate;
    private LocalDate planEndDate;
    private LocalDate actualStartDate;
    private LocalDate actualEndDate;
    private String status;
    private Integer taxIncluded;
    private BigDecimal taxRate;
    private BigDecimal totalAmount;
    private String remark;
    private String attachUrl;
    private String logisticsCompany;
    private String logisticsNo;
    @TableField(fill = FieldFill.INSERT)
    private Long companyId;
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
