package com.beichen.erp.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beichen.erp.common.DefaultBomTypes;
import com.beichen.erp.common.DefaultContractTemplate;
import com.beichen.erp.auth.entity.User;
import com.beichen.erp.auth.mapper.UserMapper;
import com.beichen.erp.dev.entity.BomType;
import com.beichen.erp.dev.entity.PhaseTemplate;
import com.beichen.erp.dev.mapper.BomTypeMapper;
import com.beichen.erp.dev.mapper.PhaseTemplateMapper;
import com.beichen.erp.outsource.entity.ContractTemplate;
import com.beichen.erp.outsource.mapper.ContractTemplateMapper;
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

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 数据初始化器：启动时自动初始化系统基础数据（角色、菜单、用户、BOM类型、阶段模板、合同模板）。
 * <p>表结构统一由 schema.sql 维护，本类不执行任何建表/加列/数据迁移，仅写入业务初始化数据。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final MenuMapper menuMapper;
    private final RoleService roleService;
    private final BomTypeMapper bomTypeMapper;
    private final PhaseTemplateMapper phaseTemplateMapper;
    private final ContractTemplateMapper contractTemplateMapper;
    private final JdbcTemplate jdbcTemplate;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public void run(ApplicationArguments args) {
        // 清空所有业务数据（保留表结构），仅当启动参数含 --clear-data 时执行
        if (args.containsOption("clear-data")) {
            clearAllData();
            log.info("===== 数据已清空，仅保留表结构 =====");
        }
        initCompany();
        initRoles();
        syncMenus();
        initRoleMenus();
        initSuperAdmin();
        initBomTypes();
        initPhaseTemplates();
        initContractTemplates();
    }

    /** 幂等初始化默认合同模板：加工合同、采购合同各建一条默认模板（无默认模板时才插入） */
    private void initContractTemplates() {
        initContractTemplate(DefaultContractTemplate.TYPE_PROCESSING, DefaultContractTemplate.NAME_PROCESSING, DefaultContractTemplate.PROCESSING_CONTRACT_HTML);
        initContractTemplate(DefaultContractTemplate.TYPE_PURCHASE, DefaultContractTemplate.NAME_PURCHASE, DefaultContractTemplate.PURCHASE_CONTRACT_HTML);
    }

    private void initContractTemplate(String type, String name, String content) {
        // 清理 company_id 为 NULL 的历史脏数据（早期初始化遗漏 companyId 导致）
        contractTemplateMapper.delete(new LambdaQueryWrapper<ContractTemplate>()
                .eq(ContractTemplate::getTemplateType, type)
                .isNull(ContractTemplate::getCompanyId));
        Long count = contractTemplateMapper.selectCount(new LambdaQueryWrapper<ContractTemplate>()
                .eq(ContractTemplate::getTemplateType, type)
                .eq(ContractTemplate::getCompanyId, 1L));
        if (count != null && count > 0) {
            // 已存在默认模板：若 content 仍是旧版含占位符的内容，重置为新的纯条款内容
            resetLegacyTemplate(type, content);
            return;
        }
        ContractTemplate tpl = new ContractTemplate();
        tpl.setTemplateName(name);
        tpl.setContent(content);
        tpl.setTemplateType(type);
        tpl.setStatus(1);
        tpl.setIsDefault(1);
        tpl.setCompanyId(1L);
        contractTemplateMapper.insert(tpl);
        log.info("===== 已初始化默认合同模板：{} =====", type);
    }

    /** 旧版模板 content 含占位符（如 {产品表格}/{签名区}），导出改为固定结构后需重置为纯条款内容 */
    private void resetLegacyTemplate(String type, String content) {
        ContractTemplate existing = contractTemplateMapper.selectOne(new LambdaQueryWrapper<ContractTemplate>()
                .eq(ContractTemplate::getTemplateType, type)
                .eq(ContractTemplate::getCompanyId, 1L)
                .eq(ContractTemplate::getIsDefault, 1)
                .last("LIMIT 1"));
        if (existing == null || existing.getContent() == null) return;
        // 仅当 content 含旧占位符时才重置，避免覆盖用户已自行编辑的条款
        if (existing.getContent().contains("{产品表格}") || existing.getContent().contains("{物料明细表格}")
                || existing.getContent().contains("{签名区}") || existing.getContent().contains("{合同信息}")) {
            ContractTemplate upd = new ContractTemplate();
            upd.setId(existing.getId());
            upd.setContent(content);
            contractTemplateMapper.updateById(upd);
            log.info("===== 已重置旧版默认合同模板内容：{} =====", type);
        }
    }

    /** 幂等补列：为存量库平滑升级（schema.sql 的 CREATE TABLE IF NOT EXISTS 不会给已存在表加列） */
    /** 清空所有业务数据（保留表结构） */
    private void clearAllData() {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        String sql = "SELECT CONCAT('DELETE FROM ', table_name, ';') FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE'";
        jdbcTemplate.queryForList(sql).forEach(row -> {
            jdbcTemplate.execute(row.values().iterator().next().toString());
        });
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
    }

    /** 初始化默认公司：北辰科技 */
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

    /** 初始化6个角色（管理员/研发工程师/销售专员/仓管员/跟单专员/财务） */
    private void initRoles() {
        jdbcTemplate.update("INSERT IGNORE INTO sys_role (role_name, role_code, status, remark, company_id) VALUES " +
            "('管理员', 'admin', 1, '系统管理员，拥有全部权限', 0), " +
            "('研发工程师', 'dev_engineer', 1, '研发工程师，负责项目研发和BOM管理', 0), " +
            "('销售专员', 'sales', 1, '销售专员，负责销售和客户管理', 0), " +
            "('仓管员', 'warehouse', 1, '仓管员，负责库存和仓库管理', 0), " +
            "('跟单专员', 'merchandiser', 1, '跟单专员，负责委外加工跟进', 0), " +
            "('财务', 'finance', 1, '财务人员，负责应收应付和资金管理', 0)"
        );
        log.info("初始化角色数据完成");
    }

    /** 初始化超级管理员 lin（密码123），关联 admin 角色 */
    private void initSuperAdmin() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE username = 'lin'", Long.class);
        if (count != null && count > 0) {
            log.info("超级管理员 lin 已存在，跳过初始化");
            ensureLinRole();
            return;
        }
        jdbcTemplate.update(
                "INSERT INTO sys_user (username, password, status, company_id, deleted, create_time, update_time) " +
                "VALUES (?, ?, 1, 1, 0, NOW(), NOW())",
                "lin", passwordEncoder.encode("123"));
        ensureLinRole();
        log.info("初始化超级管理员 lin 完成（角色: admin）");
    }

    /** 确保 lin 用户与 admin 角色关联 */
    private void ensureLinRole() {
        User lin = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, "lin"));
        if (lin == null) return;

        Role adminRole = roleMapper.selectOne(new LambdaQueryWrapper<Role>()
                .eq(Role::getRoleCode, "admin"));
        if (adminRole == null) return;

        Long count = userRoleMapper.selectCount(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, lin.getId())
                .eq(UserRole::getRoleId, adminRole.getId()));
        if (count != null && count > 0) return;

        UserRole ur = new UserRole();
        ur.setUserId(lin.getId());
        ur.setRoleId(adminRole.getId());
        userRoleMapper.insert(ur);
    }

    /** 同步标准菜单（upsert）并自动授权给管理员角色 */
    private void syncMenus() {
        Object[][] menus = {
            {1L, 0L, "首页", "menu", "/dashboard", "Dashboard", "HomeFilled", 1},
            {2L, 0L, "基础数据", "catalog", "", "", "DataBoard", 2},
            {3L, 0L, "研发管理", "catalog", "", "", "Cpu", 3},
            {4L, 0L, "委外加工", "catalog", "", "", "Setting", 4},
            {5L, 0L, "进货业务", "catalog", "", "", "ShoppingCart", 5},
            {6L, 0L, "销售业务", "catalog", "", "", "Sell", 6},
            {7L, 0L, "成品库存业务", "catalog", "", "", "Odometer", 7},
            {8L, 0L, "财务管理", "catalog", "", "", "Money", 8},
            {9L, 0L, "设置", "catalog", "", "", "Tools", 9},
            {105L, 2L, "客户管理", "menu", "/inventory/customer", "InventoryCustomer", "UserFilled", 1},
            {106L, 2L, "供应商管理", "menu", "/supplier/manage", "SupplierManage", "OfficeBuilding", 2},
            {107L, 2L, "供货商管理", "menu", "/outsource/supplier/manage", "OutsourceSupplierManage", "Van", 3},
            {103L, 2L, "BOM表类型管理", "menu", "/dev/bom-type", "DevBomType", "Tickets", 4},
            {101L, 2L, "产品管理", "menu", "/product", "ProductManage", "TakeawayBox", 5},
            {102L, 2L, "品牌管理", "menu", "/inventory/brand", "InventoryBrand", "CollectionTag", 6},
            {104L, 2L, "阶段模板管理", "menu", "/dev/phase-template", "DevPhaseTemplate", "Timer", 7},
            {301L, 3L, "研发项目", "menu", "/dev/project", "DevProject", "Notebook", 1},
            {302L, 3L, "BOM管理", "menu", "/dev/bom", "DevBom", "Tickets", 2},
            {303L, 3L, "图纸文档", "menu", "/dev/drawing", "DevDrawing", "Files", 3},
            {304L, 3L, "研发物料管理", "menu", "/dev/material", "DevMaterial", "Box", 4},
            {401L, 4L, "加工订单", "menu", "/outsource/order", "OutsourceOrder", "Document", 1},
            {402L, 4L, "物料订单", "menu", "/outsource/material-order", "OutsourceMaterialOrder", "ShoppingCart", 2},
            {403L, 4L, "物料信息", "menu", "/outsource/material-info", "OutsourceMaterialInfo", "Switch", 3},
            {406L, 4L, "物料收发单", "menu", "/outsource/delivery", "OutsourceDelivery", "Tickets", 4},
            {407L, 4L, "物料其他出入库", "menu", "/outsource/other-io", "OutsourceOtherIo", "Files", 5},
            {408L, 4L, "加工退货", "menu", "/outsource/return-order", "OutsourceReturnOrder", "CircleClose", 6},
            {411L, 4L, "物料退货", "menu", "/outsource/material-return", "OutsourceMaterialReturn", "Refrigerator", 7},
            {404L, 4L, "委外仓库", "menu", "/outsource/warehouse", "Warehouse", "Odometer", 8},
            {410L, 4L, "自有物料仓", "menu", "/outsource/material-warehouse", "OutsourceMaterialWarehouse", "Box", 9},
            {409L, 4L, "供应商管理", "menu", "/supplier/manage", "OutsourceSupplierManage", "UserFilled", 10},
            {405L, 4L, "加工合同模板", "menu", "/outsource/contract-template", "OutsourceContractTemplate", "Document", 11},
            {501L, 5L, "成品采购单", "menu", "/inventory/purchase", "InventoryPurchase", "ShoppingCart", 1},
            {502L, 5L, "采购退货单", "menu", "/inventory/purchase-return", "InventoryPurchaseReturn", "Refrigerator", 2},
            // 进货业务→供货商管理：指向 /outsource/supplier/manage（供货商=成品商，双模式页面）
            {503L, 5L, "供货商管理", "menu", "/outsource/supplier/manage", "OutsourceSupplierManage", "UserFilled", 3},
            {601L, 6L, "销售单", "menu", "/inventory/sale", "InventorySale", "Sell", 1},
            {602L, 6L, "客户管理", "menu", "/inventory/customer", "InventoryCustomer", "User", 5},
            // 售后：销售退单 → 退货整理 → 销售换货单（换货可选择性收费）
            {603L, 6L, "销售退单", "menu", "/sale/return", "SaleReturn", "Refund", 2},
            {605L, 6L, "销售换货单", "menu", "/sale/exchange", "SaleExchange", "Refresh", 3},
            {701L, 7L, "成品库存", "menu", "/inventory/stock", "InventoryStock", "Odometer", 1},
            {702L, 7L, "成品仓库管理", "menu", "/inventory/warehouse", "Warehouse", "Odometer", 2},
            {703L, 7L, "成品库存流水", "menu", "/inventory/stock-log", "WarehouseStockLog", "TrendCharts", 3},
            {704L, 7L, "成品其他出入库", "menu", "/inventory/other-io", "InventoryOtherIo", "Upload", 4},
            {705L, 7L, "成品品质重分类", "menu", "/inventory/reclassify", "InventoryReclassify", "Refresh", 5},
            {706L, 7L, "成品移仓单", "menu", "/inventory/warehouse-move", "InventoryWarehouseMove", "Rank", 6},
            // 库存盘点：每月每仓一次，仓库列表与盘点页显示待盘点/超期提醒
            {711L, 7L, "库存盘点", "menu", "/inventory/stock-take", "InventoryStockTake", "Files", 7},
            // 退货整理归属「销售」模块（售后链路的一环：退单/换货退回 → 整理分选 → 入成品仓/不良仓）
            // ID 仍保留 707（存量角色授权按 ID 关联，换 ID 会导致历史授权失效），仅迁移 parent_id
            {707L, 6L, "退货整理", "menu", "/inventory/return-sort", "InventoryReturnSort", "RefreshRight", 4},
            {801L, 8L, "应收管理", "menu", "/finance/receivable", "FinanceReceivable", "Wallet", 1},
            {802L, 8L, "应付管理", "menu", "/finance/payable", "FinancePayable", "CreditCard", 2},
            {803L, 8L, "账单生成", "menu", "/finance/bill", "FinanceBill", "Postcard", 3},
            {804L, 8L, "资金流水", "menu", "/finance/cashflow", "FinanceCashflow", "TrendCharts", 4},
            // 资金账户自「资金流水」页拆分为独立子菜单（807）
            {807L, 8L, "资金账户", "menu", "/finance/account", "FinanceAccount", "Coin", 5},
            {805L, 8L, "收款管理", "menu", "/finance/receipt", "FinanceReceipt", "Money", 6},
            {806L, 8L, "付款管理", "menu", "/finance/payment", "FinancePayment", "Sell", 7},
            // 费用登记：审核扣减资金账户并生成「费用支出」流水，供财务分析利润表取数
            {809L, 8L, "费用管理", "menu", "/finance/expense", "FinanceExpense", "Tickets", 8},
            // 财务分析：经营概览/利润表/资金趋势/应收应付账龄（纯查询报表）
            {808L, 8L, "财务分析", "menu", "/finance/analysis", "FinanceAnalysis", "DataAnalysis", 9},
            // 发票登记：销项/进项发票（税务口径），供税务分析发票汇总取数
            {810L, 8L, "发票管理", "menu", "/finance/invoice", "FinanceInvoice", "Stamp", 10},
            {901L, 9L, "智能管理", "menu", "/system/smart", "SystemSmart", "Cpu", 1},
            {902L, 9L, "用户管理", "menu", "/system/user", "SystemUser", "UserFilled", 2},
            {903L, 9L, "权限管理", "menu", "/system/permission", "SystemPermission", "Lock", 3},
            {904L, 9L, "系统信息", "menu", "/system/settings", "SystemSettings", "Setting", 4},
            {905L, 9L, "数据管理", "menu", "/system/data-manage", "SystemDataManage", "Folder", 5},
            {906L, 9L, "角色管理", "menu", "/system/role", "SystemRole", "Avatar", 6},
            {907L, 9L, "菜单管理", "menu", "/system/menu", "SystemMenu", "Menu", 7},
            {908L, 9L, "清空数据", "menu", "/system/clear-data", "SystemClearData", "Delete", 8},
        };
        // ON DUPLICATE KEY UPDATE 实现 upsert
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

        // 删除非标准菜单（旧ID已废弃）
        Long[] newMenuIds = {1L,2L,3L,4L,5L,6L,7L,8L,9L,101L,102L,103L,104L,105L,106L,107L,301L,302L,303L,304L,401L,402L,403L,404L,405L,406L,407L,408L,409L,410L,411L,501L,502L,503L,601L,602L,603L,604L,605L,701L,702L,703L,704L,705L,706L,707L,711L,801L,802L,803L,804L,805L,806L,807L,808L,809L,810L,901L,902L,903L,904L,905L,906L,907L,908L};
        Set<Long> newIds = new HashSet<>(Arrays.asList(newMenuIds));
        jdbcTemplate.update("DELETE FROM sys_role_menu WHERE menu_id NOT IN (" +
            String.join(",", newIds.stream().map(String::valueOf).toArray(String[]::new)) + ")");
        int deleted = jdbcTemplate.update("DELETE FROM sys_menu WHERE id NOT IN (" +
            String.join(",", newIds.stream().map(String::valueOf).toArray(String[]::new)) + ")");
        log.info("已清理 {} 个废弃旧菜单", deleted);

        // 为 admin 角色授权所有标准菜单
        for (Object[] m : menus) {
            try {
                jdbcTemplate.update(
                    "INSERT IGNORE INTO sys_role_menu (role_id, menu_id) " +
                    "SELECT r.id, ? FROM sys_role r WHERE r.role_code = 'admin'",
                    m[0]);
            } catch (Exception ignored) {}
        }
        log.info("已为管理员角色授权标准菜单");

        // 为研发工程师补充授权研发模块菜单
        Long[] devMenuIds = {301L, 302L, 303L, 304L, 101L};
        for (Long mid : devMenuIds) {
            try {
                jdbcTemplate.update(
                    "INSERT IGNORE INTO sys_role_menu (role_id, menu_id) " +
                    "SELECT r.id, ? FROM sys_role r WHERE r.role_code = 'dev_engineer'",
                    mid);
            } catch (Exception ignored) {}
        }
        log.info("已为研发工程师角色补充授权研发模块菜单");
    }

    /** 为6个角色分别授权对应菜单 */
    private void initRoleMenus() {
        // 管理员：全部权限
        assignRoleMenus("admin", Arrays.asList(
                1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L,
                101L, 102L, 103L, 104L, 105L, 106L, 107L,
                301L, 302L, 303L, 304L,
                401L, 402L, 403L, 404L, 405L, 406L, 407L, 408L, 409L, 410L, 411L,
                501L, 502L, 503L,
                601L, 602L, 603L, 604L,
                701L, 702L, 703L, 704L, 705L, 706L,
                801L, 802L, 803L, 804L, 805L, 806L, 807L, 808L, 809L, 810L,
                901L, 902L, 903L, 904L, 905L, 906L, 907L, 908L));
        // 研发工程师：项目研发 + BOM + 基础产品
        assignRoleMenus("dev_engineer", Arrays.asList(
                1L, 3L, 301L, 302L, 303L, 304L, 101L));
        // 销售专员：销售业务 + 客户 + 产品
        assignRoleMenus("sales", Arrays.asList(
                1L, 6L, 601L, 602L, 603L, 604L, 101L));
        // 仓管员：进货+库存 + 仓库
        assignRoleMenus("warehouse", Arrays.asList(
                1L, 5L, 7L, 501L, 502L, 701L, 702L, 703L, 704L, 705L, 706L, 711L, 603L, 604L, 101L));
        // 跟单专员：委外加工全部
        assignRoleMenus("merchandiser", Arrays.asList(
                1L, 4L, 401L, 402L, 403L, 404L, 405L, 406L, 407L, 408L, 409L, 410L, 101L, 602L, 502L, 702L, 705L));
        // 财务：财务管理
        assignRoleMenus("finance", Arrays.asList(
                1L, 8L, 801L, 802L, 803L, 804L, 805L, 806L, 807L, 808L, 809L, 810L, 101L));
    }

    /** 为指定角色授权菜单（仅当角色尚无菜单权限时执行） */
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

    /** 初始化默认BOM类型（玻璃/驱动IC/触摸IC/码片IC/排线/盖板/背贴/钢板/COP） */
    private void initBomTypes() {
        // 补齐默认BOM类型：表空时全量初始化；已有部分时仅补缺失的默认类型（幂等）
        List<BomType> existing = bomTypeMapper.selectList(
                new LambdaQueryWrapper<BomType>().eq(BomType::getCompanyId, 1L));
        java.util.Set<String> existingNames = new java.util.HashSet<>();
        int maxSort = 0;
        for (BomType b : existing) {
            if (b.getTypeName() != null) existingNames.add(b.getTypeName());
            if (b.getSortOrder() != null && b.getSortOrder() > maxSort) maxSort = b.getSortOrder();
        }
        String[] defaultTypes = DefaultBomTypes.TYPES;
        int nextSort = maxSort;
        int added = 0;
        for (String name : defaultTypes) {
            if (existingNames.contains(name)) continue;
            BomType bt = new BomType();
            bt.setTypeName(name);
            bt.setSortOrder(++nextSort);
            bt.setStatus(1);
            bt.setIsDefault(1);
            bt.setCompanyId(1L);
            bomTypeMapper.insert(bt);
            added++;
        }
        if (added > 0) log.info("补齐默认BOM类型 {} 条", added);
    }

    /** 初始化14个研发阶段模板 */
    private void initPhaseTemplates() {
        Long count = phaseTemplateMapper.selectCount(null);
        if (count != null && count > 0) {
            log.info("阶段模板数据已存在，跳过初始化");
            return;
        }
        // 阶段模板：name, defaultDays, sortOrder, remark, productStatusSync
        // 小批量/结项阶段需触发关联产品状态由"研发中"改为"正常"
        Object[][] defaultPhases = {
            {"立项", 0, 1, "", 0},
            {"结构评估", 2, 2, "根据玻璃尺寸和摄像头孔位与R角来综合评估结构是否支持立项。", 0},
            {"立项准备", 5, 3, "根据项目型号收手机，拆分成机板和屏幕分体状态，交给触摸方案公司抓取触摸协议，明确是否可以破解协议以及用哪颗物料可以满足技术标准。", 0},
            {"显示评估", 2, 4, "提供机板和原屏给到显示方案公司，并告知触摸方案商建议使用的触摸IC料号及规格书与触摸原理图，让显示方案公司抓取显示协议，根据手机的分辨率与刷新率和玻璃的分辨率综合评估用哪颗码片物料，以及驱动IC。", 0},
            {"排线图纸", 3, 5, "根据触摸方案公司建议的触摸IC和显示方案公司建议的码片，开始画图纸，一般都可以画。", 0},
            {"排线打样", 4, 6, "出图纸后，把图纸给到排线工厂打样，一般打10PCS，码片和触摸IC需要找方案公司提供。", 0},
            {"FOG打样", 2, 7, "排线打样好之后直接让工厂寄给打样加工厂，同时需要寄驱动IC过去和玻璃过去，一般先打样5PCS。", 0},
            {"显示调试", 5, 8, "FOG打样直接寄到显示方案公司，并且提供机板，开始调试显示功能。", 0},
            {"触摸调试", 5, 9, "初版显示做好以后，移交机板和FOG去触摸方案公司调试触摸。同时保留一个机板和FOG去盖板厂开模做盖板样品。", 0},
            {"背贴盖板打样", 2, 10, "使用保留的一个机板和FOG去盖板厂根据屏幕的实际显示效果开模做盖板样品，然后去背贴厂开背贴样品。", 0},
            {"总成样品", 2, 11, "将盖板和背贴样品寄到加工厂做成总成，需要寄2PCS总成和机板过去方案公司优化触摸。", 0},
            {"测试", 5, 12, "开始测试，需要测试结构/显示/触摸，详见测试文档。", 0},
            {"小批量", 3, 13, "测试没问题之后，下物料寄到工厂，先进行100PCS的小批量，到货后过一遍，没有批次问题，就可以结项了。", 1},
            {"结项", 0, 14, "结项，通知工厂开始量产。", 1}
        };
        for (Object[] p : defaultPhases) {
            PhaseTemplate t = new PhaseTemplate();
            t.setName((String) p[0]);
            t.setDefaultDays((Integer) p[1]);
            t.setSortOrder((Integer) p[2]);
            t.setRemark((String) p[3]);
            t.setProductStatusSync((Integer) p[4]);
            t.setCompanyId(1L);
            phaseTemplateMapper.insert(t);
        }
        log.info("初始化阶段模板数据完成（共 {} 条）", defaultPhases.length);
    }
}
