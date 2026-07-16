<script setup lang="ts">
defineOptions({ name: 'SupplierDetail' })
import { reactive, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
const route = useRoute(); const router = useRouter()
const id = Number(route.params.id)
const loading = ref(true)
const saving = ref(false)
const activeTab = ref('info')

const TYPE_OPTIONS = [
  { name: 'solution', label: '方案商' },
  { name: 'factory', label: '委外加工厂' },
  { name: 'product', label: '成品供应商' },
  { name: 'material', label: '辅料商' },
]

const form = reactive({
  id: undefined as any,
  code: '', name: '', supplierType: '', status: 1,
  contact: '', phone: '', address: '', remark: '',
  checkedTypes: [] as string[],
  isSolution: false, isTouch: false,
})
const products = ref<any[]>([])
const typeName = ref('')
const typeTags = ref<string[]>([])
const hasFactory = ref(false)

const TYPE_MAP: Record<string, string> = { solution: '方案商', factory: '加工厂', product: '成品商', material: '辅料商' }

function formatTypes(types: string): string {
  if (!types) return ''
  return types.split(',').map(t => TYPE_MAP[t.trim()] || t).join(' + ')
}

// 仓库/订单/缺料
const warehouses = ref<any[]>([])
const orders = ref<any[]>([])
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
      // 解析类型
      form.checkedTypes = (res.supplierType || '').split(',').map((t: string) => t.trim()).filter(Boolean)
      typeTags.value = form.checkedTypes
      typeName.value = formatTypes(res.supplierType)
      hasFactory.value = form.checkedTypes.includes('factory')
      // 解析方案商特有字段
      if (res.contactInfo) {
        try {
          const ct = JSON.parse(res.contactInfo)
          form.isSolution = ct.isSolution || false
          form.isTouch = ct.isTouch || false
        } catch { form.isSolution = false; form.isTouch = false }
      } else {
        form.isSolution = false; form.isTouch = false
      }
    }
    const prods = await request.get<any,any>(`/supplier/${id}/products`)
    products.value = prods || []
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
    const r = await request.get<any,any>('/outsource/order/page', { params: { factoryId: id, pageSize: 200 } })
    orders.value = r?.records || []
  } catch { orders.value = [] }
  finally { orderLoading.value = false }
}

function onTabChange(tab: any) {
  if (tab === 'warehouse' && warehouses.value.length === 0) loadWarehouses()
  if (tab === 'order' && orders.value.length === 0) loadOrders()
  if (tab === 'material' && materialSummary.value.length === 0) loadMaterialSummary()
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
    const body: any = { ...form, supplierType: form.checkedTypes.join(',') }
    if (form.checkedTypes.includes('solution')) {
      body.contactInfo = JSON.stringify({ isSolution: form.isSolution, isTouch: form.isTouch })
    }
    await request.put('/supplier', body)
    ElMessage.success('保存成功')
    loadData()
  } finally { saving.value = false }
}

function addProduct() { products.value.push({ name:'', spec:'', unit:'', unitPrice:0, remark:'' }) }
function removeProduct(i:number) { products.value.splice(i,1) }

async function saveProducts() {
  try {
    await request.put(`/supplier/${id}/products`, products.value)
    ElMessage.success('产品列表已保存')
  } catch (e: any) { ElMessage.error('保存失败: ' + (e?.message || '未知错误')) }
}

onMounted(loadData)
</script>

<template>
  <div class="detail-page" v-loading="loading">
    <div class="page-header">
      <span class="page-title">{{ typeName }} — {{ form.name || '详情' }}</span>
      <el-tag v-for="t in typeTags" :key="t" size="small" style="margin-left:4px"
        :type="t==='factory'?'warning':t==='solution'?'primary':t==='material'?'info':'success'"
      >{{ TYPE_MAP[t] || t }}</el-tag>
      <el-tag v-if="form.status===1" type="success" size="small" style="margin-left:4px">启用</el-tag>
      <el-tag v-else type="danger" size="small" style="margin-left:4px">停用</el-tag>
      <el-button type="primary" style="margin-left:auto" :loading="saving" @click="handleSave">保存</el-button>
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
              <!-- 方案商特有 -->
              <template v-if="form.checkedTypes.includes('solution')">
                <el-col :span="8"><el-form-item label="显示方案"><el-switch v-model="form.isSolution" /></el-form-item></el-col>
                <el-col :span="8"><el-form-item label="触摸方案"><el-switch v-model="form.isTouch" /></el-form-item></el-col>
              </template>
              <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item></el-col>
            </el-row>
          </el-form>
        </el-card>

        <el-card shadow="never" style="margin-top:12px">
          <template #header>
            <div style="display:flex;justify-content:space-between;align-items:center">
              <span style="font-weight:600">供应产品</span>
              <el-button type="primary" size="small" @click="saveProducts">保存产品</el-button>
            </div>
          </template>
          <el-button type="primary" size="small" text @click="addProduct" style="margin-bottom:8px">+ 添加产品</el-button>
          <el-table :data="products" border size="small">
            <el-table-column label="产品名称"><template #default="{row}"><el-input v-model="row.name" size="small" /></template></el-table-column>
            <el-table-column label="规格" width="120"><template #default="{row}"><el-input v-model="row.spec" size="small" /></template></el-table-column>
            <el-table-column label="单位" width="80"><template #default="{row}"><el-input v-model="row.unit" size="small" /></template></el-table-column>
            <el-table-column label="单价" width="100"><template #default="{row}"><el-input v-model="row.unitPrice" size="small" /></template></el-table-column>
            <el-table-column label="备注" width="120"><template #default="{row}"><el-input v-model="row.remark" size="small" /></template></el-table-column>
            <el-table-column label="操作" width="60" align="center"><template #default="{$index}"><el-button type="danger" link size="small" @click="removeProduct($index)">删除</el-button></template></el-table-column>
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
      <el-tab-pane label="订单详细" name="order">
        <el-card shadow="never" class="order-table-card">
          <el-table v-loading="orderLoading" :data="orders" border stripe>
            <el-table-column prop="code" label="加工单号" min-width="160" show-overflow-tooltip />
            <el-table-column prop="factoryName" label="加工厂" min-width="130" show-overflow-tooltip />
            <el-table-column label="产品" width="70" align="center">
              <template #default="{row}">{{ row.productCount || 0 }}项</template>
            </el-table-column>
            <el-table-column prop="totalAmount" label="金额" width="100" align="right">
              <template #default="{row}">{{ row.totalAmount ? Number(row.totalAmount).toFixed(2) : '-' }}</template>
            </el-table-column>
            <el-table-column prop="planEndDate" label="计划完成" width="110" />
            <el-table-column prop="status" label="状态" width="80" align="center">
              <template #default="{row}">
                <el-tag :type="row.status==='待确认'?'info':row.status==='生产中'?'primary':row.status==='已完成'?'success':'danger'" size="small">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80" align="center" fixed="right">
              <template #default="{row}">
                <el-button type="primary" link @click="router.push(`/outsource/order/detail/${row.id}`)">查看</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="orders.length===0 && !orderLoading" style="color:#909399;text-align:center;padding:40px">暂无订单</div>
        </el-card>
      </el-tab-pane>

      <!-- 物料缺料（仅委外加工厂） -->
      <el-tab-pane v-if="hasFactory" label="物料缺料" name="material">
        <el-card shadow="never" v-loading="materialLoading">
          <el-table :data="materialSummary" border stripe size="small">
            <el-table-column prop="materialName" label="物料名称" min-width="120" show-overflow-tooltip />
            <el-table-column prop="materialType" label="类型" width="80" />
            <el-table-column prop="totalDemand" label="总需求" width="90" align="right" />
            <el-table-column label="已送料" width="90" align="right">
              <template #default="{ row }">{{ row.totalDelivered || 0 }}</template>
            </el-table-column>
            <el-table-column label="库存" width="80" align="right">
              <template #default="{ row }">{{ row.warehouseStock || 0 }}</template>
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
.page-title { font-size:20px; font-weight:600; }
.order-table-card :deep(.el-card__body) { padding:16px; }
.order-table-card { margin-top:4px; }
</style>
