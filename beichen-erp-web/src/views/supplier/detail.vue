<script setup lang="ts">
defineOptions({ name: 'SupplierDetail' })
import { reactive, ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { getProjectBom } from '@/api/system'
const route = useRoute(); const router = useRouter()
const id = Number(route.params.id)
const loading = ref(true)
const saving = ref(false)
const activeTab = ref('info')

import { TYPE_OPTIONS, TYPE_MAP } from '@/constants/supplier'

const form = reactive({
  id: undefined as any,
  code: '', name: '', supplierType: '', status: 1,
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
    ElMessage.success('供应物料已保存')
    loadMaterials()
  } catch (e: any) { ElMessage.error('保存失败: ' + (e?.message || '未知错误')) }
}
const typeName = ref('')
const typeTags = ref<string[]>([])
const hasFactory = ref(false)

function formatTypes(types: string[]): string {
  if (!types || types.length === 0) return ''
  return types.map(t => TYPE_MAP[t] || t).join(' + ')
}

// 仓库/订单/缺料
const warehouses = ref<any[]>([])
const orders = ref<any[]>([])
const materialOrders = ref<any[]>([])
const activeStatusTab = ref('进行中')
const ACTIVE_STATUSES = ['待确认', '生产中', '已确认', '收货中']
const filteredOrders = computed(() => {
  const all = [...orders.value, ...materialOrders.value]
  if (activeStatusTab.value === '已完成') return all.filter(o => o.status === '已完成')
  if (activeStatusTab.value === '已取消') return all.filter(o => o.status === '已取消')
  return all.filter(o => ACTIVE_STATUSES.includes(o.status))
})
const whLoading = ref(false)
const orderLoading = ref(false)
const materialLoading = ref(false)
const materialSummary = ref<any[]>([])

async function loadData() {
  loading.value = true
  try {
    const res = await request.get<any,any>(`/supplier/${id}`)
    if (res) {
      Object.assign(form, res)
      // 类型编码列表
      form.checkedTypes = res.typeCodes || []
      typeTags.value = form.checkedTypes
      typeName.value = formatTypes(form.checkedTypes || [])
      hasFactory.value = form.checkedTypes.includes('factory')
    }
    const prods = await request.get<any,any>(`/supplier/${id}/products`)
    products.value = prods || []
    const mats = await request.get<any,any>(`/supplier/${id}/materials`)
    materials.value = mats || []
    await markBomFlags()
  } finally { loading.value = false }
}

async function loadWarehouses() {
  whLoading.value = true
  try {
    const r = await request.get<any,any>('/outsource/delivery/warehouses/by-factory/' + id)
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
    materialOrders.value = (r2?.records || []).map((o: any) => ({ ...o, _type: o.orderType || '物料单', _route: `/outsource/material-order/detail/${o.id}` }))
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
  if (!form.name) { ElMessage.warning('请输入供应商名称'); return }
  if (form.checkedTypes.length === 0) { ElMessage.warning('请选择至少一个类型'); return }
  saving.value = true
  try {
    const body: any = { ...form, typeCodes: form.checkedTypes }
    await request.put('/supplier', body)
    ElMessage.success('保存成功')
    loadData()
  } finally { saving.value = false }
}

function addProduct() { products.value.push({ productId: undefined, unitPrice:0, remark:'' }) }
function removeProduct(i:number) { products.value.splice(i,1) }

async function saveProducts() {
  try {
    await request.put(`/supplier/${id}/products`, products.value)
    ElMessage.success('产品列表已保存')
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

onMounted(loadData)
</script>

<template>
  <div class="detail-page" v-loading="loading">
    <div class="page-header">
      <el-tag v-for="t in typeTags" :key="t" size="small"
        :type="t==='factory'?'warning':t==='solution'?'primary':t==='material'?'info':'success'"
      >{{ TYPE_MAP[t] || t }}</el-tag>
    </div>

    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <el-tab-pane label="基础信息" name="info">
        <el-card shadow="never">
          <template #header><span style="font-weight:600">基础信息</span></template>
          <el-form :model="form" label-width="80px" size="small">
            <el-row :gutter="12">
              <el-col :span="8"><el-form-item label="名称"><el-input v-model="form.name" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="编码"><el-input :model-value="form.code" disabled /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="状态">
                <el-select v-model="form.status" style="width:100%">
                  <el-option label="启用" :value="1" />
                  <el-option label="停用" :value="0" />
                </el-select>
              </el-form-item></el-col>
              <el-col :span="8"><el-form-item label="联系人"><el-input v-model="form.contact" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="电话"><el-input v-model="form.phone" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="地址"><el-input v-model="form.address" /></el-form-item></el-col>
              <el-col :span="24">
                <el-form-item label="类型" required>
                  <el-checkbox-group v-model="form.checkedTypes">
                    <el-checkbox v-for="t in TYPE_OPTIONS" :key="t.name" :label="t.name" :value="t.name">{{ t.label }}</el-checkbox>
                  </el-checkbox-group>
                </el-form-item>
              </el-col>
              <el-col :span="24">
                <el-form-item label="账期">
                  <div style="display:flex;align-items:center;gap:6px">
                    <el-input-number v-model="form.creditPeriodMonths" :min="0" :max="24" placeholder="月" controls-position="right" style="width:90px" /><span>个月</span>
                    <el-input-number v-model="form.creditPeriod" :min="0" :max="31" placeholder="天" controls-position="right" style="width:90px" /><span>天</span>
                    <span style="color:#909399;font-size:12px">（收货/交货后多少天付款，默认当天）</span>
                  </div>
                </el-form-item>
              </el-col>
              <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item></el-col>
              <el-col :span="24"><el-form-item><el-button type="primary" :loading="saving" @click="handleSave">保存</el-button></el-form-item></el-col>
            </el-row>
          </el-form>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="供应产品" name="product">
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
                <el-select v-else v-model="row.productId" placeholder="搜索产品" filterable remote :remote-method="searchProducts" size="small" style="width:100%">
                  <el-option v-for="p in prodOptions" :key="p.id" :label="p.name" :value="p.id" />
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

      <el-tab-pane label="供应物料" name="material-supply">
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
            <el-table-column label="BOM类型" width="120"><template #default="{row}">{{ row.bomTypeName || '-' }}</template></el-table-column>
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
                <el-button type="primary" link size="small" @click="router.push(`/outsource/warehouse/detail/${row.id}`)">查看</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="warehouses.length===0 && !whLoading" style="color:#909399;text-align:center;padding:40px">暂无仓库</div>
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
                <el-tag :type="row.status==='待确认'?'info':row.status==='生产中'||row.status==='收货中'?'primary':row.status==='已完成'?'success':'danger'" size="small">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="70" align="center" fixed="right">
              <template #default="{row}">
                <el-button type="primary" link @click="router.push(row._route)">查看</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="filteredOrders.length===0 && !orderLoading" style="color:#909399;text-align:center;padding:40px">暂无订单</div>
        </el-card>
      </el-tab-pane>

      <!-- 物料缺料（仅委外加工厂） -->
      <el-tab-pane v-if="hasFactory" label="物料缺料" name="material">
        <el-card shadow="never" v-loading="materialLoading">
          <el-table :data="materialSummary" border stripe size="small">
            <el-table-column prop="materialName" label="物料名称" min-width="120" show-overflow-tooltip />
            <el-table-column prop="bomTypeName" label="类型" width="80" />
            <el-table-column prop="totalDemand" label="总需求" width="90" align="right" />
            <el-table-column label="已送料" width="90" align="right">
              <template #default="{ row }">{{ row.totalDelivered || 0 }}</template>
            </el-table-column>
            <el-table-column label="库存" width="80" align="right">
              <template #default="{ row }">{{ row.warehouseStock || 0 }}</template>
            </el-table-column>
            <el-table-column label="已出库" width="80" align="right">
              <template #default="{ row }"><span :style="{color: Number(row.consumed||0)>0?'#409eff':''}">{{ row.consumed || 0 }}</span></template>
            </el-table-column>
            <el-table-column label="缺口" width="100" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.gap > 0" type="danger" size="small">{{ row.gap }}</el-tag>
                <el-tag v-else type="success" size="small">已齐套</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="订单明细" min-width="220">
              <template #default="{ row }">
                <div v-for="(o, i) in (row.orders || [])" :key="i" style="font-size:12px;line-height:1.6">
                  <span>{{ o.order_code }}</span>
                  <span style="color:#909399;margin:0 4px">/</span>
                  <span>{{ o.product_name }}</span>
                  <span style="color:#409EFF;margin-left:4px">需{{ o.demand_quantity }}</span>
                </div>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="materialSummary.length===0 && !materialLoading" style="color:#909399;text-align:center;padding:40px">暂无生产中订单</div>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.detail-page { padding:16px; }
.page-header { display:flex; align-items:center; gap:12px; margin-bottom:16px; flex-wrap:wrap; }

.order-table-card :deep(.el-card__body) { padding:16px; }
.order-table-card { margin-top:4px; }
</style>
