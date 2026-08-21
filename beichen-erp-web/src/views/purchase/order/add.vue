<template>
  <div class="purchase-add">
    <el-card shadow="never">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="供应商" prop="supplierId">
              <RemoteSelect v-model="form.supplierId" :fetch="fetchSuppliers" :initial-options="suppliers" placeholder="请选择" style="width:100%"
                @change="(v: any) => { if (v === ADD_MARKER) { form.supplierId = undefined; router.push('/supplier/manage'); return } }">
                <el-option label="+ 新增" :value="ADD_MARKER" />
              </RemoteSelect>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="入库仓库" prop="warehouseId">
              <RemoteSelect v-model="form.warehouseId" :fetch="fetchWarehouses" label-key="warehouseName" placeholder="请选择" style="width:100%"
                @change="(v: any) => { if (v === ADD_MARKER) { form.warehouseId = undefined; router.push('/inventory/warehouse'); return } }">
                <el-option label="+ 新增" :value="ADD_MARKER" />
              </RemoteSelect>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="订单日期">
              <el-date-picker v-model="form.orderDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="税率(%)">
              <el-input-number v-model="form.taxRate" :min="0" :max="100" :precision="2" controls-position="right" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="请输入备注" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">产品明细</el-divider>
        <div style="margin-bottom:8px">
          <el-button type="primary" @click="addItem">添加明细</el-button>
        </div>
        <el-table :data="items" border>
          <el-table-column type="index" label="#" width="50" align="center" />
          <el-table-column label="产品" min-width="160">
            <template #default="{ row }">
              <el-select v-model="row.productId" placeholder="选择产品" filterable remote :remote-method="loadMaterials"
                style="width:100%" @change="(v: any) => { if (v === ADD_MARKER) { row.productId = undefined; router.push('/material'); return } onMaterialChange(v, row as ItemRow) }">
                <el-option v-for="m in materialOptions" :key="m.id" :label="m.name" :value="m.id" />
                <el-option label="+ 新增" :value="ADD_MARKER" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column prop="spec" label="规格" width="90" />
          <el-table-column prop="unit" label="单位" width="60" />
          <el-table-column label="品质数量/单价" min-width="360">
            <template #default="{ row }">
              <div style="font-size:var(--app-font-xs);line-height:28px">
                <div style="display:flex;align-items:center;gap:4px">
                  <span style="width:36px;text-align:right;color:var(--app-text-secondary)">A规</span>
                  <el-input v-model="row.aQty" type="number" size="small" placeholder="数量" style="flex:1;min-width:56px" />
                  <el-input v-model="row.aPrice" type="number" size="small" placeholder="单价" style="flex:1;min-width:56px" />
                  <span style="width:64px;text-align:right">{{ gradeSubtotal(row.aQty, row.aPrice) }}</span>
                </div>
                <div style="display:flex;align-items:center;gap:4px">
                  <span style="width:36px;text-align:right;color:var(--app-text-secondary)">B规</span>
                  <el-input v-model="row.bQty" type="number" size="small" placeholder="数量" style="flex:1;min-width:56px" />
                  <el-input v-model="row.bPrice" type="number" size="small" placeholder="单价" style="flex:1;min-width:56px" />
                  <span style="width:64px;text-align:right">{{ gradeSubtotal(row.bQty, row.bPrice) }}</span>
                </div>
                <div style="display:flex;align-items:center;gap:4px">
                  <span style="width:36px;text-align:right;color:var(--app-text-secondary)">C规</span>
                  <el-input v-model="row.cQty" type="number" size="small" placeholder="数量" style="flex:1;min-width:56px" />
                  <el-input v-model="row.cPrice" type="number" size="small" placeholder="单价" style="flex:1;min-width:56px" />
                  <span style="width:64px;text-align:right">{{ gradeSubtotal(row.cQty, row.cPrice) }}</span>
                </div>
                <div style="display:flex;align-items:center;gap:4px">
                  <span style="width:36px;text-align:right;color:var(--app-text-secondary)">不良</span>
                  <el-input v-model="row.defectQty" type="number" size="small" placeholder="数量" style="flex:1;min-width:56px" />
                  <el-input v-model="row.defectPrice" type="number" size="small" placeholder="单价" style="flex:1;min-width:56px" />
                  <span style="width:64px;text-align:right">{{ gradeSubtotal(row.defectQty, row.defectPrice) }}</span>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="总数" width="70" align="center">
            <template #default="{ row }">{{ rowTotalQty(row as ItemRow) }}</template>
          </el-table-column>
          <el-table-column label="总金额" width="100" align="right">
            <template #default="{ row }">{{ rowTotalAmount(row as ItemRow).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column label="备注" width="120">
            <template #default="{ row }"><el-input v-model="row.remark" size="small" placeholder="备注" /></template>
          </el-table-column>
          <el-table-column label="操作" width="70" align="center">
            <template #default="{ $index }"><el-button type="danger" link @click="items.splice($index, 1)">删除</el-button></template>
          </el-table-column>
        </el-table>
      </el-form>

      <div style="text-align:center;margin-top:24px">
        <el-button @click="$router.back()">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">提交</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { WarehouseCategory } from '@/api/enums'
defineOptions({ name: 'PurchaseAdd' })
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import request from '@/utils/request'
import { ADD_MARKER } from '@/composables/useSelectWithAdd'
import type { PurchaseOrder } from '@/api/purchase'
import RemoteSelect from '@/components/RemoteSelect.vue'

/** 明细行数据结构（前端用，含4个品质的数量+单价） */
interface ItemRow {
  productId: any
  materialName: string
  spec: string
  unit: string
  aQty: string;   aPrice: string
  bQty: string;   bPrice: string
  cQty: string;   cPrice: string
  defectQty: string; defectPrice: string
  remark: string
}

const router = useRouter()
const formRef = ref<FormInstance>()
const submitLoading = ref(false)
const suppliers = ref<any[]>([])
const materialOptions = ref<any[]>([])
const items = ref<ItemRow[]>([])

const form = reactive<PurchaseOrder>({
  supplierId: undefined,
  warehouseId: undefined,
  orderDate: new Date().toISOString().slice(0, 10),
  taxIncluded: 0,
  taxRate: 0,
  remark: ''
})

const rules: FormRules = {
  supplierId: [{ required: true, message: '请选择供应商', trigger: 'change' }],
  warehouseId: [{ required: true, message: '请选择入库仓库', trigger: 'change' }],
}

/** 空明细行 */
function emptyRow(): ItemRow {
  return { productId: undefined, materialName: '', spec: '', unit: '', aQty: '', aPrice: '', bQty: '', bPrice: '', cQty: '', cPrice: '', defectQty: '', defectPrice: '', remark: '' }
}

function addItem() {
  items.value.push(emptyRow())
}

/** 单个品质等级的小计 = 数量 × 单价 */
function gradeSubtotal(qty: string, price: string): string {
  const n = (Number(qty) || 0) * (Number(price) || 0)
  return n > 0 ? n.toFixed(2) : ''
}

/** 计算某行的四等级总数量 */
function rowTotalQty(row: ItemRow): number {
  return (Number(row.aQty) || 0) + (Number(row.bQty) || 0) + (Number(row.cQty) || 0) + (Number(row.defectQty) || 0)
}

/** 计算某行的总金额（四等级各自 数量×单价 之和） */
function rowTotalAmount(row: ItemRow): number {
  return (Number(row.aQty) || 0) * (Number(row.aPrice) || 0)
       + (Number(row.bQty) || 0) * (Number(row.bPrice) || 0)
       + (Number(row.cQty) || 0) * (Number(row.cPrice) || 0)
       + (Number(row.defectQty) || 0) * (Number(row.defectPrice) || 0)
}

async function loadMaterials(query?: string) {
  try {
    const params: any = { pageSize: 100 }
    if (query) params.keyword = query
    const res = await request.get<any, any>('/product/page', { params })
    materialOptions.value = res?.records || []
  } catch { materialOptions.value = [] }
}

function onMaterialChange(val: any, row: ItemRow) {
  const m = materialOptions.value.find((x: any) => x.id === val)
  if (m) {
    row.productId = m.id
    row.materialName = m.name
    row.spec = m.spec
    row.unit = m.unit
  }
}

const fetchSuppliers = (kw: string) => request.get('/supplier/page', { params: { pageSize: 500, name: kw, supplierType: 'product' } })
const fetchWarehouses = (kw: string) => request.get('/warehouse/page', { params: { pageSize: 500, warehouseName: kw, warehouseCategory: WarehouseCategory.INVENTORY } })

async function loadSuppliers() {
  try {
    const res = await request.get('/supplier/page', { params: { pageSize: 200, supplierType: 'product' } })
    suppliers.value = res?.records || []
  } catch { suppliers.value = [] }
}

/** 将一行明细按品质等级拆分为多条提交项（每条带各自的数量+单价） */
function expandItems(row: ItemRow) {
  const result: any[] = []
  const grades = [
    { qualityType: 'A', qty: row.aQty, price: row.aPrice },
    { qualityType: 'B', qty: row.bQty, price: row.bPrice },
    { qualityType: 'C', qty: row.cQty, price: row.cPrice },
    { qualityType: 'DEFECT', qty: row.defectQty, price: row.defectPrice },
  ]
  for (const g of grades) {
    const n = Number(g.qty) || 0
    if (n <= 0) continue
    result.push({
      productId: row.productId,
      materialName: row.materialName,
      spec: row.spec,
      unit: row.unit,
      qualityType: g.qualityType,
      quantity: n,
      unitPrice: Number(g.price) || 0,
      remark: row.remark,
    })
  }
  return result
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    if (items.value.length === 0) { ElMessage.warning('请至少添加一条明细'); return }
    if (items.value.some(it => !it.productId)) { ElMessage.warning('请选择产品'); return }
    // 校验每行至少填了一个品质数量
    for (const row of items.value) {
      if (rowTotalQty(row) <= 0) {
        ElMessage.warning(`产品"${row.materialName}"至少需要填写一个品质等级的数量（A规/B规/C规/不良）`)
        return
      }
    }
    submitLoading.value = true
    try {
      // 每行按品质拆分，合并为完整明细列表
      const flatItems: any[] = []
      for (const row of items.value) {
        flatItems.push(...expandItems(row))
      }
      const body = {
        ...form,
        items: flatItems,
      }
      await request.post('/inventory/purchase', body)
      ElMessage.success('新增成功')
      router.back()
    } catch (e: any) { ElMessage.error(e?.message || '新增失败') }
    finally { submitLoading.value = false }
  })
}

// 从 URL query 预填：供应商 + 产品/物料（来自供应商详情页"去采购"）
async function initFromQuery() {
  const q = router.currentRoute.value.query
  if (q.supplierId) {
    form.supplierId = Number(q.supplierId)
    if (!suppliers.value.some(s => s.id === form.supplierId)) {
      try {
        // 按ID查单个供应商，确保 select 能匹配显示名称（不依赖 supplierType 过滤）
        const supplier = await request.get<any, any>('/supplier/' + form.supplierId)
        if (supplier) suppliers.value.unshift({ id: supplier.id, name: supplier.name })
      } catch { /* 忽略 */ }
    }
  }
  if (q.productId) {
    try {
      const p = await request.get<any, any>(`/product/${q.productId}`)
      if (p) {
        const it = items.value[0] || (items.value.push(emptyRow()), items.value[0])
        it.productId = Number(q.productId)
        it.materialName = p.name
        it.spec = p.spec
        it.unit = p.unit
      }
    } catch { /* 忽略 */ }
  } else if (q.materialId) {
    const it = items.value[0] || (items.value.push(emptyRow()), items.value[0])
    it.materialName = String(q.materialName ?? '') || ('物料#' + q.materialId)
    try {
      if (it.materialName) {
        const res = await request.get('/outsource/material/page', { params: { materialName: it.materialName, pageSize: 1 } })
        const rec = res?.records?.[0]
        if (rec) { it.spec = rec.spec || it.spec; it.unit = rec.unit || it.unit }
      }
    } catch { /* 忽略 */ }
  }
}

onMounted(async () => {
  await loadSuppliers()
  loadMaterials()
  initFromQuery()
})
</script>

<style scoped>

</style>
