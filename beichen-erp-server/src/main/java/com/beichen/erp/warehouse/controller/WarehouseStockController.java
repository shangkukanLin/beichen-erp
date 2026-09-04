package com.beichen.erp.warehouse.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.brand.entity.Brand;
import com.beichen.erp.brand.mapper.BrandMapper;
import com.beichen.erp.common.R;
import com.beichen.erp.inventory.common.RelatedBillType;
import com.beichen.erp.inventory.common.StockChangeType;
import com.beichen.erp.dev.entity.BomType;
import com.beichen.erp.dev.mapper.BomTypeMapper;
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
    private final BomTypeMapper bomTypeMapper;
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
     * 库存变动类型下拉选项：直接取 StockChangeType 枚举，保证与写入数据库的值一致。
     * 前端不再硬编码枚举名——此前硬编码既显示英文，又因只列了 10 个导致多数类型无法筛选。
     */
    @GetMapping("/change-types")
    public R<List<Map<String, String>>> changeTypes() {
        List<Map<String, String>> list = new ArrayList<>();
        for (StockChangeType t : StockChangeType.values()) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("code", t.getCode());
            m.put("label", t.getLabel());
            list.add(m);
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
            @RequestParam(required = false) String productName) {

        // 仓库筛选支持多选（warehouseIds，逗号分隔）；warehouseId 单值参数保留向后兼容
        Set<Long> whFilter = new LinkedHashSet<>();
        if (warehouseIds != null) {
            warehouseIds.stream().filter(Objects::nonNull).forEach(whFilter::add);
        }
        if (warehouseId != null) whFilter.add(warehouseId);

        LambdaQueryWrapper<WarehouseStock> qw = new LambdaQueryWrapper<WarehouseStock>()
                .in(!whFilter.isEmpty(), WarehouseStock::getWarehouseId, whFilter)
                .isNotNull(WarehouseStock::getProductId);

        // 品牌/产品名称过滤：先按条件查产品主键集合，再按集合过滤库存（两者同时存在时取交集）
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
        Map<Long, String> whNameMap = new HashMap<>();
        if (!whIds.isEmpty()) {
            warehouseMapper.selectBatchIds(whIds).forEach(w -> whNameMap.put(w.getId(), w.getWarehouseName()));
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
            row.put("warehouseName", whNameMap.getOrDefault(row.get("warehouseId"), ""));
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
            m.put("qualityType", qualityTypeLabel(s.getQualityType(), s.getProductId() == null));
            m.put("quantity", s.getQuantity());
            if (s.getProductId() != null) {
                m.put("productName", productNameMap.getOrDefault(s.getProductId(), ""));
                m.put("sku", productSkuMap.getOrDefault(s.getProductId(), ""));
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
        Page<WarehouseStockLog> page = stockLogMapper.selectPage(new Page<>(pageNum, pageSize),
            new LambdaQueryWrapper<WarehouseStockLog>()
                .eq(WarehouseStockLog::getWarehouseId, warehouseId)
                .eq(WarehouseStockLog::getMaterialId, materialId)
                .orderByDesc(WarehouseStockLog::getId));

        // 补展示字段：变动类型/关联单据类型中文标签
        for (WarehouseStockLog log : page.getRecords()) {
            log.setChangeTypeLabel(StockChangeType.labelOf(log.getChangeType()));
            RelatedBillType rbt = RelatedBillType.fromCode(log.getRelatedBillType());
            log.setRelatedBillTypeLabel(rbt != null ? rbt.getLabel() : log.getRelatedBillType());
        }

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

    /**
     * 品质编码转中文标签：成品与委外物料的品质值域不同，必须按记录类型选用对应枚举，不可混用。
     * @param isMaterial true=委外物料记录（materialId 非空），false=成品记录（productId 非空）
     */
    private String qualityTypeLabel(String code, boolean isMaterial) {
        if (code == null) return "";
        if (isMaterial) {
            try { return QualityType.valueOf(code).getLabel(); } catch (Exception e) { return code; }
        }
        ProductQualityType p = ProductQualityType.of(code);
        return p != null ? p.getLabel() : code;
    }
}
