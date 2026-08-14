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
     * @param customerIds 客户ID集合（非空）
     * @return customerId -> 应收余额
     */
    @Select("<script>" +
            "SELECT c.id AS customer_id, IFNULL(SUM(r.unpaid_amount), 0) AS balance " +
            "FROM customer c " +
            "LEFT JOIN finance_receivable r ON r.customer_id = c.id AND r.status != '已结清' " +
            "WHERE c.id IN " +
            "<foreach collection='customerIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "GROUP BY c.id" +
            "</script>")
    @MapKey("customer_id")
    Map<Long, Map<String, Object>> sumReceivableBalance(@Param("customerIds") java.util.List<Long> customerIds);
}
