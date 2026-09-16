package com.beichen.erp.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.beichen.erp.customer.entity.Customer;

/**
 * 客户 Mapper。
 * <p>2026-09-15：原 `sumReceivableBalance()`（批量汇总客户应收余额，供列表/详情展示）已随用户要求
 * 「客户管理列表与详情页都不再显示应收余额」一并删除 —— 该字段已无任何消费方。</p>
 */
public interface CustomerMapper extends BaseMapper<Customer> {
}
