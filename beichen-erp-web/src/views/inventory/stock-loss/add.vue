<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="title">{{ isEdit ? '编辑成品报损单' : '新增成品报损单' }}</span>
          <el-button :icon="'ArrowLeft'" @click="goBack">返回</el-button>
        </div>
      </template>

      <el-form :model="form" label-width="90px" class="head-form">
        <el-form-item label="仓库" required>
          <RemoteSelect v-model="form.warehouseId" :fetch="fetchWarehouses"
            :label-key="(row:any)=>row.warehouseName" placeholder="选择报损仓库" style="width:240px"
            @change="onWarehouseChange" />
        </el-form-item>
        <el-form-item label="报损日期">
          <el-date-picker v-model="form.lossDate" type="date" value-format="YYYY-MM-DD" style="width:180px" />
        </el-form-item>
        <el-form-item label="报损原因">
          <el-select v-model="form.lossReason" placeholder="请选择" clearable style="width:180px">
            <el-option v-for="r in lossReasons" :key="r.code" :label="r.label" :value="r.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" placeholder="选填" style="width:320px" />
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="title">报损明细</span>
          <el-button type="primary" :icon="'Plus'" @click="addItem">添加产品</el-button>
        </div>
      </template>

      <el-table :data="items" border stripe>
        <el-table-column label="产品" min-width="200">
          <template #default="{ row }">
            <RemoteSelect v-model="row.productId" :fetch="fetchProducts"
              :label-key="(r:any)=>r.name || r.productName" placeholder="选择产品" style="width:100%"
              @select="(r:any)=>onProductPick(r, row)" />
          </template>
        </el-table-column>
        <el-table-column label="SKU" width="130">
          <template #default="{ row }">{{ row.sku || '—' }}</template>
        </el-table-column>
        <el-table-column label="规格" width="120">
          <template #default="{ row }">{{ row.spec || '—' }}</template>
        </el-table-column>
        <el-table-column label="单位" width="70" align="center">
          <template #default="{ row }">{{ row.unit || '—' }}</template>
        </el-table-column>
        <el-table-column label="品质" width="110">
          <template #default="{ row }">
            <el-select v-model="row.qualityType" size="small" style="width:100%" @change="loadStock(row)">
              <el-option v-for="q in qualityOptions" :key="q.value" :label="q.label" :value="q.value" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="可用库存" width="100" align="right">
          <template #default="{ row }">
            <span v-if="row.stockQty != null">{{ fmtQty(row.stockQty) }}</span>
            <span v-else style="color:#999">—</span>
          </template>
        </el-table-column>
        <el-table-column label="报损数量" width="120" align="right">
          <template #default="{ row }">
            <el-input-number v-model="row.quantity" :min="0" :precision="4" :controls="false"
              size="small" style="width:100%" @change="() => calcAmount(row)" />
            <div v-if="overStock(row)" class="warn">超出可用库存</div>
          </template>
        </el-table-column>
        <el-table-column label="单价" width="110" align="right">
          <template #default="{ row }">
            <el-input-number v-model="row.unitPrice" :min="0" :precision="4" :controls="false"
              size="small" style="width:100%" @change="() => calcAmount(row)" />
          </template>
        </el-table-column>
        <el-table-column label="金额" width="110" align="right">
          <template #default="{ row }">{{ money(row.amount) }}</template>
        </el-table-column>
        <el-table-column label="备注" min-width="140">
          <template #default="{ row }">
            <el-input v-model="row.remark" size="small" placeholder="选填" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="70" align="center">
          <template #default="{ $index }">
            <el-button link type="danger" @click="removeItem($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="footer">
        <div class="total">合计报损金额：<strong>{{ money(totalAmount) }}</strong></div>
        <div class="actions">
          <el-button @click="goBack">取消</el-button>
          <el-button type="primary" :loading="saving" @click="handleSubmit">保存</el-button>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { localDate } from '@/utils/date'
import { computed, reactive, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import RemoteSelect from '@/components/RemoteSelect.vue'
import { getQualityTypes, type QualityOption } from '@/api/product'
import { WarehouseCategory, WarehouseType, ProductQualityType, LossReasonLabel, codeLabelOptions } from '@/api/enums'

const route = useRoute()
const router = useRouter()
const editId = computed(() => Number(route.params.id) || 0)
const isEdit = computed(() => editId.value > 0)

const fetchWarehouses = (kw: string) =>
  request.get('/warehouse/page', { params: { pageSize: 500, warehouseName: kw, warehouseCategory: WarehouseCategory.INVENTORY } })
    .then((res: any) => {
      res.records = (res.records || []).filter((w: any) => w.warehouseType !== WarehouseType.AUXILIARY)
      return res
    })
const fetchProducts = (kw: string) => request.get('/product/page', { params: { pageSize: 100, keyword: kw } })

const qualityOptions = ref<QualityOption[]>([])
const lossReasons = ref<any[]>([])
const saving = ref(false)

const form = reactive({
  warehouseId: undefined as number | undefined,
  lossDate: localDate(),
  lossReason: undefined as string | undefined,
  remark: ''
})
const items = ref<any[]>([])

function fmtQty(v?: number) { return v == null ? '0' : parseFloat(Number(v).toFixed(4)).toString() }
function money(v?: number) { return v == null ? '0.00' : Number(v).toFixed(2) }
const totalAmount = computed(() =>
  items.value.reduce((s, r) => s + (Number(r.amount) || 0), 0))

function newItem() {
  return {
    productId: undefined, sku: '', productName: '', spec: '', unit: '',
    qualityType: ProductQualityType.A as string, quantity: undefined, unitPrice: undefined,
    amount: 0, remark: '', stockQty: undefined as number | undefined
  }
}
function addItem() { items.value.push(newItem()) }
function removeItem(i: number) { items.value.splice(i, 1) }

/** 选产品后带出档案信息与成本价（单价可改），并查该仓库该品质的可用库存 */
async function onProductPick(p: any, row: any) {
  if (!p) return
  row.productId = p.id
  row.sku = p.sku || ''
  row.productName = p.name || ''
  row.spec = p.spec || ''
  row.unit = p.unit || ''
  // 单价优先取产品成本价，没有再取最近进价（后端保存时同样逻辑，这里只是提前展示）
  if (row.unitPrice == null) row.unitPrice = Number(p.costPrice) || Number(p.lastInPrice) || 0
  calcAmount(row)
  loadStock(row)
}

function calcAmount(row: any) {
  const q = Number(row.quantity) || 0
  const price = Number(row.unitPrice) || 0
  row.amount = Math.round(q * price * 100) / 100
}

/**
 * 查询可用库存：仓库 + 产品 + 品质三者任一变化都要重查。
 * 报损是出库行为，数量超过可用库存时标红（后端审核还会再校验一次）。
 */
async function loadStock(row: any) {
  row.stockQty = undefined
  if (!form.warehouseId || !row.productId) return
  try {
    const r = await request.get<any, any>('/warehouse/stock/page', {
      params: { warehouseId: form.warehouseId, productId: row.productId, stockType: 'PRODUCT', pageSize: 100 }
    })
    const hit = (r?.records || []).find((x: any) => String(x.qualityType) === String(row.qualityType))
    row.stockQty = hit ? Number(hit.quantity) : 0
  } catch { row.stockQty = undefined }
}
function onWarehouseChange() { items.value.forEach(loadStock) }
function overStock(row: any) {
  return row.stockQty != null && Number(row.quantity) > 0 && Number(row.quantity) > row.stockQty
}

async function loadDetail() {
  if (!editId.value) return
  try {
    const io = await request.get<any, any>(`/inventory/stock-loss/${editId.value}`)
    form.warehouseId = io.warehouseId
    form.lossDate = io.lossDate
    form.lossReason = io.lossReason || undefined
    form.remark = io.remark || ''
    const its = await request.get<any, any>(`/inventory/stock-loss/${editId.value}/items`)
    items.value = (its || []).map((i: any) => ({
      productId: i.productId, sku: i.sku || '', productName: i.productName || '',
      spec: i.spec || '', unit: i.unit || '', qualityType: i.qualityType || ProductQualityType.A,
      quantity: i.quantity, unitPrice: i.unitPrice, amount: i.amount,
      remark: i.remark || '', stockQty: undefined
    }))
    items.value.forEach(loadStock)
  } catch (e: any) { ElMessage.error(e?.message || '加载失败') }
}

async function handleSubmit() {
  if (!form.warehouseId) { ElMessage.warning('请选择仓库'); return }
  const valid = items.value.filter((i: any) => i.productId && Number(i.quantity) > 0)
  if (valid.length === 0) { ElMessage.warning('请添加报损明细（数量需大于 0）'); return }
  for (const it of valid) {
    if (overStock(it)) { ElMessage.warning('存在明细的报损数量超出可用库存，请调整'); return }
  }
  saving.value = true
  try {
    const payload = {
      loss: {
        warehouseId: form.warehouseId,
        lossDate: form.lossDate,
        lossReason: form.lossReason,
        remark: form.remark
      },
      items: valid.map((i: any) => ({
        productId: i.productId,
        qualityType: i.qualityType,
        quantity: i.quantity,
        unitPrice: i.unitPrice,
        remark: i.remark
      }))
    }
    if (isEdit.value) await request.put(`/inventory/stock-loss/${editId.value}`, payload)
    else await request.post('/inventory/stock-loss', payload)
    ElMessage.success('保存成功')
    router.push('/inventory/stock-loss')
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally { saving.value = false }
}

function goBack() { router.push('/inventory/stock-loss') }

onMounted(async () => {
  try { qualityOptions.value = await getQualityTypes() } catch { qualityOptions.value = [] }
  lossReasons.value = codeLabelOptions(LossReasonLabel) // 2026-09-14：前端枚举映射（后端接口已只回 code）
  if (isEdit.value) loadDetail()
  else items.value = [newItem()]
})
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 12px; }
.card-header { display: flex; align-items: center; justify-content: space-between; }
.title { font-weight: 600; }
.head-form { display: flex; flex-wrap: wrap; }
.head-form :deep(.el-form-item) { margin-bottom: 8px; }
.warn { color: #f56c6c; font-size: 12px; line-height: 16px; }
.footer { margin-top: 16px; display: flex; align-items: center; justify-content: space-between; }
.total { font-size: 14px; }
.total strong { color: #f56c6c; font-size: 16px; }
:deep(.el-card__body) { padding: 16px; }
</style>
