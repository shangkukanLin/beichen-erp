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
                "DELETE FROM outsource_order_material WHERE company_id = " + companyId,
                "DELETE FROM outsource_order_product WHERE company_id = " + companyId,
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
                "DELETE FROM outsource_delivery WHERE company_id = " + companyId,
                "DELETE FROM outsource_order WHERE company_id = " + companyId,
                "DELETE FROM outsource_contract_template WHERE company_id = " + companyId,
                "DELETE FROM outsource_warehouse WHERE company_id = " + companyId,
                "DELETE FROM outsource_material WHERE company_id = " + companyId,
                "DELETE FROM dev_drawing WHERE company_id = " + companyId,
                "DELETE FROM dev_bug WHERE company_id = " + companyId,
                "DELETE FROM dev_bom WHERE company_id = " + companyId,
                "DELETE FROM dev_bom_type WHERE company_id = " + companyId,
                "DELETE FROM dev_project_timeline WHERE company_id = " + companyId,
                "DELETE FROM dev_project WHERE company_id = " + companyId,
                "DELETE FROM supplier_product WHERE company_id = " + companyId,
                "DELETE FROM supplier WHERE company_id = " + companyId,
                "DELETE FROM customer WHERE company_id = " + companyId,
                "DELETE FROM brand WHERE company_id = " + companyId,
            };
            for (String s : sqls) { stmt.execute(s); }
            stmt.execute("SET FOREIGN_KEY_CHECKS = 1");
            conn.commit();
            stmt.close();
            return R.ok("当前公司所有业务数据已清空");
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
