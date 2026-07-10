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
        alterDeliveryTable();
        initCompany();
        initRoles();
        initMenus();
        fixMaterialMenu();
        fixPurchaseMenu();
        fixSaleMenu();
        fixMenuStructure();
        removeObsoleteMenus();
        initRoleMenus();
        initSuperAdmin();
        initBomTypes();
        initMaterials();
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
    }

    /** 初始化默认公司 */
    private void initCompany() {
        try {
            Integer cnt = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_company", Integer.class);
            if (cnt == null || cnt == 0) {
               // jdbcTemplate.update("INSERT INTO sys_company (company_name, status) VALUES (?, ?)", "北辰科技", 1);
               //   log.info("已初始化默认公司：北辰科技");
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
        saveMenu(2L, 0L, "开发管理", "catalog", "", "", "Cpu", 2);
        saveMenu(4L, 0L, "委外加工", "catalog", "", "", "Setting", 4);
        saveMenu(5L, 0L, "进销存", "catalog", "", "", "Goods", 5);
        saveMenu(6L, 0L, "财务管理", "catalog", "", "", "Money", 6);
        saveMenu(7L, 0L, "设置", "catalog", "", "", "Tools", 7);
        // 开发管理子菜单
        saveMenu(8L, 2L, "研发项目", "menu", "/dev/project", "DevProject", "Notebook", 1);
        saveMenu(9L, 2L, "BOM管理", "menu", "/dev/bom", "DevBom", "Tickets", 2);
        saveMenu(10L, 2L, "图纸文档", "menu", "/dev/drawing", "DevDrawing", "Files", 3);
        saveMenu(11L, 2L, "方案商", "menu", "/supplier/solution", "SupplierSolution", "Connection", 5);
        saveMenu(14L, 2L, "辅料商", "menu", "/supplier/material-supplier", "SupplierMaterialSupplier", "Box", 6);
        // 委外加工子菜单
        saveMenu(15L, 4L, "委外加工单", "menu", "/outsource/order", "OutsourceOrder", "Document", 1);
        saveMenu(16L, 4L, "物料信息", "menu", "/outsource/material-info", "OutsourceMaterialInfo", "Switch", 2);
        saveMenu(12L, 4L, "委外加工厂", "menu", "/supplier/factory", "SupplierFactory", "OfficeBuilding", 3);
        // 进销存子菜单
        saveMenu(18L, 5L, "客户管理", "menu", "/inventory/customer", "InventoryCustomer", "User", 1);
        saveMenu(19L, 5L, "产品管理", "menu", "/material", "MaterialManage", "TakeawayBox", 2);
        saveMenu(20L, 5L, "采购单", "menu", "/inventory/purchase", "InventoryPurchase", "ShoppingCart", 3);
        saveMenu(13L, 5L, "成品供应商", "menu", "/supplier/product", "SupplierProduct", "GoodsFilled", 4);
        saveMenu(22L, 5L, "成品库存", "menu", "/inventory/stock", "InventoryStock", "Odometer", 5);
        saveMenu(23L, 5L, "销售单", "menu", "/inventory/sale", "InventorySale", "Sell", 6);
        saveMenu(38L, 5L, "物料BOM", "menu", "/material/bom", "MaterialBom", "Tickets", 8);
        // 财务管理子菜单
        saveMenu(25L, 6L, "应收管理", "menu", "/finance/receivable", "FinanceReceivable", "Wallet", 1);
        saveMenu(26L, 6L, "应付管理", "menu", "/finance/payable", "FinancePayable", "CreditCard", 2);
        saveMenu(27L, 6L, "账单生成", "menu", "/finance/bill", "FinanceBill", "Postcard", 3);
        saveMenu(28L, 6L, "资金流水", "menu", "/finance/cashflow", "FinanceCashflow", "TrendCharts", 4);
        // 设置子菜单
        saveMenu(29L, 7L, "用户管理", "menu", "/system/user", "SystemUser", "UserFilled", 1);
        saveMenu(30L, 7L, "角色管理", "menu", "/system/role", "SystemRole", "Avatar", 2);
        saveMenu(31L, 7L, "菜单管理", "menu", "/system/menu", "SystemMenu", "Menu", 3);
        // 额外菜单（不在固定ID范围）
        saveMenu(33L, 2L, "BOM类型", "menu", "/dev/bom-type", "DevBomType", "Tickets", 4);
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
                        29L, 30L, 31L, 33L, 35L, 36L, 37L, 38L));
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
                        29L, 30L, 33L, 35L, 36L, 37L, 38L));
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
        m.setStatus(1);
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

    /** 移除已废弃的采购入库(21)和销售出库(24)菜单 */
    private void removeObsoleteMenus() {
        try {
            int deleted = jdbcTemplate.update("DELETE FROM sys_role_menu WHERE menu_id IN (21, 24)");
            if (deleted > 0) log.info("已移除废弃菜单的角色关联（{} 条）", deleted);
            int menuDeleted = jdbcTemplate.update("DELETE FROM sys_menu WHERE id IN (21, 24)");
            if (menuDeleted > 0) log.info("已删除废弃菜单（采购入库/销售出库）共 {} 条", menuDeleted);
        } catch (Exception e) {
            log.warn("清理废弃菜单异常: {}", e.getMessage());
        }
    }
}
