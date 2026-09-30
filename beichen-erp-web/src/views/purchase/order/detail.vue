<script setup lang="ts">
defineOptions({ name: 'PurchaseDetail' })
import { ref, reactive, computed, onActivated } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import request from '@/utils/request'
import { getQualityTypes, productLabel, type QualityOption } from '@/api/product'
import { ADD_MARKER } from '@/composables/useSelectWithAdd'
import RemoteSelect from '@/components/RemoteSelect.vue'
import { PURCHASE_ORDER_DIRTY_KEY, WarehouseCategory, WarehouseType } from '@/api/enums'
import {
  getPurchaseOrderItems, updatePurchaseOrder, type PurchaseOrder, type PurchaseOrderItem, PurchaseStatus, PurchaseStatusLabel,
  // 2026-09-24（用户口径 B：采购单详情对齐销售单详情）：审核/反审核/作废 三个动作
  auditPurchaseOrder, unAuditPurchaseOrder, cancelPurchaseOrder,
} from '@/api/purchase'

import { DocStatusLabel } from '@/api/enums'
import PageShell from '@/components/PageShell.vue'
import { useUnsavedGuard } from '@/composables/usePageBack'
import { useTabStore } from '@/stores/tabs'
import { invalidate } from '@/utils/dataFreshness'

const route = useRoute(); const router = useRouter()
const tabStore = useTabStore()
const orderId = Number(route.params.id)
/** 只读快照（已审核/已作废的展示来源，也用于取 code/status/createByName 等不可编辑字段） */
const head = ref<PurchaseOrder>({})
/**
 * 可编辑副本（2026-09-24 用户口径 B）：草稿态渲染表单、保存时提交本对象。
 * 用 **白名单** 初始化而不是直接 Object.assign(head)：提交体里不该带上 code/status/审核人等
 * 后端不接受（或会被 updateById 覆盖）的字段；漏写字段的代价是草稿态显示为 —（见 ui-e2e-p16 的同类用例）。
 */
const form = reactive<PurchaseOrder>({
  supplierId: undefined, warehouseId: undefined, orderDate: '', taxIncluded: 0, taxRate: 0, remark: ''
})
const items = ref<PurchaseOrderItem[]>([])
/**
 * 未保存拦截（2026-09-23 统一模板）
 * ⚠️ 必须写在 form / items 等状态**之后**（watch 注册时立即求值，放前面会 TDZ 静默失效）。
 */
const { takeBaseline } = useUnsavedGuard(() => ({ form, items: items.value }))
const returns = ref<any[]>([])
// 2026-09-24（用户口径）：换货情况（与销售单详情一致，随详情接口一并返回）
const exchanges = ref<any[]>([])
const afterSaleTab = ref('return')
const loading = ref(false)
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const supplierName = ref('')
const warehouseName = ref('')
const qualityOptions = ref<QualityOption[]>([])

const rules: FormRules = {
  supplierId: [{ required: true, message: '请选择供货商', trigger: 'change' }],
  warehouseId: [{ required: true, message: '请选择入库仓库', trigger: 'change' }]
}

// Odoo 风格下拉：展开/搜索时实时查库（与 purchase/order/form.vue 同口径）
const fetchSuppliers = (kw: string) => request.get('/supplier/page', { params: { pageSize: 500, name: kw } })
// 采购入库仓统一为**自有成品仓**（后端 assertFinishedWarehouse 有兜底）：不过滤就可能把成品采进委外仓/辅料仓
const fetchWarehouses = (kw: string) => request.get('/warehouse/page',
  { params: { pageSize: 500, warehouseName: kw, warehouseCategory: WarehouseCategory.INVENTORY, warehouseType: WarehouseType.FINISHED } })
const fetchProducts = (kw: string) => request.get('/product/page', { params: { pageSize: 500, keyword: kw } })

function statusType(s?: string | number) {
  if (s === PurchaseStatus.DRAFT) return 'info'
  if (s === PurchaseStatus.AUDITED) return 'success'
  if (s === PurchaseStatus.CANCELLED) return 'danger'
  return undefined
}
function statusLabel(s?: number) { return s != null ? (PurchaseStatusLabel[s] || '') : '' }
function fmt(v?: number) { return v === undefined || v === null ? '0.00' : Number(v).toFixed(2) }

/** 品质等级中文映射 */
function qualityLabel(qt?: string) {
  const map: Record<string, string> = { A: 'A规', B: 'B规', C: 'C规', DEFECT: '不良' }
  return qt ? (map[qt] || qt) : '—'
}

/**
 * 状态判断（2026-09-24）：后端返回的 order.status 是 **number**，而 PurchaseStatus 常量是**字符串**
 * ⇒ 模板里直接比较会触发 TS2367（类型不重叠）⇒ 统一用 computed + String() 归一化后比较。
 */
const isDraft = computed(() => String(head.value.status ?? '') === String(PurchaseStatus.DRAFT))
const isAudited = computed(() => String(head.value.status ?? '') === String(PurchaseStatus.AUDITED))

// ---- 草稿态可编辑：明细行与金额（与 purchase/order/form.vue 同一套算法）----
function addItem() {
  items.value.push({ productId: undefined, qualityType: 'A', quantity: 0, unitPrice: 0, amount: 0, remark: '' })
}
function removeItem(index: number) { items.value.splice(index, 1) }
/**
 * 选中产品后即时回填展示字段（SKU / 名称）。
 * 走 RemoteSelect 的 `pick` 事件拿**选中行本体**，而不是拿 id 去本地数组 find
 * —— 下拉的选项存在组件内部 state，父组件没有那份数组（form.vue 里那段 find 找不到就会静默不回填）。
 * 这两个字段是 `@TableField(exist=false)` 的展示字段，不参与提交，仅用于保存前的即时显示。
 */
function onProductPick(p: any, row: PurchaseOrderItem) {
  if (!p) return
  row.sku = p.sku || ''
  row.productName = p.name ?? p.productName ?? ''
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

async function loadData() {
  loading.value = true
  try {
    // getQualityTypes() 是同步返回的品质选项常量（与列表页/表单页同一口径）
    qualityOptions.value = getQualityTypes() || []
    const [res, itemRes] = await Promise.all([
      request.get<any, any>(`/inventory/purchase/${orderId}`),
      getPurchaseOrderItems(orderId)
    ])
    head.value = res || {}
    // 草稿态可编辑副本：白名单初始化（createByName/auditorName 也要带上，草稿态表单同样显示这两项）
    Object.assign(form, {
      id: head.value.id,
      supplierId: head.value.supplierId,
      warehouseId: head.value.warehouseId,
      orderDate: head.value.orderDate || '',
      taxIncluded: head.value.taxIncluded ?? 0,
      taxRate: head.value.taxRate ?? 0,
      remark: head.value.remark ?? '',
      createByName: head.value.createByName,
      auditorName: head.value.auditorName
    })
    // 深拷一层：编辑草稿时改动的是 items 自己，不污染 head（避免取消/刷新前 head 显示被编辑过的值）
    items.value = (itemRes || []).map((it: any) => ({ ...it }))

    // 2026-09-20（F7-177）：详情页只需**一个**名称 ⇒ 按 id 单取（原先是 pageSize=500 全量拉回再前端 find，
    // 等于为了显示两个名字把整张主数据表拉一遍）。列表页做"多行名称映射"时才应全量拉（见 inventory/stock-log.vue）。
    if (head.value.supplierId) {
      const s: any = await request.get(`/supplier/${head.value.supplierId}`).catch(() => null)
      supplierName.value = s?.name || ''
    }
    if (head.value.warehouseId) {
      const w: any = await request.get(`/warehouse/${head.value.warehouseId}`).catch(() => null)
      warehouseName.value = w?.warehouseName || ''
    }
    // 该采购单的退货情况（期 2·2026-09-19 读隔离：随详情接口一并返回，
    // 原先跨页读 /inventory/purchase-return/by-order，需 purchase:return ⇒ 只有采购单权限的用户会 403）
    returns.value = (res as any)?.returns || []
    exchanges.value = (res as any)?.exchanges || []
  } finally { loading.value = false }
  // 数据加载完成 ⇒ 重建"未保存"基线（保存后再加载一次即等于"已干净"）
  takeBaseline()
}

/**
 * 保存（2026-09-24 用户口径 B：草稿态可编辑）。后端 PurchaseOrderServiceImpl.update
 * 自带守卫「只有草稿状态可编辑」并按明细重算 金额/总额/税额 ⇒ 前端校验只做"别提交明显无效的数据"。
 * 权限：PUT /api/inventory/purchase/** 由**页级**码 purchase:order 覆盖（ApiPermGuard），故本按钮无需按钮级码。
 */
async function submit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    if (items.value.length === 0) { ElMessage.warning('请至少添加一条明细'); return }
    for (let i = 0; i < items.value.length; i++) {
      const it = items.value[i]
      if (!it.productId) { ElMessage.warning(`第 ${i + 1} 行请选择产品`); return }
      if (Number(it.quantity) <= 0) { ElMessage.warning(`第 ${i + 1} 行数量必须大于 0`); return }
      if (Number(it.unitPrice) <= 0) { ElMessage.warning(`第 ${i + 1} 行单价必须大于 0`); return }
    }
    submitLoading.value = true
    try {
      await updatePurchaseOrder(orderId, { order: { ...form }, items: items.value })
      ElMessage.success('已保存')
      // 置脏标志：返回列表时 onActivated 按需刷新（列表页已消费 PURCHASE_ORDER_DIRTY_KEY）
      invalidate('purchaseOrder')
      await loadData()
    } catch { /* 拦截器已提示 */ } finally { submitLoading.value = false }
  })
}

function goSupplier(id?: number) { if (id) router.push(`/supplier/detail/${id}`) }
function goWarehouse(id?: number) { if (id) router.push(`/inventory/warehouse/detail/${id}`) }
function addReturn() { router.push({ path: '/inventory/purchase-return/add', query: { fromOrder: orderId } }) }
function goReturnDetail(id: number) { router.push(`/inventory/purchase-return/detail/${id}`) }
function goExchangeDetail(id: number) { router.push(`/inventory/purchase-exchange/detail/${id}`) }
/**
 * 2026-09-24（用户口径 B）：采购单详情对齐销售单详情 —— 退货 / 换货 / 反审核（+ 草稿态 审核 / 作废）。
 * 后端 PurchaseOrderServiceImpl 的 audit / unAudit / cancel **已实现全套账务**（入库 PURCHASE_IN + 应付账款，
 * 反审核冲回）⇒ 前端只需暴露入口，无需改后端。
 */
function goExchange() { router.push({ path: '/inventory/purchase-exchange/add', query: { fromOrder: orderId } }) }
async function handleAudit() {
  try {
    await ElMessageBox.confirm(`确认审核采购单「${head.value.code}」？审核后入库并生成应付账款。`, '确认审核',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' })
    await auditPurchaseOrder(orderId); ElMessage.success('已审核'); await loadData()
  } catch { /* 取消或失败 */ }
}
async function handleUnAudit() {
  try {
    await ElMessageBox.confirm(`确认反审核采购单「${head.value.code}」？将冲回入库与应付账款。`, '确认反审核',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' })
    await unAuditPurchaseOrder(orderId); ElMessage.success('已反审核'); await loadData()
  } catch { /* 取消或失败 */ }
}
async function handleCancel() {
  try {
    await ElMessageBox.confirm(`确认作废采购单「${head.value.code}」？`, '确认作废',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' })
    await cancelPurchaseOrder(orderId); ElMessage.success('已作废'); await loadData()
  } catch { /* 取消或失败 */ }
}

// 本页的供货商名/仓库名是在 loadData 内按需查询填充的（字典与业务耦合），故整体放到 onActivated：
// keep-alive 缓存下再次进入会复用组件、onMounted 不再触发，只靠 onMounted 会停留在上次缓存的状态
onActivated(async () => { await loadData() })
</script>

<template>
  <!-- 统一骨架（2026-09-23 全站最终口径）：页头左端=返回 → 标题 → 右端=操作 -->
  <PageShell :title="`采购单详情${head.code ? ' — ' + head.code : ''}`" :loading="loading" back-fallback="/inventory/purchase">
    <!-- 状态标签放页头副标题位（与销售单详情一致）：草稿态是表单、已审核是信息块，两态都能看到状态 -->
    <template #sub>
      <el-tag :type="statusType(head.status)" effect="plain">{{ statusLabel(head.status) }}</el-tag>
    </template>
    <template #actions>
      <!-- 2026-09-24（用户口径 B：对齐销售单详情）：草稿 → 保存/审核/作废；已审核 → 退货/换货/反审核 -->
      <el-button v-if="isDraft" type="primary" :loading="submitLoading" @click="submit">保存</el-button>
      <el-button v-if="isAudited" type="warning" @click="addReturn">退货</el-button>
      <el-button v-if="isAudited" type="warning" plain @click="goExchange">换货</el-button>
      <el-button v-if="isDraft" v-perm="'purchase:order:audit'" type="success" @click="handleAudit">审核</el-button>
      <el-button v-if="isAudited" v-perm="'purchase:order:unaudit'" type="warning" @click="handleUnAudit">反审核</el-button>
      <el-button v-if="isDraft" v-perm="'purchase:order:cancel'" type="danger" @click="handleCancel">作废</el-button>
    </template>

    <el-card shadow="never">
      <!-- 草稿：可编辑（与销售单详情同款分支；字段与 purchase/order/form.vue 保持一致） -->
      <template v-if="isDraft">
        <el-form ref="formRef" :model="form" :rules="rules" label-width="var(--app-label-width)">
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="供货商" prop="supplierId">
                <RemoteSelect v-model="form.supplierId" :fetch="fetchSuppliers"
                  :preset="{ id: form.supplierId, name: supplierName }"
                  placeholder="请选择" style="width:100%"
                  @change="(v: any) => { if (v === ADD_MARKER) { form.supplierId = undefined; router.push('/outsource/supplier/manage'); return } }" domain="supplier" >
                  <el-option label="+ 新增" :value="ADD_MARKER" />
                </RemoteSelect>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="入库仓库" prop="warehouseId">
                <RemoteSelect v-model="form.warehouseId" :fetch="fetchWarehouses" label-key="warehouseName"
                  :preset="{ id: form.warehouseId, warehouseName: warehouseName }"
                  placeholder="请选择" style="width:100%"
                  @change="(v: any) => { if (v === ADD_MARKER) { form.warehouseId = undefined; router.push('/inventory/warehouse'); return } }" domain="warehouse" >
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
            <!-- 制单人 / 审核人（2026-09-23 用户口径：单据详情显示这两项，**草稿态也要显示**） -->
            <el-col :span="6">
              <el-form-item label="制单人"><el-input :model-value="form.createByName || '—'" readonly /></el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="审核人"><el-input :model-value="form.auditorName || '—'" readonly /></el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" placeholder="请输入备注" /></el-form-item>
            </el-col>
          </el-row>

          <el-divider content-position="left">明细</el-divider>
          <div style="margin-bottom:8px"><el-button type="primary" :icon="'Plus'" @click="addItem">添加明细</el-button></div>
          <el-table :data="items" border>
            <el-table-column label="SKU" width="130">
              <template #default="{ row }">
                <span v-if="row.sku">{{ row.sku }}</span>
                <span v-else style="color:var(--app-text-secondary)">选择后显示</span>
              </template>
            </el-table-column>
            <el-table-column label="产品" min-width="180">
              <template #default="{ row }">
                <RemoteSelect v-model="row.productId" :fetch="fetchProducts" :label-key="productLabel"
                  :preset="{ id: row.productId, name: row.productName, sku: row.sku }"
                  placeholder="选择产品（可输SKU）" style="width:100%"
                  @pick="(opts: any[]) => onProductPick(opts && opts[0], row)"
                  @change="(v: any) => { if (v === ADD_MARKER) { row.productId = undefined; router.push('/product/add'); return } }" domain="product" >
                  <el-option label="+ 新增" :value="ADD_MARKER" />
                </RemoteSelect>
              </template>
            </el-table-column>
            <el-table-column label="品质" width="90">
              <template #default="{ row }">
                <el-select v-model="row.qualityType" size="small" style="width:100%">
                  <el-option v-for="q in qualityOptions" :key="q.value" :label="q.label" :value="q.value" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="数量" width="120">
              <template #default="{ row }"><el-input-number v-model="row.quantity" :min="0" :precision="0" controls-position="right" style="width:100%" /></template>
            </el-table-column>
            <el-table-column label="单价" width="120">
              <template #default="{ row }"><el-input-number v-model="row.unitPrice" :min="0" :precision="2" controls-position="right" style="width:100%" /></template>
            </el-table-column>
            <el-table-column label="金额" width="110" align="right">
              <template #default="{ row }">{{ itemAmount(row) }}</template>
            </el-table-column>
            <el-table-column label="备注" min-width="140">
              <template #default="{ row }"><el-input v-model="row.remark" size="small" placeholder="备注" /></template>
            </el-table-column>
            <el-table-column label="操作" width="70" align="center">
              <template #default="{ $index }"><el-button type="danger" link @click="removeItem($index)">删除</el-button></template>
            </el-table-column>
          </el-table>
          <div class="sum-bar">
            <span>应付总额（含税）：<b>{{ goodsTotal.toFixed(2) }}</b></span>
            <template v-if="form.taxIncluded === 1">
              <span>税额（{{ form.taxRate }}%）： <b class="tax-num">{{ taxAmount.toFixed(2) }}</b></span>
              <span>不含税金额： <b>{{ noTaxAmount.toFixed(2) }}</b></span>
            </template>
          </div>
        </el-form>
      </template>

      <!-- 非草稿：只读信息块 -->
      <template v-else>
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="单号">{{ head.code }}</el-descriptions-item>
          <el-descriptions-item label="供货商">
            <el-button v-if="head.supplierId" type="primary" link @click="goSupplier(head.supplierId)">{{ supplierName }}</el-button>
            <span v-else>—</span>
          </el-descriptions-item>
          <el-descriptions-item label="入库仓库">
            <el-button v-if="head.warehouseId" type="primary" link @click="goWarehouse(head.warehouseId)">{{ warehouseName }}</el-button>
            <span v-else>—</span>
          </el-descriptions-item>
          <el-descriptions-item label="订单日期">{{ head.orderDate }}</el-descriptions-item>
          <el-descriptions-item label="税额">{{ fmt(head.taxAmount) }}</el-descriptions-item>
          <el-descriptions-item label="总金额">{{ fmt(head.totalAmount) }}</el-descriptions-item>
          <!-- 制单人 / 审核人（2026-09-23 用户口径：单据详情显示这两项；历史单据无记录显示 —） -->
          <el-descriptions-item label="制单人">{{ head.createByName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="审核人">{{ head.auditorName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="3">{{ head.remark || '—' }}</el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">明细</el-divider>
        <el-table :data="items" border stripe size="small">
          <el-table-column prop="sku" label="SKU" width="130" />
          <el-table-column prop="productName" label="成品名称" min-width="160" show-overflow-tooltip />
          <el-table-column label="品质" width="80" align="center">
            <template #default="{ row }">{{ qualityLabel(row.qualityType) }}</template>
          </el-table-column>
          <el-table-column prop="quantity" label="数量" width="90" align="right" />
          <el-table-column prop="unitPrice" label="单价" width="90" align="right" />
          <el-table-column prop="amount" label="金额" width="100" align="right" />
          <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
        </el-table>
      </template>
    </el-card>

    <!-- 售后记录：仅非草稿展示（草稿不可能有退货/换货单，与销售单详情同口径） -->
    <el-card v-if="!isDraft" shadow="never">
      <el-tabs v-model="afterSaleTab">
        <el-tab-pane :label="`采购退货单 (${returns.length})`" name="return">
          <el-table :data="returns" border stripe size="small" empty-text="暂无退货记录">
            <el-table-column prop="code" label="退货单号" width="170" />
            <el-table-column prop="returnDate" label="退货日期" width="110" />
            <el-table-column label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="退货金额" width="120" align="right">
              <template #default="{ row }">{{ fmt(row.totalAmount) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="80" align="center">
              <template #default="{ row }">
                <el-button type="primary" link @click="goReturnDetail(row.id)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane :label="`采购换货单 (${exchanges.length})`" name="exchange">
          <el-table :data="exchanges" border stripe size="small" empty-text="暂无换货记录">
            <el-table-column prop="code" label="换货单号" width="170" />
            <el-table-column prop="exchangeDate" label="换货日期" width="110" />
            <el-table-column label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === 'AUDITED' ? 'success' : (row.status === 'CANCELLED' ? 'info' : 'warning')">
                  {{ DocStatusLabel[String(row.status)] || row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="收费" width="120" align="right">
              <template #default="{ row }">{{ fmt(row.chargeAmount) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="80" align="center">
              <template #default="{ row }">
                <el-button type="primary" link @click="goExchangeDetail(row.id)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </PageShell>
</template>

<style scoped>
/* 页头/底部返回条已统一到全局骨架（PageShell + styles/page.css） */
.sum-bar { margin-top: 12px; display: flex; justify-content: flex-end; gap: 24px; font-size: var(--app-font-base); color: var(--app-text-secondary); }
.sum-bar b { color: var(--app-text-primary); font-size: var(--app-font-num-sm); }
.tax-num { color: var(--app-color-danger); }
</style>
