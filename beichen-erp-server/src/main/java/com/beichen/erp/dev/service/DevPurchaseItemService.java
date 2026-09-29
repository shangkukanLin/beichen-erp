package com.beichen.erp.dev.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.beichen.erp.common.PageParam;
import com.beichen.erp.dev.entity.DevPurchaseItem;

import java.util.List;
import java.util.Map;

public interface DevPurchaseItemService extends IService<DevPurchaseItem> {

    /**
     * 研发物料全局分页查询
     * @param name 物料名称模糊（可空）
     * @param projectId 研发项目ID过滤（可空，为空则查询全部含未关联项目）
     * @param type 物料类型过滤（可空，中文标签，如"基板"）
     */
    IPage<Map<String, Object>> pageMaterial(PageParam pageParam, String name, Long projectId, String type);

    /**
     * 查询当前公司下所有启用的仓库（含自有仓库与委外仓库），供研发物料「位置」下拉使用
     * 返回 Map 列表，每项含：value(placeType:placeId)、placeId、placeType、placeName、groupLabel
     */
    List<Map<String, Object>> warehouseOptions();

    /**
     * 查询某研发项目下的物料列表，返回实体列表（warehouseName/warehouseAddress 已实时回填）
     */
    List<DevPurchaseItem> listByProject(Long projectId);

    /**
     * 查询单条研发物料详情（当前位置已实时回填）
     */
    DevPurchaseItem getDetail(Long id);

    /**
     * 新增研发物料（F7-101：名称必填、数量/金额非负；companyId 仅在有效租户上下文时写入）
     */
    DevPurchaseItem addItem(DevPurchaseItem item);

    /**
     * 修改研发物料（F7-101：白名单字段更新，companyId 不落库）
     */
    void updateItem(DevPurchaseItem item);

    /**
     * 删除研发物料（F7-101：级联清理其位置流转记录，避免 dev_material_flow 出现孤儿行）
     */
    void deleteItem(Long id);

    /**
     * 为**研发物料**登记一笔「研发支出」费用单（2026-09-28 用户口径：该功能属研发管理的研发物料）。
     *
     * <p>2026-09-27 起该功能挂在「物料信息管理」页（`/api/outsource/material/{id}/rd-expense`），
     * 2026-09-28 按用户口径**移到研发物料** —— 研发物料（`dev_purchase_item`）与委外物料
     * （`outsource_material`）是两套 id ⇒ 来源类型另用 {@code RD_DEV_MATERIAL}（幂等不串号）。</p>
     *
     * <p>登记口径（幂等 / 金额与账户校验 / 自动审核与权限降级闸门）全部走
     * {@link com.beichen.erp.finance.service.RdExpenseService}，本方法只负责"确认对象存在 + 给默认备注"。</p>
     *
     * @param id   研发物料 ID（须存在）
     * @param body {@code amount} 必填 &gt;0；{@code accountId} 必填；{@code expenseDate}（空=今天）；
     *             {@code remark}（空="研发支出：物料名"）；{@code autoAudit}（true=建单即审核并扣款）
     * @return {@code {expenseId, expenseNo, existing, audited, downgraded?}}
     */
    Map<String, Object> createRdExpense(Long id, Map<String, Object> body);
}
