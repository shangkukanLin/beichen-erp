package com.beichen.erp.inventory.common;

/**
 * 库存流水关联单据类型枚举
 * <p>统一管理 inventory_stock_log.related_bill_type 字段，标识库存变动由哪个业务模块触发。</p>
 */
public enum RelatedBillType {

    /** 采购单：采购订单审核/反审核触发 */
    PURCHASE_ORDER("采购单"),
    /** 采购入库：采购入库单审核触发 */
    PURCHASE_INBOUND("采购入库"),
    /** 成品退货单：退货单审核/反审核触发 */
    PURCHASE_RETURN("成品退货单"),

    /** 销售单：销售订单审核触发 */
    SALE_ORDER("销售单"),
    /** 销售出库：销售出库单审核触发 */
    SALE_OUTBOUND("销售出库"),

    /** 移仓单：移仓审核/反审核触发 */
    WAREHOUSE_MOVE("移仓单"),
    /** 移仓单反审核：移仓反审核触发 */
    WAREHOUSE_MOVE_UN_AUDIT("移仓单(反审核)"),

    /** 其他出入库：其他出入库触发 */
    OTHER_IO("其他出入库"),

    /** 委外发料：委外收发单发料触发 */
    OUTSOURCE_DELIVERY("委外发料"),
    /** 委外退货：委外退货单触发 */
    OUTSOURCE_RETURN("委外退货"),
    /** 委外加工：委外加工交货回滚触发 */
    OUTSOURCE_ORDER("委外加工"),
    /** 退不良：委外退不良品触发 */
    OUTSOURCE_DEFECT("退不良"),

    /** 物料收发：委外发料 adjustSourceStock 触发 */
    MATERIAL_IO("物料收发"),

    /** 供应商清算：供应商清算退料触发 */
    SUPPLIER_SETTLEMENT("供应商清算");

    private final String label;

    RelatedBillType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public String getCode() {
        return name();
    }

    public static RelatedBillType fromCode(String code) {
        if (code == null || code.isBlank()) return null;
        for (RelatedBillType t : values()) {
            if (t.name().equalsIgnoreCase(code)) return t;
        }
        for (RelatedBillType t : values()) {
            if (t.label.equals(code)) return t;
        }
        return null;
    }
}
