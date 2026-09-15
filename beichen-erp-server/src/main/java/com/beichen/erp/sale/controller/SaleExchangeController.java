package com.beichen.erp.sale.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.beichen.erp.common.R;
import com.beichen.erp.sale.entity.SaleExchange;
import com.beichen.erp.sale.entity.SaleExchangeItem;
import com.beichen.erp.sale.service.SaleExchangeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 销售换货单接口（同品换货，强关联销售单）
 */
@RestController
@RequestMapping("/api/sale/exchange")
@RequiredArgsConstructor
public class SaleExchangeController {

    private final SaleExchangeService exchangeService;

    /** 分页列表 */
    @GetMapping("/page")
    public R<IPage<Map<String, Object>>> page(@RequestParam(defaultValue = "1") long pageNum,
                                              @RequestParam(defaultValue = "10") long pageSize,
                                              @RequestParam Map<String, Object> q) {
        return R.ok(exchangeService.page(pageNum, pageSize, q));
    }

    /** 详情（含明细） */
    @GetMapping("/{id}")
    public R<Map<String, Object>> getById(@PathVariable Long id) {
        SaleExchange e = exchangeService.getById(id);
        if (e == null) return R.fail("换货单不存在");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("head", e);
        m.put("items", exchangeService.getItems(id));
        return R.ok(m);
    }

    /** 来源销售单明细（换货选单用，含已售/已退/已换/可换数量） */
    @GetMapping("/sale-order-items")
    public R<List<Map<String, Object>>> saleOrderItems(@RequestParam(required = false) Long saleOrderId) {
        return R.ok(exchangeService.saleOrderItems(saleOrderId));
    }

    /** 新增 */
    @PostMapping
    public R<SaleExchange> create(@RequestBody Map<String, Object> body) {
        return R.ok(exchangeService.create(extract(body), extractItems(body)));
    }

    /** 修改 */
    @PutMapping
    public R<SaleExchange> update(@RequestBody Map<String, Object> body) {
        return R.ok(exchangeService.update(extract(body), extractItems(body)));
    }

    /** 审核：退回入售后仓 + 换出从成品仓扣减 */
    @PutMapping("/{id}/audit")
    public R<Void> audit(@PathVariable Long id) {
        exchangeService.audit(id);
        return R.ok();
    }

    /** 反审核：对称回滚，回到草稿 */
    // E1 口径（2026-09-12）：反审核统一 /un-audit，旧路径 /unaudit 保留为别名
    @PutMapping({"/{id}/un-audit", "/{id}/unaudit"})
    public R<Void> unAudit(@PathVariable Long id) {
        exchangeService.unAudit(id);
        return R.ok();
    }

    /** 作废（仅草稿）：单据留痕，不做物理删除 */
    @PutMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id) {
        exchangeService.cancel(id);
        return R.ok();
    }

    /** 兼容旧接口：语义等同作废 */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        exchangeService.delete(id);
        return R.ok();
    }

    // ==================== 参数解析 ====================

    private SaleExchange extract(Map<String, Object> b) {
        SaleExchange e = new SaleExchange();
        if (b.get("id") != null && !b.get("id").toString().isBlank())
            e.setId(Long.valueOf(b.get("id").toString()));
        if (b.get("saleOrderId") != null && !b.get("saleOrderId").toString().isBlank())
            e.setSaleOrderId(Long.valueOf(b.get("saleOrderId").toString()));
        if (b.get("saleOrderCode") != null) e.setSaleOrderCode(b.get("saleOrderCode").toString());
        if (b.get("customerId") != null && !b.get("customerId").toString().isBlank())
            e.setCustomerId(Long.valueOf(b.get("customerId").toString()));
        if (b.get("warehouseInId") != null && !b.get("warehouseInId").toString().isBlank())
            e.setWarehouseInId(Long.valueOf(b.get("warehouseInId").toString()));
        if (b.get("warehouseOutId") != null && !b.get("warehouseOutId").toString().isBlank())
            e.setWarehouseOutId(Long.valueOf(b.get("warehouseOutId").toString()));
        if (b.get("exchangeDate") != null && !b.get("exchangeDate").toString().isBlank())
            e.setExchangeDate(LocalDate.parse(b.get("exchangeDate").toString()));
        // 是否收费：收费类型/金额/说明；金额由用户在前端手工填写，后端 normalized 后落库
        if (b.get("chargeFlag") != null && !b.get("chargeFlag").toString().isBlank())
            e.setChargeFlag(Integer.valueOf(b.get("chargeFlag").toString()));
        if (b.get("chargeType") != null) e.setChargeType(b.get("chargeType").toString());
        if (b.get("chargeAmount") != null && !b.get("chargeAmount").toString().isBlank())
            e.setChargeAmount(new BigDecimal(b.get("chargeAmount").toString()));
        if (b.get("chargeReason") != null) e.setChargeReason(b.get("chargeReason").toString());
        if (b.get("remark") != null) e.setRemark(b.get("remark").toString());
        return e;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractItems(Map<String, Object> b) {
        Object items = b.get("items");
        if (items instanceof List<?> list) {
            List<Map<String, Object>> res = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) res.add((Map<String, Object>) m);
            }
            return res;
        }
        return Collections.emptyList();
    }

}
