<script setup lang="ts">
import { ref, computed, onMounted, onActivated } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getBill, getBillItems, getBillProductItems, auditBill, unAuditBill, cancelBill, type FinanceBill, type FinanceBillItem, type BillProductGroup } from '@/api/finance'
import { BillType, BillTypeLabel, sourceBillTypeLabel, FINANCE_BILL_DIRTY_KEY, ProductQualityTypeLabel, ProductQualityTypeTag, SourceBillDetailRoute, billChargeTypeLabel } from '@/api/enums'
import { DocStatus, DocStatusLabel, DocStatusTag } from '@/api/common'
import request from '@/utils/request'
import PageShell from '@/components/PageShell.vue'
import { invalidate } from '@/utils/dataFreshness'

const route = useRoute(); const router = useRouter()
// 用 computed 取路由参数：keep-alive 会复用组件，从账单 A 跳到 B 时 route.params.id 会变
const id = computed(() => Number(route.params.id) || 0)
const loading = ref(false)
const detail = ref<FinanceBill>({})
const items = ref<FinanceBillItem[]>([])
/**
 * 「产品明细」：按来源单分组（2026-10-02 用户要求「对账单要看得出来每一张单卖了什么」）。
 * 与 `items` 的区别：`items` 是台账行（来源单号 + 金额），`groups` 是业务明细行（产品/品质/数量/单价/金额）+ 逐单对账。
 */
const groups = ref<BillProductGroup[]>([])

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

/**
 * 「来源单号」→ 该**业务单详情**的路由（2026-10-02 用户要求「来源单号下钻」）。
 * 类型→路由前缀用 `SourceBillDetailRoute`（与应收/应付台账页同一张表，免得两处口径漂移）；
 * **没配前缀的类型就返回空**，模板渲染成纯文本 —— 不假装可点（缺键时点不动的坑，F7-54 已修过一轮）。
 */
function sourceRoute(row: any) {
  const base = SourceBillDetailRoute[String(row?.sourceBillType || '')]
  return base && row?.sourceId ? base + '/' + row.sourceId : ''
}
function goSourceDetail(row: any) {
  const p = sourceRoute(row)
  if (p) router.push(p)
}
/**
 * 账单明细行 → 该行的产品明细组（按 billItemId 配对）。
 * ⚠️ 明细行（{@code finance_bill_item}）自己的 `sourceId` 是**台账行 id**，不是业务单 id！
 * 拿它拼路由会跳到 `/sale/return/detail/<台账行id>` 这种"能打开但是另一张单"的错页 ⇒ 必须走
 * 产品明细接口给的 `sourceId`（那才是业务单 id）。
 */
const groupByItemId = computed(() => {
  const m: Record<number, any> = {}
  for (const g of (groups.value || [])) if (g?.billItemId != null) m[Number(g.billItemId)] = g
  return m
})
function itemSourceRoute(row: any): string {
  const g = groupByItemId.value[Number(row?.id)]
  return g ? sourceRoute(g) : ''
}
function goItemSource(row: any) {
  const g = groupByItemId.value[Number(row?.id)]
  if (g) goSourceDetail(g)
}

/**
 * 产品明细父表合计：**单金额**与**明细合计**分别求和（不可对账的来源行不计入明细合计，
 * 否则"明细合计"会与"单金额"凭空相等，掩盖差异）。列序：0=展开 1=来源类型 2=来源单号 3=单金额 4=明细合计 5=对账。
 */
function groupsSummary({ columns }: any) {
  const rows = groups.value || []
  return columns.map((_c: any, i: number) => {
    if (i === 0) return '合计'
    if (i === 3) return fmt(rows.reduce((s, g) => s + Number(g.itemAmount || 0), 0))
    if (i === 4) return fmt(rows.reduce((s, g) => s + (g.reconcilable ? Number(g.signedLinesAmount || 0) : 0), 0))
    return ''
  })
}
/**
 * 产品行合计：数量与金额（父行展开的嵌套表用）。列序：0=产品 1=SKU 2=品质 3=数量 4=单价 5=金额。
 * ⚠️ Element Plus 的 summary-method 只传**一个对象** `{ columns, data }`（不是两个参数）——
 * 这里必须解构同一个对象，否则 vue-tsc 报 "Target signature provides too few arguments"。
 */
function linesSummary({ columns, data }: any) {
  const rows = data || []
  return columns.map((_c: any, i: number) => {
    if (i === 0) return '合计'
    if (i === 3) return fmt(rows.reduce((s: number, l: any) => s + Number(l.quantity || 0), 0))
    if (i === 5) return fmt(rows.reduce((s: number, l: any) => s + Number(l.amount || 0), 0))
    return ''
  })
}
/**
 * 收费行的合计（列序与上面的货值表不同）：0=产品 1=SKU 2=品质 3=单据数量 4=收费类型 5=收费说明 6=收费金额。
 * 收费表**没有单价列**（库里没有"本次收费单价"，摆原单价会让人算不对）⇒ 索引与 linesSummary 不能共用。
 */
function chargeLinesSummary({ columns, data }: any) {
  const rows = data || []
  return columns.map((_c: any, i: number) => {
    if (i === 0) return '合计'
    if (i === 3) return fmt(rows.reduce((s: number, l: any) => s + Number(l.quantity || 0), 0))
    if (i === 6) return fmt(rows.reduce((s: number, l: any) => s + Number(l.signedAmount || 0), 0))
    return ''
  })
}

async function loadDetail() {
  loading.value = true
  try {
    const bill = await getBill(id.value)
    detail.value = bill || {}
    const its = await getBillItems(id.value)
    items.value = Array.isArray(its) ? its : []
    const gs = await getBillProductItems(id.value)
    groups.value = Array.isArray(gs) ? gs : []
  } catch { detail.value = {}; items.value = []; groups.value = []
    // F7-246②（2026-09-29 批 D）：原先只有 try/finally ⇒ 加载失败是**未处理 rejection**，且明细不再请求、
    // 页面"半新半旧"且无提示（提示由拦截器给出，这里补状态兜底）
  } finally { loading.value = false }
}

/**
 * 审核 / 反审核 / 作废：操作后刷新本页并置脏标志，
 * 返回列表时列表会重新拉取，不会出现「详情已审核、列表还显示草稿」。
 */
async function afterStatusChange() {
  invalidate('bill')
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
        <!-- 来源单号 → 业务单详情（2026-10-02 用户要求「来源单号下钻」）：业务单 id 取自下方产品明细组
             （明细行自己的 sourceId 是台账行 id，不能用来拼路由） -->
        <el-table-column label="来源单号" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button v-if="itemSourceRoute(row)" type="primary" link
                       @click.stop="goItemSource(row)">{{ row.sourceBillNo }}</el-button>
            <span v-else>{{ row.sourceBillNo }}</span>
          </template>
        </el-table-column>
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

    <!--
      产品明细（2026-10-02 用户要求「对账单要看得出来每一张单卖了什么」）：
      父行 = 一张**来源单**（来源类型/单号/单金额）；展开 = 该单的**产品行**（产品/品质/数量/单价/金额）。
      「对账」列 = 带符号的明细合计是否等于该行金额（退货按负数冲减）—— 不一致显式标红，绝不静默合计。
      来源类型里**没有产品明细**的（各种收费/折损/报损/委外/预收台账…）展开后显示原因说明，不伪造明细。
    -->
    <el-card shadow="never">
      <template #header>
        <span style="font-weight:600">产品明细（每张来源单卖了什么）</span>
        <span class="hint">展开查看产品行；「对账」= 明细合计是否等于该行金额</span>
      </template>
      <el-table :data="groups" border stripe row-key="billItemId" show-summary :summary-method="groupsSummary">
        <el-table-column type="expand" width="48">
          <template #default="{ row }">
            <div class="expand-body">
              <div v-if="!row.lines || !row.lines.length" class="empty-hint">
                {{ row.noDetailReason || '该来源单没有产品明细' }}
              </div>
              <div v-else>
                <!-- ===== 收费类来源（销售退货收费/换货收费/采购退货付费/采购换货付费）=====
                     2026-10-02 第二批：这类行**不显示「单价」** —— 库里没有"本次收费的单价"字段，
                     把该单据行的原单价摆在这里只会让人算不对（2 件 × 100 元，却收 50，很容易被当成算错）。
                     改成直接给收费自己的语义：单据数量（注明是单据业务量）+ **收费类型** + **收费说明** + 收费金额。 -->
                <div v-if="row.lineKind === 'CHARGE'" class="charge-hint">
                  本来源是**收费**口径：金额 = 逐产品收费额（不是货值）。「单据数量」是该产品在这张单据上的数量
                  （仅供对照，不是"收费数量"）；为什么收这笔钱看「收费类型 / 收费说明」
                </div>
                <el-table v-if="row.lineKind === 'CHARGE'" :data="row.lines" size="small" border show-summary
                          :summary-method="chargeLinesSummary">
                  <el-table-column prop="productName" label="产品名称" min-width="130" show-overflow-tooltip />
                  <el-table-column prop="sku" label="SKU" min-width="112" show-overflow-tooltip />
                  <el-table-column label="品质" width="76" align="center">
                    <template #default="{ row: line }">
                      <el-tag :type="ProductQualityTypeTag[String(line.qualityType)] || 'info'" size="small">
                        {{ ProductQualityTypeLabel[String(line.qualityType)] || line.qualityType || '-' }}
                      </el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column label="单据数量" width="84" align="right">
                    <template #default="{ row: line }">
                      <span :title="'该产品在这张单据上的数量（不是收费数量）'">{{ fmt(line.quantity) }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="收费类型" width="96" align="center">
                    <template #default="{ row: line }">
                      <el-tag v-if="billChargeTypeLabel(row.sourceBillType, line.chargeType)" size="small">
                        {{ billChargeTypeLabel(row.sourceBillType, line.chargeType) }}
                      </el-tag>
                      <span v-else class="empty-hint">—</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="收费说明" min-width="150" show-overflow-tooltip>
                    <template #default="{ row: line }">
                      <span v-if="line.chargeReason">{{ line.chargeReason }}</span>
                      <span v-else class="empty-hint">—</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="收费金额" width="112" align="right">
                    <template #default="{ row: line }">{{ fmt(line.signedAmount) }}</template>
                  </el-table-column>
                </el-table>
                <!-- ===== 货值类来源（销售单/退货单/采购单/退货单）：产品/数量/单价/金额 ===== -->
                <el-table v-else :data="row.lines" size="small" border show-summary :summary-method="linesSummary">
                  <el-table-column prop="productName" label="产品名称" min-width="130" show-overflow-tooltip />
                  <el-table-column prop="sku" label="SKU" min-width="112" show-overflow-tooltip />
                  <el-table-column label="品质" width="76" align="center">
                    <template #default="{ row: line }">
                      <el-tag :type="ProductQualityTypeTag[String(line.qualityType)] || 'info'" size="small">
                        {{ ProductQualityTypeLabel[String(line.qualityType)] || line.qualityType || '-' }}
                      </el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column label="数量" width="84" align="right">
                    <template #default="{ row: line }">{{ fmt(line.quantity) }}</template>
                  </el-table-column>
                  <el-table-column label="单价" width="96" align="right">
                    <template #default="{ row: line }">{{ fmt(line.unitPrice) }}</template>
                  </el-table-column>
                  <!-- 金额用 signedAmount：退货行显示负数，与「单金额」「明细合计」同方向（否则一行 200、合计 −200 会让人以为算错） -->
                  <el-table-column label="金额" width="112" align="right">
                    <template #default="{ row: line }">{{ fmt(line.signedAmount) }}</template>
                  </el-table-column>
                </el-table>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="来源类型" width="120">
          <template #default="{ row }">{{ sourceBillTypeLabel(row.sourceBillType) }}</template>
        </el-table-column>
        <!-- 来源单号 → 业务单详情（2026-10-02 用户要求）；没配路由前缀的类型渲染成纯文本，不假装可点 -->
        <el-table-column label="来源单号" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button v-if="sourceRoute(row)" type="primary" link
                       @click.stop="goSourceDetail(row)">{{ row.sourceBillNo }}</el-button>
            <span v-else>{{ row.sourceBillNo }}</span>
          </template>
        </el-table-column>
        <el-table-column label="单金额" width="120" align="right">
          <template #default="{ row }">{{ fmt(row.itemAmount) }}</template>
        </el-table-column>
        <el-table-column label="明细合计" width="120" align="right">
          <template #default="{ row }">
            <span v-if="!row.reconcilable" style="color:#c0c4cc">—</span>
            <span v-else>{{ fmt(row.signedLinesAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="对账" width="96" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.matched === true" type="success" size="small">一致</el-tag>
            <el-tooltip v-else-if="row.matched === false"
                        :content="'明细合计 ' + fmt(row.signedLinesAmount) + ' ≠ 单金额 ' + fmt(row.itemAmount)
                          + '：两张表不一致，请核对来源单'" placement="top">
              <el-tag type="danger" size="small">不符</el-tag>
            </el-tooltip>
            <span v-else style="color:#c0c4cc" title="该来源类型没有产品明细，无法逐单对账">—</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 原「返回列表」按钮已删除：返回统一由骨架提供 -->
  </PageShell>
</template>

<style scoped>
.hint { margin-left: 8px; font-size: var(--app-font-xs); color: var(--app-text-secondary); }
.expand-body { padding: 8px 12px 12px; }
.empty-hint { font-size: var(--app-font-xs); color: var(--el-text-color-secondary); }
/* 收费类来源的提示条：金额口径与"货值"不同，必须在明细上方写清（避免被当成这批货的货值） */
.charge-hint { margin-bottom: 6px; font-size: var(--app-font-xs); color: var(--app-color-warning); }
</style>
