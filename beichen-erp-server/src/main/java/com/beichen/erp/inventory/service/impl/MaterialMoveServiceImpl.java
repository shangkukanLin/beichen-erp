package com.beichen.erp.inventory.service.impl;

import com.beichen.erp.config.UserContext;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.common.BillPrefix;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.inventory.common.RelatedBillType;
import com.beichen.erp.common.DocStatus;
import com.beichen.erp.common.DocStatusGuard;
import com.beichen.erp.inventory.common.StockChangeType;
import com.beichen.erp.inventory.entity.InventoryMaterialMove;
import com.beichen.erp.inventory.entity.InventoryMaterialMoveItem;
import com.beichen.erp.inventory.mapper.InventoryMaterialMoveMapper;
import com.beichen.erp.inventory.mapper.InventoryMaterialMoveItemMapper;
import com.beichen.erp.warehouse.service.CostService;
import com.beichen.erp.warehouse.service.WarehouseStockService;
import com.beichen.erp.inventory.service.MaterialMoveService;
import com.beichen.erp.outsource.entity.OutsourceMaterial;
import com.beichen.erp.outsource.mapper.OutsourceMaterialMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 物料移仓单实现（2026-09-24 新增）。
 *
 * <p><b>为什么是复刻而不是复用成品移仓</b>：成品移仓的明细域是 {@code product_id} + 品质等级，
 * 库存走 {@code stockService.changeStock}；物料移仓的明细域是 {@code outsource_material_id}、
 * 物料库存无品质维度，库存必须走 {@code stockService.changeMaterialStock} —— 两者库存域不同
 * （成品库存 vs 物料库存），共用一张表会把两类库存混进同一条明细，故按用户口径「参考成品移仓单」
 * 复刻一套平行的主子表。</p>
 *
 * <p><b>与成品移仓完全一致的口径</b>：① 只在审核时动库存，同一单内 from 减 / to 加；
 * ② 状态用 {@link DocStatusGuard} 原子抢占（防并发重复扣加）；③ 明细行必须选物料且数量 &gt; 0；
 * ④ 移仓不改变加权价（总量守恒），只对目标仓新出现的库存做成本兜底；⑤ 审核时盖章「审核人」。</p>
 */
@Service
@RequiredArgsConstructor
public class MaterialMoveServiceImpl implements MaterialMoveService {

    /** 明细品质分级白名单（与成品移仓单一致） */
    private static final List<String> QUALITY_TYPES = List.of("A", "B", "C", "DEFECT");

    private final InventoryMaterialMoveMapper moveMapper;
    private final InventoryMaterialMoveItemMapper itemMapper;
    private final WarehouseStockService stockService;
    private final OutsourceMaterialMapper materialMapper;
    private final CostService costService;
    /**
     * 物料类型主数据（2026-10-10 用户口径「物料名称前面需要显示物料类型」）。
     *
     * <p>明细行的 {@code materialName} / {@code materialTypeName} 都按 material_id 回填，
     * 供前端全局 `$mLabel` 显示成 `类型 | 名称` ✓。用全限定名注入，与
     * {@code OutsourceOtherIoController} 的写法保持一致（该文件同样不写 import）✓。</p>
     */
    private final com.beichen.erp.dev.mapper.MaterialTypeMapper materialTypeMapper;

    @Override
    public Page<Map<String, Object>> page(String status, Long fromWarehouseId, Long toWarehouseId, int pageNum, int pageSize) {
        LambdaQueryWrapper<InventoryMaterialMove> w = new LambdaQueryWrapper<InventoryMaterialMove>()
                .eq(status != null && !status.isBlank(), InventoryMaterialMove::getStatus, status)
                .eq(fromWarehouseId != null, InventoryMaterialMove::getFromWarehouseId, fromWarehouseId)
                .eq(toWarehouseId != null, InventoryMaterialMove::getToWarehouseId, toWarehouseId)
                .orderByDesc(InventoryMaterialMove::getId);
        Page<InventoryMaterialMove> raw = moveMapper.selectPage(new Page<>(pageNum, pageSize), w);

        List<Long> moveIds = raw.getRecords().stream().map(InventoryMaterialMove::getId).collect(Collectors.toList());
        final Map<Long, List<InventoryMaterialMoveItem>> itemsMap;
        if (!moveIds.isEmpty()) {
            List<InventoryMaterialMoveItem> allItems = itemMapper.selectList(
                    new LambdaQueryWrapper<InventoryMaterialMoveItem>().in(InventoryMaterialMoveItem::getMoveId, moveIds));
            itemsMap = allItems.stream().collect(Collectors.groupingBy(InventoryMaterialMoveItem::getMoveId));
        } else {
            itemsMap = Collections.emptyMap();
        }

        // 批量查物料名称，避免循环内逐条 selectById 产生 N+1
        List<Long> materialIds = new ArrayList<>();
        itemsMap.values().forEach(its -> its.forEach(it -> {
            if (it.getMaterialId() != null) materialIds.add(it.getMaterialId());
        }));
        final Map<Long, OutsourceMaterial> materialMap;
        if (!materialIds.isEmpty()) {
            materialMap = materialMapper.selectBatchIds(materialIds).stream()
                    .collect(Collectors.toMap(OutsourceMaterial::getId, m -> m, (a, b) -> a));
        } else {
            materialMap = Collections.emptyMap();
        }

        Page<Map<String, Object>> res = new Page<>(pageNum, pageSize, raw.getTotal());
        res.setRecords(raw.getRecords().stream().map(o -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", o.getId()); m.put("code", o.getCode());
            m.put("fromWarehouseId", o.getFromWarehouseId()); m.put("toWarehouseId", o.getToWarehouseId());
            m.put("moveDate", o.getMoveDate()); m.put("status", o.getStatus()); m.put("remark", o.getRemark());
            m.put("createTime", o.getCreateTime());
            // 2026-09-26 B5a（用户口径「明细列可点进物料详情」）：同一次遍历产出逐项
            // [{materialId,materialName,quantity}]，前端 EntityLinks(target=material) 直接用
            List<InventoryMaterialMoveItem> its = itemsMap.getOrDefault(o.getId(), Collections.emptyList());
            List<Map<String, Object>> itemList = new ArrayList<>();
            StringBuilder summarySb = new StringBuilder();
            for (InventoryMaterialMoveItem it : its) {
                String name = "";
                if (it.getMaterialId() != null) {
                    OutsourceMaterial mat = materialMap.get(it.getMaterialId());
                    if (mat != null) name = mat.getMaterialName();
                }
                String qty = it.getQuantity() != null ? it.getQuantity().stripTrailingZeros().toPlainString() : "0";
                if (summarySb.length() > 0) summarySb.append("，");
                summarySb.append(name).append("*").append(qty);
                if (it.getMaterialId() != null) {
                    Map<String, Object> im = new HashMap<>();
                    im.put("materialId", it.getMaterialId());
                    im.put("materialName", name);
                    // 2026-10-10 用户口径「物料名称前面需要显示物料类型」：列表摘要/明细都带上类型 ✓
                    // ⚠️ 上面的 `mat` 声明在 if 块内、这里取不到 ⇒ 从 materialMap 再取一次（零额外查询 ✓）
                    OutsourceMaterial mForType = materialMap.get(it.getMaterialId());
                    com.beichen.erp.dev.entity.MaterialType mt = (mForType == null || mForType.getMaterialTypeId() == null)
                            ? null : materialTypeMapper.selectById(mForType.getMaterialTypeId());
                    im.put("materialTypeName", mt == null ? null : mt.getTypeName());
                    im.put("quantity", it.getQuantity());
                    itemList.add(im);
                }
            }
            m.put("itemsSummary", summarySb.toString());
            m.put("items", itemList);
            return m;
        }).toList());
        return res;
    }

    @Override
    public InventoryMaterialMove getById(Long id) { return moveMapper.selectById(id); }

    @Override
    public List<InventoryMaterialMoveItem> getItems(Long moveId) {
        List<InventoryMaterialMoveItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<InventoryMaterialMoveItem>().eq(InventoryMaterialMoveItem::getMoveId, moveId));
        // 回填物料名称/单位（非表字段），前端免查库即可展示
        List<Long> ids = items.stream().map(InventoryMaterialMoveItem::getMaterialId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (!ids.isEmpty()) {
            Map<Long, OutsourceMaterial> map = materialMapper.selectBatchIds(ids).stream()
                    .collect(Collectors.toMap(OutsourceMaterial::getId, m -> m, (a, b) -> a));
            for (InventoryMaterialMoveItem it : items) {
                OutsourceMaterial mat = it.getMaterialId() == null ? null : map.get(it.getMaterialId());
                if (mat != null) {
                    it.setMaterialName(mat.getMaterialName());
                    it.setUnit(mat.getUnit());
                    // 2026-10-10 用户口径「物料名称前面需要显示物料类型」：明细行回填类型名 ✓
                    com.beichen.erp.dev.entity.MaterialType mt = mat.getMaterialTypeId() == null
                            ? null : materialTypeMapper.selectById(mat.getMaterialTypeId());
                    it.setMaterialTypeName(mt == null ? null : mt.getTypeName());
                }
            }
        }
        return items;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(InventoryMaterialMove move, List<InventoryMaterialMoveItem> items) {
        if (move.getFromWarehouseId() == null || move.getToWarehouseId() == null)
            throw new BusinessException("移出/移入仓库不能为空");
        if (move.getFromWarehouseId().equals(move.getToWarehouseId()))
            throw new BusinessException("移出与移入仓库不能相同");
        assertItems(items);
        move.setCode(gen(BillPrefix.MATERIAL_MOVE));
        move.setStatus(DocStatus.DRAFT.getCode());
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) move.setCompanyId(cid);
        moveMapper.insert(move);
        for (InventoryMaterialMoveItem it : items) {
            it.setId(null);
            it.setMoveId(move.getId());
            if (cid != null && cid > 0) it.setCompanyId(cid);
            itemMapper.insert(it);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(InventoryMaterialMove move, List<InventoryMaterialMoveItem> items) {
        InventoryMaterialMove old = moveMapper.selectById(move.getId());
        if (old == null) throw new BusinessException("物料移仓单不存在");
        if (!DocStatus.DRAFT.getCode().equals(old.getStatus())) throw new BusinessException("只有草稿状态可编辑");
        // 与成品移仓一致：必须在删旧明细之前校验，否则校验失败时草稿明细已被清空
        assertItems(items);
        move.setCode(old.getCode());
        moveMapper.updateById(move);
        itemMapper.delete(new LambdaQueryWrapper<InventoryMaterialMoveItem>().eq(InventoryMaterialMoveItem::getMoveId, move.getId()));
        Long cid = CompanyContext.get();
        for (InventoryMaterialMoveItem it : items) {
            it.setId(null);
            it.setMoveId(move.getId());
            if (cid != null && cid > 0) it.setCompanyId(cid);
            itemMapper.insert(it);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        InventoryMaterialMove old = moveMapper.selectById(id);
        if (old == null) throw new BusinessException("物料移仓单不存在");
        // 原子抢占 DRAFT→CANCELLED（与成品移仓 F7-50 同口径）：防 cancel 与 audit 并发时"库存已生效却显示已作废"
        if (!DocStatusGuard.claim(moveMapper, InventoryMaterialMove::getId, id,
                InventoryMaterialMove::getStatus, DocStatus.DRAFT.getCode(), DocStatus.CANCELLED.getCode()))
            throw new BusinessException("只有草稿状态可作废");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        InventoryMaterialMove move = moveMapper.selectById(id);
        if (move == null) throw new BusinessException("物料移仓单不存在");
        // 原子抢占 DRAFT→AUDITED（防并发/双击重复扣加库存）
        if (!DocStatusGuard.claim(moveMapper, InventoryMaterialMove::getId, id,
                InventoryMaterialMove::getStatus, DocStatus.DRAFT.getCode(), DocStatus.AUDITED.getCode())) {
            throw new BusinessException("只有草稿状态可审核");
        }
        List<InventoryMaterialMoveItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<InventoryMaterialMoveItem>().eq(InventoryMaterialMoveItem::getMoveId, id));
        // 兜底（覆盖历史草稿与直连库脏数据）：放在 claim 之后，抛错会连同 claim 一起回滚
        assertItems(items);
        for (InventoryMaterialMoveItem it : items) {
            BigDecimal q = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
            // 移出仓扣减（严格校验库存充足，物料移仓不提供"强制出库"开关）
            stockService.changeMaterialStock(move.getFromWarehouseId(), it.getMaterialId(), q.negate(),
                    StockChangeType.MATERIAL_MOVE_OUT.getCode(), move.getCode(),
                    RelatedBillType.MATERIAL_MOVE, null, null, move.getId());
            // 移入仓增加
            stockService.changeMaterialStock(move.getToWarehouseId(), it.getMaterialId(), q,
                    StockChangeType.MATERIAL_MOVE_IN.getCode(), move.getCode(),
                    RelatedBillType.MATERIAL_MOVE, null, null, move.getId());
            // 移仓不改变加权价（总量不变），但目标仓新出现的库存若物料无成本，用最近进价兜底
            costService.fillMaterialCostIfEmpty(it.getMaterialId());
        }
        // 状态已由 DocStatusGuard 原子置为 AUDITED；此处只补盖章「审核人」
        InventoryMaterialMove u = new InventoryMaterialMove();
        u.setId(id);
        u.setAuditorId(UserContext.getId());
        u.setAuditorName(UserContext.getName());
        moveMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unAudit(Long id) {
        InventoryMaterialMove move = moveMapper.selectById(id);
        if (move == null) throw new BusinessException("物料移仓单不存在");
        // 原子抢占 AUDITED→DRAFT，避免并发反审核重复回滚库存
        if (!DocStatusGuard.claim(moveMapper, InventoryMaterialMove::getId, id,
                InventoryMaterialMove::getStatus, DocStatus.AUDITED.getCode(), DocStatus.DRAFT.getCode())) {
            throw new BusinessException("只有已审核状态可反审核");
        }
        List<InventoryMaterialMoveItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<InventoryMaterialMoveItem>().eq(InventoryMaterialMoveItem::getMoveId, id));
        for (InventoryMaterialMoveItem it : items) {
            BigDecimal q = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
            // 反审核：退回移出仓、从移入仓扣回
            // 【与成品移仓同一 C2 口径 · 2026-09-12 定稿】MOVE_IN/MOVE_OUT 表意的是"某仓库存进/出"（方向），
            // 不是"审核/反审核"（动作）；动作由 related_bill_type 区分：审核=MATERIAL_MOVE、
            // 反审核=MATERIAL_MOVE_UN_AUDIT，故四行流水两两可辨。
            stockService.changeMaterialStock(move.getFromWarehouseId(), it.getMaterialId(), q,
                    StockChangeType.MATERIAL_MOVE_IN.getCode(), move.getCode(),
                    RelatedBillType.MATERIAL_MOVE_UN_AUDIT, null, null, move.getId());
            stockService.changeMaterialStock(move.getToWarehouseId(), it.getMaterialId(), q.negate(),
                    StockChangeType.MATERIAL_MOVE_OUT.getCode(), move.getCode(),
                    RelatedBillType.MATERIAL_MOVE_UN_AUDIT, null, null, move.getId());
        }
        // 状态已由 DocStatusGuard 原子置为 DRAFT
    }

    /**
     * 明细行校验（与成品移仓 F7-15 同口径）：明细不能为空，且每行必须选物料、数量必须大于 0。
     *
     * <p>注意：物料库存是"允许出现负数"的域（委外场景历史上允许扣成负数），但**移仓单本身**不接受
     * 负数量 —— 负数量会让 {@link #audit(Long)} 变成"按单据反方向搬运"，静默错账且反审核不会自愈，
     * 故这里直接抛错而不是静默过滤。</p>
     */
    private void assertItems(List<InventoryMaterialMoveItem> items) {
        if (items == null || items.isEmpty()) throw new BusinessException("请添加移仓明细");
        for (int i = 0; i < items.size(); i++) {
            InventoryMaterialMoveItem it = items.get(i);
            if (it == null || it.getMaterialId() == null)
                throw new BusinessException("第 " + (i + 1) + " 行未选择物料");
            if (it.getQuantity() == null || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0)
                throw new BusinessException("第 " + (i + 1) + " 行数量必须大于 0");
            // 品质分级（2026-09-24 用户要求）：空值兜底 A，只接受 A/B/C/DEFECT（与成品移仓同口径）。
            // ⚠️ 仅落单据留痕，不参与库存写入（物料库存没有品质维度）。
            if (it.getQualityType() == null || it.getQualityType().isBlank()) {
                it.setQualityType("A");
            } else if (!QUALITY_TYPES.contains(it.getQualityType())) {
                throw new BusinessException("第 " + (i + 1) + " 行品质不合法（A/B/C/DEFECT）：" + it.getQualityType());
            }
        }
    }

    /** 单号：MYC-yyyyMMddNNN（与成品移仓 YC- 同规则，各自独立取号） */
    private String gen(String prefix) {
        String d = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String pat = prefix + d;
        LambdaQueryWrapper<InventoryMaterialMove> w = new LambdaQueryWrapper<InventoryMaterialMove>()
                .likeRight(InventoryMaterialMove::getCode, pat).orderByDesc(InventoryMaterialMove::getCode).last("LIMIT 1");
        InventoryMaterialMove last = moveMapper.selectOne(w);
        int seq = 1;
        if (last != null && last.getCode() != null) {
            try { seq = Integer.parseInt(last.getCode().substring(last.getCode().length() - 3)) + 1; } catch (Exception e) { seq = 1; }
        }
        return prefix + d + String.format("%03d", seq);
    }
}
