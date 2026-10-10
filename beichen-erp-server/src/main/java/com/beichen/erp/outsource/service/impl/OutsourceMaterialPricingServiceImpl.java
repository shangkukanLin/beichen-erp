package com.beichen.erp.outsource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beichen.erp.outsource.common.MaterialOrderStatus;
import com.beichen.erp.outsource.entity.MaterialOrder;
import com.beichen.erp.outsource.entity.MaterialOrderItem;
import com.beichen.erp.outsource.entity.OutsourceMaterial;
import com.beichen.erp.outsource.mapper.MaterialOrderItemMapper;
import com.beichen.erp.outsource.mapper.MaterialOrderMapper;
import com.beichen.erp.outsource.mapper.OutsourceMaterialMapper;
import com.beichen.erp.outsource.service.OutsourceMaterialPricingService;
import com.beichen.erp.warehouse.service.CostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * {@link OutsourceMaterialPricingService} 实现（F7-77，2026-09-20）。
 *
 * <p>公共口径：<b>排除 CANCELLED 订单</b>、<b>按交期升序</b>、<b>批量取明细（消除 N+1）</b>、<b>结果 4 位</b>。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutsourceMaterialPricingServiceImpl implements OutsourceMaterialPricingService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final MaterialOrderMapper materialOrderMapper;
    private final MaterialOrderItemMapper materialOrderItemMapper;
    private final OutsourceMaterialMapper outsourceMaterialMapper;
    /** 2026-10-09：物料**完全成本**（自身加权 + 子物料递归），与产品成本/结单/报损同口径 */
    private final CostService costService;

    @Override
    public BigDecimal weightedPrice(Long supplierId, Long materialId) {
        if (materialId == null) return ZERO;
        BigDecimal price = weighted(supplierId, materialId);
        // 工厂维度查不到 ⇒ 回退该物料全部订单（保证新增单据仍有默认单价）
        if (price == null && supplierId != null) price = weighted(null, materialId);
        return price != null ? price : ZERO;
    }

    @Override
    public BigDecimal fifoPrice(Long materialId, BigDecimal requiredQty) {
        return fifo(materialId, requiredQty);
    }

    @Override
    public BigDecimal fifoPriceWithFallback(Long materialId, BigDecimal requiredQty) {
        if (materialId == null) return ZERO;
        // 1) 成本价优先（覆盖委外其他出入库 / 结算退料等非物料订单来源；原实现只扫物料订单 ⇒ 会算出 0）
        //    2026-10-09：改用**完全成本**（自身移动加权 + 子物料递归展开），
        //    原先只取自身 cost_price ⇒ 装配件的子物料成本被漏掉（与产品成本同上一个缺口）。
        OutsourceMaterial mat = outsourceMaterialMapper.selectById(materialId);
        BigDecimal fullCost = costService.materialFullCost(materialId);
        if (fullCost.compareTo(ZERO) > 0) return fullCost;
        // 2) FIFO 兜底
        BigDecimal fifo = fifo(materialId, requiredQty);
        if (fifo.compareTo(ZERO) > 0) return fifo;
        // 3) 最末兜底：物料主数据参考价
        if (mat != null && mat.getPrice() != null && mat.getPrice().compareTo(ZERO) > 0) return mat.getPrice();
        return ZERO;
    }

    // ==================== 私有 ====================

    /** 加权均价；无有效数量返回 null（由调用方决定是否回退） */
    private BigDecimal weighted(Long supplierId, Long materialId) {
        try {
            LambdaQueryWrapper<MaterialOrder> w = baseOrderWrapper();
            if (supplierId != null) w.eq(MaterialOrder::getSupplierId, supplierId);
            BigDecimal totalAmount = ZERO;
            BigDecimal totalQty = ZERO;
            for (MaterialOrderItem it : candidateItems(materialId, w)) {
                BigDecimal qty = nz(it.getOrderQuantity());
                BigDecimal price = nz(it.getUnitPrice());
                totalAmount = totalAmount.add(qty.multiply(price));
                totalQty = totalQty.add(qty);
            }
            if (totalQty.compareTo(ZERO) > 0) return totalAmount.divide(totalQty, 4, RoundingMode.HALF_UP);
        } catch (Exception e) {
            log.warn("加权均价计算失败: {}", e.getMessage());
        }
        return null;
    }

    /** 按交期 FIFO 累计到 requiredQty 的加权均价；无有效数量返回 0 */
    private BigDecimal fifo(Long materialId, BigDecimal requiredQty) {
        if (materialId == null || requiredQty == null || requiredQty.compareTo(ZERO) <= 0) return ZERO;
        try {
            BigDecimal accumulatedAmount = ZERO;
            BigDecimal accumulatedQty = ZERO;
            for (MaterialOrderItem it : candidateItems(materialId, baseOrderWrapper())) {
                BigDecimal qty = nz(it.getOrderQuantity());
                BigDecimal price = nz(it.getUnitPrice());
                if (qty.compareTo(ZERO) <= 0 || price.compareTo(ZERO) <= 0) continue;
                BigDecimal need = requiredQty.subtract(accumulatedQty);
                if (need.compareTo(ZERO) <= 0) break;      // 已满足需求量 ⇒ 停止累计（与原逐单实现等价）
                BigDecimal use = qty.min(need);
                accumulatedAmount = accumulatedAmount.add(use.multiply(price));
                accumulatedQty = accumulatedQty.add(use);
            }
            if (accumulatedQty.compareTo(ZERO) > 0)
                return accumulatedAmount.divide(accumulatedQty, 4, RoundingMode.HALF_UP);
        } catch (Exception e) {
            log.warn("FIFO单价计算失败: {}", e.getMessage());
        }
        return ZERO;
    }

    /**
     * 参与计价的订单范围：**排除已作废(CANCELLED)**，按交期升序（FIFO 需要）。
     * <p>F7-77：三处 FIFO 原先都不过滤状态 ⇒ 作废单的单价会污染结单/退货计价。</p>
     */
    private LambdaQueryWrapper<MaterialOrder> baseOrderWrapper() {
        return new LambdaQueryWrapper<MaterialOrder>()
                .ne(MaterialOrder::getStatus, MaterialOrderStatus.CANCELLED.getCode())
                .orderByAsc(MaterialOrder::getDeliveryDate);
    }

    /**
     * 取候选明细：**1 次订单查询 + 1 次明细查询**（原实现是"逐订单查明细" = N+1），
     * 并按订单交期升序把明细展开（FIFO 依赖该顺序）。
     */
    private List<MaterialOrderItem> candidateItems(Long materialId, LambdaQueryWrapper<MaterialOrder> orderWrapper) {
        List<MaterialOrder> orders = materialOrderMapper.selectList(orderWrapper);
        if (orders.isEmpty()) return List.of();
        List<Long> orderIds = orders.stream().map(MaterialOrder::getId).toList();
        Map<Long, List<MaterialOrderItem>> byOrder = materialOrderItemMapper.selectList(
                        new LambdaQueryWrapper<MaterialOrderItem>()
                                .in(MaterialOrderItem::getOrderId, orderIds)
                                .eq(MaterialOrderItem::getMaterialId, materialId))
                .stream().collect(Collectors.groupingBy(MaterialOrderItem::getOrderId));
        List<MaterialOrderItem> out = new ArrayList<>();
        for (MaterialOrder o : orders) out.addAll(byOrder.getOrDefault(o.getId(), List.of()));
        return out;
    }

    private BigDecimal nz(BigDecimal v) {
        return v != null ? v : ZERO;
    }
}
