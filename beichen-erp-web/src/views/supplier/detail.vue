<script setup lang="ts">
defineOptions({ name: 'SupplierDetail' })
import { reactive, ref, computed, onMounted, onActivated } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { applyPageTitle } from '@/utils/pageTitle'
import { productLabel } from '@/api/product'
import { getProjectBom } from '@/api/system'
import {
  OutsourceOrderStatus, OutsourceOrderStatusLabel, OutsourceOrderStatusTag,
  MaterialOrderStatus, MaterialOrderStatusLabel, MaterialOrderStatusTag,
  SUPPLIER_DIRTY_KEY,
  OrderTypeLabel
} from '@/api/enums'
const route = useRoute(); const router = useRouter()
const id = Number(route.params.id)
const loading = ref(true)
const saving = ref(false)
const activeTab = ref('info')

import { TYPE_OPTIONS, TYPE_MAP } from '@/constants/supplier'
import PageShell from '@/components/PageShell.vue'
import { useUnsavedGuard } from '@/composables/usePageBack'
// 供货商(仅product) / 供应商(方案商·加工厂·辅料商)：先按入口路径判定，加载后再按实际类型校正
// 供货商只供应产品，无「供应物料」页签
const isVendor = ref(route.query.mode === 'vendor' || route.path.startsWith('/outsource/supplier'))
/** 双模式共用模板 ⇒ 带主体前缀的标签必须动态（供货商详情不能显示成「供应商名称」） */
const entityLabel = computed(() => (isVendor.value ? '供货商' : '供应商'))
const TYPE_OPTIONS_CUSTOM = computed(() => isVendor.value
  ? [{ name: 'product', label: TYPE_MAP.product }]
  : TYPE_OPTIONS.filter(t => t.name !== 'product'))

/** 同步页面标题（面包屑与浏览器标签均取自 route.meta.title） */
function applyVendorTitle() {
  const title = isVendor.value ? '供货商详情' : '供应商详情'
  route.meta.title = title
  // 2026-09-27：后缀统一走 @/utils/pageTitle（原先 5 处各拼一次）
  applyPageTitle(title)
}

const form = reactive({
  id: undefined as any,
  code: '', name: '', supplierType: '', supplySku: '', status: 1,
  contact: '', phone: '', address: '', remark: '',
  checkedTypes: [] as string[],
  creditPeriodMonths: undefined as any, creditPeriod: undefined as any,
})
const products = ref<any[]>([])
const prodOptions = ref<any[]>([])
async function searchProducts(query?: string) {
  try {
    const params: any = { pageSize: 50 }
    if (query) params.keyword = query
    const res = await request.get<any, any>('/product/page', { params })
    prodOptions.value = res?.records || []
  } catch { prodOptions.value = [] }
}

// 供应物料（居间表 supplier_material）
const materials = ref<any[]>([])
const matOptions = ref<any[]>([])
const matLoading = ref(false)
async function searchMaterials(query?: string) {
  try {
    const params: any = { pageSize: 50 }
    if (query) params.materialName = query
    const res = await request.get<any, any>('/outsource/material/page', { params })
    matOptions.value = res?.records || []
  } catch { matOptions.value = [] }
}
async function loadMaterials() {
  matLoading.value = true
  try {
    const res = await request.get<any, any>(`/supplier/${id}/materials`)
    materials.value = res || []
  } catch { materials.value = [] }
  finally { matLoading.value = false }
}
function addMaterial() { materials.value.push({ materialId: undefined, unitPrice: 0, remark: '' }) }
function removeMaterial(i: number) { materials.value.splice(i, 1) }
async function saveMaterials() {
  try {
    const body = materials.value.map((m: any) => ({ materialId: m.materialId, unitPrice: m.unitPrice, remark: m.remark }))
    await request.put(`/supplier/${id}/materials`, body)
    ElMessage.success('供应物料已保存'); sessionStorage.setItem(SUPPLIER_DIRTY_KEY, '1')
    takeBaseline()   // 保存成功 ⇒ 重建基线（保存不重跑 loadData），避免离开时误报"未保存"
    loadMaterials()
  } catch (e: any) { ElMessage.error('保存失败: ' + (e?.message || '未知错误')) }
}
const typeName = ref('')
const hasFactory = ref(false)

function formatTypes(types: string[]): string {
  if (!types || types.length === 0) return ''
  const list = isVendor.value ? types.filter(t => t === 'product') : types.filter(t => t !== 'product')
  return list.map(t => TYPE_MAP[t] || t).join(' + ')
}

// 仓库/订单/缺料
const warehouses = ref<any[]>([])
const orders = ref<any[]>([])
const materialOrders = ref<any[]>([])
const activeStatusTab = ref('进行中')
// 进行中状态（存 code，比较也用 code）：加工单待审核/生产中 + 物料订单待确认/收货中
const ACTIVE_STATUSES = [
  OutsourceOrderStatus.PENDING, OutsourceOrderStatus.PRODUCING,
  MaterialOrderStatus.PENDING, MaterialOrderStatus.RECEIVING
]
const FINISHED_STATUSES = [OutsourceOrderStatus.FINISHED, MaterialOrderStatus.FINISHED]
const CANCELLED_STATUSES = [OutsourceOrderStatus.CANCELLED, MaterialOrderStatus.CANCELLED]
const filteredOrders = computed(() => {
  const all = [...orders.value, ...materialOrders.value]
  if (activeStatusTab.value === '已完成') return all.filter(o => FINISHED_STATUSES.includes(o.status))
  if (activeStatusTab.value === '已取消') return all.filter(o => CANCELLED_STATUSES.includes(o.status))
  return all.filter(o => ACTIVE_STATUSES.includes(o.status))
})
const whLoading = ref(false)
const orderLoading = ref(false)
const materialLoading = ref(false)
const materialSummary = ref<any[]>([])

/**
 * 未保存拦截（2026-09-23 统一模板）：本页各 Tab 可就地改基础信息/产品/物料并保存 ⇒ 属"能改数据"，接守卫。
 * ⚠️ 必须写在 form 等状态**之后**（watch 注册时立即求值，放前面会 TDZ 静默失效）。
 */
const { takeBaseline } = useUnsavedGuard(() => ({ form }))

async function loadData() {
  loading.value = true
  try {
    const res = await request.get<any,any>(`/supplier/${id}`)
    if (res) {
      Object.assign(form, res)
      // 类型编码列表
      form.checkedTypes = res.typeCodes || []
      // 实际类型校正：只勾选了"成品商"即供货商（从业务单据跳进来时入口路径不可靠）
      const codes: string[] = res.typeCodes || []
      if (codes.length > 0) isVendor.value = codes.every((t: string) => t === 'product')
      typeName.value = formatTypes(form.checkedTypes || [])
      hasFactory.value = form.checkedTypes.includes('factory')
      applyVendorTitle()
    }
    // 供应商只供应物料、供货商只供应产品：各自跳过对方数据的加载（对应页签已隐藏）
    if (isVendor.value) {
      const prods = await request.get<any,any>(`/supplier/${id}/products`)
      products.value = prods || []
    } else {
      products.value = []
    }
    // 2026-09-20（F7-152）：`isVendor` 是 ref，原先漏了 `.value` ⇒ 该条件恒为真 ⇒
    // 供应商详情的「供应物料」永远被清空（else 分支的加载永不执行）。影响比原判更重：功能实际不可用。
    if (isVendor.value) materials.value = []
    else {
      const mats = await request.get<any,any>(`/supplier/${id}/materials`)
      materials.value = mats || []
    }
    await markBomFlags()
  } finally { loading.value = false }
}

async function loadWarehouses() {
  whLoading.value = true
  try {
    const r = await request.get<any,any>('/warehouse/by-factory/' + id)
    warehouses.value = r || []
  } catch { warehouses.value = [] }
  finally { whLoading.value = false }
}

async function loadOrders() {
  orderLoading.value = true
  try {
    const [r1, r2] = await Promise.all([
      request.get<any,any>('/outsource/order/page', { params: { factoryId: id, pageSize: 200 } }),
      request.get<any,any>('/outsource/material-order/page', { params: { supplierId: id, pageSize: 200 } })
    ])
    orders.value = (r1?.records || []).map((o: any) => ({ ...o, _type: '加工单', _route: `/outsource/order/detail/${o.id}` }))
    materialOrders.value = (r2?.records || []).map((o: any) => ({ ...o, _type: OrderTypeLabel[o.orderType] || '物料单', _route: `/outsource/material-order/detail/${o.id}` }))
  } catch { orders.value = []; materialOrders.value = [] }
  finally { orderLoading.value = false }
}

function onTabChange(tab: any) {
  if (tab === 'warehouse' && warehouses.value.length === 0) loadWarehouses()
  if (tab === 'order' && filteredOrders.value.length === 0) loadOrders()
  if (tab === 'material' && materialSummary.value.length === 0) loadMaterialSummary()
  if (tab === 'product' && products.value.length === 0) searchProducts()
  if (tab === 'material-supply' && materials.value.length === 0) loadMaterials()
}

async function loadMaterialSummary() {
  materialLoading.value = true
  try {
    const res = await request.get<any, any>(`/supplier/${id}/material-summary`)
    materialSummary.value = res?.materials || []
  } catch { materialSummary.value = [] }
  finally { materialLoading.value = false }
}

async function handleSave() {
  if (!form.name) { ElMessage.warning(isVendor.value ? '请输入供货商名称' : '请输入供应商名称'); return }
  if (form.checkedTypes.length === 0) { ElMessage.warning('请选择至少一个类型'); return }
  saving.value = true
  try {
    const body: any = { ...form, typeCodes: form.checkedTypes }
    // 2026-09-21：「供货SKU」只属于供货商（类型=成品商）⇒ 非供货商保存时显式置空，
    // 避免「供货商改成供应商」后前缀仍残留在库里（后端同样会忽略该类型的值）
    if (!isVendor.value) body.supplySku = ''
    await request.put('/supplier', body)
    ElMessage.success('已保存'); sessionStorage.setItem(SUPPLIER_DIRTY_KEY, '1')
    takeBaseline()   // 保存成功 ⇒ 重建基线（保存不重跑 loadData），避免离开时误报"未保存"
    loadData()
  } finally { saving.value = false }
}

function addProduct() { products.value.push({ productId: undefined, unitPrice:0, remark:'' }) }
function removeProduct(i:number) { products.value.splice(i,1) }

async function saveProducts() {
  try {
    await request.put(`/supplier/${id}/products`, products.value)
    ElMessage.success('产品列表已保存'); sessionStorage.setItem(SUPPLIER_DIRTY_KEY, '1')
    takeBaseline()   // 保存成功 ⇒ 重建基线（保存不重跑 loadData），避免离开时误报"未保存"
  } catch (e: any) { ElMessage.error('保存失败: ' + (e?.message || '未知错误')) }
}

// 跳采购：成品→成品采购单；物料→委外物料订单（成品采购单不存 materialId，物料采购走物料订单）
function goPurchase(row: any, type: 'product' | 'material') {
  if (type === 'product') {
    router.push({ path: '/inventory/purchase/add', query: { supplierId: id, productId: row.productId } })
  } else {
    router.push({
      path: '/outsource/material-order/add',
      query: { supplierId: id, materialId: row.materialId, materialName: row.materialName }
    })
  }
}

// 跳委外：产品→委外加工单；物料有子料→委外加工单(物料直挂)，无子料→委外物料订单
async function goOutsource(row: any, type: 'product' | 'material') {
  if (type === 'product') {
    router.push({ path: '/outsource/order/add', query: { supplierId: id, productId: row.productId } })
    return
  }
  try {
    const res = await request.post<any, any>('/outsource/material/components-batch-by-ids', [row.materialId])
    const childrenMap: Record<number, any[]> = res?.childrenMap || {}
    const hasComponents = Array.isArray(childrenMap[row.materialId]) && childrenMap[row.materialId].length > 0
    if (hasComponents) {
      router.push({ path: '/outsource/order/add', query: { supplierId: id, materialId: row.materialId } })
    } else {
      router.push({
        path: '/outsource/material-order/add',
        query: { supplierId: id, materialId: row.materialId, materialName: row.materialName }
      })
    }
  } catch {
    router.push({
      path: '/outsource/material-order/add',
      query: { supplierId: id, materialId: row.materialId, materialName: row.materialName }
    })
  }
}

// 根据行是否有 BOM / 子料，决定显示"去委外"还是"去采购"，并执行对应跳转
function goAction(row: any, type: 'product' | 'material') {
  if (type === 'product') {
    if (row.hasBom) goOutsource(row, 'product')
    else goPurchase(row, 'product')
  } else {
    if (row.hasComponents) goOutsource(row, 'material')
    else goPurchase(row, 'material')
  }
}

// 加载后为每行计算标志：成品是否含 BOM（据关联项目的 BOM 表）、物料是否含子料
async function markBomFlags() {
  // 物料：一次批量判定子料
  if (materials.value.length) {
    try {
      const ids = materials.value.map((m: any) => m.materialId)
      const res = await request.post<any, any>('/outsource/material/components-batch-by-ids', ids)
      const childrenMap: Record<number, any[]> = res?.childrenMap || {}
      materials.value.forEach((m: any) => {
        m.hasComponents = Array.isArray(childrenMap[m.materialId]) && childrenMap[m.materialId].length > 0
      })
    } catch { materials.value.forEach((m: any) => (m.hasComponents = false)) }
  }
  // 成品：productId → 反查 projectId → 项目 BOM 是否非空
  if (products.value.length) {
    try {
      const proms = await Promise.all(products.value.map((p: any) => request.get<any, any>(`/product/${p.productId}`).catch(() => null)))
      const pidByProduct: Record<number, number> = {}
      proms.forEach((pr: any, i: number) => {
        if (pr?.projectId) pidByProduct[products.value[i].productId] = pr.projectId
      })
      const projectIds = [...new Set(Object.values(pidByProduct))]
      const bomMap: Record<number, boolean> = {}
      await Promise.all(projectIds.map(async (pid) => {
        try { const bom = await getProjectBom(pid); bomMap[pid] = Array.isArray(bom) && bom.length > 0 } catch { bomMap[pid] = false }
      }))
      products.value.forEach((p: any) => {
        const pid = pidByProduct[p.productId]
        p.hasBom = !!pid && !!bomMap[pid]
      })
    } catch { products.value.forEach((p: any) => (p.hasBom = false)) }
  }
}

// 业务数据放在 onActivated 加载：layout 用 keep-alive 缓存页面，再次进入详情页会复用组件、
// onMounted 不再触发，只靠 onMounted 会停留在上次缓存的状态
onActivated(async () => { await loadData(); takeBaseline() })
</script>

<template>
  <!-- 统一骨架（2026-09-23 全站定稿口径）：页头左端「← 返回」→ 标题(取 meta)；
       本页是多 Tab 页，各 Tab 内的保存按钮（保存/保存产品/保存物料）作用域是当前 Tab ⇒ 保留原位置 -->
  <PageShell :loading="loading" :back-fallback="isVendor ? '/outsource/supplier/manage' : '/supplier/manage'">
    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <el-tab-pane label="基础信息" name="info">
        <el-card shadow="never">
          <template #header><span style="font-weight:600">基础信息</span></template>
          <!-- label-width 110px：标签带模块前缀（供货商名称/供应商编码 5 字）+ 必填星号，80px 会把标签压成两行 -->
          <el-form :model="form" label-width="var(--app-label-width)" size="small">
            <el-row :gutter="12">
              <el-col :span="8"><el-form-item required :label="entityLabel + '名称'"><el-input v-model="form.name" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item :label="entityLabel + '编码'"><el-input :model-value="form.code" disabled /></el-form-item></el-col>
              <!-- 2026-09-21：供货SKU —— **只属于「供货商」（类型=成品商）**：该供货商的产品 SKU 用它打头。
                   供应商不显示该字段（isVendor 在本页既按入口路径判定、也在加载后按实际类型校正） -->
              <el-col v-if="isVendor" :span="8">
                <el-form-item label="供货SKU">
                  <el-input v-model="form.supplySku" maxlength="24" clearable placeholder="如 ABC（留空则产品走默认 SKU-）" />
                  <div style="font-size:var(--app-font-xs);color:var(--app-text-secondary);line-height:1.4">
                    该供货商的产品 SKU 以此打头（ABC → ABC-000001）；改动只影响后续新增的产品
                  </div>
                </el-form-item>
              </el-col>
              <el-col :span="8"><el-form-item label="状态">
                <el-select v-model="form.status" style="width:100%">
                  <el-option label="启用" :value="1" />
                  <el-option label="停用" :value="0" />
                </el-select>
              </el-form-item></el-col>
              <el-col :span="8"><el-form-item label="联系人"><el-input v-model="form.contact" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="联系电话"><el-input v-model="form.phone" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="地址"><el-input v-model="form.address" /></el-form-item></el-col>
              <el-col :span="24">
                <el-form-item label="类型" required>
                  <el-checkbox-group v-model="form.checkedTypes">
                    <el-checkbox v-for="t in TYPE_OPTIONS_CUSTOM" :key="t.name" :label="t.name" :value="t.name">{{ t.label }}</el-checkbox>
                  </el-checkbox-group>
                </el-form-item>
              </el-col>
              <el-col :span="24">
                <el-form-item label="账期">
                  <div style="display:flex;align-items:center;gap:6px">
                    <el-input-number v-model="form.creditPeriodMonths" :min="0" :max="24" placeholder="月" controls-position="right" style="width:90px" /><span>个月</span>
                    <el-input-number v-model="form.creditPeriod" :min="0" :max="31" placeholder="天" controls-position="right" style="width:90px" /><span>天</span>
                    <span style="color:var(--app-text-secondary);font-size:var(--app-font-xs)">（收货/交货后多少天付款，默认当天）</span>
                  </div>
                </el-form-item>
              </el-col>
              <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item></el-col>
              <el-col :span="24"><el-form-item><el-button type="primary" :loading="saving" @click="handleSave">保存</el-button></el-form-item></el-col>
            </el-row>
          </el-form>
        </el-card>
      </el-tab-pane>

      <!-- 供应产品：仅供货商（成品商）；供应商（方案商/加工厂/辅料商）只供应物料 -->
      <el-tab-pane v-if="isVendor" label="供应产品" name="product">
        <el-card shadow="never">
          <template #header>
            <div style="display:flex;justify-content:space-between;align-items:center">
              <span style="font-weight:600">供应产品</span>
              <el-button type="primary" size="small" @click="saveProducts">保存产品</el-button>
            </div>
          </template>
          <el-button type="primary" size="small" text @click="addProduct" style="margin-bottom:8px">+ 添加产品</el-button>
          <el-table :data="products" border size="small">
            <el-table-column label="产品" min-width="160">
              <template #default="{row}">
                <span v-if="row.productName">{{ row.productName }}</span>
                <el-select v-else v-model="row.productId" placeholder="搜索产品（可输SKU）" filterable remote :remote-method="searchProducts" size="small" style="width:100%">
                  <el-option v-for="p in prodOptions" :key="p.id" :label="productLabel(p)" :value="p.id" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="单价" width="100"><template #default="{row}"><el-input v-model="row.unitPrice" size="small" /></template></el-table-column>
            <el-table-column label="备注" width="120"><template #default="{row}"><el-input v-model="row.remark" size="small" /></template></el-table-column>
            <el-table-column label="操作" width="150" align="center">
              <template #default="{row, $index}">
                <el-button type="danger" link size="small" @click="removeProduct($index)">删除</el-button>
                <el-button v-if="row.hasBom" type="success" link size="small" :disabled="!row.productId" @click="goOutsource(row, 'product')">去委外</el-button>
                <el-button v-else type="primary" link size="small" :disabled="!row.productId" @click="goPurchase(row, 'product')">去采购</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- 供应物料：仅供应商（方案商/加工厂/辅料商）；供货商只供应产品 -->
      <el-tab-pane v-if="!isVendor" label="供应物料" name="material-supply">
        <el-card shadow="never" v-loading="matLoading">
          <template #header>
            <div style="display:flex;justify-content:space-between;align-items:center">
              <span style="font-weight:600">供应物料</span>
              <el-button type="primary" size="small" @click="saveMaterials">保存物料</el-button>
            </div>
          </template>
          <el-button type="primary" size="small" text @click="addMaterial" style="margin-bottom:8px">+ 添加物料</el-button>
          <el-table :data="materials" border size="small">
            <el-table-column label="物料" min-width="160">
              <template #default="{row}">
                <span v-if="row.materialName">{{ row.materialName }}</span>
                <el-select v-else v-model="row.materialId" placeholder="搜索物料" filterable remote :remote-method="searchMaterials" size="small" style="width:100%">
                  <el-option v-for="m in matOptions" :key="m.id" :label="m.materialName || ('物料#' + m.id)" :value="m.id" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="规格" width="120"><template #default="{row}">{{ row.spec || '-' }}</template></el-table-column>
            <el-table-column label="物料类型" width="120"><template #default="{row}">{{ row.materialTypeName || '-' }}</template></el-table-column>
            <el-table-column label="单价" width="100"><template #default="{row}"><el-input v-model="row.unitPrice" size="small" /></template></el-table-column>
            <el-table-column label="备注" width="120"><template #default="{row}"><el-input v-model="row.remark" size="small" /></template></el-table-column>
            <el-table-column label="操作" width="150" align="center">
              <template #default="{row, $index}">
                <el-button type="danger" link size="small" :disabled="!!row.id" @click="removeMaterial($index)">删除</el-button>
                <el-button v-if="row.hasComponents" type="success" link size="small" :disabled="!row.materialId" @click="goOutsource(row, 'material')">去委外</el-button>
                <el-button v-else type="primary" link size="small" :disabled="!row.materialId" @click="goPurchase(row, 'material')">去采购</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- 仓库详细（仅委外加工厂） -->
      <el-tab-pane v-if="hasFactory" label="仓库详细" name="warehouse">
        <el-card shadow="never" v-loading="whLoading">
          <el-table :data="warehouses" border stripe size="small">
            <el-table-column prop="warehouseName" label="仓库名称" min-width="200" />
            <el-table-column prop="code" label="仓库编码" width="140" />
            <el-table-column label="状态" width="80">
              <template #default="{row}"><el-tag :type="row.status===1?'success':'danger'" size="small">{{ row.status===1?'启用':'停用' }}</el-tag></template>
            </el-table-column>
            <el-table-column label="操作" width="100" align="center">
              <template #default="{row}">
                <el-button type="primary" link size="small" @click="router.push(`/outsource/warehouse/detail/${row.id}`)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="warehouses.length===0 && !whLoading" style="color:var(--app-text-secondary);text-align:center;padding:40px">暂无仓库</div>
        </el-card>
      </el-tab-pane>

      <!-- 订单详细 -->
      <el-tab-pane label="订单列表" name="order">
        <el-card shadow="never" class="order-table-card">
          <div style="margin-bottom:12px">
            <el-radio-group v-model="activeStatusTab" size="small">
              <el-radio-button value="进行中">进行中</el-radio-button>
              <el-radio-button value="已完成">已完成</el-radio-button>
              <el-radio-button value="已取消">已取消</el-radio-button>
            </el-radio-group>
          </div>
          <el-table v-loading="orderLoading" :data="filteredOrders" border stripe>
            <el-table-column prop="code" label="订单号" min-width="160" show-overflow-tooltip />
            <el-table-column label="类型" width="90" align="center">
              <template #default="{row}"><el-tag :type="row._type==='加工单'?'primary':'warning'" size="small">{{ row._type }}</el-tag></template>
            </el-table-column>
            <el-table-column label="产品/物料" min-width="120" show-overflow-tooltip>
              <template #default="{row}">
                <template v-if="row._type==='加工单'">{{ row.productCount || 0 }}项</template>
                <template v-else>{{ (row.items || []).map((it:any)=>it.materialName).join('、') || '-' }}</template>
              </template>
            </el-table-column>
            <el-table-column label="金额" width="90" align="right">
              <template #default="{row}">{{ row.totalAmount ? Number(row.totalAmount).toFixed(2) : '-' }}</template>
            </el-table-column>
            <el-table-column label="日期" width="100" align="center">
              <template #default="{row}">{{ $fmtDate(row._type==='加工单'?row.planEndDate:row.deliveryDate) }}</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="80" align="center">
              <template #default="{row}">
                <el-tag
                  v-if="row._type==='加工单'"
                  :type="OutsourceOrderStatusTag[row.status] || 'info'"
                  size="small"
                >{{ OutsourceOrderStatusLabel[row.status] || row.status }}</el-tag>
                <el-tag
                  v-else
                  :type="MaterialOrderStatusTag[row.status] || 'info'"
                  size="small"
                >{{ MaterialOrderStatusLabel[row.status] || row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="70" align="center" fixed="right">
              <template #default="{row}">
                <el-button type="primary" link @click="router.push(row._route)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="filteredOrders.length===0 && !orderLoading" style="color:var(--app-text-secondary);text-align:center;padding:40px">暂无订单</div>
        </el-card>
      </el-tab-pane>

      <!-- 物料缺料（仅委外加工厂） -->
      <el-tab-pane v-if="hasFactory" label="物料缺料" name="material">
        <el-card shadow="never" v-loading="materialLoading">
          <el-table :data="materialSummary" border stripe size="small">
            <el-table-column prop="materialName" label="物料名称" min-width="120" show-overflow-tooltip />
            <el-table-column prop="materialTypeName" label="类型" width="80" />
            <el-table-column prop="totalDemand" label="总需求" width="90" align="right" />
            <el-table-column label="已送料" width="90" align="right">
              <template #default="{ row }">{{ row.totalDelivered || 0 }}</template>
            </el-table-column>
            <el-table-column label="库存" width="80" align="right">
              <template #default="{ row }">{{ row.warehouseStock || 0 }}</template>
            </el-table-column>
            <el-table-column label="已出库" width="80" align="right">
              <template #default="{ row }"><span :style="{color: Number(row.consumed||0)>0?'var(--app-color-primary)':''}">{{ row.consumed || 0 }}</span></template>
            </el-table-column>
            <el-table-column label="缺口" width="100" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.gap > 0" type="danger" size="small">{{ row.gap }}</el-tag>
                <el-tag v-else type="success" size="small">已齐套</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="订单明细" min-width="220">
              <template #default="{ row }">
                <div v-for="(o, i) in (row.orders || [])" :key="i" style="font-size:var(--app-font-xs);line-height:1.6">
                  <span>{{ o.order_code }}</span>
                  <span style="color:var(--app-text-secondary);margin:0 4px">/</span>
                  <span>{{ o.product_name }}</span>
                  <span style="color:var(--app-color-primary);margin-left:4px">需{{ o.demand_quantity }}</span>
                </div>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="materialSummary.length===0 && !materialLoading" style="color:var(--app-text-secondary);text-align:center;padding:40px">暂无生产中订单</div>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </PageShell>
</template>

<style scoped>
/* 页头/根容器已统一到全局骨架（PageShell + styles/page.css）；原 .detail-page 已删除。
   原「.detail-page :deep(.el-tabs__header) { margin-bottom:0 }」改挂到骨架类，视觉不变。 */
.page-shell :deep(.el-tabs__header) { margin-bottom:0; }

.order-table-card :deep(.el-card__body) { padding:16px; }
.order-table-card { margin-top:4px; }
</style>
