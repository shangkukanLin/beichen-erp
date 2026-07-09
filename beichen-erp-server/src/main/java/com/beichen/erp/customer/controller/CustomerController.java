package com.beichen.erp.customer.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.customer.entity.Customer;
import com.beichen.erp.customer.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory/customer")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping("/page")
    public R<Page<Customer>> page(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(customerService.page(name, code, status, pageNum, pageSize));
    }

    @GetMapping("/list")
    public R<?> list() {
        return R.ok(customerService.listAll());
    }

    @GetMapping("/{id}")
    public R<Customer> getById(@PathVariable Long id) {
        return R.ok(customerService.getById(id));
    }

    @PostMapping
    public R<Void> create(@RequestBody Customer customer) {
        customerService.create(customer);
        return R.ok();
    }

    @PutMapping
    public R<Void> update(@RequestBody Customer customer) {
        customerService.update(customer);
        return R.ok();
    }

    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        customerService.updateStatus(id, status);
        return R.ok();
    }
}
