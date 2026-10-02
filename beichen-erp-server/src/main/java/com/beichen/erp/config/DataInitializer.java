package com.beichen.erp.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beichen.erp.common.DefaultMaterialTypes;
import com.beichen.erp.common.DefaultContractTemplate;
import com.beichen.erp.common.DefaultPhaseTemplates;
import com.beichen.erp.auth.entity.User;
import com.beichen.erp.auth.mapper.UserMapper;
import com.beichen.erp.dev.entity.MaterialType;
import com.beichen.erp.dev.entity.PhaseTemplate;
import com.beichen.erp.dev.mapper.MaterialTypeMapper;
import com.beichen.erp.dev.mapper.PhaseTemplateMapper;
import com.beichen.erp.outsource.entity.ContractTemplate;
import com.beichen.erp.outsource.mapper.ContractTemplateMapper;
import com.beichen.erp.system.entity.Role;
import com.beichen.erp.system.entity.UserRole;
import com.beichen.erp.system.mapper.MenuMapper;
import com.beichen.erp.system.mapper.RoleMapper;
import com.beichen.erp.system.mapper.UserRoleMapper;
import com.beichen.erp.system.service.CompanyRoleProvisioner;
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
 * 数据初始化器（种子播种器）：启动时写入系统基础数据。
 *
 * <p><b>职责（P1 · 2026-09-30 未上线清理）</b>：本类**只做种子写入** —— 公司/角色/用户/菜单/权限码/
 * 角色→菜单计划/物料类型/阶段模板/合同模板/屏幕资料，外加按公司补齐默认业务种子。
 * 全部幂等（只增不改：用户改过的菜单/角色授权会被跳过）。</p>
 *
 * <p><b>本类不再执行任何 DDL</b>：原 18 个 {@code migrate*()} 升级方法与 {@code initDocOperatorColumns()}
 * 已删除 —— 项目尚未上线，不存在"老库需要就地升级"的场景；表结构**唯一来源是 {@code db/migration/V1__base.sql}**
 * （{@code CREATE TABLE IF NOT EXISTS}，启动自动执行，全新库一次即成）。缺表缺列的后果由
 * {@link #assertSchemaReady()} 在初始化最前面直接报错，不再"悄悄补一列"。</p>
 *
 * <p><b>执行时机（F8-20）</b>：由 {@code @PostConstruct} 触发，早于 Web 容器放行端口；</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final MenuMapper menuMapper;
    private final RoleService roleService;
    private final MaterialTypeMapper materialTypeMapper;
    private final PhaseTemplateMapper phaseTemplateMapper;
    private final ContractTemplateMapper contractTemplateMapper;
    private final JdbcTemplate jdbcTemplate;
    /** S-9②：角色按公司补齐 —— 与「超管新建公司」共用同一处克隆口径 */
    private final CompanyRoleProvisioner companyRoleProvisioner;

    /**
     * P1（2026-09-30 未上线清理）：需要"操作人四列"的业务单据表 ——
     * {@code create_by}/{@code create_by_name}（MetaObjectHandler 自动填充）与
     * {@code auditor_id}/{@code auditor_name}（审核时盖章）。
     *
     * <p>这些列原先由已删除的启动期迁移 {@code initDocOperatorColumns()} 逐表 {@code ALTER} 补上；
     * 现在它们由 {@code db/migration/V1__base.sql} 声明，本常量仅供 {@link #assertSchemaReady()} 做启动断言 ——
     * 任何一列缺失都直接让应用起不来（避免"能启动、写单据时才报 1054 未知列"）。</p>
     */
    private static final List<String> DOC_TABLES = List.of(
            "purchase_order", "purchase_return", "purchase_exchange",
            "sale_order", "sale_return", "sale_exchange",
            "inventory_warehouse_move", "return_sort", "inventory_stock_take",
            "inventory_stock_loss", "inventory_other_io", "product_reclassify",
            "outsource_delivery", "outsource_stock_loss", "outsource_other_io",
            "outsource_order", "outsource_material_order", "outsource_return_order",
            "outsource_material_return", "outsource_order_delivery",
            "outsource_return_order_repair", "outsource_material_return_repair",
            "outsource_order_close_report", "dev_project",
            "finance_receipt", "finance_payment", "finance_bill", "finance_expense",
            "finance_invoice", "finance_payable_transfer", "finance_receivable", "finance_payable");
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /** 默认口令哨兵：出现即启动告警，提醒"生产忘了注入 INIT_ADMIN_PASSWORD" */
    private static final String DEFAULT_PASSWORD = "123";

    /** 初始口令（P0 配置外置 · 2026-09-14）：仅用于**首次创建**账号，生产必须注入 INIT_ADMIN_PASSWORD */
    @Value("${app.init.admin-password:123}")
    private String initAdminPassword;

    /**
     * 启动期**表/列预检**（F8-25 · 2026-09-30 设置模块批 E 修复）。
     *
     * <p>口径：迁移类方法失败只记日志继续（它们各自"判存在再改"、可重复跑）；**种子类失败必须让应用起不来**
     * （半初始化状态更难排查）。为了把"某条 SQL 撞到缺表缺列"变成**一条能直接定位的启动失败信息**，
     * 先在这里检查初始化/迁移要用到的表与关键列。</p>
     */
    private void assertSchemaReady() {
        String[][] required = {
                {"sys_company", "id"}, {"sys_role", "role_code"}, {"sys_menu", "route_name"},
                {"sys_user", "username"}, {"sys_role_menu", "menu_id"}, {"sys_user_role", "user_id"},
                {"sys_user_menu", "menu_id"}, {"sys_user_dashboard_tab", "user_id"},
                {"material_type", "type_name"}, {"dev_phase_template", "product_status_sync"},
                {"outsource_contract_template", "template_type"}, {"screen_model", "id"},
                // P1（2026-09-30 未上线清理）：这两列原先由启动期 DDL 补（F8-21/F8-22），
                // 现已并入 db/migration/V1__base.sql 的 CREATE TABLE，这里只做断言，不再执行任何 DDL。
                {"sys_menu", "customized"}, {"sys_role", "customized_menu"},
                {"sys_role_menu_plan", "menu_id"}
        };
        List<String> missing = new java.util.ArrayList<>();
        for (String[] r : required) {
            if (!columnExists(r[0], r[1])) {
                missing.add(r[0] + "." + r[1]);
            }
        }
        // P1：单据类表的"操作人四列"（MetaObjectHandler 自动填充 create_by/create_by_name，
        // 审核时盖章 auditor_id/auditor_name）。这 128 个列原先**全部**由启动期迁移补齐，
        // 现由 db/migration/V1__base.sql 声明；任何一列缺失都说明库结构落后于代码 ⇒ 必须让应用起不来。
        String[][] opColumns = {
                {"create_by", "BIGINT"}, {"create_by_name", "VARCHAR"}, {"auditor_id", "BIGINT"}, {"auditor_name", "VARCHAR"}
        };
        for (String t : DOC_TABLES) {
            for (String[] c : opColumns) {
                if (!columnExists(t, c[0])) {
                    missing.add(t + "." + c[0]);
                }
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("[启动初始化失败] 表/列缺失 " + missing.size() + " 项：" + missing
                    + " —— 本版本起不再执行启动期 DDL（未上线，见 docs/《数据库演化约定》）："
                    + "请更新 db/migration/V1__base.sql 后重建数据库（开发/测试库直接 drop + 重启即可）");
        }
    }

    /**
     * 启动初始化入口。F8-20（2026-09-30 批 E 修复）：由 {@code ApplicationRunner} 改为
     * {@code @PostConstruct} ⇒ 在 Web 容器**放行端口之前**跑完整套种子 + 迁移，
     * 从结构上消除"端口已开、DDL 还在跑 ⇒ 请求事务撞 MySQL 1412"的竞态。
     */
    @jakarta.annotation.PostConstruct
    public void init() {
        assertSchemaReady();
        initCompany();
        initRoles();
        syncMenus();
        initRoleMenuPlan();
        // S-9② 修复：**必须先补齐各公司的角色行**，再由 initRoleMenus() 统一按
        // sys_role_menu_plan 授权 —— 顺序不可颠倒，否则新克隆出来的角色拿不到标准菜单授权
        // （实测颠倒时 dev_engineer 只 1 个菜单、finance 0 个，角色等于不能用）。
        ensureCompanyRoles();
        initRoleMenus();
        initSuperAdmin();
        initMaterialTypes();
        initPhaseTemplates();
        initContractTemplates();
        backfillCompanyDefaults();
        initScreenModels();
    }

    /**
     * 屏幕资料知识库初始化：仅在表为空时导入一次种子数据（db/screen_model_data.sql），
     * 避免覆盖用户后续编辑/新增的内容。
     * <p>F7-99（2026-09-19）：本表已改为**行业共享的单一知识库**（不参与租户隔离，见
     * {@link CompanyTenantHandler} 的 IGNORE_TABLES）。种子文件里的
     * {@code INSERT ... SELECT ... FROM sys_company} 是"每个公司各一份"时代的写法 ——
     * 导入后这里统一把 company_id 归拢为 NULL，与"共享一份"的语义对齐。</p>
     * <p>注意：这里的 {@code COUNT(*)} 由 jdbcTemplate 原生 SQL 执行（不走租户插件），
     * 统计的是全表；表非空即认为已导入过，直接返回。</p>
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
            // 共享语义：种子文件按"每公司一份"插入时，这里统一归拢为一份（company_id = NULL）
            int normalized = jdbcTemplate.update(
                    "UPDATE screen_model SET company_id = NULL WHERE company_id IS NOT NULL");
            log.info("屏幕资料知识库初始化完成，导入 {} 条（company_id 归拢 {} 行）", n, normalized);
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
        // S-9②（2026-09-30 设置模块审核批 C）：角色改为**按公司隔离**后，同一 role_code 会有多行
        // （平台模板 company_id=0 + 各公司副本）⇒ `selectOne` 会抛 TooManyResultsException（曾导致启动失败）。
        // 统一取"**公司 ID 升序的第一行**" = 平台模板优先，行为确定且向后兼容。
        Role role = roleMapper.selectList(new LambdaQueryWrapper<Role>()
                        .eq(Role::getRoleCode, roleCode)
                        .orderByAsc(Role::getCompanyId))
                .stream().findFirst().orElse(null);
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
            {5L, 0L, "进货业务", "catalog", "", "", "ShoppingCart", 7},
            {6L, 0L, "销售业务", "catalog", "", "", "Sell", 8},
            // 物料仓库（2026-09-16 用户要求新增）：自「委外加工」迁入 5 个物料收发/仓储/报损子菜单。
            // 2026-10-02 用户口径「菜单物料仓库放在委外加工下面」：「物料仓库」**紧跟在「委外加工」之后**
            //   （物料收发本属委外业务线，两块相邻便于来回切）⇒ 物料仓库 8→6、进货业务 6→7、销售业务 7→8；
            //   成品库存 9 / 财务管理 10 / 设置 11 不变，仍保持两个仓库模块相邻。
            //   ⚠️ 只改 sort_order：id / 父级 / 路由 / route_name / perms / 授权一律不动（不涉菜单白名单、重定向、接口权限）。
            //   首页页签顺序**必须同步**（dashboard/index.vue 的 pane 顺序 + api/system.ts 的 DASHBOARD_TABS），
            //   由 verify-dashboard-tab-menu.ps1「页签顺序 == 顶层目录顺序」动态校验；
            //   verify-material-warehouse-menu.ps1 ① 同步加"紧随委外加工"的邻接断言。
            {11L, 0L, "物料仓库", "catalog", "", "", "Box", 6},
            {7L, 0L, "成品库存", "catalog", "", "", "Odometer", 9},
            {8L, 0L, "财务管理", "catalog", "", "", "Money", 10},
            {9L, 0L, "设置", "catalog", "", "", "Tools", 11},
            // 基础数据子菜单顺序（2026-09-15 用户定稿，2026-09-16 追加「物料信息管理」，2026-09-22 追加
            // 「成品仓库管理」+「委外仓库 / 自有物料仓」；2026-09-22 用户选定**方案 A 重新拍序**——
            // 按"主数据对象"分组、组内按使用频率、配置类收尾）：
            // 2026-09-23 用户要求：**品牌管理提到本组第 1 位**（品牌是产品/物料的共用主数据，先建品牌再建产品）
            // 品牌 → 产品（被所有单据引用）→ 往来主体（客户/供应商/供货商，对应 销售/委外/进货 三条业务线）
            // → 物料（类型 → 明细）→ 仓库（成品 → 委外 → 自有）→ 配置（模版管理收尾）
            // sort_order 即左侧栏显示顺序：
            // 品牌管理 → 产品管理 → 客户管理 → 供应商管理 → 供货商管理 → 物料类型管理 → 物料信息管理
            // → 成品仓库管理 → 委外仓库管理 → 自有物料仓管理 → 模版管理
            // ⚠️ 本次只改 sort_order：id / perms / 路由 / 授权一律不动（不迁权限、不动白名单）；
            //    verify-material-type.ps1 里"第 6 项 = 物料类型管理"的硬编码断言已同步改为**按库取基准**。
            {102L, 2L, "品牌管理", "menu", "/inventory/brand", "InventoryBrand", "CollectionTag", 1},
            {101L, 2L, "产品管理", "menu", "/product", "ProductManage", "TakeawayBox", 2},
            {105L, 2L, "客户管理", "menu", "/inventory/customer", "InventoryCustomer", "UserFilled", 3},
            {106L, 2L, "供应商管理", "menu", "/supplier/manage", "SupplierManage", "OfficeBuilding", 4},
            {107L, 2L, "供货商管理", "menu", "/outsource/supplier/manage", "OutsourceSupplierManage", "Van", 5},
            {103L, 2L, "物料类型管理", "menu", "/dev/material-type", "MaterialType", "Tickets", 6},
            // 物料信息管理（403）：2026-09-16 用户要求自「委外加工」迁入「基础数据」，紧跟「物料类型管理」；
            // 同日按用户要求菜单名由「物料信息」改为「物料信息管理」（与页面标题一致）
            // （物料主数据属基础数据；路由路径 /outsource/material-info 保持不变，故不涉及菜单白名单/重定向）
            {403L, 2L, "物料信息管理", "menu", "/outsource/material-info", "OutsourceMaterialInfo", "Switch", 7},
            // 仓库块（2026-09-22 起三个"仓库"主数据相邻）：成品仓库管理自「成品库存」迁入、
            // 委外仓库 / 自有物料仓自「物料仓库」迁入；**路由与 perms 均不变** ⇒ 不涉前端菜单白名单/重定向/接口权限。
            // ⚠️ 与 707 迁组同坑：父目录未授权会把子菜单整组丢弃 ⇒ 下方各有"补授目录 2"的幂等块（702 / 404+410）。
            {702L, 2L, "成品仓库管理", "menu", "/inventory/warehouse", "Warehouse", "Odometer", 8},
            {404L, 2L, "委外仓库管理", "menu", "/outsource/warehouse", "Warehouse", "Odometer", 9},
            {410L, 2L, "自有物料仓管理", "menu", "/outsource/material-warehouse", "OutsourceMaterialWarehouse", "Box", 10},
            // 模版管理（配置/模板类，低频收尾）：把原「阶段模板管理」（104，基础数据下）与「加工合同模板」（405，委外加工下）
            // 合并为一个页面，页内用 TAB 区分（2026-09-15 用户要求）。旧地址保留为重定向。
            {108L, 2L, "模版管理", "menu", "/template", "TemplateManage", "Timer", 11},
            // 301 菜单名（2026-09-16 用户要求）：「研发项目」→「研发立项」（仅显示文案，id/route_path/route_name/授权均不变）
            {301L, 3L, "研发立项", "menu", "/dev/project", "DevProject", "Notebook", 1},
            // 302「BOM管理」/ 303「图纸文档」已于 2026-09-16 按用户要求下线（不再 upsert，下方统一置 visible=0）。
            // 注意：BOM / 图纸 的**数据模型与页面能力保留** —— 它们在「研发立项」编辑页的 BOM / 图纸 页签内，
            // 且 dev_bom 被改配信息联动、委外带料/退不良回料依赖（BomMapper）。本次只删"独立总览页 + 菜单"。
            // 304/305 菜单名（2026-09-16 用户要求）：「研发物料管理」→「研发物料」、「屏幕资料知识库」→「屏幕资料」
            // （仅显示文案，id / route_path / route_name / 授权均不变）
            {304L, 3L, "研发物料", "menu", "/dev/material", "DevMaterial", "Box", 4},
            // 屏幕资料：行业机型屏幕参数（折叠屏/直板 AMOLED），可增删改查；清空数据时不清理
            {305L, 3L, "屏幕资料", "menu", "/dev/screen-model", "DevScreenModel", "Iphone", 5},
            // 委外加工子菜单顺序（2026-09-17 用户定稿；2026-09-28 「成品收货」文案改「加工收货」；
            // 2026-09-29 再改「加工收退」——该页既有收货也有退货，用户口径如此）：
            //   加工订单 → 加工收退 → 加工退货 → 物料订单 → 物料收退 → 物料退货。
            // sort_order 即左侧栏显示顺序（MenuMapper.selectAllEnabled 按 sort_order 排序）；下方书写顺序与实际显示顺序一致，便于维护。
            {401L, 4L, "加工订单", "menu", "/outsource/order", "OutsourceOrder", "Document", 1},
            // 加工收退（2026-09-16 立，原名「成品收货」；2026-09-28 文案改「加工收货」；
            // **2026-09-29 用户口径再改「加工收退」** —— 该页既有**收货**也有**退货**（行内「退货」= 加工退货红冲，
            // 关联退货叶子下线后这里是唯一入口），故名字带上"退"；**只改文案，页面与功能不变**）：
            // 原「加工订单详情 → 交货管理」页签**移出**独立成菜单页 —— 页面按「生产中｜已结单」两个页签列加工单，
            // 点「收货」一步收货 / 行内「退货」发起加工退货 / 点单号进详细页。
            // id 412 复用 2026-09-16 下线的「交货信息」总览页旧行（**必须同时从下方 visible=0 名单移除**）
            {412L, 4L, "加工收退", "menu", "/outsource/order/delivery", "OutsourceOrderDelivery", "Van", 2},
            // 加工售后（2026-09-17 立，原名「加工退货」目录；**2026-09-29 用户口径改名「加工售后」** ——
            // 目录下恰好是"工厂责任/我方责任"两种售后，与"收退"里的加工退货（有单红冲）不是一回事）。
            // 2026-09-27 用户口径：二级改**目录** 419，把原「一个页面 3 页签」拆成 3 个三级叶子；
            // 2026-09-29 用户口径「三级菜单关联退货不要了，以后关联退货在加工收货里面退就行」
            //   ⇒ 「关联退货」（408）叶子**整体下线**（不再 upsert，改为在下方统一置 visible=0 保号、
            //     保留行与角色授权便于回滚，与 422/424 同范式）；现在目录 419 下只有 2 个叶子：
            //     **420 工厂售后(GTW-) / 421 客户售后(REPAIR)**，两条链路的钱/料方向相反（已核对代码）：
            //       · 工厂售后（原「无单退货」）= **工厂责任**：工厂发来的货、结单后才发现的问题 ⇒
            //         工厂负责维修；修好送回（加工返回单）时按实际用料 FIFO 生成**对工厂的赔料应收**
            //         （source_bill_type=OUTSOURCE_RETURN_BACK），我方不付钱。
            //       · 客户售后（原「成品维修退货」）= **我方责任**：客户退回的售后品 ⇒ 工厂帮我们修，
            //         我方付**维修费应付**（OUTSOURCE_REPAIR_CHARGE）；维修用料扣工厂委外仓、FIFO 摊入
            //         我方回仓成品成本（料算我们的，**不**向工厂收料款）。
            //   有单（关联）的加工退货仍从「加工收退」进：列表行内「退货」/ 收货详细页「加工退货」按钮
            //   → 既有录入页 `/outsource/order/delivery/return-defect/{orderId}`；前端旧地址
            //   `/outsource/return-order` 已改为重定向到「工厂售后」（老书签不吃 403）。
            //  ⚠️ 叶子 perms 必须"自带其 API 需要的码"（目录行的 perms 会被 initMenuPerms 强制清空）：
            //    工厂售后台账接口在 /api/outsource/order-delivery 前缀下（420；412 加工收退同码），
            //    客户售后单在 /api/outsource/return-order 前缀下（421）—— 见 ApiPermGuard.RULES。
            //  ⚠️ 411 是**改父级**（4 → 423）而非新增行：syncMenus 的 upsert 会更新 parent_id，
            //    存量库的角色授权因此不丢（新叶子用下方"从旧叶子继承"的幂等补授覆盖）。
            {419L, 4L, "加工售后", "catalog", "", "", "CircleClose", 3},
            {420L, 419L, "工厂售后", "menu", "/outsource/return-order/unlinked", "OutsourceReturnOrderUnlinked", "Files", 2},
            // 2026-09-27（由 ui-e2e-1-nav 的"标签栏不得同名"不变量抓出）：加工侧与物料侧都有维修退货 ⇒
            // 两处同名会让顶部**标签栏出现两个「维修退货」**（用户无从区分）⇒ 各自带对象前缀去重；
            // 2026-09-29 用户口径：加工侧叶子改「客户售后」（物料侧那条仍是物料维修返回，不受影响）。
            {421L, 419L, "客户售后", "menu", "/outsource/return-order/repair", "OutsourceReturnOrderRepair", "Tools", 3},
            // 422「加工返回单」已于 2026-09-27 按用户要求下线（「多余了，改在详情里登记返回」）：
            //   与 104/302/303/405/406/409/503/602/701 同范式 —— 不再 upsert（upsert 会把 visible 刷回 1），
            //   改为在下方统一置 visible=0，**保留行与角色授权**便于回滚；
            //   返回登记改在「工厂售后」（原无单退货）记录详情页（`/api/outsource/order-delivery/{id}/return-back`，登记即生效）。
            {402L, 4L, "物料订单", "menu", "/outsource/material-order", "OutsourceMaterialOrder", "ShoppingCart", 4},
            // 物料收退（2026-09-16 立，原名「物料收货」；**2026-09-29 用户口径改「物料收退」** ——
            // 与加工侧 412「加工收退」同范式：该页与详细页既有**收货**也有**退货**
            // （详细页工具栏「物料退货」+ 收货记录行内「新增退货」，均为 RECEIVE_RETURN 草稿），故名字带上"退"；
            // **只改文案，id/path/perms/组件均不变**，角色授权与旧书签都不受影响）：
            // 原「物料订单详情 → 交货管理」页签**移出**独立成菜单页 —— 页面按「生产中｜已结单」两个页签列物料订单，
            // 点「收货」进详细页（带 ?add=1 自动弹收货弹窗）；
            // 详细页工具栏 = 新增收货 ｜ 物料退货 ｜ 结单（已结单时显示 反结单）。
            // 注意：**收货/退货业务本身未改**（OrderDeliveryController / OutsourceOrderDeliveryService /
            // MaterialOrderController 的收货、退货、库存、应付、BOM还料逻辑均未动）
            {415L, 4L, "物料收退", "menu", "/outsource/material-order/delivery", "OutsourceMaterialOrderDelivery", "Van", 5},
            // 物料售后（**2026-09-29 用户口径**「委外加工子菜单『物料退货』改名『物料售后』；关联退料不需要了，
            //   以后关联退料在物料收退做；下面的子菜单改为 工厂维修 + 退货退款」）—— 与加工侧 419「加工售后」同范式：
            //   目录 423 改名「物料售后」（id/type/sort 不变），两个叶子**按类型**分（不再是 关联/无单）：
            //     424 工厂维修(REPAIR) `/outsource/material-return/repair`
            //         —— 2026-09-28 曾下线（当时口径"维修返回只是一种类型"），本次按新口径**恢复为真叶子**
            //            ⇒ 同时要从下方 visible=0 列表里移除本 id。
            //     425 退货退款(REFUND) `/outsource/material-return/unlinked`
            //         —— path 沿用（与加工侧「工厂售后」沿用 /unlinked 同款；改名只改文案，老链接不断）。
            //   「关联退料」（挂在物料订单上的退料）2026-09-29 起**改在「物料收退」做**：收退详情页工具栏
            //     「物料退货」→ 落 RECEIVE_RETURN 记录，审核后 冲减该单已收数量 + 冲减应付（用户口径
            //     「关联退料需要冲减应付，然后减少该订单的收货数量」）⇒ 411 叶子下线。
            //   两个叶子同 API 前缀 /api/outsource/material-return ⇒ perms 同码；列表口径写死
            //   linked=WITHOUT_ORDER（关联单不再进本模块列表 —— 与加工侧一致，历史关联单仍可从库存流水/应收点进详情）。
            {423L, 4L, "物料售后", "catalog", "", "", "Refrigerator", 6},
            {424L, 423L, "工厂维修", "menu", "/outsource/material-return/repair", "OutsourceMaterialReturnRepair", "Tools", 1},
            {425L, 423L, "退货退款", "menu", "/outsource/material-return/unlinked", "OutsourceMaterialReturnUnlinked", "Files", 2},
            // 411「关联退料」已于 2026-09-29 下线（用户口径「关联退料不需要了，以后关联退料在物料收退做就行」）：
            //   与加工侧 408「关联退货」同范式 —— 不再 upsert（upsert 会把 visible 刷回 1），
            //   改在下方统一置 visible=0，**保留行与角色授权**便于回滚；
            //   旧地址 /outsource/material-return 在前端路由里重定向到「退货退款」，老书签不吃 403。
            // 409「供应商管理」已于 2026-09-17 按用户要求下线：它是委外加工侧的**重复入口**（与基础数据 106
            // 「供应商管理」同指 /supplier/manage，页面完全相同），基础数据里 106/107 两份都保留。
            // 与 104/405/302/303 同范式：下方统一置 visible=0（保留行与角色授权，便于回滚）。
            // 403「物料信息」已于 2026-09-16 迁入「基础数据」（紧跟物料类型管理）→ 此处不再 upsert。
            // 406/407/404/410/413「物料收发单 / 物料其他出入库 / 委外仓库 / 自有物料仓 / 物料报损」
            // 已于 2026-09-16 按用户要求迁入新目录「物料仓库」(11)——**路由路径全部不变**，故不涉白名单/重定向
            // 405「加工合同模板」已并入 108「模版管理」（基础数据，2026-09-15），不再在此 upsert
            // 物料仓库（11，2026-09-16 新增；同日按用户要求重排为「仓库 → 盘点 → 单据」；
            // 2026-09-22 把「委外仓库 / 自有物料仓」迁入「基础数据」、同日新增「物料库存流水」(417)、
            // 2026-09-24 新增「物料移仓」(418) 顶第 1 位；2026-09-29 用户口径：**物料其他出入库排到物料报损前面**）：
            // 物料移仓 → 物料库存详情 → 物料库存流水 → 物料库存盘点 → 物料其他出入库 → 物料报损
            // （2026-09-29 只对调 413/407 的 sort_order —— 与 2026-09-22 成品侧 704/713 对调同范式：
            //  id / perms / 路由 / 授权一律不动（不动前端白名单）；左侧栏顺序 = sort_order
            //  （MenuMapper.selectAllEnabled），改完要看效果需清前端 localStorage 的菜单缓存，用例里的 OpenFresh 已清；
            //  钉死本顺序的断言见 tools/regression/verify-material-warehouse-menu.ps1 的 ②b）
            // 物料库存详情（2026-09-21 新增；2026-09-22 由「物料库存情况」改名，仅展示名）：
            // 镜像成品侧「成品库存详情」（712），只是统计物料而非成品 ——
            // 列表按物料跨仓汇总（良品/不良两档，物料走 QualityType，没有成品的 A/B/C/待整理/安全库存），
            // 点行进详情看该物料在各仓库的分布。（原「委外仓库 1 → 本页 2 → 自有物料仓 3 → …」的两个仓库项已迁出）
            {416L, 11L, "物料库存详情", "menu", "/outsource/material-stock", "OutsourceMaterialStock", "Box", 2},
            // 物料库存流水（2026-09-22 用户要求新增，方案 A）：与成品侧「成品库存流水」(703) 同构 ——
            // 用同一张 warehouse_stock_log（物料行 = material_id 非空）与同一接口 /warehouse/stock/log?stockType=MATERIAL，
            // 零 DDL、零迁移。（方案选型见当日记录：A=新建列表页；B=并入成品流水页；C=只挂明细页，后两者因归属/体验被否）
            // sort 2 = 紧跟「物料库存详情」，与成品侧"详情 → 流水"的顺序同构；其余 4 项 sort 顺移。
            {417L, 11L, "物料库存流水", "menu", "/outsource/material-stock-log", "OutsourceMaterialStockLog", "TrendCharts", 3},
            // 物料库存盘点（2026-09-16 用户要求）：与成品「库存盘点」按仓库类别彻底分开 ——
            // 本页只盘物料仓（委外仓 + 自有物料仓），成品页只盘成品类仓库；
            // 且本页**接口级限「跟单专员」**（见 StockTakeServiceImpl.assertRoleForScope，管理员兜底）
            {414L, 11L, "物料库存盘点", "menu", "/outsource/material-stock-take", "OutsourceMaterialStockTake", "DocumentChecked", 4},
            // 物料其他出入库（2026-09-29 用户口径「物料其他出入库放在物料报损前面」⇒ 与 413 对调 sort_order：5←6）
            {407L, 11L, "物料其他出入库", "menu", "/outsource/other-io", "OutsourceOtherIo", "Files", 5},
            // 物料报损：与成品报损独立成表（主体为 outsource_material，物料库存不区分品质，固定按良品扣减）
            // （2026-09-29 用户口径：让位给「物料其他出入库」⇒ sort_order 6←5）
            {413L, 11L, "物料报损", "menu", "/outsource/stock-loss", "OutsourceStockLoss", "DeleteFilled", 6},
            // 406「物料收发单」已于 2026-09-24 按用户要求下线（「不要了，改用物料移仓代替」）：
            //   与 104/302/303/405/409/503/602/701 同范式 —— 不再 upsert（upsert 会把 visible 强制刷回 1），
            //   改为在下方统一置 visible=0；**保留行与角色授权**，故 outsource:delivery 权限码仍授予原三角色，
            //   库存流水/物料收货跳转的 /outsource/delivery/detail/:id 仍可正常读（详见 ApiPermGuard 的说明）。
            // 物料移仓（2026-09-24 新增）：用户口径「物料收发单不要了，改用物料移仓代替」——
            // 按成品移仓单（706「移仓单」）同构复刻，承接原手工收发单的 发料（我方物料仓 → 委外仓）
            // 与 调拨（物料相关仓之间互转）；收料/退不良仍走物料收货流程，与本页无关。
            {418L, 11L, "物料移仓", "menu", "/inventory/material-move", "InventoryMaterialMove", "Rank", 1},
            {501L, 5L, "成品采购单", "menu", "/inventory/purchase", "InventoryPurchase", "ShoppingCart", 1},
            {502L, 5L, "采购退货单", "menu", "/inventory/purchase-return", "InventoryPurchaseReturn", "Refrigerator", 2},
            // 采购换货单（2026-09-18 用户要求）：向供货商采购的成品也可换货 —— 把不良品退回供货商 + 换回良品，
            // 一张单管住"出一进"（退回出库扣减 + 换入入库增加），并生成两条应付台账（退回负 / 换入正），净额即差价
            {504L, 5L, "采购换货单", "menu", "/inventory/purchase-exchange", "InventoryPurchaseExchange", "Refresh", 3},
            // 503「供货商管理」已于 2026-09-18 按用户要求下线：它是进货业务侧的**重复入口**
            // （与基础数据 107「供货商管理」同指 /outsource/supplier/manage、同 route_name，页面完全相同）
            // ——基础数据里已有，故与 602/409/104 同范式：不再 upsert，下方统一置 visible=0。
            // 注意：merchandiser 原先**只有 409**（2026-09-17 已被隐藏的委外侧重复入口）、没有 107，
            // 若只隐藏 503/409 会让跟单专员看不到「供货商管理」⇒ 必须补授 107（见 initRoleMenus 存量补授块）。
            {601L, 6L, "销售单", "menu", "/inventory/sale", "InventorySale", "Sell", 1},
            // 602「客户管理」已于 2026-09-18 按用户要求下线：它是销售业务侧的**重复入口**
            // （与基础数据 105「客户管理」同指 /inventory/customer，页面完全相同）——基础数据里已有，
            // 故与 409/104/405/302/303 同范式：不再 upsert，下方统一置 visible=0（保留行与角色授权，便于回滚）。
            // 注意：sales / merchandiser 原先只授了 602，下线后必须补授 105（见 initRoleMenus 的存量补授块），
            // 否则这两个角色会失去「客户管理」入口与其前端菜单白名单。
            // 售后：销售退货单 → 销售换货单（换货可选择性收费）；「退货整理」已于 2026-09-18 移到「成品库存」
            {603L, 6L, "销售退货单", "menu", "/sale/return", "SaleReturn", "Refund", 2},
            {605L, 6L, "销售换货单", "menu", "/sale/exchange", "SaleExchange", "Refresh", 3},
            // 成品库存子菜单顺序（2026-09-18 用户定稿重排；2026-09-22 用户要求把 705 挪到第 3 位、704 提到 713 上面）：
            // 移仓单 → 退货整理 → 规格调整 → 成品库存详情 → 成品库存流水 → 库存盘点 → 成品其他出入库 → 成品报损
            // （2026-09-22：706「成品移仓单」改展示名为「移仓单」、712「成品库存情况」改「成品库存详情」、
            //  705「成品品质重分类」改「规格调整」并由第 8 位挪到第 3 位（只改展示名与 sort_order，id/perms/路由不动）；
            //  同日 704「成品其他出入库」与 713「成品报损」对调（其他出入库排到报损上面）；
            //  末位 702「成品仓库管理」按用户要求迁入「基础数据」⇒ 本组由 9 项变 8 项）
            // sort_order 即左侧栏显示顺序（MenuMapper.selectAllEnabled 按 sort_order 排序）；下方书写顺序与实际显示顺序一致，便于维护。
            {706L, 7L, "移仓单", "menu", "/inventory/warehouse-move", "InventoryWarehouseMove", "Rank", 1},
            // 退货整理（2026-09-18 用户要求：从「销售业务」移到「成品库存」—— 它本质是退回品的成品分选入库）
            // ID 仍保留 707（存量角色授权按 ID 关联，换 ID 会导致历史授权失效），仅迁移 parent_id 6 → 7；
            // 同日用户重排本组顺序，退货整理由第 10 位改排**第 2 位**（紧跟移仓单）。
            // ⚠️ 父目录 7 必须同时授权，否则菜单树 buildTree 会把 707 整组丢弃
            // （下方幂等补授块给其它持有 707 的角色兜底）。
            {707L, 7L, "退货整理", "menu", "/inventory/return-sort", "InventoryReturnSort", "RefreshRight", 2},
            // 规格调整（2026-09-22 用户要求：由「成品品质重分类」改展示名、并由第 8 位挪到**第 3 位**）：
            // 同一仓库内产品在不同品质等级之间转换（如 A规 降级为 B规）。
            // id 705 / perms(stock:reclassify) / 路由(/inventory/reclassify) 一律不动
            // ⇒ 不迁权限、不动前端白名单（排序由本行 upsert 的 sort_order 覆盖）。
            {705L, 7L, "规格调整", "menu", "/inventory/reclassify", "InventoryReclassify", "Refresh", 3},
            // 成品库存详情（2026-09-22 由「成品库存情况」改名，仅展示名）：按产品维度看跨仓库库存汇总；
            // 点行进详情看该产品在各仓库的分布
            {712L, 7L, "成品库存详情", "menu", "/inventory/product-stock", "InventoryProductStock", "Box", 4},
            {703L, 7L, "成品库存流水", "menu", "/inventory/stock-log", "WarehouseStockLog", "TrendCharts", 5},
            // 库存盘点：每月每仓一次，仓库列表与盘点页显示待盘点/超期提醒
            {711L, 7L, "库存盘点", "menu", "/inventory/stock-take", "InventoryStockTake", "Files", 6},
            // 成品其他出入库（2026-09-22 用户要求：与成品报损对调，排到报损上面 ⇒ 只改 sort_order）
            {704L, 7L, "成品其他出入库", "menu", "/inventory/other-io", "InventoryOtherIo", "Upload", 7},
            // 成品报损：草稿→审核扣减成品库存（LOSS_OUT 流水），可反审核回滚
            {713L, 7L, "成品报损", "menu", "/inventory/stock-loss", "InventoryStockLoss", "DeleteFilled", 8},
            // 701「成品库存」（/inventory/stock）已于 2026-09-18 按用户要求下线（页面代码已删，功能由
            // 「712 成品库存情况」按产品维度覆盖）⇒ 与 409/602/503 同范式：不再 upsert，
            // 下方统一置 visible=0（保留行与角色授权，便于回滚）；其历史 sort_order 已在下方挪到 99，
            // 不再占用 1~9，避免与 707 并列（将来若回滚启用也不会产生顺序歧义）。
            // 财务管理子菜单顺序（2026-09-18 用户定稿重排；2026-09-22 用户要求把「账单生成」挪到「付款管理」下面）：
            // 收款管理 → 付款管理 → 账单生成 → 费用管理 → 应收管理 → 应付管理 → 资金流水 → 账户管理 → 发票管理 → 应付转应收
            // sort_order 即左侧栏显示顺序（MenuMapper.selectAllEnabled 按 sort_order 排序）；下方书写顺序与实际显示顺序一致，便于维护。
            {805L, 8L, "收款管理", "menu", "/finance/receipt", "FinanceReceipt", "Money", 1},
            {806L, 8L, "付款管理", "menu", "/finance/payment", "FinancePayment", "Sell", 2},
            // 账单生成（2026-09-22 用户要求）：由首位挪到「付款管理」下面 ⇒ 只改 sort_order，id / 路由 / 授权均不变
            // （所以不涉及前端白名单与跳转；菜单顺序缓存在前端 localStorage，改完要让用例先清缓存再看）
            {803L, 8L, "账单生成", "menu", "/finance/bill", "FinanceBill", "Postcard", 3},
            // 费用登记：审核扣减账户并生成「费用支出」流水，供财务分析利润表取数
            {809L, 8L, "费用管理", "menu", "/finance/expense", "FinanceExpense", "Tickets", 4},
            {801L, 8L, "应收管理", "menu", "/finance/receivable", "FinanceReceivable", "Wallet", 5},
            {802L, 8L, "应付管理", "menu", "/finance/payable", "FinancePayable", "CreditCard", 6},
            {804L, 8L, "资金流水", "menu", "/finance/cashflow", "FinanceCashflow", "TrendCharts", 7},
            // 807 自「资金流水」页拆分为独立子菜单；2026-09-18 按用户要求菜单名由「资金账户」改为「账户管理」
            // （纯文案：id / parent_id / route_path / route_name / 授权均不变，故不涉及白名单与跳转；同日并入本组重排 → 第 8 位）
            {807L, 8L, "账户管理", "menu", "/finance/account", "FinanceAccount", "Coin", 8},
            // 发票登记：销项/进项发票（税务口径），供税务分析发票汇总取数
            {810L, 8L, "发票管理", "menu", "/finance/invoice", "FinanceInvoice", "Stamp", 9},
            // 应付转应收：退货/超损扣款（负向应付）在无货款可抵时，转为向供应商收款
            {811L, 8L, "应付转应收", "menu", "/finance/payable-transfer", "FinancePayableTransfer", "Refresh", 10},
            {901L, 9L, "智能管理", "menu", "/system/smart", "SystemSmart", "Cpu", 1},
            {902L, 9L, "用户管理", "menu", "/system/user", "SystemUser", "UserFilled", 2},
            {904L, 9L, "系统信息", "menu", "/system/settings", "SystemSettings", "Setting", 4},
            {905L, 9L, "数据管理", "menu", "/system/data-manage", "SystemDataManage", "Folder", 5},
            {906L, 9L, "角色管理", "menu", "/system/role", "SystemRole", "Avatar", 6},
            {907L, 9L, "菜单管理", "menu", "/system/menu", "SystemMenu", "Menu", 7},
            // F8-19（2026-09-30 审核批 D）：文案「清空数据」→「清空本公司数据」——原名易被读成
            // "清空（整个系统/数据库）"，而后端只清**本公司业务数据**（sys_* 系统表保留）。
            // 菜单码 system:clear-data 因 /api/system 整段在 ApiPermGuard 的 EXEMPT 里而**不用于接口收口**，
            // 授权口径是角色（ClearController 类级 admin|super_admin；全库清空仅 super_admin）。
            {908L, 9L, "清空本公司数据", "menu", "/system/clear-data", "SystemClearData", "Delete", 8},
            // ==================== 经营分析（目录 10）：原「财务分析」5 个 Tab 拆分 + 新增销售/客户分析 ====================
            // 顺序（2026-09-15 用户定稿「方案X」）：经营概览 → 销售分析 → 客户分析 → [进货分析(1007 待建)] → 税务分析 → 资金往来
            {1001L, 10L, "经营概览", "menu", "/analysis/overview", "AnalysisOverview", "DataLine", 1},
            // 2026-09-15：原 1002「利润表」(/analysis/profit) 已按用户要求整体下线（页面/路由/接口/菜单均已删除）
            // 销售分析：销售额趋势 + 产品/仓库排行，行可下钻到销售单明细
            {1005L, 10L, "销售分析", "menu", "/analysis/sale", "AnalysisSale", "Sell", 2},
            // 客户分析：客户销售额排行 + 欠款/账期，行可下钻到该客户的销售单
            {1006L, 10L, "客户分析", "menu", "/analysis/customer", "AnalysisCustomer", "UserFilled", 3},
            // 进货分析（2026-09-15 新增）：区间采购 KPI（采购金额/采购退货/净采购额/采购单数）+ 趋势 + 单据明细下钻
            {1007L, 10L, "进货分析", "menu", "/analysis/purchase", "AnalysisPurchase", "ShoppingCart", 4},
            {1004L, 10L, "税务分析", "menu", "/analysis/tax", "AnalysisTax", "Stamp", 5},
            // 资金往来（2026-09-15 由「资金与往来」改名）：资金趋势 + 应收应付账龄 + 主体往来统计
            {1003L, 10L, "资金往来", "menu", "/analysis/cash", "AnalysisCash", "Wallet", 6},
        };
        // P1（2026-09-30 未上线清理）：此处原来会"确保 customized 列存在"（F8-21 的存量库补列）——
        // 该列已由 db/migration/V1__base.sql 声明，启动期不再做 DDL（缺列由 assertSchemaReady() 直接报错）。

        // ON DUPLICATE KEY UPDATE 实现 upsert
        int processed = 0;
        for (Object[] m : menus) {
            try {
                // F8-21（2026-09-30 设置模块批 E 修复）：**用户改过的菜单不再被启动同步打回**。
                // 原实现无条件 UPDATE parent_id/menu_name/sort_order/visible/status ⇒ "菜单管理"页的
                // 编辑下次重启即失效（实测 908 改名+调序+隐藏后重启回到种子值）。
                // 现在：结构性字段（menu_type/route_path/route_name/icon）仍随代码升级同步；
                // 展示字段（parent_id/menu_name/sort_order/visible/status）只在 customized=0 时写入，
                // customized=1（用户改过）一律以用户为准。
                jdbcTemplate.update(
                    "INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, route_path, route_name, icon, sort_order, visible, status, customized) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, 1, 0) " +
                    "ON DUPLICATE KEY UPDATE " +
                    "menu_type=VALUES(menu_type), route_path=VALUES(route_path), route_name=VALUES(route_name), " +
                    "icon=VALUES(icon), " +
                    "parent_id=IF(customized=1, parent_id, VALUES(parent_id)), " +
                    "menu_name=IF(customized=1, menu_name, VALUES(menu_name)), " +
                    "sort_order=IF(customized=1, sort_order, VALUES(sort_order)), " +
                    "visible=IF(customized=1, visible, 1), " +
                    "status=IF(customized=1, status, 1)",
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

        // 下线历史菜单：104「阶段模板管理」（基础数据）/ 405「加工合同模板」（委外加工）
        // —— 两者功能已合并进 108「模版管理」（页内 TAB 区分）。这里置 visible=0 而**不删行**：便于回滚，
        // 且 getMenuTreeByRoleIds 只回 status=1 AND visible=1 → 侧栏消失、前端菜单白名单也不再含旧路径
        // （旧路径在前端已改为重定向到 /template 对应页签，直接输 URL 不会吃 403）。
        // 409「供应商管理」2026-09-17 按用户要求一并下线（委外加工侧重复入口，基础数据 106 已有同页面菜单）。
        try {
            // 注意：412 不在此列表 —— 2026-09-16 该 id 已被复用为「成品收货」菜单，
            // 若仍置 visible=0，会在上面的 upsert 之后把新菜单立刻隐藏（upsert 在前、置 0 在后）
            int hidden = jdbcTemplate.update("UPDATE sys_menu SET visible = 0 WHERE id IN (104, 405, 302, 303, 409, 602, 503, 701, 406, 408, 411, 422) AND visible = 1");
            if (hidden > 0) log.info("已下线历史菜单 {} 条（104 阶段模板管理 / 405 加工合同模板 / 302 BOM管理 / 303 图纸文档 / 409 委外加工-供应商管理 / 602 销售业务-客户管理 / 503 进货业务-供货商管理 / 701 成品库存查询 / 406 物料收发单 / 408 关联退货叶子 / 411 关联退料叶子 / 422 加工返回单）", hidden);
        } catch (Exception e) {
            log.warn("下线老菜单异常: {}", e.getMessage());
        }

        // 701 已下线：把它的历史 sort_order（2）挪到 99 —— 2026-09-18 成品库存组重排后 1~9 已被
        // 9 个在用菜单占满，不腾位就会与 707（新 sort_order=2）并列（幂等：仅在不等时更新）。
        try {
            jdbcTemplate.update("UPDATE sys_menu SET sort_order = 99 WHERE id = 701 AND sort_order <> 99");
        } catch (Exception e) {
            log.warn("调整 701 排序位异常: {}", e.getMessage());
        }

        // 406「物料收发单」2026-09-24 已下线（visible=0），但它的历史 sort_order=6 与 **413 物料报损**
        // （2026-09-29 用户口径：407 物料其他出入库提到 413 前面 ⇒ 407→5 / 413→6）并列 —— 同 701/707 先例，
        // 把隐藏行的排序位腾到 99：幂等（仅在不等时更新），**保留行与角色授权**，将来回滚启用也不会顺序歧义。
        try {
            jdbcTemplate.update("UPDATE sys_menu SET sort_order = 99 WHERE id = 406 AND sort_order <> 99");
        } catch (Exception e) {
            log.warn("调整 406 排序位异常: {}", e.getMessage());
        }

        // 2026-09-21（用户口径）：子菜单「销售退单」改名「销售退货单」—— 与采购侧「采购退货单」、
        // 财务来源类型「销售退货」统一术语。只改**展示名**：菜单 id / perms(sale:return) / 路由 path
        // 一律不动（不动就等于不迁权限、不动前端白名单）。幂等：仅当值仍是旧名时才更新，重复启动零写入；
        // 新库/其它环境由上面的 upsert（同一 id=603 已用新名）写入，这里兜住"库里还是旧名"的存量库。
        try {
            int renamed = jdbcTemplate.update(
                    "UPDATE sys_menu SET menu_name = '销售退货单' WHERE id = 603 AND menu_name = '销售退单'");
            if (renamed > 0) log.info("已重命名菜单 603：销售退单 -> 销售退货单");
        } catch (Exception e) {
            log.warn("重命名菜单 603 异常: {}", e.getMessage());
        }

        // 2026-09-22（用户口径）：子菜单「成品移仓单」改名「移仓单」—— 与「新增移仓单」「移仓单详细」
        // 及库存流水里的「移仓单」单据类型统一术语。只改**展示名**：菜单 id 706 / perms(stock:warehouse-move) /
        // 路由 path/route_name 一律不动（不动就等于不迁权限、不动前端白名单）。
        // 幂等：仅当值仍是旧名时才更新，重复启动零写入；新库/其它环境由上面的 upsert（同一 id=706 已用新名）写入，
        // 这里兜住"库里还是旧名"的存量库（与 603 销售退单→销售退货单 同范式）。
        try {
            int renamedMove = jdbcTemplate.update(
                    "UPDATE sys_menu SET menu_name = '移仓单' WHERE id = 706 AND menu_name = '成品移仓单'");
            if (renamedMove > 0) log.info("已重命名菜单 706：成品移仓单 -> 移仓单");
        } catch (Exception e) {
            log.warn("重命名菜单 706 异常: {}", e.getMessage());
        }

        // 2026-09-22（用户口径）：两处「库存情况」统一改名「库存详情」—— 712 成品侧、416 物料侧。
        // 同上只改**展示名**：id / perms(stock:product-stock、outsource:material-stock) / 路由一律不动。
        // 幂等：仅当值仍是旧名时才更新，重复启动零写入（新库由上面的 upsert 直接写新名）。
        try {
            int renamedProd = jdbcTemplate.update(
                    "UPDATE sys_menu SET menu_name = '成品库存详情' WHERE id = 712 AND menu_name = '成品库存情况'");
            if (renamedProd > 0) log.info("已重命名菜单 712：成品库存情况 -> 成品库存详情");
            int renamedMat = jdbcTemplate.update(
                    "UPDATE sys_menu SET menu_name = '物料库存详情' WHERE id = 416 AND menu_name = '物料库存情况'");
            if (renamedMat > 0) log.info("已重命名菜单 416：物料库存情况 -> 物料库存详情");
        } catch (Exception e) {
            log.warn("重命名菜单 712/416 异常: {}", e.getMessage());
        }

        // 2026-09-22（用户口径）：705「成品品质重分类」改名「规格调整」（同时挪到成品库存第 3 位，
        // 排序由种子 upsert 的 sort_order 覆盖）。同上只改**展示名**：
        // id / perms(stock:reclassify) / 路由(/inventory/reclassify) 一律不动。
        try {
            int renamedReclass = jdbcTemplate.update(
                    "UPDATE sys_menu SET menu_name = '规格调整' WHERE id = 705 AND menu_name = '成品品质重分类'");
            if (renamedReclass > 0) log.info("已重命名菜单 705：成品品质重分类 -> 规格调整");
        } catch (Exception e) {
            log.warn("重命名菜单 705 异常: {}", e.getMessage());
        }

        // 404「委外仓库」/ 410「自有物料仓」（2026-09-23 用户要求）：**只改展示名**，与 702 成品仓库管理 对齐；
        // id / perms(outsource:warehouse、outsource:material-warehouse) / route_name / 路由 一律不动。
        // 幂等：仅当值仍是旧名时才更新，重复启动零写入（新库由上面的种子 upsert 直接写新名）。
        try {
            int renamedWh = jdbcTemplate.update(
                    "UPDATE sys_menu SET menu_name = '委外仓库管理' WHERE id = 404 AND menu_name = '委外仓库'");
            if (renamedWh > 0) log.info("已重命名菜单 404：委外仓库 -> 委外仓库管理");
            int renamedMatWh = jdbcTemplate.update(
                    "UPDATE sys_menu SET menu_name = '自有物料仓管理' WHERE id = 410 AND menu_name = '自有物料仓'");
            if (renamedMatWh > 0) log.info("已重命名菜单 410：自有物料仓 -> 自有物料仓管理");
        } catch (Exception e) {
            log.warn("重命名菜单 404/410 异常: {}", e.getMessage());
        }

        // F3-3（2026-09-18 接口级权限专项）：写页面级接口权限码（幂等）
        initMenuPerms();
    }







    /** 判断某表是否已有某列（幂等 DDL 用；启动阶段无租户上下文，多租户插件不会改写本查询） */
    private boolean columnExists(String table, String column) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class, table, column);
        return n != null && n > 0;
    }

    private void initMenuPerms() {
        Object[][] perms = {
                // ===== 首页 =====
                {1L, "dashboard"},
                // ===== 基础数据（目录 2）=====
                {101L, "base:product"},
                {102L, "base:brand"},
                {103L, "base:material-type"},
                {105L, "base:customer"},
                {106L, "base:supplier"},
                {107L, "outsource:supplier"},
                {108L, "base:template"},
                {403L, "outsource:material-info"},
                // 702 成品仓库管理：2026-09-22 随菜单自「成品库存」迁入「基础数据」；
                // **perms 值仍是 stock:warehouse**（接口级权限码，改了会让仓库接口被拦）—— 迁移只动 parent_id/sort_order
                {702L, "stock:warehouse"},
                // 404 委外仓库 / 410 自有物料仓：2026-09-22 随菜单自「物料仓库」迁入「基础数据」；
                // **perms 值不变**（接口级权限码，改了会让仓库接口被拦）—— 迁移只动 parent_id/sort_order
                {404L, "outsource:warehouse"},
                {410L, "outsource:material-warehouse"},
                // ===== 研发管理（目录 3）=====
                {301L, "dev:project"},
                {304L, "dev:material"},
                {305L, "dev:screen-model"},
                // ===== 委外加工（目录 4）=====
                {401L, "outsource:order"},
                {402L, "outsource:material-order"},
                // 2026-09-29：408「关联退货」叶子已下线（visible=0）⇒ 不再写 perms —— 它原先的码与
                //   420「无单退货」/412「加工收货」相同（outsource:order-delivery），由那两行承担；
                //   存量库该行的 perms 会残留但**不可见即不生效**（有效权限 = 可见菜单的 perms 集合）。
                // 2026-09-29：411「关联退料」已下线（visible=0）⇒ 不再写 perms（它的码与 424/425 相同，
                //   由那两个叶子承担；存量库该行 perms 残留但**不可见即不生效**）；
                //   424「工厂维修」本次按新口径**恢复为真叶子** ⇒ 重新写 perms。
                {424L, "outsource:material-return"},
                {425L, "outsource:material-return"},
                {420L, "outsource:order-delivery"},
                {421L, "outsource:return-order"},
                // 422 已下线（visible=0）：不再写 perms —— 它原先的码与 421 相同，由 421 承担；
                // 存量库该行的 perms 会残留但**不可见即不生效**（有效权限 = 可见菜单的 perms 集合）。
                // 424（物料维修退货叶子）已于 2026-09-28 下线（visible=0）：不再写 perms ——
                // 它原先的码与 411/425 相同，由那两个叶子承担；存量库该行的 perms 残留但不可见即不生效。
                {412L, "outsource:order-delivery"},
                {415L, "outsource:material-delivery"},
                // ===== 进货业务（目录 5）=====
                {501L, "purchase:order"},
                {502L, "purchase:return"},
                {504L, "purchase:exchange"},
                // ===== 销售业务（目录 6）=====
                {601L, "sale:order"},
                {603L, "sale:return"},
                {605L, "sale:exchange"},
                // ===== 成品库存（目录 7）=====
                {703L, "stock:log"},
                {704L, "stock:other-io"},
                {705L, "stock:reclassify"},
                {706L, "stock:warehouse-move"},
                {418L, "stock:material-move"},
                {707L, "stock:return-sort"},
                {711L, "stock:stock-take"},
                {712L, "stock:product-stock"},
                {713L, "stock:stock-loss"},
                // ===== 财务管理（目录 8）=====
                {801L, "finance:receivable"},
                {802L, "finance:payable"},
                {803L, "finance:bill"},
                {804L, "finance:cashflow"},
                {805L, "finance:receipt"},
                {806L, "finance:payment"},
                {807L, "finance:account"},
                {809L, "finance:expense"},
                {810L, "finance:invoice"},
                {811L, "finance:payable-transfer"},
                // ===== 设置（目录 9）=====
                {901L, "system:smart"},
                {902L, "system:user"},
                {904L, "system:settings"},
                {905L, "system:data-manage"},
                {906L, "system:role"},
                {907L, "system:menu"},
                {908L, "system:clear-data"},
                // ===== 经营分析（目录 10）=====
                {1001L, "analysis:overview"},
                {1003L, "analysis:cash"},
                {1004L, "analysis:tax"},
                {1005L, "analysis:sale"},
                {1006L, "analysis:customer"},
                {1007L, "analysis:purchase"},
                // ===== 物料仓库（目录 11）=====
                {406L, "outsource:delivery"},
                {407L, "outsource:other-io"},
                {413L, "outsource:stock-loss"},
                {414L, "outsource:material-stock-take"},
                // 物料库存情况（2026-09-21）：与成品侧 712 的 stock:product-stock 同范式，
                // 页面菜单的 perms 即接口级权限码 —— 漏登记会让新页面的接口调用被拦。
                {416L, "outsource:material-stock"},
                // 物料库存流水（2026-09-22 新增）：查询类页面，同 416 范式 —— perms 即接口级权限码。
                // 注意它复用的是 /api/warehouse/stock/log（该接口本身在 ApiPermGuard 的已登记前缀内）。
                {417L, "outsource:material-stock-log"},
        };
        int updated = 0;
        for (Object[] p : perms) {
            try {
                // 仅在"与目标值不同"时更新 —— 每次启动零写入（幂等）
                updated += jdbcTemplate.update(
                        "UPDATE sys_menu SET perms = ? WHERE id = ? AND (perms IS NULL OR perms <> ?)",
                        p[1], p[0], p[1]);
            } catch (Exception e) {
                log.warn("写菜单权限码失败: id={}, err={}", p[0], e.getMessage());
            }
        }
        // 目录/非页面菜单一律不携带权限码（防止历史脏值让"看不见的目录"被授出接口权限）
        try {
            // 只清目录，**保留 button 行**的权限码（按钮级权限就靠它承载）
            int cleaned = jdbcTemplate.update("UPDATE sys_menu SET perms = NULL "
                    + "WHERE (menu_type IS NULL OR menu_type NOT IN ('menu', 'button')) AND perms IS NOT NULL");
            if (cleaned > 0) log.info("已清理非页面菜单的权限码 {} 条", cleaned);
        } catch (Exception e) {
            log.warn("清理目录权限码异常: {}", e.getMessage());
        }
        if (updated > 0) log.info("已同步菜单权限码 {} 条（F3-3 接口级权限）", updated);

        // F3-3 按钮级权限（方案 A）：动作码登记为 sys_menu 的 button 行（挂在所属页面下）
        initButtonPerms();
    }

    /**
     * F3-3 按钮级权限（**方案 A：动作码默认跟随页面**）—— 把"审核/反审核/作废/删除"这类动作
     * 登记为 {@code menu_type='button'} 的菜单行（挂在所属页面行下），权限码形如
     * {@code purchase:exchange:audit}。
     *
     * <p>为什么用 sys_menu 行而不是硬编码常量：①动作清单可查、可在菜单管理页看到；②后端服务
     * {@code MenuService.collectPermsWithButtons} 按 {@code parent_id ∈ 已授权页面} 自动带出动作码
     * ⇒ **完全不改变现有授权数据**（无需给 6 个角色补授，升级零风险）；③将来若要切到"动作码独立授权"，
     * 只需改那一处扩展逻辑（不再自动带出）+ 补授，本表即为授权 UI 的数据基础。</p>
     */
    private void initButtonPerms() {
        // {id, parentPageId, 动作名, 动作码}
        Object[][] buttons = {
                {9101L, 504L, "审核", "purchase:exchange:audit"},
                {9102L, 504L, "反审核", "purchase:exchange:unaudit"},
                {9103L, 504L, "作废", "purchase:exchange:cancel"},
                // 2026-09-24（用户口径 B：采购单详情对齐销售单）：采购单此前**只有页级码**（501 purchase:order），
                // 没有按钮级码 ⇒ 新增 审核/反审核/作废 三条，供详情页 v-perm 使用
                {9151L, 501L, "审核", "purchase:order:audit"},
                {9152L, 501L, "反审核", "purchase:order:unaudit"},
                {9153L, 501L, "作废", "purchase:order:cancel"},
                {9111L, 502L, "审核", "purchase:return:audit"},
                {9112L, 502L, "反审核", "purchase:return:unaudit"},
                {9113L, 502L, "作废", "purchase:return:cancel"},
                {9114L, 502L, "删除", "purchase:return:delete"},
                {9121L, 601L, "审核", "sale:order:audit"},
                {9122L, 601L, "反审核", "sale:order:unaudit"},
                {9123L, 601L, "作废", "sale:order:cancel"},
                {9131L, 603L, "审核", "sale:return:audit"},
                {9132L, 603L, "反审核", "sale:return:unaudit"},
                {9133L, 603L, "作废", "sale:return:cancel"},
                {9134L, 603L, "删除", "sale:return:delete"},
                // 2026-10-01（F8 进货/销售退换货改造·第 1 步）：605「销售换货单」此前**只有页级码**
                // （sale:exchange），没有任何按钮级码 —— 而 601 销售单 / 603 销售退货单都有，
                // 属同批遗漏的安全缺口（换货单的审核/反审核/作废无从按动作管控）。
                // 与 504 采购换货单的 9101~9103 对称；id 取 9141+ 避开已用的 9101-9103 / 9111-9114 /
                // 9121-9123 / 9131-9134 / 9151-9153。
                {9141L, 605L, "审核", "sale:exchange:audit"},
                {9142L, 605L, "反审核", "sale:exchange:unaudit"},
                {9143L, 605L, "作废", "sale:exchange:cancel"},
        };
        int processed = 0;
        for (Object[] b : buttons) {
            try {
                jdbcTemplate.update(
                        "INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, route_path, route_name, "
                                + "icon, sort_order, visible, status, perms) "
                                + "VALUES (?, ?, ?, 'button', '', '', '', 0, 1, 1, ?) "
                                + "ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id), menu_name=VALUES(menu_name), "
                                + "menu_type='button', perms=VALUES(perms), visible=1, status=1",
                        b[0], b[1], b[2], b[3]);
                processed++;
            } catch (Exception e) {
                log.warn("同步按钮权限失败: id={}, err={}", b[0], e.getMessage());
            }
        }
        log.info("已同步按钮级权限码 {} 条（方案 A：跟随页面自动授予）", processed);
    }

    /**
     * 为6个角色分别授权对应菜单（必须包含各自父目录，否则菜单树 buildTree 会把子菜单丢弃）
     * 注：604「销售换货单」已废弃，现为 605。
     */
    private void initRoleMenus() {
        // 清理指向已不存在菜单的脏授权（历史菜单改 id / 删除后残留），幂等自愈
        jdbcTemplate.update(
                "DELETE rm FROM sys_role_menu rm LEFT JOIN sys_menu m ON m.id = rm.menu_id WHERE m.id IS NULL");

        // 管理员：全部权限（104 阶段模板管理 / 405 加工合同模板 已并入 108 模版管理）
        assignRoleMenus("admin", Arrays.asList(
                1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L,
                101L, 102L, 103L, 105L, 106L, 107L, 108L,
                301L, 304L, 305L,
                401L, 402L, 403L, 404L, 406L, 407L, 408L, 409L, 410L, 411L, 412L, 413L, 414L, 415L,
                // 2026-09-27 三级菜单：419 加工售后目录 + 420/421 叶子（422 加工返回单已下线）；
                // 2026-09-29：423 改名「物料售后」目录 + 424/425 叶子（411 关联退料已下线，保留授权便于回滚）
                419L, 420L, 421L, 423L, 424L, 425L,
                501L, 502L, 503L, 504L,
                601L, 602L, 603L, 605L,
                702L, 703L, 704L, 705L, 706L, 711L, 712L, 713L,
                418L,
                801L, 802L, 803L, 804L, 805L, 806L, 807L, 809L, 810L, 811L,
                1001L, 1003L, 1004L, 1005L, 1006L, 1007L,
                901L, 902L, 903L, 904L, 905L, 906L, 907L, 908L));
        // 研发工程师：项目研发 + BOM + 基础产品（2 基础数据 = 101 产品管理 / 108 模版管理 的父目录）
        // 108 模版管理（内含阶段模板）：阶段模板本是研发在用，2026-09-15 随两页合并一并补授
        assignRoleMenus("dev_engineer", Arrays.asList(
                1L, 2L, 3L, 301L, 304L, 305L, 101L, 108L));
        // 销售专员：销售业务 + 客户 + 产品（605 换货单；2 基础数据）
        // 注：经营分析（目录 10 及其子页）自 2026-09-15 起**仅管理者可见**，故不再授予 sales
        // 105「客户管理」（基础数据）：2026-09-18 起客户管理只保留基础数据这一处入口，故销售专员必须授 105
        // （602 保留授权仅为回滚便利，已 visible=0 不再出菜单）
        assignRoleMenus("sales", Arrays.asList(
                1L, 2L, 6L, 601L, 602L, 603L, 605L, 101L, 105L));
        // 仓管员：进货 + 库存 + 仓库 + **物料仓库整组**（2026-09-16 用户要求：
        // 11 目录 + 404 委外仓库 / 406 物料收发单 / 407 物料其他出入库 / 410 自有物料仓 / 413 物料报损）
        // （2 基础数据 / 6 销售业务 / 11 物料仓库 为其子菜单的父目录）
        // 707「退货整理」（成品库存）：2026-09-18 用户要求补授 —— 它是**成品分选入库的操作页**，仓管员日常在用
        assignRoleMenus("warehouse", Arrays.asList(
                1L, 2L, 5L, 6L, 7L, 11L, 501L, 502L, 504L, 603L, 702L, 703L, 704L, 705L, 706L, 707L, 711L, 712L, 713L,
                404L, 406L, 407L, 410L, 413L, 418L, 101L));
        // 跟单专员：委外加工全部 + 相关基础数据/进货/销售/成品库存页面 + 物料仓库整组（含 414 物料库存盘点）
        // （原 405 加工合同模板 → 108 模版管理，权限等价迁移）
        // 412 成品收货 / 415 物料收货（2026-09-16）：原详情页签移出成菜单，权限沿用委外加工原范围
        // 107「供货商管理」（基础数据）：2026-09-18 起供货商管理只保留基础数据这一处入口；
        // 跟单专员原先只有 409（9-17 已隐藏的委外侧重复入口）⇒ 必须授 107 才看得到供货商主数据
        // 707「退货整理」（成品库存）：2026-09-18 用户要求补授 —— 售后分选入库链路跟单专员也参与
        assignRoleMenus("merchandiser", Arrays.asList(
                1L, 2L, 4L, 5L, 6L, 7L, 11L,
                401L, 402L, 403L, 404L, 406L, 407L, 408L, 409L, 410L, 412L, 413L, 414L, 415L, 418L,
                // 2026-09-27 三级菜单：跟单专员原有 408「加工退货」→ 补齐 419 目录 + 420/421 叶子
                //（422 加工返回单已下线；跟单专员本就没有 411「物料退货」，故不授 423/424）
                419L, 420L, 421L,
                101L, 105L, 107L, 108L, 502L, 504L, 602L, 702L, 705L, 707L));
        // 财务：财务管理（2 基础数据 = 101 产品管理的父目录）
        // 注：经营分析自 2026-09-15 起**仅管理者可见**，故不再授予 finance
        assignRoleMenus("finance", Arrays.asList(
                1L, 2L, 8L, 801L, 802L, 803L, 804L, 805L, 806L, 807L, 809L, 810L, 811L, 101L));

        // 存量库幂等补授：上面的 assignRoleMenus **只在角色「尚无任何菜单」时才写入**，
        // 因此新增菜单不会自动补进已有角色 → 这里单独把 108「模版管理」补授给
        // admin / 跟单专员 / 研发工程师（uk_role_menu 唯一键 + INSERT IGNORE，重复启动无副作用）
        try {
            int granted = jdbcTemplate.update(
                    "INSERT IGNORE INTO sys_role_menu (role_id, menu_id) " +
                    "SELECT r.id, 108 FROM sys_role r WHERE r.role_code IN ('admin','merchandiser','dev_engineer')");
            if (granted > 0) log.info("已补授 108「模版管理」菜单给 {} 个角色", granted);
        } catch (Exception e) {
            log.warn("补授模版管理菜单异常: {}", e.getMessage());
        }

        // 存量库幂等补授：新目录 11「物料仓库」**必须**授给 admin / 跟单专员 ——
        // 菜单树 buildTree 以「已授权菜单」为输入，父目录未授权会把其 5 个子菜单整组丢弃
        // （同 108 的做法：uk_role_menu 唯一键 + INSERT IGNORE，重复启动无副作用）
        try {
            int granted = jdbcTemplate.update(
                    "INSERT IGNORE INTO sys_role_menu (role_id, menu_id) " +
                    "SELECT r.id, m.id FROM sys_role r JOIN sys_menu m ON m.id IN (11, 414) " +
                    "WHERE r.role_code IN ('admin','merchandiser')");
            if (granted > 0) log.info("已补授 11「物料仓库」目录 / 414「物料库存盘点」给 {} 个角色", granted);
        } catch (Exception e) {
            log.warn("补授物料仓库目录异常: {}", e.getMessage());
        }

        // 存量库幂等补授：把「物料仓库」整组（11 目录 + 5 个子菜单）补授给**仓管员**
        // （2026-09-16 用户要求；uk_role_menu 唯一键 + INSERT IGNORE → 幂等）
        try {
            int granted = jdbcTemplate.update(
                    "INSERT IGNORE INTO sys_role_menu (role_id, menu_id) " +
                    "SELECT r.id, m.id FROM sys_role r JOIN sys_menu m ON m.id IN (11, 404, 406, 407, 410, 413) " +
                    "WHERE r.role_code = 'warehouse'");
            if (granted > 0) log.info("已补授「物料仓库」整组给仓管员，共 {} 条", granted);
        } catch (Exception e) {
            log.warn("补授仓管员物料仓库权限异常: {}", e.getMessage());
        }

        // 存量库幂等补授（2026-09-24）：418「物料移仓」是新增菜单 —— assignRoleMenus 只在角色
        // 「尚无任何菜单」时才写入 ⇒ 存量库必须单独补授，否则已有角色看不到它。
        // 授权范围与 406「物料收发单」原本的三个角色保持一致：admin / 仓管员 / 跟单专员。
        try {
            int granted = jdbcTemplate.update(
                    "INSERT IGNORE INTO sys_role_menu (role_id, menu_id) " +
                    "SELECT r.id, 418 FROM sys_role r WHERE r.role_code IN ('admin','warehouse','merchandiser')");
            if (granted > 0) log.info("已补授 418「物料移仓」菜单给 {} 个角色", granted);
        } catch (Exception e) {
            log.warn("补授物料移仓菜单异常: {}", e.getMessage());
        }

        // 存量库幂等补授（2026-09-27）：加工退货/物料退货改三级菜单 —— 新增 419/420/421/422（加工侧）
        // 与 423/424（物料侧）。assignRoleMenus 只在角色「尚无任何菜单」时写入 ⇒ 存量库必须单独补授，
        // 否则已有角色只能看到目录、看不到叶子（buildTree 以已授权菜单为输入）。
        // 授权范围**从原叶子继承**（最稳）：凡有 408 的角色 → 补 419/420/421；凡有 411 的角色 → 补 423/424/425。
        try {
            int granted = 0;
            granted += jdbcTemplate.update(
                    "INSERT IGNORE INTO sys_role_menu (role_id, menu_id) " +
                    "SELECT rm.role_id, m.id FROM sys_role_menu rm JOIN sys_menu m ON m.id IN (419, 420, 421) " +
                    "WHERE rm.menu_id = 408");
            granted += jdbcTemplate.update(
                    "INSERT IGNORE INTO sys_role_menu (role_id, menu_id) " +
                    "SELECT rm.role_id, m.id FROM sys_role_menu rm JOIN sys_menu m ON m.id IN (423, 424, 425) " +
                    "WHERE rm.menu_id = 411");
            if (granted > 0) log.info("已按旧叶子继承补授三级菜单 {} 条（419~421 随 408 / 423+424+425 随 411）", granted);
        } catch (Exception e) {
            log.warn("补授三级退货菜单异常: {}", e.getMessage());
        }

        // 存量库幂等补授（2026-09-21）：416「物料库存情况」是新增菜单 —— assignRoleMenus 只在角色
        // 「尚无任何菜单」时才写入 ⇒ 存量库必须单独补授，否则除 admin 外的角色（含仓管员）看不到它、
        // 前端白名单也没有该路由（直输 URL 吃 403）。查询类页面 ⇒ 给 物料仓管员 / 跟单专员 / admin。
        try {
            int granted = jdbcTemplate.update(
                    "INSERT IGNORE INTO sys_role_menu (role_id, menu_id) " +
                    "SELECT r.id, 416 FROM sys_role r WHERE r.role_code IN ('admin','merchandiser','warehouse')");
            if (granted > 0) log.info("已补授 416「物料库存情况」给 {} 个角色", granted);
        } catch (Exception e) {
            log.warn("补授物料库存情况菜单异常: {}", e.getMessage());
        }

        // 存量库幂等补授（2026-09-22）：417「物料库存流水」同为新增菜单，使用人群与 416 完全一致
        // （查询类页面）⇒ 给 物料仓管员 / 跟单专员 / admin 补授（INSERT IGNORE，重复启动零写入）。
        try {
            int granted = jdbcTemplate.update(
                    "INSERT IGNORE INTO sys_role_menu (role_id, menu_id) " +
                    "SELECT r.id, 417 FROM sys_role r WHERE r.role_code IN ('admin','merchandiser','warehouse')");
            if (granted > 0) log.info("已补授 417「物料库存流水」给 {} 个角色", granted);
        } catch (Exception e) {
            log.warn("补授物料库存流水菜单异常: {}", e.getMessage());
        }

        // 存量库幂等补授：412「成品收货」/ 415「物料收货」是 2026-09-16 由详情页签移出成菜单的新行，
        // assignRoleMenus 只在角色「尚无任何菜单」时才写入 → 存量库必须单独补授（admin / 跟单专员）
        try {
            int granted = jdbcTemplate.update(
                    "INSERT IGNORE INTO sys_role_menu (role_id, menu_id) " +
                    "SELECT r.id, m.id FROM sys_role r JOIN sys_menu m ON m.id IN (412, 415) " +
                    "WHERE r.role_code IN ('admin','merchandiser')");
            if (granted > 0) log.info("已补授 412「成品收货」/ 415「物料收货」给 {} 个角色", granted);
        } catch (Exception e) {
            log.warn("补授收货菜单异常: {}", e.getMessage());
        }

        // 存量库幂等补授（2026-09-18）：销售业务侧的 602「客户管理」重复入口已下线（与基础数据 105 同页面），
        // 而 sales / merchandiser 原先**只授了 602**；不补授 105 会让这两个角色彻底失去客户管理
        // （菜单不出现 + 前端白名单无 /inventory/customer → 直输 URL 吃 403）。
        // 同 108/11 做法：INSERT IGNORE + uk_role_menu 唯一键，重复启动无副作用。
        try {
            int granted = jdbcTemplate.update(
                    "INSERT IGNORE INTO sys_role_menu (role_id, menu_id) " +
                    "SELECT r.id, 105 FROM sys_role r WHERE r.role_code IN ('admin','sales','merchandiser')");
            if (granted > 0) log.info("已补授 105「客户管理」（基础数据）给 {} 个角色", granted);
        } catch (Exception e) {
            log.warn("补授客户管理菜单异常: {}", e.getMessage());
        }

        // 存量库幂等补授（2026-09-18）：进货业务侧的 503「供货商管理」重复入口已下线（与基础数据 107 同页面）；
        // merchandiser 历史上只有 409（9-17 已隐藏的委外侧重复入口）而没有 107 ⇒ 不补授 107 会让跟单专员
        // 看不到供货商主数据（菜单不出现 + 白名单无 /outsource/supplier/manage → 直输 URL 吃 403）。
        try {
            int granted = jdbcTemplate.update(
                    "INSERT IGNORE INTO sys_role_menu (role_id, menu_id) " +
                    "SELECT r.id, 107 FROM sys_role r WHERE r.role_code IN ('admin','merchandiser')");
            if (granted > 0) log.info("已补授 107「供货商管理」（基础数据）给 {} 个角色", granted);
        } catch (Exception e) {
            log.warn("补授供货商管理菜单异常: {}", e.getMessage());
        }

        // 存量库幂等补授（2026-09-18）：进货业务新增「504 采购换货单」（父目录 5 进货业务 + 菜单本体），
        // assignRoleMenus 只在角色"尚无任何菜单"时写入 ⇒ 存量库必须单独补授，否则老库看不到新菜单。
        try {
            int granted = jdbcTemplate.update(
                    "INSERT IGNORE INTO sys_role_menu (role_id, menu_id) " +
                    "SELECT r.id, 504 FROM sys_role r WHERE r.role_code IN ('admin','warehouse','merchandiser')");
            if (granted > 0) log.info("已补授 504「采购换货单」给 {} 个角色", granted);
        } catch (Exception e) {
            log.warn("补授采购换货单菜单异常: {}", e.getMessage());
        }

        // 存量库幂等补授（2026-09-18）：707「退货整理」由销售业务（parent 6）迁到成品库存（parent 7）。
        // 菜单树以「已授权菜单」为输入，**父目录未授权会把其子菜单整组丢弃** ⇒ 给持有 707 的角色补授目录 7
        // （admin 本就有 7，INSERT IGNORE 幂等；其它角色若有 707 也能自愈）。
        try {
            int granted = jdbcTemplate.update(
                    "INSERT IGNORE INTO sys_role_menu (role_id, menu_id) " +
                    "SELECT DISTINCT rm.role_id, 7 FROM sys_role_menu rm WHERE rm.menu_id = 707");
            if (granted > 0) log.info("已为持有 707「退货整理」的角色补授父目录 7「成品库存」，共 {} 条", granted);
        } catch (Exception e) {
            log.warn("补授成品库存目录异常: {}", e.getMessage());
        }

        // 存量库幂等补授（2026-09-18 用户要求）：707「退货整理」（成品库存）此前**只有 admin 持有**，
        // 补授给**仓管员 / 跟单专员** —— 它是成品分选入库的操作页，这两个角色日常在用。
        // （父目录 7 两者本就有，无需重复补授；INSERT IGNORE + uk_role_menu 保证重复启动无副作用）
        try {
            int granted = jdbcTemplate.update(
                    "INSERT IGNORE INTO sys_role_menu (role_id, menu_id) " +
                    "SELECT r.id, 707 FROM sys_role r WHERE r.role_code IN ('warehouse','merchandiser')");
            if (granted > 0) log.info("已补授 707「退货整理」给 {} 个角色（仓管员/跟单专员）", granted);
        } catch (Exception e) {
            log.warn("补授退货整理菜单异常: {}", e.getMessage());
        }

        // 存量库幂等补授（2026-09-22）：702「成品仓库管理」**由成品库存（parent 7）迁入基础数据（parent 2）**。
        // 与 707 迁组同坑：菜单树以「已授权菜单」为输入，**父目录未授权会把其子菜单整组丢弃** ⇒
        // 给持有 702 的角色补授目录 2（当前 admin/warehouse/merchandiser 本就有 2，这里兜住自定义角色）。
        // （parent_id 本身由上面的种子 upsert 覆盖：ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id)）
        try {
            int granted = jdbcTemplate.update(
                    "INSERT IGNORE INTO sys_role_menu (role_id, menu_id) " +
                    "SELECT DISTINCT rm.role_id, 2 FROM sys_role_menu rm WHERE rm.menu_id = 702");
            if (granted > 0) log.info("已为持有 702「成品仓库管理」的角色补授父目录 2「基础数据」，共 {} 条", granted);
        } catch (Exception e) {
            log.warn("补授基础数据目录异常: {}", e.getMessage());
        }

        // 存量库幂等补授（2026-09-22）：404「委外仓库」/ 410「自有物料仓」由物料仓库（parent 11）迁入基础数据（parent 2）。
        // 同上：父目录未授权会把子菜单整组丢弃 ⇒ 给持有 404/410 的角色补授目录 2
        // （当前 admin/warehouse/merchandiser 本就有 2，这里兜住自定义角色；INSERT IGNORE 幂等）。
        try {
            int granted = jdbcTemplate.update(
                    "INSERT IGNORE INTO sys_role_menu (role_id, menu_id) " +
                    "SELECT DISTINCT rm.role_id, 2 FROM sys_role_menu rm WHERE rm.menu_id IN (404, 410)");
            if (granted > 0) log.info("已为持有 404/410 的角色补授父目录 2「基础数据」，共 {} 条", granted);
        } catch (Exception e) {
            log.warn("补授基础数据目录（404/410）异常: {}", e.getMessage());
        }
    }
















    /** 为指定角色授权菜单（仅当角色尚无菜单权限时执行） */
    private void assignRoleMenus(String roleCode, List<Long> menuIds) {
        // S-9②（2026-09-30 批 C）：同 role_code 多行（平台模板 + 各公司副本）⇒ 取"公司 ID 升序第一行"
        // = **平台模板**优先；原 `selectOne` 在迁移后会抛 TooManyResultsException 并**导致启动失败**
        // （实测：assignRoleMenus 第 1539 行，found: 3 —— admin 模板 + 公司 1/2 副本）。
        Role role = roleMapper.selectList(new LambdaQueryWrapper<Role>()
                        .eq(Role::getRoleCode, roleCode)
                        .orderByAsc(Role::getCompanyId))
                .stream().findFirst().orElse(null);
        if (role == null) return;
        List<Long> existingMenuIds = roleService.getMenuIdsByRoleId(role.getId());
        if (existingMenuIds == null || existingMenuIds.isEmpty()) {
            roleService.saveRoleMenus(role.getId(), menuIds);
            log.info("初始化 {} 菜单权限完成", roleCode);
        }
    }

    /**
     * F8-22（2026-09-30 设置模块批 E 发现 / 本轮修复）：**声明式「角色→菜单」计划 + 启动差集重放**。
     *
     * <p>背景：新增菜单原先只在"角色当前菜单为空"时才会补授（{@code assignRoleMenus}），存量角色靠
     * {@code initRoleMenus()} 里**17 段手写 {@code INSERT IGNORE}** 逐次救火 ⇒ 加菜单忘补授就"上线了没人能进"。</p>
     *
     * <p>做法：表 {@code sys_role_menu_plan(role_code, menu_id)} 作为**唯一声明处** ——
     * 首次启动时用**当前已正确的授权**灌一次快照（只在表空时执行，幂等），此后每次启动按差集重放：
     * <pre>INSERT IGNORE INTO sys_role_menu SELECT r.id, p.menu_id FROM plan p JOIN sys_role r ON
     * r.role_code = p.role_code WHERE IFNULL(r.customized_menu,0)=0 AND 菜单存在</pre>
     * ⇒ 新增菜单、以及**新公司克隆出来的角色**都会自动补齐。</p>
     *
     * <p>与用户改动的边界（口径与 F8-21 一致）：{@code sys_role.customized_menu=1} 表示该角色的菜单被用户
     * 在界面上手工调过（{@code RoleServiceImpl.saveRoleMenus} 打标）⇒ 重放**跳过该角色**，
     * 绝不把用户刚收掉的菜单再加回来。</p>
     */
    private void initRoleMenuPlan() {
        try {
            // P1（2026-09-30 未上线清理）：sys_role_menu_plan 表与 sys_role.customized_menu 列**均已由
            // db/migration/V1__base.sql 声明**，启动期不再做任何 DDL —— 缺表缺列由 assertSchemaReady() 在初始化最前面直接报错。
            Integer cnt = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_role_menu_plan", Integer.class);
            if (cnt != null && cnt == 0) {
                int snap = jdbcTemplate.update("INSERT IGNORE INTO sys_role_menu_plan (role_code, menu_id) "
                        + "SELECT r.role_code, rm.menu_id FROM sys_role_menu rm JOIN sys_role r ON r.id = rm.role_id");
                log.info("[F8-22] 角色→菜单计划首次快照 {} 行（以当前授权为准，此后以本表为唯一声明处）", snap);
            }
            int added = jdbcTemplate.update("INSERT IGNORE INTO sys_role_menu (role_id, menu_id) "
                    + "SELECT r.id, p.menu_id FROM sys_role_menu_plan p JOIN sys_role r ON r.role_code = p.role_code "
                    + "WHERE IFNULL(r.customized_menu, 0) = 0 AND EXISTS (SELECT 1 FROM sys_menu m WHERE m.id = p.menu_id)");
            if (added > 0) {
                log.info("[F8-22] 按计划补齐角色菜单 {} 条（新菜单/新公司角色自动获得；用户调过的角色已跳过）", added);
            }
        } catch (Exception e) {
            log.warn("[F8-22] 角色→菜单计划重放失败（下次启动重试）：{}", e.getMessage());
        }
    }

    /**
     * 启动期**兜底**：为每个公司补齐本公司角色。
     *
     * <p>克隆逻辑已收口到 {@link CompanyRoleProvisioner}，与「超管新建公司」
     * （{@code CompanyServiceImpl.create}）**共用同一处口径**，避免两边各写一份导致漂移。
     * 本方法负责覆盖两类公司：① **默认公司**（由 {@link #initCompany()} 建立，从未走过新建流程
     * ⇒ 不曾有自有角色）；② 历史存量公司。</p>
     *
     * <p>⚠️ 调用点必须在 {@link #initRoleMenus()} **之前** —— 否则新克隆出来的角色拿不到标准菜单授权
     * （实测顺序颠倒时 dev_engineer 只 1 个菜单、finance 0 个，角色等于不可用）。</p>
     */
    private void ensureCompanyRoles() {
        companyRoleProvisioner.provisionAll();
    }

    /**
     * F8-23（2026-09-30 设置模块批 E 修复）：**按公司补齐默认业务种子**（幂等、只增不改）。
     *
     * <p>原先 {@code initMaterialTypes} / {@code initPhaseTemplates} / {@code initContractTemplate}
     * 一律硬编码 {@code company_id = 1L} ⇒ 第 2 家及以后的公司**没有任何阶段模板 / 合同模板**
     * （实测：{@code dev_phase_template} 27 行、{@code outsource_contract_template} 2 行全为 company 1；
     * 而 {@code sys_company} 有 2 家）⇒ 新公司在项目阶段/合同页看不到默认数据，只能手工建。</p>
     *
     * <p>做法：以**公司 1 的现网数据为模板**，为其余公司按"名称/类型"判缺后补齐
     * （{@code NOT EXISTS} + 派生表快照，同一语句里读写的 MySQL 安全写法）。</p>
     */
    private void backfillCompanyDefaults() {
        String[] sqls = {
                "INSERT INTO material_type (type_name, sort_order, status, is_default, company_id) "
                        + "SELECT m.type_name, m.sort_order, m.status, m.is_default, c.id FROM material_type m "
                        + "JOIN sys_company c ON c.id <> 1 WHERE m.company_id = 1 AND NOT EXISTS ("
                        + "SELECT 1 FROM (SELECT * FROM material_type) x WHERE x.company_id = c.id AND x.type_name = m.type_name)",
                "INSERT INTO dev_phase_template (name, spec_type, default_days, sort_order, product_status_sync, remark, company_id) "
                        + "SELECT t.name, t.spec_type, t.default_days, t.sort_order, t.product_status_sync, t.remark, c.id "
                        + "FROM dev_phase_template t JOIN sys_company c ON c.id <> 1 WHERE t.company_id = 1 AND NOT EXISTS ("
                        + "SELECT 1 FROM (SELECT * FROM dev_phase_template) x WHERE x.company_id = c.id "
                        + "AND x.name = t.name AND IFNULL(x.spec_type, '') = IFNULL(t.spec_type, ''))",
                "INSERT INTO outsource_contract_template (template_name, content, template_type, status, is_default, company_id, create_time, update_time) "
                        + "SELECT t.template_name, t.content, t.template_type, t.status, t.is_default, c.id, NOW(), NOW() "
                        + "FROM outsource_contract_template t JOIN sys_company c ON c.id <> 1 WHERE t.company_id = 1 AND NOT EXISTS ("
                        + "SELECT 1 FROM (SELECT * FROM outsource_contract_template) x "
                        + "WHERE x.company_id = c.id AND x.template_type = t.template_type)"
        };
        int total = 0;
        for (String sql : sqls) {
            try {
                total += jdbcTemplate.update(sql);
            } catch (Exception e) {
                log.warn("[F8-23] 按公司补齐默认数据失败（跳过该表，下次启动重试）：{}", e.getMessage());
            }
        }
        if (total > 0) {
            log.info("[F8-23] 已为其它公司补齐默认数据 {} 行（物料类型 / 阶段模板 / 合同模板，幂等只增）", total);
        }
    }

    /** 初始化默认物料类型（玻璃/驱动IC/触摸IC/码片IC/排线/盖板/背贴/钢板/COP） */
    private void initMaterialTypes() {
        // 补齐默认物料类型：表空时全量初始化；已有部分时仅补缺失的默认类型（幂等）
        List<MaterialType> existing = materialTypeMapper.selectList(
                new LambdaQueryWrapper<MaterialType>().eq(MaterialType::getCompanyId, 1L));
        java.util.Set<String> existingNames = new java.util.HashSet<>();
        int maxSort = 0;
        for (MaterialType b : existing) {
            if (b.getTypeName() != null) existingNames.add(b.getTypeName());
            if (b.getSortOrder() != null && b.getSortOrder() > maxSort) maxSort = b.getSortOrder();
        }
        String[] defaultTypes = DefaultMaterialTypes.TYPES;
        int nextSort = maxSort;
        int added = 0;
        for (String name : defaultTypes) {
            if (existingNames.contains(name)) continue;
            MaterialType bt = new MaterialType();
            bt.setTypeName(name);
            bt.setSortOrder(++nextSort);
            bt.setStatus(1);
            bt.setIsDefault(1);
            bt.setCompanyId(1L);
            materialTypeMapper.insert(bt);
            added++;
        }
        if (added > 0) log.info("补齐默认物料类型 {} 条", added);
    }

    /**
     * 初始化研发阶段模板 —— 2026-09-21（用户需求）：**原配 / 改配各一套**。
     * <p>默认数据统一取自 {@link DefaultPhaseTemplates}：原先本方法与 {@code ClearController} 各硬编码
     * 了一份 14 条（备注文本已经漂移），且后者**漏写 product_status_sync** ⇒ 用户点一次"清空数据"后
     * 小批量/结项就不再触发产品状态同步（现网 14 条全 0 的成因）。现改为共用一份数据。</p>
     */
    private void initPhaseTemplates() {
        Long count = phaseTemplateMapper.selectCount(null);
        if (count != null && count > 0) {
            log.info("阶段模板数据已存在，跳过初始化");
            return;
        }
        int n = 0;
        n += insertPhaseTemplates(DefaultPhaseTemplates.MODIFIED, DefaultPhaseTemplates.SPEC_MODIFIED);
        n += insertPhaseTemplates(DefaultPhaseTemplates.MATCHED, DefaultPhaseTemplates.SPEC_MATCHED);
        log.info("初始化阶段模板数据完成（原配/改配共 {} 条）", n);
    }

    /** 按规格批量插入某套默认阶段，返回插入条数 */
    private int insertPhaseTemplates(DefaultPhaseTemplates.Row[] rows, String specType) {
        for (DefaultPhaseTemplates.Row r : rows) {
            PhaseTemplate t = new PhaseTemplate();
            t.setName(r.name());
            t.setSpecType(specType);
            t.setDefaultDays(r.defaultDays());
            t.setSortOrder(r.sortOrder());
            t.setRemark(r.remark());
            t.setProductStatusSync(r.productStatusSync());
            t.setCompanyId(1L);
            phaseTemplateMapper.insert(t);
        }
        return rows.length;
    }
}
