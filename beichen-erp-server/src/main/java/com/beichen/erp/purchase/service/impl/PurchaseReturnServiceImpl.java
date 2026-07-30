package com.beichen.erp.purchase.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.auth.entity.User;
import com.beichen.erp.auth.mapper.UserMapper;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.inventory.common.RelatedBillType;
import com.beichen.erp.inventory.common.StockChangeType;
import com.beichen.erp.finance.entity.FinancePayable;
import com.beichen.erp.finance.mapper.FinancePayableMapper;
import com.beichen.erp.inventory.service.InventoryWarehouseStockService;
import com.beichen.erp.material.entity.Material;
import com.beichen.erp.material.mapper.MaterialMapper;
import com.beichen.erp.purchase.entity.*;
import com.beichen.erp.purchase.mapper.PurchaseReturnItemMapper;
import com.beichen.erp.purchase.mapper.PurchaseReturnMapper;
import com.beichen.erp.purchase.service.PurchaseReturnService;
import com.beichen.erp.supplier.entity.Supplier;
import com.beichen.erp.supplier.mapper.SupplierMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PurchaseReturnServiceImpl implements PurchaseReturnService {

    private final PurchaseReturnMapper returnMapper;
    private final PurchaseReturnItemMapper itemMapper;
    private final SupplierMapper supplierMapper;
    private final MaterialMapper materialMapper;
    private final InventoryWarehouseStockService stockService;
    private final FinancePayableMapper payableMapper;
    private final UserMapper userMapper;

    @Override
    public Page<Map<String, Object>> page(Integer status, Long supplierId, String code, int pageNum, int pageSize) {
        LambdaQueryWrapper<PurchaseReturn> w = new LambdaQueryWrapper<PurchaseReturn>()
                .eq(status != null, PurchaseReturn::getStatus, status)
                .eq(supplierId != null, PurchaseReturn::getSupplierId, supplierId)
                .like(code != null && !code.isBlank(), PurchaseReturn::getCode, code)
                .orderByDesc(PurchaseReturn::getId);
        Page<PurchaseReturn> raw = returnMapper.selectPage(new Page<>(pageNum, pageSize), w);
        // 批量查产品
        Map<Long, Material> productMap = new HashMap<>();
        if (!raw.getRecords().isEmpty()) {
            List<Long> returnIds = raw.getRecords().stream().map(PurchaseReturn::getId).collect(Collectors.toList());
            List<PurchaseReturnItem> allItems = itemMapper.selectList(
                    new LambdaQueryWrapper<PurchaseReturnItem>().in(PurchaseReturnItem::getReturnId, returnIds));
            Set<Long> productIds = allItems.stream().map(PurchaseReturnItem::getProductId).filter(Objects::nonNull).collect(Collectors.toSet());
            if (!productIds.isEmpty()) {
                List<Material> products = materialMapper.selectBatchIds(productIds);
                for (Material p : products) productMap.put(p.getId(), p);
            }
        }
        // 批量查明细
        Map<Long, List<PurchaseReturnItem>> itemsMap = new HashMap<>();
        if (!raw.getRecords().isEmpty()) {
            List<Long> returnIds = raw.getRecords().stream().map(PurchaseReturn::getId).collect(Collectors.toList());
            List<PurchaseReturnItem> allItems = itemMapper.selectList(
                    new LambdaQueryWrapper<PurchaseReturnItem>().in(PurchaseReturnItem::getReturnId, returnIds));
            itemsMap = allItems.stream().collect(Collectors.groupingBy(PurchaseReturnItem::getReturnId));
        }
        Map<Long, List<PurchaseReturnItem>> finalItemsMap = itemsMap;
        Map<Long, Material> finalProductMap = productMap;
        Page<Map<String, Object>> res = new Page<>(pageNum, pageSize, raw.getTotal());
        res.setRecords(raw.getRecords().stream().map(o -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", o.getId());
            m.put("code", o.getCode());
            m.put("supplierId", o.getSupplierId());
            m.put("warehouseId", o.getWarehouseId());
            m.put("returnDate", o.getReturnDate());
            m.put("status", o.getStatus());
            m.put("totalAmount", o.getTotalAmount());
            m.put("remark", o.getRemark());
            m.put("createTime", o.getCreateTime());
            if (o.getSupplierId() != null) {
                Supplier s = supplierMapper.selectById(o.getSupplierId());
                m.put("supplierName", s != null ? s.getName() : "");
            }
            // 退货明细摘要
            List<PurchaseReturnItem> returnItems = finalItemsMap.getOrDefault(o.getId(), Collections.emptyList());
            String itemsSummary = returnItems.stream()
                    .map(it -> {
                        String name = it.getProductId() != null && finalProductMap.containsKey(it.getProductId())
                                ? finalProductMap.get(it.getProductId()).getName() : "";
                        String qty = it.getQuantity() != null ? it.getQuantity().stripTrailingZeros().toPlainString() : "0";
                        return name + "*" + qty;
                    })
                    .collect(Collectors.joining("，"));
            m.put("itemsSummary", itemsSummary);
            return m;
        }).toList());
        return res;
    }

    @Override
    public PurchaseReturn getById(Long id) {
        PurchaseReturn order = returnMapper.selectById(id);
        if (order == null) throw new BusinessException("退货单不存在");
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseReturn create(PurchaseReturn order, List<Map<String, Object>> itemMaps) {
        order.setId(null);
        order.setStatus(PurchaseReturnStatus.DRAFT.getCode());
        order.setCode(generateCode());
        if (order.getTotalAmount() == null) order.setTotalAmount(BigDecimal.ZERO);
        returnMapper.insert(order);
        saveItems(order.getId(), itemMaps);
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseReturn update(Long id, PurchaseReturn order, List<Map<String, Object>> itemMaps) {
        PurchaseReturn old = returnMapper.selectById(id);
        if (old == null) throw new BusinessException("退货单不存在");
        if (!PurchaseReturnStatus.DRAFT.getCode().equals(old.getStatus())) throw new BusinessException("只有草稿状态可编辑");
        order.setId(id);
        order.setCode(null); // 单号不可修改
        order.setStatus(null);
        if (order.getTotalAmount() == null) order.setTotalAmount(BigDecimal.ZERO);
        returnMapper.updateById(order);
        itemMapper.delete(new LambdaQueryWrapper<PurchaseReturnItem>().eq(PurchaseReturnItem::getReturnId, id));
        saveItems(id, itemMaps);
        return returnMapper.selectById(id);
    }

    @Override
    public List<PurchaseReturnItem> getItems(Long returnId) {
        return itemMapper.selectList(
                new LambdaQueryWrapper<PurchaseReturnItem>().eq(PurchaseReturnItem::getReturnId, returnId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        PurchaseReturn order = returnMapper.selectById(id);
        if (order == null) throw new BusinessException("退货单不存在");
        if (!PurchaseReturnStatus.DRAFT.getCode().equals(order.getStatus())) throw new BusinessException("只有草稿状态可审核");
        List<PurchaseReturnItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<PurchaseReturnItem>().eq(PurchaseReturnItem::getReturnId, id));
        if (items.isEmpty()) throw new BusinessException("退货单明细不能为空");
        // 1) 库存联动：退货出库减库存
        for (PurchaseReturnItem it : items) {
            if (it.getQuantity() == null || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;
            Material product = it.getProductId() != null ? materialMapper.selectById(it.getProductId()) : null;
            stockService.changeStock(order.getWarehouseId(),
                    product != null ? product.getName() : "",
                    it.getQuantity().negate(),
                    StockChangeType.RETURN_OUT, order.getCode(), RelatedBillType.PURCHASE_RETURN, it.getProductId(),
                    product != null ? product.getSpec() : "", order.getId(), it.getQualityType());
        }
        // 2) 冲减应付：新增负数应付台账
        FinancePayable fp = new FinancePayable();
        fp.setBillNo(order.getCode());
        fp.setSupplierId(order.getSupplierId());
        Supplier s = order.getSupplierId() != null ? supplierMapper.selectById(order.getSupplierId()) : null;
        fp.setSupplierName(s != null ? s.getName() : "");
        fp.setSourceBillType("成品退货单");
        fp.setSourceBillNo(order.getCode());
        fp.setAmount(order.getTotalAmount().negate());
        fp.setPaidAmount(BigDecimal.ZERO);
        fp.setUnpaidAmount(order.getTotalAmount().negate());
        fp.setDueDate(order.getReturnDate());
        fp.setStatus("未结清");
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) fp.setCompanyId(cid);
        payableMapper.insert(fp);
        // 3) 更新状态
        PurchaseReturn u = new PurchaseReturn();
        u.setId(id);
        u.setStatus(PurchaseReturnStatus.COMPLETED.getCode());
        u.setAuditorId(getCurrentUserId());
        u.setAuditorName(getCurrentUserName());
        u.setAuditTime(LocalDateTime.now());
        returnMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unAudit(Long id) {
        PurchaseReturn order = returnMapper.selectById(id);
        if (order == null) throw new BusinessException("退货单不存在");
        if (!PurchaseReturnStatus.COMPLETED.getCode().equals(order.getStatus())) throw new BusinessException("只有已完成的退货单可反审核");
        // 1) 检查应付台账
        LambdaQueryWrapper<FinancePayable> payableW = new LambdaQueryWrapper<FinancePayable>()
                .eq(FinancePayable::getSourceBillType, "成品退货单")
                .eq(FinancePayable::getSourceBillNo, order.getCode());
        List<FinancePayable> payables = payableMapper.selectList(payableW);
        for (FinancePayable fp : payables) {
            if (!"未结清".equals(fp.getStatus())) {
                throw new BusinessException("该退货单对应的应付账款已核销，无法反审核");
            }
        }
        // 2) 恢复库存
        List<PurchaseReturnItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<PurchaseReturnItem>().eq(PurchaseReturnItem::getReturnId, id));
        for (PurchaseReturnItem it : items) {
            if (it.getQuantity() == null || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;
            Material product = it.getProductId() != null ? materialMapper.selectById(it.getProductId()) : null;
            stockService.changeStock(order.getWarehouseId(),
                    product != null ? product.getName() : "",
                    it.getQuantity(),
                    StockChangeType.RETURN_UN_AUDIT, order.getCode(), RelatedBillType.PURCHASE_RETURN, it.getProductId(),
                    product != null ? product.getSpec() : "", order.getId(), it.getQualityType());
        }
        // 3) 删除应付台账
        for (FinancePayable fp : payables) {
            payableMapper.deleteById(fp.getId());
        }
        // 4) 回退到草稿
        PurchaseReturn u = new PurchaseReturn();
        u.setId(id);
        u.setStatus(PurchaseReturnStatus.DRAFT.getCode());
        u.setAuditorId(null);
        u.setAuditorName(null);
        u.setAuditTime(null);
        returnMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        PurchaseReturn old = returnMapper.selectById(id);
        if (old == null) throw new BusinessException("退货单不存在");
        if (!PurchaseReturnStatus.DRAFT.getCode().equals(old.getStatus())) throw new BusinessException("只有草稿状态可作废");
        PurchaseReturn u = new PurchaseReturn();
        u.setId(id);
        u.setStatus(PurchaseReturnStatus.CANCELLED.getCode());
        returnMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        PurchaseReturn old = returnMapper.selectById(id);
        if (old == null) throw new BusinessException("退货单不存在");
        if (!PurchaseReturnStatus.DRAFT.getCode().equals(old.getStatus())) throw new BusinessException("只有草稿状态可删除");
        itemMapper.delete(new LambdaQueryWrapper<PurchaseReturnItem>().eq(PurchaseReturnItem::getReturnId, id));
        returnMapper.deleteById(id);
    }

    private void saveItems(Long returnId, List<Map<String, Object>> itemMaps) {
        if (itemMaps != null) {
            for (Map<String, Object> map : itemMaps) {
                PurchaseReturnItem it = new PurchaseReturnItem();
                it.setReturnId(returnId);
                if (map.get("productId") != null) it.setProductId(Long.valueOf(map.get("productId").toString()));
                if (map.get("quantity") != null) it.setQuantity(new BigDecimal(map.get("quantity").toString()));
                if (map.get("unitPrice") != null) it.setUnitPrice(new BigDecimal(map.get("unitPrice").toString()));
                if (map.get("amount") != null) it.setAmount(new BigDecimal(map.get("amount").toString()));
                if (map.get("remark") != null) it.setRemark(map.get("remark").toString());
                if (map.get("qualityType") != null) it.setQualityType(map.get("qualityType").toString());
                itemMapper.insert(it);
            }
        }
    }

    private String generateCode() {
        String d = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String pat = "TH-" + d;
        LambdaQueryWrapper<PurchaseReturn> w = new LambdaQueryWrapper<PurchaseReturn>()
                .likeRight(PurchaseReturn::getCode, pat).orderByDesc(PurchaseReturn::getCode).last("LIMIT 1");
        PurchaseReturn last = returnMapper.selectOne(w);
        int seq = 1;
        if (last != null && last.getCode() != null) {
            try {
                seq = Integer.parseInt(last.getCode().substring(last.getCode().length() - 3)) + 1;
            } catch (Exception e) { seq = 1; }
        }
        return "TH-" + d + String.format("%03d", seq);
    }

    private Long getCurrentUserId() {
        try { return StpUtil.getLoginIdAsLong(); } catch (Exception e) { return null; }
    }

    private String getCurrentUserName() {
        try {
            Long userId = StpUtil.getLoginIdAsLong();
            User user = userMapper.selectById(userId);
            return user != null ? user.getUsername() : null;
        } catch (Exception e) { return null; }
    }
}
