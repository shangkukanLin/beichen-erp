package com.beichen.erp.inventory.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.beichen.erp.common.R;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.inventory.entity.InventoryStockReclass;
import com.beichen.erp.inventory.entity.InventoryStockReclassItem;
import com.beichen.erp.inventory.service.InventoryStockReclassService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 品质重分类单接口（路径与前端 @/api/inventory 约定一致） */
@RestController
@RequestMapping("/inventory/reclassify")
@RequiredArgsConstructor
public class InventoryStockReclassController {

    private final InventoryStockReclassService reclassService;

    /** 分页查询 */
    @GetMapping("/page")
    public R<IPage<InventoryStockReclass>> page(@RequestParam Map<String, Object> params) {
        if (!params.containsKey("companyId")) params.put("companyId", CompanyContext.get());
        return R.ok(reclassService.pageList(params));
    }

    /** 详情（header） */
    @GetMapping("/{id}")
    public R<InventoryStockReclass> get(@PathVariable Long id) {
        return R.ok(reclassService.getById(id));
    }

    /** 明细 */
    @GetMapping("/{id}/items")
    public R<List<InventoryStockReclassItem>> items(@PathVariable Long id) {
        return R.ok(reclassService.loadItems(id));
    }

    /** 新增（草稿） */
    @PostMapping
    public R<Long> create(@RequestBody Map<String, Object> body) {
        return R.ok(reclassService.saveDraft(toHeader(body), toItems(body)));
    }

    /** 修改草稿 */
    @PutMapping("/{id}")
    public R<Long> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        body.put("id", id);
        return R.ok(reclassService.saveDraft(toHeader(body), toItems(body)));
    }

    /** 审核（按等级转移库存） */
    @PutMapping("/{id}/audit")
    public R<String> audit(@PathVariable Long id) {
        reclassService.review(id);
        return R.ok("审核成功");
    }

    /** 取消（反审核，逆向恢复库存） */
    @PutMapping("/{id}/cancel")
    public R<String> cancel(@PathVariable Long id) {
        reclassService.unreview(id);
        return R.ok("已取消");
    }

    /** 作废 */
    @PutMapping("/{id}/discard")
    public R<String> discard(@PathVariable Long id) {
        reclassService.discard(id);
        return R.ok("已作废");
    }

    private InventoryStockReclass toHeader(Map<String, Object> body) {
        InventoryStockReclass header = new InventoryStockReclass();
        header.setId(body.get("id") == null ? null : Long.valueOf(body.get("id").toString()));
        header.setWarehouseId(body.get("warehouseId") == null ? null : Long.valueOf(body.get("warehouseId").toString()));
        Object d = body.get("reclassifyDate");
        header.setReclassifyDate(d == null ? null : java.time.LocalDate.parse(d.toString()));
        header.setRemark(body.get("remark") == null ? null : body.get("remark").toString());
        header.setCompanyId(CompanyContext.get());
        return header;
    }

    private List<InventoryStockReclassItem> toItems(Map<String, Object> body) {
        List<Map<String, Object>> list = (List<Map<String, Object>>) body.get("items");
        if (list == null) return List.of();
        return list.stream().map(m -> {
            InventoryStockReclassItem it = new InventoryStockReclassItem();
            it.setProductId(m.get("productId") == null ? null : Long.valueOf(m.get("productId").toString()));
            it.setFromQuality(m.get("fromQuality") == null ? null : m.get("fromQuality").toString());
            it.setToQuality(m.get("toQuality") == null ? null : m.get("toQuality").toString());
            it.setQuantity(new java.math.BigDecimal(m.get("quantity").toString()));
            it.setRemark(m.get("remark") == null ? null : m.get("remark").toString());
            return it;
        }).toList();
    }
}
