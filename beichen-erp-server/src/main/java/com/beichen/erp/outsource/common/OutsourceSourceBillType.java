package com.beichen.erp.outsource.common;

import lombok.Getter;

/**
 * 委外模块应付台账来源单据类型
 * <p>
 * 统一 PayableHelper.createPayable 的 sourceBillType 口径，
 * 替代此前散落的中文硬编码（"委外加工交货"/"委外退料"/"取消退料"）与裸字符串常量，
 * 便于财务侧按类型统计与对账。
 */
@Getter
public enum OutsourceSourceBillType {

    /** 委外加工单交货（成品报工入库产生应付） */
    OUTSOURCE_DELIVERY("OUTSOURCE_DELIVERY", "委外加工交货"),

    /** 委外物料订单收货 / 退不良（收发单审核产生应付） */
    OUTSOURCE_MATERIAL_DELIVERY("OUTSOURCE_MATERIAL_DELIVERY", "委外物料收发"),

    /** 委外退料（负向应付冲减） */
    OUTSOURCE_RETURN("OUTSOURCE_RETURN", "委外退料");

    private final String code;
    private final String label;

    OutsourceSourceBillType(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
