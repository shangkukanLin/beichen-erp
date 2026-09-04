<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { OUTSOURCE_RETURN_ORDER_DIRTY_KEY, OutsourceChargeType, OutsourceChargeTypeLabel } from '@/api/enums'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { useTabStore } from '@/stores/tabs'
import RemoteSelect from '@/components/RemoteSelect.vue'

const router = useRouter()
const tabStore = useTabStore()

const form = reactive({
  factoryId: undefined as any, warehouseId: undefined as any,
  returnDate: new Date().toISOString().slice(0, 10), remark: '',
  // 收费（我方支付给加工厂的费用），审核后生成一条正向应付
  chargeFlag: 0, chargeType: '' as string, chargeAmount: 0, chargeReason: ''
})
const chargeTypeOptions = computed(() =>
  Object.values(OutsourceChargeType).map((v) => ({ value: v, label: OutsourceChargeTypeLabel[v] || v }))
)
const factoryOptions = ref<any[]>([])
const warehouseOptions = ref<any[]>([])
const productList = ref<any[]>([]) // 该工厂所有产品汇总
const rows = ref<any[]>([createEmptyRow()])
const mergedItems = ref<any[]>([])
const bomTypes = ref<any[]>([])
const loading = ref(false)

// bomTypeId -> 类型名 映射供展示
function typeName(id: number | undefined) { if (id == null) return '-'; const t = bomTypes.value.find((v: any) => v.id === id); return t ? t.typeName : (id as any) }

function createEmptyRow() {
  return { productName: undefined as any, selectedVersion: null as any, versions: [] as any[], returnQuantity: undefined as any, materials: [] as any[], stock: undefined as number | undefined }
}

function usedProducts(idx: number) {
  return rows.value.filter((_, i) => i !== idx).map(r => r.productName).filter(Boolean) as string[]
}

// ===== 纯 Odoo 方案：本地轻量列表 + RemoteSelect 实时查库 =====
const fetchSuppliers = (kw: string) =>
  request.get('/supplier/page', { params: { supplierType: 'factory', name: kw, pageSize: 500 } })
// 成品出库仓只取我方成品仓：自有仓库(INVENTORY) + 类型=成品仓，排除委外仓/不良仓/售后仓等
const fetchWarehouses = (kw: string) =>
  request.get('/warehouse/page', { params: { warehouseName: kw, warehouseCategory: 'INVENTORY', warehouseType: '成品仓', pageSize: 500 } })

async function loadFactories() {
  const [sf, wf]: any[] = await Promise.all([fetchSuppliers(''), fetchWarehouses('')])
  factoryOptions.value = sf?.records || []
  warehouseOptions.value = wf?.records || []
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
  row.stock = undefined
  if (!row.productName) { refreshMerged(); return }
  const p = productList.value.find((p: any) => p.productName === row.productName)
  if (p) {
    row.versions = p.bomVersions || []
    if (row.versions.length > 0) {
      row.selectedVersion = row.versions[0]
      loadBom(idx)
      loadStock(idx)
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

/**
 * 加载该产品在所选成品仓的库存（各品质合计）。
 * 未选仓库或未选产品时置空，展示为「—」；仓库/产品/BOM版本任一变化都会重新拉取。
 */
async function loadStock(idx: number) {
  const row = rows.value[idx]
  row.stock = undefined
  const productId = row.selectedVersion?.productId
  if (!form.warehouseId || !productId) return
  try {
    const r = await request.get<any, any>('/warehouse/stock/page', {
      params: { warehouseId: form.warehouseId, productId, stockType: 'PRODUCT', pageSize: 500 }
    })
    const recs: any[] = r?.records || []
    row.stock = recs.reduce((s, x) => s + (Number(x.quantity) || 0), 0)
  } catch { row.stock = undefined }
}

/** 退回数量超过库存 */
function overStock(row: any) {
  return row.stock != null && Number(row.returnQuantity) > 0 && Number(row.returnQuantity) > Number(row.stock)
}

function onVersionChange(idx: number) {
  loadBom(idx)
  loadStock(idx)
}

/** 切换成品出库仓：所有已选产品的库存都要按新仓库重新查 */
function onWarehouseChange() {
  rows.value.forEach((_, i) => loadStock(i))
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
      const key = m.outsourceMaterialId || m.materialName || ''
      if (!key) continue
      if (!map[key]) {
        map[key] = { materialId: m.outsourceMaterialId, bomTypeId: m.bomTypeId, materialName: m.materialName, unit: m.unit, quantity: 0, perSetQuantity: m.perSetQuantity }
      }
      map[key].quantity += qty * (Number(m.perSetQuantity) || 0)
    }
  }
  mergedItems.value = Object.values(map).filter((m: any) => m.quantity > 0)
}

async function handleSubmit() {
  if (!form.factoryId) { ElMessage.warning('请选择加工厂'); return }
  // 成品出库仓必选：库存按「仓库+产品」校验，不先选仓就没有比对基准
  if (!form.warehouseId) { ElMessage.warning('请选择成品出库仓'); return }
  // 收费校验：选择收费时必须指定类型且金额 > 0（后端会再校验一次，此处提前给提示）
  const charged = Number(form.chargeFlag) === 1
  if (charged) {
    if (!form.chargeType) { ElMessage.warning('已选择收费，请选择收费类型'); return }
    if (!(Number(form.chargeAmount) > 0)) { ElMessage.warning('已选择收费，收费金额必须大于 0'); return }
  }
  // 库存校验：退回数量不能超出所选成品仓的库存（未选仓库时查不到库存，不拦截）
  for (const r of rows.value as any[]) {
    if (!r.productName || !(Number(r.returnQuantity) > 0)) continue
    if (r.stock == null) continue
    if (Number(r.returnQuantity) > Number(r.stock)) {
      ElMessage.warning(`产品「${r.productName}」退回数量 ${r.returnQuantity} 超过库存 ${r.stock}，无法保存`)
      return
    }
  }
  const items = mergedItems.value.map(m => ({
    materialId: m.materialId, bomTypeId: m.bomTypeId, unit: m.unit,
    quantity: m.quantity, unitPrice: '', remark: ''
  }))
  if (items.length === 0) { ElMessage.warning('请选择产品并填写退回数量'); return }
  if (items.some((m: any) => !m.materialId)) { ElMessage.warning('存在未关联委外物料的明细，无法保存，请检查BOM物料是否已登记'); return }
  try {
    await request.post('/outsource/return-order', {
      factoryId: form.factoryId, warehouseId: form.warehouseId,
      returnDate: form.returnDate, remark: form.remark,
      chargeFlag: Number(form.chargeFlag) === 1 ? 1 : 0,
      chargeType: Number(form.chargeFlag) === 1 ? form.chargeType : '',
      chargeAmount: Number(form.chargeFlag) === 1 ? Number(form.chargeAmount) : 0,
      chargeReason: Number(form.chargeFlag) === 1 ? (form.chargeReason || '') : '',
      items, products: rows.value.filter((r: any) => r.productName && Number(r.returnQuantity) > 0).map((r: any) => ({ productName: r.productName, productId: r.selectedVersion?.productId || null, quantity: Number(r.returnQuantity) }))
    })
    ElMessage.success('退货单草稿已保存，请在列表中审核生效'); sessionStorage.setItem(OUTSOURCE_RETURN_ORDER_DIRTY_KEY, '1')
    resetForm()
    tabStore.removeTab(window.location.hash.replace('#', ''))
    router.replace('/outsource/return-order')
  } catch (e: any) { ElMessage.error(e?.message || '创建失败') }
}

function resetForm() {
  Object.assign(form, {
    factoryId: undefined, warehouseId: undefined,
    returnDate: new Date().toISOString().slice(0, 10), remark: '',
    chargeFlag: 0, chargeType: '', chargeAmount: 0, chargeReason: ''
  })
  rows.value = [createEmptyRow()]
  mergedItems.value = []
  productList.value = []
}

onMounted(() => { loadFactories(); loadBomTypes() })
async function loadBomTypes() {
  try { const r = await request.get<any, any>('/dev/bom-type/enabled'); bomTypes.value = r || [] } catch { bomTypes.value = [] }
}

</script>

<template>
  <div style="display:flex;flex-direction:column;gap:12px">
    <el-card shadow="never">
      <template #header><span style="font-weight:600">退货信息</span></template>
      <el-form :model="form" label-width="90px" size="small">
        <el-row :gutter="16">
          <el-col :span="8"><el-form-item label="加工厂"><RemoteSelect v-model="form.factoryId" :fetch="fetchSuppliers" placeholder="请选择加工厂" @update:modelValue="onFactoryChange" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="成品出库仓" required><RemoteSelect v-model="form.warehouseId" :fetch="fetchWarehouses" :label-key="(row:any)=>row.warehouseName" placeholder="请选择出库仓（我方成品仓）" @update:modelValue="onWarehouseChange" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="退货日期"><el-input v-model="form.returnDate" type="date" /></el-form-item></el-col>
          <el-col :span="6">
            <el-form-item label="是否收费">
              <el-switch v-model="form.chargeFlag" :active-value="1" :inactive-value="0" active-text="收费" inactive-text="不收费" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="收费类型" :required="form.chargeFlag === 1">
              <el-select v-model="form.chargeType" placeholder="请选择" clearable style="width:100%" :disabled="form.chargeFlag !== 1">
                <el-option v-for="o in chargeTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="收费金额" :required="form.chargeFlag === 1">
              <el-input-number v-model="form.chargeAmount" :min="0" :precision="2" :step="10" controls-position="right" style="width:100%" :disabled="form.chargeFlag !== 1" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="收费说明">
              <el-input v-model="form.chargeReason" placeholder="选填，如：返工费 / 运费 / 检测费 / 超损赔偿" :disabled="form.chargeFlag !== 1" />
            </el-form-item>
          </el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item></el-col>
        </el-row>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span style="font-weight:600">选择产品及BOM版本</span>
          <el-button type="primary" size="small" :disabled="!form.factoryId" @click="addRow">+ 添加产品</el-button>
        </div>
      </template>
      <el-table :data="rows" border size="small">
        <el-table-column label="产品" width="160">
          <template #default="{row,$index}">
            <el-select v-model="row.productName" size="small" filterable clearable style="width:100%"
              :disabled="!form.factoryId" :placeholder="form.factoryId ? '请选择产品' : '请先选择加工厂'"
              @change="onProductChange($index)">
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
        <el-table-column label="库存" width="90" align="right">
          <template #default="{row}">
            <span v-if="row.stock != null" :style="overStock(row) ? 'color:#f56c6c;font-weight:600' : ''">{{ Number(row.stock) }}</span>
            <span v-else style="color:var(--app-text-placeholder)">—</span>
          </template>
        </el-table-column>
        <el-table-column label="退回数量" width="110">
          <template #default="{row}">
            <el-input v-model="row.returnQuantity" size="small" type="number" @change="onQtyChange()" />
            <div v-if="overStock(row)" style="color:#f56c6c;font-size:var(--app-font-xs);line-height:1.2;margin-top:2px">超出库存</div>
          </template>
        </el-table-column>
        <el-table-column label="BOM物料（单套）" min-width="180">
          <template #default="{row}">
            <span v-if="row.materials.length" style="font-size:var(--app-font-xs)">
              {{ row.materials.map((m:any) => m.materialName + '×' + (Number(m.perSetQuantity)||0)).join('、') }}
            </span>
            <span v-else style="color:var(--app-text-placeholder);font-size:var(--app-font-xs)">选择产品后自动加载</span>
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
        <el-table-column label="类型" width="80"><template #default="{row}">{{ typeName(row.bomTypeId) }}</template></el-table-column>
        <el-table-column prop="materialName" label="物料名称" min-width="130" />
        <el-table-column prop="unit" label="单位" width="60" />
        <el-table-column prop="quantity" label="退回数量" width="100" align="right" />
      </el-table>
      <div style="margin-top:12px;text-align:right"><el-button type="primary" @click="handleSubmit">保存</el-button></div>
    </el-card>
  </div>
</template>
