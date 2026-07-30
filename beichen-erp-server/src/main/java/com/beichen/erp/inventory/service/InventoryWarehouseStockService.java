package com.beichen.erp.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.inventory.common.RelatedBillType;
import com.beichen.erp.inventory.common.StockChangeType;
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

    /** 入库：增加库存，不存在则新建 */
    @Transactional
    public void stockIn(Long warehouseId, String productName, BigDecimal quantity) {
        changeStock(warehouseId, productName, quantity, StockChangeType.PURCHASE_IN,
                null, (RelatedBillType) null, null, null, null, "A");
    }

    /**
     * 通用库存变更：按 (warehouseId, productId, qualityType) 定位唯一库存行。
     * qualityType 默认"A规"，传null时兜底为"A"。
     */
    @Transactional
    public void changeStock(Long warehouseId, String productName, BigDecimal quantity,
                            StockChangeType type, String relatedBillNo, RelatedBillType relatedBillType,
                            Long productId, String spec, Long relatedBillId, String qualityType) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) == 0) return;
        if (qualityType == null) qualityType = "A";

        LambdaQueryWrapper<InventoryWarehouseStock> w = new LambdaQueryWrapper<InventoryWarehouseStock>()
                .eq(InventoryWarehouseStock::getWarehouseId, warehouseId);
        if (productId != null) {
            w.eq(InventoryWarehouseStock::getProductId, productId);
        } else {
            w.eq(InventoryWarehouseStock::getProductName, productName);
        }
        w.eq(InventoryWarehouseStock::getQualityType, qualityType);
        InventoryWarehouseStock stock = mapper.selectOne(w);

        BigDecimal before = stock != null && stock.getQuantity() != null ? stock.getQuantity() : BigDecimal.ZERO;
        BigDecimal after = before.add(quantity);
        if (after.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("库存不足，无法出库："
                    + (productId != null ? ("产品ID=" + productId) : productName));
        }
        if (stock != null) {
            stock.setQuantity(after);
            stock.setAvailableQuantity(after);
            if (productName != null) stock.setProductName(productName);
            if (productId != null) stock.setProductId(productId);
            stock.setQualityType(qualityType);
            mapper.updateById(stock);
        } else {
            stock = new InventoryWarehouseStock();
            stock.setWarehouseId(warehouseId);
            stock.setProductName(productName);
            stock.setProductId(productId);
            stock.setQualityType(qualityType);
            stock.setQuantity(after);
            stock.setAvailableQuantity(after);
            Long cid = CompanyContext.get();
            if (cid != null && cid > 0) stock.setCompanyId(cid);
            mapper.insert(stock);
        }
        // 写库存流水
        InventoryStockLog log = new InventoryStockLog();
        log.setWarehouseId(warehouseId);
        log.setProductId(productId);
        log.setQualityType(qualityType);
        log.setChangeType(type.getCode());
        log.setChangeQuantity(quantity);
        log.setBeforeQuantity(before);
        log.setAfterQuantity(after);
        log.setRelatedBillNo(relatedBillNo);
        log.setRelatedBillType(relatedBillType != null ? relatedBillType.getCode() : null);
        log.setRelatedBillId(relatedBillId);
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) log.setCompanyId(cid);
        logMapper.insert(log);
    }
}
