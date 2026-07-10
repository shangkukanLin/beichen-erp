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
        fixMaterialMenu();
        fixPurchaseMenu();
        fixSaleMenu();
        fixMenuStructure();
        fixDevMenu();
        fixBrandMenu();
        fixSettingsMenu();
        removeObsoleteMenus();
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
        saveMenu(2L, 0L, "研发管理", "catalog", "", "", "Cpu", 2);
        saveMenu(4L, 0L, "委外加工", "catalog", "", "", "Setting", 4);
        saveMenu(5L, 0L, "进销存", "catalog", "", "", "Goods", 5);
        saveMenu(6L, 0L, "财务管理", "catalog", "", "", "Money", 6);
        saveMenu(7L, 0L, "设置", "catalog", "", "", "Tools", 7);
        // 研发管理子菜单（排序：方案商→BOM类型→研发项目→图纸文档→BOM管理）
        saveMenu(11L, 2L, "方案商", "menu", "/supplier/solution", "SupplierSolution", "Connection", 1);
        saveMenu(33L, 2L, "BOM类型", "menu", "/dev/bom-type", "DevBomType", "Tickets", 2);
        saveMenu(8L, 2L, "研发项目", "menu", "/dev/project", "DevProject", "Notebook", 3);
        saveMenu(10L, 2L, "图纸文档", "menu", "/dev/drawing", "DevDrawing", "Files", 4);
        saveMenu(9L, 2L, "BOM管理", "menu", "/dev/bom", "DevBom", "Tickets", 5);
        // 委外加工子菜单
        saveMenu(15L, 4L, "委外加工单", "menu", "/outsource/order", "OutsourceOrder", "Document", 1);
        saveMenu(16L, 4L, "物料信息", "menu", "/outsource/material-info", "OutsourceMaterialInfo", "Switch", 2);
        saveMenu(12L, 4L, "委外加工厂", "menu", "/supplier/factory", "SupplierFactory", "OfficeBuilding", 3);
        saveMenu(14L, 4L, "辅料商", "menu", "/supplier/material-supplier", "SupplierMaterialSupplier", "Box", 4);
        // 进销存子菜单
        saveMenu(39L, 5L, "品牌管理", "menu", "/inventory/brand", "InventoryBrand", "CollectionTag", 1);
        saveMenu(18L, 5L, "客户管理", "menu", "/inventory/customer", "InventoryCustomer", "User", 2);
        saveMenu(19L, 5L, "产品管理", "menu", "/material", "MaterialManage", "TakeawayBox", 3);
        saveMenu(20L, 5L, "采购单", "menu", "/inventory/purchase", "InventoryPurchase", "ShoppingCart", 4);
        saveMenu(13L, 5L, "成品供应商", "menu", "/supplier/product", "SupplierProduct", "GoodsFilled", 5);
        saveMenu(22L, 5L, "成品库存", "menu", "/inventory/stock", "InventoryStock", "Odometer", 6);
        saveMenu(23L, 5L, "销售单", "menu", "/inventory/sale", "InventorySale", "Sell", 7);
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
        // 额外菜单（不在固定ID范围）
        saveMenu(35L, 4L, "委外仓库", "menu", "/outsource/warehouse", "OutsourceWarehouse", "Odometer", 5);
        saveMenu(36L, 5L, "仓库管理", "menu", "/inventory/warehouse", "InventoryWarehouse", "Odometer", 1);
        saveMenu(37L, 4L, "加工合同模板", "menu", "/outsource/contract-template", "OutsourceContractTemplate", "Document", 6);
        log.info("初始化菜单数据完成（共 32 条）");
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
                        1L, 2L, 3L, 4L, 5L, 6L, 7L,
                        8L, 9L, 10L, 11L, 12L, 13L, 14L,
                        15L, 16L, 18L, 19L, 20L,
                        22L, 23L, 25L, 26L, 27L, 28L,
                        29L, 30L, 31L, 33L, 35L, 36L, 37L, 39L, 40L, 41L, 42L));
                log.info("初始化 super_admin 菜单权限完成");
            }
        }
        if (adminRole != null) {
            List<Long> existingMenuIds = roleService.getMenuIdsByRoleId(adminRole.getId());
            if (existingMenuIds == null || existingMenuIds.isEmpty()) {
                roleService.saveRoleMenus(adminRole.getId(), Arrays.asList(
                        1L, 2L, 3L, 4L, 5L, 6L, 7L,
                        8L, 9L, 10L, 11L, 12L, 13L, 14L,
                        15L, 16L, 18L, 19L, 20L,
                        22L, 23L, 25L, 26L, 27L, 28L,
                        29L, 30L, 33L, 35L, 36L, 37L, 39L, 40L, 41L, 42L));
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
        saveMaterial("GLASS-0601", "LED玻璃原材", "原料", "0.6mm", "片",
                new BigDecimal("100"), new BigDecimal("500"));
        saveMaterial("FPC-0801", "排线", "辅料", "8pin", "条",
                new BigDecimal("200"), new BigDecimal("800"));
        saveMaterial("SCR-1001", "屏幕总成", "成品", "10.1寸", "套",
                new BigDecimal("50"), new BigDecimal("120"));
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
     * 修正物料管理菜单路径（始终执行，确保已有库的菜单指向新页面并改名）
     */
    private void fixMaterialMenu() {
        // 菜单ID=19 始终指向物料/产品管理页面，确保路径和名称正确
        Menu menu = menuMapper.selectById(19L);
        if (menu != null) {
            boolean dirty = false;
            if (!"/material".equals(menu.getRoutePath())) {
                menu.setRoutePath("/material");
                menu.setRouteName("MaterialManage");
                dirty = true;
            }
            if (!"产品管理".equals(menu.getMenuName())) {
                menu.setMenuName("产品管理");
                dirty = true;
            }
            if (dirty) {
                menuMapper.updateById(menu);
                log.info("已修正产品管理菜单");
            }
        }
    }

    /** 修正采购单菜单名称（始终执行） */
    private void fixPurchaseMenu() {
        Menu menu = menuMapper.selectById(20L);
        if (menu != null && !"采购单".equals(menu.getMenuName())) {
            menu.setMenuName("采购单");
            menuMapper.updateById(menu);
            log.info("已修正采购单菜单名称");
        }
    }

    /** 修正销售单菜单名称（始终执行） */
    private void fixSaleMenu() {
        Menu menu = menuMapper.selectById(23L);
        if (menu != null && !"销售单".equals(menu.getMenuName())) {
            menu.setMenuName("销售单");
            menuMapper.updateById(menu);
            log.info("已修正销售单菜单名称");
        }
    }

    /** 重排供应商子菜单归属并删除供应商管理目录（始终执行） */
    private void fixMenuStructure() {
        try {
            // 方案商 → 开发管理
            jdbcTemplate.update("UPDATE sys_menu SET parent_id = 2, sort_order = 5 WHERE id = 11 AND parent_id = 3");
            // 委外加工厂 → 委外加工
            jdbcTemplate.update("UPDATE sys_menu SET parent_id = 4, sort_order = 3 WHERE id = 12 AND parent_id = 3");
            // 成品供应商 → 进销存
            jdbcTemplate.update("UPDATE sys_menu SET parent_id = 5, sort_order = 4 WHERE id = 13 AND parent_id = 3");
            // 辅料商 → 开发管理
            jdbcTemplate.update("UPDATE sys_menu SET parent_id = 2, sort_order = 6 WHERE id = 14 AND parent_id = 3");
            // 删除供应商管理目录
            int deleted = jdbcTemplate.update("DELETE FROM sys_menu WHERE id = 3 AND menu_type = 'catalog'");
            if (deleted > 0) log.info("已删除供应商管理菜单目录");
        } catch (Exception e) {
            log.warn("菜单结构调整异常: {}", e.getMessage());
        }
    }

    /** 移除已废弃的采购入库(21)、销售出库(24)和物料BOM(38)菜单 */
    private void removeObsoleteMenus() {
        try {
            int deleted = jdbcTemplate.update("DELETE FROM sys_role_menu WHERE menu_id IN (21, 24, 38)");
            if (deleted > 0) log.info("已移除废弃菜单的角色关联（{} 条）", deleted);
            int menuDeleted = jdbcTemplate.update("DELETE FROM sys_menu WHERE id IN (21, 24, 38)");
            if (menuDeleted > 0) log.info("已删除废弃菜单（采购入库/销售出库/物料BOM）共 {} 条", menuDeleted);
        } catch (Exception e) {
            log.warn("清理废弃菜单异常: {}", e.getMessage());
        }
    }

    /** 研发管理菜单修正：改名、重排子菜单、移辅料商到委外加工（始终执行） */
    private void fixDevMenu() {
        try {
            // 1) 开发管理 → 研发管理
            jdbcTemplate.update("UPDATE sys_menu SET menu_name = '研发管理' WHERE id = 2 AND menu_name = '开发管理'");
            // 2) 研发管理子菜单重排：方案商(1) → BOM类型(2) → 研发项目(3) → 图纸文档(4) → BOM管理(5)
            jdbcTemplate.update("UPDATE sys_menu SET sort_order = 1 WHERE id = 11 AND parent_id = 2");
            jdbcTemplate.update("UPDATE sys_menu SET sort_order = 2 WHERE id = 33 AND parent_id = 2");
            jdbcTemplate.update("UPDATE sys_menu SET sort_order = 3 WHERE id = 8 AND parent_id = 2");
            jdbcTemplate.update("UPDATE sys_menu SET sort_order = 4 WHERE id = 10 AND parent_id = 2");
            jdbcTemplate.update("UPDATE sys_menu SET sort_order = 5 WHERE id = 9 AND parent_id = 2");
            // 3) 辅料商(id=14) 从研发管理移到委外加工，排第4位
            int moved = jdbcTemplate.update("UPDATE sys_menu SET parent_id = 4, sort_order = 4 WHERE id = 14 AND parent_id = 2");
            if (moved > 0) log.info("已将辅料商菜单移入委外加工");
            log.info("研发管理菜单已修正（改名+重排+辅料商迁移）");
        } catch (Exception e) {
            log.warn("研发管理菜单修正异常: {}", e.getMessage());
        }
    }

    /** 品牌管理菜单补建（已有数据库缺失时自动插入，始终执行） */
    private void fixBrandMenu() {
        try {
            // 1) 插入品牌管理菜单(id=39)，如果不存在
            Integer cnt = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_menu WHERE id = 39", Integer.class);
            if (cnt == null || cnt == 0) {
                jdbcTemplate.update("INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, route_path, route_name, icon, sort_order, visible, status) VALUES (39, 5, '品牌管理', 'menu', '/inventory/brand', 'InventoryBrand', 'CollectionTag', 1, 1, 1)");
                log.info("已创建品牌管理菜单");
            }
            // 2) 重排进销存子菜单：品牌(1)→客户(2)→产品(3)→采购(4)→供应商(5)→库存(6)→销售(7)
            jdbcTemplate.update("UPDATE sys_menu SET sort_order = 2 WHERE id = 18 AND parent_id = 5");
            jdbcTemplate.update("UPDATE sys_menu SET sort_order = 3 WHERE id = 19 AND parent_id = 5");
            jdbcTemplate.update("UPDATE sys_menu SET sort_order = 4 WHERE id = 20 AND parent_id = 5");
            jdbcTemplate.update("UPDATE sys_menu SET sort_order = 5 WHERE id = 13 AND parent_id = 5");
            jdbcTemplate.update("UPDATE sys_menu SET sort_order = 6 WHERE id = 22 AND parent_id = 5");
            jdbcTemplate.update("UPDATE sys_menu SET sort_order = 7 WHERE id = 23 AND parent_id = 5");
            // 3) 给 super_admin 和 admin 角色补上 39L 权限
            for (String code : new String[]{"super_admin", "admin"}) {
                jdbcTemplate.update("INSERT IGNORE INTO sys_role_menu (role_id, menu_id) " +
                    "SELECT r.id, 39 FROM sys_role r WHERE r.role_code = ?", code);
            }
            log.info("品牌管理菜单已修正");
        } catch (Exception e) {
            log.warn("品牌管理菜单修正异常: {}", e.getMessage());
        }
    }

    /** 智能管理+系统设置+清空数据菜单补建及结构调整 */
    private void fixSettingsMenu() {
        try {
            // 1) 补建智能管理(40)
            Integer cnt40 = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_menu WHERE id = 40", Integer.class);
            if (cnt40 == null || cnt40 == 0) {
                jdbcTemplate.update("INSERT INTO sys_menu (id,parent_id,menu_name,menu_type,route_path,route_name,icon,sort_order,visible,status) VALUES (40,7,'智能管理','menu','/system/smart','SystemSmart','Cpu',1,1,1)");
                jdbcTemplate.update("INSERT IGNORE INTO sys_role_menu (role_id,menu_id) SELECT r.id,40 FROM sys_role r WHERE r.role_code IN ('super_admin','admin')");
            }
            // 2) 系统设置(41)改为 catalog，并把用户/角色/菜单管理挂入其下
            jdbcTemplate.update("UPDATE sys_menu SET menu_type='catalog', route_path='', route_name='', sort_order=2 WHERE id=41");
            Integer cnt41 = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_menu WHERE id = 41", Integer.class);
            if (cnt41 == null || cnt41 == 0) {
                jdbcTemplate.update("INSERT INTO sys_menu (id,parent_id,menu_name,menu_type,route_path,route_name,icon,sort_order,visible,status) VALUES (41,7,'系统设置','catalog','','','Tools',2,1,1)");
                jdbcTemplate.update("INSERT IGNORE INTO sys_role_menu (role_id,menu_id) SELECT r.id,41 FROM sys_role r WHERE r.role_code IN ('super_admin','admin')");
            }
            jdbcTemplate.update("UPDATE sys_menu SET parent_id=41, sort_order=1 WHERE id=29 AND parent_id=7");
            jdbcTemplate.update("UPDATE sys_menu SET parent_id=41, sort_order=2 WHERE id=30 AND parent_id=7");
            jdbcTemplate.update("UPDATE sys_menu SET parent_id=41, sort_order=3 WHERE id=31 AND parent_id=7");
            // 3) 补建清空数据(42)
            Integer cnt42 = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_menu WHERE id = 42", Integer.class);
            if (cnt42 == null || cnt42 == 0) {
                jdbcTemplate.update("INSERT INTO sys_menu (id,parent_id,menu_name,menu_type,route_path,route_name,icon,sort_order,visible,status) VALUES (42,7,'清空数据','menu','/system/clear-data','SystemClearData','Delete',3,1,1)");
                jdbcTemplate.update("INSERT IGNORE INTO sys_role_menu (role_id,menu_id) SELECT r.id,42 FROM sys_role r WHERE r.role_code IN ('super_admin','admin')");
            }
            log.info("智能管理+系统设置(catalog)+清空数据菜单已修正");
        } catch (Exception e) {
            log.warn("智能管理+系统设置菜单修正异常: {}", e.getMessage());
        }
    }
}
