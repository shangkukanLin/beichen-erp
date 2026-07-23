<script setup lang="ts">
import { reactive, ref, computed, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { useTabStore } from '@/stores/tabs'

const router = useRouter()
const tabStore = useTabStore()

const form = reactive({ factoryId: undefined as any, warehouseId: undefined as any, returnDate: new Date().toISOString().slice(0, 10), remark: '' })
const factoryOptions = ref<any[]>([])
const warehouseOptions = ref<any[]>([])
const productList = ref<any[]>([]) // 该工厂所有产品汇总
const rows = ref<any[]>([createEmptyRow()])
const mergedItems = ref<any[]>([])
const loading = ref(false)

function createEmptyRow() {
  return { productName: undefined as any, selectedVersion: null as any, versions: [] as any[], returnQuantity: undefined as any, materials: [] as any[] }
}

function usedProducts(idx: number) {
  return rows.value.filter((_, i) => i !== idx).map(r => r.productName).filter(Boolean) as string[]
}

async function loadFactories() {
  try { const r = await request.get<any, any>('/supplier/page', { params: { supplierType: 'factory', pageSize: 200 } }); factoryOptions.value = r?.records || [] } catch {}
  try { const r = await request.get<any, any>('/inventory/warehouse/page', { params: { pageSize: 200 } }); warehouseOptions.value = r?.records || [] } catch {}
}

async function onFactoryChange(v: any) {
  rows.value = [createEmptyRow()]
  mergedItems.value = []
  productList.value = []
  if (!v) return
  loading.value = true
  try {
    const r = await request.get<any, any>('/outsource/return-order/order-products', { params: { factoryId: v } })
    productList.value = r || []
  } finally { loading.value = false }
}

function onProductChange(idx: number) {
  const row = rows.value[idx]
  row.selectedVersion = null
  row.versions = []
  row.materials = []
  if (!row.productName) { refreshMerged(); return }
  const p = productList.value.find((p: any) => p.productName === row.productName)
  if (p) {
    row.versions = p.bomVersions || []
    if (row.versions.length > 0) {
      row.selectedVersion = row.versions[0]
      loadBom(idx)
    }
  }
}

async function loadBom(idx: number) {
  const row = rows.value[idx]
  if (!row.selectedVersion) return
  try {
    row.materials = await request.get<any, any>('/outsource/return-order/bom-snapshot', {
      params: { orderId: row.selectedVersion.orderId, productId: row.selectedVersion.productId }
    }) || []
  } catch { row.materials = [] }
  refreshMerged()
}

function onVersionChange(idx: number) {
  loadBom(idx)
}

function onQtyChange() { refreshMerged() }

function addRow() { rows.value.push(createEmptyRow()) }
function removeRow(idx: number) {
  if (rows.value.length <= 1) { rows.value[0] = createEmptyRow(); refreshMerged(); return }
  rows.value.splice(idx, 1)
  refreshMerged()
}

function refreshMerged() {
  const map: Record<string, any> = {}
  for (const row of rows.value) {
    const qty = Number(row.returnQuantity) || 0
    if (qty <= 0 || !row.materials) continue
    for (const m of row.materials) {
      const key = m.materialName || ''
      if (!key) continue
      if (!map[key]) {
        map[key] = { materialName: key, materialType: m.materialType, unit: m.unit, quantity: 0, perSetQuantity: m.perSetQuantity }
      }
      map[key].quantity += qty * (Number(m.perSetQuantity) || 0)
    }
  }
  mergedItems.value = Object.values(map).filter((m: any) => m.quantity > 0)
}

async function handleSubmit() {
  if (!form.factoryId) { ElMessage.warning('请选择加工厂'); return }
  const items = mergedItems.value.map(m => ({
    materialName: m.materialName, materialType: m.materialType, unit: m.unit,
    quantity: m.quantity, unitPrice: '', remark: ''
  }))
  if (items.length === 0) { ElMessage.warning('请选择产品并填写退回数量'); return }
  try {
    await request.post('/outsource/return-order', { factoryId: form.factoryId, warehouseId: form.warehouseId, returnDate: form.returnDate, remark: form.remark, items, products: rows.value.filter((r: any) => r.productName && Number(r.returnQuantity) > 0).map((r: any) => ({ productName: r.productName, quantity: Number(r.returnQuantity) })) })
    ElMessage.success('退货单已创建')
    resetForm()
    ;(window as any).__returnOrderNeedRefresh = true
    tabStore.removeTab(window.location.hash.replace('#', ''))
    router.replace('/outsource/return-order')
  } catch (e: any) { ElMessage.error(e?.message || '创建失败') }
}

function resetForm() {
  Object.assign(form, { factoryId: undefined, warehouseId: undefined, returnDate: new Date().toISOString().slice(0, 10), remark: '' })
  rows.value = [createEmptyRow()]
  mergedItems.value = []
  productList.value = []
}

onMounted(() => { loadFactories() })
onActivated(() => { resetForm() })
</script>

<template>
  <div style="display:flex;flex-direction:column;gap:12px">
    <div class="page-header"><span class="page-title">新增委外退货</span></div>

    <el-card shadow="never">
      <template #header><span style="font-weight:600">退货信息</span></template>
      <el-form :model="form" label-width="90px" size="small">
        <el-row :gutter="16">
          <el-col :span="8"><el-form-item label="加工厂"><el-select v-model="form.factoryId" filterable clearable style="width:100%" @change="onFactoryChange"><el-option v-for="f in factoryOptions" :key="f.id" :label="f.name" :value="f.id" /></el-select></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="成品出库仓"><el-select v-model="form.warehouseId" filterable clearable style="width:100%"><el-option v-for="w in warehouseOptions" :key="w.id" :label="w.warehouseName" :value="w.id" /></el-select></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="退货日期"><el-input v-model="form.returnDate" type="date" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item></el-col>
        </el-row>
      </el-form>
    </el-card>

    <el-card shadow="never" v-if="form.factoryId">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span style="font-weight:600">选择产品及BOM版本</span>
          <el-button type="primary" size="small" @click="addRow">+ 添加产品</el-button>
        </div>
      </template>
      <el-table :data="rows" border size="small">
        <el-table-column label="产品" width="160">
          <template #default="{row,$index}">
            <el-select v-model="row.productName" size="small" filterable clearable style="width:100%" @change="onProductChange($index)">
              <el-option v-for="p in productList" :key="p.productName" :label="p.productName" :value="p.productName" :disabled="usedProducts($index).includes(p.productName)" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="BOM来源（加工单）" width="220">
          <template #default="{row,$index}">
            <el-select v-model="row.selectedVersion" size="small" style="width:100%" @change="onVersionChange($index)" value-key="orderId">
              <el-option v-for="v in row.versions" :key="v.orderId" :label="v.orderCode + ' (' + $fmtDate(v.createTime) + ')'" :value="v" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="退回数量" width="100">
          <template #default="{row}"><el-input v-model="row.returnQuantity" size="small" type="number" @change="onQtyChange()" /></template>
        </el-table-column>
        <el-table-column label="BOM物料（单套）" min-width="180">
          <template #default="{row}">
            <span v-if="row.materials.length" style="font-size:12px">
              {{ row.materials.map((m:any) => m.materialName + '×' + (Number(m.perSetQuantity)||0)).join('、') }}
            </span>
            <span v-else style="color:#c0c4cc;font-size:12px">选择产品后自动加载</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="60" align="center">
          <template #default="{ $index }"><el-button type="danger" link size="small" @click="removeRow($index)">删除</el-button></template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" v-if="mergedItems.length > 0">
      <template #header><span style="font-weight:600">拆解后的退货物料（合并去重）</span></template>
      <el-table :data="mergedItems" border size="small">
        <el-table-column prop="materialType" label="类型" width="80" />
        <el-table-column prop="materialName" label="物料名称" min-width="130" />
        <el-table-column prop="unit" label="单位" width="60" />
        <el-table-column prop="quantity" label="退回数量" width="100" align="right" />
      </el-table>
      <div style="margin-top:12px;text-align:right"><el-button type="primary" @click="handleSubmit">保存</el-button></div>
    </el-card>
  </div>
</template>
