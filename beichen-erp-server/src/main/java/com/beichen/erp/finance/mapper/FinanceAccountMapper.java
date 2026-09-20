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

    /**
     * F7-140（2026-09-20）：**行锁**读取资金账户（`FOR UPDATE`），用于"余额校验 + 写支出/收入流水"的串行化。
     *
     * <p>余额是 `Σ(流水 income − expense)` 的**派生值**：付款/费用审核时"先读余额校验、再写流水"，
     * 两者不在同一条 SQL 里 ⇒ 并发两笔款会各自读到相同的旧余额、双双通过校验 ⇒ **账户被透支**
     * （原报告 §7 已把这处非原子性记为 P3 残留）。锁住账户行即可让同一账户的入账顺序串行。</p>
     *
     * <p>**带租户条件**，避免裸 `FOR UPDATE` 绕过 mybatis-plus 的租户过滤（口径同其它
     * `selectForUpdate` 方法）。</p>
     */
    @Select("<script>SELECT * FROM finance_account WHERE id = #{id}"
            + "<if test='companyId != null'> AND company_id = #{companyId}</if> FOR UPDATE</script>")
    FinanceAccount selectForUpdate(@Param("id") Long id, @Param("companyId") Long companyId);

    /**
     * F7-140（2026-09-20 补）：与 {@link #sumBalance} **同口径**，但以**当前读**（`FOR UPDATE`）返回单个账户余额，
     * 专供"余额校验 + 写流水"的审核路径。
     *
     * <p><b>为什么必须是当前读</b>：审核事务的**第一条语句**（`selectById` 取单据）就已建立 REPEATABLE READ
     * 读视图；此后即使先取到账户行锁，普通 `SELECT` 仍按**同一旧快照**读余额 ⇒ **并发的第二个事务照样看到旧余额、
     * 照样通过校验**。实测印证：只加账户行锁时，并发审核仍双双通过校验，最后是 `finance_cashflow.uk_flow_no`
     * 的**流水号唯一索引**把第二个撞回去的（**又一次"偶然正确"**）。`FOR UPDATE` 属当前读，会读到已提交的最新流水。</p>
     *
     * @return 单行 {account_id, balance}；账户不存在时返回 null
     */
    @Select("<script>" +
            "SELECT a.id AS account_id, " +
            "       IFNULL(SUM(cf.income - cf.expense), 0) AS balance " +
            "FROM finance_account a " +
            "LEFT JOIN finance_cashflow cf ON cf.account_id = a.id " +
            "WHERE a.id = #{accountId} " +
            "GROUP BY a.id " +
            "FOR UPDATE" +
            "</script>")
    Map<String, Object> sumBalanceForUpdate(@Param("accountId") Long accountId);
}
