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

    @Override
    public void run(ApplicationArguments args) {
        initRoles();
        initMenus();
        initRoleMenus();
        initSuperAdmin();
        initBomTypes();
        initMaterials();
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
        saveMenu(3L, 0L, "供应商管理", "catalog", "", "", "Shop", 3);
        saveMenu(4L, 0L, "委外加工", "catalog", "", "", "Setting", 4);
        saveMenu(5L, 0L, "进销存", "catalog", "", "", "Goods", 5);
        saveMenu(6L, 0L, "财务管理", "catalog", "", "", "Money", 6);
        saveMenu(7L, 0L, "设置", "catalog", "", "", "Tools", 7);
        // 开发管理子菜单
        saveMenu(8L, 2L, "研发项目", "menu", "/dev/project", "DevProject", "Notebook", 1);
        saveMenu(9L, 2L, "BOM管理", "menu", "/dev/bom", "DevBom", "Tickets", 2);
        saveMenu(10L, 2L, "图纸文档", "menu", "/dev/drawing", "DevDrawing", "Files", 3);
        // 供应商管理子菜单
        saveMenu(11L, 3L, "方案商", "menu", "/supplier/solution", "SupplierSolution", "Connection", 1);
        saveMenu(12L, 3L, "委外加工厂", "menu", "/supplier/factory", "SupplierFactory", "OfficeBuilding", 2);
        saveMenu(13L, 3L, "成品供应商", "menu", "/supplier/product", "SupplierProduct", "GoodsFilled", 3);
        saveMenu(14L, 3L, "辅料商", "menu", "/supplier/material-supplier", "SupplierMaterialSupplier", "Box", 4);
        // 委外加工子菜单
        saveMenu(15L, 4L, "委外加工单", "menu", "/outsource/order", "OutsourceOrder", "Document", 1);
        saveMenu(16L, 4L, "物料信息", "menu", "/outsource/material-info", "OutsourceMaterialInfo", "Switch", 2);
        // 进销存子菜单
        saveMenu(18L, 5L, "客户管理", "menu", "/inventory/customer", "InventoryCustomer", "User", 1);
        saveMenu(19L, 5L, "物料主数据", "menu", "/inventory/material", "InventoryMaterial", "TakeawayBox", 2);
        saveMenu(20L, 5L, "采购订单", "menu", "/inventory/purchase", "InventoryPurchase", "ShoppingCart", 3);
        saveMenu(21L, 5L, "采购入库", "menu", "/inventory/inbound", "InventoryInbound", "Download", 4);
        saveMenu(22L, 5L, "成品库存", "menu", "/inventory/stock", "InventoryStock", "Odometer", 5);
        saveMenu(23L, 5L, "销售订单", "menu", "/inventory/sale", "InventorySale", "Sell", 6);
        saveMenu(24L, 5L, "销售出库", "menu", "/inventory/outbound", "InventoryOutbound", "Upload", 7);
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
        log.info("初始化菜单数据完成（共 34 条）");
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
                        15L, 16L, 18L, 19L, 20L, 21L,
                        22L, 23L, 24L, 25L, 26L, 27L, 28L,
                        29L, 30L, 31L, 33L, 35L, 36L, 37L));
                log.info("初始化 super_admin 菜单权限完成");
            }
        }
        if (adminRole != null) {
            List<Long> existingMenuIds = roleService.getMenuIdsByRoleId(adminRole.getId());
            if (existingMenuIds == null || existingMenuIds.isEmpty()) {
                roleService.saveRoleMenus(adminRole.getId(), Arrays.asList(
                        1L, 2L, 3L, 4L, 5L, 6L, 7L,
                        8L, 9L, 10L, 11L, 12L, 13L, 14L,
                        15L, 16L, 18L, 19L, 20L, 21L,
                        22L, 23L, 24L, 25L, 26L, 27L, 28L,
                        29L, 30L, 33L, 35L, 36L, 37L));
                log.info("初始化 admin 菜单权限完成");
            }
        }
        if (userRole != null) {
            List<Long> existingMenuIds = roleService.getMenuIdsByRoleId(userRole.getId());
            if (existingMenuIds == null || existingMenuIds.isEmpty()) {
                roleService.saveRoleMenus(userRole.getId(), Arrays.asList(
                        1L, 5L, 18L, 19L, 20L, 21L, 22L, 23L, 24L, 36L));
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
}
