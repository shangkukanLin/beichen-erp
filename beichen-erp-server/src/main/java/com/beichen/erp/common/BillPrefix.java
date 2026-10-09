package com.beichen.erp.common;

/**
 * 单据号前缀常量
 * <p>
 * 集中管理全系统各业务单据的编码前缀，禁止在 Service 中散落硬编码前缀字符串。
 * </p>
 */
public final class BillPrefix {

    private BillPrefix() {}

    /** 采购订单 */
    public static final String PURCHASE = "CG-";
    /** 采购退货 */
    public static final String PURCHASE_RETURN = "TH-";
    /** 采购换货单（进货业务：把采购成品退回供货商换新，2026-09-18 新增；CH- 已核实未被占用） */
    public static final String PURCHASE_EXCHANGE = "CH-";
    /** 销售订单 */
    public static final String SALE = "XS-";
    /** 销售订单（历史前缀，CommonController 解析兜底） */

    /** 销售退货 */
    public static final String SALE_RETURN = "XTH-";
    /** 付款单 */
    public static final String PAYMENT = "FK-";
    /** 收款单 */
    public static final String RECEIPT = "SK-";
    /** 应付单 */
    public static final String PAYABLE = "YF-";
    /** 财务账单 */
    public static final String BILL = "ZD-";
    /** 费用登记单 */
    public static final String EXPENSE = "FY-";
    /** 资金流水 */
    public static final String CASHFLOW = "FL-";
    /** 委外加工单 */
    public static final String OUTSOURCE_ORDER = "WO-";
    /** 委外交货/收发单 */
    public static final String OUTSOURCE_DELIVERY = "DEL-";
    /** 委外物料订单 */
    public static final String OUTSOURCE_MATERIAL_ORDER = "MWO-";
    /** 委外采购物料订单（历史前缀，与 OUTSOURCE_MATERIAL_ORDER 等价） */
    public static final String OUTSOURCE_PO = "PO-";
    /** 委外退不良/缺陷单 */
    public static final String OUTSOURCE_DEFECT = "DEF-";
    /** 委外物料退货单 */
    public static final String OUTSOURCE_MATERIAL_RETURN = "MR-";
    /** 委外加工退货单（成品退回；原先 OR- 硬编码在 Service 里，2026-09-12 收进常量表） */
    public static final String OUTSOURCE_RETURN_ORDER = "OR-";
    /** 委外加工返回单（P1-2 2026-09-25：修好送回——核销在厂成品+实际用料+赔料应收；ORB- 独立取号） */
    public static final String OUTSOURCE_RETURN_BACK = "ORB-";
    /** 加工退货红冲·关联加工单（2026-09-25：红冲记录自动单号，与无单区分） */
    public static final String OUTSOURCE_DEFECT_RETURN_HAS = "GTH-";
    /** 加工退货红冲·不关联加工单（2026-09-25） */
    public static final String OUTSOURCE_DEFECT_RETURN_NO = "GTW-";
    /** 物料退货单·关联物料订单（2026-09-25：与不关联区分；存量 MR- 单号不变） */
    public static final String OUTSOURCE_MATERIAL_RETURN_HAS = "MRH-";
    /** 物料退货单·不关联物料订单（2026-09-25） */
    public static final String OUTSOURCE_MATERIAL_RETURN_NO = "MRW-";
    /** 委外其他出入库 */
    public static final String OUTSOURCE_OTHER_IO = "OWO-";
    /** 移仓单 */
    public static final String WAREHOUSE_MOVE = "YC-";
    /** 物料移仓单（2026-09-24 新增，替代已下线的手工物料收发单；与成品移仓 YC- 各自独立取号） */
    public static final String MATERIAL_MOVE = "MYC-";
    /** 品质重分类单 */
    public static final String RECLASSIFY = "PC-";
    /** 进销存其他出入库单 */
    public static final String INVENTORY_OTHER_IO = "QT-";
    /** 仓库 */
    public static final String WAREHOUSE = "WH-";
    /** 客户 */
    public static final String CUSTOMER = "CU-";
    /** 供应商结算 */
    public static final String SUPPLIER_SETTLEMENT = "DEL-";
    /** 研发项目 */
    public static final String DEV_PROJECT = "DEV-";
    /** 研发BUG */
    public static final String DEV_BUG = "DEV_BUG-";
    /** 其他出入库单（委外关单遗失，历史遗留，保留兼容） */
    public static final String OTHER_IO = "IO-";
    /** 退货整理单 */
    public static final String RETURN_SORT = "TS-";
    /** 销售换货单 */
    public static final String SALE_EXCHANGE = "HH-";
    /** 产品 SKU（产品级唯一编码，新增产品时自动生成：SKU-000001） */
    public static final String PRODUCT_SKU = "SKU-";
    /**
     * 研发立项自动建产品时使用的 SKU 前缀（2026-09-21 用户需求：「默认自动生成可修改，以 NS 打头」）。
     * <p>与 {@link #PRODUCT_SKU} **各自独立取号**（{@code NS-000001} 与 {@code SKU-000001} 可并存）。</p>
     */
    public static final String PRODUCT_SKU_NS = "NS-";
    /** 库存盘点单（每月每仓一次，周期 yyyy-MM） */
    public static final String STOCK_TAKE = "PD-";
    /** 应付转应收单（负数应付在无货款可抵时转为向供应商收款） */
    public static final String PAYABLE_TRANSFER = "PZ-";
    /** 成品报损单 */
    public static final String INVENTORY_STOCK_LOSS = "BS-";
    /** 委外物料报损单（W=委外，与成品报损 BS- 区分） */
    public static final String OUTSOURCE_STOCK_LOSS = "WBS-";
}
