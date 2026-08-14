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
import com.beichen.erp.warehouse.entity.WarehouseStock;
import com.beichen.erp.warehouse.entity.WarehouseStockLog;
import com.beichen.erp.warehouse.mapper.WarehouseStockMapper;
import com.beichen.erp.warehouse.mapper.WarehouseStockLogMapper;
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
    private final WarehouseStockMapper stockMapper;
    private final WarehouseStockLogMapper stockLogMapper;
    private final OutsourceMaterialMapper materialMapper;
    private final com.beichen.erp.dev.mapper.BomTypeMapper bomTypeMapper;
    private final WarehouseMapper warehouseMapper;
    private final WarehouseStockService warehouseStockService;

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
    @PutMapping("/{id}/approve")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> approve(@PathVariable Long id) {
        OutsourceOtherIo old = ioMapper.selectById(id);
        if (old == null) throw new BusinessException("其他出入库单不存在");
        if (!DocStatus.DRAFT.getCode().equals(old.getStatus())) throw new BusinessException("仅草稿状态可审核");
        List<OutsourceOtherIoItem> items = itemMapper.selectList(
            new LambdaQueryWrapper<OutsourceOtherIoItem>().eq(OutsourceOtherIoItem::getOtherIoId, id));
        applyStock(old, items);
        OutsourceOtherIo u = new OutsourceOtherIo(); u.setId(id); u.setStatus(DocStatus.AUDITED.getCode());
        ioMapper.updateById(u);
        return R.ok();
    }

    /** 反审核：回滚库存，回到草稿 */
    @PutMapping("/{id}/unapprove")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> unapprove(@PathVariable Long id) {
        OutsourceOtherIo old = ioMapper.selectById(id);
        if (old == null) throw new BusinessException("其他出入库单不存在");
        if (!DocStatus.AUDITED.getCode().equals(old.getStatus())) throw new BusinessException("仅已审核状态可反审核");
        List<OutsourceOtherIoItem> items = itemMapper.selectList(
            new LambdaQueryWrapper<OutsourceOtherIoItem>().eq(OutsourceOtherIoItem::getOtherIoId, id));
        revertStock(old, items);
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
        if (DocStatus.AUDITED.getCode().equals(old.getStatus())) throw new BusinessException("已审核的单据需先反审核再取消");
        // 草稿状态直接取消，无需回滚库存
        OutsourceOtherIo u = new OutsourceOtherIo(); u.setId(id); u.setStatus(DocStatus.CANCELLED.getCode());
        ioMapper.updateById(u);
        return R.ok();
    }

    private void applyStock(OutsourceOtherIo io, List<OutsourceOtherIoItem> items) {
        StockChangeType type = IoType.IN.getCode().equals(io.getIoType()) ? StockChangeType.OTHER_IN : StockChangeType.OTHER_OUT;
        for (OutsourceOtherIoItem it : items) {
            Long matId = it.getMaterialId();
            if (matId == null) continue;
            BigDecimal qty = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
            BigDecimal delta = IoType.IN.getCode().equals(io.getIoType()) ? qty : qty.negate();
            LambdaQueryWrapper<WarehouseStock> w = new LambdaQueryWrapper<WarehouseStock>()
                    .eq(WarehouseStock::getWarehouseId, io.getWarehouseId())
                    .eq(WarehouseStock::getMaterialId, matId)
                    .eq(WarehouseStock::getQualityType, QualityType.GOOD.getCode());
            WarehouseStock stock = stockMapper.selectOne(w);
            BigDecimal before = stock != null && stock.getQuantity() != null ? stock.getQuantity() : BigDecimal.ZERO;
            BigDecimal after = before.add(delta);
            if (stock == null) {
                stock = new WarehouseStock();
                stock.setWarehouseId(io.getWarehouseId()); stock.setMaterialId(matId);
                stock.setQualityType(QualityType.GOOD.getCode()); stock.setQuantity(after);
                stockMapper.insert(stock);
            } else {
                stock.setQuantity(after);
                stockMapper.updateById(stock);
            }
            WarehouseStockLog logEntry = new WarehouseStockLog();
            logEntry.setWarehouseId(io.getWarehouseId()); logEntry.setMaterialId(matId);
            logEntry.setMaterialName(getMaterialNameById(matId)); logEntry.setChangeType(type.getLabel());
            logEntry.setChangeQuantity(delta); logEntry.setBeforeQuantity(before);
            logEntry.setAfterQuantity(after); logEntry.setRelatedOrderCode(io.getCode());
            logEntry.setRelatedBillNo(io.getCode()); logEntry.setRelatedBillType(RelatedBillType.OTHER_IO.getCode());
            logEntry.setRelatedBillId(io.getId());
            stockLogMapper.insert(logEntry);
        }
    }

    private void revertStock(OutsourceOtherIo io, List<OutsourceOtherIoItem> items) {
        StockChangeType type = IoType.IN.getCode().equals(io.getIoType()) ? StockChangeType.CANCEL_IN : StockChangeType.CANCEL_OUT;
        for (OutsourceOtherIoItem it : items) {
            Long matId = it.getMaterialId();
            if (matId == null) continue;
            BigDecimal qty = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
            BigDecimal delta = IoType.IN.getCode().equals(io.getIoType()) ? qty.negate() : qty;
            LambdaQueryWrapper<WarehouseStock> w = new LambdaQueryWrapper<WarehouseStock>()
                    .eq(WarehouseStock::getWarehouseId, io.getWarehouseId())
                    .eq(WarehouseStock::getMaterialId, matId)
                    .eq(WarehouseStock::getQualityType, QualityType.GOOD.getCode());
            WarehouseStock stock = stockMapper.selectOne(w);
            BigDecimal before = stock != null && stock.getQuantity() != null ? stock.getQuantity() : BigDecimal.ZERO;
            if (stock != null) {
                stock.setQuantity(before.add(delta));
                stockMapper.updateById(stock);
            }
            WarehouseStockLog logEntry = new WarehouseStockLog();
            logEntry.setWarehouseId(io.getWarehouseId()); logEntry.setMaterialId(matId);
            logEntry.setMaterialName(getMaterialNameById(matId)); logEntry.setChangeType(type.getLabel());
            logEntry.setChangeQuantity(delta); logEntry.setBeforeQuantity(before);
            logEntry.setAfterQuantity(before.add(delta)); logEntry.setRelatedOrderCode(io.getCode());
            logEntry.setRelatedBillNo(io.getCode()); logEntry.setRelatedBillType(RelatedBillType.OTHER_IO.getCode());
            logEntry.setRelatedBillId(io.getId());
            stockLogMapper.insert(logEntry);
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
                    if (map.get("bomTypeId") != null) it.setBomTypeId(Long.valueOf(map.get("bomTypeId").toString()));
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
        int seq = 1;
        if (last != null && last.getCode() != null) {
            try { seq = Integer.parseInt(last.getCode().substring(last.getCode().length() - 3)) + 1; } catch (Exception e) { seq = 1; }
        }
        return pat + String.format("%03d", seq);
    }

    /** 根据委外物料ID查询名称，用于展示回填（ID关联查询替代冗余name字段） */
    private String getMaterialNameById(Long materialId) {
        if (materialId == null) return "";
        OutsourceMaterial m = materialMapper.selectById(materialId);
        return m != null ? m.getMaterialName() : "";
    }

    /** 根据 BOM 类型ID 查询类型名称，空安全返回 "-" */
    private String getBomTypeNameById(Long bomTypeId) {
        if (bomTypeId == null) return "-";
        com.beichen.erp.dev.entity.BomType bt = bomTypeMapper.selectById(bomTypeId);
        return bt != null ? bt.getTypeName() : "-";
    }
}
