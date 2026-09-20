package com.beichen.erp.outsource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.common.BillPrefix;
import com.beichen.erp.common.DocStatus;
import com.beichen.erp.dev.entity.Project;
import com.beichen.erp.dev.mapper.ProjectMapper;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.common.DocStatusGuard;
import com.beichen.erp.material.entity.Product;
import com.beichen.erp.material.mapper.ProductMapper;
import com.beichen.erp.material.service.ProductService;
import com.beichen.erp.outsource.entity.OutsourceMaterial;
import com.beichen.erp.outsource.entity.OutsourceOrder;
import com.beichen.erp.outsource.entity.OutsourceOrderDelivery;
import com.beichen.erp.outsource.entity.OutsourceOrderMaterial;
import com.beichen.erp.outsource.entity.OutsourceOrderProduct;
import com.beichen.erp.outsource.mapper.OutsourceOrderDeliveryMapper;
import com.beichen.erp.outsource.mapper.OutsourceOrderMapper;
import com.beichen.erp.outsource.mapper.OutsourceOrderMaterialMapper;
import com.beichen.erp.outsource.mapper.OutsourceOrderProductMapper;
import com.beichen.erp.outsource.mapper.OutsourceMaterialMapper;
import com.beichen.erp.outsource.common.MaterialRequirementCalc;
import com.beichen.erp.outsource.common.OutsourceOrderStatus;
import com.beichen.erp.outsource.common.QualityType;
import com.beichen.erp.outsource.service.BomSnapshotService;
import com.beichen.erp.outsource.service.OutsourceOrderDeliveryService;
import com.beichen.erp.outsource.service.OutsourceOrderService;
import com.beichen.erp.supplier.common.SupplierTypeEnum;
import com.beichen.erp.supplier.entity.Supplier;
import com.beichen.erp.supplier.entity.SupplierTypeRef;
import com.beichen.erp.supplier.mapper.SupplierMapper;
import com.beichen.erp.supplier.mapper.SupplierTypeRefMapper;
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
    private final OutsourceOrderDeliveryMapper orderDeliveryMapper;
    private final OutsourceMaterialMapper outsourceMaterialMapper;
    private final SupplierMapper supplierMapper;
    private final SupplierTypeRefMapper supplierTypeRefMapper;
    /** 产品主数据Mapper(product表)，与加工单产品明细Mapper(productMapper)区分 */
    private final ProductMapper masterProductMapper;
    private final ProductService productService;
    private final ProjectMapper projectMapper;
    private final JdbcTemplate jdbcTemplate;
    /** BOM 快照服务（2026-09-17）：下单时按「产品 + 研发BOM版本 + 明细内容」复用/新建快照 */
    private final BomSnapshotService bomSnapshotService;
    /** 交货记录服务与本服务互相依赖，使用 @Lazy 字段注入打破循环依赖 */
    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private OutsourceOrderDeliveryService outsourceOrderDeliveryService;

    @Override
    public Page<Map<String, Object>> page(String status, Long factoryId, String code, int pageNum, int pageSize) {
        LambdaQueryWrapper<OutsourceOrder> w = new LambdaQueryWrapper<OutsourceOrder>()
                .eq(factoryId != null, OutsourceOrder::getFactoryId, factoryId)
                // F7-65⑥（2026-09-20）：单号查询改为**模糊**匹配，与其它列表页一致（原为精确 eq）
                .like(code != null && !code.isBlank(), OutsourceOrder::getCode, code)
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
            m.put("supplyMode", o.getSupplyMode());
            m.put("factoryId", o.getFactoryId());
            m.put("planStartDate", o.getPlanStartDate()); m.put("planEndDate", o.getPlanEndDate());
            m.put("actualStartDate", o.getActualStartDate()); m.put("actualEndDate", o.getActualEndDate());
            m.put("taxIncluded", o.getTaxIncluded()); m.put("taxRate", o.getTaxRate());
            m.put("totalAmount", o.getTotalAmount()); m.put("remark", o.getRemark());
            m.put("createTime", o.getCreateTime());
            // 合同文件地址（详情页「合同文件」上传），列表页「下载合同」按钮据此判断与下载
            m.put("attachUrl", o.getAttachUrl());
            if (o.getFactoryId() != null) {
                Supplier sup = supplierMapper.selectById(o.getFactoryId());
                m.put("factoryName", sup != null ? sup.getName() : "");
            }
            // 产品信息
            List<OutsourceOrderProduct> products = productMapper.selectList(
                    new LambdaQueryWrapper<OutsourceOrderProduct>().eq(OutsourceOrderProduct::getOrderId, o.getId()));
            m.put("productCount", (long) products.size());
            // 列表汇总展示：回填 SKU 后与名称一起拼串，便于一眼识别具体型号
            productService.fillSku(products, OutsourceOrderProduct::getProductId, OutsourceOrderProduct::setSku);
            m.put("productNames", products.stream()
                    .map(p -> p.getProductName() != null ? p.getProductName() : "")
                    .filter(s -> !s.isEmpty())
                    .collect(java.util.stream.Collectors.joining(" / ")));
            m.put("productSkus", products.stream()
                    .map(p -> p.getSku() != null ? p.getSku() : "")
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

        // ===== 是否缺料（2026-09-17 新增列表列）=====
        // 口径与交货时的缺料拦截完全一致（OutsourceOrderDeliveryServiceImpl#checkMaterialShortages:742）：
        //   逐物料需求 = Σ(单套用量 × 该产品剩余待交量)，单套用量 = outsource_order_material.demand_quantity ÷ 产品行计划数量；
        //   比对 = 该加工厂委外仓(warehouse.factory_id = 加工单.factory_id)的良品库存；任一物料「需求 > 库存」= 缺料。
        //   供料方为「工厂包」(supply_type=FACTORY) 的物料不参与（与交货口径一致）。
        //   剩余待交 = 计划数量 − Σ已审核交货量（退不良 quantity 为负，累加即冲减）。
        // 仅 PENDING/PRODUCING 计算；FINISHED/CANCELLED 置 null（前端显示「—」）。
        if (!orderIds.isEmpty()) {
            String ph = orderIds.stream().map(id -> "?").collect(Collectors.joining(","));
            Object[] orderParams = orderIds.toArray();
            // 1) 产品行（计划数量 + 所属订单/工厂/状态 + 产品主数据ID）
            List<Map<String, Object>> prodRows = jdbcTemplate.queryForList(
                    "SELECT p.id AS pid, p.product_id AS master_id, p.order_id AS oid, p.quantity AS plan_qty, "
                            + "o.factory_id AS fid, o.status AS st "
                            + "FROM outsource_order_product p JOIN outsource_order o ON o.id = p.order_id "
                            + "WHERE p.order_id IN (" + ph + ")", orderParams);
            // 2) 已审核交货量（**按产品主数据ID聚合**）
            //    F7-59（2026-09-20）：原为 `GROUP BY product_id`（**只按产品行ID**）⇒ 加工单编辑会重建产品行，
            //    历史交货记录的 product_id 是旧行ID ⇒ 已交量算不到 ⇒ 列表"剩余待交"偏大、缺料判定与
            //    交货上限口径不一致。现与交货侧统一：**主数据ID优先**（缺失时先按行ID反查主数据ID，再退回行ID）。
            Map<Long, BigDecimal> deliveredByProduct = new HashMap<>();
            for (Map<String, Object> r : jdbcTemplate.queryForList(
                    "SELECT COALESCE(d.product_master_id, op.product_id, d.product_id) AS pkey, SUM(d.quantity) AS delivered "
                            + "FROM outsource_order_delivery d "
                            + "LEFT JOIN outsource_order_product op ON op.id = d.product_id "
                            + "WHERE d.order_id IN (" + ph + ") AND d.status = '" + DocStatus.AUDITED.getCode() + "' "
                            + "GROUP BY pkey", orderParams)) {
                if (r.get("pkey") == null) continue;
                deliveredByProduct.put(((Number) r.get("pkey")).longValue(),
                        r.get("delivered") != null ? new BigDecimal(r.get("delivered").toString()) : BigDecimal.ZERO);
            }
            // 3) 物料清单（按产品行）：[materialId, demandQuantity]
            Map<Long, List<Object[]>> matsByProduct = new HashMap<>();
            List<Long> prodIds = prodRows.stream().filter(r -> r.get("pid") != null)
                    .map(r -> ((Number) r.get("pid")).longValue()).distinct().collect(Collectors.toList());
            if (!prodIds.isEmpty()) {
                String pph = prodIds.stream().map(id -> "?").collect(Collectors.joining(","));
                for (Map<String, Object> r : jdbcTemplate.queryForList(
                        "SELECT product_id, outsource_material_id, demand_quantity, quantity_per_set, supply_type "
                                + "FROM outsource_order_material WHERE product_id IN (" + pph + ")", prodIds.toArray())) {
                    if (r.get("product_id") == null || r.get("outsource_material_id") == null) continue;
                    if ("FACTORY".equals(r.get("supply_type"))) continue;
                    BigDecimal dem = r.get("demand_quantity") != null
                            ? new BigDecimal(r.get("demand_quantity").toString()) : BigDecimal.ZERO;
                    BigDecimal qps = r.get("quantity_per_set") != null
                            ? new BigDecimal(r.get("quantity_per_set").toString()) : null;
                    matsByProduct.computeIfAbsent(((Number) r.get("product_id")).longValue(), k -> new ArrayList<>())
                            .add(new Object[]{((Number) r.get("outsource_material_id")).longValue(), dem, qps});
                }
            }
            // 4) 各工厂委外仓良品库存：factoryId -> materialId -> 库存
            Map<Long, Map<Long, BigDecimal>> stockByFactory = new HashMap<>();
            List<Long> factoryIds = prodRows.stream().filter(r -> r.get("fid") != null)
                    .map(r -> ((Number) r.get("fid")).longValue()).distinct().collect(Collectors.toList());
            if (!factoryIds.isEmpty()) {
                String fph = factoryIds.stream().map(id -> "?").collect(Collectors.joining(","));
                for (Map<String, Object> r : jdbcTemplate.queryForList(
                        "SELECT w.factory_id AS fid, s.material_id AS mid, SUM(s.quantity) AS qty "
                                + "FROM warehouse_stock s JOIN warehouse w ON w.id = s.warehouse_id "
                                + "WHERE w.factory_id IN (" + fph + ") AND s.material_id IS NOT NULL "
                                + "AND s.quality_type = '" + QualityType.GOOD.getCode()
                                + "' GROUP BY w.factory_id, s.material_id", factoryIds.toArray())) {
                    if (r.get("fid") == null || r.get("mid") == null) continue;
                    stockByFactory.computeIfAbsent(((Number) r.get("fid")).longValue(), k -> new HashMap<>())
                            .merge(((Number) r.get("mid")).longValue(),
                                    r.get("qty") != null ? new BigDecimal(r.get("qty").toString()) : BigDecimal.ZERO,
                                    BigDecimal::add);
                }
            }
            // 5) 逐单判定：需求先按物料合并再比库存（避免"每产品单看够、合起来不够"漏判）
            Map<Long, String> orderStatus = new HashMap<>();
            Map<Long, Long> orderFactory = new HashMap<>();
            Map<Long, Map<Long, BigDecimal>> needByOrder = new HashMap<>();
            for (Map<String, Object> r : prodRows) {
                Long oid = r.get("oid") != null ? ((Number) r.get("oid")).longValue() : null;
                Long pid = r.get("pid") != null ? ((Number) r.get("pid")).longValue() : null;
                if (oid == null || pid == null) continue;
                orderStatus.put(oid, r.get("st") != null ? r.get("st").toString() : null);
                orderFactory.put(oid, r.get("fid") != null ? ((Number) r.get("fid")).longValue() : null);
                BigDecimal planQty = r.get("plan_qty") != null
                        ? new BigDecimal(r.get("plan_qty").toString()) : BigDecimal.ZERO;
                // F7-59：已交量按「产品主数据ID」对齐（缺失时才退回产品行ID），与交货上限/summary 同一口径
                Long masterId = r.get("master_id") != null ? ((Number) r.get("master_id")).longValue() : null;
                BigDecimal deliveredQty = deliveredByProduct.getOrDefault(masterId != null ? masterId : pid, BigDecimal.ZERO);
                BigDecimal remaining = planQty.subtract(deliveredQty);
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) continue;
                BigDecimal denom = planQty.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ONE : planQty;
                for (Object[] mat : matsByProduct.getOrDefault(pid, List.of())) {
                    // F7-60：与交货侧同一口径（quantity_per_set 优先、反算 6 位、结果取整）
                    BigDecimal perUnit = MaterialRequirementCalc.perUnit((BigDecimal) mat[2], (BigDecimal) mat[1], denom);
                    BigDecimal need = MaterialRequirementCalc.need(perUnit, remaining);
                    if (need.compareTo(BigDecimal.ZERO) <= 0) continue;
                    needByOrder.computeIfAbsent(oid, k -> new HashMap<>())
                            .merge((Long) mat[0], need, BigDecimal::add);
                }
            }
            for (Map<String, Object> rec : result.getRecords()) {
                Long oid = (Long) rec.get("id");
                String st = orderStatus.get(oid);
                boolean applicable = OutsourceOrderStatus.PENDING.getCode().equals(st)
                        || OutsourceOrderStatus.PRODUCING.getCode().equals(st);
                if (!applicable) { rec.put("materialShortage", null); continue; }
                Map<Long, BigDecimal> needMap = needByOrder.getOrDefault(oid, Map.of());
                Map<Long, BigDecimal> stockMap = stockByFactory.getOrDefault(orderFactory.get(oid), Map.of());
                boolean shortage = false;
                for (Map.Entry<Long, BigDecimal> en : needMap.entrySet()) {
                    BigDecimal stock = stockMap.getOrDefault(en.getKey(), BigDecimal.ZERO);
                    if (stock.compareTo(en.getValue()) < 0) { shortage = true; break; }
                }
                rec.put("materialShortage", shortage);
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
        List<OutsourceOrderProduct> items = productMapper.selectList(
                new LambdaQueryWrapper<OutsourceOrderProduct>().eq(OutsourceOrderProduct::getOrderId, orderId));
        // 回填 SKU（非表字段），前端免查库即可展示
        productService.fillSku(items, OutsourceOrderProduct::getProductId, OutsourceOrderProduct::setSku);
        return items;
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
        assertFactory(order.getFactoryId());
        // 无产品的加工单无法交货，属无效数据：创建时即拦截（前端表单同样要求至少一行产品）
        if (products == null || products.isEmpty()) throw new BusinessException("至少需要一个加工产品");
        order.setCode(generateCode());
        order.setStatus(OutsourceOrderStatus.PENDING.getCode());
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
            // 回填关联产品主数据ID(product.id)，交货/库存落账使用
            p.setProductId(resolveProductMasterId(p));
            if (cid != null && cid > 0) p.setCompanyId(cid);
            // BOM 快照（2026-09-17 重构）：按「产品 + 研发BOM版本 + 明细内容」复用/新建，
            // 研发BOM未变化时多张加工单共享同一份快照，不再每次下单都生成一份明细
            p.setBomSnapshotId(bomSnapshotService.resolveSnapshot(
                    p.getProductId(), p.getProductName(), p.getProjectId(), p.getQuantity(), p.getMaterials(), cid));
            productMapper.insert(p);
        }
        // 更新总金额与税额
        OutsourceOrder update = new OutsourceOrder();
        update.setId(order.getId());
        update.setTotalAmount(total);
        update.setTaxAmount(calcTaxAmount(total, order.getTaxIncluded(), order.getTaxRate()));
        orderMapper.updateById(update);
    }

    /** 税额拆分（单价含税口径）：打开含税时从含税总额中按税率拆出税额 = total × rate/(100+rate) */
    private BigDecimal calcTaxAmount(BigDecimal total, Integer taxIncluded, BigDecimal taxRate) {
        if (!Integer.valueOf(1).equals(taxIncluded) || taxRate == null || taxRate.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        // F7-65④（2026-09-20）：改用 RoundingMode 枚举 —— `BigDecimal.ROUND_HALF_UP` 自 Java 9 起已废弃
        BigDecimal rate = taxRate.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP);
        return total.multiply(rate).divide(BigDecimal.ONE.add(rate), 2, RoundingMode.HALF_UP);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(OutsourceOrder order, List<OutsourceOrderProduct> newProducts) {
        OutsourceOrder old = orderMapper.selectById(order.getId());
        if (old == null) throw new BusinessException("加工单不存在");
        // F7-61（2026-09-20）：**状态白名单** —— 只有待审核(PENDING)才可整单编辑。
        // 原实现只拦 CANCELLED ⇒ 已审核(PRODUCING)/已完工(FINISHED)也能进本方法，而本方法会
        // "删掉全部产品行再重建"（下面 productMapper.delete + 逐行 insert）⇒ **已交货的加工单可被换掉
        // 产品明细**，而交货/库存/应付早已按旧行落账 ⇒ 单实不符。前端产品行虽 disabled，但「保存」
        // 按钮对 PRODUCING 仍可点（`order/detail.vue`），故必须服务端拦。
        if (!OutsourceOrderStatus.PENDING.getCode().equals(old.getStatus()))
            throw new BusinessException("只有待审核的加工单可以编辑（当前状态：" + old.getStatus()
                    + "）；已审核的请先反审核");

        // 校验口径与 create 一致：编辑时未传 factoryId 则沿用原值；空产品会清空全部明细，同样拦截
        Long factoryId = order.getFactoryId() != null ? order.getFactoryId() : old.getFactoryId();
        if (factoryId == null) throw new BusinessException("加工厂不能为空");
        assertFactory(factoryId);
        if (newProducts == null || newProducts.isEmpty()) throw new BusinessException("至少需要一个加工产品");

        // 删除旧产品行（2026-09-17 重构：物料明细已改为共享 BOM 快照，不能再随单删除——否则会波及别的加工单）
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
            // 回填关联产品主数据ID(product.id)，交货/库存落账使用
            p.setProductId(resolveProductMasterId(p));
            // BOM 快照：编辑加工单同样走"复用/新建"判定，未变化则沿用既有快照
            p.setBomSnapshotId(bomSnapshotService.resolveSnapshot(
                    p.getProductId(), p.getProductName(), p.getProjectId(), p.getQuantity(), p.getMaterials(), old.getCompanyId()));
            productMapper.insert(p);
        }
        // 更新总金额与税额
        OutsourceOrder amountUpdate = new OutsourceOrder();
        amountUpdate.setId(order.getId());
        amountUpdate.setTotalAmount(total);
        amountUpdate.setTaxAmount(calcTaxAmount(total, order.getTaxIncluded(), order.getTaxRate()));
        orderMapper.updateById(amountUpdate);
    }

    /**
     * 解析加工产品关联的产品主数据ID(product.id)。
     * 优先级：前端直传productId > 项目关联(dev_project.product_id) > 按产品名称匹配product表。
     */
    @Override
    public Long resolveProductMasterId(OutsourceOrderProduct p) {
        if (p == null) return null;
        if (p.getProductId() != null) return p.getProductId();
        if (p.getProjectId() != null) {
            Project proj = projectMapper.selectById(p.getProjectId());
            if (proj != null && proj.getProductId() != null) return proj.getProductId();
        }
        if (p.getProductName() != null && !p.getProductName().isBlank()) {
            Product product = masterProductMapper.selectOne(new LambdaQueryWrapper<Product>()
                    .eq(Product::getName, p.getProductName()).last("LIMIT 1"));
            if (product != null) return product.getId();
        }
        return null;
    }

    /**
     * 校验加工厂：必须存在且具备 factory 类型标签。
     * <p>前端加工厂下拉已按 supplierType=factory 过滤，此处为后端兜底——
     * 否则可直接用辅料商/成品商/方案商建单，导致委外单挂错往来主体
     * （应付主体类型按单据写死为 factory，与实际供应商类型不符）。</p>
     */
    private void assertFactory(Long factoryId) {
        Supplier s = supplierMapper.selectById(factoryId);
        if (s == null) throw new BusinessException("加工厂不存在");
        Long cnt = supplierTypeRefMapper.selectCount(new LambdaQueryWrapper<SupplierTypeRef>()
                .eq(SupplierTypeRef::getSupplierId, factoryId)
                .eq(SupplierTypeRef::getTypeCode, SupplierTypeEnum.FACTORY.getCode()));
        if (cnt == null || cnt == 0)
            throw new BusinessException("供应商「" + s.getName() + "」不是加工厂类型，请选择加工厂");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        OutsourceOrder order = orderMapper.selectById(id);
        if (order == null) throw new BusinessException("加工单不存在");
        // 原子抢占状态（P2-29 补齐：加工单此前漏改）：并发/双击审核只有一次生效
        if (!DocStatusGuard.claim(orderMapper, OutsourceOrder::getId, id, OutsourceOrder::getStatus,
                OutsourceOrderStatus.PENDING.getCode(), OutsourceOrderStatus.PRODUCING.getCode()))
            throw new BusinessException("只有待审核状态可以审核");
        OutsourceOrder update = new OutsourceOrder();
        update.setId(id);
        update.setStatus(OutsourceOrderStatus.PRODUCING.getCode());
        update.setActualStartDate(LocalDate.now());
        orderMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unaudit(Long id) {
        OutsourceOrder order = orderMapper.selectById(id);
        if (order == null) throw new BusinessException("加工单不存在");
        // 原子抢占状态（P2-29 补齐）：并发/双击反审核只有一次生效
        if (!DocStatusGuard.claim(orderMapper, OutsourceOrder::getId, id, OutsourceOrder::getStatus,
                OutsourceOrderStatus.PRODUCING.getCode(), OutsourceOrderStatus.PENDING.getCode()))
            throw new BusinessException("只有生产中状态可以反审核");

        // 检查是否有已审核的交货记录
        List<OutsourceOrderDelivery> deliveries = orderDeliveryMapper.selectList(
                new LambdaQueryWrapper<OutsourceOrderDelivery>()
                        .eq(OutsourceOrderDelivery::getOrderId, id)
                        .eq(OutsourceOrderDelivery::getStatus, DocStatus.AUDITED.getCode()));

        if (!deliveries.isEmpty()) {
            // 已审核交货记录：统一反审核，逆向库存/成品流水/应付，回到草稿
            // 应付冲销由交货单反审核内部调用 PayableHelper.reversePayable 完成，
            // 此处不再重复手写 SQL（原 SQL 字段名 source_type / 枚举值 PENDING 均错误，已删除）
            for (OutsourceOrderDelivery delivery : deliveries) {
                outsourceOrderDeliveryService.unaudit(delivery.getId());
            }
        }

        // 状态回退
        OutsourceOrder update = new OutsourceOrder();
        update.setId(id);
        update.setStatus(OutsourceOrderStatus.PENDING.getCode());
        update.setActualStartDate(null);
        orderMapper.updateById(update);
    }

    /** 根据委外物料ID查询名称，用于展示回填（ID关联查询替代冗余name字段） */
    private String getMaterialNameById(Long materialId) {
        if (materialId == null) return "";
        OutsourceMaterial m = outsourceMaterialMapper.selectById(materialId);
        return m != null ? m.getMaterialName() : "";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        OutsourceOrder order = orderMapper.selectById(id);
        if (order == null) throw new BusinessException("加工单不存在");
        // P2-33 边界矩阵：已取消必须显式拒绝 —— 下面 claim 的 from=当前状态，对已取消单据 from==to
        // 会匹配成功（等同无操作），导致"重复作废"返回 200 而非拒绝
        if (OutsourceOrderStatus.CANCELLED.getCode().equals(order.getStatus()))
            throw new BusinessException("已取消状态不可重复取消");
        // 原子抢占状态（P2-29 补齐）：from 取当前状态（除已取消外均可作废），并发双击只生效一次；
        // 其后的"清理草稿交货单"级联删除也因此被串行化保护
        if (!DocStatusGuard.claim(orderMapper, OutsourceOrder::getId, id, OutsourceOrder::getStatus,
                order.getStatus(), OutsourceOrderStatus.CANCELLED.getCode()))
            throw new BusinessException("已取消状态不可重复取消");
        // 存在已审核交货单时禁止直接作废，须先反审核交货单以保证成品库存/应付账实闭环（与 unaudit 对称）
        List<OutsourceOrderDelivery> audited = orderDeliveryMapper.selectList(
                new LambdaQueryWrapper<OutsourceOrderDelivery>()
                        .eq(OutsourceOrderDelivery::getOrderId, id)
                        .eq(OutsourceOrderDelivery::getStatus, DocStatus.AUDITED.getCode()));
        if (!audited.isEmpty()) throw new BusinessException("加工单存在已审核交货单，请先反审核后再取消");
        // 草稿态交货单不影响库存/账务，但订单作废后会成为孤儿记录，此处一并清理
        List<OutsourceOrderDelivery> drafts = orderDeliveryMapper.selectList(
                new LambdaQueryWrapper<OutsourceOrderDelivery>()
                        .eq(OutsourceOrderDelivery::getOrderId, id)
                        .eq(OutsourceOrderDelivery::getStatus, DocStatus.DRAFT.getCode()));
        for (OutsourceOrderDelivery d : drafts) {
            orderDeliveryMapper.deleteById(d.getId());
        }
        OutsourceOrder update = new OutsourceOrder();
        update.setId(id);
        update.setStatus(OutsourceOrderStatus.CANCELLED.getCode());
        orderMapper.updateById(update);
    }

    private String generateCode() {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String likePattern = BillPrefix.OUTSOURCE_ORDER + dateStr;
        LambdaQueryWrapper<OutsourceOrder> w = new LambdaQueryWrapper<OutsourceOrder>()
                .likeRight(OutsourceOrder::getCode, likePattern)
                .orderByDesc(OutsourceOrder::getCode)
                .last("LIMIT 1");
        OutsourceOrder last = orderMapper.selectOne(w);
        int seq = 1;
        // F7-65③（2026-09-20）：统一走 BillNoSeq（尾段连续数字解析 + 序号超 999 自动扩位，不再静默回退）
        String prefix = BillPrefix.OUTSOURCE_ORDER + dateStr;
        seq = last != null ? com.beichen.erp.common.BillNoSeq.lastSeq(last.getCode(), prefix) + 1 : 1;
        return com.beichen.erp.common.BillNoSeq.format(prefix, seq);
    }
}
