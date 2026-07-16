package com.beichen.erp.supplier.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.beichen.erp.supplier.entity.Supplier;
import com.beichen.erp.supplier.entity.dto.SupplierDTO;
import com.beichen.erp.supplier.entity.dto.SupplierQueryDTO;

import java.util.Map;

public interface SupplierService extends IService<Supplier> {

    Page<Supplier> page(SupplierQueryDTO query);

    String generateCode(String type);

    void create(SupplierDTO dto);

    void update(SupplierDTO dto);

    void delete(Long id);

    void toggleStatus(Long id);

    /** 检查供应商是否有关联数据，返回可删除标志和关联明细 */
    Map<String, Object> checkDelete(Long id);
}
