package com.beichen.erp.dev.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.beichen.erp.dev.entity.Bom;

import java.util.List;

public interface BomService extends IService<Bom> {

    /** 获取项目最新版本BOM */
    List<Bom> listByProject(Long projectId);

    /** 按版本获取BOM */
    List<Bom> listByProjectAndVersion(Long projectId, Integer version);

    /** 获取项目的所有版本号列表 */
    List<Integer> getVersions(Long projectId);

    /** 获取最大版本号 */
    Integer getMaxVersion(Long projectId);

    /** 创建新版本（从当前最新版本复制） */
    List<Bom> createNewVersion(Long projectId);

    /** 批量保存BOM（全量替换：先删旧版本数据再批量插入） */
    void saveBatch(Long projectId, List<Bom> items);
}
