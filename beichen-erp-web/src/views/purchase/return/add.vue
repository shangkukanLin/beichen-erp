<template>
  <!-- 统一骨架（2026-09-23 全站最终口径）：页头左端=返回 → 标题 → 右端=操作（保存） -->
  <PageShell :title="isEdit ? '编辑成品采购退货单' : '新增成品采购退货单'" back-fallback="/inventory/purchase-return">
    <template #actions>
      <el-button type="primary" :loading="submitLoading" @click="handleSubmit">保存</el-button>
    </template>

    <el-card shadow="never">
      <!-- label 宽度用 **xl 档**（2026-09-28 全局扫描）：「付费合计（自动）」这类带全角括号的长标签实测需 124px -->
      <el-form ref="formRef" :model="form" :rules="rules" label-width="var(--app-label-width-xl)">
        <el-alert v-if="form.purchaseOrderCode" type="success" :closable="false" style="margin-bottom:12px"
          :title="`来源采购单：${form.purchaseOrderCode}（已自动带入采购明细，可修改）`" />
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="供货商" prop="supplierId">
              <RemoteSelect v-model="form.supplierId" :fetch="fetchSuppliers" placeholder="请选择" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="退货仓库" prop="warehouseId">
              <RemoteSelect v-model="form.warehouseId" :fetch="fetchWarehouses" label-key="warehouseName" placeholder="请选择" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="退货日期">
              <el-date-picker v-model="form.returnDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="请输入备注" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">退货明细</el-divider>
        <div style="margin-bottom:8px">
          <el-button type="primary" :icon="'Plus'" @click="addItem">添加明细</el-button>
          <el-button type="success" :icon="'Download'" :disabled="!form.purchaseOrderId" @click="loadFromPurchaseOrder()">从采购单带入明细</el-button>
        </div>
        <!-- 2026-09-21（UI + 逐产品付费）：原 9 列 ~1220px ⇒ 横向滚动。现：
             ① 删掉「SKU」独占列（产品下拉已按 productLabel 显示「SKU | 名称」，信息不丢）
             ② 控件 size=small、数量/单价 :controls=false ⇒ 更窄
             ③ 新增「付费」列（金额 + 类型，**逐行可不同**；金额 0 = 该产品不付费）
             ④ 列宽合计 802px < 内容区 948px ⇒ 一行显示完、不左右滑动 -->
        <el-table :data="items" border size="small" max-height="420">
          <el-table-column label="产品" width="176" show-overflow-tooltip>
            <template #default="{ row }">
              <el-select v-model="row.productId" placeholder="选择产品（可输SKU）" filterable remote :remote-method="loadProducts"
                size="small" style="width:100%" @change="(v: number) => onProductChange(v, row)">
                <el-option v-for="m in productOptions" :key="m.id" :label="productLabel(m)" :value="m.id" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="现有库存" width="68" align="center">
            <template #default="{ row }">
              <span :style="{ color: (row._stock ?? 0) <= 0 ? 'red' : '' }">{{ row._stock ?? '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="品质" width="72">
            <template #default="{ row }">
              <el-select v-model="row.qualityType" size="small" style="width:100%">
                <el-option v-for="q in qualityOptions" :key="q.value" :label="q.label" :value="q.value" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="可退数量" width="60" align="center">
            <template #default="{ row }">
              <span v-if="row.canReturn !== undefined">{{ row.canReturn }}</span>
              <span v-else>—</span>
            </template>
          </el-table-column>
          <el-table-column label="退货数量" width="74">
            <template #default="{ row }"><el-input-number v-model="row.quantity" :min="1" :step="1" :precision="0" size="small" :controls="false" :max="row.canReturn !== undefined ? row.canReturn : undefined" style="width:100%" @change="calcAmount" /></template>
          </el-table-column>
          <el-table-column label="单价" width="74">
            <template #default="{ row }"><el-input-number v-model="row.unitPrice" :min="0" :precision="2" size="small" :controls="false" style="width:100%" @change="calcAmount" /></template>
          </el-table-column>
          <el-table-column label="金额" width="82" align="right">
            <template #default="{ row }">{{ ((Number(row.quantity) || 0) * (Number(row.unitPrice) || 0)).toFixed(2) }}</template>
          </el-table-column>
          <!-- 逐产品付费（2026-09-21 用户口径：采购退货也要有付费、且精确到产品；方向=我方付给供货商）。
               金额 > 0 即该产品付费；类型**必选**（填了金额必须能定类型） -->
          <el-table-column label="付费" width="146" align="center">
            <template #default="{ row }">
              <div style="display:flex;gap:4px">
                <el-input-number v-model="row.chargeAmount" :min="0" :precision="2" size="small" :controls="false"
                  placeholder="金额" style="width:68px" />
                <el-select v-model="row.chargeType" size="small" placeholder="类型" clearable style="width:66px"
                  :disabled="!(Number(row.chargeAmount) > 0)">
                  <el-option v-for="o in chargeTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
                </el-select>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="50" align="center">
            <template #default="{ $index }"><el-button type="danger" link @click="items.splice($index, 1)">删除</el-button></template>
          </el-table-column>
        </el-table>

        <!-- 逐产品付费（2026-09-21 用户口径）：金额填在**明细行的「付费」列**上（一行 = 一个产品），
             这里只留「合计（自动，只读）」+「整单说明」；是否付费由明细推导（合计 > 0 ⇒ 付费）。
             ⚠️ 方向：**我方付给供货商** ⇒ 审核生成一条正向应付（金额 = Σ明细，remark 逐产品）。
             与退货本身分开记账：退货侧是负数应付（冲减欠款），付费是正数应付（额外要付的钱）。 -->
        <el-row :gutter="16" style="margin-top:12px">
          <el-col :span="6">
            <el-form-item label="付费合计（自动）">
              <span style="font-weight:600;color:#e6a23c">{{ chargeTotal.toFixed(2) }}</span>
              <span style="margin-left:6px;color:var(--app-text-secondary);font-size:var(--app-font-xs)">= Σ 明细行付费</span>
            </el-form-item>
          </el-col>
          <el-col :span="10">
            <el-form-item label="付费说明（整单）">
              <el-input v-model="chargeReason" placeholder="选填，如：退货处理费 / 品质折让补价（落到该单付费台账备注）" />
            </el-form-item>
          </el-col>
        </el-row>
        <div v-if="chargeTotal > 0" style="margin:0 0 10px 110px;font-size:var(--app-font-xs);color:var(--app-color-warning)">
          付费方向：<b>我方付给供货商</b> ⇒ 审核后额外生成一条正向应付（我方欠供货商 +{{ chargeTotal.toFixed(2) }}）
        </div>
      </el-form>

    </el-card>
  </PageShell>
</template>

<script setup lang="ts">
import { localDate } from '@/utils/date'
defineOptions({ name: 'PurchaseReturnAdd' })
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { PURCHASE_RETURN_DIRTY_KEY, WarehouseCategory, WarehouseType, PurchaseChargeType, PurchaseChargeTypeLabel } from '@/api/enums'
import { useRouter, useRoute } from 'vue-router'
import PageShell from '@/components/PageShell.vue'
import { useUnsavedGuard } from '@/composables/usePageBack'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useTabStore } from '@/stores/tabs'
import request from '@/utils/request'
import { applyPageTitle } from '@/utils/pageTitle'
import { getQualityTypes, productLabel, type QualityOption } from '@/api/product'
// 2026-09-20（F7-157）：原页内直接拼 request.get/post/put('/inventory/purchase-return...') 绕过 API 层
//（同模块"部分走封装、部分直连"，且同一端点在本页与详情页各写一份字面量）⇒ 统一改用 @/api/purchase
import {
  getPurchaseReturn, getPurchaseReturnItems, createPurchaseReturn, updatePurchaseReturn,
  getPurchaseReturnPurchaseOrderItems, getPurchaseReturnSourceOrder,
} from '@/api/purchase'
import RemoteSelect from '@/components/RemoteSelect.vue'

interface ReturnItem {
  productId?: number
  sku?: string
  qualityType?: string
  purchaseOrderItemId?: number
  canReturn?: number
  _stock?: number
  quantity?: number
  unitPrice?: number
  /**
   * 逐产品付费（2026-09-21 用户口径：采购退货也要有付费、精确到产品；方向 = 我方付给供货商）。
   * 金额挂在明细行（一行 = 一个产品）；金额 > 0 即该产品付费，类型必选。
   */
  chargeAmount?: number
  chargeType?: string
  chargeReason?: string
  remark?: string
}

const router = useRouter()
const route = useRoute()
const tabStore = useTabStore()
const isEdit = !!route.query.id
const fromOrder = Number(route.query.fromOrder || 0)
const formRef = ref<FormInstance>()
const submitLoading = ref(false)
const qualityOptions = ref<QualityOption[]>([])
const productOptions = ref<any[]>([])
const items = ref<ReturnItem[]>([])
/**
 * 逐产品付费（2026-09-21 用户口径）：采购退货也要有"是否付费"，方向 = **我方付给供货商**，
 * 且**精确到产品** —— 金额挂在明细行（每行一个产品，见明细表「付费」列），单据级只作整单说明。
 * <p>是否付费（chargeFlag）与付费合计（chargeAmount）都由明细推导、后端按 Σ明细 回写 ⇒ 前端不放开关键开关。</p>
 */
const chargeReason = ref('')
const chargeTotal = computed(() =>
  items.value.reduce((s, it: any) => s + (Number(it.chargeAmount) || 0), 0))
/** 付费类型（对应后端 purchase/common/PurchaseChargeType；方向：我们向供货商付费） */
const chargeTypeOptions = computed(() =>
  Object.values(PurchaseChargeType).map((v) => ({ value: v, label: PurchaseChargeTypeLabel[v] || v }))
)
const fetchSuppliers = (kw: string) => request.get('/supplier/page', { params: { pageSize: 500, name: kw, supplierType: 'product' } })
// 2026-09-20（F7-149）：只滤 category 仍会列出辅料仓 ⇒ 补 warehouseType=FINISHED（自有成品仓）
const fetchWarehouses = (kw: string) => request.get('/warehouse/page',
  { params: { pageSize: 500, warehouseName: kw, warehouseCategory: WarehouseCategory.INVENTORY, warehouseType: WarehouseType.FINISHED } })

const form = reactive({
  supplierId: undefined as number | undefined,
  warehouseId: undefined as number | undefined,
  purchaseOrderId: undefined as number | undefined,
  purchaseOrderCode: '' as string,
  returnDate: localDate() as string,
  remark: '' as string,
})
/**
 * 未保存拦截（2026-09-23 统一模板）
 * ⚠️ 必须写在 form / items 等状态**之后**（watch 注册时立即求值，放前面会 TDZ 静默失效）。
 */
const { takeBaseline, markClean } = useUnsavedGuard(() => ({ form, items: items.value }))

/** 从采购单带入明细（含可退数量），退货仓库默认取采购单入库仓库 */
async function loadFromPurchaseOrder(orderId?: number) {
  const oid = orderId || form.purchaseOrderId
  if (!oid) { ElMessage.warning('未关联采购单'); return }
  try {
    // 期 3（2026-09-19 读隔离）：来源采购单改走退货页自身前缀（原读 /inventory/purchase/{id} 需 purchase:order）
    const order: any = await getPurchaseReturnSourceOrder(oid)
    if (order) {
      form.supplierId = order.supplierId
      form.warehouseId = order.warehouseId
      form.purchaseOrderId = order.id
      form.purchaseOrderCode = order.code
    }
    const rows = await getPurchaseReturnPurchaseOrderItems(oid)
    items.value = (rows || []).map((r: any) => ({
      productId: r.productId,
      qualityType: r.qualityType || 'A',
      purchaseOrderItemId: r.purchaseOrderItemId,
      canReturn: Number(r.canReturn),
      quantity: 0,
      unitPrice: Number(r.unitPrice || 0),
      remark: '',
    }))
    if (!items.value.length) ElMessage.info('该采购单暂无明细')
  } catch (e: any) { ElMessage.error(e?.message || '加载采购单失败') }
}

const rules: FormRules = {
  supplierId: [{ required: true, message: '请选择供货商', trigger: 'change' }],
  warehouseId: [{ required: true, message: '请选择退货仓库', trigger: 'change' }],
}

function addItem() {
  items.value.push({ productId: undefined, qualityType: 'A', quantity: 1, unitPrice: 0, remark: '' })
}

async function loadProducts(query?: string) {
  try {
    const params: any = { pageSize: 100 }
    if (query) params.keyword = query
    const res = await request.get<any, any>('/product/page', { params })
    productOptions.value = res?.records || []
  } catch { productOptions.value = [] }
}

async function onProductChange(val: number, row: ReturnItem) {
  const m = productOptions.value.find((x: any) => x.id === val)
  if (m) {
    row.productId = m.id
    row.sku = m.sku || ''
  }
  // 查询该产品库存
  row._stock = undefined
  if (val) {
    try {
      const p: any = {}
      if (form.warehouseId) p.warehouseId = form.warehouseId
      p.productId = val
      const res = await request.get<any, any>('/warehouse/stock/page', { params: p })
      const arr = res?.records || []
      let total = 0
      if (arr.length > 0) {
        total = arr.reduce((s: number, x: any) => s + (Number(x.quantity) || 0), 0)
      }
      row._stock = total
    } catch { row._stock = undefined }
  }
}

function calcAmount() { /* 金额由模板计算 */ }

async function loadReturnData() {
  const id = Number(route.query.id)
  if (!id) return
  try {
    const order: any = await getPurchaseReturn(id)
    // 仅草稿可编辑：已审核/已作废的单据禁止进入编辑
    if (order && order.status !== 'DRAFT') {
      ElMessage.warning('仅草稿状态的退货单可编辑')
      tabStore.removeTab(route.fullPath)
      router.push(`/inventory/purchase-return/detail/${id}`)
      return
    }
    if (order) {
      form.supplierId = order.supplierId
      form.warehouseId = order.warehouseId
      form.purchaseOrderId = order.purchaseOrderId
      form.purchaseOrderCode = order.purchaseOrderCode || ''
      form.returnDate = order.returnDate
      form.remark = order.remark || ''
      // 逐产品付费（2026-09-21）：整单付费说明回填（金额/类型逐行在明细里）
      chargeReason.value = order.chargeReason || ''
    }
    const its: any = await getPurchaseReturnItems(id) || []
    items.value = (Array.isArray(its) ? its : (its?.records || [])).map((it: any) => ({
      productId: it.productId,
      qualityType: it.qualityType,
      purchaseOrderItemId: it.purchaseOrderItemId,
      quantity: it.quantity,
      unitPrice: it.unitPrice,
      amount: it.amount,
      // 逐产品付费（2026-09-21）：明细接口已回传逐行付费字段
      chargeAmount: Number(it.chargeAmount || 0),
      chargeType: it.chargeType || '',
      remark: it.remark,
    }))
    // 查询每个明细产品的现有库存
    for (const item of items.value) {
      if (item.productId) {
        try {
          const p: any = { productId: item.productId }
          if (form.warehouseId) p.warehouseId = form.warehouseId
          const r = await request.get<any, any>('/warehouse/stock/page', { params: p })
          const arr = r?.records || []
          item._stock = arr.reduce((s: number, x: any) => s + (Number(x.quantity) || 0), 0)
        } catch { item._stock = undefined }
      }
    }
  } catch { /* */ }
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    if (items.value.length === 0) { ElMessage.warning('请至少添加一条明细'); return }
    if (items.value.some(it => !it.productId)) { ElMessage.warning('请选择产品'); return }
    if (items.value.some(it => !it.quantity || Number(it.quantity) <= 0)) { ElMessage.warning('产品数量必须大于0'); return }
    // 关联采购单时：数量不能超过可退数量
    if (form.purchaseOrderId) {
      for (const it of items.value) {
        if (it.purchaseOrderItemId != null && it.canReturn !== undefined && (Number(it.quantity) || 0) > Number(it.canReturn)) {
          ElMessage.warning(`产品（${it.productId}）退货数量不能超过可退数量 ${it.canReturn}`)
          return
        }
      }
    }
    // 逐产品付费校验（2026-09-21）：填了金额的行必须选类型（后端保存/审核两处会再逐行校验一次）
    for (const it of items.value) {
      if (Number(it.chargeAmount) > 0 && !it.chargeType) {
        ElMessage.warning(`产品「${it.productId}」已填付费金额，请选择付费类型`)
        return
      }
    }
    const total = items.value.reduce((s, it) => s + (Number(it.quantity) || 0) * (Number(it.unitPrice) || 0), 0)
    submitLoading.value = true
    try {
      const body = {
        supplierId: form.supplierId,
        warehouseId: form.warehouseId,
        purchaseOrderId: form.purchaseOrderId,
        purchaseOrderCode: form.purchaseOrderCode,
        returnDate: form.returnDate,
        remark: form.remark,
        totalAmount: total,
        // 是否付费（方向：我方付给供货商）⇒ 审核生成一条正向应付；
        // 金额由后端按 Σ明细 回写 ⇒ 这里只外带"是否付费"与整单说明（chargeAmount 传 0 不参与计算）
        chargeFlag: chargeTotal.value > 0 ? 1 : 0,
        chargeType: '',
        chargeAmount: 0,
        chargeReason: chargeReason.value || '',
        items: items.value.map(it => ({
          productId: it.productId,
          qualityType: it.qualityType,
          purchaseOrderItemId: it.purchaseOrderItemId,
          quantity: it.quantity,
          unitPrice: it.unitPrice,
          amount: (Number(it.quantity) || 0) * (Number(it.unitPrice) || 0),
          // 逐产品付费：金额 > 0 才付费；类型必选（行内已校验）
          chargeAmount: Number(it.chargeAmount) || 0,
          chargeType: Number(it.chargeAmount) > 0 ? (it.chargeType || '') : '',
          chargeReason: chargeReason.value || '',
          remark: it.remark,
        }))
      }
      if (isEdit) {
        await updatePurchaseReturn(Number(route.query.id), body)
      } else {
        await createPurchaseReturn(body)
      }
      ElMessage.success(isEdit ? '更新成功' : '新增成功'); sessionStorage.setItem(PURCHASE_RETURN_DIRTY_KEY, '1')
      // 保存成功 ⇒ 先清脏标记（否则离开会被未保存确认拦住），再关掉本页签回列表
      markClean()
      tabStore.closeTabAndBack(route.path)
      router.push('/inventory/purchase-return')
    } catch (e: any) { ElMessage.error(e?.message || (isEdit ? '更新失败' : '新增失败')) }
    finally { submitLoading.value = false }
  })
}

function handleCancel() {
  // 取消返回：先清脏标记（否则离开会被未保存确认拦住），再关掉本页签
  markClean()
  tabStore.closeTabAndBack(route.path)
  router.push('/inventory/purchase-return')
}

async function loadQualityTypes() { try { qualityOptions.value = await getQualityTypes() } catch { qualityOptions.value = [] } }

// 顶栏"刷新数据"：重新加载品质下拉
async function handleRefreshData() { await loadQualityTypes() }
onMounted(async () => {
  loadProducts()
  loadQualityTypes()
  if (fromOrder) {
    tabStore.updateTabTitle(route.fullPath, '从采购单开退货单')
    applyPageTitle('从采购单开退货单')
    await loadFromPurchaseOrder(fromOrder)
  } else if (isEdit) {
    tabStore.updateTabTitle(route.fullPath, '编辑采购退货单')
    applyPageTitle('编辑采购退货单')
    await loadReturnData()
  }
  window.addEventListener('refresh:dropdown-data', handleRefreshData)
  // 初始化/回填完成 ⇒ 建立"未保存"基线（必须在加载之后，否则会把回填误判成用户修改）
  takeBaseline()
})
onUnmounted(() => window.removeEventListener('refresh:dropdown-data', handleRefreshData))
</script>

<style scoped>

</style>
