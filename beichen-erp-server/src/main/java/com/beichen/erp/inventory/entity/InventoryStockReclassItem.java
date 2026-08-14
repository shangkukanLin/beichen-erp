package com.beichen.erp.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/** 品质重分类单明细表 */
@Data
@TableName("inventory_stock_reclass_item")
public class InventoryStockReclassItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 重分类单ID */
    private Long reclassId;
    /** 产品ID */
    private Long productId;
    /** 产品名称（实时查名，不落库） */
    @TableField(exist = false)
    private String productName;
    /** 源等级: A/B/C/DEFECT */
    private String fromQuality;
    /** 目标等级: A/B/C/DEFECT */
    private String toQuality;
    /** 重分类数量 */
    private BigDecimal quantity;
    /** 备注 */
    private String remark;
    /** 公司ID */
    private Long companyId;
    private LocalDateTime createTime;
}
