package com.beichen.erp.outsource.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beichen.erp.common.R;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.inventory.service.InventoryWarehouseStockService;
import com.beichen.erp.outsource.entity.OutsourceOrder;
import com.beichen.erp.outsource.entity.OutsourceOrderDelivery;
import com.beichen.erp.outsource.entity.OutsourceOrderProduct;
import com.beichen.erp.outsource.entity.OutsourceWarehouse;
import com.beichen.erp.outsource.entity.OutsourceWarehouseStock;
import com.beichen.erp.outsource.mapper.OutsourceOrderDeliveryMapper;
import com.beichen.erp.outsource.mapper.OutsourceMaterialMapper;
import com.beichen.erp.outsource.mapper.OutsourceWarehouseMapper;
import com.beichen.erp.outsource.mapper.OutsourceWarehouseStockMapper;
import com.beichen.erp.outsource.service.OutsourceOrderService;
import com.beichen.erp.dev.entity.Bom;
import com.beichen.erp.dev.mapper.BomMapper;
import com.beichen.erp.material.entity.Material;
import com.beichen.erp.material.mapper.MaterialMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Slf4j
@RestController
@RequestMapping("/api/outsource/order-delivery")
@RequiredArgsConstructor
public class OrderDeliveryController {

    private final OutsourceOrderDeliveryMapper deliveryMapper;
    private final OutsourceOrderService orderService;
    private final InventoryWarehouseStockService stockService;
    private final OutsourceMaterialMapper outsourceMaterialMapper;
    private final BomMapper bomMapper;
    private final MaterialMapper materialMapper;
    private final OutsourceWarehouseMapper warehouseMapper;
    private final OutsourceWarehouseStockMapper warehouseStockMapper;

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
    @Transactional(rollbackFor = Exception.class)
    public R<Void> create(@RequestBody OutsourceOrderDelivery delivery) {
        if (delivery.getOrderId() == null) throw new BusinessException("加工单ID不能为空");
        OutsourceOrder order = orderService.getById(delivery.getOrderId());
        if (order == null) throw new BusinessException("加工单不存在");
        if (!"生产中".equals(order.getStatus())) throw new BusinessException("只有生产中的加工单可录入交货");
        if (delivery.getProductName() == null || delivery.getProductName().isBlank())
            throw new BusinessException("产品名称不能为空");
        if (delivery.getQuantity() == null || delivery.getQuantity().compareTo(BigDecimal.ZERO) <= 0)
            throw new BusinessException("交货数量必须大于0");

        // 查该加工单对应产品
        List<OutsourceOrderProduct> products = orderService.getProducts(delivery.getOrderId());
        OutsourceOrderProduct matchedProduct = products.stream()
            .filter(p -> delivery.getProductName().equals(p.getProductName()))
            .findFirst().orElse(null);
        if (matchedProduct == null) throw new BusinessException("加工单中未找到该产品");

        // 计算并扣除委外仓库物料
        deductOutsourceMaterials(order, matchedProduct, delivery.getQuantity(), delivery.getProductName());

        deliveryMapper.insert(delivery);
        // 选择了收货仓库则自动入库（带 materialId 关联）
        if (delivery.getWarehouseId() != null) {
            Long productMaterialId = null;
            Material material = materialMapper.selectOne(
                new LambdaQueryWrapper<Material>().eq(Material::getName, delivery.getProductName()));
            if (material != null) productMaterialId = material.getId();
            stockService.changeStock(delivery.getWarehouseId(), delivery.getProductName(),
                delivery.getQuantity(), "委外交货入库", null, null, productMaterialId, null);
        }
        return R.ok();
    }

    /** 根据产品BOM(dev_bom)扣除委外仓库物料，不足则抛异常 */
    private void deductOutsourceMaterials(OutsourceOrder order, OutsourceOrderProduct product,
                                          BigDecimal deliveryQty, String productName) {
        // 1. 通过产品的 projectId 查询 dev_bom 表
        Long projectId = product.getProjectId();
        if (projectId == null) {
            log.warn("产品「{}」无关联项目ID，跳过物料扣除", productName);
            return;
        }

        List<Bom> bomList = bomMapper.selectList(
            new LambdaQueryWrapper<Bom>()
                .eq(Bom::getProjectId, projectId));
        if (bomList.isEmpty()) {
            log.info("项目(ID={})无BOM物料，跳过物料扣除", projectId);
            return;
        }
        log.info("产品「{}」(项目ID={})找到{}条BOM物料，开始校验扣除", productName, projectId, bomList.size());

        // 2. 找到该工厂的委外仓库
        List<OutsourceWarehouse> warehouses = warehouseMapper.selectList(
            new LambdaQueryWrapper<OutsourceWarehouse>()
                .eq(OutsourceWarehouse::getFactoryId, order.getFactoryId()));
        if (warehouses.isEmpty()) {
            throw new BusinessException("该加工厂未配置委外仓库，无法扣除物料");
        }
        Long warehouseId = warehouses.get(0).getId();

        // 3. 检查每个BOM物料的库存是否充足
        StringBuilder shortageMsg = new StringBuilder();
        for (Bom bom : bomList) {
            if (bom.getMaterialName() == null || bom.getMaterialName().isBlank()) continue;
            // 单套用量 = quantityPerSet（不考虑损耗率）
            BigDecimal perUnit = bom.getQuantityPerSet() != null ? bom.getQuantityPerSet() : BigDecimal.ZERO;
            BigDecimal needed = perUnit.multiply(deliveryQty);

            // 通过物料名查找委外物料ID
            Long materialId = outsourceMaterialMapper.findIdByName(bom.getMaterialName());
            if (materialId == null) {
                log.warn("BOM物料「{}」在委外物料表中未找到，跳过", bom.getMaterialName());
                continue;
            }

            // 查仓库库存（仅扣良品）
            OutsourceWarehouseStock stock = warehouseStockMapper.selectOne(
                new LambdaQueryWrapper<OutsourceWarehouseStock>()
                    .eq(OutsourceWarehouseStock::getWarehouseId, warehouseId)
                    .eq(OutsourceWarehouseStock::getMaterialId, materialId)
                    .eq(OutsourceWarehouseStock::getQualityType, "良品"));
            BigDecimal currentStock = stock != null && stock.getQuantity() != null ? stock.getQuantity() : BigDecimal.ZERO;

            log.info("BOM校验: {} 需要={}, 库存={}", bom.getMaterialName(), needed.setScale(2, RoundingMode.HALF_UP), currentStock.setScale(2, RoundingMode.HALF_UP));

            if (currentStock.compareTo(needed) < 0) {
                if (shortageMsg.length() > 0) shortageMsg.append("；");
                shortageMsg.append(bom.getMaterialName())
                    .append(" 需要").append(needed.setScale(2, RoundingMode.HALF_UP))
                    .append("，库存仅").append(currentStock.setScale(2, RoundingMode.HALF_UP));
            }
        }
        if (shortageMsg.length() > 0) {
            throw new BusinessException("物料库存不足：" + shortageMsg.toString());
        }

        // 4. 全部检查通过，执行扣减
        for (Bom bom : bomList) {
            if (bom.getMaterialName() == null || bom.getMaterialName().isBlank()) continue;
            BigDecimal perUnit = bom.getQuantityPerSet() != null ? bom.getQuantityPerSet() : BigDecimal.ZERO;
            BigDecimal needed = perUnit.multiply(deliveryQty);

            Long materialId = outsourceMaterialMapper.findIdByName(bom.getMaterialName());
            if (materialId == null) continue;

            OutsourceWarehouseStock stock = warehouseStockMapper.selectOne(
                new LambdaQueryWrapper<OutsourceWarehouseStock>()
                    .eq(OutsourceWarehouseStock::getWarehouseId, warehouseId)
                    .eq(OutsourceWarehouseStock::getMaterialId, materialId)
                    .eq(OutsourceWarehouseStock::getQualityType, "良品"));
            if (stock == null) {
                stock = new OutsourceWarehouseStock();
                stock.setWarehouseId(warehouseId);
                stock.setMaterialId(materialId);
                stock.setQualityType("良品");
                stock.setQuantity(needed.negate());
                warehouseStockMapper.insert(stock);
            } else {
                stock.setQuantity(stock.getQuantity().subtract(needed));
                warehouseStockMapper.updateById(stock);
            }
            log.info("扣减物料: {} x{} (仓库ID={}, 物料ID={})",
                bom.getMaterialName(), needed.setScale(2, RoundingMode.HALF_UP), warehouseId, materialId);
        }
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
