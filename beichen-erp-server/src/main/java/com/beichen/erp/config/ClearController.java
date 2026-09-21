package com.beichen.erp.config;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import com.beichen.erp.common.R;
import com.beichen.erp.common.DefaultMaterialTypes;
import com.beichen.erp.common.DefaultContractTemplate;
import com.beichen.erp.common.DefaultPhaseTemplates;
import com.beichen.erp.system.common.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;

/**
 * 数据清空/系统修复接口：均为高危操作。
 * 全局拦截器只校验登录，角色控制必须在此显式声明（SaTokenConfig 无统一角色鉴权）。
 *
 * <p>【P2-34 口径 · 2026-09-12 定稿】按"是否跨租户"划线：</p>
 * <ul>
 *   <li>{@code POST /api/system/clear-company-data}：**按 {@code CompanyContext} 过滤**清空本公司业务数据
 *       → 租户内自服务，保留给公司管理员（类级注解的 {@code ADMIN} 即为此）；</li>
 *   <li>{@code POST /api/system/clear-data}：**全库清空**（遍历所有表 DELETE，无 company 维度）
 *       → 平台级且不可逆，已收紧为**仅超级管理员**（方法级注解在类级放行之外再收紧）；</li>
 *   <li>原 {@code GET /api/system/check-menu} 是遗留调试端点（读 sys_menu/sys_role_menu），无调用方，
 *       已随本次收口移除。</li>
 * </ul>
 */
@Slf4j
@RestController
@SaCheckRole(value = {SystemConstants.SUPER_ADMIN_ROLE_CODE, SystemConstants.ADMIN_ROLE_CODE}, mode = SaMode.OR)
public class ClearController {

    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbcTemplate;

    /** 清空当前公司所有业务数据（保留系统表） */
    @PostMapping("/api/system/clear-company-data")
    public R<String> clearCompanyData() {
        Long companyId = com.beichen.erp.config.CompanyContext.get();
        if (companyId == null || companyId == 0) return R.fail("超管模式下请先选择公司");
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            Statement stmt = conn.createStatement();
            stmt.execute("SET FOREIGN_KEY_CHECKS = 0");
            // 按外键依赖顺序删除当前公司的业务数据（子表→主表→基础数据）
            String[] sqls = {
                // === 财务明细 ===
                "DELETE FROM finance_payment_item WHERE company_id = " + companyId,
                "DELETE FROM finance_receipt_item WHERE company_id = " + companyId,
                "DELETE FROM finance_bill_item WHERE company_id = " + companyId,
                "DELETE FROM finance_settlement WHERE company_id = " + companyId,
                "DELETE FROM finance_expense WHERE company_id = " + companyId,
                "DELETE FROM finance_invoice WHERE company_id = " + companyId,
                // 应付转应收单（引用 finance_payable，须先于应付主表删除）
                "DELETE FROM finance_payable_transfer WHERE company_id = " + companyId,
                // === 业务明细 ===
                "DELETE FROM purchase_order_item WHERE company_id = " + companyId,
                "DELETE FROM purchase_return_item WHERE company_id = " + companyId,
                "DELETE FROM sale_outbound_item WHERE company_id = " + companyId,
                "DELETE FROM sale_order_item WHERE company_id = " + companyId,
                "DELETE FROM sale_return_item WHERE company_id = " + companyId,
                "DELETE FROM sale_exchange_item WHERE company_id = " + companyId,
                "DELETE FROM return_sort_item WHERE company_id = " + companyId,
                "DELETE FROM inventory_warehouse_move_item WHERE company_id = " + companyId,
                "DELETE FROM inventory_other_io_item WHERE company_id = " + companyId,
                "DELETE FROM inventory_stock_take_item WHERE company_id = " + companyId,
                "DELETE FROM inventory_stock_reclass_item WHERE company_id = " + companyId,
                "DELETE FROM inventory_stock_loss_item WHERE company_id = " + companyId,
                "DELETE FROM outsource_stock_loss_item WHERE company_id = " + companyId,
                "DELETE FROM product_reclassify_item WHERE company_id = " + companyId,
                "DELETE FROM outsource_delivery_item WHERE company_id = " + companyId,
                "DELETE FROM outsource_material_component WHERE company_id = " + companyId,
                "DELETE FROM outsource_material_order_item WHERE company_id = " + companyId,
                "DELETE FROM outsource_material_return_item WHERE company_id = " + companyId,
                // BOM 快照（2026-09-17 重构：原 outsource_order_material 表已成只读视图，明细落在 bom_snapshot_item）
                "DELETE FROM bom_snapshot_item WHERE company_id = " + companyId,
                "DELETE FROM bom_snapshot WHERE company_id = " + companyId,
                "DELETE FROM outsource_order_product WHERE company_id = " + companyId,
                "DELETE FROM outsource_order_delivery WHERE company_id = " + companyId,
                "DELETE FROM outsource_other_io_item WHERE company_id = " + companyId,
                "DELETE FROM outsource_return_order_item WHERE company_id = " + companyId,
                "DELETE FROM outsource_return_order_product WHERE company_id = " + companyId,
                "DELETE FROM outsource_order_close_report_item WHERE report_id IN (SELECT id FROM outsource_order_close_report WHERE company_id = " + companyId + ")",
                // === 财务主表 ===
                "DELETE FROM finance_payment WHERE company_id = " + companyId,
                "DELETE FROM finance_receipt WHERE company_id = " + companyId,
                "DELETE FROM finance_bill WHERE company_id = " + companyId,
                "DELETE FROM finance_cashflow WHERE company_id = " + companyId,
                "DELETE FROM finance_receivable WHERE company_id = " + companyId,
                "DELETE FROM finance_payable WHERE company_id = " + companyId,
                "DELETE FROM finance_account WHERE company_id = " + companyId,
                // === 库存流水（先删流水再删库存） ===
                "DELETE FROM warehouse_stock_log WHERE company_id = " + companyId,
                "DELETE FROM cost_inbound_log WHERE company_id = " + companyId,
                // === 库存主表 ===
                "DELETE FROM warehouse_stock WHERE company_id = " + companyId,
                "DELETE FROM inventory_warehouse_move WHERE company_id = " + companyId,
                "DELETE FROM inventory_other_io WHERE company_id = " + companyId,
                "DELETE FROM inventory_stock_take WHERE company_id = " + companyId,
                "DELETE FROM inventory_stock_reclass WHERE company_id = " + companyId,
                "DELETE FROM inventory_stock_loss WHERE company_id = " + companyId,
                "DELETE FROM product_reclassify WHERE company_id = " + companyId,
                // === 仓库 ===
                "DELETE FROM warehouse WHERE company_id = " + companyId,
                // === 采购主表 ===
                "DELETE FROM purchase_order WHERE company_id = " + companyId,
                "DELETE FROM purchase_return WHERE company_id = " + companyId,
                // === 销售主表 ===
                "DELETE FROM sale_outbound WHERE company_id = " + companyId,
                "DELETE FROM sale_order WHERE company_id = " + companyId,
                "DELETE FROM sale_return WHERE company_id = " + companyId,
                "DELETE FROM sale_exchange WHERE company_id = " + companyId,
                "DELETE FROM return_sort WHERE company_id = " + companyId,
                "DELETE FROM after_sale_pending WHERE company_id = " + companyId,
                // === 委外主表 ===
                "DELETE FROM outsource_other_io WHERE company_id = " + companyId,
                "DELETE FROM outsource_return_order WHERE company_id = " + companyId,
                "DELETE FROM outsource_order_close_report WHERE company_id = " + companyId,
                "DELETE FROM outsource_material_order WHERE company_id = " + companyId,
                "DELETE FROM outsource_material_return WHERE company_id = " + companyId,
                "DELETE FROM outsource_stock_loss WHERE company_id = " + companyId,
                "DELETE FROM outsource_delivery WHERE company_id = " + companyId,
                "DELETE FROM outsource_order WHERE company_id = " + companyId,
                "DELETE FROM outsource_contract_template WHERE company_id = " + companyId,
                // === 委外物料 ===
                "DELETE FROM outsource_material WHERE company_id = " + companyId,
                // === 研发 ===
                "DELETE FROM dev_drawing WHERE company_id = " + companyId,
                "DELETE FROM dev_bug WHERE company_id = " + companyId,
                "DELETE FROM dev_bom WHERE company_id = " + companyId,
                "DELETE FROM material_type WHERE company_id = " + companyId,
                "DELETE FROM dev_purchase_item WHERE company_id = " + companyId,
                "DELETE FROM dev_material_flow WHERE company_id = " + companyId,
                "DELETE FROM dev_phase_template WHERE company_id = " + companyId,
                "DELETE FROM dev_project_phase WHERE company_id = " + companyId,
                "DELETE FROM dev_project WHERE company_id = " + companyId,
                // === 备忘 ===
                "DELETE FROM memo_progress WHERE company_id = " + companyId,
                "DELETE FROM memo WHERE company_id = " + companyId,
                // === 基础数据 ===
                "DELETE FROM supplier_product WHERE company_id = " + companyId,
                "DELETE FROM supplier_material WHERE company_id = " + companyId,
                "DELETE FROM supplier_type_ref WHERE company_id = " + companyId,
                "DELETE FROM supplier WHERE company_id = " + companyId,
                "DELETE FROM product WHERE company_id = " + companyId,
                "DELETE FROM customer WHERE company_id = " + companyId,
                "DELETE FROM brand WHERE company_id = " + companyId,
                // 注意：屏幕资料知识库（screen_model）不在此清单中——它是行业基础资料，
                // 清空公司业务数据时保留，避免辛苦录入的机型屏幕参数被误删。
            };
            for (String s : sqls) { stmt.execute(s); }
                // 重新初始化物料类型默认数据（统一用 DefaultMaterialTypes，避免与 DataInitializer 不一致）
            String[] defaultTypes = DefaultMaterialTypes.TYPES;
            for (int i = 0; i < defaultTypes.length; i++) {
                stmt.execute("INSERT INTO material_type (type_name, sort_order, status, is_default, company_id) VALUES ('" + defaultTypes[i] + "', " + (i + 1) + ", 1, 1, " + companyId + ")");
            }
            // 重新初始化阶段模板默认数据（2026-09-21：**原配 / 改配各一套**，统一取 DefaultPhaseTemplates，
            // 与 DataInitializer 共用一份数据，避免两处漂移；并补上原先漏写的 spec_type 与
            // product_status_sync —— 漏后者会导致"清空数据"后小批量/结项不再触发产品状态同步）
            int tplCount = 0;
            tplCount += insertPhaseTemplates(stmt, DefaultPhaseTemplates.MODIFIED, DefaultPhaseTemplates.SPEC_MODIFIED, companyId);
            tplCount += insertPhaseTemplates(stmt, DefaultPhaseTemplates.MATCHED, DefaultPhaseTemplates.SPEC_MATCHED, companyId);
            log.info("重置阶段模板默认数据 {} 条（原配/改配各一套）", tplCount);
            // 重新初始化默认合同模板（加工合同、采购合同），与 物料类型/阶段模板一致
            insertContractTemplate(stmt, companyId, DefaultContractTemplate.TYPE_PROCESSING,
                    DefaultContractTemplate.NAME_PROCESSING, DefaultContractTemplate.PROCESSING_CONTRACT_HTML);
            insertContractTemplate(stmt, companyId, DefaultContractTemplate.TYPE_PURCHASE,
                    DefaultContractTemplate.NAME_PURCHASE, DefaultContractTemplate.PURCHASE_CONTRACT_HTML);
            stmt.execute("SET FOREIGN_KEY_CHECKS = 1");
            conn.commit();
            stmt.close();
            return R.ok("当前公司所有业务数据已清空，物料类型和阶段模板已重置为默认");
        } catch (Exception e) {
            return R.fail(e.getMessage());
        }
    }

    /**
     * 插入一整套默认阶段模板（2026-09-21：原配 7 条 / 改配 14 条），返回插入条数。
     * <p>与 {@code DataInitializer} 共用 {@link DefaultPhaseTemplates} 常量；SQL 里**必须**带上
     * {@code spec_type} 与 {@code product_status_sync} —— 后者原先被漏掉，导致"清空数据"后
     * 小批量/结项不再触发产品状态同步（现网 14 条全 0 的成因）。</p>
     */
    private int insertPhaseTemplates(Statement stmt, DefaultPhaseTemplates.Row[] rows, String specType, Long companyId) throws Exception {
        for (DefaultPhaseTemplates.Row r : rows) {
            stmt.execute("INSERT INTO dev_phase_template (name, spec_type, default_days, sort_order, product_status_sync, remark, company_id) VALUES ('"
                    + r.name().replace("'", "''") + "', '" + specType + "', " + r.defaultDays() + ", "
                    + r.sortOrder() + ", " + r.productStatusSync() + ", '"
                    + r.remark().replace("'", "''") + "', " + companyId + ")");
        }
        return rows.length;
    }

    /** 插入一条默认合同模板（清空数据后重置用，与 DataInitializer 共用 DefaultContractTemplate 常量） */
    private void insertContractTemplate(Statement stmt, Long companyId, String type, String name, String content) throws Exception {
        String escapedName = name.replace("'", "''");
        String escapedContent = content.replace("'", "''");
        stmt.execute("INSERT INTO outsource_contract_template (template_name, content, template_type, status, is_default, company_id, create_time, update_time) VALUES ('"
                + escapedName + "', '" + escapedContent + "', '" + type + "', 1, 1, " + companyId + ", NOW(), NOW())");
    }

    /**
     * 全库清空（P2-34：**仅超级管理员**）。方法级注解在类级放行范围之外再收紧 ——
     * Sa-Token 会同时校验类级与方法级注解，公司管理员虽满足类级（admin），仍会被此处拦下（403）。
     */
    @SaCheckRole(SystemConstants.SUPER_ADMIN_ROLE_CODE)
    @PostMapping("/api/system/clear-data")
    public R<String> clear() {
        log.warn("[审计] 全库清空开始：operator={}, companyId={}", StpUtil.getLoginIdDefaultNull(), CompanyContext.get());
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            Statement stmt = conn.createStatement();
            stmt.execute("SET FOREIGN_KEY_CHECKS = 0");
            List<String> dels = new ArrayList<>();
            // 屏幕资料知识库（screen_model）是行业基础资料、不属于业务数据，清空数据时保留。
            // 因此这里遍历全库表时排除该表；按公司清空的 clear-company-data 同样不清理它（该表不在下方清单中）。
            ResultSet rs = stmt.executeQuery(
                "SELECT CONCAT('DELETE FROM ', table_name, ';') FROM information_schema.tables "
                + "WHERE table_schema=DATABASE() AND table_type='BASE TABLE' AND table_name <> 'screen_model'");
            while (rs.next()) dels.add(rs.getString(1));
            rs.close();
            for (String d : dels) { stmt.execute(d); }
            stmt.execute("SET FOREIGN_KEY_CHECKS = 1");
            conn.commit();
            stmt.close();
            log.warn("[审计] 全库清空完成：operator={}, tables={}", StpUtil.getLoginIdDefaultNull(), dels.size());
            return R.ok("已清空 " + dels.size() + " 张表。请重启后端以重新初始化。");
        } catch (Exception e) {
            return R.fail(e.getMessage());
        }
    }


}
