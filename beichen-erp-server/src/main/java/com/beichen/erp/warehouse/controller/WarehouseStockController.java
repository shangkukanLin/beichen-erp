package com.beichen.erp.warehouse.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.brand.entity.Brand;
import com.beichen.erp.brand.mapper.BrandMapper;
import com.beichen.erp.common.R;
import com.beichen.erp.inventory.common.RelatedBillType;
import com.beichen.erp.inventory.common.StockChangeType;
import com.beichen.erp.dev.entity.MaterialType;
import com.beichen.erp.dev.mapper.MaterialTypeMapper;
import com.beichen.erp.material.common.ProductQualityType;
import com.beichen.erp.material.entity.Product;
import com.beichen.erp.material.mapper.ProductMapper;
import com.beichen.erp.outsource.entity.OutsourceMaterial;
import com.beichen.erp.outsource.mapper.OutsourceMaterialMapper;
import com.beichen.erp.outsource.common.QualityType;
import com.beichen.erp.sale.entity.SaleOutbound;
import com.beichen.erp.sale.mapper.SaleOutboundMapper;
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
import java.util.stream.Collectors;

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
    private final MaterialTypeMapper materialTypeMapper;
    private final BrandMapper brandMapper;
    private final SaleOutboundMapper saleOutboundMapper;

    /** 库存分页查询（stockType: PRODUCT=成品库存 / MATERIAL=物料库存，不传则全量） */
    @GetMapping("/page")
    public R<Page<WarehouseStock>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Long materialId,
            @RequestParam(required = false) String qualityType,
            @RequestParam(required = false) String stockType) {
        return R.ok(stockMapper.selectPage(new Page<>(pageNum, pageSize),
            new LambdaQueryWrapper<WarehouseStock>()
                .eq(warehouseId != null, WarehouseStock::getWarehouseId, warehouseId)
                .eq(productId != null, WarehouseStock::getProductId, productId)
                .eq(materialId != null, WarehouseStock::getMaterialId, materialId)
                .eq(qualityType != null && !qualityType.isBlank(), WarehouseStock::getQualityType, qualityType)
                .isNotNull("PRODUCT".equals(stockType), WarehouseStock::getProductId)
                .isNotNull("MATERIAL".equals(stockType), WarehouseStock::getMaterialId)
                .orderByDesc(WarehouseStock::getId)));
    }

    /**
     * 库存变动类型 **code 列表**（与写入数据库的值一致）。
     * <p>2026-09-14：落实「接口只回 code、前端映射中文」口径 —— 原返回 {code,label}，
     * 现只回 code，中文由前端 `api/enums.ts` 的 `StockChangeTypeLabel` 生成。</p>
     */
    @GetMapping("/change-types")
    public R<List<String>> changeTypes() {
        List<String> list = new ArrayList<>();
        for (StockChangeType t : StockChangeType.values()) {
            list.add(t.getCode());
        }
        return R.ok(list);
    }

    /**
     * 成品库存聚合查询：按（仓库×产品）聚合为一行，展示各品质数量与名称，供成品库存查询页使用。
     * 不影响 /page（明细结构，供其他页面使用）。
     */
    @GetMapping("/product-stock/page")
    public R<Page<Map<String, Object>>> productStockPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) List<Long> warehouseIds,
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String productName) {

        Set<Long> whFilter = buildWarehouseFilter(warehouseId, warehouseIds);
        LambdaQueryWrapper<WarehouseStock> qw = new LambdaQueryWrapper<WarehouseStock>()
                .in(!whFilter.isEmpty(), WarehouseStock::getWarehouseId, whFilter)
                .eq(productId != null, WarehouseStock::getProductId, productId)
                .isNotNull(WarehouseStock::getProductId);

        Set<Long> filteredProductIds = buildProductIdFilter(brandId, productName);
        if (filteredProductIds != null) {
            if (filteredProductIds.isEmpty()) {
                Page<Map<String, Object>> empty = new Page<>(pageNum, pageSize, 0);
                empty.setRecords(Collections.emptyList());
                return R.ok(empty);
            }
            qw.in(WarehouseStock::getProductId, filteredProductIds);
        }

        List<WarehouseStock> all = stockMapper.selectList(qw);

        // 聚合：键 = 仓库ID + 产品ID
        Map<String, Map<String, Object>> agg = new LinkedHashMap<>();
        Set<Long> whIds = new HashSet<>();
        Set<Long> pIds = new HashSet<>();
        for (WarehouseStock s : all) {
            Long whId = s.getWarehouseId();
            Long pId = s.getProductId();
            if (whId == null || pId == null) continue;
            whIds.add(whId);
            pIds.add(pId);
            String key = whId + "_" + pId;
            Map<String, Object> row = agg.computeIfAbsent(key, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("warehouseId", whId);
                m.put("productId", pId);
                m.put("qtyA", BigDecimal.ZERO);
                m.put("qtyB", BigDecimal.ZERO);
                m.put("qtyC", BigDecimal.ZERO);
                m.put("qtyDefect", BigDecimal.ZERO);
                m.put("qtyPending", BigDecimal.ZERO);
                return m;
            });
            BigDecimal q = s.getQuantity() != null ? s.getQuantity() : BigDecimal.ZERO;
            // 按成品品质枚举显式归类：不可用 else 兜底，否则 PENDING(待分类) 等会被静默算成不良品
            ProductQualityType type = ProductQualityType.of(s.getQualityType());
            if (type == null) continue;
            switch (type) {
                case A -> row.put("qtyA", ((BigDecimal) row.get("qtyA")).add(q));
                case B -> row.put("qtyB", ((BigDecimal) row.get("qtyB")).add(q));
                case C -> row.put("qtyC", ((BigDecimal) row.get("qtyC")).add(q));
                case DEFECT -> row.put("qtyDefect", ((BigDecimal) row.get("qtyDefect")).add(q));
                case PENDING -> row.put("qtyPending", ((BigDecimal) row.get("qtyPending")).add(q));
            }
        }

        // 批量补齐名称
        // 存整个仓库对象而非仅名称：详情列表的仓库名要能点击进入仓库详情，需要 factoryId 区分钟委外仓/自有仓
        Map<Long, Warehouse> whMap = new HashMap<>();
        if (!whIds.isEmpty()) {
            warehouseMapper.selectBatchIds(whIds).forEach(w -> whMap.put(w.getId(), w));
        }
        Map<Long, String> pNameMap = new HashMap<>();
        Map<Long, String> pSkuMap = new HashMap<>();
        Map<Long, Long> pBrandMap = new HashMap<>();
        if (!pIds.isEmpty()) {
            productMapper.selectBatchIds(pIds).forEach(p -> {
                pNameMap.put(p.getId(), p.getName() != null ? p.getName() : "");
                pSkuMap.put(p.getId(), p.getSku() != null ? p.getSku() : "");
                if (p.getBrandId() != null) pBrandMap.put(p.getId(), p.getBrandId());
            });
        }
        Map<Long, String> brandNameMap = new HashMap<>();
        if (!pBrandMap.isEmpty()) {
            Set<Long> brandIds = new HashSet<>(pBrandMap.values());
            brandMapper.selectBatchIds(brandIds).forEach(b -> brandNameMap.put(b.getId(), b.getBrandName()));
        }
        for (Map<String, Object> row : agg.values()) {
            Warehouse wh = whMap.get(row.get("warehouseId"));
            row.put("warehouseName", wh != null && wh.getWarehouseName() != null ? wh.getWarehouseName() : "");
            row.put("warehouseCategory", wh != null ? wh.getWarehouseCategory() : null);
            // 委外仓存的是物料，正常情况下成品不会落在委外仓；仍一并返回，便于前端按 factoryId 分流详情页
            row.put("factoryId", wh != null ? wh.getFactoryId() : null);
            row.put("sku", pSkuMap.getOrDefault(row.get("productId"), ""));
            row.put("productName", pNameMap.getOrDefault(row.get("productId"), ""));
            Long pid = (Long) row.get("productId");
            Long bid = pBrandMap.get(pid);
            row.put("brandId", bid);
            row.put("brandName", bid != null ? brandNameMap.getOrDefault(bid, "") : "");
        }

        List<Map<String, Object>> list = new ArrayList<>(agg.values());
        long total = list.size();
        int from = (pageNum - 1) * pageSize;
        List<Map<String, Object>> records = (from < list.size())
                ? list.subList(from, Math.min(from + pageSize, list.size()))
                : Collections.emptyList();
        Page<Map<String, Object>> result = new Page<>(pageNum, pageSize, total);
        result.setRecords(new ArrayList<>(records));
        return R.ok(result);
    }

    /**
     * 成品库存情况：按【产品】聚合，每个产品一行，跨仓库汇总各品质数量、总库存、分布仓库数。
     * 供「成品库存情况」列表页使用；点进某产品看各仓库分布时，再用 product-stock/page?productId= 查明细。
     * 只统计成品（product_id 非空），委外物料库存不在本接口范围内。
     */
    @GetMapping("/product-summary/page")
    public R<Page<Map<String, Object>>> productSummaryPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) List<Long> warehouseIds,
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) Boolean onlyLowStock) {

        Set<Long> whFilter = buildWarehouseFilter(warehouseId, warehouseIds);
        LambdaQueryWrapper<WarehouseStock> qw = new LambdaQueryWrapper<WarehouseStock>()
                .in(!whFilter.isEmpty(), WarehouseStock::getWarehouseId, whFilter)
                .eq(productId != null, WarehouseStock::getProductId, productId)
                .isNotNull(WarehouseStock::getProductId);

        Set<Long> filteredProductIds = buildProductIdFilter(brandId, productName);
        if (filteredProductIds != null) {
            if (filteredProductIds.isEmpty()) {
                Page<Map<String, Object>> empty = new Page<>(pageNum, pageSize, 0);
                empty.setRecords(Collections.emptyList());
                return R.ok(empty);
            }
            qw.in(WarehouseStock::getProductId, filteredProductIds);
        }

        List<WarehouseStock> all = stockMapper.selectList(qw);

        // 聚合：键 = 产品ID；同时记录每个产品涉及的仓库，用于统计分布仓库数
        Map<Long, Map<String, Object>> agg = new LinkedHashMap<>();
        Map<Long, Set<Long>> productWarehouses = new HashMap<>();
        Set<Long> pIds = new HashSet<>();
        for (WarehouseStock s : all) {
            Long pId = s.getProductId();
            if (pId == null) continue;
            pIds.add(pId);
            if (s.getWarehouseId() != null) {
                productWarehouses.computeIfAbsent(pId, k -> new HashSet<>()).add(s.getWarehouseId());
            }
            Map<String, Object> row = agg.computeIfAbsent(pId, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("productId", pId);
                m.put("qtyA", BigDecimal.ZERO);
                m.put("qtyB", BigDecimal.ZERO);
                m.put("qtyC", BigDecimal.ZERO);
                m.put("qtyDefect", BigDecimal.ZERO);
                m.put("qtyPending", BigDecimal.ZERO);
                return m;
            });
            BigDecimal q = s.getQuantity() != null ? s.getQuantity() : BigDecimal.ZERO;
            // 按成品品质枚举显式归类：不可用 else 兜底，否则 PENDING(待分类) 等会被静默算成不良品
            ProductQualityType type = ProductQualityType.of(s.getQualityType());
            if (type == null) continue;
            switch (type) {
                case A -> row.put("qtyA", ((BigDecimal) row.get("qtyA")).add(q));
                case B -> row.put("qtyB", ((BigDecimal) row.get("qtyB")).add(q));
                case C -> row.put("qtyC", ((BigDecimal) row.get("qtyC")).add(q));
                case DEFECT -> row.put("qtyDefect", ((BigDecimal) row.get("qtyDefect")).add(q));
                case PENDING -> row.put("qtyPending", ((BigDecimal) row.get("qtyPending")).add(q));
            }
        }

        // 批量补齐产品档案（名称/SKU/规格/单位/安全库存/品牌）
        Map<Long, Product> productMap = new HashMap<>();
        if (!pIds.isEmpty()) {
            productMapper.selectBatchIds(pIds).forEach(p -> productMap.put(p.getId(), p));
        }
        Map<Long, String> brandNameMap = new HashMap<>();
        Set<Long> brandIds = productMap.values().stream()
                .map(Product::getBrandId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (!brandIds.isEmpty()) {
            brandMapper.selectBatchIds(brandIds).forEach(b -> brandNameMap.put(b.getId(), b.getBrandName()));
        }

        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> row : agg.values()) {
            Long pid = (Long) row.get("productId");
            Product p = productMap.get(pid);
            BigDecimal total = ((BigDecimal) row.get("qtyA")).add((BigDecimal) row.get("qtyB"))
                    .add((BigDecimal) row.get("qtyC")).add((BigDecimal) row.get("qtyDefect"))
                    .add((BigDecimal) row.get("qtyPending"));
            Set<Long> whs = productWarehouses.getOrDefault(pid, Collections.emptySet());
            BigDecimal safetyStock = p != null ? p.getSafetyStock() : null;
            // 低库存：设置了安全库存且总库存低于它（安全库存为空或 0 表示未设置，不参与判断）
            boolean lowStock = safetyStock != null && safetyStock.compareTo(BigDecimal.ZERO) > 0
                    && total.compareTo(safetyStock) < 0;

            row.put("sku", p != null && p.getSku() != null ? p.getSku() : "");
            row.put("productName", p != null && p.getName() != null ? p.getName() : "");
            row.put("unit", p != null && p.getUnit() != null ? p.getUnit() : "");
            row.put("safetyStock", safetyStock);
            row.put("brandId", p != null ? p.getBrandId() : null);
            Long bid = p != null ? p.getBrandId() : null;
            row.put("brandName", bid != null ? brandNameMap.getOrDefault(bid, "") : "");
            row.put("totalQuantity", total);
            row.put("warehouseCount", whs.size());
            row.put("lowStock", lowStock);

            if (Boolean.TRUE.equals(onlyLowStock) && !lowStock) continue;
            list.add(row);
        }

        // 库存多的排前面，便于优先关注积压产品；同库存按产品名称稳定排序
        list.sort(Comparator
                .comparing((Map<String, Object> r) -> (BigDecimal) r.get("totalQuantity")).reversed()
                .thenComparing(r -> String.valueOf(r.get("productName"))));

        long total = list.size();
        int from = (pageNum - 1) * pageSize;
        List<Map<String, Object>> records = (from < list.size())
                ? list.subList(from, Math.min(from + pageSize, list.size()))
                : Collections.emptyList();
        Page<Map<String, Object>> result = new Page<>(pageNum, pageSize, total);
        result.setRecords(new ArrayList<>(records));
        return R.ok(result);
    }

    /** 仓库筛选：支持单值 warehouseId 与多选 warehouseIds，合并去重（warehouseId 保留向后兼容） */
    private Set<Long> buildWarehouseFilter(Long warehouseId, List<Long> warehouseIds) {
        Set<Long> whFilter = new LinkedHashSet<>();
        if (warehouseIds != null) {
            warehouseIds.stream().filter(Objects::nonNull).forEach(whFilter::add);
        }
        if (warehouseId != null) whFilter.add(warehouseId);
        return whFilter;
    }

    /**
     * 产品筛选：品牌 + 名称/SKU 关键字，两个条件同时存在时取交集。
     * 返回 null 表示不筛选；返回空集合表示查无结果（调用方直接返回空页，避免 in() 空集合报错）。
     */
    private Set<Long> buildProductIdFilter(Long brandId, String productName) {
        Set<Long> filteredProductIds = null;
        if (brandId != null) {
            filteredProductIds = productMapper.selectList(
                    new LambdaQueryWrapper<Product>().eq(Product::getBrandId, brandId))
                    .stream().map(Product::getId).collect(Collectors.toSet());
        }
        if (productName != null && !productName.isBlank()) {
            // 关键字同时匹配产品名称与 SKU，便于直接粘贴 SKU 检索
            Set<Long> nameIds = productMapper.selectList(
                    new LambdaQueryWrapper<Product>()
                            .like(Product::getName, productName)
                            .or().like(Product::getSku, productName))
                    .stream().map(Product::getId).collect(Collectors.toSet());
            filteredProductIds = (filteredProductIds == null)
                    ? nameIds
                    : filteredProductIds.stream().filter(nameIds::contains).collect(Collectors.toSet());
        }
        return filteredProductIds;
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
                // 2026-09-14：变动类型/关联单据类型的中文改由前端按 code 映射（StockChangeTypeLabel / RelatedBillTypeLabel）
                // 详情跳转目标ID：销售出库单无独立详情页，映射为其关联销售单ID；其余类型直接用单据ID
                Long billId = log.getRelatedBillId();
                if (billId != null) {
                    Long detailId = billId;
                    if ("SALE_OUTBOUND".equals(log.getRelatedBillType())) {
                        SaleOutbound ob = saleOutboundMapper.selectById(billId);
                        if (ob != null && ob.getOrderId() != null) detailId = ob.getOrderId();
                    }
                    log.setRelatedBillDetailId(detailId);
                }
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

        // 批量查询物料名称和物料类型名称
        Set<Long> materialIds = new HashSet<>();
        for (WarehouseStock s : stocks) {
            if (s.getMaterialId() != null) materialIds.add(s.getMaterialId());
        }
        Map<Long, String> materialNameMap = new HashMap<>();
        Map<Long, String> materialTypeNameMap = new HashMap<>();
        // F7-132（2026-09-20）：另需「物料ID → 类型ID / 类型 sortOrder」。原文只回类型名，导致前端只能用
        // **中文类型名**做排序优先级（`['玻璃','驱动IC']`，改名即静默失效）⇒ 这里把映射提到外层作用域，返回时一并补字段。
        Map<Long, Long> matMaterialTypeMap = new HashMap<>();
        Map<Long, Integer> btSortMap = new HashMap<>();
        if (!materialIds.isEmpty()) {
            List<OutsourceMaterial> materials = outsourceMaterialMapper.selectBatchIds(materialIds);
            for (OutsourceMaterial m : materials) {
                materialNameMap.put(m.getId(), m.getMaterialName());
                if (m.getMaterialTypeId() != null) matMaterialTypeMap.put(m.getId(), m.getMaterialTypeId());
            }
            // 批量查物料类型名称与排序
            Map<Long, String> btNameMap = new HashMap<>();
            if (!matMaterialTypeMap.isEmpty()) {
                Set<Long> materialTypeIds = new HashSet<>(matMaterialTypeMap.values());
                materialTypeMapper.selectBatchIds(materialTypeIds).forEach(b -> {
                    btNameMap.put(b.getId(), b.getTypeName());
                    btSortMap.put(b.getId(), b.getSortOrder() != null ? b.getSortOrder() : 999);
                });
            }
            // 物料ID → materialTypeName
            matMaterialTypeMap.forEach((matId, btId) -> {
                String name = btNameMap.get(btId);
                if (name != null) materialTypeNameMap.put(matId, name);
            });
        }

        // 批量查询产品名称
        Set<Long> productIds = new HashSet<>();
        for (WarehouseStock s : stocks) {
            if (s.getProductId() != null) productIds.add(s.getProductId());
        }
        Map<Long, String> productNameMap = new HashMap<>();
        Map<Long, String> productSkuMap = new HashMap<>();
        if (!productIds.isEmpty()) {
            productMapper.selectBatchIds(productIds).forEach(p -> {
                productNameMap.put(p.getId(), p.getName() != null ? p.getName() : "");
                productSkuMap.put(p.getId(), p.getSku() != null ? p.getSku() : "");
            });
        }

        List<Map<String, Object>> list = new ArrayList<>();
        for (WarehouseStock s : stocks) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("warehouseId", s.getWarehouseId());
            m.put("productId", s.getProductId());
            m.put("materialId", s.getMaterialId());
            // 2026-09-14：品质按 code 原样返回（成品=ProductQualityType A/B/C/DEFECT/PENDING，物料=QualityType GOOD/DEFECT），
            // 中文由前端映射（避免后端回中文后前端无法按 code 归并——见 inventory/warehouse-detail.vue 的按品质归并逻辑）
            m.put("qualityType", s.getQualityType());
            m.put("quantity", s.getQuantity());
            if (s.getProductId() != null) {
                m.put("productName", productNameMap.getOrDefault(s.getProductId(), ""));
                m.put("sku", productSkuMap.getOrDefault(s.getProductId(), ""));
            }
            if (s.getMaterialId() != null) {
                m.put("materialName", materialNameMap.getOrDefault(s.getMaterialId(), ""));
                m.put("materialTypeName", materialTypeNameMap.getOrDefault(s.getMaterialId(), ""));
                // F7-132（2026-09-20）：补类型 id 与 sortOrder，供前端做"优先类型置顶"排序，
                // 不再依赖可改的中文类型名（纯新增字段，向后兼容）
                Long mtId = matMaterialTypeMap.get(s.getMaterialId());
                m.put("materialTypeId", mtId);
                m.put("materialTypeSortOrder", mtId != null ? btSortMap.getOrDefault(mtId, 999) : null);
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
        Page<WarehouseStockLog> page = stockLogMapper.selectPage(new Page<>(pageNum, pageSize),
            new LambdaQueryWrapper<WarehouseStockLog>()
                .eq(WarehouseStockLog::getWarehouseId, warehouseId)
                .eq(WarehouseStockLog::getMaterialId, materialId)
                .orderByDesc(WarehouseStockLog::getId));

        // 2026-09-14：变动类型/关联单据类型的中文改由前端按 code 映射，后端不再回中文

        // 物料名称兜底：流水冗余列 material_name 为空时，按 materialId 实时查补
        Map<Long, String> matNameMap = new HashMap<>();
        for (WarehouseStockLog log : page.getRecords()) {
            if (log.getMaterialId() != null && (log.getMaterialName() == null || log.getMaterialName().isEmpty())) {
                matNameMap.putIfAbsent(log.getMaterialId(), null);
            }
        }
        if (!matNameMap.isEmpty()) {
            List<OutsourceMaterial> mats = outsourceMaterialMapper.selectBatchIds(matNameMap.keySet());
            for (OutsourceMaterial m : mats) {
                matNameMap.put(m.getId(), m.getMaterialName());
            }
            for (WarehouseStockLog log : page.getRecords()) {
                if (log.getMaterialId() != null && (log.getMaterialName() == null || log.getMaterialName().isEmpty())) {
                    log.setMaterialName(matNameMap.getOrDefault(log.getMaterialId(), ""));
                }
            }
        }

        return R.ok(page);
    }

}
