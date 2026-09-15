package com.beichen.erp.common;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;

/**
 * 单据状态原子流转（CAS）工具。
 *
 * <p><b>解决的问题</b>：各单据的审核/反审核原本都是「selectById 取单据 → 判断状态 → 扣减/回补库存 → updateById 改状态」，
 * 判断与更新**不在同一条 SQL** 里，也不是原子操作。并发（或用户双击「审核」）时两个请求都会读到旧状态、
 * 都通过校验，于是**库存被重复扣减/重复回补**、台账重复生成（实测：移仓单并发审核 ×2 → 库存被扣两次）。
 *
 * <p><b>修法</b>：在业务动作开始前，用一条条件更新"抢占"状态：
 * <pre>
 * UPDATE 表 SET status = :to WHERE id = :id AND status = :from
 * </pre>
 * 受影响行数 = 1 表示本次请求抢到了流转权，可继续做库存/台账变更；= 0 表示已被其它请求（或状态不符）处理，
 * 直接抛"状态不允许"，从而保证**同一单据的审核/反审核只会真正执行一次**。
 * 方法须在 {@code @Transactional} 方法内调用：后续步骤失败会随事务回滚，状态自动恢复。
 *
 * <p>多租户：走 MyBatis-Plus 的 {@code update}，会自动带上 company_id 过滤条件。
 */
public final class DocStatusGuard {

    private DocStatusGuard() {
    }

    /**
     * 原子状态流转：仅当当前状态等于 {@code from} 时才置为 {@code to}。
     *
     * @param mapper     单据 Mapper（MyBatis-Plus BaseMapper）
     * @param idGetter   主键列，如 {@code InventoryWarehouseMove::getId}
     * @param id         单据主键
     * @param statusGetter 状态列，如 {@code InventoryWarehouseMove::getStatus}
     * @param from       期望的当前状态（如 DocStatus.DRAFT.getCode()）
     * @param to         目标状态（如 DocStatus.AUDITED.getCode()）
     * @return true=抢占成功（本次请求负责执行后续库存/台账变更）；false=状态不符或已被并发请求处理
     */
    public static <T> boolean claim(BaseMapper<T> mapper,
                                    SFunction<T, ?> idGetter, Object id,
                                    SFunction<T, ?> statusGetter, String from, String to) {
        if (id == null) return false;
        LambdaUpdateWrapper<T> w = new LambdaUpdateWrapper<T>()
                .eq(idGetter, id)
                .eq(statusGetter, from)
                .set(statusGetter, to);
        return mapper.update(null, w) > 0;
    }
}
