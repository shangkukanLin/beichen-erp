package com.beichen.erp.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.beichen.erp.customer.entity.Customer;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

public interface CustomerMapper extends BaseMapper<Customer> {

    /**
     * 批量汇总客户应收余额：按客户ID分组，SUM 未结清应收台账的未收金额
     * 采用 LEFT JOIN + GROUP BY 一次性算完，配合 idx_customer_id 索引，避免逐客户 N+1 查询
     * <p><b>2026-09-14 修复</b>：原条件为 {@code r.status != '已结清'}（中文字面量），
     * 而该列存的是 code（UNSETTLED/PARTIAL/SETTLED/CANCELLED…），条件恒为真等于没过滤；
     * 现改为按调用方传入的 code 集合排除，口径与 {@code FinanceReceivableController.unpaid} 一致。</p>
     * @param customerIds 客户ID集合（非空）
     * @param excludeStatuses 不计入余额的台账状态 code（应传 SETTLED / CANCELLED）
     * @return customerId -> 应收余额
     */
    @Select("<script>" +
            "SELECT c.id AS customer_id, IFNULL(SUM(r.unpaid_amount), 0) AS balance " +
            "FROM customer c " +
            "LEFT JOIN finance_receivable r ON r.customer_id = c.id " +
            "  AND r.status NOT IN " +
            "  <foreach collection='excludeStatuses' item='st' open='(' separator=',' close=')'>#{st}</foreach> " +
            "WHERE c.id IN " +
            "<foreach collection='customerIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "GROUP BY c.id" +
            "</script>")
    @MapKey("customer_id")
    Map<Long, Map<String, Object>> sumReceivableBalance(@Param("customerIds") java.util.List<Long> customerIds,
                                                        @Param("excludeStatuses") java.util.List<String> excludeStatuses);
}
