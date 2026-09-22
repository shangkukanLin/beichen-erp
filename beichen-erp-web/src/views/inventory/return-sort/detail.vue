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
  remark: '',
  status: DocStatus.DRAFT as string,
  // 整理人：谁操作的就是谁整理的（服务端按登录用户写入，前端只读展示）
  sortUserName: '',
  createTime: '',
  // 制单人 / 审核人（2026-09-23 单据详情口径；历史单据无记录显示 —）
  createByName: '',
  auditorName: ''
})

const items = ref<ReturnSortItem[]>([])

function warehouseName(wid?: number) {
  const w = warehouseOptions.value.find((x: any) => x.id === wid)
  return w ? (w.warehouseName || w.name) : '—'
}

const isDraft = computed(() => String(head.status) === DocStatus.DRAFT)
const isAudited = computed(() => String(head.status) === DocStatus.AUDITED)

/**
 * 是否"分选后回到源仓库"（2026-09-22 用户口径：默认回源仓，不再单独选/展示 4 个目标仓）。
 * - 新单：目标仓由服务端回填 = 源仓库（或前端未传 ⇒ 空值，审核时同样回填源仓）⇒ true
 * - 历史单：曾显式指定过与源仓不同的目标仓 ⇒ false，此时逐一列出，避免信息失真
 */
const targetsAllSource = computed(() => {
  const src = head.warehouseId
  const list = [head.targetWarehouseA, head.targetWarehouseB, head.targetWarehouseC, head.targetWarehouseDefect]
  return list.every((t) => t == null || t === src)
})

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
      remark: io.remark, status: io.status, createTime: io.createTime,
      sortUserName: io.sortUserName || '',
      createByName: io.createByName || '', auditorName: io.auditorName || ''
    })
    items.value = await getReturnSortItems(id) || []
  } finally { loading.value = false }
}

function itemSum(it: any) { return Number(it.qtyA || 0) + Number(it.qtyB || 0) + Number(it.qtyC || 0) + Number(it.qtyDefect || 0) }

function goWarehouse(id?: number | null) { if (id) router.push(`/inventory/warehouse/detail/${id}`) }
function goProduct(id?: number) { if (id) router.push(`/product/detail/${id}`) }
/** 来源单据按类型跳转：换货单 → 换货详情；销售退货单 → 退货单详情 */
function goSource(row: any) {
  if (!row.sourceId) return
  if (row.sourceType === AfterSaleSourceType.SALE_EXCHANGE) router.push(`/sale/exchange/detail/${row.sourceId}`)
  else router.push(`/sale/return/detail/${row.sourceId}`)
}

async function doAudit() {
  try {
    await ElMessageBox.confirm(`确认审核单号「${head.code}」？审核后将从成品仓扣减待整理品并分品质入库（A/B/C/不良 均入成品仓，按品质区分）。`, '审核确认', { type: 'warning' })
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
        <!-- 整理人（2026-09-22 用户要求）：谁操作的就是谁整理的 —— 新建/批量生成草稿/编辑时由服务端按登录用户写入 -->
        <el-descriptions-item label="整理人">
          <span v-if="head.sortUserName" style="font-weight:600">{{ head.sortUserName }}</span>
          <span v-else style="color:#999">—（历史单据未记录）</span>
        </el-descriptions-item>
        <!-- 入库仓（2026-09-22 用户口径：默认回到源仓库 ⇒ 不再展示 A/B/C/不良 4 个目标仓）。
             只有历史单（曾显式指定过与源仓不同的目标仓）才逐一列出，避免信息失真。 -->
        <el-descriptions-item label="分选后入库仓" :span="2">
          <template v-if="targetsAllSource">
            <el-button v-if="head.warehouseId" type="primary" link @click="goWarehouse(head.warehouseId)">{{ warehouseName(head.warehouseId) }}</el-button>
            <span v-else>—</span>
            <span style="margin-left:6px;color:var(--app-text-secondary);font-size:var(--app-font-xs)">= 源仓库（A/B/C/不良 均回源仓、按品质区分）</span>
          </template>
          <template v-else>
            <span style="font-size:var(--app-font-xs)">
              A {{ warehouseName(head.targetWarehouseA) }} · B {{ warehouseName(head.targetWarehouseB) }} ·
              C {{ warehouseName(head.targetWarehouseC) }} · 不良 {{ warehouseName(head.targetWarehouseDefect) }}
            </span>
            <span style="margin-left:6px;color:var(--app-text-secondary);font-size:var(--app-font-xs)">（历史单的显式目标仓）</span>
          </template>
        </el-descriptions-item>
        <!-- 制单人 / 审核人（2026-09-23 用户口径：单据详情显示这两项；历史单据无记录显示 —） -->
        <el-descriptions-item label="制单人">{{ head.createByName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="审核人">{{ head.auditorName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ head.createTime || '—' }}</el-descriptions-item>
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
