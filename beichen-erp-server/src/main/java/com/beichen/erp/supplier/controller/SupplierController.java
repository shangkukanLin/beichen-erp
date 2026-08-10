package com.beichen.erp.supplier.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.supplier.entity.Supplier;
import com.beichen.erp.supplier.entity.SupplierProduct;
import com.beichen.erp.supplier.entity.dto.ReturnMaterialDTO;
import com.beichen.erp.supplier.entity.dto.SupplierDTO;
import com.beichen.erp.supplier.entity.dto.SupplierProductDTO;
import com.beichen.erp.supplier.entity.dto.SupplierQueryDTO;
import com.beichen.erp.supplier.service.SupplierMaterialSummaryService;
import com.beichen.erp.supplier.service.SupplierProductService;
import com.beichen.erp.supplier.service.SupplierService;
import com.beichen.erp.supplier.service.SupplierSettlementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/supplier")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;
    private final SupplierProductService supplierProductService;
    private final SupplierMaterialSummaryService materialSummaryService;
    private final SupplierSettlementService settlementService;

    @GetMapping("/page")
    public R<Page<Supplier>> page(SupplierQueryDTO query) {
        return R.ok(supplierService.page(query));
    }

    @GetMapping("/{id}")
    public R<Supplier> getById(@PathVariable Long id) {
        return R.ok(supplierService.getById(id));
    }

    @GetMapping("/{id}/products")
    public R<List<SupplierProduct>> listProducts(@PathVariable Long id) {
        return R.ok(supplierProductService.listBySupplierId(id));
    }

    /** 新增供应商，返回供应商ID */
    @PostMapping
    public R<Long> add(@Valid @RequestBody SupplierDTO dto) {
        Long id = supplierService.create(dto);
        return R.ok(id);
    }

    @PutMapping
    public R<Void> update(@Valid @RequestBody SupplierDTO dto) {
        supplierService.update(dto);
        return R.ok();
    }

    /** 删除改为停用（不再物理删除，避免采购单/应付单变孤儿数据） */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        supplierService.delete(id);
        return R.ok();
    }

    /** 检查供应商是否有关联数据，返回可停用标志和关联明细 */
    @GetMapping("/{id}/check-delete")
    public R<Map<String, Object>> checkDelete(@PathVariable Long id) {
        return R.ok(supplierService.checkDelete(id));
    }

    @PutMapping("/{id}/status")
    public R<Void> toggleStatus(@PathVariable Long id) {
        supplierService.toggleStatus(id);
        return R.ok();
    }

    @PutMapping("/{id}/products")
    public R<Void> saveProducts(@PathVariable Long id, @RequestBody List<SupplierProductDTO> products) {
        supplierProductService.saveProducts(id, products);
        return R.ok();
    }

    /** 委外加工厂物料缺料汇总（业务已下沉到 SupplierMaterialSummaryService） */
    @GetMapping("/{factoryId}/material-summary")
    public R<Map<String, Object>> materialSummary(@PathVariable Long factoryId) {
        return R.ok(materialSummaryService.materialSummary(factoryId));
    }

    /** 供应商委外库存一键退料到指定仓库 */
    @PostMapping("/{id}/return-materials")
    public R<Void> returnMaterials(@PathVariable Long id, @Valid @RequestBody ReturnMaterialDTO dto) {
        settlementService.returnMaterials(id, dto);
        return R.ok();
    }
}
