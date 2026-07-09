package com.beichen.erp.customer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.customer.entity.Customer;
import com.beichen.erp.customer.mapper.CustomerMapper;
import com.beichen.erp.customer.service.CustomerService;
import com.beichen.erp.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private final CustomerMapper customerMapper;

    @Override
    public Page<Customer> page(String name, String code, String status, int pageNum, int pageSize) {
        LambdaQueryWrapper<Customer> w = new LambdaQueryWrapper<Customer>()
                .like(code != null && !code.isBlank(), Customer::getCode, code)
                .like(name != null && !name.isBlank(), Customer::getName, name)
                .orderByDesc(Customer::getId);
        if (status != null && !status.isBlank()) {
            w.eq(Customer::getStatus, Integer.valueOf(status));
        }
        return customerMapper.selectPage(new Page<>(pageNum, pageSize), w);
    }

    @Override
    public Customer getById(Long id) {
        return customerMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(Customer customer) {
        if (customer.getName() == null || customer.getName().isBlank()) {
            throw new BusinessException("客户名称不能为空");
        }
        if (customer.getCode() != null && !customer.getCode().isBlank()) {
            Long cnt = customerMapper.selectCount(new LambdaQueryWrapper<Customer>().eq(Customer::getCode, customer.getCode()));
            if (cnt != null && cnt > 0) throw new BusinessException("客户编码已存在");
        } else {
            customer.setCode(generateCode());
        }
        if (customer.getCreditPeriod() == null) customer.setCreditPeriod(0);
        if (customer.getCreditLimit() == null) customer.setCreditLimit(BigDecimal.ZERO);
        if (customer.getReceivableBalance() == null) customer.setReceivableBalance(BigDecimal.ZERO);
        if (customer.getPrepaidBalance() == null) customer.setPrepaidBalance(BigDecimal.ZERO);
        if (customer.getStatus() == null) customer.setStatus(1);
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) customer.setCompanyId(cid);
        customerMapper.insert(customer);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Customer customer) {
        Customer old = customerMapper.selectById(customer.getId());
        if (old == null) throw new BusinessException("客户不存在");
        if (customer.getCode() != null && !customer.getCode().equals(old.getCode())) {
            Long cnt = customerMapper.selectCount(new LambdaQueryWrapper<Customer>().eq(Customer::getCode, customer.getCode()));
            if (cnt != null && cnt > 0) throw new BusinessException("客户编码已存在");
        }
        customerMapper.updateById(customer);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        Customer old = customerMapper.selectById(id);
        if (old == null) throw new BusinessException("客户不存在");
        Customer u = new Customer();
        u.setId(id);
        u.setStatus(status);
        customerMapper.updateById(u);
    }

    @Override
    public List<Customer> listAll() {
        return customerMapper.selectList(new LambdaQueryWrapper<Customer>()
                .eq(Customer::getStatus, 1).orderByAsc(Customer::getCode));
    }

    private String generateCode() {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String likePattern = "CU-" + dateStr;
        LambdaQueryWrapper<Customer> w = new LambdaQueryWrapper<Customer>()
                .likeRight(Customer::getCode, likePattern)
                .orderByDesc(Customer::getCode)
                .last("LIMIT 1");
        Customer last = customerMapper.selectOne(w);
        int seq = 1;
        if (last != null && last.getCode() != null) {
            try {
                String numPart = last.getCode().substring(last.getCode().length() - 3);
                seq = Integer.parseInt(numPart) + 1;
            } catch (Exception e) { seq = 1; }
        }
        return "CU-" + dateStr + String.format("%03d", seq);
    }
}
