package com.beichen.erp.supplier.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.beichen.erp.supplier.entity.Supplier;
import com.beichen.erp.supplier.entity.dto.SupplierDTO;
import com.beichen.erp.supplier.entity.dto.SupplierQueryDTO;

import java.util.Map;

public interface SupplierService extends IService<Supplier> {

    Page<Supplier> page(SupplierQueryDTO query);

    /**
     * 详情查询：补充回填类型编码列表 typeCodes（transient 字段，selectById 不会填充）
     */
    Supplier getById(Long id);

    String generateCode(String type);

    /**
     * 新建供应商，返回供应商ID（同名追加类型时返回已存在的ID）
     */
    Long create(SupplierDTO dto);

    void update(SupplierDTO dto);

    /**
     * 删除改为停用：仅置 status=0，不物理删除，避免采购单/应付单变孤儿数据
     */
    void delete(Long id);

    void toggleStatus(Long id);

    /**
     * 检查供应商是否有关联数据，返回可停用标志和关联明细
     */
    Map<String, Object> checkDelete(Long id);
}
