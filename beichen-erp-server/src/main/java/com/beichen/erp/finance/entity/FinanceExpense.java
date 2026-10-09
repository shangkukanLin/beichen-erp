package com.beichen.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 费用登记单（财务费用管理）
 * <p>审核后生成「费用支出」资金流水（账户扣款）；反审核写「费用冲正」流水冲回，
 * 与收款单的审核/冲正模式保持对称。账户余额由资金流水实时累计，不维护快照。</p>
 */
@Data
@TableName("finance_expense")
public class FinanceExpense {

    /** 制单人（ID + 姓名快照）—— 2026-09-23 全站单据口径：详情页显示「制单人」，由 MetaObjectHandler 自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    @TableField(fill = FieldFill.INSERT)
    private String createByName;

    /** 审核人（ID + 姓名快照）—— 2026-09-23 全站单据口径：详情页显示「审核人」，审核时盖章 */
    private Long auditorId;

    private String auditorName;


    @TableId(type = IdType.AUTO)
    private Long id;

    /** 费用单号（FY-yyyyMMddXXX） */
    private String expenseNo;

    /**
     * 费用类型（**存 code**，取值见 {@code finance.common.ExpenseType}：OFFICE/RENT/SALARY/TRANSPORT/TRAVEL/
     * ENTERTAIN/RND=研发支出/OTHER；中文名在前端 {@code api/enums.ts} 的 {@code ExpenseTypeLabel} 映射）。
     *
     * <p>2026-09-27：① 订正历史注释（原写"中文字面量：办公费/…"与实现不符，实测现网存的是 OFFICE）；
     * ② 写入侧由 {@code FinanceExpenseServiceImpl.validate} 用 {@code ExpenseType.normalize} **校验并归一化**，
     * 未知值直接拒绝（原先无校验，前端写错即脏数据）。</p>
     */
    private String expenseType;

    /**
     * 费用金额。
     * <p><b>口径跟随 {@link #taxIncluded}（2026-10-09 V7）</b>：含税 ⇒ 这里是**含税总额**；未含税 ⇒ 就是不含税金额。
     * 本项目**不做价税分离** ⇒ 本字段始终是"真正从账户扣款、进利润表"的那个数，
     * 「是否含税」只影响税额的**拆分展示**，绝不改写本字段。</p>
     */
    private BigDecimal amount;

    /**
     * 是否含税（2026-10-09 V7 新增；口径与 V4 的四个单据 purchase/sale/outsource/outsource_material **一致**）：
     * 0=未含税（默认，与存量数据同义）1=含税。
     *
     * <p>归一化统一由 {@code FinanceExpenseServiceImpl.normalizeTax} 负责：未含税 ⇒ 税率/税额强制归 0；
     * 含税 ⇒ 按 {@code 金额 × 税率/(100+税率)} 重算税额（HALF_UP、2 位小数）。</p>
     */
    private Integer taxIncluded;

    /** 税率(%)；未含税时恒为 0（详见 {@link #taxIncluded}） */
    private BigDecimal taxRate;

    /** 税额 = 金额 × 税率/(100+税率)（含税总额拆税）；**仅展示/统计，不改 amount** */
    private BigDecimal taxAmount;

    /** 费用日期（利润表按此归月） */
    private LocalDate expenseDate;

    /** 支出账户ID */
    private Long accountId;

    /** 支出账户名称（冗余展示） */
    private String accountName;

    private String remark;

    /** 状态（DRAFT/AUDITED/CANCELLED） */
    private String status;

    /**
     * 来源类型 / 来源对象ID / 来源单号（2026-09-27 新增；命名与 {@code finance_receivable} 的来源三列一致）。
     *
     * <p>只用于"**由别的业务对象带出来的**"费用单。当前来源是**研发管理的研发物料**「研发支出」登记
     * （{@code source_bill_type=RD_DEV_MATERIAL}、{@code source_id=dev_purchase_item.id}，见
     * {@code RdExpenseServiceImpl}）；{@code RD_MATERIAL} 是**已下线的物料信息管理入口**留下的存量类型
     * （{@code source_id=outsource_material.id}）—— 2026-10-09 订正注释：原文写"唯一来源是物料信息管理页"
     * 与该功能 2026-09-28 迁到研发物料之后的事实不符。</p>
     * <p>作用：① **幂等** —— 同一物料不得重复建研发支出（见 FinanceExpenseService.findActiveBySource）；
     * ② 可追溯 —— 费用管理详情可显示"来源：物料 XXX"；③ 后续"按物料/类型汇算研发支出"有抓手。</p>
     * <p>手工在费用管理页登记的费用单，这三列均为 NULL（与历史数据一致）。</p>
     */
    private String sourceBillType;
    private Long sourceId;
    private String sourceBillNo;

    @TableField(fill = FieldFill.INSERT)
    private Long companyId;

    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
