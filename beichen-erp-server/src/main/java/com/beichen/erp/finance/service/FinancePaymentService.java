package com.beichen.erp.finance.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.finance.entity.FinancePayment;
import com.beichen.erp.finance.entity.FinancePaymentItem;

import java.util.List;
import java.util.Map;

public interface FinancePaymentService {
    Page<Map<String,Object>> page(Long supplierId, String status, int pageNum, int pageSize);
    FinancePayment getById(Long id);
    List<FinancePaymentItem> getItems(Long paymentId);
    void create(FinancePayment payment, List<FinancePaymentItem> items);
    void cancel(Long id);
    void audit(Long id);
    void updateAttach(FinancePayment payment);
}
