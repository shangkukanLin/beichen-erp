package com.beichen.erp.dev.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.common.PageParam;
import com.beichen.erp.dev.common.DevMaterialPlaceTypeEnum;
import com.beichen.erp.dev.entity.DevMaterialFlow;
import com.beichen.erp.dev.entity.DevPurchaseItem;
import com.beichen.erp.dev.entity.Project;
import com.beichen.erp.dev.mapper.DevMaterialFlowMapper;
import com.beichen.erp.dev.mapper.DevPurchaseItemMapper;
import com.beichen.erp.dev.mapper.ProjectMapper;
import com.beichen.erp.dev.service.DevPurchaseItemService;
import com.beichen.erp.warehouse.common.WarehouseCategory;
import com.beichen.erp.warehouse.entity.Warehouse;
import com.beichen.erp.warehouse.mapper.WarehouseMapper;
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
    private final WarehouseMapper warehouseMapper;
    private final DevMaterialFlowMapper materialFlowMapper;

    @Autowired
    public DevPurchaseItemServiceImpl(ProjectMapper devProjectMapper,
                                      WarehouseMapper warehouseMapper,
                                      DevMaterialFlowMapper materialFlowMapper) {
        this.devProjectMapper = devProjectMapper;
        this.warehouseMapper = warehouseMapper;
        this.materialFlowMapper = materialFlowMapper;
    }

    @Override
    public IPage<Map<String, Object>> pageMaterial(PageParam pageParam, String name, Long projectId, String type) {
        Page<DevPurchaseItem> page = new Page<>(pageParam.getPageNum(), pageParam.getPageSize());
        var qw = new LambdaQueryWrapper<DevPurchaseItem>()
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

        // 批量回填当前位置（取各物料最新流转记录）
        fillLatestFlow(itemsOf(itemPage.getRecords()));

        IPage<Map<String, Object>> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (DevPurchaseItem item : itemPage.getRecords()) {
            rows.add(toMap(item, projectNameMap));
        }
        result.setRecords(rows);
        return result;
    }

    @Override
    public List<DevPurchaseItem> listByProject(Long projectId) {
        List<DevPurchaseItem> items = this.list(new LambdaQueryWrapper<DevPurchaseItem>()
                .eq(projectId != null, DevPurchaseItem::getProjectId, projectId)
                .orderByDesc(DevPurchaseItem::getId));
        // 批量回填当前位置（warehouseName/warehouseAddress 为 transient 字段）
        fillLatestFlow(items);
        return items;
    }

    @Override
    public DevPurchaseItem getDetail(Long id) {
        DevPurchaseItem item = this.getById(id);
        if (item == null) return null;
        fillLatestFlow(List.of(item));
        return item;
    }

    /** 工具方法：返回非空列表，避免判空 */
    private List<DevPurchaseItem> itemsOf(List<DevPurchaseItem> items) {
        return items != null ? items : new ArrayList<>();
    }

    /**
     * 批量回填物料当前位置（取各物料最新一条流转记录，warehouseName=place_name、warehouseAddress=place_detail）
     */
    private void fillLatestFlow(List<DevPurchaseItem> items) {
        if (items == null || items.isEmpty()) return;
        List<Long> materialIds = items.stream().map(DevPurchaseItem::getId).filter(java.util.Objects::nonNull).collect(Collectors.toList());
        if (materialIds.isEmpty()) return;
        // 一次查询该批物料的全部流转记录，按时间倒序；取每物料第一条作为当前位置
        List<DevMaterialFlow> flows = materialFlowMapper.selectList(
                new LambdaQueryWrapper<DevMaterialFlow>()
                        .in(DevMaterialFlow::getMaterialId, materialIds)
                        .orderByDesc(DevMaterialFlow::getFlowTime)
                        .orderByDesc(DevMaterialFlow::getId));
        Map<Long, DevMaterialFlow> latestMap = new HashMap<>();
        for (DevMaterialFlow f : flows) {
            latestMap.putIfAbsent(f.getMaterialId(), f);
        }
        for (DevPurchaseItem item : items) {
            DevMaterialFlow latest = latestMap.get(item.getId());
            if (latest != null) {
                item.setWarehouseName(latest.getPlaceName() != null ? latest.getPlaceName() : "");
                item.setWarehouseAddress(latest.getPlaceDetail() != null ? latest.getPlaceDetail() : "");
            }
        }
    }

    @Override
    public List<Map<String, Object>> warehouseOptions() {
        Long companyId = CompanyContext.get();
        List<Map<String, Object>> options = new ArrayList<>();

        // 自有仓库：当前公司 + 启用 + 仓库类别为自有仓
        List<Warehouse> invList = warehouseMapper.selectList(
                new LambdaQueryWrapper<Warehouse>()
                        .eq(companyId != null, Warehouse::getCompanyId, companyId)
                        .eq(Warehouse::getStatus, 1)
                        .eq(Warehouse::getWarehouseCategory, WarehouseCategory.INVENTORY.getCode())
                        .orderByAsc(Warehouse::getId));
        for (Warehouse w : invList) {
            Map<String, Object> m = new HashMap<>();
            m.put("value", DevMaterialPlaceTypeEnum.INVENTORY.getCode() + ":" + w.getId());
            m.put("placeId", w.getId());
            m.put("placeType", DevMaterialPlaceTypeEnum.INVENTORY.getCode());
            m.put("placeName", w.getWarehouseName());
            m.put("address", w.getAddress());
            m.put("groupLabel", DevMaterialPlaceTypeEnum.INVENTORY.getLabel());
            options.add(m);
        }

        // 委外仓库：当前公司 + 启用 + 仓库类别为委外仓，名称拼接供应商名便于区分
        List<Warehouse> outList = warehouseMapper.selectList(
                new LambdaQueryWrapper<Warehouse>()
                        .eq(companyId != null, Warehouse::getCompanyId, companyId)
                        .eq(Warehouse::getStatus, 1)
                        .eq(Warehouse::getWarehouseCategory, WarehouseCategory.OUTSOURCE.getCode())
                        .orderByAsc(Warehouse::getId));
        for (Warehouse w : outList) {
            Map<String, Object> m = new HashMap<>();
            m.put("value", DevMaterialPlaceTypeEnum.OUTSOURCE.getCode() + ":" + w.getId());
            m.put("placeId", w.getId());
            m.put("placeType", DevMaterialPlaceTypeEnum.OUTSOURCE.getCode());
            m.put("placeName", w.getWarehouseName());
            m.put("address", w.getAddress());
            m.put("groupLabel", DevMaterialPlaceTypeEnum.OUTSOURCE.getLabel());
            options.add(m);
        }
        return options;
    }

    /**
     * 将实体转为前端分页所需的 Map（HashMap 无 setter，必须显式逐字段 put，不能用 BeanUtils.copyProperties）
     */
    private Map<String, Object> toMap(DevPurchaseItem item, Map<Long, String> projectNameMap) {
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
        // 当前位置由最新流转记录回填（transient 字段）
        map.put("warehouseName", item.getWarehouseName());
        map.put("warehouseAddress", item.getWarehouseAddress());
        Long pid = item.getProjectId();
        map.put("projectName", pid != null ? projectNameMap.getOrDefault(pid, PROJECT_NAME_UNLINKED) : PROJECT_NAME_UNLINKED);
        return map;
    }
}
