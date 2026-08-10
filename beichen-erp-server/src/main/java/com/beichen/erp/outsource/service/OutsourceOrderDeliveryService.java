package com.beichen.erp.outsource.service;

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

    /** 退不良：校验后存草稿记录（isReverse=true），落账在审核时执行 */
    void returnDefect(Long orderId, Map<String, Object> body);
}
