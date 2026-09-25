package com.beichen.erp.outsource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.BillNoSeq;
import com.beichen.erp.common.BillPrefix;
import com.beichen.erp.common.DocStatus;
import com.beichen.erp.common.DocStatusGuard;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.finance.common.SettlementStatus;
import com.beichen.erp.finance.common.SourceBillType;
import com.beichen.erp.finance.common.SubjectType;
import com.beichen.erp.finance.entity.FinanceReceivable;
import com.beichen.erp.finance.mapper.FinanceReceivableMapper;
import com.beichen.erp.finance.service.ReceivableHelper;
import com.beichen.erp.inventory.common.RelatedBillType;
import com.beichen.erp.inventory.common.StockChangeType;
import com.beichen.erp.material.common.ProductQualityType;
import com.beichen.erp.material.entity.Product;
import com.beichen.erp.material.mapper.ProductMapper;
import com.beichen.erp.outsource.entity.OutsourceMaterial;
import com.beichen.erp.outsource.entity.OutsourceReturnBack;
import com.beichen.erp.outsource.entity.OutsourceReturnBackItem;
import com.beichen.erp.outsource.mapper.OutsourceMaterialMapper;
import com.beichen.erp.outsource.mapper.OutsourceReturnBackItemMapper;
import com.beichen.erp.outsource.mapper.OutsourceReturnBackMapper;
import com.beichen.erp.outsource.service.OutsourceMaterialPricingService;
import com.beichen.erp.outsource.service.OutsourceReturnBackService;
import com.beichen.erp.supplier.entity.Supplier;
import com.beichen.erp.supplier.entity.SupplierTypeRef;
import com.beichen.erp.supplier.mapper.SupplierMapper;
import com.beichen.erp.supplier.common.SupplierTypeEnum;
import com.beichen.erp.supplier.mapper.SupplierTypeRefMapper;
import com.beichen.erp.warehouse.common.WarehouseCategory;
import com.beichen.erp.warehouse.entity.Warehouse;
import com.beichen.erp.warehouse.entity.WarehouseStock;
import com.beichen.erp.warehouse.mapper.WarehouseMapper;
import com.beichen.erp.warehouse.service.CostService;
import com.beichen.erp.warehouse.service.WarehouseStockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 加工返回单实现（P1-2，2026-09-25）。
 * <p>
 * 审核 = 三腿对称落账：①核销在厂成品（委外仓 PRODUCT_DEFECT 行 −）
 * ②修好成品回我方仓（+，回仓品质可自选）③实际用料逐行从委外仓扣（可超 BOM、允许扣负——工厂已实际耗用）；
 * 资金 = Σ 行 FIFO 价生成【对加工厂】的应收（subjectType=SUPPLIER，工厂赔料）；
 * 成本 = 物料耗用 FIFO 结转到修好入库的成品（移动加权 applyProduct，反审核 reverseByBill）。
 * 反审核逐腿等量逆向 + 冲应收 + 反结转；作废仅草稿可用。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutsourceReturnBackServiceImpl implements OutsourceReturnBackService {

    private final OutsourceReturnBackMapper backMapper;
    private final OutsourceReturnBackItemMapper itemMapper;
    private final SupplierMapper supplierMapper;
    private final SupplierTypeRefMapper supplierTypeRefMapper;
    private final ProductMapper productMapper;
    private final OutsourceMaterialMapper materialMapper;
    private final WarehouseMapper warehouseMapper;
    /** 委外交货记录 Mapper：统计"无单退货送修量"用于防超核销（与 OutsourceOrderDeliveryServiceImpl 无依赖环） */
    private final com.beichen.erp.outsource.mapper.OutsourceOrderDeliveryMapper deliveryMapper;
    private final WarehouseStockService stockService;
    private final OutsourceMaterialPricingService pricingService;
    private final CostService costService;
    private final FinanceReceivableMapper receivableMapper;
    private final ReceivableHelper receivableHelper;

    // ==================== 查询 ====================

    @Override
    public Page<Map<String, Object>> page(int pageNum, int pageSize, String code, Long factoryId, String status) {
        Page<OutsourceReturnBack> raw = backMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<OutsourceReturnBack>()
                        .eq(code != null && !code.isBlank(), OutsourceReturnBack::getCode, code)
                        .eq(factoryId != null, OutsourceReturnBack::getFactoryId, factoryId)
                        .eq(status != null && !status.isBlank(), OutsourceReturnBack::getStatus, status)
                        .orderByDesc(OutsourceReturnBack::getId));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (OutsourceReturnBack b : raw.getRecords()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", b.getId());
            m.put("code", b.getCode());
            m.put("factoryId", b.getFactoryId());
            m.put("factoryName", b.getFactoryName());
            m.put("productId", b.getProductId());
            m.put("productName", b.getProductName());
            m.put("quantity", b.getQuantity());
            m.put("defectQualityType", b.getDefectQualityType());
            m.put("returnQualityType", b.getReturnQualityType());
            m.put("inWarehouseId", b.getInWarehouseId());
            m.put("inWarehouseName", warehouseNameOf(b.getInWarehouseId()));
            m.put("outsourceWarehouseId", b.getOutsourceWarehouseId());
            m.put("materialAmount", b.getMaterialAmount());
            m.put("returnDate", b.getReturnDate());
            m.put("status", b.getStatus());
            m.put("auditorName", b.getAuditorName());
            m.put("createByName", b.getCreateByName());
            m.put("remark", b.getRemark());
            rows.add(m);
        }
        Page<Map<String, Object>> result = new Page<>(pageNum, pageSize, raw.getTotal());
        result.setRecords(rows);
        return result;
    }

    @Override
    public OutsourceReturnBack getById(Long id) {
        OutsourceReturnBack b = backMapper.selectById(id);
        if (b == null) throw new BusinessException("返回单不存在");
        return b;
    }

    @Override
    public List<OutsourceReturnBackItem> getItems(Long id) {
        return itemMapper.selectList(new LambdaQueryWrapper<OutsourceReturnBackItem>()
                .eq(OutsourceReturnBackItem::getReturnBackId, id)
                .orderByAsc(OutsourceReturnBackItem::getId));
    }

    // ==================== 草稿 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OutsourceReturnBack create(Map<String, Object> body) {
        OutsourceReturnBack b = parseAndValidate(body);
        b.setCode(generateCode());
        b.setStatus(DocStatus.DRAFT.getCode());
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) b.setCompanyId(cid);
        backMapper.insert(b);
        replaceItems(b.getId(), itemsOf(body), cid);
        log.info("加工返回单已保存(草稿): id={}, code={}, factoryId={}, productId={}, qty={}",
                b.getId(), b.getCode(), b.getFactoryId(), b.getProductId(), b.getQuantity());
        return b;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, Map<String, Object> body) {
        OutsourceReturnBack db = backMapper.selectById(id);
        if (db == null) throw new BusinessException("返回单不存在");
        if (!DocStatus.DRAFT.getCode().equals(db.getStatus())) throw new BusinessException("只有草稿状态可修改");
        OutsourceReturnBack b = parseAndValidate(body);
        OutsourceReturnBack u = new OutsourceReturnBack();
        u.setId(id);
        u.setFactoryId(b.getFactoryId());
        u.setFactoryName(b.getFactoryName());
        u.setProductId(b.getProductId());
        u.setProductName(b.getProductName());
        u.setQuantity(b.getQuantity());
        u.setDefectQualityType(b.getDefectQualityType());
        u.setReturnQualityType(b.getReturnQualityType());
        u.setInWarehouseId(b.getInWarehouseId());
        u.setOutsourceWarehouseId(b.getOutsourceWarehouseId());
        u.setReturnDate(b.getReturnDate());
        u.setRemark(b.getRemark());
        backMapper.updateById(u);
        Long cid = CompanyContext.get();
        replaceItems(id, itemsOf(body), cid);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDraft(Long id) {
        OutsourceReturnBack db = backMapper.selectById(id);
        if (db == null) throw new BusinessException("返回单不存在");
        if (!DocStatus.DRAFT.getCode().equals(db.getStatus())) throw new BusinessException("只有草稿状态可删除");
        backMapper.deleteById(id);
        itemMapper.delete(new LambdaQueryWrapper<OutsourceReturnBackItem>()
                .eq(OutsourceReturnBackItem::getReturnBackId, id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        if (!DocStatusGuard.claim(backMapper, OutsourceReturnBack::getId, id,
                OutsourceReturnBack::getStatus, DocStatus.DRAFT.getCode(), DocStatus.CANCELLED.getCode())) {
            throw new BusinessException("只有草稿状态可作废");
        }
    }

    // ==================== 审核 / 反审核 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        // P2-29：原子抢占 DRAFT→AUDITED，避免并发/双击重复落账
        if (!DocStatusGuard.claim(backMapper, OutsourceReturnBack::getId, id,
                OutsourceReturnBack::getStatus, DocStatus.DRAFT.getCode(), DocStatus.AUDITED.getCode())) {
            throw new BusinessException("只有草稿状态可审核");
        }
        OutsourceReturnBack b = backMapper.selectById(id);
        BigDecimal qty = b.getQuantity();
        Long factoryWhId = resolveOutsourceWarehouseId(b.getFactoryId());
        String defectQt = nzQuality(b.getDefectQualityType());
        String returnQt = nzQuality(b.getReturnQualityType());
        List<OutsourceReturnBackItem> items = getItems(id);
        if (items.isEmpty()) throw new BusinessException("用料明细不能为空（至少一行实际用料）");

        // 前置校验：在厂成品行（形态 PRODUCT_DEFECT）数量必须足够（审核间隙可能被其他单据扣走）
        BigDecimal onSite = stockService.getQuantity(factoryWhId, b.getProductId(), defectQt, WarehouseStock.FORM_PRODUCT_DEFECT);
        if (onSite.compareTo(qty) < 0) {
            throw new BusinessException("在厂成品（加工退货）不足：当前 " + onSite + "，需核销 " + qty);
        }

        // ① 核销在厂成品（PRODUCT_DEFECT 行）
        stockService.changeStock(factoryWhId, b.getProductId(), qty.negate(),
                StockChangeType.OUTSOURCE_BACK_CONSUME, b.getCode(), RelatedBillType.OUTSOURCE_RETURN_BACK,
                "", id, defectQt, WarehouseStock.FORM_PRODUCT_DEFECT);

        // ② 修好成品回我方仓（普通成品行）
        stockService.changeStock(b.getInWarehouseId(), b.getProductId(), qty,
                StockChangeType.OUTSOURCE_BACK_IN, b.getCode(), RelatedBillType.OUTSOURCE_RETURN_BACK,
                "", id, returnQt);

        // ③ 实际用料逐行从委外仓扣（允许扣负：工厂已实际耗用，账面可能不足）+ FIFO 计价快照
        BigDecimal materialAmount = BigDecimal.ZERO;
        for (OutsourceReturnBackItem it : items) {
            BigDecimal q = it.getQuantity();
            stockService.changeMaterialStockAllowNegative(factoryWhId, it.getMaterialId(), q.negate(),
                    StockChangeType.OUTSOURCE_BACK_MATERIAL.getCode(), b.getCode(), RelatedBillType.OUTSOURCE_RETURN_BACK,
                    null, null, id, WarehouseStock.FORM_MATERIAL);
            BigDecimal unit = pricingService.fifoPriceWithFallback(it.getMaterialId(), q);
            BigDecimal amount = unit.multiply(q).setScale(2, RoundingMode.HALF_UP);
            OutsourceReturnBackItem u = new OutsourceReturnBackItem();
            u.setId(it.getId());
            u.setUnitPrice(unit);
            u.setAmount(amount);
            itemMapper.updateById(u);
            materialAmount = materialAmount.add(amount);
        }

        // ④ 成本结转：物料耗用 FIFO 结转到修好入库的成品（提升移动加权成本；反审核按单冲销批次）
        if (materialAmount.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal unitCost = materialAmount.divide(qty, 4, RoundingMode.HALF_UP);
            costService.applyProduct(b.getProductId(), qty, unitCost,
                    StockChangeType.OUTSOURCE_BACK_IN.getCode(), id, b.getCode());
        }

        // ⑤ 赔料应收（对加工厂，幂等复用同 billNo 行——反审核后重新审核不撞唯一键）
        upsertReceivable(b, materialAmount);

        OutsourceReturnBack u = new OutsourceReturnBack();
        u.setId(id);
        u.setMaterialAmount(materialAmount);
        u.setAuditTime(java.time.LocalDateTime.now());
        backMapper.updateById(u);
        log.info("加工返回单审核 {}：核销在厂 {} / 回仓 {} / 用料 {} 行 / 料款应收 {}",
                b.getCode(), qty, qty, items.size(), materialAmount);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unAudit(Long id) {
        // P2-29：原子抢占 AUDITED→DRAFT，避免并发反审核重复回滚
        if (!DocStatusGuard.claim(backMapper, OutsourceReturnBack::getId, id,
                OutsourceReturnBack::getStatus, DocStatus.AUDITED.getCode(), DocStatus.DRAFT.getCode())) {
            throw new BusinessException("只有已审核状态可以反审核");
        }
        OutsourceReturnBack b = backMapper.selectById(id);
        BigDecimal qty = b.getQuantity();
        Long factoryWhId = resolveOutsourceWarehouseId(b.getFactoryId());
        String defectQt = nzQuality(b.getDefectQualityType());
        String returnQt = nzQuality(b.getReturnQualityType());
        List<OutsourceReturnBackItem> items = getItems(id);

        // ① 恢复在厂成品（同形态 PRODUCT_DEFECT 精确逆回）
        stockService.changeStock(factoryWhId, b.getProductId(), qty,
                StockChangeType.CANCEL_OUTSOURCE_BACK_CONSUME, b.getCode(), RelatedBillType.OUTSOURCE_RETURN_BACK,
                "", id, defectQt, WarehouseStock.FORM_PRODUCT_DEFECT);

        // ② 从我方仓扣回修好入库的成品
        stockService.changeStock(b.getInWarehouseId(), b.getProductId(), qty.negate(),
                StockChangeType.CANCEL_OUTSOURCE_BACK_IN, b.getCode(), RelatedBillType.OUTSOURCE_RETURN_BACK,
                "", id, returnQt);

        // ③ 等量回补委外仓物料
        for (OutsourceReturnBackItem it : items) {
            stockService.changeMaterialStockAllowNegative(factoryWhId, it.getMaterialId(), it.getQuantity(),
                    StockChangeType.CANCEL_OUTSOURCE_BACK_MATERIAL.getCode(), b.getCode(), RelatedBillType.OUTSOURCE_RETURN_BACK,
                    null, null, id, WarehouseStock.FORM_MATERIAL);
        }

        // ④ 反结转成本（CostService 既有约定：reverse 在库存冲回**之后**调用，见 reverseGroup 注释；
        //    SCALE=4 加权舍入 ⇒ 反算存在 ≤0.0005 的固有尾差，与采购/委外交货入库反审核同源，非本单缺陷）
        costService.reverseByBill(StockChangeType.OUTSOURCE_BACK_IN.getCode(), id);

        // ⑤ 冲应收（已有收款会被护栏拦截）
        receivableHelper.reverseReceivable(b.getCode());

        // 清空审核人（2026-09-23 全站口径：反审核清空审计信息）
        backMapper.update(null, new LambdaUpdateWrapper<OutsourceReturnBack>()
                .eq(OutsourceReturnBack::getId, id)
                .set(OutsourceReturnBack::getAuditorId, null)
                .set(OutsourceReturnBack::getAuditorName, null)
                .set(OutsourceReturnBack::getAuditTime, null));
        log.info("加工返回单反审核 {}：三腿对称回滚 + 应收冲销 + 成本反结转", b.getCode());
    }

    // ==================== 内部 ====================

    /** 解析并校验主表入参（创建/修改共用一套校验） */
    private OutsourceReturnBack parseAndValidate(Map<String, Object> body) {
        if (body == null) throw new BusinessException("入参不能为空");
        Long factoryId = longOf(body.get("factoryId"), "请选择加工厂");
        Long productId = longOf(body.get("productId"), "请选择产品");
        Object qtyObj = body.get("quantity");
        if (qtyObj == null || qtyObj.toString().isBlank()) throw new BusinessException("返回数量不能为空");
        BigDecimal qty;
        try {
            qty = new BigDecimal(qtyObj.toString());
        } catch (NumberFormatException e) {
            throw new BusinessException("返回数量格式不正确：" + qtyObj);
        }
        if (qty.compareTo(BigDecimal.ZERO) <= 0) throw new BusinessException("返回数量必须大于0");

        Supplier factory = supplierMapper.selectById(factoryId);
        if (factory == null) throw new BusinessException("加工厂不存在");
        // 与加工退货同口径：对象只能是加工厂/供应商，不能是供货商（成品商）
        boolean targetIsVendor = supplierTypeRefMapper.selectList(
                        new LambdaQueryWrapper<SupplierTypeRef>().eq(SupplierTypeRef::getSupplierId, factoryId))
                .stream().anyMatch(r -> SupplierTypeEnum.PRODUCT.getCode().equals(r.getTypeCode()));
        if (targetIsVendor) throw new BusinessException("「" + factory.getName() + "」是供货商（成品商）：加工返回单只能对应加工厂或供应商");

        Product product = productMapper.selectById(productId);
        if (product == null) throw new BusinessException("产品不存在");
        Long inWarehouseId = longOf(body.get("inWarehouseId"), "请选择回仓仓库");
        Warehouse inWh = warehouseMapper.selectById(inWarehouseId);
        if (inWh == null) throw new BusinessException("回仓仓库不存在");
        if (!WarehouseCategory.INVENTORY.getCode().equals(inWh.getWarehouseCategory())) {
            throw new BusinessException("回仓仓库必须是我方仓库（成品仓）");
        }

        String defectQt = nzQuality(strOrNull(body.get("defectQualityType")));
        String returnQt = nzQuality(strOrNull(body.get("returnQualityType")));

        OutsourceReturnBack b = new OutsourceReturnBack();
        b.setFactoryId(factoryId);
        b.setFactoryName(factory.getName());
        b.setProductId(productId);
        b.setProductName(product.getName());
        b.setQuantity(qty);
        b.setDefectQualityType(defectQt);
        b.setReturnQualityType(returnQt);
        b.setInWarehouseId(inWarehouseId);
        b.setOutsourceWarehouseId(resolveOutsourceWarehouseId(factoryId));
        Object rd = body.get("returnDate");
        b.setReturnDate(rd != null && !rd.toString().isBlank() ? LocalDate.parse(rd.toString()) : LocalDate.now());
        b.setRemark(strOrNull(body.get("remark")));

        // 防超核销：累计核销（已审核返回单）不得超过累计无单退货送修量
        assertNotOverReturned(factoryId, productId, defectQt, qty, null);
        return b;
    }

    /** 防超核销（修改场景排除自身）：Σ已审核返回单 + 本次 ≤ Σ已审核无单退货送修量 */
    private void assertNotOverReturned(Long factoryId, Long productId, String defectQt,
                                       BigDecimal adding, Long excludeId) {
        // 送修量 = 无单加工退货审核量（同厂同产品同规格）
        BigDecimal sent = BigDecimal.ZERO;
        for (com.beichen.erp.outsource.entity.OutsourceOrderDelivery d : deliveryMapper.selectList(
                new LambdaQueryWrapper<com.beichen.erp.outsource.entity.OutsourceOrderDelivery>()
                        .eq(com.beichen.erp.outsource.entity.OutsourceOrderDelivery::getSourceType, "RETURN_DEFECT")
                        .eq(com.beichen.erp.outsource.entity.OutsourceOrderDelivery::getFactoryId, factoryId)
                        .eq(com.beichen.erp.outsource.entity.OutsourceOrderDelivery::getProductMasterId, productId)
                        .eq(com.beichen.erp.outsource.entity.OutsourceOrderDelivery::getQualityType, defectQt)
                        .eq(com.beichen.erp.outsource.entity.OutsourceOrderDelivery::getStatus, DocStatus.AUDITED.getCode()))) {
            sent = sent.add(d.getQuantity() != null ? d.getQuantity().abs() : BigDecimal.ZERO);
        }
        BigDecimal returned = BigDecimal.ZERO;
        for (OutsourceReturnBack r : backMapper.selectList(new LambdaQueryWrapper<OutsourceReturnBack>()
                .eq(OutsourceReturnBack::getFactoryId, factoryId)
                .eq(OutsourceReturnBack::getProductId, productId)
                .eq(OutsourceReturnBack::getDefectQualityType, defectQt)
                .eq(OutsourceReturnBack::getStatus, DocStatus.AUDITED.getCode())
                .ne(excludeId != null, OutsourceReturnBack::getId, excludeId))) {
            returned = returned.add(r.getQuantity() != null ? r.getQuantity() : BigDecimal.ZERO);
        }
        if (returned.add(adding).compareTo(sent) > 0) {
            throw new BusinessException("核销超量：累计送修 " + sent + "，已返回 " + returned + "，本次 " + adding
                    + "（返回量不能超过无单加工退货送修量）");
        }
    }

    /** 整单替换明细（草稿态） */
    private void replaceItems(Long backId, List<OutsourceReturnBackItem> items, Long cid) {
        itemMapper.delete(new LambdaQueryWrapper<OutsourceReturnBackItem>()
                .eq(OutsourceReturnBackItem::getReturnBackId, backId));
        if (items == null || items.isEmpty()) return;
        for (OutsourceReturnBackItem it : items) {
            it.setId(null);
            it.setReturnBackId(backId);
            OutsourceMaterial m = materialMapper.selectById(it.getMaterialId());
            if (m == null) throw new BusinessException("委外物料不存在：ID=" + it.getMaterialId());
            it.setMaterialName(m.getMaterialName());
            it.setUnit(m.getUnit());
            if (cid != null && cid > 0) it.setCompanyId(cid);
            itemMapper.insert(it);
        }
    }

    /** 解析明细行入参（materialId/quantity 必填且 >0；数量允许超 BOM，不做 BOM 比对） */
    @SuppressWarnings("unchecked")
    private List<OutsourceReturnBackItem> itemsOf(Map<String, Object> body) {
        Object arr = body.get("items");
        List<OutsourceReturnBackItem> list = new ArrayList<>();
        if (!(arr instanceof List<?> raw) || raw.isEmpty()) {
            throw new BusinessException("用料明细不能为空（至少一行实际用料）");
        }
        for (Object o : raw) {
            Map<String, Object> row = (Map<String, Object>) o;
            Long materialId = longOf(row.get("materialId"), "用料明细行缺少物料");
            Object qObj = row.get("quantity");
            if (qObj == null || qObj.toString().isBlank()) throw new BusinessException("用料明细行缺少数量");
            BigDecimal q;
            try {
                q = new BigDecimal(qObj.toString());
            } catch (NumberFormatException e) {
                throw new BusinessException("用料明细行数量格式不正确：" + qObj);
            }
            if (q.compareTo(BigDecimal.ZERO) <= 0) throw new BusinessException("用料明细行数量必须大于0");
            OutsourceReturnBackItem it = new OutsourceReturnBackItem();
            it.setMaterialId(materialId);
            it.setQuantity(q);
            list.add(it);
        }
        return list;
    }

    /** 幂等落应收台账（对加工厂，subjectType=SUPPLIER） */
    private void upsertReceivable(OutsourceReturnBack b, BigDecimal amount) {
        FinanceReceivable exist = receivableMapper.selectOne(new LambdaQueryWrapper<FinanceReceivable>()
                .eq(FinanceReceivable::getBillNo, b.getCode()));
        FinanceReceivable fr = exist != null ? exist : new FinanceReceivable();
        fr.setBillNo(b.getCode());
        fr.setSubjectType(SubjectType.SUPPLIER.getCode());
        fr.setSupplierId(b.getFactoryId());
        fr.setSupplierName(b.getFactoryName());
        fr.setSourceBillType(SourceBillType.OUTSOURCE_RETURN_BACK.getCode());
        fr.setSourceBillNo(b.getCode());
        fr.setSourceId(b.getId());
        fr.setAmount(amount);
        fr.setPaidAmount(BigDecimal.ZERO);
        fr.setUnpaidAmount(amount);
        fr.setDueDate(b.getReturnDate());
        fr.setStatus(SettlementStatus.UNSETTLED.getCode());
        if (b.getRemark() != null && !b.getRemark().isBlank()) fr.setRemark("加工返回料款（工厂赔料） " + b.getRemark());
        else fr.setRemark("加工返回料款（工厂赔料）");
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) fr.setCompanyId(cid);
        if (exist != null) receivableMapper.updateById(fr);
        else receivableMapper.insert(fr);
    }

    /** 按工厂解析委外仓（与无单加工退货同口径：无仓显式报错） */
    private Long resolveOutsourceWarehouseId(Long factoryId) {
        List<Warehouse> warehouses = warehouseMapper.selectList(new LambdaQueryWrapper<Warehouse>()
                .eq(Warehouse::getFactoryId, factoryId)
                .eq(Warehouse::getWarehouseCategory, WarehouseCategory.OUTSOURCE.getCode())
                .orderByAsc(Warehouse::getId));
        if (warehouses.isEmpty()) {
            throw new BusinessException("该加工厂未配置委外仓库，请先在【委外仓库】页面为该工厂创建委外仓库");
        }
        return warehouses.get(0).getId();
    }

    /** 单号：ORB-yyyyMMdd###（最大号 +1，BillNoSeq 统一口径） */
    private String generateCode() {
        String prefix = BillPrefix.OUTSOURCE_RETURN_BACK + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        OutsourceReturnBack last = backMapper.selectOne(new LambdaQueryWrapper<OutsourceReturnBack>()
                .likeRight(OutsourceReturnBack::getCode, prefix)
                .orderByDesc(OutsourceReturnBack::getCode).last("LIMIT 1"));
        int seq = last != null ? BillNoSeq.lastSeq(last.getCode(), prefix) + 1 : 1;
        return BillNoSeq.format(prefix, seq);
    }

    private String warehouseNameOf(Long warehouseId) {
        if (warehouseId == null) return "";
        Warehouse w = warehouseMapper.selectById(warehouseId);
        return w != null ? w.getWarehouseName() : "";
    }

    /** 品质兜底：空 → A；仅允许 A/B/C/DEFECT */
    private String nzQuality(String qt) {
        String v = qt == null || qt.isBlank() ? ProductQualityType.A.getCode() : qt;
        for (ProductQualityType t : ProductQualityType.values()) {
            if (t.getCode().equals(v)) return v;
        }
        throw new BusinessException("非法的成品规格：" + v);
    }

    private String strOrNull(Object o) {
        return o != null && !o.toString().isBlank() ? o.toString() : null;
    }

    private Long longOf(Object o, String errMsg) {
        if (o == null || o.toString().isBlank()) throw new BusinessException(errMsg);
        return Long.valueOf(o.toString());
    }
}
