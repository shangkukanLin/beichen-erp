package com.beichen.erp.finance.common;

/**
 * 财务来源单据类型枚举
 * <p>
 * 管理 finance_receivable.source_bill_type / finance_payable.source_bill_type 字段。
 * 标识应收/应付的来源业务单据类型，禁止散落硬编码中文字符串。
 * </p>
 */
public enum SourceBillType {

    /** 销售单 */
    SALE_ORDER("销售单"),
    /** 采购单 */
    PURCHASE_ORDER("采购单"),
    /** 采购入库 */
    PURCHASE_INBOUND("采购入库"),
    /** 采购退货单 */
    PURCHASE_RETURN("采购退货单"),
    /** 采购换货-退回侧：换货单退回供货商生成**负向**应付（冲减），台账号后缀 -RET（2026-09-18） */
    PURCHASE_EXCHANGE_RETURN("采购换货退回"),
    /** 采购换货-换入侧：供货商换回的良品生成**正向**应付，台账号后缀 -IN ⇒ 与退回侧两行净额即差价 */
    PURCHASE_EXCHANGE_IN("采购换货入库"),
    /**
     * 采购换货-付费（2026-09-21 新增）：换货单「是否付费=是」时生成的正向应付。
     * <p>⚠️ 方向：**我们向供货商付费**（换货服务费 / 补差价等），与销售换货的 SALE_EXCHANGE_CHARGE（向客户收费）相反。</p>
     */
    PURCHASE_EXCHANGE_CHARGE("采购换货付费"),
    /**
     * 采购退货-付费（2026-09-21 新增）：退货单「是否付费」时生成的正向应付。
     * <p>⚠️ 方向：**我们向供货商付费**（退货处理费 / 品质折让补价等），与销售退货单的 SALE_RETURN_CHARGE（向客户收费）相反。
     * 金额 = Σ 明细行付费（精确到产品），remark 逐产品列出。</p>
     */
    PURCHASE_RETURN_CHARGE("采购退货付费"),
    /** 销售退货 */
    SALE_RETURN("销售退货"),
    /** 委外加工单交货（成品报工入库产生应付） */
    OUTSOURCE_DELIVERY("委外加工收货"),
    /** 委外物料订单收货 / 退不良（收发单审核产生应付） */
    OUTSOURCE_MATERIAL_DELIVERY("委外物料收发"),
    /** 委外退料（负向应付冲减） */
    OUTSOURCE_RETURN("委外退料"),
    /** 委外加工退货收费：**加工厂向我方收取**（我方付加工厂）的费用，与退料负向冲减分开记账，同为 sourceId=退货单ID */
    OUTSOURCE_RETURN_CHARGE("委外加工退货收费"),
    /** 委外维修收费（2026-09-17）：维修退货单送修时**加工厂向我方收取**的维修费，正向应付，单独分账便于对账 */
    OUTSOURCE_REPAIR_CHARGE("委外维修收费"),
    /**
     * 委外物料退货。
     * <p><b>P2（2026-09-28）口径变更</b>：其中的**退货退款 REFUND** 审核由「负向应付冲减」改为生成
     * **对供应商的应收**（物料退给供应商、供应商把货款退给我们 ⇒ 收款单按 subjectType=SUPPLIER 核销）。
     * 订单退料（ORDER）仍不动账务；维修返回（REPAIR）的收费走
     * {@link #OUTSOURCE_MATERIAL_REPAIR_FEE}。来源单号仍是物料退货单号，故本类型沿用。</p>
     */
    OUTSOURCE_MATERIAL_RETURN("委外物料退货"),
    /**
     * 委外物料维修费（P3 2026-09-28 新增）：**物料维修返回**送修时供应商向我方收取的维修费 = **正向应付**。
     * <p>与加工侧的 {@link #OUTSOURCE_REPAIR_CHARGE}（委外维修收费）分开记：往来主体不同
     * （物料侧=辅料商/供应商，加工侧=加工厂）、来源单也不同（物料维修返回单 / 加工退货单），
     * 分开后前端「来源单号」才能各自跳到正确详情页，也便于按业务对账。</p>
     * <p>金额 = Σ 明细行金额（即各行「维修费单价 × 数量」）；为 0 时不落账。</p>
     * <p>⚠️ 命名长度硬约束：{@code finance_payable/finance_receivable.source_bill_type} 是 <b>varchar(30)</b>
     * ⇒ 枚举名（本值即落库 code）不得超过 30 字符，否则审核时报
     * {@code Data too long for column 'source_bill_type'}（P3 曾用 32 字符的 ..._REPAIR_CHARGE 踩坑）。</p>
     */
    OUTSOURCE_MATERIAL_REPAIR_FEE("委外物料维修费"),
    /** 委外超损赔偿（结单超损总价生成负应付） */
    OUTSOURCE_EXCESS_LOSS("委外超损"),
    /** 加工返回单料款（P1-2 2026-09-25）：修好送回按实际用料 FIFO 生成**对加工厂**的应收（工厂赔料），subjectType=SUPPLIER */
    OUTSOURCE_RETURN_BACK("加工返回单"),

    /** 销售换货收费：换货单选择收费时生成的正向应收（单号后缀 -FEE） */
    SALE_EXCHANGE_CHARGE("销售换货收费"),

    /** 销售退货收费：退货单选择收费时生成的正向应收（单号后缀 -FEE） */
    SALE_RETURN_CHARGE("销售退货收费"),

    /** 退货整理折损收款：整理后 B/C/不良品的折损，向客户收取（单号后缀 -LOSS） */
    RETURN_SORT_LOSS("退货整理折损"),

    /** 应付转应收：退货/超损扣款在无货款可抵扣时，转为向供应商收款（应收挂供应商） */
    PAYABLE_TRANSFER("应付转应收"),

    /**
     * 成品报损（2026-09-29 用户口径「报损需要走财务流程」）。
     * <p>用途：① 承担方=供应商时，本条是 {@code finance_receivable}（对供应商索赔）的来源；
     * ② 承担方=内部时，本条是 {@code finance_expense}（报损损失费用，无账户=非资金）的来源。
     * 两者都以报损单号作为单号/来源单号，故**审核/反审核可幂等复用同一行**。</p>
     */
    INVENTORY_STOCK_LOSS("成品报损"),

    /** 委外物料报损（与成品报损同口径，见 {@link #INVENTORY_STOCK_LOSS}） */
    OUTSOURCE_STOCK_LOSS("委外物料报损"),

    /**
     * F7-53（2026-09-19）：**预收/预付台账**的来源标记 —— 多收/多付产生的台账，其来源就是收款单/付款单。
     *
     * <p>修复前这两处写入的是 {@code SettlementStatus.ADVANCE.getCode()}（字面量 "ADVANCE"）—— **用错枚举填错字段**：
     * ① 该值不在本枚举白名单内 ⇒ 前端 `SourceBillTypeLabel` 查不到 ⇒ 应收/应付列表的"来源"列**直接显示英文 "ADVANCE"**；
     * ② 该值不在来源类型下拉里 ⇒ 按来源筛选时**筛不到**这批行。
     * 本批把写入改为本值，并订正了库中 9 行历史数据（应收 5 + 应付 4）。</p>
     */
    ADVANCE_LEDGER("预收/预付台账");

    private final String label;

    SourceBillType(String label) { this.label = label; }

    /** 前端显示的中文名称 */
    public String getLabel() { return label; }
    /** 存入数据库的枚举常量名 */
    public String getCode() { return name(); }

    /**
     * 根据数据库存储的 code（枚举名）反向查找。
     */
    public static SourceBillType fromCode(String code) {
        if (code == null || code.isBlank()) return null;
        for (SourceBillType t : values()) {
            if (t.name().equalsIgnoreCase(code)) return t;
        }
        return null;
    }
}
