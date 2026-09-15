package com.beichen.erp.finance.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.finance.entity.PayableTransfer;

import java.util.List;
import java.util.Map;

/**
 * 应付转应收单 Service
 * <p>
 * 把「负向应付」（退货/超损扣款，正常在付款时净额抵扣）转为「向供应商收款」的应收，
 * 用于当月没有货款可抵、需要对方付款的场景。流程：草稿 → 审核 → 反审核 / 作废。
 * </p>
 */
public interface PayableTransferService {

    /** 分页查询 */
    Page<Map<String, Object>> page(String code, Long supplierId, String supplierType, String status,
                                   String startDate, String endDate, int pageNum, int pageSize);

    PayableTransfer getById(Long id);

    /**
     * 可转应收的应付列表：金额负数（退货/扣款冲减项）且未转出、未结清。
     * 供新增页下拉选择，避免用户选到不能转的记录。
     */
    List<Map<String, Object>> transferablePayables(Long supplierId, String keyword);

    void create(PayableTransfer transfer);

    void update(PayableTransfer transfer);

    /** 审核：生成供应商应收台账，并把来源应付标记为已转（付款抵扣时跳过） */
    void audit(Long id);

    /** 反审核：冲销应收台账，恢复来源应付的抵扣资格 */
    void unAudit(Long id);

    /** 作废（仅草稿） */
    void cancel(Long id);
}
