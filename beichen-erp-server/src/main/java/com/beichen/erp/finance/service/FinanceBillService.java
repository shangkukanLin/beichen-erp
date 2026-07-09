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
    FinanceBill generate(String billType, Long partnerId, String partnerName, LocalDate periodStart, LocalDate periodEnd);
}
