package com.beichen.erp.outsource.controller;

import com.beichen.erp.common.R;
import com.beichen.erp.outsource.entity.OutsourceOrderDelivery;
import com.beichen.erp.outsource.service.OutsourceOrderDeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 加工单交货记录接口层
 * <p>业务逻辑全部下沉至 {@link OutsourceOrderDeliveryService}，此处仅做请求转发与结果包装。</p>
 */
@RestController
@RequestMapping("/api/outsource/order-delivery")
@RequiredArgsConstructor
public class OrderDeliveryController {

    private final OutsourceOrderDeliveryService deliveryService;

    /** 获取某加工单的所有交货记录 */
    @GetMapping("/list/{orderId}")
    public R<List<OutsourceOrderDelivery>> listByOrder(@PathVariable Long orderId) {
        return R.ok(deliveryService.listByOrder(orderId));
    }

    /** 获取交货汇总 */
    @GetMapping("/summary/{orderId}")
    public R<Map<String, Object>> summary(@PathVariable Long orderId) {
        return R.ok(deliveryService.summary(orderId));
    }

    /**
     * 待交货订单列表（「成品收货」菜单页）：只返回正在加工（PRODUCING）的加工单，
     * 每行带订单量/已交量/剩余量/最近交货日期，前端直接渲染列表即可。
     */
    @GetMapping("/order-page")
    public R<Map<String, Object>> producingOrders(@RequestParam(defaultValue = "1") Integer page,
                                                  @RequestParam(defaultValue = "10") Integer size,
                                                  @RequestParam(required = false) String code) {
        return R.ok(deliveryService.pageProducingOrders(page, size, code));
    }

    /** 新增交货记录 */
    @PostMapping
    public R<Map<String, Object>> create(@RequestBody Map<String, Object> body,
                                         @RequestParam(defaultValue = "false") boolean forceDelivery) {
        return R.ok(deliveryService.createDelivery(parseDelivery(body), forceDelivery));
    }

    /** 审核交货记录 */
    @PutMapping("/{id}/audit")
    public R<Void> audit(@PathVariable Long id) {
        deliveryService.audit(id);
        return R.ok();
    }

    /** 反审核交货记录 */
    // E1 口径（2026-09-12）：反审核统一 /un-audit，旧路径 /unaudit 保留为别名
    @PutMapping({"/{id}/un-audit", "/{id}/unaudit"})
    public R<Void> unaudit(@PathVariable Long id) {
        deliveryService.unaudit(id);
        return R.ok();
    }

    /** 修改交货记录 */
    @PutMapping("/{id}")
    public R<Map<String, Object>> update(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                         @RequestParam(defaultValue = "false") boolean forceDelivery) {
        return R.ok(deliveryService.updateDelivery(id, parseDelivery(body), forceDelivery));
    }

    /** 删除交货记录 */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        deliveryService.deleteDelivery(id);
        return R.ok();
    }

    /** 退不良 */
    @PostMapping("/return-defect/{orderId}")
    public R<Void> returnDefect(@PathVariable Long orderId, @RequestBody Map<String, Object> body) {
        deliveryService.returnDefect(orderId, body);
        return R.ok();
    }

    /**
     * F7-128（2026-09-20）：**白名单解析请求体**。
     *
     * <p>原实现用 `@RequestBody OutsourceOrderDelivery` **整实体直绑** ⇒ 前端可顺带提交
     * `id` / `status` / `companyId` / `createTime` / `sourceType` 等**非表单字段**（mass assignment）。
     * 现改为与同模块其它控制器（`OutsourceOrderController.parseOrder`、`DeliveryController.parseDelivery`、
     * `MaterialOrderController.parseOrder`）**一致的白名单口径**，只接收业务字段。</p>
     *
     * <p>字段清单与前端 `order/delivery.vue` 的提交体一一对应（`productId` / 四等级数量 / `quantity` /
     * `warehouseId` / `deliveryDate` / `trackingNo` / `remark` / `attachUrl` / `orderId`），
     * 并保留 `qualityType` / `sourceType` / `contact` / `phone` / `isReverse` 供历史调用方兼容；
     * `status`（服务端强制草稿）、`productMasterId`（服务端按产品解析）、`companyId`（租户填充）、
     * `createTime`（DB 默认）**一律不接收**。</p>
     */
    private OutsourceOrderDelivery parseDelivery(Map<String, Object> body) {
        OutsourceOrderDelivery d = new OutsourceOrderDelivery();
        if (body == null) return d;
        if (body.get("orderId") != null) d.setOrderId(Long.valueOf(body.get("orderId").toString()));
        if (body.get("productId") != null) d.setProductId(Long.valueOf(body.get("productId").toString()));
        if (body.get("warehouseId") != null && !body.get("warehouseId").toString().isBlank())
            d.setWarehouseId(Long.valueOf(body.get("warehouseId").toString()));
        if (body.get("deliveryDate") != null && !body.get("deliveryDate").toString().isBlank())
            d.setDeliveryDate(LocalDate.parse(body.get("deliveryDate").toString()));
        if (body.get("quantity") != null && !body.get("quantity").toString().isBlank())
            d.setQuantity(new BigDecimal(body.get("quantity").toString()));
        // 四等级数量（兼容下划线键）
        d.setAQty(num(body, "aQty", "a_qty"));
        d.setBQty(num(body, "bQty", "b_qty"));
        d.setCQty(num(body, "cQty", "c_qty"));
        d.setDefectQty(num(body, "defectQty", "defect_qty"));
        d.setQualityType((String) body.get("qualityType"));
        d.setSourceType((String) body.get("sourceType"));
        d.setTrackingNo((String) body.get("trackingNo"));
        d.setRemark((String) body.get("remark"));
        d.setAttachUrl((String) body.get("attachUrl"));
        // 注：`OutsourceOrderDelivery` 无 contact/phone 字段（联系方式在 `outsource_delivery` 上），故不接收
        if (body.get("isReverse") != null && !body.get("isReverse").toString().isBlank()) {
            String s = body.get("isReverse").toString().trim();
            d.setIsReverse("1".equals(s) || "true".equalsIgnoreCase(s));   // 实体该字段是 Boolean
        }
        return d;
    }

    /** 取数值字段（驼峰优先，兼容下划线），缺省返回 null */
    private BigDecimal num(Map<String, Object> body, String camel, String snake) {
        Object v = body.get(camel) != null ? body.get(camel) : body.get(snake);
        if (v == null || v.toString().isBlank()) return null;
        return new BigDecimal(v.toString());
    }
}
