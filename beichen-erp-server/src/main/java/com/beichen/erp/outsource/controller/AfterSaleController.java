package com.beichen.erp.outsource.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.inventory.common.RelatedBillType;
import com.beichen.erp.inventory.common.StockChangeType;
import com.beichen.erp.warehouse.service.WarehouseStockService;
import com.beichen.erp.outsource.common.DeliveryType;
import com.beichen.erp.outsource.entity.OutsourceOrderDelivery;
import com.beichen.erp.outsource.entity.OutsourceOrderProduct;
import com.beichen.erp.outsource.mapper.OutsourceOrderDeliveryMapper;
import com.beichen.erp.outsource.mapper.OutsourceOrderProductMapper;
import com.beichen.erp.material.entity.Product;
import com.beichen.erp.material.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 收费售后：客户退回不良品（不关联加工单），入库增加成品仓不良品库存。
 * 复用 OutsourceOrderDelivery 表，sourceType 固定 AFTER_SALE 区分来源。
 */
@Slf4j
@RestController
@RequestMapping("/api/outsource/after-sale")
@RequiredArgsConstructor
public class AfterSaleController {

    private final OutsourceOrderDeliveryMapper deliveryMapper;
    private final OutsourceOrderProductMapper orderProductMapper;
    private final WarehouseStockService warehouseStockService;
    private final ProductMapper productMapper;

    /** 收费售后退不良：客户退回不良品 → 入库增不良品库存（立即生效，不关联加工单） */
    @PostMapping("/return-defect")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> returnDefect(@RequestBody Map<String, Object> body) {
        Long warehouseId = body.get("warehouseId") != null
                ? Long.valueOf(body.get("warehouseId").toString()) : null;
        if (warehouseId == null) throw new BusinessException("请选择退回仓库");
        Long productId = body.get("productId") != null
                ? Long.valueOf(body.get("productId").toString()) : null;
        String productName = body.get("productName") != null ? body.get("productName").toString() : null;
        if (productId == null && (productName == null || productName.isBlank()))
            throw new BusinessException("请选择退回产品");
        BigDecimal qty = body.get("quantity") != null ? new BigDecimal(body.get("quantity").toString()) : null;
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0)
            throw new BusinessException("退回数量必须大于0");
        String remark = body.get("remark") != null ? body.get("remark").toString() : null;

        // 以 productId 优先关联产品名称，保证库存按 (warehouseId, productId, 不良品) 定位
        if (productId != null) {
            Product p = productMapper.selectById(productId);
            if (p != null) productName = p.getName();
        }
        if (productName == null || productName.isBlank())
            throw new BusinessException("无法解析退回产品名称");

        // 1. 入库增加成品仓不良品库存（正数）
        warehouseStockService.changeStock(warehouseId, productId, qty,
                StockChangeType.SALE_RETURN_IN, null, RelatedBillType.SALE_RETURN,
                null, null, "DEFECT");

        // 2. 写入退不良记录（sourceType=AFTER_SALE，不关联加工单，立即生效）
        OutsourceOrderDelivery delivery = new OutsourceOrderDelivery();
        delivery.setProductId(productId);
        delivery.setQuantity(qty);
        delivery.setDeliveryType(DeliveryType.DEFECT_RETURN.getCode());
        delivery.setWarehouseId(warehouseId);
        delivery.setDeliveryDate(LocalDate.now());
        delivery.setRemark("收费售后退回不良品" + (remark != null ? "：" + remark : ""));
        delivery.setIsReverse(false);
        delivery.setStatus("CONFIRMED");
        // 标记来源：收费售后
        delivery.setSourceType("AFTER_SALE");
        deliveryMapper.insert(delivery);
        log.info("收费售后退不良入库: {} x{} 仓库={}", productName, qty, warehouseId);
        return R.ok();
    }

    /** 收费售后退不良列表：按 sourceType=AFTER_SALE 过滤 */
    @GetMapping("/page")
    public R<Page<OutsourceOrderDelivery>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String productName) {
        LambdaQueryWrapper<OutsourceOrderDelivery> w = new LambdaQueryWrapper<OutsourceOrderDelivery>()
                .eq(OutsourceOrderDelivery::getSourceType, "AFTER_SALE");
        // productName 列已删除，改为通过 product_id 关联查询
        if (productName != null && !productName.isBlank()) {
            List<Long> productIds = orderProductMapper.selectList(
                new LambdaQueryWrapper<OutsourceOrderProduct>()
                    .like(OutsourceOrderProduct::getProductName, productName)
                    .select(OutsourceOrderProduct::getId)
            ).stream().map(OutsourceOrderProduct::getId).collect(Collectors.toList());
            if (!productIds.isEmpty()) {
                w.in(OutsourceOrderDelivery::getProductId, productIds);
            } else {
                w.eq(OutsourceOrderDelivery::getId, -1L); // 无匹配产品则返回空
            }
        }
        w.orderByDesc(OutsourceOrderDelivery::getId);
        Page<OutsourceOrderDelivery> res = deliveryMapper.selectPage(
                new Page<>(pageNum, pageSize), w);
        return R.ok(res);
    }

    /** 删除收费售后退不良记录（逻辑取消：仅未产生后续业务时允许） */
    @DeleteMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> remove(@PathVariable Long id) {
        OutsourceOrderDelivery d = deliveryMapper.selectById(id);
        if (d == null) throw new BusinessException("记录不存在");
        if (!"AFTER_SALE".equals(d.getSourceType())) throw new BusinessException("仅可删除收费售后记录");
        // 逆向扣回入库的不良品库存
        if (d.getWarehouseId() != null && d.getQuantity() != null
                && d.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
            // productName 列已删除，通过 productId 查产品表获取名称
            String productName = "";
            if (d.getProductId() != null) {
                Product p = productMapper.selectById(d.getProductId());
                productName = p != null && p.getName() != null ? p.getName() : "";
            }
            warehouseStockService.changeStock(d.getWarehouseId(), d.getProductId(),
                    d.getQuantity().negate(), StockChangeType.SALE_RETURN_UN_AUDIT,
                    null, RelatedBillType.SALE_RETURN, null, null, "DEFECT");
        }
        deliveryMapper.deleteById(id);
        return R.ok();
    }
}
