package com.beichen.erp.material.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.beichen.erp.material.entity.MaterialBom;

import java.util.List;

public interface MaterialBomService extends IService<MaterialBom> {

    /** 保存物料的子物料BOM组成（全量替换） */
    void saveChildren(Long materialId, List<MaterialBom> children);

    /** 删除物料相关的所有BOM关系 */
    void removeByMaterial(Long materialId);
}
