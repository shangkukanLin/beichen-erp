package com.beichen.erp.inventory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.inventory.common.RelatedBillType;
import com.beichen.erp.common.DocStatus;
import com.beichen.erp.inventory.common.StockChangeType;
import com.beichen.erp.inventory.entity.InventoryWarehouseMove;
import com.beichen.erp.inventory.entity.InventoryWarehouseMoveItem;
import com.beichen.erp.inventory.mapper.InventoryWarehouseMoveMapper;
import com.beichen.erp.inventory.mapper.InventoryWarehouseMoveItemMapper;
import com.beichen.erp.inventory.service.InventoryWarehouseStockService;
import com.beichen.erp.inventory.service.WarehouseMoveService;
import com.beichen.erp.material.entity.Product;
import com.beichen.erp.material.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WarehouseMoveServiceImpl implements WarehouseMoveService {

    private final InventoryWarehouseMoveMapper moveMapper;
    private final InventoryWarehouseMoveItemMapper itemMapper;
    private final InventoryWarehouseStockService stockService;
    private final ProductMapper productMapper;

    @Override
    public Page<Map<String, Object>> page(String status, Long fromWarehouseId, Long toWarehouseId, int pageNum, int pageSize) {
        LambdaQueryWrapper<InventoryWarehouseMove> w = new LambdaQueryWrapper<InventoryWarehouseMove>()
                .eq(status != null && !status.isBlank(), InventoryWarehouseMove::getStatus, status)
                .eq(fromWarehouseId != null, InventoryWarehouseMove::getFromWarehouseId, fromWarehouseId)
                .eq(toWarehouseId != null, InventoryWarehouseMove::getToWarehouseId, toWarehouseId)
                .orderByDesc(InventoryWarehouseMove::getId);
        Page<InventoryWarehouseMove> raw = moveMapper.selectPage(new Page<>(pageNum, pageSize), w);
        // 批量加载明细
        List<Long> moveIds = raw.getRecords().stream().map(InventoryWarehouseMove::getId).collect(Collectors.toList());
        final Map<Long, List<InventoryWarehouseMoveItem>> itemsMap;
        if (!moveIds.isEmpty()) {
            List<InventoryWarehouseMoveItem> allItems = itemMapper.selectList(
                    new LambdaQueryWrapper<InventoryWarehouseMoveItem>().in(InventoryWarehouseMoveItem::getMoveId, moveIds));
            itemsMap = allItems.stream().collect(Collectors.groupingBy(InventoryWarehouseMoveItem::getMoveId));
        } else {
            itemsMap = Collections.emptyMap();
        }
        Page<Map<String, Object>> res = new Page<>(pageNum, pageSize, raw.getTotal());
        res.setRecords(raw.getRecords().stream().map(o -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", o.getId()); m.put("code", o.getCode());
            m.put("fromWarehouseId", o.getFromWarehouseId()); m.put("toWarehouseId", o.getToWarehouseId());
            m.put("moveDate", o.getMoveDate()); m.put("status", o.getStatus()); m.put("remark", o.getRemark());
            m.put("createTime", o.getCreateTime());
            // 产品明细摘要
            List<InventoryWarehouseMoveItem> its = itemsMap.getOrDefault(o.getId(), Collections.emptyList());
            String summary = its.stream().map(it -> {
                String name = "";
                if (it.getProductId() != null) {
                    Product prod = productMapper.selectById(it.getProductId());
                    if (prod != null) name = prod.getName();
                }
                return name + "*" + (it.getQuantity() != null ? it.getQuantity().stripTrailingZeros().toPlainString() : "0");
            }).collect(Collectors.joining("，"));
            m.put("itemsSummary", summary);
            return m;
        }).toList());
        return res;
    }

    @Override
    public InventoryWarehouseMove getById(Long id) { return moveMapper.selectById(id); }

    @Override
    public List<InventoryWarehouseMoveItem> getItems(Long moveId) {
        return itemMapper.selectList(new LambdaQueryWrapper<InventoryWarehouseMoveItem>().eq(InventoryWarehouseMoveItem::getMoveId, moveId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(InventoryWarehouseMove move, List<InventoryWarehouseMoveItem> items) {
        if (move.getFromWarehouseId() == null || move.getToWarehouseId() == null)
            throw new BusinessException("移出/移入仓库不能为空");
        if (move.getFromWarehouseId().equals(move.getToWarehouseId()))
            throw new BusinessException("移出与移入仓库不能相同");
        move.setCode(gen("YC-"));
        move.setStatus(DocStatus.DRAFT.name());
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) move.setCompanyId(cid);
        moveMapper.insert(move);
        for (InventoryWarehouseMoveItem it : items) {
            it.setId(null);
            it.setMoveId(move.getId());
            if (cid != null && cid > 0) it.setCompanyId(cid);
            itemMapper.insert(it);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(InventoryWarehouseMove move, List<InventoryWarehouseMoveItem> items) {
        InventoryWarehouseMove old = moveMapper.selectById(move.getId());
        if (old == null) throw new BusinessException("移仓单不存在");
        if (!DocStatus.DRAFT.name().equals(old.getStatus())) throw new BusinessException("只有草稿状态可编辑");
        move.setCode(old.getCode());
        moveMapper.updateById(move);
        itemMapper.delete(new LambdaQueryWrapper<InventoryWarehouseMoveItem>().eq(InventoryWarehouseMoveItem::getMoveId, move.getId()));
        Long cid = CompanyContext.get();
        for (InventoryWarehouseMoveItem it : items) {
            it.setId(null);
            it.setMoveId(move.getId());
            if (cid != null && cid > 0) it.setCompanyId(cid);
            itemMapper.insert(it);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        InventoryWarehouseMove old = moveMapper.selectById(id);
        if (old == null) throw new BusinessException("移仓单不存在");
        if (!DocStatus.DRAFT.name().equals(old.getStatus())) throw new BusinessException("只有草稿状态可作废");
        InventoryWarehouseMove u = new InventoryWarehouseMove(); u.setId(id); u.setStatus(DocStatus.CANCELLED.name());
        moveMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        InventoryWarehouseMove move = moveMapper.selectById(id);
        if (move == null) throw new BusinessException("移仓单不存在");
        if (!DocStatus.DRAFT.name().equals(move.getStatus())) throw new BusinessException("只有草稿状态可审核");
        List<InventoryWarehouseMoveItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<InventoryWarehouseMoveItem>().eq(InventoryWarehouseMoveItem::getMoveId, id));
        for (InventoryWarehouseMoveItem it : items) {
            BigDecimal q = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
            // 查询产品名称用于库存流水
            String productName = "";
            if (it.getProductId() != null) {
                Product product = productMapper.selectById(it.getProductId());
                productName = product != null ? product.getName() : "";
            }
            stockService.changeStock(move.getFromWarehouseId(), productName, q.negate(),
                    StockChangeType.MOVE_OUT, move.getCode(), RelatedBillType.WAREHOUSE_MOVE, it.getProductId(), "", move.getId(), it.getQualityType());
            stockService.changeStock(move.getToWarehouseId(), productName, q,
                    StockChangeType.MOVE_IN, move.getCode(), RelatedBillType.WAREHOUSE_MOVE, it.getProductId(), "", move.getId(), it.getQualityType());
        }
        InventoryWarehouseMove u = new InventoryWarehouseMove(); u.setId(id); u.setStatus(DocStatus.AUDITED.name());
        moveMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unAudit(Long id) {
        InventoryWarehouseMove move = moveMapper.selectById(id);
        if (move == null) throw new BusinessException("移仓单不存在");
        if (!DocStatus.AUDITED.name().equals(move.getStatus())) throw new BusinessException("只有已审核状态可反审核");
        List<InventoryWarehouseMoveItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<InventoryWarehouseMoveItem>().eq(InventoryWarehouseMoveItem::getMoveId, id));
        for (InventoryWarehouseMoveItem it : items) {
            BigDecimal q = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
            String productName = "";
            if (it.getProductId() != null) {
                Product product = productMapper.selectById(it.getProductId());
                productName = product != null ? product.getName() : "";
            }
            // 反审核：退回移出仓、从移入仓扣回
            stockService.changeStock(move.getFromWarehouseId(), productName, q,
                    StockChangeType.MOVE_IN, move.getCode(), RelatedBillType.WAREHOUSE_MOVE_UN_AUDIT, it.getProductId(), "", move.getId(), it.getQualityType());
            stockService.changeStock(move.getToWarehouseId(), productName, q.negate(),
                    StockChangeType.MOVE_OUT, move.getCode(), RelatedBillType.WAREHOUSE_MOVE_UN_AUDIT, it.getProductId(), "", move.getId(), it.getQualityType());
        }
        InventoryWarehouseMove u = new InventoryWarehouseMove(); u.setId(id); u.setStatus(DocStatus.DRAFT.name());
        moveMapper.updateById(u);
    }

    private String gen(String prefix) {
        String d = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String pat = prefix + d;
        LambdaQueryWrapper<InventoryWarehouseMove> w = new LambdaQueryWrapper<InventoryWarehouseMove>()
                .likeRight(InventoryWarehouseMove::getCode, pat).orderByDesc(InventoryWarehouseMove::getCode).last("LIMIT 1");
        InventoryWarehouseMove last = moveMapper.selectOne(w);
        int seq = 1;
        if (last != null && last.getCode() != null) {
            try { seq = Integer.parseInt(last.getCode().substring(last.getCode().length() - 3)) + 1; } catch (Exception e) { seq = 1; }
        }
        return prefix + d + String.format("%03d", seq);
    }
}
