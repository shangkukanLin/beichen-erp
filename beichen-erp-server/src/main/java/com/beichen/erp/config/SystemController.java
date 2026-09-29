package com.beichen.erp.config;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import com.beichen.erp.common.R;
import com.beichen.erp.system.common.SystemConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 数据导出/导入：导出含 sys_user 密码哈希等敏感数据，导入会重建全库，均为高危运维操作。
 *
 * <p>【P2-34 口径 · 2026-09-12 定稿：整库导入/导出**仅超级管理员**】</p>
 * <p>这两个接口的 SQL 没有任何 company 维度（导出 `SELECT * FROM 每张表`；导入先 `DELETE FROM 每张表`
 * 再重建），是**平台级**能力而非租户内操作 —— 公司管理员是本租户最高权限，不应越出租户边界，
 * 且导入**不可逆**（误用即毁掉所有租户数据）。故由 {@code SUPER_ADMIN} 独享，并加审计日志留痕。
 * 公司级操作 `/system/clear-company-data`（按 {@code CompanyContext} 过滤）仍保留给公司管理员，
 * 见 {@code com.beichen.erp.config.ClearController}。</p>
 *
 * <p>【2026-09-18 五项加固（用户要求，见报告 §7.18）】</p>
 * <ol>
 *   <li><b>兼容 R 包装</b>：导入既认 UI 下载的内层 {@code {exportInfo,tables}}，也认直接用接口/curl
 *       抓到的原始响应 {@code {code,msg,data:{exportInfo,tables}}}（此前后者会被拒，用户以为备份不可用）</li>
 *   <li><b>口径区分</b>：导入响应给出 `backupTables`（备份里的表数）/`dataTables`（真正写入数据的表）/
 *       `emptyTables`（备份中为空的表），不再让"93 张表"看上去像丢了 17 张</li>
 *   <li><b>失败表可见</b>：导出时读失败的表不再静默跳过，记入 `exportInfo.failedTables` + WARN 日志</li>
 *   <li><b>清表二次确认</b>：导入会清空"备份里没有的当前表"；预检接口先给出清单，实际导入必须带
 *       `confirmMissingTables=true`，否则拒绝（保护"用旧备份恢复新库"的场景）</li>
 *   <li><b>响应带 Content-Length</b>：不再走 chunked（无长度）响应 —— PowerShell 5.1 的
 *       `Invoke-WebRequest -OutFile` 下载无长度的较大响应会中途报"连接被强制关闭"</li>
 * </ol>
 */
@Slf4j
@RestController
@RequestMapping("/api/system")
@SaCheckRole(SystemConstants.SUPER_ADMIN_ROLE_CODE)
public class SystemController {

    @Autowired private DataSource dataSource;

    /**
     * F8-05（2026-09-30 审核批 D 修复）：整库导入也是**不可逆路径**，除 log 文件外再落一条
     * {@code sys_operation_log}（该表此前只有读、无写入方 ⇒ "操作日志"页永远为空）。
     * 注意：整库导入会清空 sys_operation_log 本身（它是全库替换），故本次留痕只在**失败/中止**时可查，
     * 成功场景以 log 文件与导入前回滚点为准 —— 口径已记入报告 F8-17。
     */
    @Autowired private com.beichen.erp.system.mapper.OperationLogMapper operationLogMapper;

    /**
     * 必须用 **Spring 容器里配置好的 ObjectMapper**（含 JavaTimeModule 等模块）。
     * <p>⚠️ 不要 `new ObjectMapper()`：裸实例没有 JavaTimeModule，序列化 JDBC 取出的
     * {@code LocalDateTime/Timestamp} 会抛 InvalidDefinitionException（2026-09-18 实测，
     * 表现为导出返回"响应序列化失败"的 42 字节兜底体）。</p>
     */
    @Autowired private ObjectMapper objectMapper;

    /**
     * 导入前自动备份（回滚点）的落盘根目录（F5-1，2026-09-18 审核修复）。
     * <p>与文件上传同根；生产请把该目录随 {@code UPLOAD_PATH} 一并纳入备份策略（见《上线运维手册》§3.1）。</p>
     */
    @Value("${file.upload.path:./uploads}")
    private String uploadPath;

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** 回滚点文件名用（不含冒号，便于 Windows 文件系统） */
    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /** 二次确认缺失表时返回的语义码（前端拦截器按"非 200 即失败 + 弹 msg"处理） */
    private static final int CODE_CONFIRM_REQUIRED = 409;

    /**
     * 统一出口：把 {@link R} 序列化成**带 Content-Length** 的 JSON 响应。
     * <p>⚠️ 不要直接返回 {@code R} —— Spring 默认对未知长度的响应走 chunked（无 Content-Length），
     * PowerShell 5.1 的 {@code Invoke-WebRequest -OutFile} 下载这种较大响应会报
     * "An existing connection was forcibly closed by the remote host"（2026-09-18 实测，1.8MB 必现）。</p>
     */
    private ResponseEntity<byte[]> json(R<?> r) {
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(r);
        } catch (Exception e) {
            log.warn("响应序列化失败: {}", e.getMessage());
            body = "{\"code\":500,\"msg\":\"响应序列化失败\"}".getBytes(StandardCharsets.UTF_8);
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .contentLength(body.length)
                .body(body);
    }

    /**
     * 读取全库所有 BASE TABLE 的数据（导出自用 + 导入前回滚点复用，避免两处口径漂移）。
     *
     * @param failedTables 出参：读取失败的表（加固 #3：不得静默跳过）
     */
    private Map<String, Object> readAllTables(Statement stmt, List<String> failedTables) throws SQLException {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String table : baseTables(stmt)) {
            try {
                List<Map<String, Object>> rows = new ArrayList<>();
                ResultSet dataRs = stmt.executeQuery("SELECT * FROM " + wrap(table));
                int colCount = dataRs.getMetaData().getColumnCount();
                while (dataRs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= colCount; i++) {
                        String colName = dataRs.getMetaData().getColumnName(i);
                        Object val = dataRs.getObject(i);
                        // JDBC 对 TINYINT(1)/BIT 返回 Boolean，统一转 1/0，
                        // 否则导入时 quoteVal 会生成 'true'/'false' 字符串导致整型列报错
                        if (val instanceof Boolean) val = (Boolean) val ? 1 : 0;
                        row.put(colName, val);
                    }
                    rows.add(row);
                }
                dataRs.close();
                result.put(table, rows);
            } catch (Exception e) {
                // 加固 #3：读失败的表**不得静默跳过** —— 否则备份"少了几张表"无人察觉，
                // 恢复时才发现丢数据。这里记入 failedTables 并打 WARN（前端会提示用户）。
                failedTables.add(table);
                log.warn("读取表 {} 失败：{}", table, e.getMessage());
            }
        }
        return result;
    }

    /**
     * 导入前把**当前库**导出为回滚点文件（F5-1，2026-09-18 审核修复）。
     * <p>整库导入会清空全部表且**不可逆**，故落库前先落一份当前数据：
     * {@code <upload>/import-backup/pre-import-<yyyyMMdd-HHmmss>.json}。格式与「导出数据」完全一致，
     * 万一导入效果不符预期，可直接用「数据导入」把该文件原样恢复回去。任一步失败由调用方**中止导入**
     * （此时尚未改动任何数据），避免"想回滚却发现没有回滚点"。</p>
     */
    private String writePreImportBackup(Statement stmt) throws Exception {
        List<String> failed = new ArrayList<>();
        Map<String, Object> tables = readAllTables(stmt, failed);
        if (tables.isEmpty()) {
            throw new IllegalStateException("当前库未读到任何表（连接或权限异常），拒绝在无回滚点的情况下导入");
        }
        if (!failed.isEmpty()) {
            log.warn("[审计] 导入前回滚点有 {} 张表读取失败（该文件不完整）：{}", failed.size(), failed);
        }
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("time", LocalDateTime.now().format(TS));
        info.put("kind", "PRE_IMPORT_ROLLBACK");
        info.put("tableCount", tables.size());
        info.put("failedTables", failed);
        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("exportInfo", info);
        wrapper.put("tables", tables);

        Path dir = Paths.get(uploadPath, "import-backup");
        Files.createDirectories(dir);
        Path file = dir.resolve("pre-import-" + LocalDateTime.now().format(FILE_TS) + ".json");
        Files.write(file, objectMapper.writeValueAsBytes(wrapper));
        return file.toAbsolutePath().toString();
    }

    /** 导出全量数据（失败表清单见 exportInfo.failedTables） */
    @GetMapping("/export-data")
    public ResponseEntity<byte[]> exportData() {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> exportInfo = new LinkedHashMap<>();
        List<String> failedTables = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            List<String> tableNames = baseTables(stmt);
            // 遍历每张表查询数据（与"导入前回滚点"共用同一实现，见 readAllTables）
            result = readAllTables(stmt, failedTables);
            int totalRecords = result.values().stream()
                    .mapToInt(v -> v instanceof List ? ((List<?>) v).size() : 0).sum();

            exportInfo.put("time", LocalDateTime.now().format(TS));
            exportInfo.put("tableCount", result.size());
            exportInfo.put("dbTableCount", tableNames.size());
            exportInfo.put("recordCount", totalRecords);
            exportInfo.put("failedTables", failedTables);
            // P2-34：高危操作留痕（导出含所有公司的数据 + sys_user 密码哈希）
            log.warn("[审计] 整库导出：operator={}, companyId={}, tables={}/{}, records={}, failed={}",
                    StpUtil.getLoginIdDefaultNull(), CompanyContext.get(), result.size(), tableNames.size(),
                    totalRecords, failedTables.size());
            if (!failedTables.isEmpty()) {
                log.warn("[审计] 整库导出存在 {} 张表读取失败：{}", failedTables.size(), failedTables);
            }

        } catch (Exception e) {
            return json(R.fail("导出失败: " + e.getMessage()));
        }

        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("exportInfo", exportInfo);
        wrapper.put("tables", result);
        return json(R.ok(wrapper));
    }

    /**
     * 导入预检（**只读**，不改任何数据）：给前端"预览 + 二次确认"提供依据。
     * <p>返回：备份时间/备份表数/有数据的表数/空表清单/备份记录数、当前库表数、
     * **tablesNotInBackup（导入后会被清空的表）**、tablesNotInDb（备份里有、当前库没有，将跳过）。</p>
     */
    @PostMapping("/import-data/precheck")
    public ResponseEntity<byte[]> importPrecheck(@RequestParam("file") MultipartFile file) {
        try {
            Map<String, Object> parsed = parseBackup(file);
            Map<String, Object> tables = asMap(parsed.get("tables"));
            if (tables == null) {
                return json(R.fail("JSON 格式无效：缺少 tables 字段"));
            }
            Map<String, Object> resp = backupSummary(tables, asMap(parsed.get("exportInfo")));
            resp.put("unwrapped", parsed.get("unwrapped"));
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                List<String> dbTables = baseTables(stmt);
                List<String> notInBackup = new ArrayList<>();
                for (String t : dbTables) {
                    if (!tables.containsKey(t)) notInBackup.add(t);
                }
                List<String> notInDb = new ArrayList<>();
                for (String t : tables.keySet()) {
                    if (!dbTables.contains(t)) notInDb.add(t);
                }
                resp.put("dbTables", dbTables.size());
                resp.put("tablesNotInBackup", notInBackup);
                resp.put("tablesNotInDb", notInDb);
            }
            return json(R.ok(resp));
        } catch (Exception e) {
            return json(R.fail("预检失败: " + e.getMessage()));
        }
    }

    /**
     * 导入全量数据。
     * <p>语义：用备份**完全替换**当前库（先清空全部表再按备份插入）。</p>
     * <p>加固 #4：备份里没有的当前表会被清空 —— 若存在这类表且未传
     * {@code confirmMissingTables=true}，直接拒绝（不删任何数据），要求先在前端预览确认。</p>
     *
     * @param confirmMissingTables 已确认"允许清空备份中缺失的当前表"
     */
    @PostMapping("/import-data")
    public ResponseEntity<byte[]> importData(@RequestParam("file") MultipartFile file,
                                             @RequestParam(value = "confirmMissingTables", required = false)
                                             Boolean confirmMissingTables) {
        // P2-34：高危操作留痕（进入即记录，便于事后追溯"谁在何时用哪份备份重建了库"）
        log.warn("[审计] 整库导入开始：operator={}, companyId={}, file={}, size={}, confirmMissingTables={}",
                StpUtil.getLoginIdDefaultNull(), CompanyContext.get(),
                file != null ? file.getOriginalFilename() : null, file != null ? file.getSize() : 0L,
                confirmMissingTables);
        // F8-05（2026-09-30 批 D 修复）：除 log 文件外再落一条库内操作日志（"操作日志"页此前永远为空）
        try {
            com.beichen.erp.system.entity.OperationLog opLog = new com.beichen.erp.system.entity.OperationLog();
            opLog.setUsername(String.valueOf(StpUtil.getLoginIdDefaultNull()));
            opLog.setModule("系统");
            opLog.setOperation("整库导入");
            opLog.setTarget(file != null ? file.getOriginalFilename() : null);
            opLog.setDetail("开始：confirmMissingTables=" + confirmMissingTables
                    + "，companyId=" + CompanyContext.get());
            opLog.setCompanyId(CompanyContext.get());
            opLog.setCreateTime(LocalDateTime.now());
            operationLogMapper.insert(opLog);
        } catch (Exception ignore) {
            log.warn("[审计] 写操作日志失败（不影响主流程）：{}", ignore.getMessage());
        }
        try {
            // 解析 JSON（加固 #1：兼容 R 包装）
            Map<String, Object> parsed = parseBackup(file);
            Map<String, Object> tables = asMap(parsed.get("tables"));
            if (tables == null) {
                return json(R.fail("JSON 格式无效：缺少 tables 字段"));
            }
            Map<String, Object> exportInfo = asMap(parsed.get("exportInfo"));
            if (exportInfo == null) exportInfo = new HashMap<>();
            if (Boolean.TRUE.equals(parsed.get("unwrapped"))) {
                log.warn("导入：检测到 R 包装（data.tables），已自动解包 —— 兼容 curl/接口抓取的原始响应");
            }

            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                Statement stmt = conn.createStatement();

                // 语义：导入 = 用备份完全替换当前库。
                // 动态获取全库 BASE TABLE（与导出对称，避免硬编码表清单过时导致漏清/漏导），
                // 禁用外键 + 显式主键插入，删除/插入顺序无关紧要。
                List<String> allTables = baseTables(stmt);

                // 加固 #4：备份缺表 = 导入后会清空这些表 —— 必须先经预览确认，否则拒绝（此刻尚未改动任何数据）
                List<String> notInBackup = new ArrayList<>();
                for (String t : allTables) {
                    if (!tables.containsKey(t)) notInBackup.add(t);
                }
                if (!notInBackup.isEmpty() && !Boolean.TRUE.equals(confirmMissingTables)) {
                    stmt.close();
                    String preview = String.join(", ", notInBackup.subList(0, Math.min(10, notInBackup.size())));
                    return json(R.fail(CODE_CONFIRM_REQUIRED,
                            "备份缺少当前库的 " + notInBackup.size() + " 张表，导入会清空它们（"
                                    + preview + (notInBackup.size() > 10 ? " 等" : "")
                                    + "）。请先在「数据导入」预览中确认后再导入。"));
                }

                // 加固 #6（F5-1，2026-09-18 审核修复）：导入**不可逆**，落库前先自动把"当前库"导出为回滚点。
                // 备份失败即中止导入（此刻尚未改动任何数据），避免"想恢复却发现没有回滚点"。
                String rollbackPath;
                try {
                    rollbackPath = writePreImportBackup(stmt);
                    log.warn("[审计] 导入前已自动备份当前库（回滚点）：{}", rollbackPath);
                } catch (Exception e) {
                    stmt.close();
                    return json(R.fail("导入前自动备份失败，已中止导入（未改动任何数据）：" + e.getMessage()));
                }

                // 禁用外键检查
                stmt.execute("SET FOREIGN_KEY_CHECKS = 0");

                // 全库清空（含备份文件里没有的表，避免残留脏数据与备份不一致）
                for (String table : allTables) {
                    stmt.executeUpdate("DELETE FROM " + wrap(table));
                }

                // 按备份文件顺序插入
                List<String> insertOrder = new ArrayList<>(tables.keySet());

                Map<String, Object> result = new LinkedHashMap<>();
                List<String> dataTables = new ArrayList<>();
                List<String> skippedNotInDb = new ArrayList<>();
                List<String> emptyTables = new ArrayList<>();
                int totalInserted = 0;
                for (String table : insertOrder) {
                    if (!allTables.contains(table)) {
                        skippedNotInDb.add(table);
                        result.put(table, "跳过: 当前库无此表（可能为旧版本备份）");
                        continue;
                    }
                    Object rowsObj = tables.get(table);
                    if (!(rowsObj instanceof List)) continue;
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> rows = (List<Map<String, Object>>) rowsObj;
                    if (rows.isEmpty()) {
                        emptyTables.add(table);
                        continue;
                    }

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
                            stmt.close();
                            return json(R.fail("导入 " + table + " 失败: " + e.getMessage()));
                        }
                    }
                    result.put(table, inserted);
                    dataTables.add(table);
                    totalInserted += inserted;
                }

                stmt.execute("SET FOREIGN_KEY_CHECKS = 1");
                stmt.close();
                conn.commit();

                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("exportTime", exportInfo.getOrDefault("time", "未知"));
                // 加固 #2：把"备份里的表 / 有数据的表 / 空表 / 被清空但备份没有的表"分开报，避免误读
                resp.put("backupTables", tables.size());
                resp.put("dataTables", dataTables.size());
                resp.put("emptyTables", emptyTables);
                resp.put("totalTables", result.size());
                resp.put("totalRecords", totalInserted);
                resp.put("clearedNotInBackup", notInBackup);
                resp.put("skippedNotInDb", skippedNotInDb);
                // 回滚点路径：前端提示用户"如需回退请用该文件重新导入"（F5-1）
                resp.put("preImportBackupPath", rollbackPath);
                resp.put("details", result);
                log.warn("[审计] 整库导入完成：operator={}, tables={}(写入 {}), records={}, 空表={}, 被清空但备份缺失={}, 回滚点={}",
                        StpUtil.getLoginIdDefaultNull(), result.size(), dataTables.size(), totalInserted,
                        emptyTables.size(), notInBackup.size(), rollbackPath);
                return json(R.ok(resp));

            } catch (Exception e) {
                // F8-05：**失败**场景的库内留痕最有价值 —— 成功的整库导入会把 sys_operation_log 一并替换掉
                try {
                    com.beichen.erp.system.entity.OperationLog opLog = new com.beichen.erp.system.entity.OperationLog();
                    opLog.setUsername(String.valueOf(StpUtil.getLoginIdDefaultNull()));
                    opLog.setModule("系统");
                    opLog.setOperation("整库导入");
                    opLog.setDetail(("失败已回滚：" + e.getMessage()).substring(0,
                            Math.min(1000, ("失败已回滚：" + e.getMessage()).length())));
                    opLog.setCompanyId(CompanyContext.get());
                    opLog.setCreateTime(LocalDateTime.now());
                    operationLogMapper.insert(opLog);
                } catch (Exception ignore) {
                    log.warn("[审计] 写操作日志失败（不影响主流程）：{}", ignore.getMessage());
                }
                return json(R.fail("导入失败: " + e.getMessage()));
            }

        } catch (Exception e) {
            return json(R.fail("导入失败: " + e.getMessage()));
        }
    }

    /**
     * 解析备份文件，返回 {@code {tables, exportInfo, unwrapped}}（加固 #1）。
     * <p>兼容两种形态：①UI 下载的文件 = 内层对象 {@code {exportInfo, tables}}（前端下载前已解包 R）；
     * ②直接用接口/curl 抓的原始响应 = R 包装 {@code {code,msg,data:{exportInfo,tables}}}。</p>
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseBackup(MultipartFile file) throws Exception {
        Map<String, Object> data = objectMapper.readValue(file.getInputStream(), Map.class);
        Map<String, Object> tables = asMap(data.get("tables"));
        Map<String, Object> exportInfo = asMap(data.get("exportInfo"));
        boolean unwrapped = false;
        if (tables == null && data.get("data") instanceof Map) {
            Map<String, Object> inner = asMap(data.get("data"));
            tables = asMap(inner.get("tables"));
            if (tables != null) {
                exportInfo = asMap(inner.get("exportInfo"));
                unwrapped = true;
            }
        }
        Map<String, Object> parsed = new LinkedHashMap<>();
        parsed.put("tables", tables);
        parsed.put("exportInfo", exportInfo == null ? new LinkedHashMap<String, Object>() : exportInfo);
        parsed.put("unwrapped", unwrapped);
        return parsed;
    }

    /** 备份概览：表数 / 有数据的表数 / 空表清单 / 记录数（预检与导入共用口径） */
    private Map<String, Object> backupSummary(Map<String, Object> tables, Map<String, Object> exportInfo) {
        List<String> emptyTables = new ArrayList<>();
        int records = 0;
        for (Map.Entry<String, Object> e : tables.entrySet()) {
            int n = (e.getValue() instanceof List) ? ((List<?>) e.getValue()).size() : 0;
            if (n == 0) emptyTables.add(e.getKey());
            records += n;
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("exportTime", (exportInfo == null ? null : exportInfo.getOrDefault("time", "未知")));
        resp.put("backupTables", tables.size());
        resp.put("tablesWithData", tables.size() - emptyTables.size());
        resp.put("emptyTables", emptyTables);
        resp.put("backupRecords", records);
        return resp;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object o) {
        if (o instanceof Map) return (Map<String, Object>) o;
        return null;
    }

    /** 全库 BASE TABLE（排除 flyway 版本表），与导出/导入两侧对称 */
    private List<String> baseTables(Statement stmt) throws SQLException {
        List<String> names = new ArrayList<>();
        ResultSet rs = stmt.executeQuery(
                "SELECT TABLE_NAME FROM information_schema.TABLES " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE' " +
                "AND TABLE_NAME NOT LIKE 'flyway%' ORDER BY TABLE_NAME");
        while (rs.next()) names.add(rs.getString("TABLE_NAME"));
        rs.close();
        return names;
    }

    private String wrap(String name) {
        return "`" + name.replace("`", "``") + "`";
    }

    private String quoteVal(Object val) {
        if (val == null) return "NULL";
        // 布尔 → 1/0（TINYINT 列）；数字直接原样输出，避免隐式转换歧义
        if (val instanceof Boolean) return (Boolean) val ? "1" : "0";
        if (val instanceof Number) return val.toString();
        // F5-2（2026-09-18 审核修复）：除反斜杠/单引号外，一并转义 NUL、换行、回车、Ctrl-Z ——
        // 这些控制字符若原样进入 SQL 文本会截断语句或造成隐式换行，属"不可达但零成本"的兜底。
        String s = val.toString()
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\u0000", "\\0")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\u001a", "\\Z");
        return "'" + s + "'";
    }
}
