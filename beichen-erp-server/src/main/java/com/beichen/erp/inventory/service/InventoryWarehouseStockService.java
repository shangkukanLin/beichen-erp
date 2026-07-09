package com.beichen.erp.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.inventory.entity.InventoryStockLog;
import com.beichen.erp.inventory.entity.InventoryWarehouseStock;
import com.beichen.erp.inventory.mapper.InventoryStockLogMapper;
import com.beichen.erp.inventory.mapper.InventoryWarehouseStockMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class InventoryWarehouseStockService {

    private final InventoryWarehouseStockMapper mapper;
    private final InventoryStockLogMapper logMapper;

    /** 入库：增加库存，不存在则新建（委外模块复用） */
    @Transactional
    public void stockIn(Long warehouseId, String productName, BigDecimal quantity) {
        changeStock(warehouseId, productName, quantity, "采购入库", null, null, null, null);
    }

    /**
     * 通用库存变更：正数入库、负数出库；自动维护库存余额并写流水。
     * 出库（quantity<0）时校验变动后余量不为负。
     */
    @Transactional
    public void changeStock(Long warehouseId, String productName, BigDecimal quantity,
                            String changeType, String relatedBillNo, String relatedBillType,
                            Long materialId, String spec) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) == 0) return;
        InventoryWarehouseStock stock = mapper.selectOne(new LambdaQueryWrapper<InventoryWarehouseStock>()
                .eq(InventoryWarehouseStock::getWarehouseId, warehouseId)
                .eq(InventoryWarehouseStock::getProductName, productName));
        BigDecimal before = stock != null && stock.getQuantity() != null ? stock.getQuantity() : BigDecimal.ZERO;
        BigDecimal after = before.add(quantity);
        if (after.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("库存不足，无法出库：" + productName);
        }
        if (stock != null) {
            stock.setQuantity(after);
            mapper.updateById(stock);
        } else {
            stock = new InventoryWarehouseStock();
            stock.setWarehouseId(warehouseId);
            stock.setProductName(productName);
            stock.setQuantity(after);
            Long cid = CompanyContext.get();
            if (cid != null && cid > 0) stock.setCompanyId(cid);
            mapper.insert(stock);
        }
        // 写库存流水（可追溯）
        InventoryStockLog log = new InventoryStockLog();
        log.setWarehouseId(warehouseId);
        log.setMaterialId(materialId);
        log.setMaterialName(productName);
        log.setSpec(spec);
        log.setChangeType(changeType);
        log.setChangeQuantity(quantity);
        log.setBeforeQuantity(before);
        log.setAfterQuantity(after);
        log.setRelatedBillNo(relatedBillNo);
        log.setRelatedBillType(relatedBillType);
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) log.setCompanyId(cid);
        logMapper.insert(log);
    }
}
