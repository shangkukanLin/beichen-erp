package com.beichen.erp.dev.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.customer.entity.Customer;
import com.beichen.erp.customer.mapper.CustomerMapper;
import com.beichen.erp.dev.common.DevMaterialPlaceTypeEnum;
import com.beichen.erp.dev.entity.DevMaterialFlow;
import com.beichen.erp.dev.mapper.DevMaterialFlowMapper;
import com.beichen.erp.dev.mapper.DevPurchaseItemMapper;
import com.beichen.erp.dev.service.DevMaterialFlowService;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.supplier.entity.Supplier;
import com.beichen.erp.supplier.mapper.SupplierMapper;
import com.beichen.erp.warehouse.entity.Warehouse;
import com.beichen.erp.warehouse.mapper.WarehouseMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 研发物料位置流转记录业务实现
 */
@Service
public class DevMaterialFlowServiceImpl extends ServiceImpl<DevMaterialFlowMapper, DevMaterialFlow> implements DevMaterialFlowService {

    private final WarehouseMapper warehouseMapper;
    private final SupplierMapper supplierMapper;
    private final CustomerMapper customerMapper;
    /** F7-103：校验 materialId 指向的研发物料是否存在（dev_material_flow.material_id = dev_purchase_item.id） */
    private final DevPurchaseItemMapper purchaseItemMapper;

    @Autowired
    public DevMaterialFlowServiceImpl(WarehouseMapper warehouseMapper,
                                      SupplierMapper supplierMapper,
                                      CustomerMapper customerMapper,
                                      DevPurchaseItemMapper purchaseItemMapper) {
        this.warehouseMapper = warehouseMapper;
        this.supplierMapper = supplierMapper;
        this.customerMapper = customerMapper;
        this.purchaseItemMapper = purchaseItemMapper;
    }

    @Override
    public List<DevMaterialFlow> listByMaterial(Long materialId) {
        return this.list(new LambdaQueryWrapper<DevMaterialFlow>()
                .eq(materialId != null, DevMaterialFlow::getMaterialId, materialId)
                .orderByDesc(DevMaterialFlow::getFlowTime)
                .orderByDesc(DevMaterialFlow::getId));
    }

    @Override
    public DevMaterialFlow add(DevMaterialFlow flow) {
        if (flow.getMaterialId() == null) {
            throw new BusinessException("物料ID不能为空");
        }
        // F7-103：物料必须存在（原先可给不存在/已删除的物料挂流转记录，之后列表里出现"幽灵当前位置"）
        if (purchaseItemMapper.selectById(flow.getMaterialId()) == null) {
            throw new BusinessException("研发物料不存在：" + flow.getMaterialId());
        }
        if (flow.getPlaceType() == null || flow.getPlaceType().isBlank()) {
            throw new BusinessException("位置类型不能为空");
        }
        if (flow.getFlowTime() == null) {
            flow.setFlowTime(java.time.LocalDateTime.now());
        }
        // 关联类型按 placeId 查名写快照；自定义文本直接取 placeDetail
        resolvePlaceName(flow);
        // F7-103：仅在"有租户上下文"时写 companyId（超管模式 0/null 下 strictInsertFill 也不兜底，
        // 显式写 null 会落成谁都查不到的脏数据）
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) flow.setCompanyId(cid);
        this.save(flow);
        return flow;
    }

    @Override
    public DevMaterialFlow update(DevMaterialFlow flow) {
        if (flow.getId() == null) {
            throw new BusinessException("流转记录ID不能为空");
        }
        // F7-103：记录必须存在（原先 updateById 对不存在的 id 静默"成功"，前端以为保存了）
        if (this.getById(flow.getId()) == null) {
            throw new BusinessException("流转记录不存在");
        }
        if (flow.getPlaceType() == null || flow.getPlaceType().isBlank()) {
            throw new BusinessException("位置类型不能为空");
        }
        resolvePlaceName(flow);
        this.updateById(flow);
        return flow;
    }

    @Override
    public void delete(Long id) {
        // F7-103：删除同样校验存在性，避免"删除成功但什么都没发生"的静默语义
        if (this.getById(id) == null) {
            throw new BusinessException("流转记录不存在");
        }
        this.removeById(id);
    }

    /**
     * 根据位置类型解析位置名称快照：
     * 关联类型（仓库/供应商/客户）按 placeId 查名；自定义文本直接取 placeDetail
     */
    private void resolvePlaceName(DevMaterialFlow flow) {
        // F7-103：placeType 必须先命中枚举（原先未知值会走完 if/else if 后静默落 placeName = ""，
        // 既不报错也不记日志，前端按 toLabel 映射时只能显示英文 code）
        DevMaterialPlaceTypeEnum type = DevMaterialPlaceTypeEnum.fromCode(flow.getPlaceType());
        if (type == null) {
            throw new BusinessException("位置类型非法：" + flow.getPlaceType());
        }
        if (type == DevMaterialPlaceTypeEnum.TEXT) {
            flow.setPlaceName(flow.getPlaceDetail());
            flow.setPlaceId(null);
            return;
        }
        if (flow.getPlaceId() == null) {
            throw new BusinessException("请选择位置");
        }
        String name = null;
        if (type == DevMaterialPlaceTypeEnum.INVENTORY || type == DevMaterialPlaceTypeEnum.OUTSOURCE) {
            Warehouse w = warehouseMapper.selectById(flow.getPlaceId());
            name = w != null ? w.getWarehouseName() : null;
        } else if (type == DevMaterialPlaceTypeEnum.SUPPLIER) {
            Supplier s = supplierMapper.selectById(flow.getPlaceId());
            name = s != null ? s.getName() : null;
        } else if (type == DevMaterialPlaceTypeEnum.CUSTOMER) {
            Customer c = customerMapper.selectById(flow.getPlaceId());
            name = c != null ? c.getName() : null;
        }
        flow.setPlaceName(name != null ? name : "");
    }
}
