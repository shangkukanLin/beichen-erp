package com.beichen.erp.supplier.controller;

import com.beichen.erp.common.R;
import com.beichen.erp.outsource.entity.SupplierMaterial;
import com.beichen.erp.outsource.entity.dto.SupplierMaterialDTO;
import com.beichen.erp.outsource.service.SupplierMaterialService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/supplier")
@RequiredArgsConstructor
public class SupplierMaterialController {

    private final SupplierMaterialService supplierMaterialService;

    /** 查询供应商供应的物料列表 */
    @GetMapping("/{id}/materials")
    public R<List<SupplierMaterial>> listMaterials(@PathVariable Long id) {
        return R.ok(supplierMaterialService.listBySupplierId(id));
    }

    /** 保存供应商供应的物料（差量更新） */
    @PutMapping("/{id}/materials")
    public R<Void> saveMaterials(@PathVariable Long id, @RequestBody List<SupplierMaterialDTO> materials) {
        supplierMaterialService.saveMaterials(id, materials);
        return R.ok();
    }
}
