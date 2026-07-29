package com.beichen.erp.supplier.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("supplier_type_ref")
public class SupplierTypeRef {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long supplierId;
    private String typeCode;
    private Long companyId;
}
