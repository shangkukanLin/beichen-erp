package com.beichen.erp.outsource.service.impl;

import com.beichen.erp.config.UserContext;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.beichen.erp.common.BillNoSeq;
import com.beichen.erp.common.BillPrefix;
import com.beichen.erp.common.DocStatus;
import com.beichen.erp.common.DocStatusGuard;
import com.beichen.erp.dev.entity.Bom;
import com.beichen.erp.dev.mapper.BomMapper;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.finance.entity.FinancePayable;
import com.beichen.erp.finance.mapper.FinancePayableMapper;
import com.beichen.erp.finance.service.PayableHelper;
import com.beichen.erp.inventory.common.RelatedBillType;
import com.beichen.erp.inventory.common.StockChangeType;
import com.beichen.erp.material.common.ProductQualityType;
import com.beichen.erp.material.entity.Product;
import com.beichen.erp.material.service.ProductService;
import com.beichen.erp.outsource.common.DeliveryType;
import com.beichen.erp.outsource.common.MaterialRequirementCalc;
import com.beichen.erp.outsource.common.OutsourceOrderStatus;
import com.beichen.erp.finance.common.SourceBillType;
import com.beichen.erp.outsource.common.QualityType;
import com.beichen.erp.outsource.entity.BomSnapshot;
import com.beichen.erp.outsource.entity.CloseReport;
import com.beichen.erp.outsource.entity.OutsourceMaterial;
import com.beichen.erp.outsource.entity.OutsourceOrder;
import com.beichen.erp.outsource.entity.OutsourceOrderDelivery;
import com.beichen.erp.outsource.entity.OutsourceOrderMaterial;
import com.beichen.erp.outsource.entity.OutsourceReturnBack;
import com.beichen.erp.outsource.entity.OutsourceOrderProduct;
import com.beichen.erp.outsource.mapper.CloseReportMapper;
import com.beichen.erp.outsource.mapper.OutsourceMaterialMapper;
import com.beichen.erp.outsource.mapper.OutsourceOrderDeliveryMapper;
import com.beichen.erp.outsource.mapper.OutsourceOrderMapper;
import com.beichen.erp.outsource.mapper.OutsourceOrderProductMapper;
import com.beichen.erp.outsource.mapper.OutsourceReturnBackMapper;
import com.beichen.erp.outsource.service.OutsourceOrderDeliveryService;
import com.beichen.erp.outsource.service.OutsourceOrderService;
import com.beichen.erp.supplier.common.SupplierTypeEnum;
import com.beichen.erp.supplier.entity.Supplier;
import com.beichen.erp.supplier.entity.SupplierTypeRef;
import com.beichen.erp.supplier.mapper.SupplierMapper;
import com.beichen.erp.supplier.mapper.SupplierTypeRefMapper;
import com.beichen.erp.warehouse.common.WarehouseCategory;
import com.beichen.erp.warehouse.entity.Warehouse;
import com.beichen.erp.warehouse.entity.WarehouseStock;
import com.beichen.erp.warehouse.entity.WarehouseStockLog;
import com.beichen.erp.warehouse.mapper.WarehouseMapper;
import com.beichen.erp.warehouse.mapper.WarehouseStockLogMapper;
import com.beichen.erp.warehouse.mapper.WarehouseStockMapper;
import com.beichen.erp.warehouse.service.WarehouseStockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 加工单交货记录服务实现
 * <p>
 * 承接原 OrderDeliveryController 中的全部业务逻辑，Controller 退化为薄接口层。
 * 交货记录状态机复用 DocStatus：DRAFT 草稿态仅存盘，AUDITED 审核后才扣物料/成品入库/生成应付。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutsourceOrderDeliveryServiceImpl
        extends ServiceImpl<OutsourceOrderDeliveryMapper, OutsourceOrderDelivery>
        implements OutsourceOrderDeliveryService {

    private final OutsourceOrderService orderService;
    private final OutsourceOrderMapper orderMapper;
    private final OutsourceOrderProductMapper orderProductMapper;
    private final WarehouseStockService stockService;
    private final WarehouseStockMapper stockMapper;
    private final OutsourceMaterialMapper outsourceMaterialMapper;
    private final BomMapper bomMapper;
    private final WarehouseMapper warehouseMapper;
    private final PayableHelper payableHelper;
    private final SupplierMapper supplierMapper;
    /** 2026-09-21（用户口径）：加工退货对象只能是加工厂/供应商，不能是供货商（成品商）⇒ 需要读类型关联 */
    private final SupplierTypeRefMapper supplierTypeRefMapper;
    private final ProductService productService;
    private final com.beichen.erp.warehouse.service.CostService costService;
    // 2026-09-25 P1-1：工厂售后（原无单加工退货）不再拆料还仓/按料价值冲应付，BOM 快照还料与 FIFO 计价依赖已随死代码移除
    /** 加工退货详情：按库存流水回溯「还回了哪些料」（related_delivery_id = 本记录） */
    private final WarehouseStockLogMapper stockLogMapper;
    /** 加工退货详情：本记录产生的应付冲减（source_id = 本记录、source_bill_type = OUTSOURCE_DELIVERY） */
    private final FinancePayableMapper payableMapper;
    /** 加工退货台账「返回进度」（2026-09-27）：按来源退货单聚合已审核加工返回单数量 */
    private final OutsourceReturnBackMapper returnBackMapper;
    /** BOM 快照（2026-09-27）：工厂售后（原无单加工退货）建单时解析快照 + 台账/详情回显版本号 */
    private final com.beichen.erp.outsource.mapper.BomSnapshotMapper bomSnapshotMapper;
    private final com.beichen.erp.outsource.mapper.BomSnapshotItemMapper bomSnapshotItemMapper;
    /**
     * 结单人（2026-09-27 用户口径「把结单人做了」）：「成品收货 → 已结单」页签要显示"谁结的"。
     * 加工单本身没有结单人字段 —— 结单 = 结单报表的一次审核动作，人在报表上
     * （{@code close_report.auditor_name}，confirmClose 盖章、reopenClose 清空）⇒ 批量回捞，避免 N+1。
     */
    private final CloseReportMapper closeReportMapper;

    /** 来源类型：不关联加工单的加工退货（与实体 sourceType 注释里的 RETURN_DEFECT 一致） */
    private static final String SOURCE_RETURN_DEFECT = "RETURN_DEFECT";

    /** 获取某加工单的所有交货记录 */
    @Override
    public List<OutsourceOrderDelivery> listByOrder(Long orderId) {
        return baseMapper.selectList(new LambdaQueryWrapper<OutsourceOrderDelivery>()
                .eq(OutsourceOrderDelivery::getOrderId, orderId)
                .orderByDesc(OutsourceOrderDelivery::getId));
    }

    /** 获取交货汇总 */
    @Override
    public Map<String, Object> summary(Long orderId) {
        OutsourceOrder order = orderService.getById(orderId);
        if (order == null) throw new BusinessException("加工单不存在");
        List<OutsourceOrderProduct> products = orderService.getProducts(orderId);
        List<OutsourceOrderDelivery> deliveries = baseMapper.selectList(
                new LambdaQueryWrapper<OutsourceOrderDelivery>()
                        .eq(OutsourceOrderDelivery::getOrderId, orderId)
                        .eq(OutsourceOrderDelivery::getStatus, DocStatus.AUDITED.getCode()));

        BigDecimal totalQty = products.stream().map(p -> p.getQuantity() != null ? p.getQuantity() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal deliveredQty = deliveries.stream().map(d -> d.getQuantity() != null ? d.getQuantity() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> result = new HashMap<>();
        result.put("totalQuantity", totalQty);
        result.put("deliveredQuantity", deliveredQty);
        result.put("remainingQuantity", totalQty.subtract(deliveredQty));
        result.put("deliveryCount", deliveries.size());

        // 统计行回填 SKU（非表字段），前端可直接展示
        productService.fillSku(products, OutsourceOrderProduct::getProductId, OutsourceOrderProduct::setSku);
        List<Map<String, Object>> productStats = new ArrayList<>();
        for (OutsourceOrderProduct p : products) {
            String pn = p.getProductName() != null ? p.getProductName() : "未命名产品";
            BigDecimal pQty = p.getQuantity() != null ? p.getQuantity() : BigDecimal.ZERO;
            BigDecimal pDelivered = deliveries.stream()
                    .filter(d -> belongsToProduct(d, p))
                    .map(d -> d.getQuantity() != null ? d.getQuantity() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            Map<String, Object> ps = new HashMap<>();
            ps.put("sku", p.getSku() != null ? p.getSku() : "");
            ps.put("productName", pn);
            ps.put("totalQuantity", pQty);
            ps.put("deliveredQuantity", pDelivered);
            ps.put("remainingQuantity", pQty.subtract(pDelivered));
            productStats.add(ps);
        }
        result.put("productStats", productStats);
        return result;
    }

    /**
     * 收货维度订单列表（「成品收货」页签）：加工单状态过滤 + 交货进度聚合。
     * <p>口径与 {@link #summary(Long)} 一致：已交数量只统计已审核（AUDITED）的交货记录（退不良为负数会自动扣减）；
     * 最近交货日期看全部交货记录（含草稿），与加工订单列表 latestDeliveryDate 口径保持一致。</p>
     *
     * <p>2026-09-27（用户口径「成品收货要有 收货中｜已结单 两个页签」）：`status` 参数化 ——
     * 原先状态**写死 PRODUCING**，已结单的加工单在本页看不到；缺省仍 PRODUCING（老调用/老守卫行为不变），
     * 已结单传 FINISHED。两条页签复用同一套聚合，只是状态不同。</p>
     */
    @Override
    public Map<String, Object> pageOrders(Integer pageNo, Integer size, String code, String status) {
        Page<OutsourceOrder> pageParam = new Page<>(pageNo == null || pageNo < 1 ? 1 : pageNo,
                size == null || size < 1 ? 10 : size);
        // 白名单：只允许本页两个页签的状态，避免被传成任意状态（默认 PRODUCING = 原行为）
        String st = (status != null && OutsourceOrderStatus.FINISHED.getCode().equals(status))
                ? OutsourceOrderStatus.FINISHED.getCode()
                : OutsourceOrderStatus.PRODUCING.getCode();
        Page<OutsourceOrder> pageResult = orderMapper.selectPage(pageParam,
                new LambdaQueryWrapper<OutsourceOrder>()
                        .eq(OutsourceOrder::getStatus, st)
                        .like(code != null && !code.isBlank(), OutsourceOrder::getCode, code)
                        .orderByDesc(OutsourceOrder::getId));

        List<Long> orderIds = pageResult.getRecords().stream()
                .map(OutsourceOrder::getId).collect(Collectors.toList());
        Map<Long, BigDecimal> totalMap = new HashMap<>();
        Map<Long, BigDecimal> deliveredMap = new HashMap<>();
        Map<Long, LocalDate> latestMap = new HashMap<>();
        Map<Long, String> nameMap = new HashMap<>();
        // 结单人（2026-09-27）：只对「已结单」页签有意义 ⇒ 仅该状态批量回捞一次
        Map<Long, String> closeByMap = new HashMap<>();
        Map<Long, String> skuMap = new HashMap<>();
        // 2026-09-25（用户口径「产品列可点进详情」）：逐项 {id,name,sku}，供前端渲染链接跳 /product/detail/:id
        Map<Long, List<Map<String, Object>>> productListMap = new HashMap<>();

        if (!orderIds.isEmpty()) {
            // 订单量 + 产品名称/SKU 拼串（一次查全部，避免逐单查询）
            List<OutsourceOrderProduct> products = orderProductMapper.selectList(
                    new LambdaQueryWrapper<OutsourceOrderProduct>().in(OutsourceOrderProduct::getOrderId, orderIds));
            productService.fillSku(products, OutsourceOrderProduct::getProductId, OutsourceOrderProduct::setSku);
            for (OutsourceOrderProduct p : products) {
                totalMap.merge(p.getOrderId(), p.getQuantity() != null ? p.getQuantity() : BigDecimal.ZERO, BigDecimal::add);
                if (p.getProductName() != null && !p.getProductName().isEmpty())
                    nameMap.merge(p.getOrderId(), p.getProductName(), (a, b) -> a + " / " + b);
                if (p.getSku() != null && !p.getSku().isEmpty())
                    skuMap.merge(p.getOrderId(), p.getSku(), (a, b) -> a + " / " + b);
                // 逐项产品（含产品主数据ID）：仅当 id 存在才入列，前端无 id 时回退纯文本
                if (p.getProductId() != null) {
                    Map<String, Object> pm = new HashMap<>();
                    pm.put("id", p.getProductId());
                    pm.put("name", p.getProductName());
                    pm.put("sku", p.getSku());
                    productListMap.computeIfAbsent(p.getOrderId(), k -> new ArrayList<>()).add(pm);
                }
            }
            // 已交数量（仅已审核）+ 最近交货日期（全部记录）
            for (OutsourceOrderDelivery d : baseMapper.selectList(
                    new LambdaQueryWrapper<OutsourceOrderDelivery>().in(OutsourceOrderDelivery::getOrderId, orderIds))) {
                if (DocStatus.AUDITED.getCode().equals(d.getStatus())) {
                    deliveredMap.merge(d.getOrderId(),
                            d.getQuantity() != null ? d.getQuantity() : BigDecimal.ZERO, BigDecimal::add);
                }
                LocalDate date = d.getDeliveryDate();
                if (date != null) {
                    LocalDate cur = latestMap.get(d.getOrderId());
                    if (cur == null || date.isAfter(cur)) latestMap.put(d.getOrderId(), date);
                }
            }
        }

        // 结单人：结单报表上盖章的人（批量查，避免 N+1；报表不存在/未盖章时留空 ⇒ 前端显示 —）
        if (!orderIds.isEmpty() && OutsourceOrderStatus.FINISHED.getCode().equals(st)) {
            for (CloseReport r : closeReportMapper.selectList(
                    new LambdaQueryWrapper<CloseReport>().in(CloseReport::getOrderId, orderIds))) {
                if (r.getOrderId() != null && r.getAuditorName() != null && !r.getAuditorName().isBlank())
                    closeByMap.put(r.getOrderId(), r.getAuditorName());
            }
        }

        // 加工厂名称（批量查，避免 N+1）
        Map<Long, String> factoryNameMap = new HashMap<>();
        List<Long> factoryIds = pageResult.getRecords().stream()
                .map(OutsourceOrder::getFactoryId).filter(fid -> fid != null).distinct().collect(Collectors.toList());
        if (!factoryIds.isEmpty()) {
            for (Supplier s : supplierMapper.selectBatchIds(factoryIds)) factoryNameMap.put(s.getId(), s.getName());
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (OutsourceOrder o : pageResult.getRecords()) {
            BigDecimal total = totalMap.getOrDefault(o.getId(), BigDecimal.ZERO);
            BigDecimal delivered = deliveredMap.getOrDefault(o.getId(), BigDecimal.ZERO);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", o.getId());
            m.put("code", o.getCode());
            m.put("status", o.getStatus());
            m.put("factoryId", o.getFactoryId());
            m.put("factoryName", factoryNameMap.getOrDefault(o.getFactoryId(), ""));
            m.put("productNames", nameMap.getOrDefault(o.getId(), ""));
            m.put("productSkus", skuMap.getOrDefault(o.getId(), ""));
            m.put("products", productListMap.getOrDefault(o.getId(), List.of()));
            m.put("planEndDate", o.getPlanEndDate());
            m.put("totalAmount", o.getTotalAmount());
            m.put("totalQuantity", total);
            m.put("deliveredQuantity", delivered);
            m.put("remainingQuantity", total.subtract(delivered));
            m.put("latestDeliveryDate", latestMap.get(o.getId()));
            // 2026-09-27：结单日期 = 加工单 actual_end_date（CloseReportServiceImpl.confirmClose 结单时写入）
            // —— 供「已结单」页签的「结单日期」列使用，与物料侧（material_order.finish_time）同口径
            m.put("actualEndDate", o.getActualEndDate());
            // 结单人（同一列的第二行小字）：结单报表盖章人
            m.put("closeByName", closeByMap.get(o.getId()));
            rows.add(m);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("records", rows);
        result.put("total", pageResult.getTotal());
        result.put("current", pageResult.getCurrent());
        result.put("size", pageResult.getSize());
        return result;
    }

    /** 新增交货记录 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createDelivery(OutsourceOrderDelivery delivery, boolean forceDelivery, boolean overReceipt) {
        log.info("【调试】delivery: aQty={}, bQty={}, cQty={}, defectQty={}, quantity={}, productId={}",
                delivery.getAQty(), delivery.getBQty(), delivery.getCQty(), delivery.getDefectQty(),
                delivery.getQuantity(), delivery.getProductId());
        if (delivery.getOrderId() == null) throw new BusinessException("加工单ID不能为空");
        if (delivery.getProductId() == null) throw new BusinessException("产品ID不能为空");
        OutsourceOrder order = orderService.getById(delivery.getOrderId());
        if (order == null) throw new BusinessException("加工单不存在");
        if (!OutsourceOrderStatus.PRODUCING.getCode().equals(order.getStatus())) throw new BusinessException("只有生产中的加工单可录入收货");
        // 工厂必须有委外仓库，否则交货时无法正确扣减我方物料
        if (resolveOutsourceWarehouseId(order) == null)
            throw new BusinessException("工厂无委外仓库，请先在【委外仓库】页面为该工厂创建委外仓库");
        // F7-58（2026-09-20）：入参校验抽成 validateDraftPayload，供 create / update **两条写入口共用**。
        // 原实现 create 校验齐全、update 只查"状态 + 数量上限" ⇒ 直调接口可把草稿改成 quantity=-5、
        // 「等级和 ≠ 总量」或把仓库清空 ⇒ 审核时三条落账路径各取不同字段（入库按等级、应付与扣料按 quantity）
        // ⇒ 库存与应付解耦、可造负应付与反向扣料。
        validateDraftPayload(delivery);

        List<OutsourceOrderProduct> products = orderService.getProducts(delivery.getOrderId());
        OutsourceOrderProduct matchedProduct = matchProduct(products, delivery.getProductId(), delivery.getProductMasterId());
        if (matchedProduct == null) throw new BusinessException("加工单中未找到该产品");
        delivery.setProductId(matchedProduct.getId());
        // 关联产品主数据ID(product.id)，成品库存/流水落账使用
        Long masterId = orderService.resolveProductMasterId(matchedProduct);
        if (masterId == null) throw new BusinessException("产品「" + matchedProduct.getProductName() + "」未关联产品主数据，请先在加工单中关联产品");
        delivery.setProductMasterId(masterId);
        log.info("新增交货: orderId={}, productId={}, masterId={}, productName={}, qty={}, warehouseId={}, force={}",
                delivery.getOrderId(), matchedProduct.getId(), masterId, matchedProduct.getProductName(),
                delivery.getQuantity(), delivery.getWarehouseId(), forceDelivery);

        // 数量口径校验放在缺料检查之前：forceDelivery（缺料强出）**不豁免**数量超订单。
        // 2026-09-29（用户口径「加工订单和物料订单都可以超量收货」）：超收不再硬拒 ——
        // 未确认（overReceipt=false）时返回**待确认**响应（沿用缺料确认那一套前端交互，不落库）；
        // 确认后落库并把 over_receipt 置 1，审核期 assertNotOverPlanned 对已确认的行放行。
        String overDetail = overOrderQtyDetail(delivery.getOrderId(), matchedProduct, delivery.getQuantity(), null);
        if (overDetail != null) {
            if (!overReceipt) return overReceiptResp(matchedProduct, overDetail);
            delivery.setOverReceipt(1);
        }

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
            return resp;
        }

        // 草稿态存盘，不扣物料/不入库存/不生成应付，审核通过后由 audit() 统一落账
        delivery.setIsReverse(false);
        delivery.setDeliveryType(DeliveryType.DELIVERY.getCode());
        delivery.setStatus(DocStatus.DRAFT.getCode());
        baseMapper.insert(delivery);
        log.info("交货记录已保存(草稿): id={}", delivery.getId());
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("canProceed", true);
        resp.put("draft", true);
        return resp;
    }

    /** 审核交货记录：草稿态生效，扣减物料、成品入库、生成应付（退不良则为冲销） */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        OutsourceOrderDelivery delivery = baseMapper.selectById(id);
        if (delivery == null) throw new BusinessException("收货记录不存在");
        // P2-29：原子抢占 DRAFT→AUDITED，避免并发/双击重复扣物料 + 重复入库 + 重复生成应付
        if (!DocStatusGuard.claim(baseMapper, OutsourceOrderDelivery::getId, id,
                OutsourceOrderDelivery::getStatus, DocStatus.DRAFT.getCode(), DocStatus.AUDITED.getCode())) {
            throw new BusinessException("仅草稿状态可以审核");
        }
        // 2026-09-21：不关联加工单的加工退货（source_type=RETURN_DEFECT）本身**没有** order ⇒ 走无单分支；
        // 其余记录仍必须能取到加工单（保留原有强校验，避免"订单被删"这类脏数据静默过审）
        OutsourceOrder order = delivery.getOrderId() == null ? null : orderService.getById(delivery.getOrderId());
        if (order == null && delivery.getOrderId() != null) throw new BusinessException("加工单不存在");
        if (order == null && !Boolean.TRUE.equals(delivery.getIsReverse()))
            throw new BusinessException("收货记录缺少加工单，无法审核");

        // F2-2（2026-09-18 审核修复）：普通交货复核「累计交货 ≤ 计划数量」（退不良为负数，天然不会超，不拦）。
        // 2026-09-29（用户口径「加工订单和物料订单都可以超量收货」）：录入时已二次确认超收的行
        // （over_receipt=1）**放行**；未确认的行仍拦 —— 本道继续兜住"直改库 / 并发叠加"。
        if (!Boolean.TRUE.equals(delivery.getIsReverse()) && !isOverConfirmed(delivery.getOverReceipt())) {
            assertNotOverPlanned(order, delivery);
        }

        if (Boolean.TRUE.equals(delivery.getIsReverse())) {
            // P3-1 收敛（2026-09-25）：已结单的加工单禁止审核红冲（防"先建草稿、后结单、再审核"绕过创建拦截）
            if (order != null && OutsourceOrderStatus.FINISHED.getCode().equals(order.getStatus()))
                throw new BusinessException("该加工单已结单，账务已清算；如需退货请走「工厂售后（原无单加工退货）」办理（不关联加工单）");
            // P3-1（2026-09-25）：有单红冲**审核期**额度复核 —— 修复并发双草稿窗口（建草稿校验与审核落账
            // 之间无复核：两人并发建草稿可双双过审造成超退）。对加工单行 FOR UPDATE 串行化后，
            // 按**建草稿同一口径**（returnDefect :508-520）复核"累计退货 ≤ 已收（净额）"。无单红冲无聚合对象，不适用。
            if (order != null) assertDefectReturnWithinDelivered(order, delivery);
            // 加工退货审核：扣成品库存 + BOM还料 + 冲减应付（有单按该单口径；无单按「工厂 + 产品快照」口径）
            if (order == null) applyDefectStockNoOrder(delivery);
            else applyDefectStock(order, delivery);
        } else {
            // 普通交货审核：扣物料 + 成品入库 + 生成应付
            // 按产品主数据ID优先匹配（产品行重建后行ID会变，主数据ID稳定）
            OutsourceOrderProduct matchedProduct =
                    findOrderProduct(orderService.getProducts(delivery.getOrderId()), delivery);
            if (matchedProduct == null) throw new BusinessException("加工单中未找到该产品");
            BigDecimal materialCost = applyMaterialDeduction(order, matchedProduct, delivery.getQuantity(), matchedProduct.getProductName(), delivery.getId());
            // F7-58（2026-09-20）：仓库缺失 ⇒ **显式拒绝**（原为 `if (warehouseId != null)` 静默跳过入库
            // ⇒ "审核通过但成品不入库，而扣料与应付照落" = 账实不符）。草稿侧已由 validateDraftPayload 保证，
            // 此处兜住"历史草稿 / 直改库"等绕过路径。
            if (delivery.getWarehouseId() == null)
                throw new BusinessException("收货记录缺少入库仓库，无法审核（请先反审核、补全收货仓库后再审核）");
            addInventoryStock(delivery, order.getCode());
            createDeliveryPayable(order, matchedProduct, delivery);
            // 移动加权成本：本批单位成本 = (加工费 + 耗用材料成本) ÷ 交货数量（包工包料时材料成本为 0）
            BigDecimal totalQty = delivery.getQuantity() != null ? delivery.getQuantity() : BigDecimal.ZERO;
            if (totalQty.compareTo(BigDecimal.ZERO) > 0 && delivery.getProductMasterId() != null) {
                BigDecimal fee = matchedProduct.getUnitPrice() != null
                        ? matchedProduct.getUnitPrice().multiply(totalQty) : BigDecimal.ZERO;
                BigDecimal unitCost = fee.add(materialCost == null ? BigDecimal.ZERO : materialCost)
                        .divide(totalQty, 4, RoundingMode.HALF_UP);
                costService.applyProduct(delivery.getProductMasterId(), totalQty, unitCost,
                        StockChangeType.OUTSOURCE_FINISH_IN.getCode(), delivery.getId(), order.getCode());
            }
        }
        OutsourceOrderDelivery upd = new OutsourceOrderDelivery();
        upd.setId(id);
        upd.setStatus(DocStatus.AUDITED.getCode());
        // 2026-09-23（用户口径：单据详情显示「制单人 + 审核人」）：成品收货审核补记录审核人
        upd.setAuditorId(UserContext.getId());
        upd.setAuditorName(UserContext.getName());
        baseMapper.updateById(upd);
    }

    /** 反审核交货记录：已审核态回滚库存/应付，回到草稿 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unaudit(Long id) {
        OutsourceOrderDelivery delivery = baseMapper.selectById(id);
        if (delivery == null) throw new BusinessException("收货记录不存在");
        // P2-29：原子抢占 AUDITED→DRAFT，避免并发反审核重复回滚库存与应付
        if (!DocStatusGuard.claim(baseMapper, OutsourceOrderDelivery::getId, id,
                OutsourceOrderDelivery::getStatus, DocStatus.AUDITED.getCode(), DocStatus.DRAFT.getCode())) {
            throw new BusinessException("仅已审核状态可以反审核");
        }
        // 2026-09-21：与 audit 同口径 —— 工厂售后（原无单加工退货）没有 order，走无单逆向分支
        OutsourceOrder order = delivery.getOrderId() == null ? null : orderService.getById(delivery.getOrderId());
        if (order == null && delivery.getOrderId() != null) throw new BusinessException("加工单不存在");
        if (order == null && !Boolean.TRUE.equals(delivery.getIsReverse()))
            throw new BusinessException("收货记录缺少加工单，无法反审核");

        if (Boolean.TRUE.equals(delivery.getIsReverse())) {
            // P3-1 收敛（2026-09-25）：已结单的加工单禁止反审核红冲（回滚会动到结单清算过的账）
            if (order != null && OutsourceOrderStatus.FINISHED.getCode().equals(order.getStatus()))
                throw new BusinessException("该加工单已结单，不可反审核红冲");
            // 2026-09-28（详情页补「反审核」的配套闸门，用户口径「加工退货详情需要有审核和反审核功能」）：
            // **已登记「加工返回」就不能反审核** —— 工厂已把货送回时，在厂成品（PRODUCT_DEFECT）已被核销，
            // 逆回会造成账实错位；且无单逆向走严格扣减、只报"库存不足"，文案含糊易误导。
            // 口径：先逐条撤销返回，再反审核（与「维修返回」的撤销口径对称）。
            BigDecimal returnedQty = returnedQtyBySource(java.util.List.of(id)).getOrDefault(id, BigDecimal.ZERO);
            if (returnedQty.compareTo(BigDecimal.ZERO) > 0)
                throw new BusinessException("该加工退货已有「加工返回」记录（已返回 "
                        + returnedQty.stripTrailingZeros().toPlainString() + "），请先在详情页逐条撤销返回后再反审核");
            if (order == null) revertDefectStockNoOrder(delivery);
            else revertDefectStock(order, delivery);
        } else {
            revertDeliveryStock(order, delivery);
            // 成本冲销：删除本批交货入库批次并反加权
            costService.reverseByBill(StockChangeType.OUTSOURCE_FINISH_IN.getCode(), delivery.getId());
        }
        // 同步冲回应付（置已作废，保留审计；已付款的会被阻止并抛异常）
        payableHelper.reversePayable(id, SourceBillType.OUTSOURCE_DELIVERY.getCode());
        OutsourceOrderDelivery upd = new OutsourceOrderDelivery();
        upd.setId(id);
        upd.setStatus(DocStatus.DRAFT.getCode());
        baseMapper.updateById(upd);
    }

    /** 修改交货记录 — 仅草稿态可编辑，不触碰库存 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> updateDelivery(Long id, OutsourceOrderDelivery delivery, boolean forceDelivery, boolean overReceipt) {
        OutsourceOrderDelivery old = baseMapper.selectById(id);
        if (old == null) throw new BusinessException("收货记录不存在");
        if (!DocStatus.DRAFT.getCode().equals(old.getStatus())) {
            throw new BusinessException("仅草稿状态可编辑，已审核记录请先反审核");
        }

        OutsourceOrder order = orderService.getById(old.getOrderId());
        if (order == null) throw new BusinessException("加工单不存在");

        List<OutsourceOrderProduct> products = orderService.getProducts(old.getOrderId());
        OutsourceOrderProduct matchedProduct = matchProduct(products, delivery.getProductId(), delivery.getProductMasterId());
        // 产品行可能因加工单编辑被重建：入参行ID失配时，按记录原有的产品主数据ID兜底定位
        if (matchedProduct == null && old.getProductMasterId() != null)
            matchedProduct = matchProduct(products, null, old.getProductMasterId());
        if (matchedProduct == null) throw new BusinessException("加工单中未找到该产品");
        // 草稿态编辑同步刷新产品行ID与产品主数据ID（行ID可能因加工单编辑而重建）
        delivery.setProductId(matchedProduct.getId());
        delivery.setProductMasterId(orderService.resolveProductMasterId(matchedProduct));
        // F7-58（2026-09-20）：与新增**同一套**入参校验（数量 > 0 · 等级和 == 总量 · 仓库必填）
        validateDraftPayload(delivery);
        // 数量口径（排除自身）同新增：超收需先二次确认（overReceipt=true），确认后落 over_receipt=1
        String overDetail = overOrderQtyDetail(old.getOrderId(), matchedProduct, delivery.getQuantity(), id);
        if (overDetail != null) {
            if (!overReceipt) return overReceiptResp(matchedProduct, overDetail);
            delivery.setOverReceipt(1);
        }

        // 草稿态编辑不触碰库存，仅更新记录本身与审核状态（保持草稿）
        delivery.setId(id);
        delivery.setIsReverse(old.getIsReverse());
        // F7-58：**类型不可改** —— 原实现直接采用请求体的 deliveryType ⇒ 可把普通交货草稿改成
        // DEFECT_RETURN（单据自我描述与审核分支错位；returnDefect 的"累计退不良"按 deliveryType
        // 统计也会被污染）。类型在创建时已定型（create 强制 DELIVERY）。
        delivery.setDeliveryType(old.getDeliveryType());
        delivery.setStatus(DocStatus.DRAFT.getCode());
        baseMapper.updateById(delivery);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("canProceed", true);
        resp.put("draft", true);
        return resp;
    }

    /** 删除交货记录 — 仅草稿态可删除 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDelivery(Long id) {
        // 原子删除（O-7）：带状态条件的物理删，只有草稿能删；affected=0 说明已被并发删除/审核或状态已变
        int rows = baseMapper.delete(new LambdaQueryWrapper<OutsourceOrderDelivery>()
                .eq(OutsourceOrderDelivery::getId, id)
                .eq(OutsourceOrderDelivery::getStatus, DocStatus.DRAFT.getCode()));
        if (rows == 0) {
            if (baseMapper.selectById(id) == null) throw new BusinessException("收货记录不存在");
            throw new BusinessException("仅草稿状态可删除，已审核记录请先反审核");
        }
    }

    /**
     * 加工退货：拆分产品为 BOM 物料还回工厂委外仓库，并扣减所选成品仓库存。
     * <p>2026-09-21（用户口径「文案改成加工退货」）：**这个名字全链一个词** —— 本动作在界面上叫「加工退货」
     * （加工单收货详细页的按钮/弹窗、收货记录类型列、「加工退货」页的同名页签），后端会弹给用户的提示语与
     * 备注快照也一律是「加工退货」。沿革：退不良 → 加工退货 → 不良退货 → **定稿「加工退货」**。
     * 实现完全不变 —— 往 `outsource_order_delivery` 写一条**负数**记录（`delivery_type=DEFECT_RETURN`、
     * `is_reverse=1`），审核时由 {@link #applyDefectStock} 落账（扣成品 + BOM 料还回工厂委外仓 + 冲减应付）。</p>
     * <p>⚠️ 枚举值 / DB 列名 / 服务方法名**一律不动**（物料收货页那边仍叫「退不良」，共用一个枚举，
     * 改共享文案会串词）；内部日志保留原词（用户不可见）。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void returnDefect(Long orderId, Map<String, Object> body) {
        log.info("退不良: orderId={}, body={}", orderId, body);
        OutsourceOrder order = orderService.getById(orderId);
        if (order == null) throw new BusinessException("加工单不存在");
        // P3-1 收敛（2026-09-25）：已结单（FINISHED）的加工单账务已清算，禁止再发起有单红冲
        //（结单后再还料/冲应付会破坏清算结果）；结单后退货请走「工厂售后（原无单加工退货）」（不关联加工单，与清算账解耦）。
        if (OutsourceOrderStatus.FINISHED.getCode().equals(order.getStatus()))
            throw new BusinessException("该加工单已结单，账务已清算；如需退货请走「工厂售后（原无单加工退货）」办理（不关联加工单）");
        if (!OutsourceOrderStatus.PRODUCING.getCode().equals(order.getStatus()))
            throw new BusinessException("只有生产中的加工单可以做加工退货");

        Long productId = body.get("productId") != null ? Long.valueOf(body.get("productId").toString()) : null;
        if (productId == null) throw new BusinessException("产品ID不能为空");
        // F7-65②（2026-09-20）：数量缺失/畸形时给业务提示（原先 `body.get("quantity").toString()` 直接 NPE ⇒ 500）
        Object defectQtyObj = body.get("quantity");
        if (defectQtyObj == null || defectQtyObj.toString().isBlank())
            throw new BusinessException("加工退货数量不能为空");
        BigDecimal defectQty;
        try {
            defectQty = new BigDecimal(defectQtyObj.toString());
        } catch (NumberFormatException e) {
            throw new BusinessException("加工退货数量格式不正确：" + defectQtyObj);
        }
        // 退不良规格：A/B/C/DEFECT，缺省按 A 规处理（兼容旧调用）
        String qualityType = body.get("qualityType") != null && !body.get("qualityType").toString().isBlank()
                ? body.get("qualityType").toString() : "A";
        if (!isValidQualityType(qualityType)) throw new BusinessException("非法的加工退货规格: " + qualityType);
        Long warehouseId = body.get("warehouseId") != null
                ? Long.valueOf(body.get("warehouseId").toString()) : null;
        if (warehouseId == null) throw new BusinessException("请选择加工退货仓库");
        if (defectQty.compareTo(BigDecimal.ZERO) <= 0) throw new BusinessException("加工退货数量必须大于0");

        // 匹配产品
        List<OutsourceOrderProduct> products = orderService.getProducts(orderId);
        OutsourceOrderProduct matchedProduct = products.stream()
                .filter(p -> productId.equals(p.getId()))
                .findFirst().orElse(null);
        if (matchedProduct == null) throw new BusinessException("加工单中未找到该产品");
        String productName = matchedProduct.getProductName();
        // 关联产品主数据ID(product.id)，退不良扣库存/还料落账使用
        Long masterId = orderService.resolveProductMasterId(matchedProduct);
        if (masterId == null) throw new BusinessException("产品「" + productName + "」未关联产品主数据，请先在加工单中关联产品");

        // 校验累计退不良(该产品已审核+草稿的负数量绝对值)不超过已交数量
        List<OutsourceOrderDelivery> allDeliveries = baseMapper.selectList(
                new LambdaQueryWrapper<OutsourceOrderDelivery>().eq(OutsourceOrderDelivery::getOrderId, orderId));
        BigDecimal deliveredQty = allDeliveries.stream()
                .filter(d -> belongsToProduct(d, matchedProduct))
                .map(d -> d.getQuantity() != null ? d.getQuantity() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal returnedQty = allDeliveries.stream()
                .filter(d -> belongsToProduct(d, matchedProduct)
                        && DeliveryType.DEFECT_RETURN.getCode().equals(d.getDeliveryType()))
                .map(d -> d.getQuantity() != null && d.getQuantity().signum() < 0 ? d.getQuantity().abs() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (returnedQty.add(defectQty).compareTo(deliveredQty) > 0)
            throw new BusinessException("累计加工退货数量(" + returnedQty.add(defectQty) + ")不能超过已收数量(" + deliveredQty + ")");

        // 校验该规格仓库成品库存（按产品主数据ID+规格定位）
        // 2026-09-28：成品行的形态同样分账（MATERIAL 正常 / PRODUCT_DEFECT 不良 / PRODUCT_REPAIR 维修）
        // ⇒ 必须带 FORM_MATERIAL，否则同一产品同规格存在其它形态行时 selectOne 查到多行直接 500。
        WarehouseStock stock = stockMapper.selectOne(
                new LambdaQueryWrapper<WarehouseStock>()
                        .eq(WarehouseStock::getWarehouseId, warehouseId)
                        .eq(WarehouseStock::getProductId, masterId)
                        .eq(WarehouseStock::getStockForm, WarehouseStock.FORM_MATERIAL)
                        .eq(WarehouseStock::getQualityType, qualityType));
        BigDecimal stockQty = stock != null && stock.getQuantity() != null ? stock.getQuantity() : BigDecimal.ZERO;
        if (stockQty.compareTo(defectQty) < 0)
            throw new BusinessException(productName + " " + qualityType + "规 仓库库存不足(库存:" + stockQty + "，退:" + defectQty + ")");

        // 确定委外仓库
        Long whId = resolveOutsourceWarehouseId(order);
        if (whId == null) throw new BusinessException("工厂无委外仓库");

        // 校验通过：仅存草稿记录（isReverse=true），库存/BOM还料/应付在审核时由 applyDefectStock 统一落账
        OutsourceOrderDelivery delivery = new OutsourceOrderDelivery();
        // 2026-09-25：红冲记录自动单号（关联加工单 GTH-），建草稿即取号（台账/流水不再用数据库ID）
        delivery.setCode(generateReturnCode(true));
        delivery.setOrderId(orderId);
        // 2026-09-21：有单红冲也写加工厂（原先只有"无单"路径写 ⇒ 加工退货台账的「加工厂」列为空，用户实测反馈）。
        // 无副作用：全库无 SQL 用 outsource_order_delivery.factory_id 做筛选/归集（已核）；落账仍按加工单口径。
        delivery.setFactoryId(order.getFactoryId());
        delivery.setProductId(matchedProduct.getId());
        delivery.setProductMasterId(masterId);
        delivery.setQualityType(qualityType);
        delivery.setQuantity(defectQty.negate());
        delivery.setDeliveryType(DeliveryType.DEFECT_RETURN.getCode());
        delivery.setWarehouseId(warehouseId);
        delivery.setDeliveryDate(LocalDate.now());
        // 2026-09-21（用户口径「统一命名」）：本页把该动作叫「加工退货」，备注快照随之改（枚举 label 仍为「退不良」，
        // 因为物料收货页共用同一枚举，不能在这里改共享文案）
        delivery.setRemark("加工退货");
        delivery.setIsReverse(true);
        delivery.setStatus(DocStatus.DRAFT.getCode());
        baseMapper.insert(delivery);
        log.info("退不良记录已保存(草稿): id={}, qualityType={}", delivery.getId(), qualityType);
    }

    // ==================== 不关联加工单的加工退货（2026-09-21 用户口径） ====================

    /**
     * 不关联加工单的加工退货（P1-1 口径，2026-09-25 用户确认）：在本表写一条负数记录
     * （`delivery_type=DEFECT_RETURN`、`is_reverse=1`），审核时把退回成品以
     * **成品（加工退货）形态（stock_form=PRODUCT_DEFECT）转移进加工厂委外仓**；
     * 不红冲收货、不分解 BOM 料、不动应付。修好送回时走 P1-2 返回单（核销在厂成品 + 实际用料 + 赔料应收）。
     * <p>`order_id` 为空，`factory_id` 定位「工厂委外仓」；数量取绝对值落账。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void returnDefectNoOrder(Map<String, Object> body) {
        if (body == null) throw new BusinessException("加工退货数量不能为空");
        Long factoryId = body.get("factoryId") != null ? Long.valueOf(body.get("factoryId").toString()) : null;
        Long warehouseId = body.get("warehouseId") != null ? Long.valueOf(body.get("warehouseId").toString()) : null;
        Long masterId = body.get("productMasterId") != null ? Long.valueOf(body.get("productMasterId").toString()) : null;
        Object qtyObj = body.get("quantity");
        if (factoryId == null) throw new BusinessException("请选择加工厂");
        // 2026-09-21（用户口径）：加工退货的对象**只能**是「加工厂或供应商」（= /supplier/manage 里的
        //   加工厂/辅料商/方案商），**不能是供货商（成品商 product）** —— 供货商是成品采购的往来单位，
        //   推给它会让「还料 + 冲减应付」的对象错位。前端下拉已按 excludeSupplierType=product 过滤，
        //   这里再兜一道，防止直接调 API 绕过（前端筛选只是体验，业务规则必须在服务层成立）。
        Supplier returnTarget = supplierMapper.selectById(factoryId);
        if (returnTarget == null) throw new BusinessException("加工厂不存在");
        boolean targetIsVendor = supplierTypeRefMapper.selectList(
                        new LambdaQueryWrapper<SupplierTypeRef>().eq(SupplierTypeRef::getSupplierId, factoryId))
                .stream()
                .anyMatch(r -> SupplierTypeEnum.PRODUCT.getCode().equals(r.getTypeCode()));
        if (targetIsVendor)
            throw new BusinessException("「" + returnTarget.getName()
                    + "」是供货商（成品商）：加工退货只能退给加工厂或供应商");
        if (warehouseId == null) throw new BusinessException("请选择加工退货仓库");
        if (masterId == null) throw new BusinessException("请选择产品");
        if (qtyObj == null || qtyObj.toString().isBlank()) throw new BusinessException("加工退货数量不能为空");
        BigDecimal defectQty;
        try {
            defectQty = new BigDecimal(qtyObj.toString());
        } catch (NumberFormatException e) {
            throw new BusinessException("加工退货数量格式不正确：" + qtyObj);
        }
        if (defectQty.compareTo(BigDecimal.ZERO) <= 0) throw new BusinessException("加工退货数量必须大于0");
        String qualityType = body.get("qualityType") != null && !body.get("qualityType").toString().isBlank()
                ? body.get("qualityType").toString() : "A";
        if (!isValidQualityType(qualityType)) throw new BusinessException("非法的加工退货规格: " + qualityType);
        Warehouse wh = warehouseMapper.selectById(warehouseId);
        if (wh == null) throw new BusinessException("加工退货仓库不存在");
        Product master = productService.getById(masterId);
        if (master == null) throw new BusinessException("产品不存在");
        // 该规格库存校验（与有单红冲同一口径与同一文案）
        BigDecimal stockQty = stockQtyOf(masterId, warehouseId, qualityType);
        if (stockQty.compareTo(defectQty) < 0)
            throw new BusinessException(master.getName() + " " + qualityType + "规 仓库库存不足(库存:" + stockQty + "，退:" + defectQty + ")");
        // 工厂委外仓必须存在：与 F7-78/F7-81 同口径，无仓**显式报错**（否则还料被静默跳过 ⇒ 料账不符）
        resolveOutsourceWarehouseId(factoryId);

        OutsourceOrderDelivery d = new OutsourceOrderDelivery();
        // 2026-09-25：红冲记录自动单号（无单 GTW-，与有单 GTH- 区分），建草稿即取号
        d.setCode(generateReturnCode(false));
        d.setOrderId(null);            // ← 本意：不关联加工单
        d.setFactoryId(factoryId);     // 无单时靠它定位委外仓与应付对象
        d.setWarehouseId(warehouseId);
        d.setProductId(null);          // ⚠️ 必须留空：财务分析按 product_id join 加工单产品行取单价
        d.setProductMasterId(masterId);
        d.setQualityType(qualityType);
        d.setQuantity(defectQty.negate());
        d.setDeliveryType(DeliveryType.DEFECT_RETURN.getCode());
        d.setSourceType(SOURCE_RETURN_DEFECT);
        d.setIsReverse(true);
        d.setStatus(DocStatus.DRAFT.getCode());
        d.setDeliveryDate(LocalDate.now());
        Object remark = body.get("remark");
        d.setRemark(remark != null && !remark.toString().isBlank() ? remark.toString() : "加工退货（不关联加工单）");
        // 2026-09-27（用户口径）：工厂售后没有加工单 ⇒ 把 **BOM 快照落到本记录**上，
        //   工厂修好送回时（加工返回单）据此限定「实际用料」的可选范围。
        //   解析顺序：① 前端指定（人工换版本）→ ② **该产品在该工厂最近一次被加工单用过的快照** → ③ 留空
        //   （留空 = 返回时按"现场解析"兜底；仍解析不到 ⇒ 可选池为空 ⇒ 用料只能留空，不卡流程）。
        Long bomSnapshotId = body.get("bomSnapshotId") != null && !body.get("bomSnapshotId").toString().isBlank()
                ? Long.valueOf(body.get("bomSnapshotId").toString()) : null;
        if (bomSnapshotId == null) bomSnapshotId = recentOrderSnapshotId(factoryId, masterId);
        d.setBomSnapshotId(bomSnapshotId);
        baseMapper.insert(d);
        log.info("工厂售后（原无单加工退货）已保存(草稿): id={}, factoryId={}, masterId={}, qualityType={}, qty={}, bomSnapshotId={}",
                d.getId(), factoryId, masterId, qualityType, defectQty, bomSnapshotId);
    }

    /**
     * 「该产品在该工厂**最近一次被加工单用过**的 BOM 快照」（2026-09-27 用户确认的口径）。
     * <p>取不到（该产品没在这个工厂下过单/订单产品行没快照）返回 null，由调用方决定留空还是现场兜底。</p>
     */
    @Override
    public Long recentOrderSnapshotId(Long factoryId, Long masterId) {
        Map<Long, BomSnapshot> snaps = orderSnapshotsOf(factoryId, masterId);
        if (snaps.isEmpty()) return null;
        // orderSnapshotsOf 已按"最近使用的加工单"倒序 ⇒ 第一个即最近一次用过的快照
        return snaps.keySet().iterator().next();
    }

    /**
     * 该产品在该工厂**用过的 BOM 快照**（按最近使用的加工单倒序；LinkedHashMap 保序）：
     * key=snapshotId, value=快照实体。用于 ① 建单默认解析 ② 前端「换版本」下拉。
     */
    private Map<Long, BomSnapshot> orderSnapshotsOf(Long factoryId, Long masterId) {
        Map<Long, BomSnapshot> out = new LinkedHashMap<>();
        if (masterId == null) return out;
        List<OutsourceOrderProduct> rows = orderProductMapper.selectList(new LambdaQueryWrapper<OutsourceOrderProduct>()
                // ⚠️ outsource_order_product.product_id 存的就是**产品主数据ID**（见实体注释），不是订单产品行ID
                .eq(OutsourceOrderProduct::getProductId, masterId)
                .isNotNull(OutsourceOrderProduct::getBomSnapshotId));
        if (rows.isEmpty()) return out;
        // 一次批量取订单（判工厂 + 按订单 id 倒序 = 最近一次）
        java.util.Set<Long> orderIds = rows.stream().map(OutsourceOrderProduct::getOrderId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, OutsourceOrder> orderMap = orderIds.isEmpty() ? new LinkedHashMap<>()
                : orderMapper.selectBatchIds(orderIds).stream()
                        .collect(Collectors.toMap(OutsourceOrder::getId, o -> o, (a, b) -> a, LinkedHashMap::new));
        List<OutsourceOrderProduct> sorted = new ArrayList<>(rows);
        sorted.sort((a, b) -> {
            OutsourceOrder oa = orderMap.get(a.getOrderId()), ob = orderMap.get(b.getOrderId());
            long ia = oa != null ? oa.getId() : 0L, ib = ob != null ? ob.getId() : 0L;
            return Long.compare(ib, ia);
        });
        for (OutsourceOrderProduct r : sorted) {
            OutsourceOrder o = orderMap.get(r.getOrderId());
            if (o == null) continue;
            if (factoryId != null && !java.util.Objects.equals(o.getFactoryId(), factoryId)) continue;
            if (r.getBomSnapshotId() == null || out.containsKey(r.getBomSnapshotId())) continue;
            BomSnapshot s = bomSnapshotMapper.selectById(r.getBomSnapshotId());
            if (s != null) out.put(r.getBomSnapshotId(), s);
        }
        return out;
    }

    /**
     * 「新增工厂售后（原无单加工退货）」页的 BOM 快照候选（2026-09-27）：该产品在该工厂**用过的快照 + 版本/来源**，
     * 按最近使用的加工单倒序 ⇒ 第一项即默认值（与建单解析同口径）。
     */
    @Override
    public List<Map<String, Object>> productSnapshotOptions(Long factoryId, Long masterId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<Long, BomSnapshot> e : orderSnapshotsOf(factoryId, masterId).entrySet()) {
            BomSnapshot s = e.getValue();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("snapshotId", e.getKey());
            m.put("bomVersion", s.getBomVersion());
            m.put("kind", s.getKind());
            m.put("itemCount", s.getItemCount());
            m.put("productKey", s.getProductKey());
            out.add(m);
        }
        return out;
    }

    /**
     * 工厂售后（原无单加工退货）列表。
     * <p>⚠️ **兼容保留**：前端已改用 {@link #pageDefectReturns}（有单+无单一台台账）；
     * 本方法供既有回归脚本与外部调用继续使用。</p>
     */
    @Override
    public List<Map<String, Object>> listNoOrderReturns() {
        List<OutsourceOrderDelivery> rows = baseMapper.selectList(
                new LambdaQueryWrapper<OutsourceOrderDelivery>()
                        .eq(OutsourceOrderDelivery::getSourceType, SOURCE_RETURN_DEFECT)
                        .isNull(OutsourceOrderDelivery::getOrderId)
                        .orderByDesc(OutsourceOrderDelivery::getId)
                        .last("LIMIT 200"));
        List<Map<String, Object>> out = new ArrayList<>();
        for (OutsourceOrderDelivery d : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.getId());
            m.put("deliveryDate", d.getDeliveryDate());
            m.put("qualityType", d.getQualityType());
            m.put("quantity", d.getQuantity());
            m.put("status", d.getStatus());
            m.put("remark", d.getRemark());
            m.put("warehouseId", d.getWarehouseId());
            m.put("factoryId", d.getFactoryId());
            Product p = d.getProductMasterId() != null ? productService.getById(d.getProductMasterId()) : null;
            m.put("productName", p != null ? p.getName() : "");
            m.put("sku", p != null ? p.getSku() : "");
            Supplier s = d.getFactoryId() != null ? supplierMapper.selectById(d.getFactoryId()) : null;
            m.put("factoryName", s != null ? s.getName() : "");
            out.add(m);
        }
        return out;
    }

    /**
     * **加工退货台账**（2026-09-21 用户口径）：**有单 + 无单都在这张表里**。
     * <p>行 = 本表 `delivery_type=DEFECT_RETURN` 的记录 —— 有单红冲（挂加工单）与无单红冲
     * （{@link #returnDefectNoOrder}）**同表且字段同构**，前端只用「关联加工单」列区分
     * （有单回填加工单号、无单留空显示"未关联"）；审核/反审核/删除沿用通用端点，
     * 因此台账不需要任何"按来源分派动作"的分支。</p>
     * <p>名称一律**批量回填**（加工单号 / 产品 / 加工厂 / 仓库），避免逐行查询的 N+1。</p>
     * <p>⚠️「加工厂」取**有效值**：无单红冲记录自带 `factory_id`；**有单红冲历史上没写这一列**（2026-09-21 前），
     * 故回退到其关联加工单的加工厂 —— 否则台账该列会空（用户实测反馈）。新记录已改为写入，见
     * {@link #returnDefect}。</p>
     */
    @Override
    public Map<String, Object> pageDefectReturns(Integer pageNo, Integer size, String linked, String status, String code,
                                                 Long factoryId, Long productId, String qualityType, String returnProgress) {
        LambdaQueryWrapper<OutsourceOrderDelivery> qw = new LambdaQueryWrapper<OutsourceOrderDelivery>()
                .eq(OutsourceOrderDelivery::getDeliveryType, DeliveryType.DEFECT_RETURN.getCode());
        if ("WITH_ORDER".equalsIgnoreCase(linked)) qw.isNotNull(OutsourceOrderDelivery::getOrderId);
        else if ("WITHOUT_ORDER".equalsIgnoreCase(linked)) qw.isNull(OutsourceOrderDelivery::getOrderId);
        // 2026-09-28（用户口径「查询按钮前面没有输入框」）：筛选行加「单号」—— **模糊**匹配，
        // 输入 GTH-/GTW- 片段即可命中（存量无 code 的旧记录不会命中，符合预期）。
        if (code != null && !code.isBlank()) qw.like(OutsourceOrderDelivery::getCode, code.trim());
        // status 支持**逗号分隔**（2026-09-27 三级菜单口径）：
        //  「有效单据」页签 = DRAFT,AUDITED（排除已作废）；「已作废」页签 = CANCELLED；不传 = 全部。
        if (status != null && !status.isBlank()) {
            List<String> sts = Arrays.stream(status.split(",")).map(String::trim)
                    .filter(s -> !s.isEmpty()).collect(Collectors.toList());
            if (sts.size() == 1) qw.eq(OutsourceOrderDelivery::getStatus, sts.get(0));
            else if (sts.size() > 1) qw.in(OutsourceOrderDelivery::getStatus, sts);
        }
        // 返回进度页签（2026-09-27：「工厂售后」的 待返回 / 已返回完）——直接下推到 SQL，保证分页正确：
        // 已返回量 = Σ(已审核加工返回单，按来源退货单) ⇒ 与 PENDING/DONE 判定同源（见 RETURNED_QTY_SQL）。
        if (returnProgress != null && !returnProgress.isBlank()) {
            if ("DONE".equalsIgnoreCase(returnProgress)) {
                qw.apply(RETURNED_QTY_SQL + " >= ABS(outsource_order_delivery.quantity)");
            } else if ("PENDING".equalsIgnoreCase(returnProgress)) {
                qw.apply(RETURNED_QTY_SQL + " < ABS(outsource_order_delivery.quantity)");
            }
        }
        // 2026-09-27：加工返回单绑定来源单时用到的定位筛选（加工厂 + 产品 + 退货规格）
        if (factoryId != null) qw.eq(OutsourceOrderDelivery::getFactoryId, factoryId);
        if (productId != null) qw.eq(OutsourceOrderDelivery::getProductMasterId, productId);
        if (qualityType != null && !qualityType.isBlank()) qw.eq(OutsourceOrderDelivery::getQualityType, qualityType);
        qw.orderByDesc(OutsourceOrderDelivery::getId);

        Page<OutsourceOrderDelivery> pageResult = baseMapper.selectPage(
                new Page<>(pageNo == null || pageNo < 1 ? 1 : pageNo, size == null || size < 1 ? 10 : size), qw);
        List<OutsourceOrderDelivery> records = pageResult.getRecords();

        // 加工单号（有单才有）+ 该单的加工厂（有单红冲记录历史上不写 factory_id，这里兜底解析）
        Map<Long, String> orderCodeMap = new HashMap<>();
        Map<Long, Long> orderFactoryMap = new HashMap<>();
        List<Long> orderIds = records.stream().map(OutsourceOrderDelivery::getOrderId)
                .filter(id -> id != null).distinct().collect(Collectors.toList());
        if (!orderIds.isEmpty()) {
            for (OutsourceOrder o : orderMapper.selectBatchIds(orderIds)) {
                orderCodeMap.put(o.getId(), o.getCode());
                orderFactoryMap.put(o.getId(), o.getFactoryId());
            }
        }
        // 产品（主数据）
        Map<Long, Product> productMap = new HashMap<>();
        List<Long> masterIds = records.stream().map(OutsourceOrderDelivery::getProductMasterId)
                .filter(id -> id != null).distinct().collect(Collectors.toList());
        if (!masterIds.isEmpty()) {
            for (Product p : productService.listByIds(masterIds)) productMap.put(p.getId(), p);
        }
        // 加工厂（取有效值：记录自身 factory_id 缺失时回退到关联加工单的加工厂）
        Map<Long, String> factoryNameMap = new HashMap<>();
        List<Long> factoryIds = new ArrayList<>();
        for (OutsourceOrderDelivery d : records) {
            Long fid = effectiveFactoryId(d, orderFactoryMap);
            if (fid != null && !factoryIds.contains(fid)) factoryIds.add(fid);
        }
        if (!factoryIds.isEmpty()) {
            for (Supplier s : supplierMapper.selectBatchIds(factoryIds)) factoryNameMap.put(s.getId(), s.getName());
        }
        // 扣减的成品仓
        Map<Long, String> warehouseNameMap = new HashMap<>();
        List<Long> warehouseIds = records.stream().map(OutsourceOrderDelivery::getWarehouseId)
                .filter(id -> id != null).distinct().collect(Collectors.toList());
        if (!warehouseIds.isEmpty()) {
            for (Warehouse w : warehouseMapper.selectBatchIds(warehouseIds))
                warehouseNameMap.put(w.getId(), w.getWarehouseName());
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        // 2026-09-27：每行的「已返回 / 未返回」（按来源单聚合已审核返回单）——1 次批量查询，无 N+1
        Map<Long, BigDecimal> returnedMap = returnedQtyBySource(
                records.stream().map(OutsourceOrderDelivery::getId).filter(id -> id != null).collect(Collectors.toList()));
        // 2026-09-27：本页用到的 BOM 快照一次批量取（台账回显「BOM 快照 vN」；无 N+1）
        java.util.Set<Long> pageSnapIds = records.stream().map(OutsourceOrderDelivery::getBomSnapshotId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, BomSnapshot> pageSnaps = pageSnapIds.isEmpty() ? new LinkedHashMap<>()
                : bomSnapshotMapper.selectBatchIds(pageSnapIds).stream()
                        .collect(Collectors.toMap(BomSnapshot::getId, s -> s, (a, b) -> a, LinkedHashMap::new));
        for (OutsourceOrderDelivery d : records) {
            Product p = d.getProductMasterId() != null ? productMap.get(d.getProductMasterId()) : null;
            Long fid = effectiveFactoryId(d, orderFactoryMap);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.getId());
            // 2026-09-25：红冲记录自有单号（GTH-/GTW-，建草稿即取号）；存量无 code 的旧记录为空（详情/流水兜底"加工退货#id"）
            m.put("code", d.getCode());
            m.put("deliveryDate", d.getDeliveryDate());
            m.put("orderId", d.getOrderId());
            // 「关联加工单」列：有单给单号、无单为空（前端显示"未关联"）
            m.put("orderCode", d.getOrderId() != null ? orderCodeMap.get(d.getOrderId()) : null);
            m.put("factoryId", fid);
            m.put("factoryName", fid != null ? factoryNameMap.get(fid) : "");
            m.put("productMasterId", d.getProductMasterId());
            m.put("productName", p != null ? p.getName() : "");
            m.put("sku", p != null ? p.getSku() : "");
            m.put("qualityType", d.getQualityType());
            // 2026-09-27：工厂售后的 BOM 快照（返回时「实际用料」范围的依据）——台账回显版本/来源
            m.put("bomSnapshotId", d.getBomSnapshotId());
            BomSnapshot rowSnap = d.getBomSnapshotId() != null ? pageSnaps.get(d.getBomSnapshotId()) : null;
            if (rowSnap != null) {
                m.put("bomVersion", rowSnap.getBomVersion());
                m.put("bomKind", rowSnap.getKind());
            }
            m.put("quantity", d.getQuantity());
            m.put("warehouseId", d.getWarehouseId());
            m.put("warehouseName", d.getWarehouseId() != null ? warehouseNameMap.get(d.getWarehouseId()) : "");
            m.put("status", d.getStatus());
            // 2026-09-27「返回进度」列：已返回 / 未返回 / 进度标记（PENDING=未返回完 / DONE=已返回完）
            BigDecimal sent = d.getQuantity() != null ? d.getQuantity().abs() : BigDecimal.ZERO;
            BigDecimal returned = returnedMap.getOrDefault(d.getId(), BigDecimal.ZERO);
            BigDecimal unreturned = sent.subtract(returned);
            if (unreturned.compareTo(BigDecimal.ZERO) < 0) unreturned = BigDecimal.ZERO;
            m.put("returnedQty", returned);
            m.put("unreturnedQty", unreturned);
            m.put("returnProgress", (sent.compareTo(BigDecimal.ZERO) > 0 && returned.compareTo(sent) >= 0) ? "DONE" : "PENDING");
            m.put("remark", d.getRemark());
            rows.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", pageResult.getTotal());
        out.put("records", rows);
        return out;
    }

    /**
     * 台账「加工厂」取有效值：记录自身 `factory_id` 优先（"无单"红冲写入），缺失时回退到**关联加工单的加工厂**
     * （"有单"红冲在 2026-09-21 前不写该列 ⇒ 不回退该列就会显示为空）。
     */
    /**
     * 加工退货草稿**作废**（2026-09-27 用户口径）—— 三级菜单「已作废」页签的数据来源。
     * <p>取代"草稿物理删除"作为台账侧的标准动作：留痕、可查、可授权。已审核记录不在此列 ——
     * 撤销已落账的红冲必须走**反审核**（等量逆回，账务严格对称）。</p>
     * <p>⚠️ {@code deleteDelivery}（物理删除）保留，供历史脚本/兼容调用；页面不再暴露「删除」入口。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelDefectReturn(Long id) {
        OutsourceOrderDelivery d = baseMapper.selectById(id);
        if (d == null || !DeliveryType.DEFECT_RETURN.getCode().equals(d.getDeliveryType()))
            throw new BusinessException("加工退货记录不存在");
        if (!DocStatusGuard.claim(baseMapper, OutsourceOrderDelivery::getId, id,
                OutsourceOrderDelivery::getStatus, DocStatus.DRAFT.getCode(), DocStatus.CANCELLED.getCode())) {
            throw new BusinessException("只有草稿状态可作废（已审核的请先反审核）");
        }
        log.info("加工退货草稿已作废：id={}, code={}", id, d.getCode());
    }

    /**
     * 「已返回数量」子查询 SQL（2026-09-27）：按**来源退货单**聚合**已审核**加工返回单数量。
     * <p>用于台账的「返回进度」列与「待返回 / 已返回完」页签 —— 下推到 SQL 里判定，
     * 分页不会错位（若在内存里过滤，total 与页码都会失真）。</p>
     */
    private static final String RETURNED_QTY_SQL =
            "(SELECT COALESCE(SUM(rb.quantity), 0) FROM outsource_return_back rb"
                    + " WHERE rb.source_delivery_id = outsource_order_delivery.id AND rb.status = 'AUDITED')";

    /** 批量取「来源退货单ID → 已返回数量」（只计已审核返回单；1 次查询，无 N+1） */
    private Map<Long, BigDecimal> returnedQtyBySource(List<Long> sourceIds) {
        Map<Long, BigDecimal> map = new HashMap<>();
        if (sourceIds == null || sourceIds.isEmpty()) return map;
        for (Map<String, Object> sm : returnBackMapper.selectMaps(new QueryWrapper<OutsourceReturnBack>()
                .select("source_delivery_id AS sid", "COALESCE(SUM(quantity), 0) AS qty")
                .in("source_delivery_id", sourceIds)
                .eq("status", DocStatus.AUDITED.getCode())
                .groupBy("source_delivery_id"))) {
            Object sid = sm.get("sid") != null ? sm.get("sid") : sm.get("source_delivery_id");
            Object qty = sm.get("qty") != null ? sm.get("qty") : sm.get("QTY");
            if (sid == null) continue;
            map.put(Long.valueOf(sid.toString()), qty == null ? BigDecimal.ZERO : new BigDecimal(qty.toString()));
        }
        return map;
    }

    private Long effectiveFactoryId(OutsourceOrderDelivery d, Map<Long, Long> orderFactoryMap) {
        if (d.getFactoryId() != null) return d.getFactoryId();
        return d.getOrderId() != null ? orderFactoryMap.get(d.getOrderId()) : null;
    }

    /**
     * 加工退货**详情**（2026-09-21 用户口径「加工退货页面的列表也应该有详情」）：一条红冲收货记录的
     * 完整信息 + **它审核后实际动了什么**——
     * <ol>
     *   <li>扣减的成品：就是记录自身的 产品/规格/数量/扣减仓库（无需回溯）；</li>
     *   <li>还回工厂委外仓的物料：按库存流水回溯（`related_delivery_id = 本记录`、
     *       `related_bill_type = OUTSOURCE_DEFECT`、只取审核动作 `OUTSOURCE_DEFECT_RETURN`）；
     *       ——**仅工厂售后（GTW-）会为空**：该口径不拆 BOM 还料（见 P1-1），料在「加工返回单」按实际用料处理；</li>
     *   <li>{@code outsourceIn}：**工厂售后**独有的「成品转移进委外仓」腿（`OUTSOURCE_DEFECT_IN`），
     *       给出还入的委外仓 / 数量 / 形态；有单红冲没有这条腿 ⇒ 前端按字段有无显示；</li>
     *   <li>冲减的应付金额：`finance_payable` 里 `source_id = 本记录`、`source_bill_type = OUTSOURCE_DELIVERY`
     *       （负数=冲减应付）。</li>
     * </ol>
     * <p>②③ 只在**已审核**时有意义（草稿未落账、反审核会等量逆回），故仅当 status=AUDITED 才查；
     * 前端据此显示"未落账"。</p>
     */
    @Override
    public Map<String, Object> defectReturnDetail(Long id) {
        OutsourceOrderDelivery d = baseMapper.selectById(id);
        if (d == null || !DeliveryType.DEFECT_RETURN.getCode().equals(d.getDeliveryType()))
            throw new BusinessException("加工退货记录不存在");

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        // 2026-09-25：红冲记录自有单号（存量旧记录为空，前端显示"加工退货#id"兜底）
        m.put("code", d.getCode());
        m.put("legacyNo", "加工退货#" + d.getId());
        m.put("deliveryDate", d.getDeliveryDate());
        m.put("status", d.getStatus());
        m.put("createTime", d.getCreateTime());
        m.put("orderId", d.getOrderId());
        OutsourceOrder order = d.getOrderId() != null ? orderMapper.selectById(d.getOrderId()) : null;
        m.put("orderCode", order != null ? order.getCode() : null);
        // 加工厂与台账同口径：记录自身 factory_id 优先，缺失时回退到关联加工单
        Long factoryId = d.getFactoryId() != null ? d.getFactoryId()
                : (order != null ? order.getFactoryId() : null);
        m.put("factoryId", factoryId);
        m.put("factoryName", supplierNameOf(factoryId));
        Product p = d.getProductMasterId() != null ? productService.getById(d.getProductMasterId()) : null;
        m.put("productMasterId", d.getProductMasterId());
        m.put("productName", p != null ? p.getName() : "");
        m.put("sku", p != null ? p.getSku() : "");
        m.put("qualityType", d.getQualityType());
        // 2026-09-27：工厂售后的 BOM 快照（返回时「实际用料」可选范围的依据；有单红冲没有快照 ⇒ 为空）
        m.put("bomSnapshotId", d.getBomSnapshotId());
        BomSnapshot dSnap = d.getBomSnapshotId() != null ? bomSnapshotMapper.selectById(d.getBomSnapshotId()) : null;
        if (dSnap != null) {
            m.put("bomVersion", dSnap.getBomVersion());
            m.put("bomKind", dSnap.getKind());
        }
        m.put("quantity", d.getQuantity());
        m.put("warehouseId", d.getWarehouseId());
        m.put("warehouseName", warehouseNameOf(d.getWarehouseId()));
        m.put("remark", d.getRemark());
        m.put("sourceType", d.getSourceType());
        m.put("linked", d.getOrderId() != null);

        boolean audited = DocStatus.AUDITED.getCode().equals(d.getStatus());
        m.put("settled", audited);
        List<Map<String, Object>> materials = new ArrayList<>();
        BigDecimal payableAmount = BigDecimal.ZERO;
        String payableStatus = null;
        if (audited) {
            // ② 还回工厂委外仓的物料（审核动作的流水；反审核的等量逆回流水不算）
            for (WarehouseStockLog log : stockLogMapper.selectList(new LambdaQueryWrapper<WarehouseStockLog>()
                    .eq(WarehouseStockLog::getRelatedDeliveryId, d.getId())
                    .eq(WarehouseStockLog::getRelatedBillType, RelatedBillType.OUTSOURCE_DEFECT.getCode())
                    .eq(WarehouseStockLog::getChangeType, StockChangeType.OUTSOURCE_DEFECT_RETURN.getCode())
                    .isNotNull(WarehouseStockLog::getMaterialId)
                    .orderByAsc(WarehouseStockLog::getId))) {
                Map<String, Object> mm = new LinkedHashMap<>();
                mm.put("materialId", log.getMaterialId());
                mm.put("materialName", log.getMaterialName());
                mm.put("qualityType", log.getQualityType());
                mm.put("quantity", log.getChangeQuantity());
                mm.put("warehouseId", log.getWarehouseId());
                mm.put("warehouseName", warehouseNameOf(log.getWarehouseId()));
                materials.add(mm);
            }
            // ②' 工厂售后的「成品转移进委外仓」腿（2026-09-27 用户口径「详情要能看出成品/料到底落在哪个委外仓」）：
            // 有单红冲**没有**这条腿（它只扣成品 + BOM 还料 + 冲应付）⇒ 查不到记录时前端不显示该行。
            // ⚠️ 定位键用 **related_bill_id** 而不是 related_delivery_id：成品侧 changeStock 的第 8 参就是
            //    relatedBillId（`related_delivery_id` 是**物料**流水专用列，物料还料那条腿才用它，见上面 ② 的查询）。
            List<WarehouseStockLog> inLogs = stockLogMapper.selectList(new LambdaQueryWrapper<WarehouseStockLog>()
                    .eq(WarehouseStockLog::getRelatedBillId, d.getId())
                    .eq(WarehouseStockLog::getRelatedBillType, RelatedBillType.OUTSOURCE_DEFECT.getCode())
                    .eq(WarehouseStockLog::getChangeType, StockChangeType.OUTSOURCE_DEFECT_IN.getCode())
                    .orderByAsc(WarehouseStockLog::getId));
            if (!inLogs.isEmpty()) {
                WarehouseStockLog in = inLogs.get(0);
                Map<String, Object> oi = new LinkedHashMap<>();
                oi.put("warehouseId", in.getWarehouseId());
                oi.put("warehouseName", warehouseNameOf(in.getWarehouseId()));
                oi.put("quantity", in.getChangeQuantity());
                oi.put("stockForm", in.getStockForm());
                m.put("outsourceIn", oi);
            }
            // ③ 本记录产生的应付（负数=冲减）
            List<FinancePayable> payables = payableMapper.selectList(new LambdaQueryWrapper<FinancePayable>()
                    .eq(FinancePayable::getSourceId, d.getId())
                    .eq(FinancePayable::getSourceBillType, SourceBillType.OUTSOURCE_DELIVERY.getCode())
                    .orderByAsc(FinancePayable::getId));
            for (FinancePayable f : payables) {
                if (f.getAmount() != null) payableAmount = payableAmount.add(f.getAmount());
                if (payableStatus == null) payableStatus = f.getStatus();
            }
        }
        m.put("materials", materials);
        m.put("payableAmount", payableAmount);
        m.put("payableStatus", payableStatus);
        return m;
    }

    /** 加工厂名（单条查询，供详情用） */
    private String supplierNameOf(Long supplierId) {
        if (supplierId == null) return "";
        Supplier s = supplierMapper.selectById(supplierId);
        return s != null ? s.getName() : "";
    }

    /** 仓库名（单条查询，供详情用） */
    private String warehouseNameOf(Long warehouseId) {
        if (warehouseId == null) return "";
        Warehouse w = warehouseMapper.selectById(warehouseId);
        return w != null ? w.getWarehouseName() : "";
    }

    /**
     * 工厂售后（原无单加工退货）审核（2026-09-25 P1-1 口径，用户确认）：
     * 退回成品以「成品（加工退货）」形态（stock_form=PRODUCT_DEFECT）转移进**加工厂委外仓**；
     * **不分解料**（原"按最新 BOM 快照拆料还回"已删除——与退货时点无关，BOM 改过即拆错）、**不动应付**（原按还料 FIFO 价值的负应付已删除）。
     * <p>腿① 我方仓扣成品（普通成品行，形态 MATERIAL，与现状一致）；腿② 委外仓入成品（PRODUCT_DEFECT 形态，可与同仓同品质的普通成品行并存不串行）。</p>
     */
    private void applyDefectStockNoOrder(OutsourceOrderDelivery delivery) {
        BigDecimal defectQty = delivery.getQuantity().abs();
        Long warehouseId = delivery.getWarehouseId();
        Long factoryId = delivery.getFactoryId();
        if (warehouseId == null) throw new BusinessException("加工退货记录缺少仓库");
        if (factoryId == null) throw new BusinessException("加工退货记录缺少加工厂，无法确定委外仓库");
        Long masterId = masterIdOf(delivery);
        String qualityType = delivery.getQualityType() != null ? delivery.getQualityType() : "A";
        String billNo = noOrderBillNo(delivery);

        // 1. 我方仓扣减成品（转移出腿）
        stockService.changeStock(warehouseId, masterId, defectQty.negate(),
                StockChangeType.OUTSOURCE_DEFECT_RETURN, billNo, RelatedBillType.OUTSOURCE_DEFECT,
                "", null, qualityType);

        // 2. 委外仓入成品（成品（加工退货）形态，工厂责任在厂库存，可查询可盘点；P1-2 返回单核销）
        Long factoryWhId = resolveOutsourceWarehouseId(factoryId);
        stockService.changeStock(factoryWhId, masterId, defectQty,
                StockChangeType.OUTSOURCE_DEFECT_IN, billNo, RelatedBillType.OUTSOURCE_DEFECT,
                "", delivery.getId(), qualityType, WarehouseStock.FORM_PRODUCT_DEFECT);
        log.info("工厂售后（原无单加工退货）：成品转移进委外仓 仓={} masterId={} +{} 形态=PRODUCT_DEFECT 单据={}", factoryWhId, masterId, defectQty, billNo);
    }

    /** 工厂售后（原无单加工退货）反审核：与审核**严格对称**（F7-64 纪律：等量逆回，不夹零）——核销委外仓成品行 + 恢复我方仓成品 */
    private void revertDefectStockNoOrder(OutsourceOrderDelivery delivery) {
        BigDecimal defectQty = delivery.getQuantity().abs();
        Long warehouseId = delivery.getWarehouseId();
        Long factoryId = delivery.getFactoryId();
        if (warehouseId == null) throw new BusinessException("加工退货记录缺少仓库");
        if (factoryId == null) throw new BusinessException("加工退货记录缺少加工厂，无法确定委外仓库");
        Long masterId = masterIdOf(delivery);
        String qualityType = delivery.getQualityType() != null ? delivery.getQualityType() : "A";
        String billNo = noOrderBillNo(delivery);

        // 1. 我方仓恢复成品
        stockService.changeStock(warehouseId, masterId, defectQty,
                StockChangeType.OUTSOURCE_DEFECT_RETURN, billNo, RelatedBillType.OUTSOURCE_DEFECT,
                "", null, qualityType);

        // 2. 核销委外仓成品行（同形态 PRODUCT_DEFECT 精确逆回）
        Long factoryWhId = resolveOutsourceWarehouseId(factoryId);
        stockService.changeStock(factoryWhId, masterId, defectQty.negate(),
                StockChangeType.CANCEL_OUTSOURCE_DEFECT_IN, billNo, RelatedBillType.OUTSOURCE_DEFECT,
                "", delivery.getId(), qualityType, WarehouseStock.FORM_PRODUCT_DEFECT);
    }

    /** 工厂售后（原无单加工退货）的库存流水/应付「相关单据号」：用红冲记录自己的单号（GTW-）；存量无 code 的旧记录兜底"加工退货#id" */
    private String noOrderBillNo(OutsourceOrderDelivery delivery) {
        return redFlushBillNo(delivery);
    }

    /**
     * 红冲记录的流水单号（2026-09-25）：优先取自动生成的 code（GTH-/GTW-）；
     * 存量旧记录（无 code）兜底"加工退货#id"——保证旧单反审核时流水单号与历史审核完全一致（对账不断链）。
     */
    private String redFlushBillNo(OutsourceOrderDelivery delivery) {
        if (delivery.getCode() != null && !delivery.getCode().isBlank()) return delivery.getCode();
        return "加工退货#" + delivery.getId();
    }

    /**
     * 红冲记录取号（2026-09-25）：关联加工单 GTH- / 无单 GTW-，最大号 +1（BillNoSeq 统一口径，建草稿即取号）。
     */
    private String generateReturnCode(boolean hasOrder) {
        String prefix = (hasOrder ? BillPrefix.OUTSOURCE_DEFECT_RETURN_HAS : BillPrefix.OUTSOURCE_DEFECT_RETURN_NO)
                + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        OutsourceOrderDelivery last = baseMapper.selectOne(new LambdaQueryWrapper<OutsourceOrderDelivery>()
                .likeRight(OutsourceOrderDelivery::getCode, prefix)
                .orderByDesc(OutsourceOrderDelivery::getCode).last("LIMIT 1"));
        int seq = last != null && last.getCode() != null
                ? BillNoSeq.lastSeq(last.getCode(), prefix) + 1 : 1;
        return BillNoSeq.formatUnique(prefix, seq, cand -> baseMapper.selectCount(new LambdaQueryWrapper<OutsourceOrderDelivery>().eq(OutsourceOrderDelivery::getCode, cand)) > 0) /* F7-261 冲突检测+重试 */;
    }

    /** 校验退不良规格是否合法(A/B/C/DEFECT) */
    private boolean isValidQualityType(String qualityType) {
        return "A".equals(qualityType) || "B".equals(qualityType) || "C".equals(qualityType) || "DEFECT".equals(qualityType);
    }

    // ==================== 私有业务方法 ====================

    /** 交货生成应付（类型 OUTSOURCE_DELIVERY + 交货记录ID，与反审核冲销 reversePayable 精确匹配） */
    private void createDeliveryPayable(OutsourceOrder order, OutsourceOrderProduct product, OutsourceOrderDelivery delivery) {
        BigDecimal price = product.getUnitPrice() != null ? product.getUnitPrice() : BigDecimal.ZERO;
        BigDecimal amount = delivery.getQuantity().multiply(price);
        if (amount.compareTo(BigDecimal.ZERO) == 0) return;
        payableHelper.createPayable(order.getFactoryId(), SourceBillType.OUTSOURCE_DELIVERY.getCode(),
                order.getCode(), delivery.getId(), amount,
                delivery.getDeliveryDate() != null ? delivery.getDeliveryDate() : LocalDate.now(),
                "交货 - " + order.getCode() + " - " + product.getProductName());
    }

    /** 退不良审核：扣成品库存 + BOM还料 + 冲减应付 */
    private void applyDefectStock(OutsourceOrder order, OutsourceOrderDelivery delivery) {
        String productName = productNameOf(delivery.getOrderId(), delivery);
        BigDecimal defectQty = delivery.getQuantity().abs();
        Long warehouseId = delivery.getWarehouseId();
        if (warehouseId == null) throw new BusinessException("加工退货记录缺少仓库");

        // 1. 扣减成品库存（按产品主数据ID+规格落账）；流水单号用红冲记录自己的单号（GTH-，存量兜底#id）
        Long masterId = masterIdOf(delivery);
        String qualityType = delivery.getQualityType() != null ? delivery.getQualityType() : "A";
        String billNo = redFlushBillNo(delivery);   // 2026-09-25：不再与普通交货混用加工单号
        stockService.changeStock(warehouseId, masterId, defectQty.negate(),
                StockChangeType.OUTSOURCE_DEFECT_RETURN, billNo, RelatedBillType.OUTSOURCE_DEFECT,
                "", order.getId(), qualityType);   // F7-65①（2026-09-20）：spec 按 WarehouseStockService 约定传 ""（原传 null）
        log.info("退不良扣成品: {} {}规 (仓库={}) {} -> {}", productName, qualityType, warehouseId, stockQtyOf(masterId, warehouseId, qualityType), stockQtyOf(masterId, warehouseId, qualityType).subtract(defectQty));

        // 2. 冲减应付（负数交货 → 负数应付）
        OutsourceOrderProduct matchedProduct =
                findOrderProduct(orderService.getProducts(delivery.getOrderId()), delivery);
        if (matchedProduct != null) createDeliveryPayable(order, matchedProduct, delivery);

        // 3. 拆BOM → 物料还回工厂委外仓库
        Long whId = resolveOutsourceWarehouseId(order);
        List<MaterialReq> materials = loadMaterialRequirements(matchedProduct != null ? matchedProduct : new OutsourceOrderProduct());
        for (MaterialReq mat : materials) {
            if (mat.materialId() == null) continue;
            // 2026-09-16 数量一律为整数：单套用量(比率) × 交货数量 → 取整
            BigDecimal restoreQty = mat.perUnit().multiply(defectQty).setScale(0, RoundingMode.HALF_UP);

            // 物料写入统一到 WarehouseStockService（架构债 A2）
            stockService.changeMaterialStockAllowNegative(whId, mat.materialId(), restoreQty,
                    StockChangeType.OUTSOURCE_DEFECT_RETURN.getCode(), billNo, RelatedBillType.OUTSOURCE_DEFECT,
                    delivery.getId(), order.getId(), delivery.getId());
            log.info("退不良还料: {} +{} (仓库ID={})", mat.materialName(), restoreQty, whId);
        }
    }

    /** 退不良反审核：逆向成品库存 + 扣回BOM还料 + 删应付 */
    private void revertDefectStock(OutsourceOrder order, OutsourceOrderDelivery delivery) {
        String productName = productNameOf(delivery.getOrderId(), delivery);
        BigDecimal defectQty = delivery.getQuantity().abs();
        Long warehouseId = delivery.getWarehouseId();
        if (warehouseId == null) return;

        // 1. 恢复成品库存（按产品主数据ID+规格落账）；流水单号与审核侧同源（红冲自有单号，存量兜底#id）
        String qualityType = delivery.getQualityType() != null ? delivery.getQualityType() : "A";
        String billNo = redFlushBillNo(delivery);
        stockService.changeStock(warehouseId, masterIdOf(delivery), defectQty,
                StockChangeType.OUTSOURCE_DEFECT_RETURN, billNo, RelatedBillType.OUTSOURCE_DEFECT,
                "", order.getId(), qualityType);   // F7-65①（2026-09-20）：spec 按 WarehouseStockService 约定传 ""（原传 null）
        log.info("退不良反审核恢复成品: {} {}规 (仓库={}) +{}", productName, qualityType, warehouseId, defectQty);

        // 2. 扣回BOM还料
        Long whId = resolveOutsourceWarehouseId(order);
        OutsourceOrderProduct matchedProduct = findOrderProduct(orderService.getProducts(delivery.getOrderId()), delivery);
        List<MaterialReq> materials = loadMaterialRequirements(matchedProduct != null ? matchedProduct : new OutsourceOrderProduct());
        for (MaterialReq mat : materials) {
            if (mat.materialId() == null) continue;
            BigDecimal restoreQty = mat.perUnit().multiply(defectQty).setScale(0, RoundingMode.HALF_UP);
            // F7-64（2026-09-20）：**与审核侧 applyDefectStock 严格对称** —— 反审核按**等量**扣回，
            // 允许扣成负数。原实现用 `min(应还, 当前库存)` 夹零 ⇒ 若还回的料已被消耗，只扣回剩余
            // ⇒ 委外仓物料**永久多出**（多出的量恰是 `应还 − 当前`），且不报错。审核侧本就是
            // `changeMaterialStockAllowNegative(+restoreQty)`，反审核必须能完全逆转它。
            // 库存写入统一到 WarehouseStockService（架构债 A2）
            stockService.changeMaterialStockAllowNegative(whId, mat.materialId(), restoreQty.negate(),
                    StockChangeType.OUTSOURCE_DEFECT_RETURN_UN_AUDIT.getCode(), billNo,
                    RelatedBillType.OUTSOURCE_DEFECT, delivery.getId(), order.getId(), delivery.getId());
        }
    }

    /** 查询某成品在某仓库的库存总量（用于日志展示） */
    private BigDecimal stockQtyOf(Long productId, Long warehouseId) {
        LambdaQueryWrapper<WarehouseStock> stockW = new LambdaQueryWrapper<WarehouseStock>()
                .eq(WarehouseStock::getWarehouseId, warehouseId)
                .eq(WarehouseStock::getProductId, productId);
        return stockMapper.selectList(stockW)
                .stream().map(s -> s.getQuantity() != null ? s.getQuantity() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 查询某成品在某仓库某规格的库存（用于日志展示） */
    private BigDecimal stockQtyOf(Long productId, Long warehouseId, String qualityType) {
        LambdaQueryWrapper<WarehouseStock> stockW = new LambdaQueryWrapper<WarehouseStock>()
                .eq(WarehouseStock::getWarehouseId, warehouseId)
                .eq(WarehouseStock::getProductId, productId)
                .eq(WarehouseStock::getQualityType, qualityType);
        return stockMapper.selectList(stockW)
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
                if ("FACTORY".equals(mat.getSupplyType())) continue; // 工厂包料（包工包料）不扣我方仓
                // F2-3（2026-09-18 审核修复）：优先**直取**视图的 quantity_per_set（精确值），
                // 不再用「整单需求 ÷ 产品数量」反算 —— 后者在单套用量为小数时会被视图的 ROUND 放大/漂移。
                // F7-60（2026-09-20）：收敛到 MaterialRequirementCalc.perUnit，与列表页/详情页/结单同一口径。
                BigDecimal perUnit = MaterialRequirementCalc.perUnit(
                        mat.getQuantityPerSet(), mat.getDemandQuantity(), productQty);
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

    /** 获取交货记录关联的产品主数据ID(product.id)，为 null 时抛异常，防止库存落错账 */
    private Long masterIdOf(OutsourceOrderDelivery delivery) {
        if (delivery.getProductMasterId() == null) {
            throw new BusinessException("收货记录(id=" + delivery.getId() + ")未关联产品主数据，请先反审核后重新录入收货");
        }
        return delivery.getProductMasterId();
    }

    /** 根据 productId 从产品列表中查找产品名称 */
    private String getProductNameByProductId(Long orderId, Long productId) {
        if (orderId == null || productId == null) return "";
        List<OutsourceOrderProduct> products = orderService.getProducts(orderId);
        return products.stream()
                .filter(p -> productId.equals(p.getId()))
                .map(p -> p.getProductName() != null ? p.getProductName() : "")
                .findFirst().orElse("");
    }

    /**
     * 按「产品行ID 或 产品主数据ID」定位加工单里的产品行。
     * <p>前端新增交货时传的是**产品行ID**（订单明细行 id）；产品主数据ID(product.id)用于跨编辑稳定匹配。</p>
     */
    private OutsourceOrderProduct matchProduct(List<OutsourceOrderProduct> products, Long productRowId, Long productMasterId) {
        if (products == null) return null;
        if (productRowId != null) {
            for (OutsourceOrderProduct p : products) {
                if (productRowId.equals(p.getId())) return p;
            }
        }
        if (productMasterId != null) {
            for (OutsourceOrderProduct p : products) {
                if (productMasterId.equals(p.getProductId())) return p;
            }
        }
        return null;
    }

    /**
     * 交货记录归属的订单产品行。
     * <p><b>以产品主数据ID为准</b>：加工单整单编辑会「删除产品明细再重建」，产品行 id 会变化，
     * 历史交货记录若按行ID关联会悬空；产品主数据ID(product.id)稳定，不受编辑影响。
     * 仅当历史数据缺失主数据ID时才回退按行ID匹配。</p>
     */
    private OutsourceOrderProduct findOrderProduct(List<OutsourceOrderProduct> products, OutsourceOrderDelivery d) {
        if (products == null || d == null) return null;
        if (d.getProductMasterId() != null) {
            OutsourceOrderProduct p = matchProduct(products, null, d.getProductMasterId());
            if (p != null) return p;
        }
        return matchProduct(products, d.getProductId(), null);
    }

    /** 判断交货记录是否属于某订单产品行（主数据ID优先，行ID兜底） */
    /**
     * P3-1（2026-09-25）：有单红冲**审核期**额度复核 —— 修复并发双草稿窗口（建草稿校验与审核落账之间无复核）。
     * <p>口径：以 <b>AUDITED 落账净额</b>为准（草稿不占额度）：deliveredQty = Σ 已审核普通交货数量，
     * returnedQty = Σ 已审核 DEFECT_RETURN 负数量绝对值（本单经 DocStatusGuard 抢占后已是 AUDITED，天然含入）。
     * 与建草稿校验（returnDefect :508-520，含草稿的宽松预检）有意不同：审核期是最终闸门，账面不允许超退。</p>
     * <p>并发安全：先对加工单行 FOR UPDATE 串行化"并发审核"，聚合读也用锁定读（FOR UPDATE，
     * 锁定读总是读**最新已提交版本**，绕开 REPEATABLE READ 快照旧读）⇒ 后审者必然看到先审者已提交的红冲量。</p>
     */
    private void assertDefectReturnWithinDelivered(OutsourceOrder order, OutsourceOrderDelivery delivery) {
        orderMapper.selectOne(new LambdaQueryWrapper<OutsourceOrder>()
                .eq(OutsourceOrder::getId, order.getId()).last("FOR UPDATE"));
        OutsourceOrderProduct matched = findOrderProduct(orderService.getProducts(order.getId()), delivery);
        if (matched == null)
            throw new BusinessException("加工单中未找到该产品（红冲记录 productId=" + delivery.getProductId() + "）");
        List<OutsourceOrderDelivery> all = baseMapper.selectList(new LambdaQueryWrapper<OutsourceOrderDelivery>()
                .eq(OutsourceOrderDelivery::getOrderId, order.getId())
                .eq(OutsourceOrderDelivery::getStatus, DocStatus.AUDITED.getCode())
                .last("FOR UPDATE"));
        BigDecimal deliveredQty = all.stream()
                .filter(d -> belongsToProduct(d, matched) && !Boolean.TRUE.equals(d.getIsReverse()))
                .map(d -> d.getQuantity() != null ? d.getQuantity() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal returnedQty = all.stream()
                .filter(d -> belongsToProduct(d, matched)
                        && DeliveryType.DEFECT_RETURN.getCode().equals(d.getDeliveryType()))
                .map(d -> d.getQuantity() != null && d.getQuantity().signum() < 0 ? d.getQuantity().abs() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (returnedQty.compareTo(deliveredQty) > 0)
            throw new BusinessException("累计加工退货数量(" + returnedQty + ")不能超过已收数量(" + deliveredQty + ")，请先撤销或调减其他红冲单");
    }

    private boolean belongsToProduct(OutsourceOrderDelivery d, OutsourceOrderProduct p) {
        if (d == null || p == null) return false;
        if (d.getProductMasterId() != null && p.getProductId() != null)
            return d.getProductMasterId().equals(p.getProductId());
        return p.getId() != null && p.getId().equals(d.getProductId());
    }

    /**
     * F2-2（2026-09-18 审核修复）：交货审核前复核「累计交货（含本次）≤ 该产品计划数量」。
     * <p>原先只有前端展示 `remainingQuantity`（{@link #summary(Long)}），接口层可超计划量交货。</p>
     * <p>⚠️ 统计"已交"时**必须排除本单** —— claim 已把本单置为 AUDITED，不排除就会把自己算进去
     * （批 1 F1-1 的同类坑：按全额交货会被误判为超交）。</p>
     */
    private void assertNotOverPlanned(OutsourceOrder order, OutsourceOrderDelivery delivery) {
        BigDecimal thisQty = delivery.getQuantity() != null ? delivery.getQuantity() : BigDecimal.ZERO;
        if (thisQty.compareTo(BigDecimal.ZERO) <= 0) return;
        OutsourceOrderProduct matched = findOrderProduct(orderService.getProducts(order.getId()), delivery);
        if (matched == null) return; // 由审核主体另行报"加工单中未找到该产品"
        BigDecimal planned = matched.getQuantity() != null ? matched.getQuantity() : BigDecimal.ZERO;
        if (planned.compareTo(BigDecimal.ZERO) <= 0) return;
        BigDecimal delivered = BigDecimal.ZERO;
        for (OutsourceOrderDelivery d : baseMapper.selectList(new LambdaQueryWrapper<OutsourceOrderDelivery>()
                .eq(OutsourceOrderDelivery::getOrderId, order.getId())
                .eq(OutsourceOrderDelivery::getStatus, DocStatus.AUDITED.getCode())
                .ne(OutsourceOrderDelivery::getId, delivery.getId()))) {
            if (!belongsToProduct(d, matched)) continue;
            delivered = delivered.add(d.getQuantity() != null ? d.getQuantity() : BigDecimal.ZERO);
        }
        if (delivered.add(thisQty).compareTo(planned) > 0) {
            String pn = matched.getProductName() != null ? matched.getProductName() : ("#" + matched.getId());
            throw new BusinessException("收货数量超过该产品剩余待收量（产品[" + pn + "]计划 "
                    + planned.stripTrailingZeros().toPlainString()
                    + "、已收 " + delivered.stripTrailingZeros().toPlainString()
                    + "、本次 " + thisQty.stripTrailingZeros().toPlainString() + "）");
        }
    }

    /** 交货记录的产品名（按记录自身归属解析，兼容产品行重建） */
    private String productNameOf(Long orderId, OutsourceOrderDelivery d) {
        OutsourceOrderProduct p = findOrderProduct(orderService.getProducts(orderId), d);
        return p != null && p.getProductName() != null ? p.getProductName() : "";
    }

    /**
     * 查找工厂的**委外仓**ID（F7-74，2026-09-20：口径收紧为 `warehouse_category = OUTSOURCE`）。
     *
     * <p>原实现只按 `factory_id` 查、取第 0 个 ⇒ 若该工厂名下还有非委外仓（如成品仓），
     * 会**取到错误的仓**（还料/扣料写进成品仓）。现网每个工厂仅有 1 个 OUTSOURCE 仓 ⇒ 行为不变，
     * 但语义确定性由"约定"变成"显式过滤"。多个委外仓时仍取第 0 个（见报告"待业务确认项"）。</p>
     *
     * <p>无委外仓时**显式抛错**（原为 `log.warn + return null`，会让调用方静默跳过还料/扣料 ⇒ 料账不符，
     * 口径对齐 F7-78）。</p>
     */
    private Long resolveOutsourceWarehouseId(OutsourceOrder order) {
        if (order.getFactoryId() == null)
            throw new BusinessException("加工单缺少加工厂，无法确定委外仓库");
        return resolveOutsourceWarehouseId(order.getFactoryId());
    }

    /** 同上，但按**工厂ID**（不关联加工单的加工退货用，2026-09-21） */
    private Long resolveOutsourceWarehouseId(Long factoryId) {
        if (factoryId == null)
            throw new BusinessException("缺少加工厂，无法确定委外仓库");
        List<Warehouse> warehouses = warehouseMapper.selectList(
                new LambdaQueryWrapper<Warehouse>()
                        .eq(Warehouse::getFactoryId, factoryId)
                        .eq(Warehouse::getWarehouseCategory, WarehouseCategory.OUTSOURCE.getCode())
                        .orderByAsc(Warehouse::getId));
        if (warehouses.isEmpty()) {
            log.warn("工厂(ID={})无委外仓库", factoryId);
            throw new BusinessException("该加工厂未配置委外仓库，请先在【委外仓库】页面为该工厂创建委外仓库");
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
            // 2026-09-16 数量一律为整数：单套用量(比率) × 交货数量 → 取整（缺料判断与提示都用整数）
            // F7-60（2026-09-20）：收敛到 MaterialRequirementCalc.need（全模块同一取整口径）
            BigDecimal needed = MaterialRequirementCalc.need(mat.perUnit(), deliveryQty);

            // 2026-09-28：补 stock_form（同 OutsourceOrderController.materialStock 的修复）—— 委外仓同一物料
            // 可能同时存在正常账(MATERIAL)与在厂维修账(MATERIAL_REPAIR)，漏形态会让 selectOne 查到 2 行直接 500。
            WarehouseStock stock = stockMapper.selectOne(
                    new LambdaQueryWrapper<WarehouseStock>()
                            .eq(WarehouseStock::getWarehouseId, whId)
                            .eq(WarehouseStock::getMaterialId, mat.materialId())
                            .eq(WarehouseStock::getStockForm, WarehouseStock.FORM_MATERIAL)
                            .eq(WarehouseStock::getQualityType, QualityType.GOOD.getCode()));
            BigDecimal currentStock = stock != null && stock.getQuantity() != null ? stock.getQuantity() : BigDecimal.ZERO;

            if (currentStock.compareTo(needed) < 0) {
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("materialName", mat.materialName());
                s.put("needed", needed.setScale(0, RoundingMode.HALF_UP));
                s.put("stock", currentStock.setScale(0, RoundingMode.HALF_UP));
                s.put("gap", needed.subtract(currentStock).setScale(0, RoundingMode.HALF_UP));
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

    /**
     * 执行物料扣减（允许负数）；返回本批耗用材料总成本（按物料加权成本计价，供产品成本归集）。
     * @param deliveryId 交货记录ID —— F1（2026-09-17）：写入流水 related_delivery_id / related_bill_id，保证可回溯
     */
    private BigDecimal applyMaterialDeduction(OutsourceOrder order, OutsourceOrderProduct product,
                                        BigDecimal deliveryQty, String productName, Long deliveryId) {
        List<MaterialReq> materials = loadMaterialRequirements(product);
        if (materials.isEmpty()) {
            log.warn("产品「{}」无物料需求，跳过物料扣除", productName);
            return BigDecimal.ZERO;
        }
        Long whId = resolveOutsourceWarehouseId(order);
        if (whId == null) {
            log.warn("无法确定委外仓库，跳过物料扣除 (factoryId={})", order.getFactoryId());
            return BigDecimal.ZERO;
        }

        BigDecimal totalMaterialCost = BigDecimal.ZERO;
        for (MaterialReq mat : materials) {
            if (mat.materialId() == null) {
                log.warn("物料「{}」在委外物料表中未找到，跳过扣减", mat.materialName());
                continue;
            }
            BigDecimal needed = mat.perUnit().multiply(deliveryQty).setScale(0, RoundingMode.HALF_UP);
            // 材料成本取物料**完全成本**（= 自身移动加权成本 + 子物料完全成本 × 用量 ×(1+损耗率)，递归展开）——
            // 2026-10-09：原先只取自身 cost_price ⇒ 装配件（如「排线 X60Pro」含子物料「触摸IC S3909」）
            // 的子物料成本被完全忽略（实测 7.0000 应为 11.1724），并沿「物料→加工单→产品成本」一路放大。
            // 未建立成本时的退回参考价口径不变（已收敛进 CostService.materialFullCost）。
            BigDecimal matUnitCost = costService.materialFullCost(mat.materialId());
            totalMaterialCost = totalMaterialCost.add(matUnitCost.multiply(needed));
            // 物料写入统一到 WarehouseStockService（架构债 A2）：本方法是"允许负数"口径
            //（forceDelivery 可忽略缺料继续出库），故用 changeMaterialStockAllowNegative，不做充足校验
            stockService.changeMaterialStockAllowNegative(whId, mat.materialId(), needed.negate(),
                    StockChangeType.OUTSOURCE_CONSUME.getCode(), order.getCode(), RelatedBillType.OUTSOURCE_ORDER,
                    deliveryId, order.getId(), deliveryId);
            log.info("扣减物料: {} x{} (仓库ID={})", mat.materialName(), needed.setScale(2, RoundingMode.HALF_UP), whId);
        }
        return totalMaterialCost;
    }

    /** 回滚交货记录的物料扣减（加回委外仓库） */
    private void revertDeliveryStock(OutsourceOrder order, OutsourceOrderDelivery delivery) {
        OutsourceOrderProduct matchedProduct = findOrderProduct(orderService.getProducts(delivery.getOrderId()), delivery);
        if (matchedProduct == null) return;

        // 先回滚成品入库：与审核侧 addInventoryStock 对称。
        // 必须放在下面的早退之前——成品入库回滚不应受「有无BOM物料」「有无委外仓」影响，
        // 否则包工包料(无BOM)场景下反审核只冲应付、不回滚库存，导致账实不符。
        if (delivery.getWarehouseId() != null) {
            revertInventoryStock(delivery, order.getCode());
        }

        List<MaterialReq> materials = loadMaterialRequirements(matchedProduct);
        if (materials.isEmpty()) return;

        Long whId = resolveOutsourceWarehouseId(order);
        if (whId == null) return;

        BigDecimal oldQty = delivery.getQuantity() != null ? delivery.getQuantity() : BigDecimal.ZERO;
        for (MaterialReq mat : materials) {
            if (mat.materialId() == null) continue;
            BigDecimal toRestore = mat.perUnit().multiply(oldQty).setScale(0, RoundingMode.HALF_UP);

            // 物料写入统一到 WarehouseStockService（架构债 A2）：回补为正数，仍走"允许负数"口径以保持与领料侧对称
            stockService.changeMaterialStockAllowNegative(whId, mat.materialId(), toRestore,
                    StockChangeType.CANCEL_OUTSOURCE_CONSUME.getCode(), order.getCode(), RelatedBillType.OUTSOURCE_ORDER,
                    delivery.getId(), order.getId(), delivery.getId());
            log.info("回滚物料: {} +{}", mat.materialName(), toRestore.setScale(2, RoundingMode.HALF_UP));
        }
    }

    /** 增加收货入库库存：按等级拆分子项分别入对应等级库存行（流水关联加工单号） */
    private void addInventoryStock(OutsourceOrderDelivery delivery, String orderCode) {
        String productName = getProductNameByProductId(delivery.getOrderId(), delivery.getProductId());
        log.info("开始入库: warehouseId={}, productName={}, productId={}, 总量={}, A={}, B={}, C={}, 不良={}",
                delivery.getWarehouseId(), productName, delivery.getProductId(), delivery.getQuantity(),
                delivery.getAQty(), delivery.getBQty(), delivery.getCQty(), delivery.getDefectQty());
        LinkedHashMap<String, BigDecimal> gradeMap = new LinkedHashMap<>();
        gradeMap.put(ProductQualityType.A.getCode(), delivery.getAQty());
        gradeMap.put(ProductQualityType.B.getCode(), delivery.getBQty());
        gradeMap.put(ProductQualityType.C.getCode(), delivery.getCQty());
        gradeMap.put(ProductQualityType.DEFECT.getCode(), delivery.getDefectQty());
        for (Map.Entry<String, BigDecimal> entry : gradeMap.entrySet()) {
            BigDecimal qty = entry.getValue();
            if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) continue;
            stockService.changeStock(delivery.getWarehouseId(), masterIdOf(delivery),
                    qty, StockChangeType.OUTSOURCE_FINISH_IN, orderCode, RelatedBillType.OUTSOURCE_ORDER,
                    "", delivery.getId(), entry.getKey());   // F7-65①（2026-09-20）：spec 按约定传 ""（原传 null）
        }
        log.info("入库完成: warehouseId={}, productId={}, orderCode={}", delivery.getWarehouseId(), delivery.getProductId(), orderCode);
    }

    /** 回滚收货入库（按等级拆分回滚） */
    private void revertInventoryStock(OutsourceOrderDelivery delivery, String orderCode) {
        String productName = getProductNameByProductId(delivery.getOrderId(), delivery.getProductId());
        LinkedHashMap<String, BigDecimal> gradeMap = new LinkedHashMap<>();
        gradeMap.put(ProductQualityType.A.getCode(), delivery.getAQty());
        gradeMap.put(ProductQualityType.B.getCode(), delivery.getBQty());
        gradeMap.put(ProductQualityType.C.getCode(), delivery.getCQty());
        gradeMap.put(ProductQualityType.DEFECT.getCode(), delivery.getDefectQty());
        for (Map.Entry<String, BigDecimal> entry : gradeMap.entrySet()) {
            BigDecimal qty = entry.getValue();
            if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) continue;
            stockService.changeStock(delivery.getWarehouseId(), masterIdOf(delivery),
                    qty.negate(), StockChangeType.OUTSOURCE_ROLLBACK, orderCode, RelatedBillType.OUTSOURCE_ORDER,
                    "", delivery.getId(), entry.getKey());   // F7-65①（2026-09-20）：spec 按约定传 ""（原传 null）
            log.info("回滚成品库存[{}]: warehouseId={}, product={}, qty=-{}", entry.getKey(), delivery.getWarehouseId(), productName, qty);
        }
    }

    /**
     * 数量口径：返回「净已收 + 本次超出订单产品数量」的说明串；未超出返回 {@code null}。
     * <p>forceDelivery 只豁免"物料库存不足仍继续出库"，<b>不豁免</b>数量口径。</p>
     * <p><b>2026-09-29 用户口径「加工订单和物料订单都可以超量收货」</b>：本方法由原来的
     * {@code assertDeliveryWithinOrderQty}（超量直接抛错）改为**只判定、不抛错** ——
     * 调用方（create/update）拿到说明串后：未确认 ⇒ 返回"待确认超收"响应（前端二次确认，不落库）；
     * 已确认（{@code overReceipt=true}）⇒ 落库并把 {@code over_receipt} 置 1。
     * 审核期的 {@link #assertNotOverPlanned} 对已确认的行放行、未确认的行仍拦。</p>
     * <p>统计口径与 {@link #summary(Long)} 一致：同订单+同产品、未作废（含草稿，避免多张草稿叠加超量）；
     * excludeId 供编辑场景排除自身。</p>
     * <p><b>2026-09-29（用户口径，加工单 WO-20260928010 实测报错）</b>：可收上限必须按<b>净已收</b>算 ——
     * 退不良/退货的红冲行（{@code delivery_type=DEFECT_RETURN}、{@code quantity} 为负、{@code is_reverse=1}）
     * 要把额度**还回去**。原实现只累加 {@code delivery_type=DELIVERY} 且 quantity&gt;0 的行 ⇒
     * "收了 100 又退了 100"的单仍被认为已收满 100，第二批货永远收不进来
     * （报「累计收货量(100 + 本次10 = 110)超出订单数量(100)」）。
     * 该口径与 {@link #summary(Long)}（全量带符号求和，界面「已收/剩余」早就是净额）以及审核期
     * {@link #assertNotOverPlanned}（同样是带符号求和）**三者一致**，本次只把建草稿这道补齐。</p>
     */
    /**
     * F7-58（2026-09-20）：**草稿写入口的共用入参校验** —— {@code createDelivery} 与 {@code updateDelivery}
     * 必须**同判**（原实现 create 全套校验、update 只查状态与数量上限，直调接口即可绕过）。
     *
     * <p>校验项：① 交货数量 &gt; 0；② 至少一个等级 &gt; 0；③ 各等级之和 == 交货总量；
     * ④ **入库仓库必填**（原 update 完全不查，而 audit 侧是 {@code if (warehouseId != null)} 才入库
     * ⇒ 可"审核通过但成品不入库，而扣料与应付照落"）。</p>
     *
     * <p>与前端一致：{@code order/delivery.vue} 提交时恒 {@code quantity = 各等级之和}、且已强制选择收货仓库
     * ⇒ 这些校验对正常 UI 无感，只拦直调接口的畸形载荷。</p>
     */
    private void validateDraftPayload(OutsourceOrderDelivery delivery) {
        if (delivery.getQuantity() == null || delivery.getQuantity().compareTo(BigDecimal.ZERO) <= 0)
            throw new BusinessException("收货数量必须大于0");
        BigDecimal a = delivery.getAQty() == null ? BigDecimal.ZERO : delivery.getAQty();
        BigDecimal b = delivery.getBQty() == null ? BigDecimal.ZERO : delivery.getBQty();
        BigDecimal c = delivery.getCQty() == null ? BigDecimal.ZERO : delivery.getCQty();
        BigDecimal d = delivery.getDefectQty() == null ? BigDecimal.ZERO : delivery.getDefectQty();
        BigDecimal gradeSum = a.add(b).add(c).add(d);
        if (gradeSum.compareTo(BigDecimal.ZERO) <= 0)
            throw new BusinessException("请至少填写一个等级的数量（A规/B规/C规/不良）");
        if (gradeSum.compareTo(delivery.getQuantity()) != 0)
            throw new BusinessException("各等级数量之和(" + gradeSum + ")必须等于收货总数量(" + delivery.getQuantity() + ")");
        if (delivery.getWarehouseId() == null)
            throw new BusinessException("入库仓库不能为空");
    }

    private String overOrderQtyDetail(Long orderId, OutsourceOrderProduct product,
                                      BigDecimal qty, Long excludeId) {
        BigDecimal orderQty = product.getQuantity() != null ? product.getQuantity() : BigDecimal.ZERO;
        if (orderQty.compareTo(BigDecimal.ZERO) <= 0) return null;
        List<OutsourceOrderDelivery> list = baseMapper.selectList(
                new LambdaQueryWrapper<OutsourceOrderDelivery>()
                        .eq(OutsourceOrderDelivery::getOrderId, orderId)
                        .ne(OutsourceOrderDelivery::getStatus, DocStatus.CANCELLED.getCode()));
        BigDecimal delivered = BigDecimal.ZERO;   // 普通交货（正数）
        BigDecimal returned = BigDecimal.ZERO;    // 退货/退不良红冲（负数量取其绝对值）
        for (OutsourceOrderDelivery d : list) {
            if (excludeId != null && excludeId.equals(d.getId())) continue;
            // F7-59（2026-09-20）：关联判定统一为 belongsToProduct（**产品主数据ID 优先、产品行ID 兜底**），
            // 与 summary / assertNotOverPlanned 同一份逻辑。
            // 原实现用 `.eq(productId, product.getId())`（**只按产品行ID**）⇒ 加工单编辑会重建产品行 ⇒
            // 历史交货记录的 product_id 是旧行ID ⇒ 累计已交量算不到 ⇒ **可重复交满/超交**。
            if (!belongsToProduct(d, product)) continue;
            BigDecimal q = d.getQuantity() != null ? d.getQuantity() : BigDecimal.ZERO;
            // 2026-09-29：红冲行（quantity<0 或 is_reverse=1）算作"退货"，从额度里扣掉 ⇒ 上限按净额判。
            if (q.signum() < 0 || Boolean.TRUE.equals(d.getIsReverse())) returned = returned.add(q.abs());
            else delivered = delivered.add(q);
        }
        BigDecimal net = delivered.subtract(returned);
        BigDecimal thisQty = qty != null ? qty : BigDecimal.ZERO;
        BigDecimal total = net.add(thisQty);
        if (total.compareTo(orderQty) <= 0) return null;
        // 说明里带上「已收 − 退货」的净额推导，用户一眼能看出额度为什么不是毛收货量；
        // ⚠️ 必须保留「超出订单数量」字样 —— verify-fix-f7-59-60-77.ps1 / verify-order-receive-net-cap.ps1
        //    按该子串判定"超量（现在=待确认超收）"。
        String detail = returned.signum() > 0
                ? (plainQty(delivered) + " − 退货" + plainQty(returned) + " = 净" + plainQty(net)
                    + " + 本次" + plainQty(thisQty) + " = " + plainQty(total))
                : (plainQty(net) + " + 本次" + plainQty(thisQty) + " = " + plainQty(total));
        return "累计收货量(" + detail + ")超出订单数量(" + plainQty(orderQty) + ")";
    }

    /**
     * 超收**待确认**响应（2026-09-29 用户口径「加工订单和物料订单都可以超量收货」）：
     * 与缺料确认同一形状（{@code canProceed=false} + {@code message}），但用 {@code overReceipt=true}
     * 标记"这是超量待确认"（前端据此弹「确认超收」，确认后带 {@code overReceipt=true} 重提）。
     * <p>刻意不落库：未确认的超收单不该在库里留下痕迹（与 item/handler 一致）。</p>
     */
    private Map<String, Object> overReceiptResp(OutsourceOrderProduct product, String detail) {
        String pn = product.getProductName() != null ? product.getProductName() : ("#" + product.getId());
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("canProceed", false);
        resp.put("overReceipt", true);
        resp.put("message", detail + "，是否确认超收？");
        resp.put("productName", pn);
        return resp;
    }

    /** 超收已确认？（over_receipt=1 表示录入时用户已二次确认超收 ⇒ 审核期不再复核数量口径） */
    private static boolean isOverConfirmed(Integer flag) {
        return flag != null && flag == 1;
    }

    /** 数量展示（去尾零，避免报错里出现 100.000000） */
    private static String plainQty(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).stripTrailingZeros().toPlainString();
    }
}
