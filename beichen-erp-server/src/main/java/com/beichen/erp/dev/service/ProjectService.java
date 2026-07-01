package com.beichen.erp.dev.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.beichen.erp.dev.entity.Project;
import com.beichen.erp.dev.entity.dto.ProjectDTO;
import com.beichen.erp.dev.entity.dto.ProjectQueryDTO;

public interface ProjectService extends IService<Project> {

    Page<Project> page(ProjectQueryDTO query);

    String generateCode();

    void create(ProjectDTO dto);

    void update(ProjectDTO dto);

    void delete(Long id);

    void updateStatus(Long id, String status);
}
