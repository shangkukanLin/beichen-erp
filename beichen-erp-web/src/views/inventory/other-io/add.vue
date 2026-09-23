<script setup lang="ts">
// 2026-09-20（F7-159）：显式声明组件名 —— 该页监听顶栏 refresh:dropdown-data，
// 将来若需按名字加入 keep-alive 的 :exclude（如 layout 已排除的 PurchaseAdd 等）才有名可用。
defineOptions({ name: 'InventoryOtherIoAdd' })
import { localDate } from '@/utils/date'
import { ref, computed, onMounted, onActivated, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { getQualityTypes, productLabel, type QualityOption } from '@/api/product'
import { IoType, IoTypeLabel, WarehouseCategory, INVENTORY_OTHER_IO_DIRTY_KEY } from '@/api/enums'
import RemoteSelect from '@/components/RemoteSelect.vue'
import PageShell from '@/components/PageShell.vue'
import { useUnsavedGuard } from '@/composables/usePageBack'
import { useTabStore } from '@/stores/tabs'

const route = useRoute(); const router = useRouter()
const tabStore = useTabStore()
// 用 computed 取路由参数：keep-alive 会复用组件，再次进入时 route.query 会变。
// 若在 setup 阶段固化成常量，编辑完再点新增会沿用上次的 id，保存时变成修改上一条单据。
const editId = computed(() => Number(route.query.id) || 0)
/** 从详情页进入编辑时带 from=detail，保存/取消都回到详情页；否则回列表 */
const fromDetail = computed(() => route.query.from === 'detail')
function goBack() {
  // 保存成功 / 取消返回：先清脏标记（否则离开会被未保存确认拦住），再关掉本次录入页签
  markClean()
  tabStore.closeTabAndBack(route.path)
  if (editId.value && fromDetail.value) router.push(`/inventory/other-io/detail/${editId.value}`)
  else router.push('/inventory/other-io')
}
const qualityOptions = ref<QualityOption[]>([])
const saving = ref(false)
// ioType 显式标注为 string：否则 reactive 会推断成字面量 "IN"，与 IoType.OUT 比较会报 TS2367
const form = reactive({ warehouseId: undefined as any, ioType: IoType.IN as string, ioDate: localDate(), remark: '' })
// 成品其他出入库：明细是成品（后端字段 productId / 表 inventory_other_io_item.product_id），
// 此前误用委外物料下拉导致 productId 存不进去，这里与详情页保持一致
// 出库单才需要看库存：入库不消耗库存，不展示也不校验
const isOut = computed(() => form.ioType === IoType.OUT)
const items = ref<any[]>([{ productId: undefined, productName: '', unit: '', qualityType: 'A', quantity: undefined, remark: '', stockQty: undefined }])
/**
 * 未保存拦截（2026-09-23 统一模板）
 * ⚠️ 必须写在 form / items **之后**：watch 注册时会立即求值一次快照，放前面会因 TDZ 静默失效。
 */
const { takeBaseline, markClean } = useUnsavedGuard(() => ({ form, items: items.value }))

const fetchWarehouses = (kw: string) => request.get('/warehouse/page', { params: { pageSize: 500, warehouseName: kw, warehouseCategory: WarehouseCategory.INVENTORY } })
const fetchProducts = (kw: string) => request.get('/product/page', { params: { pageSize: 100, keyword: kw } })

async function loadQualityTypes() { try { qualityOptions.value = await getQualityTypes() } catch { qualityOptions.value = [] } }
function onProductPick(row: any, idx: number) {
  const it = items.value[idx]
  if (!row) { it.productId = undefined; it.productName = ''; it.unit = ''; return }
  it.productId = row.id
  it.productName = row.name || row.productName || ''
  it.unit = row.unit || ''
  loadStock(it)
}
function addItem() { items.value.push({ productId: undefined, productName: '', unit: '', qualityType: 'A', quantity: undefined, remark: '', stockQty: undefined }) }

/**
 * 查询该成品在所选仓库、所选品质下的可用库存（仅出库单需要）。
 * 仓库 / 成品 / 品质三者任一变化都要重新查。
 */
async function loadStock(row: any) {
  row.stockQty = undefined
  if (!isOut.value || !form.warehouseId || !row.productId) return
  const qt = row.qualityType || 'A'
  try {
    const r = await request.get<any, any>('/warehouse/stock/page', {
      params: { warehouseId: form.warehouseId, productId: row.productId, stockType: 'PRODUCT', pageSize: 100 }
    })
    const recs: any[] = r?.records || []
    const hit = recs.find((x: any) => String(x.qualityType) === String(qt))
    row.stockQty = hit ? Number(hit.quantity) : 0
  } catch { row.stockQty = undefined }
}
function onWarehouseChange() { items.value.forEach(loadStock) }
function onIoTypeChange() { items.value.forEach(loadStock) }
/** 出库数量超过可用库存 */
function overStock(row: any) {
  return isOut.value && row.stockQty != null && Number(row.quantity) > 0 && Number(row.quantity) > row.stockQty
}
function removeItem(i: number) { items.value.splice(i,1) }

async function loadDetail() {
  if (!editId.value) return
  try {
    const io = await request.get<any,any>(`/inventory/other/${editId.value}`)
    form.warehouseId = io.warehouseId; form.ioType = io.ioType
    form.ioDate = io.ioDate; form.remark = io.remark||''
    const its = await request.get<any,any>(`/inventory/other/${editId.value}/items`)
    if (Array.isArray(its)) {
      items.value = its.map((i:any)=>({ productId: i.productId, productName: i.productName||'', unit: i.unit||'', qualityType: i.qualityType||'A', quantity: i.quantity, remark: i.remark||'', stockQty: undefined }))
      if (isOut.value) items.value.forEach(loadStock)
    }
  } catch (e: any) { ElMessage.error(e?.message||'加载失败') }
}

async function handleSubmit() {
  if (!form.warehouseId) { ElMessage.warning('请选择仓库'); return }
  const validItems = items.value.filter((i:any)=>i.quantity && Number(i.quantity)>0)
  if (validItems.length===0) { ElMessage.warning('请添加成品明细'); return }
  // 出库：数量不能超出该仓库该品质的可用库存（后端审核时还会再校验一次）
  if (isOut.value) {
    for (const it of validItems) {
      if (it.stockQty != null && Number(it.quantity) > it.stockQty) {
        ElMessage.warning(`成品「${it.productName || it.productId}」出库数量 ${it.quantity} 超过可用库存 ${it.stockQty}，无法保存`)
        return
      }
    }
  }
  saving.value = true
  try {
    const body: any = { ...form, items: validItems }
    if (editId.value) {
      await request.put(`/inventory/other/${editId.value}`, body); ElMessage.success('已更新（待审核）'); sessionStorage.setItem(INVENTORY_OTHER_IO_DIRTY_KEY, '1')
    } else {
      await request.post('/inventory/other', body); ElMessage.success('已保存（待审核）'); sessionStorage.setItem(INVENTORY_OTHER_IO_DIRTY_KEY, '1')
    }
    goBack()
  } catch (e: any) { ElMessage.error(e?.message||'保存失败') } finally { saving.value = false }
}

/**
 * 初始化页面：新增模式清空为空白表单，编辑模式加载该单据。
 * keep-alive 会复用组件，再次进入时 onMounted 不再触发，
 * 只靠 onMounted 会停留在上次的数据（新增时还带着上一条单据的内容）。
 */
async function initPage() {
  Object.assign(form, {
    warehouseId: undefined,
    ioType: IoType.IN,
    ioDate: localDate(),
    remark: ''
  })
  items.value = [{ productId: undefined, productName: '', unit: '', qualityType: 'A', quantity: undefined, remark: '', stockQty: undefined }]
  if (editId.value) await loadDetail()
  // 初始化/回填完成 ⇒ 建立"未保存"基线（必须在加载之后，否则会把回填误判成用户修改）
  takeBaseline()
}

// 顶栏"刷新数据"：重新加载品质下拉
async function handleRefreshData() { await loadQualityTypes() }
onMounted(async () => { loadQualityTypes(); await initPage(); window.addEventListener('refresh:dropdown-data', handleRefreshData) })
onActivated(async () => { await initPage() })
onUnmounted(() => window.removeEventListener('refresh:dropdown-data', handleRefreshData))
</script>

<template>
  <!-- 统一骨架（2026-09-23 全站最终口径）：页头左端=返回 → 标题 → 右端=操作（保存） -->
  <PageShell :title="editId ? '编辑其他出入库' : '新增其他出入库'" back-fallback="/inventory/other-io">
    <template #actions>
      <el-button type="primary" :loading="saving" @click="handleSubmit">保存</el-button>
    </template>
    <el-card shadow="never">
      <el-form :model="form" label-width="80px">
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item required label="仓库"><RemoteSelect v-model="form.warehouseId" :fetch="fetchWarehouses" :label-key="(row:any)=>row.warehouseName" style="width:100%" @update:modelValue="onWarehouseChange" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="类型"><el-select v-model="form.ioType" style="width:100%" @change="onIoTypeChange"><el-option :label="IoTypeLabel[IoType.IN]" :value="IoType.IN"/><el-option :label="IoTypeLabel[IoType.OUT]" :value="IoType.OUT"/></el-select></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="日期"><el-input v-model="form.ioDate" type="date"/></el-form-item></el-col>
        </el-row>
        <el-form-item label="备注"><el-input v-model="form.remark" placeholder="备注"/></el-form-item>
      </el-form>
    </el-card>
    <el-card shadow="never">
      <template #header><span style="font-weight:600">成品明细</span></template>
      <el-button type="primary" size="small" @click="addItem" style="margin-bottom:8px">+ 添加成品</el-button>
      <el-table :data="items" border size="small">
        <el-table-column label="成品名称" min-width="180"><template #default="{row,$index}"><RemoteSelect v-model="row.productId" :fetch="fetchProducts" :label-key="productLabel" placeholder="可输SKU搜索" style="width:100%" @pick="(rows:any[])=>onProductPick(rows[0],$index)" /></template></el-table-column>
        <el-table-column label="单位" width="80"><template #default="{row}">{{ row.unit || '-' }}</template></el-table-column>
        <el-table-column label="品质" width="90"><template #default="{row}"><el-select v-model="row.qualityType" size="small" style="width:100%" @change="loadStock(row)"><el-option v-for="q in qualityOptions" :key="q.value" :label="q.label" :value="q.value"/></el-select></template></el-table-column>
        <el-table-column v-if="isOut" label="可用库存" width="100" align="right">
          <template #default="{row}">
            <span v-if="row.stockQty != null" :style="overStock(row) ? 'color:#f56c6c;font-weight:600' : ''">{{ Number(row.stockQty) }}</span>
            <span v-else style="color:var(--app-text-placeholder)">—</span>
          </template>
        </el-table-column>
        <el-table-column label="数量" width="120">
          <template #default="{row}">
            <el-input-number v-model="row.quantity" size="small" :controls="false" :precision="0" :step="1" style="width:100%" />
            <div v-if="overStock(row)" style="color:#f56c6c;font-size:var(--app-font-xs);line-height:1.2;margin-top:2px">超出库存</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="60" align="center"><template #default="{$index}"><el-button type="danger" link @click="removeItem($index)">删除</el-button></template></el-table-column>
      </el-table>
    </el-card>
  </PageShell>
</template>
