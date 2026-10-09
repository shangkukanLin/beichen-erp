package com.beichen.erp.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.system.entity.UserTablePref;
import com.beichen.erp.system.mapper.UserTablePrefMapper;
import com.beichen.erp.system.service.UserTablePrefService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 用户表格列宽偏好实现（2026-10-09，表 sys_user_table_pref / Flyway V5）。
 *
 * <p>设计取舍：后端**不解释**列宽的语义（哪个列、什么含义只有前端知道），只做三件事 ——
 * ① 按用户存取；② 校验形状（必须是 JSON 对象、值是数字且在合理区间、键不超长）；③ 限制规模
 * （防滥用：单表列数、单表报文、每用户表格数都有上限）。这样列标识口径以后要改，只动前端一处。</p>
 */
@Service
@RequiredArgsConstructor
public class UserTablePrefServiceImpl implements UserTablePrefService {

    /** 单表最多记多少列（正常表格 < 50 列） */
    private static final int MAX_COLS = 200;
    /** 单条 prefs 文本上限（正常 < 2KB） */
    private static final int MAX_PREFS_LEN = 8000;
    /** 单个用户最多记多少张表（正常远小于此） */
    private static final int MAX_TABLES_PER_USER = 500;
    /** 单个列标识最长大度 */
    private static final int MAX_KEY_LEN = 100;
    /** 列宽合理区间（前端另有更严的 60~400 夹取） */
    private static final int MIN_W = 20;
    private static final int MAX_W = 2000;

    private final UserTablePrefMapper mapper;
    private final ObjectMapper objectMapper;

    @Override
    public Map<String, String> listByUser(Long userId) {
        Map<String, String> out = new LinkedHashMap<>();
        if (userId == null) return out;
        for (UserTablePref p : mapper.selectList(
                new LambdaQueryWrapper<UserTablePref>().eq(UserTablePref::getUserId, userId))) {
            if (p.getPrefKey() != null && p.getPrefs() != null && !p.getPrefs().isBlank()) {
                out.put(p.getPrefKey(), p.getPrefs());
            }
        }
        return out;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(Long userId, String prefKey, String prefs) {
        if (userId == null) throw new BusinessException("未登录");
        // 空 prefs = 清除该表（前端"重置列宽"就直接这么调）
        if (prefs == null || prefs.isBlank()) {
            resetOne(userId, prefKey);
            return;
        }
        validate(prefKey, prefs);
        String key = prefKey.trim();

        UserTablePref exist = mapper.selectOne(new LambdaQueryWrapper<UserTablePref>()
                .eq(UserTablePref::getUserId, userId)
                .eq(UserTablePref::getPrefKey, key));
        if (exist != null) {
            UserTablePref update = new UserTablePref();
            update.setId(exist.getId());
            update.setPrefs(prefs);
            mapper.updateById(update);
            return;
        }
        Long count = mapper.selectCount(new LambdaQueryWrapper<UserTablePref>()
                .eq(UserTablePref::getUserId, userId));
        if (count != null && count >= MAX_TABLES_PER_USER) {
            throw new BusinessException("自定义列宽的表格数量已达上限（" + MAX_TABLES_PER_USER + "）");
        }
        UserTablePref insert = new UserTablePref();
        insert.setUserId(userId);
        insert.setPrefKey(key);
        insert.setPrefs(prefs);
        mapper.insert(insert);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetOne(Long userId, String prefKey) {
        if (userId == null || prefKey == null || prefKey.isBlank()) return;
        mapper.delete(new LambdaQueryWrapper<UserTablePref>()
                .eq(UserTablePref::getUserId, userId)
                .eq(UserTablePref::getPrefKey, prefKey.trim()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetAll(Long userId) {
        if (userId == null) return;
        mapper.delete(new LambdaQueryWrapper<UserTablePref>().eq(UserTablePref::getUserId, userId));
    }

    /**
     * 形状校验：必须是 {"列标识": 数字} 的 JSON 对象，键不超长、值在合理区间、规模受限。
     * <p>刻意宽松（不懂语义）但足够挡住脏数据 —— 前端一次 bug 不该把表写坏，也不该让页面渲染炸掉。</p>
     */
    private void validate(String prefKey, String prefs) {
        if (prefKey == null || prefKey.isBlank()) throw new BusinessException("表格键不能为空");
        if (prefKey.trim().length() > 191) throw new BusinessException("表格键过长");
        if (prefs.length() > MAX_PREFS_LEN) throw new BusinessException("列宽数据过大");
        try {
            JsonNode node = objectMapper.readTree(prefs);
            if (node == null || !node.isObject()) throw new BusinessException("列宽数据格式不正确");
            if (node.size() > MAX_COLS) throw new BusinessException("列数超出上限（" + MAX_COLS + "）");
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                String k = e.getKey();
                if (k == null || k.isBlank() || k.length() > MAX_KEY_LEN) throw new BusinessException("列标识不合法");
                JsonNode v = e.getValue();
                if (v == null || !v.isNumber()) throw new BusinessException("列宽必须是数字");
                double d = v.asDouble();
                if (d < MIN_W || d > MAX_W) {
                    throw new BusinessException("列宽超出合理区间（" + MIN_W + "~" + MAX_W + "px）");
                }
            }
        } catch (BusinessException be) {
            throw be;
        } catch (Exception ex) {
            throw new BusinessException("列宽数据格式不正确");
        }
    }
}
