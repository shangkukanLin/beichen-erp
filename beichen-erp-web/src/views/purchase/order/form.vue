<script setup lang="ts">
// 成品采购单 新增·编辑（2026-09-23 用户要求：原 900px 弹框改独立界面）
// ⚠️ 顺带修一个现存 bug：列表页 `handleAdd()` 早就在 push `/inventory/purchase/add`，
//    但该路由一直不存在 ⇒ 点「新增」白屏；本次把 /add 与 /edit/:id 一次补齐。
import { localDate } from '@/utils/date'
import { reactive, ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageShell from '@/components/PageShell.vue'
import { useUnsavedGuard } from '@/composables/usePageBack'
import { useTabStore } from '@/stores/tabs'
import { WarehouseCategory, WarehouseType } from '@/api/enums'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import request from '@/utils/request'
import { getQualityTypes, productLabel, type QualityOption } from '@/api/product'
import { ADD_MARKER } from '@/composables/useSelectWithAdd'
import RemoteSelect from '@/components/RemoteSelect.vue'
import {
  getPurchaseOrder, getPurchaseOrderItems, createPurchaseOrder, updatePurchaseOrder,
  getOutsourceMaterialPage, type PurchaseOrder, type PurchaseOrderItem, type OutsourceMaterialOption
} from '@/api/purchase'

const route = useRoute()
const router = useRouter()
const tabStore = useTabStore()
const isEdit = computed(() => route.path.includes('/edit/'))
const listPath = '/inventory/purchase'

const loading = ref(false)
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<PurchaseOrder>({
  supplierId: undefined, warehouseId: undefined, orderDate: localDate(), taxIncluded: 0, taxRate: 0, remark: ''
})
const items = ref<PurchaseOrderItem[]>([])
/**
 * 未保存拦截（2026-09-23 统一模板）
 * ⚠️ 必须写在 form / items 等状态**之后**（watch 注册时立即求值，放前面会 TDZ 静默失效）。
 */
const { takeBaseline, markClean } = useUnsavedGuard(() => ({ form, items: items.value }))
const qualityOptions = ref<QualityOption[]>([])
const materialOptions = ref<OutsourceMaterialOption[]>([])

const rules: FormRules = {
  supplierId: [{ required: true, message: '请选择供货商', trigger: 'change' }],
  warehouseId: [{ required: true, message: '请选择入库仓库', trigger: 'change' }]
}
const fetchMaterials = (kw: string) => request.get('/product/page', { params: { pageSize: 500, keyword: kw } })
const fetchSuppliers = (kw: string) => request.get('/supplier/page', { params: { pageSize: 500, name: kw } })
// 采购入库仓统一为**自有成品仓**（与 purchase/exchange/add.vue 同口径）：不过滤就可能把成品采进委外仓/辅料仓
const fetchWarehouses = (kw: string) => request.get('/warehouse/page',
  { params: { pageSize: 500, warehouseName: kw, warehouseCategory: WarehouseCategory.INVENTORY, warehouseType: WarehouseType.FINISHED } })
const loadMaterials = async (keyword?: string) => {
  try {
    const res = await getOutsourceMaterialPage({ pageNum: 1, pageSize: 100, materialName: keyword || '' })
    materialOptions.value = res?.records || []
  } catch { materialOptions.value = [] }
}

function addItem() {
  items.value.push({ productId: undefined, qualityType: 'A', materialName: '', unit: '', quantity: 0, unitPrice: 0, amount: 0, remark: '' })
}
function removeItem(index: number) { items.value.splice(index, 1) }
function onMaterialChange(val: number, row: PurchaseOrderItem) {
  const m = materialOptions.value.find(x => x.id === val)
  if (m) { row.productId = m.id as number; row.materialName = m.materialName; row.unit = m.unit }
}
function itemAmount(row: PurchaseOrderItem) {
  const q = Number(row.quantity) || 0, p = Number(row.unitPrice) || 0
  return (q * p).toFixed(2)
}
// 税额拆分（单价含税口径）：应付总额不变，按税率从总额中拆出税额
const goodsTotal = computed(() => items.value.reduce((s, r) => s + (Number(r.quantity) || 0) * (Number(r.unitPrice) || 0), 0))
const taxAmount = computed(() => form.taxIncluded === 1 && Number(form.taxRate) > 0
  ? Math.round(goodsTotal.value * (Number(form.taxRate) / (100 + Number(form.taxRate))) * 100) / 100
  : 0)
const noTaxAmount = computed(() => Math.round((goodsTotal.value - taxAmount.value) * 100) / 100)
function onTaxSwitch(v: any) { form.taxIncluded = v ? 1 : 0; form.taxRate = v ? (form.taxRate || 13) : 0 }

async function load() {
  loading.value = true
  try {
    // getQualityTypes() 是同步返回的品质选项常量（与列表页同一口径）
    qualityOptions.value = getQualityTypes() || []
    await loadMaterials()
    if (isEdit.value) {
      const id = Number(route.params.id)
      const [o, its] = await Promise.all([
        getPurchaseOrder(id).catch(() => null),
        getPurchaseOrderItems(id).catch(() => [])
      ])
      if (o) Object.assign(form, o)
      items.value = (its as any) || []
    }
  } finally { loading.value = false }
}

async function submit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    if (items.value.length === 0) { ElMessage.warning('请至少添加一条明细'); return }
    submitLoading.value = true
    try {
      const payload = { order: { ...form }, items: items.value }
      if (form.id) { await updatePurchaseOrder(form.id as number, payload); ElMessage.success('修改成功') }
      else { await createPurchaseOrder(payload); ElMessage.success('新增成功') }
      // 保存成功 ⇒ 先清脏标记（否则离开会被未保存确认拦住），再关掉本页签回列表
      markClean()
      tabStore.closeTabAndBack(route.path)
      router.push(listPath)
    } catch { /* 拦截器已提示 */ } finally { submitLoading.value = false }
  })
}
onMounted(async () => { await load(); takeBaseline() })
</script>

<template>
  <!-- 统一骨架（2026-09-23 全站最终口径）：页头左端=返回 → 标题 → 右端=操作（确定） -->
  <PageShell :title="isEdit ? '编辑成品采购单' : '新增成品采购单'" :loading="loading" back-fallback="/inventory/purchase">
    <template #actions>
      <el-button type="primary" :loading="submitLoading" @click="submit">确定</el-button>
    </template>

    <el-card shadow="never">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="var(--app-label-width)">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="供货商" prop="supplierId">
              <RemoteSelect v-model="form.supplierId" :fetch="fetchSuppliers" placeholder="请选择" style="width:100%" @change="(v: any) => { if (v === ADD_MARKER) { form.supplierId = undefined; router.push('/outsource/supplier/manage'); return } }">
                <el-option label="+ 新增" :value="ADD_MARKER" />
              </RemoteSelect>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="入库仓库" prop="warehouseId">
              <RemoteSelect v-model="form.warehouseId" :fetch="fetchWarehouses" label-key="warehouseName" placeholder="请选择" style="width:100%" @change="(v: any) => { if (v === ADD_MARKER) { form.warehouseId = undefined; router.push('/inventory/warehouse'); return } }">
                <el-option label="+ 新增" :value="ADD_MARKER" />
              </RemoteSelect>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="订单日期">
              <el-date-picker v-model="form.orderDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="含税"><el-switch :model-value="form.taxIncluded === 1" @change="onTaxSwitch" /></el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="税率(%)">
              <el-input-number v-model="form.taxRate" :min="0" :max="100" :precision="2" :disabled="form.taxIncluded !== 1" controls-position="right" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" placeholder="请输入备注" /></el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">明细</el-divider>
        <div style="margin-bottom:8px"><el-button type="primary" :icon="'Plus'" @click="addItem">添加明细</el-button></div>
        <el-table :data="items" border>
          <el-table-column label="产品" min-width="180">
            <template #default="{ row }">
              <RemoteSelect v-model="row.productId" :fetch="fetchMaterials" :label-key="productLabel" placeholder="选择物料（可输SKU）" style="width:100%" @change="(v: any) => { if (v === ADD_MARKER) { row.productId = undefined; router.push('/product/add'); return } onMaterialChange(v, row) }">
                <el-option label="+ 新增" :value="ADD_MARKER" />
              </RemoteSelect>
            </template>
          </el-table-column>
          <el-table-column prop="unit" label="单位" width="70" />
          <el-table-column label="品质" width="90">
            <template #default="{ row }">
              <el-select v-model="row.qualityType" size="small" style="width:100%">
                <el-option v-for="q in qualityOptions" :key="q.value" :label="q.label" :value="q.value" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="数量" width="120"><template #default="{ row }"><el-input-number v-model="row.quantity" :min="0" :step="1" :precision="0" controls-position="right" style="width:100%" /></template></el-table-column>
          <el-table-column label="单价" width="120"><template #default="{ row }"><el-input-number v-model="row.unitPrice" :min="0" :precision="2" controls-position="right" style="width:100%" /></template></el-table-column>
          <el-table-column label="金额" width="110" align="right"><template #default="{ row }">{{ itemAmount(row) }}</template></el-table-column>
          <el-table-column label="操作" width="70" align="center"><template #default="{ $index }"><el-button type="danger" link @click="removeItem($index)">删除</el-button></template></el-table-column>
        </el-table>
        <div class="sum-bar">
          <span>应付总额（含税）：<b>{{ goodsTotal.toFixed(2) }}</b></span>
          <template v-if="form.taxIncluded === 1">
            <span>税额（{{ form.taxRate }}%）： <b class="tax-num">{{ taxAmount.toFixed(2) }}</b></span>
            <span>不含税金额： <b>{{ noTaxAmount.toFixed(2) }}</b></span>
          </template>
        </div>
      </el-form>

    </el-card>
  </PageShell>
</template>

<style scoped>
/* 页头/底部操作条已统一到全局骨架（PageShell + styles/page.css） */
.sum-bar { margin-top: 12px; display: flex; justify-content: flex-end; gap: 24px; font-size: var(--app-font-base); color: var(--app-text-secondary); }
.sum-bar b { color: var(--app-text-primary); font-size: var(--app-font-num-sm); }
.tax-num { color: var(--app-color-danger); }
</style>
