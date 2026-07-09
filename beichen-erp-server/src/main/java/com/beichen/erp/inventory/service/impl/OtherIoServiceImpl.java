package com.beichen.erp.inventory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.inventory.entity.InventoryOtherIo;
import com.beichen.erp.inventory.entity.InventoryOtherIoItem;
import com.beichen.erp.inventory.mapper.InventoryOtherIoMapper;
import com.beichen.erp.inventory.mapper.InventoryOtherIoItemMapper;
import com.beichen.erp.inventory.service.InventoryWarehouseStockService;
import com.beichen.erp.inventory.service.OtherIoService;
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
    private final InventoryWarehouseStockService stockService;

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
            return m;
        }).toList());
        return res;
    }

    @Override
    public InventoryOtherIo getById(Long id) { return ioMapper.selectById(id); }

    @Override
    public List<InventoryOtherIoItem> getItems(Long otherIoId) {
        return itemMapper.selectList(new LambdaQueryWrapper<InventoryOtherIoItem>().eq(InventoryOtherIoItem::getOtherIoId, otherIoId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(InventoryOtherIo otherIo, List<InventoryOtherIoItem> items) {
        if (otherIo.getWarehouseId() == null) throw new BusinessException("仓库不能为空");
        if (otherIo.getIoType() == null || otherIo.getIoType().isBlank()) throw new BusinessException("出入库类型不能为空");
        otherIo.setCode(gen("QT-"));
        otherIo.setStatus("草稿");
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
        if (!"草稿".equals(old.getStatus())) throw new BusinessException("只有草稿状态可编辑");
        otherIo.setCode(old.getCode());
        ioMapper.updateById(otherIo);
        itemMapper.delete(new LambdaQueryWrapper<InventoryOtherIoItem>().eq(InventoryOtherIoItem::getOtherIoId, otherIo.getId()));
        Long cid = CompanyContext.get();
        for (InventoryOtherIoItem it : items) {
            it.setId(null);
            it.setOtherIoId(otherIo.getId());
            if (cid != null && cid > 0) it.setCompanyId(cid);
            itemMapper.insert(it);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        InventoryOtherIo old = ioMapper.selectById(id);
        if (old == null) throw new BusinessException("其他出入库单不存在");
        if (!"草稿".equals(old.getStatus())) throw new BusinessException("只有草稿状态可作废");
        InventoryOtherIo u = new InventoryOtherIo(); u.setId(id); u.setStatus("已作废");
        ioMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        InventoryOtherIo io = ioMapper.selectById(id);
        if (io == null) throw new BusinessException("其他出入库单不存在");
        if (!"草稿".equals(io.getStatus())) throw new BusinessException("只有草稿状态可审核");
        List<InventoryOtherIoItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<InventoryOtherIoItem>().eq(InventoryOtherIoItem::getOtherIoId, id));
        String changeType = "其他入库".equals(io.getIoType()) ? "其他入库" : "其他出库";
        for (InventoryOtherIoItem it : items) {
            BigDecimal q = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
            BigDecimal delta = "其他入库".equals(io.getIoType()) ? q : q.negate();
            stockService.changeStock(io.getWarehouseId(), it.getMaterialName(), delta,
                    changeType, io.getCode(), changeType, it.getMaterialId(), it.getSpec());
        }
        InventoryOtherIo u = new InventoryOtherIo(); u.setId(id); u.setStatus("已审核");
        ioMapper.updateById(u);
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
