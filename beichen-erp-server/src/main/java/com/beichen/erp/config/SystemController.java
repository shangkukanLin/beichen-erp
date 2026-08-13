package com.beichen.erp.config;

import com.beichen.erp.common.R;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    @Autowired private DataSource dataSource;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 导出全量数据 */
    @GetMapping("/export-data")
    public R<Map<String, Object>> exportData() {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> exportInfo = new LinkedHashMap<>();
        List<String> tableNames = new ArrayList<>();
        int totalRecords = 0;

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // 获取所有表名（排除 flyway 版本表）
            ResultSet rs = stmt.executeQuery(
                "SELECT TABLE_NAME FROM information_schema.TABLES " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE' " +
                "AND TABLE_NAME NOT LIKE 'flyway%' ORDER BY TABLE_NAME");
            while (rs.next()) tableNames.add(rs.getString("TABLE_NAME"));
            rs.close();

            // 遍历每张表查询数据
            for (String table : tableNames) {
                try {
                    List<Map<String, Object>> rows = new ArrayList<>();
                    ResultSet dataRs = stmt.executeQuery("SELECT * FROM " + wrap(table));
                    int colCount = dataRs.getMetaData().getColumnCount();
                    while (dataRs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= colCount; i++) {
                            String colName = dataRs.getMetaData().getColumnName(i);
                            Object val = dataRs.getObject(i);
                            row.put(colName, val);
                        }
                        rows.add(row);
                    }
                    dataRs.close();
                    result.put(table, rows);
                    totalRecords += rows.size();
                } catch (Exception ignored) {
                    // 跳过无法查询的表
                }
            }

            exportInfo.put("time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            exportInfo.put("tableCount", result.size());
            exportInfo.put("recordCount", totalRecords);

        } catch (Exception e) {
            return R.fail("导出失败: " + e.getMessage());
        }

        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("exportInfo", exportInfo);
        wrapper.put("tables", result);
        return R.ok(wrapper);
    }

    /** 导入全量数据 */
    @PostMapping("/import-data")
    public R<Map<String, Object>> importData(@RequestParam("file") MultipartFile file) {
        try {
            // 解析 JSON
            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(file.getInputStream(), Map.class);
            Object tablesObj = data.get("tables");
            if (!(tablesObj instanceof Map)) return R.fail("JSON 格式无效：缺少 tables 字段");

            @SuppressWarnings("unchecked")
            Map<String, Object> tables = (Map<String, Object>) tablesObj;
            Object infoObj = data.get("exportInfo");
            Map<String, Object> exportInfo = (infoObj instanceof Map)
                ? (Map<String, Object>) infoObj : new HashMap<>();

            // 依赖顺序（先删依赖表，再删主表）
            List<String> deleteOrder = Arrays.asList(
                "outsource_delivery_item", "warehouse_stock",
                "outsource_order_material", "outsource_order_product", "outsource_order_delivery",
                "outsource_order", "outsource_material_component", "outsource_material",
                "material_order_item", "material_order",
                "purchase_inbound_item", "purchase_order_item",
                "sale_outbound_item", "sale_order_item",
                "warehouse_stock_log", "warehouse_stock",
                "warehouse_move_item", "inventory_other_io_item",
                "project_timeline", "bom", "project",
                "product", "brand", "supplier", "warehouse",
                "sys_role_menu", "sys_user_role",
                "sys_menu", "sys_role", "sys_user", "sys_config"
            );

            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                Statement stmt = conn.createStatement();

                // 禁用外键检查
                stmt.execute("SET FOREIGN_KEY_CHECKS = 0");

                // 按依赖倒序清空表
                for (String table : deleteOrder) {
                    try {
                        stmt.executeUpdate("DELETE FROM " + wrap(table));
                    } catch (Exception ignored) {}
                }
                // 清空不在 deleteOrder 中的表
                for (String table : tables.keySet()) {
                    if (!deleteOrder.contains(table)) {
                        try { stmt.executeUpdate("DELETE FROM " + wrap(table)); } catch (Exception ignored) {}
                    }
                }

                // 按依赖正序插入数据
                List<String> insertOrder = new ArrayList<>();
                for (int i = deleteOrder.size() - 1; i >= 0; i--) insertOrder.add(deleteOrder.get(i));
                for (String table : tables.keySet()) {
                    if (!insertOrder.contains(table)) insertOrder.add(table);
                }

                Map<String, Object> result = new LinkedHashMap<>();
                int totalInserted = 0;
                for (String table : insertOrder) {
                    Object rowsObj = tables.get(table);
                    if (!(rowsObj instanceof List)) continue;
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> rows = (List<Map<String, Object>>) rowsObj;
                    if (rows.isEmpty()) continue;

                    // 获取列名
                    Set<String> columns = new LinkedHashSet<>();
                    for (Map<String, Object> row : rows) columns.addAll(row.keySet());

                    // 构建 INSERT 语句
                    StringBuilder sql = new StringBuilder("INSERT INTO ").append(wrap(table)).append(" (");
                    int idx = 0;
                    List<String> colList = new ArrayList<>(columns);
                    for (String col : colList) {
                        if (idx++ > 0) sql.append(", ");
                        sql.append(wrap(col));
                    }
                    sql.append(") VALUES ");

                    int batchSize = 100;
                    int inserted = 0;
                    for (int i = 0; i < rows.size(); i += batchSize) {
                        int end = Math.min(i + batchSize, rows.size());
                        StringBuilder batchSql = new StringBuilder(sql.toString());
                        int rowIdx = 0;
                        for (int r = i; r < end; r++) {
                            if (rowIdx++ > 0) batchSql.append(", ");
                            batchSql.append("(");
                            for (int c = 0; c < colList.size(); c++) {
                                if (c > 0) batchSql.append(", ");
                                Object val = rows.get(r).get(colList.get(c));
                                batchSql.append(quoteVal(val));
                            }
                            batchSql.append(")");
                        }
                        try {
                            inserted += stmt.executeUpdate(batchSql.toString());
                        } catch (Exception e) {
                            result.put(table, "失败: " + e.getMessage());
                            try { conn.rollback(); } catch (Exception ignored) {}
                            return R.fail("导入 " + table + " 失败: " + e.getMessage());
                        }
                    }
                    result.put(table, inserted);
                    totalInserted += inserted;
                }

                stmt.execute("SET FOREIGN_KEY_CHECKS = 1");
                stmt.close();
                conn.commit();

                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("totalTables", result.size());
                resp.put("totalRecords", totalInserted);
                resp.put("exportTime", exportInfo.getOrDefault("time", "未知"));
                resp.put("details", result);
                return R.ok(resp);

            } catch (Exception e) {
                return R.fail("导入失败: " + e.getMessage());
            }

        } catch (Exception e) {
            return R.fail("导入失败: " + e.getMessage());
        }
    }

    private String wrap(String name) {
        return "`" + name.replace("`", "``") + "`";
    }

    private String quoteVal(Object val) {
        if (val == null) return "NULL";
        String s = val.toString().replace("\\", "\\\\").replace("'", "\\'");
        return "'" + s + "'";
    }
}
