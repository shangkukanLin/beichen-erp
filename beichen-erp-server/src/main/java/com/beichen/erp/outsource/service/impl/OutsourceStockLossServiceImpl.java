package com.beichen.erp.outsource.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.auth.entity.User;
import com.beichen.erp.auth.mapper.UserMapper;
import com.beichen.erp.common.BillPrefix;
import com.beichen.erp.common.DocStatus;
import com.beichen.erp.common.DocStatusGuard;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.dev.entity.MaterialType;
import com.beichen.erp.dev.mapper.MaterialTypeMapper;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.inventory.common.LossReason;
import com.beichen.erp.inventory.common.RelatedBillType;
import com.beichen.erp.inventory.common.StockChangeType;
import com.beichen.erp.outsource.common.QualityType;
import com.beichen.erp.outsource.entity.OutsourceMaterial;
import com.beichen.erp.outsource.entity.OutsourceStockLoss;
import com.beichen.erp.outsource.entity.OutsourceStockLossItem;
import com.beichen.erp.outsource.mapper.OutsourceMaterialMapper;
import com.beichen.erp.outsource.mapper.OutsourceStockLossItemMapper;
import com.beichen.erp.outsource.mapper.OutsourceStockLossMapper;
import com.beichen.erp.outsource.service.OutsourceStockLossService;
import com.beichen.erp.warehouse.entity.Warehouse;
import com.beichen.erp.warehouse.mapper.WarehouseMapper;
import com.beichen.erp.warehouse.service.WarehouseStockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 委外物料报损单：草稿 → 审核（扣物料库存）→ 反审核（加回）。
 * <p>与成品报损的区别：主体是 outsource_material，且物料库存不区分品质
 * （唯一键为 仓库+物料，quality_type 固定 GOOD），扣减走 changeMaterialStock。</p>
 */
@Service
@RequiredArgsConstructor
public class OutsourceStockLossServiceImpl implements OutsourceStockLossService {

    private final OutsourceStockLossMapper lossMapper;
    private final OutsourceStockLossItemMapper itemMapper;
    private final WarehouseStockService stockService;
    private final OutsourceMaterialMapper materialMapper;
    private final MaterialTypeMapper materialTypeMapper;
    private final WarehouseMapper warehouseMapper;
    private final UserMapper userMapper;

    @Override
    public Page<OutsourceStockLoss> page(String status, Long warehouseId, String lossReason, String keyword,
                                         String startDate, String endDate, int pageNum, int pageSize) {
        LambdaQueryWrapper<OutsourceStockLoss> w = new LambdaQueryWrapper<OutsourceStockLoss>()
                .eq(status != null && !status.isBlank(), OutsourceStockLoss::getStatus, status)
                .eq(warehouseId != null, OutsourceStockLoss::getWarehouseId, warehouseId)
                .eq(lossReason != null && !lossReason.isBlank(), OutsourceStockLoss::getLossReason, lossReason)
                .and(keyword != null && !keyword.isBlank(), q -> q
                        .like(OutsourceStockLoss::getCode, keyword)
                        .or().like(OutsourceStockLoss::getRemark, keyword))
                .ge(startDate != null && !startDate.isBlank(), OutsourceStockLoss::getLossDate, startDate)
                .le(endDate != null && !endDate.isBlank(), OutsourceStockLoss::getLossDate, endDate)
                .orderByDesc(OutsourceStockLoss::getId);
        Page<OutsourceStockLoss> p = lossMapper.selectPage(new Page<>(pageNum, pageSize), w);
        // F7-82（2026-09-20 性能专项）：列表填充改为**整页批量**（原逐行 1 仓 + 1 明细 + 每明细 1 物料
        // ⇒ 一页 10 行 ≈ 20~30 次查询；现为固定 3 次）。单条 getById 仍走 fillView。
        fillViewBatch(p.getRecords());
        return p;
    }

    /**
     * F7-82（2026-09-20 性能专项）：**整页**填充列表展示字段（仓库名 + 明细概况），与逐条 {@link #fillView}
     * 结果完全一致，只是把查询收敛为固定 3 次（仓库 / 明细 / 物料名）。
     */
    private void fillViewBatch(List<OutsourceStockLoss> records) {
        if (records == null || records.isEmpty()) return;
        // 1) 仓库名：仅对"缺名且有仓ID"的行补（与 fillView 的判定一致）
        java.util.Set<Long> whIds = new java.util.HashSet<>();
        for (OutsourceStockLoss r : records) {
            if ((r.getWarehouseName() == null || r.getWarehouseName().isBlank()) && r.getWarehouseId() != null) {
                whIds.add(r.getWarehouseId());
            }
        }
        Map<Long, String> whNameMap = new java.util.HashMap<>();
        if (!whIds.isEmpty()) {
            for (Warehouse wh : warehouseMapper.selectBatchIds(whIds)) {
                whNameMap.put(wh.getId(), wh.getWarehouseName());
            }
        }
        // 2) 明细：一次取全页
        java.util.Map<Long, List<OutsourceStockLossItem>> itemsByLoss = new java.util.LinkedHashMap<>();
        java.util.Set<Long> matIds = new java.util.HashSet<>();
        List<Long> lossIds = records.stream().map(OutsourceStockLoss::getId).collect(java.util.stream.Collectors.toList());
        if (!lossIds.isEmpty()) {
            for (OutsourceStockLossItem it : itemMapper.selectList(
                    new LambdaQueryWrapper<OutsourceStockLossItem>().in(OutsourceStockLossItem::getLossId, lossIds))) {
                itemsByLoss.computeIfAbsent(it.getLossId(), k -> new ArrayList<>()).add(it);
                if ((it.getMaterialName() == null || it.getMaterialName().isBlank()) && it.getMaterialId() != null) {
                    matIds.add(it.getMaterialId());
                }
            }
        }
        // 3) 物料名：仅对明细里缺名的物料批量补
        Map<Long, String> matNameMap = new java.util.HashMap<>();
        if (!matIds.isEmpty()) {
            for (OutsourceMaterial m : materialMapper.selectBatchIds(matIds)) {
                matNameMap.put(m.getId(), m.getMaterialName());
            }
        }
        for (OutsourceStockLoss r : records) {
            if ((r.getWarehouseName() == null || r.getWarehouseName().isBlank()) && r.getWarehouseId() != null) {
                r.setWarehouseName(whNameMap.get(r.getWarehouseId()));
            }
            r.setItemSummary(buildSummary(itemsByLoss.getOrDefault(r.getId(), List.of()), matNameMap));
        }
    }

    /** 明细概况拼串（纯计算，无查询）：物料名×数量，顿号分隔；与 {@link #buildItemSummary} 同逻辑 */
    private String buildSummary(List<OutsourceStockLossItem> items, Map<Long, String> matNameMap) {
        StringBuilder sb = new StringBuilder();
        for (OutsourceStockLossItem it : items) {
            if (sb.length() > 0) sb.append("、");
            String name = it.getMaterialName();
            if (name == null || name.isBlank()) {
                name = it.getMaterialId() != null ? matNameMap.get(it.getMaterialId()) : null;
                if (name == null || name.isBlank()) name = "-";
            }
            BigDecimal qty = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
            sb.append(name).append("×").append(qty.stripTrailingZeros().toPlainString());
        }
        return sb.toString();
    }

    /** 列表展示字段：补仓库名与明细概况（状态/原因的中文由前端按 code 映射，后端不再回中文） */
    private void fillView(OutsourceStockLoss r) {
        if ((r.getWarehouseName() == null || r.getWarehouseName().isBlank()) && r.getWarehouseId() != null) {
            Warehouse wh = warehouseMapper.selectById(r.getWarehouseId());
            if (wh != null) r.setWarehouseName(wh.getWarehouseName());
        }
        r.setItemSummary(buildItemSummary(r.getId()));
    }

    /** 明细概况：物料名称×数量，顿号分隔 */
    private String buildItemSummary(Long lossId) {
        List<OutsourceStockLossItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<OutsourceStockLossItem>().eq(OutsourceStockLossItem::getLossId, lossId));
        StringBuilder sb = new StringBuilder();
        for (OutsourceStockLossItem it : items) {
            if (sb.length() > 0) sb.append("、");
            String name = it.getMaterialName();
            if (name == null || name.isBlank()) {
                OutsourceMaterial m = it.getMaterialId() != null ? materialMapper.selectById(it.getMaterialId()) : null;
                name = m != null ? m.getMaterialName() : "-";
            }
            BigDecimal qty = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
            sb.append(name).append("×").append(qty.stripTrailingZeros().toPlainString());
        }
        return sb.toString();
    }

    @Override
    public OutsourceStockLoss getById(Long id) {
        OutsourceStockLoss loss = lossMapper.selectById(id);
        if (loss != null) fillView(loss);
        return loss;
    }

    @Override
    public List<OutsourceStockLossItem> getItems(Long lossId) {
        List<OutsourceStockLossItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<OutsourceStockLossItem>().eq(OutsourceStockLossItem::getLossId, lossId)
                        .orderByAsc(OutsourceStockLossItem::getId));
        // 品质中文由前端按 code 映射（QualityTypeLabel），后端不回中文
        return items;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(OutsourceStockLoss loss, List<OutsourceStockLossItem> items) {
        if (loss.getWarehouseId() == null) throw new BusinessException("仓库不能为空");
        List<OutsourceStockLossItem> valid = validItems(items);
        if (valid.isEmpty()) throw new BusinessException("请添加报损明细（数量需大于 0）");
        loss.setCode(gen(BillPrefix.OUTSOURCE_STOCK_LOSS));
        loss.setStatus(DocStatus.DRAFT.getCode());
        if (loss.getLossDate() == null) loss.setLossDate(LocalDate.now());
        if (loss.getLossReason() != null && !LossReason.isValid(loss.getLossReason())) {
            throw new BusinessException("报损原因不合法：" + loss.getLossReason());
        }
        Warehouse wh = warehouseMapper.selectById(loss.getWarehouseId());
        loss.setWarehouseName(wh != null ? wh.getWarehouseName() : null);
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) loss.setCompanyId(cid);
        loss.setTotalAmount(calcTotal(valid));
        lossMapper.insert(loss);
        saveItems(loss.getId(), valid, cid);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(OutsourceStockLoss loss, List<OutsourceStockLossItem> items) {
        OutsourceStockLoss old = lossMapper.selectById(loss.getId());
        if (old == null) throw new BusinessException("报损单不存在");
        if (!DocStatus.DRAFT.getCode().equals(old.getStatus())) throw new BusinessException("仅草稿状态可编辑");
        List<OutsourceStockLossItem> valid = validItems(items);
        if (valid.isEmpty()) throw new BusinessException("请添加报损明细（数量需大于 0）");

        loss.setCode(old.getCode());
        loss.setStatus(DocStatus.DRAFT.getCode());
        Warehouse wh = loss.getWarehouseId() != null ? warehouseMapper.selectById(loss.getWarehouseId()) : null;
        loss.setWarehouseName(wh != null ? wh.getWarehouseName() : old.getWarehouseName());
        Long cid = CompanyContext.get();
        loss.setTotalAmount(calcTotal(valid));
        lossMapper.updateById(loss);

        itemMapper.delete(new LambdaQueryWrapper<OutsourceStockLossItem>()
                .eq(OutsourceStockLossItem::getLossId, loss.getId()));
        saveItems(loss.getId(), valid, cid);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        OutsourceStockLoss old = lossMapper.selectById(id);
        if (old == null) throw new BusinessException("报损单不存在");
        // F7-50（2026-09-19）：原子抢占 DRAFT→CANCELLED（原"先查后改"可与 audit 并发互覆）
        if (!DocStatusGuard.claim(lossMapper, OutsourceStockLoss::getId, id,
                OutsourceStockLoss::getStatus, DocStatus.DRAFT.getCode(), DocStatus.CANCELLED.getCode()))
            throw new BusinessException("仅草稿状态可作废，已审核单据请先反审核");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        OutsourceStockLoss loss = lossMapper.selectById(id);
        if (loss == null) throw new BusinessException("报损单不存在");
        // P2-29：原子抢占 DRAFT→AUDITED，避免并发/双击重复扣减物料库存
        if (!DocStatusGuard.claim(lossMapper, OutsourceStockLoss::getId, id,
                OutsourceStockLoss::getStatus, DocStatus.DRAFT.getCode(), DocStatus.AUDITED.getCode())) {
            throw new BusinessException("仅草稿状态可审核");
        }
        List<OutsourceStockLossItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<OutsourceStockLossItem>().eq(OutsourceStockLossItem::getLossId, id));
        if (items.isEmpty()) throw new BusinessException("报损单无明细，无法审核");
        checkStockBeforeLoss(loss, items);
        applyStock(loss, items);

        OutsourceStockLoss u = new OutsourceStockLoss();
        u.setId(id); u.setStatus(DocStatus.AUDITED.getCode());
        u.setAuditorId(getCurrentUserId()); u.setAuditorName(getCurrentUserName());
        u.setAuditTime(LocalDateTime.now());
        lossMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unAudit(Long id) {
        OutsourceStockLoss loss = lossMapper.selectById(id);
        if (loss == null) throw new BusinessException("报损单不存在");
        // P2-29：原子抢占 AUDITED→DRAFT，避免并发反审核重复回补物料库存
        if (!DocStatusGuard.claim(lossMapper, OutsourceStockLoss::getId, id,
                OutsourceStockLoss::getStatus, DocStatus.AUDITED.getCode(), DocStatus.DRAFT.getCode())) {
            throw new BusinessException("仅已审核状态可反审核");
        }
        List<OutsourceStockLossItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<OutsourceStockLossItem>().eq(OutsourceStockLossItem::getLossId, id));
        revertStock(loss, items);

        // 审核信息必须用 UpdateWrapper 显式置 null：updateById 会忽略 null 字段，
        // 否则反审核回草稿后仍显示审核人/审核时间（审计信息失真，与成品报损同源修复）。
        lossMapper.update(null, new LambdaUpdateWrapper<OutsourceStockLoss>()
                .eq(OutsourceStockLoss::getId, id)
                .set(OutsourceStockLoss::getStatus, DocStatus.DRAFT.getCode())
                .set(OutsourceStockLoss::getAuditorId, null)
                .set(OutsourceStockLoss::getAuditorName, null)
                .set(OutsourceStockLoss::getAuditTime, null));
    }

    /** 报损前库存校验：物料库存不区分品质，按 仓库+物料 查可用量，一次列清所有不足项 */
    private void checkStockBeforeLoss(OutsourceStockLoss loss, List<OutsourceStockLossItem> items) {
        // F7-82（2026-09-20 性能专项）：库存**一次批量取**（原逐明细 getMaterialQuantity），
        // 物料名也**一次批量取**（原仅在"不足"时逐条 selectById）。判定与报错文案完全不变。
        java.util.Set<Long> matIds = new java.util.HashSet<>();
        for (OutsourceStockLossItem it : items) {
            if (it.getMaterialId() != null) matIds.add(it.getMaterialId());
        }
        Map<Long, BigDecimal> availMap = stockService.getMaterialQuantities(loss.getWarehouseId(), matIds);
        Map<Long, String> matNameMap = new java.util.HashMap<>();
        List<Long> missing = new ArrayList<>();
        for (OutsourceStockLossItem it : items) {
            BigDecimal need = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
            if (need.compareTo(BigDecimal.ZERO) <= 0 || it.getMaterialId() == null) continue;
            BigDecimal avail = availMap.getOrDefault(it.getMaterialId(), BigDecimal.ZERO);
            if (avail.compareTo(need) < 0) missing.add(it.getMaterialId());
        }
        if (!missing.isEmpty()) {
            for (OutsourceMaterial m : materialMapper.selectBatchIds(missing)) {
                matNameMap.put(m.getId(), m.getMaterialName());
            }
        }
        List<String> shortage = new ArrayList<>();
        for (OutsourceStockLossItem it : items) {
            BigDecimal need = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
            if (need.compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal avail = availMap.getOrDefault(it.getMaterialId(), BigDecimal.ZERO);
            if (avail.compareTo(need) < 0) {
                String name = matNameMap.get(it.getMaterialId());
                shortage.add(String.format("%s（需 %s，库存 %s，缺 %s）",
                        name != null ? name : "ID=" + it.getMaterialId(),
                        need.stripTrailingZeros().toPlainString(),
                        avail.stripTrailingZeros().toPlainString(),
                        need.subtract(avail).stripTrailingZeros().toPlainString()));
            }
        }
        if (!shortage.isEmpty()) {
            throw new BusinessException("库存不足，无法报损：" + String.join("；", shortage)
                    + (shortage.size() > 5 ? " 等 " + shortage.size() + " 项" : ""));
        }
    }

    /** 审核：扣减物料库存（数量为负） */
    private void applyStock(OutsourceStockLoss loss, List<OutsourceStockLossItem> items) {
        for (OutsourceStockLossItem it : items) {
            BigDecimal q = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
            if (q.compareTo(BigDecimal.ZERO) <= 0) continue;
            stockService.changeMaterialStock(loss.getWarehouseId(), it.getMaterialId(), q.negate(),
                    StockChangeType.LOSS_OUT.getCode(), loss.getCode(), RelatedBillType.OUTSOURCE_STOCK_LOSS,
                    null, null, loss.getId());
        }
    }

    /** 反审核：把报损数量加回物料库存 */
    private void revertStock(OutsourceStockLoss loss, List<OutsourceStockLossItem> items) {
        for (OutsourceStockLossItem it : items) {
            BigDecimal q = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
            if (q.compareTo(BigDecimal.ZERO) <= 0) continue;
            stockService.changeMaterialStock(loss.getWarehouseId(), it.getMaterialId(), q,
                    StockChangeType.CANCEL_LOSS_OUT.getCode(), loss.getCode(), RelatedBillType.OUTSOURCE_STOCK_LOSS,
                    null, null, loss.getId());
        }
    }

    private List<OutsourceStockLossItem> validItems(List<OutsourceStockLossItem> items) {
        List<OutsourceStockLossItem> valid = new ArrayList<>();
        if (items == null) return valid;
        for (OutsourceStockLossItem it : items) {
            if (it == null || it.getMaterialId() == null) continue;
            if (it.getQuantity() == null || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;
            valid.add(it);
        }
        return valid;
    }

    /** 保存明细：回填物料档案快照、物料类型名与单价，计算金额 */
    private void saveItems(Long lossId, List<OutsourceStockLossItem> items, Long cid) {
        for (OutsourceStockLossItem it : items) {
            it.setId(null);
            it.setLossId(lossId);
            OutsourceMaterial m = it.getMaterialId() != null ? materialMapper.selectById(it.getMaterialId()) : null;
            if (m != null) {
                it.setMaterialName(m.getMaterialName());
                // F7-125（2026-09-20）：移除 `it.setSpec(m.getSpec())` —— 规格已下线（源字段已删）
                it.setUnit(m.getUnit());
                if (it.getMaterialTypeId() == null) it.setMaterialTypeId(m.getMaterialTypeId());
                // 单价未填时优先最近进价，其次物料单价
                if (it.getUnitPrice() == null) {
                    it.setUnitPrice(firstNonZero(m.getLastInPrice(), m.getPrice(), m.getCostPrice()));
                }
            }
            if (it.getMaterialTypeId() != null) {
                MaterialType bt = materialTypeMapper.selectById(it.getMaterialTypeId());
                if (bt != null) it.setMaterialTypeName(bt.getTypeName());
            }
            // 物料库存不区分品质，统一按 GOOD 记账（字段仅作冗余展示）
            if (it.getQualityType() == null || it.getQualityType().isBlank()) {
                it.setQualityType(QualityType.GOOD.getCode());
            }
            BigDecimal price = it.getUnitPrice() != null ? it.getUnitPrice() : BigDecimal.ZERO;
            it.setAmount(price.multiply(it.getQuantity()).setScale(2, RoundingMode.HALF_UP));
            if (cid != null && cid > 0) it.setCompanyId(cid);
            itemMapper.insert(it);
        }
    }

    private BigDecimal calcTotal(List<OutsourceStockLossItem> items) {
        BigDecimal total = BigDecimal.ZERO;
        for (OutsourceStockLossItem it : items) {
            BigDecimal q = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
            BigDecimal price = it.getUnitPrice() != null ? it.getUnitPrice() : BigDecimal.ZERO;
            total = total.add(price.multiply(q));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal firstNonZero(BigDecimal... vals) {
        if (vals == null) return BigDecimal.ZERO;
        for (BigDecimal v : vals) {
            if (v != null && v.compareTo(BigDecimal.ZERO) > 0) return v;
        }
        return BigDecimal.ZERO;
    }

    /** 单号：前缀 + yyyyMMdd + 3 位序号 */
    private String gen(String prefix) {
        String d = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String pat = prefix + d;
        OutsourceStockLoss last = lossMapper.selectOne(new LambdaQueryWrapper<OutsourceStockLoss>()
                .likeRight(OutsourceStockLoss::getCode, pat).orderByDesc(OutsourceStockLoss::getCode).last("LIMIT 1"));
        int seq = 1;
        if (last != null && last.getCode() != null) {
            // F7-75③/F7-81②（2026-09-20）：统一走 BillNoSeq（尾段连续数字解析 + 序号超 999 自动扩位）
            seq = 1;
        }
        String fullPrefix = prefix + d;
        seq = (last != null && last.getCode() != null)
                ? com.beichen.erp.common.BillNoSeq.lastSeq(last.getCode(), fullPrefix) + 1 : 1;
        return com.beichen.erp.common.BillNoSeq.format(fullPrefix, seq);
    }

    private Long getCurrentUserId() {
        try {
            return StpUtil.getLoginIdAsLong();
        } catch (Exception e) {
            return null;
        }
    }

    private String getCurrentUserName() {
        try {
            User user = userMapper.selectById(StpUtil.getLoginIdAsLong());
            return user != null ? user.getUsername() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
