package com.beichen.erp.warehouse.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 入库批次成本记录（移动加权平均成本的回滚依据）。
 * <p>每次"有明确单价"的入库（采购/委外收货/委外物料入库等）记一行，
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

    /**
     * 本批入库单价（采购明细单价 / 委外加工单价含材料分摊等）。
     * <p><b>含税口径（2026-10-09 校正）</b>：本字段**跟随来源单据的「含税」开关** ——
     * {@code tax_included=1} 时用户填报的是**含税单价**，此时本字段即含税价；为 0 时是不含税价。
     * 本项目**不做价税分离**：税额仅按 {@code total × rate/(100+rate)} 拆出用于价税分离的展示/统计，
     * **不改变**单据明细金额（见 {@code V4__material_order_tax.sql} 的口径说明）。
     * 因此移动加权成本 = 用户填报价的口径（通常即含税价），"库存金额"也沿用同一口径、不再二次换算。
     * （此前的注释写作"不含税口径"，与实际业务不符，已更正。）</p>
     */
    private BigDecimal unitCost;

    /** 本批入库总成本 = quantity × unitCost */
    private BigDecimal totalCost;

    /** 入库后该对象的加权成本快照（审计用） */
    private BigDecimal costAfter;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;

    private LocalDateTime createTime;
}
