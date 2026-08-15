package com.beichen.erp.dev.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.customer.entity.Customer;
import com.beichen.erp.customer.mapper.CustomerMapper;
import com.beichen.erp.dev.common.DevMaterialPlaceTypeEnum;
import com.beichen.erp.dev.entity.DevMaterialFlow;
import com.beichen.erp.dev.mapper.DevMaterialFlowMapper;
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

    @Autowired
    public DevMaterialFlowServiceImpl(WarehouseMapper warehouseMapper,
                                      SupplierMapper supplierMapper,
                                      CustomerMapper customerMapper) {
        this.warehouseMapper = warehouseMapper;
        this.supplierMapper = supplierMapper;
        this.customerMapper = customerMapper;
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
        if (flow.getPlaceType() == null || flow.getPlaceType().isBlank()) {
            throw new BusinessException("位置类型不能为空");
        }
        if (flow.getFlowTime() == null) {
            flow.setFlowTime(java.time.LocalDateTime.now());
        }
        // 关联类型按 placeId 查名写快照；自定义文本直接取 placeDetail
        resolvePlaceName(flow);
        flow.setCompanyId(CompanyContext.get());
        this.save(flow);
        return flow;
    }

    @Override
    public DevMaterialFlow update(DevMaterialFlow flow) {
        if (flow.getId() == null) {
            throw new BusinessException("流转记录ID不能为空");
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
        this.removeById(id);
    }

    /**
     * 根据位置类型解析位置名称快照：
     * 关联类型（仓库/供应商/客户）按 placeId 查名；自定义文本直接取 placeDetail
     */
    private void resolvePlaceName(DevMaterialFlow flow) {
        String type = flow.getPlaceType();
        if (DevMaterialPlaceTypeEnum.TEXT.getCode().equals(type)) {
            flow.setPlaceName(flow.getPlaceDetail());
            flow.setPlaceId(null);
            return;
        }
        if (flow.getPlaceId() == null) {
            throw new BusinessException("请选择位置");
        }
        String name = null;
        if (DevMaterialPlaceTypeEnum.INVENTORY.getCode().equals(type)
                || DevMaterialPlaceTypeEnum.OUTSOURCE.getCode().equals(type)) {
            Warehouse w = warehouseMapper.selectById(flow.getPlaceId());
            name = w != null ? w.getWarehouseName() : null;
        } else if (DevMaterialPlaceTypeEnum.SUPPLIER.getCode().equals(type)) {
            Supplier s = supplierMapper.selectById(flow.getPlaceId());
            name = s != null ? s.getName() : null;
        } else if (DevMaterialPlaceTypeEnum.CUSTOMER.getCode().equals(type)) {
            Customer c = customerMapper.selectById(flow.getPlaceId());
            name = c != null ? c.getName() : null;
        }
        flow.setPlaceName(name != null ? name : "");
    }
}
