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
    // 期 3（2026-09-19 读隔离）：退货页的「关联物料订单」下拉改由本页接口提供
    private final com.beichen.erp.outsource.service.MaterialOrderService materialOrderService;

    /**
     * 该物料商的物料订单（物料退货页「关联物料订单」下拉；维修退货闭环：收货中/已完成）。
     * <p>期 3（2026-09-19 读隔离）：原先物料退货页直读 {@code /api/outsource/material-order/page}
     * （需 {@code outsource:material-order}）⇒ 只被授予 {@code outsource:material-return} 的用户会 403。
     * 现走本页前缀，复用物料订单模块的**同一分页查询**。</p>
     */
    @GetMapping("/material-orders")
    public R<Page<Map<String, Object>>> materialOrders(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String statuses) {
        return R.ok(materialOrderService.page(pageNum, pageSize, code, null, statuses, supplierId));
    }

    /** 分页查询 */
    @GetMapping("/page")
    public R<Page<Map<String, Object>>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String returnType,
            @RequestParam(required = false) String progress) {
        return R.ok(returnService.page(pageNum, pageSize, code, supplierId, status, returnType, progress));
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

    /** 登记维修返回（维修退货单已审核后，供应商修好把物料送回来 → 入库；不产生应付，2026-09-17） */
    @PostMapping("/{id}/repair-return")
    public R<Void> repairReturn(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        returnService.repairReturn(id, body);
        return R.ok();
    }

    /** 撤销维修返回（按记录ID：扣回已入库物料并删除该记录） */
    @DeleteMapping("/repair-return/{repairRecordId}")
    public R<Void> cancelRepairReturn(@PathVariable Long repairRecordId) {
        returnService.cancelRepairReturn(repairRecordId);
        return R.ok();
    }

    /**
     * 结案（仅维修退货，2026-09-17）：全部送修数量都已返回（未返回=0）后确认收尾 —— 场景②/③的跟踪终点。
     * <p>结案后禁止登记/撤销维修返回、禁止反审核（需先「撤销结案」）。</p>
     */
    @PutMapping("/{id}/close")
    public R<Void> close(@PathVariable Long id) {
        returnService.close(id);
        return R.ok();
    }

    /** 撤销结案：回到"送修中"跟踪状态，可继续登记维修返回 */
    @PutMapping("/{id}/re-open")
    public R<Void> reOpen(@PathVariable Long id) {
        returnService.reOpen(id);
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

    /** 从「物料收货」发起退货的预填数据（按收料单带出供应商/源仓/物料与可退数量） */
    @GetMapping("/return-prefill")
    public R<Map<String, Object>> returnPrefill(@RequestParam Long deliveryId) {
        return R.ok(returnService.returnPrefill(deliveryId));
    }

    // ===== 请求解析 =====

    private OutsourceMaterialReturn parseOrder(Map<String, Object> body) {
        OutsourceMaterialReturn o = new OutsourceMaterialReturn();
        Object sid = body.get("supplierId");
        if (sid != null && !sid.toString().isBlank()) o.setSupplierId(Long.valueOf(sid.toString()));
        Object wid = body.get("fromWarehouseId");
        if (wid != null && !wid.toString().isBlank()) o.setFromWarehouseId(Long.valueOf(wid.toString()));
        // 来源收料单（物料收货页发起退货时带出，用于按记录算「可退数量」）
        Object sdid = body.get("sourceDeliveryId");
        if (sdid != null && !sdid.toString().isBlank()) o.setSourceDeliveryId(Long.valueOf(sdid.toString()));
        // 关联物料订单（维修退货闭环，2026-09-17）：未传/清空=不关联（返回情况靠本单「送修/已返回」跟踪）
        Object moid = body.get("materialOrderId");
        if (moid != null && !moid.toString().isBlank()) o.setMaterialOrderId(Long.valueOf(moid.toString()));
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
