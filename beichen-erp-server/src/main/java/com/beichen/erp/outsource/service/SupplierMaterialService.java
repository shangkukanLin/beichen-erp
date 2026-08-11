package com.beichen.erp.outsource.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.beichen.erp.outsource.entity.SupplierMaterial;
import com.beichen.erp.outsource.entity.dto.SupplierMaterialDTO;

import java.util.List;

public interface SupplierMaterialService extends IService<SupplierMaterial> {

    /** 按供应商ID查询其供应的物料列表（联查物料名称/规格/BOM类型名） */
    List<SupplierMaterial> listBySupplierId(Long supplierId);

    /** 按物料ID生成供应商ID逗号串（供 outsource_material 列表回填 supplierIds 字段） */
    String listSupplierIdsByMaterial(Long materialId);

    /** 差量保存某供应商供应的物料：新增、更新、删除 */
    void saveMaterials(Long supplierId, List<SupplierMaterialDTO> materials);

    /** 差量保存某物料对应的供应商：新增、更新、删除（与 saveMaterials 反向维度） */
    void saveMaterialsByMaterial(Long materialId, List<SupplierMaterialDTO> materials);
}
