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
import com.beichen.erp.material.common.ProductStatus;
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
import com.beichen.erp.warehouse.mapper.StagnantAnalysisMapper;
import com.beichen.erp.warehouse.mapper.WarehouseStockMapper;
import com.beichen.erp.warehouse.service.StockCosts;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
    private final StagnantAnalysisMapper stagnantAnalysisMapper;

    /**
     * 滞销判定默认阈值（天）：**距「最近一次来货日」或「最后销售日」中较晚的那个**超过这么多天 ⇒ 滞销。
     *
     * <p>口径来源：用户 2026-10-02（默认 15 天、页面可调）；2026-10-09 追加"来货也重置滞销时钟"
     * （原话：「15 天应该是最新来货（委外加工或者是成品购入）后，15 天这个产品没有销售记录的。就算滞销」）。
     * 同一天用户还取消了原先独立的「统计窗口（默认 90 天）」（原话：「90 天这个不要」）⇒
     * 本类不再有 {@code DEFAULT_RECENT_DAYS}，「期间销量 / 周转天数」两个派生字段一并与窗口移除。</p>
     */
    private static final int DEFAULT_NO_SALE_DAYS = 15;
    /** 严重滞销：停滞超过这么多天（"从未销售且无来货"的用"在库天数"比同一个值判定） */
    private static final int SEVERE_NO_SALE_DAYS = 180;
    /** 停滞天数分桶（固定区间，末桶 = 从未销售；顺序即图表 x 轴顺序） */
    private static final List<String> STAGNANT_BUCKETS = List.of(
            "0-15天", "16-30天", "31-60天", "61-90天", "91-180天", ">180天", "从未销售");

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
            // 按成品品质枚举显式归类：不可用 else 兜底，否则 PENDING(待整理) 等会被静默算成不良品
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
        // 2026-10-09 库存金额：本段是明细页（按 仓库×产品）的唯一补档点，顺手把单价解析出来，供行金额使用
        Map<Long, BigDecimal> pUnitCostMap = new HashMap<>();
        if (!pIds.isEmpty()) {
            productMapper.selectBatchIds(pIds).forEach(p -> {
                pNameMap.put(p.getId(), p.getName() != null ? p.getName() : "");
                pSkuMap.put(p.getId(), p.getSku() != null ? p.getSku() : "");
                pUnitCostMap.put(p.getId(), StockCosts.unitCost(p));
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
            // 2026-10-09 库存金额：行金额 = 本行总库存 × 单价；单价未维护时记 0 并明示（口径见 StockCosts）
            BigDecimal rowQty = ((BigDecimal) row.get("qtyA")).add((BigDecimal) row.get("qtyB"))
                    .add((BigDecimal) row.get("qtyC")).add((BigDecimal) row.get("qtyDefect"))
                    .add((BigDecimal) row.get("qtyPending"));
            BigDecimal unitCost = pUnitCostMap.get(row.get("productId"));
            row.put("unitCost", unitCost);
            row.put("stockAmount", StockCosts.amount(rowQty, unitCost));
            row.put("costMissing", unitCost == null);
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
            // 按成品品质枚举显式归类：不可用 else 兜底，否则 PENDING(待整理) 等会被静默算成不良品
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

        // ---- 滞销时钟（2026-10-10 用户口径：列表页要新增「是否滞销」列）----
        // 与 /stagnant/page 用**同一套口径、同一个 mapper**（绝不在两处各写一份判定 ✗ 否则数字会打架 ✓）：
        //   起算点 ref = max(最后销售日, 最近来货日) —— 来货也会重置滞销时钟 ✓；
        //   两者都没有（既没来过货、也从没卖过）⇒ ref=null ⇒ 一律算滞销 ✓。
        //   阈值固定取 DEFAULT_NO_SALE_DAYS(15)：本页撤掉滞销面板后**没有阈值入口** ⇒ 与后端默认值对齐 ✓。
        LocalDate stagnantToday = LocalDate.now();
        Map<Long, LocalDate> lastSaleForList = new HashMap<>();
        for (Map<String, Object> r : stagnantAnalysisMapper.productLastSaleByProduct()) {
            if (r.get("pid") == null || r.get("d") == null) continue;
            lastSaleForList.put(((Number) r.get("pid")).longValue(), LocalDate.parse(String.valueOf(r.get("d"))));
        }
        Map<Long, LocalDate> lastInForList = new HashMap<>();
        for (Map<String, Object> r : stagnantAnalysisMapper.productLastInByProduct()) {
            if (r.get("pid") == null || r.get("d") == null) continue;
            lastInForList.put(((Number) r.get("pid")).longValue(), LocalDate.parse(String.valueOf(r.get("d"))));
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
            // 2026-10-09 库存金额：本行 = 跨仓库总库存 × 单价（单价同 product-stock：成本价 → 最近进价）
            BigDecimal pUnitCost = StockCosts.unitCost(p);
            row.put("unitCost", pUnitCost);
            row.put("stockAmount", StockCosts.amount(total, pUnitCost));
            row.put("costMissing", pUnitCost == null);
            // 滞销（2026-10-10 用户口径）：起算点 = max(最后销售日, 最近来货日)；
            // ref 为空（从没来过货、也没卖出过）⇒ stagnantDays=null 且**一律算滞销** ✓（与 /stagnant/page 一致 ✓）
            LocalDate refForStagnant = lastSaleForList.get(pid);
            LocalDate lastInDate = lastInForList.get(pid);
            if (refForStagnant == null || (lastInDate != null && lastInDate.isAfter(refForStagnant))) {
                refForStagnant = lastInDate;
            }
            Long stagnantDaysForList = refForStagnant == null ? null
                    : ChronoUnit.DAYS.between(refForStagnant, stagnantToday);
            row.put("stagnantDays", stagnantDaysForList);
            row.put("stagnant", stagnantDaysForList == null || stagnantDaysForList >= DEFAULT_NO_SALE_DAYS);

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

    /**
     * 滞销（呆滞）分析：**有库存 × 一段时间没有销售** —— 供「成品库存情况」页下部的滞销块使用（2026-10-02 用户要求）。
     *
     * <p><b>口径（用户 2026-10-02 拍板）：</b></p>
     * <ol>
     *   <li>「卖不动」= 该产品**最后销售日**（已审核销售单的**建单日**，与全站销售口径同源）距离今天 ≥ 阈值；
     *       阈值默认 {@value #DEFAULT_NO_SALE_DAYS} 天，由页面传入（{@code noSaleDays}）。</li>
     *   <li>「有库存」= 五档品质数量合计 &gt; 0（{@code available_quantity} 恒等于 quantity、全库无"在途"列
     *       ⇒ 做不了"可用库存滞销"）。库存为 0 的产品不进清单、也不进 KPI 的分母。</li>
     *   <li><b>刻意不用"最后任意出库/减少变动"当动销</b>：本库实测 13 行流水里的负向变动只有 2 行是
     *       {@code SALE_OUT}，其余是 {@code RECLASSIFY_OUT}（品质重分类）、{@code RETURN_OUT}（退货出库）、
     *       {@code CANCEL_RECLASSIFY_IN}、{@code SALE_OUT_UN_AUDIT}（反审核）⇒ 用"任何减少"口径会把它们
     *       全算成"卖过"，滞销清单**漏报**（2026-10-02 实测结论，勿改回）。</li>
     *   <li>换货出库不计入"销售"（换货不是新增销售，与产品分析同口径）。</li>
     *   <li><b>【2026-10-09 已取消】</b>原「期间销量 / 周转天数」的统计窗口（{@code recentDays}，默认 90 天）——
     *       用户口径「90 天这个不要」⇒ 窗口连同这两个派生字段一并移除，响应里不再有 {@code recentDays}。</li>
     *   <li>滞销时钟起点 = <b>max(最近一次来货日, 最后销售日)</b>：来货会重置时钟（"新到的货 15 天没卖出去"
     *       也算滞销，用户 2026-10-09 口径），"卖了之后又 15 天没动"同样算滞销。阈值 {@code noSaleDays}
     *       （默认 {@value #DEFAULT_NO_SALE_DAYS} 天）随响应回传（{@code threshold}），前端文案用回传值。</li>
     * </ol>
     *
     * <p><b>为什么这里的库存聚合另写一段，而不复用 {@link #productSummaryPage}：</b>那是本页主表的读路径，
     * 已在列宽/截断/同行等守卫下稳定；滞销块需要的是"同一份聚合 + 销售活动"，改主路径的风险大于重复这段循环。
     * 两处聚合口径必须一致（按 {@link ProductQualityType} 显式归类、未知品质跳过、跨仓库求和）。</p>
     *
     * <p><b>返回</b> {@code {records, total, kpi, buckets, threshold}}：{@code kpi} 与 {@code buckets}
     * **恒按"全部有库存产品"**统计（不受 `onlyStagnant` / `onlySevere` 影响）—— 否则一勾选分布图就跳变，
     * 看不出"滞销在整体里的位置"。{@code buckets} 的 7 个区间是**固定**的，与阈值无关。</p>
     */
    @GetMapping("/stagnant/page")
    public R<Map<String, Object>> stagnantPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) List<Long> warehouseIds,
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) Integer noSaleDays,
            @RequestParam(required = false) Boolean onlyStagnant,
            @RequestParam(required = false) Boolean onlySevere,
            @RequestParam(required = false) Boolean includeDiscontinued) {

        int threshold = noSaleDays == null ? DEFAULT_NO_SALE_DAYS : Math.max(1, Math.min(3650, noSaleDays));
        boolean stagnantOnly = !Boolean.FALSE.equals(onlyStagnant);            // 默认只列滞销
        boolean severeOnly = Boolean.TRUE.equals(onlySevere);
        boolean withDiscontinued = !Boolean.FALSE.equals(includeDiscontinued); // 默认把停售产品一并归入

        Set<Long> whFilter = buildWarehouseFilter(warehouseId, warehouseIds);
        LambdaQueryWrapper<WarehouseStock> qw = new LambdaQueryWrapper<WarehouseStock>()
                .in(!whFilter.isEmpty(), WarehouseStock::getWarehouseId, whFilter)
                .eq(productId != null, WarehouseStock::getProductId, productId)
                .isNotNull(WarehouseStock::getProductId);
        Set<Long> filteredProductIds = buildProductIdFilter(brandId, productName);
        if (filteredProductIds != null) {
            if (filteredProductIds.isEmpty()) {
                return R.ok(stagnantResult(Collections.emptyList(), threshold));
            }
            qw.in(WarehouseStock::getProductId, filteredProductIds);
        }

        // ---- ① 库存聚合（按产品）----
        Map<Long, Map<String, Object>> agg = new LinkedHashMap<>();
        Map<Long, Set<Long>> productWarehouses = new HashMap<>();
        for (WarehouseStock s : stockMapper.selectList(qw)) {
            Long pId = s.getProductId();
            if (pId == null) continue;
            if (s.getWarehouseId() != null) {
                productWarehouses.computeIfAbsent(pId, k -> new HashSet<>()).add(s.getWarehouseId());
            }
            Map<String, Object> row = agg.computeIfAbsent(pId, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("productId", k);
                m.put("qtyA", BigDecimal.ZERO);
                m.put("qtyB", BigDecimal.ZERO);
                m.put("qtyC", BigDecimal.ZERO);
                m.put("qtyDefect", BigDecimal.ZERO);
                m.put("qtyPending", BigDecimal.ZERO);
                return m;
            });
            BigDecimal q = s.getQuantity() != null ? s.getQuantity() : BigDecimal.ZERO;
            // 与 productSummaryPage 同一归类方式：显式按品质枚举，不用 else 兜底（否则 PENDING 会被算成不良）
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

        // ---- ② 销售活动：最后销售日（2026-10-09：原"近 recentWindow 天销量"随统计窗口一并取消）----
        LocalDate today = LocalDate.now();
        Map<Long, LocalDate> lastSale = new HashMap<>();
        for (Map<String, Object> r : stagnantAnalysisMapper.productLastSaleByProduct()) {
            if (r.get("pid") == null || r.get("d") == null) continue;   // 无建单日的行无法归期
            lastSale.put(((Number) r.get("pid")).longValue(), LocalDate.parse(String.valueOf(r.get("d"))));
        }

        // ---- ②b 来货日：最近一次来货（2026-10-09 新增，白名单见 StagnantAnalysisMapper.INBOUND_TYPES）----
        // 用途：与"最后销售日"一起构成滞销时钟的起点（取较晚者）—— 新到的货压 15 天没卖出去同样算滞销。
        Map<Long, LocalDate> lastIn = new HashMap<>();
        for (Map<String, Object> r : stagnantAnalysisMapper.productLastInByProduct()) {
            if (r.get("pid") == null || r.get("d") == null) continue;
            lastIn.put(((Number) r.get("pid")).longValue(), LocalDate.parse(String.valueOf(r.get("d"))));
        }

        // ---- ③ 首次入库日：在库天数；并用于"从未销售但入库未满 180 天 ⇒ 不算严重滞销（视为新品）"----
        Map<Long, LocalDate> firstIn = new HashMap<>();
        for (Map<String, Object> r : stagnantAnalysisMapper.productFirstInByProduct()) {
            if (r.get("pid") == null || r.get("d") == null) continue;
            firstIn.put(((Number) r.get("pid")).longValue(), LocalDate.parse(String.valueOf(r.get("d"))));
        }

        // ---- ④ 产品档案 / 品牌（一次批量取）----
        Set<Long> pIds = agg.keySet();
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

        // ---- ⑤ 逐产品装配（全部有库存的产品 = KPI/分桶的分母）----
        List<Map<String, Object>> allRows = new ArrayList<>();
        for (Map<String, Object> row : agg.values()) {
            Long pid = (Long) row.get("productId");
            BigDecimal total = ((BigDecimal) row.get("qtyA")).add((BigDecimal) row.get("qtyB"))
                    .add((BigDecimal) row.get("qtyC")).add((BigDecimal) row.get("qtyDefect"))
                    .add((BigDecimal) row.get("qtyPending"));
            if (total.compareTo(BigDecimal.ZERO) <= 0) continue;   // 库存为 0 ⇒ 不是滞销

            Product p = productMap.get(pid);
            LocalDate ls = lastSale.get(pid);
            LocalDate li = lastIn.get(pid);
            // 停滞天数 = 今天 − 起算点。起算点（用户 2026-10-09 第二次口径，覆盖同日第一次的"取较晚者"）：
            //   · **有销售记录** ⇒ 用**最后销售日**（回落到 2026-10-02 的原口径 ✓）；
            //   · **没有销售记录**（从未卖出过）⇒ 用**最近来货日**（白名单见 StagnantAnalysisMapper.INBOUND_TYPES）；
            //   · 两者都没有 ⇒ null：库里压着、一天都没动 ⇒ 一律算滞销（停滞天数显示 —）。
            // ⚠️ 注意"来货"**不再**重置时钟：卖过一次之后，即使之后又来货，仍按最后销售日算。
            LocalDate ref = (ls != null) ? ls : li;
            Long stagnantDays = ref == null ? null : ChronoUnit.DAYS.between(ref, today);
            LocalDate fi = firstIn.get(pid);
            Long stockAgeDays = fi == null ? null : ChronoUnit.DAYS.between(fi, today);
            boolean stagnant = stagnantDays == null || stagnantDays >= threshold;
            // 严重滞销：停滞 > 180 天；"从未销售且无来货"的按"在库超过 180 天"判定（刚建的新品不算）
            boolean severe = (stagnantDays != null)
                    ? stagnantDays > SEVERE_NO_SALE_DAYS
                    : (stockAgeDays != null && stockAgeDays > SEVERE_NO_SALE_DAYS);

            BigDecimal unitCost = p != null && p.getCostPrice() != null ? p.getCostPrice() : BigDecimal.ZERO;
            BigDecimal refAmount = total.multiply(unitCost);
            ProductStatus status = p != null ? p.getStatus() : null;
            boolean discontinued = status == ProductStatus.DISCONTINUED;

            row.put("sku", p != null && p.getSku() != null ? p.getSku() : "");
            row.put("productName", p != null && p.getName() != null ? p.getName() : "");
            row.put("unit", p != null && p.getUnit() != null ? p.getUnit() : "");
            row.put("brandId", p != null ? p.getBrandId() : null);
            Long bid = p != null ? p.getBrandId() : null;
            row.put("brandName", bid != null ? brandNameMap.getOrDefault(bid, "") : "");
            row.put("totalQuantity", total);
            row.put("warehouseCount", productWarehouses.getOrDefault(pid, Collections.emptySet()).size());
            row.put("lastSaleDate", ls != null ? ls.toString() : null);
            row.put("lastInDate", li != null ? li.toString() : null);
            row.put("stagnantDays", stagnantDays);
            row.put("firstInDate", fi != null ? fi.toString() : null);
            row.put("stockAgeDays", stockAgeDays);
            row.put("unitCost", unitCost);
            row.put("refAmount", refAmount);
            row.put("costMissing", unitCost.compareTo(BigDecimal.ZERO) <= 0);
            row.put("productStatus", status != null ? status.name() : "");
            row.put("discontinued", discontinued);
            row.put("stagnant", stagnant);
            row.put("severe", severe);
            allRows.add(row);
        }

        // ---- ⑥ 清单筛选 + 排序 ----
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> r : allRows) {
            if (stagnantOnly && !Boolean.TRUE.equals(r.get("stagnant"))) continue;
            if (severeOnly && !Boolean.TRUE.equals(r.get("severe"))) continue;
            if (!withDiscontinued && Boolean.TRUE.equals(r.get("discontinued"))) continue;
            list.add(r);
        }
        // 停滞天数降序（从未销售视为最大 ⇒ 排最前），再按库存量降序、产品名稳定排序
        list.sort(Comparator
                .comparing((Map<String, Object> r) -> {
                    Object d = r.get("stagnantDays");
                    return d == null ? Long.MAX_VALUE : ((Number) d).longValue();
                }).reversed()
                .thenComparing(Comparator.comparing((Map<String, Object> r) -> (BigDecimal) r.get("totalQuantity")).reversed())
                .thenComparing(r -> String.valueOf(r.get("productName"))));

        Map<String, Object> result = stagnantResult(allRows, threshold);
        long total = list.size();
        int from = (pageNum - 1) * pageSize;
        List<Map<String, Object>> records = (from < list.size())
                ? list.subList(from, Math.min(from + pageSize, list.size()))
                : Collections.emptyList();
        result.put("records", new ArrayList<>(records));
        result.put("total", total);
        return R.ok(result);
    }

    /**
     * 滞销响应的 KPI 与分桶：**恒以全部有库存产品为分母**（与清单的勾选无关）。
     * 分桶区间固定为 0-15 / 16-30 / 31-60 / 61-90 / 91-180 / &gt;180 / 从未销售，
     * 与页面可调的阈值分开 —— 阈值只决定"算不算滞销"，区间决定"停在哪个天数档"。
     */
    private Map<String, Object> stagnantResult(List<Map<String, Object>> allRows, int threshold) {
        int allProducts = allRows.size();
        BigDecimal allQty = BigDecimal.ZERO;
        BigDecimal stagnantQty = BigDecimal.ZERO;
        BigDecimal stagnantValue = BigDecimal.ZERO;
        BigDecimal costMissingQty = BigDecimal.ZERO;
        int stagnantProducts = 0;
        int neverSold = 0;
        long daysSum = 0;
        int daysCount = 0;
        Map<String, BigDecimal> bucketQty = new LinkedHashMap<>();
        Map<String, Integer> bucketProducts = new LinkedHashMap<>();
        for (String label : STAGNANT_BUCKETS) { bucketQty.put(label, BigDecimal.ZERO); bucketProducts.put(label, 0); }

        for (Map<String, Object> r : allRows) {
            BigDecimal qty = (BigDecimal) r.get("totalQuantity");
            allQty = allQty.add(qty);
            String label = stagnantBucketLabel((Long) r.get("stagnantDays"));
            bucketQty.put(label, bucketQty.get(label).add(qty));
            bucketProducts.put(label, bucketProducts.get(label) + 1);
            if (!Boolean.TRUE.equals(r.get("stagnant"))) continue;
            stagnantProducts++;
            stagnantQty = stagnantQty.add(qty);
            stagnantValue = stagnantValue.add((BigDecimal) r.get("refAmount"));
            if (Boolean.TRUE.equals(r.get("costMissing"))) costMissingQty = costMissingQty.add(qty);
            // 2026-10-09 口径变更：停滞天数 = 今天 − max(最近来货日, 最后销售日)。此时
            // "停滞天数为 — " ⟺ 既无来货也从未卖出过 ⇒「从未销售」改按 **lastSaleDate 为空** 判定
            // （更贴合卡片名；这类产品的 days 也一定为 null ✓）。
            if (r.get("lastSaleDate") == null) neverSold++;
            Long d = (Long) r.get("stagnantDays");
            if (d != null) { daysSum += d; daysCount++; }
        }

        Map<String, Object> kpi = new LinkedHashMap<>();
        kpi.put("productCount", stagnantProducts);                     // 滞销品项数
        kpi.put("qty", stagnantQty);                                   // 滞销库存量
        kpi.put("refAmount", stagnantValue);                           // 参考金额（总库存 × 移动加权成本价现值）
        kpi.put("costMissingQty", costMissingQty);                     // 其中成本价未维护（金额被低估）的数量
        kpi.put("avgStagnantDays", daysCount > 0
                ? BigDecimal.valueOf(daysSum / (double) daysCount).setScale(1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);                                    // 平均停滞天数（从未销售的不参与，另列）
        kpi.put("neverSoldCount", neverSold);                          // 滞销里"从未销售过"的品项数
        kpi.put("shareQty", allQty.compareTo(BigDecimal.ZERO) > 0
                ? stagnantQty.multiply(BigDecimal.valueOf(100)).divide(allQty, 1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);                                    // 滞销数量占全部库存数量的比（%）
        kpi.put("allProductCount", allProducts);
        kpi.put("allQty", allQty);
        kpi.put("severeCount", allRows.stream().filter(r -> Boolean.TRUE.equals(r.get("severe"))).count());
        kpi.put("discontinuedCount", allRows.stream().filter(r -> Boolean.TRUE.equals(r.get("discontinued"))).count());

        List<Map<String, Object>> buckets = new ArrayList<>();
        for (String label : STAGNANT_BUCKETS) {
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("label", label);
            b.put("products", bucketProducts.get(label));
            b.put("qty", bucketQty.get(label));
            buckets.add(b);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("threshold", threshold);
        result.put("kpi", kpi);
        result.put("buckets", buckets);
        result.put("records", Collections.emptyList());
        result.put("total", 0L);
        return result;
    }

    /** 停滞天数 → 固定分桶标签（null = 从未销售过）。 */
    private static String stagnantBucketLabel(Long days) {
        if (days == null) return STAGNANT_BUCKETS.get(STAGNANT_BUCKETS.size() - 1);
        long d = days;
        if (d <= 15) return STAGNANT_BUCKETS.get(0);
        if (d <= 30) return STAGNANT_BUCKETS.get(1);
        if (d <= 60) return STAGNANT_BUCKETS.get(2);
        if (d <= 90) return STAGNANT_BUCKETS.get(3);
        if (d <= 180) return STAGNANT_BUCKETS.get(4);
        return STAGNANT_BUCKETS.get(5);
    }

    /**
     * 物料库存聚合查询：按（仓库×物料）聚合为一行，展示良品/不良数量与物料档案，供「物料库存情况」详情页使用。
     * <p>与 {@link #productStockPage} 对称，差异只有两处：
     * ①统计 material_id 非空（成品统计 product_id 非空）；
     * ②品质走 {@link QualityType} —— **只有 GOOD/DEFECT 两档**（物料没有成品的 A/B/C/待整理，别照抄五档）。</p>
     * <p>不影响 /page（明细结构，供其他页面使用）。</p>
     */
    @GetMapping("/material-stock/page")
    public R<Page<Map<String, Object>>> materialStockPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) List<Long> warehouseIds,
            @RequestParam(required = false) Long materialTypeId,
            @RequestParam(required = false) Long materialId,
            @RequestParam(required = false) String materialName) {

        Set<Long> whFilter = buildWarehouseFilter(warehouseId, warehouseIds);
        LambdaQueryWrapper<WarehouseStock> qw = new LambdaQueryWrapper<WarehouseStock>()
                .in(!whFilter.isEmpty(), WarehouseStock::getWarehouseId, whFilter)
                .eq(materialId != null, WarehouseStock::getMaterialId, materialId)
                .isNotNull(WarehouseStock::getMaterialId);

        Set<Long> filteredMaterialIds = buildMaterialIdFilter(materialTypeId, materialName);
        if (filteredMaterialIds != null) {
            if (filteredMaterialIds.isEmpty()) {
                Page<Map<String, Object>> empty = new Page<>(pageNum, pageSize, 0);
                empty.setRecords(Collections.emptyList());
                return R.ok(empty);
            }
            qw.in(WarehouseStock::getMaterialId, filteredMaterialIds);
        }

        List<WarehouseStock> all = stockMapper.selectList(qw);

        // 聚合：键 = 仓库ID + 物料ID + 库存形态（2026-09-25 物料形态化：MATERIAL_REPAIR 在厂行不得混入良品/不良）
        Map<String, Map<String, Object>> agg = new LinkedHashMap<>();
        Set<Long> whIds = new HashSet<>();
        Set<Long> mIds = new HashSet<>();
        for (WarehouseStock s : all) {
            Long whId = s.getWarehouseId();
            Long mId = s.getMaterialId();
            if (whId == null || mId == null) continue;
            whIds.add(whId);
            mIds.add(mId);
            String form = s.getStockForm() != null && !s.getStockForm().isBlank()
                    ? s.getStockForm() : WarehouseStock.FORM_MATERIAL;
            String key = whId + "_" + mId + "_" + form;
            Map<String, Object> row = agg.computeIfAbsent(key, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("warehouseId", whId);
                m.put("materialId", mId);
                m.put("stockForm", form);
                m.put("qtyGood", BigDecimal.ZERO);
                m.put("qtyDefect", BigDecimal.ZERO);
                m.put("qtyRepairOnSite", BigDecimal.ZERO);
                return m;
            });
            BigDecimal q = s.getQuantity() != null ? s.getQuantity() : BigDecimal.ZERO;
            if (!WarehouseStock.FORM_MATERIAL.equals(form)) {
                // 非常规形态（如 MATERIAL_REPAIR 送修在厂）：单独字段回传，不混入良品/不良
                row.put("qtyRepairOnSite", ((BigDecimal) row.get("qtyRepairOnSite")).add(q));
                continue;
            }
            // 按物料品质枚举显式归类：**不可用 else 兜底**，否则未知/脏值会被静默算进良品
            String qt = s.getQualityType();
            if (QualityType.DEFECT.getCode().equals(qt)) {
                row.put("qtyDefect", ((BigDecimal) row.get("qtyDefect")).add(q));
            } else if (QualityType.GOOD.getCode().equals(qt)) {
                row.put("qtyGood", ((BigDecimal) row.get("qtyGood")).add(q));
            }
        }

        // 批量补齐：仓库（含类别/工厂，供前端区分委外仓与自有仓）+ 物料档案（名称/单位/类型）
        Map<Long, Warehouse> whMap = new HashMap<>();
        if (!whIds.isEmpty()) warehouseMapper.selectBatchIds(whIds).forEach(w -> whMap.put(w.getId(), w));
        Map<Long, OutsourceMaterial> matMap = new HashMap<>();
        if (!mIds.isEmpty()) outsourceMaterialMapper.selectBatchIds(mIds).forEach(m -> matMap.put(m.getId(), m));
        Map<Long, MaterialType> mtMap = new HashMap<>();
        Set<Long> mtIds = matMap.values().stream().map(OutsourceMaterial::getMaterialTypeId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (!mtIds.isEmpty()) materialTypeMapper.selectBatchIds(mtIds).forEach(t -> mtMap.put(t.getId(), t));

        for (Map<String, Object> row : agg.values()) {
            Warehouse wh = whMap.get(row.get("warehouseId"));
            row.put("warehouseName", wh != null && wh.getWarehouseName() != null ? wh.getWarehouseName() : "");
            row.put("warehouseCategory", wh != null ? wh.getWarehouseCategory() : null);
            row.put("factoryId", wh != null ? wh.getFactoryId() : null);
            OutsourceMaterial m = matMap.get(row.get("materialId"));
            row.put("materialName", m != null && m.getMaterialName() != null ? m.getMaterialName() : "");
            row.put("unit", m != null && m.getUnit() != null ? m.getUnit() : "");
            Long mtId = m != null ? m.getMaterialTypeId() : null;
            MaterialType mt = mtId != null ? mtMap.get(mtId) : null;
            row.put("materialTypeId", mtId);
            row.put("materialTypeName", mt != null && mt.getTypeName() != null ? mt.getTypeName() : "");
            row.put("materialTypeSortOrder", mt != null && mt.getSortOrder() != null ? mt.getSortOrder() : 999);
            BigDecimal g = (BigDecimal) row.get("qtyGood");
            BigDecimal d = (BigDecimal) row.get("qtyDefect");
            row.put("totalQuantity", g.add(d));
            // 2026-10-09 库存金额：本行（仓库×物料）金额 = (良品+不良) × 单价。
            // 刻意**不含** qtyRepairOnSite（送修在厂）：它不在本页"总库存"里，计入会让"金额 ÷ 数量"对不上。
            BigDecimal mUnitCost = StockCosts.unitCost(m);
            row.put("unitCost", mUnitCost);
            row.put("stockAmount", StockCosts.amount(g.add(d), mUnitCost));
            row.put("costMissing", mUnitCost == null);
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
     * 物料库存情况：按【物料】聚合，每个物料一行，跨仓库汇总良品/不良、总库存与分布仓库数。
     * <p>供「物料库存情况」列表页使用；点进某物料看各仓库分布时，再用 material-stock/page?materialId= 查明细。</p>
     * <p>只统计物料（material_id 非空）；物料**没有安全库存字段** ⇒ 不做低库存预警（与成品侧的差异点之一）。</p>
     */
    @GetMapping("/material-summary/page")
    public R<Page<Map<String, Object>>> materialSummaryPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) List<Long> warehouseIds,
            @RequestParam(required = false) Long materialTypeId,
            @RequestParam(required = false) Long materialId,
            @RequestParam(required = false) String materialName) {

        Set<Long> whFilter = buildWarehouseFilter(warehouseId, warehouseIds);
        LambdaQueryWrapper<WarehouseStock> qw = new LambdaQueryWrapper<WarehouseStock>()
                .in(!whFilter.isEmpty(), WarehouseStock::getWarehouseId, whFilter)
                .eq(materialId != null, WarehouseStock::getMaterialId, materialId)
                .isNotNull(WarehouseStock::getMaterialId);

        Set<Long> filteredMaterialIds = buildMaterialIdFilter(materialTypeId, materialName);
        if (filteredMaterialIds != null) {
            if (filteredMaterialIds.isEmpty()) {
                Page<Map<String, Object>> empty = new Page<>(pageNum, pageSize, 0);
                empty.setRecords(Collections.emptyList());
                return R.ok(empty);
            }
            qw.in(WarehouseStock::getMaterialId, filteredMaterialIds);
        }

        List<WarehouseStock> all = stockMapper.selectList(qw);

        // 聚合：键 = 物料ID；同时记录每个物料涉及的仓库，用于统计分布仓库数
        Map<Long, Map<String, Object>> agg = new LinkedHashMap<>();
        Map<Long, Set<Long>> materialWarehouses = new HashMap<>();
        Set<Long> mIds = new HashSet<>();
        for (WarehouseStock s : all) {
            Long mId = s.getMaterialId();
            if (mId == null) continue;
            mIds.add(mId);
            if (s.getWarehouseId() != null) {
                materialWarehouses.computeIfAbsent(mId, k -> new HashSet<>()).add(s.getWarehouseId());
            }
            Map<String, Object> row = agg.computeIfAbsent(mId, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("materialId", mId);
                m.put("qtyGood", BigDecimal.ZERO);
                m.put("qtyDefect", BigDecimal.ZERO);
                return m;
            });
            BigDecimal q = s.getQuantity() != null ? s.getQuantity() : BigDecimal.ZERO;
            // 按物料品质枚举显式归类（同 material-stock/page：不可用 else 兜底）
            String qt = s.getQualityType();
            if (QualityType.DEFECT.getCode().equals(qt)) {
                row.put("qtyDefect", ((BigDecimal) row.get("qtyDefect")).add(q));
            } else if (QualityType.GOOD.getCode().equals(qt)) {
                row.put("qtyGood", ((BigDecimal) row.get("qtyGood")).add(q));
            }
        }

        Map<Long, OutsourceMaterial> matMap = new HashMap<>();
        if (!mIds.isEmpty()) outsourceMaterialMapper.selectBatchIds(mIds).forEach(m -> matMap.put(m.getId(), m));
        Map<Long, MaterialType> mtMap = new HashMap<>();
        Set<Long> mtIds = matMap.values().stream().map(OutsourceMaterial::getMaterialTypeId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (!mtIds.isEmpty()) materialTypeMapper.selectBatchIds(mtIds).forEach(t -> mtMap.put(t.getId(), t));

        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> row : agg.values()) {
            Long mid = (Long) row.get("materialId");
            OutsourceMaterial m = matMap.get(mid);
            Long mtId = m != null ? m.getMaterialTypeId() : null;
            MaterialType mt = mtId != null ? mtMap.get(mtId) : null;
            BigDecimal g = (BigDecimal) row.get("qtyGood");
            BigDecimal d = (BigDecimal) row.get("qtyDefect");
            row.put("materialName", m != null && m.getMaterialName() != null ? m.getMaterialName() : "");
            row.put("unit", m != null && m.getUnit() != null ? m.getUnit() : "");
            row.put("materialTypeId", mtId);
            row.put("materialTypeName", mt != null && mt.getTypeName() != null ? mt.getTypeName() : "");
            row.put("materialTypeSortOrder", mt != null && mt.getSortOrder() != null ? mt.getSortOrder() : 999);
            row.put("totalQuantity", g.add(d));
            row.put("warehouseCount", materialWarehouses.getOrDefault(mid, Collections.emptySet()).size());
            // 2026-10-09 库存金额：本行（跨仓库汇总）金额 = 总库存 × 单价（同 material-stock 口径，不含送修在厂）
            BigDecimal mUnitCost = StockCosts.unitCost(m);
            row.put("unitCost", mUnitCost);
            row.put("stockAmount", StockCosts.amount(g.add(d), mUnitCost));
            row.put("costMissing", mUnitCost == null);
            list.add(row);
        }

        // 库存多的排前面，便于优先关注积压物料；同库存按「物料类型排序位 → 物料名称」稳定排序
        list.sort(Comparator
                .comparing((Map<String, Object> r) -> (BigDecimal) r.get("totalQuantity")).reversed()
                .thenComparing(r -> (Integer) r.getOrDefault("materialTypeSortOrder", 999))
                .thenComparing(r -> String.valueOf(r.get("materialName"))));

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
     * 库存金额汇总（2026-10-09 · 用户需求「物料仓和成品仓库需要库存金额」）。
     *
     * <p>一次给出「成品 / 物料 / 合计」三档金额与数量、成本未维护的数量与 SKU 数，以及**按仓库**的明细，
     * 供①成品库存列表与②物料库存列表的合计栏、③仓库详情页、④首页看板【库存金额】卡片共用
     * —— 口径唯一来源见 {@link StockCosts}，避免各处自己写口径而说法不一。</p>
     *
     * <p>数量口径与列表页面「总库存」严格一致：成品取 A/B/C/不良/待整理；物料取常规形态下的良品/不良
     * （「送修在厂」不计入）。范围含自有仓与委外仓（用户 2026-10-09 口径：都要做）。</p>
     */
    @GetMapping("/amount-summary")
    public R<Map<String, Object>> amountSummary(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) List<Long> warehouseIds) {

        Set<Long> whFilter = buildWarehouseFilter(warehouseId, warehouseIds);
        List<WarehouseStock> all = stockMapper.selectList(new LambdaQueryWrapper<WarehouseStock>()
                .in(!whFilter.isEmpty(), WarehouseStock::getWarehouseId, whFilter));

        Set<Long> pIds = new HashSet<>();
        Set<Long> mIds = new HashSet<>();
        Set<Long> whIds = new HashSet<>();
        for (WarehouseStock s : all) {
            if (s.getProductId() != null) pIds.add(s.getProductId());
            if (s.getMaterialId() != null) mIds.add(s.getMaterialId());
            if (s.getWarehouseId() != null) whIds.add(s.getWarehouseId());
        }
        Map<Long, Product> pMap = new HashMap<>();
        if (!pIds.isEmpty()) productMapper.selectBatchIds(pIds).forEach(p -> pMap.put(p.getId(), p));
        Map<Long, OutsourceMaterial> mMap = new HashMap<>();
        if (!mIds.isEmpty()) outsourceMaterialMapper.selectBatchIds(mIds).forEach(m -> mMap.put(m.getId(), m));
        Map<Long, Warehouse> wMap = new HashMap<>();
        if (!whIds.isEmpty()) warehouseMapper.selectBatchIds(whIds).forEach(w -> wMap.put(w.getId(), w));

        BigDecimal productAmount = BigDecimal.ZERO;
        BigDecimal materialAmount = BigDecimal.ZERO;
        BigDecimal productQty = BigDecimal.ZERO;
        BigDecimal materialQty = BigDecimal.ZERO;
        BigDecimal productMissingQty = BigDecimal.ZERO;
        BigDecimal materialMissingQty = BigDecimal.ZERO;
        Set<Long> productMissingSkus = new HashSet<>();
        Set<Long> materialMissingSkus = new HashSet<>();
        Map<Long, Map<String, Object>> byWh = new LinkedHashMap<>();

        for (WarehouseStock s : all) {
            boolean isProduct = s.getProductId() != null;
            boolean isMaterial = !isProduct && s.getMaterialId() != null;
            if (!isProduct && !isMaterial) continue;

            // 数量口径：与列表页「总库存」一致（未知/脏品质不静默计入，避免金额虚增）
            if (isProduct) {
                if (ProductQualityType.of(s.getQualityType()) == null) continue;
            } else {
                String form = s.getStockForm() != null && !s.getStockForm().isBlank()
                        ? s.getStockForm() : WarehouseStock.FORM_MATERIAL;
                if (!WarehouseStock.FORM_MATERIAL.equals(form)) continue;
                String qt = s.getQualityType();
                if (!QualityType.GOOD.getCode().equals(qt) && !QualityType.DEFECT.getCode().equals(qt)) continue;
            }

            BigDecimal q = s.getQuantity() != null ? s.getQuantity() : BigDecimal.ZERO;
            BigDecimal unitCost = isProduct ? StockCosts.unitCost(pMap.get(s.getProductId()))
                                            : StockCosts.unitCost(mMap.get(s.getMaterialId()));
            BigDecimal amt = StockCosts.amount(q, unitCost);

            Long whId = s.getWarehouseId() != null ? s.getWarehouseId() : -1L;
            Map<String, Object> w = byWh.computeIfAbsent(whId, k -> {
                Warehouse wh = wMap.get(k);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("warehouseId", k == -1L ? null : k);
                row.put("warehouseName", wh != null && wh.getWarehouseName() != null ? wh.getWarehouseName() : "");
                row.put("warehouseCategory", wh != null ? wh.getWarehouseCategory() : null);
                row.put("warehouseType", wh != null ? wh.getWarehouseType() : null);
                row.put("factoryId", wh != null ? wh.getFactoryId() : null);
                row.put("productAmount", BigDecimal.ZERO);
                row.put("materialAmount", BigDecimal.ZERO);
                row.put("totalAmount", BigDecimal.ZERO);
                return row;
            });

            if (isProduct) {
                productQty = productQty.add(q);
                productAmount = productAmount.add(amt);
                if (unitCost == null) {
                    productMissingQty = productMissingQty.add(q);
                    productMissingSkus.add(s.getProductId());
                }
                w.put("productAmount", ((BigDecimal) w.get("productAmount")).add(amt));
            } else {
                materialQty = materialQty.add(q);
                materialAmount = materialAmount.add(amt);
                if (unitCost == null) {
                    materialMissingQty = materialMissingQty.add(q);
                    materialMissingSkus.add(s.getMaterialId());
                }
                w.put("materialAmount", ((BigDecimal) w.get("materialAmount")).add(amt));
            }
            w.put("totalAmount", ((BigDecimal) w.get("productAmount")).add((BigDecimal) w.get("materialAmount")));
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("productAmount", StockCosts.scale(productAmount));
        out.put("materialAmount", StockCosts.scale(materialAmount));
        out.put("totalAmount", StockCosts.scale(productAmount.add(materialAmount)));
        out.put("productQty", productQty);
        out.put("materialQty", materialQty);
        out.put("productCostMissingQty", productMissingQty);
        out.put("materialCostMissingQty", materialMissingQty);
        out.put("productCostMissingSkuCount", productMissingSkus.size());
        out.put("materialCostMissingSkuCount", materialMissingSkus.size());
        out.put("byWarehouse", new ArrayList<>(byWh.values()));
        out.put("caliber", StockCosts.CALIBER);
        return R.ok(out);
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

    /**
     * 物料筛选：物料类型 + 名称关键字，两个条件同时存在时取交集。
     * 返回 null 表示不筛选；返回空集合表示查无结果（调用方直接返回空页，避免 in() 空集合报错）。
     */
    private Set<Long> buildMaterialIdFilter(Long materialTypeId, String materialName) {
        Set<Long> filteredMaterialIds = null;
        if (materialTypeId != null) {
            filteredMaterialIds = outsourceMaterialMapper.selectList(
                    new LambdaQueryWrapper<OutsourceMaterial>()
                            .eq(OutsourceMaterial::getMaterialTypeId, materialTypeId))
                    .stream().map(OutsourceMaterial::getId).collect(Collectors.toSet());
        }
        if (materialName != null && !materialName.isBlank()) {
            Set<Long> nameIds = outsourceMaterialMapper.selectList(
                    new LambdaQueryWrapper<OutsourceMaterial>()
                            .like(OutsourceMaterial::getMaterialName, materialName))
                    .stream().map(OutsourceMaterial::getId).collect(Collectors.toSet());
            filteredMaterialIds = (filteredMaterialIds == null)
                    ? nameIds
                    : filteredMaterialIds.stream().filter(nameIds::contains).collect(Collectors.toSet());
        }
        return filteredMaterialIds;
    }

    /** 库存流水追溯（stockType: PRODUCT=成品流水 / MATERIAL=物料流水，不传则全量） */
    @GetMapping("/log")
    public R<Page<WarehouseStockLog>> log(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long productId,
            // 2026-09-22：物料流水列表页需要按物料精确筛选，原先只有 productId（物料行只能整表翻）
            @RequestParam(required = false) Long materialId,
            @RequestParam(required = false) String changeType,
            @RequestParam(required = false) String relatedBillNo,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String stockType) {
        boolean singleProduct = warehouseId != null && productId != null;

        LambdaQueryWrapper<WarehouseStockLog> baseWrapper = new LambdaQueryWrapper<WarehouseStockLog>()
                .eq(warehouseId != null, WarehouseStockLog::getWarehouseId, warehouseId)
                .eq(productId != null, WarehouseStockLog::getProductId, productId)
                .eq(materialId != null, WarehouseStockLog::getMaterialId, materialId)
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
                // 详情跳转目标ID = 关联单据ID。
                // 2026-10-09：销售出库模块已下线，原「出库单 → 其关联销售单」的特例随之移除
                // （该特例本就无数据：全库 related_bill_type='SALE_OUTBOUND' 的流水 0 条，删掉不改变任何现存流水的跳转行为）
                Long billId = log.getRelatedBillId();
                if (billId != null) {
                    log.setRelatedBillDetailId(billId);
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
        // 2026-10-09 库存金额：本接口原来只留了物料名/类型名，金额需要物料对象（成本价/最近进价/手填单价）
        Map<Long, OutsourceMaterial> materialObjMap = new HashMap<>();
        Map<Long, Product> productObjMap = new HashMap<>();
        // F7-132（2026-09-20）：另需「物料ID → 类型ID / 类型 sortOrder」。原文只回类型名，导致前端只能用
        // **中文类型名**做排序优先级（`['玻璃','驱动IC']`，改名即静默失效）⇒ 这里把映射提到外层作用域，返回时一并补字段。
        Map<Long, Long> matMaterialTypeMap = new HashMap<>();
        Map<Long, Integer> btSortMap = new HashMap<>();
        if (!materialIds.isEmpty()) {
            List<OutsourceMaterial> materials = outsourceMaterialMapper.selectBatchIds(materialIds);
            for (OutsourceMaterial m : materials) {
                materialNameMap.put(m.getId(), m.getMaterialName());
                materialObjMap.put(m.getId(), m);
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
                productObjMap.put(p.getId(), p);
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
            // 2026-09-25 P0-3：库存形态（MATERIAL / PRODUCT_DEFECT / PRODUCT_REPAIR）随行返回，
            // 前端委外仓库存页据此把「物料 / 成品（加工退货）/ 成品（维修退货）」分组展示（纯新增字段，向后兼容）。
            m.put("stockForm", s.getStockForm());
            m.put("quantity", s.getQuantity());
            if (s.getProductId() != null) {
                m.put("productName", productNameMap.getOrDefault(s.getProductId(), ""));
                m.put("sku", productSkuMap.getOrDefault(s.getProductId(), ""));
                // 2026-10-09（§7.26 幽灵字段）：补 unit —— 仓库详情页的「单位」列一直读 `m.unit`，
                // 而本接口原先**不回传**该键（`row.put("unit", …)` 只写在其它几个兄弟端点里）
                // ⇒ 委外仓详情的「单位」列恒空。口径与其它端点一致：取主数据的 unit，空则给空串。
                Product pu = productObjMap.get(s.getProductId());
                m.put("unit", pu != null && pu.getUnit() != null ? pu.getUnit() : "");
            }
            if (s.getMaterialId() != null) {
                m.put("materialName", materialNameMap.getOrDefault(s.getMaterialId(), ""));
                m.put("materialTypeName", materialTypeNameMap.getOrDefault(s.getMaterialId(), ""));
                // 同上：物料行的「单位」取自 outsource_material.unit
                OutsourceMaterial mu = materialObjMap.get(s.getMaterialId());
                m.put("unit", mu != null && mu.getUnit() != null ? mu.getUnit() : "");
                // F7-132（2026-09-20）：补类型 id 与 sortOrder，供前端做"优先类型置顶"排序，
                // 不再依赖可改的中文类型名（纯新增字段，向后兼容）
                Long mtId = matMaterialTypeMap.get(s.getMaterialId());
                m.put("materialTypeId", mtId);
                m.put("materialTypeSortOrder", mtId != null ? btSortMap.getOrDefault(mtId, 999) : null);
            }
            // 2026-10-09 库存金额：本接口是仓库详情（自有仓/委外仓共用）的取数口 —— 逐行给单价与行金额。
            // 物料行只在常规形态（MATERIAL）下计量（与列表口径一致）；「送修在厂」等形态记 0 且不标"成本未维护"。
            boolean matRow = s.getMaterialId() != null;
            String rowForm = s.getStockForm() != null && !s.getStockForm().isBlank()
                    ? s.getStockForm() : WarehouseStock.FORM_MATERIAL;
            BigDecimal rowUnitCost = matRow ? StockCosts.unitCost(materialObjMap.get(s.getMaterialId()))
                                            : StockCosts.unitCost(productObjMap.get(s.getProductId()));
            boolean counted = !matRow || WarehouseStock.FORM_MATERIAL.equals(rowForm);
            BigDecimal amtQty = counted && s.getQuantity() != null ? s.getQuantity() : BigDecimal.ZERO;
            m.put("unitCost", rowUnitCost);
            m.put("stockAmount", StockCosts.amount(amtQty, rowUnitCost));
            m.put("costMissing", counted && rowUnitCost == null);
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
