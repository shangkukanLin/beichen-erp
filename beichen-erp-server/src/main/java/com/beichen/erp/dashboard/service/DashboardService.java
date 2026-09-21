package com.beichen.erp.dashboard.service;

import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.inventory.service.StockTakeService;
import com.beichen.erp.warehouse.common.WarehouseCategory;
import com.beichen.erp.warehouse.common.WarehouseType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 首页待办与预警统计。
 * <p>
 * 汇总各模块待审核单据数量 + 盘点待办/超期 + 售后仓超期待整理 + 超期应收，
 * 供首页「待办与预警」区块一次请求取全，避免前端分页接口拉全量统计。
 * 注意：JdbcTemplate 不经过多租户插件，必须手动带 company_id 条件。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    /** 售后仓待整理超期阈值（天），与 ReturnSortServiceImpl 保持一致 */
    private static final int STAY_ALERT_DAYS = 3;

    private final JdbcTemplate jdbcTemplate;
    private final StockTakeService stockTakeService;

    // 期 1（读隔离，2026-09-19）：首页原先前端直连各模块分页接口，属"跨页读"；改由本服务聚合
    private final com.beichen.erp.purchase.service.PurchaseOrderService purchaseOrderService;
    private final com.beichen.erp.purchase.service.PurchaseReturnService purchaseReturnService;
    private final com.beichen.erp.outsource.service.OutsourceOrderService outsourceOrderService;
    private final com.beichen.erp.outsource.service.MaterialOrderService materialOrderService;
    // 期 1b（2026-09-19）：研发项目/阶段、销售单总数、客户分析也一并下沉
    private final com.beichen.erp.dev.service.ProjectService projectService;
    private final com.beichen.erp.sale.service.SaleOrderService saleOrderService;
    private final com.beichen.erp.customer.service.CustomerAnalysisService customerAnalysisService;

    /**
     * 期 1（读隔离）：首页卡片所需的**跨模块只读**聚合，**按调用者 perms 过滤**。
     *
     * <p>背景：首页原先直接调 `/api/inventory/purchase/page`、`/api/outsource/order/page` 等模块接口
     * （前端用 `if (hasModule.x)` 已经按菜单门控，但静态上仍是"跨页读"，会阻碍这些前缀的读隔离）。
     * 现在由后端一次聚合；**缺对应页面码时该块不返回** ⇒ 前端自然不渲染，且权限判定在后端兜底
     * （前端即使被绕过也拿不到无权数据）。</p>
     *
     * <p>形状与各模块 `/page` 接口**完全一致**（`Page<Map<String,Object>>`），前端下游逻辑无需改动。</p>
     */
    public Map<String, Object> modulePages() {
        List<String> perms;
        try {
            perms = cn.dev33.satoken.stp.StpUtil.getPermissionList();
        } catch (Exception e) {
            perms = List.of();
        }
        Map<String, Object> res = new LinkedHashMap<>();
        try {
            if (perms.contains("purchase:order")) {
                res.put("purchaseOrder", purchaseOrderService.page(null, null, null, 1, 200));
            }
            if (perms.contains("purchase:return")) {
                res.put("purchaseReturn", purchaseReturnService.page(null, null, null, 1, 200));
            }
            if (perms.contains("outsource:order")) {
                res.put("outsourceOrder", outsourceOrderService.page(null, null, null, 1, 200));
            }
            if (perms.contains("outsource:material-order")) {
                res.put("materialOrder", materialOrderService.page(1, 200, null, null, null, null));
            }
            // 研发（期 1b）：项目分页 + 进行中项目（前 5）的阶段 —— 原前端要发 3 次请求
            // （pageSize=1 取 total、pageSize=200 取记录、batch-phases POST 取阶段）
            if (perms.contains("dev:project") || perms.contains("dev:material") || perms.contains("dev:screen-model")) {
                com.beichen.erp.common.PageParam pp = new com.beichen.erp.common.PageParam();
                pp.setPageNum(1);
                pp.setPageSize(200);
                var projPage = projectService.page(pp, null, null, null);
                Map<String, Object> dev = new LinkedHashMap<>();
                dev.put("projectPage", projPage);
                List<Long> activeIds = new ArrayList<>();
                if (projPage != null && projPage.getRecords() != null) {
                    // records 是 Project 实体（该接口直接序列化实体，前端读 p.id/p.status）
                    for (com.beichen.erp.dev.entity.Project r : projPage.getRecords()) {
                        // 与前端同口径：ProjectStatus.IN_PROGRESS 的**前 5 个**（列表顺序一致）
                        if ("IN_PROGRESS".equals(String.valueOf(r.getStatus())) && r.getId() != null) {
                            activeIds.add(r.getId());
                            if (activeIds.size() >= 5) break;
                        }
                    }
                }
                dev.put("phases", activeIds.isEmpty() ? Map.of() : projectService.batchPhases(activeIds));
                res.put("dev", dev);
            }
            // 销售（期 1b）：仅总单数（原前端拉 /inventory/sale/page?pageSize=1 只为取 total）
            if (perms.contains("sale:order")) {
                Map<String, Object> sale = new LinkedHashMap<>();
                sale.put("total", saleOrderService.page(null, null, null, null, null, 1, 1).getTotal());
                res.put("sale", sale);
            }
            // 客户分析（期 1b）：本月客户 TOP5 + 合计（原前端 /customer/analysis?preset=month）
            if (perms.contains("analysis:customer")) {
                res.put("customerAnalysis", customerAnalysisService.customer("month", null, null));
            }
        } catch (Exception e) {
            log.warn("首页模块聚合失败: {}", e.getMessage());
        }
        return res;
    }

    /** 待办与预警汇总 */
    public Map<String, Object> pending() {
        Long cid = CompanyContext.get();
        Map<String, Object> res = new LinkedHashMap<>();

        // 1) 各模块待审核单据数
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("saleOrder", count("sale_order", "DRAFT", cid));
        counts.put("saleReturn", count("sale_return", "DRAFT", cid));
        counts.put("purchaseOrder", count("purchase_order", "DRAFT", cid));
        counts.put("purchaseReturn", count("purchase_return", "DRAFT", cid));
        counts.put("outsourceOrder", count("outsource_order", "PENDING", cid));
        counts.put("materialOrder", count("outsource_material_order", "PENDING", cid));
        counts.put("warehouseMove", count("inventory_warehouse_move", "DRAFT", cid));
        counts.put("otherIo", count("inventory_other_io", "DRAFT", cid));
        counts.put("expense", count("finance_expense", "DRAFT", cid));
        counts.put("stockTake", count("inventory_stock_take", "DRAFT", cid));
        res.put("counts", counts);

        // 2) 盘点：本月待盘点/超期仓库数
        // 2026-09-16：按盘点范围分开 —— stockTake = 成品类仓库（成品/不良/售后仓）；materialTake = 物料类（委外仓 + 自有物料仓）
        Map<String, Object> take = new LinkedHashMap<>();
        try {
            take = takeStat("PRODUCT");
        } catch (Exception e) {
            log.warn("首页盘点看板统计失败: {}", e.getMessage());
            take.put("pending", 0); take.put("overdue", 0); take.put("period", null);
        }
        res.put("stockTake", take);
        Map<String, Object> materialTake = new LinkedHashMap<>();
        try {
            materialTake = takeStat("MATERIAL");
        } catch (Exception e) {
            log.warn("首页物料盘点看板统计失败: {}", e.getMessage());
            materialTake.put("pending", 0); materialTake.put("overdue", 0); materialTake.put("period", null);
        }
        res.put("materialTake", materialTake);

        // 3) 售后仓超期待整理：按该产品在售后仓最早的 PENDING 入库流水日期判定
        Map<String, Object> sort = new LinkedHashMap<>();
        sort.put("overdue", countOverduePendingSort(cid));
        res.put("returnSort", sort);

        // 4) 超期应收金额
        res.put("overdueReceivable", sumOverdueReceivable(cid));
        return res;
    }

    // ==================== 销售工作台（首页「销售业务」TAB，2026-09-15 新增） ====================

    /**
     * 盘点看板统计（按范围口径）：本月待盘点仓库数 / 其中超期数 / 当前盘点月份。
     *
     * @param scope PRODUCT=成品类仓库（成品/不良/售后仓）；MATERIAL=物料类（委外仓 + 自有物料仓）
     */
    private Map<String, Object> takeStat(String scope) {
        List<Map<String, Object>> status = stockTakeService.takeStatus(scope);
        long pendingWh = status.stream().filter(s -> !Boolean.TRUE.equals(s.get("taken"))).count();
        long overdueWh = status.stream().filter(s -> !Boolean.TRUE.equals(s.get("taken"))
                && s.get("overdueDays") != null && ((Number) s.get("overdueDays")).intValue() > 0).count();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pending", pendingWh);
        m.put("overdue", overdueWh);
        m.put("period", status.isEmpty() ? null : status.get(0).get("period"));
        return m;
    }

    /**
     * 销售工作台（2026-09-15 用户口径）：**当日单据量** 4 项。纯只读聚合。
     * 「当日」= 各单据的**建单日 `create_time`** 等于今天（2026-09-15 全站统一归期口径：
     * 由原「业务日期列 order_date/return_date/exchange_date/sort_date」改为**建单日**），
     * **排除已作废**；草稿与已审核都计（当天建单）。
     * 按用户要求：不做业绩数字、不做沉默客户卡、不做超期应收/售后仓超期卡、**不做出库情况提示**。
     */
    public Map<String, Object> saleWorkbench() {
        Long cid = CompanyContext.get();
        Map<String, Object> res = new LinkedHashMap<>();

        Map<String, Object> todos = new LinkedHashMap<>();
        todos.put("saleOrderToday", countToday("sale_order", "create_time", cid));
        todos.put("saleReturnToday", countToday("sale_return", "create_time", cid));
        todos.put("saleExchangeToday", countToday("sale_exchange", "create_time", cid));
        todos.put("returnSortToday", countToday("return_sort", "create_time", cid));
        res.put("todos", todos);
        return res;
    }

    /** 当日单据量：业务日期列 = 今天，排除已作废（表名/列名由调用方给定，均为代码常量，非外部输入） */
    private long countToday(String table, String dateCol, Long cid) {
        try {
            StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ").append(table)
                    .append(" WHERE ").append(dateCol).append(" = CURDATE() AND status <> 'CANCELLED'");
            List<Object> args = new ArrayList<>();
            if (cid != null && cid > 0) { sql.append(" AND company_id = ?"); args.add(cid); }
            Long n = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
            return n == null ? 0 : n;
        } catch (Exception e) {
            // 表/列不存在（旧库）时不影响首页
            return 0;
        }
    }

    // ==================== 私有统计 ====================

    private long count(String table, String status, Long cid) {
        try {
            StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ").append(table).append(" WHERE status = ?");
            List<Object> args = new ArrayList<>();
            args.add(status);
            if (cid != null && cid > 0) {
                sql.append(" AND company_id = ?");
                args.add(cid);
            }
            Long n = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
            return n == null ? 0 : n;
        } catch (Exception e) {
            // 表不存在（旧库未建表）时不影响首页
            return 0;
        }
    }

    /** 售后仓 PENDING 库存中，最早入库流水早于阈值天数的产品数 */
    private long countOverduePendingSort(Long cid) {
        try {
            StringBuilder sql = new StringBuilder(
                    "SELECT COUNT(*) FROM ( " +
                    "  SELECT ws.product_id, ( " +
                    "    SELECT MIN(l.create_time) FROM warehouse_stock_log l " +
                    "    WHERE l.warehouse_id = ws.warehouse_id AND l.product_id = ws.product_id " +
                    "      AND l.quality_type = ws.quality_type AND l.change_quantity > 0 " +
                    "  ) AS first_in " +
                    "  FROM warehouse_stock ws JOIN warehouse w ON w.id = ws.warehouse_id " +
                    // 2026-09-16 方案 A：仓型收敛为「成品仓/辅料仓」，不再有"售后仓" →
                    // 待整理=压在**自有仓**里的待整理品质库存（PENDING 只会出现在成品行，故天然排除辅料仓）
                    "  WHERE w.warehouse_category = '" + WarehouseCategory.INVENTORY.getCode() + "' AND ws.quality_type = 'PENDING' " +
                    "    AND ws.quantity > 0 AND ws.product_id IS NOT NULL");
            List<Object> args = new ArrayList<>();
            if (cid != null && cid > 0) {
                sql.append(" AND ws.company_id = ?");
                args.add(cid);
            }
            sql.append(" ) t WHERE t.first_in IS NOT NULL AND t.first_in < DATE_SUB(NOW(), INTERVAL ? DAY)");
            args.add(STAY_ALERT_DAYS);
            Long n = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
            return n == null ? 0 : n;
        } catch (Exception e) {
            log.warn("首页待整理超期统计失败: {}", e.getMessage());
            return 0;
        }
    }

    /** 超期未收金额（due_date 已过且未结清） */
    private BigDecimal sumOverdueReceivable(Long cid) {
        try {
            StringBuilder sql = new StringBuilder(
                    "SELECT IFNULL(SUM(unpaid_amount), 0) FROM finance_receivable " +
                    "WHERE status IN ('UNSETTLED', 'PARTIAL') AND due_date IS NOT NULL AND due_date < CURDATE()");
            List<Object> args = new ArrayList<>();
            if (cid != null && cid > 0) {
                sql.append(" AND company_id = ?");
                args.add(cid);
            }
            BigDecimal v = jdbcTemplate.queryForObject(sql.toString(), BigDecimal.class, args.toArray());
            return v == null ? BigDecimal.ZERO : v;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
