package com.beichen.erp.sale.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.customer.entity.Customer;
import com.beichen.erp.customer.mapper.CustomerMapper;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.common.DocStatus;
import com.beichen.erp.inventory.common.RelatedBillType;
import com.beichen.erp.inventory.common.StockChangeType;
import com.beichen.erp.warehouse.service.WarehouseStockService;
import com.beichen.erp.material.entity.Product;
import com.beichen.erp.material.mapper.ProductMapper;
import com.beichen.erp.sale.entity.SaleOutbound;
import com.beichen.erp.sale.entity.SaleOutboundItem;
import com.beichen.erp.sale.mapper.SaleOutboundMapper;
import com.beichen.erp.sale.mapper.SaleOutboundItemMapper;
import com.beichen.erp.sale.service.SaleOutboundService;
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
public class SaleOutboundServiceImpl implements SaleOutboundService {

    private final SaleOutboundMapper outboundMapper;
    private final SaleOutboundItemMapper itemMapper;
    private final CustomerMapper customerMapper;
    private final WarehouseStockService stockService;
    private final ProductMapper productMapper;

    @Override
    public Page<Map<String, Object>> page(String status, Long customerId, String code, int pageNum, int pageSize) {
        LambdaQueryWrapper<SaleOutbound> w = new LambdaQueryWrapper<SaleOutbound>()
                .eq(status != null && !status.isBlank(), SaleOutbound::getStatus, status)
                .eq(customerId != null, SaleOutbound::getCustomerId, customerId)
                .like(code != null && !code.isBlank(), SaleOutbound::getCode, code)
                .orderByDesc(SaleOutbound::getId);
        Page<SaleOutbound> raw = outboundMapper.selectPage(new Page<>(pageNum, pageSize), w);
        // 批量查询客户名称，消除 N+1
        List<Long> customerIds = raw.getRecords().stream().map(SaleOutbound::getCustomerId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, Customer> customerMap = customerIds.isEmpty() ? Collections.emptyMap()
                : customerMapper.selectBatchIds(customerIds).stream()
                        .collect(Collectors.toMap(Customer::getId, c -> c, (a, b) -> a));
        Page<Map<String, Object>> res = new Page<>(pageNum, pageSize, raw.getTotal());
        res.setRecords(raw.getRecords().stream().map(o -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", o.getId());
            m.put("code", o.getCode());
            m.put("orderId", o.getOrderId());
            m.put("customerId", o.getCustomerId());
            m.put("warehouseId", o.getWarehouseId());
            m.put("outboundDate", o.getOutboundDate());
            m.put("status", o.getStatus());
            m.put("totalAmount", o.getTotalAmount());
            m.put("remark", o.getRemark());
            m.put("createTime", o.getCreateTime());
            Customer c = o.getCustomerId() != null ? customerMap.get(o.getCustomerId()) : null;
            m.put("customerName", c != null ? c.getName() : "");
            return m;
        }).toList());
        return res;
    }

    @Override
    public SaleOutbound getById(Long id) { return outboundMapper.selectById(id); }

    @Override
    public List<SaleOutboundItem> getItems(Long outboundId) {
        return itemMapper.selectList(new LambdaQueryWrapper<SaleOutboundItem>().eq(SaleOutboundItem::getOutboundId, outboundId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(SaleOutbound outbound, List<SaleOutboundItem> items) {
        if (outbound.getCustomerId() == null) throw new BusinessException("客户不能为空");
        if (outbound.getWarehouseId() == null) throw new BusinessException("出库仓库不能为空");
        outbound.setCode(generateCode());
        outbound.setStatus(DocStatus.DRAFT.name());
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) outbound.setCompanyId(cid);
        BigDecimal total = BigDecimal.ZERO;
        outboundMapper.insert(outbound);
        for (SaleOutboundItem it : items) {
            it.setId(null);
            it.setOutboundId(outbound.getId());
            BigDecimal q = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
            BigDecimal p = it.getUnitPrice() != null ? it.getUnitPrice() : BigDecimal.ZERO;
            it.setAmount(q.multiply(p));
            total = total.add(it.getAmount());
            if (cid != null && cid > 0) it.setCompanyId(cid);
            itemMapper.insert(it);
        }
        SaleOutbound u = new SaleOutbound();
        u.setId(outbound.getId());
        u.setTotalAmount(total);
        outboundMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(SaleOutbound outbound, List<SaleOutboundItem> items) {
        SaleOutbound old = outboundMapper.selectById(outbound.getId());
        if (old == null) throw new BusinessException("销售出库单不存在");
        if (!DocStatus.DRAFT.name().equals(old.getStatus())) throw new BusinessException("只有草稿状态可编辑");
        outbound.setCode(old.getCode());
        outboundMapper.updateById(outbound);
        itemMapper.delete(new LambdaQueryWrapper<SaleOutboundItem>().eq(SaleOutboundItem::getOutboundId, outbound.getId()));
        BigDecimal total = BigDecimal.ZERO;
        Long cid = CompanyContext.get();
        for (SaleOutboundItem it : items) {
            it.setId(null);
            it.setOutboundId(outbound.getId());
            BigDecimal q = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
            BigDecimal p = it.getUnitPrice() != null ? it.getUnitPrice() : BigDecimal.ZERO;
            it.setAmount(q.multiply(p));
            total = total.add(it.getAmount());
            if (cid != null && cid > 0) it.setCompanyId(cid);
            itemMapper.insert(it);
        }
        SaleOutbound u = new SaleOutbound();
        u.setId(outbound.getId());
        u.setTotalAmount(total);
        outboundMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        SaleOutbound old = outboundMapper.selectById(id);
        if (old == null) throw new BusinessException("销售出库单不存在");
        if (!DocStatus.DRAFT.name().equals(old.getStatus())) throw new BusinessException("已审核的出库单不可作废");
        SaleOutbound u = new SaleOutbound();
        u.setId(id);
        u.setStatus(DocStatus.CANCELLED.name());
        outboundMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        SaleOutbound outbound = outboundMapper.selectById(id);
        if (outbound == null) throw new BusinessException("销售出库单不存在");
        if (!DocStatus.DRAFT.name().equals(outbound.getStatus())) throw new BusinessException("只有草稿状态可审核");
        List<SaleOutboundItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<SaleOutboundItem>().eq(SaleOutboundItem::getOutboundId, id));
        // 1) 库存联动：出库减库存（changeStock 负数，不足自动抛异常）
        for (SaleOutboundItem it : items) {
            Product product = it.getProductId() != null ? productMapper.selectById(it.getProductId()) : null;
            stockService.changeStock(outbound.getWarehouseId(),
                    product != null ? product.getName() : "",
                    it.getQuantity().negate(), StockChangeType.SALE_OUT, outbound.getCode(), RelatedBillType.SALE_OUTBOUND,
                    it.getProductId(),
                    product != null ? product.getSpec() : "", outbound.getId(), it.getQualityType());
        }
        // 2) 更新出库单状态（销售出库单仅负责真实出库：扣库存；应收由销售订单统一生成，此处不改动订单状态，避免跨单状态污染）
        SaleOutbound u = new SaleOutbound();
        u.setId(id);
        u.setStatus(DocStatus.AUDITED.name());
        outboundMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unAudit(Long id) {
        SaleOutbound outbound = outboundMapper.selectById(id);
        if (outbound == null) throw new BusinessException("销售出库单不存在");
        if (!DocStatus.AUDITED.name().equals(outbound.getStatus())) throw new BusinessException("只有已审核的出库单可反审核");
        List<SaleOutboundItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<SaleOutboundItem>().eq(SaleOutboundItem::getOutboundId, id));
        // 1) 库存回补：出库时按负数扣库存，反审核原路加回（与 audit 的扣减对称）
        for (SaleOutboundItem it : items) {
            if (it.getQuantity() == null || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;
            Product product = it.getProductId() != null ? productMapper.selectById(it.getProductId()) : null;
            stockService.changeStock(outbound.getWarehouseId(),
                    product != null ? product.getName() : "",
                    it.getQuantity(), StockChangeType.SALE_OUT_UN_AUDIT, outbound.getCode(), RelatedBillType.SALE_OUTBOUND,
                    it.getProductId(),
                    product != null ? product.getSpec() : "", outbound.getId(), it.getQualityType());
        }
        // 2) 出库单状态回退为草稿（实体无审核人字段，仅回退状态）
        SaleOutbound u = new SaleOutbound();
        u.setId(id);
        u.setStatus(DocStatus.DRAFT.name());
        outboundMapper.updateById(u);
    }

    private String generateCode() {
        String d = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String pat = "CK-" + d;
        LambdaQueryWrapper<SaleOutbound> w = new LambdaQueryWrapper<SaleOutbound>()
                .likeRight(SaleOutbound::getCode, pat).orderByDesc(SaleOutbound::getCode).last("LIMIT 1");
        SaleOutbound last = outboundMapper.selectOne(w);
        int seq = 1;
        if (last != null && last.getCode() != null) {
            try { seq = Integer.parseInt(last.getCode().substring(last.getCode().length() - 3)) + 1; } catch (Exception e) { seq = 1; }
        }
        return "CK-" + d + String.format("%03d", seq);
    }
}
