package com.beichen.erp.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.inventory.common.RelatedBillType;
import com.beichen.erp.inventory.common.StockChangeType;
import com.beichen.erp.material.common.ProductQualityType;
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
    public void stockIn(Long warehouseId, Long productId, BigDecimal quantity) {
        changeStock(warehouseId, productId, quantity, StockChangeType.PURCHASE_IN,
                null, (RelatedBillType) null, null, null, ProductQualityType.A.name());
    }

    /** 旧签名兼容（传productName+productId） */
    @Transactional
    public void changeStock(Long warehouseId, String productName, BigDecimal quantity,
                            StockChangeType type, String relatedBillNo, RelatedBillType relatedBillType,
                            Long productId, String spec, Long relatedBillId, String qualityType) {
        changeStock(warehouseId, productId, quantity, type, relatedBillNo, relatedBillType, spec, relatedBillId, qualityType);
    }

    /**
     * 通用库存变更：按 (warehouseId, productId, qualityType) 定位唯一库存行。
     * qualityType 为成品等级（见 ProductQualityType：A/B/C/DEFECT），传null时兜底为A规。
     */
    @Transactional
    public void changeStock(Long warehouseId, Long productId, BigDecimal quantity,
                            StockChangeType type, String relatedBillNo, RelatedBillType relatedBillType,
                            String spec, Long relatedBillId, String qualityType) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) == 0) return;
        if (qualityType == null) qualityType = ProductQualityType.A.name();

        LambdaQueryWrapper<InventoryWarehouseStock> w = new LambdaQueryWrapper<InventoryWarehouseStock>()
                .eq(InventoryWarehouseStock::getWarehouseId, warehouseId)
                .eq(InventoryWarehouseStock::getProductId, productId)
                .eq(InventoryWarehouseStock::getQualityType, qualityType);
        InventoryWarehouseStock stock = mapper.selectOne(w);

        BigDecimal before = stock != null && stock.getQuantity() != null ? stock.getQuantity() : BigDecimal.ZERO;
        BigDecimal after = before.add(quantity);
        if (after.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("库存不足，无法出库：产品ID=" + productId);
        }
        if (stock != null) {
            stock.setQuantity(after);
            stock.setAvailableQuantity(after);
            stock.setQualityType(qualityType);
            mapper.updateById(stock);
        } else {
            stock = new InventoryWarehouseStock();
            stock.setWarehouseId(warehouseId);
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
