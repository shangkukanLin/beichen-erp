package com.beichen.erp.outsource.common;

/**
 * 退不良处理方式枚举
 */
public enum DefectHandleType {

    /** 维修退货：不良品实物退回委外仓、BOM 物料还回工厂委外仓，不涉及款项 */
    REPAIR_RETURN("维修退货"),
    /**
     * 折现退款：按加工单价折现，向供应商收取退款（生成负向应付）。
     * <p><b>实物同样退回</b>：不良品从成品仓扣减、BOM 物料还回工厂委外仓，
     * 与维修退货的区别只在"款项"（本方式产生负应付），库存处理完全一致。</p>
     */
    CASH_REFUND("折现退款");

    private final String label;

    DefectHandleType(String label) { this.label = label; }

    public String getLabel() { return label; }
    public String getCode() { return name(); }
}
