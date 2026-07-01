package com.beichen.erp.supplier.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.supplier.entity.Supplier;
import com.beichen.erp.supplier.entity.SupplierProduct;
import com.beichen.erp.supplier.entity.dto.SupplierDTO;
import com.beichen.erp.supplier.entity.dto.SupplierProductDTO;
import com.beichen.erp.supplier.entity.dto.SupplierQueryDTO;
import com.beichen.erp.supplier.service.SupplierProductService;
import com.beichen.erp.supplier.service.SupplierService;
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

@RestController
@RequestMapping("/api/supplier")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;
    private final SupplierProductService supplierProductService;

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

    @PostMapping
    public R<Void> add(@Valid @RequestBody SupplierDTO dto) {
        supplierService.create(dto);
        return R.ok();
    }

    @PutMapping
    public R<Void> update(@Valid @RequestBody SupplierDTO dto) {
        supplierService.update(dto);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        supplierService.delete(id);
        return R.ok();
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
}
