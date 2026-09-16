package com.beichen.erp.inventory.common;

import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.warehouse.common.WarehouseCategory;
import com.beichen.erp.warehouse.common.WarehouseType;
import com.beichen.erp.warehouse.entity.Warehouse;

/**
 * 库存盘点范围（2026-09-16 用户要求：成品盘点与物料盘点彻底分开）。
 *
 * <p>判定口径（**必须与前端 StockTakePanel 的 inScopeOf 保持一致**）：
 * <ul>
 *   <li>{@link #PRODUCT} 成品盘点：自有仓（INVENTORY）中**除辅料仓**以外的仓库 —— 成品仓 / 不良仓 / 售后仓</li>
 *   <li>{@link #MATERIAL} 物料盘点：委外仓（OUTSOURCE）+ 自有辅料仓（INVENTORY + AUXILIARY）</li>
 * </ul>
 * 两者对「自有仓 + 委外仓」覆盖且互斥；warehouse_category 为空的异常数据两边都不算（不参与任何盘点）。
 * </p>
 */
public enum StockTakeScope {

    /** 成品盘点：成品仓 / 不良仓 / 售后仓 */
    PRODUCT,

    /** 物料盘点：委外仓 + 自有物料仓（辅料仓） */
    MATERIAL;

    /** 解析范围参数：null/空 = 不过滤（兼容旧调用）；无法识别则报错，避免静默按"全部"处理 */
    public static StockTakeScope of(String code) {
        if (code == null || code.isBlank()) return null;
        for (StockTakeScope s : values()) {
            if (s.name().equalsIgnoreCase(code.trim())) return s;
        }
        throw new BusinessException("未知的盘点范围：" + code);
    }

    /** 该仓库是否属于本范围 */
    public boolean test(Warehouse w) {
        if (w == null || w.getWarehouseCategory() == null) return false;
        boolean outsource = WarehouseCategory.OUTSOURCE.getCode().equals(w.getWarehouseCategory());
        boolean inventory = WarehouseCategory.INVENTORY.getCode().equals(w.getWarehouseCategory());
        boolean auxiliary = WarehouseType.AUXILIARY.getCode().equals(w.getWarehouseType());
        // 注意：委外仓的 warehouse_type 也是 AUXILIARY（历史数据口径），故 MATERIAL 判定以 category 为主
        return this == PRODUCT ? (inventory && !auxiliary) : (outsource || (inventory && auxiliary));
    }
}
