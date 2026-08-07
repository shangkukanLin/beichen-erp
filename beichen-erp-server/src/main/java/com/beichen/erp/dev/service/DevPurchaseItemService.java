package com.beichen.erp.dev.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.beichen.erp.common.PageParam;
import com.beichen.erp.dev.entity.DevPurchaseItem;

import java.util.List;
import java.util.Map;

public interface DevPurchaseItemService extends IService<DevPurchaseItem> {

    /**
     * 研发物料全局分页查询
     * @param name 物料名称模糊（可空）
     * @param projectId 研发项目ID过滤（可空，为空则查询全部含未关联项目）
     * @param type 物料类型过滤（可空，中文标签，如"基板"）
     */
    IPage<Map<String, Object>> pageMaterial(PageParam pageParam, String name, Long projectId, String type);

    /**
     * 查询当前公司下所有启用的仓库（含自有仓库与委外仓库），供研发物料「存放位置」下拉使用
     * 返回 Map 列表，每项含：value(type:id)、warehouseId、warehouseType、warehouseName、groupLabel
     */
    List<Map<String, Object>> warehouseOptions();
}
