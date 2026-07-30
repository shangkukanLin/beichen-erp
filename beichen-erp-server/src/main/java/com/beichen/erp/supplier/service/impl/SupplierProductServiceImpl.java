package com.beichen.erp.supplier.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.beichen.erp.material.entity.Product;
import com.beichen.erp.material.mapper.ProductMapper;
import com.beichen.erp.supplier.entity.SupplierProduct;
import com.beichen.erp.supplier.entity.dto.SupplierProductDTO;
import com.beichen.erp.supplier.mapper.SupplierProductMapper;
import com.beichen.erp.supplier.service.SupplierProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SupplierProductServiceImpl extends ServiceImpl<SupplierProductMapper, SupplierProduct> implements SupplierProductService {

    private final SupplierProductMapper supplierProductMapper;
    private final ProductMapper productMapper;

    @Override
    public List<SupplierProduct> listBySupplierId(Long supplierId) {
        LambdaQueryWrapper<SupplierProduct> wrapper = new LambdaQueryWrapper<SupplierProduct>()
                .eq(SupplierProduct::getSupplierId, supplierId)
                .orderByAsc(SupplierProduct::getId);
        List<SupplierProduct> list = supplierProductMapper.selectList(wrapper);
        // 联查 product 表填充名称、规格、单位
        fillProductInfo(list);
        return list;
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
            sp.setProductId(dto.getProductId());
            sp.setUnitPrice(dto.getUnitPrice());
            sp.setRemark(dto.getRemark());
            list.add(sp);
        }
        this.saveBatch(list);
    }

    /** 通过 productId 批量填充产品名称/规格/单位 */
    private void fillProductInfo(List<SupplierProduct> list) {
        List<Long> productIds = list.stream().map(SupplierProduct::getProductId)
                .filter(id -> id != null).distinct().collect(Collectors.toList());
        if (productIds.isEmpty()) return;
        List<Product> products = productMapper.selectBatchIds(productIds);
        Map<Long, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, p -> p));
        for (SupplierProduct sp : list) {
            if (sp.getProductId() != null) {
                Product p = productMap.get(sp.getProductId());
                if (p != null) {
                    sp.setProductName(p.getName());
                    sp.setSpec(p.getSpec());
                    sp.setUnit(p.getUnit());
                }
            }
        }
    }
}
