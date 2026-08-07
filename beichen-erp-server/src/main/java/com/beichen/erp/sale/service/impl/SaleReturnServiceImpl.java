package com.beichen.erp.sale.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.auth.entity.User;
import com.beichen.erp.auth.mapper.UserMapper;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.inventory.common.RelatedBillType;
import com.beichen.erp.inventory.common.StockChangeType;
import com.beichen.erp.inventory.service.InventoryWarehouseStockService;
import com.beichen.erp.material.entity.Product;
import com.beichen.erp.material.mapper.ProductMapper;
import com.beichen.erp.sale.common.SaleReturnStatus;
import com.beichen.erp.sale.entity.SaleReturn;
import com.beichen.erp.sale.entity.SaleReturnItem;
import com.beichen.erp.sale.mapper.SaleReturnItemMapper;
import com.beichen.erp.sale.mapper.SaleReturnMapper;
import com.beichen.erp.sale.service.SaleReturnService;
import com.beichen.erp.customer.entity.Customer;
import com.beichen.erp.customer.mapper.CustomerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 销售退货单业务实现
 * <p>客户退回不良品，审核时按 (warehouseId, productId, qualityType=DEFECT) 入库增加库存，并写库存流水。</p>
 */
@Service
@RequiredArgsConstructor
public class SaleReturnServiceImpl implements SaleReturnService {

    private final SaleReturnMapper returnMapper;
    private final SaleReturnItemMapper itemMapper;
    private final ProductMapper productMapper;
    private final CustomerMapper customerMapper;
    private final InventoryWarehouseStockService stockService;
    private final UserMapper userMapper;

    @Override
    public IPage<Map<String, Object>> page(Integer status, Long customerId, String code, int pageNum, int pageSize) {
        LambdaQueryWrapper<SaleReturn> w = new LambdaQueryWrapper<SaleReturn>()
                .eq(status != null, SaleReturn::getStatus, status)
                .eq(customerId != null, SaleReturn::getCustomerId, customerId)
                .like(code != null && !code.isBlank(), SaleReturn::getCode, code)
                .orderByDesc(SaleReturn::getId);
        Page<SaleReturn> raw = returnMapper.selectPage(new Page<>(pageNum, pageSize), w);
        // 批量查明细与产品
        Map<Long, Product> productMap = new HashMap<>();
        Map<Long, List<SaleReturnItem>> itemsMap = new HashMap<>();
        if (!raw.getRecords().isEmpty()) {
            List<Long> returnIds = raw.getRecords().stream().map(SaleReturn::getId).collect(Collectors.toList());
            List<SaleReturnItem> allItems = itemMapper.selectList(
                    new LambdaQueryWrapper<SaleReturnItem>().in(SaleReturnItem::getReturnId, returnIds));
            Set<Long> productIds = allItems.stream().map(SaleReturnItem::getProductId).filter(Objects::nonNull).collect(Collectors.toSet());
            if (!productIds.isEmpty()) {
                productMapper.selectBatchIds(productIds).forEach(p -> productMap.put(p.getId(), p));
            }
            itemsMap = allItems.stream().collect(Collectors.groupingBy(SaleReturnItem::getReturnId));
        }
        Map<Long, Product> finalProductMap = productMap;
        Map<Long, List<SaleReturnItem>> finalItemsMap = itemsMap;
        Page<Map<String, Object>> res = new Page<>(pageNum, pageSize, raw.getTotal());
        res.setRecords(raw.getRecords().stream().map(o -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", o.getId());
            m.put("code", o.getCode());
            m.put("customerId", o.getCustomerId());
            m.put("customerName", o.getCustomerName());
            m.put("warehouseId", o.getWarehouseId());
            m.put("returnDate", o.getReturnDate());
            m.put("status", o.getStatus());
            m.put("totalAmount", o.getTotalAmount());
            m.put("remark", o.getRemark());
            m.put("createTime", o.getCreateTime());
            List<SaleReturnItem> items = finalItemsMap.getOrDefault(o.getId(), Collections.emptyList());
            String itemsSummary = items.stream()
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
    public SaleReturn getById(Long id) {
        SaleReturn order = returnMapper.selectById(id);
        if (order == null) throw new BusinessException("销售退货单不存在");
        return order;
    }

    @Override
    public List<SaleReturnItem> getItems(Long returnId) {
        return itemMapper.selectList(
                new LambdaQueryWrapper<SaleReturnItem>().eq(SaleReturnItem::getReturnId, returnId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SaleReturn create(SaleReturn order, List<Map<String, Object>> itemMaps) {
        order.setId(null);
        order.setStatus(SaleReturnStatus.DRAFT.getCode());
        order.setCode(generateCode());
        if (order.getTotalAmount() == null) order.setTotalAmount(BigDecimal.ZERO);
        if (order.getCustomerId() != null) {
            Customer c = customerMapper.selectById(order.getCustomerId());
            order.setCustomerName(c != null ? c.getName() : null);
        }
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) order.setCompanyId(cid);
        returnMapper.insert(order);
        saveItems(order.getId(), itemMaps);
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SaleReturn update(Long id, SaleReturn order, List<Map<String, Object>> itemMaps) {
        SaleReturn old = returnMapper.selectById(id);
        if (old == null) throw new BusinessException("销售退货单不存在");
        if (!SaleReturnStatus.DRAFT.getCode().equals(old.getStatus())) throw new BusinessException("只有草稿状态可编辑");
        order.setId(id);
        order.setCode(null);
        order.setStatus(null);
        if (order.getTotalAmount() == null) order.setTotalAmount(BigDecimal.ZERO);
        if (order.getCustomerId() != null) {
            Customer c = customerMapper.selectById(order.getCustomerId());
            order.setCustomerName(c != null ? c.getName() : null);
        }
        returnMapper.updateById(order);
        itemMapper.delete(new LambdaQueryWrapper<SaleReturnItem>().eq(SaleReturnItem::getReturnId, id));
        saveItems(id, itemMaps);
        return returnMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        SaleReturn order = returnMapper.selectById(id);
        if (order == null) throw new BusinessException("销售退货单不存在");
        if (!SaleReturnStatus.DRAFT.getCode().equals(order.getStatus())) throw new BusinessException("只有草稿状态可审核");
        List<SaleReturnItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<SaleReturnItem>().eq(SaleReturnItem::getReturnId, id));
        if (items.isEmpty()) throw new BusinessException("销售退货单明细不能为空");
        // 库存联动：客户退回不良品，入库增加库存（品质等级 DEFECT）
        for (SaleReturnItem it : items) {
            if (it.getQuantity() == null || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;
            Product product = it.getProductId() != null ? productMapper.selectById(it.getProductId()) : null;
            stockService.changeStock(order.getWarehouseId(),
                    product != null ? product.getName() : (it.getProductName() != null ? it.getProductName() : ""),
                    it.getQuantity(),
                    StockChangeType.SALE_RETURN_IN, order.getCode(), RelatedBillType.SALE_RETURN, it.getProductId(),
                    product != null ? product.getSpec() : "", order.getId(), "DEFECT");
        }
        SaleReturn u = new SaleReturn();
        u.setId(id);
        u.setStatus(SaleReturnStatus.AUDITED.getCode());
        u.setAuditorId(getCurrentUserId());
        u.setAuditorName(getCurrentUserName());
        u.setAuditTime(LocalDateTime.now());
        returnMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unAudit(Long id) {
        SaleReturn order = returnMapper.selectById(id);
        if (order == null) throw new BusinessException("销售退货单不存在");
        if (!SaleReturnStatus.AUDITED.getCode().equals(order.getStatus())) throw new BusinessException("只有已审核的退货单可反审核");
        // 对称回滚：从 DEFECT 库存扣减已入库数量
        List<SaleReturnItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<SaleReturnItem>().eq(SaleReturnItem::getReturnId, id));
        for (SaleReturnItem it : items) {
            if (it.getQuantity() == null || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;
            Product product = it.getProductId() != null ? productMapper.selectById(it.getProductId()) : null;
            stockService.changeStock(order.getWarehouseId(),
                    product != null ? product.getName() : (it.getProductName() != null ? it.getProductName() : ""),
                    it.getQuantity().negate(),
                    StockChangeType.SALE_RETURN_UN_AUDIT, order.getCode(), RelatedBillType.SALE_RETURN, it.getProductId(),
                    product != null ? product.getSpec() : "", order.getId(), "DEFECT");
        }
        SaleReturn u = new SaleReturn();
        u.setId(id);
        u.setStatus(SaleReturnStatus.DRAFT.getCode());
        u.setAuditorId(null);
        u.setAuditorName(null);
        u.setAuditTime(null);
        returnMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        SaleReturn old = returnMapper.selectById(id);
        if (old == null) throw new BusinessException("销售退货单不存在");
        if (!SaleReturnStatus.DRAFT.getCode().equals(old.getStatus())) throw new BusinessException("只有草稿状态可作废");
        SaleReturn u = new SaleReturn();
        u.setId(id);
        u.setStatus(SaleReturnStatus.CANCELLED.getCode());
        returnMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SaleReturn old = returnMapper.selectById(id);
        if (old == null) throw new BusinessException("销售退货单不存在");
        if (!SaleReturnStatus.DRAFT.getCode().equals(old.getStatus())) throw new BusinessException("只有草稿状态可删除");
        itemMapper.delete(new LambdaQueryWrapper<SaleReturnItem>().eq(SaleReturnItem::getReturnId, id));
        returnMapper.deleteById(id);
    }

    private void saveItems(Long returnId, List<Map<String, Object>> itemMaps) {
        if (itemMaps == null) return;
        for (Map<String, Object> map : itemMaps) {
            SaleReturnItem it = new SaleReturnItem();
            it.setReturnId(returnId);
            if (map.get("productId") != null) it.setProductId(Long.valueOf(map.get("productId").toString()));
            if (map.get("quantity") != null) it.setQuantity(new BigDecimal(map.get("quantity").toString()));
            if (map.get("unitPrice") != null) it.setUnitPrice(new BigDecimal(map.get("unitPrice").toString()));
            if (map.get("amount") != null) it.setAmount(new BigDecimal(map.get("amount").toString()));
            if (map.get("remark") != null) it.setRemark(map.get("remark").toString());
            // 销售退货固定为不良品
            it.setQualityType("DEFECT");
            // 冗余产品名
            if (it.getProductId() != null) {
                Product p = productMapper.selectById(it.getProductId());
                if (p != null) it.setProductName(p.getName());
            }
            Long cid = CompanyContext.get();
            if (cid != null && cid > 0) it.setCompanyId(cid);
            itemMapper.insert(it);
        }
    }

    private String generateCode() {
        String d = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String pat = "XTH-" + d;
        LambdaQueryWrapper<SaleReturn> w = new LambdaQueryWrapper<SaleReturn>()
                .likeRight(SaleReturn::getCode, pat).orderByDesc(SaleReturn::getCode).last("LIMIT 1");
        SaleReturn last = returnMapper.selectOne(w);
        int seq = 1;
        if (last != null && last.getCode() != null) {
            try {
                seq = Integer.parseInt(last.getCode().substring(last.getCode().length() - 3)) + 1;
            } catch (Exception e) { seq = 1; }
        }
        return "XTH-" + d + String.format("%03d", seq);
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
