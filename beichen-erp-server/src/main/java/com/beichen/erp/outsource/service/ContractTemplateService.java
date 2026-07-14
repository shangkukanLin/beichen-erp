package com.beichen.erp.outsource.service;

import com.beichen.erp.outsource.entity.ContractTemplate;
import java.util.List;

public interface ContractTemplateService {
    List<ContractTemplate> list(String templateType);
    ContractTemplate getById(Long id);
    ContractTemplate getDefault(String templateType);
    void save(ContractTemplate template);
    void update(ContractTemplate template);
    void delete(Long id);
    void setDefault(Long id);
}
