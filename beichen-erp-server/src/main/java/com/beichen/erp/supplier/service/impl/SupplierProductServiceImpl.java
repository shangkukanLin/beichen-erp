package com.beichen.erp.supplier.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.beichen.erp.supplier.entity.SupplierProduct;
import com.beichen.erp.supplier.entity.dto.SupplierProductDTO;
import com.beichen.erp.supplier.mapper.SupplierProductMapper;
import com.beichen.erp.supplier.service.SupplierProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SupplierProductServiceImpl extends ServiceImpl<SupplierProductMapper, SupplierProduct> implements SupplierProductService {

    private final SupplierProductMapper supplierProductMapper;

    @Override
    public List<SupplierProduct> listBySupplierId(Long supplierId) {
        LambdaQueryWrapper<SupplierProduct> wrapper = new LambdaQueryWrapper<SupplierProduct>()
                .eq(SupplierProduct::getSupplierId, supplierId)
                .orderByAsc(SupplierProduct::getId);
        return supplierProductMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveProducts(Long supplierId, List<SupplierProductDTO> products) {
        supplierProductMapper.delete(new LambdaQueryWrapper<SupplierProduct>()
                .eq(SupplierProduct::getSupplierId, supplierId));

        if (products == null || products.isEmpty()) {
            return;
        }

        List<SupplierProduct> list = new ArrayList<>();
        for (SupplierProductDTO dto : products) {
            SupplierProduct sp = new SupplierProduct();
            sp.setSupplierId(supplierId);
            sp.setProductName(dto.getProductName());
            sp.setSpec(dto.getSpec());
            sp.setUnit(dto.getUnit());
            sp.setUnitPrice(dto.getUnitPrice());
            sp.setRemark(dto.getRemark());
            list.add(sp);
        }
        this.saveBatch(list);
    }
}
