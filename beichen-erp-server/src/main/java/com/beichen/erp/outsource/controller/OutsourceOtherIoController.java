package com.beichen.erp.outsource.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.BillPrefix;
import com.beichen.erp.common.R;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.warehouse.mapper.WarehouseMapper;
import com.beichen.erp.inventory.common.IoType;
import com.beichen.erp.inventory.common.RelatedBillType;
import com.beichen.erp.common.DocStatus;
import com.beichen.erp.common.DocStatusGuard;
import com.beichen.erp.inventory.common.StockChangeType;
import com.beichen.erp.warehouse.service.WarehouseStockService;
import com.beichen.erp.outsource.common.QualityType;
import com.beichen.erp.outsource.common.DeliveryStatus;
import com.beichen.erp.outsource.entity.OutsourceOtherIo;
import com.beichen.erp.outsource.entity.OutsourceOtherIoItem;
import com.beichen.erp.outsource.entity.OutsourceMaterial;
import com.beichen.erp.outsource.entity.OutsourceDelivery;
import com.beichen.erp.outsource.entity.OutsourceDeliveryItem;
import com.beichen.erp.outsource.mapper.OutsourceOtherIoMapper;
import com.beichen.erp.outsource.mapper.OutsourceOtherIoItemMapper;
import com.beichen.erp.outsource.mapper.OutsourceMaterialMapper;
import com.beichen.erp.outsource.mapper.OutsourceDeliveryMapper;
import com.beichen.erp.outsource.mapper.OutsourceDeliveryItemMapper;
import com.beichen.erp.warehouse.mapper.WarehouseMapper;
import com.beichen.erp.warehouse.entity.Warehouse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/outsource/other-io")
@RequiredArgsConstructor
public class OutsourceOtherIoController {

    private final OutsourceOtherIoMapper ioMapper;
    private final OutsourceOtherIoItemMapper itemMapper;
    private final OutsourceMaterialMapper materialMapper;
    private final com.beichen.erp.dev.mapper.MaterialTypeMapper materialTypeMapper;
    private final WarehouseMapper warehouseMapper;
    private final com.beichen.erp.warehouse.service.CostService costService;
    private final WarehouseStockService warehouseStockService;
    // 期 3（2026-09-19 读隔离）：物料加权通常需读「物料收发单」模块的同一查询
    private final com.beichen.erp.outsource.service.DeliveryService deliveryService;

    /**
     * 某个工厂某个物料的**加权平均单价**（委外仓选物料时自动带出单价）。
     * <p>期 3（2026-09-19 读隔离）：原先本模块三个页面（新增/编辑/详情）直读物料收发单模块的
     * {@code /api/outsource/delivery/material-weighted-price}（需 {@code outsource:delivery}）
     * ⇒ 只被授予 {@code outsource:other-io} 的用户会 403。现走本页前缀，复用同一计算方法，单价口径不变。</p>
     */
    @GetMapping("/material-weighted-price")
    public R<BigDecimal> materialWeightedPrice(@RequestParam Long factoryId, @RequestParam Long materialId) {
        return R.ok(deliveryService.calcWeightedPrice(factoryId, materialId));
    }

    @GetMapping("/page")
    public R<Page<Map<String, Object>>> page(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) String ioType,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        LambdaQueryWrapper<OutsourceOtherIo> w = new LambdaQueryWrapper<OutsourceOtherIo>()
                .eq(warehouseId != null, OutsourceOtherIo::getWarehouseId, warehouseId)
                .eq(ioType != null && !ioType.isBlank(), OutsourceOtherIo::getIoType, ioType)
                .orderByDesc(OutsourceOtherIo::getId);
        Page<OutsourceOtherIo> raw = ioMapper.selectPage(new Page<>(pageNum, pageSize), w);
        Page<Map<String, Object>> res = new Page<>(pageNum, pageSize, raw.getTotal());
        res.setRecords(raw.getRecords().stream().map(o -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", o.getId()); m.put("code", o.getCode());
            m.put("warehouseId", o.getWarehouseId()); m.put("ioType", o.getIoType());
            m.put("ioDate", o.getIoDate()); m.put("status", o.getStatus()); m.put("remark", o.getRemark());
            m.put("createTime", o.getCreateTime());
            // 物料明细
            List<OutsourceOtherIoItem> items = itemMapper.selectList(new LambdaQueryWrapper<OutsourceOtherIoItem>().eq(OutsourceOtherIoItem::getOtherIoId, o.getId()));
            java.util.StringJoiner sj = new java.util.StringJoiner("、");
            for (OutsourceOtherIoItem it : items) {
                String n = getMaterialNameById(it.getMaterialId());
                java.math.BigDecimal q = it.getQuantity() != null ? it.getQuantity() : java.math.BigDecimal.ZERO;
                sj.add(n + "×" + q.stripTrailingZeros().toPlainString());
            }
            m.put("itemSummary", sj.toString());
            return m;
        }).toList());
        return R.ok(res);
    }

    @GetMapping("/{id}")
    public R<OutsourceOtherIo> getById(@PathVariable Long id) {
        return R.ok(ioMapper.selectById(id));
    }

    @GetMapping("/{id}/items")
    public R<List<OutsourceOtherIoItem>> getItems(@PathVariable Long id) {
        return R.ok(itemMapper.selectList(
            new LambdaQueryWrapper<OutsourceOtherIoItem>().eq(OutsourceOtherIoItem::getOtherIoId, id)));
    }

    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public R<Void> create(@RequestBody Map<String, Object> body) {
        OutsourceOtherIo io = parseIo(body);
        List<OutsourceOtherIoItem> items = parseItems(body);
        if (io.getWarehouseId() == null) throw new BusinessException("仓库不能为空");
        if (io.getIoType() == null || io.getIoType().isBlank()) throw new BusinessException("出入库类型不能为空");
        io.setCode(gen());
        io.setStatus(DocStatus.DRAFT.getCode()); // 创建时为草稿，审核后才变更库存
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) io.setCompanyId(cid);
        ioMapper.insert(io);
        for (OutsourceOtherIoItem it : items) {
            it.setOtherIoId(io.getId());
            if (cid != null && cid > 0) it.setCompanyId(cid);
            itemMapper.insert(it);
        }
        return R.ok();
    }

    @PutMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        OutsourceOtherIo old = ioMapper.selectById(id);
        if (old == null) throw new BusinessException("其他出入库单不存在");
        if (!DocStatus.DRAFT.getCode().equals(old.getStatus())) throw new BusinessException("仅草稿状态的单据可编辑");

        OutsourceOtherIo io = parseIo(body); io.setId(id);
        io.setCode(old.getCode()); io.setStatus(DocStatus.DRAFT.getCode());
        ioMapper.updateById(io);
        itemMapper.delete(new LambdaQueryWrapper<OutsourceOtherIoItem>().eq(OutsourceOtherIoItem::getOtherIoId, id));

        List<OutsourceOtherIoItem> items = parseItems(body);
        Long cid = CompanyContext.get();
        for (OutsourceOtherIoItem it : items) {
            it.setId(null); it.setOtherIoId(id);
            if (cid != null && cid > 0) it.setCompanyId(cid);
            itemMapper.insert(it);
        }
        return R.ok();
    }

    /** 审核：库存生效 */
    // E1/E3 口径（2026-09-12）：审核统一 /audit（旧 /approve 保留为别名）
    @PutMapping({"/{id}/audit", "/{id}/approve"})
    @Transactional(rollbackFor = Exception.class)
    public R<Void> approve(@PathVariable Long id) {
        OutsourceOtherIo old = ioMapper.selectById(id);
        if (old == null) throw new BusinessException("其他出入库单不存在");
        // P2-29：原子抢占 DRAFT→AUDITED，避免并发/双击重复应用库存与成本
        if (!DocStatusGuard.claim(ioMapper, OutsourceOtherIo::getId, id,
                OutsourceOtherIo::getStatus, DocStatus.DRAFT.getCode(), DocStatus.AUDITED.getCode())) {
            throw new BusinessException("仅草稿状态可审核");
        }
        List<OutsourceOtherIoItem> items = itemMapper.selectList(
            new LambdaQueryWrapper<OutsourceOtherIoItem>().eq(OutsourceOtherIoItem::getOtherIoId, id));
        if (items.isEmpty()) throw new BusinessException("请添加物料明细");
        // P2-33：类型必须合法（原先 ioType='BAD' 会走"非 IN 即 OUT"分支当作出库扣减库存）；数量必须为正
        if (!IoType.IN.getCode().equals(old.getIoType()) && !IoType.OUT.getCode().equals(old.getIoType()))
            throw new BusinessException("出入库类型非法：" + old.getIoType());
        for (OutsourceOtherIoItem it : items) {
            if (it.getQuantity() == null || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0)
                throw new BusinessException("数量必须大于 0（明细行ID=" + it.getId() + "）");
        }
        applyStock(old, items);
        // 移动加权成本：IN 单按明细单价入库加权（OUT 单不影响成本）
        if (IoType.IN.getCode().equals(old.getIoType())) {
            for (OutsourceOtherIoItem it : items) {
                costService.applyMaterial(it.getMaterialId(), it.getQuantity(), it.getUnitPrice(),
                        StockChangeType.OTHER_IN.getCode(), old.getId(), old.getCode());
                // 未填单价的入库不产生批次：成本仍为空时用主数据参考价兜底
                costService.fillMaterialCostIfEmpty(it.getMaterialId());
            }
        }
        OutsourceOtherIo u = new OutsourceOtherIo(); u.setId(id); u.setStatus(DocStatus.AUDITED.getCode());
        ioMapper.updateById(u);
        return R.ok();
    }

    /** 反审核：回滚库存，回到草稿 */
    // E1/E3 口径（2026-09-12）：反审核统一 /un-audit（旧 /unapprove 保留为别名）
    @PutMapping({"/{id}/un-audit", "/{id}/unapprove"})
    @Transactional(rollbackFor = Exception.class)
    public R<Void> unapprove(@PathVariable Long id) {
        OutsourceOtherIo old = ioMapper.selectById(id);
        if (old == null) throw new BusinessException("其他出入库单不存在");
        // P2-29：原子抢占 AUDITED→DRAFT，避免并发反审核重复回滚库存与成本
        if (!DocStatusGuard.claim(ioMapper, OutsourceOtherIo::getId, id,
                OutsourceOtherIo::getStatus, DocStatus.AUDITED.getCode(), DocStatus.DRAFT.getCode())) {
            throw new BusinessException("仅已审核状态可反审核");
        }
        List<OutsourceOtherIoItem> items = itemMapper.selectList(
            new LambdaQueryWrapper<OutsourceOtherIoItem>().eq(OutsourceOtherIoItem::getOtherIoId, id));
        revertStock(old, items);
        // 成本冲销：IN 单删除入库批次并反加权
        if (IoType.IN.getCode().equals(old.getIoType())) {
            costService.reverseByBill(StockChangeType.OTHER_IN.getCode(), id);
        }
        OutsourceOtherIo u = new OutsourceOtherIo(); u.setId(id); u.setStatus(DocStatus.DRAFT.getCode());
        ioMapper.updateById(u);
        return R.ok();
    }

    @PutMapping("/{id}/cancel")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> cancel(@PathVariable Long id) {
        OutsourceOtherIo old = ioMapper.selectById(id);
        if (old == null) throw new BusinessException("其他出入库单不存在");
        if (DocStatus.CANCELLED.getCode().equals(old.getStatus())) throw new BusinessException("单据已取消");
        // 草稿状态直接取消，无需回滚库存。
        // F7-50（2026-09-19）：原子抢占 DRAFT→CANCELLED（原"先查后改"可与 audit 并发互覆）
        if (!DocStatusGuard.claim(ioMapper, OutsourceOtherIo::getId, id,
                OutsourceOtherIo::getStatus, DocStatus.DRAFT.getCode(), DocStatus.CANCELLED.getCode()))
            throw new BusinessException("已审核的单据需先反审核再取消");
        return R.ok();
    }

    private void applyStock(OutsourceOtherIo io, List<OutsourceOtherIoItem> items) {
        boolean isIn = IoType.IN.getCode().equals(io.getIoType());
        StockChangeType type = isIn ? StockChangeType.OTHER_IN : StockChangeType.OTHER_OUT;
        for (OutsourceOtherIoItem it : items) {
            Long matId = it.getMaterialId();
            if (matId == null) continue;
            BigDecimal qty = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
            BigDecimal delta = isIn ? qty : qty.negate();
            // 库存 + 流水写入统一到 WarehouseStockService（架构债 A4，原先在 Controller 层直写 mapper）；
            // 委外其他出入库沿用既有的"允许负数"口径（不做库存充足校验），避免改语义
            warehouseStockService.changeMaterialStockAllowNegative(io.getWarehouseId(), matId, delta,
                    type.getCode(), io.getCode(), RelatedBillType.OTHER_IO, null, null, io.getId());
        }
    }

    private void revertStock(OutsourceOtherIo io, List<OutsourceOtherIoItem> items) {
        boolean isIn = IoType.IN.getCode().equals(io.getIoType());
        StockChangeType type = isIn ? StockChangeType.CANCEL_IN : StockChangeType.CANCEL_OUT;
        for (OutsourceOtherIoItem it : items) {
            Long matId = it.getMaterialId();
            if (matId == null) continue;
            BigDecimal qty = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
            BigDecimal delta = isIn ? qty.negate() : qty;
            // 库存 + 流水写入统一到 WarehouseStockService（架构债 A4），沿用"允许负数"口径
            warehouseStockService.changeMaterialStockAllowNegative(io.getWarehouseId(), matId, delta,
                    type.getCode(), io.getCode(), RelatedBillType.OTHER_IO, null, null, io.getId());
        }
    }

    @SuppressWarnings("unchecked")
    private OutsourceOtherIo parseIo(Map<String, Object> body) {
        OutsourceOtherIo o = new OutsourceOtherIo();
        if (body.get("warehouseId") != null) o.setWarehouseId(Long.valueOf(body.get("warehouseId").toString()));
        o.setIoType((String) body.get("ioType"));
        if (body.get("ioDate") != null && !body.get("ioDate").toString().isBlank())
            o.setIoDate(LocalDate.parse(body.get("ioDate").toString()));
        o.setRemark((String) body.get("remark"));
        return o;
    }

    @SuppressWarnings("unchecked")
    private List<OutsourceOtherIoItem> parseItems(Map<String, Object> body) {
        List<OutsourceOtherIoItem> list = new ArrayList<>();
        Object obj = body.get("items");
        if (obj instanceof List<?> raw) {
            for (Object o : raw) {
                if (o instanceof Map<?, ?> m) {
                    Map<String, Object> map = (Map<String, Object>) m;
                    OutsourceOtherIoItem it = new OutsourceOtherIoItem();
                    if (map.get("materialId") != null) it.setMaterialId(Long.valueOf(map.get("materialId").toString()));
                    if (map.get("materialTypeId") != null) it.setMaterialTypeId(Long.valueOf(map.get("materialTypeId").toString()));
                    it.setUnit((String) map.get("unit"));
                    if (map.get("quantity") != null && !map.get("quantity").toString().isBlank())
                        it.setQuantity(new BigDecimal(map.get("quantity").toString()));
                    if (map.get("unit_price") != null && !map.get("unit_price").toString().isBlank())
                        it.setUnitPrice(new BigDecimal(map.get("unit_price").toString()));
                    it.setRemark((String) map.get("remark"));
                    list.add(it);
                }
            }
        }
        return list;
    }

    private String gen() {
        String d = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String pat = BillPrefix.OUTSOURCE_OTHER_IO + d;
        LambdaQueryWrapper<OutsourceOtherIo> w = new LambdaQueryWrapper<OutsourceOtherIo>()
                .likeRight(OutsourceOtherIo::getCode, pat).orderByDesc(OutsourceOtherIo::getCode).last("LIMIT 1");
        OutsourceOtherIo last = ioMapper.selectOne(w);
        // F7-81②（2026-09-20）：统一走 BillNoSeq（尾段连续数字解析 + 序号超 999 自动扩位，不再静默回退）
        int seq = last != null ? com.beichen.erp.common.BillNoSeq.lastSeq(last.getCode(), pat) + 1 : 1;
        return com.beichen.erp.common.BillNoSeq.format(pat, seq);
    }

    /** 根据委外物料ID查询名称，用于展示回填（ID关联查询替代冗余name字段） */
    private String getMaterialNameById(Long materialId) {
        if (materialId == null) return "";
        OutsourceMaterial m = materialMapper.selectById(materialId);
        return m != null ? m.getMaterialName() : "";
    }

    /** 根据 物料类型ID 查询类型名称，空安全返回 "-" */
    private String getMaterialTypeNameById(Long materialTypeId) {
        if (materialTypeId == null) return "-";
        com.beichen.erp.dev.entity.MaterialType bt = materialTypeMapper.selectById(materialTypeId);
        return bt != null ? bt.getTypeName() : "-";
    }
}
