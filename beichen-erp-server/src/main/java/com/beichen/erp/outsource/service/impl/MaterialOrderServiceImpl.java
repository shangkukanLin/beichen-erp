package com.beichen.erp.outsource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.BillPrefix;
import com.beichen.erp.common.DocStatus;
import com.beichen.erp.common.DocStatusGuard;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.outsource.common.DefectHandleType;
import com.beichen.erp.outsource.common.DeliveryType;
import com.beichen.erp.outsource.common.MaterialOrderStatus;
import com.beichen.erp.outsource.common.OrderType;
import com.beichen.erp.outsource.common.QualityType;
import com.beichen.erp.outsource.entity.*;
import com.beichen.erp.outsource.mapper.*;
import com.beichen.erp.outsource.service.MaterialOrderService;
import com.beichen.erp.outsource.service.SupplierMaterialService;
import com.beichen.erp.warehouse.entity.Warehouse;
import com.beichen.erp.warehouse.entity.WarehouseStock;
import com.beichen.erp.warehouse.entity.WarehouseStockLog;
import com.beichen.erp.warehouse.mapper.WarehouseMapper;
import com.beichen.erp.warehouse.mapper.WarehouseStockMapper;
import com.beichen.erp.warehouse.mapper.WarehouseStockLogMapper;
import com.beichen.erp.supplier.entity.Supplier;
import com.beichen.erp.supplier.mapper.SupplierMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 委外物料订单业务层实现
 * <p>采购/委外物料订单：创建-审核-收货(生成收发单草稿)-结单-作废状态机。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MaterialOrderServiceImpl implements MaterialOrderService {

    private final MaterialOrderMapper orderMapper;
    private final MaterialOrderItemMapper itemMapper;
    private final SupplierMapper supplierMapper;
    private final OutsourceMaterialMapper materialMapper;
    private final com.beichen.erp.dev.mapper.MaterialTypeMapper materialTypeMapper;
    private final WarehouseMapper warehouseMapper;
    private final WarehouseStockMapper warehouseStockMapper;
    private final OutsourceDeliveryMapper deliveryMapper;
    private final OutsourceDeliveryItemMapper deliveryItemMapper;
    private final com.beichen.erp.outsource.service.DeliveryService deliveryService;
    private final OutsourceMaterialComponentMapper componentMapper;
    private final WarehouseStockLogMapper stockLogMapper;
    private final SupplierMaterialService supplierMaterialService;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public Page<Map<String, Object>> page(int pageNum, int pageSize, String code, String status, String statuses, Long supplierId) {
        LambdaQueryWrapper<MaterialOrder> w = new LambdaQueryWrapper<MaterialOrder>()
            .eq(code != null && !code.isBlank(), MaterialOrder::getCode, code)
            .eq(supplierId != null, MaterialOrder::getSupplierId, supplierId);
        if (status != null && !status.isBlank()) {
            w.eq(MaterialOrder::getStatus, status);
        } else if (statuses != null && !statuses.isBlank()) {
            String[] arr = statuses.split(",");
            if (arr.length == 1) {
                w.eq(MaterialOrder::getStatus, arr[0].trim());
            } else if (arr.length > 1) {
                w.in(MaterialOrder::getStatus, java.util.Arrays.stream(arr).map(String::trim).toList());
            }
        }
        w.orderByDesc(MaterialOrder::getId);
        Page<MaterialOrder> page = orderMapper.selectPage(new Page<>(pageNum, pageSize), w);
        Page<Map<String, Object>> result = new Page<>(pageNum, pageSize, page.getTotal());
        result.setRecords(page.getRecords().stream().map(o -> {
            Map<String, Object> m = buildOrderMap(o);
            List<MaterialOrderItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<MaterialOrderItem>().eq(MaterialOrderItem::getOrderId, o.getId()));
            m.put("items", buildItemMaps(items, o.getSupplierId()));
            return m;
        }).toList());
        return result;
    }

    @Override
    public Map<String, Object> detail(Long id) {
        MaterialOrder o = orderMapper.selectById(id);
        if (o == null) return null;
        Map<String, Object> m = buildOrderMap(o);
        m.put("items", buildItemMaps(itemMapper.selectList(
            new LambdaQueryWrapper<MaterialOrderItem>().eq(MaterialOrderItem::getOrderId, id)), o.getSupplierId()));
        return m;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(MaterialOrder o, List<Map<String, Object>> itemsRaw) {
        o.setCode(generateCode());
        o.setStatus(MaterialOrderStatus.PENDING.getCode());
        orderMapper.insert(o);

        if (itemsRaw != null) {
            for (Map<String, Object> it : itemsRaw) {
                MaterialOrderItem item = parseItem(it);
                item.setOrderId(o.getId());
                itemMapper.insert(item);
            }
        }
        return o.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, MaterialOrder o, List<Map<String, Object>> itemsRaw) {
        MaterialOrder old = orderMapper.selectById(id);
        if (old == null) throw new BusinessException("订单不存在");
        if (MaterialOrderStatus.CANCELLED.getCode().equals(old.getStatus())) throw new BusinessException("已作废的订单不可编辑");
        o.setId(id);
        orderMapper.updateById(o);

        // F7-67（2026-09-20）：明细改为**差量更新，保住行 id**。
        // 原实现是"全删重插" ⇒ item 行 id 全部变化 ⇒ 已存在的收货草稿（outsource_delivery_item.item_id）
        // 随之悬空 ⇒ 审核时无法回写订单"已收数量"（DeliveryServiceImpl.auditMaterialDelivery 已把该处
        // 由静默 continue 改为抛错兜住"静默落账"，但那只把问题变成"编辑过的收货单无法审核"）。
        // 根治 = 编辑时保住行 id：按 business key（materialId）原地更新。
        // 业务键安全性：实查现网 (order_id, material_id) 无重复组合。
        List<MaterialOrderItem> oldItems = itemMapper.selectList(
                new LambdaQueryWrapper<MaterialOrderItem>().eq(MaterialOrderItem::getOrderId, id));
        Map<Long, MaterialOrderItem> byMaterial = new LinkedHashMap<>();
        for (MaterialOrderItem oi : oldItems) {
            if (oi.getMaterialId() != null) byMaterial.put(oi.getMaterialId(), oi);
        }
        java.util.Set<Long> keptIds = new java.util.HashSet<>();
        if (itemsRaw != null) {
            for (Map<String, Object> it : itemsRaw) {
                MaterialOrderItem item = parseItem(it);
                item.setOrderId(id);
                MaterialOrderItem exist = item.getMaterialId() == null ? null : byMaterial.get(item.getMaterialId());
                if (exist != null) {
                    item.setId(exist.getId());   // 原地更新：保住行 id 与其下所有引用
                    keptIds.add(exist.getId());
                    itemMapper.updateById(item);
                } else {
                    itemMapper.insert(item);
                    keptIds.add(item.getId());
                }
            }
        }
        // 被移除的行：**若已被收货单引用则拒绝删除**（否则又会造出悬空的 item_id）
        for (MaterialOrderItem oi : oldItems) {
            if (keptIds.contains(oi.getId())) continue;
            Long refs = deliveryItemMapper.selectCount(new LambdaQueryWrapper<OutsourceDeliveryItem>()
                    .eq(OutsourceDeliveryItem::getItemId, oi.getId()));
            if (refs != null && refs > 0) {
                throw new BusinessException("物料「" + getMaterialNameById(oi.getMaterialId())
                        + "」已有 " + refs + " 条收货明细引用，不能从订单中移除；请先作废相关收货单");
            }
            itemMapper.deleteById(oi.getId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        MaterialOrder o = orderMapper.selectById(id);
        if (o == null) throw new BusinessException("订单不存在");
        // F7-46（2026-09-19）：原子抢占 PENDING→RECEIVING（原"先查后改"非原子，双击/并发都能通过校验）
        if (!DocStatusGuard.claim(orderMapper, MaterialOrder::getId, id,
                MaterialOrder::getStatus, MaterialOrderStatus.PENDING.getCode(), MaterialOrderStatus.RECEIVING.getCode()))
            throw new BusinessException("只有待审核状态可审核");
        MaterialOrder upd = new MaterialOrder(); upd.setId(id); upd.setStatus(MaterialOrderStatus.RECEIVING.getCode());
        orderMapper.updateById(upd);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unAudit(Long id) {
        MaterialOrder o = orderMapper.selectById(id);
        if (o == null) throw new BusinessException("订单不存在");
        // F7-46（2026-09-19）：原子抢占 RECEIVING→PENDING（原"先查后改"非原子）
        if (!DocStatusGuard.claim(orderMapper, MaterialOrder::getId, id,
                MaterialOrder::getStatus, MaterialOrderStatus.RECEIVING.getCode(), MaterialOrderStatus.PENDING.getCode()))
            throw new BusinessException("仅收货中状态可反审核");
        List<MaterialOrderItem> items = itemMapper.selectList(
            new LambdaQueryWrapper<MaterialOrderItem>().eq(MaterialOrderItem::getOrderId, id));
        boolean hasDelivery = items.stream().anyMatch(it -> it.getReceivedQuantity() != null && it.getReceivedQuantity().compareTo(BigDecimal.ZERO) > 0);
        if (hasDelivery) throw new BusinessException("该订单已有交货记录，不可反审核");
        MaterialOrder upd = new MaterialOrder(); upd.setId(id); upd.setStatus(MaterialOrderStatus.PENDING.getCode());
        orderMapper.updateById(upd);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Object receive(Long id, Map<String, Object> body) {
        boolean force = Boolean.TRUE.equals(body.get("force"));
        MaterialOrder o = orderMapper.selectById(id);
        if (o == null) throw new BusinessException("订单不存在");
        // 仅已审核(收货中)的订单可收货；待审核/已结单/已作废均禁止
        if (MaterialOrderStatus.PENDING.getCode().equals(o.getStatus()))
            throw new BusinessException("订单尚未审核，请先审核订单再收货");
        if (MaterialOrderStatus.FINISHED.getCode().equals(o.getStatus()))
            throw new BusinessException("订单已结单，不可再收货");
        if (MaterialOrderStatus.CANCELLED.getCode().equals(o.getStatus()))
            throw new BusinessException("订单已作废，不可收货");

        // 供应商（加工厂）仓库：子物料从该仓扣减
        List<Warehouse> supWhs = warehouseMapper.selectList(
            new LambdaQueryWrapper<Warehouse>().eq(Warehouse::getFactoryId, o.getSupplierId()));
        Long compWhId = supWhs.isEmpty() ? null : supWhs.get(0).getId();

        // 父物料收货仓：前端指定 > 订单目标仓 > 供应商仓
        Long whId;
        if (body.get("warehouseId") != null) {
            whId = Long.valueOf(body.get("warehouseId").toString());
        } else if (o.getTargetWarehouseId() != null) {
            whId = o.getTargetWarehouseId();
        } else {
            if (compWhId == null) throw new BusinessException("未指定收货仓库且供应商无默认仓库");
            whId = compWhId;
        }
        if (compWhId == null) compWhId = whId;

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        if (items == null || items.isEmpty()) throw new BusinessException("收货明细不能为空");

        // 1. 校验委外单的子物料库存
        List<Map<String, Object>> shortages = new ArrayList<>();
        log.info("收货: orderId={}, orderType={}, force={}", id, o.getOrderType(), force);
        if (OrderType.OUTSOURCE.getCode().equals(o.getOrderType())) {
            for (Map<String, Object> it : items) {
                BigDecimal qty = new BigDecimal(it.get("quantity").toString());
                if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;
                Long itemId = Long.valueOf(it.get("itemId").toString());
                MaterialOrderItem orderItem = itemMapper.selectById(itemId);
                if (orderItem == null || orderItem.getMaterialId() == null) continue;
                List<OutsourceMaterialComponent> comps = componentMapper.selectList(
                    new LambdaQueryWrapper<OutsourceMaterialComponent>()
                        .eq(OutsourceMaterialComponent::getParentMaterialId, orderItem.getMaterialId()));
                if (comps == null || comps.isEmpty()) continue;
                log.info("物料[{}]有{}个子物料", getMaterialNameById(orderItem.getMaterialId()), comps.size());
                for (OutsourceMaterialComponent c : comps) {
                    BigDecimal compDemand = (c.getQuantity() != null ? c.getQuantity() : BigDecimal.ONE).multiply(qty);
                    BigDecimal compStock = getStock(compWhId, c.getChildMaterialId());
                    log.info("子物料[{}] demand={} stock={} compWhId={}", c.getChildMaterialId(), compDemand, compStock, compWhId);
                    if (compStock.compareTo(compDemand) < 0) {
                        String childName = c.getChildMaterialId() != null ? 
                            (materialMapper.selectById(c.getChildMaterialId()) != null ? materialMapper.selectById(c.getChildMaterialId()).getMaterialName() : "子物料" + c.getChildMaterialId()) : "子物料";
                        if (force) continue; // 强制模式跳过校验
                        Map<String, Object> sh = new LinkedHashMap<>();
                        sh.put("materialName", childName);
                        sh.put("demand", compDemand.stripTrailingZeros().toPlainString());
                        sh.put("stock", compStock.stripTrailingZeros().toPlainString());
                        sh.put("shortage", compDemand.subtract(compStock).stripTrailingZeros().toPlainString());
                        shortages.add(sh);
                    }
                }
            }
            if (!shortages.isEmpty()) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("_shortage", true);
                result.put("shortages", shortages);
                return result;
            }
        }

        // F7-46（2026-09-19）：**秒级重复提交兜底**（前端暂无幂等键 —— 已核实 `requestId/uuid/nonce` 0 命中，
        // 故服务端兜底挡住"双击/网络重试"这一主场景）。`receive` 会 insert 一张收货草稿单，双击即两张相同草稿，
        // 各自审核 ⇒ **双倍入库 / 双倍应付 / 订单已收数量翻倍**。
        //
        // 规则：同「来源订单 + 收货仓库 + 收货类型」在 **5 秒**内已存在 **DRAFT** 收货单 ⇒ 视为同一次提交，
        // 直接返回该单 id（与新建路径**同一返回类型** Long），不再新建。
        // 为何是 5 秒：双击/超时重试必在秒级；而"同订单分批两次真实收货"间隔通常更久，不会被误判。
        OutsourceDelivery dup = deliveryMapper.selectOne(new LambdaQueryWrapper<OutsourceDelivery>()
                .eq(OutsourceDelivery::getSourceOrderId, id)
                .eq(OutsourceDelivery::getDeliveryType, DeliveryType.RECEIVE.getCode())
                .eq(OutsourceDelivery::getToWarehouseId, whId)
                .eq(OutsourceDelivery::getStatus, DocStatus.DRAFT.getCode())
                .gt(OutsourceDelivery::getCreateTime, java.time.LocalDateTime.now().minusSeconds(5))
                .orderByDesc(OutsourceDelivery::getId)
                .last("LIMIT 1"));
        if (dup != null) {
            // F7-66 / §24.8（2026-09-19）：原实现是"静默复用"该草稿并返回其 id —— 第二次请求携带的
            // 数量/明细被丢弃，而调用方拿到 200 + 一个单号，会误以为"本次收货成功"。
            // 改为明确报错并给出既有草稿单号，由用户确认是审核它还是稍后重试。
            log.warn("收货重复提交拦截：orderId={} 已存在 5 秒内的草稿收货单 {}", id, dup.getCode());
            throw new BusinessException("该订单刚刚已生成收货草稿单 " + dup.getCode()
                    + "（疑似重复提交）。请到「物料收货」页确认并审核该草稿单，确认不是重复后再重新收货。");
        }

        // F7-72（2026-09-20）：**去重必须覆盖"第一张已被审核"的情形**。上面的兜底只查 DRAFT ⇒
        // "双击 → 第一张被审核 → 第二次请求"会再建一张同内容的草稿 ⇒ 审核即双倍入库 / 双倍应付
        // （现网 8 行超收中 received 恰为 order 的 2 倍，符合该模式）。
        // 判定口径：仅当**同订单 + 同收货仓 + 同类型 + 明细（物料与数量）完全一致**才视为重复提交
        // —— 只要有一项不同就放行，以免误拦"同日分两批收不同数量"这一正常业务。
        OutsourceDelivery recentAudited = deliveryMapper.selectOne(new LambdaQueryWrapper<OutsourceDelivery>()
                .eq(OutsourceDelivery::getSourceOrderId, id)
                .eq(OutsourceDelivery::getDeliveryType, DeliveryType.RECEIVE.getCode())
                .eq(OutsourceDelivery::getToWarehouseId, whId)
                .eq(OutsourceDelivery::getStatus, DocStatus.AUDITED.getCode())
                .gt(OutsourceDelivery::getUpdateTime, java.time.LocalDateTime.now().minusSeconds(5))
                .orderByDesc(OutsourceDelivery::getId)
                .last("LIMIT 1"));
        if (recentAudited != null && sameReceiveLines(recentAudited.getId(), items)) {
            log.warn("收货重复提交拦截（已审核）：orderId={} 已存在 5 秒内的同明细收货单 {}", id, recentAudited.getCode());
            throw new BusinessException("该订单刚刚已收货并审核（单号 " + recentAudited.getCode()
                    + "，明细与本单完全一致，疑似重复提交）。若确实要分两批收同样数量，请稍后重试。");
        }

        // 2. 创建收货草稿单（库存/应付/订单明细的更新推迟到审核时统一处理，支持反审核）
        OutsourceDelivery delivery = new OutsourceDelivery();
        delivery.setDeliveryType(DeliveryType.RECEIVE.getCode());
        // 收货工厂 = 收货仓库所属工厂（采购类订单供应商≠仓库所属工厂，如向盟迪采购收货到捷鹤委外仓）
        Warehouse toWh = whId != null ? warehouseMapper.selectById(whId) : null;
        delivery.setFactoryId(toWh != null && toWh.getFactoryId() != null ? toWh.getFactoryId() : o.getSupplierId());
        delivery.setToWarehouseId(whId);
        delivery.setDeliveryDate(LocalDate.now());
        delivery.setStatus(DocStatus.DRAFT.getCode());
        delivery.setRemark((OrderType.OUTSOURCE.getCode().equals(o.getOrderType()) ? "委外收货 - " : "采购收货 - ") + o.getCode());
        // 强关联来源订单ID，便于财务/库存回查（替代 remark LIKE 弱关联）
        delivery.setSourceOrderId(id);
        // 来源标记：供应商（列表页会自动查名称）
        delivery.setSupplierDirect(1);
        delivery.setSupplierId(o.getSupplierId());
        delivery.setCode(generateDeliveryCode());
        deliveryMapper.insert(delivery);

        // 3. 仅存盘收发明细，不触发库存与应付
        for (Map<String, Object> it : items) {
            // itemId 必填（订单明细行ID），缺失则跳过该行避免 NPE
            if (it.get("itemId") == null) { log.warn("收货明细缺少 itemId，已跳过: {}", it); continue; }
            BigDecimal qty = new BigDecimal(it.get("quantity").toString());
            if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;
            Long itemId = Long.valueOf(it.get("itemId").toString());
            MaterialOrderItem orderItem = itemMapper.selectById(itemId);
            if (orderItem == null) continue;

            // F7-66（2026-09-19）：收货数量不得超过「下单数 − 已收数 − 在途草稿数」。
            // 原实现只判断 qty > 0 ⇒ 直调接口或前端手填即可超收（实测现网已产生 8 行超收：1900 件、
            // 按单价折算 18800 元应付虚增）。此处是**第一道防线**（建草稿即拦）；
            // 第二道在 DeliveryServiceImpl.auditMaterialDelivery（落账前复核）。
            BigDecimal orderedQty = orderItem.getOrderQuantity() != null ? orderItem.getOrderQuantity() : BigDecimal.ZERO;
            BigDecimal receivedQty = orderItem.getReceivedQuantity() != null ? orderItem.getReceivedQuantity() : BigDecimal.ZERO;
            BigDecimal onWayQty = pendingReceiveDraftQty(itemId);
            BigDecimal remainQty = orderedQty.subtract(receivedQty).subtract(onWayQty);
            if (qty.compareTo(remainQty) > 0) {
                throw new BusinessException("收货数量超过该物料剩余可收量：物料「" + getMaterialNameById(orderItem.getMaterialId())
                        + "」下单 " + orderedQty.stripTrailingZeros().toPlainString()
                        + "、已收 " + receivedQty.stripTrailingZeros().toPlainString()
                        + "、在途草稿 " + onWayQty.stripTrailingZeros().toPlainString()
                        + "、本次 " + qty.stripTrailingZeros().toPlainString()
                        + "；剩余可收 " + remainQty.max(BigDecimal.ZERO).stripTrailingZeros().toPlainString());
            }

            OutsourceDeliveryItem di = new OutsourceDeliveryItem();
            di.setDeliveryId(delivery.getId());
            di.setItemId(itemId);
            di.setMaterialId(orderItem.getMaterialId());
            di.setMaterialTypeId(orderItem.getMaterialTypeId());
            di.setUnit(orderItem.getUnit());
            di.setQuantity(qty);
            di.setAmount(qty.multiply(orderItem.getUnitPrice() != null ? orderItem.getUnitPrice() : BigDecimal.ZERO));
            di.setQualityType(QualityType.GOOD.getCode());
            deliveryItemMapper.insert(di);
        }

        // 订单进入收货中（仅标记，不等到全部收满）
        if (!MaterialOrderStatus.RECEIVING.getCode().equals(o.getStatus())
                && !MaterialOrderStatus.FINISHED.getCode().equals(o.getStatus())) {
            MaterialOrder upd = new MaterialOrder();
            upd.setId(id);
            upd.setStatus(MaterialOrderStatus.RECEIVING.getCode());
            orderMapper.updateById(upd);
        }
        return delivery.getId();
    }

    /**
     * F7-72（2026-09-20）：判断已存在收货单的明细与本次请求是否**完全一致**（按订单明细行ID聚合数量，
     * 逐项相同）。只有"完全一致"才认定重复提交；任何一项不同即放行，以保护正常的分批收货。
     * <p>畸形入参（缺 itemId / 非数字）在这里一律忽略 —— 它们会由主流程的校验负责报错。</p>
     */
    private boolean sameReceiveLines(Long deliveryId, List<Map<String, Object>> items) {
        Map<Long, BigDecimal> want = new LinkedHashMap<>();
        for (Map<String, Object> it : items) {
            if (it.get("itemId") == null || it.get("quantity") == null) continue;
            try {
                BigDecimal q = new BigDecimal(it.get("quantity").toString());
                if (q.compareTo(BigDecimal.ZERO) <= 0) continue;
                want.merge(Long.valueOf(it.get("itemId").toString()), q, BigDecimal::add);
            } catch (Exception ignore) { /* 交给主流程校验 */ }
        }
        if (want.isEmpty()) return false;
        Map<Long, BigDecimal> have = new LinkedHashMap<>();
        for (OutsourceDeliveryItem di : deliveryItemMapper.selectList(
                new LambdaQueryWrapper<OutsourceDeliveryItem>().eq(OutsourceDeliveryItem::getDeliveryId, deliveryId))) {
            if (di.getItemId() == null || di.getQuantity() == null) continue;
            have.merge(di.getItemId(), di.getQuantity(), BigDecimal::add);
        }
        if (have.size() != want.size()) return false;
        for (Map.Entry<Long, BigDecimal> e : want.entrySet()) {
            BigDecimal h = have.get(e.getKey());
            if (h == null || h.compareTo(e.getValue()) != 0) return false;
        }
        return true;
    }

    /**
     * 该订单明细行上「在途」的收货草稿数量 = 同一 item_id 且单据为 DRAFT 的 **RECEIVE** 明细合计。
     * <p>F7-66（2026-09-19）：剩余可收量必须扣掉尚未审核的草稿，否则"连续建两张超量草稿"即可绕过上限
     * （第一张草稿审核前 received_quantity 尚未变化）。</p>
     */
    private BigDecimal pendingReceiveDraftQty(Long orderItemId) {
        if (orderItemId == null) return BigDecimal.ZERO;
        List<OutsourceDeliveryItem> rows = deliveryItemMapper.selectList(
                new LambdaQueryWrapper<OutsourceDeliveryItem>().eq(OutsourceDeliveryItem::getItemId, orderItemId));
        if (rows.isEmpty()) return BigDecimal.ZERO;
        List<Long> deliveryIds = rows.stream().map(OutsourceDeliveryItem::getDeliveryId)
                .filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
        if (deliveryIds.isEmpty()) return BigDecimal.ZERO;
        Set<Long> draftIds = deliveryMapper.selectList(new LambdaQueryWrapper<OutsourceDelivery>()
                        .in(OutsourceDelivery::getId, deliveryIds)
                        .eq(OutsourceDelivery::getDeliveryType, DeliveryType.RECEIVE.getCode())
                        .eq(OutsourceDelivery::getStatus, DocStatus.DRAFT.getCode()))
                .stream().map(OutsourceDelivery::getId).collect(Collectors.toSet());
        BigDecimal sum = BigDecimal.ZERO;
        for (OutsourceDeliveryItem r : rows) {
            if (r.getDeliveryId() != null && draftIds.contains(r.getDeliveryId()) && r.getQuantity() != null) {
                sum = sum.add(r.getQuantity());
            }
        }
        return sum;
    }

    /** 获取某个仓库某个物料的良品库存 */
    private BigDecimal getStock(Long warehouseId, Long materialId) {
        if (warehouseId == null || materialId == null) return BigDecimal.ZERO;
        WarehouseStock s = warehouseStockMapper.selectOne(
            new LambdaQueryWrapper<WarehouseStock>()
                .eq(WarehouseStock::getWarehouseId, warehouseId)
                .eq(WarehouseStock::getMaterialId, materialId)
                .eq(WarehouseStock::getQualityType, QualityType.GOOD.getCode()));
        return s != null && s.getQuantity() != null ? s.getQuantity() : BigDecimal.ZERO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Object returnDefect(Long id, Map<String, Object> body) {
        MaterialOrder o = orderMapper.selectById(id);
        if (o == null) throw new BusinessException("订单不存在");
        // 已审核(收货中)或已完成的订单可退不良；待审核/已作废禁止
        if (!MaterialOrderStatus.RECEIVING.getCode().equals(o.getStatus())
                && !MaterialOrderStatus.FINISHED.getCode().equals(o.getStatus()))
            throw new BusinessException("仅收货中或已完成的订单可退不良");

        Long factoryId = o.getSupplierId();
        if (factoryId == null) throw new BusinessException("订单未关联供应商");
        String handleType = body.get("handleType") != null ? body.get("handleType").toString() : DefectHandleType.REPAIR_RETURN.getCode();

        // 退不良仓库（维修返还、折现退款均需退料仓库，缺省取供应商第一委外仓）
        Long whId = body.get("warehouseId") != null ? Long.valueOf(body.get("warehouseId").toString()) : null;
        if (whId == null) {
            List<Warehouse> whs = warehouseMapper.selectList(
                new LambdaQueryWrapper<Warehouse>().eq(Warehouse::getFactoryId, factoryId));
            whId = whs.isEmpty() ? null : whs.get(0).getId();
        }
        if (whId == null) throw new BusinessException("请选择退料仓库");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        if (items == null || items.isEmpty()) throw new BusinessException("退料明细不能为空");

        OutsourceDelivery delivery = new OutsourceDelivery();
        delivery.setDeliveryType(DeliveryType.DEFECT_RETURN.getCode());
        delivery.setFactoryId(factoryId);
        delivery.setToWarehouseId(whId);
        delivery.setDeliveryDate(LocalDate.now());
        delivery.setStatus(DocStatus.DRAFT.getCode());
        delivery.setRemark("不良退料(" + labelOfHandleType(handleType) + ") - " + o.getCode());
        // 强关联来源订单ID，便于退不良记录回查（替代 remark LIKE 弱关联）
        delivery.setSourceOrderId(id);
        delivery.setCode(generateDeliveryCode());
        deliveryMapper.insert(delivery);

        for (Map<String, Object> it : items) {
            if (it.get("itemId") == null) { log.warn("退不良明细缺少 itemId，已跳过: {}", it); continue; }
            BigDecimal qty = new BigDecimal(it.get("quantity").toString());
            if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;
            Long itemId = Long.valueOf(it.get("itemId").toString());
            MaterialOrderItem orderItem = itemMapper.selectById(itemId);
            if (orderItem == null) continue;
            if (orderItem.getReceivedQuantity().subtract(orderItem.getDefectReturnedQty()).compareTo(qty) < 0)
                throw new BusinessException(getMaterialNameById(orderItem.getMaterialId()) + " 可退数量不足");

            // 退不良（维修返还、折现退款均校验仓库库存是否足够，实际扣减推迟到审核）
            if (whId != null && orderItem.getMaterialId() != null) {
                WarehouseStock s = warehouseStockMapper.selectOne(
                    new LambdaQueryWrapper<WarehouseStock>()
                        .eq(WarehouseStock::getWarehouseId, whId)
                        .eq(WarehouseStock::getMaterialId, orderItem.getMaterialId())
                        .eq(WarehouseStock::getQualityType, QualityType.GOOD.getCode()));
                BigDecimal stockQty = s != null && s.getQuantity() != null ? s.getQuantity() : BigDecimal.ZERO;
                if (stockQty.compareTo(qty) < 0)
                    throw new BusinessException(getMaterialNameById(orderItem.getMaterialId()) + " 仓库库存不足(库存:" + stockQty + "，退:" + qty + ")");
            }

            // 仅存盘退不良明细，不触发库存与应付
            OutsourceDeliveryItem di = new OutsourceDeliveryItem();
            di.setDeliveryId(delivery.getId());
            di.setItemId(itemId);
            di.setMaterialId(orderItem.getMaterialId());
            di.setMaterialTypeId(orderItem.getMaterialTypeId());
            di.setUnit(orderItem.getUnit());
            di.setQuantity(qty);
            di.setAmount(qty.multiply(orderItem.getUnitPrice() != null ? orderItem.getUnitPrice() : BigDecimal.ZERO));
            di.setQualityType(QualityType.DEFECT.getCode());
            di.setHandleType(handleType);
            deliveryItemMapper.insert(di);
        }
        return delivery.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void finish(Long id) {
        MaterialOrder o = orderMapper.selectById(id);
        if (o == null) throw new BusinessException("订单不存在");
        if (MaterialOrderStatus.CANCELLED.getCode().equals(o.getStatus())) throw new BusinessException("已作废的订单不可结单");
        if (MaterialOrderStatus.FINISHED.getCode().equals(o.getStatus())) throw new BusinessException("订单已完成");
        // F7-46（2026-09-19）：按当前状态原子抢占 → FINISHED（动态 from：PENDING/RECEIVING 均可结单）
        if (!DocStatusGuard.claim(orderMapper, MaterialOrder::getId, id,
                MaterialOrder::getStatus, o.getStatus(), MaterialOrderStatus.FINISHED.getCode()))
            throw new BusinessException("订单状态已变化，请刷新后重试");
        MaterialOrder upd = new MaterialOrder(); upd.setId(id); upd.setStatus(MaterialOrderStatus.FINISHED.getCode()); upd.setFinishTime(LocalDateTime.now());
        orderMapper.updateById(upd);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        MaterialOrder o = orderMapper.selectById(id);
        if (o == null) throw new BusinessException("订单不存在");
        if (MaterialOrderStatus.CANCELLED.getCode().equals(o.getStatus()) || MaterialOrderStatus.FINISHED.getCode().equals(o.getStatus()))
            throw new BusinessException("当前状态不可作废");
        // F7-46（2026-09-19）：按当前状态原子抢占 → CANCELLED（动态 from：PENDING/RECEIVING 均可作废）
        if (!DocStatusGuard.claim(orderMapper, MaterialOrder::getId, id,
                MaterialOrder::getStatus, o.getStatus(), MaterialOrderStatus.CANCELLED.getCode()))
            throw new BusinessException("订单状态已变化，请刷新后重试");
        List<MaterialOrderItem> items = itemMapper.selectList(
            new LambdaQueryWrapper<MaterialOrderItem>().eq(MaterialOrderItem::getOrderId, id));
        boolean hasDelivery = items.stream().anyMatch(it -> it.getReceivedQuantity() != null && it.getReceivedQuantity().compareTo(BigDecimal.ZERO) > 0);
        if (hasDelivery) throw new BusinessException("该订单已有交货记录，不可作废");
        MaterialOrder upd = new MaterialOrder(); upd.setId(id); upd.setStatus(MaterialOrderStatus.CANCELLED.getCode());
        orderMapper.updateById(upd);
    }

    @Override
    public List<Map<String, Object>> defectWarehouses(Long id) {
        MaterialOrder o = orderMapper.selectById(id);
        if (o == null) return Collections.emptyList();
        // 查询该订单关联的所有收货单(RECEIVE)，收集目标仓库作为可退不良仓库
        // 注意：delivery_type 库存的是 DeliveryType 的 code(枚举名)，非中文 label
        List<OutsourceDelivery> deliveries = deliveryMapper.selectList(
            new LambdaQueryWrapper<OutsourceDelivery>()
                .eq(OutsourceDelivery::getSourceOrderId, id)
                .eq(OutsourceDelivery::getDeliveryType, DeliveryType.RECEIVE.getCode())
                .orderByDesc(OutsourceDelivery::getId));
        // 去重仓库ID
        Set<Long> whIds = new LinkedHashSet<>();
        for (OutsourceDelivery d : deliveries) {
            if (d.getToWarehouseId() != null) whIds.add(d.getToWarehouseId());
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Long whId : whIds) {
            Warehouse wh = warehouseMapper.selectById(whId);
            if (wh != null) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", wh.getId()); m.put("warehouseName", wh.getWarehouseName());
                m.put("factoryId", wh.getFactoryId());
                result.add(m);
            }
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> deliveries(Long id) {
        MaterialOrder o = orderMapper.selectById(id);
        if (o == null) return Collections.emptyList();
        // 强关联来源订单ID查询收发单（替代 remark LIKE 弱关联）
        List<OutsourceDelivery> list = deliveryMapper.selectList(
            new LambdaQueryWrapper<OutsourceDelivery>()
                .eq(OutsourceDelivery::getSourceOrderId, id)
                .orderByDesc(OutsourceDelivery::getId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (OutsourceDelivery d : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.getId()); m.put("code", d.getCode()); m.put("deliveryType", d.getDeliveryType());
            m.put("deliveryDate", d.getDeliveryDate()); m.put("status", d.getStatus()); m.put("remark", d.getRemark());
            m.put("toWarehouseId", d.getToWarehouseId());
            if (d.getToWarehouseId() != null) {
                Warehouse wh = warehouseMapper.selectById(d.getToWarehouseId());
                m.put("warehouseName", wh != null ? wh.getWarehouseName() : "");
            }
            List<OutsourceDeliveryItem> items = deliveryItemMapper.selectList(
                new LambdaQueryWrapper<OutsourceDeliveryItem>().eq(OutsourceDeliveryItem::getDeliveryId, d.getId()));
            // 补物料名称到明细Map（实体无materialName字段，需关联查询）
            List<Map<String, Object>> itemMaps = new ArrayList<>();
            for (OutsourceDeliveryItem it : items) {
                Map<String, Object> im = new LinkedHashMap<>();
                im.put("id", it.getId()); im.put("deliveryId", it.getDeliveryId());
                im.put("materialId", it.getMaterialId());
                im.put("materialName", getMaterialNameById(it.getMaterialId()));
                im.put("materialTypeId", it.getMaterialTypeId());
                im.put("itemId", it.getItemId());
                im.put("unit", it.getUnit());
                im.put("quantity", it.getQuantity());
                im.put("unitPrice", it.getUnitPrice());
                im.put("amount", it.getAmount());
                im.put("qualityType", it.getQualityType());
                im.put("handleType", it.getHandleType());
                im.put("createTime", it.getCreateTime());
                itemMaps.add(im);
            }
            m.put("items", itemMaps);
            result.add(m);
        }
        return result;
    }

    @Override
    public void deleteAttach(Long id) {
        MaterialOrder update = new MaterialOrder();
        update.setId(id);
        update.setAttachUrl("");
        orderMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        // 逻辑删除：MP @TableLogic 自动将 delete 转为 UPDATE deleted=1，保留审计与关联收发流水。
        // 原子删除（O-7）：带状态条件的逻辑删 —— 仅「待审核 / 已作废」可删。
        // 此前本入口**没有任何状态校验**（收货中/已完成的订单也能被"删除"），且"先查后删"非原子；
        // 现改为单条条件 UPDATE，affected=0 即状态不符或已被并发删除。
        int rows = orderMapper.delete(new LambdaQueryWrapper<MaterialOrder>()
                .eq(MaterialOrder::getId, id)
                .in(MaterialOrder::getStatus, MaterialOrderStatus.PENDING.getCode(), MaterialOrderStatus.CANCELLED.getCode()));
        if (rows == 0) {
            if (orderMapper.selectById(id) == null) throw new BusinessException("物料订单不存在");
            throw new BusinessException("仅待审核或已作废的物料订单可删除，收货中/已完成请先作废");
        }
        itemMapper.delete(new LambdaQueryWrapper<MaterialOrderItem>().eq(MaterialOrderItem::getOrderId, id));
    }

    // ==================== 私有 ====================

    private Map<String, Object> buildOrderMap(MaterialOrder o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.getId()); m.put("code", o.getCode());
        m.put("supplierId", o.getSupplierId());
        m.put("orderType", o.getOrderType() != null ? o.getOrderType() : OrderType.PURCHASE.getCode());
        m.put("targetWarehouseId", o.getTargetWarehouseId());
        m.put("deliveryDate", o.getDeliveryDate()); m.put("status", o.getStatus());
        m.put("remark", o.getRemark()); m.put("createTime", o.getCreateTime());
        m.put("finishTime", o.getFinishTime());
        m.put("attachUrl", o.getAttachUrl());
        if (o.getSupplierId() != null) { Supplier s = supplierMapper.selectById(o.getSupplierId()); m.put("supplierName", s != null ? s.getName() : ""); }
        if (o.getTargetWarehouseId() != null) {
            Warehouse wh = warehouseMapper.selectById(o.getTargetWarehouseId());
            m.put("targetWarehouseName", wh != null ? wh.getWarehouseName() : "");
        }
        // 最近一次交货时间（强关联 sourceOrderId 查询，兼容旧数据备注弱关联）
        OutsourceDelivery lastDelivery = findLastDeliveryByOrder(o.getId(), o.getCode());
        m.put("lastDeliveryTime", lastDelivery != null ? lastDelivery.getCreateTime() : null);
        return m;
    }

    /** 将 MaterialOrderItem 转为 Map，附带子物料组件列表 */
    private List<Map<String, Object>> buildItemMaps(List<MaterialOrderItem> items, Long supplierId) {
        // 该供应商的委外仓ID列表
        java.util.Set<Long> supplierWhIds = new java.util.HashSet<>();
        if (supplierId != null) {
            warehouseMapper.selectList(new LambdaQueryWrapper<Warehouse>().eq(Warehouse::getFactoryId, supplierId))
                .forEach(w -> supplierWhIds.add(w.getId()));
        }
        // 查所有活跃物料订单的在途数量（按物料ID汇总），逻辑删除订单不计入（@TableLogic 已自动过滤，此处显式声明语义）
        Map<Long, BigDecimal> inTransitMap = new HashMap<>();
        List<MaterialOrder> activeOrders = orderMapper.selectList(
            new LambdaQueryWrapper<MaterialOrder>()
                .eq(MaterialOrder::getDeleted, 0)
                .notIn(MaterialOrder::getStatus, List.of(MaterialOrderStatus.FINISHED.getCode(), MaterialOrderStatus.CANCELLED.getCode())));
            if (!activeOrders.isEmpty()) {
                List<Long> orderIds = activeOrders.stream().map(MaterialOrder::getId).collect(Collectors.toList());
                List<MaterialOrderItem> orderItems = itemMapper.selectList(
                    new LambdaQueryWrapper<MaterialOrderItem>()
                        .in(MaterialOrderItem::getOrderId, orderIds));
                for (MaterialOrderItem oi : orderItems) {
                    if (oi.getMaterialId() == null) continue;
                    BigDecimal ordered = oi.getOrderQuantity() != null ? oi.getOrderQuantity() : BigDecimal.ZERO;
                    BigDecimal received = oi.getReceivedQuantity() != null ? oi.getReceivedQuantity() : BigDecimal.ZERO;
                    BigDecimal inTransit = ordered.subtract(received);
                    if (inTransit.compareTo(BigDecimal.ZERO) > 0) {
                        inTransitMap.merge(oi.getMaterialId(), inTransit, BigDecimal::add);
                    }
                }
            }

        if (items == null || items.isEmpty()) return Collections.emptyList();
        return items.stream().map(it -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", it.getId());
            map.put("orderId", it.getOrderId());
            map.put("materialId", it.getMaterialId());
            map.put("materialName", getMaterialNameById(it.getMaterialId()));
            map.put("materialTypeId", it.getMaterialTypeId());
            map.put("materialTypeName", getMaterialTypeNameById(it.getMaterialTypeId()));
            map.put("unit", it.getUnit());
            map.put("orderQuantity", it.getOrderQuantity());
            map.put("receivedQuantity", it.getReceivedQuantity());
            map.put("defectReturnedQty", it.getDefectReturnedQty());
            // 送修中（2026-09-17）：维修返还已送修未返回的数量（已从 receivedQuantity 中扣出）
            map.put("repairReturnedQty", it.getRepairReturnedQty());
            map.put("unitPrice", it.getUnitPrice());
            map.put("amount", it.getAmount());
            map.put("remark", it.getRemark());
            // 查子物料组件（含库存、缺料、已发料）
            if (it.getMaterialId() != null) {
                List<OutsourceMaterialComponent> comps = componentMapper.selectList(
                    new LambdaQueryWrapper<OutsourceMaterialComponent>()
                        .eq(OutsourceMaterialComponent::getParentMaterialId, it.getMaterialId()));
                if (comps != null && !comps.isEmpty()) {
                    List<Map<String, Object>> compMaps = new ArrayList<>();
                    for (OutsourceMaterialComponent c : comps) {
                        Map<String, Object> cm = new LinkedHashMap<>();
                        cm.put("id", c.getId());
                        cm.put("childMaterialId", c.getChildMaterialId());
                        cm.put("quantity", c.getQuantity());
                        cm.put("lossRate", c.getLossRate());
                        cm.put("remark", c.getRemark());
                        // 需求总数 = 每套用量 × 下单数
                        BigDecimal compQty = c.getQuantity() != null ? c.getQuantity() : BigDecimal.ONE;
                        BigDecimal ordQty = it.getOrderQuantity() != null ? it.getOrderQuantity() : BigDecimal.ZERO;
                        BigDecimal demand = compQty.multiply(ordQty);
                        cm.put("demandQuantity", demand);
                        // 已发料 = 每套用量 × 已收数（按比例）
                        BigDecimal recQty = it.getReceivedQuantity() != null ? it.getReceivedQuantity() : BigDecimal.ZERO;
                        cm.put("deliveredQuantity", compQty.multiply(recQty));
                        // 查子物料的供应商信息（供"去采购"使用）
                        if (c.getChildMaterialId() != null) {
                            OutsourceMaterial childMat = materialMapper.selectById(c.getChildMaterialId());
                            if (childMat != null) {
                                cm.put("childMaterialName", childMat.getMaterialName());
                                cm.put("childUnit", childMat.getUnit());
                                cm.put("childMaterialTypeId", childMat.getMaterialTypeId());
                                cm.put("childMaterialTypeName", getMaterialTypeNameById(childMat.getMaterialTypeId()));
                                // 供应商从 supplier_material 居间表联查（outsource_material.supplier_ids 冗余字段已废弃）
                                cm.put("supplierIds", supplierMaterialService.listSupplierIdsByMaterial(childMat.getId()));
                            }
                            // 查该供应商委外仓库存
                            BigDecimal stock = BigDecimal.ZERO;
                            if (!supplierWhIds.isEmpty()) {
                                List<WarehouseStock> stocks = warehouseStockMapper.selectList(
                                    new LambdaQueryWrapper<WarehouseStock>()
                                        .eq(WarehouseStock::getMaterialId, c.getChildMaterialId())
                                        .in(WarehouseStock::getWarehouseId, supplierWhIds)
                                        .eq(WarehouseStock::getQualityType, QualityType.GOOD.getCode()));
                                if (stocks != null) {
                                    for (WarehouseStock s : stocks) {
                                        if (s.getQuantity() != null) stock = stock.add(s.getQuantity());
                                    }
                                }
                            }
                            cm.put("stockQuantity", stock);
                            // 缺料 = max(剩余需求 - 库存, 0)，其中剩余需求 = max(需求 - 已出货消耗, 0)
                            BigDecimal delivered = compQty.multiply(recQty);
                            BigDecimal remaining = demand.subtract(delivered).max(BigDecimal.ZERO);
                            cm.put("shortage", remaining.subtract(stock).max(BigDecimal.ZERO));
                            cm.put("inTransit", inTransitMap.getOrDefault(c.getChildMaterialId(), BigDecimal.ZERO));
                        } else {
                            cm.put("inTransit", BigDecimal.ZERO);
                        }
                        compMaps.add(cm);
                    }
                    map.put("components", compMaps);
                }
            }
            return map;
        }).toList();
    }

    private MaterialOrderItem parseItem(Map<String, Object> it) {
        MaterialOrderItem item = new MaterialOrderItem();
        if (it.get("materialId") != null) item.setMaterialId(Long.valueOf(it.get("materialId").toString()));
        if (it.get("materialTypeId") != null) item.setMaterialTypeId(Long.valueOf(it.get("materialTypeId").toString()));
        item.setUnit((String) it.get("unit"));
        if (it.get("orderQuantity") != null) item.setOrderQuantity(new BigDecimal(it.get("orderQuantity").toString()));
        if (it.get("unitPrice") != null) item.setUnitPrice(new BigDecimal(it.get("unitPrice").toString()));
        if (item.getOrderQuantity() != null && item.getUnitPrice() != null) item.setAmount(item.getOrderQuantity().multiply(item.getUnitPrice()));
        if (it.get("remark") != null) item.setRemark((String) it.get("remark"));
        return item;
    }

    /**
     * 按来源订单ID强关联查询收发单（替代 remark LIKE 弱关联）；
     * 兼容旧数据：历史收发单无 sourceOrderId 时，兜底按 remark 包含订单 code 匹配
     */
    private List<OutsourceDelivery> findDeliveriesByOrder(Long orderId, String orderCode) {
        LambdaQueryWrapper<OutsourceDelivery> w = new LambdaQueryWrapper<OutsourceDelivery>()
            .eq(OutsourceDelivery::getSourceOrderId, orderId)
            .orderByDesc(OutsourceDelivery::getId);
        if (orderCode != null && !orderCode.isBlank()) {
            w.or().like(OutsourceDelivery::getRemark, orderCode);
        }
        return deliveryMapper.selectList(w);
    }

    /**
     * 查询某订单最近一次交货收发单（强关联优先，兼容旧数据备注弱关联）
     */
    private OutsourceDelivery findLastDeliveryByOrder(Long orderId, String orderCode) {
        List<OutsourceDelivery> list = findDeliveriesByOrder(orderId, orderCode);
        return list.isEmpty() ? null : list.get(0);
    }

    /** 根据委外物料ID查询名称，用于展示回填（ID关联查询替代冗余name字段） */
    private String getMaterialNameById(Long materialId) {
        if (materialId == null) return "";
        OutsourceMaterial m = materialMapper.selectById(materialId);
        return m != null ? m.getMaterialName() : "";
    }

    /** 根据 物料类型ID 查询类型名称，空安全返回 "-" */
    private String getMaterialTypeNameById(Long materialTypeId) {
        if (materialTypeId == null) return "-";
        com.beichen.erp.dev.entity.MaterialType bt = materialTypeMapper.selectById(materialTypeId);
        return bt != null ? bt.getTypeName() : "-";
    }

    private String generateCode() {
        String ds = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        LambdaQueryWrapper<MaterialOrder> w = new LambdaQueryWrapper<MaterialOrder>()
            .likeRight(MaterialOrder::getCode, BillPrefix.OUTSOURCE_MATERIAL_ORDER + ds).orderByDesc(MaterialOrder::getCode).last("LIMIT 1");
        MaterialOrder last = orderMapper.selectOne(w);
        int seq = 1;
        if (last != null && last.getCode() != null) {
            try { seq = Integer.parseInt(last.getCode().substring(last.getCode().length() - 3)) + 1; } catch (Exception ignored) {}
        }
        return BillPrefix.OUTSOURCE_MATERIAL_ORDER + ds + String.format("%03d", seq);
    }

    /** 处理方式 code → 中文名（用于备注展示） */
    private String labelOfHandleType(String code) {
        for (DefectHandleType t : DefectHandleType.values()) { if (t.getCode().equals(code)) return t.getLabel(); }
        return code;
    }

    private String generateDeliveryCode() {
        String ds = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        LambdaQueryWrapper<OutsourceDelivery> w = new LambdaQueryWrapper<OutsourceDelivery>()
            .likeRight(OutsourceDelivery::getCode, BillPrefix.OUTSOURCE_DELIVERY + ds).orderByDesc(OutsourceDelivery::getCode).last("LIMIT 1");
        OutsourceDelivery last = deliveryMapper.selectOne(w);
        int seq = 1;
        if (last != null && last.getCode() != null) {
            try { seq = Integer.parseInt(last.getCode().substring(last.getCode().length() - 3)) + 1; } catch (Exception ignored) {}
        }
        return BillPrefix.OUTSOURCE_DELIVERY + ds + String.format("%03d", seq);
    }
}
