package com.beichen.erp.outsource.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.beichen.erp.outsource.entity.OutsourceOrderDelivery;

import java.util.List;

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
 */
public interface OutsourceOrderDeliveryService extends IService<OutsourceOrderDelivery> {

    /** 审核：草稿态生效，扣减物料/成品入库/生成应付（退不良则为冲销） */
    void audit(Long id);

    /** 反审核：已审核态回滚库存与应付，回到草稿 */
    void unaudit(Long id);

    /** 按加工单查询交货记录 */
    List<OutsourceOrderDelivery> listByOrder(Long orderId);
}
