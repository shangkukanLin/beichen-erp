package com.beichen.erp.config;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import com.beichen.erp.common.R;
import com.beichen.erp.common.DefaultMaterialTypes;
import com.beichen.erp.common.DefaultContractTemplate;
import com.beichen.erp.common.DefaultPhaseTemplates;
import com.beichen.erp.system.common.SystemConstants;
import com.beichen.erp.system.entity.OperationLog;
import com.beichen.erp.system.mapper.OperationLogMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 数据清空/系统修复接口：均为高危操作。
 * 全局拦截器只校验登录，角色控制必须在此显式声明（SaTokenConfig 无统一角色鉴权）。
 *
 * <p>【P2-34 口径 · 2026-09-12 定稿】按"是否跨租户"划线：</p>
 * <ul>
 *   <li>{@code POST /api/system/clear-company-data}：**按 {@code CompanyContext} 过滤**清空本公司业务数据
 *       → 租户内自服务，保留给公司管理员（类级注解的 {@code ADMIN} 即为此）；</li>
 *   <li>{@code POST /api/system/clear-data}：**全库清空**（遍历所有表 DELETE，无 company 维度）
 *       → 平台级且不可逆，已收紧为**仅超级管理员**（方法级注解在类级放行之外再收紧）；</li>
 * </ul>
 *
 * <p>【F8-16 ~ F8-19 · 2026-09-30 审核批 D 修复】原实现（清空）与 {@link SystemController}（导入）的加固
 * 严重不对称，本次按导入侧口径补齐：</p>
 * <ul>
 *   <li><b>F8-16</b>：清单由"手写 86 张"补齐为 {@link #COMPANY_DELETES}（**98 张**，含原先漏掉的
 *       采购换货、加工返回、物料移库、收付款账户分摊、维修相关等 12 张），并由启动自检
 *       {@link ClearTableSelfCheck} 校验"清单 ⊇ 含 company_id 的 BASE TABLE − {@link #PRESERVE_TABLES}"，
 *       防止再次漂移；</li>
 *   <li><b>F8-17</b>：补 4 项加固 —— ① 清空前**自动导出回滚点**（格式同「导出数据」，可直接用「数据导入」恢复）
 *       ② 清空**必须带 {@code confirm} 口令**（后端强制，直调 API 也拦）③ 全过程**写操作日志**到
 *       {@code sys_operation_log} ④ 失败**显式 {@code rollback() }**（不再依赖连接关闭的隐式回滚）；</li>
 *   <li><b>F8-18</b>：全库清空的保留表改为**显式常量** {@link #PRESERVE_TABLES}（原 SQL 里硬写着
 *       {@code table_name <> 'screen_model'}），并加"待清表数异常即中止"的兜底；</li>
 *   <li><b>F8-19</b>：菜单码 {@code system:clear-data}/{@code system:data-manage} 因 {@code /api/system}
 *       整段在 ApiPermGuard 的 EXEMPT 名单里而**无法用于接口收口**（EXEMPT 判定在 RULES 之前）⇒ 本类的
 *       授权口径就是**角色**（见类注解）；已在 {@code DataInitializer} 把菜单文案改为「清空本公司数据」，
 *       消除"名字像清库、实际只清本公司"的歧义。</li>
 * </ul>
 *
 * <p><b>演练模式</b>：两个端点均支持 {@code dryRun=true} —— 只做"读 + 生成回滚点 + 报每张表将删行数"，
 * 不执行任何 DELETE（且无需 confirm）。它既是给管理员的安全预演，也是本次修复**可自动实证**的入口。</p>
 */
@Slf4j
@RestController
@SaCheckRole(value = {SystemConstants.SUPER_ADMIN_ROLE_CODE, SystemConstants.ADMIN_ROLE_CODE}, mode = SaMode.OR)
public class ClearController {

    @Autowired private DataSource dataSource;
    @Autowired private OperationLogMapper operationLogMapper;
    /** 必须用容器里的 ObjectMapper（含 JavaTimeModule），裸 new 会把 LocalDateTime 序列化炸掉 */
    @Autowired private ObjectMapper objectMapper;
    /** 回滚点落盘根目录（与文件上传、导入前备份同根） */
    @Value("${file.upload.path:./uploads}")
    private String uploadPath;

    /** 清空本公司数据的确认口令（前端第一道，后端强制第二道） */
    public static final String CONFIRM_COMPANY = "清空数据";
    /** 全库清空的确认口令 */
    public static final String CONFIRM_ALL = "清空全库";

    /**
     * 全库清空时**必须保留**的表（F8-18：显式名单，替代原先散在 SQL 里的 {@code table_name <> 'screen_model'}）。
     * <p>新增"行业基础资料/不可清"性质的表时在此登记，并写明理由。</p>
     */
    public static final Set<String> PRESERVE_TABLES = Collections.unmodifiableSet(
            new LinkedHashSet<>(List.of("screen_model")));   // 屏幕资料知识库：行业基础资料，不属于业务数据

    /**
     * 「清空本公司数据」的删除清单（F8-16，**顺序：子表 → 主表 → 基础数据**；{@code ?} 由 company_id 绑定）。
     * <p>⚠️ 维护约定：新增含 {@code company_id} 的业务表后，**必须**在此补一条；启动自检
     * {@link ClearTableSelfCheck} 会在日志里报出遗漏的表（差集不为空即 ERROR）。</p>
     */
    private static final List<String> COMPANY_DELETES = List.of(
            // === 财务明细 ===
            "DELETE FROM finance_payment_item WHERE company_id = ?",              // F8-16 已存在
            "DELETE FROM finance_payment_account WHERE company_id = ?",           // F8-16 新增（付款-账户分摊，156/38 行级残留）
            "DELETE FROM finance_receipt_item WHERE company_id = ?",
            "DELETE FROM finance_receipt_account WHERE company_id = ?",           // F8-16 新增（收款-账户分摊）
            "DELETE FROM finance_bill_item WHERE company_id = ?",
            "DELETE FROM finance_settlement WHERE company_id = ?",
            "DELETE FROM finance_expense WHERE company_id = ?",
            "DELETE FROM finance_invoice WHERE company_id = ?",
            // 应付转应收单（引用 finance_payable，须先于应付主表删除）
            "DELETE FROM finance_payable_transfer WHERE company_id = ?",
            // === 业务明细 ===
            "DELETE FROM purchase_order_item WHERE company_id = ?",
            "DELETE FROM purchase_return_item WHERE company_id = ?",
            "DELETE FROM purchase_exchange_item WHERE company_id = ?",            // F8-16 新增（采购换货，113/114 行）
            "DELETE FROM sale_outbound_item WHERE company_id = ?",
            "DELETE FROM sale_order_item WHERE company_id = ?",
            "DELETE FROM sale_return_item WHERE company_id = ?",
            "DELETE FROM sale_exchange_item WHERE company_id = ?",
            "DELETE FROM return_sort_item WHERE company_id = ?",
            "DELETE FROM inventory_warehouse_move_item WHERE company_id = ?",
            "DELETE FROM inventory_material_move_item WHERE company_id = ?",      // F8-16 新增（物料移库）
            "DELETE FROM inventory_other_io_item WHERE company_id = ?",
            "DELETE FROM inventory_stock_take_item WHERE company_id = ?",
            "DELETE FROM inventory_stock_reclass_item WHERE company_id = ?",
            "DELETE FROM inventory_stock_loss_item WHERE company_id = ?",
            "DELETE FROM outsource_stock_loss_item WHERE company_id = ?",
            "DELETE FROM product_reclassify_item WHERE company_id = ?",
            "DELETE FROM outsource_delivery_item WHERE company_id = ?",
            "DELETE FROM outsource_material_component WHERE company_id = ?",
            "DELETE FROM outsource_material_order_item WHERE company_id = ?",
            "DELETE FROM outsource_material_return_item WHERE company_id = ?",
            "DELETE FROM outsource_material_return_repair_material WHERE company_id = ?",   // F8-16 新增（物料退货维修）
            // BOM 快照（2026-09-17 重构：原 outsource_order_material 表已成只读视图，明细落在 bom_snapshot_item）
            "DELETE FROM bom_snapshot_item WHERE company_id = ?",
            "DELETE FROM bom_snapshot WHERE company_id = ?",
            "DELETE FROM outsource_order_product WHERE company_id = ?",
            "DELETE FROM outsource_order_delivery WHERE company_id = ?",
            "DELETE FROM outsource_other_io_item WHERE company_id = ?",
            "DELETE FROM outsource_return_order_item WHERE company_id = ?",
            "DELETE FROM outsource_return_order_product WHERE company_id = ?",
            "DELETE FROM outsource_return_back_item WHERE company_id = ?",        // F8-16 新增（加工返回单）
            "DELETE FROM outsource_return_order_repair_item WHERE company_id = ?",// F8-16 新增（加工退货维修）
            // 结单报告明细：该表**自身无 company_id**，只能按父表归属删除（故不是简单的 WHERE company_id = ?）
            "DELETE FROM outsource_order_close_report_item WHERE report_id IN (SELECT id FROM outsource_order_close_report WHERE company_id = ?)",
            // === 财务主表 ===
            "DELETE FROM finance_payment WHERE company_id = ?",
            "DELETE FROM finance_receipt WHERE company_id = ?",
            "DELETE FROM finance_bill WHERE company_id = ?",
            "DELETE FROM finance_cashflow WHERE company_id = ?",
            "DELETE FROM finance_receivable WHERE company_id = ?",
            "DELETE FROM finance_payable WHERE company_id = ?",
            "DELETE FROM finance_account WHERE company_id = ?",
            // === 库存流水（先删流水再删库存） ===
            "DELETE FROM warehouse_stock_log WHERE company_id = ?",
            "DELETE FROM cost_inbound_log WHERE company_id = ?",
            // === 库存主表 ===
            "DELETE FROM warehouse_stock WHERE company_id = ?",
            "DELETE FROM inventory_warehouse_move WHERE company_id = ?",
            "DELETE FROM inventory_material_move WHERE company_id = ?",           // F8-16 新增
            "DELETE FROM inventory_other_io WHERE company_id = ?",
            "DELETE FROM inventory_stock_take WHERE company_id = ?",
            "DELETE FROM inventory_stock_reclass WHERE company_id = ?",
            "DELETE FROM inventory_stock_loss WHERE company_id = ?",
            "DELETE FROM product_reclassify WHERE company_id = ?",
            // === 仓库 ===
            "DELETE FROM warehouse WHERE company_id = ?",
            // === 采购主表 ===
            "DELETE FROM purchase_order WHERE company_id = ?",
            "DELETE FROM purchase_return WHERE company_id = ?",
            "DELETE FROM purchase_exchange WHERE company_id = ?",                 // F8-16 新增
            // === 销售主表 ===
            "DELETE FROM sale_outbound WHERE company_id = ?",
            "DELETE FROM sale_order WHERE company_id = ?",
            "DELETE FROM sale_return WHERE company_id = ?",
            "DELETE FROM sale_exchange WHERE company_id = ?",
            "DELETE FROM return_sort WHERE company_id = ?",
            "DELETE FROM after_sale_pending WHERE company_id = ?",
            // === 委外主表 ===
            "DELETE FROM outsource_other_io WHERE company_id = ?",
            "DELETE FROM outsource_return_order WHERE company_id = ?",
            "DELETE FROM outsource_return_order_repair WHERE company_id = ?",     // F8-16 新增
            "DELETE FROM outsource_return_back WHERE company_id = ?",             // F8-16 新增（加工返回单）
            "DELETE FROM outsource_order_close_report WHERE company_id = ?",
            "DELETE FROM outsource_material_order WHERE company_id = ?",
            "DELETE FROM outsource_material_return WHERE company_id = ?",
            "DELETE FROM outsource_material_return_repair WHERE company_id = ?",  // F8-16 新增
            "DELETE FROM outsource_stock_loss WHERE company_id = ?",
            "DELETE FROM outsource_delivery WHERE company_id = ?",
            "DELETE FROM outsource_order WHERE company_id = ?",
            "DELETE FROM outsource_contract_template WHERE company_id = ?",
            // === 委外物料 ===
            "DELETE FROM outsource_material WHERE company_id = ?",
            // === 研发 ===
            "DELETE FROM dev_drawing WHERE company_id = ?",
            "DELETE FROM dev_bug WHERE company_id = ?",
            "DELETE FROM dev_bom WHERE company_id = ?",
            "DELETE FROM material_type WHERE company_id = ?",
            "DELETE FROM dev_purchase_item WHERE company_id = ?",
            "DELETE FROM dev_material_flow WHERE company_id = ?",
            "DELETE FROM dev_phase_template WHERE company_id = ?",
            "DELETE FROM dev_project_phase WHERE company_id = ?",
            "DELETE FROM dev_project WHERE company_id = ?",
            // === 备忘 ===
            "DELETE FROM memo_progress WHERE company_id = ?",
            "DELETE FROM memo WHERE company_id = ?",
            // === 基础数据 ===
            "DELETE FROM supplier_product WHERE company_id = ?",
            "DELETE FROM supplier_material WHERE company_id = ?",
            "DELETE FROM supplier_type_ref WHERE company_id = ?",
            "DELETE FROM supplier WHERE company_id = ?",
            "DELETE FROM product WHERE company_id = ?",
            "DELETE FROM customer WHERE company_id = ?",
            "DELETE FROM brand WHERE company_id = ?",
            // === 系统表中的**本公司**数据（D-26 · 2026-09-30 用户定：**纳入**）===
            // 这两张表是 sys_* 里唯二"带 company_id 且属于本公司数据"的：
            //   · sys_param          —— 本公司系统参数（清空后回到默认值/空）
            //   · sys_operation_log  —— 本公司操作日志（清空后日志归零）
            // ⚠️ 副作用（有意接受）：本次清空自己写的"开始"审计行也会被一并删掉；但"完成"审计是在
            //    commit **之后**写的（独立事务）⇒ 每个公司的清空动作仍留有一条可查记录。
            // 其余 sys_* （公司/用户/角色/菜单）**保留** —— 与页面文案"系统数据不受影响"一致。
            "DELETE FROM sys_param WHERE company_id = ?",
            "DELETE FROM sys_operation_log WHERE company_id = ?"
            // 注意：屏幕资料知识库（screen_model）不在本清单中——它是行业基础资料，
            // 清空公司业务数据时保留，避免辛苦录入的机型屏幕参数被误删（见 PRESERVE_TABLES）。
    );

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /**
     * 本类会删除/覆盖的表名集合（供启动自检做差集校验）。
     * <p>含"按父表归属删除"的表（自身没有 company_id，如 {@code outsource_order_close_report_item}）。</p>
     */
    public static Set<String> deletedTables() {
        Set<String> out = new LinkedHashSet<>();
        for (String sql : COMPANY_DELETES) {
            out.add(tableOf(sql));
        }
        return out;
    }

    /** 从 {@code DELETE FROM <t> WHERE ...} 里取表名。 */
    private static String tableOf(String deleteSql) {
        String s = deleteSql.substring("DELETE FROM ".length());
        int i = s.indexOf(' ');
        return i < 0 ? s : s.substring(0, i);
    }

    /** {@code DELETE FROM t WHERE ...} → {@code SELECT COUNT(*) FROM t WHERE ...} */
    private static String countSql(String deleteSql) {
        return "SELECT COUNT(*) FROM " + deleteSql.substring("DELETE FROM ".length());
    }

    /** {@code DELETE FROM t WHERE ...} → {@code SELECT * FROM t WHERE ...} */
    private static String selectSql(String deleteSql) {
        return "SELECT * FROM " + deleteSql.substring("DELETE FROM ".length());
    }

    // ------------------------------------------------------------------ 按公司清空

    /**
     * 清空当前公司所有业务数据（保留系统表）。
     *
     * @param confirm 必须等于 {@link #CONFIRM_COMPANY}（{@code dryRun=true} 时不需要）
     * @param dryRun  演练：只读 + 生成回滚点 + 报将删行数，**不删除任何数据**
     */
    @PostMapping("/api/system/clear-company-data")
    public R<?> clearCompanyData(@RequestParam(value = "confirm", required = false) String confirm,
                                 @RequestParam(value = "dryRun", required = false, defaultValue = "false") boolean dryRun) {
        Long companyId = CompanyContext.get();
        if (companyId == null || companyId == 0) {
            return R.fail("超管模式下请先选择公司");
        }
        // F8-17②：后端强制二次确认（前端口令只是第一道；直调 API 同样拦下）
        if (!dryRun && !CONFIRM_COMPANY.equals(confirm)) {
            return R.fail("危险操作未确认：请输入「" + CONFIRM_COMPANY + "」后重试（接口需带 confirm 参数）");
        }
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // F8-17①：先落回滚点（读本公司数据，格式同「导出数据」⇒ 可直接用「数据导入」恢复）
                Map<String, Object> tables = new LinkedHashMap<>();
                Map<String, Object> counts = new LinkedHashMap<>();
                List<String> failed = new ArrayList<>();
                for (String sql : COMPANY_DELETES) {
                    String table = tableOf(sql);
                    try {
                        List<Map<String, Object>> rows = queryRows(conn, selectSql(sql), companyId);
                        tables.put(table, rows);
                        counts.put(table, rows.size());
                    } catch (Exception e) {
                        failed.add(table);
                        log.warn("[审计] 清空回滚点读取表 {} 失败：{}", table, e.getMessage());
                    }
                }
                if (!failed.isEmpty()) {
                    throw new IllegalStateException("回滚点不完整（" + failed.size() + " 张表读取失败）：" + failed + "，已中止清空");
                }
                String backup = writeBackup("PRE_CLEAR_COMPANY_ROLLBACK", tables, companyId);

                Map<String, Object> out = new LinkedHashMap<>();
                out.put("dryRun", dryRun);
                out.put("companyId", companyId);
                out.put("tables", COMPANY_DELETES.size());
                out.put("totalRows", counts.values().stream().mapToInt(v -> (Integer) v).sum());
                out.put("rows", counts);
                out.put("backup", backup);

                if (dryRun) {
                    conn.rollback();      // 演练：什么都没改
                    // F8-05：**演练也留痕**（"谁在何时预演过清空"本身就有审计价值），
                    // 同时这条路径让"审计写入"可以在不删任何数据的前提下被自动化验证（见 verify-fix-f8-16-19.ps1）。
                    audit("清空公司数据", "演练（未删数据）：companyId=" + companyId + "，表数=" + COMPANY_DELETES.size()
                            + "，行数=" + out.get("totalRows") + "，回滚点=" + backup, companyId);
                    log.info("[审计] 清空公司数据演练完成：companyId={}, 表={}, 行={}, 回滚点={}",
                            companyId, COMPANY_DELETES.size(), out.get("totalRows"), backup);
                    return R.ok(out);
                }

                audit("清空公司数据", "开始：companyId=" + companyId + "，表数=" + COMPANY_DELETES.size()
                        + "，行数=" + out.get("totalRows") + "，回滚点=" + backup, companyId);
                log.warn("[审计] 清空公司数据开始：operator={}, companyId={}, 表={}, 行={}, 回滚点={}",
                        operatorName(), companyId, COMPANY_DELETES.size(), out.get("totalRows"), backup);

                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("SET FOREIGN_KEY_CHECKS = 0");
                }
                for (String sql : COMPANY_DELETES) {
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        bindCompany(ps, sql, companyId);
                        ps.executeUpdate();
                    }
                }
                // 重新初始化物料类型默认数据（统一用 DefaultMaterialTypes，避免与 DataInitializer 不一致）
                try (Statement stmt = conn.createStatement()) {
                    String[] defaultTypes = DefaultMaterialTypes.TYPES;
                    for (int i = 0; i < defaultTypes.length; i++) {
                        stmt.execute("INSERT INTO material_type (type_name, sort_order, status, is_default, company_id) VALUES ('"
                                + defaultTypes[i].replace("'", "''") + "', " + (i + 1) + ", 1, 1, " + companyId + ")");
                    }
                    // 重新初始化阶段模板默认数据（2026-09-21：**原配 / 改配各一套**，统一取 DefaultPhaseTemplates，
                    // 与 DataInitializer 共用一份数据，避免两处漂移；并补上原先漏写的 spec_type 与
                    // product_status_sync —— 漏后者会导致"清空数据"后小批量/结项不再触发产品状态同步）
                    int tplCount = 0;
                    tplCount += insertPhaseTemplates(stmt, DefaultPhaseTemplates.MODIFIED, DefaultPhaseTemplates.SPEC_MODIFIED, companyId);
                    tplCount += insertPhaseTemplates(stmt, DefaultPhaseTemplates.MATCHED, DefaultPhaseTemplates.SPEC_MATCHED, companyId);
                    log.info("重置阶段模板默认数据 {} 条（原配/改配各一套）", tplCount);
                    // 重新初始化默认合同模板（加工合同、采购合同），与 物料类型/阶段模板一致
                    insertContractTemplate(stmt, companyId, DefaultContractTemplate.TYPE_PROCESSING,
                            DefaultContractTemplate.NAME_PROCESSING, DefaultContractTemplate.PROCESSING_CONTRACT_HTML);
                    insertContractTemplate(stmt, companyId, DefaultContractTemplate.TYPE_PURCHASE,
                            DefaultContractTemplate.NAME_PURCHASE, DefaultContractTemplate.PURCHASE_CONTRACT_HTML);
                    stmt.execute("SET FOREIGN_KEY_CHECKS = 1");
                }
                conn.commit();
                audit("清空公司数据", "完成：companyId=" + companyId + "，表数=" + COMPANY_DELETES.size()
                        + "，行数=" + out.get("totalRows") + "，回滚点=" + backup, companyId);
                log.warn("[审计] 清空公司数据完成：operator={}, companyId={}, 回滚点={}", operatorName(), companyId, backup);
                return R.ok(out);
            } catch (Exception e) {
                // F8-17④：显式回滚，不再依赖"连接关闭时驱动隐式回滚"这一实现细节
                try {
                    conn.rollback();
                    log.warn("[审计] 清空公司数据失败，已回滚：{}", e.getMessage());
                } catch (Exception ignore) {
                    log.error("[审计] 回滚失败（数据可能不一致）：{}", ignore.getMessage());
                }
                audit("清空公司数据", "失败已回滚：" + e.getMessage(), companyId);
                return R.fail(e.getMessage());
            }
        } catch (Exception e) {
            log.error("[审计] 清空公司数据异常：{}", e.getMessage(), e);
            return R.fail(e.getMessage());
        }
    }

    // ------------------------------------------------------------------ 全库清空

    /**
     * 全库清空（P2-34：**仅超级管理员**）。方法级注解在类级放行范围之外再收紧 ——
     * Sa-Token 会同时校验类级与方法级注解，公司管理员虽满足类级（admin），仍会被此处拦下（403）。
     *
     * <p>F8-17/F8-18：补 {@code confirm} 强制确认、清空前**全库回滚点**、操作日志、显式回滚，
     * 保留表改为显式 {@link #PRESERVE_TABLES}，并加"待清表数异常即中止"兜底（防连错库）。</p>
     */
    @SaCheckRole(SystemConstants.SUPER_ADMIN_ROLE_CODE)
    @PostMapping("/api/system/clear-data")
    public R<?> clear(@RequestParam(value = "confirm", required = false) String confirm,
                      @RequestParam(value = "dryRun", required = false, defaultValue = "false") boolean dryRun) {
        if (!dryRun && !CONFIRM_ALL.equals(confirm)) {
            return R.fail("危险操作未确认：接口需带 confirm=" + CONFIRM_ALL);
        }
        log.warn("[审计] 全库清空{}开始：operator={}, companyId={}", dryRun ? "（演练）" : "", operatorName(), CompanyContext.get());
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                List<String> all = baseTables(conn);
                List<String> dels = new ArrayList<>();
                for (String t : all) {
                    if (!PRESERVE_TABLES.contains(t)) dels.add(t);
                }
                // 兜底：连错库/权限异常都会让表数异常偏小，此时宁可中止（导入侧的同类兜底见 SystemController）
                if (dels.size() < 20) {
                    throw new IllegalStateException("待清空表数异常（" + dels.size() + " 张，期望 ≥20）：已中止，请检查数据库连接");
                }

                // F8-17①：全库回滚点（含保留表；格式同「导出数据」）
                Map<String, Object> dump = new LinkedHashMap<>();
                Map<String, Object> counts = new LinkedHashMap<>();
                List<String> failed = new ArrayList<>();
                for (String t : all) {
                    try {
                        List<Map<String, Object>> rows = queryRows(conn, "SELECT * FROM `" + t + "`", null);
                        dump.put(t, rows);
                        counts.put(t, rows.size());
                    } catch (Exception e) {
                        failed.add(t);
                        log.warn("[审计] 全库回滚点读取表 {} 失败：{}", t, e.getMessage());
                    }
                }
                if (!failed.isEmpty()) {
                    throw new IllegalStateException("回滚点不完整（" + failed.size() + " 张表读取失败）：" + failed + "，已中止清空");
                }
                String backup = writeBackup("PRE_CLEAR_ALL_ROLLBACK", dump, null);

                Map<String, Object> out = new LinkedHashMap<>();
                out.put("dryRun", dryRun);
                out.put("tables", dels.size());
                out.put("preserved", new ArrayList<>(PRESERVE_TABLES));
                out.put("rows", counts);
                out.put("backup", backup);
                out.put("note", "清空后请重启后端以重新初始化（含建号/菜单/默认数据）");

                if (dryRun) {
                    conn.rollback();
                    audit("全库清空", "演练（未删数据）：待清 " + dels.size() + " 张表，回滚点=" + backup, CompanyContext.get());
                    log.info("[审计] 全库清空演练完成：待清 {} 张表，回滚点={}", dels.size(), backup);
                    return R.ok(out);
                }

                audit("全库清空", "开始：表数=" + dels.size() + "，回滚点=" + backup, CompanyContext.get());
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("SET FOREIGN_KEY_CHECKS = 0");
                    for (String t : dels) {
                        stmt.execute("DELETE FROM `" + t + "`");
                    }
                    stmt.execute("SET FOREIGN_KEY_CHECKS = 1");
                }
                conn.commit();
                // 注意：sys_operation_log 本身也在待清清单里 ⇒ 上面那条"开始"日志会被本次清空一并删掉，
                // 这是"恢复出厂"语义的必然结果；留存证据以 log 文件与回滚点为准（见报告 F8-17）。
                log.warn("[审计] 全库清空完成：operator={}, tables={}, 回滚点={}", operatorName(), dels.size(), backup);
                return R.ok(out);
            } catch (Exception e) {
                try {
                    conn.rollback();
                    log.warn("[审计] 全库清空失败，已回滚：{}", e.getMessage());
                } catch (Exception ignore) {
                    log.error("[审计] 回滚失败（数据可能不一致）：{}", ignore.getMessage());
                }
                audit("全库清空", "失败已回滚：" + e.getMessage(), CompanyContext.get());
                return R.fail(e.getMessage());
            }
        } catch (Exception e) {
            log.error("[审计] 全库清空异常：{}", e.getMessage(), e);
            return R.fail(e.getMessage());
        }
    }

    // ------------------------------------------------------------------ 助手

    /** 当前库所有 BASE TABLE（排除视图：视图无法 DELETE，且 outsource_order_material 已是只读视图）。 */
    private List<String> baseTables(Connection conn) throws Exception {
        List<String> out = new ArrayList<>();
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT table_name FROM information_schema.tables "
                    + "WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE' ORDER BY table_name");
            while (rs.next()) {
                out.add(rs.getString(1));
            }
            rs.close();
        }
        return out;
    }

    /** 把 SQL 里的每个 {@code ?} 都绑定为 companyId（清单里最多出现两次——子查询形式）。 */
    private void bindCompany(PreparedStatement ps, String sql, Long companyId) throws Exception {
        int n = 0;
        for (int i = 0; i < sql.length(); i++) {
            if (sql.charAt(i) == '?') {
                n++;
            }
        }
        for (int i = 1; i <= n; i++) {
            ps.setLong(i, companyId);
        }
    }

    /** 执行 SELECT 并把结果读成"列名 → 值"的行列表（值为 null 时写 null，导入侧可识别）。 */
    private List<Map<String, Object>> queryRows(Connection conn, String sql, Long companyId) throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (companyId != null) {
                bindCompany(ps, sql, companyId);
            }
            ResultSet rs = ps.executeQuery();
            int colCount = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= colCount; i++) {
                    Object val = rs.getObject(i);
                    // 与 SystemController 导出保持同一口径：TINYINT(1)/BIT 的 Boolean 统一转 1/0
                    if (val instanceof Boolean) {
                        val = (Boolean) val ? 1 : 0;
                    }
                    row.put(rs.getMetaData().getColumnName(i), val);
                }
                rows.add(row);
            }
            rs.close();
        }
        return rows;
    }

    /**
     * 落回滚点文件：{@code <upload>/clear-backup/<kind>-<yyyyMMdd-HHmmss>.json}，
     * 结构与「导出数据」完全一致（{@code {exportInfo, tables}}）⇒ 可直接用「数据导入」原样恢复。
     */
    private String writeBackup(String kind, Map<String, Object> tables, Long companyId) throws Exception {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("time", LocalDateTime.now().format(TS));
        info.put("kind", kind);
        info.put("companyId", companyId);
        info.put("operator", operatorName());
        info.put("tableCount", tables.size());
        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("exportInfo", info);
        wrapper.put("tables", tables);

        String dirName = "PRE_CLEAR_ALL_ROLLBACK".equals(kind) ? "clear-backup/all" : "clear-backup/company-" + companyId;
        Path dir = Paths.get(uploadPath, dirName);
        Files.createDirectories(dir);
        Path file = dir.resolve(kind.toLowerCase() + "-" + LocalDateTime.now().format(FILE_TS) + ".json");
        Files.write(file, objectMapper.writeValueAsBytes(wrapper));
        return file.toAbsolutePath().toString();
    }

    /** 写操作日志（F8-05 落地：三条不可逆路径至少要有库内留痕；失败只 WARN，不打断主流程）。 */
    private void audit(String operation, String detail, Long companyId) {
        try {
            OperationLog opLog = new OperationLog();
            Object uid = StpUtil.getLoginIdDefaultNull();
            if (uid != null) {
                try {
                    opLog.setUserId(Long.parseLong(String.valueOf(uid)));
                } catch (NumberFormatException ignore) {
                    // 非数字登录 id：只留 username
                }
            }
            opLog.setUsername(operatorName());
            opLog.setModule("系统");
            opLog.setOperation(operation);
            opLog.setDetail(detail != null && detail.length() > 1000 ? detail.substring(0, 1000) : detail);
            opLog.setCompanyId(companyId);
            opLog.setCreateTime(LocalDateTime.now());
            operationLogMapper.insert(opLog);
        } catch (Exception e) {
            log.warn("[审计] 写操作日志失败（不影响主流程）：{}", e.getMessage());
        }
    }

    private String operatorName() {
        Object uid = StpUtil.getLoginIdDefaultNull();
        return uid == null ? "unknown" : String.valueOf(uid);
    }

    /**
     * 插入一整套默认阶段模板（2026-09-21：原配 7 条 / 改配 14 条），返回插入条数。
     * <p>与 {@code DataInitializer} 共用 {@link DefaultPhaseTemplates} 常量；SQL 里**必须**带上
     * {@code spec_type} 与 {@code product_status_sync} —— 后者原先被漏掉，导致"清空数据"后
     * 小批量/结项不再触发产品状态同步（现网 14 条全 0 的成因）。</p>
     */
    private int insertPhaseTemplates(Statement stmt, DefaultPhaseTemplates.Row[] rows, String specType, Long companyId) throws Exception {
        for (DefaultPhaseTemplates.Row r : rows) {
            stmt.execute("INSERT INTO dev_phase_template (name, spec_type, default_days, sort_order, product_status_sync, remark, company_id) VALUES ('"
                    + r.name().replace("'", "''") + "', '" + specType + "', " + r.defaultDays() + ", "
                    + r.sortOrder() + ", " + r.productStatusSync() + ", '"
                    + r.remark().replace("'", "''") + "', " + companyId + ")");
        }
        return rows.length;
    }

    /** 插入一条默认合同模板（清空数据后重置用，与 DataInitializer 共用 DefaultContractTemplate 常量） */
    private void insertContractTemplate(Statement stmt, Long companyId, String type, String name, String content) throws Exception {
        String escapedName = name.replace("'", "''");
        String escapedContent = content.replace("'", "''");
        stmt.execute("INSERT INTO outsource_contract_template (template_name, content, template_type, status, is_default, company_id, create_time, update_time) VALUES ('"
                + escapedName + "', '" + escapedContent + "', '" + type + "', 1, 1, " + companyId + ", NOW(), NOW())");
    }
}
