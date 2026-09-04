<script setup lang="ts">
import { reactive, ref, computed, onMounted, onActivated } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { AfterSaleSourceType } from '@/api/enums'
import { DocStatus, DocStatusLabel, DocStatusTag } from '@/api/common'
import {
  getReturnSort, getReturnSortItems, auditReturnSort, cancelReturnSort,
  type ReturnSortItem
} from '@/api/inventory'

const route = useRoute(); const router = useRouter()
const id = Number(route.params.id)

const loading = ref(false)
const acting = ref(false)
const warehouseOptions = ref<any[]>([])

const head = reactive({
  code: '',
  warehouseId: undefined as number | undefined,
  sortDate: '',
  targetWarehouseA: undefined as number | undefined,
  targetWarehouseB: undefined as number | undefined,
  targetWarehouseC: undefined as number | undefined,
  targetWarehouseDefect: undefined as number | undefined,
  lossAmount: 0,
  lossRemark: '',
  remark: '',
  status: DocStatus.DRAFT as string,
  createTime: ''
})

const items = ref<ReturnSortItem[]>([])

function warehouseName(wid?: number) {
  const w = warehouseOptions.value.find((x: any) => x.id === wid)
  return w ? (w.warehouseName || w.name) : '—'
}

const isDraft = computed(() => String(head.status) === DocStatus.DRAFT)
const isAudited = computed(() => String(head.status) === DocStatus.AUDITED)

async function loadWarehouses() {
  try { const res: any = await request.get('/warehouse/page', { params: { pageSize: 500, warehouseCategory: 'INVENTORY' } }); warehouseOptions.value = res?.records || [] } catch { warehouseOptions.value = [] }
}

async function loadDetail() {
  loading.value = true
  try {
    const io = await getReturnSort(id)
    Object.assign(head, {
      code: io.code, warehouseId: io.warehouseId, sortDate: io.sortDate,
      targetWarehouseA: io.targetWarehouseA, targetWarehouseB: io.targetWarehouseB,
      targetWarehouseC: io.targetWarehouseC, targetWarehouseDefect: io.targetWarehouseDefect,
      lossAmount: Number(io.lossAmount || 0), lossRemark: io.lossRemark || '',
      remark: io.remark, status: io.status, createTime: io.createTime
    })
    items.value = await getReturnSortItems(id) || []
  } finally { loading.value = false }
}

function itemSum(it: any) { return Number(it.qtyA || 0) + Number(it.qtyB || 0) + Number(it.qtyC || 0) + Number(it.qtyDefect || 0) }

function goWarehouse(id?: number | null) { if (id) router.push(`/inventory/warehouse/detail/${id}`) }
function goProduct(id?: number) { if (id) router.push(`/product/detail/${id}`) }
/** 来源单据按类型跳转：换货单 → 换货详情；销售退单 → 退单详情 */
function goSource(row: any) {
  if (!row.sourceId) return
  if (row.sourceType === AfterSaleSourceType.SALE_EXCHANGE) router.push(`/sale/exchange/detail/${row.sourceId}`)
  else router.push(`/sale/return/detail/${row.sourceId}`)
}

async function doAudit() {
  const loss = Number(head.lossAmount) || 0
  const lossTip = loss > 0 ? `\n并将生成一条向客户收取的折损应收 ${loss.toFixed(2)} 元（台账单号 ${head.code}-LOSS）。` : ''
  try {
    await ElMessageBox.confirm(`确认审核单号「${head.code}」？审核后将从售后仓扣减待分类品并分品质入库（A/B/C 入成品仓）。${lossTip}`, '审核确认', { type: 'warning' })
  } catch { return }
  acting.value = true
  try {
    await auditReturnSort(id)
    ElMessage.success('审核成功')
    loadDetail()
  } finally { acting.value = false }
}

async function doUnAudit() {
  try {
    await ElMessageBox.confirm(`确认反审核单号「${head.code}」？反审核后将逆向恢复库存。`, '反审核确认', { type: 'warning' })
  } catch { return }
  acting.value = true
  try {
    await cancelReturnSort(id)
    ElMessage.success('已反审核')
    loadDetail()
  } finally { acting.value = false }
}

onMounted(() => { loadWarehouses(); loadDetail() })
// keep-alive 缓存下再次进入会复用组件、onMounted 不再触发
onActivated(() => { loadDetail() })
</script>

<template>
  <div class="app-container" v-loading="loading">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>退货整理详情 — {{ head.code }}</span>
          <el-tag :type="DocStatusTag[String(head.status)] || 'info'">{{ DocStatusLabel[String(head.status)] || head.status }}</el-tag>
        </div>
      </template>

      <el-descriptions :column="3" border>
        <el-descriptions-item label="单号">{{ head.code }}</el-descriptions-item>
        <el-descriptions-item label="源仓库">
          <el-button v-if="head.warehouseId" type="primary" link @click="goWarehouse(head.warehouseId)">{{ warehouseName(head.warehouseId) }}</el-button>
          <span v-else>—</span>
        </el-descriptions-item>
        <el-descriptions-item label="整理日期">{{ head.sortDate || '—' }}</el-descriptions-item>
        <el-descriptions-item label="A规入库仓">
          <el-button v-if="head.targetWarehouseA" type="primary" link @click="goWarehouse(head.targetWarehouseA)">{{ warehouseName(head.targetWarehouseA) }}</el-button>
          <span v-else>—</span>
        </el-descriptions-item>
        <el-descriptions-item label="B规入库仓">
          <el-button v-if="head.targetWarehouseB" type="primary" link @click="goWarehouse(head.targetWarehouseB)">{{ warehouseName(head.targetWarehouseB) }}</el-button>
          <span v-else>—</span>
        </el-descriptions-item>
        <el-descriptions-item label="C规入库仓">
          <el-button v-if="head.targetWarehouseC" type="primary" link @click="goWarehouse(head.targetWarehouseC)">{{ warehouseName(head.targetWarehouseC) }}</el-button>
          <span v-else>—</span>
        </el-descriptions-item>
        <el-descriptions-item label="不良入库仓">
          <el-button v-if="head.targetWarehouseDefect" type="primary" link @click="goWarehouse(head.targetWarehouseDefect)">{{ warehouseName(head.targetWarehouseDefect) }}</el-button>
          <span v-else>—</span>
        </el-descriptions-item>
        <el-descriptions-item label="折损收款">
          <span v-if="Number(head.lossAmount) > 0" style="color:#e6a23c;font-weight:600">{{ Number(head.lossAmount).toFixed(2) }}</span>
          <span v-else>—</span>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ head.createTime || '—' }}</el-descriptions-item>
        <el-descriptions-item label="折损说明" :span="2">{{ head.lossRemark || '—' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="3">{{ head.remark || '—' }}</el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">整理明细（待整理数量 = A + B + C + 不良）</el-divider>
      <el-table :data="items" border>
        <el-table-column label="来源单据" width="170" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button v-if="row.sourceId" type="primary" link @click="goSource(row)">{{ row.sourceCode || '-' }}</el-button>
            <span v-else>{{ row.sourceCode || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="SKU" width="130">
          <template #default="{ row }">
            <el-button v-if="row.productId" type="primary" link @click="goProduct(row.productId)">{{ row.sku || '—' }}</el-button>
            <span v-else>{{ row.sku || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="productName" label="产品" min-width="160" show-overflow-tooltip />
        <el-table-column prop="totalQuantity" label="待整理数量" width="110" align="center">
          <template #default="{ row }"><b>{{ row.totalQuantity }}</b></template>
        </el-table-column>
        <el-table-column label="A数量" width="100" align="right">
          <template #default="{ row }">{{ row.qtyA }}</template>
        </el-table-column>
        <el-table-column label="B数量" width="100" align="right">
          <template #default="{ row }">{{ row.qtyB }}</template>
        </el-table-column>
        <el-table-column label="C数量" width="100" align="right">
          <template #default="{ row }">{{ row.qtyC }}</template>
        </el-table-column>
        <el-table-column label="不良数量" width="100" align="right">
          <template #default="{ row }">{{ row.qtyDefect }}</template>
        </el-table-column>
        <el-table-column label="校验" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="itemSum(row) === Number(row.totalQuantity) ? 'success' : 'danger'" size="small">
              {{ itemSum(row) === Number(row.totalQuantity) ? '✓ 相等' : `合计${itemSum(row)}` }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>

      <div class="footer">
        <el-button @click="router.back()">返回</el-button>
        <el-button v-if="isDraft" type="success" :loading="acting" @click="doAudit">审核</el-button>
        <el-button v-if="isAudited" type="warning" :loading="acting" @click="doUnAudit">反审核</el-button>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.card-header { display: flex; align-items: center; justify-content: space-between; }
.footer { margin-top: 20px; text-align: right; }
</style>
