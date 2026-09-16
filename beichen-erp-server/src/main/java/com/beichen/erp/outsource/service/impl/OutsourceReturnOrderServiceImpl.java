package com.beichen.erp.outsource.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.auth.entity.User;
import com.beichen.erp.auth.mapper.UserMapper;
import com.beichen.erp.common.BillPrefix;
import com.beichen.erp.common.DocStatus;
import com.beichen.erp.common.DocStatusGuard;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.dev.mapper.MaterialTypeMapper;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.finance.service.PayableHelper;
import com.beichen.erp.inventory.common.RelatedBillType;
import com.beichen.erp.inventory.common.StockChangeType;
import com.beichen.erp.warehouse.service.WarehouseStockService;
import com.beichen.erp.outsource.common.OutsourceChargeType;
import com.beichen.erp.outsource.common.OutsourceOrderStatus;
import com.beichen.erp.finance.common.SourceBillType;
import com.beichen.erp.outsource.common.QualityType;
import com.beichen.erp.material.common.ProductQualityType;
import com.beichen.erp.outsource.entity.*;
import com.beichen.erp.outsource.mapper.*;
import com.beichen.erp.outsource.service.OutsourceReturnOrderService;
import com.beichen.erp.warehouse.entity.Warehouse;
import com.beichen.erp.warehouse.mapper.WarehouseMapper;
import com.beichen.erp.supplier.entity.Supplier;
import com.beichen.erp.supplier.mapper.SupplierMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 委外加工退货单业务层实现
 * <p>退货物料入工厂委外仓、成品出库、负向应付 + 收费应付。草稿-审核-取消审核状态机。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutsourceReturnOrderServiceImpl implements OutsourceReturnOrderService {

    private final ReturnOrderMapper returnOrderMapper;
    private final ReturnOrderItemMapper returnOrderItemMapper;
    private final OutsourceReturnOrderProductMapper returnProductMapper;
    private final OutsourceOrderMapper orderMapper;
    private final OutsourceOrderMaterialMapper orderMaterialMapper;
    private final OutsourceOrderProductMapper orderProductMapper;
    private final WarehouseMapper warehouseMapper;
    private final OutsourceMaterialMapper outsourceMaterialMapper;
    private final com.beichen.erp.dev.mapper.MaterialTypeMapper materialTypeMapper;
    private final SupplierMapper supplierMapper;
    private final PayableHelper payableHelper;
    private final WarehouseStockService warehouseStockService;
    private final MaterialOrderMapper materialOrderMapper;
    private final MaterialOrderItemMapper materialOrderItemMapper;
    private final UserMapper userMapper;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public Page<Map<String, Object>> page(int pageNum, int pageSize, String code, Long factoryId) {
        LambdaQueryWrapper<ReturnOrder> w = new LambdaQueryWrapper<ReturnOrder>()
            .eq(code != null && !code.isBlank(), ReturnOrder::getCode, code)
            .eq(factoryId != null, ReturnOrder::getFactoryId, factoryId)
            .orderByDesc(ReturnOrder::getId);
        Page<ReturnOrder> raw = returnOrderMapper.selectPage(new Page<>(pageNum, pageSize), w);
        Page<Map<String, Object>> result = new Page<>(pageNum, pageSize, raw.getTotal());
        result.setRecords(raw.getRecords().stream().map(o -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", o.getId()); m.put("code", o.getCode());
            m.put("factoryId", o.getFactoryId()); m.put("orderId", o.getOrderId());
            m.put("returnDate", o.getReturnDate()); m.put("status", o.getStatus());
            m.put("remark", o.getRemark()); m.put("createTime", o.getCreateTime());
            m.put("chargeFlag", o.getChargeFlag()); m.put("chargeType", o.getChargeType());
            m.put("chargeAmount", o.getChargeAmount()); m.put("chargeReason", o.getChargeReason());
            if (o.getFactoryId() != null) {
                Supplier f = supplierMapper.selectById(o.getFactoryId());
                m.put("factoryName", f != null ? f.getName() : "");
            }
            if (o.getOrderId() != null) {
                OutsourceOrder ord = orderMapper.selectById(o.getOrderId());
                m.put("orderCode", ord != null ? ord.getCode() : "");
            }
            List<ReturnOrderItem> items = returnOrderItemMapper.selectList(
                new LambdaQueryWrapper<ReturnOrderItem>().eq(ReturnOrderItem::getReturnOrderId, o.getId()));
            BigDecimal totalQty = BigDecimal.ZERO;
            StringBuilder sb = new StringBuilder();
            for (ReturnOrderItem it : items) {
                BigDecimal qty = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
                totalQty = totalQty.add(qty);
                if (sb.length() > 0) sb.append("、");
                sb.append(getMaterialNameById(it.getMaterialId())).append("×").append(qty.stripTrailingZeros().toPlainString());
            }
            m.put("totalQuantity", totalQty); m.put("itemSummary", sb.toString());
            return m;
        }).toList());
        return result;
    }

    @Override
    public Map<String, Object> detail(Long id) {
        ReturnOrder o = returnOrderMapper.selectById(id);
        if (o == null) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.getId()); m.put("code", o.getCode()); m.put("factoryId", o.getFactoryId());
        m.put("orderId", o.getOrderId()); m.put("returnDate", o.getReturnDate());
        m.put("status", o.getStatus()); m.put("remark", o.getRemark());
        m.put("chargeFlag", o.getChargeFlag()); m.put("chargeType", o.getChargeType());
        m.put("chargeAmount", o.getChargeAmount()); m.put("chargeReason", o.getChargeReason());
        // E4：编辑页需要回填「成品出库仓」与退货成品明细（原先只返回物料明细）
        m.put("warehouseId", o.getWarehouseId());
        m.put("products", returnProductMapper.selectList(
            new LambdaQueryWrapper<OutsourceReturnOrderProduct>().eq(OutsourceReturnOrderProduct::getReturnOrderId, id)));
        m.put("createTime", o.getCreateTime());
        if (o.getFactoryId() != null) {
            Supplier f = supplierMapper.selectById(o.getFactoryId());
            m.put("factoryName", f != null ? f.getName() : "");
        }
        if (o.getOrderId() != null) {
            OutsourceOrder ord = orderMapper.selectById(o.getOrderId());
            m.put("orderCode", ord != null ? ord.getCode() : "");
        }
        m.put("items", returnOrderItemMapper.selectList(
            new LambdaQueryWrapper<ReturnOrderItem>().eq(ReturnOrderItem::getReturnOrderId, id)));
        return m;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(ReturnOrder order, Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> itemsRaw = (List<Map<String, Object>>) body.get("items");
        if (itemsRaw == null || itemsRaw.isEmpty()) throw new BusinessException("请添加退货物料");

        order.setCode(generateCode());
        if (order.getReturnDate() == null) order.setReturnDate(LocalDate.now());
        order.setStatus(DocStatus.DRAFT.getCode());
        // 成品出库仓（审核时从我方仓扣减成品）
        Object invWhObj = body.get("warehouseId");
        if (invWhObj != null && !invWhObj.toString().isBlank()) order.setWarehouseId(Long.valueOf(invWhObj.toString()));
        order.setRemark((String) body.get("remark"));
        // 收费字段（我方支付给加工厂的费用）：不收费归零，收费则类型必须合法且金额 > 0
        normalizeCharge(order, body);
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) order.setCompanyId(cid);
        returnOrderMapper.insert(order);

        saveDetailLines(order, body, cid);
    }

    /**
     * 编辑草稿（E4 · 2026-09-12 补端点）。
     * <p>草稿阶段不产生库存/应付副作用，故明细可整体替换（删旧插新）。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ReturnOrder order, Map<String, Object> body) {
        ReturnOrder exist = returnOrderMapper.selectById(id);
        if (exist == null) throw new BusinessException("退货单不存在");
        // 用 DRAFT→DRAFT 的条件更新充当"草稿才可编辑"的原子护栏（并发编辑/编辑与审核并发时只有一个能过）
        if (!DocStatusGuard.claim(returnOrderMapper, ReturnOrder::getId, id,
                ReturnOrder::getStatus, DocStatus.DRAFT.getCode(), DocStatus.DRAFT.getCode())) {
            throw new BusinessException("只有草稿状态可编辑");
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> itemsRaw = (List<Map<String, Object>>) body.get("items");
        if (itemsRaw == null || itemsRaw.isEmpty()) throw new BusinessException("请添加退货物料");

        // 表头：单号/状态不可改；仓库不传则保持原值（MP updateById 会忽略 null）
        order.setId(id);
        order.setCode(exist.getCode());
        order.setStatus(DocStatus.DRAFT.getCode());
        Object invWhObj = body.get("warehouseId");
        if (invWhObj != null && !invWhObj.toString().isBlank()) order.setWarehouseId(Long.valueOf(invWhObj.toString()));
        order.setRemark((String) body.get("remark"));
        normalizeCharge(order, body);
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) order.setCompanyId(cid);
        returnOrderMapper.updateById(order);

        // 明细整体替换
        returnOrderItemMapper.delete(new LambdaQueryWrapper<ReturnOrderItem>()
                .eq(ReturnOrderItem::getReturnOrderId, id));
        returnProductMapper.delete(new LambdaQueryWrapper<OutsourceReturnOrderProduct>()
                .eq(OutsourceReturnOrderProduct::getReturnOrderId, id));
        saveDetailLines(order, body, cid);
    }

    /** 保存明细（退货物料 FIFO 计价 + 退货成品）：create / update 共用，避免两处口径漂移（E4 抽出） */
    private void saveDetailLines(ReturnOrder order, Map<String, Object> body, Long cid) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> itemsRaw = (List<Map<String, Object>>) body.get("items");
        if (itemsRaw == null || itemsRaw.isEmpty()) throw new BusinessException("请添加退货物料");
        // 保存退货物料明细（FIFO 价，草稿阶段不动库存/应付）
        for (Map<String, Object> it : itemsRaw) {
            BigDecimal qty = toBigDecimal(it.get("quantity"));
            Long matId = toLong(it.get("materialId"));
            BigDecimal price = calcFifoPrice(matId, qty);

            ReturnOrderItem item = new ReturnOrderItem();
            item.setReturnOrderId(order.getId());
            item.setMaterialId(matId);
            item.setMaterialTypeId(toLong(it.get("materialTypeId")));
            item.setUnit((String) it.get("unit"));
            item.setQuantity(qty);
            item.setUnitPrice(price);
            item.setAmount(qty.multiply(price));
            item.setRemark((String) it.get("remark"));
            if (cid != null && cid > 0) item.setCompanyId(cid);
            returnOrderItemMapper.insert(item);
        }

        // 保存退货成品明细（审核时扣减成品库存）
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> products = (List<Map<String, Object>>) body.get("products");
        if (products != null) {
            for (Map<String, Object> p : products) {
                BigDecimal qty = toBigDecimal(p.get("quantity"));
                if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;
                OutsourceReturnOrderProduct prod = new OutsourceReturnOrderProduct();
                prod.setReturnOrderId(order.getId());
                // 产品维度统一落**产品主数据ID**：前端可能传订单产品行ID，这里解析后落库
                prod.setProductId(resolveStockProductId(order, toLong(p.get("productId")), toLong(p.get("productMasterId"))));
                prod.setProductName((String) p.get("productName"));
                prod.setQuantity(qty);
                prod.setQualityType(normalizeQualityType(p.get("qualityType")));
                if (cid != null && cid > 0) prod.setCompanyId(cid);
                returnProductMapper.insert(prod);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        ReturnOrder order = returnOrderMapper.selectById(id);
        if (order == null) throw new BusinessException("退货单不存在");
        // P2-29：原子抢占 DRAFT→AUDITED，避免并发/双击重复扣料与重复生成应付
        if (!DocStatusGuard.claim(returnOrderMapper, ReturnOrder::getId, id,
                ReturnOrder::getStatus, DocStatus.DRAFT.getCode(), DocStatus.AUDITED.getCode())) {
            throw new BusinessException("只有草稿状态可审核");
        }

        List<ReturnOrderItem> items = returnOrderItemMapper.selectList(
            new LambdaQueryWrapper<ReturnOrderItem>().eq(ReturnOrderItem::getReturnOrderId, id));
        if (items.isEmpty()) throw new BusinessException("退货单明细不能为空");
        // P2-33：数量必须为正；加工厂必须存在（否则找不到工厂委外仓会静默跳过退料入库）
        for (ReturnOrderItem it : items) {
            if (it.getQuantity() == null || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0)
                throw new BusinessException("退货数量必须大于 0（明细行ID=" + it.getId() + "）");
        }
        if (order.getFactoryId() == null || supplierMapper.selectById(order.getFactoryId()) == null)
            throw new BusinessException("加工厂不存在：ID=" + order.getFactoryId());

        // 工厂委外仓（物料退回目标仓）
        Long factoryWhId = null;
        if (order.getFactoryId() != null) {
            List<Warehouse> whs = warehouseMapper.selectList(
                new LambdaQueryWrapper<Warehouse>().eq(Warehouse::getFactoryId, order.getFactoryId()));
            factoryWhId = whs.isEmpty() ? null : whs.get(0).getId();
        }
        Long invWhId = order.getWarehouseId();

        // 1. 退货物料入工厂委外仓 + 流水
        BigDecimal totalReturnAmount = BigDecimal.ZERO;
        for (ReturnOrderItem it : items) {
            if (it.getQuantity() == null || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;
            if (factoryWhId != null && it.getMaterialId() != null) {
                updateOutsourceStock(factoryWhId, it.getMaterialId(), it.getQuantity(), QualityType.GOOD.getCode(), StockChangeType.RETURN_IN.getCode(), order.getCode());
            }
            if (it.getAmount() != null) totalReturnAmount = totalReturnAmount.add(it.getAmount());
        }

        // 2. 成品从我方仓减少
        if (invWhId != null) {
            List<OutsourceReturnOrderProduct> products = returnProductMapper.selectList(
                new LambdaQueryWrapper<OutsourceReturnOrderProduct>().eq(OutsourceReturnOrderProduct::getReturnOrderId, id));
            for (OutsourceReturnOrderProduct p : products) {
                if (p.getQuantity() == null || p.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;
                // 产品按主数据ID落账（历史数据存的可能是订单产品行ID，这里兜底解析）；
                // 规格按明细上的 quality_type 扣减（历史数据为空时按 A 规）
                warehouseStockService.changeStock(invWhId,
                        resolveStockProductId(order, p.getProductId(), null), p.getQuantity().negate(),
                        StockChangeType.OUTSOURCE_RETURN_OUT, order.getCode(), RelatedBillType.OUTSOURCE_RETURN,
                        null, order.getId(), normalizeQualityType(p.getQualityType()));
            }
        }

        // 3. 应付冲减（负向应付）
        if (totalReturnAmount.compareTo(BigDecimal.ZERO) > 0) {
            payableHelper.createPayable(order.getFactoryId(), SourceBillType.OUTSOURCE_RETURN.getCode(),
                order.getCode(), order.getId(), totalReturnAmount.negate(), order.getReturnDate(),
                "委外退料 - " + order.getCode());
        }

        // 4. 收费应付（正向）：我方支付给加工厂的费用，与退料冲减分开记账，便于对账
        //    两笔共用 sourceId=退货单ID，反审核时按「两个类型 + 退货单ID」一并冲销（见 unAudit）
        if (order.getChargeFlag() != null && order.getChargeFlag() == 1
                && order.getChargeAmount() != null && order.getChargeAmount().compareTo(BigDecimal.ZERO) > 0) {
            payableHelper.createPayable(order.getFactoryId(), SourceBillType.OUTSOURCE_RETURN_CHARGE.getCode(),
                order.getCode(), order.getId(), order.getChargeAmount(), order.getReturnDate(),
                "委外加工退货收费"
                    + (order.getChargeReason() != null && !order.getChargeReason().isBlank()
                        ? "：" + order.getChargeReason() : ""));
        }

        // 5. 更新状态与审计
        ReturnOrder u = new ReturnOrder();
        u.setId(id);
        u.setStatus(DocStatus.AUDITED.getCode());
        u.setAuditorId(getCurrentUserId());
        u.setAuditorName(getCurrentUserName());
        u.setAuditTime(LocalDateTime.now());
        returnOrderMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unAudit(Long id) {
        ReturnOrder order = returnOrderMapper.selectById(id);
        if (order == null) throw new BusinessException("退货单不存在");
        // P2-29：原子抢占 AUDITED→DRAFT，避免并发反审核重复回滚库存与应付
        if (!DocStatusGuard.claim(returnOrderMapper, ReturnOrder::getId, id,
                ReturnOrder::getStatus, DocStatus.AUDITED.getCode(), DocStatus.DRAFT.getCode())) {
            throw new BusinessException("只有已审核状态可取消审核");
        }

        // 工厂委外仓
        Long whId = null;
        if (order.getFactoryId() != null) {
            List<Warehouse> whs = warehouseMapper.selectList(
                new LambdaQueryWrapper<Warehouse>().eq(Warehouse::getFactoryId, order.getFactoryId()));
            whId = whs.isEmpty() ? null : whs.get(0).getId();
        }
        // 1. 物料逆向（从工厂委外仓扣回）
        List<ReturnOrderItem> items = returnOrderItemMapper.selectList(
            new LambdaQueryWrapper<ReturnOrderItem>().eq(ReturnOrderItem::getReturnOrderId, id));
        for (ReturnOrderItem it : items) {
            if (it.getQuantity() == null || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;
            if (whId != null && it.getMaterialId() != null) {
                updateOutsourceStock(whId, it.getMaterialId(), it.getQuantity().negate(), QualityType.GOOD.getCode(), StockChangeType.CANCEL_RETURN_IN.getCode(), order.getCode());
            }
        }
        // 2. 成品逆向（恢复我方成品库存）
        Long invWhId = order.getWarehouseId();
        if (invWhId != null) {
            List<OutsourceReturnOrderProduct> products = returnProductMapper.selectList(
                new LambdaQueryWrapper<OutsourceReturnOrderProduct>().eq(OutsourceReturnOrderProduct::getReturnOrderId, id));
            for (OutsourceReturnOrderProduct p : products) {
                if (p.getQuantity() == null || p.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;
                warehouseStockService.changeStock(invWhId,
                        resolveStockProductId(order, p.getProductId(), null), p.getQuantity(),
                        StockChangeType.OUTSOURCE_RETURN_OUT_UN_AUDIT, order.getCode(), RelatedBillType.OUTSOURCE_RETURN,
                        null, order.getId(), normalizeQualityType(p.getQualityType()));
            }
        }
        // 3. 冲销应付：同单两笔（退料负向 OUTSOURCE_RETURN + 退货收费正向 OUTSOURCE_RETURN_CHARGE）一并冲销
        payableHelper.reversePayable(id,
                SourceBillType.OUTSOURCE_RETURN.getCode(), SourceBillType.OUTSOURCE_RETURN_CHARGE.getCode());
        // 4. 回草稿
        // 审核信息必须用 UpdateWrapper 显式置 null：updateById 忽略 null 字段，反审核后仍显示审核人/时间
        returnOrderMapper.update(null, new LambdaUpdateWrapper<ReturnOrder>()
                .eq(ReturnOrder::getId, id)
                .set(ReturnOrder::getStatus, DocStatus.DRAFT.getCode())
                .set(ReturnOrder::getAuditorId, null)
                .set(ReturnOrder::getAuditorName, null)
                .set(ReturnOrder::getAuditTime, null));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        ReturnOrder order = returnOrderMapper.selectById(id);
        if (order == null) throw new BusinessException("退货单不存在");
        if (!DocStatus.DRAFT.getCode().equals(order.getStatus())) throw new BusinessException("只有草稿状态可作废");
        ReturnOrder u = new ReturnOrder();
        u.setId(id);
        u.setStatus(DocStatus.CANCELLED.getCode());
        returnOrderMapper.updateById(u);
    }

    @Override
    public BigDecimal fifoPrice(Long materialId, BigDecimal qty) {
        return calcFifoPrice(materialId, qty);
    }

    @Override
    public List<Map<String, Object>> orderProducts(Long factoryId) {
        // 查该工厂所有已确认/已结单的加工单
        List<OutsourceOrder> orders = orderMapper.selectList(
            new LambdaQueryWrapper<OutsourceOrder>().eq(OutsourceOrder::getFactoryId, factoryId)
                .in(OutsourceOrder::getStatus, OutsourceOrderStatus.PRODUCING.getCode(), OutsourceOrderStatus.FINISHED.getCode())
                .orderByDesc(OutsourceOrder::getCreateTime));
        // 按产品名汇总，每个产品列出可选的BOM版本
        Map<String, Map<String, Object>> productMap = new LinkedHashMap<>();
        for (OutsourceOrder o : orders) {
            List<OutsourceOrderProduct> prods = orderProductMapper.selectList(
                new LambdaQueryWrapper<OutsourceOrderProduct>().eq(OutsourceOrderProduct::getOrderId, o.getId()));
            for (OutsourceOrderProduct p : prods) {
                String pn = p.getProductName() != null ? p.getProductName() : "";
                if (pn.isBlank()) continue;
                Map<String, Object> pm = productMap.computeIfAbsent(pn, k -> {
                    Map<String, Object> x = new LinkedHashMap<>();
                    x.put("productName", k);
                    x.put("bomVersions", new ArrayList<Map<String, Object>>());
                    return x;
                });
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> versions = (List<Map<String, Object>>) pm.get("bomVersions");
                Map<String, Object> v = new LinkedHashMap<>();
                v.put("orderId", o.getId());
                v.put("orderCode", o.getCode());
                // productId = 订单产品行ID（前端选版本用）；productMasterId = 产品主数据ID（库存维度、提交时优先用）
                v.put("productId", p.getId());
                v.put("productMasterId", p.getProductId());
                v.put("createTime", o.getCreateTime());
                v.put("status", o.getStatus());
                versions.add(v);
            }
        }
        List<Map<String, Object>> result = new ArrayList<>(productMap.values());
        // 每个产品的版本按创建时间降序
        for (Map<String, Object> pm : result) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> versions = (List<Map<String, Object>>) pm.get("bomVersions");
            versions.sort((a, b) -> {
                Object at = a.get("createTime"), bt = b.get("createTime");
                if (at == null && bt == null) return 0;
                if (at == null) return 1;
                if (bt == null) return -1;
                return ((java.time.LocalDateTime) bt).compareTo((java.time.LocalDateTime) at);
            });
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> bomSnapshot(Long orderId, Long productId) {
        List<OutsourceOrderMaterial> mats = orderMaterialMapper.selectList(
            new LambdaQueryWrapper<OutsourceOrderMaterial>().eq(OutsourceOrderMaterial::getProductId, productId));
        Map<Long, Map<String, Object>> map = new LinkedHashMap<>();
        for (OutsourceOrderMaterial mat : mats) {
            Long key = mat.getMaterialId();
            if (key == null) continue;
            Map<String, Object> m = map.computeIfAbsent(key, k -> {
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("outsourceMaterialId", key);
                x.put("materialName", getMaterialNameById(key));
                x.put("materialTypeId", mat.getMaterialTypeId());
                x.put("materialTypeName", getMaterialTypeNameById(mat.getMaterialTypeId()));
                x.put("unit", mat.getUnit());
                x.put("perSetQuantity", BigDecimal.ZERO);
                return x;
            });
            BigDecimal d = mat.getDemandQuantity() != null ? mat.getDemandQuantity() : BigDecimal.ZERO;
            m.put("perSetQuantity", ((BigDecimal) m.get("perSetQuantity")).add(d));
        }
        // 计算单套用量 = 总需求 / 产品订单数量
        OutsourceOrderProduct prod = orderProductMapper.selectById(productId);
        BigDecimal productQty = prod != null && prod.getQuantity() != null ? prod.getQuantity() : BigDecimal.ONE;
        for (Map<String, Object> m : map.values()) {
            BigDecimal total = (BigDecimal) m.get("perSetQuantity");
            m.put("perSetQuantity", total.divide(productQty, 10, RoundingMode.HALF_UP));
        }
        return new ArrayList<>(map.values());
    }

    // ===== 私有方法 =====

    private void updateOutsourceStock(Long warehouseId, Long materialId, BigDecimal delta, String qualityType, String changeType, String orderCode) {
        // 物料库存统一 GOOD 品质；统一走标准库存服务 changeMaterialStock：
        // 1) 流水自动补齐 quality_type / before_quantity / after_quantity（原原生 SQL 漏写，对账漏行）
        // 2) 负向扣减带库存充足校验（原原生 SQL 直接 UPDATE 会把库存扣成负数）
        // 3) related_order_code 口径与标准物料流水一致
        if (warehouseId == null || materialId == null) return;
        warehouseStockService.changeMaterialStock(warehouseId, materialId, delta, changeType, orderCode,
                RelatedBillType.OUTSOURCE_RETURN, null, null);
    }

    /**
     * 收费字段归一化（与销售退货 SaleReturnServiceImpl.normalizeCharge 同策略）：
     * 不收费则金额归零、类型清空；收费则类型必须合法且金额必须 > 0。
     * 收费是「我方支付给加工厂」的费用，审核后生成正向应付。
     */
    private void normalizeCharge(ReturnOrder order, Map<String, Object> body) {
        Long flagVal = toLong(body.get("chargeFlag"));
        if (flagVal == null || flagVal != 1) {
            order.setChargeFlag(0);
            order.setChargeType(null);
            order.setChargeAmount(BigDecimal.ZERO);
            order.setChargeReason(null);
            return;
        }
        Object t = body.get("chargeType");
        String type = t == null ? null : t.toString().trim();
        if (type == null || type.isEmpty()) throw new BusinessException("已选择收费，请选择收费类型");
        if (!OutsourceChargeType.isValid(type))
            throw new BusinessException("非法的收费类型：" + type);
        BigDecimal amount = toBigDecimal(body.get("chargeAmount"));
        if (amount.compareTo(BigDecimal.ZERO) <= 0)
            throw new BusinessException("已选择收费，收费金额必须大于 0");
        order.setChargeFlag(1);
        order.setChargeType(OutsourceChargeType.fromCode(type).getCode());
        order.setChargeAmount(amount);
        order.setChargeReason((String) body.get("chargeReason"));
    }

    private BigDecimal calcFifoPrice(Long materialId, BigDecimal requiredQty) {
        if (materialId == null || requiredQty == null || requiredQty.compareTo(BigDecimal.ZERO) <= 0)
            return BigDecimal.ZERO;
        // 1) 优先取系统统一成本（物料移动加权成本，由成本服务维护）：
        //    覆盖「委外其他出入库」「结算退料」等一切入库来源；原实现只扫物料订单，
        //    非物料订单来源的物料会算出 0 价 → 退货金额 0 → 负向应付不生成（应付漏冲减）。
        OutsourceMaterial mat = outsourceMaterialMapper.selectById(materialId);
        if (mat != null && mat.getCostPrice() != null && mat.getCostPrice().compareTo(BigDecimal.ZERO) > 0)
            return mat.getCostPrice();
        // 2) 兜底：物料订单 FIFO（历史口径，保留以兼容未建立移动加权成本的物料）
        try {
            List<MaterialOrder> orders = materialOrderMapper.selectList(
                new LambdaQueryWrapper<MaterialOrder>().orderByAsc(MaterialOrder::getDeliveryDate));
            BigDecimal accumulatedAmount = BigDecimal.ZERO, accumulatedQty = BigDecimal.ZERO;
            for (MaterialOrder o : orders) {
                LambdaQueryWrapper<MaterialOrderItem> itemW = new LambdaQueryWrapper<MaterialOrderItem>()
                    .eq(MaterialOrderItem::getOrderId, o.getId())
                    .eq(MaterialOrderItem::getMaterialId, materialId);
                List<MaterialOrderItem> items = materialOrderItemMapper.selectList(itemW);
                for (MaterialOrderItem itt : items) {
                    BigDecimal q = itt.getOrderQuantity() != null ? itt.getOrderQuantity() : BigDecimal.ZERO;
                    BigDecimal p = itt.getUnitPrice() != null ? itt.getUnitPrice() : BigDecimal.ZERO;
                    if (q.compareTo(BigDecimal.ZERO) <= 0 || p.compareTo(BigDecimal.ZERO) <= 0) continue;
                    BigDecimal need = requiredQty.subtract(accumulatedQty);
                    if (need.compareTo(BigDecimal.ZERO) <= 0) break;
                    BigDecimal use = q.min(need);
                    accumulatedAmount = accumulatedAmount.add(use.multiply(p));
                    accumulatedQty = accumulatedQty.add(use);
                }
                if (accumulatedQty.compareTo(requiredQty) >= 0) break;
            }
            if (accumulatedQty.compareTo(BigDecimal.ZERO) > 0)
                return accumulatedAmount.divide(accumulatedQty, 4, RoundingMode.HALF_UP);
        } catch (Exception e) { log.warn("FIFO单价计算失败: {}", e.getMessage()); }
        // 3) 再兜底：物料主数据参考单价
        if (mat != null && mat.getPrice() != null && mat.getPrice().compareTo(BigDecimal.ZERO) > 0)
            return mat.getPrice();
        return BigDecimal.ZERO;
    }

    /**
     * 退货成品的产品维度统一为「产品主数据ID」。
     * <p>前端产品下拉取值来自 {@link #orderProducts(Long)} 的**订单产品行ID**，若直接当主数据ID写库存，
     * 会出现「库存不足，无法出库：产品ID=行ID」或扣错产品。此处统一解析：
     * 显式传了主数据ID优先；否则按订单产品行ID解析出主数据ID；解析不到则原样返回（交由库存校验报错）。</p>
     */
    private Long resolveStockProductId(ReturnOrder order, Long productKey, Long explicitMasterId) {
        if (explicitMasterId != null) return explicitMasterId;
        if (productKey == null) return null;
        OutsourceOrderProduct row = orderProductMapper.selectById(productKey);
        if (row != null && row.getProductId() != null
                && (order == null || order.getOrderId() == null || order.getOrderId().equals(row.getOrderId()))) {
            return row.getProductId();
        }
        return productKey;
    }

    /** 退回成品规格归一化：仅允许 A/B/C/DEFECT，空值按 A 规 */
    private String normalizeQualityType(Object raw) {
        String code = raw == null ? null : raw.toString().trim();
        if (code == null || code.isEmpty()) return ProductQualityType.A.getCode();
        String upper = code.toUpperCase();
        if ("A".equals(upper) || "B".equals(upper) || "C".equals(upper) || "DEFECT".equals(upper))
            return upper;
        throw new BusinessException("非法的退回成品规格：" + code);
    }

    private BigDecimal toBigDecimal(Object val) {
        if (val == null) return BigDecimal.ZERO;
        String s = val.toString().trim();
        if (s.isEmpty()) return BigDecimal.ZERO;
        try { return new BigDecimal(s); } catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }

    private Long toLong(Object val) {
        if (val == null) return null;
        String s = val.toString().trim();
        if (s.isEmpty()) return null;
        try { return Long.valueOf(s); } catch (NumberFormatException e) { return null; }
    }

    /** 根据委外物料ID查询名称，用于展示回填（ID关联查询替代冗余name字段） */
    private String getMaterialNameById(Long materialId) {
        if (materialId == null) return "";
        OutsourceMaterial m = outsourceMaterialMapper.selectById(materialId);
        return m != null ? m.getMaterialName() : "";
    }

    /** 根据 物料类型ID 查询类型名称，空安全返回 "-" */
    private String getMaterialTypeNameById(Long materialTypeId) {
        if (materialTypeId == null) return "-";
        com.beichen.erp.dev.entity.MaterialType bt = materialTypeMapper.selectById(materialTypeId);
        return bt != null ? bt.getTypeName() : "-";
    }

    private String generateCode() {
        String prefix = BillPrefix.OUTSOURCE_RETURN_ORDER + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        Long seq = returnOrderMapper.selectCount(
            new LambdaQueryWrapper<ReturnOrder>().likeRight(ReturnOrder::getCode, prefix)) + 1;
        return prefix + String.format("%03d", seq);
    }

    /** 当前登录用户ID */
    private Long getCurrentUserId() {
        try { return StpUtil.getLoginIdAsLong(); } catch (Exception e) { return null; }
    }

    /** 当前登录用户名 */
    private String getCurrentUserName() {
        try {
            Long userId = StpUtil.getLoginIdAsLong();
            User user = userMapper.selectById(userId);
            return user != null ? user.getUsername() : null;
        } catch (Exception e) { return null; }
    }
}
