package com.beichen.erp.dev.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.beichen.erp.dev.entity.Bug;
import com.beichen.erp.dev.entity.dto.BugDTO;

import java.util.List;

public interface BugService extends IService<Bug> {

    List<Bug> listByProject(Long projectId);

    void create(Long projectId, BugDTO dto);

    void update(Long id, BugDTO dto);

    void delete(Long id);
}
