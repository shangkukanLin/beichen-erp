<template>
  <!-- 统一骨架（2026-09-23 全站最终口径）：页头左端=返回 → 标题 → 右端=操作（保存） -->
  <PageShell :title="isEdit ? '编辑移仓单' : '新增移仓单'" back-fallback="/inventory/warehouse-move">
    <template #actions>
      <el-button type="primary" :loading="submitLoading" @click="handleSubmit">保存</el-button>
    </template>

    <el-card shadow="never">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="移出仓库" prop="fromWarehouseId">
              <RemoteSelect v-model="form.fromWarehouseId" :fetch="fetchWarehouses" :label-key="(row:any)=>row.warehouseName" placeholder="请选择" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="移入仓库" prop="toWarehouseId">
              <RemoteSelect v-model="form.toWarehouseId" :fetch="fetchWarehouses" :label-key="(row:any)=>row.warehouseName" placeholder="请选择" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="移仓日期">
              <el-date-picker v-model="form.moveDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="请输入备注" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">移仓明细</el-divider>
        <div style="margin-bottom:8px">
          <el-button type="primary" @click="addItem">添加明细</el-button>
        </div>
        <el-table :data="items" border>
          <el-table-column label="SKU" width="130">
            <template #default="{ row }">
              <span v-if="row.sku">{{ row.sku }}</span>
              <span v-else style="color:var(--app-text-secondary)">自动生成</span>
            </template>
          </el-table-column>
          <el-table-column label="产品" min-width="220">
            <template #default="{ row }">
              <RemoteSelect v-model="row.productId" :fetch="fetchProducts" :label-key="productLabel" placeholder="选择产品（可输SKU）" style="width:100%"
                @pick="(rows:any[]) => onProductPick(rows[0], row)" />
            </template>
          </el-table-column>
          <el-table-column label="单位" width="70">
            <template #default="{ row }">{{ row._unit || '' }}</template>
          </el-table-column>
          <el-table-column label="品质" width="90">
            <template #default="{ row }">
              <el-select v-model="row.qualityType" size="small" style="width:100%" @change="() => loadStock(row)">
                <el-option v-for="q in qualityOptions" :key="q.value" :label="q.label" :value="q.value" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="现有库存" width="110" align="right">
            <template #default="{ row }">{{ row._stock != null ? row._stock : '-' }}</template>
          </el-table-column>
          <el-table-column label="移仓数量" width="130">
            <template #default="{ row }"><el-input-number v-model="row.quantity" :min="1" :precision="0" controls-position="right" style="width:100%" /></template>
          </el-table-column>
          <el-table-column label="操作" width="70" align="center">
            <template #default="{ $index }"><el-button type="danger" link @click="items.splice($index, 1)">删除</el-button></template>
          </el-table-column>
        </el-table>

      </el-form>
    </el-card>
  </PageShell>
</template>

<script setup lang="ts">
import { localDate } from '@/utils/date'
import { WarehouseCategory, INVENTORY_WAREHOUSE_MOVE_DIRTY_KEY } from '@/api/enums'
defineOptions({ name: 'InventoryWarehouseMoveAdd' })

import { reactive, ref, watch, onMounted, onUnmounted, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useTabStore } from '@/stores/tabs'
import PageShell from '@/components/PageShell.vue'
import { useUnsavedGuard } from '@/composables/usePageBack'
import request from '@/utils/request'
import { getQualityTypes, productLabel, type QualityOption } from '@/api/product'
import RemoteSelect from '@/components/RemoteSelect.vue'

interface MoveItem {
  productId?: number
  sku?: string
  qualityType?: string
  _unit?: string
  _stock?: number
  quantity?: number
  remark?: string
}

const router = useRouter()
const route = useRoute()
const tabStore = useTabStore()

const editId = ref<number | undefined>(route.query.id ? Number(route.query.id) : undefined)
const isEdit = ref(!!editId.value)

const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({
  id: undefined as number | undefined,
  fromWarehouseId: undefined as number | undefined,
  toWarehouseId: undefined as number | undefined,
  moveDate: localDate() as string,
  remark: '' as string,
})
const items = ref<MoveItem[]>([])
/**
 * 统一「返回」的未保存拦截（2026-09-23 次级页面统一模板）：
 * ⚠️ 必须写在 form / items **之后** —— watch 注册时会立即求值一次快照，放前面会因 TDZ 静默失效。
 */
const { takeBaseline, markClean } = useUnsavedGuard(() => ({ form, items: items.value }))
const rules: FormRules = {
  fromWarehouseId: [{ required: true, message: '请选择移出仓库', trigger: 'change' }],
  toWarehouseId: [{ required: true, message: '请选择移入仓库', trigger: 'change' }]
}

const qualityOptions = ref<QualityOption[]>([])

const fetchWarehouses = (kw: string) => request.get('/warehouse/page', { params: { pageSize: 200, warehouseName: kw, warehouseCategory: WarehouseCategory.INVENTORY } })
const fetchProducts = (kw: string) => request.get('/product/page', { params: { pageSize: 100, keyword: kw } })
// 2026-09-20（F7-180）：删除 loadProducts —— 它填充的 productOptions **模板从未使用**
// （产品下拉走 RemoteSelect 实时查库 `fetchProducts`），但 onMounted 仍会调用它 ⇒ 每次进页多打一次 /product/page。

async function loadQualityTypes() { try { qualityOptions.value = await getQualityTypes() } catch { qualityOptions.value = [] } }

function onProductPick(p: any, row: MoveItem) {
  if (!p) return
  row.productId = p.id
  row.sku = p.sku || ''
  row._unit = p.unit
  void loadStock(row)
}

/**
 * F7-21：移仓从「移出仓」扣减，库存展示与校验都针对移出仓 + **该行品质**。
 * 原先取数用 pageSize:1 且不按品质过滤 ⇒ _stock 实为"(仓,产品)任意首个品质行"的数量，
 * 与详情页（detail.vue）口径不一致。此处与详情页实现完全对齐。
 */
async function loadStock(row: MoveItem) {
  row._stock = undefined
  if (!form.fromWarehouseId || !row.productId) return
  try {
    const r = await request.get<any, any>('/warehouse/stock/page', {
      params: { warehouseId: form.fromWarehouseId, productId: row.productId, stockType: 'PRODUCT', pageSize: 100 }
    })
    const recs: any[] = r?.records || []
    const hit = recs.find((x: any) => String(x.qualityType) === String(row.qualityType || 'A'))
    row._stock = hit ? Number(hit.quantity) : 0
  } catch { row._stock = undefined }
}

/** F7-21：该行数量是否超过移出仓该品质的可用库存（与 detail.vue 同判定） */
function overStock(row: MoveItem) {
  return row._stock != null && Number(row.quantity) > 0 && Number(row.quantity) > row._stock
}

// 刷新所有明细行的库存（移出仓变化时；行品质变化时也走 loadStock 单行重算）
async function refreshStock() {
  for (const item of items.value) await loadStock(item)
}

function addItem() { items.value.push({ productId: undefined, qualityType: 'A', quantity: 1 }) }

function resetForm() {
  Object.assign(form, { id: undefined, fromWarehouseId: undefined, toWarehouseId: undefined, moveDate: localDate(), remark: '' })
  items.value = []
  formRef.value?.clearValidate()
}

async function loadMoveData() {
  if (!editId.value) return
  try {
    const r = await request.get<any, any>(`/inventory/warehouse-move/${editId.value}`)
    form.id = r.id
    form.fromWarehouseId = r.fromWarehouseId
    form.toWarehouseId = r.toWarehouseId
    form.moveDate = r.moveDate
    form.remark = r.remark
    const its = await request.get<any, any>(`/inventory/warehouse-move/${editId.value}/items`)
    items.value = (its || []).map((it: any) => ({
      productId: it.productId,
      qualityType: it.qualityType,
      _unit: it.unit,
      quantity: it.quantity,
      remark: it.remark,
    }))
    await refreshStock()
  } catch { ElMessage.error('加载移仓单失败') }
}

watch(() => route.query.id, (newId) => {
  editId.value = newId ? Number(newId) : undefined
  isEdit.value = !!editId.value
  resetForm()
  if (isEdit.value) {
    tabStore.updateTabTitle(route.fullPath, '编辑移仓单')
    document.title = '编辑移仓单 - 北辰ERP管理系统'
    loadMoveData()
  }
})

// 移出仓库变化时刷新所有明细的库存
watch(() => form.fromWarehouseId, () => {
  if (items.value.length > 0) refreshStock()
})

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    if (form.fromWarehouseId === form.toWarehouseId) { ElMessage.warning('移出与移入仓库不能相同'); return }
    if (items.value.length === 0) { ElMessage.warning('请至少添加一条明细'); return }
    if (items.value.some(it => !it.productId)) { ElMessage.warning('请选择产品'); return }
    // F7-21：超量提交拦截（与详情页一致；后端审核时另有库存层兜底）
    const bad = items.value.find(it => overStock(it))
    if (bad) {
      ElMessage.warning(`第 ${items.value.indexOf(bad) + 1} 行数量超过移出仓该品质可用库存（${bad._stock}）`)
      return
    }
    submitLoading.value = true
    try {
      const payload = { move: { ...form }, items: items.value }
      if (isEdit.value) await request.put(`/inventory/warehouse-move/${editId.value}`, payload)
      else await request.post('/inventory/warehouse-move', payload)
      ElMessage.success('保存成功'); sessionStorage.setItem(INVENTORY_WAREHOUSE_MOVE_DIRTY_KEY, '1')
      resetForm()
      // 保存成功 ⇒ 先清脏标记（否则离开会被未保存确认拦住），再关掉本页签回列表
      markClean()
      tabStore.closeTabAndBack(route.path)
      router.push('/inventory/warehouse-move')
    } catch (e: any) { ElMessage.error(e?.message || '保存失败') }
    finally { submitLoading.value = false }
  })
}

// 顶栏"刷新数据"：重新加载品质下拉
async function handleRefreshData() { await loadQualityTypes() }
onMounted(async () => {
  loadQualityTypes()
  if (isEdit.value) {
    tabStore.updateTabTitle(route.fullPath, '编辑移仓单')
    document.title = '编辑移仓单 - 北辰ERP管理系统'
    await loadMoveData()
  }
  window.addEventListener('refresh:dropdown-data', handleRefreshData)
  // 数据加载完成 ⇒ 建立"未保存"基线（必须在加载之后，否则会把回填误判成用户修改）
  takeBaseline()
})
onUnmounted(() => window.removeEventListener('refresh:dropdown-data', handleRefreshData))



onBeforeUnmount(() => {
  resetForm()
})
</script>

<style scoped>
/* 页头/底部操作条已统一到全局骨架（PageShell + styles/page.css），本页不再自写 .page */
</style>
