package com.beichen.erp.purchase.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.auth.entity.User;
import com.beichen.erp.auth.mapper.UserMapper;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.common.BillPrefix;
import com.beichen.erp.common.DocStatus;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.inventory.common.RelatedBillType;
import com.beichen.erp.finance.common.SettlementStatus;
import com.beichen.erp.finance.common.SourceBillType;
import com.beichen.erp.inventory.common.StockChangeType;
import com.beichen.erp.finance.entity.FinancePayable;
import com.beichen.erp.finance.mapper.FinancePayableMapper;
import com.beichen.erp.warehouse.service.WarehouseStockService;
import com.beichen.erp.material.entity.Product;
import com.beichen.erp.material.mapper.ProductMapper;
import com.beichen.erp.purchase.entity.*;
import com.beichen.erp.purchase.mapper.PurchaseOrderItemMapper;
import com.beichen.erp.purchase.mapper.PurchaseOrderMapper;
import com.beichen.erp.purchase.mapper.PurchaseReturnItemMapper;
import com.beichen.erp.purchase.mapper.PurchaseReturnMapper;
import com.beichen.erp.purchase.service.PurchaseReturnService;
import com.beichen.erp.supplier.entity.Supplier;
import com.beichen.erp.supplier.mapper.SupplierMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final ProductMapper productMapper;
    private final WarehouseStockService stockService;
    private final FinancePayableMapper payableMapper;
    private final UserMapper userMapper;
    private final com.beichen.erp.finance.service.PayableHelper payableHelper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final PurchaseOrderItemMapper purchaseOrderItemMapper;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public Page<Map<String, Object>> page(Integer status, Long supplierId, String code, int pageNum, int pageSize) {
        LambdaQueryWrapper<PurchaseReturn> w = new LambdaQueryWrapper<PurchaseReturn>()
                .eq(status != null, PurchaseReturn::getStatus, status)
                .eq(supplierId != null, PurchaseReturn::getSupplierId, supplierId)
                .like(code != null && !code.isBlank(), PurchaseReturn::getCode, code)
                .orderByDesc(PurchaseReturn::getId);
        Page<PurchaseReturn> raw = returnMapper.selectPage(new Page<>(pageNum, pageSize), w);
        // 批量查产品
        Map<Long, Product> productMap = new HashMap<>();
        if (!raw.getRecords().isEmpty()) {
            List<Long> returnIds = raw.getRecords().stream().map(PurchaseReturn::getId).collect(Collectors.toList());
            List<PurchaseReturnItem> allItems = itemMapper.selectList(
                    new LambdaQueryWrapper<PurchaseReturnItem>().in(PurchaseReturnItem::getReturnId, returnIds));
            Set<Long> productIds = allItems.stream().map(PurchaseReturnItem::getProductId).filter(Objects::nonNull).collect(Collectors.toSet());
            if (!productIds.isEmpty()) {
                List<Product> products = productMapper.selectBatchIds(productIds);
                for (Product p : products) productMap.put(p.getId(), p);
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
        Map<Long, Product> finalProductMap = productMap;
        Page<Map<String, Object>> res = new Page<>(pageNum, pageSize, raw.getTotal());
        res.setRecords(raw.getRecords().stream().map(o -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", o.getId());
            m.put("code", o.getCode());
            m.put("supplierId", o.getSupplierId());
            m.put("warehouseId", o.getWarehouseId());
            m.put("purchaseOrderId", o.getPurchaseOrderId());
            m.put("purchaseOrderCode", o.getPurchaseOrderCode());
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
        order.setStatus(DocStatus.DRAFT.getCode());
        order.setCode(generateCode());
        fillPurchaseOrderInfo(order);
        validateReturnQuantity(order, itemMaps);
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
        if (!DocStatus.DRAFT.getCode().equals(old.getStatus())) throw new BusinessException("只有草稿状态可编辑");
        order.setId(id);
        order.setCode(null); // 单号不可修改
        order.setStatus(null);
        fillPurchaseOrderInfo(order);
        validateReturnQuantity(order, itemMaps);
        if (order.getTotalAmount() == null) order.setTotalAmount(BigDecimal.ZERO);
        returnMapper.updateById(order);
        itemMapper.delete(new LambdaQueryWrapper<PurchaseReturnItem>().eq(PurchaseReturnItem::getReturnId, id));
        saveItems(id, itemMaps);
        return returnMapper.selectById(id);
    }

    @Override
    public List<PurchaseReturnItem> getItems(Long returnId) {
        List<PurchaseReturnItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<PurchaseReturnItem>().eq(PurchaseReturnItem::getReturnId, returnId));
        // 批量回填产品名称，随明细一起返回，前端免查库
        Set<Long> pids = items.stream().map(PurchaseReturnItem::getProductId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (!pids.isEmpty()) {
            Map<Long, Product> pm = productMapper.selectBatchIds(pids).stream()
                    .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));
            for (PurchaseReturnItem it : items) {
                if (it.getProductId() != null && pm.containsKey(it.getProductId())) {
                    Product p = pm.get(it.getProductId());
                    it.setProductName(p.getName());
                    it.setSku(p.getSku() != null ? p.getSku() : "");
                }
            }
        }
        return items;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        PurchaseReturn order = returnMapper.selectById(id);
        if (order == null) throw new BusinessException("退货单不存在");
        if (!DocStatus.DRAFT.getCode().equals(order.getStatus())) throw new BusinessException("只有草稿状态可审核");
        List<PurchaseReturnItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<PurchaseReturnItem>().eq(PurchaseReturnItem::getReturnId, id));
        if (items.isEmpty()) throw new BusinessException("退货单明细不能为空");
        // 1) 库存联动：退货出库减库存
        for (PurchaseReturnItem it : items) {
            if (it.getQuantity() == null || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;
            Product product = it.getProductId() != null ? productMapper.selectById(it.getProductId()) : null;
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
        fp.setSourceBillType(SourceBillType.PURCHASE_RETURN.getCode());
        fp.setSourceBillNo(order.getCode());
        fp.setSourceId(order.getId());
        fp.setAmount(order.getTotalAmount().negate());
        fp.setPaidAmount(BigDecimal.ZERO);
        fp.setUnpaidAmount(order.getTotalAmount().negate());
        fp.setDueDate(order.getReturnDate());
        fp.setStatus(SettlementStatus.UNSETTLED.getCode());
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) fp.setCompanyId(cid);
        payableMapper.insert(fp);
        // 3) 更新状态
        PurchaseReturn u = new PurchaseReturn();
        u.setId(id);
        u.setStatus(DocStatus.AUDITED.getCode());
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
        if (!DocStatus.AUDITED.getCode().equals(order.getStatus())) throw new BusinessException("只有已完成的退货单可反审核");
        // 1) 检查应付台账
        LambdaQueryWrapper<FinancePayable> payableW = new LambdaQueryWrapper<FinancePayable>()
                .eq(FinancePayable::getSourceBillType, SourceBillType.PURCHASE_RETURN.getCode())
                .eq(FinancePayable::getSourceBillNo, order.getCode());
        List<FinancePayable> payables = payableMapper.selectList(payableW);
        for (FinancePayable fp : payables) {
            if (!SettlementStatus.UNSETTLED.getCode().equals(fp.getStatus())) {
                throw new BusinessException("该退货单对应的应付账款已核销，无法反审核");
            }
        }
        // 2) 恢复库存
        List<PurchaseReturnItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<PurchaseReturnItem>().eq(PurchaseReturnItem::getReturnId, id));
        for (PurchaseReturnItem it : items) {
            if (it.getQuantity() == null || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;
            Product product = it.getProductId() != null ? productMapper.selectById(it.getProductId()) : null;
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
        u.setStatus(DocStatus.DRAFT.getCode());
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
        if (!DocStatus.DRAFT.getCode().equals(old.getStatus())) throw new BusinessException("只有草稿状态可作废");
        PurchaseReturn u = new PurchaseReturn();
        u.setId(id);
        u.setStatus(DocStatus.CANCELLED.getCode());
        returnMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        PurchaseReturn old = returnMapper.selectById(id);
        if (old == null) throw new BusinessException("退货单不存在");
        if (!DocStatus.DRAFT.getCode().equals(old.getStatus())) throw new BusinessException("只有草稿状态可删除");
        itemMapper.delete(new LambdaQueryWrapper<PurchaseReturnItem>().eq(PurchaseReturnItem::getReturnId, id));
        returnMapper.deleteById(id);
    }

    @Override
    public List<Map<String, Object>> byOrder(Long purchaseOrderId) {
        if (purchaseOrderId == null) return List.of();
        List<PurchaseReturn> list = returnMapper.selectList(new LambdaQueryWrapper<PurchaseReturn>()
                .eq(PurchaseReturn::getPurchaseOrderId, purchaseOrderId)
                .orderByDesc(PurchaseReturn::getId));
        List<Map<String, Object>> res = new ArrayList<>();
        for (PurchaseReturn o : list) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", o.getId());
            m.put("code", o.getCode());
            m.put("returnDate", o.getReturnDate());
            m.put("status", o.getStatus());
            m.put("totalAmount", o.getTotalAmount());
            m.put("warehouseId", o.getWarehouseId());
            res.add(m);
        }
        return res;
    }

    @Override
    public List<Map<String, Object>> purchaseOrderItems(Long purchaseOrderId) {
        if (purchaseOrderId == null) return List.of();
        List<PurchaseOrderItem> oiList = purchaseOrderItemMapper.selectList(new LambdaQueryWrapper<PurchaseOrderItem>()
                .eq(PurchaseOrderItem::getOrderId, purchaseOrderId));
        Map<Long, Product> pMap = new HashMap<>();
        Set<Long> pids = oiList.stream().map(PurchaseOrderItem::getProductId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (!pids.isEmpty()) productMapper.selectBatchIds(pids).forEach(p -> pMap.put(p.getId(), p));
        List<Map<String, Object>> res = new ArrayList<>();
        for (PurchaseOrderItem oi : oiList) {
            Product p = pMap.get(oi.getProductId());
            BigDecimal sold = oi.getQuantity() == null ? BigDecimal.ZERO : oi.getQuantity();
            BigDecimal returned = alreadyReturned(oi.getId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("purchaseOrderItemId", oi.getId());
            m.put("productId", oi.getProductId());
            m.put("productName", p != null ? p.getName() : "");
            m.put("spec", p != null ? p.getSpec() : "");
            m.put("unit", p != null ? p.getUnit() : "");
            m.put("qualityType", oi.getQualityType());
            m.put("quantity", oi.getQuantity());
            m.put("unitPrice", oi.getUnitPrice());
            m.put("amount", oi.getAmount());
            m.put("returnedQuantity", returned);
            m.put("canReturn", sold.subtract(returned));
            res.add(m);
        }
        return res;
    }

    /** 已退累计数量：关联该采购单明细的已审核退货单数量之和 */
    private BigDecimal alreadyReturned(Long purchaseOrderItemId) {
        return jdbcTemplate.query(
                "SELECT COALESCE(SUM(ri.quantity), 0) FROM purchase_return_item ri " +
                "JOIN purchase_return sr ON sr.id = ri.return_id " +
                "WHERE ri.purchase_order_item_id = ? AND sr.status = 'AUDITED'",
                rs -> rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO, purchaseOrderItemId);
    }

    /** 填充关联采购单号（按 purchaseOrderId 实时查名） */
    private void fillPurchaseOrderInfo(PurchaseReturn order) {
        if (order.getPurchaseOrderId() != null) {
            PurchaseOrder po = purchaseOrderMapper.selectById(order.getPurchaseOrderId());
            order.setPurchaseOrderCode(po != null ? po.getCode() : null);
        } else {
            order.setPurchaseOrderCode(null);
        }
    }

    /** 关联采购单时校验：本次退货 ≤ 已购 - 已退（仅校验带 purchaseOrderItemId 的行） */
    private void validateReturnQuantity(PurchaseReturn order, List<Map<String, Object>> itemMaps) {
        if (order.getPurchaseOrderId() == null || itemMaps == null || itemMaps.isEmpty()) return;
        Map<Long, BigDecimal> qtyMap = new HashMap<>();
        Map<Long, String> nameMap = new HashMap<>();
        for (Map<String, Object> map : itemMaps) {
            Object poiObj = map.get("purchaseOrderItemId");
            if (poiObj == null || poiObj.toString().isBlank()) continue;
            Long poiId = Long.valueOf(poiObj.toString());
            BigDecimal qty = map.get("quantity") != null ? new BigDecimal(map.get("quantity").toString()) : BigDecimal.ZERO;
            qtyMap.merge(poiId, qty, BigDecimal::add);
            if (map.get("productId") != null) {
                Product p = productMapper.selectById(Long.valueOf(map.get("productId").toString()));
                if (p != null) nameMap.put(poiId, p.getName());
            }
        }
        if (qtyMap.isEmpty()) return;
        List<PurchaseOrderItem> oiList = purchaseOrderItemMapper.selectBatchIds(qtyMap.keySet());
        for (PurchaseOrderItem oi : oiList) {
            BigDecimal sold = oi.getQuantity() == null ? BigDecimal.ZERO : oi.getQuantity();
            BigDecimal returned = alreadyReturned(oi.getId());
            BigDecimal canReturn = sold.subtract(returned);
            BigDecimal thisQty = qtyMap.getOrDefault(oi.getId(), BigDecimal.ZERO);
            if (thisQty.compareTo(canReturn) > 0) {
                String name = nameMap.getOrDefault(oi.getId(), String.valueOf(oi.getProductId()));
                throw new BusinessException("产品[" + name + "]退货数量超过可退数量（已购" + fmt(sold)
                        + "，已退" + fmt(returned) + "，可退" + fmt(canReturn) + "）");
            }
        }
    }

    private String fmt(BigDecimal v) {
        return v == null ? "0" : v.stripTrailingZeros().toPlainString();
    }

    private void saveItems(Long returnId, List<Map<String, Object>> itemMaps) {
        if (itemMaps != null) {
            for (Map<String, Object> map : itemMaps) {
                PurchaseReturnItem it = new PurchaseReturnItem();
                it.setReturnId(returnId);
                if (map.get("purchaseOrderItemId") != null && !map.get("purchaseOrderItemId").toString().isBlank())
                    it.setPurchaseOrderItemId(Long.valueOf(map.get("purchaseOrderItemId").toString()));
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
        String pat = BillPrefix.PURCHASE_RETURN + d;
        LambdaQueryWrapper<PurchaseReturn> w = new LambdaQueryWrapper<PurchaseReturn>()
                .likeRight(PurchaseReturn::getCode, pat).orderByDesc(PurchaseReturn::getCode).last("LIMIT 1");
        PurchaseReturn last = returnMapper.selectOne(w);
        int seq = 1;
        if (last != null && last.getCode() != null) {
            try {
                seq = Integer.parseInt(last.getCode().substring(last.getCode().length() - 3)) + 1;
            } catch (Exception e) { seq = 1; }
        }
        return BillPrefix.PURCHASE_RETURN + d + String.format("%03d", seq);
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
