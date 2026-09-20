package com.beichen.erp.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.beichen.erp.customer.entity.Customer;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 客户 Mapper。
 * <p>2026-09-15：原 `sumReceivableBalance()`（批量汇总客户应收余额，供列表/详情展示）已随用户要求
 * 「客户管理列表与详情页都不再显示应收余额」一并删除 —— 该字段已无任何消费方。</p>
 */
public interface CustomerMapper extends BaseMapper<Customer> {

    /**
     * F7-139（2026-09-20）：**行锁**读取客户（`FOR UPDATE`），供"按往来单位串行化"的场景使用 ——
     * 目前用于账单生成（{@code FinanceBillServiceImpl.generate}：查重键含 customerId）。
     *
     * <p>与 `SupplierMapper.selectForUpdate` 的区别：**带租户条件**，一次调用同时完成"加锁 + 租户校验"，
     * 避免裸 `FOR UPDATE` 绕过 mybatis-plus 的租户过滤而锁到/读到别家客户。</p>
     *
     * @param companyId 为 null 时不加租户条件（超管/无上下文场景，与项目其它 `buildWrapper` 口径一致）
     */
    @Select("<script>SELECT * FROM customer WHERE id = #{id}"
            + "<if test='companyId != null'> AND company_id = #{companyId}</if> FOR UPDATE</script>")
    Customer selectForUpdate(@Param("id") Long id, @Param("companyId") Long companyId);
}
