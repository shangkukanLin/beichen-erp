package com.beichen.erp.outsource.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beichen.erp.common.R;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.inventory.entity.InventoryWarehouseStock;
import com.beichen.erp.inventory.mapper.InventoryWarehouseStockMapper;
import com.beichen.erp.inventory.service.InventoryWarehouseStockService;
import com.beichen.erp.outsource.entity.OutsourceOrder;
import com.beichen.erp.outsource.entity.OutsourceOrderDelivery;
import com.beichen.erp.outsource.entity.OutsourceOrderMaterial;
import com.beichen.erp.outsource.entity.OutsourceOrderProduct;
import com.beichen.erp.outsource.entity.OutsourceMaterial;
import com.beichen.erp.outsource.entity.OutsourceStockLog;
import com.beichen.erp.outsource.entity.OutsourceWarehouse;
import com.beichen.erp.outsource.entity.OutsourceWarehouseStock;
import com.beichen.erp.outsource.mapper.OutsourceOrderDeliveryMapper;
import com.beichen.erp.outsource.mapper.OutsourceMaterialMapper;
import com.beichen.erp.outsource.mapper.OutsourceStockLogMapper;
import com.beichen.erp.outsource.mapper.OutsourceWarehouseMapper;
import com.beichen.erp.outsource.mapper.OutsourceWarehouseStockMapper;
import com.beichen.erp.outsource.mapper.OutsourceStockLogMapper;
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
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/outsource/order-delivery")
@RequiredArgsConstructor
public class OrderDeliveryController {

    private final OutsourceOrderDeliveryMapper deliveryMapper;
    private final OutsourceOrderService orderService;
    private final InventoryWarehouseStockService stockService;
    private final InventoryWarehouseStockMapper inventoryStockMapper;
    private final OutsourceMaterialMapper outsourceMaterialMapper;
    private final BomMapper bomMapper;
    private final MaterialMapper materialMapper;
    private final OutsourceWarehouseMapper warehouseMapper;
    private final OutsourceWarehouseStockMapper warehouseStockMapper;
    private final OutsourceStockLogMapper stockLogMapper;

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

        List<Map<String, Object>> productStats = new ArrayList<>();
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
    public R<Map<String, Object>> create(@RequestBody OutsourceOrderDelivery delivery,
                                         @RequestParam(defaultValue = "false") boolean forceDelivery) {
        log.info("新增交货: orderId={}, product={}, qty={}, warehouseId={}, force={}",
                delivery.getOrderId(), delivery.getProductName(), delivery.getQuantity(),
                delivery.getWarehouseId(), forceDelivery);
        if (delivery.getOrderId() == null) throw new BusinessException("加工单ID不能为空");
        OutsourceOrder order = orderService.getById(delivery.getOrderId());
        if (order == null) throw new BusinessException("加工单不存在");
        if (!"生产中".equals(order.getStatus())) throw new BusinessException("只有生产中的加工单可录入交货");
        if (delivery.getProductName() == null || delivery.getProductName().isBlank())
            throw new BusinessException("产品名称不能为空");
        if (delivery.getQuantity() == null || delivery.getQuantity().compareTo(BigDecimal.ZERO) <= 0)
            throw new BusinessException("交货数量必须大于0");

        List<OutsourceOrderProduct> products = orderService.getProducts(delivery.getOrderId());
        log.info("加工单产品列表: {}", products.stream()
            .map(p -> "name=" + p.getProductName() + ",id=" + p.getId() + ",projectId=" + p.getProjectId() + ",qty=" + p.getQuantity())
            .toList());
        OutsourceOrderProduct matchedProduct = products.stream()
            .filter(p -> delivery.getProductName().equals(p.getProductName()))
            .findFirst().orElse(null);
        if (matchedProduct == null) throw new BusinessException("加工单中未找到该产品");
        log.info("匹配产品: id={}, projectId={}, quantity={}", matchedProduct.getId(), matchedProduct.getProjectId(), matchedProduct.getQuantity());

        // 加载物料需求
        List<MaterialReq> materialReqs = loadMaterialRequirements(matchedProduct);
        log.info("物料需求: {} 项", materialReqs.size());

        // 检查物料库存，返回缺料列表
        List<Map<String, Object>> shortages = checkMaterialShortages(order, matchedProduct, delivery.getQuantity());
        log.info("物料短缺检查结果: {} 项", shortages.size());

        if (!shortages.isEmpty() && !forceDelivery) {
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("canProceed", false);
            resp.put("shortages", shortages);
            resp.put("message", buildShortageMessage(shortages));
            return R.ok(resp);
        }

        // 扣除物料（forceDelivery 时允许负数）
        applyMaterialDeduction(order, matchedProduct, delivery.getQuantity(), delivery.getProductName());

        deliveryMapper.insert(delivery);
        log.info("交货记录已保存: id={}", delivery.getId());
        // 选择了收货仓库则自动入库
        if (delivery.getWarehouseId() != null) {
            addInventoryStock(delivery);
            log.info("成品库存已入库: warehouseId={}, qty={}", delivery.getWarehouseId(), delivery.getQuantity());
        } else {
            log.warn("未选择收货仓库，跳过入库");
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("canProceed", true);
        return R.ok(resp);
    }

    /** 修改交货记录 — 回滚旧库存 + 应用新库存 */
    @PutMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public R<Map<String, Object>> update(@PathVariable Long id, @RequestBody OutsourceOrderDelivery delivery,
                                         @RequestParam(defaultValue = "false") boolean forceDelivery) {
        OutsourceOrderDelivery old = deliveryMapper.selectById(id);
        if (old == null) throw new BusinessException("交货记录不存在");

        OutsourceOrder order = orderService.getById(old.getOrderId());
        if (order == null) throw new BusinessException("加工单不存在");

        List<OutsourceOrderProduct> products = orderService.getProducts(old.getOrderId());
        OutsourceOrderProduct matchedProduct = products.stream()
            .filter(p -> delivery.getProductName().equals(p.getProductName()))
            .findFirst().orElse(null);
        if (matchedProduct == null) throw new BusinessException("加工单中未找到该产品");

        // 回滚旧的库存影响
        revertDeliveryStock(order, old);

        // 检查新库存
        List<Map<String, Object>> shortages = checkMaterialShortages(order, matchedProduct, delivery.getQuantity());

        if (!shortages.isEmpty() && !forceDelivery) {
            // 库存不足，需要把旧库存加回去（事务自动回滚）
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("canProceed", false);
            resp.put("shortages", shortages);
            resp.put("message", buildShortageMessage(shortages));
            return R.ok(resp);
        }

        // 应用新库存
        applyMaterialDeduction(order, matchedProduct, delivery.getQuantity(), delivery.getProductName());
        // 回滚旧入库 + 添加新入库
        if (old.getWarehouseId() != null) {
            revertInventoryStock(old);
        }
        delivery.setId(id);
        deliveryMapper.updateById(delivery);
        if (delivery.getWarehouseId() != null) {
            addInventoryStock(delivery);
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("canProceed", true);
        return R.ok(resp);
    }

    /** 删除交货记录 — 回滚库存 */
    @DeleteMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> delete(@PathVariable Long id) {
        OutsourceOrderDelivery old = deliveryMapper.selectById(id);
        if (old == null) throw new BusinessException("交货记录不存在");
        OutsourceOrder order = orderService.getById(old.getOrderId());
        if (order != null) {
            revertDeliveryStock(order, old);
        }
        deliveryMapper.deleteById(id);
        return R.ok();
    }

    /** 退不良：拆分产品为BOM物料还回工厂委外仓库 */
    @PostMapping("/return-defect/{orderId}")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> returnDefect(@PathVariable Long orderId, @RequestBody Map<String, Object> body) {
        log.info("退不良: orderId={}, body={}", orderId, body);
        OutsourceOrder order = orderService.getById(orderId);
        if (order == null) throw new BusinessException("加工单不存在");
        if (!"生产中".equals(order.getStatus()) && !"已完成".equals(order.getStatus()))
            throw new BusinessException("只有生产中或已完成的加工单可退不良");

        String productName = (String) body.get("productName");
        BigDecimal defectQty = new BigDecimal(body.get("quantity").toString());

        // 匹配产品
        List<OutsourceOrderProduct> products = orderService.getProducts(orderId);
        OutsourceOrderProduct matchedProduct = products.stream()
            .filter(p -> productName.equals(p.getProductName()))
            .findFirst().orElse(null);
        if (matchedProduct == null) throw new BusinessException("加工单中未找到该产品");

        // 校验退不良数量不超过已交数量
        List<OutsourceOrderDelivery> allDeliveries = deliveryMapper.selectList(
            new LambdaQueryWrapper<OutsourceOrderDelivery>().eq(OutsourceOrderDelivery::getOrderId, orderId));
        BigDecimal deliveredQty = allDeliveries.stream()
            .filter(d -> productName.equals(d.getProductName()))
            .map(d -> d.getQuantity() != null ? d.getQuantity() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (defectQty.compareTo(deliveredQty) > 0)
            throw new BusinessException("退不良数量(" + defectQty + ")不能超过已交数量(" + deliveredQty + ")");

        // 确定委外仓库
        Long whId = resolveOutsourceWarehouseId(order);
        if (whId == null) throw new BusinessException("工厂无委外仓库");

        // 1. 创建退不良交货记录
        OutsourceOrderDelivery delivery = new OutsourceOrderDelivery();
        delivery.setOrderId(orderId);
        delivery.setProductName(productName);
        delivery.setQuantity(defectQty.negate());
        delivery.setDeliveryType("退不良");
        delivery.setDeliveryDate(LocalDate.now());
        delivery.setRemark("退不良");
        deliveryMapper.insert(delivery);

        // 2. 拆BOM → 物料还回工厂委外仓库
        List<MaterialReq> materials = loadMaterialRequirements(matchedProduct);
        for (MaterialReq mat : materials) {
            if (mat.materialId() == null) continue;
            BigDecimal restoreQty = mat.perUnit().multiply(defectQty);

            OutsourceWarehouseStock stock = warehouseStockMapper.selectOne(
                new LambdaQueryWrapper<OutsourceWarehouseStock>()
                    .eq(OutsourceWarehouseStock::getWarehouseId, whId)
                    .eq(OutsourceWarehouseStock::getMaterialId, mat.materialId())
                    .eq(OutsourceWarehouseStock::getQualityType, "良品"));
            BigDecimal before = stock != null && stock.getQuantity() != null ? stock.getQuantity() : BigDecimal.ZERO;
            if (stock == null) {
                stock = new OutsourceWarehouseStock();
                stock.setWarehouseId(whId);
                stock.setMaterialId(mat.materialId());
                stock.setQualityType("良品");
                stock.setQuantity(restoreQty);
                warehouseStockMapper.insert(stock);
            } else {
                stock.setQuantity(before.add(restoreQty));
                warehouseStockMapper.updateById(stock);
            }
            writeStockLog(whId, mat.materialId(), mat.materialName(), "退不良还料",
                    restoreQty, before, before.add(restoreQty), order.getCode());
            log.info("退不良还料: {} +{} (仓库ID={})", mat.materialName(), restoreQty, whId);
        }

        return R.ok();
    }

    // ==================== 私有辅助方法 ====================

    /** 物料需求信息 */
    private record MaterialReq(Long materialId, String materialName, BigDecimal perUnit) {}

    /**
     * 加载产品的物料需求列表。
     * 优先使用订单保存的 OutsourceOrderMaterial（不依赖 projectId），
     * 如果没有则回退到 BOM 查询。
     */
    private List<MaterialReq> loadMaterialRequirements(OutsourceOrderProduct product) {
        List<MaterialReq> result = new ArrayList<>();

        // 1. 优先使用订单保存的物料
        List<OutsourceOrderMaterial> orderMaterials = orderService.getMaterials(product.getId());
        if (!orderMaterials.isEmpty()) {
            BigDecimal productQty = product.getQuantity() != null && product.getQuantity().compareTo(BigDecimal.ZERO) != 0
                    ? product.getQuantity() : BigDecimal.ONE;
            for (OutsourceOrderMaterial mat : orderMaterials) {
                if (mat.getMaterialName() == null || mat.getMaterialName().isBlank()) continue;
                BigDecimal perUnit = mat.getDemandQuantity() != null
                        ? mat.getDemandQuantity().divide(productQty, 6, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
                Long materialId = mat.getMaterialId();
                if (materialId == null) {
                    materialId = resolveOrCreateMaterialId(mat.getMaterialName(), mat.getMaterialType(), mat.getUnit());
                }
                result.add(new MaterialReq(materialId, mat.getMaterialName(), perUnit));
            }
            log.info("从订单物料加载 {} 项 (productId={})", result.size(), product.getId());
            return result;
        }

        // 2. 回退：通过 projectId 查询 BOM
        Long projectId = product.getProjectId();
        if (projectId == null) {
            log.warn("产品「{}」无订单物料且 projectId 为空，无法计算物料需求", product.getProductName());
            return result;
        }
        List<Bom> bomList = bomMapper.selectList(
            new LambdaQueryWrapper<Bom>().eq(Bom::getProjectId, projectId));
        for (Bom bom : bomList) {
            if (bom.getMaterialName() == null || bom.getMaterialName().isBlank()) continue;
            BigDecimal perUnit = bom.getQuantityPerSet() != null ? bom.getQuantityPerSet() : BigDecimal.ZERO;
            Long materialId = resolveOrCreateMaterialId(bom.getMaterialName(), bom.getMaterialType(), bom.getUnit());
            result.add(new MaterialReq(materialId, bom.getMaterialName(), perUnit));
        }
        log.info("从 BOM 加载 {} 项 (projectId={})", result.size(), projectId);
        return result;
    }

    /**
     * 按名称查找委外物料ID，如果不存在则自动创建。
     * 这样即使 BOM 中的物料未在委外物料表中登记，也能正常扣减库存。
     */
    private Long resolveOrCreateMaterialId(String name, String type, String unit) {
        Long id = outsourceMaterialMapper.findIdByName(name);
        if (id != null) return id;
        // 自动创建物料记录
        OutsourceMaterial mat = new OutsourceMaterial();
        mat.setMaterialName(name);
        mat.setMaterialType(type);
        mat.setUnit(unit);
        mat.setStatus(1);
        outsourceMaterialMapper.insert(mat);
        log.info("自动创建委外物料: name={}, id={}", name, mat.getId());
        return mat.getId();
    }

    /** 查找工厂的委外仓库 ID */
    private Long resolveOutsourceWarehouseId(OutsourceOrder order) {
        if (order.getFactoryId() == null) return null;
        List<OutsourceWarehouse> warehouses = warehouseMapper.selectList(
            new LambdaQueryWrapper<OutsourceWarehouse>()
                .eq(OutsourceWarehouse::getFactoryId, order.getFactoryId()));
        if (warehouses.isEmpty()) {
            log.warn("工厂(ID={})无委外仓库", order.getFactoryId());
            return null;
        }
        return warehouses.get(0).getId();
    }

    /** 检查物料短缺情况，返回缺料列表 */
    private List<Map<String, Object>> checkMaterialShortages(OutsourceOrder order, OutsourceOrderProduct product,
                                                              BigDecimal deliveryQty) {
        List<Map<String, Object>> shortages = new ArrayList<>();
        List<MaterialReq> materials = loadMaterialRequirements(product);
        if (materials.isEmpty()) return shortages;

        Long whId = resolveOutsourceWarehouseId(order);
        if (whId == null) return shortages;

        for (MaterialReq mat : materials) {
            if (mat.materialId() == null) {
                log.warn("物料「{}」在委外物料表中未找到，跳过库存检查", mat.materialName());
                continue;
            }
            BigDecimal needed = mat.perUnit().multiply(deliveryQty);

            OutsourceWarehouseStock stock = warehouseStockMapper.selectOne(
                new LambdaQueryWrapper<OutsourceWarehouseStock>()
                    .eq(OutsourceWarehouseStock::getWarehouseId, whId)
                    .eq(OutsourceWarehouseStock::getMaterialId, mat.materialId())
                    .eq(OutsourceWarehouseStock::getQualityType, "良品"));
            BigDecimal currentStock = stock != null && stock.getQuantity() != null ? stock.getQuantity() : BigDecimal.ZERO;

            if (currentStock.compareTo(needed) < 0) {
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("materialName", mat.materialName());
                s.put("needed", needed.setScale(2, RoundingMode.HALF_UP));
                s.put("stock", currentStock.setScale(2, RoundingMode.HALF_UP));
                s.put("gap", needed.subtract(currentStock).setScale(2, RoundingMode.HALF_UP));
                shortages.add(s);
            }
        }
        return shortages;
    }

    /** 构建缺料提示信息 */
    private String buildShortageMessage(List<Map<String, Object>> shortages) {
        StringBuilder sb = new StringBuilder("以下物料库存不足：");
        for (Map<String, Object> s : shortages) {
            sb.append("\n  - ").append(s.get("materialName"))
              .append(" 需要").append(s.get("needed"))
              .append("，库存仅").append(s.get("stock"))
              .append("，缺口").append(s.get("gap"));
        }
        sb.append("\n\n是否确认继续出库？（物料将变为负数）");
        return sb.toString();
    }

    /** 执行物料扣减（允许负数） */
    private void applyMaterialDeduction(OutsourceOrder order, OutsourceOrderProduct product,
                                        BigDecimal deliveryQty, String productName) {
        List<MaterialReq> materials = loadMaterialRequirements(product);
        if (materials.isEmpty()) {
            log.warn("产品「{}」无物料需求，跳过物料扣除", productName);
            return;
        }
        Long whId = resolveOutsourceWarehouseId(order);
        if (whId == null) {
            log.warn("无法确定委外仓库，跳过物料扣除 (factoryId={})", order.getFactoryId());
            return;
        }

        for (MaterialReq mat : materials) {
            if (mat.materialId() == null) {
                log.warn("物料「{}」在委外物料表中未找到，跳过扣减", mat.materialName());
                continue;
            }
            BigDecimal needed = mat.perUnit().multiply(deliveryQty);
            OutsourceWarehouseStock stock = warehouseStockMapper.selectOne(
                new LambdaQueryWrapper<OutsourceWarehouseStock>()
                    .eq(OutsourceWarehouseStock::getWarehouseId, whId)
                    .eq(OutsourceWarehouseStock::getMaterialId, mat.materialId())
                    .eq(OutsourceWarehouseStock::getQualityType, "良品"));
            BigDecimal before = stock != null && stock.getQuantity() != null ? stock.getQuantity() : BigDecimal.ZERO;
            BigDecimal after;
            if (stock == null) {
                stock = new OutsourceWarehouseStock();
                stock.setWarehouseId(whId);
                stock.setMaterialId(mat.materialId());
                stock.setQualityType("良品");
                after = needed.negate();
                stock.setQuantity(after);
                warehouseStockMapper.insert(stock);
            } else {
                after = stock.getQuantity().subtract(needed);
                stock.setQuantity(after);
                warehouseStockMapper.updateById(stock);
            }
            writeStockLog(whId, mat.materialId(), mat.materialName(), "出货扣料",
                    needed.negate(), before, after, order.getCode());
            log.info("扣减物料: {} x{} (仓库ID={})", mat.materialName(), needed.setScale(2, RoundingMode.HALF_UP), whId);
        }
    }

    /** 回滚交货记录的物料扣减（加回委外仓库） */
    private void revertDeliveryStock(OutsourceOrder order, OutsourceOrderDelivery delivery) {
        List<OutsourceOrderProduct> products = orderService.getProducts(delivery.getOrderId());
        OutsourceOrderProduct matchedProduct = products.stream()
            .filter(p -> delivery.getProductName().equals(p.getProductName()))
            .findFirst().orElse(null);
        if (matchedProduct == null) return;

        List<MaterialReq> materials = loadMaterialRequirements(matchedProduct);
        if (materials.isEmpty()) return;

        Long whId = resolveOutsourceWarehouseId(order);
        if (whId == null) return;

        BigDecimal oldQty = delivery.getQuantity() != null ? delivery.getQuantity() : BigDecimal.ZERO;
        for (MaterialReq mat : materials) {
            if (mat.materialId() == null) continue;
            BigDecimal toRestore = mat.perUnit().multiply(oldQty);

            OutsourceWarehouseStock stock = warehouseStockMapper.selectOne(
                new LambdaQueryWrapper<OutsourceWarehouseStock>()
                    .eq(OutsourceWarehouseStock::getWarehouseId, whId)
                    .eq(OutsourceWarehouseStock::getMaterialId, mat.materialId())
                    .eq(OutsourceWarehouseStock::getQualityType, "良品"));
            if (stock != null) {
                BigDecimal before = stock.getQuantity();
                BigDecimal after = before.add(toRestore);
                stock.setQuantity(after);
                warehouseStockMapper.updateById(stock);
                writeStockLog(whId, mat.materialId(), mat.materialName(), "出货扣料-回滚",
                        toRestore, before, after, order.getCode());
                log.info("回滚物料: {} +{}", mat.materialName(), toRestore.setScale(2, RoundingMode.HALF_UP));
            }
        }
        // 回滚收货入库
        if (delivery.getWarehouseId() != null) {
            revertInventoryStock(delivery);
        }
    }

    /** 增加收货入库库存 */
    private void addInventoryStock(OutsourceOrderDelivery delivery) {
        log.info("开始入库: warehouseId={}, productName={}, qty={}",
                delivery.getWarehouseId(), delivery.getProductName(), delivery.getQuantity());
        Long productMaterialId = null;
        Material material = materialMapper.selectOne(
            new LambdaQueryWrapper<Material>().eq(Material::getName, delivery.getProductName()));
        if (material != null) {
            productMaterialId = material.getId();
            log.info("找到对应物料记录: materialId={}", productMaterialId);
        } else {
            log.info("物料表中未找到「{}」，将按 productName 入库", delivery.getProductName());
        }
        stockService.changeStock(delivery.getWarehouseId(), delivery.getProductName(),
            delivery.getQuantity(), "委外交货入库", null, null, productMaterialId, null);
        log.info("入库完成: warehouseId={}, productName={}, qty={}", delivery.getWarehouseId(), delivery.getProductName(), delivery.getQuantity());
    }

    /** 回滚收货入库，直接扣减库存，允许扣到零以下 */
    private void revertInventoryStock(OutsourceOrderDelivery delivery) {
        Long materialId = null;
        Material material = materialMapper.selectOne(
            new LambdaQueryWrapper<Material>().eq(Material::getName, delivery.getProductName()));
        if (material != null) materialId = material.getId();

        LambdaQueryWrapper<InventoryWarehouseStock> w = new LambdaQueryWrapper<InventoryWarehouseStock>()
                .eq(InventoryWarehouseStock::getWarehouseId, delivery.getWarehouseId());
        if (materialId != null) {
            w.eq(InventoryWarehouseStock::getMaterialId, materialId);
        } else {
            w.eq(InventoryWarehouseStock::getProductName, delivery.getProductName());
        }
        InventoryWarehouseStock stock = inventoryStockMapper.selectOne(w);
        if (stock != null) {
            stock.setQuantity(stock.getQuantity().subtract(delivery.getQuantity()));
            inventoryStockMapper.updateById(stock);
            log.info("回滚成品库存: warehouseId={}, product={}, qty=-{}",
                    delivery.getWarehouseId(), delivery.getProductName(), delivery.getQuantity());
        }
    }

    /** 写入库存流水日志（变更前/后数量） */
    private void writeStockLog(Long whId, Long matId, String matName, String changeType,
                               BigDecimal changeQty, BigDecimal before, BigDecimal after,
                               String orderCode) {
        OutsourceStockLog record = new OutsourceStockLog();
        record.setWarehouseId(whId);
        record.setMaterialId(matId);
        record.setMaterialName(matName);
        record.setChangeType(changeType);
        record.setChangeQuantity(changeQty);
        record.setBeforeQuantity(before);
        record.setAfterQuantity(after);
        record.setRelatedOrderCode(orderCode);
        stockLogMapper.insert(record);
    }
}
