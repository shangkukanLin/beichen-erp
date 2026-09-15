package com.beichen.erp.outsource.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.outsource.entity.ReturnOrder;
import com.beichen.erp.outsource.service.OutsourceReturnOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 委外加工退货单接口层
 * <p>仅做请求解析与结果包装，业务逻辑下沉至 {@link OutsourceReturnOrderService}。</p>
 */
@RestController
@RequestMapping("/api/outsource/return-order")
@RequiredArgsConstructor
public class ReturnOrderController {

    private final OutsourceReturnOrderService returnOrderService;

    @GetMapping("/page")
    public R<Page<Map<String, Object>>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) Long factoryId) {
        return R.ok(returnOrderService.page(pageNum, pageSize, code, factoryId));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(returnOrderService.detail(id));
    }

    @PostMapping
    public R<Void> create(@RequestBody Map<String, Object> body) {
        ReturnOrder order = parseOrder(body);
        returnOrderService.create(order, body);
        return R.ok();
    }

    /** 编辑草稿（E4：仅草稿可编辑；明细整体替换，草稿不动库存/应付） */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        ReturnOrder order = parseOrder(body);
        returnOrderService.update(id, order, body);
        return R.ok();
    }

    /** 审核：退货物料入工厂委外仓 + 成品出库 + 负向应付 */
    @PutMapping("/{id}/audit")
    public R<Void> audit(@PathVariable Long id) {
        returnOrderService.audit(id);
        return R.ok();
    }

    /** 取消审核：物料出工厂委外仓 + 成品恢复 + 冲销应付 */
    @PutMapping("/{id}/un-audit")
    public R<Void> unAudit(@PathVariable Long id) {
        returnOrderService.unAudit(id);
        return R.ok();
    }

    @PutMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id) {
        returnOrderService.cancel(id);
        return R.ok();
    }

    /** FIFO 物料单价 */
    @GetMapping("/fifo-price")
    public R<BigDecimal> fifoPrice(@RequestParam Long materialId, @RequestParam(defaultValue = "1") BigDecimal qty) {
        return R.ok(returnOrderService.fifoPrice(materialId, qty));
    }

    /** 获取某工厂的产品列表（含每个产品的BOM版本来源），用于退货选择 */
    @GetMapping("/order-products")
    public R<List<Map<String, Object>>> orderProducts(@RequestParam Long factoryId) {
        return R.ok(returnOrderService.orderProducts(factoryId));
    }

    /** 获取某产品在某加工单中的BOM快照物料 */
    @GetMapping("/bom-snapshot")
    public R<List<Map<String, Object>>> bomSnapshot(@RequestParam Long orderId, @RequestParam Long productId) {
        return R.ok(returnOrderService.bomSnapshot(orderId, productId));
    }

    // ===== 请求解析 =====

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
}
