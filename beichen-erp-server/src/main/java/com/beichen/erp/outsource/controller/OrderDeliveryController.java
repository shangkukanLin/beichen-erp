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

    /**
     * 2026-09-27（用户口径「加工返回单多余了，和成品维修退货一样在详情里登记返回就行」）：
     * 返回登记的读写**挂在本前缀下**，权限随本页（`outsource:order-delivery`）——
     * 否则只有台账页权限的角色点「登记返回」会被 ApiPermGuard 拦 403。
     */
    private final com.beichen.erp.outsource.service.OutsourceReturnBackService returnBackService;

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
     * **不关联加工单的加工退货**（2026-09-21 用户口径：该入口的本意就是"可以不关联加工单"，
     * 其余业务与有单红冲完全一致）—— 成品收货列表页「无单加工退货」区块的新增入口。
     * <p>草稿仍存本表，审核/反审核/删除沿用通用端点 {@code /{id}/audit}·{@code /{id}/un-audit}·{@code DELETE /{id}}。</p>
     */
    @PostMapping("/return-defect-no-order")
    public R<Void> returnDefectNoOrder(@RequestBody Map<String, Object> body) {
        deliveryService.returnDefectNoOrder(body);
        return R.ok();
    }

    /**
     * 「新增无单加工退货」页的 **BOM 快照候选**（2026-09-27 用户口径）：该产品在该工厂**用过**的快照
     * （按最近使用的加工单倒序 ⇒ 第一项即默认），带版本/来源；解析不到则返回空数组。
     */
    @GetMapping("/product-snapshot-options")
    public R<List<Map<String, Object>>> productSnapshotOptions(@RequestParam(required = false) Long factoryId,
                                                               @RequestParam Long productMasterId) {
        return R.ok(deliveryService.productSnapshotOptions(factoryId, productMasterId));
    }

    /**
     * 无单加工退货列表（**兼容保留**：2026-09-21 起前端已改用下面的台账端点
     * {@link #defectReturnPage} 把有单/无单展示在一张表；本端点供既有回归脚本与外部调用继续使用）。
     */
    @GetMapping("/return-defect-no-order/list")
    public R<List<Map<String, Object>>> listNoOrderReturns() {
        return R.ok(deliveryService.listNoOrderReturns());
    }

    /**
     * **加工退货台账**（2026-09-21 用户口径）：有单 + 无单**一张表**，用「关联加工单」列区分。
     * <p>2026-09-27 三级菜单：本端点被「关联退货」（linked=WITH_ORDER）与「无单退货」（linked=WITHOUT_ORDER）
     * 两个页面共用；`status` 支持逗号分隔多值（有效单据 = DRAFT,AUDITED / 已作废 = CANCELLED），
     * `returnProgress` 支持按来源单聚合的返回进度（PENDING/DONE）筛选。</p>
     */
    @GetMapping("/return-defect/page")
    public R<Map<String, Object>> defectReturnPage(@RequestParam(defaultValue = "1") Integer page,
                                                   @RequestParam(defaultValue = "10") Integer size,
                                                   @RequestParam(required = false) String linked,
                                                   @RequestParam(required = false) String status,
                                                   @RequestParam(required = false) Long factoryId,
                                                   @RequestParam(required = false) Long productId,
                                                   @RequestParam(required = false) String qualityType,
                                                   @RequestParam(required = false) String returnProgress) {
        return R.ok(deliveryService.pageDefectReturns(page, size, linked, status,
                factoryId, productId, qualityType, returnProgress));
    }

    /**
     * **作废加工退货草稿**（2026-09-27 用户口径）：DRAFT → CANCELLED，供台账「已作废」页签。
     * <p>与「反审核」分工：草稿作废（留痕、可查）；已审核单据必须先反审核（账务等量逆回）。
     * 原先页面用的是物理删除（{@code DELETE /{id}}，端点保留供历史脚本调用）。</p>
     */
    @PutMapping("/{id}/cancel")
    public R<Void> cancelDefectReturn(@PathVariable Long id) {
        deliveryService.cancelDefectReturn(id);
        return R.ok();
    }

    /**
     * **加工退货详情**（2026-09-21 用户口径「列表也应该有详情」）：台账行内「详情」抽屉的数据源 ——
     * 记录全字段 + 审核后的落账明细（还回物料 / 冲减应付）。草稿未落账时后两项为空。
     */
    @GetMapping("/return-defect/{id}/detail")
    public R<Map<String, Object>> defectReturnDetail(@PathVariable Long id) {
        return R.ok(deliveryService.defectReturnDetail(id));
    }

    /**
     * 单条收货记录（2026-09-23 用户要求：详情由抽屉改独立页 ⇒ 页面必须能按 id 回源，
     * 原先只有列表接口，抽屉是拿列表已加载的行渲染的）。
     */
    @GetMapping("/{id}")
    public R<OutsourceOrderDelivery> getById(@PathVariable Long id) {
        return R.ok(deliveryService.getById(id));
    }

    // ==================== 加工返回（2026-09-27 用户口径）====================
    // 「加工返回单」不再是独立单据/独立菜单叶子：改成在**无单加工退货详情页**登记返回，
    // 交互与「成品维修退货」详情页的「登记维修返回」完全一致（登记即生效 + 逐条撤销 + 记录列表）。

    /**
     * **登记返回**：核销在厂成品（PRODUCT_DEFECT）+ 修好成品回我方仓 + 按实际用料扣委外仓料
     * + 料款生成对加工厂的**赔料应收** + FIFO 成本结转。登记即生效（无草稿/审核两步）。
     * <p>body: quantity / returnQualityType / inWarehouseId / returnDate / remark / items[]，其余
     * （工厂/产品/在厂规格）由来源单自动带入并复核。</p>
     */
    @PostMapping("/{id}/return-back")
    public R<com.beichen.erp.outsource.entity.OutsourceReturnBack> registerReturnBack(
            @PathVariable Long id, @RequestBody Map<String, Object> body) {
        return R.ok(returnBackService.register(id, body));
    }

    /** 撤销返回登记（库存/应收/成本对称逆回后删除该记录；与"登记维修返回"的逐条撤销同口径） */
    @DeleteMapping("/return-back/{recordId}")
    public R<Void> revokeReturnBack(@PathVariable Long recordId) {
        returnBackService.revoke(recordId);
        return R.ok();
    }

    /** 某无单加工退货单的返回记录（详情页「返回记录」表；已返回量 = Σ quantity） */
    @GetMapping("/{id}/return-backs")
    public R<List<Map<String, Object>>> returnBacks(@PathVariable Long id) {
        return R.ok(returnBackService.listBySource(id));
    }

    /**
     * 「实际用料」候选（按来源单 BOM 快照解析；池空 ⇒ 允许只登记返回、不填用料）—— 详情页登记弹窗用。
     * <p>⚠️ 工厂/产品必须从来源单带出：候选解析的第二档兜底是「该产品在该工厂最近用过的快照」，
     * 传 null 会直接落到空池（实测踩过：提交时报"用料明细不能为空"，而候选端点却返回空）。</p>
     */
    @GetMapping("/{id}/return-back-material-candidates")
    public R<List<Map<String, Object>>> returnBackCandidates(@PathVariable Long id) {
        OutsourceOrderDelivery src = deliveryService.getById(id);
        return R.ok(returnBackService.materialCandidates(id,
                src != null ? src.getFactoryId() : null,
                src != null ? src.getProductMasterId() : null));
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
