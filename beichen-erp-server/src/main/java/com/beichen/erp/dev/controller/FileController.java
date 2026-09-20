package com.beichen.erp.dev.controller;

import com.beichen.erp.common.R;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.dev.service.FileStorage;
import com.beichen.erp.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/dev/file")
@RequiredArgsConstructor
public class FileController {

    /** F7-95 遗留①收尾：目录解析与"按 URL 删文件"统一由 FileStorage 提供（DrawingController 也用） */
    private final FileStorage fileStorage;

    /** 附件单文件上限（F7-95）：与"全量导入 JSON"共用的全局 200MB 上限分开，避免任意登录用户反复上传大文件耗尽磁盘 */
    private static final long MAX_ATTACHMENT_BYTES = 20L * 1024 * 1024;

    /** 危险后缀（F7-95）：一律拒绝；白名单外的其它类型由下载侧的 octet-stream 兜底，不会被当页面渲染 */
    private static final java.util.Set<String> BLOCKED_EXTENSIONS = java.util.Set.of(
            ".jsp", ".jspx", ".jspf", ".php", ".asp", ".aspx", ".exe", ".dll", ".so", ".sh", ".bat",
            ".cmd", ".ps1", ".jar", ".war", ".class", ".html", ".htm", ".svg", ".xml");

    @PostMapping("/upload")
    public R<String> upload(@RequestParam("file") MultipartFile file) throws IOException {
        // F7-95（2026-09-19）三项加固：
        // ① 独立大小上限（原依赖全局 multipart 的 200MB）；
        // ② 危险后缀拒绝；
        // ③ 文件名清洗（只取最后一段、去控制字符）+ normalize + startsWith 校验 —— download 早就有
        //    该校验而 upload 没有（护栏不对称）。
        // 注：即便不清洗，UUID 前缀也会把首段污染成"随机名 + .."，使 ../ 逃逸在 OS 逐段解析下失败；
        // 但一旦将来去掉 UUID 前缀或支持子目录，就会立刻变成"可写任意路径"，所以这里必须补齐。
        if (file == null || file.isEmpty()) throw new BusinessException("上传文件为空");
        if (file.getSize() > MAX_ATTACHMENT_BYTES) {
            throw new BusinessException("附件不能超过 " + (MAX_ATTACHMENT_BYTES / 1024 / 1024) + "MB");
        }
        String safeName = sanitizeFileName(file.getOriginalFilename());
        String ext = "";
        int dot = safeName.lastIndexOf('.');
        if (dot >= 0) ext = safeName.substring(dot).toLowerCase();
        if (BLOCKED_EXTENSIONS.contains(ext)) {
            throw new BusinessException("不允许上传该类型文件：" + ext);
        }
        String dateDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String name = UUID.randomUUID().toString().substring(0, 8) + "_" + safeName;
        // F7-95 遗留①（2026-09-20）：**上传路径加公司维度** —— 原为 {dateDir}/{name}，文件名只有随机前缀、
        // 不含租户信息 ⇒ 任一登录用户凭 URL 即可下载别家附件。现为 company/{companyId}/{dateDir}/{name}，
        // 下载侧再校验"路径公司 == 当前上下文公司"。路径越界校验在 resolveInCompany 内（normalize + startsWith）。
        Long cid = fileStorage.currentCompanyKey();
        Path target = fileStorage.resolveInCompany(cid, dateDir, name);
        Files.createDirectories(target.getParent());
        file.transferTo(target.toFile());
        // 返回值即前端保存的附件 URL（含 company 段）；历史文件仍走旧路由，见下方兼容注释
        return R.ok("/api/dev/file/download/" + cid + "/" + dateDir + "/" + name);
    }

    /** 清洗原始文件名：只保留最后一段，去掉路径分隔符与控制字符（前端提交不可信） */
    private static String sanitizeFileName(String original) {
        String n = original == null ? "" : original;
        n = n.replace('\\', '/');
        int slash = n.lastIndexOf('/');
        if (slash >= 0) n = n.substring(slash + 1);
        n = n.replaceAll("[\\p{Cntrl}]", "").trim();
        if (n.isEmpty() || ".".equals(n) || "..".equals(n)) n = "file";
        return n;
    }

    /**
     * 下载（**新格式，含公司段**）。F7-95 遗留①：路径里的公司必须与当前上下文一致 ——
     * 否则任何租户只要猜到/拿到别家的 URL 就能下载（越权读）。超管（无租户上下文）不受限。
     */
    @GetMapping("/download/{companyId}/{dateDir}/{fileName}")
    public ResponseEntity<Resource> downloadScoped(@PathVariable Long companyId, @PathVariable String dateDir,
            @PathVariable String fileName, @RequestParam(defaultValue = "false") boolean inline) throws IOException {
        Long cid = CompanyContext.get();
        boolean superAdmin = (cid == null || cid <= 0);
        if (!superAdmin && !cid.equals(companyId)) {
            log.warn("拒绝跨租户下载：当前公司={} 路径公司={} 文件={}", cid, companyId, fileName);
            return ResponseEntity.status(403).build();
        }
        return serve(fileStorage.resolveInCompany(companyId, dateDir, fileName), fileName, inline);
    }

    /**
     * 下载（**历史格式，无公司段**）。
     *
     * <p>保留用于**兼容旧数据**：2026-09-20 之前上传的文件 URL 形如
     * {@code /api/dev/file/download/{dateDir}/{name}}，改路径规则会使这些 URL 全部 404
     * （附件散落在各业务单据的 attachUrl/fileUrl 字段里，无法一次性迁移）。
     * **新上传一律走带公司段的路由**，本路由只服务历史文件。</p>
     */
    @GetMapping("/download/{dateDir}/{fileName}")
    public ResponseEntity<Resource> download(@PathVariable String dateDir, @PathVariable String fileName,
            @RequestParam(defaultValue = "false") boolean inline) throws IOException {
        return serve(fileStorage.resolveLegacy(dateDir, fileName), fileName, inline);
    }

    /** 共同的响应组装：存在性 + 后缀→MIME 映射 + 下载文件名编码 */
    private ResponseEntity<Resource> serve(Path filePath, String fileName, boolean inline) {
        if (!Files.exists(filePath)) return ResponseEntity.notFound().build();
        Resource resource = new FileSystemResource(filePath);
        String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        String disposition = inline ? "inline" : "attachment";
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) mediaType = MediaType.APPLICATION_PDF;
        else if (lower.endsWith(".png")) mediaType = MediaType.IMAGE_PNG;
        else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) mediaType = MediaType.IMAGE_JPEG;
        else if (lower.endsWith(".gif")) mediaType = MediaType.IMAGE_GIF;
        else if (lower.endsWith(".txt")) mediaType = MediaType.TEXT_PLAIN;
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition + "; filename*=UTF-8''" + encodedName)
                .body(resource);
    }
}
