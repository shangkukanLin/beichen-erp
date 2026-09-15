package com.beichen.erp.sale.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.BillPrefix;
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
 * 退货整理：销售退货先入售后仓(待分类品/待整理)，再按 A/B/C/不良品 分选后分别入库（A/B/C 入成品仓）。
 */
@Service
@RequiredArgsConstructor
public class ReturnSortServiceImpl implements ReturnSortService {

    private final ReturnSortMapper rsMapper;
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
     * 将售后仓待分类库存分配回各「售后待整理批次」（after_sale_pending，销售退单与销售换货单共用入口），
     * 使每行都能追溯到具体来源单据，并给出「原数量 / 已整理数量 / 本次可整理数量」。</p>
     */
    public List<Map<String, Object>> defectStock(Long warehouseId) {
        if (warehouseId == null) throw new BusinessException("仓库不能为空");
        // 1) 售后仓待分类库存（按产品聚合）：决定实物有多少可整理
        List<WarehouseStock> stocks = stockMapper.selectList(new LambdaQueryWrapper<WarehouseStock>()
                .eq(WarehouseStock::getWarehouseId, warehouseId)
                .eq(WarehouseStock::getQualityType, ProductQualityType.PENDING.getCode())
                .gt(WarehouseStock::getQuantity, BigDecimal.ZERO)
                .isNotNull(WarehouseStock::getProductId));
        Map<Long, BigDecimal> avail = new LinkedHashMap<>();
        for (WarehouseStock s : stocks) {
            if (s.getProductId() == null) continue;
            avail.merge(s.getProductId(), nz(s.getQuantity()), BigDecimal::add);
        }
        List<Map<String, Object>> res = new ArrayList<>();
        if (avail.isEmpty()) return res;

        // 2) 该售后仓的待整理批次（FIFO：批次ID 升序）
        List<AfterSalePending> pendings = afterSalePendingMapper.selectList(
                new LambdaQueryWrapper<AfterSalePending>()
                        .eq(AfterSalePending::getWarehouseId, warehouseId)
                        .orderByAsc(AfterSalePending::getId));
        if (pendings.isEmpty()) return res;

        // 回填 SKU（非表字段），清单直接带出便于识别型号
        productService.fillSku(pendings, AfterSalePending::getProductId, AfterSalePending::setSku);

        // 3) 批次按 FIFO 分配可用库存，逐行展开（同一产品可来自多张单据，各自独立成行以便追溯）
        for (AfterSalePending pending : pendings) {
            BigDecimal left = avail.get(pending.getProductId());
            if (left == null || left.compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal remain = nz(pending.getQuantity()).subtract(nz(pending.getSortedQuantity()));
            if (remain.compareTo(BigDecimal.ZERO) <= 0) continue; // 该批次已整理完
            BigDecimal alloc = remain.min(left);
            AfterSaleSourceType st = AfterSaleSourceType.fromCode(pending.getSourceType());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("pendingId", pending.getId());
            m.put("sourceType", pending.getSourceType());
            m.put("sourceTypeLabel", st != null ? st.getLabel() : pending.getSourceType());
            m.put("sourceId", pending.getSourceId());
            m.put("sourceCode", pending.getSourceCode() != null ? pending.getSourceCode() : "");
            m.put("sourceDate", pending.getSourceDate() != null ? pending.getSourceDate().toString() : "");
            m.put("productId", pending.getProductId());
            m.put("sku", pending.getSku() != null ? pending.getSku() : "");
            m.put("productName", pending.getProductName() != null ? pending.getProductName() : "");
            m.put("spec", pending.getSpec() != null ? pending.getSpec() : "");
            m.put("unit", pending.getUnit() != null ? pending.getUnit() : "");
            m.put("quantity", alloc);
            m.put("totalQuantity", nz(pending.getQuantity()));
            m.put("sortedQuantity", nz(pending.getSortedQuantity()));
            m.put("unitPrice", nz(pending.getUnitPrice()));
            // 停留天数：按该产品在售后仓最早的待分类入库日期计算（预警用，无流水则为 0）
            LocalDate firstIn = firstPendingInDate(warehouseId, pending.getProductId());
            m.put("stayDays", firstIn == null ? 0
                    : Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(firstIn, LocalDate.now())));
            res.add(m);
            avail.put(pending.getProductId(), left.subtract(alloc));
        }
        return res;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(ReturnSort s, List<ReturnSortItem> items) {
        validate(s, items);
        s.setCode(gen(BillPrefix.RETURN_SORT));
        s.setStatus(DocStatus.DRAFT.getCode());
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
        validate(s, items);

        s.setCode(old.getCode()); s.setStatus(DocStatus.DRAFT.getCode());
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
        // 原子抢占状态（P2-29）：并发/双击时只有一个请求能抢到，其余在此失败，避免分选库存重复变动
        if (!DocStatusGuard.claim(rsMapper, ReturnSort::getId, id, ReturnSort::getStatus,
                DocStatus.DRAFT.getCode(), DocStatus.AUDITED.getCode()))
            throw new BusinessException("只有草稿状态可审核");
        if (s.getTargetWarehouseA() == null || s.getTargetWarehouseB() == null
                || s.getTargetWarehouseC() == null || s.getTargetWarehouseDefect() == null)
            throw new BusinessException("请选择 A/B/C/不良 的目标入库仓库");
        List<ReturnSortItem> items = getItems(id);
        if (items.isEmpty()) throw new BusinessException("退货整理明细不能为空");
        // 源仓库必须为售后仓：防止创建后仓库被改成非售后仓再审核
        assertAfterSaleWarehouse(s.getWarehouseId());
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
                        + " 超过售后仓可用待分类库存 " + avail + "，请刷新待整理库存后重试");
            assertSourceAvailable(it);

            Product prod = it.getProductId() != null ? productMapper.selectById(it.getProductId()) : null;
            String spec = prod != null ? prod.getSpec() : (it.getSpec() != null ? it.getSpec() : "");

            // 扣售后仓 待分类品（changeStock 内部校验库存不足）
            stockService.changeStock(s.getWarehouseId(), it.getProductId(), it.getTotalQuantity().negate(),
                    StockChangeType.RETURN_SORT_OUT, s.getCode(), RelatedBillType.RETURN_SORT,
                    spec, s.getId(), ProductQualityType.PENDING.getCode());
            // 追溯：累加来源待整理批次的已整理数量
            applySortedQuantity(it.getPendingId(), it.getTotalQuantity(), true);

            if (a.compareTo(BigDecimal.ZERO) > 0)
                stockService.changeStock(s.getTargetWarehouseA(), it.getProductId(), a,
                        StockChangeType.RETURN_SORT_IN, s.getCode(), RelatedBillType.RETURN_SORT,
                        spec, s.getId(), ProductQualityType.A.getCode());
            if (b.compareTo(BigDecimal.ZERO) > 0)
                stockService.changeStock(s.getTargetWarehouseB(), it.getProductId(), b,
                        StockChangeType.RETURN_SORT_IN, s.getCode(), RelatedBillType.RETURN_SORT,
                        spec, s.getId(), ProductQualityType.B.getCode());
            if (c.compareTo(BigDecimal.ZERO) > 0)
                stockService.changeStock(s.getTargetWarehouseC(), it.getProductId(), c,
                        StockChangeType.RETURN_SORT_IN, s.getCode(), RelatedBillType.RETURN_SORT,
                        spec, s.getId(), ProductQualityType.C.getCode());
            if (d.compareTo(BigDecimal.ZERO) > 0)
                stockService.changeStock(s.getTargetWarehouseDefect(), it.getProductId(), d,
                        StockChangeType.RETURN_SORT_IN, s.getCode(), RelatedBillType.RETURN_SORT,
                        spec, s.getId(), ProductQualityType.DEFECT.getCode());
        }
        // 财务联动：折损收款生成独立正向应收（单号 -LOSS 后缀，与整理单本体区分）
        saveLossReceivable(s, items);

        ReturnSort u = new ReturnSort(); u.setId(id); u.setStatus(DocStatus.AUDITED.getCode());
        rsMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
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
            Product prod = it.getProductId() != null ? productMapper.selectById(it.getProductId()) : null;
            String spec = prod != null ? prod.getSpec() : (it.getSpec() != null ? it.getSpec() : "");

            // 加回售后仓 待分类品
            stockService.changeStock(s.getWarehouseId(), it.getProductId(), it.getTotalQuantity(),
                    StockChangeType.CANCEL_RETURN_SORT_OUT, s.getCode(), RelatedBillType.RETURN_SORT,
                    spec, s.getId(), ProductQualityType.PENDING.getCode());
            // 追溯：扣回来源待整理批次的已整理数量
            applySortedQuantity(it.getPendingId(), it.getTotalQuantity(), false);
            // 冲回目标仓库各品质
            if (a.compareTo(BigDecimal.ZERO) > 0)
                stockService.changeStock(s.getTargetWarehouseA(), it.getProductId(), a.negate(),
                        StockChangeType.CANCEL_RETURN_SORT_IN, s.getCode(), RelatedBillType.RETURN_SORT,
                        spec, s.getId(), ProductQualityType.A.getCode());
            if (b.compareTo(BigDecimal.ZERO) > 0)
                stockService.changeStock(s.getTargetWarehouseB(), it.getProductId(), b.negate(),
                        StockChangeType.CANCEL_RETURN_SORT_IN, s.getCode(), RelatedBillType.RETURN_SORT,
                        spec, s.getId(), ProductQualityType.B.getCode());
            if (c.compareTo(BigDecimal.ZERO) > 0)
                stockService.changeStock(s.getTargetWarehouseC(), it.getProductId(), c.negate(),
                        StockChangeType.CANCEL_RETURN_SORT_IN, s.getCode(), RelatedBillType.RETURN_SORT,
                        spec, s.getId(), ProductQualityType.C.getCode());
            if (d.compareTo(BigDecimal.ZERO) > 0)
                stockService.changeStock(s.getTargetWarehouseDefect(), it.getProductId(), d.negate(),
                        StockChangeType.CANCEL_RETURN_SORT_IN, s.getCode(), RelatedBillType.RETURN_SORT,
                        spec, s.getId(), ProductQualityType.DEFECT.getCode());
        }
        // 财务联动：冲销折损收款台账（未填折损金额时台账不存在，跳过）
        reverseReceivableIfExists(s.getCode() + "-LOSS");

        // 反审核回到草稿态（与销售退单 unAudit 保持一致，便于修正后重新审核）
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

    /** 源仓库必须为售后仓（前端下拉已过滤，此处防止接口绕过） */
    private void assertAfterSaleWarehouse(Long warehouseId) {
        Warehouse wh = warehouseMapper.selectById(warehouseId);
        if (wh == null) throw new BusinessException("源仓库不存在");
        if (!WarehouseType.AFTER_SALE.getCode().equals(wh.getWarehouseType()))
            throw new BusinessException("退货整理的源仓库必须是售后仓，当前仓库类型为：" + wh.getWarehouseType());
    }

    /**
     * 目标入库仓库校验：4 个目标仓均不能与源仓(售后仓)相同；A/B/C 必须为成品仓。
     * 不良仓为柔性校验——仅当系统已配置"不良仓"类型的仓库时才强制，避免未建不良仓时无法提交整理单。
     */
    private void assertTargetWarehouses(ReturnSort s) {
        Long src = s.getWarehouseId();
        assertTargetNotSource(s.getTargetWarehouseA(), src, "A规");
        assertTargetNotSource(s.getTargetWarehouseB(), src, "B规");
        assertTargetNotSource(s.getTargetWarehouseC(), src, "C规");
        assertTargetNotSource(s.getTargetWarehouseDefect(), src, "不良品");

        assertWarehouseType(s.getTargetWarehouseA(), WarehouseType.FINISHED, "A规");
        assertWarehouseType(s.getTargetWarehouseB(), WarehouseType.FINISHED, "B规");
        assertWarehouseType(s.getTargetWarehouseC(), WarehouseType.FINISHED, "C规");

        Long defectCount = warehouseMapper.selectCount(new LambdaQueryWrapper<Warehouse>()
                .eq(Warehouse::getWarehouseType, WarehouseType.DEFECT.getCode()));
        if (defectCount != null && defectCount > 0)
            assertWarehouseType(s.getTargetWarehouseDefect(), WarehouseType.DEFECT, "不良品");
    }

    private void assertTargetNotSource(Long targetId, Long srcId, String label) {
        if (targetId != null && targetId.equals(srcId))
            throw new BusinessException(label + "的目标入库仓库不能与源仓库(售后仓)相同");
    }

    private void assertWarehouseType(Long warehouseId, WarehouseType expect, String label) {
        if (warehouseId == null) return; // 空值由前置的非空校验拦截
        Warehouse wh = warehouseMapper.selectById(warehouseId);
        if (wh == null) throw new BusinessException(label + "的目标入库仓库不存在");
        if (!expect.getCode().equals(wh.getWarehouseType()))
            throw new BusinessException(label + "的目标入库仓库必须是" + expect.getCode()
                    + "，当前仓库类型为：" + wh.getWarehouseType());
    }

    /** 售后仓指定产品的待分类(PENDING)可用库存 */
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
        if (p == null) return;
        BigDecimal next = add ? nz(p.getSortedQuantity()).add(qty) : nz(p.getSortedQuantity()).subtract(qty);
        if (next.compareTo(BigDecimal.ZERO) < 0) next = BigDecimal.ZERO;
        AfterSalePending u = new AfterSalePending();
        u.setId(p.getId());
        u.setSortedQuantity(next);
        afterSalePendingMapper.updateById(u);
    }

    private void validate(ReturnSort s, List<ReturnSortItem> items) {
        if (s.getWarehouseId() == null) throw new BusinessException("源仓库(售后仓)不能为空");
        assertAfterSaleWarehouse(s.getWarehouseId());
        if (s.getTargetWarehouseA() == null || s.getTargetWarehouseB() == null
                || s.getTargetWarehouseC() == null || s.getTargetWarehouseDefect() == null)
            throw new BusinessException("请选择 A/B/C/不良 的目标入库仓库");
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
            // 可用量校验：待整理数量不得超过售后仓待分类库存，提前给出明确提示
            BigDecimal avail = availablePending(s.getWarehouseId(), it.getProductId());
            if (it.getTotalQuantity().compareTo(avail) > 0)
                throw new BusinessException("产品[" + nameOf(it) + "]待整理数量 " + it.getTotalQuantity()
                        + " 超过售后仓可用待分类库存 " + avail + "，请刷新待整理库存后重试");
            assertSourceAvailable(it);
        }
    }

    /** 该产品在指定售后仓最早的待分类(PENDING)入库日期：用于计算实物停留天数（预警用） */
    private LocalDate firstPendingInDate(Long warehouseId, Long productId) {
        WarehouseStockLog log = stockLogMapper.selectOne(new LambdaQueryWrapper<WarehouseStockLog>()
                .eq(WarehouseStockLog::getWarehouseId, warehouseId)
                .eq(WarehouseStockLog::getProductId, productId)
                .eq(WarehouseStockLog::getQualityType, ProductQualityType.PENDING.getCode())
                .gt(WarehouseStockLog::getChangeQuantity, BigDecimal.ZERO)
                .orderByAsc(WarehouseStockLog::getCreateTime)
                .last("LIMIT 1"));
        return log == null || log.getCreateTime() == null ? null : log.getCreateTime().toLocalDate();
    }

    private String nameOf(ReturnSortItem it) {
        return it.getProductName() != null && !it.getProductName().isBlank() ? it.getProductName() : String.valueOf(it.getProductId());
    }

    // ==================== 折损收款 ====================

    /**
     * 折损收款：整理后才知道 B/C/不良 各多少，因此金额挂在整理单上而非销售退单。
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
        int seq = 1;
        if (last != null && last.getCode() != null) {
            try { seq = Integer.parseInt(last.getCode().substring(last.getCode().length() - 3)) + 1; } catch (Exception e) { seq = 1; }
        }
        return prefix + d + String.format("%03d", seq);
    }
}
