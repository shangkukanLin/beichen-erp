package com.beichen.erp.sale.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.dev33.satoken.stp.StpUtil;
import com.beichen.erp.auth.entity.User;
import com.beichen.erp.auth.mapper.UserMapper;
import com.beichen.erp.common.BillPrefix;
import com.beichen.erp.common.BillNoSeq;
import com.beichen.erp.common.DocStatus;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.customer.entity.Customer;
import com.beichen.erp.customer.mapper.CustomerMapper;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.common.DocStatusGuard;
import com.beichen.erp.finance.common.SettlementStatus;
import com.beichen.erp.finance.common.SourceBillType;
import com.beichen.erp.finance.entity.FinanceReceivable;
import com.beichen.erp.finance.mapper.FinanceReceivableMapper;
import com.beichen.erp.finance.service.ReceivableHelper;
import com.beichen.erp.inventory.common.RelatedBillType;
import com.beichen.erp.inventory.common.StockChangeType;
import com.beichen.erp.material.common.ProductQualityType;
import com.beichen.erp.material.entity.Product;
import com.beichen.erp.material.mapper.ProductMapper;
import com.beichen.erp.material.service.ProductService;
import com.beichen.erp.sale.common.AfterSaleSourceType;
import com.beichen.erp.sale.entity.AfterSalePending;
import com.beichen.erp.sale.entity.ReturnSort;
import com.beichen.erp.sale.entity.ReturnSortItem;
import com.beichen.erp.sale.mapper.AfterSalePendingMapper;
import com.beichen.erp.sale.mapper.ReturnSortItemMapper;
import com.beichen.erp.sale.mapper.ReturnSortMapper;
import com.beichen.erp.sale.service.ReturnSortService;
import com.beichen.erp.warehouse.common.WarehouseCategory;
import com.beichen.erp.warehouse.common.WarehouseType;
import com.beichen.erp.warehouse.entity.Warehouse;
import com.beichen.erp.warehouse.entity.WarehouseStock;
import com.beichen.erp.warehouse.entity.WarehouseStockLog;
import com.beichen.erp.warehouse.mapper.WarehouseMapper;
import com.beichen.erp.warehouse.mapper.WarehouseStockLogMapper;
import com.beichen.erp.warehouse.mapper.WarehouseStockMapper;
import com.beichen.erp.warehouse.service.WarehouseStockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 退货整理：销售退货先入售后仓(待整理品/待整理)，再按 A/B/C/不良品 分选后分别入库（A/B/C 入成品仓）。
 */
@Service
@RequiredArgsConstructor
public class ReturnSortServiceImpl implements ReturnSortService {

    private final ReturnSortMapper rsMapper;
    /** 整理人（「谁操作的就是谁整理的」）要按当前登录用户查名字，故注入用户 mapper */
    private final UserMapper userMapper;
    private final ReturnSortItemMapper itemMapper;
    private final WarehouseStockService stockService;
    private final WarehouseStockMapper stockMapper;
    private final WarehouseMapper warehouseMapper;
    private final ProductMapper productMapper;
    private final ProductService productService;
    private final AfterSalePendingMapper afterSalePendingMapper;
    private final CustomerMapper customerMapper;
    private final FinanceReceivableMapper financeReceivableMapper;
    private final ReceivableHelper receivableHelper;
    private final WarehouseStockLogMapper stockLogMapper;

    /** 待整理实物停留预警阈值（天）：与前端 form.vue 的 STAY_ALERT_DAYS 同值，随总览一起下发 */
    private static final int STAY_ALERT_DAYS = 3;
    /** 待整理行的三种状态（见 pendingRows） */
    private static final String STATUS_SORTABLE = "SORTABLE";
    private static final String STATUS_SHORTAGE = "SHORTAGE";
    private static final String STATUS_CLEARED = "CLEARED";
    /** 允许的默认分选品质（批量建草稿时整批预置） */
    private static final Set<String> SORT_QUALITIES = new LinkedHashSet<>(Arrays.asList(
            ProductQualityType.A.getCode(), ProductQualityType.B.getCode(),
            ProductQualityType.C.getCode(), ProductQualityType.DEFECT.getCode()));

    @Override
    public Page<Map<String, Object>> page(String status, Long warehouseId, int pageNum, int pageSize) {
        LambdaQueryWrapper<ReturnSort> w = new LambdaQueryWrapper<ReturnSort>()
                .eq(status != null && !status.isBlank(), ReturnSort::getStatus, status)
                .eq(warehouseId != null, ReturnSort::getWarehouseId, warehouseId)
                .orderByDesc(ReturnSort::getId);
        Page<ReturnSort> raw = rsMapper.selectPage(new Page<>(pageNum, pageSize), w);
        // 整理概况（产品名 + 分选结果）为非表字段，按本页单据批量查明细后拼接，避免逐条查库
        List<ReturnSortItem> allItems = raw.getRecords().isEmpty() ? Collections.emptyList()
                : itemMapper.selectList(new LambdaQueryWrapper<ReturnSortItem>()
                        .in(ReturnSortItem::getSortId,
                                raw.getRecords().stream().map(ReturnSort::getId).collect(Collectors.toList())));
        fillItemDisplay(allItems);
        Map<Long, List<ReturnSortItem>> itemsMap = allItems.stream()
                .collect(Collectors.groupingBy(ReturnSortItem::getSortId));
        Page<Map<String, Object>> res = new Page<>(pageNum, pageSize, raw.getTotal());
        res.setRecords(raw.getRecords().stream().map(o -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", o.getId()); m.put("code", o.getCode());
            m.put("warehouseId", o.getWarehouseId());
            m.put("sortDate", o.getSortDate()); m.put("status", o.getStatus());
            m.put("targetWarehouseA", o.getTargetWarehouseA());
            m.put("targetWarehouseB", o.getTargetWarehouseB());
            m.put("targetWarehouseC", o.getTargetWarehouseC());
            m.put("targetWarehouseDefect", o.getTargetWarehouseDefect());
            m.put("lossAmount", o.getLossAmount());
            m.put("lossRemark", o.getLossRemark());
            m.put("remark", o.getRemark()); m.put("createTime", o.getCreateTime());
            // 整理概况：产品名 整理合计（A x/B y/C z/不良 d），多条明细用「；」连接
            List<ReturnSortItem> its = itemsMap.getOrDefault(o.getId(), Collections.emptyList());
            String summary = its.stream()
                    .map(it -> String.format("%s 整理%s（A%s/B%s/C%s/不良%s）",
                            it.getProductName() != null ? it.getProductName() : "",
                            nz(it.getQtyA()).add(nz(it.getQtyB())).add(nz(it.getQtyC())).add(nz(it.getQtyDefect())).stripTrailingZeros().toPlainString(),
                            nz(it.getQtyA()).stripTrailingZeros().toPlainString(),
                            nz(it.getQtyB()).stripTrailingZeros().toPlainString(),
                            nz(it.getQtyC()).stripTrailingZeros().toPlainString(),
                            nz(it.getQtyDefect()).stripTrailingZeros().toPlainString()))
                    .collect(Collectors.joining("；"));
            m.put("sortSummary", summary);
            return m;
        }).toList());
        return res;
    }

    @Override
    public ReturnSort getById(Long id) { return rsMapper.selectById(id); }

    /** 回填明细展示字段：SKU、来源追溯（来源单据/日期/产品名存于售后待整理批次，明细表不落库），按 pendingId 批量取 */
    private void fillItemDisplay(List<ReturnSortItem> items) {
        if (items == null || items.isEmpty()) return;
        // SKU（非表字段）
        productService.fillSku(items, ReturnSortItem::getProductId, ReturnSortItem::setSku);
        List<Long> pendingIds = items.stream().map(ReturnSortItem::getPendingId)
                .filter(Objects::nonNull).collect(Collectors.toList());
        Map<Long, AfterSalePending> pendingMap = pendingIds.isEmpty() ? Collections.emptyMap()
                : afterSalePendingMapper.selectBatchIds(pendingIds).stream()
                        .collect(Collectors.toMap(AfterSalePending::getId, p -> p, (a, b) -> a));
        for (ReturnSortItem it : items) {
            AfterSalePending p = it.getPendingId() != null ? pendingMap.get(it.getPendingId()) : null;
            if (p == null) continue;
            it.setSourceType(p.getSourceType());
            it.setSourceCode(p.getSourceCode());
            it.setSourceId(p.getSourceId());
            it.setSourceDate(p.getSourceDate() != null ? p.getSourceDate().toString() : "");
            if (it.getProductName() == null || it.getProductName().isBlank()) it.setProductName(p.getProductName());
        }
    }

    @Override
    public List<ReturnSortItem> getItems(Long sortId) {
        List<ReturnSortItem> items = itemMapper.selectList(new LambdaQueryWrapper<ReturnSortItem>()
                .eq(ReturnSortItem::getSortId, sortId));
        // 回填 SKU 与来源追溯
        fillItemDisplay(items);
        return items;
    }

    @Override
    /**
     * 售后仓待整理库存清单（新增整理单时带出）。
     * <p>库存按 (仓库,产品,品质) 聚合、不记录来源，无法直接追溯。此处按批次ID 升序(FIFO)
     * 将售后仓待整理库存分配回各「售后待整理批次」（after_sale_pending，销售退货单与销售换货单共用入口），
     * 使每行都能追溯到具体来源单据，并给出「原数量 / 已整理数量 / 本次可整理数量」。</p>
     *
     * <p>口径与 {@link #pendingOverview(boolean)} 完全一致（同一 {@link #pendingRows}），此处只保留
     * 「本次可整理数量 &gt; 0」的行 —— 表单带入不可整理的行（已整理完 / 实物不足）没有意义。</p>
     */
    public List<Map<String, Object>> defectStock(Long warehouseId) {
        if (warehouseId == null) throw new BusinessException("仓库不能为空");
        return pendingRows(List.of(warehouseId), false).stream()
                .filter(r -> nz((BigDecimal) r.get("quantity")).compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> pendingOverview(boolean includeCleared) {
        // 只覆盖**自有成品仓**（与新增页源仓库下拉同口径：INVENTORY + FINISHED）
        List<Warehouse> whs = warehouseMapper.selectList(new LambdaQueryWrapper<Warehouse>()
                .eq(Warehouse::getWarehouseCategory, WarehouseCategory.INVENTORY.getCode())
                .eq(Warehouse::getWarehouseType, WarehouseType.FINISHED.getCode())
                .orderByAsc(Warehouse::getId));
        Map<Long, String> whNames = new LinkedHashMap<>();
        for (Warehouse w : whs) whNames.put(w.getId(), w.getWarehouseName());

        List<Map<String, Object>> rows = whNames.isEmpty() ? Collections.emptyList()
                : pendingRows(whNames.keySet(), includeCleared);

        // 按仓分组（仓库ID升序，与仓档列表一致；没有批次的仓不出现，避免总览被空仓淹没）
        Map<Long, List<Map<String, Object>>> byWh = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            byWh.computeIfAbsent((Long) r.get("warehouseId"), k -> new ArrayList<>()).add(r);
        }

        List<Map<String, Object>> groups = new ArrayList<>();
        int sCnt = 0, shCnt = 0, cCnt = 0, odCnt = 0;
        BigDecimal sortableQty = BigDecimal.ZERO, remainQty = BigDecimal.ZERO;
        for (Map.Entry<Long, List<Map<String, Object>>> e : byWh.entrySet()) {
            List<Map<String, Object>> rs = e.getValue();
            int sc = 0, shc = 0, cc = 0, od = 0, oldest = 0;
            BigDecimal sq = BigDecimal.ZERO, rq = BigDecimal.ZERO;
            for (Map<String, Object> r : rs) {
                String st = String.valueOf(r.get("status"));
                int days = ((Number) r.get("stayDays")).intValue();
                if (days > oldest) oldest = days;
                if (Boolean.TRUE.equals(r.get("overdue"))) od++;
                if (STATUS_SORTABLE.equals(st)) {
                    sc++;
                    sq = sq.add(nz((BigDecimal) r.get("quantity")));
                } else if (STATUS_SHORTAGE.equals(st)) {
                    shc++;
                } else {
                    cc++;
                }
                rq = rq.add(nz((BigDecimal) r.get("remainQuantity")));
            }
            Map<String, Object> g = new LinkedHashMap<>();
            g.put("warehouseId", e.getKey());
            g.put("warehouseName", whNames.getOrDefault(e.getKey(), ""));
            g.put("rows", rs);
            g.put("batchCount", rs.size());
            g.put("sortableCount", sc);
            g.put("shortageCount", shc);
            g.put("clearedCount", cc);
            g.put("sortableQuantity", sq);
            g.put("remainQuantity", rq);
            g.put("oldestStayDays", oldest);
            g.put("overdueCount", od);
            groups.add(g);
            sCnt += sc; shCnt += shc; cCnt += cc; odCnt += od;
            sortableQty = sortableQty.add(sq);
            remainQty = remainQty.add(rq);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("warehouseCount", groups.size());
        summary.put("batchCount", rows.size());
        summary.put("sortableCount", sCnt);
        summary.put("shortageCount", shCnt);
        summary.put("clearedCount", cCnt);
        summary.put("sortableQuantity", sortableQty);
        summary.put("remainQuantity", remainQty);
        summary.put("overdueCount", odCnt);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("asOf", LocalDate.now().toString());
        res.put("stayAlertDays", STAY_ALERT_DAYS);
        res.put("warehouses", groups);
        res.put("summary", summary);
        return res;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> batchCreateDrafts(List<Long> pendingIds, ReturnSort template, String defaultQuality) {
        if (pendingIds == null || pendingIds.isEmpty()) throw new BusinessException("请先勾选需要整理的来源批次");
        if (template.getWarehouseId() != null)
            throw new BusinessException("批量生成不支持指定源仓库：源仓库由勾选的批次决定");
        String quality = defaultQuality == null || defaultQuality.isBlank()
                ? ProductQualityType.A.getCode() : defaultQuality;
        if (!SORT_QUALITIES.contains(quality)) throw new BusinessException("默认分选品质不合法：" + quality);

        List<Long> ids = pendingIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (ids.isEmpty()) throw new BusinessException("请先勾选需要整理的来源批次");
        Map<Long, AfterSalePending> pm = afterSalePendingMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(AfterSalePending::getId, p -> p, (a, b) -> a));

        // 可整理量必须与总览**同一算法**（pendingRows 内含 FIFO 分配）：总览显示多少，批量就生成多少
        Set<Long> whIds = ids.stream().map(pm::get).filter(Objects::nonNull)
                .map(AfterSalePending::getWarehouseId).filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, Map<String, Object>> rowByPending = new LinkedHashMap<>();
        for (Map<String, Object> r : pendingRows(whIds, true)) rowByPending.put((Long) r.get("pendingId"), r);

        // 按 (仓库, 客户) 分组：一张整理单只能对应一个客户（折损收款才有唯一对象），也便于按仓分单作业
        Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        List<Map<String, Object>> skipped = new ArrayList<>();
        for (Long pid : ids) {
            AfterSalePending p = pm.get(pid);
            if (p == null) { skipped.add(skipRow(pid, null, "来源批次不存在")); continue; }
            Map<String, Object> r = rowByPending.get(pid);
            BigDecimal alloc = r == null ? BigDecimal.ZERO : nz((BigDecimal) r.get("quantity"));
            if (alloc.compareTo(BigDecimal.ZERO) <= 0) {
                BigDecimal remain = nz(p.getQuantity()).subtract(nz(p.getSortedQuantity()));
                skipped.add(skipRow(pid, p.getSourceCode(), remain.compareTo(BigDecimal.ZERO) <= 0
                        ? "该批次已整理完"
                        : "成品仓待整理实物不足（FIFO 已分配给更早批次）"));
                continue;
            }
            groups.computeIfAbsent(p.getWarehouseId() + "#" + (p.getCustomerId() == null ? "-" : p.getCustomerId()),
                    k -> new ArrayList<>()).add(r);
        }
        if (groups.isEmpty())
            throw new BusinessException("勾选的批次当前都没有可整理数量（可能已整理完或实物不足），请刷新「待整理」列表后重试");

        // 先整批构造 + 校验，再落库：避免中途失败留下半套草稿
        List<ReturnSort> drafts = new ArrayList<>();
        List<List<ReturnSortItem>> draftItems = new ArrayList<>();
        ProductQualityType qt = ProductQualityType.of(quality);
        for (Map.Entry<String, List<Map<String, Object>>> e : groups.entrySet()) {
            List<Map<String, Object>> rs = e.getValue();
            ReturnSort s = new ReturnSort();
            s.setWarehouseId(((Number) rs.get(0).get("warehouseId")).longValue());
            s.setSortDate(template.getSortDate() != null ? template.getSortDate() : LocalDate.now());
            s.setTargetWarehouseA(template.getTargetWarehouseA());
            s.setTargetWarehouseB(template.getTargetWarehouseB());
            s.setTargetWarehouseC(template.getTargetWarehouseC());
            s.setTargetWarehouseDefect(template.getTargetWarehouseDefect());
            s.setLossAmount(template.getLossAmount() != null ? template.getLossAmount() : BigDecimal.ZERO);
            s.setLossRemark(template.getLossRemark());
            String auto = "批量生成：" + rs.size() + " 个来源批次，默认按"
                    + (qt != null ? qt.getLabel() : quality) + "入库";
            s.setRemark(template.getRemark() != null && !template.getRemark().isBlank()
                    ? template.getRemark() : auto);

            List<ReturnSortItem> items = new ArrayList<>();
            for (Map<String, Object> r : rs) {
                BigDecimal q = nz((BigDecimal) r.get("quantity"));
                ReturnSortItem it = new ReturnSortItem();
                it.setPendingId((Long) r.get("pendingId"));
                it.setProductId((Long) r.get("productId"));
                it.setProductName((String) r.get("productName"));
                it.setUnit((String) r.get("unit"));
                it.setTotalQuantity(q);
                it.setQtyA(ProductQualityType.A.getCode().equals(quality) ? q : BigDecimal.ZERO);
                it.setQtyB(ProductQualityType.B.getCode().equals(quality) ? q : BigDecimal.ZERO);
                it.setQtyC(ProductQualityType.C.getCode().equals(quality) ? q : BigDecimal.ZERO);
                it.setQtyDefect(ProductQualityType.DEFECT.getCode().equals(quality) ? q : BigDecimal.ZERO);
                items.add(it);
            }
            validate(s, items);
            drafts.add(s);
            draftItems.add(items);
        }

        Map<Long, String> whNames = warehouseNames(drafts.stream().map(ReturnSort::getWarehouseId)
                .collect(Collectors.toList()));
        Map<Long, String> cusNames = customerNames(new ArrayList<>(pm.values()));
        List<Map<String, Object>> created = new ArrayList<>();
        for (int i = 0; i < drafts.size(); i++) {
            ReturnSort s = drafts.get(i);
            List<ReturnSortItem> items = draftItems.get(i);
            create(s, items); // 生成单号 + 置草稿态 + 落库（同事务）
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("id", s.getId());
            c.put("code", s.getCode());
            c.put("warehouseId", s.getWarehouseId());
            c.put("warehouseName", whNames.getOrDefault(s.getWarehouseId(), ""));
            // 分组键是 (仓库, 客户)，组内客户必然相同：取第一行批次上的客户即可代表本单
            AfterSalePending first = items.get(0).getPendingId() == null ? null : pm.get(items.get(0).getPendingId());
            Long cid = first == null ? null : first.getCustomerId();
            c.put("customerId", cid);
            c.put("customerName", cid == null ? "" : cusNames.getOrDefault(cid, ""));
            c.put("itemCount", items.size());
            c.put("quantity", items.stream().map(it -> nz(it.getTotalQuantity()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            created.add(c);
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("created", created);
        res.put("skipped", skipped);
        res.put("draftCount", created.size());
        return res;
    }

    /**
     * 待整理批次 × 待整理(PENDING)实物的 **FIFO 分配**（跨仓批量版）。
     *
     * <p>这是「待整理」的唯一口径来源：{@link #defectStock(Long)}（表单带出）、
     * {@link #pendingOverview(boolean)}（跨仓总览）、{@link #batchCreateDrafts}（批量建单）
     * 全部走这里，避免出现"总览显示 80 件、表单只能整理 50 件"的口径漂移。</p>
     *
     * <p>库存按 (仓库,产品,品质) 聚合、不记录来源，故按批次ID 升序把实物分配回各批次：每行给出
     * 原数量 / 已整理数量 / 剩余数量 / 本次可整理数量({@code quantity}) 与停留天数。状态三态：
     * {@code SORTABLE} 可整理 · {@code SHORTAGE} 实物不足（账实不符）· {@code CLEARED} 已整理完。</p>
     *
     * @param includeCleared 是否包含「已整理完」（剩余 ≤ 0）的批次
     */
    private List<Map<String, Object>> pendingRows(Collection<Long> warehouseIds, boolean includeCleared) {
        List<Map<String, Object>> res = new ArrayList<>();
        if (warehouseIds == null) return res;
        List<Long> ids = warehouseIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (ids.isEmpty()) return res;

        // 1) 各仓待整理(PENDING)实物，按 仓库+产品 聚合：决定每个仓每个产品实有多少可整理
        Map<String, BigDecimal> avail = new LinkedHashMap<>();
        for (WarehouseStock s : stockMapper.selectList(new LambdaQueryWrapper<WarehouseStock>()
                .in(WarehouseStock::getWarehouseId, ids)
                .eq(WarehouseStock::getQualityType, ProductQualityType.PENDING.getCode())
                .gt(WarehouseStock::getQuantity, BigDecimal.ZERO)
                .isNotNull(WarehouseStock::getProductId))) {
            if (s.getProductId() == null) continue;
            avail.merge(whProductKey(s.getWarehouseId(), s.getProductId()), nz(s.getQuantity()), BigDecimal::add);
        }

        // 2) 各仓待整理批次（FIFO：批次ID 升序）
        List<AfterSalePending> pendings = afterSalePendingMapper.selectList(
                new LambdaQueryWrapper<AfterSalePending>()
                        .in(AfterSalePending::getWarehouseId, ids)
                        .orderByAsc(AfterSalePending::getId));
        if (pendings.isEmpty()) return res;

        // 回填 SKU 与客户名（均批量，避免逐行查库）、各「仓库+产品」最早待整理入库日期
        productService.fillSku(pendings, AfterSalePending::getProductId, AfterSalePending::setSku);
        Map<Long, String> customerNames = customerNames(pendings);
        Map<String, LocalDate> firstIns = firstPendingInDates(ids);

        // 3) 逐批次 FIFO 分配（同一产品可来自多张单据，各自独立成行以便追溯）
        for (AfterSalePending pending : pendings) {
            BigDecimal remain = nz(pending.getQuantity()).subtract(nz(pending.getSortedQuantity()));
            boolean cleared = remain.compareTo(BigDecimal.ZERO) <= 0;
            if (cleared && !includeCleared) continue; // 已整理完：默认不返回（否则历史批次会淹没列表）
            String k = whProductKey(pending.getWarehouseId(), pending.getProductId());
            BigDecimal left = avail.getOrDefault(k, BigDecimal.ZERO);
            BigDecimal alloc = cleared ? BigDecimal.ZERO : remain.min(left);
            if (alloc.compareTo(BigDecimal.ZERO) > 0) avail.put(k, left.subtract(alloc));

            LocalDate firstIn = firstIns.get(k);
            int stayDays = firstIn == null ? 0
                    : (int) Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(firstIn, LocalDate.now()));
            AfterSaleSourceType st = AfterSaleSourceType.fromCode(pending.getSourceType());

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("pendingId", pending.getId());
            m.put("warehouseId", pending.getWarehouseId());
            m.put("status", cleared ? STATUS_CLEARED
                    : (alloc.compareTo(BigDecimal.ZERO) > 0 ? STATUS_SORTABLE : STATUS_SHORTAGE));
            // 部分可整理：实物少于批次剩余量（其余部分实物不足，账实不符的轻量提示）
            m.put("partial", !cleared && alloc.compareTo(BigDecimal.ZERO) > 0 && alloc.compareTo(remain) < 0);
            m.put("sourceType", pending.getSourceType());
            m.put("sourceTypeLabel", st != null ? st.getLabel() : pending.getSourceType());
            m.put("sourceId", pending.getSourceId());
            m.put("sourceCode", pending.getSourceCode() != null ? pending.getSourceCode() : "");
            m.put("sourceDate", pending.getSourceDate() != null ? pending.getSourceDate().toString() : "");
            m.put("customerId", pending.getCustomerId());
            m.put("customerName", pending.getCustomerId() == null ? ""
                    : customerNames.getOrDefault(pending.getCustomerId(), ""));
            m.put("productId", pending.getProductId());
            m.put("sku", pending.getSku() != null ? pending.getSku() : "");
            m.put("productName", pending.getProductName() != null ? pending.getProductName() : "");
            m.put("unit", pending.getUnit() != null ? pending.getUnit() : "");
            m.put("quantity", alloc);
            m.put("totalQuantity", nz(pending.getQuantity()));
            m.put("sortedQuantity", nz(pending.getSortedQuantity()));
            m.put("remainQuantity", remain.max(BigDecimal.ZERO));
            m.put("unitPrice", nz(pending.getUnitPrice()));
            // 停留天数：按该产品在该仓最早的待整理入库日期计算（预警用，无流水则为 0）
            m.put("stayDays", stayDays);
            m.put("overdue", stayDays > STAY_ALERT_DAYS);
            res.add(m);
        }
        return res;
    }

    /** 批量建草稿的跳过项（勾选的批次已整理完 / 实物不足 / 不存在） */
    private Map<String, Object> skipRow(Long pendingId, String sourceCode, String reason) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pendingId", pendingId);
        m.put("sourceCode", sourceCode != null ? sourceCode : "");
        m.put("reason", reason);
        return m;
    }

    private Map<Long, String> customerNames(List<AfterSalePending> pendings) {
        List<Long> ids = pendings.stream().map(AfterSalePending::getCustomerId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, String> res = new LinkedHashMap<>();
        if (ids.isEmpty()) return res;
        for (Customer c : customerMapper.selectBatchIds(ids)) {
            res.put(c.getId(), c.getName() != null ? c.getName() : "");
        }
        return res;
    }

    private Map<Long, String> warehouseNames(List<Long> warehouseIds) {
        List<Long> ids = warehouseIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, String> res = new LinkedHashMap<>();
        if (ids.isEmpty()) return res;
        for (Warehouse w : warehouseMapper.selectBatchIds(ids)) res.put(w.getId(), w.getWarehouseName());
        return res;
    }

    /** 各「仓库+产品」最早的待整理(PENDING)入库日期（批量，替代逐行查询；停留天数预警用） */
    private Map<String, LocalDate> firstPendingInDates(Collection<Long> warehouseIds) {
        Map<String, LocalDate> res = new LinkedHashMap<>();
        if (warehouseIds == null || warehouseIds.isEmpty()) return res;
        List<WarehouseStockLog> logs = stockLogMapper.selectList(new LambdaQueryWrapper<WarehouseStockLog>()
                .in(WarehouseStockLog::getWarehouseId, warehouseIds)
                .eq(WarehouseStockLog::getQualityType, ProductQualityType.PENDING.getCode())
                .gt(WarehouseStockLog::getChangeQuantity, BigDecimal.ZERO)
                .orderByAsc(WarehouseStockLog::getCreateTime));
        for (WarehouseStockLog log : logs) {
            if (log.getProductId() == null || log.getCreateTime() == null) continue;
            res.putIfAbsent(whProductKey(log.getWarehouseId(), log.getProductId()),
                    log.getCreateTime().toLocalDate());
        }
        return res;
    }

    private String whProductKey(Long warehouseId, Long productId) { return warehouseId + "#" + productId; }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(ReturnSort s, List<ReturnSortItem> items) {
        defaultTargetsToSource(s);   // 2026-09-22：未指定入库仓 ⇒ 分选后回源仓库
        validate(s, items);
        s.setCode(gen(BillPrefix.RETURN_SORT));
        s.setStatus(DocStatus.DRAFT.getCode());
        stampSorter(s);   // 整理人 = 当前登录用户（批量生成草稿内部也走本方法 ⇒ 一并覆盖）
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) s.setCompanyId(cid);
        rsMapper.insert(s);
        for (ReturnSortItem it : items) {
            it.setId(null); it.setSortId(s.getId());
            if (cid != null && cid > 0) it.setCompanyId(cid);
            itemMapper.insert(it);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(ReturnSort s, List<ReturnSortItem> items) {
        ReturnSort old = rsMapper.selectById(s.getId());
        if (old == null) throw new BusinessException("退货整理单不存在");
        if (DocStatus.AUDITED.getCode().equals(old.getStatus())) throw new BusinessException("已审核的单据不可编辑");
        defaultTargetsToSource(s);   // 同 create：缺省回源仓库（历史单原值保留）
        validate(s, items);

        s.setCode(old.getCode()); s.setStatus(DocStatus.DRAFT.getCode());
        stampSorter(s);   // 编辑算一次整理操作 ⇒ 整理人刷新为最后操作人
        rsMapper.updateById(s);

        itemMapper.delete(new LambdaQueryWrapper<ReturnSortItem>().eq(ReturnSortItem::getSortId, s.getId()));
        Long cid = CompanyContext.get();
        for (ReturnSortItem it : items) {
            it.setId(null); it.setSortId(s.getId());
            if (cid != null && cid > 0) it.setCompanyId(cid);
            itemMapper.insert(it);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        ReturnSort s = rsMapper.selectById(id);
        if (s == null) throw new BusinessException("退货整理单不存在");
        // 整理人为空（历史单）时补写当前用户；已有整理人的不覆盖
        backfillSorterOnAudit(s);
        // 原子抢占状态（P2-29）：并发/双击时只有一个请求能抢到，其余在此失败，避免分选库存重复变动
        if (!DocStatusGuard.claim(rsMapper, ReturnSort::getId, id, ReturnSort::getStatus,
                DocStatus.DRAFT.getCode(), DocStatus.AUDITED.getCode()))
            throw new BusinessException("只有草稿状态可审核");
        // 目标仓缺省回源仓库（2026-09-22）。历史单/异常空值在这里兜底**并落库** —— 否则反审核时取不到仓库，
        // 会出现"入到源仓、冲回时找不到仓"的不一致。
        if (defaultTargetsToSource(s)) {
            rsMapper.update(null, new LambdaUpdateWrapper<ReturnSort>()
                    .eq(ReturnSort::getId, s.getId())
                    .set(ReturnSort::getTargetWarehouseA, s.getTargetWarehouseA())
                    .set(ReturnSort::getTargetWarehouseB, s.getTargetWarehouseB())
                    .set(ReturnSort::getTargetWarehouseC, s.getTargetWarehouseC())
                    .set(ReturnSort::getTargetWarehouseDefect, s.getTargetWarehouseDefect()));
        }
        List<ReturnSortItem> items = getItems(id);
        if (items.isEmpty()) throw new BusinessException("退货整理明细不能为空");
        // 源仓库必须为售后仓：防止创建后仓库被改成非售后仓再审核
        assertSourceWarehouse(s.getWarehouseId());
        assertTargetWarehouses(s);

        for (ReturnSortItem it : items) {
            if (it.getTotalQuantity() == null || it.getTotalQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal a = nz(it.getQtyA()), b = nz(it.getQtyB()), c = nz(it.getQtyC()), d = nz(it.getQtyDefect());
            if (a.add(b).add(c).add(d).compareTo(it.getTotalQuantity()) != 0)
                throw new BusinessException("产品[" + nameOf(it) + "]分选数量之和必须等于待整理数量");
            // 可用量校验：审核时才真正扣库存，此处提前校验避免部分成功后回滚
            BigDecimal avail = availablePending(s.getWarehouseId(), it.getProductId());
            if (it.getTotalQuantity().compareTo(avail) > 0)
                throw new BusinessException("产品[" + nameOf(it) + "]待整理数量 " + it.getTotalQuantity()
                        + " 超过售后仓可用待整理库存 " + avail + "，请刷新待整理库存后重试");
            assertSourceAvailable(it);


            // 扣售后仓 待整理品（changeStock 内部校验库存不足）
            stockService.changeStock(s.getWarehouseId(), it.getProductId(), it.getTotalQuantity().negate(),
                    StockChangeType.RETURN_SORT_OUT, s.getCode(), RelatedBillType.RETURN_SORT,
                    "", s.getId(), ProductQualityType.PENDING.getCode());
            // 追溯：累加来源待整理批次的已整理数量
            applySortedQuantity(it.getPendingId(), it.getTotalQuantity(), true);

            if (a.compareTo(BigDecimal.ZERO) > 0)
                stockService.changeStock(s.getTargetWarehouseA(), it.getProductId(), a,
                        StockChangeType.RETURN_SORT_IN, s.getCode(), RelatedBillType.RETURN_SORT,
                        "", s.getId(), ProductQualityType.A.getCode());
            if (b.compareTo(BigDecimal.ZERO) > 0)
                stockService.changeStock(s.getTargetWarehouseB(), it.getProductId(), b,
                        StockChangeType.RETURN_SORT_IN, s.getCode(), RelatedBillType.RETURN_SORT,
                        "", s.getId(), ProductQualityType.B.getCode());
            if (c.compareTo(BigDecimal.ZERO) > 0)
                stockService.changeStock(s.getTargetWarehouseC(), it.getProductId(), c,
                        StockChangeType.RETURN_SORT_IN, s.getCode(), RelatedBillType.RETURN_SORT,
                        "", s.getId(), ProductQualityType.C.getCode());
            if (d.compareTo(BigDecimal.ZERO) > 0)
                stockService.changeStock(s.getTargetWarehouseDefect(), it.getProductId(), d,
                        StockChangeType.RETURN_SORT_IN, s.getCode(), RelatedBillType.RETURN_SORT,
                        "", s.getId(), ProductQualityType.DEFECT.getCode());
        }
        // 财务联动：折损收款生成独立正向应收（单号 -LOSS 后缀，与整理单本体区分）
        saveLossReceivable(s, items);

        ReturnSort u = new ReturnSort(); u.setId(id); u.setStatus(DocStatus.AUDITED.getCode());
        rsMapper.updateById(u);
    }

    /** @deprecated 反审核语义，请改用 {@link #unAudit(Long)}（F7-51：原名与其余模块的"作废"语义相反；
     *  保留为兼容别名，Controller 的 /cancel 与 /un-audit 都指向同一实现） */
    @Override
    @Deprecated
    public void cancel(Long id) {
        unAudit(id);
    }

    /** 反审核：逆向回滚（canonical 命名 · F7-51） */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unAudit(Long id) {
        ReturnSort s = rsMapper.selectById(id);
        if (s == null) throw new BusinessException("退货整理单不存在");
        // 原子抢占状态（P2-29）：并发/双击时只有一个请求能抢到，其余在此失败，避免分选库存重复冲回
        if (!DocStatusGuard.claim(rsMapper, ReturnSort::getId, id, ReturnSort::getStatus,
                DocStatus.AUDITED.getCode(), DocStatus.DRAFT.getCode()))
            throw new BusinessException("只有已审核状态可反审核");
        List<ReturnSortItem> items = getItems(id);

        for (ReturnSortItem it : items) {
            if (it.getTotalQuantity() == null || it.getTotalQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal a = nz(it.getQtyA()), b = nz(it.getQtyB()), c = nz(it.getQtyC()), d = nz(it.getQtyDefect());

            // 加回售后仓 待整理品
            stockService.changeStock(s.getWarehouseId(), it.getProductId(), it.getTotalQuantity(),
                    StockChangeType.CANCEL_RETURN_SORT_OUT, s.getCode(), RelatedBillType.RETURN_SORT,
                    "", s.getId(), ProductQualityType.PENDING.getCode());
            // 追溯：扣回来源待整理批次的已整理数量
            applySortedQuantity(it.getPendingId(), it.getTotalQuantity(), false);
            // 冲回目标仓库各品质
            if (a.compareTo(BigDecimal.ZERO) > 0)
                stockService.changeStock(s.getTargetWarehouseA(), it.getProductId(), a.negate(),
                        StockChangeType.CANCEL_RETURN_SORT_IN, s.getCode(), RelatedBillType.RETURN_SORT,
                        "", s.getId(), ProductQualityType.A.getCode());
            if (b.compareTo(BigDecimal.ZERO) > 0)
                stockService.changeStock(s.getTargetWarehouseB(), it.getProductId(), b.negate(),
                        StockChangeType.CANCEL_RETURN_SORT_IN, s.getCode(), RelatedBillType.RETURN_SORT,
                        "", s.getId(), ProductQualityType.B.getCode());
            if (c.compareTo(BigDecimal.ZERO) > 0)
                stockService.changeStock(s.getTargetWarehouseC(), it.getProductId(), c.negate(),
                        StockChangeType.CANCEL_RETURN_SORT_IN, s.getCode(), RelatedBillType.RETURN_SORT,
                        "", s.getId(), ProductQualityType.C.getCode());
            if (d.compareTo(BigDecimal.ZERO) > 0)
                stockService.changeStock(s.getTargetWarehouseDefect(), it.getProductId(), d.negate(),
                        StockChangeType.CANCEL_RETURN_SORT_IN, s.getCode(), RelatedBillType.RETURN_SORT,
                        "", s.getId(), ProductQualityType.DEFECT.getCode());
        }
        // 财务联动：冲销折损收款台账（未填折损金额时台账不存在，跳过）
        reverseReceivableIfExists(s.getCode() + "-LOSS");

        // 反审核回到草稿态（与销售退货单 unAudit 保持一致，便于修正后重新审核）
        ReturnSort u = new ReturnSort(); u.setId(id); u.setStatus(DocStatus.DRAFT.getCode());
        rsMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        // 原子删除（O-7）：带状态条件的物理删，只有草稿能删；affected=0 说明已被并发删除/审核或状态已变
        int rows = rsMapper.delete(new LambdaQueryWrapper<ReturnSort>()
                .eq(ReturnSort::getId, id)
                .eq(ReturnSort::getStatus, DocStatus.DRAFT.getCode()));
        if (rows == 0) {
            if (rsMapper.selectById(id) == null) throw new BusinessException("退货整理单不存在");
            throw new BusinessException("只有草稿状态可删除");
        }
        itemMapper.delete(new LambdaQueryWrapper<ReturnSortItem>().eq(ReturnSortItem::getSortId, id));
    }

    /** 源仓库必须为自有成品仓（2026-09-16 方案 A：原"售后仓"取消，退回品直接压在成品仓内按品质 PENDING 待整理）；前端下拉已过滤，此处防接口绕过 */
    private void assertSourceWarehouse(Long warehouseId) {
        Warehouse wh = warehouseMapper.selectById(warehouseId);
        if (wh == null) throw new BusinessException("源仓库不存在");
        if (!com.beichen.erp.warehouse.common.WarehouseCategory.INVENTORY.getCode().equals(wh.getWarehouseCategory())
                || !WarehouseType.FINISHED.getCode().equals(wh.getWarehouseType()))
            throw new BusinessException("退货整理的源仓库必须是自有成品仓，当前仓库类别=" + wh.getWarehouseCategory()
                    + "，仓型=" + wh.getWarehouseType());
    }

    /**
     * 目标入库仓库校验（2026-09-16 方案 A）：A/B/C/不良 4 个目标仓都必须是**自有成品仓**
     * （仓型已无"不良仓/售后仓"，不良品改由**品质 DEFECT** 区分）。
     * <p>⚠️ 不再校验"目标仓 ≠ 源仓"：退货整理本质是**同一仓内的品质分流**
     * （PENDING → A/B/C/DEFECT），源仓与目标仓同为成品仓是正常且最常见的用法。</p>
     */
    private void assertTargetWarehouses(ReturnSort s) {
        assertWarehouseType(s.getTargetWarehouseA(), WarehouseType.FINISHED, "A规");
        assertWarehouseType(s.getTargetWarehouseB(), WarehouseType.FINISHED, "B规");
        assertWarehouseType(s.getTargetWarehouseC(), WarehouseType.FINISHED, "C规");
        assertWarehouseType(s.getTargetWarehouseDefect(), WarehouseType.FINISHED, "不良品");
    }

    private void assertWarehouseType(Long warehouseId, WarehouseType expect, String label) {
        if (warehouseId == null) return; // 空值由前置的非空校验拦截
        Warehouse wh = warehouseMapper.selectById(warehouseId);
        if (wh == null) throw new BusinessException(label + "的目标入库仓库不存在");
        if (!expect.getCode().equals(wh.getWarehouseType()))
            throw new BusinessException(label + "的目标入库仓库必须是" + expect.getCode()
                    + "，当前仓库类型为：" + wh.getWarehouseType());
    }

    /** 售后仓指定产品的待整理(PENDING)可用库存 */
    private BigDecimal availablePending(Long warehouseId, Long productId) {
        WarehouseStock st = stockMapper.selectOne(new LambdaQueryWrapper<WarehouseStock>()
                .eq(WarehouseStock::getWarehouseId, warehouseId)
                .eq(WarehouseStock::getProductId, productId)
                .eq(WarehouseStock::getQualityType, ProductQualityType.PENDING.getCode()));
        return st == null || st.getQuantity() == null ? BigDecimal.ZERO : st.getQuantity();
    }

    /** 来源待整理批次剩余可整理数量 = 批次数量 − 已整理数量；批次不存在返回 null（历史数据无锚点，不校验） */
    private BigDecimal pendingRemain(Long pendingId) {
        AfterSalePending p = pendingId == null ? null : afterSalePendingMapper.selectById(pendingId);
        if (p == null) return null;
        return nz(p.getQuantity()).subtract(nz(p.getSortedQuantity()));
    }

    /** 校验整理数量不得超过来源批次的剩余可整理数量（追溯用，防止同一批次被重复整理） */
    private void assertSourceAvailable(ReturnSortItem it) {
        BigDecimal remain = pendingRemain(it.getPendingId());
        if (remain == null) return; // 无锚点（历史数据）不校验
        if (remain.compareTo(BigDecimal.ZERO) <= 0)
            throw new BusinessException("产品[" + nameOf(it) + "]对应来源批次已整理完，无剩余可整理数量");
        if (it.getTotalQuantity() != null && it.getTotalQuantity().compareTo(remain) > 0)
            throw new BusinessException("产品[" + nameOf(it) + "]待整理数量 " + it.getTotalQuantity()
                    + " 超过来源批次可整理数量 " + remain);
    }

    /** 追溯：累加(add=true)/扣回(add=false) 来源待整理批次的已整理数量 */
    private void applySortedQuantity(Long pendingId, BigDecimal qty, boolean add) {
        if (pendingId == null || qty == null) return;
        AfterSalePending p = afterSalePendingMapper.selectById(pendingId);
        if (p == null) return; // 无锚点（历史数据）不校验、不回写（保持原语义）
        // F7-49（2026-09-19）：改为 **SQL 原子累加**（原 Java 侧"读-改-写"，两张整理单并发审核同一来源批次
        // 会互相覆盖、已整理量少记一次）；扣回侧用 GREATEST(...,0) 保留原来的"不为负"语义。
        String qtySql = qty.toPlainString();
        afterSalePendingMapper.update(null, new LambdaUpdateWrapper<AfterSalePending>()
                .eq(AfterSalePending::getId, p.getId())
                .setSql(add
                        ? "sorted_quantity = IFNULL(sorted_quantity, 0) + (" + qtySql + ")"
                        : "sorted_quantity = GREATEST(IFNULL(sorted_quantity, 0) - (" + qtySql + "), 0)"));
    }

    // ==================== 整理人（2026-09-22 用户要求：谁操作的就是谁整理的） ====================

    /**
     * 打上/刷新整理人 = 当前登录用户。
     * <p>新建、批量生成草稿（内部也走 {@link #create}）、编辑三条路径都调用 ⇒ 最后一次操作的人就是整理人。</p>
     */
    private void stampSorter(ReturnSort s) {
        Long uid = getCurrentUserId();
        String name = getCurrentUserName();
        if (uid != null) s.setSortUserId(uid);
        if (name != null) s.setSortUserName(name);
    }

    /**
     * 审核时补写整理人：**仅当为空**（历史单兜底）。
     * <p>已有整理人的单**不覆盖** —— 整理人与审核人可能不是同一个人，覆盖会把"谁整理的"记错。</p>
     */
    private void backfillSorterOnAudit(ReturnSort s) {
        if (s.getSortUserName() != null && !s.getSortUserName().isBlank()) return;
        Long uid = getCurrentUserId();
        String name = getCurrentUserName();
        if (uid == null && name == null) return;
        rsMapper.update(null, new LambdaUpdateWrapper<ReturnSort>()
                .eq(ReturnSort::getId, s.getId())
                .set(uid != null, ReturnSort::getSortUserId, uid)
                .set(name != null, ReturnSort::getSortUserName, name));
        if (uid != null) s.setSortUserId(uid);
        if (name != null) s.setSortUserName(name);
    }

    /** 当前登录用户ID（未登录/异常一律 null，不影响主流程） */
    private Long getCurrentUserId() {
        try { return StpUtil.getLoginIdAsLong(); } catch (Exception e) { return null; }
    }

    /** 当前登录用户名（取 sys_user.username；查不到则 null） */
    private String getCurrentUserName() {
        try {
            Long userId = StpUtil.getLoginIdAsLong();
            User user = userMapper.selectById(userId);
            return user != null ? user.getUsername() : null;
        } catch (Exception e) { return null; }
    }

    /**
     * 目标入库仓缺省回填 = 源仓库（2026-09-22 用户口径：「默认回到源仓库」——
     * A规/B规/C规/不良 4 个入库仓前端不再选择，分选后按品质回到该批次的源仓）。
     *
     * <p>⚠️ 只填空值：**已显式填过的历史单保持原值**。反审核按原目标仓冲回、再审核必须落回同一个仓，
     * 若无条件改成源仓，历史单反审核后再审核会"货凭空搬家"。</p>
     *
     * @return true 表示有改动（调用方需要落库）
     */
    private boolean defaultTargetsToSource(ReturnSort s) {
        Long src = s.getWarehouseId();
        if (src == null) return false;
        boolean changed = false;
        if (s.getTargetWarehouseA() == null) { s.setTargetWarehouseA(src); changed = true; }
        if (s.getTargetWarehouseB() == null) { s.setTargetWarehouseB(src); changed = true; }
        if (s.getTargetWarehouseC() == null) { s.setTargetWarehouseC(src); changed = true; }
        if (s.getTargetWarehouseDefect() == null) { s.setTargetWarehouseDefect(src); changed = true; }
        return changed;
    }

    private void validate(ReturnSort s, List<ReturnSortItem> items) {
        if (s.getWarehouseId() == null) throw new BusinessException("源仓库(售后仓)不能为空");
        assertSourceWarehouse(s.getWarehouseId());
        // 2026-09-22 用户口径：**默认回到源仓库** ⇒ 前端不再收集 A/B/C/不良 4 个入库仓，
        // 原来的非空校验随之删除；空值由 create/update/audit 里的 defaultTargetsToSource 回填成源仓库。
        if (items == null || items.isEmpty()) throw new BusinessException("退货整理明细不能为空");
        if (s.getLossAmount() != null && s.getLossAmount().compareTo(BigDecimal.ZERO) < 0)
            throw new BusinessException("折损收款金额不能为负数");
        assertTargetWarehouses(s);
        for (ReturnSortItem it : items) {
            if (it.getProductId() == null) throw new BusinessException("产品不能为空");
            if (it.getTotalQuantity() == null || it.getTotalQuantity().compareTo(BigDecimal.ZERO) <= 0)
                throw new BusinessException("产品[" + nameOf(it) + "]待整理数量必须大于0");
            BigDecimal a = nz(it.getQtyA()), b = nz(it.getQtyB()), c = nz(it.getQtyC()), d = nz(it.getQtyDefect());
            if (a.add(b).add(c).add(d).compareTo(it.getTotalQuantity()) != 0)
                throw new BusinessException("产品[" + nameOf(it) + "]分选数量之和必须等于待整理数量");
            // 可用量校验：待整理数量不得超过售后仓待整理库存，提前给出明确提示
            BigDecimal avail = availablePending(s.getWarehouseId(), it.getProductId());
            if (it.getTotalQuantity().compareTo(avail) > 0)
                throw new BusinessException("产品[" + nameOf(it) + "]待整理数量 " + it.getTotalQuantity()
                        + " 超过售后仓可用待整理库存 " + avail + "，请刷新待整理库存后重试");
            assertSourceAvailable(it);
        }
    }

    private String nameOf(ReturnSortItem it) {
        return it.getProductName() != null && !it.getProductName().isBlank() ? it.getProductName() : String.valueOf(it.getProductId());
    }

    // ==================== 折损收款 ====================

    /**
     * 折损收款：整理后才知道 B/C/不良 各多少，因此金额挂在整理单上而非销售退货单。
     * 审核时生成一条独立正向应收，单号 -LOSS 后缀，与整理单本体区分。
     */
    private void saveLossReceivable(ReturnSort s, List<ReturnSortItem> items) {
        if (s.getLossAmount() == null || s.getLossAmount().compareTo(BigDecimal.ZERO) <= 0) return;
        Long customerId = resolveLossCustomer(items);
        if (customerId == null) throw new BusinessException("无法识别折损收款客户：来源待整理批次缺少客户信息");
        Customer c = customerMapper.selectById(customerId);
        FinanceReceivable fr = new FinanceReceivable();
        fr.setBillNo(s.getCode() + "-LOSS");
        fr.setCustomerId(customerId);
        fr.setCustomerName(c != null && c.getName() != null ? c.getName() : "");
        fr.setSourceBillType(SourceBillType.RETURN_SORT_LOSS.getCode());
        fr.setSourceBillNo(s.getCode());
        fr.setSourceId(s.getId());
        fr.setAmount(s.getLossAmount());
        fr.setPaidAmount(BigDecimal.ZERO);
        fr.setUnpaidAmount(s.getLossAmount());
        fr.setDueDate(s.getSortDate());
        fr.setStatus(SettlementStatus.UNSETTLED.getCode());
        fr.setRemark("退货整理折损收款"
                + (s.getLossRemark() != null && !s.getLossRemark().isBlank() ? "：" + s.getLossRemark() : ""));
        saveReceivable(fr);
    }

    /**
     * 折损收款客户：取来源待整理批次上冗余的 customerId。
     * 一张整理单只能对应一个客户，否则无法生成单一应收台账，需按客户拆单。
     */
    private Long resolveLossCustomer(List<ReturnSortItem> items) {
        Set<Long> ids = new LinkedHashSet<>();
        for (ReturnSortItem it : items) {
            if (it.getPendingId() == null) continue;
            AfterSalePending p = afterSalePendingMapper.selectById(it.getPendingId());
            if (p != null && p.getCustomerId() != null) ids.add(p.getCustomerId());
        }
        if (ids.size() > 1)
            throw new BusinessException("本单待整理批次涉及多个客户，无法确定折损收款对象，请按客户拆分整理单");
        return ids.isEmpty() ? null : ids.iterator().next();
    }

    /**
     * 保存应收台账：按 billNo 复用已存在记录后再写入。
     * <p>反审核是「冲销」（仅置 CANCELLED，记录保留），再次审核直接 insert 同 billNo 会撞 uk_bill_no。</p>
     */
    private void saveReceivable(FinanceReceivable fr) {
        FinanceReceivable exist = financeReceivableMapper.selectOne(
                new LambdaQueryWrapper<FinanceReceivable>().eq(FinanceReceivable::getBillNo, fr.getBillNo()));
        if (exist == null) {
            financeReceivableMapper.insert(fr);
            return;
        }
        financeReceivableMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<FinanceReceivable>()
                        .eq(FinanceReceivable::getId, exist.getId())
                        .set(FinanceReceivable::getCustomerId, fr.getCustomerId())
                        .set(FinanceReceivable::getCustomerName, fr.getCustomerName())
                        .set(FinanceReceivable::getSourceBillType, fr.getSourceBillType())
                        .set(FinanceReceivable::getSourceBillNo, fr.getSourceBillNo())
                        .set(FinanceReceivable::getSourceId, fr.getSourceId())
                        .set(FinanceReceivable::getAmount, fr.getAmount())
                        .set(FinanceReceivable::getPaidAmount, fr.getPaidAmount())
                        .set(FinanceReceivable::getUnpaidAmount, fr.getUnpaidAmount())
                        .set(FinanceReceivable::getDueDate, fr.getDueDate())
                        .set(FinanceReceivable::getStatus, fr.getStatus())
                        .set(FinanceReceivable::getRemark, fr.getRemark()));
    }

    /** 冲销应收台账：不存在则跳过（未填折损金额时本就没有台账） */
    private void reverseReceivableIfExists(String billNo) {
        if (billNo == null || billNo.isBlank()) return;
        FinanceReceivable fr = financeReceivableMapper.selectOne(
                new LambdaQueryWrapper<FinanceReceivable>().eq(FinanceReceivable::getBillNo, billNo));
        if (fr == null) return;
        receivableHelper.reverseReceivable(billNo);
    }

    private BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    private String gen(String prefix) {
        String d = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String pat = prefix + d;
        LambdaQueryWrapper<ReturnSort> w = new LambdaQueryWrapper<ReturnSort>()
                .likeRight(ReturnSort::getCode, pat).orderByDesc(ReturnSort::getCode).last("LIMIT 1");
        ReturnSort last = rsMapper.selectOne(w);
        // F7-116（2026-09-20）：统一走 BillNoSeq（详见该类 javadoc）。
        int seq = BillNoSeq.lastSeq(last == null ? null : last.getCode(), pat) + 1;
        return BillNoSeq.format(pat, seq);
    }
}
