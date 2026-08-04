package com.beichen.erp.outsource.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beichen.erp.common.R;
import com.beichen.erp.common.DocStatus;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.finance.service.PayableHelper;
import com.beichen.erp.inventory.entity.InventoryWarehouseStock;
import com.beichen.erp.inventory.mapper.InventoryWarehouseStockMapper;
import com.beichen.erp.inventory.common.RelatedBillType;
import com.beichen.erp.inventory.common.StockChangeType;
import com.beichen.erp.outsource.common.DeliveryType;
import com.beichen.erp.outsource.common.OutsourceOrderStatus;
import com.beichen.erp.outsource.common.QualityType;
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
import com.beichen.erp.material.entity.Product;
import com.beichen.erp.material.mapper.ProductMapper;
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
    private final ProductMapper productMapper;
    private final OutsourceWarehouseMapper warehouseMapper;
    private final OutsourceWarehouseStockMapper warehouseStockMapper;
    private final OutsourceStockLogMapper stockLogMapper;
    private final PayableHelper payableHelper;

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
                    .filter(d -> p.getId().equals(d.getProductId()))
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
        if (!OutsourceOrderStatus.PRODUCING.name().equals(order.getStatus())) throw new BusinessException("只有生产中的加工单可录入交货");
        if (delivery.getProductName() == null || delivery.getProductName().isBlank())
            throw new BusinessException("产品名称不能为空");
        if (delivery.getQuantity() == null || delivery.getQuantity().compareTo(BigDecimal.ZERO) <= 0)
            throw new BusinessException("交货数量必须大于0");

        List<OutsourceOrderProduct> products = orderService.getProducts(delivery.getOrderId());
        log.info("加工单产品列表: {}", products.stream()
            .map(p -> "name=" + p.getProductName() + ",id=" + p.getId() + ",projectId=" + p.getProjectId() + ",qty=" + p.getQuantity())
            .toList());
        OutsourceOrderProduct matchedProduct = products.stream()
            .filter(p -> delivery.getProductId() != null && delivery.getProductId().equals(p.getId()))
            .findFirst().orElse(null);
        if (matchedProduct == null && delivery.getProductName() != null) {
            matchedProduct = products.stream()
                .filter(p -> delivery.getProductId() != null && delivery.getProductId().equals(p.getId()))
                .findFirst().orElse(null);
            if (matchedProduct != null) delivery.setProductId(matchedProduct.getId());
        }
        if (matchedProduct == null) throw new BusinessException("加工单中未找到该产品");
        delivery.setProductId(matchedProduct.getId());
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

        // 草稿态存盘，不扣物料/不入库存/不生成应付，审核通过后由 audit() 统一落账
        delivery.setIsReverse(false);
        delivery.setStatus(DocStatus.DRAFT.name());
        deliveryMapper.insert(delivery);
        log.info("交货记录已保存(草稿): id={}", delivery.getId());
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("canProceed", true);
        resp.put("draft", true);
        return R.ok(resp);
    }

    /** 审核交货记录：草稿态生效，扣减物料、成品入库、生成应付（退不良则为冲销） */
    @PutMapping("/{id}/audit")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> audit(@PathVariable Long id) {
        OutsourceOrderDelivery delivery = deliveryMapper.selectById(id);
        if (delivery == null) throw new BusinessException("交货记录不存在");
        if (!DocStatus.DRAFT.name().equals(delivery.getStatus())) throw new BusinessException("仅草稿状态可以审核");
        OutsourceOrder order = orderService.getById(delivery.getOrderId());
        if (order == null) throw new BusinessException("加工单不存在");

        if (Boolean.TRUE.equals(delivery.getIsReverse())) {
            // 退不良审核：扣成品库存 + BOM还料 + 冲减应付
            applyDefectStock(order, delivery);
        } else {
            // 普通交货审核：扣物料 + 成品入库 + 生成应付
            OutsourceOrderProduct matchedProduct = orderService.getProducts(delivery.getOrderId()).stream()
                .filter(p -> delivery.getProductId() != null && delivery.getProductId().equals(p.getId()))
                .findFirst().orElse(null);
            if (matchedProduct == null) throw new BusinessException("加工单中未找到该产品");
            applyMaterialDeduction(order, matchedProduct, delivery.getQuantity(), delivery.getProductName());
            if (delivery.getWarehouseId() != null) {
                addInventoryStock(delivery);
            }
            createDeliveryPayable(order, matchedProduct, delivery);
        }
        OutsourceOrderDelivery upd = new OutsourceOrderDelivery();
        upd.setId(id);
        upd.setStatus(DocStatus.AUDITED.name());
        deliveryMapper.updateById(upd);
        return R.ok();
    }

    /** 反审核交货记录：已审核态回滚库存/应付，回到草稿 */
    @PutMapping("/{id}/unaudit")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> unaudit(@PathVariable Long id) {
        OutsourceOrderDelivery delivery = deliveryMapper.selectById(id);
        if (delivery == null) throw new BusinessException("交货记录不存在");
        if (!DocStatus.AUDITED.name().equals(delivery.getStatus())) throw new BusinessException("仅已审核状态可以反审核");
        OutsourceOrder order = orderService.getById(delivery.getOrderId());
        if (order == null) throw new BusinessException("加工单不存在");

        if (Boolean.TRUE.equals(delivery.getIsReverse())) {
            revertDefectStock(order, delivery);
        } else {
            revertDeliveryStock(order, delivery);
        }
        // 同步删除应付（已付款的会被阻止并抛异常）
        payableHelper.deleteBySourceId(id);
        OutsourceOrderDelivery upd = new OutsourceOrderDelivery();
        upd.setId(id);
        upd.setStatus(DocStatus.DRAFT.name());
        deliveryMapper.updateById(upd);
        return R.ok();
    }

    /** 交货生成应付 */
    private void createDeliveryPayable(OutsourceOrder order, OutsourceOrderProduct product, OutsourceOrderDelivery delivery) {
        BigDecimal price = product.getUnitPrice() != null ? product.getUnitPrice() : BigDecimal.ZERO;
        BigDecimal amount = delivery.getQuantity().multiply(price);
        if (amount.compareTo(BigDecimal.ZERO) == 0) return;
        payableHelper.createPayable(order.getFactoryId(), "委外加工交货", order.getCode(), delivery.getId(),
            amount, delivery.getDeliveryDate() != null ? delivery.getDeliveryDate() : LocalDate.now(),
            "交货 - " + order.getCode() + " - " + delivery.getProductName());
    }

    /** 修改交货记录 — 回滚旧库存 + 应用新库存 */
    @PutMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public R<Map<String, Object>> update(@PathVariable Long id, @RequestBody OutsourceOrderDelivery delivery,
                                         @RequestParam(defaultValue = "false") boolean forceDelivery) {
        OutsourceOrderDelivery old = deliveryMapper.selectById(id);
        if (old == null) throw new BusinessException("交货记录不存在");
        if (!DocStatus.DRAFT.name().equals(old.getStatus())) {
            throw new BusinessException("仅草稿状态可编辑，已审核记录请先反审核");
        }

        OutsourceOrder order = orderService.getById(old.getOrderId());
        if (order == null) throw new BusinessException("加工单不存在");

        List<OutsourceOrderProduct> products = orderService.getProducts(old.getOrderId());
        OutsourceOrderProduct matchedProduct = products.stream()
            .filter(p -> delivery.getProductId() != null && delivery.getProductId().equals(p.getId()))
            .findFirst().orElse(null);
        if (matchedProduct == null) throw new BusinessException("加工单中未找到该产品");

        // 草稿态编辑不触碰库存，仅更新记录本身与审核状态（保持草稿）
        delivery.setId(id);
        delivery.setIsReverse(old.getIsReverse());
        delivery.setStatus(DocStatus.DRAFT.name());
        deliveryMapper.updateById(delivery);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("canProceed", true);
        resp.put("draft", true);
        return R.ok(resp);
    }

    /** 删除交货记录 — 回滚库存 */
    @DeleteMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> delete(@PathVariable Long id) {
        OutsourceOrderDelivery old = deliveryMapper.selectById(id);
        if (old == null) throw new BusinessException("交货记录不存在");
        if (!DocStatus.DRAFT.name().equals(old.getStatus())) {
            throw new BusinessException("仅草稿状态可删除，已审核记录请先反审核");
        }
        deliveryMapper.deleteById(id);
        return R.ok();
    }

    /** 退不良：拆分产品为BOM物料还回工厂委外仓库，扣减所选成品仓库库存 */
    @PostMapping("/return-defect/{orderId}")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> returnDefect(@PathVariable Long orderId, @RequestBody Map<String, Object> body) {
        log.info("退不良: orderId={}, body={}", orderId, body);
        OutsourceOrder order = orderService.getById(orderId);
        if (order == null) throw new BusinessException("加工单不存在");
        if (!OutsourceOrderStatus.PRODUCING.name().equals(order.getStatus()) && !OutsourceOrderStatus.FINISHED.name().equals(order.getStatus()))
            throw new BusinessException("只有生产中或已完成的加工单可退不良");

        String productName = (String) body.get("productName");
        Long productId = body.get("productId") != null ? Long.valueOf(body.get("productId").toString()) : null;
        BigDecimal defectQty = new BigDecimal(body.get("quantity").toString());
        Long warehouseId = body.get("warehouseId") != null
                ? Long.valueOf(body.get("warehouseId").toString()) : null;
        if (warehouseId == null) throw new BusinessException("请选择退不良仓库");

        // 匹配产品
        List<OutsourceOrderProduct> products = orderService.getProducts(orderId);
        OutsourceOrderProduct matchedProduct = products.stream()
            .filter(p -> productId != null ? productId.equals(p.getId()) : productName != null && productName.equals(p.getProductName()))
            .findFirst().orElse(null);
        if (matchedProduct == null) throw new BusinessException("加工单中未找到该产品");

        // 校验退不良数量不超过已交数量
        List<OutsourceOrderDelivery> allDeliveries = deliveryMapper.selectList(
            new LambdaQueryWrapper<OutsourceOrderDelivery>().eq(OutsourceOrderDelivery::getOrderId, orderId));
        final Long fProductId = matchedProduct.getId();
        BigDecimal deliveredQty = allDeliveries.stream()
            .filter(d -> fProductId.equals(d.getProductId()))
            .map(d -> d.getQuantity() != null ? d.getQuantity() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (defectQty.compareTo(deliveredQty) > 0)
            throw new BusinessException("退不良数量(" + defectQty + ")不能超过已交数量(" + deliveredQty + ")");

        // 校验仓库成品库存
        LambdaQueryWrapper<InventoryWarehouseStock> stockW = new LambdaQueryWrapper<InventoryWarehouseStock>()
                .eq(InventoryWarehouseStock::getWarehouseId, warehouseId)
                .eq(InventoryWarehouseStock::getProductName, productName);
        BigDecimal stockQty = inventoryStockMapper.selectList(stockW)
                .stream().map(s -> s.getQuantity() != null ? s.getQuantity() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (stockQty.compareTo(defectQty) < 0)
            throw new BusinessException(productName + " 仓库库存不足(库存:" + stockQty + "，退:" + defectQty + ")");

        // 确定委外仓库
        Long whId = resolveOutsourceWarehouseId(order);
        if (whId == null) throw new BusinessException("工厂无委外仓库");

        // 校验通过：仅存草稿记录（isReverse=true），库存/BOM还料/应付在审核时由 applyDefectStock 统一落账
        OutsourceOrderDelivery delivery = new OutsourceOrderDelivery();
        delivery.setOrderId(orderId);
        delivery.setProductName(productName);
        delivery.setProductId(matchedProduct.getId());
        delivery.setQuantity(defectQty.negate());
        delivery.setDeliveryType(DeliveryType.DEFECT_RETURN.getCode());
        delivery.setWarehouseId(warehouseId);
        delivery.setDeliveryDate(LocalDate.now());
        delivery.setRemark(DeliveryType.DEFECT_RETURN.getLabel());
        delivery.setIsReverse(true);
        delivery.setStatus(DocStatus.DRAFT.name());
        deliveryMapper.insert(delivery);
        log.info("退不良记录已保存(草稿): id={}", delivery.getId());
        return R.ok();
    }

    /** 退不良审核：扣成品库存 + BOM还料 + 冲减应付 */
    private void applyDefectStock(OutsourceOrder order, OutsourceOrderDelivery delivery) {
        String productName = delivery.getProductName();
        BigDecimal defectQty = delivery.getQuantity().abs();
        Long warehouseId = delivery.getWarehouseId();
        if (warehouseId == null) throw new BusinessException("退不良记录缺少仓库");

        // 1. 扣减成品库存（写入 inventory_stock_log）
        Long productMaterialId = null;
        Product productMat = productMapper.selectOne(
            new LambdaQueryWrapper<Product>().eq(Product::getName, productName));
        if (productMat != null) productMaterialId = productMat.getId();
        stockService.changeStock(warehouseId, productName, defectQty.negate(),
            StockChangeType.OUTSOURCE_DEFECT_RETURN, order.getCode(), RelatedBillType.OUTSOURCE_DEFECT, productMaterialId, null, order.getId(), null);
        log.info("退不良扣成品: {} (仓库={}) {} -> {}", productName, warehouseId, stockQtyOf(productName, warehouseId), stockQtyOf(productName, warehouseId).subtract(defectQty));

        // 2. 冲减应付（负数交货 → 负数应付）
        OutsourceOrderProduct matchedProduct = orderService.getProducts(delivery.getOrderId()).stream()
            .filter(p -> delivery.getProductId() != null && delivery.getProductId().equals(p.getId()))
            .findFirst().orElse(null);
        if (matchedProduct != null) createDeliveryPayable(order, matchedProduct, delivery);

        // 3. 拆BOM → 物料还回工厂委外仓库
        Long whId = resolveOutsourceWarehouseId(order);
        List<MaterialReq> materials = loadMaterialRequirements(matchedProduct != null ? matchedProduct : new OutsourceOrderProduct());
        for (MaterialReq mat : materials) {
            if (mat.materialId() == null) continue;
            BigDecimal restoreQty = mat.perUnit().multiply(defectQty);

            OutsourceWarehouseStock stock = warehouseStockMapper.selectOne(
                new LambdaQueryWrapper<OutsourceWarehouseStock>()
                    .eq(OutsourceWarehouseStock::getWarehouseId, whId)
                    .eq(OutsourceWarehouseStock::getMaterialId, mat.materialId())
                    .eq(OutsourceWarehouseStock::getQualityType, QualityType.GOOD.getCode()));
            BigDecimal before = stock != null && stock.getQuantity() != null ? stock.getQuantity() : BigDecimal.ZERO;
            if (stock == null) {
                stock = new OutsourceWarehouseStock();
                stock.setWarehouseId(whId);
                stock.setMaterialId(mat.materialId());
                stock.setQualityType(QualityType.GOOD.getCode());
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
    }

    /** 退不良反审核：逆向成品库存 + 扣回BOM还料 + 删应付 */
    private void revertDefectStock(OutsourceOrder order, OutsourceOrderDelivery delivery) {
        String productName = delivery.getProductName();
        BigDecimal defectQty = delivery.getQuantity().abs();
        Long warehouseId = delivery.getWarehouseId();
        if (warehouseId == null) return;

        // 1. 恢复成品库存
        Long productMaterialId = null;
        Product productMat = productMapper.selectOne(
            new LambdaQueryWrapper<Product>().eq(Product::getName, productName));
        if (productMat != null) productMaterialId = productMat.getId();
        stockService.changeStock(warehouseId, productName, defectQty,
            StockChangeType.OUTSOURCE_DEFECT_RETURN, order.getCode(), RelatedBillType.OUTSOURCE_DEFECT, productMaterialId, null, order.getId(), null);
        log.info("退不良反审核恢复成品: {} (仓库={}) +{}", productName, warehouseId, defectQty);

        // 2. 扣回BOM还料
        Long whId = resolveOutsourceWarehouseId(order);
        OutsourceOrderProduct matchedProduct = orderService.getProducts(delivery.getOrderId()).stream()
            .filter(p -> delivery.getProductId() != null && delivery.getProductId().equals(p.getId()))
            .findFirst().orElse(null);
        List<MaterialReq> materials = loadMaterialRequirements(matchedProduct != null ? matchedProduct : new OutsourceOrderProduct());
        for (MaterialReq mat : materials) {
            if (mat.materialId() == null) continue;
            BigDecimal restoreQty = mat.perUnit().multiply(defectQty);
            OutsourceWarehouseStock stock = warehouseStockMapper.selectOne(
                new LambdaQueryWrapper<OutsourceWarehouseStock>()
                    .eq(OutsourceWarehouseStock::getWarehouseId, whId)
                    .eq(OutsourceWarehouseStock::getMaterialId, mat.materialId())
                    .eq(OutsourceWarehouseStock::getQualityType, QualityType.GOOD.getCode()));
            if (stock != null) {
                BigDecimal before = stock.getQuantity() != null ? stock.getQuantity() : BigDecimal.ZERO;
                stock.setQuantity(before.subtract(restoreQty));
                if (stock.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                    warehouseStockMapper.deleteById(stock.getId());
                } else {
                    warehouseStockMapper.updateById(stock);
                }
                writeStockLog(whId, mat.materialId(), mat.materialName(), "退不良反审核扣回还料",
                        restoreQty.negate(), before, stock.getQuantity(), order.getCode());
            }
        }
    }

    /** 查询某成品在某仓库的库存总量（用于日志展示） */
    private BigDecimal stockQtyOf(String productName, Long warehouseId) {
        LambdaQueryWrapper<InventoryWarehouseStock> stockW = new LambdaQueryWrapper<InventoryWarehouseStock>()
                .eq(InventoryWarehouseStock::getWarehouseId, warehouseId)
                .eq(InventoryWarehouseStock::getProductName, productName);
        return inventoryStockMapper.selectList(stockW)
                .stream().map(s -> s.getQuantity() != null ? s.getQuantity() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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
                Long materialId = mat.getMaterialId();
                if (materialId == null) continue; // 无物料ID则跳过（BOM快照已删名称字段，无法按名兜底）
                BigDecimal perUnit = mat.getDemandQuantity() != null
                        ? mat.getDemandQuantity().divide(productQty, 6, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
                result.add(new MaterialReq(materialId, getMaterialNameById(materialId), perUnit));
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
            if (bom.getOutsourceMaterialId() == null) continue;
            Long materialId = bom.getOutsourceMaterialId();
            // 通过ID查询物料名称
            OutsourceMaterial mat = outsourceMaterialMapper.selectById(materialId);
            String materialName = mat != null ? mat.getMaterialName() : "";
            if (materialName.isBlank()) continue;
            BigDecimal perUnit = bom.getQuantity() != null ? bom.getQuantity() : BigDecimal.ZERO;
            result.add(new MaterialReq(materialId, materialName, perUnit));
        }
        log.info("从 BOM 加载 {} 项 (projectId={})", result.size(), projectId);
        return result;
    }

    /** 根据委外物料ID查询名称，用于展示回填（ID关联查询替代冗余name字段） */
    private String getMaterialNameById(Long materialId) {
        if (materialId == null) return "";
        OutsourceMaterial m = outsourceMaterialMapper.selectById(materialId);
        return m != null ? m.getMaterialName() : "";
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
                    .eq(OutsourceWarehouseStock::getQualityType, QualityType.GOOD.getCode()));
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
                    .eq(OutsourceWarehouseStock::getQualityType, QualityType.GOOD.getCode()));
            BigDecimal before = stock != null && stock.getQuantity() != null ? stock.getQuantity() : BigDecimal.ZERO;
            BigDecimal after;
            if (stock == null) {
                stock = new OutsourceWarehouseStock();
                stock.setWarehouseId(whId);
                stock.setMaterialId(mat.materialId());
                stock.setQualityType(QualityType.GOOD.getCode());
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
            .filter(p -> delivery.getProductId() != null && delivery.getProductId().equals(p.getId()))
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
                    .eq(OutsourceWarehouseStock::getQualityType, QualityType.GOOD.getCode()));
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
            revertInventoryStock(delivery, order.getCode());
        }
    }

    /** 增加收货入库库存 */
    private void addInventoryStock(OutsourceOrderDelivery delivery) {
        log.info("开始入库: warehouseId={}, productName={}, qty={}",
                delivery.getWarehouseId(), delivery.getProductName(), delivery.getQuantity());
        Long productMaterialId = null;
        Product product = productMapper.selectOne(
            new LambdaQueryWrapper<Product>().eq(Product::getName, delivery.getProductName()));
        if (product != null) {
            productMaterialId = product.getId();
            log.info("找到对应产品记录: productId={}", productMaterialId);
        } else {
            log.info("产品表中未找到「{}」，将按 productName 入库", delivery.getProductName());
        }
        stockService.changeStock(delivery.getWarehouseId(), delivery.getProductName(),
            delivery.getQuantity(), StockChangeType.OUTSOURCE_FINISH_IN, null, (RelatedBillType) null, productMaterialId, null, delivery.getId(), null);
        log.info("入库完成: warehouseId={}, productName={}, qty={}", delivery.getWarehouseId(), delivery.getProductName(), delivery.getQuantity());
    }

    /** 回滚收货入库（统一走 changeStock，自动写 inventory_stock_log） */
    private void revertInventoryStock(OutsourceOrderDelivery delivery, String orderCode) {
        Long materialId = null;
        Product product = productMapper.selectOne(
            new LambdaQueryWrapper<Product>().eq(Product::getName, delivery.getProductName()));
        if (product != null) materialId = product.getId();

        stockService.changeStock(delivery.getWarehouseId(), delivery.getProductName(),
            delivery.getQuantity().negate(), StockChangeType.OUTSOURCE_ROLLBACK, orderCode, RelatedBillType.OUTSOURCE_ORDER,
            materialId, null, delivery.getId(), null);
        log.info("回滚成品库存: warehouseId={}, product={}, qty=-{}",
                delivery.getWarehouseId(), delivery.getProductName(), delivery.getQuantity());
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
