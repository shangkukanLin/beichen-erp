package com.beichen.erp.config;

import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * F3-3（2026-09-18 接口级权限专项）：**接口级权限的唯一事实来源**。
 *
 * <p><b>四张表</b>（判定顺序：EXEMPT → 共享只读 → RULES → WRITE_RULES）：</p>
 * <p><b>⚠️ 2026-09-20（F7-106 第二步）起：四者都未命中 ⇒ <u>默认拒绝</u></b>
 * （{@code app.perm.default-deny}，默认 {@code true}）。此前是"未命中即放行"的白名单式收口 ——
 * 新增控制器若忘记登记就等于"任何登录用户可读可写"（F7-105 的 {@code /api/inventory/outbound} 即如此）。
 * 启动期另由 {@link ApiPermGuardSelfCheck} 枚举全部 {@code /api} 端点核对是否已登记（未登记打 ERROR）。</p>
 * <ol>
 *   <li>{@link #EXEMPT}：完全豁免（登录即可），跨模块共用的基础数据读、字典、附件、门户、已由角色保护的 system 域。</li>
 *   <li>{@link #READ_SHARED}：**共享只读** —— 该前缀被**多个页面**读取（详见每条注释里的调用方清单，
 *       来自 {@code audit-frontend-api-crosspage.ps1} 的静态走查），因此 GET 放行、**写仍按页面码收口**。
 *       不放宽会误拦：例如首页工作台要读各模块"待办/最近单据"、委外下单页要读研发项目。</li>
 *   <li>{@link #RULES}：模块规则 —— 按页面码收口（GET 未列入共享只读时同样收口；写一律收口）。</li>
 *   <li>{@link #WRITE_RULES}：基础数据的**写保护** —— GET 视为共享读取放行，非 GET 需要对应页面码
 *       （v1 曾整体豁免，等于任何登录用户都能改产品/客户/仓库主数据；此处补齐）。</li>
 * </ol>
 *
 * <p>为什么用集中映射而不是给 469 个端点逐个加 {@code @SaCheckPermission}：端点量大、散落 85 个文件易漏难审；
 * 集中表可一眼看全，且便于显式表达"一控制器多页""跨页共享读"。需要同页细分（查看/审核/删除）时，
 * 仍可在具体端点叠加 {@code @SaCheckPermission} 逐步收细。</p>
 *
 * <p>匹配方式：前缀**最长匹配**优先（如 {@code /api/inventory/purchase-return} 先于 {@code /api/inventory/purchase}），
 * 命中后 {@code StpUtil.checkPermissionOr(...)}：缺码抛 {@code NotPermissionException} →
 * {@code GlobalExceptionHandler} 返回 403「无权限访问」。</p>
 */
@Component
public class ApiPermGuard {

    /**
     * 完全豁免前缀（读写都放行，登录即可访问）。
     * <p>注意：**基础数据（产品/客户/供应商/仓库/物料类型…）不在这里** —— 它们是
     * "读共享 + 写收口"，见 {@link #WRITE_RULES}；放进 EXEMPT 会让写保护失效（判定顺序在写规则之前）。</p>
     */
    private static final List<String> EXEMPT = List.of(
            "/api/auth",                 // 登录、本人信息、改密
            "/api/common",               // 单号解析（跨模块跳转）
            "/api/company",              // 公司（超管，内部自校验）
            "/api/memo",                 // 个人备忘录
            "/api/dashboard",            // 首页门户接口
            "/api/analysis",             // 经营分析（只读聚合）
            "/api/finance/analysis",     // 财务分析聚合（门户 + 多个分析页共用）
            "/api/supplier-settlement",  // 供应商结算（跨模块只读）
            "/api/dev/file",             // 附件上传/下载（各页皆可上传）
            // F7-106（2026-09-20）：销售分析（只读聚合，与 /api/analysis、/api/finance/analysis 同类）。
            // 原先未登记 —— 因本类是"白名单式收口 + 无默认拒绝"，未登记前缀等于"登录即可"；
            // 它只有 GET 聚合查询，故按只读聚合登记在 EXEMPT（比塞进 RULES 更贴合语义，避免误收口）。
            "/api/sale/analysis",        // 销售分析（只读聚合）
            "/api/system"                // 已由 @SaCheckRole(admin/super_admin) 保护，不重复收口
    );

    /**
     * 共享只读前缀：GET 放行，非 GET 仍按 {@link #RULES} 收口。
     *
     * <p><b>2026-09-19「读隔离」收口后已清空</b> —— 曾经有 14 条（首页工作台读各模块待办、付款页读应付、
     * 采购退货页读来源采购单、销售单详情读收款/换货单……），它们都是"跨页面直接读别的模块接口"的兜底。
     * 三期改造把这些读取全部下沉为**消费页自己的接口**（首页走聚合 {@code /api/dashboard/module-pages}
     * 且按 perms 过滤；详情页随详情响应返回；来源单据选择器在各自前缀下开只读端点），
     * 于是本名单不再需要 —— 现在"读"与"写"同一口径：只认页面码。</p>
     *
     * <p>回归资产：{@code audit-frontend-api-crosspage.ps1 -Strict}（忽略本名单，逐条列跨页读）
     * 在收口后为 <b>0 违规</b>；任何新的跨页调用都会被它标红。</p>
     *
     * <p>保留本字段（空表）是为了：①未来的确跨模块共用的**新**前缀仍可显式登记；
     * ②判定顺序（EXEMPT → 共享只读 → RULES → WRITE_RULES）不变。真正的基础数据（产品/客户/供应商/
     * 仓库/物料…）读共享走 {@link #WRITE_RULES}，不在这里。</p>
     */
    private static final List<String> READ_SHARED = List.of(
            // 空：2026-09-19 读隔离收口（历史 14 条见上方说明；audit -Strict 已归零）
    );

    /** 前缀 → 允许的权限码（任一满足即可） */
    private static final Map<String, String[]> RULES = new LinkedHashMap<>();

    /**
     * 基础数据：**读共享 + 写收口** —— GET 视为跨模块共享读取直接放行；非 GET（新增/修改/删除）
     * 需要对应页面码。v1 曾把这些前缀整体豁免，等于任何登录用户都能改主数据，此处补齐。
     */
    private static final Map<String, String[]> WRITE_RULES = new LinkedHashMap<>();

    /**
     * **用 POST 实现的只读批量查询**（前端按 id 数组批量取数）—— 按读取处理，否则会被
     * "写必收口"误拦。跨页走查（{@code audit-frontend-api-crosspage.ps1}）就是靠这张表把
     * 首页工作台的偏差从 1 降到 0。
     */
    private static final List<String> READ_POST_PATHS = List.of(
            "/api/dev/project/batch-phases"   // 批量取项目阶段（首页工作台 + 研发立项页共用）
    );

    /**
     * 按钮级动作码（**方案 A：动作码默认跟随页面**）：模块前缀 → 已登记的动作名集合。
     * <p>登记范围 = {@code DataInitializer.initButtonPerms()} 写入的 {@code menu_type='button'} 行
     * （当前 4 个试点模块：采购换货/采购退货/销售单/销售退货）。判定用
     * {@code <页面码>:<动作>} **或** 页面码 —— 因为方案 A 下动作码必然跟随页面；
     * 将来切到"动作独立授权"只需去掉页面码兜底（见 {@link #check}）。</p>
     */
    private static final Map<String, List<String>> ACTION_RULES = new LinkedHashMap<>();

    /** 动作后缀 → 动作名（{@code DELETE} 方法对应 {@code delete} 动作） */
    private static final Map<String, String> ACTION_SUFFIX = Map.of(
            "/audit", "audit",
            "/un-audit", "unaudit",
            "/cancel", "cancel"
    );

    /**
     * F7-106 第二步（2026-09-20）：**未命中任何规则时的默认策略**（{@code app.perm.default-deny}）。
     *
     * <ul>
     *   <li>{@code true}（默认）：**拒绝** —— 白名单式收口的安全兜底，防"新增控制器忘登记即裸奔"
     *       （F7-105 的 {@code /api/inventory/outbound} 就是未登记 ⇒ 任何登录用户可写）；</li>
     *   <li>{@code false}：**放行** —— 应急回滚开关，等价于 2026-09-20 之前的旧行为。</li>
     * </ul>
     *
     * <p>配套保障：{@link ApiPermGuardSelfCheck} 在启动时枚举全部 {@code /api} 端点核对是否已登记，
     * 未登记会打 ERROR 日志 ⇒ 先看日志归零、再依赖默认拒绝。</p>
     */
    @Value("${app.perm.default-deny:true}")
    private boolean defaultDeny;

    private static void actionRule(String prefix, String... actions) {
        ACTION_RULES.put(prefix, List.of(actions));
    }

    private static void rule(String prefix, String... perms) {
        RULES.put(prefix, perms);
    }

    private static void writeRule(String prefix, String... perms) {
        WRITE_RULES.put(prefix, perms);
    }

    static {
        // ===== 采购（进货业务）=====
        rule("/api/inventory/purchase-exchange", "purchase:exchange");
        rule("/api/inventory/purchase-return", "purchase:return");
        rule("/api/inventory/purchase", "purchase:order");
        // ===== 销售业务 =====
        // F7-105（2026-09-20）：销售出库单原先**未登记**（⇒ 登录即可调用，见报告 §37）。它语义上是
        // 销售单的出库凭证，故复用销售单页面码 sale:order（系统内无 sale:outbound 码）。
        rule("/api/inventory/outbound", "sale:order");
        rule("/api/sale/exchange", "sale:exchange");
        rule("/api/sale/return", "sale:return");
        rule("/api/inventory/sale", "sale:order");
        // ===== 成品库存 =====
        rule("/api/inventory/return-sort", "stock:return-sort");
        rule("/api/inventory/other", "stock:other-io");
        rule("/api/inventory/reclassify", "stock:reclassify");
        rule("/api/inventory/stock-take", "stock:stock-take");
        rule("/api/inventory/stock-loss", "stock:stock-loss");
        rule("/api/inventory/warehouse-move", "stock:warehouse-move");
        // 物料移仓单（2026-09-24 新增，替代已下线的手工物料收发单）
        rule("/api/inventory/material-move", "stock:material-move");
        // ===== 委外加工 =====
        // 401 加工订单页的「交货」页签会写交货记录 ⇒ 与 412 成品收货页共用同一控制器，两码任一即可
        rule("/api/outsource/order-delivery", "outsource:order-delivery", "outsource:order");
        // 物料订单控制器被**两个页面**合法共用：401 物料订单（列表/详情）+ 415 物料收货
        // （待收货物料订单列表页 `outsource/material-order/delivery`）⇒ 两码任一即可。
        // 2026-09-19 期 3：这是"一控制器多页"的显式登记（不再靠共享只读白名单兜底）。
        rule("/api/outsource/material-order", "outsource:material-order", "outsource:material-delivery");
        rule("/api/outsource/order", "outsource:order");
        rule("/api/outsource/delivery", "outsource:delivery");
        rule("/api/outsource/material-return", "outsource:material-return");
        rule("/api/outsource/return-order", "outsource:return-order");
        rule("/api/outsource/material-info", "outsource:material-info");
        rule("/api/outsource/other-io", "outsource:other-io");
        rule("/api/outsource/stock-loss", "outsource:stock-loss");
        rule("/api/outsource/contract-template", "base:template");
        // ===== 财务管理 =====
        rule("/api/finance/payable-transfer", "finance:payable-transfer");
        rule("/api/finance/payable", "finance:payable");
        rule("/api/finance/receivable", "finance:receivable");
        rule("/api/finance/bill", "finance:bill");
        rule("/api/finance/receipt", "finance:receipt");
        rule("/api/finance/payment", "finance:payment");
        // 资金流水页内联登记费用 ⇒ 与费用管理页共用，两码任一即可
        rule("/api/finance/expense", "finance:expense", "finance:cashflow");
        rule("/api/finance/invoice", "finance:invoice");
        rule("/api/finance/cashflow", "finance:cashflow");
        // ===== 研发（三页共用同一控制器 ⇒ 任一码即可，避免互相打断）=====
        rule("/api/dev/purchase-item", "dev:material", "dev:project");
        rule("/api/dev/screen-model", "dev:screen-model");
        // ===== 客户分析 =====
        rule("/api/customer/analysis", "analysis:customer");

        // ===== 基础数据写保护（GET 共享读取；写需对应页面码）=====
        writeRule("/api/product", "base:product");
        writeRule("/api/brand", "base:brand");
        writeRule("/api/supplier", "base:supplier", "outsource:supplier"); // 两页共用同一控制器
        writeRule("/api/inventory/customer", "base:customer");
        // 委外仓库 / 自有物料仓两个页面也用同一控制器维护仓库（含委外仓库），三码任一即可
        writeRule("/api/warehouse", "stock:warehouse", "outsource:warehouse", "outsource:material-warehouse");
        writeRule("/api/dev/material-type", "base:material-type");
        // 研发项目（2026-09-19）：**基础数据归类** —— 4 个委外页面（下单/加工订单详情/委外仓库/物料信息）
        // 与首页都要选研发项目，故 GET 共享、写仍收口（与产品/客户/仓库同口径）。
        // 原先是 RULES 收口 ⇒ 委外用户直读项目列表会 403（读隔离走查 audit-frontend-api-crosspage -Strict 查出 6 处）。
        writeRule("/api/dev/project", "dev:project", "dev:material", "dev:screen-model");
        writeRule("/api/dev/phase-template", "base:template", "dev:project");
        writeRule("/api/dev/material-flow", "dev:material");
        writeRule("/api/settings", "system:settings");
        // 委外物料主数据：7 个页面读取（物料选择器），研发立项/委外下单页还会内联新增 ⇒ 见 audit 走查
        writeRule("/api/outsource/material", "outsource:material-info", "dev:project", "outsource:order");
        // 资金账户：账户管理页维护；收款/付款/资金流水/费用/销售单页内联新增
        writeRule("/api/finance/account", "finance:account", "finance:cashflow", "finance:expense",
                "finance:payment", "finance:receipt", "sale:order");

        // ===== 按钮级动作码（方案 A 试点 4 个模块，与 DataInitializer.initButtonPerms 保持一致）=====
        actionRule("/api/inventory/purchase-exchange", "audit", "unaudit", "cancel");
        actionRule("/api/inventory/purchase-return", "audit", "unaudit", "cancel", "delete");
        actionRule("/api/inventory/sale", "audit", "unaudit", "cancel");
        actionRule("/api/sale/return", "audit", "unaudit", "cancel", "delete");
    }

    /**
     * 前缀匹配必须**按路径段**：{@code /api/outsource/material} 只能命中
     * {@code /api/outsource/material} 与 {@code /api/outsource/material/xxx}，
     * **不能**命中 {@code /api/outsource/material-info}（否则会误放行/误收口相邻模块）。
     */
    private static boolean under(String uri, String prefix) {
        return uri.equals(prefix) || uri.startsWith(prefix + "/");
    }

    /** 最长前缀匹配（按路径段）；无命中返回 null */
    private static String longest(Map<String, String[]> map, String uri) {
        String best = null;
        for (String k : map.keySet()) {
            if (under(uri, k) && (best == null || k.length() > best.length())) {
                best = k;
            }
        }
        return best;
    }

    /**
     * 命中按钮级动作时返回 {@code <页面码>:<动作>}；该模块未登记此动作则返回 null。
     *
     * @param prefix    命中的模块前缀
     * @param uri       请求路径
     * @param method    HTTP 方法（DELETE 对应 delete 动作）
     * @param pageCodes 该前缀的页面码（主码取第一个，用于拼动作码）
     */
    private static String actionCodeOf(String prefix, String uri, String method, String[] pageCodes) {
        List<String> actions = ACTION_RULES.get(prefix);
        if (actions == null || actions.isEmpty() || pageCodes.length == 0) {
            return null;
        }
        String action = null;
        if ("DELETE".equalsIgnoreCase(method) && actions.contains("delete")) {
            action = "delete";
        } else {
            for (Map.Entry<String, String> e : ACTION_SUFFIX.entrySet()) {
                if (uri.endsWith(e.getKey()) && actions.contains(e.getValue())) {
                    action = e.getValue();
                    break;
                }
            }
        }
        return action == null ? null : pageCodes[0] + ":" + action;
    }

    /**
     * 校验请求。
     *
     * @param uri    请求路径（不含 context-path，如 {@code /api/inventory/purchase-exchange/page}）
     * @param method HTTP 方法（GET/HEAD/OPTIONS 视为读取）
     */
    public void check(String uri, String method) {
        if (uri == null || uri.isEmpty()) {
            return;
        }
        for (String ex : EXEMPT) {
            if (under(uri, ex)) {
                return;
            }
        }
        boolean read = method == null || "GET".equalsIgnoreCase(method)
                || "HEAD".equalsIgnoreCase(method) || "OPTIONS".equalsIgnoreCase(method);
        if (!read && "POST".equalsIgnoreCase(method)) {
            for (String p : READ_POST_PATHS) {
                if (uri.equals(p)) {
                    read = true;
                    break;
                }
            }
        }
        if (read) {
            // 跨页共享读：模块共享只读名单 + 基础数据（写规则命中的前缀一律读放行）
            for (String s : READ_SHARED) {
                if (under(uri, s)) {
                    return;
                }
            }
            if (longest(WRITE_RULES, uri) != null) {
                return;
            }
        }
        String rp = longest(RULES, uri);
        if (rp != null) {
            String[] pageCodes = RULES.get(rp);
            String action = actionCodeOf(rp, uri, method, pageCodes);
            if (action != null) {
                // 方案 A：动作码 或 页面码 任一即可（动作码跟随页面，故两者等价）。
                // 如将来改为"动作独立授权"：删掉下面的 pageCodes 兜底，改为 StpUtil.checkPermissionOr(action)。
                String[] any = new String[pageCodes.length + 1];
                any[0] = action;
                System.arraycopy(pageCodes, 0, any, 1, pageCodes.length);
                StpUtil.checkPermissionOr(any);
            } else {
                StpUtil.checkPermissionOr(pageCodes);
            }
            return;
        }
        String wp = longest(WRITE_RULES, uri);
        if (wp != null && !read) {
            StpUtil.checkPermissionOr(WRITE_RULES.get(wp));
            return;
        }
        // F7-106 第二步（2026-09-20）：**四张表都没命中** ⇒ 按默认策略处理。
        // 本类原先是纯"白名单式收口"：未命中即**放行**（没有默认拒绝分支）⇒ 新增控制器忘登记
        // 就等于"任何登录用户可读、可写"（F7-105 的 /api/inventory/outbound 正是如此，
        // 且它叠加了"出库单重复扣库存"⇒ 曾可用一条 curl 造出库存差异）。
        // 现改为**默认拒绝**；应急可用 app.perm.default-deny=false 回滚为旧行为。
        // 注意：命中 WRITE_RULES 的**读**请求已在上面 `if (read)` 分支 return，走到这里的
        // `wp != null` 只可能是"写"（已被收口）；因此只需在 `wp == null` 时兜底。
        if (defaultDeny) {
            throw new NotPermissionException(
                    "接口未登记收口前缀，已按默认策略拒绝（app.perm.default-deny=true）：" + uri);
        }
    }

    // ==================== F7-106（2026-09-20）：收口自检支持 ====================

    /**
     * 已登记的收口前缀全量（{@link #EXEMPT} + {@link #RULES} + {@link #WRITE_RULES} 的 key）。
     *
     * <p>本类是**白名单式收口**：上面四张表都没命中时原本 {@link #check} 直接返回 = <b>放行</b>
     * （没有默认拒绝分支）⇒ 新增控制器若忘登记就"登录即可读写"；**2026-09-20（F7-106 第二步）起
     * 已改为默认拒绝**。该访问器供 {@code ApiPermGuardSelfCheck} 在启动时与**实际注册的端点**比对，
     * 把"忘登记"从静默盲区变成**启动即报**。</p>
     */
    public static Set<String> registeredPrefixes() {
        Set<String> all = new LinkedHashSet<>(EXEMPT);
        all.addAll(RULES.keySet());
        all.addAll(WRITE_RULES.keySet());
        return all;
    }

    /**
     * 某个 URI 是否落在已登记的收口前缀内（**按路径段最长匹配**，语义与 {@link #check} 保持一致）。
     *
     * <p>注意 {@link #READ_POST_PATHS} 是"精确路径"而非前缀，故单独判断。
     * 换句话说：本方法返回 {@code false} 的 URI，**2026-09-20 之前任何人都能访问**（仅需登录），
     * 现在则由默认拒绝（{@link #defaultDeny}）兜底成 403。</p>
     */
    public static boolean isRegistered(String uri) {
        if (uri == null || uri.isEmpty()) {
            return true;
        }
        for (String ex : EXEMPT) {
            if (under(uri, ex)) {
                return true;
            }
        }
        for (String p : READ_POST_PATHS) {
            if (uri.equals(p)) {
                return true;
            }
        }
        return longest(RULES, uri) != null || longest(WRITE_RULES, uri) != null;
    }
}
