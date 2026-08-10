package com.beichen.erp.supplier.service;

import com.beichen.erp.supplier.entity.dto.ReturnMaterialDTO;

import java.util.Map;

/**
 * 供应商清算业务（汇总应付/订单/物料、一键退料、清算停用）
 */
public interface SupplierSettlementService {

    /**
     * 清算汇总
     */
    Map<String, Object> summary(Long supplierId);

    /**
     * 一键退料：该供应商所有委外仓的正库存物料全部退回我方仓
     */
    void returnMaterials(Long supplierId, ReturnMaterialDTO dto);

    /**
     * 清算完成：校验三项清零 → 停用供应商（行锁防 TOCTOU）
     */
    void finish(Long supplierId);
}
