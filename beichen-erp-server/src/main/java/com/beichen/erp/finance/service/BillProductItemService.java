package com.beichen.erp.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.finance.common.BillType;
import com.beichen.erp.finance.common.SourceBillType;
import com.beichen.erp.finance.entity.FinanceBill;
import com.beichen.erp.finance.entity.FinanceBillItem;
import com.beichen.erp.finance.entity.FinancePayable;
import com.beichen.erp.finance.entity.FinanceReceivable;
import com.beichen.erp.finance.mapper.FinanceBillItemMapper;
import com.beichen.erp.finance.mapper.FinanceBillMapper;
import com.beichen.erp.finance.mapper.FinancePayableMapper;
import com.beichen.erp.finance.mapper.FinanceReceivableMapper;
import com.beichen.erp.material.entity.Product;
import com.beichen.erp.material.mapper.ProductMapper;
import com.beichen.erp.purchase.entity.PurchaseExchangeItem;
import com.beichen.erp.purchase.entity.PurchaseOrderItem;
import com.beichen.erp.purchase.entity.PurchaseReturnItem;
import com.beichen.erp.purchase.mapper.PurchaseExchangeItemMapper;
import com.beichen.erp.purchase.mapper.PurchaseOrderItemMapper;
import com.beichen.erp.purchase.mapper.PurchaseReturnItemMapper;
import com.beichen.erp.sale.entity.SaleExchangeItem;
import com.beichen.erp.sale.entity.SaleOrderItem;
import com.beichen.erp.sale.entity.SaleReturnItem;
import com.beichen.erp.sale.mapper.SaleExchangeItemMapper;
import com.beichen.erp.sale.mapper.SaleOrderItemMapper;
import com.beichen.erp.sale.mapper.SaleReturnItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * 账单「产品明细」解析：把一张对账单里的**每一张来源单卖了什么（或收了什么费）**查出来（2026-10-02 用户要求）。
 *
 * <p><b>为什么必须两跳 join</b>：{@code finance_bill_item} 只存「来源单号 + 金额」，它的
 * {@code source_id} 指向的是**台账行**（{@code finance_receivable} / {@code finance_payable}），不是业务单据；
 * 业务单侧也没有反指账单的字段。明细行只能这样取：</p>
 * <pre>
 *   finance_bill_item.source_id → receivable/payable.id
 *     → receivable/payable.{source_bill_type, source_id} → 业务单（销售/采购 单·退货单·换货单）
 *       → 对应 *_item 明细表
 * </pre>
 *
 * <p><b>两类明细（2026-10-02 第二批：用户要求把"收费"也逐产品展开）</b>：</p>
 * <table border="1">
 *   <tr><th>lineKind</th><th>来源类型</th><th>明细来源 / 金额列</th><th>对账符号</th></tr>
 *   <tr><td rowspan="4">GOODS</td><td>SALE_ORDER / SALE_RETURN</td><td>{@code sale_order_item} / {@code sale_return_item} 的 amount</td><td>销售单取正、退货取负</td></tr>
 *   <tr><td>PURCHASE_ORDER / PURCHASE_RETURN</td><td>{@code purchase_order_item} / {@code purchase_return_item} 的 amount</td><td>采购单取正、退货取负</td></tr>
 *   <tr><td colspan="2"><i>（退货台账本身是负向冲减 ⇒ 明细合计要取负后才等于单金额）</i></td><td></td></tr>
 *   <tr><td colspan="3"></td></tr>
 *   <tr><td rowspan="4">CHARGE</td><td>SALE_RETURN_CHARGE / SALE_EXCHANGE_CHARGE</td><td>{@code sale_return_item} / {@code sale_exchange_item} 的 <b>chargeAmount</b></td><td rowspan="4">一律取正</td></tr>
 *   <tr><td>PURCHASE_RETURN_CHARGE / PURCHASE_EXCHANGE_CHARGE</td><td>{@code purchase_return_item} / {@code purchase_exchange_item} 的 <b>chargeAmount</b></td></tr>
 *   <tr><td colspan="2">金额 = 逐产品**收费/付费额**（不是货值）；数量/单价是该单据行的业务量，仅供对照</td></tr>
 *   <tr><td colspan="2">⚠️ 必须过滤 {@code chargeAmount <= 0} 的行：写台账时就是这么过滤的（见 SaleReturnServiceImpl:293 等四处），
 *       否则"明细合计"会比单金额小（0 行被算进来不会有事，但空行会让行数虚高、与台账口径不符）</td></tr>
 * </table>
 *
 * <p><b>逐单对账</b>：8 类来源都能对账 —— {@code 带符号的明细合计 == bill_item.amount}。
 * 不一致时前端显式标"不符"、导出的明细表也标出来，绝不静默合计：这是"明细没跑偏"的唯一硬证据。</p>
 *
 * <p><b>仍然不展开的类型</b>（{@code reasonOf} 给一句**类型专属**说明，不伪造明细）：采购换货的
 * 退回/换入两侧（金额是差额的一侧）、退货整理折损、报损、委外系列、预收预付台账、应付转应收、
 * 销售出库/采购入库（与同号单据同源）。</p>
 *
 * <p><b>性能</b>：全程批量 —— 台账一次 IN、每类型明细一次 IN、产品档案一次 IN，禁止按行查询。
 * 本类**只读**，不改任何写入路径与状态机。</p>
 */
@Service
@RequiredArgsConstructor
public class BillProductItemService {

    private final FinanceBillMapper billMapper;
    private final FinanceBillItemMapper billItemMapper;
    private final FinanceReceivableMapper receivableMapper;
    private final FinancePayableMapper payableMapper;
    private final ProductMapper productMapper;
    private final SaleOrderItemMapper saleOrderItemMapper;
    private final SaleReturnItemMapper saleReturnItemMapper;
    private final SaleExchangeItemMapper saleExchangeItemMapper;
    private final PurchaseOrderItemMapper purchaseOrderItemMapper;
    private final PurchaseReturnItemMapper purchaseReturnItemMapper;
    private final PurchaseExchangeItemMapper purchaseExchangeItemMapper;

    /** 货值类：明细行是产品/品质/数量/单价/金额 */
    private static final Set<SourceBillType> GOODS_TYPES = EnumSet.of(
            SourceBillType.SALE_ORDER, SourceBillType.SALE_RETURN,
            SourceBillType.PURCHASE_ORDER, SourceBillType.PURCHASE_RETURN);

    /** 收费类：明细行是**逐产品收费额**（charge_amount），不是货值 */
    private static final Set<SourceBillType> CHARGE_TYPES = EnumSet.of(
            SourceBillType.SALE_RETURN_CHARGE, SourceBillType.SALE_EXCHANGE_CHARGE,
            SourceBillType.PURCHASE_RETURN_CHARGE, SourceBillType.PURCHASE_EXCHANGE_CHARGE);

    /** 能逐产品展开（= 能逐单对账）的全部类型 */
    private static final Set<SourceBillType> DETAIL_TYPES;

    /** 退货类：明细方向与台账方向相反（台账为负向冲减） */
    private static final Set<SourceBillType> NEGATIVE_TYPES = EnumSet.of(
            SourceBillType.SALE_RETURN, SourceBillType.PURCHASE_RETURN);

    static {
        EnumSet<SourceBillType> all = EnumSet.noneOf(SourceBillType.class);
        all.addAll(GOODS_TYPES);
        all.addAll(CHARGE_TYPES);
        DETAIL_TYPES = Collections.unmodifiableSet(all);
    }

    /**
     * 按来源单分组的账单产品明细。
     *
     * <p>每组：{@code billItemId / sourceBillType / sourceBillNo / sourceId / itemAmount / lineKind /
     * linesAmount / signedLinesAmount / reconcilable / matched / noDetailReason / lines}；
     * {@code sourceId} = **业务单 id**（前端据此把来源单号做成可点，跳该单据详情）✓。
     * {@code lineKind} = {@code GOODS}（货值）或 {@code CHARGE}（收费）—— 前端据此切换金额列标题与提示。</p>
     */
    public List<Map<String, Object>> groups(Long billId) {
        FinanceBill bill = billMapper.selectById(billId);
        if (bill == null) throw new BusinessException("账单不存在");
        List<FinanceBillItem> items = billItemMapper.selectList(new LambdaQueryWrapper<FinanceBillItem>()
                .eq(FinanceBillItem::getBillId, billId).orderByAsc(FinanceBillItem::getId));
        if (items.isEmpty()) return List.of();

        // ---- ① 台账行 → 业务单（类型 + 业务单 id）----
        boolean payableSide = BillType.PAYABLE.getCode().equalsIgnoreCase(bill.getBillType());
        List<Long> ledgerIds = items.stream().map(FinanceBillItem::getSourceId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, SourceBillType> bizType = new HashMap<>();
        Map<Long, Long> bizId = new HashMap<>();
        if (!ledgerIds.isEmpty()) {
            if (payableSide) {
                for (FinancePayable p : payableMapper.selectBatchIds(ledgerIds)) {
                    bizType.put(p.getId(), SourceBillType.fromCode(p.getSourceBillType()));
                    bizId.put(p.getId(), p.getSourceId());
                }
            } else {
                for (FinanceReceivable r : receivableMapper.selectBatchIds(ledgerIds)) {
                    bizType.put(r.getId(), SourceBillType.fromCode(r.getSourceBillType()));
                    bizId.put(r.getId(), r.getSourceId());
                }
            }
        }

        // ---- ② 各类型要查的业务单 id（每类型一次 IN）----
        Map<SourceBillType, Set<Long>> idsByType = new EnumMap<>(SourceBillType.class);
        for (FinanceBillItem it : items) {
            SourceBillType t = bizType.get(it.getSourceId());
            Long oid = bizId.get(it.getSourceId());
            if (t == null || oid == null || !DETAIL_TYPES.contains(t)) continue;
            idsByType.computeIfAbsent(t, k -> new LinkedHashSet<>()).add(oid);
        }

        // ---- ③ 明细行：类型 → (业务单 id → 行) ----
        Map<SourceBillType, Map<Long, List<Map<String, Object>>>> linesByType = new EnumMap<>(SourceBillType.class);
        for (Map.Entry<SourceBillType, Set<Long>> e : idsByType.entrySet()) {
            List<Long> ids = new ArrayList<>(e.getValue());
            Map<Long, List<Map<String, Object>>> byOrder = new HashMap<>();
            switch (e.getKey()) {
                case SALE_ORDER -> {
                    for (SaleOrderItem i : saleOrderItemMapper.selectList(
                            new LambdaQueryWrapper<SaleOrderItem>().in(SaleOrderItem::getOrderId, ids))) {
                        addLine(byOrder, i.getOrderId(), i.getProductId(), i.getQualityType(),
                                i.getQuantity(), i.getUnitPrice(), i.getAmount());
                    }
                }
                case SALE_RETURN -> {
                    for (SaleReturnItem i : saleReturnItemMapper.selectList(
                            new LambdaQueryWrapper<SaleReturnItem>().in(SaleReturnItem::getReturnId, ids))) {
                        addLine(byOrder, i.getReturnId(), i.getProductId(), i.getQualityType(),
                                i.getQuantity(), i.getUnitPrice(), i.getAmount());
                    }
                }
                case PURCHASE_ORDER -> {
                    for (PurchaseOrderItem i : purchaseOrderItemMapper.selectList(
                            new LambdaQueryWrapper<PurchaseOrderItem>().in(PurchaseOrderItem::getOrderId, ids))) {
                        addLine(byOrder, i.getOrderId(), i.getProductId(), i.getQualityType(),
                                i.getQuantity(), i.getUnitPrice(), i.getAmount());
                    }
                }
                case PURCHASE_RETURN -> {
                    for (PurchaseReturnItem i : purchaseReturnItemMapper.selectList(
                            new LambdaQueryWrapper<PurchaseReturnItem>().in(PurchaseReturnItem::getReturnId, ids))) {
                        addLine(byOrder, i.getReturnId(), i.getProductId(), i.getQualityType(),
                                i.getQuantity(), i.getUnitPrice(), i.getAmount());
                    }
                }
                // ---- 收费类：金额取逐产品 chargeAmount（过滤 <=0，与写台账同口径）----
                case SALE_RETURN_CHARGE -> {
                    for (SaleReturnItem i : saleReturnItemMapper.selectList(
                            new LambdaQueryWrapper<SaleReturnItem>().in(SaleReturnItem::getReturnId, ids))) {
                        if (positive(i.getChargeAmount())) {
                            addLine(byOrder, i.getReturnId(), i.getProductId(), i.getQualityType(),
                                    i.getQuantity(), i.getUnitPrice(), i.getChargeAmount(),
                                    i.getChargeType(), i.getChargeReason());
                        }
                    }
                }
                case SALE_EXCHANGE_CHARGE -> {
                    // ⚠️ sale_exchange_item **没有退回品质列**（退回品质落在关联的销售单明细上）⇒ 品质取换出侧
                    // outQualityType（只支持同品换货，产品就是退回产品，2026-10-02 编译期发现）
                    for (SaleExchangeItem i : saleExchangeItemMapper.selectList(
                            new LambdaQueryWrapper<SaleExchangeItem>().in(SaleExchangeItem::getExchangeId, ids))) {
                        if (positive(i.getChargeAmount())) {
                            addLine(byOrder, i.getExchangeId(), i.getProductId(), i.getOutQualityType(),
                                    i.getQuantity(), i.getUnitPrice(), i.getChargeAmount(),
                                    i.getChargeType(), i.getChargeReason());
                        }
                    }
                }
                case PURCHASE_RETURN_CHARGE -> {
                    for (PurchaseReturnItem i : purchaseReturnItemMapper.selectList(
                            new LambdaQueryWrapper<PurchaseReturnItem>().in(PurchaseReturnItem::getReturnId, ids))) {
                        if (positive(i.getChargeAmount())) {
                            addLine(byOrder, i.getReturnId(), i.getProductId(), i.getQualityType(),
                                    i.getQuantity(), i.getUnitPrice(), i.getChargeAmount(),
                                    i.getChargeType(), i.getChargeReason());
                        }
                    }
                }
                case PURCHASE_EXCHANGE_CHARGE -> {
                    for (PurchaseExchangeItem i : purchaseExchangeItemMapper.selectList(
                            new LambdaQueryWrapper<PurchaseExchangeItem>().in(PurchaseExchangeItem::getExchangeId, ids))) {
                        if (positive(i.getChargeAmount())) {
                            addLine(byOrder, i.getExchangeId(), i.getProductId(), i.getQualityType(),
                                    i.getQuantity(), i.getUnitPrice(), i.getChargeAmount(),
                                    i.getChargeType(), i.getChargeReason());
                        }
                    }
                }
                default -> { /* DETAIL_TYPES 之外的取不到这里 */ }
            }
            linesByType.put(e.getKey(), byOrder);
        }

        // ---- ④ 产品档案（一次 IN）----
        Set<Long> productIds = new LinkedHashSet<>();
        for (Map<Long, List<Map<String, Object>>> m : linesByType.values()) {
            for (List<Map<String, Object>> ls : m.values()) {
                for (Map<String, Object> l : ls) {
                    Long pid = asLong(l.get("productId"));
                    if (pid != null) productIds.add(pid);
                }
            }
        }
        Map<Long, Product> products = new HashMap<>();
        if (!productIds.isEmpty()) productMapper.selectBatchIds(productIds).forEach(p -> products.put(p.getId(), p));

        // ---- ⑤ 组装 ----
        List<Map<String, Object>> out = new ArrayList<>();
        for (FinanceBillItem it : items) {
            SourceBillType t = bizType.get(it.getSourceId());
            Long oid = bizId.get(it.getSourceId());
            Map<String, Object> g = new LinkedHashMap<>();
            g.put("billItemId", it.getId());
            g.put("sourceBillType", it.getSourceBillType());
            g.put("sourceBillNo", it.getSourceBillNo());
            g.put("sourceId", oid);
            g.put("itemAmount", it.getAmount());
            g.put("dueDate", it.getDueDate());

            if (t == null || oid == null) {
                g.put("lines", List.of());
                g.put("lineKind", null);
                g.put("reconcilable", false);
                g.put("matched", null);
                g.put("noDetailReason", "台账行不存在或未挂业务单（来源单号 " + it.getSourceBillNo() + "），无法追明细");
            } else if (!DETAIL_TYPES.contains(t)) {
                g.put("lines", List.of());
                g.put("lineKind", null);
                g.put("reconcilable", false);
                g.put("matched", null);
                g.put("noDetailReason", reasonOf(t));
            } else {
                boolean charge = CHARGE_TYPES.contains(t);
                List<Map<String, Object>> lines = linesByType
                        .getOrDefault(t, Map.of()).getOrDefault(oid, List.of());
                boolean negate = !charge && NEGATIVE_TYPES.contains(t);
                BigDecimal sum = BigDecimal.ZERO;
                for (Map<String, Object> l : lines) {
                    Long pid = asLong(l.get("productId"));
                    Product p = pid != null ? products.get(pid) : null;
                    l.put("sku", p != null && p.getSku() != null ? p.getSku() : "");
                    l.put("productName", p != null && p.getName() != null ? p.getName() : "");
                    l.put("unit", p != null && p.getUnit() != null ? p.getUnit() : "");
                    l.put("charge", charge);
                    BigDecimal amt = (BigDecimal) l.get("amount");
                    // 带符号金额：与台账方向一致（退货为负）⇒ 前端"金额"列与导出明细页的合计都能与单金额/主表合计对齐
                    l.put("signedAmount", negate ? amt.negate() : amt);
                    sum = sum.add(amt);
                }
                BigDecimal signed = negate ? sum.negate() : sum;
                g.put("lines", lines);
                g.put("lineKind", charge ? "CHARGE" : "GOODS");
                g.put("linesAmount", sum);
                g.put("signedLinesAmount", signed);
                g.put("reconcilable", true);
                g.put("matched", it.getAmount() != null && signed.compareTo(it.getAmount()) == 0);
            }
            out.add(g);
        }
        return out;
    }

    private static boolean positive(BigDecimal v) { return v != null && v.compareTo(BigDecimal.ZERO) > 0; }

    private static Long asLong(Object v) {
        if (v == null) return null;
        return v instanceof Number ? ((Number) v).longValue() : null;
    }

    private static void addLine(Map<Long, List<Map<String, Object>>> byOrder, Long parentId, Long productId,
                               String qualityType, BigDecimal quantity, BigDecimal unitPrice, BigDecimal amount) {
        addLine(byOrder, parentId, productId, qualityType, quantity, unitPrice, amount, null, null);
    }

    /**
     * 同上 + 收费行的**收费类型/收费说明**（2026-10-02 第二批：收费明细要能回答"这 50 元是什么钱"）。
     *
     * <p>⚠️ 收费类型是**分侧**的：销售退货用 {@code COVER_SCRATCH/OTHER}（盖板划伤/其他），
     * 销售换货与采购侧用 {@code SERVICE/DIFF/FULL/OTHER}。所以只把原始 code 带给前端，
     * **中文映射由 {@code enums.billChargeTypeLabel(sourceBillType, code)} 按来源类型查**
     * —— 后端不做映射，避免两个模块的枚举混在一处（历史上销售退货借用过换货的枚举，2026-10-02 拆开）。</p>
     *
     * <p>另：收费行**不再往外抛"本次收费的数量/单价"** —— 库里没有这两个字段，而且服务费/其他这类
     * 收费本来就是**一笔整额**、没有数量可言（硬加字段只会得到一堆空值或假数字）。收费行前端改列
     * 「单据数量 / 收费类型 / 收费说明 / 收费金额」，数量注明是该单据行的业务量。</p>
     */
    private static void addLine(Map<Long, List<Map<String, Object>>> byOrder, Long parentId, Long productId,
                               String qualityType, BigDecimal quantity, BigDecimal unitPrice, BigDecimal amount,
                               String chargeType, String chargeReason) {
        if (parentId == null) return;
        Map<String, Object> l = new LinkedHashMap<>();
        l.put("productId", productId);
        l.put("qualityType", qualityType);
        l.put("quantity", quantity != null ? quantity : BigDecimal.ZERO);
        l.put("unitPrice", unitPrice != null ? unitPrice : BigDecimal.ZERO);
        l.put("amount", amount != null ? amount : BigDecimal.ZERO);
        l.put("chargeType", chargeType);
        l.put("chargeReason", chargeReason);
        byOrder.computeIfAbsent(parentId, k -> new ArrayList<>()).add(l);
    }

    /**
     * 无产品明细的来源类型 → 给用户的一句话（**不能只写"无明细"**：不同类型的原因不一样，
     * 混成一句话会让"为什么这张单没有明细"变成新的黑盒）。
     */
    private static String reasonOf(SourceBillType t) {
        return switch (t) {
            case PURCHASE_EXCHANGE_RETURN, PURCHASE_EXCHANGE_IN ->
                    "该来源是采购换货的「" + t.getLabel() + "」：金额是换货差额的一侧，本批不展开";
            case RETURN_SORT_LOSS -> "该来源是「退货整理折损」：按整理后的折损额入账，与退货单明细不同源";
            case INVENTORY_STOCK_LOSS, OUTSOURCE_STOCK_LOSS -> "该来源是「" + t.getLabel() + "」：按报损单金额入账，无销售/采购明细";
            case ADVANCE_LEDGER -> "该来源是「预收/预付台账」：多收/多付的挂账，没有对应的业务单明细";
            case PAYABLE_TRANSFER -> "该来源是「应付转应收」：由应付转入的扣款，明细在被转入的那张应付上";
            case SALE_OUTBOUND -> "该来源是「销售出库」：与同号销售单同源，请对照该销售单的明细";
            case PURCHASE_INBOUND -> "该来源是「采购入库」：与同号采购单同源，请对照该采购单的明细";
            case OUTSOURCE_DELIVERY, OUTSOURCE_MATERIAL_DELIVERY, OUTSOURCE_RETURN, OUTSOURCE_RETURN_CHARGE,
                 OUTSOURCE_REPAIR_CHARGE, OUTSOURCE_MATERIAL_RETURN, OUTSOURCE_MATERIAL_REPAIR_FEE,
                 OUTSOURCE_EXCESS_LOSS, OUTSOURCE_RETURN_BACK ->
                    "该来源是委外单据「" + t.getLabel() + "」：产品/物料明细在委外模块，本批不展开";
            default -> "该来源类型（" + t.getLabel() + "）没有产品明细，只按金额入账";
        };
    }
}
