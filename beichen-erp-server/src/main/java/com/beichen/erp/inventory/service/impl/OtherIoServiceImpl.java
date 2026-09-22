package com.beichen.erp.inventory.service.impl;

import com.beichen.erp.config.UserContext;

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
        otherIo.setIoType(normalizeIoType(otherIo.getIoType()));
        items = validItems(items);
        if (items.isEmpty()) throw new BusinessException("请添加明细（每行需选择产品且数量大于 0）");
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
        items = validItems(items);
        if (items.isEmpty()) throw new BusinessException("请添加明细（每行需选择产品且数量大于 0）");
        // F7-17：归一化出入库类型（编辑未传时沿用原值，避免被误判为非法）
        otherIo.setIoType(normalizeIoType(
                otherIo.getIoType() == null || otherIo.getIoType().isBlank() ? old.getIoType() : otherIo.getIoType()));
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
        // F7-50（2026-09-19）：原子抢占 DRAFT→CANCELLED（原"先查后改"可与 audit 并发互覆）
        if (!DocStatusGuard.claim(ioMapper, InventoryOtherIo::getId, id,
                InventoryOtherIo::getStatus, DocStatus.DRAFT.getCode(), DocStatus.CANCELLED.getCode()))
            throw new BusinessException("仅草稿状态可作废，已审核单据请先反审核");
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
        // T7（2026-09-18）：审核前兜底校验明细可归属（兼容修复前保存的旧草稿单）
        assertItemsAttributable(items);
        // F7-17 兜底：归一化出入库类型（覆盖历史脏数据），使下方"是否出库"的判定口径一致
        io.setIoType(normalizeIoType(io.getIoType()));
        // 出库前校验库存：一次列清所有不足项，避免落到 changeStock 只报「产品ID=xx」
        checkStockBeforeOut(io, items);
        applyStock(io, items);
        // 状态已由 DocStatusGuard 在该方法开头原子置为 AUDITED
        // 2026-09-23（用户口径：单据详情显示「制单人 + 审核人」）：补写审核人（原先审核后不留任何审核信息）
        InventoryOtherIo u = new InventoryOtherIo();
        u.setId(id);
        u.setAuditorId(UserContext.getId());
        u.setAuditorName(UserContext.getName());
        ioMapper.updateById(u);
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
     * F7-17（2026-09-19）：出入库类型必须是合法枚举，并**归一化**为枚举常量名后再落库。
     *
     * <p><b>修复的缺陷</b>：修复前 create / update 只校验"非空"，而下游三处判定用的是大小写敏感的
     * {@code equals}：{@link #checkStockBeforeOut} 对非 {@code OUT} 的值**直接跳过库存校验**，
     * {@link #applyStock} 却把非 {@code IN} 的值一律按"出库扣减"处理 ⇒ 传 {@code "XYZ"}（或小写
     * {@code in}）会"跳过校验但仍然扣减"，报错也只剩库存写入层的兜底文案。</p>
     *
     * <p>{@link IoType#fromCode(String)} 忽略大小写，故此处**回写规范 code**，使下游三处判定口径一致
     * （否则会出现"校验通过、却按出库扣"的残留不一致）。</p>
     */
    private String normalizeIoType(String ioType) {
        if (ioType == null || ioType.isBlank()) throw new BusinessException("出入库类型不能为空");
        IoType t = IoType.fromCode(ioType);
        if (t == null) throw new BusinessException("出入库类型不合法：" + ioType);
        return t.getCode();
    }

    /** F7-17：出入库方向的**唯一**判定（checkStockBeforeOut / applyStock / revertStock 三处共用） */
    private boolean isIn(InventoryOtherIo io) {
        return IoType.IN.getCode().equals(io.getIoType());
    }

    /**
     * T7（2026-09-18 修复）：明细行合法性 —— 只保留「已选择产品且数量 > 0」的行。
     * <p>修复前允许保存"未选产品"的空行，审核时因数量为 0 跳过库存校验，却仍把
     * {@code product_id=NULL} 的幽灵库存行写进 `warehouse_stock`（详见 §12.109.6-T7）。</p>
     */
    private List<InventoryOtherIoItem> validItems(List<InventoryOtherIoItem> items) {
        List<InventoryOtherIoItem> valid = new ArrayList<>();
        if (items == null) return valid;
        for (InventoryOtherIoItem it : items) {
            if (it.getProductId() == null) continue;
            if (it.getQuantity() == null || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;
            valid.add(it);
        }
        return valid;
    }

    /**
     * T7（2026-09-18 修复）：审核前兜底校验 —— 每行必须选择产品。
     * <p>覆盖修复前已保存的旧草稿单（那些单里可能残留"未选产品"的行）。</p>
     */
    private void assertItemsAttributable(List<InventoryOtherIoItem> items) {
        if (items == null || items.isEmpty()) throw new BusinessException("明细不能为空，无法审核");
        for (InventoryOtherIoItem it : items) {
            if (it.getProductId() == null)
                throw new BusinessException("明细行未选择产品，无法审核（请补全产品后再审核）");
        }
    }

    /**
     * 出库前的库存校验（仅出库单生效）。
     * changeStock 本身也会拦（SQL 带 quantity + delta >= 0），但报错只有「产品ID=xx」，
     * 用户看不出是哪个产品、差多少。这里前置一次性检查全部明细，给出产品名/品质/需量/库存/缺口。
     */
    private void checkStockBeforeOut(InventoryOtherIo io, List<InventoryOtherIoItem> items) {
        if (isIn(io)) return; // F7-17：仅出库单需要校验库存（入库只会增加库存）
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
        // F7-17：方向判定统一走 isIn()，避免三处各自 equals 造成"校验跳过却仍按出库扣减"的口径漂移
        boolean in = isIn(io);
        StockChangeType type = in ? StockChangeType.OTHER_IN : StockChangeType.OTHER_OUT;
        for (InventoryOtherIoItem it : items) {
            BigDecimal q = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
            BigDecimal delta = in ? q : q.negate();
            Product prod = it.getProductId() != null ? productMapper.selectById(it.getProductId()) : null;
            stockService.changeStock(io.getWarehouseId(), prod != null ? prod.getName() : "",
                    delta, type, io.getCode(), RelatedBillType.OTHER_IO, it.getProductId(),
                    "", io.getId(), it.getQualityType());
            // 其他入库单无单价：成本为空时用最近进价兜底，避免"有库存无成本"
            if (in) costService.fillProductCostIfEmpty(it.getProductId());
        }
    }

    /** 逆向库存（编辑回滚 / 取消） */
    private void revertStock(InventoryOtherIo io, List<InventoryOtherIoItem> items) {
        boolean in = isIn(io);
        StockChangeType type = in ? StockChangeType.CANCEL_IN : StockChangeType.CANCEL_OUT;
        for (InventoryOtherIoItem it : items) {
            BigDecimal q = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
            // 逆向：入库变成扣回，出库变成加回
            BigDecimal delta = in ? q.negate() : q;
            Product prod = it.getProductId() != null ? productMapper.selectById(it.getProductId()) : null;
            stockService.changeStock(io.getWarehouseId(), prod != null ? prod.getName() : "",
                    delta, type, io.getCode(), RelatedBillType.OTHER_IO, it.getProductId(),
                    "", io.getId(), it.getQualityType());
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
