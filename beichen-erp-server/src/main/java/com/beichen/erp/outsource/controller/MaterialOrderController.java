package com.beichen.erp.outsource.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.outsource.entity.MaterialOrder;
import com.beichen.erp.outsource.service.DeliveryService;
import com.beichen.erp.outsource.service.MaterialOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 委外物料订单接口层
 * <p>仅做请求解析与结果包装，业务逻辑下沉至 {@link MaterialOrderService}。</p>
 */
@RestController
@RequestMapping("/api/outsource/material-order")
@RequiredArgsConstructor
public class MaterialOrderController {

    private final MaterialOrderService materialOrderService;
    private final DeliveryService deliveryService;

    @GetMapping("/page")
    public R<Page<Map<String, Object>>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String statuses,
            @RequestParam(required = false) Long supplierId) {
        return R.ok(materialOrderService.page(pageNum, pageSize, code, status, statuses, supplierId));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(materialOrderService.detail(id));
    }

    @PostMapping
    public R<Long> create(@RequestBody Map<String, Object> body) {
        // 返回新单ID，避免前端"创建后再查列表取ID"导致并发下取错
        return R.ok(materialOrderService.create(parseOrder(body), parseItems(body)));
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        materialOrderService.update(id, parseOrder(body), parseItems(body));
        return R.ok();
    }

    @PutMapping("/{id}/audit")
    public R<Void> audit(@PathVariable Long id) {
        materialOrderService.audit(id);
        return R.ok();
    }

    /** 反审核：生产中且无交货记录时回到待审核（与作废逻辑一致，保护已产生库存/应付的单据） */
    @PutMapping("/{id}/un-audit")
    public R<Void> unAudit(@PathVariable Long id) {
        materialOrderService.unAudit(id);
        return R.ok();
    }

    /** 收货，需指定 factoryId（收货仓库对应工厂）。含子物料库存校验与扣减 */
    @PostMapping("/{id}/receive")
    public R<?> receive(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return R.ok(materialOrderService.receive(id, body));
    }

    /** 退不良，使用订单关联的供应商作为工厂。支持处理方式：维修退货(扣库存)/折现退款(仅记录) */
    @PostMapping("/{id}/return-defect")
    public R<?> returnDefect(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return R.ok(materialOrderService.returnDefect(id, body));
    }

    /**
     * 新增退货（2026-09-29 用户口径）：把该订单**已收**的物料退回物料商。
     *
     * <p>只落一张 {@code RECEIVE_RETURN} **草稿**（不动库存/数量/账）—— 与收货同口径，须在「收货记录」里
     * 点「审核」才：① 扣退货仓库存（{@code MATERIAL_RETURN_OUT}）② 冲减该订单已收数量
     * ③ 生成负应付（不再欠物料商这批货的钱）；「反审核」原路回滚（见 DeliveryService）。</p>
     *
     * <p>入参：{@code warehouseId}（退货仓库，前端默认带该收货记录的入库仓）、
     * {@code items:[{itemId, quantity}]}。</p>
     */
    @PostMapping("/{id}/return")
    public R<?> returnMaterial(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return R.ok(materialOrderService.returnMaterial(id, body));
    }

    /** 结单：直接标记为已完成 */
    @PutMapping("/{id}/finish")
    public R<Void> finish(@PathVariable Long id) {
        materialOrderService.finish(id);
        return R.ok();
    }

    /**
     * 反结单（2026-09-27 用户口径「E 也要做」）：
     * 已结单 → 生产中（曾被审核或已收过货）/ 待审核（两者皆无），并清空结单时间。
     * <p>与 {@code /finish} 对称；纯状态回退、无账务副作用。</p>
     */
    @PutMapping("/{id}/reopen")
    public R<Void> reopen(@PathVariable Long id) {
        materialOrderService.reopen(id);
        return R.ok();
    }

    @PutMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id) {
        materialOrderService.cancel(id);
        return R.ok();
    }

    /** 查询该物料订单的物料发到了哪些委外仓库（用于退不良选择） */
    @GetMapping("/{id}/defect-warehouses")
    public R<List<Map<String, Object>>> defectWarehouses(@PathVariable Long id) {
        return R.ok(materialOrderService.defectWarehouses(id));
    }

    /** 收货/退不良草稿单审核：扣/增库存、生成应付、回写订单明细与状态（纯委托 DeliveryService） */
    @PutMapping("/delivery/{id}/audit")
    public R<Void> auditDelivery(@PathVariable Long id) {
        deliveryService.auditMaterialDelivery(id);
        return R.ok();
    }

    /** 收货/退不良已审核单反审核：逆向回滚库存、冲回应付、回退订单明细与状态（纯委托 DeliveryService） */
    // E1 口径（2026-09-12）：反审核统一 /un-audit，旧路径 /unaudit 保留为别名
    @PutMapping({"/delivery/{id}/un-audit", "/delivery/{id}/unaudit"})
    public R<Void> unauditDelivery(@PathVariable Long id) {
        deliveryService.unauditMaterialDelivery(id);
        return R.ok();
    }

    /** 查询该订单的交货记录 */
    @GetMapping("/{id}/deliveries")
    public R<List<Map<String, Object>>> deliveries(@PathVariable Long id) {
        return R.ok(materialOrderService.deliveries(id));
    }

    @DeleteMapping("/{id}/attach")
    public R<Void> deleteAttach(@PathVariable Long id) {
        materialOrderService.deleteAttach(id);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        materialOrderService.delete(id);
        return R.ok();
    }

    // ===== 请求解析 =====

    private MaterialOrder parseOrder(Map<String, Object> body) {
        MaterialOrder o = new MaterialOrder();
        Object sid = body.get("supplierId");
        if (sid != null && !sid.toString().isBlank()) o.setSupplierId(Long.valueOf(sid.toString()));
        if (body.get("orderType") != null && !body.get("orderType").toString().isBlank())
            o.setOrderType(body.get("orderType").toString());
        Object twid = body.get("targetWarehouseId");
        if (twid != null && !twid.toString().isBlank()) o.setTargetWarehouseId(Long.valueOf(twid.toString()));
        Object dd = body.get("deliveryDate");
        if (dd != null && !dd.toString().isBlank()) o.setDeliveryDate(LocalDate.parse(dd.toString()));
        if (body.get("remark") != null) o.setRemark(body.get("remark").toString());
        if (body.get("attachUrl") != null) o.setAttachUrl(body.get("attachUrl").toString());
        // 2026-10-08（用户口径：物料订单与加工单一致）——是否含税 / 税率。
        // 兼容前端 el-switch 可能传来的 boolean（true/false）与数字（1/0）两种形态。
        Object ti = body.get("taxIncluded");
        if (ti != null) {
            String s = ti.toString().trim();
            if ("true".equalsIgnoreCase(s)) o.setTaxIncluded(1);
            else if ("false".equalsIgnoreCase(s)) o.setTaxIncluded(0);
            else if (!s.isEmpty()) o.setTaxIncluded(Integer.valueOf(s));
        }
        Object tr = body.get("taxRate");
        if (tr != null && !tr.toString().isBlank()) {
            try { o.setTaxRate(new BigDecimal(tr.toString().trim())); } catch (NumberFormatException ignore) { /* 非法税率按未填处理 */ }
        }
        // 2026-10-09 结算方式（现金/账期）。**必须显式映射**：本方法是手写白名单，不列出的字段会被静默丢弃
        // ——成品采购单那边刚被守卫抓到过同一个坑（字段丢了之后服务层只看得到 null，报错还像"校验失败"）。
        if (body.get("settleType") != null && !body.get("settleType").toString().isBlank())
            o.setSettleType(body.get("settleType").toString());
        Object said = body.get("settleAccountId");
        if (said != null && !said.toString().isBlank()) o.setSettleAccountId(Long.valueOf(said.toString()));
        Object samt = body.get("settleAmount");
        if (samt != null && !samt.toString().isBlank()) o.setSettleAmount(new BigDecimal(samt.toString().trim()));
        return o;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseItems(Map<String, Object> body) {
        Object obj = body.get("items");
        return obj instanceof List ? (List<Map<String, Object>>) obj : null;
    }
}
