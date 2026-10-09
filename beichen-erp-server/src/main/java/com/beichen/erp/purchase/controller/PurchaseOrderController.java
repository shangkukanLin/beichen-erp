package com.beichen.erp.purchase.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.purchase.entity.PurchaseOrder;
import com.beichen.erp.purchase.entity.PurchaseOrderItem;
import com.beichen.erp.purchase.service.PurchaseOrderService;
import com.beichen.erp.purchase.service.PurchaseReturnService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory/purchase")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final PurchaseOrderService service;
    // 期 2（2026-09-19 读隔离）：详情页的「本单退货情况」下沉到本页接口
    private final PurchaseReturnService purchaseReturnService;
    // 2026-09-24（用户口径：采购单详情补「换货情况」，与销售单详情一致）：同 returns，**随详情接口一并返回**，
    // 避免前端跨页去查换货列表（那需要 purchase:exchange ⇒ 只有 purchase:order 的用户会 403）
    private final com.beichen.erp.purchase.service.PurchaseExchangeService purchaseExchangeService;
    /** Spring 容器里的 ObjectMapper（含 JavaTimeModule，见 SystemController 的说明） */
    private final ObjectMapper objectMapper;

    @GetMapping("/page")
    public R<Page<Map<String, Object>>> page(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String code,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(service.page(status, supplierId, code, pageNum, pageSize));
    }

    /**
     * 详情（期 2·2026-09-19 读隔离）：追加 {@code returns} —— 本单的退货情况。
     *
     * <p>原先采购单详情页要去读 {@code /api/inventory/purchase-return/by-order}（需 {@code purchase:return}），
     * 只被授予 {@code purchase:order} 的用户会 403。现由本页接口一并返回：
     * 响应体 = 实体原字段（JSON 路径不变）+ 追加字段。</p>
     */
    @GetMapping("/{id}")
    public R<Map<String, Object>> getById(@PathVariable Long id) {
        PurchaseOrder o = service.getById(id);
        if (o == null) return R.ok(Map.of());
        Map<String, Object> m = objectMapper.convertValue(o, new TypeReference<Map<String, Object>>() {});
        m.put("returns", purchaseReturnService.byOrder(id));
        // 2026-09-24：本单的换货情况（与销售单详情同款：page 按 purchaseOrderId 过滤后取 records）
        m.put("exchanges", purchaseExchangeService.page(1, 100, Map.of("purchaseOrderId", id)).getRecords());
        // 2026-10-02（用户口径「单据详情明细加已退 / 已换数量」）：逐**采购明细**的已退 / 已换累计。
        // 口径与来源选单接口 purchaseExchangeService.purchaseOrderItems 完全同源（已审核退货单 / 换货单的累计），
        // 前端按 purchaseOrderItemId 取 returnedQuantity / exchangedQuantity 两字段即可。
        // ⚠️ 必须由本页接口返回：2026-09-19 读隔离后「读也按页面码收口」，采购单详情页不能去调
        // 采购换货 / 采购退货的接口（只被授予 purchase:order 的用户会 403）。
        m.put("itemStats", purchaseExchangeService.purchaseOrderItems(id));
        return R.ok(m);
    }

    @GetMapping("/{id}/items")
    public R<List<PurchaseOrderItem>> getItems(@PathVariable Long id) {
        return R.ok(service.getItems(id));
    }

    @PostMapping
    public R<Long> create(@RequestBody Map<String, Object> body) {
        PurchaseOrder o = parseOrder(body);
        service.create(o, parseItems(body));
        // 返回新单ID，避免前端"创建后再查列表取ID"导致并发下取错
        return R.ok(o.getId());
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        PurchaseOrder order = parseOrder(body);
        order.setId(id);
        service.update(order, parseItems(body));
        return R.ok();
    }

    @PutMapping("/{id}/audit")
    public R<Void> audit(@PathVariable Long id) {
        service.audit(id);
        return R.ok();
    }

    @PutMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id) {
        service.cancel(id);
        return R.ok();
    }

    @PutMapping("/{id}/un-audit")
    public R<Void> unAudit(@PathVariable Long id) {
        service.unAudit(id);
        return R.ok();
    }

    @SuppressWarnings("unchecked")
    private PurchaseOrder parseOrder(Map<String, Object> body) {
        Map<String, Object> d = body.containsKey("order") ? (Map<String, Object>) body.get("order") : body;
        PurchaseOrder o = new PurchaseOrder();
        if (d.get("supplierId") != null) o.setSupplierId(Long.valueOf(d.get("supplierId").toString()));
        if (d.get("warehouseId") != null) o.setWarehouseId(Long.valueOf(d.get("warehouseId").toString()));
        if (d.get("orderDate") != null && !d.get("orderDate").toString().isBlank())
            o.setOrderDate(LocalDate.parse(d.get("orderDate").toString()));
        if (d.get("taxIncluded") != null) o.setTaxIncluded(Integer.valueOf(d.get("taxIncluded").toString()));
        if (d.get("taxRate") != null && !d.get("taxRate").toString().isBlank())
            o.setTaxRate(new BigDecimal(d.get("taxRate").toString()));
        o.setRemark((String) d.get("remark"));
        // 2026-10-09 结算方式（现金/账期）随单提交。**必须显式映射**：本方法是手写白名单，
        // 不列出的字段会被静默丢弃 —— 守卫实测抓到的就是这个：字段丢了之后服务层只看到 null，
        // 于是"未指定 ⇒ 现金"兜底生效并报「必须选择付款账户」，看起来像校验错，其实是入参被吃掉。
        if (d.get("settleType") != null) o.setSettleType(d.get("settleType").toString());
        if (d.get("settleAccountId") != null && !d.get("settleAccountId").toString().isBlank())
            o.setSettleAccountId(Long.valueOf(d.get("settleAccountId").toString()));
        if (d.get("settleAmount") != null && !d.get("settleAmount").toString().isBlank())
            o.setSettleAmount(new BigDecimal(d.get("settleAmount").toString()));
        return o;
    }

    @SuppressWarnings("unchecked")
    private List<PurchaseOrderItem> parseItems(Map<String, Object> body) {
        List<PurchaseOrderItem> list = new ArrayList<>();
        Object obj = body.get("items");
        if (obj instanceof List<?> raw) {
            for (Object o : raw) {
                if (o instanceof Map<?, ?> m) {
                    Map<String, Object> map = (Map<String, Object>) m;
                    PurchaseOrderItem it = new PurchaseOrderItem();
                    if (map.get("productId") != null) it.setProductId(Long.valueOf(map.get("productId").toString()));
                    if (map.get("quantity") != null && !map.get("quantity").toString().isBlank())
                        it.setQuantity(new BigDecimal(map.get("quantity").toString()));
                    if (map.get("unitPrice") != null && !map.get("unitPrice").toString().isBlank())
                        it.setUnitPrice(new BigDecimal(map.get("unitPrice").toString()));
                    it.setRemark((String) map.get("remark"));
                    it.setQualityType((String) map.get("qualityType"));
                    list.add(it);
                }
            }
        }
        return list;
    }
}
