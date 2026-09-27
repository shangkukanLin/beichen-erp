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

    /**
     * 费用类型常量：**研发支出**（2026-09-27 新增）。
     *
     * <p>与前端 {@code api/enums.ts} 的 {@code ExpenseTypeLabel.RND = '研发支出'} 对应。后端历史上**没有**费用类型枚举
     * （该列一直是"前端映射 + 后端只校验非空"），故这里用一个常量把跨模块写入（物料页「新增物料 → 同时登记研发支出」）
     * 要写的值收敛到一处；若将来把费用类型收成后端枚举，本常量随之删除。</p>
     */
    public static final String TYPE_RD = "RND";

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
     * 费用类型（**存 code**：OFFICE / RENT / SALARY / TRANSPORT / TRAVEL / ENTERTAIN / RND=研发支出 / OTHER）。
     * <p>2026-09-27 订正：原注释写"中文字面量：办公费/…"，与实现不符 —— 明细页/筛选/列表都按
     * {@code api/enums.ts} 的 {@code ExpenseTypeLabel} 把 code 映射成中文（实测现网存的是 OFFICE）。</p>
     */
    private String expenseType;

    /** 费用金额 */
    private BigDecimal amount;

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
     * <p>只用于"**由别的业务对象带出来的**"费用单：目前唯一来源是物料信息管理页「新增物料 → 同时登记研发支出」
     * （{@code source_bill_type=RD_MATERIAL}、{@code source_id=outsource_material.id}）。</p>
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
