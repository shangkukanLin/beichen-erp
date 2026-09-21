package com.beichen.erp.dev.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.dev.entity.PhaseTemplate;

import java.util.List;

/**
 * 阶段模板服务
 * <p>2026-09-21：模板分「原配 / 改配」两套 ⇒ 查询接口带 {@code specType}；
 * **传空表示不过滤规格（返回两套）**，保持对旧调用方的向后兼容。</p>
 */
public interface PhaseTemplateService {

    Page<PhaseTemplate> page(int pageNum, int pageSize, String specType);

    List<PhaseTemplate> list(String specType);

    PhaseTemplate create(PhaseTemplate t);

    void update(PhaseTemplate t);

    void delete(Long id);
}
