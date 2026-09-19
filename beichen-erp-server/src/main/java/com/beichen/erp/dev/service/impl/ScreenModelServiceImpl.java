package com.beichen.erp.dev.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.dev.entity.ScreenModel;
import com.beichen.erp.dev.mapper.ScreenModelMapper;
import com.beichen.erp.dev.service.ScreenModelService;
import com.beichen.erp.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 屏幕资料知识库业务实现。
 * <p>F7-102（2026-09-19）：原先把全部 CRUD 直连 {@code ScreenModelMapper} 写在控制器里
 * （架构债 A4 同族，本报告第 3 例）⇒ 现收口到服务层，并补上：统一事务、必填/重复校验、
 * 批量导入条数上限、导出不再用魔法值 {@code pageSize=100000} 而是真正的全量查询。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScreenModelServiceImpl implements ScreenModelService {

    private final ScreenModelMapper mapper;

    /** 批量导入单次上限（F7-102）：导入是循环 insert，不设上限时一次请求可写几十万行 */
    private static final int MAX_BATCH_SIZE = 1000;

    /** 刷新率提取正则：取首个「数字Hz」（忽略大小写），丢弃 PWM 等冗余说明 */
    private static final Pattern REFRESH_PATTERN = Pattern.compile("([0-9]+)\\s*HZ", Pattern.CASE_INSENSITIVE);

    @Override
    public Page<ScreenModel> page(int pageNum, int pageSize, String category, String keyword, String brand,
                                  String screenSize, String resolution, String screenType) {
        return mapper.selectPage(new Page<>(pageNum, pageSize),
                buildQuery(category, keyword, brand, screenSize, resolution, screenType));
    }

    @Override
    public List<ScreenModel> listAll(String category, String keyword, String brand,
                                     String screenSize, String resolution, String screenType) {
        // F7-102：原实现复用 page(1, 100000, ...) 再取 records —— 魔法值、且超过上限会静默截断
        return mapper.selectList(buildQuery(category, keyword, brand, screenSize, resolution, screenType));
    }

    private LambdaQueryWrapper<ScreenModel> buildQuery(String category, String keyword, String brand,
                                                       String screenSize, String resolution, String screenType) {
        return new LambdaQueryWrapper<ScreenModel>()
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
    }

    @Override
    public List<String> brands() {
        return distinct(ScreenModel::getBrand);
    }

    @Override
    public Map<String, List<String>> options() {
        return Map.of(
                "screenTypes", distinct(ScreenModel::getScreenType),
                "screenSizes", distinct(ScreenModel::getScreenSize),
                "resolutions", distinct(ScreenModel::getResolution));
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

    @Override
    public ScreenModel getById(Long id) {
        return mapper.selectById(id);
    }

    @Override
    @Transactional
    public ScreenModel create(ScreenModel m) {
        m.setId(null);
        validate(m);
        // F7-102：新增时同品牌+型号（同类别）不允许重复，避免知识库被重复条目淹没
        if (isDuplicate(m, null)) throw new BusinessException("该品牌 + 型号的屏幕资料已存在");
        normalize(m);
        mapper.insert(m);
        return m;
    }

    @Override
    @Transactional
    public ScreenModel update(Long id, ScreenModel m) {
        if (mapper.selectById(id) == null) throw new BusinessException("屏幕资料不存在");
        m.setId(id);
        validate(m);
        if (isDuplicate(m, id)) throw new BusinessException("该品牌 + 型号的屏幕资料已存在");
        normalize(m);
        mapper.updateById(m);
        return m;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        // F7-102：原先 deleteById 对不存在的 id 静默"成功"
        if (mapper.selectById(id) == null) throw new BusinessException("屏幕资料不存在");
        mapper.deleteById(id);
    }

    @Override
    @Transactional
    public int batchImport(List<ScreenModel> list) {
        if (list == null || list.isEmpty()) throw new BusinessException("导入内容为空");
        if (list.size() > MAX_BATCH_SIZE) {
            throw new BusinessException("单次最多导入 " + MAX_BATCH_SIZE + " 条，请分批导入");
        }
        int n = 0;
        for (ScreenModel m : list) {
            if (m == null) continue;
            m.setId(null);
            validate(m);
            // 导入按"跳过重复"处理：整批失败不如跳过并如实返回写入条数
            if (isDuplicate(m, null)) continue;
            normalize(m);
            mapper.insert(m);
            n++;
        }
        log.info("屏幕资料批量导入完成: 提交 {} 条，实际写入 {} 条", list.size(), n);
        return n;
    }

    private void validate(ScreenModel m) {
        if (m.getBrand() == null || m.getBrand().isBlank()) throw new BusinessException("品牌不能为空");
        if (m.getModel() == null || m.getModel().isBlank()) throw new BusinessException("型号不能为空");
    }

    /** 同品牌+型号（+类别，若填写）是否已存在；excludeId 用于更新时排除自身 */
    private boolean isDuplicate(ScreenModel m, Long excludeId) {
        LambdaQueryWrapper<ScreenModel> w = new LambdaQueryWrapper<ScreenModel>()
                .eq(ScreenModel::getBrand, m.getBrand())
                .eq(ScreenModel::getModel, m.getModel());
        if (m.getCategory() != null && !m.getCategory().isBlank()) {
            w.eq(ScreenModel::getCategory, m.getCategory());
        }
        if (excludeId != null) w.ne(ScreenModel::getId, excludeId);
        Long c = mapper.selectCount(w);
        return c != null && c > 0;
    }

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
}
