package com.beichen.erp.outsource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.outsource.entity.OutsourceOrder;
import com.beichen.erp.outsource.entity.OutsourceOrderMaterial;
import com.beichen.erp.outsource.entity.OutsourceOrderProduct;
import com.beichen.erp.outsource.mapper.OutsourceOrderMapper;
import com.beichen.erp.outsource.mapper.OutsourceOrderMaterialMapper;
import com.beichen.erp.outsource.mapper.OutsourceOrderProductMapper;
import com.beichen.erp.outsource.service.OutsourceOrderService;
import com.beichen.erp.supplier.entity.Supplier;
import com.beichen.erp.supplier.mapper.SupplierMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OutsourceOrderServiceImpl implements OutsourceOrderService {

    private final OutsourceOrderMapper orderMapper;
    private final OutsourceOrderProductMapper productMapper;
    private final OutsourceOrderMaterialMapper materialMapper;
    private final SupplierMapper supplierMapper;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public Page<Map<String, Object>> page(String status, Long factoryId, String code, int pageNum, int pageSize) {
        LambdaQueryWrapper<OutsourceOrder> w = new LambdaQueryWrapper<OutsourceOrder>()
                .eq(factoryId != null, OutsourceOrder::getFactoryId, factoryId)
                .eq(code != null && !code.isBlank(), OutsourceOrder::getCode, code)
                .orderByDesc(OutsourceOrder::getId);
        if (status != null && !status.isBlank()) {
            if (status.contains(",")) {
                w.in(OutsourceOrder::getStatus, Arrays.stream(status.split(",")).map(String::trim).toList());
            } else {
                w.eq(OutsourceOrder::getStatus, status);
            }
        }
        Page<OutsourceOrder> rawPage = orderMapper.selectPage(new Page<>(pageNum, pageSize), w);
        Page<Map<String, Object>> result = new Page<>(pageNum, pageSize, rawPage.getTotal());
        result.setRecords(rawPage.getRecords().stream().map(o -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", o.getId()); m.put("code", o.getCode()); m.put("status", o.getStatus());
            m.put("factoryId", o.getFactoryId());
            m.put("planStartDate", o.getPlanStartDate()); m.put("planEndDate", o.getPlanEndDate());
            m.put("actualStartDate", o.getActualStartDate()); m.put("actualEndDate", o.getActualEndDate());
            m.put("taxIncluded", o.getTaxIncluded()); m.put("taxRate", o.getTaxRate());
            m.put("totalAmount", o.getTotalAmount()); m.put("remark", o.getRemark());
            m.put("createTime", o.getCreateTime());
            if (o.getFactoryId() != null) {
                Supplier sup = supplierMapper.selectById(o.getFactoryId());
                m.put("factoryName", sup != null ? sup.getName() : "");
            }
            // 产品信息
            List<OutsourceOrderProduct> products = productMapper.selectList(
                    new LambdaQueryWrapper<OutsourceOrderProduct>().eq(OutsourceOrderProduct::getOrderId, o.getId()));
            m.put("productCount", (long) products.size());
            m.put("productNames", products.stream()
                    .map(p -> p.getProductName() != null ? p.getProductName() : "")
                    .filter(s -> !s.isEmpty())
                    .collect(java.util.stream.Collectors.joining(" / ")));
            return m;
        }).toList());

        // 补充最近交货时间
        List<Long> orderIds = result.getRecords().stream()
                .map(m -> (Long) m.get("id")).collect(Collectors.toList());
        if (!orderIds.isEmpty()) {
            String placeholders = orderIds.stream().map(id -> "?").collect(Collectors.joining(","));
            List<Map<String, Object>> deliveryRows = jdbcTemplate.queryForList(
                    "SELECT order_id, MAX(delivery_date) AS latest_date FROM outsource_order_delivery " +
                    "WHERE order_id IN (" + placeholders + ") GROUP BY order_id",
                    orderIds.toArray());
            Map<Long, Object> deliveryMap = new HashMap<>();
            for (Map<String, Object> row : deliveryRows) {
                Long oid = ((Number) row.get("order_id")).longValue();
                deliveryMap.put(oid, row.get("latest_date"));
            }
            for (Map<String, Object> m : result.getRecords()) {
                m.put("latestDeliveryDate", deliveryMap.getOrDefault(m.get("id"), null));
            }
        }

        return result;
    }

    @Override
    public OutsourceOrder getById(Long id) {
        return orderMapper.selectById(id);
    }

    @Override
    public List<OutsourceOrderProduct> getProducts(Long orderId) {
        return productMapper.selectList(
                new LambdaQueryWrapper<OutsourceOrderProduct>().eq(OutsourceOrderProduct::getOrderId, orderId));
    }

    @Override
    public List<OutsourceOrderMaterial> getMaterials(Long productId) {
        return materialMapper.selectList(
                new LambdaQueryWrapper<OutsourceOrderMaterial>().eq(OutsourceOrderMaterial::getProductId, productId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(OutsourceOrder order, List<OutsourceOrderProduct> products) {
        if (order.getFactoryId() == null) throw new BusinessException("加工厂不能为空");
        order.setCode(generateCode());
        order.setStatus("待确认");
        // 设置公司ID
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) order.setCompanyId(cid);
        // 计算总金额
        BigDecimal total = BigDecimal.ZERO;
        orderMapper.insert(order);
        for (OutsourceOrderProduct p : products) {
            if (p.getQuantity() != null && p.getUnitPrice() != null) {
                p.setAmount(p.getQuantity().multiply(p.getUnitPrice()));
            } else {
                p.setAmount(BigDecimal.ZERO);
            }
            total = total.add(p.getAmount());
            p.setOrderId(order.getId());
            if (cid != null && cid > 0) p.setCompanyId(cid);
            productMapper.insert(p);
            // 保存物料
            if (p.getMaterials() != null) {
                for (OutsourceOrderMaterial mat : p.getMaterials()) {
                    mat.setId(null);
                    mat.setProductId(p.getId());
                    if (cid != null && cid > 0) mat.setCompanyId(cid);
                    materialMapper.insert(mat);
                }
            }
        }
        // 更新总金额
        OutsourceOrder update = new OutsourceOrder();
        update.setId(order.getId());
        update.setTotalAmount(total);
        orderMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(OutsourceOrder order, List<OutsourceOrderProduct> newProducts) {
        OutsourceOrder old = orderMapper.selectById(order.getId());
        if (old == null) throw new BusinessException("加工单不存在");
        if ("已取消".equals(old.getStatus())) throw new BusinessException("已取消的单据不可编辑");

        // 删除旧产品（级联删除物料）
        List<OutsourceOrderProduct> oldProducts = productMapper.selectList(
                new LambdaQueryWrapper<OutsourceOrderProduct>().eq(OutsourceOrderProduct::getOrderId, order.getId()));
        for (OutsourceOrderProduct op : oldProducts) {
            materialMapper.delete(new LambdaQueryWrapper<OutsourceOrderMaterial>()
                    .eq(OutsourceOrderMaterial::getProductId, op.getId()));
        }
        productMapper.delete(new LambdaQueryWrapper<OutsourceOrderProduct>()
                .eq(OutsourceOrderProduct::getOrderId, order.getId()));

        // 更新主表
        order.setCode(old.getCode());
        orderMapper.updateById(order);

        // 插入新产品
        BigDecimal total = BigDecimal.ZERO;
        for (OutsourceOrderProduct p : newProducts) {
            if (p.getQuantity() != null && p.getUnitPrice() != null) {
                p.setAmount(p.getQuantity().multiply(p.getUnitPrice()));
            } else {
                p.setAmount(BigDecimal.ZERO);
            }
            total = total.add(p.getAmount());
            p.setId(null);
            p.setOrderId(order.getId());
            productMapper.insert(p);
            // 插入物料
            if (p.getMaterials() != null) {
                for (OutsourceOrderMaterial mat : p.getMaterials()) {
                    mat.setId(null);
                    mat.setProductId(p.getId());
                    materialMapper.insert(mat);
                }
            }
        }
        // 更新总金额
        OutsourceOrder amountUpdate = new OutsourceOrder();
        amountUpdate.setId(order.getId());
        amountUpdate.setTotalAmount(total);
        orderMapper.updateById(amountUpdate);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long id) {
        OutsourceOrder order = orderMapper.selectById(id);
        if (order == null) throw new BusinessException("加工单不存在");
        if (!"待确认".equals(order.getStatus())) throw new BusinessException("只有待确认状态可以确认");
        OutsourceOrder update = new OutsourceOrder();
        update.setId(id);
        update.setStatus("生产中");
        update.setActualStartDate(LocalDate.now());
        orderMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void complete(Long id) {
        OutsourceOrder order = orderMapper.selectById(id);
        if (order == null) throw new BusinessException("加工单不存在");
        if (!"生产中".equals(order.getStatus())) throw new BusinessException("只有生产中状态可以完成");
        OutsourceOrder update = new OutsourceOrder();
        update.setId(id);
        update.setStatus("已完成");
        update.setActualEndDate(LocalDate.now());
        orderMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        OutsourceOrder order = orderMapper.selectById(id);
        if (order == null) throw new BusinessException("加工单不存在");
        if ("已取消".equals(order.getStatus())) throw new BusinessException("已取消状态不可重复取消");
        OutsourceOrder update = new OutsourceOrder();
        update.setId(id);
        update.setStatus("已取消");
        orderMapper.updateById(update);
    }

    private String generateCode() {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String likePattern = "WO-" + dateStr;
        LambdaQueryWrapper<OutsourceOrder> w = new LambdaQueryWrapper<OutsourceOrder>()
                .likeRight(OutsourceOrder::getCode, likePattern)
                .orderByDesc(OutsourceOrder::getCode)
                .last("LIMIT 1");
        OutsourceOrder last = orderMapper.selectOne(w);
        int seq = 1;
        if (last != null && last.getCode() != null) {
            try {
                String numPart = last.getCode().substring(last.getCode().length() - 3);
                seq = Integer.parseInt(numPart) + 1;
            } catch (Exception e) { seq = 1; }
        }
        return "WO-" + dateStr + String.format("%03d", seq);
    }
}
