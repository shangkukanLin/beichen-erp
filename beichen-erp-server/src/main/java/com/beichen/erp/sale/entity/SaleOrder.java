package com.beichen.erp.sale.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("sale_order")
public class SaleOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private Long customerId;

    /** 客户名称（实时查名，不落库） */
    @TableField(exist = false)
    private String customerName;

    private Long warehouseId;

    private LocalDate orderDate;

    private String status;

    /**
     * 审核时间（**纯审计留痕**）。
     *
     * <p><b>⚠️ 注释订正（F7-119 · 2026-09-20）</b>：原文写"财务分析利润表按此归月"，但财务分析已在
     * <b>F7-42（2026-09-19）纠偏为「归期一律按建单日 {@code create_time}」</b> ——
     * 原因："审核日口径**不可复现**：反审核 → 重审会写入新的 {@code audit_time}，
     * 历史月度数字会随之后的操作持续漂移"；销售分析（{@code SaleAnalysisServiceImpl}）同样按
     * {@code create_time} 归期。</p>
     *
     * <p>⇒ <b>任何统计/分析都不要再按 audit_time 归期</b>；本字段仅用于"谁在何时审核"的留痕
     * （反审核时由 {@code unAudit} 用 UpdateWrapper 显式置 null，见 F7-48）。</p>
     */
    private LocalDateTime auditTime;

    private Integer taxIncluded;

    private BigDecimal taxRate;

    /** 税额（taxIncluded=1 时按税率从含税总额中拆出：total × rate/(100+rate)） */
    private BigDecimal taxAmount;

    private BigDecimal totalAmount;

    /**
     * 结算方式（2026-09-18，**按单记**）：CREDIT 账期（只挂应收）/ CASH 现金（审核后自动生成草稿收款单）。
     * 见 {@link com.beichen.erp.sale.common.SettleType}。
     */
    private String settleType;

    /** 结算账户ID（finance_account.id）：settleType=CASH 时必填，默认取现金账户 */
    private Long settleAccountId;

    /** 结算账户名（实时查名，不落库） */
    @TableField(exist = false)
    private String settleAccountName;

    private String remark;

    /** 制单人（ID + 姓名快照）—— 2026-09-23 全站单据口径：详情页显示「制单人」，由 MetaObjectHandler 自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    @TableField(fill = FieldFill.INSERT)
    private String createByName;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;

    private LocalDateTime createTime;

    /** 审核人（ID + 姓名快照）—— 2026-09-23 全站单据口径：详情页显示「审核人」，审核时盖章 */
    private Long auditorId;

    private String auditorName;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
