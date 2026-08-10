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
        Long companyId = CompanyContext.get();
        if (companyId != null && companyId <= 0) companyId = null;

        // 原子库存加减：先尝试 UPDATE（带库存不足校验），行不存在或库存不足时 affectedRows=0
        int rows = mapper.deltaUpdate(warehouseId, productId, qualityType, companyId, quantity);
        if (rows == 0) {
            // 区分「库存行不存在」与「库存不足」
            InventoryWarehouseStock exist = this.selectExist(warehouseId, productId, qualityType, companyId);
            if (exist != null) {
                // 行存在但扣减后为负 -> 库存不足
                throw new BusinessException("库存不足，无法出库：产品ID=" + productId);
            }
            // 行不存在：仅入库（delta>0）才插入首条；出库无库存直接报错
            if (quantity.compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException("库存不足，无法出库：产品ID=" + productId);
            }
            try {
                mapper.insertStock(warehouseId, productId, qualityType, companyId, quantity);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                // 并发下另一线程刚插入，重试一次原子加减
                int retry = mapper.deltaUpdate(warehouseId, productId, qualityType, companyId, quantity);
                if (retry == 0) throw new BusinessException("库存不足，无法出库：产品ID=" + productId);
            }
        }

        // 写库存流水（before/after 通过重新查询获得，保证与原子操作一致）
        InventoryWarehouseStock latest = this.selectExist(warehouseId, productId, qualityType, companyId);
        BigDecimal after = latest != null && latest.getQuantity() != null ? latest.getQuantity() : quantity;
        BigDecimal before = after.subtract(quantity);

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
        if (companyId != null) log.setCompanyId(companyId);
        logMapper.insert(log);
    }

    /** 按唯一维度查询库存行（供原子操作前后读取，避免重复代码） */
    private InventoryWarehouseStock selectExist(Long warehouseId, Long productId, String qualityType, Long companyId) {
        LambdaQueryWrapper<InventoryWarehouseStock> w = new LambdaQueryWrapper<InventoryWarehouseStock>()
                .eq(InventoryWarehouseStock::getWarehouseId, warehouseId)
                .eq(InventoryWarehouseStock::getProductId, productId)
                .eq(InventoryWarehouseStock::getQualityType, qualityType)
                .eq(companyId != null, InventoryWarehouseStock::getCompanyId, companyId);
        return mapper.selectOne(w);
    }
}
