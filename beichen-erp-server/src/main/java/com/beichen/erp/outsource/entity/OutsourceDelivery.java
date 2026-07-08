package com.beichen.erp.outsource.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("outsource_delivery")
public class OutsourceDelivery {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String deliveryType;
    private Long projectId;
    private Long factoryId;
    private Long fromWarehouseId;
    private Long toWarehouseId;
    private Integer supplierDirect;
    private Long supplierId;
    private String logisticsCompany;
    private String logisticsNo;
    private LocalDate deliveryDate;
    private String contact;
    private String phone;
    private String status;
    private String remark;
    private String attachUrl;
    @TableField(fill = FieldFill.INSERT)
    private Long companyId;
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
