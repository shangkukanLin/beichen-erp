package com.beichen.erp.sale.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.customer.entity.Customer;
import com.beichen.erp.customer.mapper.CustomerMapper;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.finance.entity.FinanceReceivable;
import com.beichen.erp.finance.mapper.FinanceReceivableMapper;
import com.beichen.erp.inventory.service.InventoryWarehouseStockService;
import com.beichen.erp.sale.entity.SaleOrder;
import com.beichen.erp.sale.entity.SaleOutbound;
import com.beichen.erp.sale.entity.SaleOutboundItem;
import com.beichen.erp.sale.mapper.SaleOutboundMapper;
import com.beichen.erp.sale.mapper.SaleOutboundItemMapper;
import com.beichen.erp.sale.mapper.SaleOrderMapper;
import com.beichen.erp.sale.service.SaleOutboundService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SaleOutboundServiceImpl implements SaleOutboundService {

    private final SaleOutboundMapper outboundMapper;
    private final SaleOutboundItemMapper itemMapper;
    private final SaleOrderMapper orderMapper;
    private final CustomerMapper customerMapper;
    private final InventoryWarehouseStockService stockService;
    private final FinanceReceivableMapper receivableMapper;

    @Override
    public Page<Map<String, Object>> page(String status, Long customerId, String code, int pageNum, int pageSize) {
        LambdaQueryWrapper<SaleOutbound> w = new LambdaQueryWrapper<SaleOutbound>()
                .eq(status != null && !status.isBlank(), SaleOutbound::getStatus, status)
                .eq(customerId != null, SaleOutbound::getCustomerId, customerId)
                .like(code != null && !code.isBlank(), SaleOutbound::getCode, code)
                .orderByDesc(SaleOutbound::getId);
        Page<SaleOutbound> raw = outboundMapper.selectPage(new Page<>(pageNum, pageSize), w);
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
            if (o.getCustomerId() != null) {
                Customer c = customerMapper.selectById(o.getCustomerId());
                m.put("customerName", c != null ? c.getName() : "");
            }
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
        if (outbound.getCustomerId() != null) {
            Customer c = customerMapper.selectById(outbound.getCustomerId());
            outbound.setCustomerName(c != null ? c.getName() : "");
        }
        outbound.setCode(generateCode());
        outbound.setStatus("草稿");
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
        if (!"草稿".equals(old.getStatus())) throw new BusinessException("只有草稿状态可编辑");
        if (outbound.getCustomerId() != null) {
            Customer c = customerMapper.selectById(outbound.getCustomerId());
            outbound.setCustomerName(c != null ? c.getName() : "");
        }
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
        if (!"草稿".equals(old.getStatus())) throw new BusinessException("已审核的出库单不可作废");
        SaleOutbound u = new SaleOutbound();
        u.setId(id);
        u.setStatus("已作废");
        outboundMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        SaleOutbound outbound = outboundMapper.selectById(id);
        if (outbound == null) throw new BusinessException("销售出库单不存在");
        if (!"草稿".equals(outbound.getStatus())) throw new BusinessException("只有草稿状态可审核");
        List<SaleOutboundItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<SaleOutboundItem>().eq(SaleOutboundItem::getOutboundId, id));
        // 1) 库存联动：出库减库存（changeStock 负数，不足自动抛异常）
        for (SaleOutboundItem it : items) {
            stockService.changeStock(outbound.getWarehouseId(), it.getMaterialName(),
                    it.getQuantity().negate(), "销售出库", outbound.getCode(), "销售出库",
                    it.getMaterialId(), it.getSpec());
        }
        // 2) 生成应收台账
        FinanceReceivable fr = new FinanceReceivable();
        fr.setBillNo(outbound.getCode());
        fr.setCustomerId(outbound.getCustomerId());
        fr.setCustomerName(outbound.getCustomerName());
        fr.setSourceBillType("销售出库");
        fr.setSourceBillNo(outbound.getCode());
        fr.setAmount(outbound.getTotalAmount());
        fr.setPaidAmount(BigDecimal.ZERO);
        fr.setUnpaidAmount(outbound.getTotalAmount());
        fr.setDueDate(outbound.getOutboundDate() != null ? outbound.getOutboundDate().plusMonths(1) : null);
        fr.setStatus("未结清");
        receivableMapper.insert(fr);
        // 3) 更新客户应收余额（冗余）
        if (outbound.getCustomerId() != null) {
            Customer c = customerMapper.selectById(outbound.getCustomerId());
            if (c != null) {
                Customer u = new Customer();
                u.setId(c.getId());
                BigDecimal newBal = (c.getReceivableBalance() != null ? c.getReceivableBalance() : BigDecimal.ZERO)
                        .add(outbound.getTotalAmount());
                u.setReceivableBalance(newBal);
                customerMapper.updateById(u);
            }
        }
        // 4) 更新出库单状态
        SaleOutbound u = new SaleOutbound();
        u.setId(id);
        u.setStatus("已审核");
        outboundMapper.updateById(u);
        // 5) 关联销售订单置为已出库
        if (outbound.getOrderId() != null) {
            SaleOrder o = new SaleOrder();
            o.setId(outbound.getOrderId());
            o.setStatus("已出库");
            orderMapper.updateById(o);
        }
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
