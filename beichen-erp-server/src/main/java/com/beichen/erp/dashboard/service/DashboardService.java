package com.beichen.erp.dashboard.service;

import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.inventory.service.StockTakeService;
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
        Map<String, Object> take = new LinkedHashMap<>();
        try {
            List<Map<String, Object>> status = stockTakeService.takeStatus();
            long pendingWh = status.stream().filter(s -> !Boolean.TRUE.equals(s.get("taken"))).count();
            long overdueWh = status.stream().filter(s -> !Boolean.TRUE.equals(s.get("taken"))
                    && s.get("overdueDays") != null && ((Number) s.get("overdueDays")).intValue() > 0).count();
            take.put("pending", pendingWh);
            take.put("overdue", overdueWh);
            take.put("period", status.isEmpty() ? null : status.get(0).get("period"));
        } catch (Exception e) {
            log.warn("首页盘点看板统计失败: {}", e.getMessage());
            take.put("pending", 0); take.put("overdue", 0); take.put("period", null);
        }
        res.put("stockTake", take);

        // 3) 售后仓超期待整理：按该产品在售后仓最早的 PENDING 入库流水日期判定
        Map<String, Object> sort = new LinkedHashMap<>();
        sort.put("overdue", countOverduePendingSort(cid));
        res.put("returnSort", sort);

        // 4) 超期应收金额
        res.put("overdueReceivable", sumOverdueReceivable(cid));
        return res;
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
                    // 2026-09-14 修复：原为中文 '售后仓'，但该列存 code（AFTER_SALE），条件恒不成立 → 指标恒为 0
                    "  WHERE w.warehouse_type = '" + WarehouseType.AFTER_SALE.getCode() + "' AND ws.quality_type = 'PENDING' " +
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
