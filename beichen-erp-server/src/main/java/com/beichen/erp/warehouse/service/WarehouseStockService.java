package com.beichen.erp.warehouse.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.inventory.common.RelatedBillType;
import com.beichen.erp.inventory.common.StockChangeType;
import com.beichen.erp.material.common.ProductQualityType;
import com.beichen.erp.warehouse.entity.WarehouseStock;
import com.beichen.erp.warehouse.entity.WarehouseStockLog;
import com.beichen.erp.warehouse.mapper.WarehouseStockLogMapper;
import com.beichen.erp.warehouse.mapper.WarehouseStockMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 统一库存变更 Service（合并 inventory_warehouse_stock + outsource_warehouse_stock）
 */
@Service
@RequiredArgsConstructor
public class WarehouseStockService {

    private final WarehouseStockMapper warehouseStockMapper;
    private final WarehouseStockLogMapper warehouseStockLogMapper;

    /** 入库：增加库存，不存在则新建 */
    @Transactional
    public void stockIn(Long warehouseId, Long productId, BigDecimal quantity) {
        changeStock(warehouseId, productId, quantity, StockChangeType.PURCHASE_IN,
                null, (RelatedBillType) null, null, null, ProductQualityType.A.name());
    }

    /** 旧签名兼容 */
    @Transactional
    public void changeStock(Long warehouseId, String productName, BigDecimal quantity,
                            StockChangeType type, String relatedBillNo, RelatedBillType relatedBillType,
                            Long productId, String spec, Long relatedBillId, String qualityType) {
        changeStock(warehouseId, productId, quantity, type, relatedBillNo, relatedBillType, spec, relatedBillId, qualityType);
    }

    /**
     * 通用库存变更：按 (warehouseId, productId, qualityType) 定位唯一库存行。
     */
    @Transactional
    public void changeStock(Long warehouseId, Long productId, BigDecimal quantity,
                            StockChangeType type, String relatedBillNo, RelatedBillType relatedBillType,
                            String spec, Long relatedBillId, String qualityType) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) == 0) return;
        if (qualityType == null) qualityType = ProductQualityType.A.name();
        Long companyId = CompanyContext.get();
        if (companyId != null && companyId <= 0) companyId = null;

        int rows = warehouseStockMapper.updateQuantity(warehouseId, productId, qualityType, companyId, quantity);
        if (rows == 0) {
            WarehouseStock exist = selectExist(warehouseId, productId, qualityType, companyId);
            if (exist != null) {
                throw new BusinessException("库存不足，无法出库：产品ID=" + productId);
            }
            if (quantity.compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException("库存不足，无法出库：产品ID=" + productId);
            }
            try {
                insertStock(warehouseId, productId, qualityType, companyId, quantity);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                int retry = warehouseStockMapper.updateQuantity(warehouseId, productId, qualityType, companyId, quantity);
                if (retry == 0) throw new BusinessException("库存不足，无法出库：产品ID=" + productId);
            }
        }

        // 写库存流水
        WarehouseStock latest = selectExist(warehouseId, productId, qualityType, companyId);
        BigDecimal after = latest != null && latest.getQuantity() != null ? latest.getQuantity() : quantity;
        BigDecimal before = after.subtract(quantity);

        WarehouseStockLog log = new WarehouseStockLog();
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
        warehouseStockLogMapper.insert(log);
    }

    /**
     * 物料库存变更（委外仓物料）
     */
    @Transactional
    public void changeMaterialStock(Long warehouseId, Long materialId, BigDecimal quantity,
                                     String changeType, String relatedCode, RelatedBillType relatedBillType,
                                     Long relatedDeliveryId, Long relatedOrderId) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) == 0) return;
        Long companyId = CompanyContext.get();

        // 物料库存用 material_id，qualityType 统一为 GOOD
        String qt = "GOOD";
        int rows = warehouseStockMapper.updateMaterialQuantity(warehouseId, materialId, companyId, quantity);
        if (rows == 0) {
            WarehouseStock exist = selectMaterialExist(warehouseId, materialId, companyId);
            if (exist != null) {
                throw new BusinessException("物料库存不足");
            }
            if (quantity.compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException("物料库存不足");
            }
            try {
                insertMaterialStock(warehouseId, materialId, companyId, quantity);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                int retry = warehouseStockMapper.updateMaterialQuantity(warehouseId, materialId, companyId, quantity);
                if (retry == 0) throw new BusinessException("物料库存不足");
            }
        }

        WarehouseStock latest = selectMaterialExist(warehouseId, materialId, companyId);
        BigDecimal after = latest != null && latest.getQuantity() != null ? latest.getQuantity() : quantity;
        BigDecimal before = after.subtract(quantity);

        WarehouseStockLog log = new WarehouseStockLog();
        log.setWarehouseId(warehouseId);
        log.setMaterialId(materialId);
        log.setQualityType(qt);
        log.setChangeType(changeType);
        log.setChangeQuantity(quantity);
        log.setBeforeQuantity(before);
        log.setAfterQuantity(after);
        log.setRelatedOrderCode(relatedCode);
        log.setRelatedBillType(relatedBillType != null ? relatedBillType.getCode() : null);
        log.setRelatedDeliveryId(relatedDeliveryId);
        if (companyId != null) log.setCompanyId(companyId);
        warehouseStockLogMapper.insert(log);
    }

    private WarehouseStock selectExist(Long warehouseId, Long productId, String qualityType, Long companyId) {
        return warehouseStockMapper.selectOne(new LambdaQueryWrapper<WarehouseStock>()
                .eq(WarehouseStock::getWarehouseId, warehouseId)
                .eq(WarehouseStock::getProductId, productId)
                .eq(WarehouseStock::getQualityType, qualityType)
                .eq(companyId != null, WarehouseStock::getCompanyId, companyId));
    }

    private WarehouseStock selectMaterialExist(Long warehouseId, Long materialId, Long companyId) {
        return warehouseStockMapper.selectOne(new LambdaQueryWrapper<WarehouseStock>()
                .eq(WarehouseStock::getWarehouseId, warehouseId)
                .eq(WarehouseStock::getMaterialId, materialId)
                .eq(companyId != null, WarehouseStock::getCompanyId, companyId));
    }

    private void insertStock(Long warehouseId, Long productId, String qualityType, Long companyId, BigDecimal quantity) {
        WarehouseStock s = new WarehouseStock();
        s.setWarehouseId(warehouseId);
        s.setProductId(productId);
        s.setQualityType(qualityType);
        s.setQuantity(quantity);
        s.setAvailableQuantity(quantity);
        if (companyId != null) s.setCompanyId(companyId);
        warehouseStockMapper.insert(s);
    }

    private void insertMaterialStock(Long warehouseId, Long materialId, Long companyId, BigDecimal quantity) {
        WarehouseStock s = new WarehouseStock();
        s.setWarehouseId(warehouseId);
        s.setMaterialId(materialId);
        s.setQualityType("GOOD");
        s.setQuantity(quantity);
        if (companyId != null) s.setCompanyId(companyId);
        warehouseStockMapper.insert(s);
    }
}
