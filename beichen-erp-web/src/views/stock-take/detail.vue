<script setup lang="ts">
// 盘点明细（2026-09-23 用户要求：原 900px「盘点明细」弹框改为独立页面）
// —— 明细走接口；**2026-09-28 起表头也自己查一次主单**（见 loadItems），URL 查询参数只当首帧占位。
//    可编辑仅限草稿（与弹框口径一致）；物料盘点不分品质、无 SKU。
import { computed, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getStockTakeById, getStockTakeItems, saveStockTakeItems, type StockTakeItem } from '@/api/inventory'
import { DocStatusLabel, DocStatusTag } from '@/api/enums'
import { ElMessage } from 'element-plus'
import PageShell from '@/components/PageShell.vue'
import { useUnsavedGuard } from '@/composables/usePageBack'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const items = ref<StockTakeItem[]>([])
/**
 * 单据头（2026-09-28 用户口径「盘点明细页面需要显示盘点人、盘点时间等」）：
 * 口径 = **盘点人取「制单人」**（盘点单就是"谁发起这次盘点"的产物，`create_by_name` 由服务端自动盖章）、
 * **盘点时间取「创建时间」**（录单时刻，含时分秒）；审核人/审核时间同表已有 ⇒ 一并显示。
 * ⚠️ 主单是在明细页里查的（不再只靠 query）⇒ 深链/刷新进来也有值；历史单据这几列为空 ⇒ 显示「—」。
 */
const doc = ref<any>({})
/**
 * 未保存拦截（2026-09-23 统一模板）：盘点行可就地填实盘数量/备注 ⇒ 属"能改数据"，接守卫。
 * ⚠️ 必须写在 items 等状态**之后**（watch 注册时立即求值，放前面会 TDZ 静默失效）。
 */
const { takeBaseline } = useUnsavedGuard(() => ({ items: items.value }))

const id = Number(route.params.id)
// 表头取值：**主单优先、query 兜底**（首帧还没拿到主单时先按列表页塞进来的参数渲染，不闪空白）
const isMaterial = computed(() => route.query.scope === 'MATERIAL')
const status = computed(() => String(doc.value.status || route.query.status || ''))
const warehouseName = computed(() => String(doc.value.warehouseName || route.query.warehouseName || '-'))
const takeNo = computed(() => String(doc.value.takeNo || route.query.takeNo || ''))
const editable = computed(() => status.value === 'DRAFT')
/** 时间戳展示（含时分秒）：后端回 ISO（2026-09-28T16:10:03）⇒ 显示成 "2026-09-28 16:10:03"；空值 — */
function fmtTime(v: any) {
  const s = String(v == null ? '' : v)
  return s ? s.replace('T', ' ').slice(0, 19) : '—'
}

function nameOf(it: StockTakeItem) { return it.productName || it.materialName || '' }
/** 实时差异 = 实盘 − 账面 */
function diffOf(it: StockTakeItem) {
  const a = Number(it.actualQuantity || 0), b = Number(it.bookQuantity || 0)
  return Math.round((a - b) * 10000) / 10000
}
const diffRows = computed(() => items.value.filter(it => diffOf(it) !== 0))

async function loadItems() {
  if (!id) return
  loading.value = true
  try {
    // 主单（单据信息块）+ 明细一起取；任一失败都不该让整页空白
    const [head, lines] = await Promise.all([
      getStockTakeById(id).catch(() => null),
      getStockTakeItems(id).catch(() => [])
    ])
    if (head) doc.value = head
    items.value = (lines || []) as StockTakeItem[]
  } finally { loading.value = false }
}
async function saveItems() {
  try {
    await saveStockTakeItems(id, items.value)
    ElMessage.success('实盘数量已保存')
    takeBaseline()   // 保存成功 ⇒ 重建基线，避免离开时误报"未保存"
    await loadItems()
  } catch { /* 失败提示由 request 拦截器统一给出 */ }
}
onMounted(async () => { await loadItems(); takeBaseline() })
</script>

<template>
  <!-- 统一骨架（2026-09-23 全站定稿口径）：标题/单号交骨架、返回交骨架、保存实盘上移页头 -->
  <PageShell :loading="loading" :title="`盘点明细 - ${warehouseName}`" back-fallback="/inventory/stock-take">
    <template #sub><span v-if="takeNo" style="color:var(--app-text-secondary);font-size:var(--app-font-xs)">（{{ takeNo }}）</span></template>
    <template #actions>
      <el-button v-if="editable" type="primary" @click="saveItems">保存</el-button>
    </template>

    <el-card shadow="never">
      <!-- 卡片页头已删除：标题与单号交骨架，保存实盘上移 #actions -->

      <!-- 单据信息（2026-09-28 用户口径「盘点明细页面需要显示盘点人、盘点时间等」，方案 A）：
           **盘点人 = 制单人**（服务端自动盖章）、**盘点时间 = 创建时间**（录单时刻，含时分秒），
           另补 审核人/审核时间（未审核显示 —）；状态标签此前完全没渲染，一并补上。
           历史单据这几列是后补的（2026-09-23）⇒ 显示「—」属正常。 -->
      <el-descriptions :column="3" border size="small" style="margin-bottom:12px">
        <el-descriptions-item label="状态">
          <el-tag :type="DocStatusTag[status] || 'info'" size="small">{{ DocStatusLabel[status] || status || '—' }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="盘点月份">{{ doc.period || '—' }}</el-descriptions-item>
        <el-descriptions-item label="盘点日期">{{ $fmtDate(doc.takeDate) || '—' }}</el-descriptions-item>
        <el-descriptions-item label="盘点人（制单人）">{{ doc.createByName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="盘点时间">{{ fmtTime(doc.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="审核人">{{ doc.auditorName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="审核时间">{{ fmtTime(doc.auditTime) }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ doc.remark || '—' }}</el-descriptions-item>
      </el-descriptions>

      <div style="margin-bottom:8px;font-size:var(--app-font-base)">
        账面数量来自建单时快照；修改实盘数量后自动算差异。当前差异行：<b :style="{color: diffRows.length ? 'var(--app-color-danger)' : ''}">{{ diffRows.length }}</b>
        <span v-if="!editable" style="margin-left:8px;color:var(--app-text-secondary);font-size:var(--app-font-xs)">（非草稿单据只能查看）</span>
      </div>

      <el-table :data="items" border stripe size="small" max-height="560">
        <!-- SKU 仅成品有；品质仅成品区分（物料库存不分品质，恒为良品） -->
        <el-table-column v-if="!isMaterial" prop="sku" label="SKU" width="130" />
        <el-table-column :label="isMaterial ? '物料' : '名称'" min-width="160" show-overflow-tooltip><template #default="{row}">{{ nameOf(row) }}</template></el-table-column>
        <el-table-column v-if="!isMaterial" prop="qualityType" label="品质" width="80" align="center" />
        <el-table-column prop="unit" label="单位" width="70" align="center" />
        <el-table-column prop="bookQuantity" label="账面数量" width="100" align="right" />
        <el-table-column label="实盘数量" width="140" align="right">
          <template #default="{row}">
            <el-input-number v-model="row.actualQuantity" :min="0" :precision="0" :step="1" controls-position="right" size="small" style="width:100%" :disabled="!editable" />
          </template>
        </el-table-column>
        <el-table-column label="差异" width="100" align="right">
          <template #default="{row}">
            <span :style="{ color: diffOf(row) ? 'var(--app-color-danger)' : '', fontWeight: diffOf(row) ? 'bold' : '' }">{{ diffOf(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="120">
          <template #default="{row}"><el-input v-model="row.remark" size="small" :disabled="!editable" /></template>
        </el-table-column>
      </el-table>

    </el-card>
  </PageShell>
</template>

<style scoped>/* 页头/根容器已统一到全局骨架（PageShell + styles/page.css）；原 .p 局部样式已删除 */</style>
