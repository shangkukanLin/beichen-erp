package com.beichen.erp.dev.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.dev.entity.PhaseTemplate;

import java.util.List;

public interface PhaseTemplateService {

    Page<PhaseTemplate> page(int pageNum, int pageSize);

    List<PhaseTemplate> list();

    PhaseTemplate create(PhaseTemplate t);

    void update(PhaseTemplate t);

    void delete(Long id);
}
