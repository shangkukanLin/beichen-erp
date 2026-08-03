<template>
  <div class="dashboard">
    <el-tabs v-model="activeTab" type="border-card">
      <el-tab-pane v-if="hasModule['dev']" label="项目研发" name="dev">
        <div class="stat-grid">
          <div class="stat-card clickable" @click="$router.push('/dev/project?tab=active')">
            <div class="stat-value" style="color:#0C4A6E">{{ devTotal }}</div>
            <div class="stat-label">项目总数</div>
          </div>
          <div class="stat-card clickable" @click="$router.push('/dev/project?tab=active')">
            <div class="stat-value" style="color:#e6a23c">{{ devInProgress }}</div>
            <div class="stat-label">进行中</div>
          </div>
          <div class="stat-card clickable" @click="$router.push('/dev/bom')">
            <div class="stat-value" style="color:#409eff">{{ devBomCount }}</div>
            <div class="stat-label">BOM总数</div>
          </div>
          <div class="stat-card clickable" @click="$router.push('/dev/project?tab=finished')">
            <div class="stat-value" style="color:#67c23a">{{ devFinished }}</div>
            <div class="stat-label">已结项</div>
          </div>
        </div>

        <el-card shadow="never" class="section-card" v-if="inProgressProjects.length">
          <template #header>
            <span class="section-title">进行中项目</span>
            <el-button size="small" text style="float:right" @click="$router.push('/dev/project')">查看更多 →</el-button>
          </template>
          <el-table :data="inProgressProjects" size="small" stripe>
            <el-table-column prop="name" label="项目名称" min-width="160">
              <template #default="{row}"><el-link type="primary" @click="$router.push(`/dev/project/edit/${row.id}`)">{{ row.name }}</el-link></template>
            </el-table-column>
            <el-table-column label="当前阶段" width="120"><template #default="{row}"><el-tag type="warning" size="small">{{ getDashboardPhase(row) }}</el-tag></template></el-table-column>
          </el-table>
        </el-card>

        <div class="quick-links">
          <span class="links-label">快捷入口：</span>
          <el-button v-if="hasMenu['DevProject']" type="primary" size="small" text @click="$router.push('/dev/project')">研发项目</el-button>
          <el-button v-if="hasMenu['DevBom']" type="primary" size="small" text @click="$router.push('/dev/bom')">BOM管理</el-button>
          <el-button v-if="hasMenu['DevPhaseTemplate']" type="primary" size="small" text @click="$router.push('/dev/phase-template')">阶段模板</el-button>
        </div>
      </el-tab-pane>

      <el-tab-pane v-if="hasModule['outsource']" label="委外加工" name="outsource">
        <div class="stat-grid">
          <div class="stat-card clickable" style="background:#fef0f0" @click="$router.push('/outsource/order')">
            <div class="stat-value" style="color:#e6a23c">{{ osPending }}</div>
            <div class="stat-label">加工单待处理</div>
          </div>
          <div class="stat-card clickable" style="background:#f0f5ff" @click="$router.push('/outsource/order')">
            <div class="stat-value" style="color:#409eff">{{ osInProgress }}</div>
            <div class="stat-label">加工单进行中</div>
          </div>
          <div class="stat-card clickable" style="background:#fef0f0" @click="$router.push('/outsource/material-order')">
            <div class="stat-value" style="color:#e6a23c">{{ osMatPending }}</div>
            <div class="stat-label">物料订单待处理</div>
          </div>
          <div class="stat-card clickable" style="background:#f0f5ff" @click="$router.push('/outsource/material-order')">
            <div class="stat-value" style="color:#409eff">{{ osMatReceiving }}</div>
            <div class="stat-label">物料订单收货中</div>
          </div>
        </div>

        <el-card shadow="never" class="section-card" v-if="activeOrders.length">
          <template #header>
            <span class="section-title">进行中加工单</span>
            <el-button size="small" text style="float:right" @click="$router.push('/outsource/order')">查看更多 →</el-button>
          </template>
          <el-table :data="activeOrders" size="small" stripe>
            <el-table-column prop="code" label="单号" min-width="120" show-overflow-tooltip>
              <template #default="{row}"><el-link type="primary" @click="$router.push(`/outsource/order/detail/${row.id}`)">{{ row.code }}</el-link></template>
            </el-table-column>
            <el-table-column prop="factoryName" label="加工厂" min-width="140" show-overflow-tooltip />
            <el-table-column label="产品" min-width="160" show-overflow-tooltip>
              <template #default="{row}">{{ row.productNames || ((row.productCount || 0) + '项') }}</template>
            </el-table-column>
            <el-table-column label="金额" width="110" align="right">
              <template #default="{row}">{{ row.totalAmount ? Number(row.totalAmount).toFixed(2) : '-' }}</template>
            </el-table-column>
            <el-table-column label="计划开始" width="110"><template #default="{row}">{{ row.planStartDate || '-' }}</template></el-table-column>
            <el-table-column label="计划完成" width="110"><template #default="{row}">{{ row.planEndDate || '-' }}</template></el-table-column>
            <el-table-column label="最近交货" width="110"><template #default="{row}">{{ row.latestDeliveryDate || '-' }}</template></el-table-column>
            <el-table-column label="状态" width="90" align="center">
              <template #default="{row}"><el-tag :type="row.status==='待确认'?'info':row.status==='生产中'?'':row.status==='已完成'?'success':'danger'" size="small">{{ row.status }}</el-tag></template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-card shadow="never" class="section-card" v-if="pendingMatOrders.length">
          <template #header>
            <span class="section-title">进行中物料订单</span>
            <el-button size="small" text style="float:right" @click="$router.push('/outsource/material-order')">查看更多 →</el-button>
          </template>
          <el-table :data="pendingMatOrders" size="small" stripe>
            <el-table-column prop="code" label="订单号" width="170">
              <template #default="{row}"><el-link type="primary" @click="$router.push(`/outsource/material-order/detail/${row.id}`)">{{ row.code }}</el-link></template>
            </el-table-column>
            <el-table-column prop="supplierName" label="供应商" width="160" show-overflow-tooltip />
            <el-table-column label="下单日期" width="100" align="center"><template #default="{row}">{{ row.createTime ? row.createTime.slice(0,10) : '-' }}</template></el-table-column>
            <el-table-column label="物料名称" min-width="120" show-overflow-tooltip>
              <template #default="{row}">
                <span>{{ (row.items || []).map((it: any) => it.materialName).filter(Boolean).join('、') || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="下单总数" width="80" align="center">
              <template #default="{row}">{{ (row.items || []).reduce((s: number, it: any) => s + (it.orderQuantity || 0), 0) }}</template>
            </el-table-column>
            <el-table-column label="已收" width="70" align="center">
              <template #default="{row}">
                <span :style="{color: (row.items || []).reduce((s: number, it: any) => s + (it.receivedQuantity || 0), 0)>0?'#67c23a':''}">
                  {{ (row.items || []).reduce((s: number, it: any) => s + (it.receivedQuantity || 0), 0) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="最近交货" width="100" align="center"><template #default="{row}">{{ row.lastDeliveryTime ? row.lastDeliveryTime.slice(0,10) : '-' }}</template></el-table-column>
            <el-table-column label="交期" width="100" align="center"><template #default="{row}">{{ row.deliveryDate || '-' }}</template></el-table-column>
            <el-table-column label="状态" width="80" align="center">
              <template #default="{row}"><el-tag :type="row.status==='待确认'?'info':row.status==='收货中'?'warning':row.status==='已完成'?'success':'danger'" size="small">{{ row.status }}</el-tag></template>
            </el-table-column>
          </el-table>
        </el-card>

        <div class="quick-links">
          <span class="links-label">快捷入口：</span>
          <el-button v-if="hasMenu['OutsourceOrder']" type="primary" size="small" text @click="$router.push('/outsource/order')">委外加工单</el-button>
          <el-button v-if="hasMenu['OutsourceMaterialOrder']" type="primary" size="small" text @click="$router.push('/outsource/material-order')">委外物料订单</el-button>
          <el-button v-if="hasMenu['OutsourceMaterialInfo']" type="primary" size="small" text @click="$router.push('/outsource/material-info')">物料信息管理</el-button>
          <el-button v-if="hasMenu['OutsourceWarehouse']" type="primary" size="small" text @click="$router.push('/outsource/warehouse')">委外仓库</el-button>
          <el-button v-if="hasMenu['OutsourceContractTemplate']" type="primary" size="small" text @click="$router.push('/outsource/contract-template')">加工合同模板</el-button>
          <el-button v-if="hasMenu['OutsourceDelivery']" type="primary" size="small" text @click="$router.push('/outsource/delivery')">物料收发单</el-button>
        </div>
      </el-tab-pane>

      <el-tab-pane v-if="hasModule['purchase']" label="进货业务" name="purchase">
        <div class="stat-grid">
          <div class="stat-card clickable" @click="$router.push('/inventory/purchase')">
            <div class="stat-value" style="color:#409eff">{{ purchaseTotal }}</div>
            <div class="stat-label">成品采购单</div>
          </div>
          <div class="stat-card clickable" @click="$router.push('/supplier/manage')">
            <div class="stat-value" style="color:#67c23a">{{ supplierTotal }}</div>
            <div class="stat-label">供应商</div>
          </div>
        </div>
        <div class="quick-links">
          <span class="links-label">快捷入口：</span>
          <el-button v-if="hasMenu['InventoryPurchase']" type="primary" size="small" text @click="$router.push('/inventory/purchase')">成品采购单</el-button>
          <el-button v-if="hasMenu['SupplierManage']" type="primary" size="small" text @click="$router.push('/supplier/manage')">供应商管理</el-button>
        </div>
      </el-tab-pane>

      <el-tab-pane v-if="hasModule['sale']" label="销售业务" name="sale">
        <div class="stat-grid">
          <div class="stat-card clickable" @click="$router.push('/inventory/sale')">
            <div class="stat-value" style="color:#409eff">{{ saleTotal }}</div>
            <div class="stat-label">销售单</div>
          </div>
          <div class="stat-card clickable" @click="$router.push('/inventory/customer')">
            <div class="stat-value" style="color:#67c23a">{{ customerTotal }}</div>
            <div class="stat-label">客户</div>
          </div>
        </div>
        <div class="quick-links">
          <span class="links-label">快捷入口：</span>
          <el-button v-if="hasMenu['InventorySale']" type="primary" size="small" text @click="$router.push('/inventory/sale')">销售单</el-button>
          <el-button v-if="hasMenu['InventoryCustomer']" type="primary" size="small" text @click="$router.push('/inventory/customer')">客户管理</el-button>
        </div>
      </el-tab-pane>

      <el-tab-pane v-if="hasModule['stock']" label="库存业务" name="stock">
        <div class="stat-grid">
          <div class="stat-card clickable" @click="$router.push('/material')">
            <div class="stat-value" style="color:#409eff">{{ productTotal }}</div>
            <div class="stat-label">产品</div>
          </div>
          <div class="stat-card clickable" @click="$router.push('/inventory/warehouse')">
            <div class="stat-value" style="color:#67c23a">{{ warehouseTotal }}</div>
            <div class="stat-label">仓库</div>
          </div>
        </div>
        <div class="quick-links">
          <span class="links-label">快捷入口：</span>
          <el-button v-if="hasMenu['InventoryStock']" type="primary" size="small" text @click="$router.push('/inventory/stock')">成品库存</el-button>
          <el-button v-if="hasMenu['InventoryWarehouse']" type="primary" size="small" text @click="$router.push('/inventory/warehouse')">仓库管理</el-button>
          <el-button v-if="hasMenu['MaterialManage']" type="primary" size="small" text @click="$router.push('/material')">产品管理</el-button>
        </div>
      </el-tab-pane>

      <el-tab-pane v-if="hasModule['finance']" label="财务" name="finance">
        <div class="stat-grid">
          <div class="stat-card" v-for="s in financeStats" :key="s.label">
            <div class="stat-value" :style="{color:s.color}">{{ s.value }}</div>
            <div class="stat-label">{{ s.label }}</div>
          </div>
        </div>
        <div class="quick-links">
          <span class="links-label">快捷入口：</span>
          <el-button v-if="hasMenu['FinanceReceivable']" type="primary" size="small" text @click="$router.push('/finance/receivable')">应收管理</el-button>
          <el-button v-if="hasMenu['FinancePayable']" type="primary" size="small" text @click="$router.push('/finance/payable')">应付管理</el-button>
          <el-button v-if="hasMenu['FinanceCashflow']" type="primary" size="small" text @click="$router.push('/finance/cashflow')">资金流水</el-button>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import request from '@/utils/request'
import { useUserStore } from '@/stores/user'
import { ProjectStatus } from '@/api/material'

const userStore = useUserStore()
const activeTab = ref('dev')

// 根据用户菜单权限判断可见模块
const hasMenu = ref<Record<string, boolean>>({})
const hasModule = reactive({ dev: false, outsource: false, purchase: false, sale: false, stock: false, finance: false })

// 统计卡片数据
const devTotal = ref(0)
const devInProgress = ref(0)
const devBomCount = ref(0)
const devFinished = ref(0)
const inProgressProjects = ref<any[]>([])
const dashboardTimelineMap = ref<Record<number, any[]>>({})

function getDashboardPhase(row: any) {
  const timelines = dashboardTimelineMap.value[row.id]
  if (!timelines || !timelines.length) return '-'
  const active = timelines.find((t: any) => t.status === 'IN_PROGRESS')
  return active ? active.statusName : '-'
}
const osPending = ref(0)
const osInProgress = ref(0)
const osMatPending = ref(0)
const osMatReceiving = ref(0)
const activeOrders = ref<any[]>([])
const pendingMatOrders = ref<any[]>([])
const purchaseTotal = ref(0)
const supplierTotal = ref(0)
const saleTotal = ref(0)
const customerTotal = ref(0)
const productTotal = ref(0)
const warehouseTotal = ref(0)
const financeStats = ref<{label:string,value:any,color:string}[]>([])

function checkUserMenus() {
  const menus = userStore.menus || []
  const names = new Set<string>()
  const collect = (list: any[]) => {
    if (!list) return
    list.forEach((m: any) => {
      if (m.routeName) names.add(m.routeName)
      if (m.children) collect(m.children)
    })
  }
  collect(menus)

  // 可见模块判断
  hasModule.dev = names.has('DevProject') || names.has('DevBom')
  hasModule.outsource = names.has('OutsourceOrder') || names.has('OutsourceMaterialOrder')
  hasModule.purchase = names.has('InventoryPurchase') || names.has('SupplierManage')
  hasModule.sale = names.has('InventorySale') || names.has('InventoryCustomer')
  hasModule.stock = names.has('InventoryStock') || names.has('InventoryWarehouse') || names.has('MaterialManage')
  hasModule.finance = names.has('FinanceReceivable') || names.has('FinancePayable')

  // 快捷入口可见性
  const menuNames = ['DevProject','DevBom','DevPhaseTemplate','OutsourceOrder','OutsourceMaterialOrder','OutsourceMaterialInfo','OutsourceWarehouse','OutsourceContractTemplate','OutsourceDelivery','InventoryPurchase','SupplierManage','InventorySale','InventoryCustomer','InventoryStock','InventoryWarehouse','MaterialManage','FinanceReceivable','FinancePayable','FinanceCashflow']
  menuNames.forEach(n => { hasMenu.value[n] = names.has(n) })

  // 默认激活第一个可见Tab
  if (hasModule.dev) activeTab.value = 'dev'
  else if (hasModule.outsource) activeTab.value = 'outsource'
  else if (hasModule.purchase) activeTab.value = 'purchase'
  else if (hasModule.sale) activeTab.value = 'sale'
  else if (hasModule.stock) activeTab.value = 'stock'
  else if (hasModule.finance) activeTab.value = 'finance'
}

async function loadStats() {
  try {
    // 项目研发
    if (hasModule.dev) {
      const [projRes, bomRes, allProjRes] = await Promise.all([
        request.get<any, any>('/dev/project/page', { params: { pageSize: 1 } }).catch(() => ({})),
        request.get<any, any>('/dev/bom/page', { params: { pageSize: 500 } }).catch(() => ({})),
        request.get<any, any>('/dev/project/page', { params: { pageSize: 500 } }).catch(() => ({})),
      ])
      const projTotal = projRes?.total || 0
      const bomRecords = bomRes?.records || []
      const uniqueProjectIds = new Set(bomRecords.map((r: any) => r.projectId))
      const bomProjectCount = uniqueProjectIds.size
      const allRecords = allProjRes?.records || []
      let inProgress = 0, finished = 0
      const activeProjects: any[] = []
      allRecords.forEach((p: any) => {
        if (p.status === ProjectStatus.IN_PROGRESS) { inProgress++; activeProjects.push(p) }
        else if (p.status === ProjectStatus.CLOSED) finished++
      })
      inProgressProjects.value = activeProjects.slice(0, 5)
      devTotal.value = projTotal
      devInProgress.value = inProgress
      devBomCount.value = bomProjectCount
      devFinished.value = finished
      // 加载进行中项目的时间线
      if (activeProjects.length > 0) {
        try {
          const tlRes = await request.post('/dev/project/timelines/batch', { projectIds: activeProjects.map((p: any) => p.id) })
          dashboardTimelineMap.value = tlRes || {}
        } catch { /* ignore */}
      }
    }
  } catch { /* ignore */}
  try {
    if (hasModule.outsource) {
      const [allOrderRes, allMatRes] = await Promise.all([
        request.get<any, any>('/outsource/order/page', { params: { pageSize: 500 } }).catch(() => ({})),
        request.get<any, any>('/outsource/material-order/page', { params: { pageSize: 500 } }).catch(() => ({})),
      ])
      const allOrders = allOrderRes?.records || []
      let pending = 0, inProd = 0
      const activeList: any[] = []
      allOrders.forEach((o: any) => {
        if (o.status === '待确认') pending++
        if (o.status === '生产中') { inProd++; activeList.push(o) }
        if (o.status === '待确认') activeList.push(o)
      })
      osPending.value = pending
      osInProgress.value = pending + inProd
      activeOrders.value = activeList.slice(0, 5)
      const allMats = allMatRes?.records || []
      let matPending = 0, matReceiving = 0
      allMats.forEach((m: any) => {
        if (m.status === '待确认' || m.status === '已确认') matPending++
        if (m.status === '收货中') matReceiving++
      })
      osMatPending.value = matPending
      osMatReceiving.value = matReceiving
      pendingMatOrders.value = allMats.filter((m: any) => m.status !== '已完成' && m.status !== '已取消').slice(0, 5)
    }
  } catch { /* ignore */}
  try {
    if (hasModule.purchase) {
      const [purRes, supRes] = await Promise.all([
        request.get<any, any>('/inventory/purchase/page', { params: { pageSize: 1 } }).catch(() => ({})),
        request.get<any, any>('/supplier/page', { params: { pageSize: 1 } }).catch(() => ({})),
      ])
      purchaseTotal.value = purRes?.total || 0
      supplierTotal.value = supRes?.total || 0
    }
  } catch { /* ignore */}
  try {
    if (hasModule.sale) {
      const [saleRes, cusRes] = await Promise.all([
        request.get<any, any>('/inventory/sale/page', { params: { pageSize: 1 } }).catch(() => ({})),
        request.get<any, any>('/inventory/customer/page', { params: { pageSize: 1 } }).catch(() => ({})),
      ])
      saleTotal.value = saleRes?.total || 0
      customerTotal.value = cusRes?.total || 0
    }
  } catch { /* ignore */}
  try {
    if (hasModule.stock) {
      const [prodRes, whRes] = await Promise.all([
        request.get<any, any>('/product/page', { params: { pageSize: 1 } }).catch(() => ({})),
        request.get<any, any>('/inventory/warehouse/page', { params: { pageSize: 1 } }).catch(() => ({})),
      ])
      productTotal.value = prodRes?.total || 0
      warehouseTotal.value = whRes?.total || 0
    }
  } catch { /* ignore */}
  try {
    if (hasModule.finance) {
      const [recRes, payRes] = await Promise.all([
        request.get<any, any>('/finance/receivable/page', { params: { pageSize: 1 } }).catch(() => ({})),
        request.get<any, any>('/finance/payable/page', { params: { pageSize: 1 } }).catch(() => ({})),
      ])
      financeStats.value = [
        { label:'应收记录', value: recRes?.total || 0, color: '#f56c6c' },
        { label:'应付记录', value: payRes?.total || 0, color: '#e6a23c' },
      ]
    }
  } catch { /* ignore */}
}

onMounted(async () => {
  checkUserMenus()
  await loadStats()
})
</script>

<style scoped>
.dashboard { padding: 0; }

.stat-grid { display: flex; gap: 16px; flex-wrap: wrap; margin-bottom: 16px; }
.stat-card {
  flex: 1; min-width: 140px; max-width: 200px;
  background: #f5f7fa; border-radius: 8px; padding: 16px 20px; text-align: center;
}
.stat-card.clickable { cursor: pointer; transition: box-shadow 0.2s; }
.stat-card.clickable:hover { box-shadow: 0 2px 8px rgba(0,0,0,0.1); }
.stat-value { font-size: 28px; font-weight: 700; }
.stat-label { font-size: 13px; color: #909399; margin-top: 4px; }

.quick-links { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-top: 12px; }
.links-label { color: #909399; font-size: 13px; }

.section-card { margin-bottom: 16px; }
.section-title { font-weight: 600; font-size: 14px; }
</style>
