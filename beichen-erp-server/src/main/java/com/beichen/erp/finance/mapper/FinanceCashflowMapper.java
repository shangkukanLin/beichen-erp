package com.beichen.erp.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.beichen.erp.finance.entity.FinanceCashflow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface FinanceCashflowMapper extends BaseMapper<FinanceCashflow> {

    /**
     * F7-238（2026-09-29 决策 D-15）：**按页取余额** —— 每行余额 = 该账户**截至本行（含）**的累计
     * {@code SUM(income - expense)}（与"逐笔累计"语义等价：期初行的 income 即期初额）。
     *
     * <p>只对传入的这几行求聚合值，不再把账户的**全部流水**读进 JVM（账户流水越多越慢、且全量明细占内存）。</p>
     *
     * <p>注意：{@code <} 在 MyBatis {@code <script>} 注解里必须写成 {@code &lt;}，否则会被当标签解析。</p>
     *
     * @param ids 本页流水 id（非空）
     * @return 每行 {@code {id, balance}}
     */
    @Select("<script>" +
            "SELECT c1.id AS id, " +
            "       IFNULL((SELECT SUM(c2.income - c2.expense) FROM finance_cashflow c2 " +
            "               WHERE c2.account_id = c1.account_id AND c2.id &lt;= c1.id), 0) AS balance " +
            "FROM finance_cashflow c1 " +
            "WHERE c1.id IN " +
            "<foreach collection='ids' item='i' open='(' separator=',' close=')'>#{i}</foreach>" +
            "</script>")
    List<Map<String, Object>> sumBalanceByIds(@Param("ids") Collection<Long> ids);
}
