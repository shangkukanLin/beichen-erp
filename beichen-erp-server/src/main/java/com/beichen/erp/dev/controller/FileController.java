package com.beichen.erp.dev.controller;

import com.beichen.erp.common.R;
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

    @PostMapping("/upload")
    public R<String> upload(@RequestParam("file") MultipartFile file) throws IOException {
        Files.createDirectories(uploadDir);
        String dateDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        Path dir = uploadDir.resolve(dateDir);
        Files.createDirectories(dir);
        String name = UUID.randomUUID().toString().substring(0, 8) + "_" + file.getOriginalFilename();
        Path target = dir.resolve(name);
        file.transferTo(target.toFile());
        return R.ok("/api/dev/file/download/" + dateDir + "/" + name);
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
