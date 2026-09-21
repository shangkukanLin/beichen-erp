package com.beichen.erp.purchase.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.auth.entity.User;
import com.beichen.erp.auth.mapper.UserMapper;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.common.BillPrefix;
import com.beichen.erp.common.DocStatus;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.common.DocStatusGuard;
import com.beichen.erp.inventory.common.RelatedBillType;
import com.beichen.erp.finance.common.SettlementStatus;
import com.beichen.erp.finance.common.SourceBillType;
import com.beichen.erp.inventory.common.StockChangeType;
import com.beichen.erp.finance.entity.FinancePayable;
import com.beichen.erp.finance.mapper.FinancePayableMapper;
import com.beichen.erp.warehouse.service.WarehouseStockService;
import com.beichen.erp.material.entity.Product;
import com.beichen.erp.material.mapper.ProductMapper;
import com.beichen.erp.purchase.common.PurchaseChargeType;
import com.beichen.erp.purchase.entity.*;
import com.beichen.erp.purchase.mapper.PurchaseOrderItemMapper;
import com.beichen.erp.purchase.mapper.PurchaseOrderMapper;
import com.beichen.erp.purchase.mapper.PurchaseReturnItemMapper;
import com.beichen.erp.purchase.mapper.PurchaseReturnMapper;
import com.beichen.erp.purchase.service.PurchaseReturnService;
import com.beichen.erp.supplier.entity.Supplier;
import com.beichen.erp.supplier.mapper.SupplierMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PurchaseReturnServiceImpl implements PurchaseReturnService {

    private final PurchaseReturnMapper returnMapper;
    private final PurchaseReturnItemMapper itemMapper;
    private final SupplierMapper supplierMapper;
    private final ProductMapper productMapper;
    private final WarehouseStockService stockService;
    private final FinancePayableMapper payableMapper;
    private final UserMapper userMapper;
    private final com.beichen.erp.finance.service.PayableHelper payableHelper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final PurchaseOrderItemMapper purchaseOrderItemMapper;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public Page<Map<String, Object>> page(Integer status, Long supplierId, String code, int pageNum, int pageSize) {
        LambdaQueryWrapper<PurchaseReturn> w = new LambdaQueryWrapper<PurchaseReturn>()
                .eq(status != null, PurchaseReturn::getStatus, status)
                .eq(supplierId != null, PurchaseReturn::getSupplierId, supplierId)
                .like(code != null && !code.isBlank(), PurchaseReturn::getCode, code)
                .orderByDesc(PurchaseReturn::getId);
        Page<PurchaseReturn> raw = returnMapper.selectPage(new Page<>(pageNum, pageSize), w);
        // 批量查产品
        Map<Long, Product> productMap = new HashMap<>();
        if (!raw.getRecords().isEmpty()) {
            List<Long> returnIds = raw.getRecords().stream().map(PurchaseReturn::getId).collect(Collectors.toList());
            List<PurchaseReturnItem> allItems = itemMapper.selectList(
                    new LambdaQueryWrapper<PurchaseReturnItem>().in(PurchaseReturnItem::getReturnId, returnIds));
            Set<Long> productIds = allItems.stream().map(PurchaseReturnItem::getProductId).filter(Objects::nonNull).collect(Collectors.toSet());
            if (!productIds.isEmpty()) {
                List<Product> products = productMapper.selectBatchIds(productIds);
                for (Product p : products) productMap.put(p.getId(), p);
            }
        }
        // 批量查明细
        Map<Long, List<PurchaseReturnItem>> itemsMap = new HashMap<>();
        if (!raw.getRecords().isEmpty()) {
            List<Long> returnIds = raw.getRecords().stream().map(PurchaseReturn::getId).collect(Collectors.toList());
            List<PurchaseReturnItem> allItems = itemMapper.selectList(
                    new LambdaQueryWrapper<PurchaseReturnItem>().in(PurchaseReturnItem::getReturnId, returnIds));
            itemsMap = allItems.stream().collect(Collectors.groupingBy(PurchaseReturnItem::getReturnId));
        }
        Map<Long, List<PurchaseReturnItem>> finalItemsMap = itemsMap;
        Map<Long, Product> finalProductMap = productMap;
        Page<Map<String, Object>> res = new Page<>(pageNum, pageSize, raw.getTotal());
        res.setRecords(raw.getRecords().stream().map(o -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", o.getId());
            m.put("code", o.getCode());
            m.put("supplierId", o.getSupplierId());
            m.put("warehouseId", o.getWarehouseId());
            m.put("purchaseOrderId", o.getPurchaseOrderId());
            m.put("purchaseOrderCode", o.getPurchaseOrderCode());
            m.put("returnDate", o.getReturnDate());
            m.put("status", o.getStatus());
            m.put("totalAmount", o.getTotalAmount());
            m.put("remark", o.getRemark());
            m.put("createTime", o.getCreateTime());
            if (o.getSupplierId() != null) {
                Supplier s = supplierMapper.selectById(o.getSupplierId());
                m.put("supplierName", s != null ? s.getName() : "");
            }
            // 退货明细摘要
            List<PurchaseReturnItem> returnItems = finalItemsMap.getOrDefault(o.getId(), Collections.emptyList());
            String itemsSummary = returnItems.stream()
                    .map(it -> {
                        String name = it.getProductId() != null && finalProductMap.containsKey(it.getProductId())
                                ? finalProductMap.get(it.getProductId()).getName() : "";
                        String qty = it.getQuantity() != null ? it.getQuantity().stripTrailingZeros().toPlainString() : "0";
                        return name + "*" + qty;
                    })
                    .collect(Collectors.joining("，"));
            m.put("itemsSummary", itemsSummary);
            return m;
        }).toList());
        return res;
    }

    @Override
    public PurchaseReturn getById(Long id) {
        PurchaseReturn order = returnMapper.selectById(id);
        if (order == null) throw new BusinessException("退货单不存在");
        fillSupplierName(order);
        return order;
    }

    /** 按 supplierId 回填供货商名称（实体字段非表字段，供详情接口返回） */
    private void fillSupplierName(PurchaseReturn order) {
        if (order == null || order.getSupplierId() == null) return;
        Supplier s = supplierMapper.selectById(order.getSupplierId());
        order.setSupplierName(s != null ? s.getName() : "");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseReturn create(PurchaseReturn order, List<Map<String, Object>> itemMaps) {
        order.setId(null);
        order.setStatus(DocStatus.DRAFT.getCode());
        order.setCode(generateCode());
        fillPurchaseOrderInfo(order);
        validateReturnQuantity(order, itemMaps);
        normalizeCharge(order, itemMaps);
        if (order.getTotalAmount() == null) order.setTotalAmount(BigDecimal.ZERO);
        returnMapper.insert(order);
        saveItems(order.getId(), itemMaps);
        // 金额以明细 数量×单价 为准，避免前端未传 totalAmount 导致金额为 0
        recalcTotalAmount(order.getId());
        fillSupplierName(order);
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseReturn update(Long id, PurchaseReturn order, List<Map<String, Object>> itemMaps) {
        PurchaseReturn old = returnMapper.selectById(id);
        if (old == null) throw new BusinessException("退货单不存在");
        if (!DocStatus.DRAFT.getCode().equals(old.getStatus())) throw new BusinessException("只有草稿状态可编辑");
        order.setId(id);
        order.setCode(null); // 单号不可修改
        order.setStatus(null);
        fillPurchaseOrderInfo(order);
        validateReturnQuantity(order, itemMaps);
        normalizeCharge(order, itemMaps);
        if (order.getTotalAmount() == null) order.setTotalAmount(BigDecimal.ZERO);
        returnMapper.updateById(order);
        itemMapper.delete(new LambdaQueryWrapper<PurchaseReturnItem>().eq(PurchaseReturnItem::getReturnId, id));
        saveItems(id, itemMaps);
        recalcTotalAmount(id);
        PurchaseReturn updated = returnMapper.selectById(id);
        fillSupplierName(updated);
        return updated;
    }

    @Override
    public List<PurchaseReturnItem> getItems(Long returnId) {
        List<PurchaseReturnItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<PurchaseReturnItem>().eq(PurchaseReturnItem::getReturnId, returnId));
        // 批量回填产品名称，随明细一起返回，前端免查库
        Set<Long> pids = items.stream().map(PurchaseReturnItem::getProductId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (!pids.isEmpty()) {
            Map<Long, Product> pm = productMapper.selectBatchIds(pids).stream()
                    .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));
            for (PurchaseReturnItem it : items) {
                if (it.getProductId() != null && pm.containsKey(it.getProductId())) {
                    Product p = pm.get(it.getProductId());
                    it.setProductName(p.getName());
                    it.setSku(p.getSku() != null ? p.getSku() : "");
                }
            }
        }
        return items;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        PurchaseReturn order = returnMapper.selectById(id);
        if (order == null) throw new BusinessException("退货单不存在");
        // 原子抢占状态（P2-29）：并发/双击时只有一个请求能抢到，其余在此失败，避免库存/台账重复写
        if (!DocStatusGuard.claim(returnMapper, PurchaseReturn::getId, id, PurchaseReturn::getStatus,
                DocStatus.DRAFT.getCode(), DocStatus.AUDITED.getCode()))
            throw new BusinessException("只有草稿状态可审核");
        List<PurchaseReturnItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<PurchaseReturnItem>().eq(PurchaseReturnItem::getReturnId, id));
        if (items.isEmpty()) throw new BusinessException("退货单明细不能为空");
        // P2-33：数量必须为正（原先 <=0 被下面的循环静默 continue，会生成负向应付却不减库存 → 账实不符）
        for (PurchaseReturnItem it : items) {
            if (it.getQuantity() == null || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0)
                throw new BusinessException("退货数量必须大于 0（明细行ID=" + it.getId() + "）");
        }
        // 1) 库存联动：退货出库减库存
        for (PurchaseReturnItem it : items) {
            if (it.getQuantity() == null || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;
            Product product = it.getProductId() != null ? productMapper.selectById(it.getProductId()) : null;
            stockService.changeStock(order.getWarehouseId(),
                    product != null ? product.getName() : "",
                    it.getQuantity().negate(),
                    StockChangeType.RETURN_OUT, order.getCode(), RelatedBillType.PURCHASE_RETURN, it.getProductId(),
                    "", order.getId(), it.getQualityType());
        }
        // 1.5) 重算金额（兜底存量数据：totalAmount 按明细 数量×单价 重新计算，应付随之正确）
        BigDecimal totalAmount = recalcTotalAmount(id);
        order.setTotalAmount(totalAmount);
        // 2) 冲减应付：新增负数应付台账（D1 口径 2026-09-12：台账号一律 YF- 流水号，来源单号仍写 source_bill_no）
        FinancePayable fp = new FinancePayable();
        fp.setBillNo(payableHelper.newBillNo());
        fp.setSupplierId(order.getSupplierId());
        Supplier s = order.getSupplierId() != null ? supplierMapper.selectById(order.getSupplierId()) : null;
        fp.setSupplierName(s != null ? s.getName() : "");
        fp.setSourceBillType(SourceBillType.PURCHASE_RETURN.getCode());
        fp.setSourceBillNo(order.getCode());
        fp.setSourceId(order.getId());
        fp.setAmount(order.getTotalAmount().negate());
        fp.setPaidAmount(BigDecimal.ZERO);
        fp.setUnpaidAmount(order.getTotalAmount().negate());
        fp.setDueDate(order.getReturnDate());
        fp.setStatus(SettlementStatus.UNSETTLED.getCode());
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) fp.setCompanyId(cid);
        // 按 bill_no 保存：反审核后该单号台账已存在（仅置 CANCELLED 留痕），必须复用重置，
        // 否则再次审核会撞 finance_payable.uk_bill_no（2026-09-10 审核发现，P1-01）
        payableHelper.saveByBillNo(fp);
        // 2.5) 是否付费（2026-09-21 用户口径）：**我们向供货商付费** ⇒ 额外一条**正向应付**
        //      金额 = Σ 明细行付费（精确到产品），remark 逐产品列出（口径 A：一张单据一条台账）
        saveChargePayable(order);
        // 3) 更新状态
        PurchaseReturn u = new PurchaseReturn();
        u.setId(id);
        u.setStatus(DocStatus.AUDITED.getCode());
        u.setAuditorId(getCurrentUserId());
        u.setAuditorName(getCurrentUserName());
        u.setAuditTime(LocalDateTime.now());
        returnMapper.updateById(u);
    }

    /**
     * 按明细重算金额：回填每行 amount = 数量 × 单价，并汇总主表 totalAmount。
     * 前端提交的明细不含金额，历史数据 totalAmount 恒为 0，审核生成应付时必须先重算。
     * @return 重算后的总金额
     */
    private BigDecimal recalcTotalAmount(Long returnId) {
        List<PurchaseReturnItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<PurchaseReturnItem>().eq(PurchaseReturnItem::getReturnId, returnId));
        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseReturnItem it : items) {
            BigDecimal qty = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
            BigDecimal price = it.getUnitPrice() != null ? it.getUnitPrice() : BigDecimal.ZERO;
            BigDecimal amount = qty.multiply(price).setScale(2, RoundingMode.HALF_UP);
            if (it.getAmount() == null || it.getAmount().compareTo(amount) != 0) {
                PurchaseReturnItem u = new PurchaseReturnItem();
                u.setId(it.getId());
                u.setAmount(amount);
                itemMapper.updateById(u);
            }
            total = total.add(amount);
        }
        PurchaseReturn u = new PurchaseReturn();
        u.setId(returnId);
        u.setTotalAmount(total);
        returnMapper.updateById(u);
        return total;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unAudit(Long id) {
        PurchaseReturn order = returnMapper.selectById(id);
        if (order == null) throw new BusinessException("退货单不存在");
        // 原子抢占状态（P2-29）：并发/双击时只有一个请求能抢到，其余在此失败，避免库存/应付重复冲销
        if (!DocStatusGuard.claim(returnMapper, PurchaseReturn::getId, id, PurchaseReturn::getStatus,
                DocStatus.AUDITED.getCode(), DocStatus.DRAFT.getCode()))
            throw new BusinessException("只有已完成的退货单可反审核");
        // 1) 检查应付台账
        // 2026-09-21：本单所有台账都要查 —— 退回冲减（PURCHASE_RETURN）+ 付费（PURCHASE_RETURN_CHARGE），
        // 下面既做"已核销/已转应收"护栏，也统一冲销（漏掉付费那条会让应付永远挂着）
        LambdaQueryWrapper<FinancePayable> payableW = new LambdaQueryWrapper<FinancePayable>()
                .in(FinancePayable::getSourceBillType, SourceBillType.PURCHASE_RETURN.getCode(),
                        SourceBillType.PURCHASE_RETURN_CHARGE.getCode())
                .eq(FinancePayable::getSourceBillNo, order.getCode());
        List<FinancePayable> payables = payableMapper.selectList(payableW);
        for (FinancePayable fp : payables) {
            // 只拦「真正核销过」的（已结清/部分结清/有付款额）；CANCELLED 是反审核自身的冲销留痕，不算核销
            if (SettlementStatus.isSettled(fp.getStatus(), fp.getPaidAmount())) {
                throw new BusinessException("该退货单对应的应付账款已核销，无法反审核");
            }
            // 已转应收的冲减项挂着供应商应收台账，删除会造成应收悬空，须先反审核对应转应收单
            if (Integer.valueOf(1).equals(fp.getTransferredToReceivable())) {
                throw new BusinessException("该退货单的扣款已转应收，请先反审核对应的转应收单");
            }
        }
        // 2) 恢复库存
        List<PurchaseReturnItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<PurchaseReturnItem>().eq(PurchaseReturnItem::getReturnId, id));
        for (PurchaseReturnItem it : items) {
            if (it.getQuantity() == null || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;
            Product product = it.getProductId() != null ? productMapper.selectById(it.getProductId()) : null;
            stockService.changeStock(order.getWarehouseId(),
                    product != null ? product.getName() : "",
                    it.getQuantity(),
                    StockChangeType.RETURN_UN_AUDIT, order.getCode(), RelatedBillType.PURCHASE_RETURN, it.getProductId(),
                    "", order.getId(), it.getQualityType());
        }
        // 3) 冲销应付台账：置「已作废」并保留审计，不再物理删除（与委外交货反审核一致）
        //    I29 口径（2026-09-18）：作废行**金额一并清零**（原金额记入备注留痕），
        //    使「amount = paid + unpaid」在所有行上恒成立，行级对账不再误报作废行
        for (FinancePayable fp : payables) {
            payableHelper.cancelLedger(fp);
        }
        // 4) 回退到草稿
        // 审核信息必须用 UpdateWrapper 显式置 null：updateById 忽略 null 字段，反审核后仍显示审核人/时间
        returnMapper.update(null, new LambdaUpdateWrapper<PurchaseReturn>()
                .eq(PurchaseReturn::getId, id)
                .set(PurchaseReturn::getStatus, DocStatus.DRAFT.getCode())
                .set(PurchaseReturn::getAuditorId, null)
                .set(PurchaseReturn::getAuditorName, null)
                .set(PurchaseReturn::getAuditTime, null));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        PurchaseReturn old = returnMapper.selectById(id);
        if (old == null) throw new BusinessException("退货单不存在");
        // 原子抢占状态（P2-29）：并发/双击时只有一个请求能抢到，其余在此失败
        if (!DocStatusGuard.claim(returnMapper, PurchaseReturn::getId, id, PurchaseReturn::getStatus,
                DocStatus.DRAFT.getCode(), DocStatus.CANCELLED.getCode()))
            throw new BusinessException("只有草稿状态可作废");
        PurchaseReturn u = new PurchaseReturn();
        u.setId(id);
        u.setStatus(DocStatus.CANCELLED.getCode());
        returnMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        // 原子删除（O-7）：带状态条件的物理删，只有草稿能删；affected=0 说明已被并发删除/审核或状态已变
        int rows = returnMapper.delete(new LambdaQueryWrapper<PurchaseReturn>()
                .eq(PurchaseReturn::getId, id)
                .eq(PurchaseReturn::getStatus, DocStatus.DRAFT.getCode()));
        if (rows == 0) {
            if (returnMapper.selectById(id) == null) throw new BusinessException("退货单不存在");
            throw new BusinessException("只有草稿状态可删除");
        }
        itemMapper.delete(new LambdaQueryWrapper<PurchaseReturnItem>().eq(PurchaseReturnItem::getReturnId, id));
    }

    @Override
    public List<Map<String, Object>> byOrder(Long purchaseOrderId) {
        if (purchaseOrderId == null) return List.of();
        List<PurchaseReturn> list = returnMapper.selectList(new LambdaQueryWrapper<PurchaseReturn>()
                .eq(PurchaseReturn::getPurchaseOrderId, purchaseOrderId)
                .orderByDesc(PurchaseReturn::getId));
        List<Map<String, Object>> res = new ArrayList<>();
        for (PurchaseReturn o : list) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", o.getId());
            m.put("code", o.getCode());
            m.put("returnDate", o.getReturnDate());
            m.put("status", o.getStatus());
            m.put("totalAmount", o.getTotalAmount());
            m.put("warehouseId", o.getWarehouseId());
            res.add(m);
        }
        return res;
    }

    @Override
    public List<Map<String, Object>> purchaseOrderItems(Long purchaseOrderId) {
        if (purchaseOrderId == null) return List.of();
        List<PurchaseOrderItem> oiList = purchaseOrderItemMapper.selectList(new LambdaQueryWrapper<PurchaseOrderItem>()
                .eq(PurchaseOrderItem::getOrderId, purchaseOrderId));
        Map<Long, Product> pMap = new HashMap<>();
        Set<Long> pids = oiList.stream().map(PurchaseOrderItem::getProductId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (!pids.isEmpty()) productMapper.selectBatchIds(pids).forEach(p -> pMap.put(p.getId(), p));
        List<Map<String, Object>> res = new ArrayList<>();
        for (PurchaseOrderItem oi : oiList) {
            Product p = pMap.get(oi.getProductId());
            BigDecimal sold = oi.getQuantity() == null ? BigDecimal.ZERO : oi.getQuantity();
            BigDecimal returned = alreadyReturned(oi.getId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("purchaseOrderItemId", oi.getId());
            m.put("productId", oi.getProductId());
            m.put("productName", p != null ? p.getName() : "");
            m.put("unit", p != null ? p.getUnit() : "");
            m.put("qualityType", oi.getQualityType());
            m.put("quantity", oi.getQuantity());
            m.put("unitPrice", oi.getUnitPrice());
            m.put("amount", oi.getAmount());
            m.put("returnedQuantity", returned);
            m.put("canReturn", sold.subtract(returned));
            res.add(m);
        }
        return res;
    }

    /** 已退累计数量：关联该采购单明细的已审核退货单数量之和 */
    private BigDecimal alreadyReturned(Long purchaseOrderItemId) {
        return jdbcTemplate.query(
                "SELECT COALESCE(SUM(ri.quantity), 0) FROM purchase_return_item ri " +
                "JOIN purchase_return sr ON sr.id = ri.return_id " +
                "WHERE ri.purchase_order_item_id = ? AND sr.status = 'AUDITED'",
                rs -> rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO, purchaseOrderItemId);
    }

    /** 填充关联采购单号（按 purchaseOrderId 实时查名） */
    private void fillPurchaseOrderInfo(PurchaseReturn order) {
        if (order.getPurchaseOrderId() != null) {
            PurchaseOrder po = purchaseOrderMapper.selectById(order.getPurchaseOrderId());
            order.setPurchaseOrderCode(po != null ? po.getCode() : null);
        } else {
            order.setPurchaseOrderCode(null);
        }
    }

    /** 关联采购单时校验：本次退货 ≤ 已购 - 已退（仅校验带 purchaseOrderItemId 的行） */
    private void validateReturnQuantity(PurchaseReturn order, List<Map<String, Object>> itemMaps) {
        if (order.getPurchaseOrderId() == null || itemMaps == null || itemMaps.isEmpty()) return;
        Map<Long, BigDecimal> qtyMap = new HashMap<>();
        Map<Long, String> nameMap = new HashMap<>();
        for (Map<String, Object> map : itemMaps) {
            Object poiObj = map.get("purchaseOrderItemId");
            if (poiObj == null || poiObj.toString().isBlank()) continue;
            Long poiId = Long.valueOf(poiObj.toString());
            BigDecimal qty = map.get("quantity") != null ? new BigDecimal(map.get("quantity").toString()) : BigDecimal.ZERO;
            qtyMap.merge(poiId, qty, BigDecimal::add);
            if (map.get("productId") != null) {
                Product p = productMapper.selectById(Long.valueOf(map.get("productId").toString()));
                if (p != null) nameMap.put(poiId, p.getName());
            }
        }
        if (qtyMap.isEmpty()) return;
        List<PurchaseOrderItem> oiList = purchaseOrderItemMapper.selectBatchIds(qtyMap.keySet());
        for (PurchaseOrderItem oi : oiList) {
            BigDecimal sold = oi.getQuantity() == null ? BigDecimal.ZERO : oi.getQuantity();
            BigDecimal returned = alreadyReturned(oi.getId());
            BigDecimal canReturn = sold.subtract(returned);
            BigDecimal thisQty = qtyMap.getOrDefault(oi.getId(), BigDecimal.ZERO);
            if (thisQty.compareTo(canReturn) > 0) {
                String name = nameMap.getOrDefault(oi.getId(), String.valueOf(oi.getProductId()));
                throw new BusinessException("产品[" + name + "]退货数量超过可退数量（已购" + fmt(sold)
                        + "，已退" + fmt(returned) + "，可退" + fmt(canReturn) + "）");
            }
        }
    }

    private String fmt(BigDecimal v) {
        return v == null ? "0" : v.stripTrailingZeros().toPlainString();
    }

    private void saveItems(Long returnId, List<Map<String, Object>> itemMaps) {
        if (itemMaps != null) {
            for (Map<String, Object> map : itemMaps) {
                PurchaseReturnItem it = new PurchaseReturnItem();
                it.setReturnId(returnId);
                if (map.get("purchaseOrderItemId") != null && !map.get("purchaseOrderItemId").toString().isBlank())
                    it.setPurchaseOrderItemId(Long.valueOf(map.get("purchaseOrderItemId").toString()));
                if (map.get("productId") != null) it.setProductId(Long.valueOf(map.get("productId").toString()));
                if (map.get("quantity") != null) it.setQuantity(new BigDecimal(map.get("quantity").toString()));
                if (map.get("unitPrice") != null) it.setUnitPrice(new BigDecimal(map.get("unitPrice").toString()));
                if (map.get("amount") != null) it.setAmount(new BigDecimal(map.get("amount").toString()));
                if (map.get("remark") != null) it.setRemark(map.get("remark").toString());
                if (map.get("qualityType") != null) it.setQualityType(map.get("qualityType").toString());
                // 逐产品付费（2026-09-21）：一行 = 一个产品；金额 > 0 即付费，类型由 normalizeCharge 保证
                BigDecimal payAmt = toBig(map.get("chargeAmount"));
                it.setChargeFlag(payAmt.compareTo(BigDecimal.ZERO) > 0 ? 1 : 0);
                it.setChargeAmount(payAmt);
                it.setChargeType(payAmt.compareTo(BigDecimal.ZERO) > 0 && map.get("chargeType") != null
                        ? map.get("chargeType").toString() : null);
                it.setChargeReason(map.get("chargeReason") != null ? map.get("chargeReason").toString() : null);
                itemMapper.insert(it);
            }
        }
        // 明细写完后统一回写单据级派生值（金额 = Σ 明细）—— 同时覆盖 create 与 update 两条路径
        recalcDocCharge(returnId);
    }

    // ==================== 逐产品付费（2026-09-21 用户口径：采购退货单也要有「是否付费」，且精确到产品） ====================

    /**
     * 付费归一化：**明细级为准**，单据级 charge_amount 由 {@link #recalcDocCharge} 按 Σ 明细回写。
     *
     * <p>单行金额 &gt; 0 必须**本行自带类型**；单据级类型只作整单外带值（校验合法），说明缺省时落到各行。
     * 兼容旧形状（只填单据级金额、没逐行填）时落到**第一条明细**（类型缺省 OTHER）。</p>
     *
     * <p>⚠️ 方向：<b>我们向供货商付费</b> ⇒ 审核生成一条正向应付
     * （source_bill_type = {@code PURCHASE_RETURN_CHARGE}），与退回侧冲减应付分开记账。</p>
     */
    private void normalizeCharge(PurchaseReturn order, List<Map<String, Object>> itemMaps) {
        if (order.getChargeType() != null && !order.getChargeType().isBlank()
                && !PurchaseChargeType.isValid(order.getChargeType()))
            throw new BusinessException("非法的付费类型：" + order.getChargeType());
        boolean anyItem = hasItemCharge(itemMaps);
        if (!anyItem && order.getChargeFlag() != null && order.getChargeFlag() == 1
                && toBig(order.getChargeAmount()).compareTo(BigDecimal.ZERO) > 0) {
            applyDocChargeToFirstItem(itemMaps, order.getChargeType(), order.getChargeAmount(), order.getChargeReason());
            anyItem = true;
        }
        if (!anyItem && order.getChargeFlag() != null && order.getChargeFlag() == 1)
            throw new BusinessException("已选择付费，请为具体产品填写付费金额（付费精确到产品）");
        if (itemMaps != null) {
            for (Map<String, Object> m : itemMaps) {
                BigDecimal amt = toBig(m.get("chargeAmount"));
                Object typeObj = m.get("chargeType");
                String type = typeObj != null ? typeObj.toString() : null;
                if (amt.compareTo(BigDecimal.ZERO) > 0) {
                    if (type == null || type.isBlank())
                        throw new BusinessException("产品["
                                + (m.get("productName") != null ? m.get("productName") : m.get("productId"))
                                + "]已填付费金额，请选择付费类型");
                    if (!PurchaseChargeType.isValid(type))
                        throw new BusinessException("非法的付费类型：" + type);
                    if (m.get("chargeReason") == null && order.getChargeReason() != null)
                        m.put("chargeReason", order.getChargeReason());
                } else {
                    m.put("chargeAmount", BigDecimal.ZERO);
                    m.remove("chargeType");
                }
            }
        }
        order.setChargeFlag(anyItem ? 1 : 0);
        order.setChargeAmount(BigDecimal.ZERO); // 交给 recalcDocCharge 按 Σ 明细回写
        if (!anyItem) {
            order.setChargeType(null);
            order.setChargeReason(null);
        }
    }

    /** 兼容旧形状：把"单据级付费"落到第一条明细（类型缺省 OTHER） */
    private void applyDocChargeToFirstItem(List<Map<String, Object>> itemMaps, String type,
                                          BigDecimal amount, String reason) {
        if (itemMaps == null || itemMaps.isEmpty())
            throw new BusinessException("已选择付费，请先添加明细（付费精确到产品）");
        Map<String, Object> first = itemMaps.get(0);
        first.put("chargeAmount", amount);
        first.put("chargeType", type != null && !type.isBlank() ? type : PurchaseChargeType.OTHER.getCode());
        if (reason != null) first.put("chargeReason", reason);
    }

    /** 本次提交里是否有任意一行填了付费金额（&gt; 0） */
    private boolean hasItemCharge(List<Map<String, Object>> itemMaps) {
        if (itemMaps == null) return false;
        for (Map<String, Object> m : itemMaps) {
            if (toBig(m.get("chargeAmount")).compareTo(BigDecimal.ZERO) > 0) return true;
        }
        return false;
    }

    /**
     * 主表付费 = Σ(明细)：charge_flag=任一行付费；charge_amount=Σ；
     * charge_type 仅当各付费行**类型一致**时回填，否则留空（详情显示"多类型"）。
     */
    private void recalcDocCharge(Long returnId) {
        List<PurchaseReturnItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<PurchaseReturnItem>().eq(PurchaseReturnItem::getReturnId, returnId));
        BigDecimal sum = BigDecimal.ZERO;
        java.util.Set<String> types = new java.util.LinkedHashSet<>();
        for (PurchaseReturnItem it : items) {
            BigDecimal amt = it.getChargeAmount() != null ? it.getChargeAmount() : BigDecimal.ZERO;
            if (amt.compareTo(BigDecimal.ZERO) > 0) {
                sum = sum.add(amt);
                if (it.getChargeType() != null && !it.getChargeType().isBlank()) types.add(it.getChargeType());
            }
        }
        returnMapper.update(null, new LambdaUpdateWrapper<PurchaseReturn>()
                .eq(PurchaseReturn::getId, returnId)
                .set(PurchaseReturn::getChargeFlag, sum.compareTo(BigDecimal.ZERO) > 0 ? 1 : 0)
                .set(PurchaseReturn::getChargeAmount, sum)
                .set(PurchaseReturn::getChargeType, types.size() == 1 ? types.iterator().next() : null));
    }

    /**
     * 审核时生成付费应付：**我们向供货商付费** ⇒ 一条正向应付（与退回侧的负向冲减分开记账）。
     * <p>口径 A（与销售侧一致）：金额 = Σ 明细行付费，remark 逐产品列出；未付费时不写任何台账。</p>
     */
    private void saveChargePayable(PurchaseReturn order) {
        // 用 getItems(...) 而不是裸 mapper：productName/sku 是 @TableField(exist=false) 的非表字段，
        // 裸 select 拿到的是 null ⇒ 台账 remark 会退化成"产品<id>"（实测踩到，断言 remark 含产品名失败）
        List<PurchaseReturnItem> items = getItems(order.getId());
        BigDecimal total = BigDecimal.ZERO;
        java.util.List<String> parts = new java.util.ArrayList<>();
        for (PurchaseReturnItem it : items) {
            BigDecimal amt = it.getChargeAmount() != null ? it.getChargeAmount() : BigDecimal.ZERO;
            if (amt.compareTo(BigDecimal.ZERO) <= 0) continue;
            total = total.add(amt);
            String name = it.getProductName() != null && !it.getProductName().isBlank()
                    ? it.getProductName() : "产品" + it.getProductId();
            parts.add(name + " " + amt.stripTrailingZeros().toPlainString()
                    + (it.getChargeType() != null && !it.getChargeType().isBlank()
                    ? "（" + it.getChargeType() + "）" : ""));
        }
        if (total.compareTo(BigDecimal.ZERO) <= 0) return;
        FinancePayable fp = new FinancePayable();
        fp.setBillNo(payableHelper.newBillNo());
        fp.setSupplierId(order.getSupplierId());
        Supplier s = order.getSupplierId() != null ? supplierMapper.selectById(order.getSupplierId()) : null;
        fp.setSupplierName(s != null ? s.getName() : "");
        fp.setSourceBillType(SourceBillType.PURCHASE_RETURN_CHARGE.getCode());
        fp.setSourceBillNo(order.getCode());
        fp.setSourceId(order.getId());
        fp.setAmount(total);
        fp.setPaidAmount(BigDecimal.ZERO);
        fp.setUnpaidAmount(total);
        fp.setDueDate(order.getReturnDate());
        fp.setStatus(SettlementStatus.UNSETTLED.getCode());
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) fp.setCompanyId(cid);
        fp.setRemark("采购退货付费（逐产品，我方付给供货商）：" + String.join("；", parts)
                + (order.getChargeReason() != null && !order.getChargeReason().isBlank()
                ? "；说明：" + order.getChargeReason() : ""));
        payableHelper.saveByBillNo(fp);
    }

    /** 宽松数值转换（Map 取值为 Object；null/非法一律按 0，避免 NPE 让整单保存失败） */
    private BigDecimal toBig(Object v) {
        if (v == null) return BigDecimal.ZERO;
        String s = v.toString().trim();
        if (s.isEmpty()) return BigDecimal.ZERO;
        try { return new BigDecimal(s); } catch (Exception e) { return BigDecimal.ZERO; }
    }

    private String generateCode() {
        String d = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String pat = BillPrefix.PURCHASE_RETURN + d;
        LambdaQueryWrapper<PurchaseReturn> w = new LambdaQueryWrapper<PurchaseReturn>()
                .likeRight(PurchaseReturn::getCode, pat).orderByDesc(PurchaseReturn::getCode).last("LIMIT 1");
        PurchaseReturn last = returnMapper.selectOne(w);
        int seq = 1;
        if (last != null && last.getCode() != null) {
            try {
                seq = Integer.parseInt(last.getCode().substring(last.getCode().length() - 3)) + 1;
            } catch (Exception e) { seq = 1; }
        }
        return BillPrefix.PURCHASE_RETURN + d + String.format("%03d", seq);
    }

    private Long getCurrentUserId() {
        try { return StpUtil.getLoginIdAsLong(); } catch (Exception e) { return null; }
    }

    private String getCurrentUserName() {
        try {
            Long userId = StpUtil.getLoginIdAsLong();
            User user = userMapper.selectById(userId);
            return user != null ? user.getUsername() : null;
        } catch (Exception e) { return null; }
    }
}
