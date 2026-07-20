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
    /** 订单类型：采购 / 委外 */
    private String orderType;
    /** 收货目标仓库（委外单可指定入总成厂仓，空则默认入供应商仓） */
    private Long targetWarehouseId;
    private LocalDate deliveryDate;
    private String status;
    private String remark;
    private String attachUrl;
    @TableField(fill = FieldFill.INSERT)
    private Long companyId;
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    private LocalDateTime finishTime;

    @TableField(exist = false)
    private List<MaterialOrderItem> items;
}
