package com.beichen.erp.warehouse.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 入库批次成本记录（移动加权平均成本的回滚依据）。
 * <p>每次"有明确单价"的入库（采购/委外交货/委外物料入库等）记一行，
 * 反审核按 relatedBillId + changeType 冲销对应批次并反加权。</p>
 */
@Data
@TableName("cost_inbound_log")
public class CostInboundLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 成本对象类型: MATERIAL=委外物料 PRODUCT=产品 */
    private String targetType;

    /** 成本对象ID（outsource_material.id 或 product.id） */
    private Long targetId;

    /** 库存变动类型（StockChangeType code，用于反审核精确匹配批次） */
    private String changeType;

    /** 关联单据ID（反审核按此冲销） */
    private Long relatedBillId;

    /** 关联单号（冗余，便于追溯） */
    private String relatedBillNo;

    /** 本批入库数量 */
    private BigDecimal quantity;

    /** 本批入库单价（不含税口径：采购明细单价/委外加工单价含材料分摊等） */
    private BigDecimal unitCost;

    /** 本批入库总成本 = quantity × unitCost */
    private BigDecimal totalCost;

    /** 入库后该对象的加权成本快照（审计用） */
    private BigDecimal costAfter;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;

    private LocalDateTime createTime;
}
