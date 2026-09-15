package com.beichen.erp.inventory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.BillPrefix;
import com.beichen.erp.common.DocStatus;
import com.beichen.erp.common.DocStatusGuard;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.inventory.common.RelatedBillType;
import com.beichen.erp.inventory.common.StockChangeType;
import com.beichen.erp.inventory.entity.InventoryStockTake;
import com.beichen.erp.inventory.entity.InventoryStockTakeItem;
import com.beichen.erp.inventory.mapper.InventoryStockTakeItemMapper;
import com.beichen.erp.inventory.mapper.InventoryStockTakeMapper;
import com.beichen.erp.inventory.service.StockTakeService;
import com.beichen.erp.material.entity.Product;
import com.beichen.erp.material.mapper.ProductMapper;
import com.beichen.erp.outsource.entity.OutsourceMaterial;
import com.beichen.erp.outsource.mapper.OutsourceMaterialMapper;
import com.beichen.erp.warehouse.entity.Warehouse;
import com.beichen.erp.warehouse.entity.WarehouseStock;
import com.beichen.erp.warehouse.mapper.WarehouseMapper;
import com.beichen.erp.warehouse.mapper.WarehouseStockMapper;
import com.beichen.erp.warehouse.service.CostService;
import com.beichen.erp.warehouse.service.WarehouseStockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 库存盘点实现（每月每仓一次）。
 * <p>
 * 创建：按仓库现有库存行快照生成明细（成品 product+品质，委外物料 material）；
 * 审核：逐行按差异调整库存（盘盈 STOCK_TAKE_IN / 盘亏 STOCK_TAKE_OUT，物料走 changeMaterialStock）；
 * 反审核：逆向调整回滚；作废仅草稿可用。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockTakeServiceImpl implements StockTakeService {

    /** 进入"本月待盘点"提醒窗口的日期（当月 25 号起） */
    private static final int REMIND_DAY = 25;
    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyy-MM");

    private final InventoryStockTakeMapper takeMapper;
    private final InventoryStockTakeItemMapper itemMapper;
    private final WarehouseMapper warehouseMapper;
    private final WarehouseStockMapper stockMapper;
    private final WarehouseStockService stockService;
    private final ProductMapper productMapper;
    private final OutsourceMaterialMapper materialMapper;
    private final CostService costService;

    @Override
    public Page<Map<String, Object>> page(Long warehouseId, String period, String status, int pageNum, int pageSize) {
        LambdaQueryWrapper<InventoryStockTake> w = new LambdaQueryWrapper<InventoryStockTake>()
                .eq(warehouseId != null, InventoryStockTake::getWarehouseId, warehouseId)
                .eq(period != null && !period.isBlank(), InventoryStockTake::getPeriod, period)
                .eq(status != null && !status.isBlank(), InventoryStockTake::getStatus, status)
                .orderByDesc(InventoryStockTake::getId);
        Page<InventoryStockTake> raw = takeMapper.selectPage(new Page<>(pageNum, pageSize), w);
        Page<Map<String, Object>> res = new Page<>(pageNum, pageSize, raw.getTotal());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (InventoryStockTake t : raw.getRecords()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("takeNo", t.getTakeNo());
            m.put("warehouseId", t.getWarehouseId());
            m.put("warehouseName", t.getWarehouseName());
            m.put("period", t.getPeriod());
            m.put("takeDate", t.getTakeDate());
            m.put("status", t.getStatus());
            m.put("remark", t.getRemark());
            // 差异合计（绝对值合计 + 有差异行数），列表直接看盘点结果
            List<InventoryStockTakeItem> items = itemMapper.selectList(
                    new LambdaQueryWrapper<InventoryStockTakeItem>().eq(InventoryStockTakeItem::getTakeId, t.getId()));
            BigDecimal diffSum = BigDecimal.ZERO;
            int diffCount = 0;
            for (InventoryStockTakeItem it : items) {
                BigDecimal d = it.getDiffQuantity() != null ? it.getDiffQuantity() : BigDecimal.ZERO;
                if (d.compareTo(BigDecimal.ZERO) != 0) {
                    diffCount++;
                    diffSum = diffSum.add(d);
                }
            }
            m.put("itemCount", items.size());
            m.put("diffCount", diffCount);
            m.put("diffSum", diffSum);
            rows.add(m);
        }
        res.setRecords(rows);
        return res;
    }

    @Override
    public InventoryStockTake getById(Long id) {
        return takeMapper.selectById(id);
    }

    @Override
    public List<InventoryStockTakeItem> getItems(Long takeId) {
        return itemMapper.selectList(
                new LambdaQueryWrapper<InventoryStockTakeItem>().eq(InventoryStockTakeItem::getTakeId, takeId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InventoryStockTake create(Long warehouseId, String period, LocalDate takeDate, String remark) {
        if (warehouseId == null) throw new BusinessException("请选择盘点仓库");
        Warehouse wh = warehouseMapper.selectById(warehouseId);
        if (wh == null) throw new BusinessException("仓库不存在");
        String ym = (period == null || period.isBlank()) ? LocalDate.now().format(YM) : period;
        // 同一仓库同一月份只保留一条有效单据（草稿/已审核）
        Long exist = takeMapper.selectCount(new LambdaQueryWrapper<InventoryStockTake>()
                .eq(InventoryStockTake::getWarehouseId, warehouseId)
                .eq(InventoryStockTake::getPeriod, ym)
                .ne(InventoryStockTake::getStatus, DocStatus.CANCELLED.getCode()));
        if (exist != null && exist > 0) throw new BusinessException("该仓库 " + ym + " 已存在盘点单，请勿重复创建");

        InventoryStockTake t = new InventoryStockTake();
        t.setTakeNo(genNo());
        t.setWarehouseId(warehouseId);
        t.setWarehouseName(wh.getWarehouseName());
        t.setPeriod(ym);
        t.setTakeDate(takeDate != null ? takeDate : LocalDate.now());
        t.setStatus(DocStatus.DRAFT.getCode());
        t.setRemark(remark);
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) t.setCompanyId(cid);
        takeMapper.insert(t);

        // 快照当前库存行生成明细
        List<WarehouseStock> stocks = stockMapper.selectList(new LambdaQueryWrapper<WarehouseStock>()
                .eq(WarehouseStock::getWarehouseId, warehouseId));
        for (WarehouseStock s : stocks) {
            InventoryStockTakeItem it = new InventoryStockTakeItem();
            it.setTakeId(t.getId());
            it.setProductId(s.getProductId());
            it.setMaterialId(s.getMaterialId());
            it.setQualityType(s.getQualityType());
            it.setBookQuantity(s.getQuantity() != null ? s.getQuantity() : BigDecimal.ZERO);
            it.setActualQuantity(it.getBookQuantity()); // 默认实盘=账面，用户只改有差异的行
            it.setDiffQuantity(BigDecimal.ZERO);
            if (s.getProductId() != null) {
                Product p = productMapper.selectById(s.getProductId());
                if (p != null) {
                    it.setProductName(p.getName());
                    it.setSku(p.getSku());
                    it.setSpec(p.getSpec());
                    it.setUnit(p.getUnit());
                }
            } else if (s.getMaterialId() != null) {
                OutsourceMaterial m = materialMapper.selectById(s.getMaterialId());
                if (m != null) {
                    it.setMaterialName(m.getMaterialName());
                    it.setSpec(m.getSpec());
                    it.setUnit(m.getUnit());
                }
            }
            if (cid != null && cid > 0) it.setCompanyId(cid);
            itemMapper.insert(it);
        }
        log.info("创建盘点单 {} 仓库={} 月份={} 明细={} 行", t.getTakeNo(), wh.getWarehouseName(), ym, stocks.size());
        return t;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveItems(Long takeId, List<InventoryStockTakeItem> items) {
        InventoryStockTake t = takeMapper.selectById(takeId);
        if (t == null) throw new BusinessException("盘点单不存在");
        if (!DocStatus.DRAFT.getCode().equals(t.getStatus())) throw new BusinessException("只有草稿状态可保存实盘数量");
        if (items == null) return;
        for (InventoryStockTakeItem in : items) {
            if (in.getId() == null) continue;
            InventoryStockTakeItem u = new InventoryStockTakeItem();
            u.setId(in.getId());
            u.setActualQuantity(in.getActualQuantity() != null ? in.getActualQuantity() : BigDecimal.ZERO);
            BigDecimal book = in.getBookQuantity() != null ? in.getBookQuantity() : BigDecimal.ZERO;
            u.setDiffQuantity(u.getActualQuantity().subtract(book));
            u.setRemark(in.getRemark());
            itemMapper.updateById(u);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        InventoryStockTake t = takeMapper.selectById(id);
        if (t == null) throw new BusinessException("盘点单不存在");
        // P2-29：原子抢占 DRAFT→AUDITED，避免并发/双击重复按差异调整库存
        if (!DocStatusGuard.claim(takeMapper, InventoryStockTake::getId, id,
                InventoryStockTake::getStatus, DocStatus.DRAFT.getCode(), DocStatus.AUDITED.getCode())) {
            throw new BusinessException("只有草稿状态可审核");
        }
        applyDiff(t, false);
        InventoryStockTake u = new InventoryStockTake();
        u.setId(id);
        u.setStatus(DocStatus.AUDITED.getCode());
        u.setAuditTime(LocalDateTime.now());
        takeMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unAudit(Long id) {
        InventoryStockTake t = takeMapper.selectById(id);
        if (t == null) throw new BusinessException("盘点单不存在");
        // P2-29：原子抢占 AUDITED→DRAFT，避免并发反审核重复冲回盘点差异
        if (!DocStatusGuard.claim(takeMapper, InventoryStockTake::getId, id,
                InventoryStockTake::getStatus, DocStatus.AUDITED.getCode(), DocStatus.DRAFT.getCode())) {
            throw new BusinessException("只有已审核状态可反审核");
        }
        applyDiff(t, true);
        // 审核信息必须用 UpdateWrapper 显式置 null：updateById 会忽略 null 字段，
        // 否则反审核回草稿后 audit_time/审核人仍残留，单据看着"像已审核"（审计信息失真）。
        takeMapper.update(null, new LambdaUpdateWrapper<InventoryStockTake>()
                .eq(InventoryStockTake::getId, id)
                .set(InventoryStockTake::getStatus, DocStatus.DRAFT.getCode())
                .set(InventoryStockTake::getAuditTime, null)
                .set(InventoryStockTake::getAuditorId, null)
                .set(InventoryStockTake::getAuditorName, null));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        InventoryStockTake t = takeMapper.selectById(id);
        if (t == null) throw new BusinessException("盘点单不存在");
        if (!DocStatus.DRAFT.getCode().equals(t.getStatus())) throw new BusinessException("只有草稿状态可作废");
        InventoryStockTake u = new InventoryStockTake();
        u.setId(id);
        u.setStatus(DocStatus.CANCELLED.getCode());
        takeMapper.updateById(u);
    }

    @Override
    public List<Map<String, Object>> takeStatus() {
        LocalDate today = LocalDate.now();
        String curYm = today.format(YM);
        LocalDate due = today.withDayOfMonth(today.lengthOfMonth()); // 应盘日 = 当月最后一天
        boolean remind = today.getDayOfMonth() >= REMIND_DAY;

        List<Warehouse> whs = warehouseMapper.selectList(new LambdaQueryWrapper<Warehouse>()
                .eq(Warehouse::getStatus, 1)
                .orderByAsc(Warehouse::getId));
        // 本月已审核的盘点单（仓库 -> 审核日期）
        Map<Long, InventoryStockTake> done = new LinkedHashMap<>();
        for (InventoryStockTake t : takeMapper.selectList(new LambdaQueryWrapper<InventoryStockTake>()
                .eq(InventoryStockTake::getPeriod, curYm)
                .eq(InventoryStockTake::getStatus, DocStatus.AUDITED.getCode()))) {
            done.put(t.getWarehouseId(), t);
        }
        // 各仓最近一次已审核盘点日期
        Map<Long, LocalDate> lastMap = new LinkedHashMap<>();
        for (InventoryStockTake t : takeMapper.selectList(new LambdaQueryWrapper<InventoryStockTake>()
                .eq(InventoryStockTake::getStatus, DocStatus.AUDITED.getCode())
                .orderByDesc(InventoryStockTake::getId))) {
            if (!lastMap.containsKey(t.getWarehouseId()) && t.getTakeDate() != null) {
                lastMap.put(t.getWarehouseId(), t.getTakeDate());
            }
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Warehouse w : whs) {
            boolean taken = done.containsKey(w.getId());
            int overdue = taken ? 0 : (int) java.time.temporal.ChronoUnit.DAYS.between(due, today);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("warehouseId", w.getId());
            m.put("warehouseName", w.getWarehouseName());
            m.put("warehouseType", w.getWarehouseType());
            m.put("warehouseCategory", w.getWarehouseCategory());
            m.put("period", curYm);
            m.put("taken", taken);
            m.put("lastTakeDate", lastMap.get(w.getId()));
            m.put("dueDate", due);
            m.put("overdueDays", Math.max(0, overdue));
            m.put("remind", remind && !taken);
            rows.add(m);
        }
        return rows;
    }

    // ==================== 内部实现 ====================

    /**
     * 按差异调整库存；reverse=true 时反向冲回。
     *
     * <p>【C3 口径 · 2026-09-12 定稿：刻意复用，不再改动】审核/反审核**共用** {@code STOCK_TAKE_IN/STOCK_TAKE_OUT}：
     * 这两个 code 表意的是"库存因盘点增加/减少"（盘盈/盘亏方向），不表意"审核/反审核"；
     * 反审核只是把 delta 取反（原盘盈的反审核会写成 {@code STOCK_TAKE_OUT} 的负数），业务动作可用
     * {@code related_bill_id}（盘点单）+ 盘点单自身的差异正负还原。不新增 {@code STOCK_TAKE_UN_AUDIT_*}：
     * 历史流水无法回填新 code，报表反而要同时认两套。</p>
     */
    private void applyDiff(InventoryStockTake t, boolean reverse) {
        List<InventoryStockTakeItem> items = getItems(t.getId());
        for (InventoryStockTakeItem it : items) {
            BigDecimal diff = it.getDiffQuantity() != null ? it.getDiffQuantity() : BigDecimal.ZERO;
            if (diff.compareTo(BigDecimal.ZERO) == 0) continue;
            BigDecimal delta = reverse ? diff.negate() : diff;
            if (it.getProductId() != null) {
                stockService.changeStock(t.getWarehouseId(), it.getProductId(), delta,
                        delta.compareTo(BigDecimal.ZERO) > 0 ? StockChangeType.STOCK_TAKE_IN : StockChangeType.STOCK_TAKE_OUT,
                        t.getTakeNo(), RelatedBillType.STOCK_TAKE, null, t.getId(), it.getQualityType());
                // 盘盈无单价：成本为空时用最近进价兜底，避免"有库存无成本"
                if (delta.compareTo(BigDecimal.ZERO) > 0) costService.fillProductCostIfEmpty(it.getProductId());
            } else if (it.getMaterialId() != null) {
                stockService.changeMaterialStock(t.getWarehouseId(), it.getMaterialId(), delta,
                        delta.compareTo(BigDecimal.ZERO) > 0 ? StockChangeType.STOCK_TAKE_IN.getCode() : StockChangeType.STOCK_TAKE_OUT.getCode(),
                        t.getTakeNo(), RelatedBillType.STOCK_TAKE, null, t.getId());
                if (delta.compareTo(BigDecimal.ZERO) > 0) costService.fillMaterialCostIfEmpty(it.getMaterialId());
            }
        }
        log.info("盘点{} {} 差异调整 {} 行", reverse ? "反审核回滚" : "审核", t.getTakeNo(), items.size());
    }

    private String genNo() {
        String d = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String pat = BillPrefix.STOCK_TAKE + d;
        InventoryStockTake last = takeMapper.selectOne(new LambdaQueryWrapper<InventoryStockTake>()
                .likeRight(InventoryStockTake::getTakeNo, pat)
                .orderByDesc(InventoryStockTake::getTakeNo).last("LIMIT 1"));
        int seq = 1;
        if (last != null && last.getTakeNo() != null) {
            try {
                seq = Integer.parseInt(last.getTakeNo().substring(last.getTakeNo().length() - 3)) + 1;
            } catch (Exception e) {
                seq = 1;
            }
        }
        return BillPrefix.STOCK_TAKE + d + String.format("%03d", seq);
    }
}
