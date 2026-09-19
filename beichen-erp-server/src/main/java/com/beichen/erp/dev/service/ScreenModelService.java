package com.beichen.erp.dev.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.dev.entity.ScreenModel;

import java.util.List;
import java.util.Map;

/**
 * 屏幕资料知识库服务（F7-102：原先由控制器直连 Mapper 完成全部 CRUD，现收口到服务层）。
 * <p>本表是行业共享基础资料，不参与租户隔离（company_id 恒为 NULL，见 F7-99）。</p>
 */
public interface ScreenModelService {

    Page<ScreenModel> page(int pageNum, int pageSize, String category, String keyword, String brand,
                           String screenSize, String resolution, String screenType);

    /** 不分页全量（导出 Excel 用，筛选条件与 page 一致） */
    List<ScreenModel> listAll(String category, String keyword, String brand,
                              String screenSize, String resolution, String screenType);

    /** 品牌去重列表（品牌下拉筛选用） */
    List<String> brands();

    /** 下拉选项：屏幕类型 / 主屏尺寸 / 分辨率的去重列表 */
    Map<String, List<String>> options();

    ScreenModel getById(Long id);

    ScreenModel create(ScreenModel m);

    ScreenModel update(Long id, ScreenModel m);

    void delete(Long id);

    /** 批量新增（Excel 导入用）：重复项跳过，返回实际写入条数 */
    int batchImport(List<ScreenModel> list);
}
