package com.beichen.erp.inventory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.auth.entity.User;
import com.beichen.erp.auth.mapper.UserMapper;
import com.beichen.erp.common.BillPrefix;
import com.beichen.erp.common.DocStatus;
import com.beichen.erp.common.DocStatusGuard;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.inventory.common.LossReason;
import com.beichen.erp.inventory.common.RelatedBillType;
import com.beichen.erp.inventory.common.StockChangeType;
import com.beichen.erp.inventory.entity.InventoryStockLoss;
import com.beichen.erp.inventory.entity.InventoryStockLossItem;
import com.beichen.erp.inventory.mapper.InventoryStockLossItemMapper;
import com.beichen.erp.inventory.mapper.InventoryStockLossMapper;
import com.beichen.erp.inventory.service.InventoryStockLossService;
import com.beichen.erp.material.common.ProductQualityType;
import com.beichen.erp.material.entity.Product;
import com.beichen.erp.material.mapper.ProductMapper;
import com.beichen.erp.warehouse.entity.Warehouse;
import com.beichen.erp.warehouse.mapper.WarehouseMapper;
import com.beichen.erp.warehouse.service.WarehouseStockService;
import cn.dev33.satoken.stp.StpUtil;
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

/**
 * 成品报损单：草稿 → 审核（扣库存，写 LOSS_OUT 流水）→ 反审核（加回）。
 * 库存一律走 WarehouseStockService.changeStock，不直接改表，保证流水完整。
 */
@Service
@RequiredArgsConstructor
public class InventoryStockLossServiceImpl implements InventoryStockLossService {

    private final InventoryStockLossMapper lossMapper;
    private final InventoryStockLossItemMapper itemMapper;
    private final WarehouseStockService stockService;
    private final ProductMapper productMapper;
    private final WarehouseMapper warehouseMapper;
    private final UserMapper userMapper;

    @Override
    public Page<InventoryStockLoss> page(String status, Long warehouseId, String lossReason, String keyword,
                                         String startDate, String endDate, int pageNum, int pageSize) {
        LambdaQueryWrapper<InventoryStockLoss> w = new LambdaQueryWrapper<InventoryStockLoss>()
                .eq(status != null && !status.isBlank(), InventoryStockLoss::getStatus, status)
                .eq(warehouseId != null, InventoryStockLoss::getWarehouseId, warehouseId)
                .eq(lossReason != null && !lossReason.isBlank(), InventoryStockLoss::getLossReason, lossReason)
                .and(keyword != null && !keyword.isBlank(), q -> q
                        .like(InventoryStockLoss::getCode, keyword)
                        .or().like(InventoryStockLoss::getRemark, keyword))
                .ge(startDate != null && !startDate.isBlank(), InventoryStockLoss::getLossDate, startDate)
                .le(endDate != null && !endDate.isBlank(), InventoryStockLoss::getLossDate, endDate)
                .orderByDesc(InventoryStockLoss::getId);
        Page<InventoryStockLoss> p = lossMapper.selectPage(new Page<>(pageNum, pageSize), w);
        for (InventoryStockLoss r : p.getRecords()) fillView(r);
        return p;
    }

    /** 列表展示字段：补仓库名与明细概况（状态/原因的中文由前端按 code 映射，后端不再回中文） */
    private void fillView(InventoryStockLoss r) {
        if ((r.getWarehouseName() == null || r.getWarehouseName().isBlank()) && r.getWarehouseId() != null) {
            Warehouse wh = warehouseMapper.selectById(r.getWarehouseId());
            if (wh != null) r.setWarehouseName(wh.getWarehouseName());
        }
        r.setItemSummary(buildItemSummary(r.getId()));
    }

    /** 明细概况：产品名称×数量，顿号分隔（与其他出入库列表同风格） */
    private String buildItemSummary(Long lossId) {
        List<InventoryStockLossItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<InventoryStockLossItem>().eq(InventoryStockLossItem::getLossId, lossId));
        StringBuilder sb = new StringBuilder();
        for (InventoryStockLossItem it : items) {
            if (sb.length() > 0) sb.append("、");
            String name = it.getProductName();
            if (name == null || name.isBlank()) {
                Product p = it.getProductId() != null ? productMapper.selectById(it.getProductId()) : null;
                name = p != null ? p.getName() : "-";
            }
            BigDecimal qty = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
            sb.append(name).append("×").append(qty.stripTrailingZeros().toPlainString());
        }
        return sb.toString();
    }

    @Override
    public InventoryStockLoss getById(Long id) {
        InventoryStockLoss loss = lossMapper.selectById(id);
        if (loss != null) fillView(loss);
        return loss;
    }

    @Override
    public List<InventoryStockLossItem> getItems(Long lossId) {
        List<InventoryStockLossItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<InventoryStockLossItem>().eq(InventoryStockLossItem::getLossId, lossId)
                        .orderByAsc(InventoryStockLossItem::getId));
        // 品质中文由前端按 code 映射（ProductQualityTypeLabel），后端不回中文
        return items;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(InventoryStockLoss loss, List<InventoryStockLossItem> items) {
        if (loss.getWarehouseId() == null) throw new BusinessException("仓库不能为空");
        List<InventoryStockLossItem> valid = validItems(items);
        if (valid.isEmpty()) throw new BusinessException("请添加报损明细（数量需大于 0）");
        loss.setCode(gen(BillPrefix.INVENTORY_STOCK_LOSS));
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
    public void update(InventoryStockLoss loss, List<InventoryStockLossItem> items) {
        InventoryStockLoss old = lossMapper.selectById(loss.getId());
        if (old == null) throw new BusinessException("报损单不存在");
        if (!DocStatus.DRAFT.getCode().equals(old.getStatus())) throw new BusinessException("仅草稿状态可编辑");
        List<InventoryStockLossItem> valid = validItems(items);
        if (valid.isEmpty()) throw new BusinessException("请添加报损明细（数量需大于 0）");

        loss.setCode(old.getCode());
        loss.setStatus(DocStatus.DRAFT.getCode());
        Warehouse wh = loss.getWarehouseId() != null ? warehouseMapper.selectById(loss.getWarehouseId()) : null;
        loss.setWarehouseName(wh != null ? wh.getWarehouseName() : old.getWarehouseName());
        Long cid = CompanyContext.get();
        loss.setTotalAmount(calcTotal(valid));
        lossMapper.updateById(loss);

        itemMapper.delete(new LambdaQueryWrapper<InventoryStockLossItem>()
                .eq(InventoryStockLossItem::getLossId, loss.getId()));
        saveItems(loss.getId(), valid, cid);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        InventoryStockLoss old = lossMapper.selectById(id);
        if (old == null) throw new BusinessException("报损单不存在");
        // F7-50（2026-09-19）：原子抢占 DRAFT→CANCELLED（原"先查后改"可与 audit 并发互覆）
        if (!DocStatusGuard.claim(lossMapper, InventoryStockLoss::getId, id,
                InventoryStockLoss::getStatus, DocStatus.DRAFT.getCode(), DocStatus.CANCELLED.getCode()))
            throw new BusinessException("仅草稿状态可作废，已审核单据请先反审核");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        InventoryStockLoss loss = lossMapper.selectById(id);
        if (loss == null) throw new BusinessException("报损单不存在");
        // P2-29：原子抢占 DRAFT→AUDITED，避免并发/双击重复扣减库存
        if (!DocStatusGuard.claim(lossMapper, InventoryStockLoss::getId, id,
                InventoryStockLoss::getStatus, DocStatus.DRAFT.getCode(), DocStatus.AUDITED.getCode())) {
            throw new BusinessException("仅草稿状态可审核");
        }
        List<InventoryStockLossItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<InventoryStockLossItem>().eq(InventoryStockLossItem::getLossId, id));
        if (items.isEmpty()) throw new BusinessException("报损单无明细，无法审核");
        checkStockBeforeLoss(loss, items);
        applyStock(loss, items);

        InventoryStockLoss u = new InventoryStockLoss();
        u.setId(id); u.setStatus(DocStatus.AUDITED.getCode());
        u.setAuditorId(getCurrentUserId()); u.setAuditorName(getCurrentUserName());
        u.setAuditTime(LocalDateTime.now());
        lossMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unAudit(Long id) {
        InventoryStockLoss loss = lossMapper.selectById(id);
        if (loss == null) throw new BusinessException("报损单不存在");
        // P2-29：原子抢占 AUDITED→DRAFT，避免并发反审核重复回补库存
        if (!DocStatusGuard.claim(lossMapper, InventoryStockLoss::getId, id,
                InventoryStockLoss::getStatus, DocStatus.AUDITED.getCode(), DocStatus.DRAFT.getCode())) {
            throw new BusinessException("仅已审核状态可反审核");
        }
        List<InventoryStockLossItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<InventoryStockLossItem>().eq(InventoryStockLossItem::getLossId, id));
        revertStock(loss, items);

        // 审核信息必须用 UpdateWrapper 显式置 null：updateById 会忽略 null 字段，
        // 否则反审核回草稿后仍显示审核人/审核时间（审计信息失真）。
        lossMapper.update(null, new LambdaUpdateWrapper<InventoryStockLoss>()
                .eq(InventoryStockLoss::getId, id)
                .set(InventoryStockLoss::getStatus, DocStatus.DRAFT.getCode())
                .set(InventoryStockLoss::getAuditorId, null)
                .set(InventoryStockLoss::getAuditorName, null)
                .set(InventoryStockLoss::getAuditTime, null));
    }

    /**
     * 报损前库存校验：一次列清所有库存不足项。
     * changeStock 自身也会拦（SQL 带 quantity + delta >= 0），但报错只有「产品ID=xx」，
     * 用户看不出是哪个产品、差多少，这里前置检查给出产品名/品质/需量/库存/缺口。
     */
    private void checkStockBeforeLoss(InventoryStockLoss loss, List<InventoryStockLossItem> items) {
        List<String> shortage = new ArrayList<>();
        for (InventoryStockLossItem it : items) {
            BigDecimal need = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
            if (need.compareTo(BigDecimal.ZERO) <= 0) continue;
            String qt = it.getQualityType() != null && !it.getQualityType().isBlank()
                    ? it.getQualityType() : ProductQualityType.A.getCode();
            BigDecimal avail = stockService.getQuantity(loss.getWarehouseId(), it.getProductId(), qt);
            if (avail.compareTo(need) < 0) {
                Product p = it.getProductId() != null ? productMapper.selectById(it.getProductId()) : null;
                ProductQualityType type = ProductQualityType.of(qt);
                shortage.add(String.format("%s（%s，需 %s，库存 %s，缺 %s）",
                        p != null ? p.getName() : "ID=" + it.getProductId(),
                        type != null ? type.getLabel() : qt,
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

    /** 审核：按明细扣减库存（报损数量取负） */
    private void applyStock(InventoryStockLoss loss, List<InventoryStockLossItem> items) {
        for (InventoryStockLossItem it : items) {
            BigDecimal q = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
            if (q.compareTo(BigDecimal.ZERO) <= 0) continue;
            String qt = it.getQualityType() != null && !it.getQualityType().isBlank()
                    ? it.getQualityType() : ProductQualityType.A.getCode();
            stockService.changeStock(loss.getWarehouseId(), it.getProductId(), q.negate(),
                    StockChangeType.LOSS_OUT, loss.getCode(), RelatedBillType.INVENTORY_STOCK_LOSS,
                    "", loss.getId(), qt);
        }
    }

    /** 反审核：把报损数量加回库存 */
    private void revertStock(InventoryStockLoss loss, List<InventoryStockLossItem> items) {
        for (InventoryStockLossItem it : items) {
            BigDecimal q = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
            if (q.compareTo(BigDecimal.ZERO) <= 0) continue;
            String qt = it.getQualityType() != null && !it.getQualityType().isBlank()
                    ? it.getQualityType() : ProductQualityType.A.getCode();
            stockService.changeStock(loss.getWarehouseId(), it.getProductId(), q,
                    StockChangeType.CANCEL_LOSS_OUT, loss.getCode(), RelatedBillType.INVENTORY_STOCK_LOSS,
                    "", loss.getId(), qt);
        }
    }

    /** 过滤出数量大于 0 的有效明细 */
    private List<InventoryStockLossItem> validItems(List<InventoryStockLossItem> items) {
        List<InventoryStockLossItem> valid = new ArrayList<>();
        if (items == null) return valid;
        for (InventoryStockLossItem it : items) {
            if (it == null || it.getProductId() == null) continue;
            if (it.getQuantity() == null || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;
            valid.add(it);
        }
        return valid;
    }

    /** 保存明细：回填产品档案快照与单价，计算金额 */
    private void saveItems(Long lossId, List<InventoryStockLossItem> items, Long cid) {
        for (InventoryStockLossItem it : items) {
            it.setId(null);
            it.setLossId(lossId);
            Product p = it.getProductId() != null ? productMapper.selectById(it.getProductId()) : null;
            if (p != null) {
                it.setProductName(p.getName());
                it.setSku(p.getSku());
                it.setUnit(p.getUnit());
                // 单价未填时用产品成本价，成本价为空再退到最近进价
                if (it.getUnitPrice() == null) {
                    it.setUnitPrice(firstNonZero(p.getCostPrice(), p.getLastInPrice()));
                }
            }
            if (it.getQualityType() == null || it.getQualityType().isBlank()) {
                it.setQualityType(ProductQualityType.A.getCode());
            }
            BigDecimal price = it.getUnitPrice() != null ? it.getUnitPrice() : BigDecimal.ZERO;
            it.setAmount(price.multiply(it.getQuantity()).setScale(2, RoundingMode.HALF_UP));
            if (cid != null && cid > 0) it.setCompanyId(cid);
            itemMapper.insert(it);
        }
    }

    /** 明细金额合计 */
    private BigDecimal calcTotal(List<InventoryStockLossItem> items) {
        BigDecimal total = BigDecimal.ZERO;
        for (InventoryStockLossItem it : items) {
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

    /** 单号：前缀 + yyyyMMdd + 3 位序号，按当天已有最大号递增 */
    private String gen(String prefix) {
        String d = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String pat = prefix + d;
        InventoryStockLoss last = lossMapper.selectOne(new LambdaQueryWrapper<InventoryStockLoss>()
                .likeRight(InventoryStockLoss::getCode, pat).orderByDesc(InventoryStockLoss::getCode).last("LIMIT 1"));
        int seq = 1;
        if (last != null && last.getCode() != null) {
            try {
                seq = Integer.parseInt(last.getCode().substring(last.getCode().length() - 3)) + 1;
            } catch (Exception e) {
                seq = 1;
            }
        }
        return prefix + d + String.format("%03d", seq);
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
