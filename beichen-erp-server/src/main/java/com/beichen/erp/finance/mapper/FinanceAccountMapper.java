package com.beichen.erp.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.beichen.erp.finance.entity.FinanceAccount;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface FinanceAccountMapper extends BaseMapper<FinanceAccount> {

    /**
     * 批量汇总资金账户余额：余额 = Σ(income - expense)
     * 期初余额已作为一笔「期初」流水（income=期初）计入，故无需再叠加 opening_balance 字段，
     * 否则会重复计算期初余额。采用 LEFT JOIN + GROUP BY 一次性算完，配合 account_id 索引，避免逐账户 N+1。
     * @param accountIds 账户ID集合（非空）
     * @return accountId -> 实时余额
     */
    @Select("<script>" +
            "SELECT a.id AS account_id, " +
            "       IFNULL(SUM(cf.income - cf.expense), 0) AS balance " +
            "FROM finance_account a " +
            "LEFT JOIN finance_cashflow cf ON cf.account_id = a.id " +
            "WHERE a.id IN " +
            "<foreach collection='accountIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "GROUP BY a.id" +
            "</script>")
    @MapKey("account_id")
    Map<Long, Map<String, Object>> sumBalance(@Param("accountIds") List<Long> accountIds);
}
