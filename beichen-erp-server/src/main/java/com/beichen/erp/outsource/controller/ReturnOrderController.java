package com.beichen.erp.outsource.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.finance.service.PayableHelper;
import com.beichen.erp.inventory.common.RelatedBillType;
import com.beichen.erp.common.DocStatus;
import com.beichen.erp.inventory.common.StockChangeType;
import com.beichen.erp.inventory.service.InventoryWarehouseStockService;
import com.beichen.erp.outsource.common.OutsourceOrderStatus;
import com.beichen.erp.outsource.common.DeliveryStatus;
import com.beichen.erp.outsource.common.QualityType;
import com.beichen.erp.outsource.entity.*;
import com.beichen.erp.outsource.mapper.*;
import com.beichen.erp.supplier.entity.Supplier;
import com.beichen.erp.supplier.mapper.SupplierMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/outsource/return-order")
@RequiredArgsConstructor
public class ReturnOrderController {

    private final ReturnOrderMapper returnOrderMapper;
    private final ReturnOrderItemMapper returnOrderItemMapper;
    private final OutsourceOrderMapper orderMapper;
    private final OutsourceOrderMaterialMapper orderMaterialMapper;
    private final OutsourceOrderProductMapper orderProductMapper;
    private final OutsourceWarehouseMapper warehouseMapper;
    private final OutsourceMaterialMapper outsourceMaterialMapper;
    private final com.beichen.erp.dev.mapper.BomTypeMapper bomTypeMapper;
    private final SupplierMapper supplierMapper;
    private final PayableHelper payableHelper;
    private final InventoryWarehouseStockService inventoryStockService;
    private final MaterialOrderMapper materialOrderMapper;
    private final MaterialOrderItemMapper materialOrderItemMapper;
    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/page")
    public R<Page<Map<String, Object>>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) Long factoryId) {
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
        return R.ok(result);
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        ReturnOrder o = returnOrderMapper.selectById(id);
        if (o == null) return R.ok(null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.getId()); m.put("code", o.getCode()); m.put("factoryId", o.getFactoryId());
        m.put("orderId", o.getOrderId()); m.put("returnDate", o.getReturnDate());
        m.put("status", o.getStatus()); m.put("remark", o.getRemark());
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
        return R.ok(m);
    }

    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public R<Void> create(@RequestBody Map<String, Object> body) {
        ReturnOrder order = parseOrder(body);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> itemsRaw = (List<Map<String, Object>>) body.get("items");
        if (itemsRaw == null || itemsRaw.isEmpty()) throw new BusinessException("请添加退货物料");

        order.setCode(generateCode());
        if (order.getReturnDate() == null) order.setReturnDate(LocalDate.now());
        order.setStatus(DeliveryStatus.CONFIRMED.getCode());
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) order.setCompanyId(cid);
        returnOrderMapper.insert(order);

        // 工厂委外仓（物料退回目标仓）
        Long factoryWhId = null;
        if (order.getFactoryId() != null) {
            List<OutsourceWarehouse> whs = warehouseMapper.selectList(
                new LambdaQueryWrapper<OutsourceWarehouse>().eq(OutsourceWarehouse::getFactoryId, order.getFactoryId()));
            factoryWhId = whs.isEmpty() ? null : whs.get(0).getId();
        }
        // 成品出库仓（从我方仓扣减成品）
        Long invWhId = null;
        Object invWhObj = body.get("warehouseId");
        if (invWhObj != null && !invWhObj.toString().isBlank()) invWhId = Long.valueOf(invWhObj.toString());
        order.setRemark((String) body.get("remark"));
        if (invWhId != null) order.setWarehouseId(invWhId);

        BigDecimal totalReturnAmount = BigDecimal.ZERO;
        for (Map<String, Object> it : itemsRaw) {
            BigDecimal qty = toBigDecimal(it.get("quantity"));
            Long matId = toLong(it.get("materialId"));
            BigDecimal price = calcFifoPrice(matId, qty);

            ReturnOrderItem item = new ReturnOrderItem();
            item.setReturnOrderId(order.getId());
            item.setMaterialId(matId);
            item.setBomTypeId(toLong(it.get("bomTypeId")));
            item.setUnit((String) it.get("unit"));
            item.setQuantity(qty);
            item.setUnitPrice(price);
            item.setAmount(qty.multiply(price));
            item.setRemark((String) it.get("remark"));
            if (cid != null && cid > 0) item.setCompanyId(cid);
            returnOrderItemMapper.insert(item);

            totalReturnAmount = totalReturnAmount.add(item.getAmount());

            // 退回物料入工厂委外仓 + 流水
            if (factoryWhId != null && matId != null) {
                updateOutsourceStock(factoryWhId, matId, qty, QualityType.GOOD.getCode(), "委外退料入", order.getCode());
            }
        }

        // 成品从我方仓减少
        if (invWhId != null) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> products = (List<Map<String, Object>>) body.get("products");
            if (products != null) {
                for (Map<String, Object> p : products) {
                    String pn = (String) p.get("productName");
                    BigDecimal qty = toBigDecimal(p.get("quantity"));
                    if (pn != null && qty.compareTo(BigDecimal.ZERO) > 0) {
                        inventoryStockService.changeStock(invWhId, pn, qty.negate(), StockChangeType.OUTSOURCE_RETURN_OUT, order.getCode(), RelatedBillType.OUTSOURCE_RETURN, null, null, order.getId(), null);
                    }
                }
            }
        }

        // 应付冲减
        if (totalReturnAmount.compareTo(BigDecimal.ZERO) > 0) {
            payableHelper.createPayable(order.getFactoryId(), "委外退料", order.getCode(), order.getId(),
                totalReturnAmount.negate(), order.getReturnDate(), "委外退料 - " + order.getCode());
        }

        return R.ok();
    }

    @PutMapping("/{id}/cancel")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> cancel(@PathVariable Long id) {
        ReturnOrder order = returnOrderMapper.selectById(id);
        if (order == null) throw new BusinessException("退货单不存在");
        if (DocStatus.CANCELLED.name().equals(order.getStatus())) throw new BusinessException("已取消");
        order.setStatus(DocStatus.CANCELLED.name());
        returnOrderMapper.updateById(order);

        // 逆向库存
        Long whId = null;
        if (order.getFactoryId() != null) {
            List<OutsourceWarehouse> whs = warehouseMapper.selectList(
                new LambdaQueryWrapper<OutsourceWarehouse>().eq(OutsourceWarehouse::getFactoryId, order.getFactoryId()));
            whId = whs.isEmpty() ? null : whs.get(0).getId();
        }
        List<ReturnOrderItem> items = returnOrderItemMapper.selectList(
            new LambdaQueryWrapper<ReturnOrderItem>().eq(ReturnOrderItem::getReturnOrderId, id));
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (ReturnOrderItem it : items) {
            if (whId != null && it.getQuantity() != null && it.getMaterialId() != null) {
                updateOutsourceStock(whId, it.getMaterialId(), it.getQuantity().negate(), QualityType.GOOD.getCode(), "取消退料", order.getCode());
            }
            if (it.getAmount() != null) totalAmount = totalAmount.add(it.getAmount());
        }

        // 应付冲销
        if (totalAmount.compareTo(BigDecimal.ZERO) > 0) {
            payableHelper.createPayable(order.getFactoryId(), "取消退料", order.getCode(), order.getId(),
                totalAmount, LocalDate.now(), "取消退料 - " + order.getCode());
        }

        return R.ok();
    }

    /** FIFO 物料单价 */
    @GetMapping("/fifo-price")
    public R<BigDecimal> fifoPrice(@RequestParam Long materialId, @RequestParam(defaultValue = "1") BigDecimal qty) {
        return R.ok(calcFifoPrice(materialId, qty));
    }

    /** 获取某工厂的产品列表（含每个产品的BOM版本来源），用于退货选择 */
    @GetMapping("/order-products")
    public R<List<Map<String, Object>>> orderProducts(@RequestParam Long factoryId) {
        // 查该工厂所有已确认/已结单的加工单
        List<OutsourceOrder> orders = orderMapper.selectList(
            new LambdaQueryWrapper<OutsourceOrder>().eq(OutsourceOrder::getFactoryId, factoryId)
                .in(OutsourceOrder::getStatus, OutsourceOrderStatus.PRODUCING.name(), OutsourceOrderStatus.FINISHED.name())
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
                v.put("productId", p.getId());
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
        return R.ok(result);
    }

    /** 获取某产品在某加工单中的BOM快照物料 */
    @GetMapping("/bom-snapshot")
    public R<List<Map<String, Object>>> bomSnapshot(@RequestParam Long orderId, @RequestParam Long productId) {
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
                x.put("bomTypeId", mat.getBomTypeId());
                x.put("bomTypeName", getBomTypeNameById(mat.getBomTypeId()));
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
            m.put("perSetQuantity", total.divide(productQty, 10, java.math.RoundingMode.HALF_UP));
        }
        return R.ok(new ArrayList<>(map.values()));
    }

    private void updateOutsourceStock(Long warehouseId, Long materialId, BigDecimal delta, String qualityType, String changeType, String orderCode) {
        if (materialId == null) materialId = -1L; // fallback

        // 查找现有库存记录（按 warehouse + material_id + quality）
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM outsource_warehouse_stock WHERE warehouse_id=? AND outsource_material_id=? AND quality_type=?",
            Integer.class, warehouseId, materialId, qualityType);
        if (count != null && count > 0) {
            jdbcTemplate.update("UPDATE outsource_warehouse_stock SET quantity=quantity+? WHERE warehouse_id=? AND outsource_material_id=? AND quality_type=?",
                delta, warehouseId, materialId, qualityType);
        } else {
            jdbcTemplate.update("INSERT INTO outsource_warehouse_stock (warehouse_id, outsource_material_id, material_name, quality_type, quantity, company_id) VALUES (?,?,?,?,?,?)",
                warehouseId, materialId, getMaterialNameById(materialId), qualityType, delta, CompanyContext.get());
        }

        // 写库存流水
        jdbcTemplate.update("INSERT INTO outsource_stock_log (warehouse_id, outsource_material_id, material_name, change_type, change_quantity, related_order_code, company_id) VALUES (?,?,?,?,?,?,?)",
            warehouseId, materialId, getMaterialNameById(materialId), changeType, delta, orderCode, CompanyContext.get());
    }

    private ReturnOrder parseOrder(Map<String, Object> body) {
        ReturnOrder o = new ReturnOrder();
        Object fid = body.get("factoryId");
        if (fid != null && !fid.toString().isBlank()) o.setFactoryId(Long.valueOf(fid.toString()));
        Object oid = body.get("orderId");
        if (oid != null && !oid.toString().isBlank()) o.setOrderId(Long.valueOf(oid.toString()));
        Object dd = body.get("returnDate");
        if (dd != null && !dd.toString().isBlank()) o.setReturnDate(LocalDate.parse(dd.toString()));
        if (body.get("remark") != null) o.setRemark(body.get("remark").toString());
        return o;
    }

    private BigDecimal calcFifoPrice(Long materialId, BigDecimal requiredQty) {
        if (materialId == null || requiredQty == null || requiredQty.compareTo(BigDecimal.ZERO) <= 0)
            return BigDecimal.ZERO;
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
                return accumulatedAmount.divide(accumulatedQty, 4, java.math.RoundingMode.HALF_UP);
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

    /** 根据委外物料ID查询名称，用于展示回填（ID关联查询替代冗余name字段） */
    private String getMaterialNameById(Long materialId) {
        if (materialId == null) return "";
        OutsourceMaterial m = outsourceMaterialMapper.selectById(materialId);
        return m != null ? m.getMaterialName() : "";
    }

    /** 根据 BOM 类型ID 查询类型名称，空安全返回 "-" */
    private String getBomTypeNameById(Long bomTypeId) {
        if (bomTypeId == null) return "-";
        com.beichen.erp.dev.entity.BomType bt = bomTypeMapper.selectById(bomTypeId);
        return bt != null ? bt.getTypeName() : "-";
    }

    private String generateCode() {
        String prefix = "OR-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        Long seq = returnOrderMapper.selectCount(
            new LambdaQueryWrapper<ReturnOrder>().likeRight(ReturnOrder::getCode, prefix)) + 1;
        return prefix + String.format("%03d", seq);
    }
}
