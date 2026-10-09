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

    /**
     * 校验一行 BOM 的**用户输入**（2026-10-09：损耗率% 放开为可就地修改后才需要 —— 后端不信任前端）。
     *
     * <p>口径：损耗率为**百分数 0~100**（与「加工单下单」页同口径）；结单良率按
     * {@code targetYieldRate = 100 − lossRate} 计算 ⇒ 越界值（如 500）会把良率算成负数 ✗。
     * 前端已用 `el-input-number` 的 :min/:max 限制，这里兜底"直调 API"的情况。
     * null / 空 ⇒ 归 0（与列默认值一致，避免库里出现 NULL）。违反时抛 {@code BusinessException}。</p>
     */
    void validateItem(Bom bom);

    /** 批量保存BOM（全量替换：先删旧版本数据再批量插入） */
    void saveBatch(Long projectId, List<Bom> items);

    /** 删除单个BOM项（禁止删除所在版本的唯一明细，避免版本号回退导致历史版本被改写） */
    void deleteItem(Long id);
}
