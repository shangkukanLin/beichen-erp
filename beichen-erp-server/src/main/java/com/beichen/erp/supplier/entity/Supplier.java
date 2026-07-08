package com.beichen.erp.supplier.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("supplier")
public class Supplier {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private String name;

    private String supplierType;

    private String contact;

    private String phone;

    private String address;

    private Integer status;

    private Integer hasDisplay;

    private Integer hasTouch;

    private Long relatedSupplierId;

    private String brand;

    private String materialType;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
