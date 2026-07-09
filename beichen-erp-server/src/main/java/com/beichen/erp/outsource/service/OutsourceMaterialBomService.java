package com.beichen.erp.outsource.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.beichen.erp.outsource.entity.OutsourceMaterialBom;
import com.beichen.erp.outsource.entity.OutsourceMaterialBomVO;
import com.beichen.erp.outsource.entity.OutsourceMaterialBriefVO;
import java.util.List;
import java.util.Map;

public interface OutsourceMaterialBomService extends IService<OutsourceMaterialBom> {
    void saveChildren(Long parentId, List<OutsourceMaterialBom> children);
    List<OutsourceMaterialBomVO> getDirect(Long parentId);
    List<OutsourceMaterialBomVO> getTree(Long materialId);
    List<OutsourceMaterialBriefVO> getWhereUsed(Long childId);
    void removeByMaterial(Long materialId);
    Map<String, List<OutsourceMaterialBomVO>> getChildrenByNames(List<String> materialNames);
}
