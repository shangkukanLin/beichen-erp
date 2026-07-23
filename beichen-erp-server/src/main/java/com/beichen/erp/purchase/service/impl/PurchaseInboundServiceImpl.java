package com.beichen.erp.purchase.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.finance.entity.FinancePayable;
import com.beichen.erp.finance.mapper.FinancePayableMapper;
import com.beichen.erp.inventory.service.InventoryWarehouseStockService;
import com.beichen.erp.purchase.entity.PurchaseInbound;
import com.beichen.erp.purchase.entity.PurchaseInboundItem;
import com.beichen.erp.purchase.entity.PurchaseOrder;
import com.beichen.erp.purchase.mapper.PurchaseInboundMapper;
import com.beichen.erp.purchase.mapper.PurchaseInboundItemMapper;
import com.beichen.erp.purchase.mapper.PurchaseOrderMapper;
import com.beichen.erp.purchase.service.PurchaseInboundService;
import com.beichen.erp.supplier.entity.Supplier;
import com.beichen.erp.supplier.mapper.SupplierMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PurchaseInboundServiceImpl implements PurchaseInboundService {

    private final PurchaseInboundMapper inboundMapper;
    private final PurchaseInboundItemMapper itemMapper;
    private final PurchaseOrderMapper orderMapper;
    private final SupplierMapper supplierMapper;
    private final InventoryWarehouseStockService stockService;
    private final FinancePayableMapper payableMapper;
    private final com.beichen.erp.finance.service.PayableHelper payableHelper;

    @Override
    public Page<Map<String, Object>> page(String status, Long supplierId, String code, int pageNum, int pageSize) {
        LambdaQueryWrapper<PurchaseInbound> w = new LambdaQueryWrapper<PurchaseInbound>()
                .eq(status != null && !status.isBlank(), PurchaseInbound::getStatus, status)
                .eq(supplierId != null, PurchaseInbound::getSupplierId, supplierId)
                .like(code != null && !code.isBlank(), PurchaseInbound::getCode, code)
                .orderByDesc(PurchaseInbound::getId);
        Page<PurchaseInbound> raw = inboundMapper.selectPage(new Page<>(pageNum, pageSize), w);
        Page<Map<String, Object>> res = new Page<>(pageNum, pageSize, raw.getTotal());
        res.setRecords(raw.getRecords().stream().map(o -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", o.getId());
            m.put("code", o.getCode());
            m.put("orderId", o.getOrderId());
            m.put("supplierId", o.getSupplierId());
            m.put("warehouseId", o.getWarehouseId());
            m.put("inboundDate", o.getInboundDate());
            m.put("status", o.getStatus());
            m.put("totalAmount", o.getTotalAmount());
            m.put("remark", o.getRemark());
            m.put("createTime", o.getCreateTime());
            if (o.getSupplierId() != null) {
                Supplier s = supplierMapper.selectById(o.getSupplierId());
                m.put("supplierName", s != null ? s.getName() : "");
            }
            return m;
        }).toList());
        return res;
    }

    @Override
    public PurchaseInbound getById(Long id) {
        return inboundMapper.selectById(id);
    }

    @Override
    public List<PurchaseInboundItem> getItems(Long inboundId) {
        return itemMapper.selectList(new LambdaQueryWrapper<PurchaseInboundItem>().eq(PurchaseInboundItem::getInboundId, inboundId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(PurchaseInbound inbound, List<PurchaseInboundItem> items) {
        if (inbound.getSupplierId() == null) throw new BusinessException("供应商不能为空");
        if (inbound.getWarehouseId() == null) throw new BusinessException("入库仓库不能为空");
        if (inbound.getSupplierId() != null) {
            Supplier s = supplierMapper.selectById(inbound.getSupplierId());
            inbound.setSupplierName(s != null ? s.getName() : "");
        }
        inbound.setCode(generateCode());
        inbound.setStatus("草稿");
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) inbound.setCompanyId(cid);
        BigDecimal total = BigDecimal.ZERO;
        inboundMapper.insert(inbound);
        for (PurchaseInboundItem it : items) {
            it.setId(null);
            it.setInboundId(inbound.getId());
            BigDecimal q = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
            BigDecimal p = it.getUnitPrice() != null ? it.getUnitPrice() : BigDecimal.ZERO;
            it.setAmount(q.multiply(p));
            total = total.add(it.getAmount());
            if (cid != null && cid > 0) it.setCompanyId(cid);
            itemMapper.insert(it);
        }
        PurchaseInbound u = new PurchaseInbound();
        u.setId(inbound.getId());
        u.setTotalAmount(total);
        inboundMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(PurchaseInbound inbound, List<PurchaseInboundItem> items) {
        PurchaseInbound old = inboundMapper.selectById(inbound.getId());
        if (old == null) throw new BusinessException("采购入库单不存在");
        if (!"草稿".equals(old.getStatus())) throw new BusinessException("只有草稿状态可编辑");
        if (inbound.getSupplierId() != null) {
            Supplier s = supplierMapper.selectById(inbound.getSupplierId());
            inbound.setSupplierName(s != null ? s.getName() : "");
        }
        inbound.setCode(old.getCode());
        inboundMapper.updateById(inbound);
        itemMapper.delete(new LambdaQueryWrapper<PurchaseInboundItem>().eq(PurchaseInboundItem::getInboundId, inbound.getId()));
        BigDecimal total = BigDecimal.ZERO;
        Long cid = CompanyContext.get();
        for (PurchaseInboundItem it : items) {
            it.setId(null);
            it.setInboundId(inbound.getId());
            BigDecimal q = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
            BigDecimal p = it.getUnitPrice() != null ? it.getUnitPrice() : BigDecimal.ZERO;
            it.setAmount(q.multiply(p));
            total = total.add(it.getAmount());
            if (cid != null && cid > 0) it.setCompanyId(cid);
            itemMapper.insert(it);
        }
        PurchaseInbound u = new PurchaseInbound();
        u.setId(inbound.getId());
        u.setTotalAmount(total);
        inboundMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        PurchaseInbound old = inboundMapper.selectById(id);
        if (old == null) throw new BusinessException("采购入库单不存在");
        if (!"草稿".equals(old.getStatus())) throw new BusinessException("已审核的入库单不可作废");
        PurchaseInbound u = new PurchaseInbound();
        u.setId(id);
        u.setStatus("已作废");
        inboundMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        PurchaseInbound inbound = inboundMapper.selectById(id);
        if (inbound == null) throw new BusinessException("采购入库单不存在");
        if (!"草稿".equals(inbound.getStatus())) throw new BusinessException("只有草稿状态可审核");
        List<PurchaseInboundItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<PurchaseInboundItem>().eq(PurchaseInboundItem::getInboundId, id));
        // 1) 库存联动：入库加库存 + 写流水
        for (PurchaseInboundItem it : items) {
            stockService.changeStock(inbound.getWarehouseId(), it.getMaterialName(), it.getQuantity(),
                    "采购入库", inbound.getCode(), "采购入库", it.getProductId(), it.getSpec());
        }
        // 2) 生成应付台账
        FinancePayable fp = new FinancePayable();
        fp.setBillNo(inbound.getCode());
        fp.setSupplierId(inbound.getSupplierId());
        fp.setSupplierName(inbound.getSupplierName());
        fp.setSourceBillType("采购入库");
        fp.setSourceBillNo(inbound.getCode());
        fp.setAmount(inbound.getTotalAmount());
        fp.setPaidAmount(BigDecimal.ZERO);
        fp.setUnpaidAmount(inbound.getTotalAmount());
        fp.setDueDate(payableHelper.calcDueDate(supplierMapper.selectById(inbound.getSupplierId()), inbound.getInboundDate()));
        fp.setStatus("未结清");
        payableMapper.insert(fp);
        // 3) 更新入库单状态
        PurchaseInbound u = new PurchaseInbound();
        u.setId(id);
        u.setStatus("已审核");
        inboundMapper.updateById(u);
        // 4) 关联采购订单置为已入库
        if (inbound.getOrderId() != null) {
            PurchaseOrder o = new PurchaseOrder();
            o.setId(inbound.getOrderId());
            o.setStatus("已入库");
            orderMapper.updateById(o);
        }
    }

    private String generateCode() {
        String d = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String pat = "RK-" + d;
        LambdaQueryWrapper<PurchaseInbound> w = new LambdaQueryWrapper<PurchaseInbound>()
                .likeRight(PurchaseInbound::getCode, pat).orderByDesc(PurchaseInbound::getCode).last("LIMIT 1");
        PurchaseInbound last = inboundMapper.selectOne(w);
        int seq = 1;
        if (last != null && last.getCode() != null) {
            try {
                seq = Integer.parseInt(last.getCode().substring(last.getCode().length() - 3)) + 1;
            } catch (Exception e) { seq = 1; }
        }
        return "RK-" + d + String.format("%03d", seq);
    }
}
