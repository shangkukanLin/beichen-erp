package com.beichen.erp.inventory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.inventory.entity.InventoryStockTake;
import com.beichen.erp.inventory.entity.InventoryStockTakeItem;
import com.beichen.erp.inventory.entity.InventoryWarehouse;
import com.beichen.erp.inventory.entity.InventoryWarehouseStock;
import com.beichen.erp.inventory.mapper.InventoryStockTakeMapper;
import com.beichen.erp.inventory.mapper.InventoryStockTakeItemMapper;
import com.beichen.erp.inventory.mapper.InventoryWarehouseMapper;
import com.beichen.erp.inventory.mapper.InventoryWarehouseStockMapper;
import com.beichen.erp.inventory.service.InventoryWarehouseStockService;
import com.beichen.erp.inventory.service.StockTakeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class StockTakeServiceImpl implements StockTakeService {

    private final InventoryStockTakeMapper takeMapper;
    private final InventoryStockTakeItemMapper itemMapper;
    private final InventoryWarehouseStockMapper stockMapper;
    private final InventoryWarehouseMapper warehouseMapper;
    private final InventoryWarehouseStockService stockService;

    @Override
    public Page<Map<String, Object>> page(String status, Long warehouseId, String code, int pageNum, int pageSize) {
        LambdaQueryWrapper<InventoryStockTake> w = new LambdaQueryWrapper<InventoryStockTake>()
                .eq(status != null && !status.isBlank(), InventoryStockTake::getStatus, status)
                .eq(warehouseId != null, InventoryStockTake::getWarehouseId, warehouseId)
                .like(code != null && !code.isBlank(), InventoryStockTake::getCode, code)
                .orderByDesc(InventoryStockTake::getId);
        Page<InventoryStockTake> raw = takeMapper.selectPage(new Page<>(pageNum, pageSize), w);
        Page<Map<String, Object>> res = new Page<>(pageNum, pageSize, raw.getTotal());
        res.setRecords(raw.getRecords().stream().map(o -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", o.getId()); m.put("code", o.getCode());
            m.put("warehouseId", o.getWarehouseId()); m.put("takeDate", o.getTakeDate());
            m.put("status", o.getStatus()); m.put("remark", o.getRemark());
            m.put("createTime", o.getCreateTime());
            if (o.getWarehouseId() != null) {
                InventoryWarehouse wh = warehouseMapper.selectById(o.getWarehouseId());
                m.put("warehouseName", wh != null ? wh.getWarehouseName() : "");
            }
            return m;
        }).toList());
        return res;
    }

    @Override
    public InventoryStockTake getById(Long id) { return takeMapper.selectById(id); }

    @Override
    public List<InventoryStockTakeItem> getItems(Long takeId) {
        return itemMapper.selectList(new LambdaQueryWrapper<InventoryStockTakeItem>().eq(InventoryStockTakeItem::getTakeId, takeId));
    }

    private BigDecimal currentStock(Long warehouseId, String materialName) {
        InventoryWarehouseStock st = stockMapper.selectOne(new LambdaQueryWrapper<InventoryWarehouseStock>()
                .eq(InventoryWarehouseStock::getWarehouseId, warehouseId)
                .eq(InventoryWarehouseStock::getProductName, materialName));
        return st != null && st.getQuantity() != null ? st.getQuantity() : BigDecimal.ZERO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(InventoryStockTake take, List<InventoryStockTakeItem> items) {
        if (take.getWarehouseId() == null) throw new BusinessException("仓库不能为空");
        take.setCode(gen("PD-"));
        take.setStatus("草稿");
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) take.setCompanyId(cid);
        takeMapper.insert(take);
        for (InventoryStockTakeItem it : items) {
            it.setId(null);
            it.setTakeId(take.getId());
            BigDecimal book = currentStock(take.getWarehouseId(), it.getMaterialName());
            it.setBookQuantity(book);
            BigDecimal actual = it.getActualQuantity() != null ? it.getActualQuantity() : BigDecimal.ZERO;
            it.setProfitLossQuantity(actual.subtract(book));
            if (cid != null && cid > 0) it.setCompanyId(cid);
            itemMapper.insert(it);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(InventoryStockTake take, List<InventoryStockTakeItem> items) {
        InventoryStockTake old = takeMapper.selectById(take.getId());
        if (old == null) throw new BusinessException("盘点单不存在");
        if (!"草稿".equals(old.getStatus())) throw new BusinessException("只有草稿状态可编辑");
        take.setCode(old.getCode());
        takeMapper.updateById(take);
        itemMapper.delete(new LambdaQueryWrapper<InventoryStockTakeItem>().eq(InventoryStockTakeItem::getTakeId, take.getId()));
        Long cid = CompanyContext.get();
        for (InventoryStockTakeItem it : items) {
            it.setId(null);
            it.setTakeId(take.getId());
            BigDecimal book = currentStock(take.getWarehouseId(), it.getMaterialName());
            it.setBookQuantity(book);
            BigDecimal actual = it.getActualQuantity() != null ? it.getActualQuantity() : BigDecimal.ZERO;
            it.setProfitLossQuantity(actual.subtract(book));
            if (cid != null && cid > 0) it.setCompanyId(cid);
            itemMapper.insert(it);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        InventoryStockTake old = takeMapper.selectById(id);
        if (old == null) throw new BusinessException("盘点单不存在");
        if (!"草稿".equals(old.getStatus())) throw new BusinessException("只有草稿状态可作废");
        InventoryStockTake u = new InventoryStockTake(); u.setId(id); u.setStatus("已作废");
        takeMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        InventoryStockTake take = takeMapper.selectById(id);
        if (take == null) throw new BusinessException("盘点单不存在");
        if (!"草稿".equals(take.getStatus())) throw new BusinessException("只有草稿状态可审核");
        List<InventoryStockTakeItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<InventoryStockTakeItem>().eq(InventoryStockTakeItem::getTakeId, id));
        for (InventoryStockTakeItem it : items) {
            BigDecimal book = it.getBookQuantity() != null ? it.getBookQuantity() : BigDecimal.ZERO;
            BigDecimal actual = it.getActualQuantity() != null ? it.getActualQuantity() : BigDecimal.ZERO;
            BigDecimal pl = actual.subtract(book);
            if (pl.compareTo(BigDecimal.ZERO) != 0) {
                stockService.changeStock(take.getWarehouseId(), it.getMaterialName(), pl,
                        pl.compareTo(BigDecimal.ZERO) > 0 ? "盘点溢" : "盘点损",
                        take.getCode(), "盘点", it.getProductId(), it.getSpec());
            }
        }
        InventoryStockTake u = new InventoryStockTake(); u.setId(id); u.setStatus("已审核");
        takeMapper.updateById(u);
    }

    private String gen(String prefix) {
        String d = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String pat = prefix + d;
        LambdaQueryWrapper<InventoryStockTake> w = new LambdaQueryWrapper<InventoryStockTake>()
                .likeRight(InventoryStockTake::getCode, pat).orderByDesc(InventoryStockTake::getCode).last("LIMIT 1");
        InventoryStockTake last = takeMapper.selectOne(w);
        int seq = 1;
        if (last != null && last.getCode() != null) {
            try { seq = Integer.parseInt(last.getCode().substring(last.getCode().length() - 3)) + 1; } catch (Exception e) { seq = 1; }
        }
        return prefix + d + String.format("%03d", seq);
    }
}
