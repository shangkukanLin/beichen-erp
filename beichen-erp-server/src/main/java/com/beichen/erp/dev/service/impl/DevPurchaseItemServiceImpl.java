package com.beichen.erp.dev.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.beichen.erp.dev.entity.DevPurchaseItem;
import com.beichen.erp.dev.mapper.DevPurchaseItemMapper;
import com.beichen.erp.dev.service.DevPurchaseItemService;
import org.springframework.stereotype.Service;

@Service
public class DevPurchaseItemServiceImpl extends ServiceImpl<DevPurchaseItemMapper, DevPurchaseItem> implements DevPurchaseItemService {
}
