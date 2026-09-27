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
 * 数据初始化器：启动时自动初始化系统基础数据（角色、菜单、用户、物料类型、阶段模板、合同模板）。
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
    private final MaterialTypeMapper materialTypeMapper;
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
        migrateDashboardTabs();
        migrateUserMenuMode();
        initDocOperatorColumns();
        migrateMaterialOrderFinisher();
        migratePurchaseExchangeCharge();
        migratePurchaseChargePerProduct();
        migrateSaleItemCharge();
        migrateReturnSortSorter();
        migrateReturnBackSource();
        migrateMaterialMoveQuality();
        migrateMaterialRepairOnsiteLeg();
        migrateFinanceExpenseSource();
        initSuperAdmin();
        initMaterialTypes();
        initPhaseTemplates();
        initContractTemplates();
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
            // 物料仓库（2026-09-16 用户要求新增）：自「委外加工」迁入 5 个物料收发/仓储/报损子菜单。
            // 排在「成品库存」之前，使两个仓库模块相邻（成品库存 8→9、财务管理 9→10、设置 10→11）
            {11L, 0L, "物料仓库", "catalog", "", "", "Box", 8},
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
            // 委外加工子菜单顺序（2026-09-17 用户定稿）：加工订单 → 成品收货 → 加工退货 → 物料订单 → 物料收货 → 物料退货。
            // sort_order 即左侧栏显示顺序（MenuMapper.selectAllEnabled 按 sort_order 排序）；下方书写顺序与实际显示顺序一致，便于维护。
            {401L, 4L, "加工订单", "menu", "/outsource/order", "OutsourceOrder", "Document", 1},
            // 成品收货（2026-09-16 用户要求）：原「加工订单详情 → 交货管理」页签**移出**独立成菜单页 ——
            // 页面只列正在加工（PRODUCING）的加工单，点「交货」进详细页并自动弹出新增交货弹窗。
            // id 412 复用 2026-09-16 下线的「交货信息」总览页旧行（**必须同时从下方 visible=0 名单移除**）
            {412L, 4L, "成品收货", "menu", "/outsource/order/delivery", "OutsourceOrderDelivery", "Van", 2},
            // 加工退货（2026-09-17 起排在物料类之前）。
            // 2026-09-27 用户口径：二级改**目录** 419，把原「408 一个页面 3 页签」拆成 3 个三级叶子：
            //  408 关联退货(GTH-) / 420 无单退货(GTW-) / 421 成品维修退货(REPAIR)
            //  ⚠️ 叶子 perms 必须"自带其 API 需要的码"（目录行的 perms 会被 initMenuPerms 强制清空）：
            //    加工退货台账接口在 /api/outsource/order-delivery 前缀下（408/420），
            //    维修退货单在 /api/outsource/return-order 前缀下（421）—— 见 ApiPermGuard.RULES。
            //  ⚠️ 408/411 是**改父级**（4 → 419/423）而非新增行：syncMenus 的 upsert 会更新 parent_id，
            //    存量库的角色授权因此不丢（新叶子用下方"从旧叶子继承"的幂等补授覆盖）。
            {419L, 4L, "加工退货", "catalog", "", "", "CircleClose", 3},
            {408L, 419L, "关联退货", "menu", "/outsource/return-order", "OutsourceReturnOrder", "Document", 1},
            {420L, 419L, "无单退货", "menu", "/outsource/return-order/unlinked", "OutsourceReturnOrderUnlinked", "Files", 2},
            // 2026-09-27（由 ui-e2e-1-nav 的"标签栏不得同名"不变量抓出）：加工侧与物料侧都有维修退货 ⇒
            // 两处同名会让顶部**标签栏出现两个「维修退货」**（用户无从区分）⇒ 各自带对象前缀去重。
            {421L, 419L, "成品维修退货", "menu", "/outsource/return-order/repair", "OutsourceReturnOrderRepair", "Tools", 3},
            // 422「加工返回单」已于 2026-09-27 按用户要求下线（「多余了，改在详情里登记返回」）：
            //   与 104/302/303/405/406/409/503/602/701 同范式 —— 不再 upsert（upsert 会把 visible 刷回 1），
            //   改为在下方统一置 visible=0，**保留行与角色授权**便于回滚；
            //   返回登记改在无单退货记录详情页（`/api/outsource/order-delivery/{id}/return-back`，登记即生效）。
            {402L, 4L, "物料订单", "menu", "/outsource/material-order", "OutsourceMaterialOrder", "ShoppingCart", 4},
            // 物料收货（2026-09-16 用户要求）：原「物料订单详情 → 交货管理」页签**移出**独立成菜单页 ——
            // 页面只列收货中（RECEIVING）的物料订单，点「收料」进详细页并自动弹出收货弹窗。
            // 注意：**交货业务本身未改**（OrderDeliveryController / OutsourceOrderDeliveryService /
            // MaterialOrderController 的收料、退不良、库存、应付、BOM还料逻辑均未动）
            {415L, 4L, "物料收货", "menu", "/outsource/material-order/delivery", "OutsourceMaterialOrderDelivery", "Van", 5},
            // 物料退货（2026-09-27 用户口径「物料侧也按关联物料订单/未关联分叶子」）：二级改目录 423，
            // 把原「411 一个页面 2 页签」拆成 **3 个**三级叶子：
            //  411 关联退料(MRH-) / 425 无单退料(MRW-) / 424 物料维修退货(REPAIR)；
            //  三者 API 都在 /api/outsource/material-return 前缀下 ⇒ perms 同码；
            //  关联/无单两个叶子同 returnType=REFUND，靠 linked 参数区分（与加工侧 linked 同口径）。
            {423L, 4L, "物料退货", "catalog", "", "", "Refrigerator", 6},
            {411L, 423L, "关联退料", "menu", "/outsource/material-return", "OutsourceMaterialReturn", "Document", 1},
            {425L, 423L, "无单退料", "menu", "/outsource/material-return/unlinked", "OutsourceMaterialReturnUnlinked", "Files", 2},
            {424L, 423L, "物料维修退货", "menu", "/outsource/material-return/repair", "OutsourceMaterialReturnRepair", "Tools", 3},
            // 409「供应商管理」已于 2026-09-17 按用户要求下线：它是委外加工侧的**重复入口**（与基础数据 106
            // 「供应商管理」同指 /supplier/manage，页面完全相同），基础数据里 106/107 两份都保留。
            // 与 104/405/302/303 同范式：下方统一置 visible=0（保留行与角色授权，便于回滚）。
            // 403「物料信息」已于 2026-09-16 迁入「基础数据」（紧跟物料类型管理）→ 此处不再 upsert。
            // 406/407/404/410/413「物料收发单 / 物料其他出入库 / 委外仓库 / 自有物料仓 / 物料报损」
            // 已于 2026-09-16 按用户要求迁入新目录「物料仓库」(11)——**路由路径全部不变**，故不涉白名单/重定向
            // 405「加工合同模板」已并入 108「模版管理」（基础数据，2026-09-15），不再在此 upsert
            // 物料仓库（11，2026-09-16 新增；同日按用户要求重排为「仓库 → 盘点 → 单据」；
            // 2026-09-22 用户要求把「委外仓库 / 自有物料仓」迁入「基础数据」⇒ 本目录只剩"查询 + 作业单据"5 项）：
            // 物料库存详情 → 物料库存盘点 → 物料报损 → 物料其他出入库 → 物料收发单
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
            // 物料报损：与成品报损独立成表（主体为 outsource_material，物料库存不区分品质，固定按良品扣减）
            {413L, 11L, "物料报损", "menu", "/outsource/stock-loss", "OutsourceStockLoss", "DeleteFilled", 5},
            {407L, 11L, "物料其他出入库", "menu", "/outsource/other-io", "OutsourceOtherIo", "Files", 6},
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
            {908L, 9L, "清空数据", "menu", "/system/clear-data", "SystemClearData", "Delete", 8},
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

        // 下线历史菜单：104「阶段模板管理」（基础数据）/ 405「加工合同模板」（委外加工）
        // —— 两者功能已合并进 108「模版管理」（页内 TAB 区分）。这里置 visible=0 而**不删行**：便于回滚，
        // 且 getMenuTreeByRoleIds 只回 status=1 AND visible=1 → 侧栏消失、前端菜单白名单也不再含旧路径
        // （旧路径在前端已改为重定向到 /template 对应页签，直接输 URL 不会吃 403）。
        // 409「供应商管理」2026-09-17 按用户要求一并下线（委外加工侧重复入口，基础数据 106 已有同页面菜单）。
        try {
            // 注意：412 不在此列表 —— 2026-09-16 该 id 已被复用为「成品收货」菜单，
            // 若仍置 visible=0，会在上面的 upsert 之后把新菜单立刻隐藏（upsert 在前、置 0 在后）
            int hidden = jdbcTemplate.update("UPDATE sys_menu SET visible = 0 WHERE id IN (104, 405, 302, 303, 409, 602, 503, 701, 406, 422) AND visible = 1");
            if (hidden > 0) log.info("已下线历史菜单 {} 条（104 阶段模板管理 / 405 加工合同模板 / 302 BOM管理 / 303 图纸文档 / 409 委外加工-供应商管理 / 602 销售业务-客户管理 / 503 进货业务-供货商管理 / 701 成品库存查询 / 406 物料收发单 / 422 加工返回单）", hidden);
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

    /**
     * F3-3（2026-09-18 接口级权限专项）：给**页面菜单**写接口权限码 {@code sys_menu.perms}（幂等）。
     *
     * <p>口径：权限码 = {@code 模块:资源}，与页面菜单一一对应（仅 {@code menu_type='menu'} 的行有值，
     * 目录(catalog) 恒为 NULL）。用户的有效权限 = 其**可见菜单**的权限码集合，由
     * {@code StpInterfaceImpl.getPermissionList} 提供给 {@code @SaCheckPermission} ⇒
     * 与侧栏同源，保证"看得见的页面，接口一定调得通"。</p>
     *
     * <p>注意：**已下线菜单**（104/302/303/405/409/503/602/701，visible=0）不在此表 —— 权限码只授给
     * 在用页面；其页面若仍被复用（如 301 研发立项复用 BomController），注解取**复用页**的码。</p>
     */
    /**
     * 全站单据「制单人 / 审核人」列（2026-09-23 用户口径：所有单据生成的详情都要显示这两项）。
     *
     * <p>做法：所有单据主表幂等补列 —— {@code create_by}/{@code create_by_name}（制单人，由
     * {@code MybatisPlusConfig} 的 MetaObjectHandler 自动填充，业务代码零改动）+ {@code auditor_id}/
     * {@code auditor_name}（审核人，审核时盖章；无审核流程的单据留空）。</p>
     *
     * <p>MySQL 8 没有 {@code ADD COLUMN IF NOT EXISTS} ⇒ 先查 {@code information_schema} 判断列是否存在，
     * 缺哪列补哪列 ⇒ 重复启动零写入。（启动阶段没有请求上下文 ⇒ companyId 为空 ⇒ 多租户插件不介入。）</p>
     */
    private void initDocOperatorColumns() {
        String[] docTables = {
                // 采购
                "purchase_order", "purchase_return", "purchase_exchange",
                // 销售
                "sale_order", "sale_return", "sale_exchange",
                // 成品库存
                "inventory_warehouse_move", "return_sort", "inventory_stock_take",
                "inventory_stock_loss", "inventory_other_io", "product_reclassify",
                // 物料仓库
                "outsource_delivery", "outsource_stock_loss", "outsource_other_io",
                // 委外
                "outsource_order", "outsource_material_order", "outsource_return_order",
                "outsource_material_return", "outsource_order_delivery",
                "outsource_return_order_repair", "outsource_material_return_repair",
                // 结单报表（2026-09-23 排查补漏）：结单 = 该单的"审核"动作（confirmClose）⇒ 也要有制单人/审核人
                "outsource_order_close_report",
                // 研发立项（2026-09-23 用户要求纳入）：属项目主数据、**无审核流程** ⇒ 只填「制单人」；
                // auditor_id/auditor_name 两列会一并建出但按口径始终留空（页面也不显示审核人）
                "dev_project",
                // 财务（台账 finance_receivable/payable 也记，便于追溯由哪张单触发）
                "finance_receipt", "finance_payment", "finance_bill", "finance_expense",
                "finance_invoice", "finance_payable_transfer",
                "finance_receivable", "finance_payable",
        };
        int added = 0;
        for (String t : docTables) {
            try {
                if (!columnExists(t, "create_by")) {
                    jdbcTemplate.execute("ALTER TABLE " + t
                            + " ADD COLUMN create_by BIGINT NULL COMMENT '制单人ID（MetaObjectHandler 自动填充）',"
                            + " ADD COLUMN create_by_name VARCHAR(50) NULL COMMENT '制单人姓名快照'");
                    added++;
                }
                if (!columnExists(t, "auditor_id")) {
                    jdbcTemplate.execute("ALTER TABLE " + t
                            + " ADD COLUMN auditor_id BIGINT NULL COMMENT '审核人ID（审核时盖章）',"
                            + " ADD COLUMN auditor_name VARCHAR(50) NULL COMMENT '审核人姓名快照'");
                    added++;
                }
            } catch (Exception e) {
                log.warn("补列失败 {}: {}", t, e.getMessage());
            }
        }
        if (added > 0) log.info("已为 {} 处单据表补「制单人/审核人」列", added);
    }

    /**
     * 物料订单补「结单人」（2026-09-27 用户口径「把结单人做了」）：结单是一次人工动作，要留痕"谁结的"。
     *
     * <p>与制单人/审核人同规格（ID + 姓名快照）：{@code finish()} 盖章、{@code reopen()} **清空**
     * （口径与成品侧 {@code CloseReportServiceImpl.reopenClose} 一致 —— 反结单清空结单人，避免
     * "已回收货中却还显示结单人"）。历史已结单的行保持 NULL，页面显示「—」。</p>
     */
    private void migrateMaterialOrderFinisher() {
        addColumnIfMissing("outsource_material_order",
                "finisher_id BIGINT NULL COMMENT '结单人ID（finish 时盖章，反结单清空）'");
        addColumnIfMissing("outsource_material_order",
                "finisher_name VARCHAR(50) NULL COMMENT '结单人姓名快照'");
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
                // 2026-09-27：408 改为三级「关联退货」——它的页面主体是**加工退货台账**
                // （/api/outsource/order-delivery/return-defect/*）⇒ perms 换成台账接口的码；
                // 原 outsource:return-order 归 421「维修退货」/422「加工返回单」使用（同码，职责更清楚）。
                {408L, "outsource:order-delivery"},
                {411L, "outsource:material-return"},
                {425L, "outsource:material-return"},
                {420L, "outsource:order-delivery"},
                {421L, "outsource:return-order"},
                // 422 已下线（visible=0）：不再写 perms —— 它原先的码与 421 相同，由 421 承担；
                // 存量库该行的 perms 会残留但**不可见即不生效**（有效权限 = 可见菜单的 perms 集合）。
                {424L, "outsource:material-return"},
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
                // 2026-09-27 三级菜单：419 加工退货目录 + 420/421 叶子（422 加工返回单已下线）；423 物料退货目录 + 424/425 叶子
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
            if (granted > 0) log.info("已按旧叶子继承补授三级菜单 {} 条（419~421 随 408 / 423~425 随 411）", granted);
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

    /**
     * 存量用户首页 TAB 补齐（2026-09-16）：新增「物料仓库」TAB 后，**已显式配置过首页 TAB 的用户**
     * （sys_user_dashboard_tab 有记录；无记录=全部可见，不受影响）默认看不到它 ——
     * 这 5 个页面原本挂在「委外加工」TAB 下，故给配了 outsource 的用户补上 materialWarehouse。
     * uk_user_tab(user_id, tab_key) 唯一键 + INSERT IGNORE → 幂等，重复启动无副作用。
     */
    private void migrateDashboardTabs() {
        try {
            int n = jdbcTemplate.update(
                    "INSERT IGNORE INTO sys_user_dashboard_tab (user_id, tab_key, company_id) " +
                    "SELECT user_id, 'materialWarehouse', company_id FROM sys_user_dashboard_tab WHERE tab_key = 'outsource'");
            if (n > 0) log.info("已为 {} 位已配置首页 TAB 的用户补上「物料仓库」TAB", n);
        } catch (Exception e) {
            log.warn("补齐首页 TAB 异常: {}", e.getMessage());
        }
    }

    /**
     * 存量库幂等迁移（2026-09-18）：sys_user 增加 menu_mode 列（页面权限模式 ROLE/CUSTOM）。
     * <p>新库由 schema.sql 直接建列；老库必须 ALTER —— MySQL 不支持 ADD COLUMN IF NOT EXISTS，
     * 故用 try/catch 忽略「列已存在」错误，保证重复启动无副作用。</p>
     */
    private void migrateUserMenuMode() {
        try {
            jdbcTemplate.execute("ALTER TABLE sys_user ADD COLUMN menu_mode VARCHAR(10) DEFAULT 'ROLE' "
                    + "COMMENT '页面权限模式: ROLE=跟随角色(默认) CUSTOM=以用户级菜单为准'");
            log.info("已为 sys_user 增加 menu_mode 列（用户级页面权限模式）");
        } catch (Exception e) {
            log.debug("menu_mode 列已存在，跳过：{}", e.getMessage());
        }
    }

    /**
     * 存量库幂等迁移（2026-09-21）：purchase_exchange 增加「是否付费」4 列。
     * <p>用户口径：采购换货单需要有「是否付费」，且方向是 <b>我们向供货商付费</b>
     * （charge_flag=1 ⇒ 审核额外生成一条正向应付，source_bill_type=PURCHASE_EXCHANGE_CHARGE）。
     * 新库由 schema.sql 直接建列；老库必须 ALTER —— MySQL 不支持 ADD COLUMN IF NOT EXISTS，
     * 故逐列 try/catch，忽略「列已存在」错误，保证重复启动无副作用。</p>
     */
    /**
     * 存量库幂等迁移（2026-09-24）：inventory_material_move_item 增加「品质分级」列。
     *
     * <p>用户口径：物料移仓明细要与成品移仓单一样带品质（A/B/C/DEFECT）。</p>
     *
     * <p><b>⚠️ 该字段只落单据、不参与库存</b>：物料库存（warehouse_stock 的物料维度）没有品质列，
     * 物料侧一贯"不区分品质、按良品扣减" ⇒ 审核写入 changeMaterialStock 时不带品质。
     * 本列用于"这单搬的是哪一档物料"的业务留痕与展示，不等于按品质分账。</p>
     *
     * <p>新库由 schema.sql 直接建列；老库必须 ALTER —— MySQL 不支持 ADD COLUMN IF NOT EXISTS，
     * 故走 {@link #addColumnIfMissing} 逐列 try/catch。</p>
     */
    private void migrateMaterialMoveQuality() {
        addColumnIfMissing("inventory_material_move_item",
                "quality_type VARCHAR(10) DEFAULT 'A' COMMENT '品质等级: A/B/C/DEFECT（单据留痕，不参与库存）'");
    }

    private void migratePurchaseExchangeCharge() {
        addColumnIfMissing("purchase_exchange",
                "charge_flag TINYINT DEFAULT 0 COMMENT '是否付费: 0否 1是（我们向供货商付费）'");
        addColumnIfMissing("purchase_exchange",
                "charge_type VARCHAR(30) DEFAULT NULL COMMENT '付费类型: SERVICE/DIFF/FULL/OTHER'");
        addColumnIfMissing("purchase_exchange",
                "charge_amount DECIMAL(18,2) DEFAULT 0 COMMENT '付费金额（我方付给供货商）'");
        addColumnIfMissing("purchase_exchange",
                "charge_reason VARCHAR(255) DEFAULT NULL COMMENT '付费说明'");
    }

    /**
     * 存量库幂等迁移（2026-09-21）：销售退货单 / 销售换货单的**逐产品收费**。
     * <p>用户口径：「销售退货单和销售换货单应该都有付费，而且付费需要精确到产品上」⇒ 原本挂在单据上的
     * charge_flag/charge_type/charge_amount 下沉到明细行，单据级改为 Σ(明细)（由服务层回写）。
     * 新库由 schema.sql 直接建列；老库逐列 ALTER（{@link #addColumnIfMissing}，重复启动无副作用）。</p>
     */
    private void migrateSaleItemCharge() {
        for (String t : new String[]{"sale_return_item", "sale_exchange_item"}) {
            addColumnIfMissing(t, "charge_flag TINYINT DEFAULT 0 COMMENT '是否收费: 0否 1是(逐产品)'");
            addColumnIfMissing(t, "charge_type VARCHAR(20) DEFAULT NULL COMMENT '收费类型: SERVICE/DIFF/FULL/OTHER'");
            addColumnIfMissing(t, "charge_amount DECIMAL(18,2) DEFAULT 0 COMMENT '该产品收费金额(向客户收取)'");
            addColumnIfMissing(t, "charge_reason VARCHAR(200) COMMENT '该产品收费说明'");
        }
        // 存量兜底：老数据把金额挂在**单据**上（当前库 0 行，生产可能有）⇒ 回填到该单**第一条明细**，
        // 保证「Σ(明细) = 单据金额」恒等、台账金额不变。已是逐产品的单据不会被匹配到 ⇒ 幂等。
        backfillFirstItemCharge("sale_return_item", "return_id", "sale_return");
        backfillFirstItemCharge("sale_exchange_item", "exchange_id", "sale_exchange");
    }

    /** 把"单据级收费"回填到第一条明细（幂等：该单已有逐产品收费时跳过） */
    private void backfillFirstItemCharge(String itemTable, String fk, String docTable) {
        try {
            List<Long> docIds = jdbcTemplate.queryForList(
                    "SELECT d.id FROM " + docTable + " d WHERE IFNULL(d.charge_flag,0) = 1 AND IFNULL(d.charge_amount,0) > 0"
                            + " AND NOT EXISTS (SELECT 1 FROM " + itemTable + " i WHERE i." + fk + " = d.id AND IFNULL(i.charge_amount,0) > 0)",
                    Long.class);
            int n = 0;
            for (Long did : docIds) {
                java.util.Map<String, Object> d = jdbcTemplate.queryForMap(
                        "SELECT charge_type, charge_amount, charge_reason FROM " + docTable + " WHERE id = " + did);
                Long itemId = jdbcTemplate.queryForObject(
                        "SELECT MIN(id) FROM " + itemTable + " WHERE " + fk + " = " + did, Long.class);
                if (itemId == null) continue;
                jdbcTemplate.update("UPDATE " + itemTable + " SET charge_flag = 1, charge_type = ?, charge_amount = ?, charge_reason = ? WHERE id = ?",
                        d.get("charge_type"), d.get("charge_amount"), d.get("charge_reason"), itemId);
                n++;
            }
            if (n > 0) log.info("已把 {} 张单据的历史收费回填到 {} 的第一条明细", n, itemTable);
        } catch (Exception e) {
            log.debug("{} 历史收费回填跳过：{}", itemTable, e.getMessage());
        }
    }

    /**
     * 存量库幂等迁移（2026-09-21 第二轮）：采购侧「逐产品付费」—— 付费金额/类型**下沉到明细行**。
     * <p>用户口径：采购退货单与采购换货单都要有「是否付费」，方向是 <b>我们付给供货商</b>（生成正向应付），
     * 且必须**精确到产品**。单据级 charge_* 改为派生值（金额 = Σ 明细，类型各明细一致才回填）。</p>
     * <p>本方法为 {@code purchase_return} / {@code purchase_return_item} / {@code purchase_exchange_item}
     * 三张表补列；{@code purchase_exchange} 的 4 列已由 {@link #migratePurchaseExchangeCharge()} 补过。
     * MySQL 不支持 ADD COLUMN IF NOT EXISTS ⇒ 逐列 try/catch（{@link #addColumnIfMissing}）。</p>
     */
    private void migratePurchaseChargePerProduct() {
        // 采购退货单主表（新）：4 列，与 purchase_exchange 同型
        addColumnIfMissing("purchase_return",
                "charge_flag TINYINT DEFAULT 0 COMMENT '是否付费: 0否 1是（我们向供货商付费；派生自明细）'");
        addColumnIfMissing("purchase_return",
                "charge_type VARCHAR(30) DEFAULT NULL COMMENT '付费类型: SERVICE/DIFF/FULL/OTHER（各明细一致才回填）'");
        addColumnIfMissing("purchase_return",
                "charge_amount DECIMAL(18,2) DEFAULT 0 COMMENT '付费金额 = Σ 明细行付费'");
        addColumnIfMissing("purchase_return",
                "charge_reason VARCHAR(255) DEFAULT NULL COMMENT '付费说明（整单共用一句话）'");
        // 采购退货单明细（新）：逐产品 4 列，镜像 sale_return_item
        addColumnIfMissing("purchase_return_item",
                "charge_flag TINYINT DEFAULT 0 COMMENT '是否付费: 0否 1是(逐产品)'");
        addColumnIfMissing("purchase_return_item",
                "charge_type VARCHAR(20) DEFAULT NULL COMMENT '付费类型: SERVICE/DIFF/FULL/OTHER'");
        addColumnIfMissing("purchase_return_item",
                "charge_amount DECIMAL(18,2) DEFAULT 0 COMMENT '该产品付费金额(我方付给供货商)'");
        addColumnIfMissing("purchase_return_item",
                "charge_reason VARCHAR(200) DEFAULT NULL COMMENT '该产品付费说明'");
        // 采购换货单明细（新）：逐产品 4 列（主表 4 列早前已有）
        addColumnIfMissing("purchase_exchange_item",
                "charge_flag TINYINT DEFAULT 0 COMMENT '是否付费: 0否 1是(逐产品)'");
        addColumnIfMissing("purchase_exchange_item",
                "charge_type VARCHAR(20) DEFAULT NULL COMMENT '付费类型: SERVICE/DIFF/FULL/OTHER'");
        addColumnIfMissing("purchase_exchange_item",
                "charge_amount DECIMAL(18,2) DEFAULT 0 COMMENT '该产品付费金额(我方付给供货商)'");
        addColumnIfMissing("purchase_exchange_item",
                "charge_reason VARCHAR(200) DEFAULT NULL COMMENT '该产品付费说明'");
    }

    /**
     * 退货整理「整理人」（2026-09-22 用户要求）：谁操作的就是谁整理的。
     * <p>两列都只由服务端按当前登录用户写入（新建/批量生成草稿/编辑刷新为最后操作人；审核时为空则补写），
     * 因此历史单为空属正常，详情显示「—」。</p>
     */
    /**
     * 加工返回单补「来源无单加工退货记录」列（2026-09-27）。
     * <p>用途：修好送回时把返回单绑定到具体那条无单退货单 ⇒ 退货台账才能显示「已返回/未返回」、
     * 才能按「待返回/已返回完」分页签，并在创建时按单防超返（原先只能按工厂+产品+规格总额校验）。
     * 新库由 schema.sql 直接建列；老库 ALTER（{@link #addColumnIfMissing}，重复启动无副作用）。</p>
     */
    private void migrateReturnBackSource() {
        addColumnIfMissing("outsource_return_back",
                "source_delivery_id BIGINT DEFAULT NULL COMMENT '来源无单加工退货记录ID(outsource_order_delivery.id)：已返回/未返回与防超返按它聚合（存量单为NULL）'");
        try {
            jdbcTemplate.execute("CREATE INDEX idx_source_delivery ON outsource_return_back (source_delivery_id)");
            log.info("已为 outsource_return_back 增加索引 idx_source_delivery");
        } catch (Exception e) {
            log.debug("idx_source_delivery 已存在，跳过：{}", e.getMessage());
        }
    }

    /**
     * 存量库幂等迁移（2026-09-27）：物料维修返回记录增加 onsite_leg 列。
     * <p>背景（在厂行对称性二修）：登记时若在厂行不存在（旧单）会**跳过**核销腿，撤销腿必须知道这一点，
     * 否则旧单撤销会凭空给在厂行 +qty。默认 1 = 历史上绝大多数记录确实核销过在厂行（与新库 schema.sql 一致）。</p>
     */
    private void migrateMaterialRepairOnsiteLeg() {
        addColumnIfMissing("outsource_material_return_repair",
                "onsite_leg TINYINT DEFAULT 1 COMMENT '登记时是否核销在厂行：1=是(撤销需恢复) 0=旧单跳过(撤销不恢复)'");
    }

    /**
     * 存量库幂等迁移（2026-09-27）：费用单增加**来源引用三列**（source_bill_type / source_id / source_bill_no）+ 索引。
     *
     * <p>背景：物料信息管理「新增物料 → 同时登记研发支出」需要回答"这笔费用是从哪个物料带出来的"，
     * 用于 ① **幂等**（同一物料不重复建研发支出）② **可追溯**。命名与 finance_receivable 的来源三列一致。
     * 新库由 schema.sql 直接建列；老库 ALTER（重复启动无副作用）；**历史费用单三列为 NULL**（视为手工登记）。</p>
     */
    private void migrateFinanceExpenseSource() {
        addColumnIfMissing("finance_expense",
                "source_bill_type VARCHAR(30) DEFAULT NULL COMMENT '来源类型(存code): RD_MATERIAL=物料研发支出'");
        addColumnIfMissing("finance_expense", "source_id BIGINT DEFAULT NULL COMMENT '来源对象ID(如 outsource_material.id)'");
        addColumnIfMissing("finance_expense", "source_bill_no VARCHAR(50) DEFAULT NULL COMMENT '来源单号'");
        try {
            jdbcTemplate.execute("CREATE INDEX idx_expense_source ON finance_expense (source_bill_type, source_id)");
            log.info("已为 finance_expense 增加索引 idx_expense_source");
        } catch (Exception e) {
            log.debug("idx_expense_source 已存在，跳过：{}", e.getMessage());
        }
    }

    private void migrateReturnSortSorter() {
        addColumnIfMissing("return_sort",
                "sort_user_id BIGINT DEFAULT NULL COMMENT '整理人用户ID（服务端按当前登录用户写入）'");
        addColumnIfMissing("return_sort",
                "sort_user_name VARCHAR(50) DEFAULT NULL COMMENT '整理人登录名（冗余，便于详情直接展示）'");
    }

    /** 幂等补列：列已存在时 MySQL 报错，捕获忽略即可（不依赖 MySQL 版本特性） */
    private void addColumnIfMissing(String table, String columnDdl) {
        try {
            jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + columnDdl);
            log.info("已为 {} 增加列：{}", table, columnDdl);
        } catch (Exception e) {
            log.debug("{}.{} 已存在，跳过：{}", table, columnDdl, e.getMessage());
        }
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
