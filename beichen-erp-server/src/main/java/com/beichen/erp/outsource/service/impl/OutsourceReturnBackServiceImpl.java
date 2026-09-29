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
import com.beichen.erp.outsource.entity.BomSnapshotItem;
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
import java.util.HashMap;
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
    /** 审核盖章用（2026-09-28）：取 sys_user.username 写 auditor_id/auditor_name */
    private final com.beichen.erp.auth.mapper.UserMapper userMapper;
    /** 2026-09-27（用户口径）：「实际用料」可选范围收口到来源无单退货单的 BOM 快照 —— 见 materialCandidates */
    private final com.beichen.erp.outsource.mapper.BomSnapshotItemMapper bomSnapshotItemMapper;
    /** 复用"最近一次被加工单用过的快照"解析（与无单退货建单同口径，单一实现） */
    private final com.beichen.erp.outsource.service.OutsourceOrderDeliveryService deliveryService;

    // ==================== 查询 ====================

    @Override
    public Page<Map<String, Object>> page(int pageNum, int pageSize, String code, Long factoryId, String status) {
        LambdaQueryWrapper<OutsourceReturnBack> qw = new LambdaQueryWrapper<OutsourceReturnBack>()
                .eq(code != null && !code.isBlank(), OutsourceReturnBack::getCode, code)
                .eq(factoryId != null, OutsourceReturnBack::getFactoryId, factoryId);
        // 2026-09-27 三级菜单：status 支持逗号分隔多值（「有效单据」= DRAFT,AUDITED；「已作废」= CANCELLED）
        if (status != null && !status.isBlank()) {
            List<String> sts = java.util.Arrays.stream(status.split(",")).map(String::trim)
                    .filter(s -> !s.isEmpty()).collect(java.util.stream.Collectors.toList());
            if (sts.size() == 1) qw.eq(OutsourceReturnBack::getStatus, sts.get(0));
            else if (!sts.isEmpty()) qw.in(OutsourceReturnBack::getStatus, sts);
        }
        qw.orderByDesc(OutsourceReturnBack::getId);
        Page<OutsourceReturnBack> raw = backMapper.selectPage(new Page<>(pageNum, pageSize), qw);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (OutsourceReturnBack b : raw.getRecords()) rows.add(rowOf(b));
        Page<Map<String, Object>> result = new Page<>(pageNum, pageSize, raw.getTotal());
        result.setRecords(rows);
        return result;
    }

    /** 单行展示字段（列表与「来源单详情页的返回记录」共用，避免两处字段漂移） */
    private Map<String, Object> rowOf(OutsourceReturnBack b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", b.getId());
        m.put("code", b.getCode());
        m.put("factoryId", b.getFactoryId());
        m.put("factoryName", b.getFactoryName());
        m.put("productId", b.getProductId());
        m.put("productName", b.getProductName());
        // 2026-09-27：来源无单加工退货单（存量未绑定的返回单为 null ⇒ 前端显示「未绑定」）
        m.put("sourceDeliveryId", b.getSourceDeliveryId());
        m.put("sourceCode", sourceCodeOf(b.getSourceDeliveryId()));
        m.put("quantity", b.getQuantity());
        // 2026-09-28：**返回单自带草稿/审核状态与审核人** ⇒ 详情页的「返回记录」按状态渲染
        // 「审核 / 反审核 / 删除」（原先登记即生效，前端只会渲染"撤销"）
        m.put("status", b.getStatus());
        m.put("auditorName", b.getAuditorName());
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
        return m;
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

    // ==================== 登记 / 撤销（2026-09-27 用户口径：不再单独开「加工返回单」，改在无单退货详情页登记） ====================

    /**
     * 登记返回：`create` + `audit` 合成一个事务（**登记即生效**，与「成品维修退货」的登记维修返回同范式）。
     * <p>来源单由**路径**决定并强制覆盖 body 里的同名字段 ⇒ 不可能把返回登记到别的来源单上。
     * 校验（来源单必须已审核 / 无单 / 同厂同产品同规格 / 按单防超返）与账务全部复用既有实现，一行未改。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OutsourceReturnBack register(Long sourceDeliveryId, Map<String, Object> body) {
        if (sourceDeliveryId == null) throw new BusinessException("缺少来源无单加工退货单");
        Map<String, Object> b = new HashMap<>();
        if (body != null) b.putAll(body);
        b.put("sourceDeliveryId", sourceDeliveryId);
        OutsourceReturnBack rb = create(b);          // 校验：来源单/工厂/产品/规格/防超返 + 用料范围
        // 2026-09-28（用户口径「加工和物料的登记返回都需要审核和反审核功能」）：登记**只建草稿**，
        // 三腿落账（核销在厂 + 修好回仓 + 用料 + 赔料应收 + 成本结转）改在「审核」时执行。
        // 草稿未审核 ⇒ 不计入"已返回量"（聚合只算 AUDITED），也不会挡住来源单的反审核（见 returnedQtyBySource）。
        log.info("加工返回草稿已保存: id={}, code={}, source={}", rb.getId(), rb.getCode(), sourceDeliveryId);
        return getById(rb.getId());
    }

    /**
     * 撤销登记：`unAudit`（三腿对称逆回 + 冲应收 + 成本反结转，成功后状态置回 DRAFT）+ `deleteDraft`（删除记录）。
     * <p>两步同一事务：任一失败整体回滚，不会出现"库存回滚了记录还在"的中间态。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revoke(Long id) {
        OutsourceReturnBack db = getById(id);        // 不存在直接报错（避免静默成功）
        // 2026-09-28（用户口径）：删除只对**草稿**开放；已审核的要先「反审核」（对称逆回 + 留痕）再删。
        if (!DocStatus.DRAFT.getCode().equals(db.getStatus()))
            throw new BusinessException("只有草稿可以删除；已审核的返回请先「反审核」（逆回库存/账务并留痕）再删除");
        deleteDraft(id);
        log.info("加工返回草稿已删除: id={}", id);
    }

    /** 当前登录账户ID（未登录返回 null）—— 审核盖章用（与全站单据同口径） */
    private Long getCurrentUserId() {
        try { return cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong(); } catch (Exception e) { return null; }
    }

    /** 当前登录账户名（查不到返回 null） */
    private String getCurrentUserName() {
        try {
            com.beichen.erp.auth.entity.User u = userMapper.selectById(cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong());
            return u != null ? u.getUsername() : null;
        } catch (Exception e) { return null; }
    }

    @Override
    public List<Map<String, Object>> listBySource(Long sourceDeliveryId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (sourceDeliveryId == null) return rows;
        for (OutsourceReturnBack b : backMapper.selectList(new LambdaQueryWrapper<OutsourceReturnBack>()
                .eq(OutsourceReturnBack::getSourceDeliveryId, sourceDeliveryId)
                .orderByDesc(OutsourceReturnBack::getId))) {
            rows.add(rowOf(b));
        }
        return rows;
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
        // 2026-09-27：可选池为空（来源单没绑 BOM 快照且该产品没有 BOM）⇒ 允许"只登记返回、不填用料"（用户确认：
        // 不卡死流程）；池非空时沿用原口径 —— 必须至少一行实际用料。
        List<Map<String, Object>> pool = candidatesOf(b.getSourceDeliveryId(), b.getFactoryId(), b.getProductId());
        List<OutsourceReturnBackItem> items = itemsOf(body);
        if (items.isEmpty() && !pool.isEmpty()) throw new BusinessException("用料明细不能为空（至少一行实际用料）");
        assertMaterialsInPool(pool, items);
        snapshotDefaultPrices(items);
        replaceItems(b.getId(), items, cid);
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
        u.setSourceDeliveryId(b.getSourceDeliveryId());
        u.setQuantity(b.getQuantity());
        u.setDefectQualityType(b.getDefectQualityType());
        u.setReturnQualityType(b.getReturnQualityType());
        u.setInWarehouseId(b.getInWarehouseId());
        u.setOutsourceWarehouseId(b.getOutsourceWarehouseId());
        u.setReturnDate(b.getReturnDate());
        u.setRemark(b.getRemark());
        backMapper.updateById(u);
        Long cid = CompanyContext.get();
        // 与 create 同口径（校验用**新值** b：来源单/工厂/产品可能都被改过）
        List<Map<String, Object>> pool = candidatesOf(b.getSourceDeliveryId(), b.getFactoryId(), b.getProductId());
        List<OutsourceReturnBackItem> items = itemsOf(body);
        if (items.isEmpty() && !pool.isEmpty()) throw new BusinessException("用料明细不能为空（至少一行实际用料）");
        assertMaterialsInPool(pool, items);
        snapshotDefaultPrices(items);
        replaceItems(id, items, cid);
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
        // 2026-09-27（与 create/update 同口径，修一处**老缺陷**）：可选池为空（来源单没绑 BOM 快照 / 该产品没有 BOM）
        // ⇒ 允许"只登记返回、不填用料"（④ 成本结转与料款会自然按 0 走）；池非空仍必须至少一行实际用料。
        // 原先 audit 无条件要求用料 ⇒ create 放过的空池草稿**永远审核不了**（登记即生效后立刻暴露，实测命中）。
        if (items.isEmpty() && !candidatesOf(b.getSourceDeliveryId(), b.getFactoryId(), b.getProductId()).isEmpty())
            throw new BusinessException("用料明细不能为空（至少一行实际用料）");

        // 前置校验：在厂成品行（形态 PRODUCT_DEFECT）数量必须足够（审核间隙可能被其他单据扣走）
        BigDecimal onSite = stockService.getQuantity(factoryWhId, b.getProductId(), defectQt, WarehouseStock.FORM_PRODUCT_DEFECT);
        if (onSite.compareTo(qty) < 0) {
            throw new BusinessException("在厂成品（加工退货）不足：当前 " + onSite + "，需核销 " + qty);
        }
        // 2026-09-27：审核时**再按来源单复核一次**（堵住"多张草稿各自不超、审核后累计超"的窗口；
        // 草稿不参与聚合，只在审核动作上做最终判定；排除自身因为在 claim 后本单已是 AUDITED）
        if (b.getSourceDeliveryId() != null) {
            assertSourceReturnable(b.getSourceDeliveryId(), b.getFactoryId(), b.getProductId(), defectQt, qty, id);
        }

        // ① 核销在厂成品（PRODUCT_DEFECT 行）
        stockService.changeStock(factoryWhId, b.getProductId(), qty.negate(),
                StockChangeType.OUTSOURCE_BACK_CONSUME, b.getCode(), RelatedBillType.OUTSOURCE_RETURN_BACK,
                "", id, defectQt, WarehouseStock.FORM_PRODUCT_DEFECT);

        // ② 修好成品回我方仓（普通成品行）
        stockService.changeStock(b.getInWarehouseId(), b.getProductId(), qty,
                StockChangeType.OUTSOURCE_BACK_IN, b.getCode(), RelatedBillType.OUTSOURCE_RETURN_BACK,
                "", id, returnQt);

        // ③ 实际用料逐行从委外仓扣（允许扣负：工厂已实际耗用，账面可能不足）。
        //    2026-09-29（用户口径「登记返回时可以填写具体价格，默认 FIFO 可修改」）：**两笔钱分开算** ——
        //      · 赔料应收 = **登记时快照的单价**（人工定价，或登记时点的默认 FIFO 价）× 数量；
        //      · 成本结转 = **审核时点**的 FIFO（人工定价只改"向工厂要多少钱"，不污染库存成本）。
        BigDecimal receivableAmount = BigDecimal.ZERO;
        BigDecimal fifoCostAmount = BigDecimal.ZERO;
        boolean anyManual = false;
        for (OutsourceReturnBackItem it : items) {
            BigDecimal q = it.getQuantity();
            stockService.changeMaterialStockAllowNegative(factoryWhId, it.getMaterialId(), q.negate(),
                    StockChangeType.OUTSOURCE_BACK_MATERIAL.getCode(), b.getCode(), RelatedBillType.OUTSOURCE_RETURN_BACK,
                    null, null, id, WarehouseStock.FORM_MATERIAL);
            // 应收单价：登记时快照（人工/默认）；历史草稿没有快照（2026-09-29 之前的行）才现算 FIFO 兜底
            BigDecimal unit = it.getUnitPrice() != null
                    ? it.getUnitPrice()
                    : nzAmount(pricingService.fifoPriceWithFallback(it.getMaterialId(), q));
            BigDecimal amount = unit.multiply(q).setScale(2, RoundingMode.HALF_UP);
            // 成本单价：始终按审核时点的 FIFO（与库存计价口径一致）
            BigDecimal fifoUnit = nzAmount(pricingService.fifoPriceWithFallback(it.getMaterialId(), q));
            fifoCostAmount = fifoCostAmount.add(fifoUnit.multiply(q).setScale(2, RoundingMode.HALF_UP));
            if (it.getPriceManual() != null && it.getPriceManual() == 1) anyManual = true;
            OutsourceReturnBackItem u = new OutsourceReturnBackItem();
            u.setId(it.getId());
            u.setUnitPrice(unit);
            u.setAmount(amount);
            itemMapper.updateById(u);
            receivableAmount = receivableAmount.add(amount);
        }

        // ④ 成本结转：**物料耗用 FIFO** 结转到修好入库的成品（提升移动加权成本；反审核按单冲销批次）
        if (fifoCostAmount.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal unitCost = fifoCostAmount.divide(qty, 4, RoundingMode.HALF_UP);
            costService.applyProduct(b.getProductId(), qty, unitCost,
                    StockChangeType.OUTSOURCE_BACK_IN.getCode(), id, b.getCode());
        }

        // ⑤ 赔料应收（对加工厂，幂等复用同 billNo 行——反审核后重新审核不撞唯一键）
        upsertReceivable(b, receivableAmount, anyManual);

        OutsourceReturnBack u = new OutsourceReturnBack();
        u.setId(id);
        u.setMaterialAmount(receivableAmount);
        u.setAuditTime(java.time.LocalDateTime.now());
        // 2026-09-28（用户口径「登记返回需要审核和反审核」）：审核时**盖审核人章**
        // —— 原实现只写 auditTime，详情页「审核人」恒为空；现与全站其他单据同口径（unAudit 会清空三者）
        u.setAuditorId(getCurrentUserId());
        u.setAuditorName(getCurrentUserName());
        backMapper.updateById(u);
        log.info("加工返回单审核 {}：核销在厂 {} / 回仓 {} / 用料 {} 行 / 赔料应收 {}{}（成本结转按 FIFO {}）",
                b.getCode(), qty, qty, items.size(), receivableAmount, anyManual ? " (含人工定价)" : "", fifoCostAmount);
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
        // 2026-09-27：可选绑定「来源无单加工退货单」——台账据此显示已返回/未返回、按单防超返
        Long sourceDeliveryId = longOrNull(body.get("sourceDeliveryId"));
        b.setSourceDeliveryId(sourceDeliveryId);
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
        // 2026-09-27：绑定了来源单时，再按**单**防超返（同厂/同产品/同规格/已审核是前置条件）
        if (sourceDeliveryId != null) {
            assertSourceReturnable(sourceDeliveryId, factoryId, productId, defectQt, qty, null);
        }
        return b;
    }

    /**
     * 来源单校验 + **按单防超返**（2026-09-27）。
     * <p>规则：来源单必须存在、已审核、是无单加工退货记录（{@code sourceType=RETURN_DEFECT} 且不关联加工单），
     * 且与本次返回单**同加工厂 / 同产品 / 同退货规格**；然后
     * {@code Σ(已审核返回单，按本来源单) + 本次 ≤ 该来源单退货量}。</p>
     * <p>与 {@link #assertNotOverReturned}（"工厂+产品+规格"总额口径）并存：总额口径防跨单乱核销，
     * 本方法让每张来源单自己也可追溯、可显示进度。</p>
     */
    private void assertSourceReturnable(Long sourceDeliveryId, Long factoryId, Long productId,
                                        String defectQt, BigDecimal adding, Long excludeId) {
        com.beichen.erp.outsource.entity.OutsourceOrderDelivery src = deliveryMapper.selectById(sourceDeliveryId);
        if (src == null) throw new BusinessException("来源加工退货单不存在：ID=" + sourceDeliveryId);
        String srcNo = src.getCode() != null ? src.getCode() : ("加工退货#" + src.getId());
        if (!DocStatus.AUDITED.getCode().equals(src.getStatus())) {
            throw new BusinessException("来源单 " + srcNo + " 尚未审核（只有已审核的加工退货才能登记返回）");
        }
        if (src.getOrderId() != null) {
            throw new BusinessException("来源单 " + srcNo + " 已关联加工单（有单红冲不产生待返回，请选择无单加工退货单）");
        }
        if (!java.util.Objects.equals(src.getFactoryId(), factoryId)) {
            throw new BusinessException("来源单 " + srcNo + " 的加工厂与本次不一致");
        }
        if (!java.util.Objects.equals(src.getProductMasterId(), productId)) {
            throw new BusinessException("来源单 " + srcNo + " 的产品与本次不一致");
        }
        if (src.getQualityType() != null && !src.getQualityType().equals(defectQt)) {
            throw new BusinessException("来源单 " + srcNo + " 的退货规格（" + src.getQualityType() + "）与本次（" + defectQt + "）不一致");
        }
        BigDecimal sent = src.getQuantity() != null ? src.getQuantity().abs() : BigDecimal.ZERO;
        BigDecimal returned = BigDecimal.ZERO;
        for (OutsourceReturnBack r : backMapper.selectList(new LambdaQueryWrapper<OutsourceReturnBack>()
                .eq(OutsourceReturnBack::getSourceDeliveryId, sourceDeliveryId)
                .eq(OutsourceReturnBack::getStatus, DocStatus.AUDITED.getCode())
                .ne(excludeId != null, OutsourceReturnBack::getId, excludeId))) {
            returned = returned.add(r.getQuantity() != null ? r.getQuantity() : BigDecimal.ZERO);
        }
        if (returned.add(adding).compareTo(sent) > 0) {
            throw new BusinessException("来源单 " + srcNo + " 可返回量不足：退货 " + sent
                    + "，已返回 " + returned + "，本次 " + adding);
        }
    }

    /** 来源单号（列表展示用；存量未绑定/已删除来源单回落 "加工退货#id"） */
    private String sourceCodeOf(Long sourceDeliveryId) {
        if (sourceDeliveryId == null) return null;
        com.beichen.erp.outsource.entity.OutsourceOrderDelivery d = deliveryMapper.selectById(sourceDeliveryId);
        if (d == null) return "加工退货#" + sourceDeliveryId + "（已删除）";
        return d.getCode() != null ? d.getCode() : ("加工退货#" + d.getId());
    }

    /** 可空 Long 解析（来源单等可选参数） */
    private Long longOrNull(Object v) {
        if (v == null) return null;
        String s = v.toString().trim();
        if (s.isEmpty()) return null;
        try {
            return Long.valueOf(s);
        } catch (NumberFormatException e) {
            throw new BusinessException("参数格式不正确：" + s);
        }
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

    /**
     * 加工返回单「实际用料」**候选集**（2026-09-27 用户口径）：只能从**来源无单加工退货单的 BOM 快照**里选。
     * <p>解析顺序：① 来源单自带的 {@code bom_snapshot_id} → ② 现场兜底 = 该产品在该工厂
     * 「最近一次被加工单用过的快照」（存量返回单没绑来源时走这条）→ ③ 都拿不到 ⇒ 空池
     * （前端提示"未绑定来源/无 BOM ⇒ 没有可选的用料"，允许只登记返回、不填用料）。</p>
     * <p>与提交校验**共用本方法**（前端限制 + 后端拦截成对）；不筛库存（数量仍可超 BOM）。</p>
     */
    @Override
    public List<Map<String, Object>> materialCandidates(Long id) {
        OutsourceReturnBack b = backMapper.selectById(id);
        if (b == null) throw new BusinessException("返回单不存在");
        return candidatesOf(b.getSourceDeliveryId(), b.getFactoryId(), b.getProductId());
    }

    @Override
    public List<Map<String, Object>> materialCandidates(Long sourceDeliveryId, Long factoryId, Long productMasterId) {
        return candidatesOf(sourceDeliveryId, factoryId, productMasterId);
    }

    /** 候选集实现：① 来源单快照 → ② 现场解析兜底 → ③ 空 */
    private List<Map<String, Object>> candidatesOf(Long sourceDeliveryId, Long factoryId, Long productMasterId) {
        Long snapId = null;
        if (sourceDeliveryId != null) {
            com.beichen.erp.outsource.entity.OutsourceOrderDelivery src = deliveryMapper.selectById(sourceDeliveryId);
            if (src != null) snapId = src.getBomSnapshotId();
        }
        if (snapId == null) snapId = deliveryService.recentOrderSnapshotId(factoryId, productMasterId);
        if (snapId == null) return new ArrayList<>();
        final Long snapIdFinal = snapId;
        // 与「维修返回」的用料候选同口径：按物料合并单套用量（同一物料可能拆多行）
        Map<Long, Map<String, Object>> map = new LinkedHashMap<>();
        for (BomSnapshotItem it : bomSnapshotItemMapper.selectList(
                new LambdaQueryWrapper<BomSnapshotItem>().eq(BomSnapshotItem::getSnapshotId, snapIdFinal))) {
            Long key = it.getMaterialId();
            if (key == null) continue;
            Map<String, Object> m = map.computeIfAbsent(key, k -> {
                Map<String, Object> x = new LinkedHashMap<>();
                OutsourceMaterial om = materialMapper.selectById(k);
                x.put("materialId", k);
                x.put("materialName", om != null ? om.getMaterialName() : ("#" + k));
                x.put("unit", om != null ? om.getUnit() : it.getUnit());
                x.put("perSetQuantity", BigDecimal.ZERO);
                x.put("snapshotId", snapIdFinal);
                return x;
            });
            BigDecimal per = it.getQuantityPerSet() != null ? it.getQuantityPerSet() : BigDecimal.ZERO;
            m.put("perSetQuantity", ((BigDecimal) m.get("perSetQuantity")).add(per));
        }
        return new ArrayList<>(map.values());
    }

    /**
     * 2026-09-27（用户口径）：实际用料的**可选范围**收口到来源单的 BOM 快照 ——
     * 池外物料直接拒（数量仍可超：原"不做 BOM 比对"只针对数量）。
     */
    private void assertMaterialsInPool(List<Map<String, Object>> poolRows, List<OutsourceReturnBackItem> items) {
        if (items == null || items.isEmpty()) return;
        java.util.Set<Long> pool = new java.util.HashSet<>();
        for (Map<String, Object> c : poolRows) pool.add((Long) c.get("materialId"));
        for (OutsourceReturnBackItem it : items) {
            if (it.getMaterialId() == null || pool.contains(it.getMaterialId())) continue;
            OutsourceMaterial m = materialMapper.selectById(it.getMaterialId());
            throw new BusinessException("实际用料只能从**该加工退货单的 BOM 快照**里选：「"
                    + (m != null ? m.getMaterialName() : ("#" + it.getMaterialId())) + "」不在其中"
                    + (pool.isEmpty()
                        ? "（来源单未绑定 BOM 快照、或该产品没有 BOM ⇒ 本次只能不填用料）"
                        : "（本单可选物料 " + pool.size() + " 个）"));
        }
    }

    /** 解析明细行入参（materialId/quantity 必填且 >0；**数量**允许超 BOM，不做 BOM 比对 —— 范围由 assertMaterialsInPool 收口） */
    @SuppressWarnings("unchecked")
    private List<OutsourceReturnBackItem> itemsOf(Map<String, Object> body) {
        Object arr = body.get("items");
        List<OutsourceReturnBackItem> list = new ArrayList<>();
        // 2026-09-27：空表不再在这里直接报错 —— 由调用方按"可选池是否为空"决定（池空时允许只登记返回、
        // 不填用料；池非空仍要求至少一行，文案不变）。
        if (!(arr instanceof List<?> raw) || raw.isEmpty()) {
            return list;
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
            // 2026-09-29（用户口径「登记返回时可以填写具体价格，默认 FIFO 可修改」）：
            // 人工填价（unitPrice + priceManual=true）原样收下；没填的由 snapshotDefaultPrices 补登记时点的默认价。
            boolean manual = Boolean.TRUE.equals(row.get("priceManual"));
            Object upObj = row.get("unitPrice");
            if (upObj != null && !upObj.toString().isBlank()) {
                BigDecimal up;
                try {
                    up = new BigDecimal(upObj.toString());
                } catch (NumberFormatException e) {
                    throw new BusinessException("用料明细行单价格式不正确：" + upObj);
                }
                if (up.compareTo(BigDecimal.ZERO) < 0) throw new BusinessException("用料明细行单价不能为负数");
                it.setUnitPrice(up);
                it.setPriceManual(manual ? 1 : 0);
            } else {
                if (manual) throw new BusinessException("勾选人工定价的用料行必须填写单价");
                it.setPriceManual(0);
            }
            list.add(it);
        }
        return list;
    }

    /**
     * 登记时的**默认价快照**（2026-09-29 用户口径「登记返回时可以填写具体价格，默认 FIFO 可修改」）：
     * 只给**没带单价**的用料行补默认值（口径 = FIFO 四级链，与审核落账一致），并落在
     * {@code outsource_return_back_item.unit_price} 上 —— 审核直接用该快照算赔料应收，
     * 因此登记到审核之间的价格漂移不会改变本单金额；人工填价的行（{@code price_manual=1}）原样保留。
     */
    private void snapshotDefaultPrices(List<OutsourceReturnBackItem> items) {
        if (items == null) return;
        for (OutsourceReturnBackItem it : items) {
            if (it.getUnitPrice() != null) continue;
            if (it.getMaterialId() == null || it.getQuantity() == null) continue;
            it.setUnitPrice(nzAmount(pricingService.fifoPriceWithFallback(it.getMaterialId(), it.getQuantity())));
            it.setPriceManual(0);
        }
    }

    /** BigDecimal 兜底（null → 0），金额/单价计算用 */
    private static BigDecimal nzAmount(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    @Override
    public Map<String, Object> defaultMaterialPrice(Long materialId, BigDecimal quantity) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("materialId", materialId);
        m.put("quantity", quantity);
        if (materialId == null) {
            m.put("unitPrice", BigDecimal.ZERO);
            return m;
        }
        // 与登记/审核同一口径：FIFO 四级链（成本价 → 交期 FIFO → 参考价 → 0）
        m.put("unitPrice", nzAmount(pricingService.fifoPriceWithFallback(materialId, quantity)));
        return m;
    }

    /** 幂等落应收台账（对加工厂，subjectType=SUPPLIER） */
    private void upsertReceivable(OutsourceReturnBack b, BigDecimal amount, boolean anyManual) {
        // F7-250（2026-09-30 审核批 F 修复）：**金额 ≤ 0 不落账** —— 与同族
        // `StockLossAccountingHelper.post(:92)`「金额 ≤ 0 直接返回不落账」同口径。
        // 原先无论金额多少都插一行 ⇒ 库中留下 2 条 0 元**活跃**应收（ORB-20260929001/002），
        // 在「未结清 / 账龄」里形成"金额 0 却不结清"的歧义行（对账无法解释）。
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) return;
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
        // 2026-09-29：含人工定价行时在备注里点明（财务看到金额与 FIFO 不符时的依据）
        String tail = anyManual ? "（含人工定价用料）" : "";
        if (b.getRemark() != null && !b.getRemark().isBlank())
            fr.setRemark("加工返回料款（工厂赔料）" + tail + " " + b.getRemark());
        else fr.setRemark("加工返回料款（工厂赔料）" + tail);
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
        return BillNoSeq.formatUnique(prefix, seq, cand -> backMapper.selectCount(new LambdaQueryWrapper<OutsourceReturnBack>().eq(OutsourceReturnBack::getCode, cand)) > 0) /* F7-261 冲突检测+重试 */;
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
