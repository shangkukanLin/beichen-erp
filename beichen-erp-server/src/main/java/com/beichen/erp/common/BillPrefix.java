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
    /** 销售订单 */
    public static final String SALE = "XS-";
    /** 销售订单（历史前缀，CommonController 解析兜底） */
    public static final String SALE_ORDER_LEGACY = "SO-";
    /** 销售出库 */
    public static final String SALE_OUTBOUND = "CK-";
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
    /** 委外其他出入库 */
    public static final String OUTSOURCE_OTHER_IO = "OWO-";
    /** 移仓单 */
    public static final String WAREHOUSE_MOVE = "YC-";
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
    /** 库存盘点重分类单 */
    public static final String STOCK_RECLASS = "FL-";
}
