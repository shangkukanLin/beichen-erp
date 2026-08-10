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
                .eq(Customer::getCompanyId, CompanyContext.get())
                .like(code != null && !code.isBlank(), Customer::getCode, code)
                .like(name != null && !name.isBlank(), Customer::getName, name)
                .orderByDesc(Customer::getId);
        if (status != null && !status.isBlank()) {
            // 防止前端传入非数字 status 触发 NumberFormatException
            if (!status.matches("\\d+")) {
                throw new BusinessException("状态参数非法");
            }
            w.eq(Customer::getStatus, Integer.valueOf(status));
        }
        return customerMapper.selectPage(new Page<>(pageNum, pageSize), w);
    }

    @Override
    public Customer getById(Long id) {
        return customerMapper.selectOne(new LambdaQueryWrapper<Customer>()
                .eq(Customer::getId, id)
                .eq(Customer::getCompanyId, CompanyContext.get()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(Customer customer) {
        if (customer.getName() == null || customer.getName().isBlank()) {
            throw new BusinessException("客户名称不能为空");
        }
        // 账期、信用额度非负校验
        if (customer.getCreditPeriod() != null && customer.getCreditPeriod() < 0) {
            throw new BusinessException("账期天数不能为负");
        }
        if (customer.getCreditPeriodMonths() != null && customer.getCreditPeriodMonths() < 0) {
            throw new BusinessException("账期月数不能为负");
        }
        if (customer.getCreditLimit() != null && customer.getCreditLimit().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("信用额度不能为负");
        }
        if (customer.getCode() != null && !customer.getCode().isBlank()) {
            // 编码唯一性需限定本公司内
            Long cnt = customerMapper.selectCount(new LambdaQueryWrapper<Customer>()
                    .eq(Customer::getCode, customer.getCode())
                    .eq(Customer::getCompanyId, CompanyContext.get()));
            if (cnt != null && cnt > 0) throw new BusinessException("客户编码已存在");
        } else {
            customer.setCode(generateCode());
        }
        // 资金类字段（应收/预收余额）由收付款流水累计，新建时强制归零，禁止外部写入
        if (customer.getCreditPeriod() == null) customer.setCreditPeriod(0);
        if (customer.getCreditLimit() == null) customer.setCreditLimit(BigDecimal.ZERO);
        customer.setReceivableBalance(BigDecimal.ZERO);
        customer.setPrepaidBalance(BigDecimal.ZERO);
        if (customer.getStatus() == null) customer.setStatus(1);
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) customer.setCompanyId(cid);
        customerMapper.insert(customer);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Customer customer) {
        Customer old = customerMapper.selectOne(new LambdaQueryWrapper<Customer>()
                .eq(Customer::getId, customer.getId())
                .eq(Customer::getCompanyId, CompanyContext.get()));
        if (old == null) throw new BusinessException("客户不存在");
        if (customer.getCode() != null && !customer.getCode().equals(old.getCode())) {
            Long cnt = customerMapper.selectCount(new LambdaQueryWrapper<Customer>()
                    .eq(Customer::getCode, customer.getCode())
                    .eq(Customer::getCompanyId, CompanyContext.get()));
            if (cnt != null && cnt > 0) throw new BusinessException("客户编码已存在");
        }
        // 账期、信用额度非负校验
        if (customer.getCreditPeriod() != null && customer.getCreditPeriod() < 0) {
            throw new BusinessException("账期天数不能为负");
        }
        if (customer.getCreditPeriodMonths() != null && customer.getCreditPeriodMonths() < 0) {
            throw new BusinessException("账期月数不能为负");
        }
        if (customer.getCreditLimit() != null && customer.getCreditLimit().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("信用额度不能为负");
        }
        // 仅更新基础资料字段，应收/预收余额由收付款业务维护，禁止此处被外部篡改
        Customer u = new Customer();
        u.setId(customer.getId());
        u.setName(customer.getName());
        u.setContact(customer.getContact());
        u.setPhone(customer.getPhone());
        u.setAddress(customer.getAddress());
        u.setCreditPeriod(customer.getCreditPeriod());
        u.setCreditPeriodMonths(customer.getCreditPeriodMonths());
        u.setCreditLimit(customer.getCreditLimit());
        u.setStatus(customer.getStatus());
        u.setRemark(customer.getRemark());
        customerMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        Customer old = customerMapper.selectOne(new LambdaQueryWrapper<Customer>()
                .eq(Customer::getId, id)
                .eq(Customer::getCompanyId, CompanyContext.get()));
        if (old == null) throw new BusinessException("客户不存在");
        Customer u = new Customer();
        u.setId(id);
        u.setStatus(status);
        customerMapper.updateById(u);
    }

    @Override
    public List<Customer> listAll() {
        return customerMapper.selectList(new LambdaQueryWrapper<Customer>()
                .eq(Customer::getCompanyId, CompanyContext.get())
                .eq(Customer::getStatus, 1).orderByAsc(Customer::getCode));
    }

    private String generateCode() {
        Long cid = CompanyContext.get();
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String likePattern = "CU-" + dateStr;
        // 自动生成编码存在性校验 + 重试，避免并发重复
        for (int i = 0; i < 3; i++) {
            LambdaQueryWrapper<Customer> w = new LambdaQueryWrapper<Customer>()
                    .likeRight(Customer::getCode, likePattern)
                    .orderByDesc(Customer::getCode)
                    .last("LIMIT 1");
            if (cid != null && cid > 0) {
                w.eq(Customer::getCompanyId, cid);
            }
            Customer last = customerMapper.selectOne(w);
            int seq = 1;
            if (last != null && last.getCode() != null) {
                try {
                    String numPart = last.getCode().substring(last.getCode().length() - 3);
                    seq = Integer.parseInt(numPart) + 1;
                } catch (Exception e) { seq = 1; }
            }
            String code = "CU-" + dateStr + String.format("%03d", seq);
            Long cnt = customerMapper.selectCount(new LambdaQueryWrapper<Customer>()
                    .eq(Customer::getCode, code)
                    .eq(Customer::getCompanyId, cid));
            if (cnt == null || cnt == 0) {
                return code;
            }
        }
        // 极端并发兜底：时间戳后缀
        return "CU-" + dateStr + "-" + System.nanoTime() % 1000;
    }
}
