<script setup lang="ts">
import { localDate } from '@/utils/date'
import { reactive, ref, computed, onMounted, onActivated, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import RemoteSelect from '@/components/RemoteSelect.vue'
import { WarehouseType, AfterSaleSourceType, AfterSaleSourceTypeLabel } from '@/api/enums'
import {
  getReturnSort, getReturnSortItems, getReturnSortDefectStock,
  createReturnSort, updateReturnSort, type ReturnSortItem
} from '@/api/inventory'

const route = useRoute(); const router = useRouter()

/** 新增（/inventory/return-sort/add）与编辑（/inventory/return-sort/edit/:id）共用本页 */
const id = computed(() => (route.params.id != null && route.params.id !== '' ? Number(route.params.id) : undefined))
const isNew = computed(() => id.value == null)

const loading = ref(false)
const saving = ref(false)
/** 编辑模式的单号（标题展示用；新增时为空） */
const code = ref('')

const form = reactive({
  id: undefined as any,
  warehouseId: undefined as any,
  sortDate: localDate(),
  targetWarehouseA: undefined as any,
  targetWarehouseB: undefined as any,
  targetWarehouseC: undefined as any,
  targetWarehouseDefect: undefined as any,
  /** 折损收款：整理后 B/C/不良品的折损，向客户收取，审核后生成一条正向应收（单号 -LOSS） */
  lossAmount: 0,
  lossRemark: '',
  remark: ''
})
const items = ref<any[]>([])

/** 待整理库存超期预警阈值（天）：停留超过该天数则标红提示 */
const STAY_ALERT_DAYS = 3

// 源仓库：只允许选售后仓
const fetchWarehouses = (kw: string) => request.get('/warehouse/page', { params: { pageSize: 200, warehouseName: kw, warehouseCategory: 'INVENTORY', warehouseType: WarehouseType.AFTER_SALE } })
// 目标仓库：自有仓（成品仓/不良仓等）
const fetchTargetWarehouses = (kw: string) => request.get('/warehouse/page', { params: { pageSize: 200, warehouseName: kw, warehouseCategory: 'INVENTORY' } })
// A规/B规/C规 目标仓：必须为成品仓（与后端校验一致）
const fetchFinishedWarehouses = (kw: string) => request.get('/warehouse/page', { params: { pageSize: 200, warehouseName: kw, warehouseCategory: 'INVENTORY', warehouseType: WarehouseType.FINISHED } })

const warehouseOptions = ref<any[]>([])

function resetForm() {
  Object.assign(form, {
    id: undefined, warehouseId: undefined,
    sortDate: localDate(),
    targetWarehouseA: undefined, targetWarehouseB: undefined,
    targetWarehouseC: undefined, targetWarehouseDefect: undefined,
    lossAmount: 0, lossRemark: '', remark: ''
  })
  items.value = []
}

// 默认目标仓库：A/B/C 取第一个成品仓，不良取第一个不良仓
function applyTargetDefaults() {
  const fin = warehouseOptions.value.find((w: any) => w.warehouseType === WarehouseType.FINISHED)
  const def = warehouseOptions.value.find((w: any) => w.warehouseType === WarehouseType.DEFECT)
  form.targetWarehouseA = fin?.id
  form.targetWarehouseB = fin?.id
  form.targetWarehouseC = fin?.id
  form.targetWarehouseDefect = def?.id
}

// 加载售后仓待整理库存（待分类品）
async function loadDefectStock() {
  if (!form.warehouseId) { ElMessage.warning('请先选择源仓库(售后仓)'); return }
  try {
    const rows: any[] = await getReturnSortDefectStock(form.warehouseId)
    for (const r of rows) {
      // 按待整理批次逐行展开：同一产品可来自多张退单/换货单，各自独立成行以便追溯
      const exist = r.pendingId
        ? items.value.find((it) => it.pendingId === r.pendingId)
        : items.value.find((it) => it.productId === r.productId)
      const qty = Number(r.quantity) || 0
      if (exist) { exist.totalQuantity = qty; exist.available = qty; exist.stayDays = Number(r.stayDays) || 0 }
      else {
        items.value.push({
          pendingId: r.pendingId,
          sourceType: r.sourceType, sourceCode: r.sourceCode, sourceDate: r.sourceDate,
          sku: r.sku || '',
          productId: r.productId, productName: r.productName, spec: r.spec, unit: r.unit,
          totalQuantity: qty, available: qty,
          batchQuantity: Number(r.totalQuantity) || 0, sortedQuantity: Number(r.sortedQuantity) || 0,
          stayDays: Number(r.stayDays) || 0,
          qtyA: 0, qtyB: 0, qtyC: 0, qtyDefect: 0
        })
      }
    }
    if (rows.length === 0) ElMessage.info('该售后仓暂无待整理库存（待分类品）')
  } catch { ElMessage.error('加载待整理库存失败') }
}

function removeItem(index: number) { items.value.splice(index, 1) }
function itemSum(it: any) { return Number(it.qtyA || 0) + Number(it.qtyB || 0) + Number(it.qtyC || 0) + Number(it.qtyDefect || 0) }
function isItemValid(it: any) { return it.totalQuantity > 0 && itemSum(it) === Number(it.totalQuantity) }

/** 已加载的数据标识（新增/编辑切换或换单时重新拉取，避免残留上一单数据） */
const loadedKey = ref('')

async function init() {
  const key = id.value != null ? `edit-${id.value}` : 'add'
  if (loadedKey.value === key) return
  loadedKey.value = key

  resetForm()
  code.value = ''
  await loadWarehouses()
  if (id.value == null) { applyTargetDefaults(); return }

  loading.value = true
  try {
    const io = await getReturnSort(id.value)
    code.value = io.code || ''
    Object.assign(form, {
      id: io.id, warehouseId: io.warehouseId, sortDate: io.sortDate,
      targetWarehouseA: io.targetWarehouseA, targetWarehouseB: io.targetWarehouseB,
      targetWarehouseC: io.targetWarehouseC, targetWarehouseDefect: io.targetWarehouseDefect,
      lossAmount: Number(io.lossAmount || 0), lossRemark: io.lossRemark || '',
      remark: io.remark
    })
    const its = await getReturnSortItems(id.value)
    items.value = (its || []).map((it: any) => ({
      pendingId: it.pendingId,
      sourceType: it.sourceType, sourceCode: it.sourceCode, sourceDate: it.sourceDate,
      productId: it.productId, productName: it.productName, spec: it.spec, unit: it.unit,
      sku: it.sku || '',
      totalQuantity: Number(it.totalQuantity), qtyA: Number(it.qtyA), qtyB: Number(it.qtyB),
      qtyC: Number(it.qtyC), qtyDefect: Number(it.qtyDefect)
    }))
  } catch { ElMessage.error('获取详情失败') } finally { loading.value = false }
}

async function loadWarehouses() {
  try { const res: any = await request.get('/warehouse/page', { params: { pageSize: 500, warehouseCategory: 'INVENTORY' } }); warehouseOptions.value = res?.records || [] } catch { warehouseOptions.value = [] }
}

async function handleSave() {
  if (!form.warehouseId) { ElMessage.warning('请选择源仓库(售后仓)'); return }
  if (!form.targetWarehouseA || !form.targetWarehouseB || !form.targetWarehouseC || !form.targetWarehouseDefect) {
    ElMessage.warning('请选择 A规/B规/C规/不良 的目标入库仓库'); return
  }
  if (items.value.length === 0) { ElMessage.warning('请先加载售后仓待整理库存并录入分选数量'); return }
  if (Number(form.lossAmount) < 0) { ElMessage.warning('折损收款金额不能为负数'); return }
  for (const it of items.value) {
    if (!it.totalQuantity || it.totalQuantity <= 0) { ElMessage.warning(`产品「${it.productName || it.productId}」待整理数量必须大于0`); return }
    if (it.available != null && it.totalQuantity > it.available) {
      ElMessage.warning(`产品「${it.productName || it.productId}」待整理数量(${it.totalQuantity})超过售后仓可用待分类库存(${it.available})`)
      return
    }
    if (itemSum(it) !== Number(it.totalQuantity)) {
      ElMessage.warning(`产品「${it.productName || it.productId}」分选数量之和(${itemSum(it)})必须等于待整理数量(${it.totalQuantity})`)
      return
    }
  }
  saving.value = true
  try {
    const data = { ...form, items: items.value }
    if (form.id) { await updateReturnSort(form.id, data); ElMessage.success('修改成功') }
    else { await createReturnSort(data); ElMessage.success('新增成功') }
    router.push('/inventory/return-sort')
  } catch (e: any) { ElMessage.error(e?.message || '保存失败') } finally { saving.value = false }
}

function handleCancel() { router.push('/inventory/return-sort') }

onMounted(() => { init() })
// keep-alive 缓存下再次进入会复用组件；新增/编辑路由切换也需重新加载
onActivated(() => { init() })
watch(() => route.fullPath, () => { init() })
</script>

<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>{{ isNew ? '新增退货整理' : `编辑退货整理 — ${code}` }}</span>
          <div>
            <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
            <el-button @click="handleCancel">取消</el-button>
          </div>
        </div>
      </template>

      <el-form :model="form" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="源仓库" required>
              <RemoteSelect v-model="form.warehouseId" :fetch="fetchWarehouses" :label-key="(row:any)=>row.warehouseName" placeholder="选择售后仓" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="整理日期">
              <el-input v-model="form.sortDate" type="date" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="备注"><el-input v-model="form.remark" placeholder="备注" /></el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="A规入库仓" required>
              <RemoteSelect v-model="form.targetWarehouseA" :fetch="fetchFinishedWarehouses" :label-key="(row:any)=>row.warehouseName" placeholder="选择成品仓" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="B规入库仓" required>
              <RemoteSelect v-model="form.targetWarehouseB" :fetch="fetchFinishedWarehouses" :label-key="(row:any)=>row.warehouseName" placeholder="选择成品仓" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="C规入库仓" required>
              <RemoteSelect v-model="form.targetWarehouseC" :fetch="fetchFinishedWarehouses" :label-key="(row:any)=>row.warehouseName" placeholder="选择成品仓" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="不良入库仓" required>
              <RemoteSelect v-model="form.targetWarehouseDefect" :fetch="fetchTargetWarehouses" :label-key="(row:any)=>row.warehouseName" placeholder="选择仓库" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <!-- 折损收款：整理后才知道 B/C/不良 各多少，因此金额挂在本单而非销售退单 -->
        <el-row :gutter="12">
          <el-col :span="6">
            <el-form-item label="折损金额">
              <el-input-number v-model="form.lossAmount" :min="0" :precision="2" :step="10"
                controls-position="right" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="18">
            <el-form-item label="折损说明">
              <el-input v-model="form.lossRemark" placeholder="选填，如：C规 5 台按残值折损；审核后生成应收向客户收取" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <el-divider content-position="left">整理明细（待整理数量 = A + B + C + 不良）</el-divider>
      <div style="margin-bottom:8px">
        <el-button type="primary" :icon="'Download'" :disabled="!form.warehouseId" @click="loadDefectStock">加载售后仓待整理库存</el-button>
      </div>
      <el-table :data="items" border>
        <el-table-column label="来源单据" width="190" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag size="small" :type="row.sourceType === AfterSaleSourceType.SALE_EXCHANGE ? 'warning' : 'info'" style="margin-right:4px">
              {{ AfterSaleSourceTypeLabel[row.sourceType] || '-' }}
            </el-tag>
            {{ row.sourceCode || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="来源日期" width="110">
          <template #default="{ row }">{{ row.sourceDate || '-' }}</template>
        </el-table-column>
        <el-table-column prop="sku" label="SKU" width="130" />
        <el-table-column prop="productName" label="产品" min-width="160" show-overflow-tooltip />
        <el-table-column prop="spec" label="规格" width="110" show-overflow-tooltip />
        <el-table-column prop="unit" label="单位" width="70" />
        <el-table-column prop="totalQuantity" label="待整理数量" width="110" align="center">
          <template #default="{ row }"><b>{{ row.totalQuantity }}</b></template>
        </el-table-column>
        <el-table-column label="批次量/已整理" width="120" align="center">
          <template #default="{ row }">
            <span v-if="row.pendingId">{{ row.batchQuantity ?? 0 }} / {{ row.sortedQuantity ?? 0 }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="停留天数" width="100" align="center">
          <template #default="{ row }">
            <span :style="row.stayDays > STAY_ALERT_DAYS ? 'color:#f56c6c;font-weight:bold' : ''">
              {{ row.stayDays ?? '-' }}
              <span v-if="row.stayDays > STAY_ALERT_DAYS" :title="`已超过 ${STAY_ALERT_DAYS} 天未整理`">超期</span>
            </span>
          </template>
        </el-table-column>
        <el-table-column label="A数量" width="110">
          <template #default="{ row }"><el-input-number v-model="row.qtyA" :min="0" :precision="0" controls-position="right" style="width:100%" /></template>
        </el-table-column>
        <el-table-column label="B数量" width="110">
          <template #default="{ row }"><el-input-number v-model="row.qtyB" :min="0" :precision="0" controls-position="right" style="width:100%" /></template>
        </el-table-column>
        <el-table-column label="C数量" width="110">
          <template #default="{ row }"><el-input-number v-model="row.qtyC" :min="0" :precision="0" controls-position="right" style="width:100%" /></template>
        </el-table-column>
        <el-table-column label="不良数量" width="110">
          <template #default="{ row }"><el-input-number v-model="row.qtyDefect" :min="0" :precision="0" controls-position="right" style="width:100%" /></template>
        </el-table-column>
        <el-table-column label="校验" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="isItemValid(row) ? 'success' : 'danger'" size="small">{{ isItemValid(row) ? '✓ 相等' : `合计${itemSum(row)}` }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="70" align="center">
          <template #default="{ $index }"><el-button type="danger" link @click="removeItem($index)">删除</el-button></template>
        </el-table-column>
      </el-table>
    </el-card>

    <div style="text-align:center;margin-top:20px">
      <el-button @click="router.back()">返回</el-button>
    </div>
  </div>
</template>

<style scoped>
.card-header { display: flex; align-items: center; justify-content: space-between; }
</style>
