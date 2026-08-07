package com.beichen.erp.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 品质重分类单主表 */
@Data
@TableName("inventory_stock_reclass")
public class InventoryStockReclass {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 重分类单号(FL-yyyyMMdd-NNN) */
    private String code;
    /** 仓库ID */
    private Long warehouseId;
    /** 业务日期 */
    private java.time.LocalDate reclassifyDate;
    /** 状态: 草稿/已审核/已作废 */
    private String status;
    /** 备注 */
    private String remark;
    /** 公司ID */
    private Long companyId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
