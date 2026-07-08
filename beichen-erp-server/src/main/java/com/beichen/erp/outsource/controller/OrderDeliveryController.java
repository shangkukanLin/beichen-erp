package com.beichen.erp.outsource.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beichen.erp.common.R;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.outsource.entity.OutsourceOrder;
import com.beichen.erp.outsource.entity.OutsourceOrderDelivery;
import com.beichen.erp.outsource.entity.OutsourceOrderProduct;
import com.beichen.erp.outsource.mapper.OutsourceOrderDeliveryMapper;
import com.beichen.erp.outsource.service.OutsourceOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/outsource/order-delivery")
@RequiredArgsConstructor
public class OrderDeliveryController {

    private final OutsourceOrderDeliveryMapper deliveryMapper;
    private final OutsourceOrderService orderService;

    /** 获取某加工单的所有交货记录 */
    @GetMapping("/list/{orderId}")
    public R<List<OutsourceOrderDelivery>> listByOrder(@PathVariable Long orderId) {
        return R.ok(deliveryMapper.selectList(new LambdaQueryWrapper<OutsourceOrderDelivery>()
                .eq(OutsourceOrderDelivery::getOrderId, orderId)
                .orderByDesc(OutsourceOrderDelivery::getId)));
    }

    /** 获取交货汇总 */
    @GetMapping("/summary/{orderId}")
    public R<Map<String, Object>> summary(@PathVariable Long orderId) {
        OutsourceOrder order = orderService.getById(orderId);
        if (order == null) throw new BusinessException("加工单不存在");
        List<OutsourceOrderProduct> products = orderService.getProducts(orderId);
        List<OutsourceOrderDelivery> deliveries = deliveryMapper.selectList(
                new LambdaQueryWrapper<OutsourceOrderDelivery>().eq(OutsourceOrderDelivery::getOrderId, orderId));

        BigDecimal totalQty = products.stream().map(p -> p.getQuantity() != null ? p.getQuantity() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal deliveredQty = deliveries.stream().map(d -> d.getQuantity() != null ? d.getQuantity() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> result = new HashMap<>();
        result.put("totalQuantity", totalQty);
        result.put("deliveredQuantity", deliveredQty);
        result.put("remainingQuantity", totalQty.subtract(deliveredQty));
        result.put("deliveryCount", deliveries.size());

        // 按产品分组统计
        List<Map<String, Object>> productStats = new java.util.ArrayList<>();
        for (OutsourceOrderProduct p : products) {
            String pn = p.getProductName() != null ? p.getProductName() : "未命名产品";
            BigDecimal pQty = p.getQuantity() != null ? p.getQuantity() : BigDecimal.ZERO;
            BigDecimal pDelivered = deliveries.stream()
                    .filter(d -> pn.equals(d.getProductName()))
                    .map(d -> d.getQuantity() != null ? d.getQuantity() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            Map<String, Object> ps = new HashMap<>();
            ps.put("productName", pn);
            ps.put("totalQuantity", pQty);
            ps.put("deliveredQuantity", pDelivered);
            ps.put("remainingQuantity", pQty.subtract(pDelivered));
            productStats.add(ps);
        }
        result.put("productStats", productStats);
        return R.ok(result);
    }

    /** 新增交货记录 */
    @PostMapping
    public R<Void> create(@RequestBody OutsourceOrderDelivery delivery) {
        if (delivery.getOrderId() == null) throw new BusinessException("加工单ID不能为空");
        OutsourceOrder order = orderService.getById(delivery.getOrderId());
        if (order == null) throw new BusinessException("加工单不存在");
        if (!"生产中".equals(order.getStatus())) throw new BusinessException("只有生产中的加工单可录入交货");
        deliveryMapper.insert(delivery);
        return R.ok();
    }

    /** 修改交货记录 */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody OutsourceOrderDelivery delivery) {
        delivery.setId(id);
        deliveryMapper.updateById(delivery);
        return R.ok();
    }

    /** 删除交货记录 */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        deliveryMapper.deleteById(id);
        return R.ok();
    }
}
