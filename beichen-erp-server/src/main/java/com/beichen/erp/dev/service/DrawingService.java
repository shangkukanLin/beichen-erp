package com.beichen.erp.dev.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.beichen.erp.dev.entity.Drawing;

import java.util.List;

public interface DrawingService extends IService<Drawing> {

    /** 获取项目图纸列表（按名称分组，版本号降序） */
    List<Drawing> listByProject(Long projectId);

    /** 上传图纸（版本自动递增） */
    Drawing upload(Drawing drawing);
}
