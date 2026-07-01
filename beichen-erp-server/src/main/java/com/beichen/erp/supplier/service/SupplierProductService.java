package com.beichen.erp.supplier.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.beichen.erp.supplier.entity.SupplierProduct;
import com.beichen.erp.supplier.entity.dto.SupplierProductDTO;

import java.util.List;

public interface SupplierProductService extends IService<SupplierProduct> {

    List<SupplierProduct> listBySupplierId(Long supplierId);

    void saveProducts(Long supplierId, List<SupplierProductDTO> products);
}
