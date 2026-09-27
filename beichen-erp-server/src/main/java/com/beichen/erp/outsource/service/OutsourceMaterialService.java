package com.beichen.erp.outsource.service;

import java.util.List;
import java.util.Map;

/**
 * 委外物料**主数据写路径**（2026-09-20 · F7-70）。
 *
 * <p>原实现把这四个写操作直接放在 {@code OutsourceMaterialController} 里直连 Mapper，
 * 既没有 {@code @Transactional}（物料主表 + 供应商居间表 + 子物料组成是三处写入，中途失败会留半套），
 * 也没有删除前的**引用校验**。本接口把它们下沉到 Service 层，与其它模块的分层约定保持一致。</p>
 */
public interface OutsourceMaterialService {

    /** 新增物料（主表 + supplier_material 居间表，同一事务） */
    Long create(Map<String, Object> body);

    /** 修改物料（主表 + supplier_material 居间表差量，同一事务） */
    void update(Map<String, Object> body);

    /**
     * 删除物料（级联删除其子物料组成，同一事务）。
     * <p>**被引用（库存 / 物料订单明细 / 退货明细 / 收发明细 / 子物料组成）时拒绝删除**，
     * 避免历史单据上的物料与库存记录悬空。</p>
     */
    void delete(Long id);

    /** 保存物料的子物料组成（全量替换，同一事务） */
    void saveComponents(Long materialId, List<Map<String, Object>> items);

    /**
     * 为物料登记一笔「**研发支出**」**草稿**费用单（2026-09-27 用户需求：新增物料时提示"要不要根据物料新增研发支出"）。
     *
     * <p><b>为什么放在物料前缀下</b>（而不是让物料页直接调 {@code /api/finance/expense}）：</p>
     * <ol>
     *   <li><b>权限</b>：费用接口需 {@code finance:expense}（或 finance:cashflow），只被授「物料信息管理」的用户会 403；
     *       走物料前缀则用本页自己的页面码 {@code outsource:material-info} 即可。</li>
     *   <li><b>读隔离</b>：前端无需跨页调财务接口（`audit-frontend-api-crosspage` 不必新增登记）。</li>
     * </ol>
     *
     * <p><b>审核口径（2026-09-27 用户要求「新增物料时勾选，需要自动审核」）</b>：</p>
     * <ul>
     *   <li>{@code autoAudit=true}（新增物料页勾选路径）⇒ 建单后**立即审核**：当场写「费用支出」资金流水、
     *       扣支出账户余额。审核内含账户行锁 + 余额校验，**余额不足会抛错并整体回滚**（不会留下只落草稿的半成品）。</li>
     *   <li>{@code autoAudit=false}（列表行操作「补登记」路径）⇒ 只落**草稿**，由财务在「费用管理」审核（此时钱不动）。</li>
     * </ul>
     *
     * <p><b>幂等</b>：同一物料已有**未作废**的研发支出 ⇒ 直接返回那张单（{@code existing=true}），不新建；
     * 若该单还是草稿且本次 {@code autoAudit=true} ⇒ **补审核**（避免"以为扣了其实没扣"）；已审核的不再动账；
     * 已作废的不算（作废后可重新登记）。物料创建与本次费用登记是**两次请求**（物料已建、费用失败可重试），幂等保证重试不重复扣款。</p>
     *
     * @param materialId 物料 ID（须存在）
     * @param body       {@code amount} 必填 &gt;0；{@code accountId} 必填；{@code expenseDate}（空=今天）；
     *                   {@code remark}（空="研发支出：物料名"）；{@code autoAudit}（true=建单即审核并扣款）
     * @return {@code {expenseId, expenseNo, existing, audited}}
     */
    Map<String, Object> createRdExpense(Long materialId, Map<String, Object> body);
}
