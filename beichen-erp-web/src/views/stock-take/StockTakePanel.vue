<script setup lang="ts">
/**
 * 库存盘点面板（2026-09-16 由 views/inventory/stock-take/index.vue 抽出）
 *
 * 通过 scope 区分两种盘点，**仓库范围与服务端口径完全一致**：
 *  - PRODUCT  成品盘点：自有仓中除辅料仓以外的仓库（现只有成品仓）
 *  - MATERIAL 物料盘点：委外仓（OUTSOURCE）+ 自有物料仓（INVENTORY + AUXILIARY）
 *
 * 后端在 create/save/audit 等入口也会按「盘点单所属仓库」再校验一次，前端过滤只是体验层面；
 * 物料仓单据额外要求「跟单专员」角色（见 StockTakeServiceImpl.assertRoleForScope）。
 */
import { localDate, localMonth } from '@/utils/date'
import { reactive, ref, computed, onMounted, onActivated } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
// 2026-09-20（F7-191①）：补 DocStatus —— 本页原先状态判定硬编码 'DRAFT'/'AUDITED'，
// 而同页的下拉与标签却用 DocStatusLabel/DocStatusTag（同页两套写法）
import { DocStatus, DocStatusLabel, DocStatusTag } from '@/api/common'
import { WarehouseCategory, WarehouseType } from '@/api/enums'
import {
  getStockTakePage, getStockTakeItems, createStockTake, saveStockTakeItems,
  auditStockTake, unAuditStockTake, cancelStockTake,
  type StockTake, type StockTakeItem,
} from '@/api/inventory'
import request from '@/utils/request'
// 2026-09-23：明细由 900px 弹框改为独立页 ⇒ 行内「录入实盘/查看明细」改为路由跳转
import { useRouter } from 'vue-router'

const router = useRouter()
const props = defineProps<{ scope: 'PRODUCT' | 'MATERIAL' }>()
const isMaterial = computed(() => props.scope === 'MATERIAL')
/** 页面标题：用于表头提示当前盘的是哪一类仓库 */
const pageTitle = computed(() => (isMaterial.value ? '物料库存盘点' : '库存盘点'))
const scopeHint = computed(() => (isMaterial.value ? '（委外仓 / 自有物料仓）' : '（成品仓）'))

// 库存盘点：每月每仓一次
const query = reactive({ warehouseId: undefined as number | undefined, period: '', status: '' })
const page = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const loading = ref(false)
const data = ref<StockTake[]>([])
const warehouses = ref<any[]>([])

async function loadData() {
  loading.value = true
  try {
    const p: any = { pageNum: page.pageNum, pageSize: page.pageSize, scope: props.scope }
    if (query.warehouseId) p.warehouseId = query.warehouseId
    if (query.period) p.period = query.period
    if (query.status) p.status = query.status
    const res = await getStockTakePage(p)
    data.value = res?.records || []
    page.total = res?.total || 0
  } catch { data.value = [] } finally { loading.value = false }
}

/** 仓库下拉：按 scope 拉对应类别的仓库（与后端 StockTakeScope 判定口径一致） */
async function loadWarehouses() {
  try {
    if (isMaterial.value) {
      const [os, aux] = await Promise.all([
        request.get<any, any>('/warehouse/page', { params: { pageSize: 500, warehouseCategory: WarehouseCategory.OUTSOURCE } }).catch(() => ({})),
        request.get<any, any>('/warehouse/page', { params: { pageSize: 500, warehouseCategory: WarehouseCategory.INVENTORY, warehouseType: WarehouseType.AUXILIARY } }).catch(() => ({})),
      ])
      warehouses.value = [...(os?.records || []), ...(aux?.records || [])].filter((w: any) => w.status === 1)
    } else {
      const r = await request.get<any, any>('/warehouse/page', { params: { pageSize: 500, warehouseCategory: WarehouseCategory.INVENTORY } })
      // 自有仓里排除辅料仓（辅料仓归「物料仓库 → 物料库存盘点」）
      warehouses.value = (r?.records || []).filter((w: any) => w.status === 1 && w.warehouseType !== WarehouseType.AUXILIARY)
    }
  } catch { warehouses.value = [] }
}

// 新建盘点单
const createDialog = ref(false)
const createForm = reactive({ warehouseId: undefined as number | undefined, period: '', takeDate: localDate(), remark: '' })
function openCreate() {
  Object.assign(createForm, { warehouseId: undefined, period: localMonth(), takeDate: localDate(), remark: '' })
  createDialog.value = true
}
async function submitCreate() {
  if (!createForm.warehouseId) { ElMessage.warning('请选择盘点仓库'); return }
  try {
    const t = await createStockTake({ ...createForm, scope: props.scope })
    createDialog.value = false
    ElMessage.success('盘点单已创建，请录入实盘数量')
    await loadData()
    if (t?.id) openItems(t)
  } catch { /* F7-29：失败提示由 request 拦截器统一给出；此处不给成功提示，故不会被误判为成功 */ }
}

// 明细（录入实盘数量）
const itemDialog = ref(false)
const itemLoading = ref(false)
const items = ref<StockTakeItem[]>([])
const curTake = ref<StockTake>({})
/**
 * 明细改独立页（2026-09-23 用户要求：弹框改独立界面）。
 * 表头字段（范围/状态/仓库名/单号）随 query 带过去 —— 盘点单没有单条查询接口，
 * 而这些字段只用于展示与"是否可编辑"判定，放 URL 里刷新/直链都不会丢。
 */
function openItems(row: StockTake) {
  curTake.value = row
  router.push({
    path: `/inventory/stock-take/detail/${row.id}`,
    query: {
      scope: props.scope,
      status: row.status,
      warehouseName: row.warehouseName,
      takeNo: row.takeNo,
    },
  })
}
async function loadItems(id: number) {
  itemLoading.value = true
  try { items.value = await getStockTakeItems(id) } catch { items.value = [] } finally { itemLoading.value = false }
}
// 实时差异 = 实盘 − 账面
function diffOf(it: StockTakeItem) {
  const a = Number(it.actualQuantity || 0), b = Number(it.bookQuantity || 0)
  return Math.round((a - b) * 10000) / 10000
}
const diffRows = computed(() => items.value.filter(it => diffOf(it) !== 0))
async function saveItems() {
  try {
    await saveStockTakeItems(curTake.value.id!, items.value)
    ElMessage.success('实盘数量已保存')
    await loadData()
    await loadItems(curTake.value.id!)
  } catch { /* F7-29：同上，失败提示由拦截器给出 */ }
}
async function audit(row: StockTake) {
  const tips = (row.diffCount || 0) > 0
    ? `该盘点单有 ${row.diffCount} 行差异（合计 ${Number(row.diffSum || 0).toFixed(2)}），审核后将按实盘数量调整库存（盘盈入库、盘亏出库）。确认审核？`
    : `确认审核盘点单 ${row.takeNo}？（无差异，库存不变）`
  try { await ElMessageBox.confirm(tips, '审核确认', { type: 'warning' }) } catch { return }
  try { await auditStockTake(row.id!); ElMessage.success('已审核，库存已按实盘调整'); loadData() } catch {}
}
async function unAudit(row: StockTake) {
  try { await ElMessageBox.confirm('反审核将按差异反向冲回库存，确认继续？', '反审核确认', { type: 'warning' }) } catch { return }
  try { await unAuditStockTake(row.id!); ElMessage.success('已反审核，库存已回滚'); loadData() } catch {}
}
async function cancel(row: StockTake) {
  try { await ElMessageBox.confirm('确认作废该盘点单？', '作废确认', { type: 'warning' }) } catch { return }
  try { await cancelStockTake(row.id!); ElMessage.success('已作废'); loadData() } catch {}
}
// 数量一律整数（2026-09-16）
function fmt(v?: number) { return v == null ? '0' : String(Math.round(Number(v))) }
function fmtDate(v?: string) { return v ? String(v).slice(0, 10) : '' }
function nameOf(it: StockTakeItem) { return it.productName || it.materialName || '' }
/**
 * 仓库详情分流（2026-09-26 B5a）：本面板被「成品库存盘点(scope=PRODUCT)」与「物料库存盘点(scope=MATERIAL)」
 * 共用 ⇒ 仓库可能是成品仓、辅助物料仓或委外仓，三种详情页不同。仓库列表已按 scope 加载（含 warehouseCategory），
 * 直接本地判定，零额外请求。
 */
function goWarehouseDetail(id?: number) {
  if (id == null) return
  const w = warehouses.value.find((x: any) => x.id === id)
  if (w?.warehouseCategory === WarehouseCategory.OUTSOURCE) router.push(`/outsource/warehouse/detail/${id}`)
  else router.push(`/inventory/warehouse/detail/${id}`)
}
// 2026-09-20（F7-191②）：仓库列表只需加载一次；单据数据改为每次进入都重拉 ——
// 本面板被两个路由页包裹在 keep-alive 内（再次进入复用组件、onMounted 不再触发），
// 原先只挂 onMounted ⇒ 从新增/明细返回列表时不刷新。
onMounted(() => { loadWarehouses() })
onActivated(() => { loadData() })
</script>

<template>
  <div class="page-list">
    <el-card shadow="never" class="query-card">
      <div class="query-bar">
        <el-form :inline="true" :model="query" class="qf">
          <el-form-item label="仓库">
            <el-select v-model="query.warehouseId" placeholder="全部" clearable style="width:200px">
              <el-option v-for="w in warehouses" :key="w.id" :label="w.warehouseName" :value="w.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="月份">
            <el-input v-model="query.period" placeholder="如 2026-09" clearable style="width:120px" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="query.status" placeholder="全部" clearable style="width:120px">
              <el-option v-for="(label, code) in DocStatusLabel" :key="code" :label="label" :value="code" />
            </el-select>
          </el-form-item>
        </el-form>
        <div class="toolbar">
          <el-button type="primary" :icon="'Search'" @click="page.pageNum=1;loadData()">查询</el-button>
          <el-button :icon="'Refresh'" @click="query.warehouseId=undefined;query.period='';query.status='';page.pageNum=1;loadData()">重置</el-button>
          <el-button type="success" :icon="'Plus'" @click="openCreate">新增</el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <template #header>
        <span style="font-weight:600">{{ pageTitle }}</span>
        <span style="margin-left:8px;color:var(--app-text-secondary);font-size:var(--app-font-xs)">盘点范围{{ scopeHint }}</span>
      </template>
      <!-- 2026-09-24（用户规则：所有列表一行显示完、不左右滑动）：原列宽合计 1120px > 内容区 971px
           ⇒ 横向滚动 149px。收窄为合计 936px（本组件被 成品库存盘点 / 物料库存盘点 两页共用 ⇒ 一处修两页）。
           2026-09-26 B5a（用户口径「数据显示完整 + 单号/仓库可点」）：
           ①盘点单号 130→**146** 并做成链接（点开与「查看明细/录入实盘」同一出口，避免两套入口）；
           ②仓库 min110→**189**（委外仓名实测需 189 ⇒ 完整显示）并做成链接进**对应仓库详情**（按 warehouseCategory 分流）；
           ③为抵平：盘点月份 80→76、明细行数 70→62、差异行数 70→62、差异合计 88→76、状态 76→72。
           合计 = 146+189+76+96+62+62+76+72+174 = **953** ✓ -->
      <el-table v-loading="loading" :data="data" border stripe>
        <el-table-column label="盘点单号" width="146" show-overflow-tooltip>
          <template #default="{ row }"><el-button type="primary" link @click.stop="openItems(row)">{{ row.takeNo }}</el-button></template>
        </el-table-column>
        <el-table-column label="仓库" min-width="189" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button v-if="row.warehouseId" type="primary" link @click.stop="goWarehouseDetail(row.warehouseId)">{{ row.warehouseName }}</el-button>
            <span v-else>{{ row.warehouseName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="period" label="盘点月份" width="76" align="center" />
        <el-table-column label="盘点日期" width="96"><template #default="{row}">{{ fmtDate(row.takeDate) }}</template></el-table-column>
        <el-table-column prop="itemCount" label="明细行数" width="62" align="right" />
        <el-table-column label="差异行数" width="62" align="right">
          <template #default="{row}">
            <span :style="{ color: row.diffCount ? 'var(--app-color-danger)' : '' }">{{ row.diffCount || 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="差异合计" width="76" align="right">
          <template #default="{row}"><span :style="{ color: row.diffSum ? 'var(--app-color-danger)' : '' }">{{ fmt(row.diffSum) }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="72" align="center">
          <template #default="{row}"><el-tag :type="(DocStatusTag[row.status] || 'info') as any" size="small">{{ DocStatusLabel[row.status] || row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="174" align="center" fixed="right">
          <template #default="{row}">
            <el-button type="primary" link @click="openItems(row)">{{ row.status===DocStatus.DRAFT ? '录入实盘' : '查看明细' }}</el-button>
            <el-button v-if="row.status===DocStatus.DRAFT" type="success" link @click="audit(row)">审核</el-button>
            <el-button v-if="row.status===DocStatus.AUDITED" type="warning" link @click="unAudit(row)">反审核</el-button>
            <el-button v-if="row.status===DocStatus.DRAFT" type="danger" link @click="cancel(row)">作废</el-button>
          </template>
        </el-table-column>
      </el-table>
      <!-- 2026-09-20（F7-191③）：改每页条数必须复位到第 1 页，否则停在越界页显示空列表 -->
      <div class="pagination"><el-pagination v-model:current-page="page.pageNum" v-model:page-size="page.pageSize" :page-sizes="[10,20,50,100]" :total="page.total" layout="total,sizes,prev,pager,next,jumper" background @size-change="page.pageNum=1;loadData()" @current-change="loadData" /></div>
    </el-card>

    <!-- 新建盘点单 -->
    <el-dialog v-model="createDialog" :title="`新增盘点${scopeHint}`" width="var(--app-dialog-sm)" :close-on-click-modal="false">
      <el-form :model="createForm" label-width="90px">
        <el-form-item label="盘点仓库" required>
          <el-select v-model="createForm.warehouseId" placeholder="请选择" style="width:100%">
            <el-option v-for="w in warehouses" :key="w.id" :label="w.warehouseName" :value="w.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="盘点月份"><el-input v-model="createForm.period" placeholder="yyyy-MM" /></el-form-item>
        <el-form-item label="盘点日期"><el-date-picker v-model="createForm.takeDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="createForm.remark" type="textarea" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="createDialog=false">取消</el-button><el-button type="primary" @click="submitCreate">确定</el-button></template>
    </el-dialog>

  </div>
</template>

<style scoped>
/* 根容器/分页样式已统一到全局（styles/page.css 的 .page-list / .pagination） */
.qf { display: flex; flex-wrap: wrap; }
</style>
