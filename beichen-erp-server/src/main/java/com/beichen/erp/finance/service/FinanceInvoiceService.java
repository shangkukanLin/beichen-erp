package com.beichen.erp.finance.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.finance.entity.FinanceInvoice;

import java.time.LocalDate;

/** 发票登记 Service：销项/进项发票登记（税务口径），登记即生效，作废仅标记。 */
public interface FinanceInvoiceService {

    Page<FinanceInvoice> page(String direction, String status, String keyword,
                              LocalDate start, LocalDate end, int pageNum, int pageSize);

    void create(FinanceInvoice invoice);

    void update(FinanceInvoice invoice);

    /** 作废（已登记 → 已作废，仅标记不删除） */
    void cancel(Long id);
}
