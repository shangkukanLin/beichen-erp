package com.beichen.erp.outsource.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.outsource.entity.ReturnOrder;
import com.beichen.erp.outsource.service.OutsourceOrderService;
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
    // 期 3（2026-09-19 读隔离）：退货页的「关联加工单」下拉改由本页接口提供
    private final OutsourceOrderService outsourceOrderService;

    /**
     * 该加工厂可关联的加工单（退货页「关联加工单」下拉；2026-09-17 口径：可选可清空，清空=不关联）。
     * <p>期 3（2026-09-19 读隔离）：原先退货页直读 {@code /api/outsource/order/page}
     * （需 {@code outsource:order}）⇒ 只被授予 {@code outsource:return-order} 的用户会 403。
     * 现走本页前缀，复用加工单模块的**同一分页查询**（形状与 {@code /outsource/order/page} 一致）。</p>
     */
    @GetMapping("/orders")
    public R<Page<Map<String, Object>>> orders(@RequestParam(required = false) Long factoryId,
                                               @RequestParam(defaultValue = "1") int pageNum,
                                               @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(outsourceOrderService.page(null, factoryId, null, pageNum, pageSize));
    }

    @GetMapping("/page")
    public R<Page<Map<String, Object>>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) Long factoryId,
            @RequestParam(required = false) String returnType,
            @RequestParam(required = false) String progress) {
        return R.ok(returnOrderService.page(pageNum, pageSize, code, factoryId, returnType, progress));
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

    /**
     * 该工厂加工过的产品 + 每个产品可用的 **BOM 快照**（2026-09-17 起选项单位由"加工单"改为"BOM 快照"，
     * 同一份快照被多张单共享时只出现一次，并附带用过它的加工单）。
     */
    @GetMapping("/order-products")
    public R<List<Map<String, Object>>> orderProducts(@RequestParam Long factoryId) {
        return R.ok(returnOrderService.orderProducts(factoryId));
    }

    /** BOM 快照的物料明细（单套用量口径） */
    @GetMapping("/bom-snapshot")
    public R<List<Map<String, Object>>> bomSnapshot(@RequestParam Long snapshotId) {
        return R.ok(returnOrderService.bomSnapshot(snapshotId));
    }

    /** 从「成品收货」发起退货的预填数据（交货记录ID 或 加工单ID 二选一） */
    @GetMapping("/return-prefill")
    public R<Map<String, Object>> returnPrefill(@RequestParam(required = false) Long deliveryId,
                                                @RequestParam(required = false) Long orderId) {
        return R.ok(returnOrderService.returnPrefill(deliveryId, orderId));
    }

    /** 登记维修返回（维修退货单已审核后，工厂修好送回入库；登记即生效，不产生应付） */
    @PostMapping("/{id}/repair-return")
    public R<Void> repairReturn(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        returnOrderService.repairReturn(id, body);
        return R.ok();
    }

    /** 撤销维修返回（库存回滚 + 删除该条返回记录） */
    @DeleteMapping("/repair-return/{recordId}")
    public R<Void> cancelRepairReturn(@PathVariable Long recordId) {
        returnOrderService.cancelRepairReturn(recordId);
        return R.ok();
    }

    /**
     * 结案（仅维修退货，2026-09-17）：工厂送修的全部成品都送回（未返回=0）后确认收尾。
     * <p>结案后禁止登记/撤销维修返回、禁止反审核（需先「撤销结案」）。</p>
     */
    @PutMapping("/{id}/close")
    public R<Void> close(@PathVariable Long id) {
        returnOrderService.close(id);
        return R.ok();
    }

    /** 撤销结案：回到「送修中」跟踪状态，可继续登记维修返回 */
    @PutMapping("/{id}/re-open")
    public R<Void> reOpen(@PathVariable Long id) {
        returnOrderService.reOpen(id);
        return R.ok();
    }

    // ===== 请求解析 =====

    private ReturnOrder parseOrder(Map<String, Object> body) {
        ReturnOrder o = new ReturnOrder();
        // 退货类型（2026-09-17）：DEFECT 不良退货 / REPAIR 维修退货；不传则保持原值/默认不良退货
        Object rt = body.get("returnType");
        if (rt != null && !rt.toString().isBlank()) o.setReturnType(rt.toString());
        Object fid = body.get("factoryId");
        if (fid != null && !fid.toString().isBlank()) o.setFactoryId(Long.valueOf(fid.toString()));
        Object oid = body.get("orderId");
        if (oid != null && !oid.toString().isBlank()) o.setOrderId(Long.valueOf(oid.toString()));
        // 来源交货记录（成品收货页发起退货时带出，用于按记录算「可退数量」）
        Object sdid = body.get("sourceDeliveryId");
        if (sdid != null && !sdid.toString().isBlank()) o.setSourceDeliveryId(Long.valueOf(sdid.toString()));
        Object dd = body.get("returnDate");
        if (dd != null && !dd.toString().isBlank()) o.setReturnDate(LocalDate.parse(dd.toString()));
        if (body.get("remark") != null) o.setRemark(body.get("remark").toString());
        return o;
    }
}
