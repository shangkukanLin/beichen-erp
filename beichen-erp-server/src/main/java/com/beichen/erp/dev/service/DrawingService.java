package com.beichen.erp.dev.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.beichen.erp.dev.entity.Drawing;

import java.util.List;

public interface DrawingService extends IService<Drawing> {

    List<Drawing> listByProject(Long projectId);

    void addDrawing(Drawing drawing);

    void deleteDrawing(Long id);
}
