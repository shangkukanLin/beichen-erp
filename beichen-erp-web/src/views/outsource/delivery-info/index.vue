<script setup lang="ts">
defineOptions({ name: 'OutsourceDeliveryInfo' })

import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import request from '@/utils/request'
import { DocStatusLabel, DocStatusTag } from '@/api/common'

const router = useRouter()
const activeTab = ref('material')

// ==================== 物料交货信息 ====================
const TYPE_OPTIONS = [
  { value: 'RECEIVE', label: '收货' },
  { value: 'DEFECT_RETURN', label: '退不良' }
]
const TYPE_TAG: Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger'> = { RECEIVE: 'success', DEFECT_RETURN: 'danger' }
const mQuery = reactive({ deliveryCode: '', orderCode: '', materialName: '', deliveryType: '', status: '', dateRange: null as [string, string] | null })
const mPagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const mTableData = ref<any[]>([])
const mLoading = ref(false)

// ==================== 成品交货信息 ====================
// 类型：DELIVERY=普通交货，DEFECT_RETURN=退不良
const P_TYPE_OPTIONS = [
  { value: 'DELIVERY', label: '交货' },
  { value: 'DEFECT_RETURN', label: '退不良' }
]
const P_TYPE_TAG: Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger'> = { DELIVERY: 'success', DEFECT_RETURN: 'danger' }
const pQuery = reactive({ orderCode: '', productName: '', deliveryType: '', status: '', dateRange: null as [string, string] | null })
const pPagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const pTableData = ref<any[]>([])
const pLoading = ref(false)

function typeLabel(v?: string) {
  const t = TYPE_OPTIONS.find(o => o.value === v)
  return t ? t.label : (v || '-')
}
function pTypeLabel(v?: string) {
  const t = P_TYPE_OPTIONS.find(o => o.value === v)
  return t ? t.label : (v || '-')
}

function fmt(v: any) { return v == null ? '0.00' : Number(v).toFixed(2) }
function fmtDate(v: any) { return v ? String(v).slice(0, 10) : '' }

// 仓库详情路由：工厂仓走委外仓库详情，自有仓走进销存仓库详情（与收发单列表一致）
function whDetailRoute(row: any) {
  return row.warehouse_factory_id != null
    ? `/outsource/warehouse/detail/${row.warehouse_id}`
    : `/inventory/warehouse/detail/${row.warehouse_id}`
}

// 产品详情：跳产品主数据详情（productMasterId = product.id）
function goProduct(id?: number | null) { if (id) router.push(`/product/detail/${id}`) }

// 成品交货·仓库详情路由：工厂仓(委外仓)与自有成品仓路由不同，按 factoryId 分流
function pWhDetailRoute(row: any) {
  return row.warehouseFactoryId != null
    ? `/outsource/warehouse/detail/${row.warehouseId}`
    : `/inventory/warehouse/detail/${row.warehouseId}`
}

async function loadMaterial() {
  mLoading.value = true
  try {
    const p: any = { pageNum: mPagination.pageNum, pageSize: mPagination.pageSize }
    if (mQuery.deliveryCode) p.deliveryCode = mQuery.deliveryCode
    if (mQuery.orderCode) p.orderCode = mQuery.orderCode
    if (mQuery.materialName) p.materialName = mQuery.materialName
    if (mQuery.deliveryType) p.deliveryType = mQuery.deliveryType
    if (mQuery.status) p.status = mQuery.status
    if (mQuery.dateRange && mQuery.dateRange.length === 2) {
      p.startDate = mQuery.dateRange[0]; p.endDate = mQuery.dateRange[1]
    }
    const r = await request.get<any, any>('/outsource/material-order/delivery-items/page', { params: p })
    mTableData.value = r?.records || []; mPagination.total = r?.total || 0
  } catch { mTableData.value = [] } finally { mLoading.value = false }
}

async function loadProduct() {
  pLoading.value = true
  try {
    const p: any = { pageNum: pPagination.pageNum, pageSize: pPagination.pageSize }
    if (pQuery.orderCode) p.orderCode = pQuery.orderCode
    if (pQuery.productName) p.productName = pQuery.productName
    if (pQuery.deliveryType) p.deliveryType = pQuery.deliveryType
    if (pQuery.status) p.status = pQuery.status
    if (pQuery.dateRange && pQuery.dateRange.length === 2) {
      p.startDate = pQuery.dateRange[0]; p.endDate = pQuery.dateRange[1]
    }
    const r = await request.get<any, any>('/outsource/order-delivery/page', { params: p })
    pTableData.value = r?.records || []; pPagination.total = r?.total || 0
  } catch { pTableData.value = [] } finally { pLoading.value = false }
}

function handleMaterialQuery() { mPagination.pageNum = 1; loadMaterial() }
function handleMaterialReset() {
  mQuery.deliveryCode = ''; mQuery.orderCode = ''; mQuery.materialName = ''
  mQuery.deliveryType = ''; mQuery.status = ''; mQuery.dateRange = null
  mPagination.pageNum = 1; loadMaterial()
}
function handleProductQuery() { pPagination.pageNum = 1; loadProduct() }
function handleProductReset() {
  pQuery.orderCode = ''; pQuery.productName = ''
  pQuery.deliveryType = ''; pQuery.status = ''; pQuery.dateRange = null
  pPagination.pageNum = 1; loadProduct()
}

// 切换页签时按需加载，未激活的页签不发请求
function handleTabChange() {
  if (activeTab.value === 'material') loadMaterial()
  else loadProduct()
}

onMounted(() => { loadMaterial() })
</script>

<template>
  <div class="p">
    <el-card shadow="never" class="tabs-card">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <!-- ==================== 物料交货信息 ==================== -->
        <el-tab-pane label="物料交货信息" name="material">
          <el-card shadow="never" class="query-card">
            <el-form :inline="true" :model="mQuery" label-width="84px" class="query-form">
              <el-form-item label="交货单号"><el-input v-model="mQuery.deliveryCode" placeholder="交货单号" clearable @keyup.enter="handleMaterialQuery" /></el-form-item>
              <el-form-item label="物料订单号"><el-input v-model="mQuery.orderCode" placeholder="物料订单号" clearable @keyup.enter="handleMaterialQuery" /></el-form-item>
              <el-form-item label="物料名称"><el-input v-model="mQuery.materialName" placeholder="物料名称" clearable @keyup.enter="handleMaterialQuery" /></el-form-item>
              <el-form-item label="类型">
                <el-select v-model="mQuery.deliveryType" placeholder="全部" clearable>
                  <el-option v-for="o in TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
                </el-select>
              </el-form-item>
              <el-form-item label="状态">
                <el-select v-model="mQuery.status" placeholder="全部" clearable>
                  <el-option v-for="(label, code) in DocStatusLabel" :key="code" :label="label" :value="code" />
                </el-select>
              </el-form-item>
              <el-form-item label="交货日期">
                <el-date-picker v-model="mQuery.dateRange" type="daterange" value-format="YYYY-MM-DD" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" />
              </el-form-item>
              <div class="query-actions">
                <el-button type="primary" :icon="'Search'" @click="handleMaterialQuery">查询</el-button>
                <el-button :icon="'Refresh'" @click="handleMaterialReset">重置</el-button>
              </div>
            </el-form>
          </el-card>

          <el-card shadow="never" class="table-card">
            <el-table :data="mTableData" border stripe v-loading="mLoading" style="width:100%">
              <el-table-column label="交货日期" width="105"><template #default="{row}">{{ fmtDate(row.delivery_date) }}</template></el-table-column>
              <el-table-column label="类型" width="85" align="center">
                <template #default="{row}"><el-tag :type="TYPE_TAG[row.delivery_type] || 'info'" size="small">{{ typeLabel(row.delivery_type) }}</el-tag></template>
              </el-table-column>
              <el-table-column label="交货单号" width="160">
                <template #default="{row}">
                  <el-link type="primary" underline="never" @click="router.push(`/outsource/delivery/detail/${row.delivery_id}`)">{{ row.delivery_code }}</el-link>
                </template>
              </el-table-column>
              <el-table-column label="物料订单号" width="165">
                <template #default="{row}">
                  <el-link v-if="row.order_id" type="primary" underline="never" @click="router.push(`/outsource/material-order/detail/${row.order_id}`)">{{ row.order_code }}</el-link>
                  <span v-else>-</span>
                </template>
              </el-table-column>
              <el-table-column label="供应商" width="130" show-overflow-tooltip>
                <template #default="{row}">
                  <el-link v-if="row.supplier_id" type="primary" underline="never" @click="router.push(`/supplier/detail/${row.supplier_id}`)">{{ row.supplier_name }}</el-link>
                  <span v-else>-</span>
                </template>
              </el-table-column>
              <el-table-column prop="material_name" label="物料名称" min-width="130" show-overflow-tooltip />
              <el-table-column label="收货仓库" width="120" show-overflow-tooltip>
                <template #default="{row}">
                  <el-link v-if="row.warehouse_id" type="primary" underline="never" @click="router.push(whDetailRoute(row))">{{ row.warehouse_name || '-' }}</el-link>
                  <span v-else>-</span>
                </template>
              </el-table-column>
              <el-table-column label="数量" width="95" align="right"><template #default="{row}">{{ fmt(row.quantity) }}</template></el-table-column>
              <el-table-column label="金额" width="105" align="right"><template #default="{row}">{{ fmt(row.amount) }}</template></el-table-column>
              <el-table-column label="状态" width="85" align="center">
                <template #default="{row}"><el-tag :type="DocStatusTag[row.delivery_status] || 'info'" size="small">{{ DocStatusLabel[row.delivery_status] || row.delivery_status }}</el-tag></template>
              </el-table-column>
            </el-table>
            <div class="pagination"><el-pagination v-model:current-page="mPagination.pageNum" v-model:page-size="mPagination.pageSize" :total="mPagination.total" :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next" background @current-change="loadMaterial" @size-change="handleMaterialQuery" /></div>
          </el-card>
        </el-tab-pane>

        <!-- ==================== 成品交货信息 ==================== -->
        <el-tab-pane label="成品交货信息" name="product">
          <el-card shadow="never" class="query-card">
            <el-form :inline="true" :model="pQuery" label-width="84px" class="query-form">
              <el-form-item label="加工单号"><el-input v-model="pQuery.orderCode" placeholder="加工单号" clearable @keyup.enter="handleProductQuery" /></el-form-item>
              <el-form-item label="产品名称"><el-input v-model="pQuery.productName" placeholder="产品名称" clearable @keyup.enter="handleProductQuery" /></el-form-item>
              <el-form-item label="类型">
                <el-select v-model="pQuery.deliveryType" placeholder="全部" clearable>
                  <el-option v-for="o in P_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
                </el-select>
              </el-form-item>
              <el-form-item label="状态">
                <el-select v-model="pQuery.status" placeholder="全部" clearable>
                  <el-option v-for="(label, code) in DocStatusLabel" :key="code" :label="label" :value="code" />
                </el-select>
              </el-form-item>
              <el-form-item label="交货日期">
                <el-date-picker v-model="pQuery.dateRange" type="daterange" value-format="YYYY-MM-DD" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" />
              </el-form-item>
              <div class="query-actions">
                <el-button type="primary" :icon="'Search'" @click="handleProductQuery">查询</el-button>
                <el-button :icon="'Refresh'" @click="handleProductReset">重置</el-button>
              </div>
            </el-form>
          </el-card>

          <el-card shadow="never" class="table-card">
            <el-table :data="pTableData" border stripe v-loading="pLoading" style="width:100%">
              <el-table-column label="交货日期" width="105"><template #default="{row}">{{ fmtDate(row.deliveryDate) }}</template></el-table-column>
              <el-table-column label="类型" width="85" align="center">
                <template #default="{row}"><el-tag :type="P_TYPE_TAG[row.deliveryType] || 'info'" size="small">{{ pTypeLabel(row.deliveryType) }}</el-tag></template>
              </el-table-column>
              <el-table-column label="加工单号" width="165">
                <template #default="{row}">
                  <el-link v-if="row.orderId" type="primary" underline="never" @click="router.push(`/outsource/order/detail/${row.orderId}`)">{{ row.orderCode || '-' }}</el-link>
                  <span v-else>-</span>
                </template>
              </el-table-column>
              <el-table-column label="产品名称" min-width="140" show-overflow-tooltip>
                <template #default="{row}">
                  <span>{{ row.productName || '-' }}</span>
                  <span v-if="row.productSpec" style="color:var(--app-text-secondary);font-size:var(--app-font-xs)"> / {{ row.productSpec }}</span>
                </template>
              </el-table-column>
              <el-table-column label="SKU" width="120" show-overflow-tooltip>
                <template #default="{row}">
                  <el-link v-if="row.productMasterId" type="primary" underline="never" @click="goProduct(row.productMasterId)">{{ row.sku || '-' }}</el-link>
                  <span v-else>{{ row.sku || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="收货仓库" width="120" show-overflow-tooltip>
                <template #default="{row}">
                  <el-link v-if="row.warehouseId" type="primary" underline="never" @click="router.push(pWhDetailRoute(row))">{{ row.warehouseName || '-' }}</el-link>
                  <span v-else>-</span>
                </template>
              </el-table-column>
              <el-table-column label="总数量" width="95" align="right"><template #default="{row}">{{ fmt(row.quantity) }}</template></el-table-column>
              <el-table-column label="等级分布" min-width="165">
                <template #default="{row}">
                  <span v-if="row.aQty || row.bQty || row.cQty || row.defectQty">
                    <span style="color:var(--app-color-success)">A{{ row.aQty||0 }}</span> /
                    <span style="color:var(--app-color-primary)">B{{ row.bQty||0 }}</span> /
                    <span style="color:var(--app-color-warning)">C{{ row.cQty||0 }}</span> /
                    <span style="color:var(--app-color-danger)">不良{{ row.defectQty||0 }}</span>
                  </span>
                  <span v-else style="color:var(--app-text-secondary)">{{ fmt(row.quantity) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="85" align="center">
                <template #default="{row}"><el-tag :type="DocStatusTag[row.status] || 'info'" size="small">{{ DocStatusLabel[row.status] || row.status }}</el-tag></template>
              </el-table-column>
              <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip />
            </el-table>
            <div class="pagination"><el-pagination v-model:current-page="pPagination.pageNum" v-model:page-size="pPagination.pageSize" :total="pPagination.total" :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next" background @current-change="loadProduct" @size-change="handleProductQuery" /></div>
          </el-card>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<style scoped>
.p { display:flex; flex-direction:column; gap:12px; }
.tabs-card :deep(.el-card__body) { padding:12px 16px 16px; }
.query-card :deep(.el-card__body), .table-card :deep(.el-card__body) { padding:16px; }
.query-card { margin-bottom:12px; }
.pagination { margin-top:16px; display:flex; justify-content:flex-end; }

/* 查询区：4 列 Grid 栅格，列距 24px / 行距 16px 固定 */
.query-form { display:grid; grid-template-columns:repeat(4, minmax(0, 1fr)); gap:16px 24px; }
.query-form :deep(.el-form-item) { margin:0; }
.query-form :deep(.el-form-item__content) { flex:1; min-width:0; }
.query-form :deep(.el-form-item__content .el-input),
.query-form :deep(.el-form-item__content .el-select),
.query-form :deep(.el-form-item__content .el-date-editor) { width:100%; }
.query-actions { display:flex; align-items:center; gap:8px; }
</style>
