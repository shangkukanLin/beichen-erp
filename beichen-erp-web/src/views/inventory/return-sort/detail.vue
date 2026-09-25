<script setup lang="ts">
import { reactive, ref, computed, onMounted, onActivated } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { AfterSaleSourceType, INVENTORY_RETURN_SORT_DIRTY_KEY } from '@/api/enums'
import { DocStatus, DocStatusLabel, DocStatusTag } from '@/api/common'
import PageShell from '@/components/PageShell.vue'
import {
  getReturnSort, getReturnSortItems, auditReturnSort, cancelReturnSort, updateReturnSort,
  type ReturnSortItem
} from '@/api/inventory'

/**
 * 退货整理详情（2026-09-24 用户口径：草稿态就地可编辑）
 *
 * 结构对齐其它单据详情：`head` = 只读快照，`form`/`items` = 可编辑副本（草稿态才建）。
 * 草稿态可改：**整理日期 / 备注 / 每行的 A·B·C·不良 数量**（待整理数量是来源批次的既定值，不可改），
 * 校验与 payload 与 form.vue 一致（每行 Σ(A,B,C,不良) 必须等于待整理数量；后端 update 只允许草稿）。
 * 已审核/已作废分支保留原只读展示。
 */
const route = useRoute(); const router = useRouter()
const id = Number(route.params.id)

const loading = ref(false)
const acting = ref(false)
const saving = ref(false)
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
/** 可编辑副本（白名单：单号/源仓库/整理人/审核人 不回传，由后端按主键取） */
const form = reactive({ sortDate: '', remark: '' })

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
    const its = await getReturnSortItems(id) || []
    items.value = its
    if (String(head.status) === DocStatus.DRAFT) {
      // 草稿：建可编辑副本（数量显式转 Number，避免输入框拿到字符串）
      form.sortDate = head.sortDate || ''
      form.remark = head.remark || ''
      items.value = (its as any[]).map((it: any) => ({
        ...it,
        totalQuantity: Number(it.totalQuantity),
        qtyA: Number(it.qtyA || 0), qtyB: Number(it.qtyB || 0),
        qtyC: Number(it.qtyC || 0), qtyDefect: Number(it.qtyDefect || 0),
      })) as ReturnSortItem[]
    }
  } finally { loading.value = false }
}

function itemSum(it: any) { return Number(it.qtyA || 0) + Number(it.qtyB || 0) + Number(it.qtyC || 0) + Number(it.qtyDefect || 0) }

/** 保存（校验与 payload 与 form.vue 一致；后端 update 自带「只有草稿可编辑」守卫） */
async function doSave() {
  if (items.value.length === 0) { ElMessage.warning('本单没有明细，无法保存'); return }
  for (const it of items.value as any[]) {
    if (!it.totalQuantity || Number(it.totalQuantity) <= 0) {
      ElMessage.warning(`产品「${it.productName || it.productId}」待整理数量必须大于0`); return
    }
    if (itemSum(it) !== Number(it.totalQuantity)) {
      ElMessage.warning(`产品「${it.productName || it.productId}」分选数量之和(${itemSum(it)})必须等于待整理数量(${it.totalQuantity})`)
      return
    }
  }
  saving.value = true
  try {
    await updateReturnSort(id, {
      id,
      warehouseId: head.warehouseId,
      sortDate: form.sortDate,
      remark: form.remark,
      items: (items.value as any[]).map((it: any) => ({
        pendingId: it.pendingId,
        sourceId: it.sourceId,
        sourceType: it.sourceType,
        productId: it.productId,
        productName: it.productName,
        sku: it.sku,
        totalQuantity: Number(it.totalQuantity),
        qtyA: Number(it.qtyA || 0),
        qtyB: Number(it.qtyB || 0),
        qtyC: Number(it.qtyC || 0),
        qtyDefect: Number(it.qtyDefect || 0),
      }))
    })
    ElMessage.success('已保存')
    sessionStorage.setItem(INVENTORY_RETURN_SORT_DIRTY_KEY, '1')
    await loadDetail()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally { saving.value = false }
}

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
    ElMessage.success('已审核')
    sessionStorage.setItem(INVENTORY_RETURN_SORT_DIRTY_KEY, '1')
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
    sessionStorage.setItem(INVENTORY_RETURN_SORT_DIRTY_KEY, '1')
    loadDetail()
  } finally { acting.value = false }
}

onMounted(() => { loadWarehouses(); loadDetail() })
// keep-alive 缓存下再次进入会复用组件、onMounted 不再触发
onActivated(() => { loadDetail() })
</script>

<template>
  <!-- 统一骨架（2026-09-23 全站最终口径）：页头左端=返回 → 标题 → 右端=操作 -->
  <PageShell :title="`退货整理详情${head.code ? ' — ' + head.code : ''}`" :loading="loading" back-fallback="/inventory/return-sort">
    <template #sub>
      <el-tag :type="DocStatusTag[String(head.status)] || 'info'">{{ DocStatusLabel[String(head.status)] || head.status }}</el-tag>
    </template>
    <template #actions>
      <!-- 草稿：保存(主) + 审核（2026-09-24：草稿态就地编辑，不再跳独立编辑页） -->
      <el-button v-if="isDraft" type="primary" :loading="saving" @click="doSave">保存</el-button>
      <el-button v-if="isDraft" type="success" :loading="acting" @click="doAudit">审核</el-button>
      <el-button v-if="isAudited" type="warning" :loading="acting" @click="doUnAudit">反审核</el-button>
    </template>

    <el-card shadow="never">

      <!-- ============ 草稿：可编辑（整理日期/备注 + 每行 A/B/C/不良 数量） ============ -->
      <el-form v-if="isDraft" :model="form" label-width="var(--app-label-width)" class="head-form">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="单号">{{ head.code }}</el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="源仓库">
              <el-button v-if="head.warehouseId" type="primary" link @click="goWarehouse(head.warehouseId)">{{ warehouseName(head.warehouseId) }}</el-button>
              <span v-else>—</span>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="整理日期">
              <el-date-picker v-model="form.sortDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <!-- 整理人由服务端按登录用户写入 ⇒ 只读 -->
            <el-form-item label="整理人">
              <span v-if="head.sortUserName" style="font-weight:600">{{ head.sortUserName }}</span>
              <span v-else style="color:#999">—（历史单据未记录）</span>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="分选后入库仓">
              <span>{{ warehouseName(head.warehouseId) }}</span>
              <span style="margin-left:6px;color:var(--app-text-secondary);font-size:var(--app-font-xs)">= 源仓库（A/B/C/不良 均回源仓、按品质区分）</span>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="制单人">{{ head.createByName || '—' }}</el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注"><el-input v-model="form.remark" placeholder="选填" /></el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <el-descriptions v-else :column="3" border>
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
        <!-- 2026-09-26 B4 顺带修：本表明细列宽合计 1080px ＞ 容器 963px（专属守卫
             verify-returnsort-list-fit.ps1 的「详情页」段一直 FAIL，横向溢出 117px）。
             按"标识列给足、数量列够用"重排：来源单据 170→150、SKU 130→110、产品 min160→130、
             待整理数量 110→96、A/B/C/不良 各 100→88、校验 110→96 ⇒ 合计 **934 ≤ 963** ✓ -->
        <el-table-column label="来源单据" width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button v-if="row.sourceId" type="primary" link @click="goSource(row)">{{ row.sourceCode || '-' }}</el-button>
            <span v-else>{{ row.sourceCode || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="SKU" width="110">
          <template #default="{ row }">
            <el-button v-if="row.productId" type="primary" link @click="goProduct(row.productId)">{{ row.sku || '—' }}</el-button>
            <span v-else>{{ row.sku || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="productName" label="产品" min-width="130" show-overflow-tooltip />
        <el-table-column prop="totalQuantity" label="待整理数量" width="96" align="center">
          <template #default="{ row }"><b>{{ row.totalQuantity }}</b></template>
        </el-table-column>
        <!-- 草稿：四列数量可就地修改（数字列 size=small、:controls=false ⇒ 窄而稳定） -->
        <el-table-column label="A数量" width="92" align="right">
          <template #default="{ row }">
            <el-input-number v-if="isDraft" v-model="row.qtyA" :min="0" :precision="0" size="small" :controls="false" style="width:100%" />
            <span v-else>{{ row.qtyA }}</span>
          </template>
        </el-table-column>
        <el-table-column label="B数量" width="92" align="right">
          <template #default="{ row }">
            <el-input-number v-if="isDraft" v-model="row.qtyB" :min="0" :precision="0" size="small" :controls="false" style="width:100%" />
            <span v-else>{{ row.qtyB }}</span>
          </template>
        </el-table-column>
        <el-table-column label="C数量" width="92" align="right">
          <template #default="{ row }">
            <el-input-number v-if="isDraft" v-model="row.qtyC" :min="0" :precision="0" size="small" :controls="false" style="width:100%" />
            <span v-else>{{ row.qtyC }}</span>
          </template>
        </el-table-column>
        <el-table-column label="不良数量" width="92" align="right">
          <template #default="{ row }">
            <el-input-number v-if="isDraft" v-model="row.qtyDefect" :min="0" :precision="0" size="small" :controls="false" style="width:100%" />
            <span v-else>{{ row.qtyDefect }}</span>
          </template>
        </el-table-column>
        <el-table-column label="校验" width="96" align="center">
          <template #default="{ row }">
            <el-tag :type="itemSum(row) === Number(row.totalQuantity) ? 'success' : 'danger'" size="small">
              {{ itemSum(row) === Number(row.totalQuantity) ? '✓ 相等' : `合计${itemSum(row)}` }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>

    </el-card>
  </PageShell>
</template>

<style scoped>
/* 页头/操作区已统一到全局骨架（PageShell + styles/page.css） */
.head-form :deep(.el-form-item) { margin-bottom: 8px; }
</style>
