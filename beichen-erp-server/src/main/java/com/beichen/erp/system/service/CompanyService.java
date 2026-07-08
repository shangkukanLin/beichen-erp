package com.beichen.erp.system.service;

import com.beichen.erp.system.entity.Company;
import java.util.List;

public interface CompanyService {
    List<Company> listAll();
    Company getById(Long id);
    void create(Company company);
    void update(Company company);
    void delete(Long id);
}
