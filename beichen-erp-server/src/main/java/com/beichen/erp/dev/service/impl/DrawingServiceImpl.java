package com.beichen.erp.dev.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.beichen.erp.dev.entity.Drawing;
import com.beichen.erp.dev.mapper.DrawingMapper;
import com.beichen.erp.dev.service.DrawingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

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
    public Drawing upload(Drawing drawing) {
        // 自动计算版本号：同项目+同文档名+同类型 的最大版本号 + 1
        Integer maxVersion = getMaxVersion(drawing.getProjectId(), drawing.getDocName(), drawing.getDrawingType());
        drawing.setVersionCode(maxVersion != null ? maxVersion + 1 : 1);
        drawing.setUploadTime(LocalDateTime.now());
        baseMapper.insert(drawing);
        return drawing;
    }

    private Integer getMaxVersion(Long projectId, String docName, String drawingType) {
        Drawing last = baseMapper.selectOne(
                new LambdaQueryWrapper<Drawing>()
                        .eq(Drawing::getProjectId, projectId)
                        .eq(Drawing::getDocName, docName)
                        .eq(Drawing::getDrawingType, drawingType)
                        .orderByDesc(Drawing::getVersionCode)
                        .last("LIMIT 1"));
        return last != null ? last.getVersionCode() : null;
    }
}
