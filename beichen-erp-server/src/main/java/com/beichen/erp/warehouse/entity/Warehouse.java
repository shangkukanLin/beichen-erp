package com.beichen.erp.warehouse.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 统一仓库实体（合并 inventory_warehouse + outsource_warehouse）
 */
@Data
@TableName("warehouse")
public class Warehouse {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 仓库编码 */
    private String code;

    /** 仓库名称 */
    private String warehouseName;

    /** 仓库类别: INVENTORY(自有仓库) / OUTSOURCE(委外仓库) */
    private String warehouseCategory;

    /** 仓库类型(仅INVENTORY): 成品仓/不良品仓/辅料仓 */
    private String warehouseType;

    /** 关联加工厂ID(仅OUTSOURCE) */
    private Long factoryId;

    /** 地址 */
    private String address;

    /** 联系人 */
    private String contact;

    /** 电话 */
    private String phone;

    /** 状态: 1启用 0停用 */
    private Integer status;

    /** 备注 */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;

    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
