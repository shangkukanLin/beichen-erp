package com.beichen.erp.supplier.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beichen.erp.common.BillPrefix;
import com.beichen.erp.common.R;
import com.beichen.erp.dev.entity.BomType;
import com.beichen.erp.dev.mapper.BomTypeMapper;
import com.beichen.erp.outsource.mapper.OutsourceMaterialMapper;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.finance.common.SettlementStatus;
import com.beichen.erp.finance.entity.FinancePayable;
import com.beichen.erp.finance.mapper.FinancePayableMapper;
import com.beichen.erp.inventory.common.RelatedBillType;
import com.beichen.erp.inventory.common.StockChangeType;
import com.beichen.erp.warehouse.service.WarehouseStockService;
import com.beichen.erp.outsource.common.DeliveryStatus;
import com.beichen.erp.outsource.common.DeliveryType;
import com.beichen.erp.outsource.common.MaterialOrderStatus;
import com.beichen.erp.outsource.common.OutsourceOrderStatus;
import com.beichen.erp.outsource.entity.*;
import com.beichen.erp.outsource.mapper.*;
import com.beichen.erp.warehouse.entity.Warehouse;
import com.beichen.erp.warehouse.entity.WarehouseStock;
import com.beichen.erp.warehouse.entity.WarehouseStockLog;
import com.beichen.erp.warehouse.mapper.WarehouseMapper;
import com.beichen.erp.warehouse.mapper.WarehouseStockMapper;
import com.beichen.erp.warehouse.mapper.WarehouseStockLogMapper;
import com.beichen.erp.supplier.entity.Supplier;
import com.beichen.erp.supplier.entity.dto.ReturnMaterialDTO;
import com.beichen.erp.supplier.mapper.SupplierMapper;
import com.beichen.erp.supplier.service.SupplierSettlementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 供应商清算业务实现（汇总应付/订单/物料，一键退料，清算停用）
 * 业务由原 SupplierSettlementController 下沉，保留原接口返回结构
 */
@Slf4j
@Service
public class SupplierSettlementServiceImpl implements SupplierSettlementService {

    @Resource
    private SupplierMapper supplierMapper;
    @Resource
    private FinancePayableMapper payableMapper;
    @Resource
    private OutsourceOrderMapper orderMapper;
    @Resource
    private MaterialOrderMapper materialOrderMapper;
    @Resource
    private WarehouseMapper warehouseMapper;
    @Resource
    private WarehouseStockMapper warehouseStockMapper;
    @Resource
    private OutsourceMaterialMapper materialMapper;
    @Resource
    private OutsourceDeliveryMapper deliveryMapper;
    @Resource
    private OutsourceDeliveryItemMapper deliveryItemMapper;
    @Resource
    private WarehouseStockLogMapper stockLogMapper;
    @Resource
    private WarehouseStockService warehouseStockService;
    @Resource
    private BomTypeMapper bomTypeMapper;

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> summary(Long supplierId) {
        return buildSummary(supplierId);
    }

    /** 内部汇总（finish 复用，与原 controller 返回结构保持一致） */
    private Map<String, Object> buildSummary(Long supplierId) {
        Supplier s = supplierMapper.selectById(supplierId);
        if (s == null) throw new BusinessException("供应商不存在");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("supplier", s);

        // 1. 未结清应付
        List<FinancePayable> payables = payableMapper.selectList(new LambdaQueryWrapper<FinancePayable>()
                .eq(FinancePayable::getSupplierId, supplierId)
                .ne(FinancePayable::getStatus, SettlementStatus.SETTLED.getCode())
                .orderByAsc(FinancePayable::getDueDate));
        BigDecimal unpaidTotal = payables.stream()
                .map(p -> p.getUnpaidAmount() != null ? p.getUnpaidAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        result.put("payables", payables);
        result.put("unpaidTotal", unpaidTotal);

        // 2. 进行中订单
        List<OutsourceOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<OutsourceOrder>()
                .eq(OutsourceOrder::getFactoryId, supplierId)
                .in(OutsourceOrder::getStatus, OutsourceOrderStatus.PENDING.getCode(), OutsourceOrderStatus.PRODUCING.getCode())
                .orderByDesc(OutsourceOrder::getId));
        result.put("activeOrders", orders);

        List<MaterialOrder> materialOrders = materialOrderMapper.selectList(new LambdaQueryWrapper<MaterialOrder>()
                .eq(MaterialOrder::getSupplierId, supplierId)
                .in(MaterialOrder::getStatus, MaterialOrderStatus.PENDING.getCode(), MaterialOrderStatus.RECEIVING.getCode())
                .orderByDesc(MaterialOrder::getId));
        result.put("activeMaterialOrders", materialOrders);

        // 3. 委外仓库存（批量预取物料与BOM类型，避免 N+1）
        List<Warehouse> warehouses = warehouseMapper.selectList(
                new LambdaQueryWrapper<Warehouse>().eq(Warehouse::getFactoryId, supplierId));
        Set<Long> matIds = new HashSet<>();
        List<WarehouseStock> allStocks = new ArrayList<>();
        for (Warehouse wh : warehouses) {
            List<WarehouseStock> list = warehouseStockMapper.selectList(
                    new LambdaQueryWrapper<WarehouseStock>()
                            .eq(WarehouseStock::getWarehouseId, wh.getId())
                            .ne(WarehouseStock::getQuantity, BigDecimal.ZERO));
            for (WarehouseStock st : list) {
                allStocks.add(st);
                if (st.getMaterialId() != null) matIds.add(st.getMaterialId());
            }
        }
        Map<Long, OutsourceMaterial> matMap = matIds.isEmpty() ? Collections.emptyMap()
                : materialMapper.selectBatchIds(new ArrayList<>(matIds)).stream()
                .collect(Collectors.toMap(OutsourceMaterial::getId, m -> m, (a, b) -> a));
        Set<Long> bomIds = matMap.values().stream().map(OutsourceMaterial::getBomTypeId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> bomNameMap = bomIds.isEmpty() ? Collections.emptyMap()
                : bomTypeMapper.selectBatchIds(new ArrayList<>(bomIds)).stream()
                .collect(Collectors.toMap(BomType::getId, BomType::getTypeName, (a, b) -> a));

        List<Map<String, Object>> stocks = new ArrayList<>();
        for (Warehouse wh : warehouses) {
            for (WarehouseStock st : allStocks) {
                if (!st.getWarehouseId().equals(wh.getId())) continue;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("warehouseId", wh.getId());
                m.put("warehouseName", wh.getWarehouseName());
                m.put("materialId", st.getMaterialId());
                OutsourceMaterial mat = st.getMaterialId() != null ? matMap.get(st.getMaterialId()) : null;
                m.put("materialName", mat != null ? mat.getMaterialName() : "未知物料");
                m.put("bomTypeName", mat != null && mat.getBomTypeId() != null
                        ? bomNameMap.getOrDefault(mat.getBomTypeId(), "-") : "-");
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
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void returnMaterials(Long supplierId, ReturnMaterialDTO dto) {
        Supplier s = supplierMapper.selectById(supplierId);
        if (s == null) throw new BusinessException("供应商不存在");
        if (dto.getToWarehouseId() == null) throw new BusinessException("请选择退回目标仓（我方仓库）");

        List<Warehouse> warehouses = warehouseMapper.selectList(
                new LambdaQueryWrapper<Warehouse>().eq(Warehouse::getFactoryId, supplierId));
        String code = generateDeliveryCode();
        int count = 0;

        for (Warehouse wh : warehouses) {
            List<WarehouseStock> list = warehouseStockMapper.selectList(
                    new LambdaQueryWrapper<WarehouseStock>()
                            .eq(WarehouseStock::getWarehouseId, wh.getId())
                            .gt(WarehouseStock::getQuantity, BigDecimal.ZERO));
            if (list.isEmpty()) continue;

            OutsourceDelivery delivery = new OutsourceDelivery();
            delivery.setDeliveryType(DeliveryType.RETURN.getCode());
            delivery.setFactoryId(supplierId);
            delivery.setFromWarehouseId(wh.getId());
            delivery.setDeliveryDate(LocalDate.now());
            delivery.setStatus(DeliveryStatus.CONFIRMED.getCode());
            delivery.setRemark("清算退料 - " + s.getName());
            delivery.setCode(count == 0 ? code : generateDeliveryCode());
            deliveryMapper.insert(delivery);
            count++;

            for (WarehouseStock st : list) {
                BigDecimal qty = st.getQuantity();
                OutsourceMaterial mat = st.getMaterialId() != null ? materialMapper.selectById(st.getMaterialId()) : null;
                String matName = mat != null ? mat.getMaterialName() : "未知物料";

                // 委外仓扣减 + 流水
                BigDecimal before = st.getQuantity();
                st.setQuantity(BigDecimal.ZERO);
                warehouseStockMapper.updateById(st);
                WarehouseStockLog slog = new WarehouseStockLog();
                slog.setWarehouseId(wh.getId()); slog.setMaterialId(st.getMaterialId());
                slog.setMaterialName(matName); slog.setChangeType(StockChangeType.SETTLEMENT_RETURN_OUT.getCode());
                slog.setChangeQuantity(qty.negate()); slog.setBeforeQuantity(before);
                slog.setAfterQuantity(BigDecimal.ZERO); slog.setRelatedOrderCode(delivery.getCode());
                stockLogMapper.insert(slog);

                // 收发单明细
                OutsourceDeliveryItem di = new OutsourceDeliveryItem();
                di.setDeliveryId(delivery.getId());
                di.setMaterialId(st.getMaterialId());
                di.setBomTypeId(mat != null ? mat.getBomTypeId() : null);
                di.setUnit(mat != null ? mat.getUnit() : null);
                di.setQuantity(qty);
                di.setQualityType(st.getQualityType());
                deliveryItemMapper.insert(di);

                // 入我方 inventory 仓
                warehouseStockService.changeStock(dto.getToWarehouseId(), matName, qty,
                        StockChangeType.SETTLEMENT_RETURN_IN, delivery.getCode(), RelatedBillType.SUPPLIER_SETTLEMENT, st.getMaterialId(), null, delivery.getId(), null);
            }
        }
        if (count == 0) throw new BusinessException("该供应商委外仓无可退物料");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void finish(Long supplierId) {
        // 行锁防 TOCTOU：校验与停用必须在同一事务同一把锁内完成
        Supplier locked = supplierMapper.selectForUpdate(supplierId);
        if (locked == null) throw new BusinessException("供应商不存在");
        Map<String, Object> data = buildSummary(supplierId);
        if (!Boolean.TRUE.equals(data.get("canSettle"))) {
            BigDecimal unpaid = (BigDecimal) data.get("unpaidTotal");
            @SuppressWarnings("unchecked") List<Object> orders = (List<Object>) data.get("activeOrders");
            @SuppressWarnings("unchecked") List<Object> mOrders = (List<Object>) data.get("activeMaterialOrders");
            @SuppressWarnings("unchecked") List<Object> stocks = (List<Object>) data.get("stocks");
            throw new BusinessException(String.format("不满足清算条件：未付¥%s，进行中加工单%d，进行中物料单%d，库存物料%d项",
                    unpaid.toPlainString(), orders.size(), mOrders.size(), stocks.size()));
        }
        locked.setStatus(0);
        supplierMapper.updateById(locked);
    }

    private String generateDeliveryCode() {
        String ds = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        OutsourceDelivery last = deliveryMapper.selectOne(new LambdaQueryWrapper<OutsourceDelivery>()
                .likeRight(OutsourceDelivery::getCode, BillPrefix.OUTSOURCE_DELIVERY + ds).orderByDesc(OutsourceDelivery::getCode).last("LIMIT 1"));
        int seq = 1;
        if (last != null && last.getCode() != null) {
            try { seq = Integer.parseInt(last.getCode().substring(last.getCode().length() - 3)) + 1; } catch (Exception ignored) {}
        }
        return BillPrefix.OUTSOURCE_DELIVERY + ds + String.format("%03d", seq);
    }
}
