package com.beichen.erp.dev.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.beichen.erp.dev.entity.Drawing;
import com.beichen.erp.dev.mapper.DrawingMapper;
import com.beichen.erp.dev.service.DrawingService;
import com.beichen.erp.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DrawingServiceImpl extends ServiceImpl<DrawingMapper, Drawing> implements DrawingService {

    private final DrawingMapper drawingMapper;

    @Override
    public List<Drawing> listByProject(Long projectId) {
        LambdaQueryWrapper<Drawing> wrapper = new LambdaQueryWrapper<Drawing>()
                .eq(Drawing::getProjectId, projectId)
                .orderByDesc(Drawing::getId);
        return drawingMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addDrawing(Drawing drawing) {
        drawingMapper.insert(drawing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDrawing(Long id) {
        Drawing drawing = drawingMapper.selectById(id);
        if (drawing == null) {
            throw new BusinessException("图纸不存在");
        }
        drawingMapper.deleteById(id);
    }
}
