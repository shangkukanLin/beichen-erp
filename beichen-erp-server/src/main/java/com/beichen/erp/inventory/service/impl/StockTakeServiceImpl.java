package com.beichen.erp.inventory.service.impl;

import com.beichen.erp.config.UserContext;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beichen.erp.common.BillPrefix;
import com.beichen.erp.common.DocStatus;
import com.beichen.erp.common.DocStatusGuard;
import com.beichen.erp.config.CompanyContext;
import com.beichen.erp.exception.BusinessException;
import com.beichen.erp.inventory.common.RelatedBillType;
import com.beichen.erp.inventory.common.StockChangeType;
import com.beichen.erp.inventory.common.StockTakeScope;
import com.beichen.erp.inventory.entity.InventoryStockTake;
import com.beichen.erp.inventory.entity.InventoryStockTakeItem;
import com.beichen.erp.inventory.mapper.InventoryStockTakeItemMapper;
import com.beichen.erp.inventory.mapper.InventoryStockTakeMapper;
import com.beichen.erp.inventory.service.StockTakeService;
import com.beichen.erp.material.entity.Product;
import com.beichen.erp.material.mapper.ProductMapper;
import com.beichen.erp.outsource.entity.OutsourceMaterial;
import com.beichen.erp.outsource.mapper.OutsourceMaterialMapper;
import com.beichen.erp.system.common.SystemConstants;
import com.beichen.erp.warehouse.entity.Warehouse;
import com.beichen.erp.warehouse.entity.WarehouseStock;
import com.beichen.erp.warehouse.mapper.WarehouseMapper;
import com.beichen.erp.warehouse.mapper.WarehouseStockMapper;
import com.beichen.erp.warehouse.service.CostService;
import com.beichen.erp.warehouse.service.WarehouseStockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 库存盘点实现（每月每仓一次）。
 * <p>
 * 创建：按仓库现有库存行快照生成明细（成品 product+品质，委外物料 material）；
 * 审核：逐行按差异调整库存（盘盈 STOCK_TAKE_IN / 盘亏 STOCK_TAKE_OUT，物料走 changeMaterialStock）；
 * 反审核：逆向调整回滚；作废仅草稿可用。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockTakeServiceImpl implements StockTakeService {

    /** 进入"本月待盘点"提醒窗口的日期（当月 25 号起） */
    private static final int REMIND_DAY = 25;
    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyy-MM");

    private final InventoryStockTakeMapper takeMapper;
    private final InventoryStockTakeItemMapper itemMapper;
    private final WarehouseMapper warehouseMapper;
    private final WarehouseStockMapper stockMapper;
    private final WarehouseStockService stockService;
    private final ProductMapper productMapper;
    private final OutsourceMaterialMapper materialMapper;
    private final CostService costService;
    /**
     * 物料类型主数据（2026-10-10 用户口径「物料名称前面需要显示物料类型」）。
     *
     * <p>建单快照时把类型名一并写进展示字段 {@code InventoryStockTakeItem.materialTypeName}
     * （`@TableField(exist = false)` ⇒ 不落库、不需迁移 ✓），前端全局 `$mLabel` 直接可用 ✓。</p>
     */
    private final com.beichen.erp.dev.mapper.MaterialTypeMapper materialTypeMapper;

    @Override
    public Page<Map<String, Object>> page(Long warehouseId, String period, String status, String scope, int pageNum, int pageSize) {
        // 2026-09-16：按盘点范围过滤 —— 成品盘点页只看成品类仓库的单据，物料盘点页只看物料类
        StockTakeScope sc = StockTakeScope.of(scope);
        assertRoleForScope(sc); // 物料盘点查询同样限跟单专员（防止绕过页面直连接口看物料盘点数据）
        List<Long> scopeIds = sc == null ? null : scopeWarehouseIds(sc);
        if (scopeIds != null && scopeIds.isEmpty()) {
            return new Page<>(pageNum, pageSize, 0); // 该范围内无启用仓库 → 直接空结果（避免 IN () 语法错误）
        }
        LambdaQueryWrapper<InventoryStockTake> w = new LambdaQueryWrapper<InventoryStockTake>()
                .eq(warehouseId != null, InventoryStockTake::getWarehouseId, warehouseId)
                .in(scopeIds != null, InventoryStockTake::getWarehouseId, scopeIds != null ? scopeIds : List.of(-1L))
                .eq(period != null && !period.isBlank(), InventoryStockTake::getPeriod, period)
                .eq(status != null && !status.isBlank(), InventoryStockTake::getStatus, status)
                .orderByDesc(InventoryStockTake::getId);
        Page<InventoryStockTake> raw = takeMapper.selectPage(new Page<>(pageNum, pageSize), w);
        Page<Map<String, Object>> res = new Page<>(pageNum, pageSize, raw.getTotal());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (InventoryStockTake t : raw.getRecords()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("takeNo", t.getTakeNo());
            m.put("warehouseId", t.getWarehouseId());
            m.put("warehouseName", t.getWarehouseName());
            m.put("period", t.getPeriod());
            m.put("takeDate", t.getTakeDate());
            m.put("status", t.getStatus());
            m.put("remark", t.getRemark());
            // 差异合计（绝对值合计 + 有差异行数），列表直接看盘点结果
            List<InventoryStockTakeItem> items = itemMapper.selectList(
                    new LambdaQueryWrapper<InventoryStockTakeItem>().eq(InventoryStockTakeItem::getTakeId, t.getId()));
            BigDecimal diffSum = BigDecimal.ZERO;
            int diffCount = 0;
            for (InventoryStockTakeItem it : items) {
                BigDecimal d = it.getDiffQuantity() != null ? it.getDiffQuantity() : BigDecimal.ZERO;
                if (d.compareTo(BigDecimal.ZERO) != 0) {
                    diffCount++;
                    diffSum = diffSum.add(d);
                }
            }
            m.put("itemCount", items.size());
            m.put("diffCount", diffCount);
            m.put("diffSum", diffSum);
            rows.add(m);
        }
        res.setRecords(rows);
        return res;
    }

    @Override
    public InventoryStockTake getById(Long id) {
        InventoryStockTake t = takeMapper.selectById(id);
        assertRoleForTake(t);
        return t;
    }

    @Override
    public List<InventoryStockTakeItem> getItems(Long takeId) {
        assertRoleForTake(takeMapper.selectById(takeId));
        List<InventoryStockTakeItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<InventoryStockTakeItem>().eq(InventoryStockTakeItem::getTakeId, takeId));
        fillMaterialTypeNames(items);
        return items;
    }

    /**
     * 回填「物料类型名」（展示字段、`@TableField(exist = false)` **不落库**）。
     *
     * <p>2026-10-10 用户口径「物料名称前面需要显示物料类型」。⚠️ 为什么必须在**读取路径**做：
     * 该字段不持久化 ⇒ 只在 {@link #create} 里赋值的话，**存量盘点单**（以及任何重新查询出来的行）
     * 的类型名都是 null ⇒ 页面永远显示不出类型 ✗（这是本次差点漏掉的坑 ✓）。
     * 用两次批量查询（物料 → 类型）而不是逐行查，避免 N+1 ✓。</p>
     */
    private void fillMaterialTypeNames(List<InventoryStockTakeItem> items) {
        if (items == null || items.isEmpty()) return;
        List<Long> mids = items.stream()
                .filter(i -> i.getMaterialId() != null && i.getMaterialTypeName() == null)
                .map(InventoryStockTakeItem::getMaterialId).distinct().toList();
        if (mids.isEmpty()) return;
        Map<Long, Long> midToTypeId = new LinkedHashMap<>();
        for (OutsourceMaterial m : materialMapper.selectBatchIds(mids)) {
            if (m.getMaterialTypeId() != null) midToTypeId.put(m.getId(), m.getMaterialTypeId());
        }
        if (midToTypeId.isEmpty()) return;
        Map<Long, String> typeNames = new LinkedHashMap<>();
        for (com.beichen.erp.dev.entity.MaterialType mt
                : materialTypeMapper.selectBatchIds(midToTypeId.values().stream().distinct().toList())) {
            typeNames.put(mt.getId(), mt.getTypeName());
        }
        for (InventoryStockTakeItem it : items) {
            Long tid = midToTypeId.get(it.getMaterialId());
            if (tid != null) it.setMaterialTypeName(typeNames.get(tid));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InventoryStockTake create(Long warehouseId, String period, LocalDate takeDate, String remark, String scope) {
        if (warehouseId == null) throw new BusinessException("请选择盘点仓库");
        Warehouse wh = warehouseMapper.selectById(warehouseId);
        if (wh == null) throw new BusinessException("仓库不存在");
        // 服务端范围校验：前端传了 scope 就必须匹配（防止绕过前端把成品仓建成"物料盘点"或反之）
        StockTakeScope want = StockTakeScope.of(scope);
        if (want != null && !want.test(wh)) {
            throw new BusinessException(want == StockTakeScope.MATERIAL
                    ? "该仓库不属于物料盘点范围（仅委外仓 / 自有物料仓）"
                    : "该仓库不属于成品盘点范围（成品仓 / 不良仓 / 售后仓）");
        }
        // 物料仓的盘点单：接口级限跟单专员（按仓库实际归属判定，不依赖前端是否传 scope）
        assertRoleForScope(scopeOf(wh));
        String ym = (period == null || period.isBlank()) ? LocalDate.now().format(YM) : period;
        // F7-24（2026-09-19）：对仓库行加行锁，使"同仓同月查重 → 插入"串行化。
        // 原先查重（selectCount）与插入之间无锁，且表上没有 (warehouse_id, period) 唯一索引
        // ⇒ 并发提交（两人同时建同仓同月盘点单）会建出两张。锁在 warehouse 行上（该行必然存在）。
        warehouseMapper.selectOne(new LambdaQueryWrapper<Warehouse>()
                .select(Warehouse::getId).eq(Warehouse::getId, warehouseId).last("FOR UPDATE"));
        // 同一仓库同一月份只保留一条有效单据（草稿/已审核）
        Long exist = takeMapper.selectCount(new LambdaQueryWrapper<InventoryStockTake>()
                .eq(InventoryStockTake::getWarehouseId, warehouseId)
                .eq(InventoryStockTake::getPeriod, ym)
                .ne(InventoryStockTake::getStatus, DocStatus.CANCELLED.getCode()));
        if (exist != null && exist > 0) throw new BusinessException("该仓库 " + ym + " 已存在盘点单，请勿重复创建");

        InventoryStockTake t = new InventoryStockTake();
        t.setTakeNo(genNo());
        t.setWarehouseId(warehouseId);
        t.setWarehouseName(wh.getWarehouseName());
        t.setPeriod(ym);
        t.setTakeDate(takeDate != null ? takeDate : LocalDate.now());
        t.setStatus(DocStatus.DRAFT.getCode());
        t.setRemark(remark);
        Long cid = CompanyContext.get();
        if (cid != null && cid > 0) t.setCompanyId(cid);
        takeMapper.insert(t);

        // 快照当前库存行生成明细
        List<WarehouseStock> stocks = stockMapper.selectList(new LambdaQueryWrapper<WarehouseStock>()
                .eq(WarehouseStock::getWarehouseId, warehouseId));
        for (WarehouseStock s : stocks) {
            InventoryStockTakeItem it = new InventoryStockTakeItem();
            it.setTakeId(t.getId());
            it.setProductId(s.getProductId());
            it.setMaterialId(s.getMaterialId());
            it.setQualityType(s.getQualityType());
            // P0-3 收尾（2026-09-25）：快照带库存形态 —— 仅形态不同的两行库存自然生成两条独立明细（不并表）
            it.setStockForm(s.getStockForm());
            it.setBookQuantity(s.getQuantity() != null ? s.getQuantity() : BigDecimal.ZERO);
            it.setActualQuantity(it.getBookQuantity()); // 默认实盘=账面，用户只改有差异的行
            it.setDiffQuantity(BigDecimal.ZERO);
            if (s.getProductId() != null) {
                Product p = productMapper.selectById(s.getProductId());
                if (p != null) {
                    it.setProductName(p.getName());
                    it.setSku(p.getSku());
                    it.setUnit(p.getUnit());
                }
            } else if (s.getMaterialId() != null) {
                OutsourceMaterial m = materialMapper.selectById(s.getMaterialId());
                if (m != null) {
                    it.setMaterialName(m.getMaterialName());
                    it.setUnit(m.getUnit());
                }
            }
            if (cid != null && cid > 0) it.setCompanyId(cid);
            itemMapper.insert(it);
        }
        log.info("创建盘点单 {} 仓库={} 月份={} 明细={} 行", t.getTakeNo(), wh.getWarehouseName(), ym, stocks.size());
        return t;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveItems(Long takeId, List<InventoryStockTakeItem> items) {
        InventoryStockTake t = takeMapper.selectById(takeId);
        if (t == null) throw new BusinessException("盘点单不存在");
        assertRoleForTake(t);
        if (!DocStatus.DRAFT.getCode().equals(t.getStatus())) throw new BusinessException("只有草稿状态可保存实盘数量");
        if (items == null) return;
        for (InventoryStockTakeItem in : items) {
            if (in.getId() == null) continue;
            // F6（2026-09-18 修）：只认本单自己的明细行（原先直接 updateById 提交的 id，跨单 id 会改到别的盘点单）
            InventoryStockTakeItem db = itemMapper.selectById(in.getId());
            if (db == null || !takeId.equals(db.getTakeId())) continue;
            BigDecimal actual = in.getActualQuantity() != null ? in.getActualQuantity() : BigDecimal.ZERO;
            // F6：账面一律取**当前库存**（不信前端提交的账面快照）—— 创建后若库存被其他单据变动过，
            // 旧快照会让差异算错，审核后库存 ≠ 实盘。
            BigDecimal liveBook = currentBook(t.getWarehouseId(), db);
            // 是否"用户确实盘过这一行"：页面默认 实盘=账面，用户只改有差异的行 → 仍等于原账面快照即视为未盘
            boolean counted = db.getBookQuantity() == null || actual.compareTo(db.getBookQuantity()) != 0;
            if (!counted) actual = liveBook; // 未盘行以当前账面为准（差异 0），避免把库存拉回历史快照
            InventoryStockTakeItem u = new InventoryStockTakeItem();
            u.setId(db.getId());
            u.setActualQuantity(actual);
            u.setBookQuantity(liveBook);
            u.setDiffQuantity(counted ? actual.subtract(liveBook) : BigDecimal.ZERO);
            u.setRemark(in.getRemark());
            itemMapper.updateById(u);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        InventoryStockTake t = takeMapper.selectById(id);
        if (t == null) throw new BusinessException("盘点单不存在");
        assertRoleForTake(t);
        // P2-29：原子抢占 DRAFT→AUDITED，避免并发/双击重复按差异调整库存
        if (!DocStatusGuard.claim(takeMapper, InventoryStockTake::getId, id,
                InventoryStockTake::getStatus, DocStatus.DRAFT.getCode(), DocStatus.AUDITED.getCode())) {
            throw new BusinessException("只有草稿状态可审核");
        }
        // F6（2026-09-18 修）：落账前按**当前账面**兜底重算差异 —— 保证盘点不变式「审核后库存 == 实盘」：
        // 保存实盘之后、审核之前若库存又被其他单据变动过，仍按此刻账面结算（不是保存时的旧账面）。
        refreshBookAtAudit(t);
        applyDiff(t, false);
        InventoryStockTake u = new InventoryStockTake();
        u.setId(id);
        u.setStatus(DocStatus.AUDITED.getCode());
        u.setAuditTime(LocalDateTime.now());
        // 2026-09-23（用户口径：单据详情显示「制单人 + 审核人」）：补记录审核人
        // （原先只记 audit_time；而 unAudit 会清空审核人 ⇒ 审计信息一直缺失，属既有瑕疵）
        u.setAuditorId(UserContext.getId());
        u.setAuditorName(UserContext.getName());
        takeMapper.updateById(u);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unAudit(Long id) {
        InventoryStockTake t = takeMapper.selectById(id);
        if (t == null) throw new BusinessException("盘点单不存在");
        assertRoleForTake(t);
        // P2-29：原子抢占 AUDITED→DRAFT，避免并发反审核重复冲回盘点差异
        if (!DocStatusGuard.claim(takeMapper, InventoryStockTake::getId, id,
                InventoryStockTake::getStatus, DocStatus.AUDITED.getCode(), DocStatus.DRAFT.getCode())) {
            throw new BusinessException("只有已审核状态可反审核");
        }
        applyDiff(t, true);
        // 审核信息必须用 UpdateWrapper 显式置 null：updateById 会忽略 null 字段，
        // 否则反审核回草稿后 audit_time/审核人仍残留，单据看着"像已审核"（审计信息失真）。
        takeMapper.update(null, new LambdaUpdateWrapper<InventoryStockTake>()
                .eq(InventoryStockTake::getId, id)
                .set(InventoryStockTake::getStatus, DocStatus.DRAFT.getCode())
                .set(InventoryStockTake::getAuditTime, null)
                .set(InventoryStockTake::getAuditorId, null)
                .set(InventoryStockTake::getAuditorName, null));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        InventoryStockTake t = takeMapper.selectById(id);
        if (t == null) throw new BusinessException("盘点单不存在");
        assertRoleForTake(t);
        // F7-50（2026-09-19）：原子抢占 DRAFT→CANCELLED（原"先查后改"可与 audit 并发互覆，见移仓单同款注释）
        if (!DocStatusGuard.claim(takeMapper, InventoryStockTake::getId, id,
                InventoryStockTake::getStatus, DocStatus.DRAFT.getCode(), DocStatus.CANCELLED.getCode()))
            throw new BusinessException("只有草稿状态可作废");
    }

    @Override
    public List<Map<String, Object>> takeStatus(String scope) {
        LocalDate today = LocalDate.now();
        String curYm = today.format(YM);
        LocalDate due = today.withDayOfMonth(today.lengthOfMonth()); // 应盘日 = 当月最后一天
        boolean remind = today.getDayOfMonth() >= REMIND_DAY;

        StockTakeScope sc = StockTakeScope.of(scope);
        assertRoleForScope(sc); // 物料盘点看板同样限跟单专员
        List<Warehouse> whs = warehouseMapper.selectList(new LambdaQueryWrapper<Warehouse>()
                .eq(Warehouse::getStatus, 1)
                .orderByAsc(Warehouse::getId));
        if (sc != null) whs = whs.stream().filter(sc::test).toList(); // 2026-09-16：成品/物料盘点看板分开
        // 本月已审核的盘点单（仓库 -> 审核日期）
        Map<Long, InventoryStockTake> done = new LinkedHashMap<>();
        for (InventoryStockTake t : takeMapper.selectList(new LambdaQueryWrapper<InventoryStockTake>()
                .eq(InventoryStockTake::getPeriod, curYm)
                .eq(InventoryStockTake::getStatus, DocStatus.AUDITED.getCode()))) {
            done.put(t.getWarehouseId(), t);
        }
        // 各仓最近一次已审核盘点日期
        Map<Long, LocalDate> lastMap = new LinkedHashMap<>();
        for (InventoryStockTake t : takeMapper.selectList(new LambdaQueryWrapper<InventoryStockTake>()
                .eq(InventoryStockTake::getStatus, DocStatus.AUDITED.getCode())
                .orderByDesc(InventoryStockTake::getId))) {
            if (!lastMap.containsKey(t.getWarehouseId()) && t.getTakeDate() != null) {
                lastMap.put(t.getWarehouseId(), t.getTakeDate());
            }
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Warehouse w : whs) {
            boolean taken = done.containsKey(w.getId());
            int overdue = taken ? 0 : (int) java.time.temporal.ChronoUnit.DAYS.between(due, today);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("warehouseId", w.getId());
            m.put("warehouseName", w.getWarehouseName());
            m.put("warehouseType", w.getWarehouseType());
            m.put("warehouseCategory", w.getWarehouseCategory());
            m.put("period", curYm);
            m.put("taken", taken);
            m.put("lastTakeDate", lastMap.get(w.getId()));
            m.put("dueDate", due);
            m.put("overdueDays", Math.max(0, overdue));
            m.put("remind", remind && !taken);
            rows.add(m);
        }
        return rows;
    }

    // ==================== 盘点范围（2026-09-16：成品 / 物料 分开） ====================

    /** 物料盘点接口级角色：跟单专员 */
    private static final String MATERIAL_TAKE_ROLE = "merchandiser";

    /** 取该范围内的启用仓库 id（用于盘点列表/看板过滤） */
    private List<Long> scopeWarehouseIds(StockTakeScope scope) {
        return warehouseMapper.selectList(new LambdaQueryWrapper<Warehouse>()
                        .eq(Warehouse::getStatus, 1))
                .stream().filter(scope::test).map(Warehouse::getId).toList();
    }

    /** 由仓库实际归属判定盘点范围（category 为空的异常数据返回 null，不参与任何范围） */
    private StockTakeScope scopeOf(Warehouse w) {
        if (w == null) return null;
        if (StockTakeScope.PRODUCT.test(w)) return StockTakeScope.PRODUCT;
        if (StockTakeScope.MATERIAL.test(w)) return StockTakeScope.MATERIAL;
        return null;
    }

    /** 按盘点单所属仓库校验接口级权限 */
    private void assertRoleForTake(InventoryStockTake t) {
        if (t == null) return;
        assertRoleForScope(scopeOf(warehouseMapper.selectById(t.getWarehouseId())));
    }

    /**
     * 物料盘点接口级权限（2026-09-16 用户要求）：**仅跟单专员**可操作物料仓的盘点单
     * （建单/录实盘/审核/反审核/作废/看明细）。
     * <p>管理员（admin / super_admin）按项目惯例保留兜底 —— 避免管理员界面可见但接口 403。</p>
     */
    private void assertRoleForScope(StockTakeScope scope) {
        if (scope != StockTakeScope.MATERIAL) return;
        boolean allowed;
        try {
            allowed = StpUtil.hasRole(MATERIAL_TAKE_ROLE)
                    || StpUtil.hasRole(SystemConstants.ADMIN_ROLE_CODE)
                    || StpUtil.hasRole(SystemConstants.SUPER_ADMIN_ROLE_CODE);
        } catch (Exception e) {
            allowed = false; // 无登录上下文（定时任务/内部调用）→ 从严拒绝
        }
        if (!allowed) throw new BusinessException(403, "物料库存盘点仅限跟单专员操作");
    }

    // ==================== 内部实现 ====================

    /**
     * 按差异调整库存；reverse=true 时反向冲回。
     *
     * <p>【C3 口径 · 2026-09-12 定稿：刻意复用，不再改动】审核/反审核**共用** {@code STOCK_TAKE_IN/STOCK_TAKE_OUT}：
     * 这两个 code 表意的是"库存因盘点增加/减少"（盘盈/盘亏方向），不表意"审核/反审核"；
     * 反审核只是把 delta 取反（原盘盈的反审核会写成 {@code STOCK_TAKE_OUT} 的负数），业务动作可用
     * {@code related_bill_id}（盘点单）+ 盘点单自身的差异正负还原。不新增 {@code STOCK_TAKE_UN_AUDIT_*}：
     * 历史流水无法回填新 code，报表反而要同时认两套。</p>
     */
    /**
     * F6（2026-09-18）：明细行的「当前账面」——产品按 (仓库,产品,品质,形态)、物料按 (仓库,物料,形态) 实时取。
     * <p>盘点单创建时写入的 {@code book_quantity} 只是**快照**，不能拿它当结算口径（见 §12.107.3-F6）。
     * P0-3 收尾（2026-09-25）：定位加入库存形态，防止仅形态不同的两行库存串行读取（不并表）。</p>
     */
    private BigDecimal currentBook(Long warehouseId, InventoryStockTakeItem it) {
        if (it.getProductId() != null) {
            return nz(stockService.getQuantity(warehouseId, it.getProductId(), it.getQualityType(), formOf(it)));
        }
        if (it.getMaterialId() != null) {
            return nz(stockService.getMaterialQuantity(warehouseId, it.getMaterialId(), formOf(it)));
        }
        return BigDecimal.ZERO;
    }

    /** 明细行形态兜底：迁移前的存量行可能为空，一律按 MATERIAL 处理（与 DB 默认值一致） */
    private String formOf(InventoryStockTakeItem it) {
        return it.getStockForm() == null || it.getStockForm().isBlank()
                ? WarehouseStock.FORM_MATERIAL : it.getStockForm();
    }

    /**
     * 审核前按当前账面兜底重算（F6）：
     * <ul>
     *   <li>已有差异的行（用户盘出差异）→ 差异按<b>当前账面</b>重算 ⇒ 审核后库存必然等于实盘；</li>
     *   <li>无差异的行 → 只把账面刷新为当前值、差异保持 0（不把库存拉回历史快照）。</li>
     * </ul>
     * 重算结果持久化，使单据显示、反审核冲回（按存储差异取反）三者口径一致。
     */
    private void refreshBookAtAudit(InventoryStockTake t) {
        for (InventoryStockTakeItem it : getItems(t.getId())) {
            if (it.getActualQuantity() == null) continue;
            BigDecimal liveBook = currentBook(t.getWarehouseId(), it);
            boolean hasDiff = nz(it.getDiffQuantity()).compareTo(BigDecimal.ZERO) != 0;
            BigDecimal newDiff = hasDiff ? it.getActualQuantity().subtract(liveBook) : BigDecimal.ZERO;
            BigDecimal newActual = hasDiff ? it.getActualQuantity() : liveBook;
            if (nz(it.getBookQuantity()).compareTo(liveBook) == 0
                    && nz(it.getDiffQuantity()).compareTo(newDiff) == 0
                    && nz(it.getActualQuantity()).compareTo(newActual) == 0) {
                continue;
            }
            InventoryStockTakeItem u = new InventoryStockTakeItem();
            u.setId(it.getId());
            u.setBookQuantity(liveBook);
            u.setActualQuantity(newActual);
            u.setDiffQuantity(newDiff);
            itemMapper.updateById(u);
        }
    }

    /** null 视为 0 */
    private BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private void applyDiff(InventoryStockTake t, boolean reverse) {
        List<InventoryStockTakeItem> items = getItems(t.getId());
        for (InventoryStockTakeItem it : items) {
            BigDecimal diff = it.getDiffQuantity() != null ? it.getDiffQuantity() : BigDecimal.ZERO;
            if (diff.compareTo(BigDecimal.ZERO) == 0) continue;
            BigDecimal delta = reverse ? diff.negate() : diff;
            if (it.getProductId() != null) {
                // P0-3 收尾：差异写回**同形态**库存行（走带 stockForm 的重载），不同形态互不串行
                stockService.changeStock(t.getWarehouseId(), it.getProductId(), delta,
                        delta.compareTo(BigDecimal.ZERO) > 0 ? StockChangeType.STOCK_TAKE_IN : StockChangeType.STOCK_TAKE_OUT,
                        t.getTakeNo(), RelatedBillType.STOCK_TAKE, null, t.getId(), it.getQualityType(), formOf(it));
                // 盘盈无单价：成本为空时用最近进价兜底，避免"有库存无成本"
                if (delta.compareTo(BigDecimal.ZERO) > 0) costService.fillProductCostIfEmpty(it.getProductId());
            } else if (it.getMaterialId() != null) {
                stockService.changeMaterialStock(t.getWarehouseId(), it.getMaterialId(), delta,
                        delta.compareTo(BigDecimal.ZERO) > 0 ? StockChangeType.STOCK_TAKE_IN.getCode() : StockChangeType.STOCK_TAKE_OUT.getCode(),
                        t.getTakeNo(), RelatedBillType.STOCK_TAKE, null, t.getId(), t.getId(), formOf(it));
                if (delta.compareTo(BigDecimal.ZERO) > 0) costService.fillMaterialCostIfEmpty(it.getMaterialId());
            }
        }
        log.info("盘点{} {} 差异调整 {} 行", reverse ? "反审核回滚" : "审核", t.getTakeNo(), items.size());
    }

    private String genNo() {
        String d = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String pat = BillPrefix.STOCK_TAKE + d;
        InventoryStockTake last = takeMapper.selectOne(new LambdaQueryWrapper<InventoryStockTake>()
                .likeRight(InventoryStockTake::getTakeNo, pat)
                .orderByDesc(InventoryStockTake::getTakeNo).last("LIMIT 1"));
        int seq = 1;
        if (last != null && last.getTakeNo() != null) {
            try {
                seq = Integer.parseInt(last.getTakeNo().substring(last.getTakeNo().length() - 3)) + 1;
            } catch (Exception e) {
                seq = 1;
            }
        }
        return BillPrefix.STOCK_TAKE + d + String.format("%03d", seq);
    }
}
