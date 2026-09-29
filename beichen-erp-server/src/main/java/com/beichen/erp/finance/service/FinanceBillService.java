package com.beichen.erp.finance.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.finance.entity.FinanceBill;
import com.beichen.erp.finance.entity.FinanceBillItem;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface FinanceBillService {
    Page<Map<String,Object>> page(String billType, Long partnerId, int pageNum, int pageSize);
    FinanceBill getById(Long id);
    List<FinanceBillItem> getItems(Long billId);
    /**
     * 生成账单（草稿）。
     *
     * <p><b>F7-244（2026-09-29 批 D）</b>：{@code partnerName} 入参**仅作兼容保留**，落库抬头一律按
     * {@code partnerId} 回查主数据（客户/供应商表），不信任客户端提交值（自动任务本就是回查口径）。</p>
     *
     * <p><b>D-19（2026-09-29）</b>：{@code source} 记录出账来源（{@code MANUAL} 手工 / {@code AUTO} 自动任务），
     * 供事后追溯"这张单是谁出的"。</p>
     */
    FinanceBill generate(String billType, Long partnerId, String partnerName, LocalDate periodStart,
                         LocalDate periodEnd, String source);
    void audit(Long id);
    void unAudit(Long id);
    void cancel(Long id);

    /** 自动账单生成指令：由 autoGeneratePlan 计算（只查不写），逐条交给 generate 执行（各自独立事务） */
    record AutoBillCommand(String billType, Long partnerId, String partnerName, LocalDate periodStart, LocalDate periodEnd) {}

    /**
     * 计算当前公司「到期（dueDate ≤ today）未结清且未被未作废账单覆盖」的自动账单计划。
     * 只做查询与防重判定，不写库；调用方逐条执行 {@link #generate}。
     */
    List<AutoBillCommand> autoGeneratePlan(LocalDate today);
}
