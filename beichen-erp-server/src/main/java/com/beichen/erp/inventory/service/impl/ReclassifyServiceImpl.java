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
import com.beichen.erp.inventory.entity.InventoryProductReclassify;
import com.beichen.erp.inventory.entity.InventoryProductReclassifyItem;
import com.beichen.erp.inventory.mapper.InventoryProductReclassifyMapper;
import com.beichen.erp.inventory.mapper.InventoryProductReclassifyItemMapper;
import com.beichen.erp.warehouse.service.WarehouseStockService;
import com.beichen.erp.inventory.service.ReclassifyService;
import com.beichen.erp.material.common.ProductQualityType;
import com.beichen.erp.material.entity.Product;
import com.beichen.erp.material.mapper.ProductMapper;
import com.beichen.erp.material.service.ProductService;
import com.beichen.erp.auth.entity.User;
import com.beichen.erp.auth.mapper.UserMapper;
import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ReclassifyServiceImpl implements ReclassifyService {

    private final InventoryProductReclassifyMapper rcMapper;
    private final InventoryProductReclassifyItemMapper itemMapper;
    private final UserMapper userMapper;
    private final WarehouseStockService stockService;
    private final ProductMapper productMapper;
    private final ProductService productService;

    @Override
    public Page<Map<String, Object>> page(String status, Long warehouseId, int pageNum, int pageSize) {
        LambdaQueryWrapper<InventoryProductReclassify> w = new LambdaQueryWrapper<InventoryProductReclassify>()
                .eq(status != null && !status.isBlank(), InventoryProductReclassify::getStatus, status)
                .eq(warehouseId != null, InventoryProductReclassify::getWarehouseId, warehouseId)
                .orderByDesc(InventoryProductReclassify::getId);
        Page<InventoryProductReclassify> raw = rcMapper.selectPage(new Page<>(pageNum, pageSize), w);
        Page<Map<String, Object>> res = new Page<>(pageNum, pageSize, raw.getTotal());
        res.setRecords(raw.getRecords().stream().map(o -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", o.getId()); m.put("code", o.getCode());
            m.put("warehouseId", o.getWarehouseId());
            m.put("reclassifyDate", o.getReclassifyDate()); m.put("status", o.getStatus());
            m.put("remark", o.getRemark()); m.put("createTime", o.getCreateTime());
            m.put("createBy", o.getCreateBy()); m.put("createByName", o.getCreateByName());
            // 概况：产品名 + 品质转换 + 数量，供列表直接展示，免去前端逐条拉明细
            m.put("itemSummary", buildItemSummary(o.getId()));
            return m;
        }).toList());
        return res;
    }

    /** 明细概况：产品名 原品质→目标品质×数量，顿号分隔（与成品其他出入库列表 itemSummary 同风格） */
    private String buildItemSummary(Long reclassifyId) {
        List<InventoryProductReclassifyItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<InventoryProductReclassifyItem>()
                        .eq(InventoryProductReclassifyItem::getReclassifyId, reclassifyId));
        StringBuilder sb = new StringBuilder();
        for (InventoryProductReclassifyItem it : items) {
            Product p = it.getProductId() != null ? productMapper.selectById(it.getProductId()) : null;
            BigDecimal qty = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
            if (sb.length() > 0) sb.append("、");
            sb.append(p != null ? p.getName() : "-")
                    .append(" ").append(it.getFromQuality()).append("→").append(it.getToQuality())
                    .append("×").append(qty.stripTrailingZeros().toPlainString());
        }
        return sb.toString();
    }

    @Override
    public InventoryProductReclassify getById(Long id) { return rcMapper.selectById(id); }

    @Override
    public List<InventoryProductReclassifyItem> getItems(Long reclassifyId) {
        List<InventoryProductReclassifyItem> items = itemMapper.selectList(new LambdaQueryWrapper<InventoryProductReclassifyItem>()
                .eq(InventoryProductReclassifyItem::getReclassifyId, reclassifyId));
        // 回填 SKU（非表字段），前端免查库即可展示
        productService.fillSku(items, InventoryProductReclassifyItem::getProductId, InventoryProductReclassifyItem::setSku);
        return items;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(InventoryProductReclassify rc, List<InventoryProductReclassifyItem> items) {
        if (rc.getWarehouseId() == null) throw new BusinessException("仓库不能为空");
        rc.setCode(gen(BillPrefix.RECLASSIFY));
        rc.setStatus(DocStatus.DRAFT.getCode());
        // 整理人=建单时的登录账户（后续编辑/审核不覆盖，保留原始整理人）
        rc.setCreateBy(getCurrentUserId());
        rc.setCreateByName(getCurrentUserName());
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) rc.setCompanyId(cid);
        rcMapper.insert(rc);
        for (InventoryProductReclassifyItem it : items) {
            if (it.getFromQuality() == null || it.getToQuality() == null)
                throw new BusinessException("原品质和目标品质不能为空");
            if (it.getFromQuality().equals(it.getToQuality()))
                throw new BusinessException("原品质和目标品质不能相同");
            it.setId(null);
            it.setReclassifyId(rc.getId());
            if (cid != null && cid > 0) it.setCompanyId(cid);
            itemMapper.insert(it);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(InventoryProductReclassify rc, List<InventoryProductReclassifyItem> items) {
        InventoryProductReclassify old = rcMapper.selectById(rc.getId());
        if (old == null) throw new BusinessException("品质重分类单不存在");
        if (DocStatus.AUDITED.getCode().equals(old.getStatus())) throw new BusinessException("已审核的单据不可编辑");
        // 已作废单据不可编辑：update 会把状态写回 DRAFT，等于让作废单"复活"（作废语义失效）
        if (DocStatus.CANCELLED.getCode().equals(old.getStatus())) throw new BusinessException("已作废的单据不可编辑");

        // 草稿状态更新：直接删旧明细 + 插新
        rc.setCode(old.getCode()); rc.setStatus(DocStatus.DRAFT.getCode());
        rcMapper.updateById(rc);

        itemMapper.delete(new LambdaQueryWrapper<InventoryProductReclassifyItem>()
                .eq(InventoryProductReclassifyItem::getReclassifyId, rc.getId()));
        Long cid = CompanyContext.get();
        for (InventoryProductReclassifyItem it : items) {
            it.setId(null); it.setReclassifyId(rc.getId());
            if (cid != null && cid > 0) it.setCompanyId(cid);
            if (it.getFromQuality().equals(it.getToQuality()))
                throw new BusinessException("原品质和目标品质不能相同");
            itemMapper.insert(it);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        InventoryProductReclassify rc = rcMapper.selectById(id);
        if (rc == null) throw new BusinessException("品质重分类单不存在");
        // P2-29：原子抢占 DRAFT→AUDITED，避免并发/双击重复调整品质库存
        if (!DocStatusGuard.claim(rcMapper, InventoryProductReclassify::getId, id,
                InventoryProductReclassify::getStatus, DocStatus.DRAFT.getCode(), DocStatus.AUDITED.getCode())) {
            throw new BusinessException("只有草稿状态可审核");
        }
        List<InventoryProductReclassifyItem> items = getItems(id);
        if (items.isEmpty()) throw new BusinessException("重分类明细不能为空");
        // P2-33：数量必须为正（原先 <=0 被下面的循环静默跳过 → 零/负数量单也能"审核通过"）
        for (InventoryProductReclassifyItem it : items) {
            if (it.getQuantity() == null || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0)
                throw new BusinessException("重分类数量必须大于 0（明细行ID=" + it.getId() + "）");
        }
        // 审核前校验原品质库存：一次列清所有不足项，避免落到 changeStock 只报「产品ID=xx」
        checkStockBeforeAudit(rc, items);

        // 执行库存变更：from_quality 扣减，to_quality 增加
        for (InventoryProductReclassifyItem it : items) {
            if (it.getQuantity() == null || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;
            Product prod = it.getProductId() != null ? productMapper.selectById(it.getProductId()) : null;
            // 扣减原品质
            stockService.changeStock(rc.getWarehouseId(), prod != null ? prod.getName() : "",
                    it.getQuantity().negate(), StockChangeType.RECLASSIFY_OUT, rc.getCode(),
                    RelatedBillType.PRODUCT_RECLASSIFY, it.getProductId(),
                    "", rc.getId(), it.getFromQuality());
            // 增加目标品质
            stockService.changeStock(rc.getWarehouseId(), prod != null ? prod.getName() : "",
                    it.getQuantity(), StockChangeType.RECLASSIFY_IN, rc.getCode(),
                    RelatedBillType.PRODUCT_RECLASSIFY, it.getProductId(),
                    "", rc.getId(), it.getToQuality());
        }
        InventoryProductReclassify u = new InventoryProductReclassify(); u.setId(id); u.setStatus(DocStatus.AUDITED.getCode());
        rcMapper.updateById(u);
    }

    /**
     * 审核前校验「原品质」的可用库存（目标品质是增加库存，无需校验）。
     * changeStock 本身也会拦（SQL 带 quantity + delta >= 0），但报错只有「产品ID=xx」，
     * 用户看不出是哪个产品、差多少。这里前置一次性检查全部明细，给出产品名/品质/需量/库存/缺口。
     */
    private void checkStockBeforeAudit(InventoryProductReclassify rc, List<InventoryProductReclassifyItem> items) {
        List<String> shortage = new ArrayList<>();
        for (InventoryProductReclassifyItem it : items) {
            BigDecimal need = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
            if (need.compareTo(BigDecimal.ZERO) <= 0) continue;
            String fq = it.getFromQuality() != null && !it.getFromQuality().isBlank()
                    ? it.getFromQuality() : ProductQualityType.A.getCode();
            BigDecimal avail = stockService.getQuantity(rc.getWarehouseId(), it.getProductId(), fq);
            if (avail.compareTo(need) < 0) {
                Product p = it.getProductId() != null ? productMapper.selectById(it.getProductId()) : null;
                shortage.add(String.format("%s（%s规，需 %s，库存 %s，缺 %s）",
                        p != null ? p.getName() : "ID=" + it.getProductId(),
                        fq,
                        need.stripTrailingZeros().toPlainString(),
                        avail.stripTrailingZeros().toPlainString(),
                        need.subtract(avail).stripTrailingZeros().toPlainString()));
            }
        }
        if (!shortage.isEmpty()) {
            throw new BusinessException("原品质库存不足，无法审核：" + String.join("；", shortage)
                    + (shortage.size() > 5 ? " 等 " + shortage.size() + " 项" : ""));
        }
    }

    /**
     * 作废（E2 口径 · 2026-09-12）：**仅草稿**可作废。
     * <p>此前 cancel 一身两职（草稿作废 + 已审核反审核并逆向库存），同名不同义易误用；
     * 现将"反审核"拆到 {@link #unAudit(Long)}，本方法只保留草稿作废。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        InventoryProductReclassify rc = rcMapper.selectById(id);
        if (rc == null) throw new BusinessException("品质重分类单不存在");
        if (DocStatus.CANCELLED.getCode().equals(rc.getStatus())) throw new BusinessException("单据已作废");
        if (!DocStatus.DRAFT.getCode().equals(rc.getStatus()))
            throw new BusinessException("已审核单据不可直接作废，请先反审核");
        // 草稿：未应用库存，直接作废即可（与其他四类库存单据的 cancel 语义对齐，
        // 否则空明细/超量等无效草稿没有任何出路，会永久滞留在列表中）
        rcMapper.update(null, new LambdaUpdateWrapper<InventoryProductReclassify>()
                .eq(InventoryProductReclassify::getId, id)
                .set(InventoryProductReclassify::getStatus, DocStatus.CANCELLED.getCode()));
    }

    /**
     * 反审核（E2 口径 · 2026-09-12 从 cancel 拆出）：已审核 → CANCELLED，并逆向库存
     * （恢复原品质、冲回目标品质）。与 WarehouseMove.unAudit 语义一致。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unAudit(Long id) {
        InventoryProductReclassify rc = rcMapper.selectById(id);
        if (rc == null) throw new BusinessException("品质重分类单不存在");
        // P2-29：已审核分支原子抢占 AUDITED→CANCELLED，避免并发反审核重复逆向库存
        if (!DocStatusGuard.claim(rcMapper, InventoryProductReclassify::getId, id,
                InventoryProductReclassify::getStatus, DocStatus.AUDITED.getCode(), DocStatus.CANCELLED.getCode())) {
            throw new BusinessException("只有已审核状态可反审核");
        }
        List<InventoryProductReclassifyItem> items = getItems(id);

        // 逆向操作：恢复 from_quality，冲回 to_quality
        for (InventoryProductReclassifyItem it : items) {
            if (it.getQuantity() == null || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;
            Product prod = it.getProductId() != null ? productMapper.selectById(it.getProductId()) : null;
            // 恢复原品质
            stockService.changeStock(rc.getWarehouseId(), prod != null ? prod.getName() : "",
                    it.getQuantity(), StockChangeType.CANCEL_RECLASSIFY_OUT, rc.getCode(),
                    RelatedBillType.PRODUCT_RECLASSIFY, it.getProductId(),
                    "", rc.getId(), it.getFromQuality());
            // 冲回目标品质
            stockService.changeStock(rc.getWarehouseId(), prod != null ? prod.getName() : "",
                    it.getQuantity().negate(), StockChangeType.CANCEL_RECLASSIFY_IN, rc.getCode(),
                    RelatedBillType.PRODUCT_RECLASSIFY, it.getProductId(),
                    "", rc.getId(), it.getToQuality());
        }
        InventoryProductReclassify u = new InventoryProductReclassify(); u.setId(id); u.setStatus(DocStatus.CANCELLED.getCode());
        rcMapper.updateById(u);
    }

    private String gen(String prefix) {
        String d = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String pat = prefix + d;
        LambdaQueryWrapper<InventoryProductReclassify> w = new LambdaQueryWrapper<InventoryProductReclassify>()
                .likeRight(InventoryProductReclassify::getCode, pat).orderByDesc(InventoryProductReclassify::getCode).last("LIMIT 1");
        InventoryProductReclassify last = rcMapper.selectOne(w);
        int seq = 1;
        if (last != null && last.getCode() != null) {
            try { seq = Integer.parseInt(last.getCode().substring(last.getCode().length() - 3)) + 1; } catch (Exception e) { seq = 1; }
        }
        return prefix + d + String.format("%03d", seq);
    }

    /** 当前登录账户ID（未登录返回 null） */
    private Long getCurrentUserId() {
        try { return StpUtil.getLoginIdAsLong(); } catch (Exception e) { return null; }
    }

    /** 当前登录账户名（未登录或查不到返回 null） */
    private String getCurrentUserName() {
        try {
            User user = userMapper.selectById(StpUtil.getLoginIdAsLong());
            return user != null ? user.getUsername() : null;
        } catch (Exception e) { return null; }
    }
}
