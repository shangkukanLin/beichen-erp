package com.beichen.erp.dev.controller;

import com.beichen.erp.common.R;
import com.beichen.erp.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/dev/file")
public class FileController {

    @Value("${file.upload.path:./uploads}")
    private String uploadPath;

    private Path uploadDir;

    @PostConstruct
    public void init() {
        // 解析路径：如果是相对路径，则相对于当前工作目录
        Path p = Paths.get(uploadPath);
        if (!p.isAbsolute()) {
            p = Paths.get(System.getProperty("user.dir")).resolve(uploadPath);
        }
        this.uploadDir = p.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadDir);
            log.info("文件上传目录: {}", this.uploadDir);
        } catch (IOException e) {
            log.error("无法创建上传目录: {}", e.getMessage());
        }
    }

    /** 附件单文件上限（F7-95）：与"全量导入 JSON"共用的全局 200MB 上限分开，避免任意登录用户反复上传大文件耗尽磁盘 */
    private static final long MAX_ATTACHMENT_BYTES = 20L * 1024 * 1024;

    /** 危险后缀（F7-95）：一律拒绝；白名单外的其它类型由下载侧的 octet-stream 兜底，不会被当页面渲染 */
    private static final java.util.Set<String> BLOCKED_EXTENSIONS = java.util.Set.of(
            ".jsp", ".jspx", ".jspf", ".php", ".asp", ".aspx", ".exe", ".dll", ".so", ".sh", ".bat",
            ".cmd", ".ps1", ".jar", ".war", ".class", ".html", ".htm", ".svg", ".xml");

    @PostMapping("/upload")
    public R<String> upload(@RequestParam("file") MultipartFile file) throws IOException {
        Files.createDirectories(uploadDir);
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
        Path dir = uploadDir.resolve(dateDir);
        Files.createDirectories(dir);
        String name = UUID.randomUUID().toString().substring(0, 8) + "_" + safeName;
        Path target = dir.resolve(name).normalize();
        if (!target.startsWith(uploadDir)) throw new BusinessException("非法文件路径");
        file.transferTo(target.toFile());
        return R.ok("/api/dev/file/download/" + dateDir + "/" + name);
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

    @GetMapping("/download/{dateDir}/{fileName}")
    public ResponseEntity<Resource> download(@PathVariable String dateDir, @PathVariable String fileName,
            @RequestParam(defaultValue = "false") boolean inline) throws IOException {
        Path filePath = uploadDir.resolve(dateDir).resolve(fileName).normalize();
        // 安全检查：防止路径穿越攻击
        if (!filePath.startsWith(uploadDir)) {
            return ResponseEntity.badRequest().build();
        }
        if (!Files.exists(filePath)) return ResponseEntity.notFound().build();
        Resource resource = new FileSystemResource(filePath);
        String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        String disposition = inline ? "inline" : "attachment";
        // 根据后缀设置正确的MIME类型
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
