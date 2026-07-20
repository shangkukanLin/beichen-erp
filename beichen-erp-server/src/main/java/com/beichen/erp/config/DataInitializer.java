package com.beichen.erp.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beichen.erp.auth.entity.User;
import com.beichen.erp.auth.mapper.UserMapper;
import com.beichen.erp.dev.entity.BomType;
import com.beichen.erp.dev.mapper.BomTypeMapper;
import com.beichen.erp.material.entity.Material;
import com.beichen.erp.material.mapper.MaterialMapper;
import com.beichen.erp.system.entity.Menu;
import com.beichen.erp.system.entity.Role;
import com.beichen.erp.system.entity.UserRole;
import com.beichen.erp.system.mapper.MenuMapper;
import com.beichen.erp.system.mapper.RoleMapper;
import com.beichen.erp.system.mapper.UserRoleMapper;
import org.springframework.core.io.ClassPathResource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import com.beichen.erp.system.service.RoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * 数据初始化：首次启动自动初始化内置角色、菜单、角色权限、超级管理员账号和示例物料数据
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final UserMapper userMapper;
    private final MaterialMapper materialMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final MenuMapper menuMapper;
    private final RoleService roleService;
    private final BomTypeMapper bomTypeMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        initSchema(); // 首次启动执行建表脚本
        // 清空所有业务数据（保留表结构），仅当启动参数含 --clear-data 时执行
        if (args.containsOption("clear-data")) {
            clearAllData();
            log.info("===== 数据已清空，仅保留表结构 =====");
        }
        alterDeliveryTable();
        alterInventoryStockTable();
        initCompany();
        initRoles();
        initMenus();
        syncMenus();
        initRoleMenus();
        initSuperAdmin();
        initBomTypes();
        initMaterials();
    }

    private void initSchema() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS sys_user (id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',username VARCHAR(50) UNIQUE NOT NULL COMMENT '登录账号',password VARCHAR(100) NOT NULL COMMENT 'BCrypt加密密码',phone VARCHAR(20) COMMENT '手机号',dept VARCHAR(50) COMMENT '所属部门',status TINYINT DEFAULT 1 COMMENT '1启用 0禁用',company_id BIGINT DEFAULT NULL COMMENT '公司ID',deleted TINYINT DEFAULT 0 COMMENT '0正常 1已删除',create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',INDEX idx_company_id (company_id),INDEX idx_username (username)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS sys_company (id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '公司ID',company_name VARCHAR(100) NOT NULL COMMENT '公司名称',status TINYINT DEFAULT 1 COMMENT '1启用 0禁用',create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间') ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS sys_role (id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '角色ID',role_name VARCHAR(50) NOT NULL COMMENT '角色名称',role_code VARCHAR(50) NOT NULL COMMENT '角色编码',status TINYINT DEFAULT 1 COMMENT '1启用 0禁用',remark VARCHAR(255) DEFAULT NULL COMMENT '备注',create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',company_id BIGINT DEFAULT NULL COMMENT '公司ID',update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',UNIQUE KEY uk_role_code (role_code),INDEX idx_company_id (company_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS sys_user_role (id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',user_id BIGINT NOT NULL COMMENT '用户ID',role_id BIGINT NOT NULL COMMENT '角色ID',UNIQUE KEY uk_user_role (user_id, role_id),INDEX idx_user_id (user_id),INDEX idx_role_id (role_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS sys_menu (id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '菜单ID',parent_id BIGINT DEFAULT 0 COMMENT '父菜单ID',menu_name VARCHAR(50) NOT NULL COMMENT '菜单名称',menu_type VARCHAR(20) NOT NULL COMMENT '类型',route_path VARCHAR(100) DEFAULT '' COMMENT '路由路径',route_name VARCHAR(100) DEFAULT '' COMMENT '路由名称',icon VARCHAR(50) DEFAULT '' COMMENT '图标',sort_order INT DEFAULT 0 COMMENT '排序',visible TINYINT DEFAULT 1 COMMENT '0隐藏 1显示',status TINYINT DEFAULT 1 COMMENT '0禁用 1启用',create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',company_id BIGINT DEFAULT NULL COMMENT '公司ID',update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',INDEX idx_parent_id (parent_id),INDEX idx_company_id (company_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS sys_role_menu (id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',role_id BIGINT NOT NULL COMMENT '角色ID',menu_id BIGINT NOT NULL COMMENT '菜单ID',UNIQUE KEY uk_role_menu (role_id, menu_id),INDEX idx_role_id (role_id),INDEX idx_menu_id (menu_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS brand (id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '品牌ID',brand_name VARCHAR(100) NOT NULL COMMENT '品牌名称',status TINYINT DEFAULT 1 COMMENT '1启用 0禁用',company_id BIGINT DEFAULT NULL COMMENT '公司ID',create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',UNIQUE KEY uk_brand_name_company (brand_name, company_id),INDEX idx_company_id (company_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        // 增量 DDL：给已有表补加缺失的关键列
        safeDDL("ALTER TABLE dev_project ADD COLUMN assembly_name VARCHAR(100) COMMENT '总成名称' AFTER name");
        safeDDL("ALTER TABLE material ADD COLUMN brand_id BIGINT DEFAULT NULL COMMENT '品牌ID' AFTER name");
        safeDDL("ALTER TABLE sys_company ADD COLUMN phone VARCHAR(20) COMMENT '电话' AFTER company_name");
        safeDDL("ALTER TABLE sys_company ADD COLUMN address VARCHAR(200) COMMENT '地址' AFTER phone");
        safeDDL("ALTER TABLE sys_company ADD COLUMN contact_person VARCHAR(50) COMMENT '联系人' AFTER address");
        safeDDL("ALTER TABLE sys_company ADD COLUMN tax_no VARCHAR(50) COMMENT '税号' AFTER contact_person");
        safeDDL("ALTER TABLE sys_company ADD COLUMN email VARCHAR(100) COMMENT '邮箱' AFTER tax_no");
        safeDDL("ALTER TABLE inventory_warehouse_stock ADD COLUMN material_id BIGINT DEFAULT NULL COMMENT '物料ID' AFTER product_name");
        safeDDL("ALTER TABLE inventory_warehouse_stock ADD COLUMN available_quantity DECIMAL(18,4) DEFAULT 0 COMMENT '可用数量' AFTER quantity");
        log.info("核心表+增量DDL完成");
    }

    private void safeDDL(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception e) {
            log.warn("safeDDL 执行失败: {} — 错误: {}", sql, e.getMessage());
        }
    }

    private void clearAllData() {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        String sql = "SELECT CONCAT('DELETE FROM ', table_name, ';') FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE'";
        jdbcTemplate.queryForList(sql).forEach(row -> {
            jdbcTemplate.execute(row.values().iterator().next().toString());
        });
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
    }

    /** 补全 dev_project 表所有可能缺失的列 */
    private void alterDevProjectColumns() {
        String[][] columns = {
            {"assembly_name", "VARCHAR(100) COMMENT '总成名称'"},
            {"display_supplier_name", "VARCHAR(100) COMMENT '显示方案供应商'"},
            {"touch_supplier_name", "VARCHAR(100) COMMENT '触摸方案供应商'"},
            {"adapt_model", "VARCHAR(100) COMMENT '适配机型'"},
            {"original_size", "VARCHAR(50) COMMENT '原始尺寸'"},
            {"original_resolution", "VARCHAR(50) COMMENT '原始分辨率'"},
            {"project_leader_id", "BIGINT COMMENT '项目负责人ID'"},
            {"sample_factory_id", "BIGINT COMMENT '样品工厂ID'"},
            {"outsource_factory_id", "BIGINT COMMENT '外协工厂ID'"},
            {"start_date", "DATE COMMENT '开始日期'"},
            {"expected_end_date", "DATE COMMENT '预计结束日期'"},
            {"actual_end_date", "DATE COMMENT '实际结束日期'"},
        };
        for (String[] col : columns) {
            try {
                Integer cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='dev_project' AND COLUMN_NAME=?",
                    Integer.class, col[0]);
                if (cnt == null || cnt == 0) {
                    jdbcTemplate.execute("ALTER TABLE dev_project ADD COLUMN " + col[0] + " " + col[1]);
                    log.info("已添加 dev_project.{} 列", col[0]);
                }
            } catch (Exception e) {
                log.warn("添加 dev_project.{} 列异常: {}", col[0], e.getMessage());
            }
        }
    }

    /** 为 material 表添加 project_id 列 */
    private void alterMaterialProjectId() {
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='material' AND COLUMN_NAME='project_id'",
                Integer.class);
            if (cnt == null || cnt == 0) {
                jdbcTemplate.execute("ALTER TABLE material ADD COLUMN project_id BIGINT DEFAULT NULL COMMENT '关联研发项目ID' AFTER status");
                log.info("已添加 material.project_id 列");
            }
        } catch (Exception e) {
            log.warn("添加 material.project_id 列异常: {}", e.getMessage());
        }
    }

    /** 将 material.status 从 TINYINT 升级为 VARCHAR */
    private void alterMaterialStatusColumn() {
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='material' AND COLUMN_NAME='status' AND DATA_TYPE='varchar'",
                Integer.class);
            if (cnt == null || cnt == 0) {
                jdbcTemplate.execute("ALTER TABLE material MODIFY COLUMN status VARCHAR(20) DEFAULT '正常' COMMENT '状态: 正常/停售/研发中'");
                // 迁移旧数据: 1→正常, 0→停售
                jdbcTemplate.execute("UPDATE material SET status = '正常' WHERE status = '1'");
                jdbcTemplate.execute("UPDATE material SET status = '停售' WHERE status = '0'");
                log.info("已升级 material.status 为 VARCHAR(20)");
            }
        } catch (Exception e) {
            log.warn("升级 material.status 列类型异常: {}", e.getMessage());
        }
    }

    /** 增量 DDL：给已有表补加缺失的列 */
    private void alterDeliveryTable() {
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='outsource_order_delivery' AND COLUMN_NAME='warehouse_id'", Integer.class);
            if (cnt == null || cnt == 0) {
                jdbcTemplate.execute("ALTER TABLE outsource_order_delivery ADD COLUMN warehouse_id BIGINT DEFAULT NULL COMMENT '收货仓库ID' AFTER order_id");
                log.info("已添加 outsource_order_delivery.warehouse_id 列");
            }
        } catch (Exception e) {
            log.warn("DDL 执行异常: {}", e.getMessage());
        }
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='customer' AND COLUMN_NAME='credit_period_months'", Integer.class);
            if (cnt == null || cnt == 0) {
                jdbcTemplate.execute("ALTER TABLE customer ADD COLUMN credit_period_months INT DEFAULT 0 COMMENT '账期(月)' AFTER credit_period");
                log.info("已添加 customer.credit_period_months 列");
            }
        } catch (Exception e) {
            log.warn("DDL 执行异常: {}", e.getMessage());
        }
        // 补全 dev_project 所有可能缺失的列
        alterDevProjectColumns();
        // 物料状态字段从 TINYINT 升级为 VARCHAR
        alterMaterialStatusColumn();
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='material' AND COLUMN_NAME='brand_id'", Integer.class);
            if (cnt == null || cnt == 0) {
                jdbcTemplate.execute("ALTER TABLE material ADD COLUMN brand_id BIGINT DEFAULT NULL COMMENT '品牌ID' AFTER name");
                log.info("已添加 material.brand_id 列");
            }
        } catch (Exception e) {
            log.warn("DDL 执行异常: {}", e.getMessage());
        }
        // material.project_id：关联研发项目
        alterMaterialProjectId();
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sys_company' AND COLUMN_NAME='phone'", Integer.class);
            if (cnt == null || cnt == 0) {
                jdbcTemplate.execute("ALTER TABLE sys_company ADD COLUMN phone VARCHAR(20) COMMENT '电话' AFTER company_name");
                jdbcTemplate.execute("ALTER TABLE sys_company ADD COLUMN address VARCHAR(200) COMMENT '地址' AFTER phone");
                jdbcTemplate.execute("ALTER TABLE sys_company ADD COLUMN contact_person VARCHAR(50) COMMENT '联系人' AFTER address");
                jdbcTemplate.execute("ALTER TABLE sys_company ADD COLUMN tax_no VARCHAR(50) COMMENT '税号' AFTER contact_person");
                jdbcTemplate.execute("ALTER TABLE sys_company ADD COLUMN email VARCHAR(100) COMMENT '邮箱' AFTER tax_no");
                log.info("已添加 sys_company 扩展字段");
            }
        } catch (Exception e) { log.warn("DDL 执行异常: {}", e.getMessage()); }
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sys_param'", Integer.class);
            if (cnt == null || cnt == 0) {
                jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS sys_param (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, param_key VARCHAR(50) NOT NULL, param_value VARCHAR(200)," +
                    "remark VARCHAR(255), company_id BIGINT, create_time DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, UNIQUE KEY uk_key_company (param_key, company_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
                // 插默认参数
                Long cid = CompanyContext.get();
                if (cid != null && cid > 0) {
                    jdbcTemplate.update("INSERT IGNORE INTO sys_param (param_key, param_value, remark, company_id) VALUES ('tax_rate','13.00','税率(%)',?),('credit_period','30','账期天数',?),('stock_alert_threshold','10','库存预警阈值',?)", cid, cid, cid);
                }
                log.info("已创建 sys_param 表");
            }
        } catch (Exception e) { log.warn("DDL 执行异常: {}", e.getMessage()); }
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sys_operation_log'", Integer.class);
            if (cnt == null || cnt == 0) {
                jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS sys_operation_log (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT, username VARCHAR(50)," +
                    "module VARCHAR(50), operation VARCHAR(50), target VARCHAR(200), detail VARCHAR(500)," +
                    "ip VARCHAR(50), company_id BIGINT, create_time DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "INDEX idx_user_id (user_id), INDEX idx_module (module), INDEX idx_company_id (company_id), INDEX idx_create_time (create_time)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
                log.info("已创建 sys_operation_log 表");
            }
        } catch (Exception e) { log.warn("DDL 执行异常: {}", e.getMessage()); }
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='brand'", Integer.class);
            if (cnt == null || cnt == 0) {
                jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS brand (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '品牌ID'," +
                    "brand_name VARCHAR(100) NOT NULL COMMENT '品牌名称'," +
                    "status TINYINT DEFAULT 1 COMMENT '1启用 0禁用'," +
                    "company_id BIGINT DEFAULT NULL COMMENT '公司ID'," +
                    "create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
                    "update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'," +
                    "UNIQUE KEY uk_brand_name_company (brand_name, company_id)," +
                    "INDEX idx_company_id (company_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='品牌表'");
                log.info("已创建 brand 品牌表");
            }
        } catch (Exception e) {
            log.warn("DDL 执行异常: {}", e.getMessage());
        }
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='dev_material'", Integer.class);
            if (cnt == null || cnt == 0) {
                jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS dev_material (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID'," +
                    "project_id BIGINT NOT NULL COMMENT '项目ID'," +
                    "material_name VARCHAR(100) NOT NULL COMMENT '物料名称'," +
                    "material_type VARCHAR(30) COMMENT '物料类型'," +
                    "quantity DECIMAL(18,4) DEFAULT 1 COMMENT '数量'," +
                    "location VARCHAR(30) COMMENT '存放位置'," +
                    "location_detail VARCHAR(200) COMMENT '位置详情'," +
                    "purchase_date DATE COMMENT '采购日期'," +
                    "cost DECIMAL(18,4) COMMENT '采购金额'," +
                    "status VARCHAR(20) DEFAULT '完好' COMMENT '状态'," +
                    "remark VARCHAR(255) COMMENT '备注'," +
                    "company_id BIGINT DEFAULT NULL COMMENT '公司ID'," +
                    "create_time DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                    "INDEX idx_project_id (project_id)," +
                    "INDEX idx_company_id (company_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='研发项目物料表'");
                log.info("已创建 dev_material 研发物料表");
            }
        } catch (Exception e) { log.warn("DDL 执行异常: {}", e.getMessage()); }
        // 已有表补加 company_id 列
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='dev_material' AND COLUMN_NAME='company_id'",
                Integer.class);
            if (cnt == null || cnt == 0) {
                jdbcTemplate.execute("ALTER TABLE dev_material ADD COLUMN company_id BIGINT DEFAULT NULL COMMENT '公司ID' AFTER remark");
                log.info("已为 dev_material 添加 company_id 列");
            }
        } catch (Exception e) { log.warn("DDL 执行异常: {}", e.getMessage()); }
        // 已有表补加 company_id 列 - outsource_material_order_item
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='outsource_material_order_item' AND COLUMN_NAME='company_id'",
                Integer.class);
            if (cnt == null || cnt == 0) {
                jdbcTemplate.execute("ALTER TABLE outsource_material_order_item ADD COLUMN company_id BIGINT DEFAULT NULL COMMENT '公司ID' AFTER remark");
                log.info("已为 outsource_material_order_item 添加 company_id 列");
            }
        } catch (Exception e) { log.warn("DDL 执行异常: {}", e.getMessage()); }
        // outsource_material_order 添加 finish_time 列
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='outsource_material_order' AND COLUMN_NAME='finish_time'",
                Integer.class);
            if (cnt == null || cnt == 0) {
                jdbcTemplate.execute("ALTER TABLE outsource_material_order ADD COLUMN finish_time DATETIME DEFAULT NULL COMMENT '订单完成时间' AFTER update_time");
                log.info("已为 outsource_material_order 添加 finish_time 列");
            }
        } catch (Exception e) { log.warn("DDL 执行异常: {}", e.getMessage()); }
        // outsource_contract_template 添加 template_type 列（加工合同/采购合同）
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='outsource_contract_template' AND COLUMN_NAME='template_type'",
                Integer.class);
            if (cnt == null || cnt == 0) {
                jdbcTemplate.execute("ALTER TABLE outsource_contract_template ADD COLUMN template_type VARCHAR(20) DEFAULT '加工合同' COMMENT '模板类型：加工合同/采购合同' AFTER is_default");
                log.info("已为 outsource_contract_template 添加 template_type 列");
            }
        } catch (Exception e) { log.warn("DDL 执行异常: {}", e.getMessage()); }
        // outsource_material_order 添加 attach_url 列
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='outsource_material_order' AND COLUMN_NAME='attach_url'",
                Integer.class);
            if (cnt == null || cnt == 0) {
                jdbcTemplate.execute("ALTER TABLE outsource_material_order ADD COLUMN attach_url VARCHAR(500) DEFAULT NULL COMMENT '合同附件URL' AFTER remark");
                log.info("已为 outsource_material_order 添加 attach_url 列");
            }
        } catch (Exception e) { log.warn("DDL 执行异常: {}", e.getMessage()); }
        // outsource_delivery_item 添加 handle_type 列（退不良处理方式：维修返还/折现退款）
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='outsource_delivery_item' AND COLUMN_NAME='handle_type'",
                Integer.class);
            if (cnt == null || cnt == 0) {
                jdbcTemplate.execute("ALTER TABLE outsource_delivery_item ADD COLUMN handle_type VARCHAR(20) DEFAULT NULL COMMENT '处理方式：维修返还/折现退款' AFTER quality_type");
                log.info("已为 outsource_delivery_item 添加 handle_type 列");
            }
        } catch (Exception e) { log.warn("DDL 执行异常: {}", e.getMessage()); }
        // outsource_warehouse_stock 添加 company_id 列
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='outsource_warehouse_stock' AND COLUMN_NAME='company_id'",
                Integer.class);
            if (cnt == null || cnt == 0) {
                jdbcTemplate.execute("ALTER TABLE outsource_warehouse_stock ADD COLUMN company_id BIGINT DEFAULT NULL COMMENT '公司ID' AFTER quantity");
                jdbcTemplate.execute("UPDATE outsource_warehouse_stock SET company_id = 1 WHERE company_id IS NULL");
                log.info("已为 outsource_warehouse_stock 添加 company_id 列并回填默认值");
            }
        } catch (Exception e) { log.warn("DDL 执行异常: {}", e.getMessage()); }
        // outsource_order_delivery 添加 delivery_type 列
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='outsource_order_delivery' AND COLUMN_NAME='delivery_type'",
                Integer.class);
            if (cnt == null || cnt == 0) {
                jdbcTemplate.execute("ALTER TABLE outsource_order_delivery ADD COLUMN delivery_type VARCHAR(10) DEFAULT '正常' COMMENT '正常/退不良' AFTER quantity");
                log.info("已为 outsource_order_delivery 添加 delivery_type 列");
            }
        } catch (Exception e) { log.warn("DDL 执行异常: {}", e.getMessage()); }
        // outsource_order_close_report_item 添加 company_id 列
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='outsource_order_close_report_item' AND COLUMN_NAME='company_id'",
                Integer.class);
            if (cnt == null || cnt == 0) {
                jdbcTemplate.execute("ALTER TABLE outsource_order_close_report_item ADD COLUMN company_id BIGINT DEFAULT NULL COMMENT '公司ID' AFTER remark");
                jdbcTemplate.execute("UPDATE outsource_order_close_report_item SET company_id = 1 WHERE company_id IS NULL");
                log.info("已为 outsource_order_close_report_item 添加 company_id 列");
            }
        } catch (Exception e) { log.warn("DDL 执行异常: {}", e.getMessage()); }
        // outsource_order_close_report_item 添加 material_price 列
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='outsource_order_close_report_item' AND COLUMN_NAME='material_price'",
                Integer.class);
            if (cnt == null || cnt == 0) {
                jdbcTemplate.execute("ALTER TABLE outsource_order_close_report_item ADD COLUMN material_price DECIMAL(18,4) DEFAULT 0 COMMENT '物料单价' AFTER excess_loss_qty");
                log.info("已为 outsource_order_close_report_item 添加 material_price 列");
            }
        } catch (Exception e) { log.warn("DDL 执行异常: {}", e.getMessage()); }
        // outsource_delivery_item 添加 unit_price 列
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='outsource_delivery_item' AND COLUMN_NAME='unit_price'",
                Integer.class);
            if (cnt == null || cnt == 0) {
                jdbcTemplate.execute("ALTER TABLE outsource_delivery_item ADD COLUMN unit_price DECIMAL(18,4) DEFAULT 0 COMMENT '单价' AFTER quantity");
                log.info("已为 outsource_delivery_item 添加 unit_price 列");
            }
        } catch (Exception e) { log.warn("DDL 执行异常: {}", e.getMessage()); }
        // outsource_other_io 和 outsource_other_io_item 表
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='outsource_other_io'", Integer.class);
            if (cnt == null || cnt == 0) {
                jdbcTemplate.execute("CREATE TABLE outsource_other_io (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, code VARCHAR(30), warehouse_id BIGINT," +
                    "io_type VARCHAR(20) COMMENT '入库/出库', io_date DATE, status VARCHAR(20) DEFAULT '已确认'," +
                    "remark VARCHAR(500), company_id BIGINT, create_time DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                    "INDEX idx_company_id (company_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
                jdbcTemplate.execute("CREATE TABLE outsource_other_io_item (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, other_io_id BIGINT," +
                    "material_id BIGINT, material_name VARCHAR(100), material_type VARCHAR(50)," +
                    "unit VARCHAR(20), quantity DECIMAL(18,4), unit_price DECIMAL(18,4) DEFAULT 0," +
                    "remark VARCHAR(500), company_id BIGINT, INDEX idx_other_io_id (other_io_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
                log.info("已创建 outsource_other_io / outsource_other_io_item 表");
            }
            // 已有表补加 unit_price 列
            Integer cnt2 = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='outsource_other_io_item' AND COLUMN_NAME='unit_price'",
                Integer.class);
            if (cnt2 == null || cnt2 == 0) {
                jdbcTemplate.execute("ALTER TABLE outsource_other_io_item ADD COLUMN unit_price DECIMAL(18,4) DEFAULT 0 COMMENT '单价' AFTER quantity");
                log.info("已为 outsource_other_io_item 添加 unit_price 列");
            }
        } catch (Exception e) { log.warn("DDL 执行异常: {}", e.getMessage()); }
        // outsource_material_order 添加 order_type / target_warehouse_id 列
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='outsource_material_order' AND COLUMN_NAME='order_type'",
                Integer.class);
            if (cnt == null || cnt == 0) {
                jdbcTemplate.execute("ALTER TABLE outsource_material_order ADD COLUMN order_type VARCHAR(10) DEFAULT '采购' COMMENT '订单类型：采购/委外' AFTER supplier_id");
                jdbcTemplate.execute("ALTER TABLE outsource_material_order ADD COLUMN target_warehouse_id BIGINT DEFAULT NULL COMMENT '收货目标仓库' AFTER order_type");
                log.info("已为 outsource_material_order 添加 order_type / target_warehouse_id 列");
            }
        } catch (Exception e) { log.warn("DDL 执行异常: {}", e.getMessage()); }
        // supplier 添加账期列
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='supplier' AND COLUMN_NAME='credit_period_months'",
                Integer.class);
            if (cnt == null || cnt == 0) {
                jdbcTemplate.execute("ALTER TABLE supplier ADD COLUMN credit_period_months INT DEFAULT NULL COMMENT '账期(月)' AFTER related_supplier_id");
                jdbcTemplate.execute("ALTER TABLE supplier ADD COLUMN credit_period INT DEFAULT NULL COMMENT '账期(天)' AFTER credit_period_months");
                log.info("已为 supplier 添加账期列");
            }
        } catch (Exception e) { log.warn("DDL 执行异常: {}", e.getMessage()); }
        // finance_payable 添加 source_id 列（关联来源记录ID，用于编辑/删除时定位）
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='finance_payable' AND COLUMN_NAME='source_id'",
                Integer.class);
            if (cnt == null || cnt == 0) {
                jdbcTemplate.execute("ALTER TABLE finance_payable ADD COLUMN source_id BIGINT DEFAULT NULL COMMENT '来源记录ID' AFTER source_bill_no");
                log.info("已为 finance_payable 添加 source_id 列");
            }
        } catch (Exception e) { log.warn("DDL 执行异常: {}", e.getMessage()); }
        // finance_payment 添加 attach_url 列（付款凭证）
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='finance_payment' AND COLUMN_NAME='attach_url'",
                Integer.class);
            if (cnt == null || cnt == 0) {
                jdbcTemplate.execute("ALTER TABLE finance_payment ADD COLUMN attach_url VARCHAR(500) DEFAULT NULL COMMENT '付款凭证' AFTER remark");
                log.info("已为 finance_payment 添加 attach_url 列");
            }
        } catch (Exception e) { log.warn("DDL 执行异常: {}", e.getMessage()); }
        // dev_bom 添加 material_id 列
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='dev_bom' AND COLUMN_NAME='material_id'",
                Integer.class);
            if (cnt == null || cnt == 0) {
                jdbcTemplate.execute("ALTER TABLE dev_bom ADD COLUMN material_id BIGINT DEFAULT NULL COMMENT '关联物料ID' AFTER supplier_id");
                log.info("已为 dev_bom 添加 material_id 列");
            }
        } catch (Exception e) { log.warn("DDL 执行异常: {}", e.getMessage()); }
        // material 添加 company_id 列
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='material' AND COLUMN_NAME='company_id'",
                Integer.class);
            if (cnt == null || cnt == 0) {
                jdbcTemplate.execute("ALTER TABLE material ADD COLUMN company_id BIGINT DEFAULT NULL COMMENT '公司ID' AFTER remark");
                jdbcTemplate.execute("UPDATE material SET company_id = 1 WHERE company_id IS NULL");
                log.info("已为 material 添加 company_id 列并回填默认值");
            }
        } catch (Exception e) { log.warn("DDL 执行异常: {}", e.getMessage()); }
        // 移除 supplier 表的 brand 和 material_type 列（未使用）
        try { jdbcTemplate.execute("ALTER TABLE supplier DROP COLUMN IF EXISTS brand"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("ALTER TABLE supplier DROP COLUMN IF EXISTS material_type"); } catch (Exception ignored) {}
        // 供应商类型支持多值（逗号分隔），扩展列长
        try { jdbcTemplate.execute("ALTER TABLE supplier MODIFY COLUMN supplier_type VARCHAR(100) NOT NULL COMMENT '供应商类型(逗号分隔多值)'"); } catch (Exception ignored) {}
    }

    /** 增量 DDL：库存表按 material_id 维度重构 + 唯一键补 company_id + 可用量字段 */
    private void alterInventoryStockTable() {
        try {
            // 1) 添加 material_id 列
            Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_warehouse_stock' AND COLUMN_NAME='material_id'", Integer.class);
            if (cnt == null || cnt == 0) {
                jdbcTemplate.execute("ALTER TABLE inventory_warehouse_stock ADD COLUMN material_id BIGINT DEFAULT NULL COMMENT '物料ID' AFTER product_name");
                jdbcTemplate.execute("ALTER TABLE inventory_warehouse_stock ADD INDEX idx_material_id (material_id)");
                log.info("已添加 inventory_warehouse_stock.material_id 列及索引");
            }
        } catch (Exception e) { log.warn("DDL 执行异常: {}", e.getMessage()); }
        try {
            // 2) 添加 available_quantity 列
            Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_warehouse_stock' AND COLUMN_NAME='available_quantity'", Integer.class);
            if (cnt == null || cnt == 0) {
                jdbcTemplate.execute("ALTER TABLE inventory_warehouse_stock ADD COLUMN available_quantity DECIMAL(18,4) DEFAULT 0 COMMENT '可用数量' AFTER quantity");
                log.info("已添加 inventory_warehouse_stock.available_quantity 列");
            }
        } catch (Exception e) { log.warn("DDL 执行异常: {}", e.getMessage()); }
        try {
            // 3) 修改唯一键：补 company_id 并增加 material_id 维度的唯一约束
            Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_warehouse_stock' AND INDEX_NAME='uk_warehouse_product_company'", Integer.class);
            if (cnt == null || cnt == 0) {
                // 先尝试删除旧唯一键（忽略不存在的错误）
                try { jdbcTemplate.execute("ALTER TABLE inventory_warehouse_stock DROP INDEX uk_warehouse_product"); } catch (Exception ignored) {}
                try { jdbcTemplate.execute("ALTER TABLE inventory_warehouse_stock ADD UNIQUE KEY uk_warehouse_product_company (warehouse_id, product_name, company_id)"); } catch (Exception e) { log.warn("添加 uk_warehouse_product_company 失败: {}", e.getMessage()); }
                try { jdbcTemplate.execute("ALTER TABLE inventory_warehouse_stock ADD UNIQUE KEY uk_warehouse_material_company (warehouse_id, material_id, company_id)"); } catch (Exception e) { log.warn("添加 uk_warehouse_material_company 失败: {}", e.getMessage()); }
                log.info("已更新 inventory_warehouse_stock 唯一键");
            }
        } catch (Exception e) { log.warn("DDL 执行异常: {}", e.getMessage()); }
    }

    /** 初始化默认公司 */
    private void initCompany() {
        try {
            Integer cnt = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_company", Integer.class);
            if (cnt == null || cnt == 0) {
                jdbcTemplate.update("INSERT INTO sys_company (company_name, status) VALUES ('北辰科技', 1)");
                log.info("已初始化默认公司：北辰科技");
            }
        } catch (Exception e) {
            log.warn("初始化公司异常: {}", e.getMessage());
        }
    }

    private void initRoles() {
        insertRoleIfNotExist("超级管理员", "super_admin", 1, "系统超级管理员");
        insertRoleIfNotExist("管理员", "admin", 1, "系统管理员");
        insertRoleIfNotExist("普通用户", "user", 1, "普通用户");
    }

    private void insertRoleIfNotExist(String name, String code, Integer status, String remark) {
        Long count = roleMapper.selectCount(new LambdaQueryWrapper<Role>()
                .eq(Role::getRoleCode, code));
        if (count != null && count > 0) {
            return;
        }
        Role role = new Role();
        role.setRoleName(name);
        role.setRoleCode(code);
        role.setStatus(status);
        role.setRemark(remark);
        roleMapper.insert(role);
        log.info("初始化角色完成: {} ({})", name, code);
    }

    private void initSuperAdmin() {
        // 清理旧的默认 admin 账号（迁移用，确保系统不再保留 admin）
        userMapper.delete(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, "admin"));

        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, "lin"));
        if (count != null && count > 0) {
            log.info("超级管理员 lin 已存在，跳过初始化");
            ensureLinRole();
            return;
        }
        User user = new User();
        user.setUsername("lin");
        user.setPassword(passwordEncoder.encode("123"));
        user.setStatus(1);
        userMapper.insert(user);
        ensureLinRole();
        log.info("初始化超级管理员 lin 完成（角色: super_admin）");
    }

    /**
     * 确保 lin 用户与 super_admin 角色已建立关联
     */
    private void ensureLinRole() {
        User lin = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, "lin"));
        if (lin == null) {
            return;
        }
        Role superAdmin = roleMapper.selectOne(new LambdaQueryWrapper<Role>()
                .eq(Role::getRoleCode, "super_admin"));
        if (superAdmin == null) {
            return;
        }
        Long count = userRoleMapper.selectCount(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, lin.getId())
                .eq(UserRole::getRoleId, superAdmin.getId()));
        if (count != null && count > 0) {
            return;
        }
        UserRole ur = new UserRole();
        ur.setUserId(lin.getId());
        ur.setRoleId(superAdmin.getId());
        userRoleMapper.insert(ur);
    }

    private void initMenus() {
        Long count = menuMapper.selectCount(null);
        if (count != null && count > 0) {
            log.info("菜单数据已存在，跳过初始化");
            return;
        }
        // 一级菜单
        saveMenu(1L, 0L, "首页", "menu", "/dashboard", "Dashboard", "HomeFilled", 1);
        saveMenu(50L, 0L, "基础数据", "catalog", "", "", "DataBoard", 2);
        saveMenu(2L, 0L, "研发管理", "catalog", "", "", "Cpu", 3);
        saveMenu(4L, 0L, "委外加工", "catalog", "", "", "Setting", 4);
        saveMenu(5L, 0L, "进销存", "catalog", "", "", "Goods", 5);
        saveMenu(6L, 0L, "财务管理", "catalog", "", "", "Money", 6);
        saveMenu(7L, 0L, "设置", "catalog", "", "", "Tools", 7);
        // 研发管理子菜单
        saveMenu(8L, 2L, "研发项目", "menu", "/dev/project", "DevProject", "Notebook", 1);
        saveMenu(10L, 2L, "图纸文档", "menu", "/dev/drawing", "DevDrawing", "Files", 2);
        saveMenu(9L, 2L, "BOM管理", "menu", "/dev/bom", "DevBom", "Tickets", 3);
        // 基础数据子菜单
        saveMenu(49L, 50L, "供应商管理", "menu", "/supplier/manage", "SupplierManage", "UserFilled", 1);
        saveMenu(18L, 50L, "客户管理", "menu", "/inventory/customer", "InventoryCustomer", "User", 2);
        saveMenu(19L, 50L, "产品管理", "menu", "/material", "MaterialManage", "TakeawayBox", 3);
        saveMenu(39L, 50L, "品牌管理", "menu", "/inventory/brand", "InventoryBrand", "CollectionTag", 4);
        saveMenu(36L, 50L, "仓库管理", "menu", "/inventory/warehouse", "InventoryWarehouse", "Odometer", 5);
        saveMenu(33L, 50L, "BOM类型", "menu", "/dev/bom-type", "DevBomType", "Tickets", 6);
        // 委外加工子菜单
        saveMenu(15L, 4L, "委外加工单", "menu", "/outsource/order", "OutsourceOrder", "Document", 1);
        saveMenu(44L, 4L, "委外物料订单", "menu", "/outsource/material-order", "OutsourceMaterialOrder", "ShoppingCart", 2);
        saveMenu(16L, 4L, "物料信息", "menu", "/outsource/material-info", "OutsourceMaterialInfo", "Switch", 3);
        saveMenu(35L, 4L, "委外仓库", "menu", "/outsource/warehouse", "OutsourceWarehouse", "Odometer", 4);
        saveMenu(37L, 4L, "加工合同模板", "menu", "/outsource/contract-template", "OutsourceContractTemplate", "Document", 5);
        saveMenu(48L, 4L, "物料收发单", "menu", "/outsource/delivery", "OutsourceDelivery", "Tickets", 6);
        // 进销存子菜单
        saveMenu(20L, 5L, "采购单", "menu", "/inventory/purchase", "InventoryPurchase", "ShoppingCart", 1);
        saveMenu(22L, 5L, "成品库存", "menu", "/inventory/stock", "InventoryStock", "Odometer", 2);
        saveMenu(23L, 5L, "销售单", "menu", "/inventory/sale", "InventorySale", "Sell", 3);
        // 财务管理子菜单
        saveMenu(25L, 6L, "应收管理", "menu", "/finance/receivable", "FinanceReceivable", "Wallet", 1);
        saveMenu(26L, 6L, "应付管理", "menu", "/finance/payable", "FinancePayable", "CreditCard", 2);
        saveMenu(27L, 6L, "账单生成", "menu", "/finance/bill", "FinanceBill", "Postcard", 3);
        saveMenu(28L, 6L, "资金流水", "menu", "/finance/cashflow", "FinanceCashflow", "TrendCharts", 4);
        // 设置子菜单
        saveMenu(40L, 7L, "智能管理", "menu", "/system/smart", "SystemSmart", "Cpu", 1);
        saveMenu(41L, 7L, "系统设置", "catalog", "", "", "Tools", 2);
        saveMenu(29L, 41L, "用户管理", "menu", "/system/user", "SystemUser", "UserFilled", 1);
        saveMenu(30L, 41L, "角色管理", "menu", "/system/role", "SystemRole", "Avatar", 2);
        saveMenu(31L, 41L, "菜单管理", "menu", "/system/menu", "SystemMenu", "Menu", 3);
        saveMenu(42L, 7L, "清空数据", "menu", "/system/clear-data", "SystemClearData", "Delete", 3);
        log.info("初始化菜单数据完成");
    }

    private void saveMenu(Long id, Long parentId, String menuName, String menuType,
                          String routePath, String routeName, String icon, Integer sortOrder) {
        Menu menu = new Menu();
        menu.setId(id);
        menu.setParentId(parentId);
        menu.setMenuName(menuName);
        menu.setMenuType(menuType);
        menu.setRoutePath(routePath);
        menu.setRouteName(routeName);
        menu.setIcon(icon);
        menu.setSortOrder(sortOrder);
        menu.setVisible(1);
        menu.setStatus(1);
        menuMapper.insert(menu);
    }

    private void initRoleMenus() {
        Role superAdminRole = roleMapper.selectOne(new LambdaQueryWrapper<Role>()
                .eq(Role::getRoleCode, "super_admin"));
        Role adminRole = roleMapper.selectOne(new LambdaQueryWrapper<Role>()
                .eq(Role::getRoleCode, "admin"));
        Role userRole = roleMapper.selectOne(new LambdaQueryWrapper<Role>()
                .eq(Role::getRoleCode, "user"));

        if (superAdminRole != null) {
            List<Long> existingMenuIds = roleService.getMenuIdsByRoleId(superAdminRole.getId());
            if (existingMenuIds == null || existingMenuIds.isEmpty()) {
                roleService.saveRoleMenus(superAdminRole.getId(), Arrays.asList(
                        1L, 2L, 4L, 5L, 6L, 7L,
                        8L, 9L, 10L,
                        49L, 50L, 18L, 19L, 33L, 36L, 39L,
                        15L, 16L, 35L, 37L, 44L, 48L,
                        20L, 22L, 23L,
                        25L, 26L, 27L, 28L,
                        29L, 30L, 31L, 40L, 41L, 42L));
                log.info("初始化 super_admin 菜单权限完成");
            }
        }
        if (adminRole != null) {
            List<Long> existingMenuIds = roleService.getMenuIdsByRoleId(adminRole.getId());
            if (existingMenuIds == null || existingMenuIds.isEmpty()) {
                roleService.saveRoleMenus(adminRole.getId(), Arrays.asList(
                        1L, 2L, 4L, 5L, 6L, 7L,
                        8L, 9L, 10L,
                        49L, 50L, 18L, 19L, 33L, 36L, 39L,
                        15L, 16L, 35L, 37L, 44L, 48L,
                        20L, 22L, 23L,
                        25L, 26L, 27L, 28L,
                        29L, 30L, 33L, 40L, 41L, 42L));
                log.info("初始化 admin 菜单权限完成");
            }
        }
        if (userRole != null) {
            List<Long> existingMenuIds = roleService.getMenuIdsByRoleId(userRole.getId());
            if (existingMenuIds == null || existingMenuIds.isEmpty()) {
                roleService.saveRoleMenus(userRole.getId(), Arrays.asList(
                        1L, 5L, 18L, 19L, 20L, 22L, 23L, 36L));
                log.info("初始化 user 菜单权限完成");
            }
        }
    }

    private void initMaterials() {
        Long count = materialMapper.selectCount(null);
        if (count != null && count > 0) {
            log.info("物料数据已存在，跳过初始化");
            return;
        }
      
        log.info("初始化示例物料数据完成（共 3 条）");
    }

    private void initBomTypes() {
        Long count = bomTypeMapper.selectCount(null);
        if (count != null && count > 0) {
            log.info("BOM类型数据已存在，跳过初始化");
            return;
        }
        String[] defaultTypes = {"玻璃", "驱动IC", "码片IC", "触摸IC", "排线", "背贴", "盖板"};
        for (int i = 0; i < defaultTypes.length; i++) {
            BomType bt = new BomType();
            bt.setTypeName(defaultTypes[i]);
            bt.setSortOrder(i + 1);
            bt.setStatus(1);
            bt.setCompanyId(1L);
            bomTypeMapper.insert(bt);
        }
        log.info("初始化BOM类型数据完成（共 {} 条）", defaultTypes.length);
    }

    private void saveMaterial(String code, String name, String category, String spec, String unit,
                              BigDecimal safetyStock, BigDecimal currentStock) {
        Material m = new Material();
        m.setCode(code);
        m.setName(name);
        m.setCategory(category);
        m.setSpec(spec);
        m.setUnit(unit);
        m.setSafetyStock(safetyStock);
        m.setCurrentStock(currentStock);
        m.setStatus("正常");
        materialMapper.insert(m);
    }

    /**
     * 同步菜单：用 ON DUPLICATE KEY UPDATE 确保所有标准菜单存在且字段最新。
     * 会更新已存在菜单的 parent_id 等字段，适用于菜单结构变更后同步已有数据库。
     */
    private void syncMenus() {
        // 标准菜单定义: {id, parent_id, name, type, route_path, route_name, icon, sort_order}
        Object[][] menus = {
            {1L, 0L, "首页", "menu", "/dashboard", "Dashboard", "HomeFilled", 1},
            {50L, 0L, "基础数据", "catalog", "", "", "DataBoard", 2},
            {2L, 0L, "研发管理", "catalog", "", "", "Cpu", 3},
            {4L, 0L, "委外加工", "catalog", "", "", "Setting", 4},
            {5L, 0L, "进销存", "catalog", "", "", "Goods", 5},
            {6L, 0L, "财务管理", "catalog", "", "", "Money", 6},
            {7L, 0L, "设置", "catalog", "", "", "Tools", 7},
            {8L, 2L, "研发项目", "menu", "/dev/project", "DevProject", "Notebook", 1},
            {10L, 2L, "图纸文档", "menu", "/dev/drawing", "DevDrawing", "Files", 2},
            {9L, 2L, "BOM管理", "menu", "/dev/bom", "DevBom", "Tickets", 3},
            {49L, 50L, "供应商管理", "menu", "/supplier/manage", "SupplierManage", "UserFilled", 1},
            {18L, 50L, "客户管理", "menu", "/inventory/customer", "InventoryCustomer", "User", 2},
            {19L, 50L, "产品管理", "menu", "/material", "MaterialManage", "TakeawayBox", 3},
            {39L, 50L, "品牌管理", "menu", "/inventory/brand", "InventoryBrand", "CollectionTag", 4},
            {36L, 50L, "仓库管理", "menu", "/inventory/warehouse", "InventoryWarehouse", "Odometer", 5},
            {33L, 50L, "BOM类型", "menu", "/dev/bom-type", "DevBomType", "Tickets", 6},
            {15L, 4L, "委外加工单", "menu", "/outsource/order", "OutsourceOrder", "Document", 1},
            {44L, 4L, "委外物料订单", "menu", "/outsource/material-order", "OutsourceMaterialOrder", "ShoppingCart", 2},
            {16L, 4L, "物料信息", "menu", "/outsource/material-info", "OutsourceMaterialInfo", "Switch", 3},
            {48L, 4L, "物料收发单", "menu", "/outsource/delivery", "OutsourceDelivery", "Tickets", 4},
            {51L, 4L, "物料其他出入库", "menu", "/outsource/other-io", "OutsourceOtherIo", "Files", 5},
            {35L, 4L, "委外仓库", "menu", "/outsource/warehouse", "OutsourceWarehouse", "Odometer", 6},
            {37L, 4L, "加工合同模板", "menu", "/outsource/contract-template", "OutsourceContractTemplate", "Document", 7},
            {20L, 5L, "采购单", "menu", "/inventory/purchase", "InventoryPurchase", "ShoppingCart", 1},
            {22L, 5L, "成品库存", "menu", "/inventory/stock", "InventoryStock", "Odometer", 2},
            {23L, 5L, "销售单", "menu", "/inventory/sale", "InventorySale", "Sell", 3},
            {52L, 5L, "其他出入库", "menu", "/inventory/other-io", "InventoryOtherIo", "Files", 4},
            {25L, 6L, "应收管理", "menu", "/finance/receivable", "FinanceReceivable", "Wallet", 1},
            {26L, 6L, "应付管理", "menu", "/finance/payable", "FinancePayable", "CreditCard", 2},
            {27L, 6L, "账单生成", "menu", "/finance/bill", "FinanceBill", "Postcard", 3},
            {28L, 6L, "资金流水", "menu", "/finance/cashflow", "FinanceCashflow", "TrendCharts", 4},
            {53L, 6L, "付款管理", "menu", "/finance/payment", "FinancePayment", "Money", 5},
            {40L, 7L, "智能管理", "menu", "/system/smart", "SystemSmart", "Cpu", 1},
            {41L, 7L, "系统设置", "catalog", "", "", "Tools", 2},
            {29L, 41L, "用户管理", "menu", "/system/user", "SystemUser", "UserFilled", 1},
            {30L, 41L, "角色管理", "menu", "/system/role", "SystemRole", "Avatar", 2},
            {31L, 41L, "菜单管理", "menu", "/system/menu", "SystemMenu", "Menu", 3},
            {42L, 7L, "清空数据", "menu", "/system/clear-data", "SystemClearData", "Delete", 3},
        };
        // 使用 ON DUPLICATE KEY UPDATE 实现 upsert，确保已存在菜单的 parent_id 等字段也能更新
        int processed = 0;
        for (Object[] m : menus) {
            try {
                jdbcTemplate.update(
                    "INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, route_path, route_name, icon, sort_order, visible, status) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, 1) " +
                    "ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id), menu_name=VALUES(menu_name), " +
                    "menu_type=VALUES(menu_type), route_path=VALUES(route_path), route_name=VALUES(route_name), " +
                    "icon=VALUES(icon), sort_order=VALUES(sort_order), visible=1, status=1",
                    m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7]);
                processed++;
            } catch (Exception e) {
                log.warn("同步菜单失败: id={}, err={}", m[0], e.getMessage());
            }
        }
        log.info("同步菜单完成，处理 {} 条", processed);

        // 删除已废弃的旧菜单及其角色关联（方案商/委外加工厂/成品供应商/辅料商 已合并到供应商管理；43L已被48L替代）
        Long[] obsoleteMenuIds = {11L, 12L, 13L, 14L, 43L};
        for (Long menuId : obsoleteMenuIds) {
            try {
                jdbcTemplate.update("DELETE FROM sys_role_menu WHERE menu_id = ?", menuId);
                jdbcTemplate.update("DELETE FROM sys_menu WHERE id = ?", menuId);
            } catch (Exception e) {
                log.warn("删除废弃菜单失败: id={}, err={}", menuId, e.getMessage());
            }
        }
        log.info("已清理 {} 个废弃菜单", obsoleteMenuIds.length);

        // 始终为所有标准菜单授权给 super_admin 和 admin（每次启动都确保授权完整）
        for (Object[] m : menus) {
            try {
                jdbcTemplate.update(
                    "INSERT IGNORE INTO sys_role_menu (role_id, menu_id) " +
                    "SELECT r.id, ? FROM sys_role r WHERE r.role_code IN ('super_admin', 'admin')",
                    m[0]);
            } catch (Exception ignored) {}
        }
        log.info("已为管理员角色授权标准菜单");
    }
}

