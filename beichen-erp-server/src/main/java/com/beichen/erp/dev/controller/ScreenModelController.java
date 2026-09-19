package com.beichen.erp.dev.controller;

import com.beichen.erp.common.R;
import com.beichen.erp.dev.entity.ScreenModel;
import com.beichen.erp.dev.service.ScreenModelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 屏幕资料知识库（研发管理 → 屏幕资料知识库）：行业机型屏幕参数，支持增删改查。
 * <p>数据由 DataInitializer 在表为空时从 db/screen_model_data.sql 导入，之后由用户维护；
 * 本表为**行业共享**基础资料（不参与租户隔离，company_id 恒为 NULL）。</p>
 * <p>F7-102（2026-09-19）：原先控制器直连 {@code ScreenModelMapper} 完成全部 CRUD（架构债 A4 同族）
 * ⇒ 现改为薄控制器，业务逻辑与校验全部下沉到 {@link ScreenModelService}。</p>
 */
@RestController
@RequestMapping("/api/dev/screen-model")
@RequiredArgsConstructor
public class ScreenModelController {

    private final ScreenModelService screenModelService;

    /** 分页查询：关键词匹配品牌/型号/屏幕类型/供应商；另有品牌/主屏尺寸/分辨率/屏幕类型独立筛选 */
    @GetMapping("/page")
    public R<com.baomidou.mybatisplus.extension.plugins.pagination.Page<ScreenModel>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String screenSize,
            @RequestParam(required = false) String resolution,
            @RequestParam(required = false) String screenType) {
        return R.ok(screenModelService.page(pageNum, pageSize, category, keyword, brand,
                screenSize, resolution, screenType));
    }

    /** 不分页全量（导出 Excel 用，筛选条件与 page 一致） */
    @GetMapping("/list")
    public R<List<ScreenModel>> list(@RequestParam(required = false) String category,
                                     @RequestParam(required = false) String keyword,
                                     @RequestParam(required = false) String brand,
                                     @RequestParam(required = false) String screenSize,
                                     @RequestParam(required = false) String resolution,
                                     @RequestParam(required = false) String screenType) {
        return R.ok(screenModelService.listAll(category, keyword, brand, screenSize, resolution, screenType));
    }

    /** 品牌去重列表（品牌下拉筛选用） */
    @GetMapping("/brands")
    public R<List<String>> brands() {
        return R.ok(screenModelService.brands());
    }

    /** 下拉选项：屏幕类型 / 主屏尺寸 / 分辨率 的去重列表（新增/编辑弹窗与筛选栏共用） */
    @GetMapping("/options")
    public R<Map<String, List<String>>> options() {
        return R.ok(screenModelService.options());
    }

    @GetMapping("/{id}")
    public R<ScreenModel> getById(@PathVariable Long id) {
        return R.ok(screenModelService.getById(id));
    }

    @PostMapping
    public R<ScreenModel> create(@RequestBody ScreenModel m) {
        return R.ok(screenModelService.create(m));
    }

    @PutMapping("/{id}")
    public R<ScreenModel> update(@PathVariable Long id, @RequestBody ScreenModel m) {
        return R.ok(screenModelService.update(id, m));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        screenModelService.delete(id);
        return R.ok();
    }

    /** 批量新增（Excel 导入用）：重复项跳过，返回实际写入条数 */
    @PostMapping("/batch")
    public R<Integer> batch(@RequestBody List<ScreenModel> list) {
        return R.ok(screenModelService.batchImport(list));
    }
}
