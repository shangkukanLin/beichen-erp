package com.beichen.erp.inventory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.common.BillPrefix;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.inventory.entity.InventoryOtherIo;
import com.beichen.erp.inventory.entity.InventoryOtherIoItem;
import com.beichen.erp.inventory.mapper.InventoryOtherIoMapper;
import com.beichen.erp.inventory.mapper.InventoryOtherIoItemMapper;
import com.beichen.erp.warehouse.service.CostService;
import com.beichen.erp.warehouse.service.WarehouseStockService;
import com.beichen.erp.common.DocStatus;
import com.beichen.erp.common.DocStatusGuard;
import com.beichen.erp.inventory.common.IoType;
import com.beichen.erp.inventory.common.RelatedBillType;
import com.beichen.erp.inventory.common.StockChangeType;
import com.beichen.erp.inventory.service.OtherIoService;
import com.beichen.erp.material.common.ProductQualityType;
import com.beichen.erp.material.entity.Product;
import com.beichen.erp.material.mapper.ProductMapper;
import com.beichen.erp.material.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OtherIoServiceImpl implements OtherIoService {

    private final InventoryOtherIoMapper ioMapper;
    private final InventoryOtherIoItemMapper itemMapper;
    private final WarehouseStockService stockService;
    private final ProductMapper productMapper;
    private final ProductService productService;
    private final CostService costService;

    @Override
    public Page<Map<String, Object>> page(String status, Long warehouseId, String ioType, int pageNum, int pageSize) {
        LambdaQueryWrapper<InventoryOtherIo> w = new LambdaQueryWrapper<InventoryOtherIo>()
                .eq(status != null && !status.isBlank(), InventoryOtherIo::getStatus, status)
                .eq(warehouseId != null, InventoryOtherIo::getWarehouseId, warehouseId)
                .eq(ioType != null && !ioType.isBlank(), InventoryOtherIo::getIoType, ioType)
                .orderByDesc(InventoryOtherIo::getId);
        Page<InventoryOtherIo> raw = ioMapper.selectPage(new Page<>(pageNum, pageSize), w);
        Page<Map<String, Object>> res = new Page<>(pageNum, pageSize, raw.getTotal());
        res.setRecords(raw.getRecords().stream().map(o -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", o.getId()); m.put("code", o.getCode());
            m.put("warehouseId", o.getWarehouseId()); m.put("ioType", o.getIoType());
            m.put("ioDate", o.getIoDate()); m.put("status", o.getStatus()); m.put("remark", o.getRemark());
            m.put("createTime", o.getCreateTime());
            // 概况：成品名称×数量，供列表直接展示，免去前端逐条拉明细
            m.put("itemSummary", buildItemSummary(o.getId()));
            return m;
        }).toList());
        return res;
    }

    /** 明细概况：成品名称×数量，顿号分隔（与委外加工退货列表 itemSummary 同风格） */
    private String buildItemSummary(Long ioId) {
        List<InventoryOtherIoItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<InventoryOtherIoItem>().eq(InventoryOtherIoItem::getOtherIoId, ioId));
        StringBuilder sb = new StringBuilder();
        for (InventoryOtherIoItem it : items) {
            Product p = it.getProductId() != null ? productMapper.selectById(it.getProductId()) : null;
            BigDecimal qty = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
            if (sb.length() > 0) sb.append("、");
            sb.append(p != null ? p.getName() : "-").append("×").append(qty.stripTrailingZeros().toPlainString());
        }
        return sb.toString();
    }

    @Override
    public InventoryOtherIo getById(Long id) { return ioMapper.selectById(id); }

    @Override
    public List<InventoryOtherIoItem> getItems(Long otherIoId) {
        List<InventoryOtherIoItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<InventoryOtherIoItem>().eq(InventoryOtherIoItem::getOtherIoId, otherIoId));
        // 回填 SKU（非表字段），前端免查库即可展示
        productService.fillSku(items, InventoryOtherIoItem::getProductId, InventoryOtherIoItem::setSku);
        return items;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(InventoryOtherIo otherIo, List<InventoryOtherIoItem> items) {
        if (otherIo.getWarehouseId() == null) throw new BusinessException("仓库不能为空");
        if (otherIo.getIoType() == null || otherIo.getIoType().isBlank()) throw new BusinessException("出入库类型不能为空");
        otherIo.setCode(gen(BillPrefix.INVENTORY_OTHER_IO));
        // 统一流程：创建为草稿，审核时才应用库存
        otherIo.setStatus(DocStatus.DRAFT.getCode());
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) otherIo.setCompanyId(cid);
        ioMapper.insert(otherIo);
        for (InventoryOtherIoItem it : items) {
            it.setId(null);
            it.setOtherIoId(otherIo.getId());
            if (cid != null && cid > 0) it.setCompanyId(cid);
            itemMapper.insert(it);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(InventoryOtherIo otherIo, List<InventoryOtherIoItem> items) {
        InventoryOtherIo old = ioMapper.selectById(otherIo.getId());
        if (old == null) throw new BusinessException("其他出入库单不存在");
        // 统一流程：仅草稿可编辑（草稿未应用库存，直接更新主表与明细）
        if (!DocStatus.DRAFT.getCode().equals(old.getStatus())) throw new BusinessException("仅草稿状态可编辑");

        otherIo.setCode(old.getCode()); otherIo.setStatus(DocStatus.DRAFT.getCode());
        ioMapper.updateById(otherIo);

        // 删旧明细 + 插新明细
        itemMapper.delete(new LambdaQueryWrapper<InventoryOtherIoItem>().eq(InventoryOtherIoItem::getOtherIoId, otherIo.getId()));
        Long cid = CompanyContext.get();
        for (InventoryOtherIoItem it : items) {
            it.setId(null); it.setOtherIoId(otherIo.getId());
            if (cid != null && cid > 0) it.setCompanyId(cid);
            itemMapper.insert(it);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        InventoryOtherIo old = ioMapper.selectById(id);
        if (old == null) throw new BusinessException("其他出入库单不存在");
        // 统一流程：仅草稿可作废（草稿未应用库存，无需逆向）；已审核单据请先反审核
        if (!DocStatus.DRAFT.getCode().equals(old.getStatus())) throw new BusinessException("仅草稿状态可作废，已审核单据请先反审核");
        InventoryOtherIo u = new InventoryOtherIo(); u.setId(id); u.setStatus(DocStatus.CANCELLED.getCode());
        ioMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        InventoryOtherIo io = ioMapper.selectById(id);
        if (io == null) throw new BusinessException("其他出入库单不存在");
        // P2-29：原子抢占 DRAFT→AUDITED，避免并发/双击重复应用库存
        if (!DocStatusGuard.claim(ioMapper, InventoryOtherIo::getId, id,
                InventoryOtherIo::getStatus, DocStatus.DRAFT.getCode(), DocStatus.AUDITED.getCode())) {
            throw new BusinessException("仅草稿状态可审核");
        }
        // 审核时应用库存
        List<InventoryOtherIoItem> items = itemMapper.selectList(
            new LambdaQueryWrapper<InventoryOtherIoItem>().eq(InventoryOtherIoItem::getOtherIoId, id));
        // 出库前校验库存：一次列清所有不足项，避免落到 changeStock 只报「产品ID=xx」
        checkStockBeforeOut(io, items);
        applyStock(io, items);
        // 状态已由 DocStatusGuard 在该方法开头原子置为 AUDITED
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unAudit(Long id) {
        InventoryOtherIo io = ioMapper.selectById(id);
        if (io == null) throw new BusinessException("其他出入库单不存在");
        // P2-29：原子抢占 AUDITED→DRAFT，避免并发反审核重复回滚库存
        if (!DocStatusGuard.claim(ioMapper, InventoryOtherIo::getId, id,
                InventoryOtherIo::getStatus, DocStatus.AUDITED.getCode(), DocStatus.DRAFT.getCode())) {
            throw new BusinessException("仅已审核状态可反审核");
        }
        // 反审核时逆向库存，回到草稿
        List<InventoryOtherIoItem> items = itemMapper.selectList(
            new LambdaQueryWrapper<InventoryOtherIoItem>().eq(InventoryOtherIoItem::getOtherIoId, id));
        revertStock(io, items);
        // 状态已由 DocStatusGuard 在该方法开头原子置为 DRAFT
    }

    /**
     * 出库前的库存校验（仅出库单生效）。
     * changeStock 本身也会拦（SQL 带 quantity + delta >= 0），但报错只有「产品ID=xx」，
     * 用户看不出是哪个产品、差多少。这里前置一次性检查全部明细，给出产品名/品质/需量/库存/缺口。
     */
    private void checkStockBeforeOut(InventoryOtherIo io, List<InventoryOtherIoItem> items) {
        if (!IoType.OUT.getCode().equals(io.getIoType())) return;
        List<String> shortage = new ArrayList<>();
        for (InventoryOtherIoItem it : items) {
            BigDecimal need = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
            if (need.compareTo(BigDecimal.ZERO) <= 0) continue;
            String qt = it.getQualityType() != null && !it.getQualityType().isBlank()
                    ? it.getQualityType() : ProductQualityType.A.getCode();
            BigDecimal avail = stockService.getQuantity(io.getWarehouseId(), it.getProductId(), qt);
            if (avail.compareTo(need) < 0) {
                Product p = it.getProductId() != null ? productMapper.selectById(it.getProductId()) : null;
                shortage.add(String.format("%s（%s规，需 %s，库存 %s，缺 %s）",
                        p != null ? p.getName() : "ID=" + it.getProductId(),
                        qt,
                        need.stripTrailingZeros().toPlainString(),
                        avail.stripTrailingZeros().toPlainString(),
                        need.subtract(avail).stripTrailingZeros().toPlainString()));
            }
        }
        if (!shortage.isEmpty()) {
            throw new BusinessException("库存不足，无法审核：" + String.join("；", shortage)
                    + (shortage.size() > 5 ? " 等 " + shortage.size() + " 项" : ""));
        }
    }

    /** 应用库存变更 */
    private void applyStock(InventoryOtherIo io, List<InventoryOtherIoItem> items) {
            StockChangeType type = IoType.IN.getCode().equals(io.getIoType()) ? StockChangeType.OTHER_IN : StockChangeType.OTHER_OUT;
        boolean isIn = IoType.IN.getCode().equals(io.getIoType());
        for (InventoryOtherIoItem it : items) {
            BigDecimal q = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
            BigDecimal delta = isIn ? q : q.negate();
            Product prod = it.getProductId() != null ? productMapper.selectById(it.getProductId()) : null;
            stockService.changeStock(io.getWarehouseId(), prod != null ? prod.getName() : "",
                    delta, type, io.getCode(), RelatedBillType.OTHER_IO, it.getProductId(),
                    prod != null ? prod.getSpec() : "", io.getId(), it.getQualityType());
            // 其他入库单无单价：成本为空时用最近进价兜底，避免"有库存无成本"
            if (isIn) costService.fillProductCostIfEmpty(it.getProductId());
        }
    }

    /** 逆向库存（编辑回滚 / 取消） */
    private void revertStock(InventoryOtherIo io, List<InventoryOtherIoItem> items) {
        StockChangeType type = IoType.IN.getCode().equals(io.getIoType()) ? StockChangeType.CANCEL_IN : StockChangeType.CANCEL_OUT;
        for (InventoryOtherIoItem it : items) {
            BigDecimal q = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
            // 逆向：入库变成扣回，出库变成加回
            BigDecimal delta = IoType.IN.getCode().equals(io.getIoType()) ? q.negate() : q;
            Product prod = it.getProductId() != null ? productMapper.selectById(it.getProductId()) : null;
            stockService.changeStock(io.getWarehouseId(), prod != null ? prod.getName() : "",
                    delta, type, io.getCode(), RelatedBillType.OTHER_IO, it.getProductId(),
                    prod != null ? prod.getSpec() : "", io.getId(), it.getQualityType());
        }
    }

    private String gen(String prefix) {
        String d = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String pat = prefix + d;
        LambdaQueryWrapper<InventoryOtherIo> w = new LambdaQueryWrapper<InventoryOtherIo>()
                .likeRight(InventoryOtherIo::getCode, pat).orderByDesc(InventoryOtherIo::getCode).last("LIMIT 1");
        InventoryOtherIo last = ioMapper.selectOne(w);
        int seq = 1;
        if (last != null && last.getCode() != null) {
            try { seq = Integer.parseInt(last.getCode().substring(last.getCode().length() - 3)) + 1; } catch (Exception e) { seq = 1; }
        }
        return prefix + d + String.format("%03d", seq);
    }
}
