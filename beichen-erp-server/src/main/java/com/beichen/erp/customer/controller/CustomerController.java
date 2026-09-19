package com.beichen.erp.customer.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.customer.entity.Customer;
import com.beichen.erp.customer.service.CustomerService;
import com.beichen.erp.sale.service.SaleOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/inventory/customer")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;
    // 期 2（2026-09-19 读隔离）：客户详情页的「销售单」页签改由本页接口提供
    private final SaleOrderService saleOrderService;

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

    /**
     * 该客户的销售单分页（客户详情页「销售单」页签；期 2·2026-09-19 读隔离）。
     *
     * <p>原先该页签直读 {@code /api/inventory/sale/page}（需 {@code sale:order}），
     * 只有客户档案权限的用户会 403。改为走**本页前缀**，服务端分页语义不变
     * （为什么不做进 {@code /{id}} 详情响应：该页签可翻页、可改每页条数，并入详情会让翻页退化为全量下发）。</p>
     */
    @GetMapping("/{id}/sale-orders")
    public R<Page<Map<String, Object>>> saleOrders(@PathVariable Long id,
                                                   @RequestParam(defaultValue = "1") int pageNum,
                                                   @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(saleOrderService.page(null, id, null, null, null, pageNum, pageSize));
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
