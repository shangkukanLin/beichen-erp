package com.beichen.erp.dev.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.beichen.erp.dev.entity.DevMaterial;

import java.util.List;

public interface DevMaterialService extends IService<DevMaterial> {

    /** 根据项目ID查询所有物料 */
    List<DevMaterial> listByProjectId(Long projectId);

    /** 新增物料 */
    void add(DevMaterial material);

    /** 更新物料 */
    void update(DevMaterial material);

    /** 删除物料 */
    void remove(Long id);
}
