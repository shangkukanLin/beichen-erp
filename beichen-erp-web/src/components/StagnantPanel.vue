<script setup lang="ts">
/**
 * 滞销（呆滞）分析面板 —— 有库存 × 一段时间没有销售（2026-10-02 用户要求）。
 *
 * <p><b>两个页面共用本组件（同一份口径与列宽预算）</b>：</p>
 * <ul>
 *   <li>「成品库存情况」`views/inventory/product-stock/index.vue`（仓管视角，带品牌/仓库/产品筛选）；</li>
 *   <li>「产品分析」`views/analysis/product.vue`（经营视角，不带筛选 —— 滞销是**时点**概念，不受区间影响）。</li>
 * </ul>
 * 数据一律取 `GET /api/warehouse/stock/stagnant/page`（该前缀是"读共享"的基础数据读，两页都可读，跨页读审计不判违规）。
 *
 * <p><b>为什么是独立卡片而不是给主表加列</b>：本页主表 12 列实测 colSum 971 = avail 971（margin 0），
 * 再插 4 列必然出现横向滚动条，而"所有列表一行显示、不许左右滑动"是本仓用户口径
 * （`tools/regression/scan-table-overflow.ps1` 的 margin &lt; -2 直接判 FAIL）。故本组件自成一张表。</p>
 *
 * <p><b>现在只有一个天数（2026-10-09 用户口径变更，同一天两次）</b>：`noSaleDays`（默认 15）是**滞销判定线** ——
 * 含义为「距**最后销售日**超过此天数没有销售」；**从未有过销售记录**的产品改按**最近来货日**
 * （采购入库 / 委外加工回货）起算，两者都没有的一律算滞销。
 * 原先独立的「统计窗口（默认 90 天，可选 30/60/90/180）」按用户「90 天这个不要」⇒ 连同「期间销量 / 周转天数」
 * 两列一并移除；阈值仍由后端随响应回传（`threshold`），本组件文案一律用回传值，不写死。</p>
 *
 * <p>刷新的驱动方式：**由父页面调用 {@link reload}**（组件自身不在 mounted 里拉数据，避免父页面
 * `useDomainRefresh` 的"首次挂载拉一次"与组件挂载各拉一次 ⇒ 首屏白拉两遍）。</p>
 */
import { computed, nextTick, onUnmounted, reactive, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { QuestionFilled, WarningFilled } from '@element-plus/icons-vue'
import * as XLSX from 'xlsx'
import request from '@/utils/request'
import { localDate } from '@/utils/date'
import { STAGNANT_KPI_FORMULA } from '@/utils/kpiFormula'

const props = withDefaults(defineProps<{
  /** 卡片标题（两个页面文案不同：仓管页用默认，产品分析页可写「滞销与呆滞」之类） */
  title?: string
  /** 父页面的筛选条件（可选）：品牌 / 仓库（逗号分隔 id）/ 产品关键字。父页面**查询/重置时**才更新，
   *  这样输入框每敲一个字不会触发本组件重查（与父页面"点查询才生效"一致）。 */
  brandId?: number | null
  warehouseIds?: string
  productName?: string
}>(), {
  title: '滞销分析',
  brandId: null,
  warehouseIds: '',
  productName: '',
})

const emit = defineEmits<{
  (e: 'row-click', row: any): void
  (e: 'product-click', row: any): void
}>()

// 2026-10-09 用户口径变更：原「统计窗口（默认 90 天，可选 30/60/90/180）」整体取消（原话「90 天这个不要」）
// ⇒ RECENT_OPTIONS / st.recentDays / 「期间销量」「周转天数」两列一并移除；
//    「15 天」的起算点改为「最近一次来货日（委外加工回货 / 成品购入）与最后销售日里较晚的那个」。

const st = reactive({
  noSaleDays: 15,        // 滞销判定线（可调）：距最近来货或销售超过这么多天 ⇒ 滞销
  onlyStagnant: true,    // 默认只列滞销；取消 = 列全部有库存产品（等价于"给主表补上停滞天数列"）
  onlySevere: false,
  includeDiscontinued: true,
  pageNum: 1,
  pageSize: 10,
  total: 0,
})
const loading = ref(false)
const rows = ref<any[]>([])
const kpi = ref<any>({})
const buckets = ref<any[]>([])
const empty = ref(false)
let chart: echarts.ECharts | null = null

// 2026-10-09：原 recent / recentText（统计窗口回显）已随窗口一并移除；
// 「最近来货」列直接用后端回传的 lastInDate（不再有第二个天数需要在文案里拼）。

function fmt(v?: any) { return v == null ? '0' : String(Math.round(Number(v))) }
/** 金额展示：千分位 + 2 位小数 */
function fmtAmount(v?: any) {
  if (v == null) return '0.00'
  return Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

/** KPI 6 张：口径写进 tooltip（数字从哪来必须可查，否则滞销金额没人敢用） */
const kpiCards = computed(() => {
  const k = kpi.value || {}
  const miss = Number(k.costMissingQty) || 0
  return [
    { label: '滞销品项', value: fmt(k.productCount), tip: STAGNANT_KPI_FORMULA.productCount, color: '#f56c6c' },
    { label: '滞销库存量', value: fmt(k.qty), tip: STAGNANT_KPI_FORMULA.qty },
    {
      label: '参考金额', value: fmtAmount(k.refAmount),
      tip: STAGNANT_KPI_FORMULA.refAmount + (miss > 0 ? '\n其中 ' + fmt(miss) + ' 件成本价未维护，未计入金额' : ''),
    },
    { label: '平均停滞天数', value: Number(k.avgStagnantDays || 0).toFixed(1), tip: STAGNANT_KPI_FORMULA.avgStagnantDays },
    { label: '滞销占比（数量）', value: Number(k.shareQty || 0).toFixed(1) + '%', tip: STAGNANT_KPI_FORMULA.shareQty },
    { label: '从未销售', value: fmt(k.neverSoldCount), tip: STAGNANT_KPI_FORMULA.neverSoldCount },
  ]
})

/** 参数拼装（列表 / 分页 / 导出共用） */
function params(pageNum: number, pageSize: number) {
  const p: any = {
    pageNum,
    pageSize,
    noSaleDays: st.noSaleDays,
    // ⚠️ 三个布尔一律**显式传**（不能"为默认值时省略"）：后端把缺省当成 true（只看滞销 / 含停售），
    //    省略参数时"取消勾选"就完全没有效果 —— 2026-10-02 浏览器实测抓到过（取消勾选后仍是 1 行）。
    onlyStagnant: st.onlyStagnant,
    onlySevere: st.onlySevere,
    includeDiscontinued: st.includeDiscontinued,
  }
  if (props.warehouseIds) p.warehouseIds = props.warehouseIds
  if (props.brandId) p.brandId = props.brandId
  if (props.productName) p.productName = props.productName
  return p
}

async function reload() {
  loading.value = true
  try {
    const res = await request.get<any, any>('/warehouse/stock/stagnant/page', { params: params(st.pageNum, st.pageSize) })
    rows.value = res?.records || []
    st.total = res?.total || 0
    kpi.value = res?.kpi || {}
    buckets.value = res?.buckets || []
  } catch {
    rows.value = []; st.total = 0; kpi.value = {}; buckets.value = []
  } finally { loading.value = false }
  // 图表需等容器渲染完再初始化，否则会按 0 宽高绘制（与产品分析/销售分析同做法）
  await nextTick()
  setTimeout(renderBar, 60)
}

/** 停滞天数分桶柱状图（数量口径）；7 个区间由后端固定给出，与阈值/窗口无关 */
function renderBar() {
  const el = document.getElementById('stagnantBar')
  if (!el) return
  chart = chart || echarts.init(el)
  const list = buckets.value || []
  const has = list.some((b: any) => Number(b.qty) > 0)
  empty.value = !has
  if (!has) { chart.clear(); return }
  chart.setOption({
    tooltip: {
      trigger: 'axis', axisPointer: { type: 'shadow' },
      formatter: (ps: any) => {
        const p = ps && ps[0]
        if (!p) return ''
        const b = list[p.dataIndex] || {}
        return b.label + '<br/>库存数量：' + fmt(b.qty) + '<br/>品项数：' + fmt(b.products)
          + '<br/>占全部库存：' + shareOf(b.qty)
      },
    },
    grid: { left: 8, right: 16, top: 30, bottom: 4, containLabel: true },
    xAxis: { type: 'category', data: list.map((b: any) => b.label), axisLabel: { fontSize: 11 } },
    yAxis: { type: 'value', axisLabel: { fontSize: 11 } },
    series: [{
      name: '库存数量', type: 'bar', barMaxWidth: 48,
      data: list.map((b: any) => Number(b.qty) || 0),
      itemStyle: { color: '#409eff' },
      label: { show: true, position: 'top', fontSize: 11 },
    }],
  }, true)
  chart.resize()
}

/** 某分桶占全部库存的比（图表 tooltip 用；分母 = KPI 的 allQty） */
function shareOf(qty: any) {
  const all = Number(kpi.value?.allQty) || 0
  const v = Number(qty) || 0
  if (all <= 0) return '—'
  const pct = v * 100 / all
  return pct < 0.1 ? '<0.1%' : pct.toFixed(1) + '%'
}

/** 阈值 / 窗口 / 勾选变化：回第一页重查 */
function onFilterChange() { st.pageNum = 1; reload() }
function onSizeChange(v: number) { st.pageSize = v; st.pageNum = 1; reload() }

/** 导出：按当前筛选**全量**拉取（pageSize=9999，不受分页限制），列与清单一致 */
async function exportList() {
  let data: any[] = rows.value || []
  try {
    const res = await request.get<any, any>('/warehouse/stock/stagnant/page', { params: params(1, 9999) })
    if (Array.isArray(res?.records)) data = res.records
  } catch { /* 拉取失败：退回当前页已加载数据 */ }
  const cols = ['SKU', '产品名称', '品牌', '最后销售日', '最近来货', '停滞天数', '总库存', '参考金额', '状态']
  const statusText = (r: any) => (r.discontinued ? '停售' : (r.productStatus === 'DEVELOPING' ? '研发中' : '正常'))
  const aoa: (string | number)[][] = [
    [`滞销清单（判定：距最近来货或销售超过 ${st.noSaleDays} 天没有销售${st.onlyStagnant ? '' : '；含未滞销产品'}，导出时间：${new Date().toLocaleString('zh-CN')}，共 ${data.length} 行）`],
    [],
    cols,
  ]
  data.forEach((r: any) => {
    aoa.push([
      r.sku || '',
      r.productName || '',
      r.brandName || '—',
      r.lastSaleDate || '从未销售',
      r.lastInDate || '无来货',
      r.stagnantDays == null ? '—' : Number(r.stagnantDays),
      Number(r.totalQuantity ?? 0),
      r.costMissing ? '成本未维护' : Number(r.refAmount ?? 0),
      statusText(r),
    ])
  })
  const ws = XLSX.utils.aoa_to_sheet(aoa)
  ws['!merges'] = [{ s: { r: 0, c: 0 }, e: { r: 0, c: cols.length - 1 } }]
  ws['!cols'] = [{ wch: 16 }, { wch: 24 }, { wch: 14 }, { wch: 12 }, { wch: 12 }, { wch: 10 }, { wch: 10 }, { wch: 14 }, { wch: 8 }]
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, '滞销清单')
  XLSX.writeFile(wb, `滞销清单_${localDate()}.xlsx`)
}

// 父页面筛选变化（点查询/重置后才更新）⇒ 回第一页重查
watch(() => [props.brandId, props.warehouseIds, props.productName], () => { st.pageNum = 1; reload() })

// keep-alive 反复进出会累积 ECharts 实例 ⇒ 组件真正卸载时释放
onUnmounted(() => {
  try { chart?.dispose() } catch { /* 已释放 */ }
  chart = null
})

defineExpose({ reload })
</script>

<template>
  <el-card shadow="never" class="stagnant-card">
    <template #header>
      <div class="st-head">
        <span class="st-title">{{ props.title }}</span>
        <span class="st-hint">
          口径：有库存 × **距「最后销售日」**超过阈值没有销售 = 滞销；**从未有过销售记录**的按**最近来货日**起算。
          最后销售日 = 已审核销售单的**建单日**；移仓 / 品质重分类 / 退货出库 / 反审核都不算"卖过"（否则会漏报）
        </span>
      </div>
    </template>
    <div class="st-controls">
      <!-- 2026-10-09：两个天数并排、且都没有说明，正是用户两次追问的来源 ⇒ 这里补上 tooltip ✓ -->
      <el-tooltip placement="top">
        <template #content>
          距 <b>最后销售日</b> 超过此天数没有销售 ⇒ 滞销。<br />
          <b>从未有过销售记录</b>的产品，改按 <b>最近来货日</b>（采购入库 / 委外加工回货）起算；<br />
          两者都没有的（既无销售也无来货）一律算滞销。
        </template>
        <span class="st-label" style="cursor:help;border-bottom:1px dashed currentColor">滞销判定</span>
      </el-tooltip>
      <el-input-number v-model="st.noSaleDays" :min="1" :max="3650" :step="5" controls-position="right"
                       size="small" style="width:118px" @change="onFilterChange" />
      <span class="st-label">天没有销售</span>
      <el-checkbox v-model="st.onlyStagnant" label="仅看滞销" @change="onFilterChange" />
      <el-checkbox v-model="st.onlySevere" label="仅看严重滞销（>180 天）" @change="onFilterChange" />
      <el-checkbox v-model="st.includeDiscontinued" label="含停售产品" @change="onFilterChange" />
      <el-button size="small" :icon="'Download'" @click="exportList">导出滞销清单</el-button>
    </div>
    <!-- KPI：恒按"全部有库存产品"统计（不受上面勾选影响），否则一勾选数字就跳 -->
    <div class="stat-grid">
      <div v-for="k in kpiCards" :key="k.label" class="stat-card">
        <div class="stat-label">
          {{ k.label }}
          <el-tooltip placement="top" effect="dark" :show-after="100">
            <template #content><div class="kpi-formula">{{ k.tip }}</div></template>
            <el-icon class="kpi-help"><QuestionFilled /></el-icon>
          </el-tooltip>
        </div>
        <div class="stat-value" :style="k.color ? { color: k.color } : {}">{{ k.value }}</div>
      </div>
    </div>
    <div class="bar-body">
      <!-- 空态用 v-show 叠加在图上：图容器不能被 v-if 删掉（ECharts 实例挂在被删的 DOM 上，下次画不出来） -->
      <div class="bar-empty" v-show="empty">当前筛选下没有有库存的产品</div>
      <div id="stagnantBar" class="bar-chart" />
    </div>
    <!-- 列宽合计 ≈ 864（SKU98/名称130/品牌84/最后销售日100/最近来货100/停滞92/库存76/金额116/状态68
         ≤ 内容区 ~948）。2026-10-09：随「统计窗口」取消，「期间销量」「周转天数」两列删除（原各 92/84），
         新增「最近来货」——**从未有过销售记录**的产品靠它起算，用户要能看见"为什么它被算成滞销"。 -->
    <el-table v-loading="loading" :data="rows" border stripe @row-click="(r: any) => emit('row-click', r)">
      <el-table-column prop="sku" label="SKU" min-width="98" show-overflow-tooltip />
      <el-table-column label="产品名称" min-width="130" show-overflow-tooltip>
        <template #default="{ row }">
          <el-button type="primary" link @click.stop="emit('product-click', row)">{{ row.productName }}</el-button>
        </template>
      </el-table-column>
      <el-table-column prop="brandName" label="品牌" min-width="84" show-overflow-tooltip>
        <template #default="{ row }">{{ row.brandName || '—' }}</template>
      </el-table-column>
      <el-table-column label="最后销售日" min-width="100" align="center">
        <template #default="{ row }">
          <span v-if="row.lastSaleDate">{{ row.lastSaleDate }}</span>
          <span v-else class="never">从未销售</span>
        </template>
      </el-table-column>
      <el-table-column label="最近来货" min-width="100" align="center">
        <template #default="{ row }">
          <span v-if="row.lastInDate" title="最近一次来货：采购入库（成品购入）或委外收货入库（委外加工回货）">{{ row.lastInDate }}</span>
          <el-tooltip v-else content="没有采购入库 / 委外收货入库流水 ⇒ 起算点只看最后销售日（两者都没有的，一律算滞销）" placement="top">
            <span class="never">无来货</span>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="停滞天数" min-width="92" align="right">
        <template #default="{ row }">
          <span title="滞销起算点 = 最后销售日；从未有过销售记录的按最近来货日算"
                :style="{ color: row.severe ? '#f56c6c' : (row.stagnant ? '#e6a23c' : ''), fontWeight: (row.severe || row.stagnant) ? 600 : 400 }">
            {{ row.stagnantDays == null ? '—' : row.stagnantDays }}
          </span>
          <el-tooltip v-if="row.severe" content="严重滞销：距起算点超过 180 天（从未销售且无来货的按在库已超 180 天判定）" placement="top">
            <el-icon style="vertical-align:-2px"><WarningFilled /></el-icon>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="总库存" min-width="76" align="right">
        <template #default="{ row }"><strong>{{ fmt(row.totalQuantity) }}</strong></template>
      </el-table-column>
      <el-table-column label="参考金额" min-width="116" align="right">
        <template #default="{ row }">
          <span v-if="row.costMissing" class="never" title="产品成本价未维护，算不出占用金额（不计入 KPI 参考金额）">—</span>
          <span v-else>{{ fmtAmount(row.refAmount) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" min-width="68" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.discontinued" type="info" size="small">停售</el-tag>
          <el-tag v-else-if="row.productStatus === 'DEVELOPING'" type="warning" size="small">研发中</el-tag>
          <span v-else>正常</span>
        </template>
      </el-table-column>
    </el-table>
    <div class="pagination">
      <el-pagination v-model:current-page="st.pageNum" v-model:page-size="st.pageSize"
        :page-sizes="[10, 20, 50, 100]" :total="st.total" layout="total, sizes, prev, pager, next, jumper"
        background @size-change="onSizeChange" @current-change="reload" />
    </div>
  </el-card>
</template>

<style scoped>
/* 2026-10-02 建立：本组件原先是「成品库存情况」页内的一段（该页注释里保留了"为什么不能加列"的实测依据）。
   卡片上边距：产品分析页的卡片之间靠全局样式排布，这里补一个统一间距，两个页面观感一致。 */
.stagnant-card { margin-top: 12px; }
.st-head { display: flex; align-items: baseline; gap: 10px; flex-wrap: wrap; }
.st-title { font-weight: 600; }
.st-hint { font-size: var(--app-font-xs); color: var(--app-text-secondary); }
/* 控件行：允许折行（窄屏下"阈值 + 窗口 + 3 个勾选 + 导出"一行放不下），不参与查询条那套同行断言 */
.st-controls { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; margin-bottom: 12px; }
.st-label { font-size: var(--app-font-xs); color: var(--app-text-secondary); }
/* KPI 卡：auto-fit 每行按宽度摆 4~6 张，窄屏自动降列 */
.stat-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(164px, 1fr)); gap: 12px; margin-bottom: 12px; }
.stat-card { background: var(--el-fill-color-light); border-radius: 8px; padding: 12px 16px; }
.stat-label { font-size: var(--app-font-xs); color: var(--el-text-color-secondary); }
.stat-value { font-size: var(--app-font-num-sm); font-weight: 600; }
/* 分桶柱状图：容器必须有确定高度，否则 ECharts 会按 0 高绘制 */
.bar-body { position: relative; margin-bottom: 12px; }
.bar-chart { height: 260px; }
.bar-empty { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; font-size: var(--app-font-xs); color: var(--el-text-color-secondary); }
/* 「从未销售 / 成本未维护 / 算不出周转」统一置灰，避免与真实 0 混淆 */
.never { color: #c0c4cc; }
</style>
