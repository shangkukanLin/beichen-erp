package com.beichen.erp.supplier.service;

import java.util.Map;

/**
 * 供应商（加工厂）委外物料缺料汇总
 */
public interface SupplierMaterialSummaryService {

    /**
     * 加工厂物料缺料汇总（加工单 + 物料订单子物料需求，已送料来自收发单发料到该厂仓）
     * 统一以 outsource_material_id 为聚合口径，避免同名物料被错误合并
     */
    Map<String, Object> materialSummary(Long factoryId);
}
