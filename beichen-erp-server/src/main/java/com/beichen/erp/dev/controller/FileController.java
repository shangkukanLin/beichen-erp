package com.beichen.erp.dev.controller;

import com.beichen.erp.common.R;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@RestController
@RequestMapping("/api/dev/file")
public class FileController {

    private final Path uploadDir = Paths.get("/workspace/uploads");

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
    public ResponseEntity<Resource> download(@PathVariable String dateDir, @PathVariable String fileName) throws IOException {
        Path filePath = uploadDir.resolve(dateDir).resolve(fileName).normalize();
        if (!Files.exists(filePath)) return ResponseEntity.notFound().build();
        Resource resource = new FileSystemResource(filePath);
        String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName)
                .body(resource);
    }
}
