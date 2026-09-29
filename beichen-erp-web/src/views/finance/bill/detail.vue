<script setup lang="ts">
import { ref, computed, onMounted, onActivated } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getBill, getBillItems, auditBill, unAuditBill, cancelBill, type FinanceBill, type FinanceBillItem } from '@/api/finance'
import { BillType, BillTypeLabel, sourceBillTypeLabel, FINANCE_BILL_DIRTY_KEY } from '@/api/enums'
import { DocStatus, DocStatusLabel, DocStatusTag } from '@/api/common'
import request from '@/utils/request'
import PageShell from '@/components/PageShell.vue'

const route = useRoute(); const router = useRouter()
// 用 computed 取路由参数：keep-alive 会复用组件，从账单 A 跳到 B 时 route.params.id 会变
const id = computed(() => Number(route.params.id) || 0)
const loading = ref(false)
const detail = ref<FinanceBill>({})
const items = ref<FinanceBillItem[]>([])

const StatusLabel: Record<string, string> = DocStatusLabel
const StatusTag: Record<string, 'info' | 'success' | 'warning' | 'danger' | 'primary'> = DocStatusTag

/**
 * 「已结算 / 未结算」两个字段的文案（2026-09-29 用户口径：「已收付/未收付」这种**合写词不合理** ——
 * "已收就是已收、已付就是已付"）。账单类型是单据属性 ⇒ 文案跟类型走：
 * **应付账单 = 已付/未付，应收账单 = 已收/未收**（与 payable.vue / receivable.vue 的既有措辞一致）。
 */
const isPayable = computed(() => String(detail.value?.billType) === BillType.PAYABLE)
const paidLabel = computed(() => (isPayable.value ? '已付' : '已收'))
const unpaidLabel = computed(() => (isPayable.value ? '未付' : '未收'))

function fmt(v?: number) { return v == null ? '0.00' : Number(v).toFixed(2) }
function statusLabel(s?: string) { return StatusLabel[s || ''] || s || '-' }
function statusTag(s?: string) { return StatusTag[s || ''] || 'info' }

/**
 * 导出「应收/应付对账单」Excel（2026-09-29 用户口径「**账单详情要添加导出功能**」；当天按"要专业"重做为后端渲染）。
 *
 * <p><b>为什么不再是前端拼表</b>：前端 `xlsx` 是社区版，**写不出字体/边框/底色/对齐，也没有打印设置**
 * ⇒ 只能导出"排得整齐的裸数据"（金额还是文本），拿给往来单位对账不够专业。改为**后端 POI 渲染对账单**：
 * 公司抬头 + 单据标题（含（草稿）/（已作废）标注）+ 单头（往来单位/联系人/电话/制单日期/对账截止）+
 * 明细（**逾期 N 天**、红字未收付）+ **合计行真 SUM 公式** + **人民币大写** + 口径说明 + 签字区，
 * 并设好 A4 横向"一页宽"、跨页重复表头、页脚页码 ⇒ 可直接打印盖章。</p>
 *
 * <p>前端只负责把文件流落盘（与合同导出同一套写法，见 `outsource/order/detail.vue` 的 `exportPdf`）：
 * `responseType: 'blob'`；**失败时全局异常处理器回的是 JSON** ⇒ 按 `blob.type` 判别并弹出 `msg`，
 * 避免把一段错误 JSON 存成 `.xlsx` 交给用户。</p>
 *
 * <p>文件名在本地拼（响应拦截器只回 `res.data`，拿不到 `Content-Disposition`），与后端
 * `BillStatementExcelBuilder.fileName` 同规则：`应付对账单_往来单位_账单号.xlsx`。</p>
 */
const exporting = ref(false)
async function handleExport() {
  exporting.value = true
  try {
    const res: any = await request.get('/finance/bill/' + id.value + '/export', { responseType: 'blob' })
    const blob: Blob = res instanceof Blob
      ? res
      : new Blob([res], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    if (blob.type && blob.type.includes('application/json')) {
      const txt = await blob.text()
      let msg = '导出失败'
      try { msg = JSON.parse(txt)?.msg || msg } catch { /* 非 JSON 就沿用默认文案 */ }
      ElMessage.error(msg)
      return
    }
    const d = detail.value || {}
    // F7-246③（2026-09-29 批 D）：复用枚举 label（原先硬编码"应付/应收"，与 BillTypeLabel 不同源）
    const type = BillTypeLabel[String(d.billType || '')]
      || (String(d.billType) === BillType.PAYABLE ? '应付' : '应收')
    const link = document.createElement('a')
    link.href = URL.createObjectURL(blob)
    link.download = type + '对账单_' + (d.partnerName || '') + '_' + (d.billNo || id.value) + '.xlsx'
    link.click()
    URL.revokeObjectURL(link.href)
    ElMessage.success('对账单已导出')
  } catch (e: any) {
    ElMessage.error(e?.message || '导出失败')
  } finally { exporting.value = false }
}

async function loadDetail() {
  loading.value = true
  try {
    const bill = await getBill(id.value)
    detail.value = bill || {}
    const its = await getBillItems(id.value)
    items.value = Array.isArray(its) ? its : []
  } catch { detail.value = {}; items.value = []
    // F7-246②（2026-09-29 批 D）：原先只有 try/finally ⇒ 加载失败是**未处理 rejection**，且明细不再请求、
    // 页面"半新半旧"且无提示（提示由拦截器给出，这里补状态兜底）
  } finally { loading.value = false }
}

/**
 * 审核 / 反审核 / 作废：操作后刷新本页并置脏标志，
 * 返回列表时列表会重新拉取，不会出现「详情已审核、列表还显示草稿」。
 */
async function afterStatusChange() {
  sessionStorage.setItem(FINANCE_BILL_DIRTY_KEY, '1')
  await loadDetail()
}
async function handleAudit() {
  try { await ElMessageBox.confirm('确认审核该账单？', '审核确认', { type: 'warning' }) } catch { return }
  try { await auditBill(id.value); ElMessage.success('账单已审核'); await afterStatusChange() }
  catch (e: any) { ElMessage.error(e?.message || '审核失败') }
}
async function handleUnAudit() {
  try { await ElMessageBox.confirm('确认反审核该账单？', '反审核确认', { type: 'warning' }) } catch { return }
  try { await unAuditBill(id.value); ElMessage.success('账单已反审核'); await afterStatusChange() }
  catch (e: any) { ElMessage.error(e?.message || '反审核失败') }
}
async function handleCancel() {
  try { await ElMessageBox.confirm('确认作废该账单？', '作废确认', { type: 'warning' }) } catch { return }
  try { await cancelBill(id.value); ElMessage.success('账单已作废'); await afterStatusChange() }
  catch (e: any) { ElMessage.error(e?.message || '作废失败') }
}

onMounted(() => { loadDetail() })
// keep-alive 缓存下再次进入会复用组件，onMounted 不再触发
onActivated(() => { loadDetail() })
</script>

<template>
  <!-- 统一骨架（2026-09-23 全站定稿口径）：页头左端「← 返回」→ 标题(取 meta) → 右端操作 -->
  <PageShell :loading="loading" back-fallback="/finance/bill">
    <template #actions>
      <!-- 2026-09-29 用户口径「账单详情要添加导出功能」：后端渲染专业对账单（页头动作最左，与"改单据"类动作分开） -->
      <!-- F7-246①（2026-09-29 批 D）：四个动作按 finance:bill 显示（后端 /api/finance/bill 前缀守卫同码） -->
      <el-button :icon="'Download'" size="small" :loading="exporting" v-perm="'finance:bill'" @click="handleExport">导出 Excel</el-button>
      <el-button type="success" size="small" v-if="detail.status===DocStatus.DRAFT" v-perm="'finance:bill'" @click="handleAudit">审核</el-button>
      <el-button type="warning" size="small" v-if="detail.status===DocStatus.AUDITED" v-perm="'finance:bill'" @click="handleUnAudit">反审核</el-button>
      <el-button type="danger" size="small" v-if="detail.status!==DocStatus.CANCELLED" v-perm="'finance:bill'" @click="handleCancel">作废</el-button>
    </template>

    <el-card shadow="never">
      <template #header>
        <span style="font-weight:600">账单信息</span>
        <el-tag :type="statusTag(detail.status)" size="small" style="margin-left:8px">{{ statusLabel(detail.status) }}</el-tag>
      </template>

      <el-descriptions :column="3" border>
        <el-descriptions-item label="账单号">{{ detail.billNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="类型">
          <el-tag :type="detail.billType===BillType.PAYABLE?'warning':undefined" size="small">
            {{ BillTypeLabel[String(detail.billType)] || detail.billType || '-' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="往来单位">{{ detail.partnerName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="账期">
          {{ detail.periodStart || '-' }} ~ {{ detail.periodEnd || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="总额">{{ fmt(detail.totalAmount) }}</el-descriptions-item>
        <el-descriptions-item :label="paidLabel">{{ fmt(detail.paidAmount) }}</el-descriptions-item>
        <el-descriptions-item :label="unpaidLabel">
          <span style="color:var(--app-color-danger)">{{ fmt(detail.unpaidAmount) }}</span>
        </el-descriptions-item>
        <!-- 制单人 / 审核人（2026-09-23 用户口径：单据详情显示这两项；账单无审核流程 ⇒ 审核人通常为 —） -->
        <el-descriptions-item label="制单人">{{ detail.createByName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="审核人">{{ detail.auditorName || '—' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="never">
      <template #header><span style="font-weight:600">账单明细</span></template>
      <el-table :data="items" border stripe>
        <el-table-column label="来源类型" width="120">
          <template #default="{ row }">{{ sourceBillTypeLabel(row.sourceBillType) }}</template>
        </el-table-column>
        <el-table-column prop="sourceBillNo" label="来源单号" min-width="160" />
        <el-table-column prop="amount" label="金额" width="120" align="right">
          <template #default="{ row }">{{ fmt(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="paidAmount" :label="paidLabel" width="120" align="right">
          <template #default="{ row }">{{ fmt(row.paidAmount) }}</template>
        </el-table-column>
        <el-table-column prop="unpaidAmount" :label="unpaidLabel" width="120" align="right">
          <template #default="{ row }"><span style="color:var(--app-color-danger)">{{ fmt(row.unpaidAmount) }}</span></template>
        </el-table-column>
        <el-table-column prop="dueDate" label="到期日" width="130" align="center" />
      </el-table>
    </el-card>

    <!-- 原「返回列表」按钮已删除：返回统一由骨架提供 -->
  </PageShell>
</template>
