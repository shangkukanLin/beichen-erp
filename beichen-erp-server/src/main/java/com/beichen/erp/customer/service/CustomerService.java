package com.beichen.erp.customer.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.customer.entity.Customer;

import java.util.List;

public interface CustomerService {

    Page<Customer> page(String name, String code, String status, int pageNum, int pageSize);

    Customer getById(Long id);

    void create(Customer customer);

    void update(Customer customer);

    void updateStatus(Long id, Integer status);

    List<Customer> listAll();
}
