package com.beichen.erp.dev.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.dev.entity.BomType;

import java.util.List;

public interface BomTypeService {

    /** 查询当前公司启用的 BOM 类型（按排序） */
    List<BomType> enabled();

    /** 分页查询 BOM 类型 */
    Page<BomType> page(int pageNum, int pageSize);

    /** 新增 BOM 类型（同公司类型名称不可重复） */
    void add(BomType type);

    /** 更新 BOM 类型 */
    void update(BomType type);

    /** 删除 BOM 类型（类型下有关联物料时拦截） */
    void delete(Long id);

    /** 构建带公司过滤的条件构造器 */
    LambdaQueryWrapper<BomType> buildWrapper();
}
