package com.beichen.erp.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beichen.erp.common.DefaultMaterialTypes;
import com.beichen.erp.common.DefaultContractTemplate;
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
        initSuperAdmin();
        initMaterialTypes();
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
            // 物料仓库（2026-09-16 用户要求新增）：自「委外加工」迁入 5 个物料收发/仓储/报损子菜单。
            // 排在「成品库存」之前，使两个仓库模块相邻（成品库存 8→9、财务管理 9→10、设置 10→11）
            {11L, 0L, "物料仓库", "catalog", "", "", "Box", 8},
            {7L, 0L, "成品库存", "catalog", "", "", "Odometer", 9},
            {8L, 0L, "财务管理", "catalog", "", "", "Money", 10},
            {9L, 0L, "设置", "catalog", "", "", "Tools", 11},
            // 基础数据子菜单顺序（2026-09-15 用户定稿，2026-09-16 追加「物料信息管理」）：sort_order 即左侧栏显示顺序：
            // 客户管理 → 产品管理 → 品牌管理 → 供货商管理 → 供应商管理 → 物料类型管理 → 物料信息管理 → 模版管理
            {105L, 2L, "客户管理", "menu", "/inventory/customer", "InventoryCustomer", "UserFilled", 1},
            {101L, 2L, "产品管理", "menu", "/product", "ProductManage", "TakeawayBox", 2},
            {102L, 2L, "品牌管理", "menu", "/inventory/brand", "InventoryBrand", "CollectionTag", 3},
            {107L, 2L, "供货商管理", "menu", "/outsource/supplier/manage", "OutsourceSupplierManage", "Van", 4},
            {106L, 2L, "供应商管理", "menu", "/supplier/manage", "SupplierManage", "OfficeBuilding", 5},
            {103L, 2L, "物料类型管理", "menu", "/dev/material-type", "MaterialType", "Tickets", 6},
            // 物料信息管理（403）：2026-09-16 用户要求自「委外加工」迁入「基础数据」，紧跟「物料类型管理」；
            // 同日按用户要求菜单名由「物料信息」改为「物料信息管理」（与页面标题一致）
            // （物料主数据属基础数据；路由路径 /outsource/material-info 保持不变，故不涉及菜单白名单/重定向）
            {403L, 2L, "物料信息管理", "menu", "/outsource/material-info", "OutsourceMaterialInfo", "Switch", 7},
            // 模版管理：把原「阶段模板管理」（104，基础数据下）与「加工合同模板」（405，委外加工下）
            // 合并为一个页面，页内用 TAB 区分（2026-09-15 用户要求）。旧地址保留为重定向。
            {108L, 2L, "模版管理", "menu", "/template", "TemplateManage", "Timer", 8},
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
            // 加工退货（2026-09-17 起排在物料类之前：不良退货 / 维修退货）
            {408L, 4L, "加工退货", "menu", "/outsource/return-order", "OutsourceReturnOrder", "CircleClose", 3},
            {402L, 4L, "物料订单", "menu", "/outsource/material-order", "OutsourceMaterialOrder", "ShoppingCart", 4},
            // 物料收货（2026-09-16 用户要求）：原「物料订单详情 → 交货管理」页签**移出**独立成菜单页 ——
            // 页面只列收货中（RECEIVING）的物料订单，点「收料」进详细页并自动弹出收货弹窗。
            // 注意：**交货业务本身未改**（OrderDeliveryController / OutsourceOrderDeliveryService /
            // MaterialOrderController 的收料、退不良、库存、应付、BOM还料逻辑均未动）
            {415L, 4L, "物料收货", "menu", "/outsource/material-order/delivery", "OutsourceMaterialOrderDelivery", "Van", 5},
            {411L, 4L, "物料退货", "menu", "/outsource/material-return", "OutsourceMaterialReturn", "Refrigerator", 6},
            // 409「供应商管理」已于 2026-09-17 按用户要求下线：它是委外加工侧的**重复入口**（与基础数据 106
            // 「供应商管理」同指 /supplier/manage，页面完全相同），基础数据里 106/107 两份都保留。
            // 与 104/405/302/303 同范式：下方统一置 visible=0（保留行与角色授权，便于回滚）。
            // 403「物料信息」已于 2026-09-16 迁入「基础数据」（紧跟物料类型管理）→ 此处不再 upsert。
            // 406/407/404/410/413「物料收发单 / 物料其他出入库 / 委外仓库 / 自有物料仓 / 物料报损」
            // 已于 2026-09-16 按用户要求迁入新目录「物料仓库」(11)——**路由路径全部不变**，故不涉白名单/重定向
            // 405「加工合同模板」已并入 108「模版管理」（基础数据，2026-09-15），不再在此 upsert
            // 物料仓库（11，2026-09-16 新增；同日按用户要求重排为「仓库 → 盘点 → 单据」）：
            {404L, 11L, "委外仓库", "menu", "/outsource/warehouse", "Warehouse", "Odometer", 1},
            {410L, 11L, "自有物料仓", "menu", "/outsource/material-warehouse", "OutsourceMaterialWarehouse", "Box", 2},
            // 物料库存盘点（2026-09-16 用户要求）：与成品「库存盘点」按仓库类别彻底分开 ——
            // 本页只盘物料仓（委外仓 + 自有物料仓），成品页只盘成品类仓库；
            // 且本页**接口级限「跟单专员」**（见 StockTakeServiceImpl.assertRoleForScope，管理员兜底）
            {414L, 11L, "物料库存盘点", "menu", "/outsource/material-stock-take", "OutsourceMaterialStockTake", "DocumentChecked", 3},
            // 物料报损：与成品报损独立成表（主体为 outsource_material，物料库存不区分品质，固定按良品扣减）
            {413L, 11L, "物料报损", "menu", "/outsource/stock-loss", "OutsourceStockLoss", "DeleteFilled", 4},
            {407L, 11L, "物料其他出入库", "menu", "/outsource/other-io", "OutsourceOtherIo", "Files", 5},
            {406L, 11L, "物料收发单", "menu", "/outsource/delivery", "OutsourceDelivery", "Tickets", 6},
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
            // 售后：销售退单 → 销售换货单（换货可选择性收费）；「退货整理」已于 2026-09-18 移到「成品库存」
            {603L, 6L, "销售退单", "menu", "/sale/return", "SaleReturn", "Refund", 2},
            {605L, 6L, "销售换货单", "menu", "/sale/exchange", "SaleExchange", "Refresh", 3},
            // 成品库存子菜单顺序（2026-09-18 用户定稿重排）：
            // 成品移仓单 → 退货整理 → 成品库存情况 → 成品库存流水 → 库存盘点 → 成品报损 → 成品其他出入库 → 成品品质重分类 → 成品仓库管理
            // sort_order 即左侧栏显示顺序（MenuMapper.selectAllEnabled 按 sort_order 排序）；下方书写顺序与实际显示顺序一致，便于维护。
            {706L, 7L, "成品移仓单", "menu", "/inventory/warehouse-move", "InventoryWarehouseMove", "Rank", 1},
            // 退货整理（2026-09-18 用户要求：从「销售业务」移到「成品库存」—— 它本质是退回品的成品分选入库）
            // ID 仍保留 707（存量角色授权按 ID 关联，换 ID 会导致历史授权失效），仅迁移 parent_id 6 → 7；
            // 同日用户重排本组顺序，退货整理由第 10 位改排**第 2 位**（紧跟成品移仓单）。
            // ⚠️ 父目录 7 必须同时授权，否则菜单树 buildTree 会把 707 整组丢弃
            // （下方幂等补授块给其它持有 707 的角色兜底）。
            {707L, 7L, "退货整理", "menu", "/inventory/return-sort", "InventoryReturnSort", "RefreshRight", 2},
            // 成品库存情况：按产品维度看跨仓库库存汇总；点行进详情看该产品在各仓库的分布
            {712L, 7L, "成品库存情况", "menu", "/inventory/product-stock", "InventoryProductStock", "Box", 3},
            {703L, 7L, "成品库存流水", "menu", "/inventory/stock-log", "WarehouseStockLog", "TrendCharts", 4},
            // 库存盘点：每月每仓一次，仓库列表与盘点页显示待盘点/超期提醒
            {711L, 7L, "库存盘点", "menu", "/inventory/stock-take", "InventoryStockTake", "Files", 5},
            // 成品报损：草稿→审核扣减成品库存（LOSS_OUT 流水），可反审核回滚
            {713L, 7L, "成品报损", "menu", "/inventory/stock-loss", "InventoryStockLoss", "DeleteFilled", 6},
            {704L, 7L, "成品其他出入库", "menu", "/inventory/other-io", "InventoryOtherIo", "Upload", 7},
            {705L, 7L, "成品品质重分类", "menu", "/inventory/reclassify", "InventoryReclassify", "Refresh", 8},
            {702L, 7L, "成品仓库管理", "menu", "/inventory/warehouse", "Warehouse", "Odometer", 9},
            // 701「成品库存」（/inventory/stock）已于 2026-09-18 按用户要求下线（页面代码已删，功能由
            // 「712 成品库存情况」按产品维度覆盖）⇒ 与 409/602/503 同范式：不再 upsert，
            // 下方统一置 visible=0（保留行与角色授权，便于回滚）；其历史 sort_order 已在下方挪到 99，
            // 不再占用 1~9，避免与 707 并列（将来若回滚启用也不会产生顺序歧义）。
            // 财务管理子菜单顺序（2026-09-18 用户定稿重排）：
            // 账单生成 → 收款管理 → 付款管理 → 费用管理 → 应收管理 → 应付管理 → 资金流水 → 账户管理 → 发票管理 → 应付转应收
            // sort_order 即左侧栏显示顺序（MenuMapper.selectAllEnabled 按 sort_order 排序）；下方书写顺序与实际显示顺序一致，便于维护。
            {803L, 8L, "账单生成", "menu", "/finance/bill", "FinanceBill", "Postcard", 1},
            {805L, 8L, "收款管理", "menu", "/finance/receipt", "FinanceReceipt", "Money", 2},
            {806L, 8L, "付款管理", "menu", "/finance/payment", "FinancePayment", "Sell", 3},
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
            int hidden = jdbcTemplate.update("UPDATE sys_menu SET visible = 0 WHERE id IN (104, 405, 302, 303, 409, 602, 503, 701) AND visible = 1");
            if (hidden > 0) log.info("已下线历史菜单 {} 条（104 阶段模板管理 / 405 加工合同模板 / 302 BOM管理 / 303 图纸文档 / 409 委外加工-供应商管理 / 602 销售业务-客户管理 / 503 进货业务-供货商管理 / 701 成品库存查询）", hidden);
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
                // ===== 研发管理（目录 3）=====
                {301L, "dev:project"},
                {304L, "dev:material"},
                {305L, "dev:screen-model"},
                // ===== 委外加工（目录 4）=====
                {401L, "outsource:order"},
                {402L, "outsource:material-order"},
                {408L, "outsource:return-order"},
                {411L, "outsource:material-return"},
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
                {702L, "stock:warehouse"},
                {703L, "stock:log"},
                {704L, "stock:other-io"},
                {705L, "stock:reclassify"},
                {706L, "stock:warehouse-move"},
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
                {404L, "outsource:warehouse"},
                {406L, "outsource:delivery"},
                {407L, "outsource:other-io"},
                {410L, "outsource:material-warehouse"},
                {413L, "outsource:stock-loss"},
                {414L, "outsource:material-stock-take"},
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
                501L, 502L, 503L, 504L,
                601L, 602L, 603L, 605L,
                702L, 703L, 704L, 705L, 706L, 711L, 712L, 713L,
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
                404L, 406L, 407L, 410L, 413L, 101L));
        // 跟单专员：委外加工全部 + 相关基础数据/进货/销售/成品库存页面 + 物料仓库整组（含 414 物料库存盘点）
        // （原 405 加工合同模板 → 108 模版管理，权限等价迁移）
        // 412 成品收货 / 415 物料收货（2026-09-16）：原详情页签移出成菜单，权限沿用委外加工原范围
        // 107「供货商管理」（基础数据）：2026-09-18 起供货商管理只保留基础数据这一处入口；
        // 跟单专员原先只有 409（9-17 已隐藏的委外侧重复入口）⇒ 必须授 107 才看得到供货商主数据
        // 707「退货整理」（成品库存）：2026-09-18 用户要求补授 —— 售后分选入库链路跟单专员也参与
        assignRoleMenus("merchandiser", Arrays.asList(
                1L, 2L, 4L, 5L, 6L, 7L, 11L,
                401L, 402L, 403L, 404L, 406L, 407L, 408L, 409L, 410L, 412L, 413L, 414L, 415L,
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
