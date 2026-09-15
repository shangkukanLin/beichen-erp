package com.beichen.erp.dev.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.R;
import com.beichen.erp.dev.entity.ScreenModel;
import com.beichen.erp.dev.mapper.ScreenModelMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 屏幕资料知识库（研发管理 → 屏幕资料知识库）：行业机型屏幕参数，支持增删改查。
 * 数据由 DataInitializer 在表为空时从 db/screen_model_data.sql 导入，之后由用户维护。
 */
@RestController
@RequestMapping("/api/dev/screen-model")
@RequiredArgsConstructor
public class ScreenModelController {

    private final ScreenModelMapper mapper;

    /** 分页查询：关键词匹配品牌/型号/屏幕类型/供应商；另有品牌/主屏尺寸/分辨率/屏幕类型独立筛选 */
    @GetMapping("/page")
    public R<Page<ScreenModel>> page(@RequestParam(defaultValue = "1") int pageNum,
                                     @RequestParam(defaultValue = "20") int pageSize,
                                     @RequestParam(required = false) String category,
                                     @RequestParam(required = false) String keyword,
                                     @RequestParam(required = false) String brand,
                                     @RequestParam(required = false) String screenSize,
                                     @RequestParam(required = false) String resolution,
                                     @RequestParam(required = false) String screenType) {
        LambdaQueryWrapper<ScreenModel> w = new LambdaQueryWrapper<ScreenModel>()
                .eq(category != null && !category.isBlank(), ScreenModel::getCategory, category)
                .and(keyword != null && !keyword.isBlank(), q -> q
                        .like(ScreenModel::getBrand, keyword)
                        .or().like(ScreenModel::getModel, keyword)
                        .or().like(ScreenModel::getScreenType, keyword)
                        .or().like(ScreenModel::getPanelSupplier, keyword))
                .like(brand != null && !brand.isBlank(), ScreenModel::getBrand, brand)
                .like(screenSize != null && !screenSize.isBlank(), ScreenModel::getScreenSize, screenSize)
                .like(resolution != null && !resolution.isBlank(), ScreenModel::getResolution, resolution)
                .like(screenType != null && !screenType.isBlank(), ScreenModel::getScreenType, screenType)
                .orderByAsc(ScreenModel::getCategory)
                .orderByAsc(ScreenModel::getBrand)
                .orderByAsc(ScreenModel::getModel);
        return R.ok(mapper.selectPage(new Page<>(pageNum, pageSize), w));
    }

    /** 不分页全量（导出 Excel 用，筛选条件与 page 一致） */
    @GetMapping("/list")
    public R<List<ScreenModel>> list(@RequestParam(required = false) String category,
                                     @RequestParam(required = false) String keyword,
                                     @RequestParam(required = false) String brand,
                                     @RequestParam(required = false) String screenSize,
                                     @RequestParam(required = false) String resolution,
                                     @RequestParam(required = false) String screenType) {
        Page<ScreenModel> p = page(1, 100000, category, keyword, brand, screenSize, resolution, screenType).getData();
        return R.ok(p.getRecords());
    }

    /** 品牌去重列表（品牌下拉筛选用） */
    @GetMapping("/brands")
    public R<List<String>> brands() {
        List<Object> raw = mapper.selectObjs(new LambdaQueryWrapper<ScreenModel>()
                .select(ScreenModel::getBrand)
                .isNotNull(ScreenModel::getBrand)
                .ne(ScreenModel::getBrand, "")
                .groupBy(ScreenModel::getBrand)
                .orderByAsc(ScreenModel::getBrand));
        return R.ok(raw.stream().map(String::valueOf).toList());
    }

    /** 下拉选项：屏幕类型 / 主屏尺寸 / 分辨率 的去重列表（新增/编辑弹窗与筛选栏共用） */
    @GetMapping("/options")
    public R<Map<String, List<String>>> options() {
        return R.ok(Map.of(
                "screenTypes", distinct(ScreenModel::getScreenType),
                "screenSizes", distinct(ScreenModel::getScreenSize),
                "resolutions", distinct(ScreenModel::getResolution)));
    }

    /** 通用去重：按指定字段聚合非空值并升序返回 */
    private List<String> distinct(SFunction<ScreenModel, String> field) {
        List<Object> raw = mapper.selectObjs(new LambdaQueryWrapper<ScreenModel>()
                .select(field)
                .isNotNull(field)
                .ne(field, "")
                .groupBy(field)
                .orderByAsc(field));
        return raw.stream().map(String::valueOf).toList();
    }

    /** 刷新率提取正则：取首个「数字Hz」（忽略大小写），丢弃 PWM 等冗余说明 */
    private static final Pattern REFRESH_PATTERN = Pattern.compile("([0-9]+)\\s*HZ", Pattern.CASE_INSENSITIVE);

    /** 落库前规范化：分辨率统一为「数字 x 数字」；刷新率只保留「数字Hz」，去掉 PWM 等冗余说明 */
    private void normalize(ScreenModel m) {
        if (m.getResolution() != null && !m.getResolution().isBlank()) {
            m.setResolution(m.getResolution().replaceAll("([0-9])[xX]([0-9])", "$1 x $2"));
        }
        if (m.getRefreshRate() != null && !m.getRefreshRate().isBlank()) {
            Matcher mt = REFRESH_PATTERN.matcher(m.getRefreshRate());
            if (mt.find()) {
                m.setRefreshRate(mt.group(1) + "Hz");
            }
        }
    }

    @GetMapping("/{id}")
    public R<ScreenModel> getById(@PathVariable Long id) {
        return R.ok(mapper.selectById(id));
    }

    @PostMapping
    public R<ScreenModel> create(@RequestBody ScreenModel m) {
        m.setId(null);
        normalize(m);
        mapper.insert(m);
        return R.ok(m);
    }

    @PutMapping("/{id}")
    public R<ScreenModel> update(@PathVariable Long id, @RequestBody ScreenModel m) {
        m.setId(id);
        normalize(m);
        mapper.updateById(m);
        return R.ok(m);
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        mapper.deleteById(id);
        return R.ok();
    }

    /** 批量新增（Excel 导入用） */
    @PostMapping("/batch")
    public R<Integer> batch(@RequestBody List<ScreenModel> list) {
        int n = 0;
        for (ScreenModel m : list) {
            if (m == null) continue;
            m.setId(null);
            normalize(m);
            mapper.insert(m);
            n++;
        }
        return R.ok(n);
    }
}
