package com.beichen.erp.outsource.common;

import lombok.Getter;

/**
 * 交货记录来源类型枚举
 * <p>
 * 管理 outsource_order_delivery.source_type 字段。
 * 用于标记交货记录的来源业务：
 * <ul>
 *     <li>AFTER_SALE：收费售后（客户退回不良品，不关联加工单）</li>
 * </ul>
 * 加工单正常交货/退不良等记录 source_type 为空，不在此枚举中。
 * </p>
 */
@Getter
public enum DeliverySourceType {

    /** 收费售后：客户退回不良品入库 */
    AFTER_SALE("AFTER_SALE", "收费售后");

    private final String code;
    private final String label;

    DeliverySourceType(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
