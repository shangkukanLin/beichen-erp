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
import org.springframework.beans.factory.annotation.Value;
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

    /** 默认口令哨兵：出现即启动告警，提醒"生产忘了注入 INIT_ADMIN_PASSWORD" */
    private static final String DEFAULT_PASSWORD = "123";

    /** 初始口令（P0 配置外置 · 2026-09-14）：仅用于**首次创建**账号，生产必须注入 INIT_ADMIN_PASSWORD */
    @Value("${app.init.admin-password:123}")
    private String initAdminPassword;

    @Override
    public void run(ApplicationArguments args) {
        initCompany();
        initRoles();
        syncMenus();
        initRoleMenus();
        initSuperAdmin();
        initBomTypes();
        initPhaseTemplates();
        initContractTemplates();
        initScreenModels();
    }

    /**
     * 屏幕资料知识库初始化：仅在表为空时导入一次种子数据（db/screen_model_data.sql），
     * 避免覆盖用户后续编辑/新增的内容。种子 SQL 用 INSERT...SELECT...FROM company，
     * 因此每个公司各导入一份，切换公司也能看到完整知识库。
     */
    private void initScreenModels() {
        try {
            Integer cnt = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM screen_model", Integer.class);
            if (cnt != null && cnt > 0) return;
            org.springframework.core.io.Resource res =
                    new org.springframework.core.io.ClassPathResource("db/screen_model_data.sql");
            if (!res.exists()) { log.warn("屏幕资料知识库种子文件缺失，跳过导入"); return; }
            // 按语句执行：种子文件一条 INSERT 占多行、以分号结尾，逐行执行会语法错误。
            // 这里累积到「以分号结尾」视为一条完整语句再执行（已核验：字段值中不含分号，切分安全）。
            int n = 0;
            StringBuilder stmt = new StringBuilder();
            try (java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(res.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String s = line.trim();
                    if (s.isEmpty() || s.startsWith("--")) continue;
                    if (stmt.length() > 0) stmt.append(' ');
                    stmt.append(s);
                    if (s.endsWith(";")) {
                        jdbcTemplate.execute(stmt.toString());
                        stmt.setLength(0);
                        n++;
                    }
                }
                // 兜底：文件末尾若缺少分号，剩余内容也执行一次
                if (stmt.length() > 0) {
                    jdbcTemplate.execute(stmt.toString());
                    n++;
                }
            }
            log.info("屏幕资料知识库初始化完成，导入 {} 条", n);
        } catch (Exception e) {
            // 打印完整堆栈：bad SQL grammar 的具体 MySQL 原因在 cause 链里
            log.warn("初始化屏幕资料知识库异常", e);
        }
    }

    /** 幂等初始化默认合同模板：加工合同、采购合同各建一条默认模板（无默认模板时才插入） */
    private void initContractTemplates() {
        initContractTemplate(DefaultContractTemplate.TYPE_PROCESSING, DefaultContractTemplate.NAME_PROCESSING, DefaultContractTemplate.PROCESSING_CONTRACT_HTML);
        initContractTemplate(DefaultContractTemplate.TYPE_PURCHASE, DefaultContractTemplate.NAME_PURCHASE, DefaultContractTemplate.PURCHASE_CONTRACT_HTML);
    }

    private void initContractTemplate(String type, String name, String content) {
        Long count = contractTemplateMapper.selectCount(new LambdaQueryWrapper<ContractTemplate>()
                .eq(ContractTemplate::getTemplateType, type)
                .eq(ContractTemplate::getCompanyId, 1L));
        if (count != null && count > 0) {
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

    /** 初始化7个角色（超级管理员/管理员/研发工程师/销售专员/仓管员/跟单专员/财务） */
    private void initRoles() {
        jdbcTemplate.update("INSERT IGNORE INTO sys_role (role_name, role_code, status, remark, company_id) VALUES " +
            // P2-34：super_admin 是**平台级**角色（company_id=0），只给平台运维账号（lin）持有；
            // 用户管理侧禁止分配该角色（UserServiceImpl.saveUserRoles 会跳过），避免公司管理员自提权
            "('超级管理员', 'super_admin', 1, '平台级最高权限：整库导入/导出等跨租户运维操作', 0), " +
            "('管理员', 'admin', 1, '系统管理员，拥有全部权限', 0), " +
            "('研发工程师', 'dev_engineer', 1, '研发工程师，负责项目研发和BOM管理', 0), " +
            "('销售专员', 'sales', 1, '销售专员，负责销售和客户管理', 0), " +
            "('仓管员', 'warehouse', 1, '仓管员，负责库存和仓库管理', 0), " +
            "('跟单专员', 'merchandiser', 1, '跟单专员，负责委外加工跟进', 0), " +
            "('财务', 'finance', 1, '财务人员，负责应收应付和资金管理', 0)"
        );
        log.info("初始化角色数据完成");
    }

    /** 初始化超级管理员 lin（初始口令见 {@code app.init.admin-password}，默认 123 仅供开发） */
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
                "lin", passwordEncoder.encode(initAdminPassword));
        ensureLinRole();
        if (DEFAULT_PASSWORD.equals(initAdminPassword)) {
            log.warn("安全提示：平台超管 lin 的初始口令仍为默认值 {}，请登录后立即修改，"
                    + "部署生产请注入 INIT_ADMIN_PASSWORD（见《上线运维手册》§3.3）", DEFAULT_PASSWORD);
        }
        log.info("初始化超级管理员 lin 完成（角色: admin + super_admin）");
    }

    /**
     * 确保 lin 用户同时持有 admin 与 super_admin 角色。
     * <p>P2-34（2026-09-12）：整库导入/导出等高危端点要求 {@code super_admin}（平台级）；
     * 而 super_admin 角色此前**根本没被创建**、lin 只有 admin → 收口后会连平台操作员一起拦掉。
     * 此处做幂等自愈：启动时补建角色并授予 lin（新库/存量库都会自动修好）。</p>
     */
    private void ensureLinRole() {
        User lin = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, "lin"));
        if (lin == null) return;
        ensureUserRole(lin.getId(), "admin");
        ensureUserRole(lin.getId(), "super_admin");
    }

    /** 幂等授予：用户已持有该角色则跳过 */
    private void ensureUserRole(Long userId, String roleCode) {
        Role role = roleMapper.selectOne(new LambdaQueryWrapper<Role>()
                .eq(Role::getRoleCode, roleCode));
        if (role == null) return;
        Long count = userRoleMapper.selectCount(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, userId)
                .eq(UserRole::getRoleId, role.getId()));
        if (count != null && count > 0) return;
        UserRole ur = new UserRole();
        ur.setUserId(userId);
        ur.setRoleId(role.getId());
        userRoleMapper.insert(ur);
        log.info("已为用户 {} 授予角色 {}", userId, roleCode);
    }

    /** 同步标准菜单（upsert）并自动授权给管理员角色 */
    private void syncMenus() {
        Object[][] menus = {
            {1L, 0L, "首页", "menu", "/dashboard", "Dashboard", "HomeFilled", 1},
            // 经营分析：紧跟首页（用户要求置于首页之下），纯查询报表聚合；原「财务分析」已拆分迁入
            {10L, 0L, "经营分析", "catalog", "", "", "TrendCharts", 2},
            {2L, 0L, "基础数据", "catalog", "", "", "DataBoard", 3},
            {3L, 0L, "研发管理", "catalog", "", "", "Cpu", 4},
            {4L, 0L, "委外加工", "catalog", "", "", "Setting", 5},
            {5L, 0L, "进货业务", "catalog", "", "", "ShoppingCart", 6},
            {6L, 0L, "销售业务", "catalog", "", "", "Sell", 7},
            {7L, 0L, "成品库存", "catalog", "", "", "Odometer", 8},
            {8L, 0L, "财务管理", "catalog", "", "", "Money", 9},
            {9L, 0L, "设置", "catalog", "", "", "Tools", 10},
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
            // 屏幕资料知识库：行业机型屏幕参数（折叠屏/直板 AMOLED），可增删改查；清空数据时不清理
            {305L, 3L, "屏幕资料知识库", "menu", "/dev/screen-model", "DevScreenModel", "Iphone", 5},
            {401L, 4L, "加工订单", "menu", "/outsource/order", "OutsourceOrder", "Document", 1},
            {402L, 4L, "物料订单", "menu", "/outsource/material-order", "OutsourceMaterialOrder", "ShoppingCart", 2},
            {412L, 4L, "交货信息", "menu", "/outsource/delivery-info", "OutsourceDeliveryInfo", "Van", 3},
            {403L, 4L, "物料信息", "menu", "/outsource/material-info", "OutsourceMaterialInfo", "Switch", 4},
            {406L, 4L, "物料收发单", "menu", "/outsource/delivery", "OutsourceDelivery", "Tickets", 5},
            {407L, 4L, "物料其他出入库", "menu", "/outsource/other-io", "OutsourceOtherIo", "Files", 6},
            {408L, 4L, "加工退货", "menu", "/outsource/return-order", "OutsourceReturnOrder", "CircleClose", 7},
            {411L, 4L, "物料退货", "menu", "/outsource/material-return", "OutsourceMaterialReturn", "Refrigerator", 8},
            {404L, 4L, "委外仓库", "menu", "/outsource/warehouse", "Warehouse", "Odometer", 9},
            // 物料报损：与成品报损独立成表（主体为 outsource_material，物料库存不区分品质，固定按良品扣减）
            {413L, 4L, "物料报损", "menu", "/outsource/stock-loss", "OutsourceStockLoss", "DeleteFilled", 10},
            {410L, 4L, "自有物料仓", "menu", "/outsource/material-warehouse", "OutsourceMaterialWarehouse", "Box", 10},
            {409L, 4L, "供应商管理", "menu", "/supplier/manage", "OutsourceSupplierManage", "UserFilled", 11},
            {405L, 4L, "加工合同模板", "menu", "/outsource/contract-template", "OutsourceContractTemplate", "Document", 12},
            {501L, 5L, "成品采购单", "menu", "/inventory/purchase", "InventoryPurchase", "ShoppingCart", 1},
            {502L, 5L, "采购退货单", "menu", "/inventory/purchase-return", "InventoryPurchaseReturn", "Refrigerator", 2},
            // 进货业务→供货商管理：指向 /outsource/supplier/manage（供货商=成品商，双模式页面）
            {503L, 5L, "供货商管理", "menu", "/outsource/supplier/manage", "OutsourceSupplierManage", "UserFilled", 3},
            {601L, 6L, "销售单", "menu", "/inventory/sale", "InventorySale", "Sell", 1},
            {602L, 6L, "客户管理", "menu", "/inventory/customer", "InventoryCustomer", "User", 5},
            // 售后：销售退单 → 退货整理 → 销售换货单（换货可选择性收费）
            {603L, 6L, "销售退单", "menu", "/sale/return", "SaleReturn", "Refund", 2},
            {605L, 6L, "销售换货单", "menu", "/sale/exchange", "SaleExchange", "Refresh", 3},
            // 成品库存情况：按产品维度看跨仓库库存汇总，列表置顶；点行进详情看该产品在各仓库的分布
            {712L, 7L, "成品库存情况", "menu", "/inventory/product-stock", "InventoryProductStock", "Box", 1},
            {701L, 7L, "成品库存", "menu", "/inventory/stock", "InventoryStock", "Odometer", 2},
            {702L, 7L, "成品仓库管理", "menu", "/inventory/warehouse", "Warehouse", "Odometer", 3},
            {703L, 7L, "成品库存流水", "menu", "/inventory/stock-log", "WarehouseStockLog", "TrendCharts", 4},
            {704L, 7L, "成品其他出入库", "menu", "/inventory/other-io", "InventoryOtherIo", "Upload", 5},
            {705L, 7L, "成品品质重分类", "menu", "/inventory/reclassify", "InventoryReclassify", "Refresh", 6},
            {706L, 7L, "成品移仓单", "menu", "/inventory/warehouse-move", "InventoryWarehouseMove", "Rank", 7},
            // 库存盘点：每月每仓一次，仓库列表与盘点页显示待盘点/超期提醒
            {711L, 7L, "库存盘点", "menu", "/inventory/stock-take", "InventoryStockTake", "Files", 8},
            // 成品报损：草稿→审核扣减成品库存（LOSS_OUT 流水），可反审核回滚
            {713L, 7L, "成品报损", "menu", "/inventory/stock-loss", "InventoryStockLoss", "DeleteFilled", 9},
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
            // 发票登记：销项/进项发票（税务口径），供税务分析发票汇总取数
            {810L, 8L, "发票管理", "menu", "/finance/invoice", "FinanceInvoice", "Stamp", 10},
            // 应付转应收：退货/超损扣款（负向应付）在无货款可抵时，转为向供应商收款
            {811L, 8L, "应付转应收", "menu", "/finance/payable-transfer", "FinancePayableTransfer", "Refresh", 11},
            {901L, 9L, "智能管理", "menu", "/system/smart", "SystemSmart", "Cpu", 1},
            {902L, 9L, "用户管理", "menu", "/system/user", "SystemUser", "UserFilled", 2},
            {904L, 9L, "系统信息", "menu", "/system/settings", "SystemSettings", "Setting", 4},
            {905L, 9L, "数据管理", "menu", "/system/data-manage", "SystemDataManage", "Folder", 5},
            {906L, 9L, "角色管理", "menu", "/system/role", "SystemRole", "Avatar", 6},
            {907L, 9L, "菜单管理", "menu", "/system/menu", "SystemMenu", "Menu", 7},
            {908L, 9L, "清空数据", "menu", "/system/clear-data", "SystemClearData", "Delete", 8},
            // ==================== 经营分析（目录 10）：原「财务分析」5 个 Tab 拆分 + 新增销售/客户分析 ====================
            {1001L, 10L, "经营概览", "menu", "/analysis/overview", "AnalysisOverview", "DataLine", 1},
            // 利润表：按天明细 + 快捷区间；点行「详细」进 /analysis/profit/detail/:date 看每条单据
            {1002L, 10L, "利润表", "menu", "/analysis/profit", "AnalysisProfit", "DataAnalysis", 2},
            // 资金与往来：资金趋势 + 应收应付账龄 + 主体往来统计（原两个 Tab 合并）
            {1003L, 10L, "资金与往来", "menu", "/analysis/cash", "AnalysisCash", "Wallet", 3},
            {1004L, 10L, "税务分析", "menu", "/analysis/tax", "AnalysisTax", "Stamp", 4},
            // 销售分析：销售额趋势 + 产品/仓库排行，行可下钻到销售单明细
            {1005L, 10L, "销售分析", "menu", "/analysis/sale", "AnalysisSale", "Sell", 5},
            // 客户分析：客户销售额排行 + 欠款/账期，行可下钻到该客户的销售单
            {1006L, 10L, "客户分析", "menu", "/analysis/customer", "AnalysisCustomer", "UserFilled", 6},
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
    }

    /**
     * 为6个角色分别授权对应菜单（必须包含各自父目录，否则菜单树 buildTree 会把子菜单丢弃）
     * 注：604「销售换货单」已废弃，现为 605。
     */
    private void initRoleMenus() {
        // 清理指向已不存在菜单的脏授权（历史菜单改 id / 删除后残留），幂等自愈
        jdbcTemplate.update(
                "DELETE rm FROM sys_role_menu rm LEFT JOIN sys_menu m ON m.id = rm.menu_id WHERE m.id IS NULL");

        // 管理员：全部权限
        assignRoleMenus("admin", Arrays.asList(
                1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L,
                101L, 102L, 103L, 104L, 105L, 106L, 107L,
                301L, 302L, 303L, 304L, 305L,
                401L, 402L, 403L, 404L, 405L, 406L, 407L, 408L, 409L, 410L, 411L, 412L, 413L,
                501L, 502L, 503L,
                601L, 602L, 603L, 605L,
                701L, 702L, 703L, 704L, 705L, 706L, 711L, 712L, 713L,
                801L, 802L, 803L, 804L, 805L, 806L, 807L, 809L, 810L, 811L,
                1001L, 1002L, 1003L, 1004L, 1005L, 1006L,
                901L, 902L, 903L, 904L, 905L, 906L, 907L, 908L));
        // 研发工程师：项目研发 + BOM + 基础产品（2 基础数据 = 101 产品管理的父目录）
        assignRoleMenus("dev_engineer", Arrays.asList(
                1L, 2L, 3L, 301L, 302L, 303L, 304L, 305L, 101L));
        // 销售专员：销售业务 + 客户 + 产品 + 经营分析（605 换货单；2 基础数据）
        assignRoleMenus("sales", Arrays.asList(
                1L, 2L, 6L, 601L, 602L, 603L, 605L, 101L,
                10L, 1001L, 1002L, 1003L, 1004L, 1005L, 1006L));
        // 仓管员：进货 + 库存 + 仓库（2 基础数据 / 6 销售业务 为其子菜单的父目录）
        assignRoleMenus("warehouse", Arrays.asList(
                1L, 2L, 5L, 6L, 7L, 501L, 502L, 603L, 701L, 702L, 703L, 704L, 705L, 706L, 711L, 712L, 713L, 101L));
        // 跟单专员：委外加工全部 + 相关基础数据/进货/销售/成品库存页面
        assignRoleMenus("merchandiser", Arrays.asList(
                1L, 2L, 4L, 5L, 6L, 7L,
                401L, 402L, 403L, 404L, 405L, 406L, 407L, 408L, 409L, 410L, 412L, 413L,
                101L, 502L, 602L, 702L, 705L));
        // 财务：财务管理 + 经营分析（2 基础数据 = 101 产品管理的父目录）
        assignRoleMenus("finance", Arrays.asList(
                1L, 2L, 8L, 801L, 802L, 803L, 804L, 805L, 806L, 807L, 809L, 810L, 811L, 101L,
                10L, 1001L, 1002L, 1003L, 1004L, 1005L, 1006L));
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
