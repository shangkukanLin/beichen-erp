package com.beichen.erp.inventory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.inventory.entity.InventoryTransfer;
import com.beichen.erp.inventory.entity.InventoryTransferItem;
import com.beichen.erp.inventory.mapper.InventoryTransferMapper;
import com.beichen.erp.inventory.mapper.InventoryTransferItemMapper;
import com.beichen.erp.inventory.service.InventoryWarehouseStockService;
import com.beichen.erp.inventory.service.TransferService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TransferServiceImpl implements TransferService {

    private final InventoryTransferMapper transferMapper;
    private final InventoryTransferItemMapper itemMapper;
    private final InventoryWarehouseStockService stockService;

    @Override
    public Page<Map<String, Object>> page(String status, Long fromWarehouseId, Long toWarehouseId, int pageNum, int pageSize) {
        LambdaQueryWrapper<InventoryTransfer> w = new LambdaQueryWrapper<InventoryTransfer>()
                .eq(status != null && !status.isBlank(), InventoryTransfer::getStatus, status)
                .eq(fromWarehouseId != null, InventoryTransfer::getFromWarehouseId, fromWarehouseId)
                .eq(toWarehouseId != null, InventoryTransfer::getToWarehouseId, toWarehouseId)
                .orderByDesc(InventoryTransfer::getId);
        Page<InventoryTransfer> raw = transferMapper.selectPage(new Page<>(pageNum, pageSize), w);
        Page<Map<String, Object>> res = new Page<>(pageNum, pageSize, raw.getTotal());
        res.setRecords(raw.getRecords().stream().map(o -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", o.getId()); m.put("code", o.getCode());
            m.put("fromWarehouseId", o.getFromWarehouseId()); m.put("toWarehouseId", o.getToWarehouseId());
            m.put("transferDate", o.getTransferDate()); m.put("status", o.getStatus()); m.put("remark", o.getRemark());
            m.put("createTime", o.getCreateTime());
            return m;
        }).toList());
        return res;
    }

    @Override
    public InventoryTransfer getById(Long id) { return transferMapper.selectById(id); }

    @Override
    public List<InventoryTransferItem> getItems(Long transferId) {
        return itemMapper.selectList(new LambdaQueryWrapper<InventoryTransferItem>().eq(InventoryTransferItem::getTransferId, transferId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(InventoryTransfer transfer, List<InventoryTransferItem> items) {
        if (transfer.getFromWarehouseId() == null || transfer.getToWarehouseId() == null)
            throw new BusinessException("调出/调入仓库不能为空");
        if (transfer.getFromWarehouseId().equals(transfer.getToWarehouseId()))
            throw new BusinessException("调出与调入仓库不能相同");
        transfer.setCode(gen("DB-"));
        transfer.setStatus("草稿");
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) transfer.setCompanyId(cid);
        transferMapper.insert(transfer);
        for (InventoryTransferItem it : items) {
            it.setId(null);
            it.setTransferId(transfer.getId());
            if (cid != null && cid > 0) it.setCompanyId(cid);
            itemMapper.insert(it);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(InventoryTransfer transfer, List<InventoryTransferItem> items) {
        InventoryTransfer old = transferMapper.selectById(transfer.getId());
        if (old == null) throw new BusinessException("调拨单不存在");
        if (!"草稿".equals(old.getStatus())) throw new BusinessException("只有草稿状态可编辑");
        transfer.setCode(old.getCode());
        transferMapper.updateById(transfer);
        itemMapper.delete(new LambdaQueryWrapper<InventoryTransferItem>().eq(InventoryTransferItem::getTransferId, transfer.getId()));
        Long cid = CompanyContext.get();
        for (InventoryTransferItem it : items) {
            it.setId(null);
            it.setTransferId(transfer.getId());
            if (cid != null && cid > 0) it.setCompanyId(cid);
            itemMapper.insert(it);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        InventoryTransfer old = transferMapper.selectById(id);
        if (old == null) throw new BusinessException("调拨单不存在");
        if (!"草稿".equals(old.getStatus())) throw new BusinessException("只有草稿状态可作废");
        InventoryTransfer u = new InventoryTransfer(); u.setId(id); u.setStatus("已作废");
        transferMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        InventoryTransfer transfer = transferMapper.selectById(id);
        if (transfer == null) throw new BusinessException("调拨单不存在");
        if (!"草稿".equals(transfer.getStatus())) throw new BusinessException("只有草稿状态可审核");
        List<InventoryTransferItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<InventoryTransferItem>().eq(InventoryTransferItem::getTransferId, id));
        for (InventoryTransferItem it : items) {
            BigDecimal q = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
            stockService.changeStock(transfer.getFromWarehouseId(), it.getMaterialName(), q.negate(),
                    "调拨出", transfer.getCode(), "调拨", it.getProductId(), it.getSpec());
            stockService.changeStock(transfer.getToWarehouseId(), it.getMaterialName(), q,
                    "调拨入", transfer.getCode(), "调拨", it.getProductId(), it.getSpec());
        }
        InventoryTransfer u = new InventoryTransfer(); u.setId(id); u.setStatus("已审核");
        transferMapper.updateById(u);
    }

    private String gen(String prefix) {
        String d = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String pat = prefix + d;
        LambdaQueryWrapper<InventoryTransfer> w = new LambdaQueryWrapper<InventoryTransfer>()
                .likeRight(InventoryTransfer::getCode, pat).orderByDesc(InventoryTransfer::getCode).last("LIMIT 1");
        InventoryTransfer last = transferMapper.selectOne(w);
        int seq = 1;
        if (last != null && last.getCode() != null) {
            try { seq = Integer.parseInt(last.getCode().substring(last.getCode().length() - 3)) + 1; } catch (Exception e) { seq = 1; }
        }
        return prefix + d + String.format("%03d", seq);
    }
}
