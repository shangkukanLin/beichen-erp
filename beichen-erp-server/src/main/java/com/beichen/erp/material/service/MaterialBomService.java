package com.beichen.erp.material.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.beichen.erp.material.entity.MaterialBom;
import com.beichen.erp.material.entity.MaterialBomVO;
import com.beichen.erp.material.entity.MaterialBriefVO;

import java.util.List;

public interface MaterialBomService extends IService<MaterialBom> {

    /** 保存(替换)某父物料的直接子物料组成 */
    void saveChildren(Long parentId, List<MaterialBom> children);

    /** 查询某父物料的直接子物料(含实时物料信息) */
    List<MaterialBomVO> getDirect(Long parentId);

    /** 递归构建某物料的多级BOM树(含实时物料信息，自动防环) */
    List<MaterialBomVO> getTree(Long materialId);

    /** 查询某子物料被哪些父物料使用(向上追溯) */
    List<MaterialBriefVO> getWhereUsed(Long childId);

    /** 删除与某物料相关的所有BOM关系(作为父或作为子) */
    void removeByMaterial(Long materialId);
}
