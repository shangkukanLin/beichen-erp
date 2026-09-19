package com.beichen.erp.finance.common;

/**
 * 财务来源单据类型枚举
 * <p>
 * 管理 finance_receivable.source_bill_type / finance_payable.source_bill_type 字段。
 * 标识应收/应付的来源业务单据类型，禁止散落硬编码中文字符串。
 * </p>
 */
public enum SourceBillType {

    /** 销售出库 */
    SALE_OUTBOUND("销售出库"),
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
    /** 销售退货 */
    SALE_RETURN("销售退货"),
    /** 委外加工单交货（成品报工入库产生应付） */
    OUTSOURCE_DELIVERY("委外加工交货"),
    /** 委外物料订单收货 / 退不良（收发单审核产生应付） */
    OUTSOURCE_MATERIAL_DELIVERY("委外物料收发"),
    /** 委外退料（负向应付冲减） */
    OUTSOURCE_RETURN("委外退料"),
    /** 委外加工退货收费：**加工厂向我方收取**（我方付加工厂）的费用，与退料负向冲减分开记账，同为 sourceId=退货单ID */
    OUTSOURCE_RETURN_CHARGE("委外加工退货收费"),
    /** 委外维修收费（2026-09-17）：维修退货单送修时**加工厂向我方收取**的维修费，正向应付，单独分账便于对账 */
    OUTSOURCE_REPAIR_CHARGE("委外维修收费"),
    /** 委外物料退货（退回物料商，负向应付冲减） */
    OUTSOURCE_MATERIAL_RETURN("委外物料退货"),
    /** 委外超损赔偿（结单超损总价生成负应付） */
    OUTSOURCE_EXCESS_LOSS("委外超损"),

    /** 销售换货收费：换货单选择收费时生成的正向应收（单号后缀 -FEE） */
    SALE_EXCHANGE_CHARGE("销售换货收费"),

    /** 销售退货收费：退单选择收费时生成的正向应收（单号后缀 -FEE） */
    SALE_RETURN_CHARGE("销售退货收费"),

    /** 退货整理折损收款：整理后 B/C/不良品的折损，向客户收取（单号后缀 -LOSS） */
    RETURN_SORT_LOSS("退货整理折损"),

    /** 应付转应收：退货/超损扣款在无货款可抵扣时，转为向供应商收款（应收挂供应商） */
    PAYABLE_TRANSFER("应付转应收"),

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
