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
import com.beichen.erp.dev.entity.BomType;
import com.beichen.erp.dev.mapper.BomTypeMapper;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.finance.common.SourceBillType;
import com.beichen.erp.finance.service.PayableHelper;
import com.beichen.erp.inventory.common.RelatedBillType;
import com.beichen.erp.inventory.common.StockChangeType;
import com.beichen.erp.outsource.common.MaterialReturnType;
import com.beichen.erp.outsource.common.QualityType;
import com.beichen.erp.outsource.entity.*;
import com.beichen.erp.outsource.mapper.*;
import com.beichen.erp.outsource.service.OutsourceMaterialReturnService;
import com.beichen.erp.supplier.entity.Supplier;
import com.beichen.erp.supplier.mapper.SupplierMapper;
import com.beichen.erp.warehouse.entity.Warehouse;
import com.beichen.erp.warehouse.entity.WarehouseStock;
import com.beichen.erp.warehouse.mapper.WarehouseMapper;
import com.beichen.erp.warehouse.mapper.WarehouseStockMapper;
import com.beichen.erp.warehouse.service.WarehouseStockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 委外物料退货单业务层实现
 * <p>物料从源仓退回物料商，冲减应付。草稿-审核-取消审核状态机。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutsourceMaterialReturnServiceImpl implements OutsourceMaterialReturnService {

    private final OutsourceMaterialReturnMapper returnMapper;
    private final OutsourceMaterialReturnItemMapper itemMapper;
    private final SupplierMapper supplierMapper;
    private final WarehouseMapper warehouseMapper;
    private final OutsourceMaterialMapper outsourceMaterialMapper;
    private final BomTypeMapper bomTypeMapper;
    private final WarehouseStockMapper warehouseStockMapper;
    /** 物料库存统一写入口（架构债 A1：原先本类私有实现直写 mapper + jdbcTemplate 写流水） */
    private final WarehouseStockService warehouseStockService;
    private final MaterialOrderMapper materialOrderMapper;
    private final MaterialOrderItemMapper materialOrderItemMapper;
    private final PayableHelper payableHelper;
    private final UserMapper userMapper;

    @Override
    public Page<Map<String, Object>> page(int pageNum, int pageSize, String code, Long supplierId, String status) {
        LambdaQueryWrapper<OutsourceMaterialReturn> w = new LambdaQueryWrapper<OutsourceMaterialReturn>()
                .eq(code != null && !code.isBlank(), OutsourceMaterialReturn::getCode, code)
                .eq(supplierId != null, OutsourceMaterialReturn::getSupplierId, supplierId)
                .eq(status != null && !status.isBlank(), OutsourceMaterialReturn::getStatus, status)
                .orderByDesc(OutsourceMaterialReturn::getId);
        Page<OutsourceMaterialReturn> raw = returnMapper.selectPage(new Page<>(pageNum, pageSize), w);
        Page<Map<String, Object>> result = new Page<>(pageNum, pageSize, raw.getTotal());
        result.setRecords(raw.getRecords().stream().map(o -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", o.getId()); m.put("code", o.getCode());
            m.put("returnType", o.getReturnType());
            m.put("supplierId", o.getSupplierId());
            m.put("fromWarehouseId", o.getFromWarehouseId());
            m.put("returnDate", o.getReturnDate());
            m.put("status", o.getStatus());
            m.put("remark", o.getRemark());
            m.put("createTime", o.getCreateTime());
            if (o.getSupplierId() != null) {
                Supplier s = supplierMapper.selectById(o.getSupplierId());
                m.put("supplierName", s != null ? s.getName() : "");
            }
            if (o.getFromWarehouseId() != null) {
                Warehouse wh = warehouseMapper.selectById(o.getFromWarehouseId());
                m.put("warehouseName", wh != null ? wh.getWarehouseName() : "");
            }
            List<OutsourceMaterialReturnItem> items = itemMapper.selectList(
                    new LambdaQueryWrapper<OutsourceMaterialReturnItem>().eq(OutsourceMaterialReturnItem::getReturnOrderId, o.getId()));
            BigDecimal totalQty = BigDecimal.ZERO;
            BigDecimal totalAmount = BigDecimal.ZERO;
            StringBuilder sb = new StringBuilder();
            for (OutsourceMaterialReturnItem it : items) {
                BigDecimal qty = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
                totalQty = totalQty.add(qty);
                if (it.getAmount() != null) totalAmount = totalAmount.add(it.getAmount());
                if (sb.length() > 0) sb.append("、");
                sb.append(getMaterialName(it.getMaterialId())).append("×").append(qty.stripTrailingZeros().toPlainString());
            }
            m.put("totalQuantity", totalQty);
            m.put("totalAmount", totalAmount);
            m.put("itemSummary", sb.toString());
            return m;
        }).toList());
        return result;
    }

    @Override
    public Map<String, Object> detail(Long id) {
        OutsourceMaterialReturn o = returnMapper.selectById(id);
        if (o == null) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.getId()); m.put("code", o.getCode());
        m.put("returnType", o.getReturnType());
        m.put("supplierId", o.getSupplierId());
        m.put("fromWarehouseId", o.getFromWarehouseId());
        m.put("returnDate", o.getReturnDate());
        m.put("status", o.getStatus());
        m.put("remark", o.getRemark());
        m.put("createTime", o.getCreateTime());
        if (o.getSupplierId() != null) {
            Supplier s = supplierMapper.selectById(o.getSupplierId());
            m.put("supplierName", s != null ? s.getName() : "");
        }
        if (o.getFromWarehouseId() != null) {
            Warehouse wh = warehouseMapper.selectById(o.getFromWarehouseId());
            m.put("warehouseName", wh != null ? wh.getWarehouseName() : "");
        }
        List<OutsourceMaterialReturnItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<OutsourceMaterialReturnItem>().eq(OutsourceMaterialReturnItem::getReturnOrderId, id));
        List<Map<String, Object>> itemList = new ArrayList<>();
        for (OutsourceMaterialReturnItem it : items) {
            Map<String, Object> im = new LinkedHashMap<>();
            im.put("id", it.getId());
            im.put("materialId", it.getMaterialId());
            im.put("materialName", getMaterialName(it.getMaterialId()));
            im.put("bomTypeId", it.getBomTypeId());
            im.put("bomTypeName", getBomTypeName(it.getBomTypeId()));
            im.put("unit", it.getUnit());
            im.put("quantity", it.getQuantity());
            im.put("unitPrice", it.getUnitPrice());
            im.put("amount", it.getAmount());
            im.put("remark", it.getRemark());
            itemList.add(im);
        }
        m.put("items", itemList);
        return m;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(OutsourceMaterialReturn order, List<Map<String, Object>> itemsRaw) {
        if (itemsRaw == null || itemsRaw.isEmpty()) throw new BusinessException("请添加退货物料");

        order.setCode(generateCode());
        if (order.getReturnDate() == null) order.setReturnDate(LocalDate.now());
        order.setStatus(DocStatus.DRAFT.getCode());
        if (order.getReturnType() == null) order.setReturnType(MaterialReturnType.MATERIAL.getCode());
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) order.setCompanyId(cid);
        returnMapper.insert(order);
        saveItems(order.getId(), itemsRaw);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, OutsourceMaterialReturn order, List<Map<String, Object>> itemsRaw) {
        OutsourceMaterialReturn old = returnMapper.selectById(id);
        if (old == null) throw new BusinessException("退货单不存在");
        if (!DocStatus.DRAFT.getCode().equals(old.getStatus())) throw new BusinessException("只有草稿状态可编辑");

        order.setId(id);
        order.setCode(null); // 单号不可改
        order.setStatus(null);
        returnMapper.updateById(order);

        itemMapper.delete(new LambdaQueryWrapper<OutsourceMaterialReturnItem>().eq(OutsourceMaterialReturnItem::getReturnOrderId, id));
        saveItems(id, itemsRaw);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        OutsourceMaterialReturn order = returnMapper.selectById(id);
        if (order == null) throw new BusinessException("退货单不存在");
        // P2-29：原子抢占 DRAFT→AUDITED，避免并发/双击重复出库与重复生成应付
        if (!DocStatusGuard.claim(returnMapper, OutsourceMaterialReturn::getId, id,
                OutsourceMaterialReturn::getStatus, DocStatus.DRAFT.getCode(), DocStatus.AUDITED.getCode())) {
            throw new BusinessException("只有草稿状态可审核");
        }
        if (order.getSupplierId() == null) throw new BusinessException("请选择退回对象供应商");
        if (order.getFromWarehouseId() == null) throw new BusinessException("请选择出库源仓");

        List<OutsourceMaterialReturnItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<OutsourceMaterialReturnItem>().eq(OutsourceMaterialReturnItem::getReturnOrderId, id));
        if (items.isEmpty()) throw new BusinessException("退货单明细不能为空");
        // P2-33：数量必须为正（负数量会反向操作库存与应付）
        for (OutsourceMaterialReturnItem it : items) {
            if (it.getQuantity() == null || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0)
                throw new BusinessException("退货数量必须大于 0（明细行ID=" + it.getId() + "）");
        }

        // 1. 物料出源仓（默认扣良品 GOOD 库存）—— 统一走 WarehouseStockService（架构债 A1），
        //    严格口径：源仓库存不足直接抛错（与原先的私有实现行为一致）
        for (OutsourceMaterialReturnItem it : items) {
            if (it.getQuantity() == null || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;
            warehouseStockService.changeMaterialStock(order.getFromWarehouseId(), it.getMaterialId(),
                    it.getQuantity().negate(), StockChangeType.MATERIAL_RETURN_OUT.getCode(), order.getCode(),
                    RelatedBillType.OUTSOURCE_MATERIAL_RETURN, null, null, order.getId());
        }

        // 2. 冲减应付（负向应付，允许负应付）
        BigDecimal totalAmount = items.stream()
                .map(it -> it.getAmount() != null ? it.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalAmount.compareTo(BigDecimal.ZERO) > 0) {
            payableHelper.createPayable(order.getSupplierId(), SourceBillType.OUTSOURCE_MATERIAL_RETURN.getCode(),
                    order.getCode(), order.getId(), totalAmount.negate(), order.getReturnDate(),
                    "委外物料退货 - " + order.getCode());
        }

        // 3. 更新状态与审计
        OutsourceMaterialReturn u = new OutsourceMaterialReturn();
        u.setId(id);
        u.setStatus(DocStatus.AUDITED.getCode());
        u.setAuditorId(getCurrentUserId());
        u.setAuditorName(getCurrentUserName());
        u.setAuditTime(LocalDateTime.now());
        returnMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unAudit(Long id) {
        OutsourceMaterialReturn order = returnMapper.selectById(id);
        if (order == null) throw new BusinessException("退货单不存在");
        // P2-29：原子抢占 AUDITED→DRAFT，避免并发反审核重复回补库存与冲销应付
        if (!DocStatusGuard.claim(returnMapper, OutsourceMaterialReturn::getId, id,
                OutsourceMaterialReturn::getStatus, DocStatus.AUDITED.getCode(), DocStatus.DRAFT.getCode())) {
            throw new BusinessException("只有已审核状态可取消审核");
        }

        List<OutsourceMaterialReturnItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<OutsourceMaterialReturnItem>().eq(OutsourceMaterialReturnItem::getReturnOrderId, id));
        // 1. 物料回源仓 —— 同样统一走 WarehouseStockService（架构债 A1）
        for (OutsourceMaterialReturnItem it : items) {
            if (it.getQuantity() == null || it.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;
            warehouseStockService.changeMaterialStock(order.getFromWarehouseId(), it.getMaterialId(),
                    it.getQuantity(), StockChangeType.CANCEL_MATERIAL_RETURN_OUT.getCode(), order.getCode(),
                    RelatedBillType.OUTSOURCE_MATERIAL_RETURN, null, null, order.getId());
        }
        // 2. 冲销应付（内部校验已付款则阻止）
        payableHelper.reversePayable(id, SourceBillType.OUTSOURCE_MATERIAL_RETURN.getCode());
        // 3. 回草稿
        // 审核信息必须用 UpdateWrapper 显式置 null：updateById 忽略 null 字段，反审核后仍显示审核人/时间
        returnMapper.update(null, new LambdaUpdateWrapper<OutsourceMaterialReturn>()
                .eq(OutsourceMaterialReturn::getId, id)
                .set(OutsourceMaterialReturn::getStatus, DocStatus.DRAFT.getCode())
                .set(OutsourceMaterialReturn::getAuditorId, null)
                .set(OutsourceMaterialReturn::getAuditorName, null)
                .set(OutsourceMaterialReturn::getAuditTime, null));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        OutsourceMaterialReturn old = returnMapper.selectById(id);
        if (old == null) throw new BusinessException("退货单不存在");
        if (!DocStatus.DRAFT.getCode().equals(old.getStatus())) throw new BusinessException("只有草稿状态可作废");
        OutsourceMaterialReturn u = new OutsourceMaterialReturn();
        u.setId(id);
        u.setStatus(DocStatus.CANCELLED.getCode());
        returnMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        // 原子删除（O-7）：带状态条件的物理删，只有草稿能删；affected=0 说明已被并发删除/审核或状态已变
        int rows = returnMapper.delete(new LambdaQueryWrapper<OutsourceMaterialReturn>()
                .eq(OutsourceMaterialReturn::getId, id)
                .eq(OutsourceMaterialReturn::getStatus, DocStatus.DRAFT.getCode()));
        if (rows == 0) {
            if (returnMapper.selectById(id) == null) throw new BusinessException("退货单不存在");
            throw new BusinessException("只有草稿状态可删除");
        }
        itemMapper.delete(new LambdaQueryWrapper<OutsourceMaterialReturnItem>().eq(OutsourceMaterialReturnItem::getReturnOrderId, id));
    }

    @Override
    public List<Map<String, Object>> warehouseOptions() {
        List<Warehouse> whs = warehouseMapper.selectList(
                new LambdaQueryWrapper<Warehouse>().eq(Warehouse::getStatus, 1));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Warehouse w : whs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", w.getId());
            m.put("warehouseName", w.getWarehouseName());
            m.put("warehouseCategory", w.getWarehouseCategory());
            m.put("warehouseType", w.getWarehouseType());
            m.put("factoryId", w.getFactoryId());
            result.add(m);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> materialStock(Long warehouseId) {
        List<WarehouseStock> stocks = warehouseStockMapper.selectList(
                new LambdaQueryWrapper<WarehouseStock>()
                        .eq(WarehouseStock::getWarehouseId, warehouseId)
                        .isNotNull(WarehouseStock::getMaterialId));
        Map<Long, BigDecimal> qtyMap = new LinkedHashMap<>();
        for (WarehouseStock s : stocks) {
            if (!QualityType.GOOD.getCode().equals(s.getQualityType())) continue;
            BigDecimal q = s.getQuantity() != null ? s.getQuantity() : BigDecimal.ZERO;
            qtyMap.merge(s.getMaterialId(), q, BigDecimal::add);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> e : qtyMap.entrySet()) {
            if (e.getValue().compareTo(BigDecimal.ZERO) <= 0) continue;
            OutsourceMaterial m = outsourceMaterialMapper.selectById(e.getKey());
            if (m == null) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("materialId", m.getId());
            row.put("materialName", m.getMaterialName());
            row.put("bomTypeId", m.getBomTypeId());
            row.put("bomTypeName", getBomTypeName(m.getBomTypeId()));
            row.put("unit", m.getUnit());
            row.put("quantity", e.getValue());
            result.add(row);
        }
        return result;
    }

    @Override
    public BigDecimal fifoPrice(Long materialId, BigDecimal qty) {
        return calcFifoPrice(materialId, qty);
    }

    // ===== 私有方法 =====


    private void saveItems(Long returnOrderId, List<Map<String, Object>> itemsRaw) {
        if (itemsRaw == null) return;
        Long cid = CompanyContext.get();
        for (Map<String, Object> it : itemsRaw) {
            Long materialId = toLong(it.get("materialId"));
            BigDecimal qty = toBigDecimal(it.get("quantity"));
            BigDecimal price = it.get("unitPrice") != null && toBigDecimal(it.get("unitPrice")).compareTo(BigDecimal.ZERO) > 0
                    ? toBigDecimal(it.get("unitPrice"))
                    : calcFifoPrice(materialId, qty);

            OutsourceMaterialReturnItem item = new OutsourceMaterialReturnItem();
            item.setReturnOrderId(returnOrderId);
            item.setMaterialId(materialId);
            item.setBomTypeId(toLong(it.get("bomTypeId")));
            item.setUnit((String) it.get("unit"));
            item.setQuantity(qty);
            item.setUnitPrice(price);
            item.setAmount(qty.multiply(price));
            item.setRemark((String) it.get("remark"));
            if (cid != null && cid > 0) item.setCompanyId(cid);
            itemMapper.insert(item);
        }
    }

    private BigDecimal calcFifoPrice(Long materialId, BigDecimal requiredQty) {
        if (materialId == null || requiredQty == null || requiredQty.compareTo(BigDecimal.ZERO) <= 0)
            return BigDecimal.ZERO;
        try {
            List<MaterialOrder> orders = materialOrderMapper.selectList(
                    new LambdaQueryWrapper<MaterialOrder>().orderByAsc(MaterialOrder::getDeliveryDate));
            BigDecimal accumulatedAmount = BigDecimal.ZERO, accumulatedQty = BigDecimal.ZERO;
            for (MaterialOrder o : orders) {
                List<MaterialOrderItem> items = materialOrderItemMapper.selectList(
                        new LambdaQueryWrapper<MaterialOrderItem>()
                                .eq(MaterialOrderItem::getOrderId, o.getId())
                                .eq(MaterialOrderItem::getMaterialId, materialId));
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
        return BigDecimal.ZERO;
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

    private String getMaterialName(Long materialId) {
        if (materialId == null) return "";
        OutsourceMaterial m = outsourceMaterialMapper.selectById(materialId);
        return m != null ? m.getMaterialName() : "";
    }

    private String getBomTypeName(Long bomTypeId) {
        if (bomTypeId == null) return "-";
        BomType bt = bomTypeMapper.selectById(bomTypeId);
        return bt != null ? bt.getTypeName() : "-";
    }

    private String generateCode() {
        String prefix = BillPrefix.OUTSOURCE_MATERIAL_RETURN + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        Long seq = returnMapper.selectCount(
                new LambdaQueryWrapper<OutsourceMaterialReturn>().likeRight(OutsourceMaterialReturn::getCode, prefix)) + 1;
        return prefix + String.format("%03d", seq);
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
