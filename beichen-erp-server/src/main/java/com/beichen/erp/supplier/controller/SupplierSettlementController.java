package com.beichen.erp.supplier.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beichen.erp.common.R;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.finance.entity.FinancePayable;
import com.beichen.erp.finance.mapper.FinancePayableMapper;
import com.beichen.erp.inventory.service.InventoryWarehouseStockService;
import com.beichen.erp.outsource.entity.*;
import com.beichen.erp.outsource.mapper.*;
import com.beichen.erp.supplier.entity.Supplier;
import com.beichen.erp.supplier.mapper.SupplierMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** 供应商清算：汇总应付/订单/物料，一键退料，清算停用 */
@RestController
@RequestMapping("/api/supplier-settlement")
@RequiredArgsConstructor
public class SupplierSettlementController {

    private final SupplierMapper supplierMapper;
    private final FinancePayableMapper payableMapper;
    private final OutsourceOrderMapper orderMapper;
    private final MaterialOrderMapper materialOrderMapper;
    private final OutsourceWarehouseMapper warehouseMapper;
    private final OutsourceWarehouseStockMapper warehouseStockMapper;
    private final OutsourceMaterialMapper materialMapper;
    private final OutsourceDeliveryMapper deliveryMapper;
    private final OutsourceDeliveryItemMapper deliveryItemMapper;
    private final OutsourceStockLogMapper stockLogMapper;
    private final InventoryWarehouseStockService inventoryStockService;

    /** 清算汇总 */
    @GetMapping("/{supplierId}")
    public R<Map<String, Object>> summary(@PathVariable Long supplierId) {
        Supplier s = supplierMapper.selectById(supplierId);
        if (s == null) throw new BusinessException("供应商不存在");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("supplier", s);

        // 1. 未结清应付
        List<FinancePayable> payables = payableMapper.selectList(new LambdaQueryWrapper<FinancePayable>()
            .eq(FinancePayable::getSupplierId, supplierId)
            .ne(FinancePayable::getStatus, "已结清")
            .orderByAsc(FinancePayable::getDueDate));
        BigDecimal unpaidTotal = payables.stream()
            .map(p -> p.getUnpaidAmount() != null ? p.getUnpaidAmount() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        result.put("payables", payables);
        result.put("unpaidTotal", unpaidTotal);

        // 2. 进行中订单
        List<OutsourceOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<OutsourceOrder>()
            .eq(OutsourceOrder::getFactoryId, supplierId)
            .in(OutsourceOrder::getStatus, "待确认", "生产中")
            .orderByDesc(OutsourceOrder::getId));
        result.put("activeOrders", orders);

        List<MaterialOrder> materialOrders = materialOrderMapper.selectList(new LambdaQueryWrapper<MaterialOrder>()
            .eq(MaterialOrder::getSupplierId, supplierId)
            .in(MaterialOrder::getStatus, "待确认", "已确认", "收货中")
            .orderByDesc(MaterialOrder::getId));
        result.put("activeMaterialOrders", materialOrders);

        // 3. 委外仓库存
        List<OutsourceWarehouse> warehouses = warehouseMapper.selectList(
            new LambdaQueryWrapper<OutsourceWarehouse>().eq(OutsourceWarehouse::getFactoryId, supplierId));
        List<Map<String, Object>> stocks = new ArrayList<>();
        for (OutsourceWarehouse wh : warehouses) {
            List<OutsourceWarehouseStock> list = warehouseStockMapper.selectList(
                new LambdaQueryWrapper<OutsourceWarehouseStock>()
                    .eq(OutsourceWarehouseStock::getWarehouseId, wh.getId())
                    .ne(OutsourceWarehouseStock::getQuantity, BigDecimal.ZERO));
            for (OutsourceWarehouseStock st : list) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("warehouseId", wh.getId());
                m.put("warehouseName", wh.getWarehouseName());
                m.put("materialId", st.getMaterialId());
                OutsourceMaterial mat = st.getMaterialId() != null ? materialMapper.selectById(st.getMaterialId()) : null;
                m.put("materialName", mat != null ? mat.getMaterialName() : "未知物料");
                m.put("materialType", mat != null ? mat.getMaterialType() : "");
                m.put("unit", mat != null ? mat.getUnit() : "");
                m.put("qualityType", st.getQualityType());
                m.put("quantity", st.getQuantity());
                stocks.add(m);
            }
        }
        result.put("stocks", stocks);

        // 4. 是否可清算
        boolean canSettle = unpaidTotal.compareTo(BigDecimal.ZERO) == 0
            && orders.isEmpty() && materialOrders.isEmpty() && stocks.isEmpty();
        result.put("canSettle", canSettle);
        return R.ok(result);
    }

    /** 一键退料：该供应商所有委外仓的正库存物料全部退回我方仓 */
    @PostMapping("/{supplierId}/return-materials")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> returnMaterials(@PathVariable Long supplierId, @RequestBody Map<String, Object> body) {
        Supplier s = supplierMapper.selectById(supplierId);
        if (s == null) throw new BusinessException("供应商不存在");
        if (body.get("toWarehouseId") == null) throw new BusinessException("请选择退回目标仓（我方仓库）");
        Long toWarehouseId = Long.valueOf(body.get("toWarehouseId").toString());

        List<OutsourceWarehouse> warehouses = warehouseMapper.selectList(
            new LambdaQueryWrapper<OutsourceWarehouse>().eq(OutsourceWarehouse::getFactoryId, supplierId));
        String code = generateDeliveryCode();
        int count = 0;

        for (OutsourceWarehouse wh : warehouses) {
            List<OutsourceWarehouseStock> list = warehouseStockMapper.selectList(
                new LambdaQueryWrapper<OutsourceWarehouseStock>()
                    .eq(OutsourceWarehouseStock::getWarehouseId, wh.getId())
                    .gt(OutsourceWarehouseStock::getQuantity, BigDecimal.ZERO));
            if (list.isEmpty()) continue;

            OutsourceDelivery delivery = new OutsourceDelivery();
            delivery.setDeliveryType("退料");
            delivery.setFactoryId(supplierId);
            delivery.setFromWarehouseId(wh.getId());
            delivery.setDeliveryDate(LocalDate.now());
            delivery.setStatus("已确认");
            delivery.setRemark("清算退料 - " + s.getName());
            delivery.setCode(count == 0 ? code : generateDeliveryCode());
            deliveryMapper.insert(delivery);
            count++;

            for (OutsourceWarehouseStock st : list) {
                BigDecimal qty = st.getQuantity();
                OutsourceMaterial mat = st.getMaterialId() != null ? materialMapper.selectById(st.getMaterialId()) : null;
                String matName = mat != null ? mat.getMaterialName() : "未知物料";

                // 委外仓扣减 + 流水
                BigDecimal before = st.getQuantity();
                st.setQuantity(BigDecimal.ZERO);
                warehouseStockMapper.updateById(st);
                OutsourceStockLog slog = new OutsourceStockLog();
                slog.setWarehouseId(wh.getId()); slog.setMaterialId(st.getMaterialId());
                slog.setMaterialName(matName); slog.setChangeType("清算退料出");
                slog.setChangeQuantity(qty.negate()); slog.setBeforeQuantity(before);
                slog.setAfterQuantity(BigDecimal.ZERO); slog.setRelatedOrderCode(delivery.getCode());
                stockLogMapper.insert(slog);

                // 收发单明细
                OutsourceDeliveryItem di = new OutsourceDeliveryItem();
                di.setDeliveryId(delivery.getId());
                di.setMaterialId(st.getMaterialId());
                di.setMaterialName(matName);
                di.setMaterialType(mat != null ? mat.getMaterialType() : null);
                di.setUnit(mat != null ? mat.getUnit() : null);
                di.setQuantity(qty);
                di.setQualityType(st.getQualityType());
                deliveryItemMapper.insert(di);

                // 入我方 inventory 仓
                inventoryStockService.changeStock(toWarehouseId, matName, qty,
                    "清算退料入", delivery.getCode(), "供应商清算", st.getMaterialId(), null);
            }
        }
        if (count == 0) throw new BusinessException("该供应商委外仓无可退物料");
        return R.ok();
    }

    /** 清算完成：校验三项清零 → 停用供应商 */
    @PostMapping("/{supplierId}/finish")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> finish(@PathVariable Long supplierId) {
        R<Map<String, Object>> check = summary(supplierId);
        Map<String, Object> data = check.getData();
        if (!Boolean.TRUE.equals(data.get("canSettle"))) {
            BigDecimal unpaid = (BigDecimal) data.get("unpaidTotal");
            @SuppressWarnings("unchecked") List<Object> orders = (List<Object>) data.get("activeOrders");
            @SuppressWarnings("unchecked") List<Object> mOrders = (List<Object>) data.get("activeMaterialOrders");
            @SuppressWarnings("unchecked") List<Object> stocks = (List<Object>) data.get("stocks");
            throw new BusinessException(String.format("不满足清算条件：未付¥%s，进行中加工单%d，进行中物料单%d，库存物料%d项",
                unpaid.toPlainString(), orders.size(), mOrders.size(), stocks.size()));
        }
        Supplier u = new Supplier();
        u.setId(supplierId);
        u.setStatus(0);
        supplierMapper.updateById(u);
        return R.ok();
    }

    private String generateDeliveryCode() {
        String ds = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        OutsourceDelivery last = deliveryMapper.selectOne(new LambdaQueryWrapper<OutsourceDelivery>()
            .likeRight(OutsourceDelivery::getCode, "DEL-" + ds).orderByDesc(OutsourceDelivery::getCode).last("LIMIT 1"));
        int seq = 1;
        if (last != null && last.getCode() != null) {
            try { seq = Integer.parseInt(last.getCode().substring(last.getCode().length() - 3)) + 1; } catch (Exception ignored) {}
        }
        return "DEL-" + ds + String.format("%03d", seq);
    }
}
