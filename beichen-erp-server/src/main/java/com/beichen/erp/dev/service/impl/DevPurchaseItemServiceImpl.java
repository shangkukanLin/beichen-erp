package com.beichen.erp.dev.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.common.PageParam;
import com.beichen.erp.dev.common.DevMaterialLocationTypeEnum;
import com.beichen.erp.dev.entity.Project;
import com.beichen.erp.dev.entity.DevPurchaseItem;
import com.beichen.erp.dev.mapper.DevPurchaseItemMapper;
import com.beichen.erp.dev.mapper.ProjectMapper;
import com.beichen.erp.dev.service.DevPurchaseItemService;
import com.beichen.erp.inventory.entity.InventoryWarehouse;
import com.beichen.erp.inventory.mapper.InventoryWarehouseMapper;
import com.beichen.erp.outsource.entity.OutsourceWarehouse;
import com.beichen.erp.outsource.mapper.OutsourceWarehouseMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 研发物料管理业务实现
 */
@Service
public class DevPurchaseItemServiceImpl extends ServiceImpl<DevPurchaseItemMapper, DevPurchaseItem> implements DevPurchaseItemService {

    /** 未关联项目显示文案 */
    private static final String PROJECT_NAME_UNLINKED = "未关联";

    private final ProjectMapper devProjectMapper;
    private final InventoryWarehouseMapper inventoryWarehouseMapper;
    private final OutsourceWarehouseMapper outsourceWarehouseMapper;

    @Autowired
    public DevPurchaseItemServiceImpl(ProjectMapper devProjectMapper,
                                      InventoryWarehouseMapper inventoryWarehouseMapper,
                                      OutsourceWarehouseMapper outsourceWarehouseMapper) {
        this.devProjectMapper = devProjectMapper;
        this.inventoryWarehouseMapper = inventoryWarehouseMapper;
        this.outsourceWarehouseMapper = outsourceWarehouseMapper;
    }

    @Override
    public IPage<Map<String, Object>> pageMaterial(PageParam pageParam, String name, Long projectId, String type) {
        Page<DevPurchaseItem> page = new Page<>(pageParam.getPageNum(), pageParam.getPageSize());
        var qw = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DevPurchaseItem>()
                .like(name != null && !name.isBlank(), DevPurchaseItem::getName, name)
                .eq(projectId != null, DevPurchaseItem::getProjectId, projectId)
                .eq(type != null && !type.isBlank(), DevPurchaseItem::getType, type)
                .orderByDesc(DevPurchaseItem::getId);
        IPage<DevPurchaseItem> itemPage = this.page(page, qw);

        // 批量查询关联项目名称，避免 N+1
        List<Long> pidList = itemPage.getRecords().stream()
                .map(DevPurchaseItem::getProjectId).filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, String> projectNameMap = new HashMap<>();
        if (!pidList.isEmpty()) {
            List<Project> projects = devProjectMapper.selectBatchIds(pidList);
            for (Project p : projects) projectNameMap.put(p.getId(), p.getName());
        }

        // 区分两类仓库 id，批量查名避免 N+1
        List<Long> inventoryIds = new ArrayList<>();
        List<Long> outsourceIds = new ArrayList<>();
        for (DevPurchaseItem item : itemPage.getRecords()) {
            if (item.getWarehouseId() == null || item.getWarehouseType() == null) continue;
            if (DevMaterialLocationTypeEnum.INVENTORY.getCode().equals(item.getWarehouseType())) {
                inventoryIds.add(item.getWarehouseId());
            } else if (DevMaterialLocationTypeEnum.OUTSOURCE.getCode().equals(item.getWarehouseType())) {
                outsourceIds.add(item.getWarehouseId());
            }
        }
        Map<Long, String> inventoryNameMap = new HashMap<>();
        if (!inventoryIds.isEmpty()) {
            List<InventoryWarehouse> ws = inventoryWarehouseMapper.selectBatchIds(inventoryIds);
            for (InventoryWarehouse w : ws) inventoryNameMap.put(w.getId(), w.getWarehouseName());
        }
        Map<Long, String> outsourceNameMap = new HashMap<>();
        if (!outsourceIds.isEmpty()) {
            List<OutsourceWarehouse> ws = outsourceWarehouseMapper.selectBatchIds(outsourceIds);
            for (OutsourceWarehouse w : ws) outsourceNameMap.put(w.getId(), w.getWarehouseName());
        }
        // 批量查询仓库地址，避免 N+1；位置详情按 warehouseId 实时带出，不存快照
        Map<Long, String> inventoryAddrMap = new HashMap<>();
        if (!inventoryIds.isEmpty()) {
            List<InventoryWarehouse> ws = inventoryWarehouseMapper.selectBatchIds(inventoryIds);
            for (InventoryWarehouse w : ws) inventoryAddrMap.put(w.getId(), w.getAddress());
        }
        Map<Long, String> outsourceAddrMap = new HashMap<>();
        if (!outsourceIds.isEmpty()) {
            List<OutsourceWarehouse> ws = outsourceWarehouseMapper.selectBatchIds(outsourceIds);
            for (OutsourceWarehouse w : ws) outsourceAddrMap.put(w.getId(), w.getAddress());
        }

        IPage<Map<String, Object>> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (DevPurchaseItem item : itemPage.getRecords()) {
            rows.add(toMap(item, projectNameMap, inventoryNameMap, outsourceNameMap, inventoryAddrMap, outsourceAddrMap));
        }
        result.setRecords(rows);
        return result;
    }

    @Override
    public List<Map<String, Object>> warehouseOptions() {
        Long companyId = CompanyContext.get();
        List<Map<String, Object>> options = new ArrayList<>();

        // 自有仓库：当前公司 + 启用
        List<InventoryWarehouse> invList = inventoryWarehouseMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<InventoryWarehouse>()
                        .eq(companyId != null, InventoryWarehouse::getCompanyId, companyId)
                        .eq(InventoryWarehouse::getStatus, 1)
                        .orderByAsc(InventoryWarehouse::getId));
        for (InventoryWarehouse w : invList) {
            Map<String, Object> m = new HashMap<>();
            m.put("value", DevMaterialLocationTypeEnum.INVENTORY.getCode() + ":" + w.getId());
            m.put("warehouseId", w.getId());
            m.put("warehouseType", DevMaterialLocationTypeEnum.INVENTORY.getCode());
            m.put("warehouseName", w.getWarehouseName());
            m.put("address", w.getAddress());
            m.put("groupLabel", DevMaterialLocationTypeEnum.INVENTORY.getLabel());
            options.add(m);
        }

        // 委外仓库：当前公司 + 启用，名称拼接供应商名便于区分
        List<OutsourceWarehouse> outList = outsourceWarehouseMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OutsourceWarehouse>()
                        .eq(companyId != null, OutsourceWarehouse::getCompanyId, companyId)
                        .eq(OutsourceWarehouse::getStatus, 1)
                        .orderByAsc(OutsourceWarehouse::getId));
        for (OutsourceWarehouse w : outList) {
            Map<String, Object> m = new HashMap<>();
            m.put("value", DevMaterialLocationTypeEnum.OUTSOURCE.getCode() + ":" + w.getId());
            m.put("warehouseId", w.getId());
            m.put("warehouseType", DevMaterialLocationTypeEnum.OUTSOURCE.getCode());
            m.put("warehouseName", w.getWarehouseName());
            m.put("address", w.getAddress());
            m.put("groupLabel", DevMaterialLocationTypeEnum.OUTSOURCE.getLabel());
            options.add(m);
        }
        return options;
    }

    /**
     * 将实体转为前端分页所需的 Map（HashMap 无 setter，必须显式逐字段 put，不能用 BeanUtils.copyProperties）
     */
    private Map<String, Object> toMap(DevPurchaseItem item, Map<Long, String> projectNameMap,
                                      Map<Long, String> inventoryNameMap, Map<Long, String> outsourceNameMap,
                                      Map<Long, String> inventoryAddrMap, Map<Long, String> outsourceAddrMap) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", item.getId());
        map.put("projectId", item.getProjectId());
        map.put("companyId", item.getCompanyId());
        map.put("name", item.getName());
        map.put("type", item.getType());
        map.put("quantity", item.getQuantity());
        map.put("locationDetail", item.getLocationDetail());
        map.put("purchaseDate", item.getPurchaseDate());
        map.put("amount", item.getAmount());
        map.put("status", item.getStatus());
        map.put("remark", item.getRemark());
        map.put("createTime", item.getCreateTime());
        map.put("updateTime", item.getUpdateTime());
        map.put("warehouseId", item.getWarehouseId());
        map.put("warehouseType", item.getWarehouseType());
        // 仓库名称、地址均按 warehouseId + warehouseType 实时查询，不依赖快照文本
        map.put("warehouseName", resolveWarehouseName(item, inventoryNameMap, outsourceNameMap));
        map.put("warehouseAddress", resolveWarehouseAddress(item, inventoryAddrMap, outsourceAddrMap));
        Long pid = item.getProjectId();
        map.put("projectName", pid != null ? projectNameMap.getOrDefault(pid, PROJECT_NAME_UNLINKED) : PROJECT_NAME_UNLINKED);
        return map;
    }

    private String resolveWarehouseName(DevPurchaseItem item, Map<Long, String> inventoryNameMap, Map<Long, String> outsourceNameMap) {
        if (item.getWarehouseId() == null || item.getWarehouseType() == null) {
            return "";
        }
        if (DevMaterialLocationTypeEnum.INVENTORY.getCode().equals(item.getWarehouseType())) {
            return inventoryNameMap.getOrDefault(item.getWarehouseId(), "");
        } else if (DevMaterialLocationTypeEnum.OUTSOURCE.getCode().equals(item.getWarehouseType())) {
            return outsourceNameMap.getOrDefault(item.getWarehouseId(), "");
        }
        return "";
    }

    private String resolveWarehouseAddress(DevPurchaseItem item, Map<Long, String> inventoryAddrMap, Map<Long, String> outsourceAddrMap) {
        if (item.getWarehouseId() == null || item.getWarehouseType() == null) {
            return "";
        }
        if (DevMaterialLocationTypeEnum.INVENTORY.getCode().equals(item.getWarehouseType())) {
            return inventoryAddrMap.getOrDefault(item.getWarehouseId(), "");
        } else if (DevMaterialLocationTypeEnum.OUTSOURCE.getCode().equals(item.getWarehouseType())) {
            return outsourceAddrMap.getOrDefault(item.getWarehouseId(), "");
        }
        return "";
    }
}
