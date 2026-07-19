package com.beichen.erp.outsource.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.outsource.entity.*;
import com.beichen.erp.outsource.mapper.*;
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
    private final OutsourceWarehouseStockMapper stockMapper;
    private final OutsourceStockLogMapper stockLogMapper;
    private final OutsourceMaterialMapper materialMapper;

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
        io.setStatus("已确认");
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) io.setCompanyId(cid);
        ioMapper.insert(io);
        for (OutsourceOtherIoItem it : items) {
            it.setId(null); it.setOtherIoId(io.getId());
            if (cid != null && cid > 0) it.setCompanyId(cid);
            itemMapper.insert(it);
        }
        applyStock(io, items);
        return R.ok();
    }

    @PutMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        OutsourceOtherIo old = ioMapper.selectById(id);
        if (old == null) throw new BusinessException("其他出入库单不存在");
        if ("已取消".equals(old.getStatus())) throw new BusinessException("已取消的单据不可编辑");

        List<OutsourceOtherIoItem> oldItems = itemMapper.selectList(
            new LambdaQueryWrapper<OutsourceOtherIoItem>().eq(OutsourceOtherIoItem::getOtherIoId, id));
        revertStock(old, oldItems);

        OutsourceOtherIo io = parseIo(body); io.setId(id);
        io.setCode(old.getCode()); io.setStatus("已确认");
        ioMapper.updateById(io);
        itemMapper.delete(new LambdaQueryWrapper<OutsourceOtherIoItem>().eq(OutsourceOtherIoItem::getOtherIoId, id));

        List<OutsourceOtherIoItem> items = parseItems(body);
        Long cid = CompanyContext.get();
        for (OutsourceOtherIoItem it : items) {
            it.setId(null); it.setOtherIoId(id);
            if (cid != null && cid > 0) it.setCompanyId(cid);
            itemMapper.insert(it);
        }
        applyStock(io, items);
        return R.ok();
    }

    @PutMapping("/{id}/cancel")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> cancel(@PathVariable Long id) {
        OutsourceOtherIo old = ioMapper.selectById(id);
        if (old == null) throw new BusinessException("其他出入库单不存在");
        if ("已取消".equals(old.getStatus())) throw new BusinessException("单据已取消");
        List<OutsourceOtherIoItem> items = itemMapper.selectList(
            new LambdaQueryWrapper<OutsourceOtherIoItem>().eq(OutsourceOtherIoItem::getOtherIoId, id));
        revertStock(old, items);
        OutsourceOtherIo u = new OutsourceOtherIo(); u.setId(id); u.setStatus("已取消");
        ioMapper.updateById(u);
        return R.ok();
    }

    private void applyStock(OutsourceOtherIo io, List<OutsourceOtherIoItem> items) {
        String changeType = "入库".equals(io.getIoType()) ? "其他入库" : "其他出库";
        for (OutsourceOtherIoItem it : items) {
            BigDecimal qty = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
            BigDecimal delta = "入库".equals(io.getIoType()) ? qty : qty.negate();
            // 确保物料存在
            Long matId = it.getMaterialId();
            if (matId == null && it.getMaterialName() != null) {
                matId = materialMapper.findIdByName(it.getMaterialName());
                if (matId == null) {
                    OutsourceMaterial mat = new OutsourceMaterial();
                    mat.setMaterialName(it.getMaterialName());
                    mat.setMaterialType(it.getMaterialType());
                    mat.setUnit(it.getUnit());
                    mat.setStatus(1);
                    materialMapper.insert(mat);
                    matId = mat.getId();
                }
            }
            if (matId == null) continue;

            LambdaQueryWrapper<OutsourceWarehouseStock> w = new LambdaQueryWrapper<OutsourceWarehouseStock>()
                    .eq(OutsourceWarehouseStock::getWarehouseId, io.getWarehouseId())
                    .eq(OutsourceWarehouseStock::getMaterialId, matId)
                    .eq(OutsourceWarehouseStock::getQualityType, "良品");
            OutsourceWarehouseStock stock = stockMapper.selectOne(w);
            BigDecimal before = stock != null && stock.getQuantity() != null ? stock.getQuantity() : BigDecimal.ZERO;
            BigDecimal after = before.add(delta);
            if (stock == null) {
                stock = new OutsourceWarehouseStock();
                stock.setWarehouseId(io.getWarehouseId()); stock.setMaterialId(matId);
                stock.setQualityType("良品"); stock.setQuantity(after);
                stockMapper.insert(stock);
            } else {
                stock.setQuantity(after);
                stockMapper.updateById(stock);
            }
            OutsourceStockLog logEntry = new OutsourceStockLog();
            logEntry.setWarehouseId(io.getWarehouseId()); logEntry.setMaterialId(matId);
            logEntry.setMaterialName(it.getMaterialName()); logEntry.setChangeType(changeType);
            logEntry.setChangeQuantity(delta); logEntry.setBeforeQuantity(before);
            logEntry.setAfterQuantity(after); logEntry.setRelatedOrderCode(io.getCode());
            stockLogMapper.insert(logEntry);
        }
    }

    private void revertStock(OutsourceOtherIo io, List<OutsourceOtherIoItem> items) {
        String changeType = "入库".equals(io.getIoType()) ? "取消入库" : "取消出库";
        for (OutsourceOtherIoItem it : items) {
            BigDecimal qty = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
            BigDecimal delta = "入库".equals(io.getIoType()) ? qty.negate() : qty;
            Long matId = it.getMaterialId();
            if (matId == null && it.getMaterialName() != null)
                matId = materialMapper.findIdByName(it.getMaterialName());
            if (matId == null) continue;

            LambdaQueryWrapper<OutsourceWarehouseStock> w = new LambdaQueryWrapper<OutsourceWarehouseStock>()
                    .eq(OutsourceWarehouseStock::getWarehouseId, io.getWarehouseId())
                    .eq(OutsourceWarehouseStock::getMaterialId, matId)
                    .eq(OutsourceWarehouseStock::getQualityType, "良品");
            OutsourceWarehouseStock stock = stockMapper.selectOne(w);
            BigDecimal before = stock != null && stock.getQuantity() != null ? stock.getQuantity() : BigDecimal.ZERO;
            if (stock != null) {
                stock.setQuantity(before.add(delta));
                stockMapper.updateById(stock);
            }
            OutsourceStockLog logEntry = new OutsourceStockLog();
            logEntry.setWarehouseId(io.getWarehouseId()); logEntry.setMaterialId(matId);
            logEntry.setMaterialName(it.getMaterialName()); logEntry.setChangeType(changeType);
            logEntry.setChangeQuantity(delta); logEntry.setBeforeQuantity(before);
            logEntry.setAfterQuantity(before.add(delta)); logEntry.setRelatedOrderCode(io.getCode());
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
                    it.setMaterialName((String) map.get("materialName"));
                    it.setMaterialType((String) map.get("materialType"));
                    it.setUnit((String) map.get("unit"));
                    if (map.get("quantity") != null && !map.get("quantity").toString().isBlank())
                        it.setQuantity(new BigDecimal(map.get("quantity").toString()));
                    it.setRemark((String) map.get("remark"));
                    list.add(it);
                }
            }
        }
        return list;
    }

    private String gen() {
        String d = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String pat = "OWO-" + d;
        LambdaQueryWrapper<OutsourceOtherIo> w = new LambdaQueryWrapper<OutsourceOtherIo>()
                .likeRight(OutsourceOtherIo::getCode, pat).orderByDesc(OutsourceOtherIo::getCode).last("LIMIT 1");
        OutsourceOtherIo last = ioMapper.selectOne(w);
        int seq = 1;
        if (last != null && last.getCode() != null) {
            try { seq = Integer.parseInt(last.getCode().substring(last.getCode().length() - 3)) + 1; } catch (Exception e) { seq = 1; }
        }
        return pat + String.format("%03d", seq);
    }
}
