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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        if (products == null || products.isEmpty()) {
            // 入参为空仍保留历史关联（差量更新语义：不清空）
            return;
        }
        // 差量更新：按 productId 比对，新增的 insert、已存在的 update、多余的 delete
        List<SupplierProduct> existing = supplierProductMapper.selectList(
                new LambdaQueryWrapper<SupplierProduct>().eq(SupplierProduct::getSupplierId, supplierId));
        Map<Long, SupplierProduct> existMap = existing.stream()
                .collect(Collectors.toMap(SupplierProduct::getProductId, p -> p, (a, b) -> a));

        List<SupplierProduct> toInsert = new ArrayList<>();
        List<SupplierProduct> toUpdate = new ArrayList<>();
        Set<Long> seen = new java.util.HashSet<>();
        for (SupplierProductDTO dto : products) {
            if (dto.getProductId() == null) continue;
            seen.add(dto.getProductId());
            SupplierProduct exist = existMap.get(dto.getProductId());
            if (exist != null) {
                // 已存在：更新单价/备注，保留原 id 与 create_time
                exist.setUnitPrice(dto.getUnitPrice());
                exist.setRemark(dto.getRemark());
                toUpdate.add(exist);
            } else {
                SupplierProduct sp = new SupplierProduct();
                sp.setSupplierId(supplierId);
                sp.setProductId(dto.getProductId());
                sp.setUnitPrice(dto.getUnitPrice());
                sp.setRemark(dto.getRemark());
                toInsert.add(sp);
            }
        }
        // 多余的删除（历史中存在但本次未传的 productId）
        List<Long> toDelete = existing.stream()
                .filter(p -> !seen.contains(p.getProductId()))
                .map(SupplierProduct::getId)
                .collect(Collectors.toList());

        if (!toInsert.isEmpty()) this.saveBatch(toInsert);
        if (!toUpdate.isEmpty()) this.updateBatchById(toUpdate);
        if (!toDelete.isEmpty()) this.removeByIds(toDelete);
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
