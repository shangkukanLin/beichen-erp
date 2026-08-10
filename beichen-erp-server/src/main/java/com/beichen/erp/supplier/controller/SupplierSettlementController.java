package com.beichen.erp.supplier.controller;

import com.beichen.erp.common.R;
import com.beichen.erp.supplier.entity.dto.ReturnMaterialDTO;
import com.beichen.erp.supplier.service.SupplierSettlementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 供应商清算：汇总应付/订单/物料，一键退料，清算停用（业务下沉到 SupplierSettlementService） */
@RestController
@RequestMapping("/api/supplier-settlement")
@RequiredArgsConstructor
public class SupplierSettlementController {

    private final SupplierSettlementService settlementService;

    /** 清算汇总 */
    @GetMapping("/{supplierId}")
    public R<Map<String, Object>> summary(@PathVariable Long supplierId) {
        return R.ok(settlementService.summary(supplierId));
    }

    /** 一键退料：该供应商所有委外仓的正库存物料全部退回我方仓 */
    @PostMapping("/{supplierId}/return-materials")
    public R<Void> returnMaterials(@PathVariable Long supplierId, @Valid @RequestBody ReturnMaterialDTO dto) {
        settlementService.returnMaterials(supplierId, dto);
        return R.ok();
    }

    /** 清算完成：校验三项清零 → 停用供应商 */
    @PostMapping("/{supplierId}/finish")
    public R<Void> finish(@PathVariable Long supplierId) {
        settlementService.finish(supplierId);
        return R.ok();
    }
}
