package com.beichen.erp.outsource.service;

import java.math.BigDecimal;

/**
 * 委外**物料单价**的唯一实现（F7-77，2026-09-20）。
 *
 * <p>背景：同一物料单价原先有 4 份实现（1 份加权 + 3 份几乎复制粘贴的 FIFO），差异三类——
 * ① 订单范围：只有加权那处排除作废单，三处 FIFO **都不过滤状态** ⇒ 作废单单价污染计价；
 * ② 回退链：三处 FIFO 各不相同（无兜底返回 0 / 成本价优先 / 参考价兜底）；
 * ③ 取数：四处都是"逐订单查明细"（N+1）。</p>
 *
 * <p>本服务把上述差异**参数化**并固定两条公共口径：<b>参与计价的订单一律排除 CANCELLED</b>
 * （已完工 FINISHED 保留 —— 其收货真实发生），且**候选明细一次批量取回**（1 次订单查询 + 1 次明细查询）。</p>
 */
public interface OutsourceMaterialPricingService {

    /**
     * 加权平均价 = Σ(订购量 × 单价) ÷ Σ订购量（4 位）。
     * <p>先按工厂（{@code supplierId}）取，取不到时**回退该物料的全部订单**（保证新增单据有默认单价）。</p>
     *
     * @param supplierId 工厂/供应商ID（可为 null = 不限）
     * @return 加权均价；无有效数量时返回 0
     */
    BigDecimal weightedPrice(Long supplierId, Long materialId);

    /**
     * 先进先出价：按订单交期升序累计到 {@code requiredQty} 的加权均价（4 位）。
     * <p>无有效数量时返回 0（与 {@code CloseReportServiceImpl} / {@code OutsourceMaterialReturnServiceImpl} 原口径一致）。</p>
     */
    BigDecimal fifoPrice(Long materialId, BigDecimal requiredQty);

    /**
     * 先进先出价（**退货计价口径**）：成本价 → FIFO → 物料参考价 → 0 的四级链。
     * <p>成本价优先是为了覆盖"委外其他出入库/结算退料"等非物料订单来源；参考价是最末兜底。</p>
     */
    BigDecimal fifoPriceWithFallback(Long materialId, BigDecimal requiredQty);
}
