package com.beichen.erp.dev.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.beichen.erp.dev.entity.Drawing;
import com.beichen.erp.dev.mapper.DrawingMapper;
import com.beichen.erp.dev.service.DrawingService;
import com.beichen.erp.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DrawingServiceImpl extends ServiceImpl<DrawingMapper, Drawing> implements DrawingService {

    @Override
    public List<Drawing> listByProject(Long projectId) {
        return baseMapper.selectList(
                new LambdaQueryWrapper<Drawing>()
                        .eq(Drawing::getProjectId, projectId)
                        .orderByAsc(Drawing::getDocName)
                        .orderByDesc(Drawing::getVersionCode));
    }

    @Override
    @Transactional
    public Drawing upload(Drawing drawing) {
        // F7-98（2026-09-19）：① 必填校验 —— 原先 docName/docType 都可空，缺名图纸会全部挤在
        // "null|null" 这一个分组里递加版本号；② 版本号生成加乐观重试 —— 并发上传同名文件原先会
        // 取到同一个 versionCode，配合 dev_drawing 的 uk_proj_doc_ver 唯一键，重试可把"写脏数据"
        // 变成"自动改正"。
        if (drawing.getProjectId() == null) throw new BusinessException("项目ID不能为空");
        if (drawing.getDocName() == null || drawing.getDocName().isBlank()) {
            throw new BusinessException("文档名称不能为空");
        }
        if (drawing.getDocType() == null || drawing.getDocType().isBlank()) {
            throw new BusinessException("文档类型不能为空");
        }
        drawing.setUploadTime(LocalDateTime.now());
        for (int attempt = 0; attempt < 3; attempt++) {
            // 自动计算版本号：同项目 + 同文档名 + 同类型 的最大版本号 + 1
            Integer maxVersion = getMaxVersion(drawing.getProjectId(), drawing.getDocName(), drawing.getDocType());
            drawing.setVersionCode(maxVersion != null ? maxVersion + 1 : 1);
            try {
                baseMapper.insert(drawing);
                return drawing;
            } catch (org.springframework.dao.DuplicateKeyException e) {
                drawing.setId(null);
                log.warn("图纸版本号并发冲突，重试第 {} 次: projectId={}, docName={}",
                        attempt + 1, drawing.getProjectId(), drawing.getDocName());
            }
        }
        throw new BusinessException("图纸版本号生成冲突，请重试");
    }

    private Integer getMaxVersion(Long projectId, String docName, String docType) {
        Drawing last = baseMapper.selectOne(
                new LambdaQueryWrapper<Drawing>()
                        .eq(Drawing::getProjectId, projectId)
                        .eq(Drawing::getDocName, docName)
                        .eq(Drawing::getDocType, docType)
                        .orderByDesc(Drawing::getVersionCode)
                        .last("LIMIT 1"));
        return last != null ? last.getVersionCode() : null;
    }
}
