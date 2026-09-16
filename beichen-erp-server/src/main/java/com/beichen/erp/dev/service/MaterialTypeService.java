package com.beichen.erp.dev.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.dev.entity.MaterialType;

import java.util.List;

public interface MaterialTypeService {

    /** 查询当前公司启用的 物料类型（按排序） */
    List<MaterialType> enabled();

    /** 分页查询 物料类型 */
    Page<MaterialType> page(int pageNum, int pageSize);

    /** 新增 物料类型（同公司类型名称不可重复） */
    void add(MaterialType type);

    /** 更新 物料类型 */
    void update(MaterialType type);

    /** 删除 物料类型（类型下有关联物料时拦截） */
    void delete(Long id);

    /** 构建带公司过滤的条件构造器 */
    LambdaQueryWrapper<MaterialType> buildWrapper();
}
