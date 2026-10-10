package com.beichen.erp.supplier.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.beichen.erp.supplier.entity.Supplier;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface SupplierMapper extends BaseMapper<Supplier> {

    /**
     * 行锁查询供应商（用于清算等**既有**调用方），配合 FOR UPDATE 使用。
     *
     * <p>⚠️ 本方法**不带租户条件**（裸 `FOR UPDATE` ⇒ 会绕过 mybatis-plus 的租户过滤）。
     * 需要"加锁 + 租户校验"一次完成的新调用方，请用
     * {@link #selectForUpdateWithCompany(Long, Long)}。</p>
     */
    @Select("SELECT * FROM supplier WHERE id = #{id} FOR UPDATE")
    Supplier selectForUpdate(@Param("id") Long id);

    /**
     * F7-139（2026-09-20）：**带租户条件**的行锁（单参版本保留给清算等既有调用方，避免连带改动）。
     * 裸 `FOR UPDATE` 不带 `company_id` 会绕过 mybatis-plus 的租户过滤，新调用方请优先用本方法
     * （如账单生成的"往来单位串行化"）。
     *
     * <p><b>⚠️ 2026-10-10 改名（原名与上面单参版本**同名重载** {@code selectForUpdate}）：</b>
     * <b>MyBatis 的 Mapper 接口不支持同名重载</b> —— statement id 是 {@code <接口全名>.<方法名>}，
     * 两个同名方法抢同一个 id，只有一个被注册、另一个被**静默忽略**（启动日志一行
     * {@code ERROR ... mapper[...] is ignored}，不中断启动 ⇒ 极难发现）。
     * <b>实测：改名前的 20 天里本方法的 {@code company_id} 条件从未生效</b>，F7-139 形同虚设 ——
     * 因为 {@code verify-fix-f7-139.ps1} 只断言"调用后返回业务错误（供应商不存在）"⇒ 照绿。
     * 防复发：{@code tools/regression/verify-mapper-no-overload.ps1}（静态扫描全部 Mapper 接口的同名方法）。</p>
     */
    @Select("<script>SELECT * FROM supplier WHERE id = #{id}"
            + "<if test='companyId != null'> AND company_id = #{companyId}</if> FOR UPDATE</script>")
    Supplier selectForUpdateWithCompany(@Param("id") Long id, @Param("companyId") Long companyId);

    /**
     * 批量汇总供应商应付余额：按供应商ID分组，SUM 未结清应付台账的未付金额
     * 采用 LEFT JOIN + GROUP BY 一次性算完，配合 idx_supplier_id 索引，避免逐供应商 N+1 查询
     * <p><b>2026-09-14 修复</b>：原条件为 {@code p.status != '已结清'}（中文字面量），
     * 而该列存的是 code（UNSETTLED/PARTIAL/SETTLED/CANCELLED…），条件恒为真等于没过滤；
     * 现改为按调用方传入的 code 集合排除，口径与 {@code FinancePayableController.unpaid} 一致。</p>
     * <p><b>2026-09-19 修复（F7-34）</b>：补上 <code>IFNULL(p.transferred_to_receivable, 0) &lt;&gt; 1</code> ——
     * 已转应收的负数应付是「退货/扣款冲减项」，其债务已在供应商应收侧挂账，
     * 若还计入应付余额就会与应收双算（口径与 {@code PayableQuery.supplierSummary} /
     * {@code SupplierSettlementServiceImpl} / 应付汇总·账龄 保持一致）。
     * 条件写在 LEFT JOIN 的 ON 里，保证「无未结应付的供应商」仍返回 balance=0 行。</p>
     * @param supplierIds 供应商ID集合（非空）
     * @param excludeStatuses 不计入余额的台账状态 code（应传 SETTLED / CANCELLED / **ADVANCE**；
     *                        三者之外正好是 UNSETTLED + PARTIAL，见 {@code SupplierServiceImpl.fillPayableBalance}）
     * @return supplierId -> 应付余额
     */
    @Select("<script>" +
            "SELECT s.id AS supplier_id, IFNULL(SUM(p.unpaid_amount), 0) AS balance " +
            "FROM supplier s " +
            "LEFT JOIN finance_payable p ON p.supplier_id = s.id " +
            "  AND p.status NOT IN " +
            "  <foreach collection='excludeStatuses' item='st' open='(' separator=',' close=')'>#{st}</foreach> " +
            "  AND IFNULL(p.transferred_to_receivable, 0) &lt;&gt; 1 " +
            "WHERE s.id IN " +
            "<foreach collection='supplierIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "GROUP BY s.id" +
            "</script>")
    @MapKey("supplier_id")
    Map<Long, Map<String, Object>> sumPayableBalance(@Param("supplierIds") List<Long> supplierIds,
                                                     @Param("excludeStatuses") List<String> excludeStatuses);
}
