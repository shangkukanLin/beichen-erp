package com.beichen.erp.outsource.controller;

import com.beichen.erp.common.R;
import com.beichen.erp.outsource.entity.OutsourceOrderDelivery;
import com.beichen.erp.outsource.service.OutsourceOrderDeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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

    /** 新增交货记录 */
    @PostMapping
    public R<Map<String, Object>> create(@RequestBody OutsourceOrderDelivery delivery,
                                         @RequestParam(defaultValue = "false") boolean forceDelivery) {
        return R.ok(deliveryService.createDelivery(delivery, forceDelivery));
    }

    /** 审核交货记录 */
    @PutMapping("/{id}/audit")
    public R<Void> audit(@PathVariable Long id) {
        deliveryService.audit(id);
        return R.ok();
    }

    /** 反审核交货记录 */
    @PutMapping("/{id}/unaudit")
    public R<Void> unaudit(@PathVariable Long id) {
        deliveryService.unaudit(id);
        return R.ok();
    }

    /** 修改交货记录 */
    @PutMapping("/{id}")
    public R<Map<String, Object>> update(@PathVariable Long id, @RequestBody OutsourceOrderDelivery delivery,
                                         @RequestParam(defaultValue = "false") boolean forceDelivery) {
        return R.ok(deliveryService.updateDelivery(id, delivery, forceDelivery));
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
}
