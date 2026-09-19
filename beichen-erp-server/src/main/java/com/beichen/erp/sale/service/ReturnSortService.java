package com.beichen.erp.sale.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.sale.entity.ReturnSort;
import com.beichen.erp.sale.entity.ReturnSortItem;

import java.util.List;
import java.util.Map;

/**
 * 退货整理（销售退货分选入库）
 */
public interface ReturnSortService {

    Page<Map<String, Object>> page(String status, Long warehouseId, int pageNum, int pageSize);

    ReturnSort getById(Long id);

    List<ReturnSortItem> getItems(Long sortId);

    /** 售后仓不良品库存清单（新增时带出，按产品聚合） */
    List<Map<String, Object>> defectStock(Long warehouseId);

    /**
     * 待整理总览（2026-09-19 退货整理页优化）：跨**自有成品仓**列出待整理批次与其账实差额。
     *
     * <p>每行 {@code status} 三态：{@code SORTABLE} 可整理 · {@code SHORTAGE} 实物不足（账实不符，需盘库）·
     * {@code CLEARED} 已整理完（仅 {@code includeCleared=true} 时返回）。</p>
     *
     * <p>返回 {@code { asOf, stayAlertDays, warehouses:[{warehouseId, warehouseName, rows, ...汇总}], summary }}。</p>
     */
    Map<String, Object> pendingOverview(boolean includeCleared);

    /**
     * 批量生成整理草稿：勾选多个来源批次，按 (仓库, 客户) 分组各生成一张草稿。
     *
     * <p>分选数量按 {@code defaultQuality} 整批预置（默认 A 规），用户可再进编辑页微调。
     * 可整理量走与总览**同一套 FIFO 分配**，保证"总览显示多少、批量就生成多少"。</p>
     *
     * <p>{@code template} 只取 sortDate / 4 个目标仓 / 折损 / 备注；{@code warehouseId} 必须为空（由批次决定）。</p>
     */
    Map<String, Object> batchCreateDrafts(List<Long> pendingIds, ReturnSort template, String defaultQuality);

    void create(ReturnSort s, List<ReturnSortItem> items);

    void update(ReturnSort s, List<ReturnSortItem> items);

    /** 审核：售后仓扣 DEFECT，A/B/C/不良 分别入目标仓库 */
    void audit(Long id);

    /**
     * 反审核：逆向回滚（canonical 命名 · 2026-09-19 F7-51）。
     *
     * <p>本方法原名 {@code cancel}，与其余模块的"作废（DRAFT→CANCELLED）"语义**相反**（这里是 AUDITED→DRAFT），
     * 同名不同义易误用；现按统一口径改名为 {@code unAudit}，原方法保留为 {@code @Deprecated} 兼容别名
     * （行为不变，Controller 的 {@code /un-audit} 与 {@code /cancel} 两个 URL 都仍可用）。</p>
     */
    void unAudit(Long id);

    /** @deprecated 反审核语义，请改用 {@link #unAudit(Long)}（F7-51） */
    @Deprecated
    void cancel(Long id);

    void delete(Long id);
}
