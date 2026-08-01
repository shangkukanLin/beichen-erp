package com.beichen.erp.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beichen.erp.auth.entity.User;
import com.beichen.erp.auth.mapper.UserMapper;
import com.beichen.erp.dev.entity.BomType;
import com.beichen.erp.dev.mapper.BomTypeMapper;
import com.beichen.erp.dev.mapper.PhaseTemplateMapper;
import com.beichen.erp.material.entity.Material;
import com.beichen.erp.material.mapper.MaterialMapper;
import com.beichen.erp.system.entity.Menu;
import com.beichen.erp.system.entity.Role;
import com.beichen.erp.system.entity.UserRole;
import com.beichen.erp.system.mapper.MenuMapper;
import com.beichen.erp.system.mapper.RoleMapper;
import com.beichen.erp.system.mapper.UserRoleMapper;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    private final PhaseTemplateMapper phaseTemplateMapper;
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
        initPhaseTemplates();
    }

    private void initSchema() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS sys_user (id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',username VARCHAR(50) UNIQUE NOT NULL COMMENT '登录账号',password VARCHAR(100) NOT NULL COMMENT 'BCrypt加密密码',phone VARCHAR(20) COMMENT '手机号',dept VARCHAR(50) COMMENT '所属部门',status TINYINT DEFAULT 1 COMMENT '1启用 0禁用',company_id BIGINT DEFAULT NULL COMMENT '公司ID',deleted TINYINT DEFAULT 0 COMMENT '0正常 1已删除',create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',INDEX idx_company_id (company_id),INDEX idx_username (username)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS sys_company (id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '公司ID',company_name VARCHAR(100) NOT NULL COMMENT '公司名称',status TINYINT DEFAULT 1 COMMENT '1启用 0禁用',create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间') ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS sys_role (id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '角色ID',role_name VARCHAR(50) NOT NULL COMMENT '角色名称',role_code VARCHAR(50) NOT NULL COMMENT '角色编码',status TINYINT DEFAULT 1 COMMENT '1启用 0禁用',remark VARCHAR(255) DEFAULT NULL COMMENT '备注',create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',company_id BIGINT DEFAULT NULL COMMENT '公司ID',update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',UNIQUE KEY uk_role_code (role_code),INDEX idx_company_id (company_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS sys_user_role (id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',user_id BIGINT NOT NULL COMMENT '用户ID',role_id BIGINT NOT NULL COMMENT '角色ID',UNIQUE KEY uk_user_role (user_id, role_id),INDEX idx_user_id (user_id),INDEX idx_role_id (role_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS sys_menu (id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '菜单ID',parent_id BIGINT DEFAULT 0 COMMENT '父菜单ID',menu_name VARCHAR(50) NOT NULL COMMENT '菜单名称',menu_type VARCHAR(20) NOT NULL COMMENT '类型',route_path VARCHAR(100) DEFAULT '' COMMENT '路由路径',route_name VARCHAR(100) DEFAULT '' COMMENT '路由名称',icon VARCHAR(50) DEFAULT '' COMMENT '图标',sort_order INT DEFAULT 0 COMMENT '排序',visible TINYINT DEFAULT 1 COMMENT '0隐藏 1显示',status TINYINT DEFAULT 1 COMMENT '0禁用 1启用',create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',company_id BIGINT DEFAULT NULL COMMENT '公司ID',update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',INDEX idx_parent_id (parent_id),INDEX idx_company_id (company_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS sys_role_menu (id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',role_id BIGINT NOT NULL COMMENT '角色ID',menu_id BIGINT NOT NULL COMMENT '菜单ID',UNIQUE KEY uk_role_menu (role_id, menu_id),INDEX idx_role_id (role_id),INDEX idx_menu_id (menu_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        log.info("系统核心表创建完成");
    }

    private void safeDDL(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception e) {
            log.warn("safeDDL 执行失败: {} — 错误: {}", sql, e.getMessage());
        }
    }

    /** purchase_order.status: VARCHAR → TINYINT (0=草稿 1=已完成 2=已作废) */
    private void migratePurchaseOrderStatus() {
        try {
            String dataType = jdbcTemplate.queryForObject(
                    "SELECT DATA_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='purchase_order' AND COLUMN_NAME='status'",
                    String.class);
            if (dataType != null && ("varchar".equalsIgnoreCase(dataType) || "char".equalsIgnoreCase(dataType))) {
                jdbcTemplate.execute("UPDATE purchase_order SET status = '0' WHERE status = '草稿'");
                jdbcTemplate.execute("UPDATE purchase_order SET status = '1' WHERE status = '已完成'");
                jdbcTemplate.execute("UPDATE purchase_order SET status = '2' WHERE status = '已作废'");
                jdbcTemplate.execute("ALTER TABLE purchase_order MODIFY COLUMN status TINYINT DEFAULT 0 COMMENT '状态: 0=草稿 1=已完成 2=已作废'");
                log.info("已将 purchase_order.status 从 VARCHAR 迁移到 TINYINT");
            }
        } catch (Exception e) {
            log.warn("purchase_order.status 迁移异常: {}", e.getMessage());
        }
    }

    private void initPurchaseReturnTables() {
        safeDDL("CREATE TABLE IF NOT EXISTS purchase_return (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '退货单ID'," +
                "code VARCHAR(50) NOT NULL UNIQUE COMMENT '退货单号 TH-{date}-{seq}'," +
                "supplier_id BIGINT COMMENT '供应商ID'," +
                "warehouse_id BIGINT COMMENT '出库仓库ID'," +
                "return_date DATE COMMENT '退货日期'," +
                "status TINYINT DEFAULT 0 COMMENT '状态: 0=草稿 1=已完成 2=已作废'," +
                "total_amount DECIMAL(18,4) DEFAULT 0 COMMENT '退货总金额'," +
                "remark VARCHAR(500) COMMENT '备注'," +
                "auditor_id BIGINT COMMENT '审核人ID'," +
                "auditor_name VARCHAR(50) COMMENT '审核人姓名'," +
                "audit_time DATETIME COMMENT '审核时间'," +
                "company_id BIGINT COMMENT '公司ID'," +
                "create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
                "update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成品退货单主表'");
        safeDDL("CREATE TABLE IF NOT EXISTS purchase_return_item (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '明细ID'," +
                "return_id BIGINT NOT NULL COMMENT '退货单ID'," +
                "product_id BIGINT COMMENT '产品ID'," +
                "quantity DECIMAL(18,4) DEFAULT 0 COMMENT '退货数量'," +
                "unit_price DECIMAL(18,4) DEFAULT 0 COMMENT '退货单价'," +
                "amount DECIMAL(18,4) DEFAULT 0 COMMENT '退货金额'," +
                "remark VARCHAR(255) COMMENT '备注'," +
                "company_id BIGINT COMMENT '公司ID'," +
                "create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成品退货明细表'");
    }

    private void dropRedundantItemColumns() {
        String[] actions = {
                "ALTER TABLE purchase_order_item DROP COLUMN IF EXISTS material_code",
                "ALTER TABLE purchase_order_item DROP COLUMN IF EXISTS material_name",
                "ALTER TABLE purchase_order_item DROP COLUMN IF EXISTS spec",
                "ALTER TABLE purchase_order_item DROP COLUMN IF EXISTS unit",
                "ALTER TABLE purchase_inbound_item DROP COLUMN IF EXISTS material_code",
                "ALTER TABLE purchase_inbound_item DROP COLUMN IF EXISTS material_name",
                "ALTER TABLE purchase_inbound_item DROP COLUMN IF EXISTS spec",
                "ALTER TABLE purchase_inbound_item DROP COLUMN IF EXISTS unit",
                "ALTER TABLE sale_order_item DROP COLUMN IF EXISTS material_code",
                "ALTER TABLE sale_order_item DROP COLUMN IF EXISTS material_name",
                "ALTER TABLE sale_order_item DROP COLUMN IF EXISTS spec",
                "ALTER TABLE sale_order_item DROP COLUMN IF EXISTS unit",
                "ALTER TABLE sale_outbound_item DROP COLUMN IF EXISTS material_code",
                "ALTER TABLE sale_outbound_item DROP COLUMN IF EXISTS material_name",
                "ALTER TABLE sale_outbound_item DROP COLUMN IF EXISTS spec",
                "ALTER TABLE sale_outbound_item DROP COLUMN IF EXISTS unit",
                "ALTER TABLE inventory_stock_log DROP COLUMN IF EXISTS material_name",
                "ALTER TABLE inventory_stock_log DROP COLUMN IF EXISTS spec",
                "ALTER TABLE inventory_stock_log DROP COLUMN IF EXISTS unit",
        };
        for (String sql : actions) {
            safeDDL(sql);
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
        // close_report_item 添加 factory_retain_qty 列
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='outsource_order_close_report_item' AND COLUMN_NAME='factory_retain_qty'",
                Integer.class);
            if (cnt == null || cnt == 0) {
                jdbcTemplate.execute("ALTER TABLE outsource_order_close_report_item ADD COLUMN factory_retain_qty DECIMAL(18,4) DEFAULT NULL COMMENT '留存工厂' AFTER material_price");
                log.info("已为 close_report_item 添加 factory_retain_qty 列");
            }
        } catch (Exception e) { log.warn("DDL 执行异常: {}", e.getMessage()); }
        // outsource_return_order 添加 warehouse_id 列
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='outsource_return_order' AND COLUMN_NAME='warehouse_id'",
                Integer.class);
            if (cnt == null || cnt == 0) {
                jdbcTemplate.execute("ALTER TABLE outsource_return_order ADD COLUMN warehouse_id BIGINT DEFAULT NULL COMMENT '成品出库仓' AFTER order_id");
                log.info("已为 outsource_return_order 添加 warehouse_id 列");
            }
        } catch (Exception e) { log.warn("DDL 异常: {}", e.getMessage()); }
        // outsource_order_delivery 添加 product_id 列
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='outsource_order_delivery' AND COLUMN_NAME='product_id'",
                Integer.class);
            if (cnt == null || cnt == 0) {
                jdbcTemplate.execute("ALTER TABLE outsource_order_delivery ADD COLUMN product_id BIGINT DEFAULT NULL COMMENT '关联产品ID' AFTER order_id");
                log.info("已为 outsource_order_delivery 添加 product_id 列");
            }
        } catch (Exception e) { log.warn("DDL 异常: {}", e.getMessage()); }
        // 补齐 outsource_order_material 的 outsource_material_id（用 material_name 匹配）
        try {
            int updated = jdbcTemplate.update(
                "UPDATE outsource_order_material om " +
                "INNER JOIN outsource_material m ON om.material_name = m.material_name " +
                "SET om.outsource_material_id = m.id " +
                "WHERE om.outsource_material_id IS NULL AND om.material_name IS NOT NULL");
            if (updated > 0) log.info("已补齐 {} 条 outsource_order_material 的 outsource_material_id", updated);
        } catch (Exception e) { log.warn("补齐数据失败: {}", e.getMessage()); }
        // 将委外相关表的 material_id 重命名为 outsource_material_id
        String[][] osMaterialRename = {
            {"outsource_order_material", "material_id", "outsource_material_id", "BIGINT"},
            {"outsource_delivery_item", "material_id", "outsource_material_id", "BIGINT"},
            {"outsource_warehouse_stock", "material_id", "outsource_material_id", "BIGINT"},
            {"outsource_stock_log", "material_id", "outsource_material_id", "BIGINT"},
            {"outsource_order_close_report_item", "material_id", "outsource_material_id", "BIGINT"},
            {"outsource_other_io_item", "material_id", "outsource_material_id", "BIGINT"},
            {"outsource_material_component", "parent_material_id", "parent_outsource_material_id", "BIGINT"},
            {"outsource_material_component", "child_material_id", "child_outsource_material_id", "BIGINT"},
            {"outsource_material_order_item", "material_id", "outsource_material_id", "BIGINT"},
        };
        for (String[] r : osMaterialRename) {
            try {
                Integer cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND COLUMN_NAME=?",
                    Integer.class, r[0], r[2]);
                if (cnt == null || cnt == 0) {
                    jdbcTemplate.execute("ALTER TABLE " + r[0] + " ADD COLUMN " + r[2] + " " + r[3] + " DEFAULT NULL COMMENT '委外物料ID' AFTER " + r[1]);
                    jdbcTemplate.execute("UPDATE " + r[0] + " SET " + r[2] + " = " + r[1] + " WHERE " + r[1] + " IS NOT NULL");
                    // 旧列改为 NULLABLE，避免 INSERT 只写新列时报错
                    try { jdbcTemplate.execute("ALTER TABLE " + r[0] + " MODIFY COLUMN " + r[1] + " " + r[3] + " NULL"); } catch (Exception ign) {}
                    log.info("已为 {} 添加 {} 列并回填数据", r[0], r[2]);
                }
            } catch (Exception e) { log.warn("DDL 异常 {}: {}", r[0], e.getMessage()); }
        }
        // 将旧 material_id 列改为 NULLABLE（已添加 outsource_material_id 后，旧列不再强制 NOT NULL）
        for (String[] r : osMaterialRename) {
            try {
                jdbcTemplate.execute("ALTER TABLE " + r[0] + " MODIFY COLUMN " + r[1] + " " + r[3] + " NULL");
            } catch (Exception e) { /* 可能已经改过了 */ }
        }
        // 补齐 dev_phase_template 已存在记录的 company_id
        try {
            jdbcTemplate.update("UPDATE dev_phase_template SET company_id = 1 WHERE company_id IS NULL");
        } catch (Exception ignored) {}
        // dev_phase_template 阶段模板表
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='dev_phase_template'",
                Integer.class);
            if (cnt == null || cnt == 0) {
                jdbcTemplate.execute(
                    "CREATE TABLE dev_phase_template (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "name VARCHAR(50) NOT NULL, " +
                    "default_days INT DEFAULT 0, " +
                    "sort_order INT DEFAULT 0, " +
                    "remark VARCHAR(500), " +
                    "company_id BIGINT) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
                log.info("已创建 dev_phase_template 表");
            }
        } catch (Exception e) { log.warn("DDL 异常: {}", e.getMessage()); }
        // dev_project_timeline 添加 default_days 列
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='dev_project_timeline' AND COLUMN_NAME='default_days'",
                Integer.class);
            if (cnt == null || cnt == 0) {
                jdbcTemplate.execute("ALTER TABLE dev_project_timeline ADD COLUMN default_days INT DEFAULT 0 AFTER sort_order");
                log.info("已为 dev_project_timeline 添加 default_days 列");
            }
        } catch (Exception e) { log.warn("DDL 异常: {}", e.getMessage()); }
        // material 表 → product 表，material_id → product_id
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='product'",
                Integer.class);
            if (cnt == null || cnt == 0) {
                jdbcTemplate.execute("RENAME TABLE material TO product");
                log.info("已将 material 表重命名为 product");
            }
        } catch (Exception e) { log.warn("RENAME TABLE 异常: {}", e.getMessage()); }
        // 所有 inventory/purchase/sale 的 material_id → product_id
        String[][] mToP = {
            {"inventory_warehouse_stock", "material_id", "product_id"},
            {"inventory_stock_log", "material_id", "product_id"},
            {"inventory_stock_take_item", "material_id", "product_id"},
            {"inventory_transfer_item", "material_id", "product_id"},
            {"inventory_other_io_item", "material_id", "product_id"},
            {"purchase_order_item", "material_id", "product_id"},
            {"purchase_inbound_item", "material_id", "product_id"},
            {"sale_order_item", "material_id", "product_id"},
            {"sale_outbound_item", "material_id", "product_id"},
        };
        for (String[] r : mToP) {
            try {
                Integer cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND COLUMN_NAME=?",
                    Integer.class, r[0], r[2]);
                if (cnt == null || cnt == 0) {
                    jdbcTemplate.execute("ALTER TABLE " + r[0] + " CHANGE COLUMN " + r[1] + " " + r[2] + " BIGINT DEFAULT NULL COMMENT '产品ID'");
                    log.info("已将 {}.{} 重命名为 {}", r[0], r[1], r[2]);
                }
            } catch (Exception e) { log.warn("列重命名异常 {}: {}", r[0], e.getMessage()); }
        }
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
        // 供应商类型多对多中间表
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS supplier_type_ref (" +
            "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
            "supplier_id BIGINT NOT NULL COMMENT '供应商ID'," +
            "type_code VARCHAR(20) NOT NULL COMMENT '类型编码'," +
            "company_id BIGINT DEFAULT NULL COMMENT '公司ID'," +
            "UNIQUE KEY uk_supplier_type (supplier_id, type_code)," +
            "INDEX idx_supplier_id (supplier_id)," +
            "INDEX idx_type_code (type_code)," +
            "INDEX idx_company_id (company_id))");
        // 删除旧 supplier_type 列
        try { jdbcTemplate.execute("ALTER TABLE supplier DROP COLUMN IF EXISTS supplier_type"); } catch (Exception ignored) {}
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
        // 使用 INSERT IGNORE 确保新角色存在，不会覆盖旧角色
        jdbcTemplate.update("INSERT IGNORE INTO sys_role (role_name, role_code, status, remark, company_id) VALUES " +
            "('管理员', 'admin', 1, '系统管理员，拥有全部权限', 0), " +
            "('研发工程师', 'dev_engineer', 1, '研发工程师，负责项目研发和BOM管理', 0), " +
            "('销售专员', 'sales', 1, '销售专员，负责销售和客户管理', 0), " +
            "('仓管员', 'warehouse', 1, '仓管员，负责库存和仓库管理', 0), " +
            "('跟单专员', 'merchandiser', 1, '跟单专员，负责委外加工跟进', 0), " +
            "('财务', 'finance', 1, '财务人员，负责应收应付和资金管理', 0)"
        );
        // 清理旧角色（如果存在则删除）
        jdbcTemplate.update("DELETE FROM sys_role WHERE role_code IN ('super_admin', 'user')");
        // 如果 lin 原来是 super_admin，改为 admin
        jdbcTemplate.update("UPDATE sys_user_role ur JOIN sys_role r ON ur.role_id = r.id SET ur.role_id = " +
            "(SELECT id FROM sys_role WHERE role_code = 'admin' LIMIT 1) " +
            "WHERE r.role_code = 'super_admin'");
        log.info("初始化角色数据完成");
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
        Role adminRole = roleMapper.selectOne(new LambdaQueryWrapper<Role>()
                .eq(Role::getRoleCode, "admin"));
        if (adminRole == null) {
            return;
        }
        Long count = userRoleMapper.selectCount(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, lin.getId())
                .eq(UserRole::getRoleId, adminRole.getId()));
        if (count != null && count > 0) {
            return;
        }
        UserRole ur = new UserRole();
        ur.setUserId(lin.getId());
        ur.setRoleId(adminRole.getId());
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
        saveMenu(2L, 0L, "基础数据", "catalog", "", "", "DataBoard", 2);
        saveMenu(3L, 0L, "研发管理", "catalog", "", "", "Cpu", 3);
        saveMenu(4L, 0L, "委外加工", "catalog", "", "", "Setting", 4);
        saveMenu(5L, 0L, "进货业务", "catalog", "", "", "ShoppingCart", 5);
        saveMenu(6L, 0L, "销售业务", "catalog", "", "", "Sell", 6);
        saveMenu(7L, 0L, "库存业务", "catalog", "", "", "Odometer", 7);
        saveMenu(8L, 0L, "财务管理", "catalog", "", "", "Money", 8);
        saveMenu(9L, 0L, "设置", "catalog", "", "", "Tools", 9);
        // 100s 基础数据
        saveMenu(101L, 2L, "产品管理", "menu", "/material", "MaterialManage", "TakeawayBox", 1);
        saveMenu(102L, 2L, "品牌管理", "menu", "/inventory/brand", "InventoryBrand", "CollectionTag", 2);
        saveMenu(103L, 2L, "BOM类型", "menu", "/dev/bom-type", "DevBomType", "Tickets", 3);
        saveMenu(104L, 2L, "阶段模板", "menu", "/dev/phase-template", "DevPhaseTemplate", "Timer", 4);
        // 300s 研发管理
        saveMenu(301L, 3L, "研发项目", "menu", "/dev/project", "DevProject", "Notebook", 1);
        saveMenu(302L, 3L, "BOM管理", "menu", "/dev/bom", "DevBom", "Tickets", 2);
        saveMenu(303L, 3L, "图纸文档", "menu", "/dev/drawing", "DevDrawing", "Files", 3);
        // 400s 委外加工
        saveMenu(401L, 4L, "委外加工单", "menu", "/outsource/order", "OutsourceOrder", "Document", 1);
        saveMenu(402L, 4L, "委外物料订单", "menu", "/outsource/material-order", "OutsourceMaterialOrder", "ShoppingCart", 2);
        saveMenu(403L, 4L, "物料信息管理", "menu", "/outsource/material-info", "OutsourceMaterialInfo", "Switch", 3);
        saveMenu(404L, 4L, "委外仓库", "menu", "/outsource/warehouse", "OutsourceWarehouse", "Odometer", 4);
        saveMenu(405L, 4L, "加工合同模板", "menu", "/outsource/contract-template", "OutsourceContractTemplate", "Document", 5);
        saveMenu(406L, 4L, "物料收发单", "menu", "/outsource/delivery", "OutsourceDelivery", "Tickets", 6);
        saveMenu(407L, 4L, "物料其他出入库", "menu", "/outsource/other-io", "OutsourceOtherIo", "Files", 7);
        saveMenu(408L, 4L, "委外退货", "menu", "/outsource/return-order", "OutsourceReturnOrder", "CircleClose", 8);
        saveMenu(409L, 4L, "供应商管理", "menu", "/outsource/supplier/manage", "OutsourceSupplierManage", "UserFilled", 9);
        // 500s 进货业务
        saveMenu(501L, 5L, "成品采购单", "menu", "/inventory/purchase", "InventoryPurchase", "ShoppingCart", 1);
        saveMenu(502L, 5L, "成品退货单", "menu", "/inventory/purchase-return", "InventoryPurchaseReturn", "Refrigerator", 2);
        saveMenu(503L, 5L, "供应商管理", "menu", "/supplier/manage", "SupplierManage", "UserFilled", 3);
        // 600s 销售业务
        saveMenu(601L, 6L, "销售单", "menu", "/inventory/sale", "InventorySale", "Sell", 1);
        saveMenu(602L, 6L, "客户管理", "menu", "/inventory/customer", "InventoryCustomer", "User", 2);
        // 700s 库存业务
        saveMenu(701L, 7L, "成品库存", "menu", "/inventory/stock", "InventoryStock", "Odometer", 1);
        saveMenu(702L, 7L, "仓库管理", "menu", "/inventory/warehouse", "InventoryWarehouse", "Odometer", 2);
        // 800s 财务管理
        saveMenu(801L, 8L, "应收管理", "menu", "/finance/receivable", "FinanceReceivable", "Wallet", 1);
        saveMenu(802L, 8L, "应付管理", "menu", "/finance/payable", "FinancePayable", "CreditCard", 2);
        saveMenu(803L, 8L, "账单生成", "menu", "/finance/bill", "FinanceBill", "Postcard", 3);
        saveMenu(804L, 8L, "资金流水", "menu", "/finance/cashflow", "FinanceCashflow", "TrendCharts", 4);
        // 900s 设置
        saveMenu(901L, 9L, "智能管理", "menu", "/system/smart", "SystemSmart", "Cpu", 1);
        saveMenu(902L, 9L, "用户管理", "menu", "/system/user", "SystemUser", "UserFilled", 2);
        saveMenu(903L, 9L, "权限管理", "menu", "/system/permission", "SystemPermission", "Lock", 3);
        saveMenu(904L, 9L, "系统信息", "menu", "/system/settings", "SystemSettings", "Setting", 4);
        saveMenu(905L, 9L, "数据管理", "menu", "/system/data-manage", "SystemDataManage", "Folder", 5);
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
        // 管理员：全部权限
        assignRoleMenus("admin", Arrays.asList(
                1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L,
                101L, 102L, 103L, 104L,
                301L, 302L, 303L,
                401L, 402L, 403L, 404L, 405L, 406L, 407L, 408L, 409L,
                501L, 502L,
                601L, 602L,
                701L, 702L,
                801L, 802L, 803L, 804L,
                901L, 902L, 903L, 904L, 905L));
        // 研发工程师：项目研发 + BOM + 基础产品
        assignRoleMenus("dev_engineer", Arrays.asList(
                1L, 3L, 301L, 302L, 101L));
        // 销售专员：销售业务 + 客户 + 产品
        assignRoleMenus("sales", Arrays.asList(
                1L, 6L, 601L, 602L, 101L));
        // 仓管员：进货+库存 + 仓库
        assignRoleMenus("warehouse", Arrays.asList(
                1L, 5L, 7L, 501L, 701L, 702L, 101L));
        // 跟单专员：委外加工全部
        assignRoleMenus("merchandiser", Arrays.asList(
                1L, 4L, 401L, 402L, 403L, 404L, 405L, 406L, 409L, 101L, 602L, 502L, 702L));
        // 财务：财务管理
        assignRoleMenus("finance", Arrays.asList(
                1L, 8L, 801L, 802L, 803L, 804L, 101L));
    }

    private void assignRoleMenus(String roleCode, List<Long> menuIds) {
        Role role = roleMapper.selectOne(new LambdaQueryWrapper<Role>()
                .eq(Role::getRoleCode, roleCode));
        if (role == null) return;
        List<Long> existingMenuIds = roleService.getMenuIdsByRoleId(role.getId());
        if (existingMenuIds == null || existingMenuIds.isEmpty()) {
            roleService.saveRoleMenus(role.getId(), menuIds);
            log.info("初始化 {} 菜单权限完成", roleCode);
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

    private void initPhaseTemplates() {
        // 补齐已有数据的备注（如果备注为空）
        try {
            int updated = jdbcTemplate.update(
                "UPDATE dev_phase_template SET remark = " +
                "CASE name " +
                "WHEN '结构评估' THEN '根据玻璃尺寸和摄像头孔位与R角来综合评估结构是否支持立项。' " +
                "WHEN '立项准备' THEN '根据项目型号收手机，拆分成机板和屏幕分体状态，交给触摸方案公司抓取触摸协议，明确是否可以破解协议以及用哪颗物料可以满足技术标准。' " +
                "WHEN '显示评估' THEN '提供机板和原屏给到显示方案公司，并告知触摸方案商建议使用的触摸IC料号及规格书与触摸原理图，让显示方案公司抓取显示协议，根据手机的分辨率与刷新率和玻璃的分辨率综合评估用哪颗码片物料，以及驱动IC。' " +
                "WHEN '排线图纸' THEN '根据触摸方案公司建议的触摸IC和显示方案公司建议的码片，开始画图纸，一般都可以画，后期一般是谁画的图纸就和谁买码片。' " +
                "WHEN '排线打样' THEN '出图纸后，把图纸给到排线工厂打样，一般打10PCS，码片和触摸IC需要找方案公司提供，哪个公司画的排线图纸就找哪个公司寄码片，触摸公司寄触摸IC。' " +
                "WHEN 'FOG打样' THEN '排线打样好之后直接让工厂寄给打样加工厂，同时需要寄驱动IC过去和玻璃过去，一般先打样5PCS。' " +
                "WHEN '显示调试' THEN 'FOG打样直接寄到显示方案公司，并且提供机板，开始调试显示功能。其他兼容的基板，等没什么大问题再去购买给方案公司做兼容。' " +
                "WHEN '触摸调试' THEN '初版显示做好以后，移交机板和FOG去触摸方案公司调试触摸。同时保留一个机板和FOG去盖板厂根据屏幕的实际显示效果开模做盖板样品，然后去背贴厂开背贴样品。' " +
                "WHEN '背贴盖板打样' THEN '使用保留的一个机板和FOG去盖板厂根据屏幕的实际显示效果开模做盖板样品，然后去背贴厂开背贴样品。' " +
                "WHEN '总成样品' THEN '将盖板和背贴样品寄到加工厂做成总成，需要寄2PCS总成和机板过去方案公司优化触摸。' " +
                "WHEN '测试' THEN '开始测试，需要测试结构/显示/触摸，详见测试文档。' " +
                "WHEN '小批量' THEN '测试没问题之后，下物料寄到工厂，先进行100PCS的小批量，到货后过一遍，没有批次问题，就可以结项了。' " +
                "WHEN '结项' THEN '结项，通知工厂开始量产。' " +
                "END WHERE remark IS NULL OR remark LIKE '%模糊%'");
            if (updated > 0) log.info("已补齐 {} 条阶段模板备注", updated);
        } catch (Exception e) { log.warn("补齐备注失败: {}", e.getMessage()); }
        Long count = phaseTemplateMapper.selectCount(null);
        if (count != null && count > 0) {
            log.info("阶段模板数据已存在，跳过初始化");
            return;
        }
        // 阶段模板：name, defaultDays, sortOrder, remark
        Object[][] defaultPhases = {
            {"立项", 0, 1, ""},
            {"结构评估", 2, 2, "根据玻璃尺寸和摄像头孔位与R角来综合评估结构是否支持立项。"},
            {"立项准备", 5, 3, "根据项目型号收手机，拆分成机板和屏幕分体状态，交给触摸方案公司抓取触摸协议，明确是否可以破解协议以及用哪颗物料可以满足技术标准。"},
            {"显示评估", 2, 4, "提供机板和原屏给到显示方案公司，并告知触摸方案商建议使用的触摸IC料号及规格书与触摸原理图，让显示方案公司抓取显示协议，根据手机的分辨率与刷新率和玻璃的分辨率综合评估用哪颗码片物料，以及驱动IC。"},
            {"排线图纸", 3, 5, "根据触摸方案公司建议的触摸IC和显示方案公司建议的码片，开始画图纸，一般都可以画。"},
            {"排线打样", 4, 6, "出图纸后，把图纸给到排线工厂打样，一般打10PCS，码片和触摸IC需要找方案公司提供。"},
            {"FOG打样", 2, 7, "排线打样好之后直接让工厂寄给打样加工厂，同时需要寄驱动IC过去和玻璃过去，一般先打样5PCS。"},
            {"显示调试", 5, 8, "FOG打样直接寄到显示方案公司，并且提供机板，开始调试显示功能。"},
            {"触摸调试", 5, 9, "初版显示做好以后，移交机板和FOG去触摸方案公司调试触摸。同时保留一个机板和FOG去盖板厂开模做盖板样品。"},
            {"背贴盖板打样", 2, 10, "使用保留的一个机板和FOG去盖板厂根据屏幕的实际显示效果开模做盖板样品，然后去背贴厂开背贴样品。"},
            {"总成样品", 2, 11, "将盖板和背贴样品寄到加工厂做成总成，需要寄2PCS总成和机板过去方案公司优化触摸。"},
            {"测试", 5, 12, "开始测试，需要测试结构/显示/触摸，详见测试文档。"},
            {"小批量", 3, 13, "测试没问题之后，下物料寄到工厂，先进行100PCS的小批量，到货后过一遍，没有批次问题，就可以结项了。"},
            {"结项", 0, 14, "结项，通知工厂开始量产。"}
        };
        for (Object[] p : defaultPhases) {
            com.beichen.erp.dev.entity.PhaseTemplate t = new com.beichen.erp.dev.entity.PhaseTemplate();
            t.setName((String) p[0]);
            t.setDefaultDays((Integer) p[1]);
            t.setSortOrder((Integer) p[2]);
            t.setRemark((String) p[3]);
            t.setCompanyId(1L);
            phaseTemplateMapper.insert(t);
        }
        log.info("初始化阶段模板数据完成（共 {} 条）", defaultPhases.length);
    }

    /**
     * 同步菜单：用 ON DUPLICATE KEY UPDATE 确保所有标准菜单存在且字段最新。
     * 会更新已存在菜单的 parent_id 等字段，适用于菜单结构变更后同步已有数据库。
     */
    private void syncMenus() {
        // 标准菜单定义: {id, parent_id, name, type, route_path, route_name, icon, sort_order}
        Object[][] menus = {
            {1L, 0L, "首页", "menu", "/dashboard", "Dashboard", "HomeFilled", 1},
            {2L, 0L, "基础数据", "catalog", "", "", "DataBoard", 2},
            {3L, 0L, "研发管理", "catalog", "", "", "Cpu", 3},
            {4L, 0L, "委外加工", "catalog", "", "", "Setting", 4},
            {5L, 0L, "进货业务", "catalog", "", "", "ShoppingCart", 5},
            {6L, 0L, "销售业务", "catalog", "", "", "Sell", 6},
            {7L, 0L, "库存业务", "catalog", "", "", "Odometer", 7},
            {8L, 0L, "财务管理", "catalog", "", "", "Money", 8},
            {9L, 0L, "设置", "catalog", "", "", "Tools", 9},
            {101L, 2L, "产品管理", "menu", "/material", "MaterialManage", "TakeawayBox", 1},
            {102L, 2L, "品牌管理", "menu", "/inventory/brand", "InventoryBrand", "CollectionTag", 2},
            {103L, 2L, "BOM类型", "menu", "/dev/bom-type", "DevBomType", "Tickets", 3},
            {104L, 2L, "阶段模板", "menu", "/dev/phase-template", "DevPhaseTemplate", "Timer", 4},
            {301L, 3L, "研发项目", "menu", "/dev/project", "DevProject", "Notebook", 1},
            {302L, 3L, "BOM管理", "menu", "/dev/bom", "DevBom", "Tickets", 2},
            {303L, 3L, "图纸文档", "menu", "/dev/drawing", "DevDrawing", "Files", 3},
            {401L, 4L, "委外加工单", "menu", "/outsource/order", "OutsourceOrder", "Document", 1},
            {402L, 4L, "委外物料订单", "menu", "/outsource/material-order", "OutsourceMaterialOrder", "ShoppingCart", 2},
            {403L, 4L, "物料信息管理", "menu", "/outsource/material-info", "OutsourceMaterialInfo", "Switch", 3},
            {404L, 4L, "委外仓库", "menu", "/outsource/warehouse", "OutsourceWarehouse", "Odometer", 4},
            {405L, 4L, "加工合同模板", "menu", "/outsource/contract-template", "OutsourceContractTemplate", "Document", 5},
            {406L, 4L, "物料收发单", "menu", "/outsource/delivery", "OutsourceDelivery", "Tickets", 6},
            {407L, 4L, "物料其他出入库", "menu", "/outsource/other-io", "OutsourceOtherIo", "Files", 7},
            {408L, 4L, "委外退货", "menu", "/outsource/return-order", "OutsourceReturnOrder", "CircleClose", 8},
            {409L, 4L, "供应商管理", "menu", "/outsource/supplier/manage", "OutsourceSupplierManage", "UserFilled", 9},
            {501L, 5L, "成品采购单", "menu", "/inventory/purchase", "InventoryPurchase", "ShoppingCart", 1},
            {502L, 5L, "成品退货单", "menu", "/inventory/purchase-return", "InventoryPurchaseReturn", "Refrigerator", 2},
            {503L, 5L, "供应商管理", "menu", "/supplier/manage", "SupplierManage", "UserFilled", 3},
            {601L, 6L, "销售单", "menu", "/inventory/sale", "InventorySale", "Sell", 1},
            {602L, 6L, "客户管理", "menu", "/inventory/customer", "InventoryCustomer", "User", 2},
            {701L, 7L, "成品库存", "menu", "/inventory/stock", "InventoryStock", "Odometer", 1},
            {702L, 7L, "仓库管理", "menu", "/inventory/warehouse", "InventoryWarehouse", "Odometer", 2},
            {703L, 7L, "库存流水", "menu", "/inventory/stock-log", "InventoryStockLog", "TrendCharts", 3},
            {704L, 7L, "其他出入库", "menu", "/inventory/other-io", "InventoryOtherIo", "Upload", 4},
            {705L, 7L, "品质重分类", "menu", "/inventory/reclassify", "InventoryReclassify", "Refresh", 5},
            {706L, 7L, "成品移仓单", "menu", "/inventory/warehouse-move", "InventoryWarehouseMove", "Rank", 6},
            {801L, 8L, "应收管理", "menu", "/finance/receivable", "FinanceReceivable", "Wallet", 1},
            {802L, 8L, "应付管理", "menu", "/finance/payable", "FinancePayable", "CreditCard", 2},
            {803L, 8L, "账单生成", "menu", "/finance/bill", "FinanceBill", "Postcard", 3},
            {804L, 8L, "资金流水", "menu", "/finance/cashflow", "FinanceCashflow", "TrendCharts", 4},
            {805L, 8L, "收款管理", "menu", "/finance/receipt", "FinanceReceipt", "Money", 5},
            {806L, 8L, "付款管理", "menu", "/finance/payment", "FinancePayment", "Sell", 6},
            {901L, 9L, "智能管理", "menu", "/system/smart", "SystemSmart", "Cpu", 1},
            {902L, 9L, "用户管理", "menu", "/system/user", "SystemUser", "UserFilled", 2},
            {903L, 9L, "权限管理", "menu", "/system/permission", "SystemPermission", "Lock", 3},
            {904L, 9L, "系统信息", "menu", "/system/settings", "SystemSettings", "Setting", 4},
            {905L, 9L, "数据管理", "menu", "/system/data-manage", "SystemDataManage", "Folder", 5},
            {906L, 9L, "角色管理", "menu", "/system/role", "SystemRole", "Avatar", 6},
            {907L, 9L, "菜单管理", "menu", "/system/menu", "SystemMenu", "Menu", 7},
            {908L, 9L, "清空数据", "menu", "/system/clear-data", "SystemClearData", "Delete", 8},
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

        // 删除所有旧ID菜单（重新编号后旧ID已废弃）
        Long[] newMenuIds = {1L,2L,3L,4L,5L,6L,7L,8L,9L,101L,102L,103L,104L,301L,302L,303L,401L,402L,403L,404L,405L,406L,407L,408L,409L,501L,502L,503L,601L,602L,701L,702L,703L,704L,705L,706L,801L,802L,803L,804L,805L,806L,901L,902L,903L,904L,905L,906L,907L,908L};
        Set<Long> newIds = new HashSet<>(Arrays.asList(newMenuIds));
        jdbcTemplate.update("DELETE FROM sys_role_menu WHERE menu_id NOT IN (" +
            String.join(",", newIds.stream().map(String::valueOf).toArray(String[]::new)) + ")");
        int deleted = jdbcTemplate.update("DELETE FROM sys_menu WHERE id NOT IN (" +
            String.join(",", newIds.stream().map(String::valueOf).toArray(String[]::new)) + ")");
        log.info("已清理 {} 个废弃旧菜单", deleted);

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

