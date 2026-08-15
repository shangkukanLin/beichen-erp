package com.beichen.erp.dev.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.beichen.erp.dev.entity.DevMaterialFlow;

import java.util.List;

public interface DevMaterialFlowService extends IService<DevMaterialFlow> {

    /**
     * 查询某物料的流转记录列表（时间倒序，最新在前）
     */
    List<DevMaterialFlow> listByMaterial(Long materialId);

    /**
     * 新增一条流转记录
     */
    DevMaterialFlow add(DevMaterialFlow flow);

    /**
     * 修改流转记录
     */
    DevMaterialFlow update(DevMaterialFlow flow);

    /**
     * 删除流转记录
     */
    void delete(Long id);
}
