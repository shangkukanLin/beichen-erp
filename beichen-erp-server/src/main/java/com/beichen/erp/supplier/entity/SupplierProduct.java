package com.beichen.erp.supplier.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("supplier_product")
public class SupplierProduct {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long supplierId;

    private Long productId;

    @TableField(exist = false)
    private String productName;

    @TableField(exist = false)
    private String spec;

    @TableField(exist = false)
    private String unit;

    private BigDecimal unitPrice;

    private String remark;

    private Long companyId;
    private LocalDateTime createTime;
}
