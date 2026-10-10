<template>
  <div class="page-list">
    <el-card shadow="never" class="query-card">
      <div class="query-bar">
        <!-- 2026-09-29（用户口径「产品/品牌/所在仓库 三个搜索项要显示在一行，不要两行」）：
             查询条是全局 grid「1fr（表单）+ auto（按钮组）」，实测视口 1262 时表单列只有 **614px**；
             原先 180/160/240 ⇒ 项宽 220+200+308（+每个 item 32px margin-right）= 826 > 614 ⇒ 「所在仓库」折行。
             现按实测收窄到 **145/125/145**（项宽 ≈ 185+165+213 = 563）＋ 2×12px gap = **587 ≤ 614**（余量 27）⇒
             三项锁死一行；「仅看低于安全库存」勾选仍留在第二行（口径只要求三个搜索项一行）。
             ⚠️ 改这三个宽度前请跑 verify-stock-querybar-oneline.ps1（它断言三项同一行且不溢出）。 -->
        <el-form :inline="true" :model="query" class="query-form">
          <el-form-item label="产品">
            <el-input v-model="query.productName" placeholder="产品名称或 SKU" clearable style="width:145px" @keyup.enter="doQuery" />
          </el-form-item>
          <el-form-item label="品牌">
            <RemoteSelect v-model="query.brandId" add-route="/inventory/brand" :fetch="fetchBrands" label-key="brandName" placeholder="全部" style="width:125px" domain="brand" />
          </el-form-item>
          <el-form-item label="所在仓库">
            <RemoteSelect v-model="query.warehouseIds" multiple collapse-tags collapse-tags-tooltip
              add-route="/inventory/warehouse" :fetch="fetchWarehouses" :label-key="(row:any)=>row.warehouseName" placeholder="全部" style="width:145px" domain="warehouse" />
          </el-form-item>
          <el-form-item label="">
            <el-checkbox v-model="query.onlyLowStock" label="仅看低于安全库存" />
          </el-form-item>
        </el-form>
        <div class="toolbar">
          <el-button type="primary" :icon="'Search'" @click="doQuery">查询</el-button>
          <el-button :icon="'Refresh'" @click="resetQuery">重置</el-button>
          <el-button :icon="'Download'" @click="exportProductStock">导出 Excel</el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <!--
        每行一个产品：数量为跨仓库汇总值，点击行进入详情看该产品在各仓库的分布。
        所有列统一用 min-width（不用固定 width）：Element Plus 会把剩余空间按 min-width 比例分摊给各列，
        因此窄屏刚好放下、宽屏自动拉伸铺满整页，不会出现留白或横向滚动。
        2026-09-26 B5a（用户口径「数据显示完整 + 产品可点」，实测驱动）：
        ①品牌 min78→**108**（实测需 107，原先全部行被截断）；
        ②产品名称做成链接进产品详情（原先只能点整行/操作列）；
        ③为抵平把 SKU 95→88、产品名称 min125→116、待整理 74→72、总库存 74→72、安全库存 82→80、分布仓库 80→74
          ⇒ 声明合计 **938** ✓（容器约 948；全部 min-width ⇒ 宽屏自动铺满）。
        ⚠️ 上列三处数值已被 2026-09-26 B6 记录更新（现值：SKU min98、品牌 min100、分布仓库 min82、声明合计 948 ≤ 容器 956）。
      -->
      <!-- 2026-10-09 用户需求：成品仓库存金额。本表 12 列 colSum 已 = avail（余量 0），
           再加一列必然横向滚动 ⇒ 这里用合计条（含成品/物料/合计 + 按仓库明细 + 成本未维护提示）；
           逐行金额在「仓库分布」明细页与仓库详情页给出（那两处列宽够）。 -->
      <StockAmountBar mode="product" />
      <el-table v-loading="loading" :data="rows" border stripe @row-click="goDetail">
        <!-- 2026-09-26 B6（实测）：SKU 是最长 10 位的业务编码（最长样本「SKU-000012」正文需 93px），
             min88 会把 10 行 SKU 全部省略 ⇒ min88→**98**（93 + 内边距 16 + 边框 1 的最省值再留 2px 余量）。 -->
        <el-table-column prop="sku" label="SKU" min-width="98" show-overflow-tooltip />
        <el-table-column label="产品名称" min-width="116" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button type="primary" link @click.stop="goProduct(row)">{{ row.productName }}</el-button>
          </template>
        </el-table-column>
        <el-table-column prop="brandName" label="品牌" min-width="100" show-overflow-tooltip>
          <template #default="{ row }">{{ row.brandName || '—' }}</template>
        </el-table-column>
        <!-- 品质数量列用紧凑数字（非 tag）：五档品质 + 汇总列要在一屏内放得下，避免横向滚动。
             2026-10-10 宽度配平：本表要"删「分布仓库」列、加「库存金额」列"⇒ 必须从别处让宽 ✓
             这里 4 个 2~4 字数字列共让出 36px（60→56、72→64），使合计仍 946 ≤ 948 ✓ 不引入横向滚动。 -->
        <el-table-column label="A规" min-width="56" align="right">
          <template #default="{ row }"><span :class="qtyClass(row.qtyA, 'a')">{{ fmt(row.qtyA) }}</span></template>
        </el-table-column>
        <el-table-column label="B规" min-width="56" align="right">
          <template #default="{ row }"><span :class="qtyClass(row.qtyB, 'b')">{{ fmt(row.qtyB) }}</span></template>
        </el-table-column>
        <el-table-column label="C规" min-width="56" align="right">
          <template #default="{ row }"><span :class="qtyClass(row.qtyC, 'c')">{{ fmt(row.qtyC) }}</span></template>
        </el-table-column>
        <el-table-column label="不良" min-width="56" align="right">
          <template #default="{ row }"><span :class="qtyClass(row.qtyDefect, 'defect')">{{ fmt(row.qtyDefect) }}</span></template>
        </el-table-column>
        <el-table-column label="待整理" min-width="64" align="right">
          <template #default="{ row }">
            <span :class="qtyClass(row.qtyPending, 'pending')" title="压在成品仓、等待退货整理的库存（品质待整理 PENDING）">{{ fmt(row.qtyPending) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="总库存" min-width="64" align="right">
          <template #default="{ row }"><strong>{{ fmt(totalQty(row)) }}</strong></template>
        </el-table-column>
        <!-- 2026-10-09 用户需求：安全库存**点一下就能改**（弹框）。
             ① 只改 `product.safety_stock` 一列（独立端点，不复用整实体更新 ⇒ 不触发规格必填校验）；
             ② 该值是**产品级**（一个产品一份、所有仓库共用）⇒ 弹框里向用户写明；
             ③ 走"点击 → 弹框"而非列内输入框 ⇒ **列宽零变化**，不触碰 scan-table-overflow / scan-col-truncation；
             ④ 无 base:product（产品写权限）的账号仍渲染为只读文本（体验层可点与否，真正边界在后端）；
             ⑤ @click.stop：本表整行可点进详情（@row-click）⇒ 必须阻止冒泡，否则点一下弹框又跳页 ✗。 -->
        <el-table-column label="安全库存" min-width="76" align="right">
          <template #default="{ row }">
            <span :class="{ 'safety-edit': canEditSafety }" :style="safetyCellStyle(row)"
                  :title="canEditSafety ? '点击修改安全库存（产品级，该产品所有仓库共用）' : ''"
                  @click.stop="canEditSafety ? openSafety(row) : undefined">
              <template v-if="row.safetyStock">{{ fmt(row.safetyStock) }}<el-icon v-if="row.lowStock" style="vertical-align:-2px"><WarningFilled /></el-icon></template>
              <template v-else>未设置</template>
            </span>
          </template>
        </el-table-column>
        <!-- 2026-10-10 用户口径：把「分布仓库」列**去掉**，换成**「库存金额」**列（逐行金额；原先只在合计条上给）。
             ① 金额口径**照抄仓库现成的 StockCosts**（= 数量 × 单价；单价 cost_price → last_in_price 兜底）
                —— 并且**直接用后端已算好的字段**：`product-summary/page` 早已回 `stockAmount` / `costMissing`
                ⇒ **零后端改动** ✓（见 WarehouseStockController:335-336）。
             ② 成本未维护时**绝不显示 0.00**：StockAmountCell 会显示「成本未维护」（静默显示 0 会让用户以为算错 ✗
                —— 这正是 StockCosts 注释里警告过的）；该组件与物料库存列表同源，口径一致 ✓。
             ③ **仓库个数信息不丢**：移到操作列按钮文案里 —— `分布仓库（N）`，N = row.warehouseCount ✓。
                ⚠️ 原按钮文案是「仓库分布」（与用户口径字序相反）⇒ 这里按用户口径改为「分布仓库」✓。
             列宽：删 82、新增 88、操作 88→116（按钮多 4 个字），并从数字列让出 36 ⇒ 合计仍 946 ≤ 948 ✓。 -->
        <el-table-column label="库存金额" min-width="88" align="right">
          <template #default="{ row }"><StockAmountCell :row="row" /></template>
        </el-table-column>
        <el-table-column label="操作" min-width="116" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="goDetail(row)">分布仓库（{{ row.warehouseCount ?? 0 }}）</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination">
        <el-pagination v-model:current-page="page.pageNum" v-model:page-size="page.pageSize"
          :page-sizes="[10, 20, 50, 100]" :total="page.total" layout="total, sizes, prev, pager, next, jumper"
          background @size-change="onSizeChange" @current-change="load" />
      </div>
    </el-card>

    <!-- 滞销分析：独立卡片（2026-10-02 用户要求；两个页面共用 components/StagnantPanel.vue）。
         为什么不做成主表加列：主表 12 列实测 colSum 971 = avail 971（margin 0），再加 4 列必然横向滚动 ✗
         —— 详见 tools/regression/scan-table-overflow.ps1 与该组件的说明。 -->
    <StagnantPanel ref="stagnantRef"
      :brand-id="applied.brandId" :warehouse-ids="applied.warehouseIds" :product-name="applied.productName"
      @row-click="goDetail" @product-click="goProduct" />

    <!-- 安全库存弹框（2026-10-09 用户需求：在成品库存详情列表里点安全库存直接改） -->
    <el-dialog v-model="safetyDialog" :title="`修改安全库存 - ${safetyRow?.productName || ''}`" width="440px" append-to-body>
      <el-form label-width="92px">
        <el-form-item label="产品">
          <span>{{ safetyRow?.sku ? safetyRow.sku + ' | ' : '' }}{{ safetyRow?.productName || '' }}</span>
        </el-form-item>
        <el-form-item label="安全库存">
          <el-input-number v-model="safetyValue" :min="0" :precision="0" :step="1" style="width:100%" />
        </el-form-item>
      </el-form>
      <!-- 粒度提醒（必须写清：这是**产品级**，不是按仓库） -->
      <div style="color:var(--app-text-secondary);font-size:var(--app-font-xs);line-height:1.6">
        安全库存是<b>产品级</b>设置：同一产品在<b>所有仓库</b>共用这一个值（不按仓库分别设置）。<br />
        当前总库存 <b>{{ fmt(totalQty(safetyRow || {})) }}</b>；保存后「仅看低于安全库存」与低库存标记会按新值重算。填 0 表示未设置。
      </div>
      <template #footer>
        <el-button @click="safetyDialog = false">取消</el-button>
        <el-button type="primary" :loading="safetySaving" @click="saveSafety">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { localDate } from '@/utils/date'
import { reactive, ref, onMounted, computed } from 'vue'
import { useDomainRefresh } from '@/utils/dataFreshness'
import { useRouter } from 'vue-router'
import { WarningFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { WarehouseCategory, WarehouseType } from '@/api/enums'
import { updateProductSafetyStock } from '@/api/product'
import request from '@/utils/request'
import * as XLSX from 'xlsx'
import RemoteSelect from '@/components/RemoteSelect.vue'
import StagnantPanel from '@/components/StagnantPanel.vue'
import StockAmountBar from '@/components/StockAmountBar.vue'
// 2026-10-10：逐行「库存金额」单元格 —— 与物料库存列表**同一个组件**（口径一致：数量 × 单价，
// 单价 cost_price → last_in_price 兜底；成本未维护时显示「成本未维护」而不是 0.00 ✓）。
import StockAmountCell from '@/components/StockAmountCell.vue'

const router = useRouter()

// 仓库下拉：与成品库存查询一致，只列自有成品仓（排除辅料仓，辅料仓放物料不放成品）
const fetchWarehouses = (kw: string) =>
  request.get('/warehouse/page', { params: { pageSize: 500, warehouseName: kw, warehouseCategory: WarehouseCategory.INVENTORY } })
    .then((res: any) => {
      res.records = (res.records || []).filter((w: any) => w.warehouseType !== WarehouseType.AUXILIARY)
      return res
    })
const fetchBrands = (kw: string) => request.get('/brand/page', { params: { pageSize: 200, brandName: kw } })

const query = reactive({
  productName: '',
  brandId: undefined as number | undefined,
  warehouseIds: [] as number[],
  onlyLowStock: false
})
const page = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const loading = ref(false)
const rows = ref<any[]>([])

/**
 * 导出 Excel（**全量**）：按当前筛选条件重新请求全部匹配产品（pageNum=1、pageSize=9999），
 * 不受列表分页限制；**列与页面逐列对齐**（2026-10-10 用户口径「对齐」），数量按数值写入（整数不带小数）。
 * 请求失败时退回当前页已加载数据，保证导出始终可用。
 *
 * <p>2026-10-10 对齐要点：屏上已把「分布仓库」列**换成「库存金额」**、并把仓库个数移进操作列按钮 ⇒
 * 导出同步**去掉「分布仓库」列**、并把「库存金额」排到「安全库存」之后（与屏上列序一致 ✓）。</p>
 * <p>⚠️ 金额口径与 `StockAmountCell` 对齐：**成本未维护时写「成本未维护」而不是 0** ✓ ——
 * 写 0 会让人在 Excel 里以为系统算错 ✗（这正是 StockCosts 注释警告过的坑 ✓）。</p>
 */
async function exportProductStock() {
  let data: any[] = rows.value || []
  try {
    const params: any = { pageNum: 1, pageSize: 9999 }
    // 多选用逗号分隔：与列表查询一致（axios 默认序列化成 warehouseIds[]=1，后端 @RequestParam List 收不到）
    if (query.warehouseIds?.length) params.warehouseIds = query.warehouseIds.join(',')
    if (query.brandId) params.brandId = query.brandId
    if (query.productName) params.productName = query.productName
    if (query.onlyLowStock) params.onlyLowStock = true
    const res = await request.get<any, any>('/warehouse/stock/product-summary/page', { params })
    if (Array.isArray(res?.records)) data = res.records
  } catch { /* 拉取失败：退回当前页数据 */ }
  // 列序与屏上**逐列对齐**：SKU → 产品名称 → 品牌 → A/B/C规 → 不良 → 待整理 → 总库存 → 安全库存 → 库存金额
  // （屏上的「操作」列是入口按钮，非数据 ⇒ 不进导出 ✓；原「分布仓库」列已随屏上改动删除 ✓）
  const cols = ['SKU', '产品名称', '品牌', 'A规', 'B规', 'C规', '不良', '待整理', '总库存', '安全库存', '库存金额']
  const aoa: (string | number)[][] = [
    [`成品库存汇总（导出时间：${new Date().toLocaleString('zh-CN')}，共 ${data.length} 行）`],
    [],
    cols,
  ]
  data.forEach((r: any) => {
    aoa.push([
      r.sku || '',
      r.productName || '',
      r.brandName || '—',
      Number(r.qtyA ?? 0),
      Number(r.qtyB ?? 0),
      Number(r.qtyC ?? 0),
      Number(r.qtyDefect ?? 0),
      Number(r.qtyPending ?? 0),
      Number(totalQty(r) ?? 0),
      r.safetyStock ? Number(r.safetyStock) : '',
      // 与 StockAmountCell 同口径：成本未维护明确写出，不静默写 0 ✓
      r.costMissing ? '成本未维护' : Number(r.stockAmount ?? 0),
    ])
  })
  const ws = XLSX.utils.aoa_to_sheet(aoa)
  ws['!merges'] = [{ s: { r: 0, c: 0 }, e: { r: 0, c: cols.length - 1 } }]
  ws['!cols'] = [{ wch: 16 }, { wch: 24 }, { wch: 14 }, { wch: 8 }, { wch: 8 }, { wch: 8 }, { wch: 8 }, { wch: 10 }, { wch: 10 }, { wch: 10 }, { wch: 14 }]
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, '成品库存汇总')
  XLSX.writeFile(wb, `成品库存汇总_${localDate()}.xlsx`)
}

// 数量一律整数（2026-09-16）
function fmt(v?: number) { return v == null ? '0' : String(Math.round(Number(v))) }

/** 品质数量配色：有量用品质色加粗，为 0 统一置灰，避免满屏色块干扰阅读 */
function qtyClass(v: number, type: 'a' | 'b' | 'c' | 'defect' | 'pending') {
  return (Number(v) || 0) > 0 ? `qty-${type}` : 'qty-zero'
}

function totalQty(row: any) {
  return (Number(row.qtyA) || 0) + (Number(row.qtyB) || 0) + (Number(row.qtyC) || 0)
    + (Number(row.qtyDefect) || 0) + (Number(row.qtyPending) || 0)
}

// ==================== 安全库存就地修改（2026-10-09 用户需求） ====================
// ① 该字段是**产品级**（`product.safety_stock`）⇒ 改一个产品 = 改它所有仓库共用的那一个值（弹框里写明 ✓）；
// ② 写接口 `PUT /product/{id}/safety-stock` 是独立的最小写入口（不复用整实体更新 ⇒ 不触发规格必填校验 ✓）；
// ③ 保存成功后重拉本页；同时请求层按 URL 把 `product` 域置脏 ⇒ `productStock` 级联失效（其它页面的
//    低库存标记也会跟着更新 ✓，见 utils/dataFreshness.ts 的 DOMAIN_DEPS）。
const userStore = useUserStore()
/** 只有持产品写权限（base:product）的账号才把安全库存渲染成"可点"；真正边界在后端 ApiPermGuard ✓ */
const canEditSafety = computed(() => userStore.hasPerm('base:product'))

const safetyDialog = ref(false)
const safetySaving = ref(false)
const safetyRow = ref<any>(null)
const safetyValue = ref<number>(0)
/** 目标产品 id：汇总行的产品 id 字段是 **productId**（后端聚合 key，见 WarehouseStockController 的 product-summary），
 *  不是 row.id ✗ —— 用错会往 `/product/undefined/safety-stock` 发请求、静默失败（保存后列表仍是旧值 ✓ 很难发现）。 */
const safetyProductId = ref<number | undefined>(undefined)

/** 单元格样式：可改时给"可点"的视觉提示（虚线下划线 + 手型）；只读时维持原有配色 */
function safetyCellStyle(row: any) {
  const color = row.safetyStock ? (row.lowStock ? '#f56c6c' : '#67c23a') : '#999'
  return canEditSafety.value
    ? { color, cursor: 'pointer', borderBottom: '1px dashed currentColor' }
    : { color }
}

function openSafety(row: any) {
  safetyRow.value = row
  safetyProductId.value = row.productId ?? row.id
  safetyValue.value = Number(row.safetyStock) || 0
  safetyDialog.value = true
}

async function saveSafety() {
  const row = safetyRow.value
  if (!row) return
  const id = safetyProductId.value
  if (id == null) { ElMessage.warning('未取到产品标识，无法保存（请刷新后重试）'); return }
  const v = Number(safetyValue.value)
  if (!Number.isFinite(v) || v < 0) { ElMessage.warning('安全库存必须是不小于 0 的整数'); return }
  if (!Number.isInteger(v)) { ElMessage.warning('安全库存必须是整数'); return }
  safetySaving.value = true
  try {
    await updateProductSafetyStock(id, v)
    ElMessage.success(`安全库存已更新为 ${v}`)
    safetyDialog.value = false
    await load()
  } catch { /* 提示由拦截器统一给出（缺 base:product 时会 403） */ }
  finally { safetySaving.value = false }
}

async function load() {
  loading.value = true
  try {
    const params: any = { pageNum: page.pageNum, pageSize: page.pageSize }
    // 多选用逗号分隔：axios 默认把数组序列化成 warehouseIds[]=1，后端 @RequestParam List 收不到
    if (query.warehouseIds?.length) params.warehouseIds = query.warehouseIds.join(',')
    if (query.brandId) params.brandId = query.brandId
    if (query.productName) params.productName = query.productName
    if (query.onlyLowStock) params.onlyLowStock = true
    const res = await request.get<any, any>('/warehouse/stock/product-summary/page', { params })
    rows.value = res?.records || []
    page.total = res?.total || 0
  } catch {
    rows.value = []; page.total = 0
  } finally { loading.value = false }
}

// 查询/重置同时刷滞销块：两者共用同一套筛选（产品/品牌/仓库），只刷一半会让两块对不上
// 注：重置只清**查询条**里的条件（含「仅看低于安全库存」），滞销块自己的阈值/窗口/勾选保留 ——
//     它们是块内状态，不属于查询条，被"重置"顺手清掉会让用户以为设置丢失。
function doQuery() { page.pageNum = 1; syncApplied(); load() }
function resetQuery() {
  query.productName = ''
  query.brandId = undefined
  query.warehouseIds = []
  query.onlyLowStock = false
  page.pageNum = 1
  syncApplied()
  load()
}
function onSizeChange(v: number) { page.pageSize = v; page.pageNum = 1; load() }
function goDetail(row: any) { router.push(`/inventory/product-stock/detail/${row.productId}`) }
/** 产品名称 → 产品主数据详情（2026-09-26 B5a：本页「产品名称」列由纯文本改为可点） */
function goProduct(row: any) { if (row.productId) router.push(`/product/detail/${row.productId}`) }

/**
 * 滞销块的筛选：只把**已确认应用**的条件传给它（输入框里正在敲的字不该触发组件重查 ——
 * 本页原口径就是"点查询才生效"）。`applied` 只在 doQuery / resetQuery 里更新，
 * 组件内部 watch 这三个值，变化后自动回第一页重查。
 */
const stagnantRef = ref<any>(null)
const applied = reactive({ brandId: undefined as number | undefined, warehouseIds: '', productName: '' })
function syncApplied() {
  applied.brandId = query.brandId
  applied.warehouseIds = (query.warehouseIds || []).join(',')
  applied.productName = query.productName
}

useDomainRefresh('productStock', () => { load(); stagnantRef.value?.reload() })
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 12px; }
/* 卡片内边距已统一到全局（styles/page.css 的 .table-card .el-card__body） */
/* 2026-09-29（用户口径「三个搜索项要在一行」）：全局 .query-bar 的 grid 只把「表单 1fr / 按钮组 auto」分两列，
   表单内部仍是 el-form--inline 的 **inline-flex + 每个 item 32px margin-right** ⇒ 宽度一不够就折行
   （实测：三项 563 + 3×32 = 659 > 表单列 614）。
   ⚠️ 全局还写着 `.query-card .query-bar .query-form { flex-wrap: nowrap }`（0,3,0）⇒ 本页选择器**必须同权重以上**
      （scoped 后是 0,4,0）才盖得住它；否则 display:flex 一旦生效、又不许换行 ⇒ 4 个 item 挤成一行**溢出** 119px。 */
.query-card .query-bar .query-form { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; }
/* flex:0 0 auto ⇒ item 保持声明宽度**不被压缩**（否则会被 flex-shrink 悄悄压成 131/111/103 这种"看不出来
   但已经变窄"的状态）；装不下时让后面的勾选项自然折行。 */
.query-card .query-bar .query-form :deep(.el-form-item) { margin-right: 0; flex: 0 0 auto; }
/* 分页样式已统一到全局（styles/page.css 的 .pagination） */
/* 整行可点：给出手型光标，操作列按钮不再额外高亮 */
:deep(.el-table__row) { cursor: pointer; }
/* 品质数量紧凑着色（对应 qtyClass）：替代 el-tag，省出横向空间 */
.qty-a { color: #67c23a; font-weight: 600; }
.qty-b { color: #409eff; font-weight: 600; }
.qty-c { color: #e6a23c; font-weight: 600; }
.qty-defect { color: #f56c6c; font-weight: 600; }
.qty-pending { color: #909399; font-weight: 600; }
.qty-zero { color: #c0c4cc; }
/* 列宽全部交给 min-width 按比例分配，不设 nowrap：nowrap 会让超宽表头溢出到相邻列，反而造成「对不齐」 */

</style>
