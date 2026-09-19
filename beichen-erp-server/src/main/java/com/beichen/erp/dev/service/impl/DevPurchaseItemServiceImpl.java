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
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.warehouse.common.WarehouseCategory;
import com.beichen.erp.warehouse.entity.Warehouse;
import com.beichen.erp.warehouse.mapper.WarehouseMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 研发物料管理业务实现
 */
@Slf4j
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

    @Override
    @Transactional
    public DevPurchaseItem addItem(DevPurchaseItem item) {
        validateItem(item);
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) item.setCompanyId(cid);
        this.save(item);
        return item;
    }

    @Override
    @Transactional
    public void updateItem(DevPurchaseItem item) {
        if (item.getId() == null) throw new BusinessException("物料ID不能为空");
        if (this.getById(item.getId()) == null) throw new BusinessException("研发物料不存在");
        validateItem(item);
        // F7-101：白名单字段更新 —— 只写"允许编辑"的字段（companyId 一律不落库；借 MyBatis-Plus
        // 默认的 NOT_NULL 策略，"前端未提交" == "保持原值"）
        DevPurchaseItem patch = new DevPurchaseItem();
        patch.setId(item.getId());
        patch.setProjectId(item.getProjectId());
        patch.setName(item.getName());
        patch.setType(item.getType());
        patch.setQuantity(item.getQuantity());
        patch.setLocationDetail(item.getLocationDetail());
        patch.setPurchaseDate(item.getPurchaseDate());
        patch.setAmount(item.getAmount());
        patch.setStatus(item.getStatus());
        patch.setRemark(item.getRemark());
        this.updateById(patch);
    }

    @Override
    @Transactional
    public void deleteItem(Long id) {
        if (this.getById(id) == null) throw new BusinessException("研发物料不存在");
        // F7-101：级联清理位置流转记录 —— 原先只删物料，dev_material_flow 的行会成为孤儿，
        // 而 fillLatestFlow / DevMaterialFlowServiceImpl.listByMaterial 仍会按 material_id 查到它们
        // （表现为"幽灵当前位置"）
        int flows = materialFlowMapper.delete(new LambdaQueryWrapper<DevMaterialFlow>()
                .eq(DevMaterialFlow::getMaterialId, id));
        this.removeById(id);
        if (flows > 0) log.info("删除研发物料时级联清理流转记录 {} 条: materialId={}", flows, id);
    }

    /** 必填与取值范围校验（F7-101）：名称必填，数量/金额不允许负数 */
    private void validateItem(DevPurchaseItem item) {
        if (item.getName() == null || item.getName().isBlank()) {
            throw new BusinessException("物料名称不能为空");
        }
        if (item.getQuantity() != null && item.getQuantity() < 0) {
            throw new BusinessException("数量不能为负数");
        }
        if (item.getAmount() != null && item.getAmount().compareTo(java.math.BigDecimal.ZERO) < 0) {
            throw new BusinessException("金额不能为负数");
        }
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
            // 2026-09-14：不再回 groupLabel（中文分组名由前端按 placeType 映射，见 MaterialPlaceTypeLabel）
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
            // 2026-09-14：同上，不再回 groupLabel
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
