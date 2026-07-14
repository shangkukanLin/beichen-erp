package com.beichen.erp.outsource.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("outsource_material_order")
public class MaterialOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private Long supplierId;
    private LocalDate deliveryDate;
    private String status;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private Long companyId;
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    private LocalDateTime finishTime;

    @TableField(exist = false)
    private List<MaterialOrderItem> items;
}
