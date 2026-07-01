package com.beichen.erp.material.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.beichen.erp.material.entity.Material;
import com.beichen.erp.material.mapper.MaterialMapper;
import com.beichen.erp.material.service.MaterialService;
import org.springframework.stereotype.Service;

@Service
public class MaterialServiceImpl extends ServiceImpl<MaterialMapper, Material> implements MaterialService {
}
