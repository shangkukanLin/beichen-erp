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
    /** 供料模式：OURS来料加工 FACTORY包工包料 */
    private String supplyMode;
    private Integer taxIncluded;
    private BigDecimal taxRate;

    /** 税额（taxIncluded=1 时按税率从含税总额中拆出：total × rate/(100+rate)） */
    private BigDecimal taxAmount;
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
