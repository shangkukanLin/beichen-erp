package com.beichen.erp.warehouse.common;

import lombok.Getter;

/**
 * 仓库类型（对应 warehouse.warehouse_type 字段，DB 存 code）
 * <p>须与前端 api/enums.ts 的 WarehouseType 保持一致，避免前后端过滤口径不一致。</p>
 */
@Getter
public enum WarehouseType {

    /** 辅料仓 */
    AUXILIARY("辅料仓"),

    /** 成品仓 */
    FINISHED("成品仓"),

    /** 不良仓 */
    DEFECT("不良仓"),

    /** 售后仓：销售退回待分类品的暂存仓，退货整理后再分流到成品仓/不良仓 */
    AFTER_SALE("售后仓");

    private final String label;

    WarehouseType(String label) {
        this.label = label;
    }

    /** 存入数据库的枚举 code */
    public String getCode() { return name(); }
}
