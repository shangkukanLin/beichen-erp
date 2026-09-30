<script setup lang="ts">
import { localDate } from '@/utils/date'
import { reactive, ref, onMounted, onActivated } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { WarehouseCategory, INVENTORY_RECLASSIFY_DIRTY_KEY } from '@/api/enums'
import { getQualityTypes, productLabel, type QualityOption } from '@/api/product'
import { createReclassify, type ReclassifyItem } from '@/api/inventory'
import RemoteSelect from '@/components/RemoteSelect.vue'
import PageShell from '@/components/PageShell.vue'
import { useUnsavedGuard } from '@/composables/usePageBack'
import { useTabStore } from '@/stores/tabs'
import { invalidate } from '@/utils/dataFreshness'

const route = useRoute(); const router = useRouter()
const tabStore = useTabStore()
const saving = ref(false)
const qualityOptions = ref<QualityOption[]>([])

const form = reactive({
  warehouseId: undefined as any,
  reclassifyDate: localDate(),
  remark: ''
})
const items = ref<any[]>([])
/**
 * 未保存拦截（2026-09-23 统一模板）
 * ⚠️ 必须写在 form / items **之后**：watch 注册时会立即求值一次快照，放前面会因 TDZ 静默失效。
 */
const { takeBaseline, markClean } = useUnsavedGuard(() => ({ form, items: items.value }))

const fetchWarehouses = (kw: string) =>
  request.get('/warehouse/page', { params: { pageSize: 200, warehouseName: kw, warehouseCategory: WarehouseCategory.INVENTORY } })
const fetchProducts = (kw: string) => request.get('/product/page', { params: { pageSize: 50, keyword: kw } })

function addItem() {
  items.value.push({ productId: undefined, productName: '', sku: '', unit: '', fromQuality: 'A', toQuality: 'B', quantity: undefined, stockQty: undefined })
}
function removeItem(i: number) { items.value.splice(i, 1) }

function onProductPick(p: any, row: any) {
  if (!p) { row.productId = undefined; row.productName = ''; row.sku = ''; row.unit = ''; return }
  row.productId = p.id
  row.productName = p.name || p.productName || ''
  row.sku = p.sku || ''
  row.unit = p.unit || ''
  loadStock(row)
}

/**
 * 重分类是从「原品质」扣减、向「目标品质」增加，
 * 因此只需要校验并展示原品质的可用库存。仓库/产品/原品质任一变化都重新查。
 */
async function loadStock(row: any) {
  row.stockQty = undefined
  if (!form.warehouseId || !row.productId || !row.fromQuality) return
  try {
    const r = await request.get<any, any>('/warehouse/stock/page', {
      params: { warehouseId: form.warehouseId, productId: row.productId, stockType: 'PRODUCT', pageSize: 100 }
    })
    const recs: any[] = r?.records || []
    const hit = recs.find((x: any) => String(x.qualityType) === String(row.fromQuality))
    row.stockQty = hit ? Number(hit.quantity) : 0
  } catch { row.stockQty = undefined }
}
function onWarehouseChange() { items.value.forEach(loadStock) }
/** 数量超过原品质可用库存 */
function overStock(row: any) {
  return row.stockQty != null && Number(row.quantity) > 0 && Number(row.quantity) > row.stockQty
}

async function handleSubmit() {
  if (!form.warehouseId) { ElMessage.warning('请选择仓库'); return }
  if (items.value.length === 0) { ElMessage.warning('请添加重分类明细'); return }
  for (const it of items.value) {
    if (!it.productId) { ElMessage.warning('请选择产品'); return }
    if (!it.fromQuality || !it.toQuality) { ElMessage.warning('请选择品质'); return }
    if (it.fromQuality === it.toQuality) { ElMessage.warning('原品质和目标品质不能相同'); return }
    if (!(Number(it.quantity) > 0)) { ElMessage.warning('数量必须大于0'); return }
    // 原品质库存校验：扣减侧不能超库存（后端审核时还会再校验一次）
    if (it.stockQty != null && Number(it.quantity) > it.stockQty) {
      ElMessage.warning(`产品「${it.productName || it.productId}」原品质 ${it.fromQuality} 数量 ${it.quantity} 超过可用库存 ${it.stockQty}，无法保存`)
      return
    }
  }
  saving.value = true
  try {
    await createReclassify({
      ...form,
      items: items.value.map((it: any) => ({
        productId: it.productId,
        fromQuality: it.fromQuality,
        toQuality: it.toQuality,
        quantity: Number(it.quantity)
      })) as ReclassifyItem[]
    })
    ElMessage.success('已保存（待审核）')
    invalidate('reclassify')
    // 保存成功 ⇒ 先清脏标记（否则离开会被未保存确认拦住），再关掉本页签回列表
    markClean()
    tabStore.closeTabAndBack(route.path)
    router.push('/inventory/reclassify')
  } catch (e: any) { ElMessage.error(e?.message || '保存失败') } finally { saving.value = false }
}

async function loadQualityTypes() {
  try { qualityOptions.value = await getQualityTypes() } catch { qualityOptions.value = [] }
}

/**
 * 重置为空白新增态。
 * layout 用 keep-alive 缓存页面，再次进入新增页会复用组件、onMounted 不再触发，
 * 只靠 onMounted 初始化会停留在上次填写的数据，因此 onActivated 也要重置一次。
 */
function resetPage() {
  Object.assign(form, {
    warehouseId: undefined,
    reclassifyDate: localDate(),
    remark: ''
  })
  items.value = []
  addItem()
}

onMounted(() => { loadQualityTypes(); resetPage(); takeBaseline() })
onActivated(() => { resetPage(); takeBaseline() })
</script>

<template>
  <!-- 统一骨架（2026-09-23 全站最终口径）：页头左端=返回 → 标题 → 右端=操作（保存） -->
  <PageShell back-fallback="/inventory/reclassify">
    <template #actions>
      <el-button type="primary" :loading="saving" @click="handleSubmit">保存</el-button>
    </template>

    <el-card shadow="never">
      <el-form :model="form" label-width="80px" size="small">
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="仓库" required>
              <RemoteSelect v-model="form.warehouseId" :fetch="fetchWarehouses" :label-key="(row:any)=>row.warehouseName" placeholder="选择仓库" style="width:100%" @update:modelValue="onWarehouseChange" domain="warehouse" />
            </el-form-item>
          </el-col>
          <el-col :span="8"><el-form-item label="日期"><el-input v-model="form.reclassifyDate" type="date" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="备注"><el-input v-model="form.remark" placeholder="备注" /></el-form-item></el-col>
        </el-row>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span style="font-weight:600">重分类明细</span>
          <el-button type="primary" size="small" @click="addItem">+ 添加明细</el-button>
        </div>
      </template>
      <el-table :data="items" border size="small">
        <el-table-column label="SKU" width="130">
          <template #default="{ row }">
            <span v-if="row.sku">{{ row.sku }}</span>
            <span v-else style="color:var(--app-text-placeholder)">自动生成</span>
          </template>
        </el-table-column>
        <el-table-column label="产品" min-width="200">
          <template #default="{ row }">
            <RemoteSelect v-model="row.productId" :fetch="fetchProducts" :label-key="productLabel" placeholder="搜索产品（可输SKU）" style="width:100%" @pick="(rows:any[])=>onProductPick(rows[0],row)" domain="product" />
          </template>
        </el-table-column>
        <el-table-column label="原品质" width="110">
          <template #default="{ row }">
            <el-select v-model="row.fromQuality" size="small" style="width:100%" @change="loadStock(row)">
              <el-option v-for="q in qualityOptions" :key="q.value" :label="q.label" :value="q.value" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="目标品质" width="110">
          <template #default="{ row }">
            <el-select v-model="row.toQuality" size="small" style="width:100%">
              <el-option v-for="q in qualityOptions" :key="q.value" :label="q.label" :value="q.value" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="原品质可用库存" width="130" align="right">
          <template #default="{ row }">
            <span v-if="row.stockQty != null" :style="overStock(row) ? 'color:#f56c6c;font-weight:600' : ''">{{ Number(row.stockQty) }}</span>
            <span v-else style="color:var(--app-text-placeholder)">—</span>
          </template>
        </el-table-column>
        <el-table-column label="数量" width="130">
          <template #default="{ row }">
            <el-input-number v-model="row.quantity" size="small" :controls="false" :precision="0" :step="1" style="width:100%" />
            <div v-if="overStock(row)" style="color:#f56c6c;font-size:var(--app-font-xs);line-height:1.2;margin-top:2px">超出库存</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="70" align="center">
          <template #default="{ $index }"><el-button type="danger" link size="small" @click="removeItem($index)">删除</el-button></template>
        </el-table-column>
      </el-table>
    </el-card>

  </PageShell>
</template>
