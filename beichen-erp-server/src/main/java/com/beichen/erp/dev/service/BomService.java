package com.beichen.erp.dev.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.beichen.erp.dev.entity.Bom;
import com.beichen.erp.dev.entity.dto.BomDTO;

import java.util.List;

public interface BomService extends IService<Bom> {

    List<Bom> listByProject(Long projectId);

    void saveItems(Long projectId, List<BomDTO> items);
}
