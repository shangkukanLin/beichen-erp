package com.beichen.erp.dev.service;

import com.beichen.erp.config.CompanyContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 附件物理存储的**唯一入口**（F7-95 遗留项收尾 · 2026-09-20）。
 *
 * <p>把"上传根目录的解析"与"按 URL 删除物理文件"集中到一处，供：
 * <ul>
 *   <li>{@code FileController} —— 上传/下载（上传路径含**公司维度**，见下）；</li>
 *   <li>{@code DrawingController} —— 删除图纸记录时**一并删除物理文件**（原先只删记录，文件永久滞留 ⇒ 孤儿文件）。</li>
 * </ul>
 *
 * <p><b>租户隔离（F7-95 遗留①）</b>：上传路径由 {@code {dateDir}/{name}} 改为
 * {@code company/{companyId}/{dateDir}/{name}}，下载侧校验"路径里的公司 == 当前上下文公司"，
 * 避免任一租户凭 URL 拿到别家附件。**历史文件**（不含 company 段的旧 URL）仍走旧下载路由，保持可用。</p>
 */
@Slf4j
@Component
public class FileStorage {

    @Value("${file.upload.path:./uploads}")
    private String uploadPath;

    private Path root;

    @PostConstruct
    public void init() {
        Path p = Paths.get(uploadPath);
        if (!p.isAbsolute()) {
            p = Paths.get(System.getProperty("user.dir")).resolve(uploadPath);
        }
        this.root = p.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
            log.info("文件上传根目录: {}", this.root);
        } catch (IOException e) {
            log.error("无法创建上传目录: {}", e.getMessage());
        }
    }

    /** 上传根目录（绝对、已 normalize） */
    public Path root() {
        return root;
    }

    /**
     * 解析"公司目录 + 日期目录 + 文件名"为一个合法路径；任何越界（`..`）一律抛 {@link IllegalArgumentException}。
     * <p>调用方负责 {@code Files.createDirectories(path.getParent())}。</p>
     */
    public Path resolveInCompany(Long companyId, String dateDir, String fileName) {
        Path dir = root.resolve("company").resolve(String.valueOf(normalizeCompany(companyId))).resolve(dateDir);
        Path target = dir.resolve(fileName).normalize();
        if (!target.startsWith(root)) throw new IllegalArgumentException("非法文件路径");
        return target;
    }

    /** 解析"历史格式"（无公司段）的路径，用于兼容旧 URL */
    public Path resolveLegacy(String dateDir, String fileName) {
        Path target = root.resolve(dateDir).resolve(fileName).normalize();
        if (!target.startsWith(root)) throw new IllegalArgumentException("非法文件路径");
        return target;
    }

    /** 当前上下文公司：>0 用其值；无上下文/超管统一落到 {@code 0} 目录（仍可被超管下载） */
    public Long currentCompanyKey() {
        return normalizeCompany(CompanyContext.get());
    }

    private static Long normalizeCompany(Long cid) {
        return (cid != null && cid > 0) ? cid : 0L;
    }

    /**
     * 由下载 URL 反推物理文件并**删除**（F7-95 遗留②：删记录时清文件，避免孤儿）。
     *
     * <p>支持两种 URL：{@code /api/dev/file/download/{companyId}/{dateDir}/{name}}（新）与
     * {@code /api/dev/file/download/{dateDir}/{name}}（历史）。文件名先 URL 解码。
     * 文件不存在时返回 false（不抛错 —— 清理动作应是幂等的）。</p>
     */
    public boolean deleteByUrl(String url) {
        if (url == null || url.isBlank()) return false;
        String prefix = "/api/dev/file/download/";
        int i = url.indexOf(prefix);
        if (i < 0) return false;
        String tail = url.substring(i + prefix.length());
        int q = tail.indexOf('?');
        if (q >= 0) tail = tail.substring(0, q);
        String[] parts = tail.split("/");
        try {
            Path target;
            String name = URLDecoder.decode(parts[parts.length - 1], StandardCharsets.UTF_8);
            if (parts.length >= 3) {
                // 新格式：companyId/dateDir/fileName
                target = root.resolve("company").resolve(parts[parts.length - 3])
                        .resolve(parts[parts.length - 2]).resolve(name).normalize();
            } else if (parts.length == 2) {
                target = root.resolve(parts[0]).resolve(name).normalize();
            } else {
                return false;
            }
            if (!target.startsWith(root)) {
                log.warn("拒绝删除越界路径: {}", target);
                return false;
            }
            boolean ok = Files.deleteIfExists(target);
            if (ok) log.info("已删除物理文件: {}", target);
            return ok;
        } catch (IOException e) {
            log.warn("删除物理文件失败（url={}）：{}", url, e.getMessage());
            return false;
        }
    }
}
