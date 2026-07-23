package com.beichen.erp.config;

import com.beichen.erp.common.R;
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

@RestController
public class ClearController {

    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbcTemplate;

    @GetMapping("/api/system/check-menu")
    public R<Object> checkMenu() {
        Map<String, Object> r = new HashMap<>();
        r.put("sys_menu_39", jdbcTemplate.queryForList("SELECT * FROM sys_menu WHERE id=39"));
        r.put("sys_role_menu_39", jdbcTemplate.queryForList("SELECT * FROM sys_role_menu WHERE menu_id=39"));
        r.put("menus_under_5", jdbcTemplate.queryForList("SELECT id, menu_name, sort_order FROM sys_menu WHERE parent_id=5 ORDER BY sort_order"));
        return R.ok(r);
    }

    /** 清空当前公司所有业务数据（保留系统表） */
    @PostMapping("/api/system/clear-company-data")
    public R<String> clearCompanyData() {
        Long companyId = com.beichen.erp.config.CompanyContext.get();
        if (companyId == null || companyId == 0) return R.fail("超管模式下请先选择公司");
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            Statement stmt = conn.createStatement();
            stmt.execute("SET FOREIGN_KEY_CHECKS = 0");
            // 按外键依赖顺序删除当前公司的业务数据
            String[] sqls = {
                "DELETE FROM finance_payment_item WHERE company_id = " + companyId,
                "DELETE FROM finance_receipt_item WHERE company_id = " + companyId,
                "DELETE FROM finance_bill_item WHERE company_id = " + companyId,
                "DELETE FROM inventory_transfer_item WHERE company_id = " + companyId,
                "DELETE FROM inventory_stock_take_item WHERE company_id = " + companyId,
                "DELETE FROM inventory_other_io_item WHERE company_id = " + companyId,
                "DELETE FROM purchase_inbound_item WHERE company_id = " + companyId,
                "DELETE FROM purchase_order_item WHERE company_id = " + companyId,
                "DELETE FROM sale_outbound_item WHERE company_id = " + companyId,
                "DELETE FROM sale_order_item WHERE company_id = " + companyId,
                "DELETE FROM outsource_delivery_item WHERE company_id = " + companyId,
                "DELETE FROM outsource_material_component WHERE company_id = " + companyId,
                "DELETE FROM outsource_material_order_item WHERE company_id = " + companyId,
                "DELETE FROM outsource_order_material WHERE company_id = " + companyId,
                "DELETE FROM outsource_order_product WHERE company_id = " + companyId,
                "DELETE FROM outsource_order_close_report_item WHERE report_id IN (SELECT id FROM outsource_order_close_report WHERE company_id = " + companyId + ")",
                "DELETE FROM outsource_order_delivery WHERE company_id = " + companyId,
                "DELETE FROM finance_payment WHERE company_id = " + companyId,
                "DELETE FROM finance_receipt WHERE company_id = " + companyId,
                "DELETE FROM finance_bill WHERE company_id = " + companyId,
                "DELETE FROM finance_cashflow WHERE company_id = " + companyId,
                "DELETE FROM finance_receivable WHERE company_id = " + companyId,
                "DELETE FROM finance_payable WHERE company_id = " + companyId,
                "DELETE FROM finance_account WHERE company_id = " + companyId,
                "DELETE FROM inventory_stock_log WHERE company_id = " + companyId,
                "DELETE FROM inventory_transfer WHERE company_id = " + companyId,
                "DELETE FROM inventory_stock_take WHERE company_id = " + companyId,
                "DELETE FROM inventory_other_io WHERE company_id = " + companyId,
                "DELETE FROM inventory_warehouse_stock WHERE company_id = " + companyId,
                "DELETE FROM inventory_warehouse WHERE company_id = " + companyId,
                "DELETE FROM purchase_inbound WHERE company_id = " + companyId,
                "DELETE FROM purchase_order WHERE company_id = " + companyId,
                "DELETE FROM sale_outbound WHERE company_id = " + companyId,
                "DELETE FROM sale_order WHERE company_id = " + companyId,
                "DELETE FROM outsource_order_close_report WHERE company_id = " + companyId,
                "DELETE FROM outsource_material_order WHERE company_id = " + companyId,
                "DELETE FROM outsource_delivery WHERE company_id = " + companyId,
                "DELETE FROM outsource_order WHERE company_id = " + companyId,
                "DELETE FROM outsource_contract_template WHERE company_id = " + companyId,
                "DELETE FROM outsource_warehouse_stock WHERE company_id = " + companyId,
                "DELETE FROM outsource_other_io_item WHERE company_id = " + companyId,
                "DELETE FROM outsource_other_io WHERE company_id = " + companyId,
                "DELETE FROM outsource_warehouse WHERE company_id = " + companyId,
                "DELETE FROM outsource_material WHERE company_id = " + companyId,
                "DELETE FROM dev_drawing WHERE company_id = " + companyId,
                "DELETE FROM dev_bug WHERE company_id = " + companyId,
                "DELETE FROM dev_bom WHERE company_id = " + companyId,
                "DELETE FROM dev_bom_type WHERE company_id = " + companyId,
                "DELETE FROM dev_phase_template WHERE company_id = " + companyId,
                "DELETE FROM dev_project_timeline WHERE company_id = " + companyId,
                "DELETE FROM dev_project WHERE company_id = " + companyId,
                "DELETE FROM supplier_product WHERE company_id = " + companyId,
                "DELETE FROM supplier WHERE company_id = " + companyId,
                "DELETE FROM product WHERE company_id = " + companyId,
                "DELETE FROM customer WHERE company_id = " + companyId,
                "DELETE FROM brand WHERE company_id = " + companyId,
            };
            for (String s : sqls) { stmt.execute(s); }
                // 重新初始化BOM类型默认数据
            String[] defaultTypes = {"玻璃", "驱动IC", "码片IC", "触摸IC", "排线", "背贴", "盖板"};
            for (int i = 0; i < defaultTypes.length; i++) {
                stmt.execute("INSERT INTO dev_bom_type (type_name, sort_order, status, company_id) VALUES ('" + defaultTypes[i] + "', " + (i + 1) + ", 1, " + companyId + ")");
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
            stmt.execute("SET FOREIGN_KEY_CHECKS = 1");
            conn.commit();
            stmt.close();
            return R.ok("当前公司所有业务数据已清空，BOM类型和阶段模板已重置为默认");
        } catch (Exception e) {
            return R.fail(e.getMessage());
        }
    }

    @PostMapping("/api/system/clear-data")
    public R<String> clear() {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            Statement stmt = conn.createStatement();
            stmt.execute("SET FOREIGN_KEY_CHECKS = 0");
            List<String> dels = new ArrayList<>();
            ResultSet rs = stmt.executeQuery(
                "SELECT CONCAT('DELETE FROM ', table_name, ';') FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE'");
            while (rs.next()) dels.add(rs.getString(1));
            rs.close();
            for (String d : dels) { stmt.execute(d); }
            stmt.execute("SET FOREIGN_KEY_CHECKS = 1");
            conn.commit();
            stmt.close();
            return R.ok("已清空 " + dels.size() + " 张表。请重启后端以重新初始化。");
        } catch (Exception e) {
            return R.fail(e.getMessage());
        }
    }

    @GetMapping("/api/system/fix-menus")
    public R<String> fixMenus() {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            Statement stmt = conn.createStatement();
            stmt.execute("DELETE FROM sys_role_menu WHERE menu_id=38");
            stmt.execute("DELETE FROM sys_menu WHERE id=38");
            stmt.execute("INSERT IGNORE INTO sys_menu (id,parent_id,menu_name,menu_type,route_path,route_name,icon,sort_order,visible,status) VALUES (39,5,'品牌管理','menu','/inventory/brand','InventoryBrand','CollectionTag',1,1,1)");
            stmt.execute("INSERT IGNORE INTO sys_role_menu (role_id,menu_id) SELECT r.id,39 FROM sys_role r WHERE r.role_code='super_admin'");
            stmt.execute("INSERT IGNORE INTO sys_role_menu (role_id,menu_id) SELECT r.id,39 FROM sys_role r WHERE r.role_code='admin'");
            stmt.execute("UPDATE sys_menu SET sort_order=2 WHERE id=18");
            stmt.execute("UPDATE sys_menu SET sort_order=3 WHERE id=19");
            stmt.execute("UPDATE sys_menu SET sort_order=4 WHERE id=20");
            stmt.execute("UPDATE sys_menu SET sort_order=5 WHERE id=13");
            stmt.execute("UPDATE sys_menu SET sort_order=6 WHERE id=22");
            stmt.execute("UPDATE sys_menu SET sort_order=7 WHERE id=23");
            conn.commit();
            stmt.close();
            return R.ok("品牌管理菜单已创建。请重新登录。");
        } catch (Exception e) { return R.fail(e.getMessage()); }
    }
}
