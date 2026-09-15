package com.beichen.erp.outsource.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.outsource.entity.OutsourceMaterialReturn;
import com.beichen.erp.outsource.service.OutsourceMaterialReturnService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 委外物料退货单接口层
 * <p>仅做请求解析与结果包装，业务逻辑下沉至 {@link OutsourceMaterialReturnService}。</p>
 */
@RestController
@RequestMapping("/api/outsource/material-return")
@RequiredArgsConstructor
public class OutsourceMaterialReturnController {

    private final OutsourceMaterialReturnService returnService;

    /** 分页查询 */
    @GetMapping("/page")
    public R<Page<Map<String, Object>>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String status) {
        return R.ok(returnService.page(pageNum, pageSize, code, supplierId, status));
    }

    /** 详情 */
    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(returnService.detail(id));
    }

    /** 创建草稿 */
    @PostMapping
    public R<Void> create(@RequestBody Map<String, Object> body) {
        OutsourceMaterialReturn order = parseOrder(body);
        returnService.create(order, parseItems(body));
        return R.ok();
    }

    /** 编辑草稿 */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        OutsourceMaterialReturn order = parseOrder(body);
        returnService.update(id, order, parseItems(body));
        return R.ok();
    }

    /** 审核：物料出源仓 + 负向应付 */
    @PutMapping("/{id}/audit")
    public R<Void> audit(@PathVariable Long id) {
        returnService.audit(id);
        return R.ok();
    }

    /** 取消审核：物料回源仓 + 冲销应付 */
    @PutMapping("/{id}/un-audit")
    public R<Void> unAudit(@PathVariable Long id) {
        returnService.unAudit(id);
        return R.ok();
    }

    /** 作废（仅草稿） */
    @PutMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id) {
        returnService.cancel(id);
        return R.ok();
    }

    /** 删除（仅草稿） */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        returnService.delete(id);
        return R.ok();
    }

    /** 可选源仓列表（启用仓库） */
    @GetMapping("/warehouse-options")
    public R<List<Map<String, Object>>> warehouseOptions() {
        return R.ok(returnService.warehouseOptions());
    }

    /** 指定源仓可退物料库存（按物料聚合良品 GOOD 库存） */
    @GetMapping("/material-stock")
    public R<List<Map<String, Object>>> materialStock(@RequestParam Long warehouseId) {
        return R.ok(returnService.materialStock(warehouseId));
    }

    /** FIFO 物料单价 */
    @GetMapping("/fifo-price")
    public R<BigDecimal> fifoPrice(@RequestParam Long materialId, @RequestParam(defaultValue = "1") BigDecimal qty) {
        return R.ok(returnService.fifoPrice(materialId, qty));
    }

    // ===== 请求解析 =====

    private OutsourceMaterialReturn parseOrder(Map<String, Object> body) {
        OutsourceMaterialReturn o = new OutsourceMaterialReturn();
        Object sid = body.get("supplierId");
        if (sid != null && !sid.toString().isBlank()) o.setSupplierId(Long.valueOf(sid.toString()));
        Object wid = body.get("fromWarehouseId");
        if (wid != null && !wid.toString().isBlank()) o.setFromWarehouseId(Long.valueOf(wid.toString()));
        Object rt = body.get("returnType");
        if (rt != null && !rt.toString().isBlank()) o.setReturnType(rt.toString());
        Object dd = body.get("returnDate");
        if (dd != null && !dd.toString().isBlank()) o.setReturnDate(LocalDate.parse(dd.toString()));
        if (body.get("remark") != null) o.setRemark(body.get("remark").toString());
        return o;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseItems(Map<String, Object> body) {
        Object obj = body.get("items");
        return obj instanceof List ? (List<Map<String, Object>>) obj : null;
    }
}
