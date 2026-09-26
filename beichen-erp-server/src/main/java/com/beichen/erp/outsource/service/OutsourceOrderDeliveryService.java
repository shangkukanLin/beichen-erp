package com.beichen.erp.outsource.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.beichen.erp.outsource.entity.OutsourceOrderDelivery;

import java.util.List;
import java.util.Map;

/**
 * 加工单交货记录服务
 * <p>
 * 交货记录引入审核状态机（status 字段复用 DocStatus）：
 * DRAFT=草稿（仅存盘，不扣料/不入库存/不生成应付）、
 * AUDITED=已审核（审核后才扣物料、成品入库、生成应付）、
 * CANCELLED=已作废。
 * 草稿态可编辑/删除且不碰库存；审核后发现填错可调 unaudit 逆向回滚到草稿。
 * 退不良通过 isReverse=true + 负数数量表达。
 * </p>
 * <p>
 * 命名说明：createDelivery/updateDelivery/deleteDelivery 刻意避开 IService
 * 已有的 save/update/removeById 签名，防止方法重载冲突。
 * </p>
 */
public interface OutsourceOrderDeliveryService extends IService<OutsourceOrderDelivery> {

    /** 按加工单查询交货记录（按ID倒序） */
    List<OutsourceOrderDelivery> listByOrder(Long orderId);

    /** 交货汇总：总数量/已交数量/剩余数量/明细统计 */
    Map<String, Object> summary(Long orderId);

    /**
     * 待交货订单列表：正在加工（PRODUCING）的加工单 + 交货进度聚合。
     * <p>供「成品收货」独立菜单页使用，口径与 {@link #summary(Long)} 一致（只统计已审核交货），
     * 一次分页查询即可，避免前端逐单调用 summary 造成 N+1 请求。</p>
     *
     * @param pageNo 页码（从 1 开始）
     * @param size   每页条数
     * @param code   单号模糊筛选（可空）
     */
    Map<String, Object> pageProducingOrders(Integer pageNo, Integer size, String code);

    /**
     * 新增交货记录（草稿态存盘，不落账）
     *
     * @param forceDelivery 缺料时是否强制继续（false 则返回缺料清单不落库）
     */
    Map<String, Object> createDelivery(OutsourceOrderDelivery delivery, boolean forceDelivery);

    /** 审核：草稿态生效，扣减物料/成品入库/生成应付（退不良则为冲销） */
    void audit(Long id);

    /** 反审核：已审核态回滚库存与应付，回到草稿 */
    void unaudit(Long id);

    /** 修改交货记录（仅草稿态可编辑，不触碰库存） */
    Map<String, Object> updateDelivery(Long id, OutsourceOrderDelivery delivery, boolean forceDelivery);

    /** 删除交货记录（仅草稿态可删除） */
    void deleteDelivery(Long id);

    /** 加工退货（成品侧命名，原名「退不良」）：校验后存草稿记录（isReverse=true），落账在审核时执行 */
    void returnDefect(Long orderId, Map<String, Object> body);

    /**
     * **不关联加工单的加工退货**（2026-09-21 用户口径）：与 {@link #returnDefect} 是**同一个动作**，
     * 只是不挂加工单 —— 同样往本表写一条负数记录（`delivery_type=DEFECT_RETURN`、`is_reverse=1`），
     * 同样由 {@link #audit(Long)} 落账（扣成品 + BOM 料还回工厂委外仓 + 冲减应付），
     * 同样由 {@link #unaudit(Long)} 回滚、{@link #deleteDelivery(Long)} 删草稿。
     * <p>无单时：`order_id`/`product_id` 留空，改由 `factory_id` 定位工厂委外仓与应付对象；
     * 还料依据 = 该产品的**最新 BOM 快照**（无快照回退 dev_bom）；冲减金额 = **还回物料的 FIFO 价值**。</p>
     *
     * @param body factoryId / warehouseId / productMasterId / qualityType / quantity / remark
     */
    void returnDefectNoOrder(Map<String, Object> body);

    /**
     * 无单加工退货列表（按ID倒序）。
     * <p>⚠️ **兼容保留**：2026-09-21 起前端改用 {@link #pageDefectReturns} 把有单/无单展示在一张台账；
     * 本方法供既有回归脚本与外部调用继续使用，新代码请用台账端点。</p>
     */
    List<Map<String, Object>> listNoOrderReturns();

    /**
     * **加工退货台账**（2026-09-21 用户口径）：**有单 + 无单都在这张表里** ——
     * 行取自本表 `delivery_type=DEFECT_RETURN` 的记录：有关联加工单的（该单收货详细页发起的红冲收货）
     * 与不关联加工单的（{@link #returnDefectNoOrder}），字段完全同构，前端只用「关联加工单」列区分
     * （有单显示加工单号 / 无单显示"未关联"），审核·反审核·删除沿用通用端点。
     *
     * @param linked ALL（默认，全部）/ WITH_ORDER（只看关联加工单）/ WITHOUT_ORDER（只看无单）
     * @param status 单据状态筛选：单值（DRAFT/AUDITED/CANCELLED）或**逗号分隔多值**
     *               （2026-09-27 三级菜单口径：「有效单据」= DRAFT,AUDITED；「已作废」= CANCELLED），可空
     * @param factoryId 加工厂筛选（2026-09-27：加工返回单绑定来源单时的定位用），可空
     * @param productId 产品筛选（同上），可空
     * @param qualityType 退货规格筛选（同上），可空
     * @param returnProgress 返回进度（2026-09-27）：PENDING=未返回完 / DONE=已返回完（按来源单聚合已审核返回单），可空
     */
    Map<String, Object> pageDefectReturns(Integer pageNo, Integer size, String linked, String status,
                                          Long factoryId, Long productId, String qualityType, String returnProgress);

    /**
     * 加工退货草稿**作废**（2026-09-27 用户口径）：DRAFT → CANCELLED。
     * <p>台账「已作废」页签的数据来源；已审核的撤销仍走 {@code unAudit}（账务等量逆回）。</p>
     */
    void cancelDefectReturn(Long id);

    /**
     * 加工退货**详情**（2026-09-21 用户口径「加工退货页面的列表也应该有详情」）：记录全字段
     * （含记录ID/仓库/建单时间/是否关联加工单）+ **审核后的落账明细** —— 还回工厂委外仓的物料
     * （按库存流水回溯）与冲减的应付金额（`finance_payable`）。草稿未落账时这两项为空。
     *
     * <p>供「加工退货」页台账的行内「详情」抽屉使用（列表只留扫读几列、细节进详情）。</p>
     */
    Map<String, Object> defectReturnDetail(Long id);
}
