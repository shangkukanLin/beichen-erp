package com.beichen.erp.warehouse.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.inventory.common.RelatedBillType;
import com.beichen.erp.inventory.common.StockChangeType;
import com.beichen.erp.dev.entity.BomType;
import com.beichen.erp.dev.mapper.BomTypeMapper;
import com.beichen.erp.material.entity.Product;
import com.beichen.erp.material.mapper.ProductMapper;
import com.beichen.erp.outsource.entity.OutsourceMaterial;
import com.beichen.erp.outsource.mapper.OutsourceMaterialMapper;
import com.beichen.erp.outsource.common.QualityType;
import com.beichen.erp.warehouse.entity.Warehouse;
import com.beichen.erp.warehouse.entity.WarehouseStock;
import com.beichen.erp.warehouse.entity.WarehouseStockLog;
import com.beichen.erp.warehouse.mapper.WarehouseMapper;
import com.beichen.erp.warehouse.mapper.WarehouseStockLogMapper;
import com.beichen.erp.warehouse.mapper.WarehouseStockMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

/**
 * 统一库存 Controller（替代 InventoryStockController）
 * <p>路由前缀: /api/warehouse/stock</p>
 */
@RestController
@RequestMapping("/api/warehouse/stock")
@RequiredArgsConstructor
public class WarehouseStockController {

    private final WarehouseStockMapper stockMapper;
    private final WarehouseStockLogMapper stockLogMapper;
    private final ProductMapper productMapper;
    private final WarehouseMapper warehouseMapper;
    private final OutsourceMaterialMapper outsourceMaterialMapper;
    private final BomTypeMapper bomTypeMapper;

    /** 库存分页查询（stockType: PRODUCT=成品库存 / MATERIAL=物料库存，不传则全量） */
    @GetMapping("/page")
    public R<Page<WarehouseStock>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String qualityType,
            @RequestParam(required = false) String stockType) {
        return R.ok(stockMapper.selectPage(new Page<>(pageNum, pageSize),
            new LambdaQueryWrapper<WarehouseStock>()
                .eq(warehouseId != null, WarehouseStock::getWarehouseId, warehouseId)
                .eq(productId != null, WarehouseStock::getProductId, productId)
                .eq(qualityType != null && !qualityType.isBlank(), WarehouseStock::getQualityType, qualityType)
                .isNotNull("PRODUCT".equals(stockType), WarehouseStock::getProductId)
                .isNotNull("MATERIAL".equals(stockType), WarehouseStock::getMaterialId)
                .orderByDesc(WarehouseStock::getId)));
    }

    /** 库存流水追溯（stockType: PRODUCT=成品流水 / MATERIAL=物料流水，不传则全量） */
    @GetMapping("/log")
    public R<Page<WarehouseStockLog>> log(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String changeType,
            @RequestParam(required = false) String relatedBillNo,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String stockType) {
        boolean singleProduct = warehouseId != null && productId != null;

        LambdaQueryWrapper<WarehouseStockLog> baseWrapper = new LambdaQueryWrapper<WarehouseStockLog>()
                .eq(warehouseId != null, WarehouseStockLog::getWarehouseId, warehouseId)
                .eq(productId != null, WarehouseStockLog::getProductId, productId)
                .eq(changeType != null && !changeType.isBlank(), WarehouseStockLog::getChangeType, changeType)
                .like(relatedBillNo != null && !relatedBillNo.isBlank(), WarehouseStockLog::getRelatedBillNo, relatedBillNo)
                .ge(startDate != null && !startDate.isBlank(), WarehouseStockLog::getCreateTime, startDate)
                .le(endDate != null && !endDate.isBlank(), WarehouseStockLog::getCreateTime, endDate + " 23:59:59")
                .isNotNull("PRODUCT".equals(stockType), WarehouseStockLog::getProductId)
                .isNotNull("MATERIAL".equals(stockType), WarehouseStockLog::getMaterialId);

        List<WarehouseStockLog> allRecords;
        long total;
        if (singleProduct) {
            LambdaQueryWrapper<WarehouseStockLog> allWrapper = baseWrapper.clone().orderByAsc(WarehouseStockLog::getId);
            allRecords = stockLogMapper.selectList(allWrapper);
            total = allRecords.size();
        } else {
            LambdaQueryWrapper<WarehouseStockLog> pageWrapper = baseWrapper.clone().orderByDesc(WarehouseStockLog::getId);
            Page<WarehouseStockLog> mpPage = stockLogMapper.selectPage(new Page<>(pageNum, pageSize), pageWrapper);
            allRecords = mpPage.getRecords();
            total = mpPage.getTotal();
        }

        if (!allRecords.isEmpty()) {
            Set<Long> pIds = new HashSet<>();
            for (WarehouseStockLog log : allRecords) {
                if (log.getProductId() != null) pIds.add(log.getProductId());
            }
            Map<Long, String> nameMap = new HashMap<>();
            if (!pIds.isEmpty()) {
                productMapper.selectBatchIds(pIds).forEach(p -> nameMap.put(p.getId(),
                    p.getName() != null ? p.getName() : ""));
            }
            for (WarehouseStockLog log : allRecords) {
                if (log.getProductId() != null) {
                    log.setProductName(nameMap.getOrDefault(log.getProductId(), ""));
                }
                log.setChangeTypeLabel(StockChangeType.labelOf(log.getChangeType()));
                RelatedBillType rbt = RelatedBillType.fromCode(log.getRelatedBillType());
                log.setRelatedBillTypeLabel(rbt != null ? rbt.getLabel() : log.getRelatedBillType());
            }

            if (singleProduct) {
                Map<String, BigDecimal> qualityAfterMap = new HashMap<>();
                for (WarehouseStockLog log : allRecords) {
                    String qt = log.getQualityType() != null ? log.getQualityType() : "A";
                    BigDecimal after = log.getAfterQuantity() != null ? log.getAfterQuantity() : BigDecimal.ZERO;
                    qualityAfterMap.put(qt, after);
                    BigDecimal totalAfter = BigDecimal.ZERO;
                    for (BigDecimal v : qualityAfterMap.values()) {
                        totalAfter = totalAfter.add(v);
                    }
                    log.setTotalAfterStock(totalAfter);
                }
                Collections.reverse(allRecords);
            }

            if (singleProduct) {
                int from = (pageNum - 1) * pageSize;
                int to = Math.min(from + pageSize, allRecords.size());
                allRecords = from < allRecords.size() ? allRecords.subList(from, to) : Collections.emptyList();
            }
        }

        Page<WarehouseStockLog> result = new Page<>(pageNum, pageSize, total);
        result.setRecords(allRecords);
        return R.ok(result);
    }

    /** 按仓库查库存列表 */
    @GetMapping("/by-warehouse/{warehouseId}")
    public R<List<Map<String, Object>>> byWarehouse(@PathVariable Long warehouseId) {
        List<WarehouseStock> stocks = stockMapper.selectList(
            new LambdaQueryWrapper<WarehouseStock>().eq(WarehouseStock::getWarehouseId, warehouseId));

        // 批量查询物料名称和BOM类型名称
        Set<Long> materialIds = new HashSet<>();
        for (WarehouseStock s : stocks) {
            if (s.getMaterialId() != null) materialIds.add(s.getMaterialId());
        }
        Map<Long, String> materialNameMap = new HashMap<>();
        Map<Long, String> bomTypeNameMap = new HashMap<>();
        if (!materialIds.isEmpty()) {
            List<OutsourceMaterial> materials = outsourceMaterialMapper.selectBatchIds(materialIds);
            Map<Long, Long> matBomTypeMap = new HashMap<>();
            for (OutsourceMaterial m : materials) {
                materialNameMap.put(m.getId(), m.getMaterialName());
                if (m.getBomTypeId() != null) matBomTypeMap.put(m.getId(), m.getBomTypeId());
            }
            // 批量查BOM类型名称
            Map<Long, String> btNameMap = new HashMap<>();
            if (!matBomTypeMap.isEmpty()) {
                Set<Long> bomTypeIds = new HashSet<>(matBomTypeMap.values());
                bomTypeMapper.selectBatchIds(bomTypeIds).forEach(b -> btNameMap.put(b.getId(), b.getTypeName()));
            }
            // 物料ID → bomTypeName
            matBomTypeMap.forEach((matId, btId) -> {
                String name = btNameMap.get(btId);
                if (name != null) bomTypeNameMap.put(matId, name);
            });
        }

        // 批量查询产品名称
        Set<Long> productIds = new HashSet<>();
        for (WarehouseStock s : stocks) {
            if (s.getProductId() != null) productIds.add(s.getProductId());
        }
        Map<Long, String> productNameMap = new HashMap<>();
        if (!productIds.isEmpty()) {
            productMapper.selectBatchIds(productIds).forEach(p -> productNameMap.put(p.getId(),
                p.getName() != null ? p.getName() : ""));
        }

        List<Map<String, Object>> list = new ArrayList<>();
        for (WarehouseStock s : stocks) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("warehouseId", s.getWarehouseId());
            m.put("productId", s.getProductId());
            m.put("materialId", s.getMaterialId());
            m.put("qualityType", qualityTypeLabel(s.getQualityType()));
            m.put("quantity", s.getQuantity());
            if (s.getProductId() != null) {
                m.put("productName", productNameMap.getOrDefault(s.getProductId(), ""));
            }
            if (s.getMaterialId() != null) {
                m.put("materialName", materialNameMap.getOrDefault(s.getMaterialId(), ""));
                m.put("bomTypeName", bomTypeNameMap.getOrDefault(s.getMaterialId(), ""));
            }
            list.add(m);
        }
        return R.ok(list);
    }

    /** 物料库存流水（委外） */
    @GetMapping("/material-history")
    public R<Page<WarehouseStockLog>> materialHistory(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam Long warehouseId,
            @RequestParam Long materialId) {
        return R.ok(stockLogMapper.selectPage(new Page<>(pageNum, pageSize),
            new LambdaQueryWrapper<WarehouseStockLog>()
                .eq(WarehouseStockLog::getWarehouseId, warehouseId)
                .eq(WarehouseStockLog::getMaterialId, materialId)
                .orderByDesc(WarehouseStockLog::getId)));
    }

    /** 品质类型枚举转中文标签 */
    private String qualityTypeLabel(String code) {
        if (code == null) return "";
        try { return QualityType.valueOf(code).getLabel(); } catch (Exception e) { return code; }
    }
}
