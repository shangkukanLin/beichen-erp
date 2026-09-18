package com.beichen.erp.config;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import com.beichen.erp.common.R;
import com.beichen.erp.common.DefaultMaterialTypes;
import com.beichen.erp.common.DefaultContractTemplate;
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
            // 重新初始化阶段模板默认数据
            String[][] phaseDefaults = {
                {"立项", "0", "1", ""},
                {"结构评估", "2", "2", "根据玻璃尺寸和摄像头孔位与R角来综合评估结构是否支持立项。"},
                {"立项准备", "5", "3", "根据项目型号收手机，拆分成机板和屏幕分体状态，交给触摸方案公司抓取触摸协议，明确是否可以破解协议以及用哪颗物料可以满足技术标准。"},
                {"显示评估", "2", "4", "提供机板和原屏给到显示方案公司，并告知触摸方案商建议使用的触摸IC料号及规格书与触摸原理图，让显示方案公司抓取显示协议，根据手机的分辨率与刷新率和玻璃的分辨率综合评估用哪颗码片物料，以及驱动IC。"},
                {"排线图纸", "3", "5", "根据触摸方案公司建议的触摸IC和显示方案公司建议的码片，开始画图纸，一般都可以画，后期一般是谁画的图纸就和谁买码片。"},
                {"排线打样", "4", "6", "出图纸后，把图纸给到排线工厂打样，一般打10PCS，码片和触摸IC需要找方案公司提供，哪个公司画的排线图纸就找哪个公司寄码片，触摸公司寄触摸IC。"},
                {"FOG打样", "2", "7", "排线打样好之后直接让工厂寄给打样加工厂，同时需要寄驱动IC过去和玻璃过去，一般先打样5PCS。"},
                {"显示调试", "5", "8", "FOG打样直接寄到显示方案公司，并且提供机板，开始调试显示功能。其他兼容的基板，等没什么大问题再去购买给方案公司做兼容。"},
                {"触摸调试", "5", "9", "初版显示做好以后，移交机板和FOG去触摸方案公司调试触摸。同时保留一个机板和FOG去盖板厂根据屏幕的实际显示效果开模做盖板样品，然后去背贴厂开背贴样品。"},
                {"背贴盖板打样", "2", "10", "使用保留的一个机板和FOG去盖板厂根据屏幕的实际显示效果开模做盖板样品，然后去背贴厂开背贴样品。"},
                {"总成样品", "2", "11", "将盖板和背贴样品寄到加工厂做成总成，需要寄2PCS总成和机板过去方案公司优化触摸。"},
                {"测试", "5", "12", "开始测试，需要测试结构/显示/触摸，详见测试文档。"},
                {"小批量", "3", "13", "测试没问题之后，下物料寄到工厂，先进行100PCS的小批量，到货后过一遍，没有批次问题，就可以结项了。"},
                {"结项", "0", "14", "结项，通知工厂开始量产。"},
            };
            for (String[] p : phaseDefaults) {
                stmt.execute("INSERT INTO dev_phase_template (name, default_days, sort_order, remark, company_id) VALUES ('"
                    + p[0].replace("'", "''") + "', " + p[1] + ", " + p[2] + ", '"
                    + p[3].replace("'", "''") + "', " + companyId + ")");
            }
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
