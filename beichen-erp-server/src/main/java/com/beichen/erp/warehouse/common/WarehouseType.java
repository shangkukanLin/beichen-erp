package com.beichen.erp.warehouse.common;

import lombok.Getter;

/**
 * 仓库类型（对应 warehouse.warehouse_type 字段，DB 存 code）
 *
 * <p>2026-09-16 用户要求（方案 A）：仓型由 4 种**收敛为 2 种** —— 成品仓 / 辅料仓。
 * 原「不良仓」「售后仓」不再作为仓型，理由与替代方式：
 * <ul>
 *   <li>退回品 / 不良品**统一入自有成品仓**，改用**品质**区分（warehouse_stock 本就是
 *       (仓库,产品,品质) 分行：A/B/C 良品、DEFECT 不良、PENDING 待整理）；</li>
 *   <li>委外仓（warehouse_category=OUTSOURCE）**不再写仓型**（保持 NULL，仓型仅对自有仓有意义）。</li>
 * </ul>
 * 须与前端 api/enums.ts 的 WarehouseType 保持一致，避免前后端过滤口径不一致。</p>
 */
@Getter
public enum WarehouseType {

    /** 辅料仓（自有物料仓：物料仓库 → 自有物料仓） */
    AUXILIARY("辅料仓"),

    /** 成品仓（自有成品仓：成品库存 → 仓库管理） */
    FINISHED("成品仓");

    private final String label;

    WarehouseType(String label) {
        this.label = label;
    }

    /** 存入数据库的枚举 code */
    public String getCode() { return name(); }
}
