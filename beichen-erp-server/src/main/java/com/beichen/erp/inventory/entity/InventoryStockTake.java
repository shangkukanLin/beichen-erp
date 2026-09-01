package com.beichen.erp.inventory.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 库存盘点单（每月每仓一次）
 * <p>盘点周期 period=yyyy-MM，同一仓库同一月份只保留一条有效单据（草稿/已审核）。
 * 审核时按实盘数量调整库存（盘盈入库、盘亏出库），反审核对称回滚。</p>
 */
@Data
@TableName("inventory_stock_take")
public class InventoryStockTake {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 盘点单号（PD-yyyyMMddNNN） */
    private String takeNo;

    private Long warehouseId;

    /** 仓库名称（冗余展示） */
    private String warehouseName;

    /** 盘点月份（yyyy-MM） */
    private String period;

    /** 盘点日期 */
    private LocalDate takeDate;

    /** 状态（DRAFT/AUDITED/CANCELLED） */
    private String status;

    private String remark;

    private Long auditorId;

    private String auditorName;

    private LocalDateTime auditTime;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;

    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
