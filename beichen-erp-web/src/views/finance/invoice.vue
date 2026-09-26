<script setup lang="ts">
import { localDate } from '@/utils/date'
import { reactive, ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getInvoicePage, createInvoice, updateInvoice, cancelInvoice, type FinanceInvoice } from '@/api/finance'
// 2026-09-20（F7-165）：4 组映射原先硬编码在本页，现集中到 @/api/enums（新增票种/状态只改一处）；
// 用**别名导入**以保持下方所有用法不变（最小改动）。
import {
  InvoiceKindLabel as INVOICE_KIND_LABELS,
  InvoiceDirectionLabel as DIRECTION_LABEL,
  InvoiceStatusLabel as STATUS_LABEL,
  InvoiceStatusTag as STATUS_TAG,
} from '@/api/enums'

// 发票管理（税务口径）：销项=我方开出的发票，进项=我方收到的发票。登记即生效，作废仅标记可重新登记同号。
// 发票类型存枚举 code，显示用 label 映射
const INVOICE_KINDS = Object.keys(INVOICE_KIND_LABELS)

/**
 * 列表用**短名**（2026-09-26 B6）：枚举全称「增值税专用发票」7 字实测需 132px，
 * 而本页 12 列已挤满 956px ⇒ 列表显示 专票/普票/电子专票/电子普票（需 ~74px，完整显示）；
 * 登记/编辑弹窗与筛选项仍用全称（`INVOICE_KIND_LABELS`），信息不丢。
 */
const KIND_SHORT: Record<string, string> = {
  special: '专票', normal: '普票', e_special: '电子专票', e_normal: '电子普票',
}

const query = reactive({ direction: '', status: '', keyword: '', dateRange: null as [string, string] | null })
const page = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const loading = ref(false)
const data = ref<FinanceInvoice[]>([])
const dialog = ref(false)
const dialogTitle = ref('登记发票')
const form = reactive<FinanceInvoice>({})
// 输入模式：total=填价税合计（自动拆税），net=填不含税金额（自动算税额与价税合计）
const inputMode = ref<'total' | 'net'>('total')

const emptyForm = (): FinanceInvoice => ({
  id: undefined, invoiceNo: '', direction: 'SALE', invoiceKind: 'special',
  invoiceDate: localDate(), partnerName: '',
  amount: undefined, taxRate: 13, taxAmount: undefined, totalAmount: undefined,
  sourceBillCode: '', remark: '',
})

async function loadData() {
  loading.value = true
  try {
    const p: any = { pageNum: page.pageNum, pageSize: page.pageSize }
    if (query.direction) p.direction = query.direction
    if (query.status) p.status = query.status
    if (query.keyword) p.keyword = query.keyword
    if (query.dateRange && query.dateRange[0]) { p.start = query.dateRange[0]; p.end = query.dateRange[1] }
    const res = await getInvoicePage(p)
    data.value = res?.records || []; page.total = res?.total || 0
  } catch { data.value = [] } finally { loading.value = false }
}

// 金额联动预览（后端同样兜底计算）：total 模式=价税合计×税率/(100+税率)；net 模式反向
const preview = computed(() => {
  const rate = Number(form.taxRate) || 0
  if (inputMode.value === 'total') {
    const t = Number(form.totalAmount) || 0
    const tax = Math.round(t * rate / (100 + rate) * 100) / 100
    return { amount: Math.round((t - tax) * 100) / 100, tax, total: t }
  }
  const a = Number(form.amount) || 0
  const total = Math.round(a * (100 + rate) / 100 * 100) / 100
  return { amount: a, tax: Math.round((total - a) * 100) / 100, total }
})

function handleAdd() { Object.assign(form, emptyForm()); inputMode.value = 'total'; dialogTitle.value = '登记发票'; dialog.value = true }
function handleEdit(row: FinanceInvoice) {
  Object.assign(form, JSON.parse(JSON.stringify(row)))
  inputMode.value = 'total'
  dialogTitle.value = '编辑发票'; dialog.value = true
}
async function save() {
  if (!form.invoiceNo) { ElMessage.warning('请填写发票号码'); return }
  if (inputMode.value === 'total' && (!form.totalAmount || form.totalAmount <= 0)) { ElMessage.warning('请填写价税合计'); return }
  if (inputMode.value === 'net' && (!form.amount || form.amount <= 0)) { ElMessage.warning('请填写不含税金额'); return }
  // 按输入模式清掉另一侧金额，后端统一联动计算
  const payload: any = { ...form }
  if (inputMode.value === 'total') payload.amount = undefined
  else payload.totalAmount = undefined
  try {
    if (form.id) { await updateInvoice(payload); ElMessage.success('已更新') }
    else { await createInvoice(payload); ElMessage.success('已登记') }
    dialog.value = false; loadData()
  } catch {}
}
async function cancel(row: FinanceInvoice) {
  try { await ElMessageBox.confirm(`确认作废发票 ${row.invoiceNo}？作废后该号码可重新登记。`, '作废确认', { type: 'warning' }) } catch { return }
  try { await cancelInvoice(row.id!); ElMessage.success('已作废'); loadData() } catch {}
}
function fmt(v?: number) { return v == null ? '0.00' : Number(v).toFixed(2) }
function fmtDate(v?: string) { return v ? String(v).slice(0, 10) : '' }
onMounted(() => { loadData() })
</script>
<template>
  <div class="page-list">
    <el-card shadow="never" class="query-card">
      <div class="query-bar">
        <el-form :inline="true" :model="query" class="qf">
          <el-form-item label="方向">
            <el-select v-model="query.direction" placeholder="全部" clearable style="width:100px">
              <el-option label="销项" value="SALE"/><el-option label="进项" value="PURCHASE"/>
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="query.status" placeholder="全部" clearable style="width:110px">
              <el-option v-for="(label, code) in STATUS_LABEL" :key="code" :label="label" :value="code"/>
            </el-select>
          </el-form-item>
          <el-form-item label="关键字">
            <el-input v-model="query.keyword" placeholder="发票号/对方单位/关联单号" clearable style="width:200px" @keyup.enter="page.pageNum=1;loadData()"/>
          </el-form-item>
          <el-form-item label="开票日期">
            <el-date-picker v-model="query.dateRange" type="daterange" value-format="YYYY-MM-DD"
              start-placeholder="开始" end-placeholder="结束" style="width:240px" :clearable="true"/>
          </el-form-item>
        </el-form>
        <div class="toolbar">
          <el-button type="primary" :icon="'Search'" @click="page.pageNum=1;loadData()">查询</el-button>
          <el-button :icon="'Refresh'" @click="query.direction='';query.status='';query.keyword='';query.dateRange=null;page.pageNum=1;loadData()">重置</el-button>
          <el-button type="success" :icon="'Plus'" @click="handleAdd">登记发票</el-button>
        </div>
      </div>
    </el-card>
    <el-card shadow="never" class="table-card">
      <!-- 2026-09-24（用户规则：所有列表一行显示完、不左右滑动）：12 列、原列宽合计 1415px > 内容区 971px
           ⇒ 横向滚动 444px。
           2026-09-26 B6（用户口径「数据显示完整」，实测驱动）：
           ①发票号码 96→**140**（实测需 140，原 4/4 行全被截断；带 tooltip 兜 20 位全电发票号）；
           ②方向 56→**58**、状态 64→**70**：两列 tag **补 size="small"**（与全站一致；原默认尺寸 tag 比 small 宽 ~24px）；
           ③发票类型 84→**78**：列表内改用**短名**（专票/普票/电子专票/电子普票，登记/详情弹窗仍用全称
              「增值税专用发票」）⇒ 实测需 132 降到 ~74；
           ④开票日期 84→**106**（实测需 106，原先全被截断）；
           ⑤对方单位 min84→72（名称列，长度无上界 ⇒ 白名单 + tooltip）；
           ⑥金额列按实测微调：不含税 82→76、税率 52→44（只显示 "13%"）、税额 80→74、价税合计 84→80、
              关联单号 84→74、操作 96→84。
           合计 = 140+58+78+106+72+76+44+74+80+74+70+84 = **956** ✓（本页 4 行无纵向滚动条） -->
      <el-table v-loading="loading" :data="data" border stripe>
        <el-table-column prop="invoiceNo" label="发票号码" width="140" show-overflow-tooltip/>
        <el-table-column label="方向" width="58" align="center">
          <template #default="{row}"><el-tag :type="row.direction==='SALE'?'success':'warning'" size="small">{{ DIRECTION_LABEL[row.direction] || row.direction }}</el-tag></template>
        </el-table-column>
        <el-table-column label="发票类型" width="78" show-overflow-tooltip><template #default="{row}">{{ KIND_SHORT[row.invoiceKind] || INVOICE_KIND_LABELS[row.invoiceKind] || row.invoiceKind }}</template></el-table-column>
        <el-table-column label="开票日期" width="106"><template #default="{row}">{{ fmtDate(row.invoiceDate) }}</template></el-table-column>
        <!-- 2026-09-26 B10：列名「对方单位」4 字需 90px（本列 72）⇒ 改短名「对方」（方向列已标明是购买方/销售方） -->
        <el-table-column prop="partnerName" label="对方" min-width="62" show-overflow-tooltip/>
        <!-- 2026-09-26 B10：列名「不含税金额」5 字需 104px ⇒ 改短名「不含税」（3 字需 76）并把 74→76 -->
        <el-table-column prop="amount" label="不含税" width="76" align="right" show-overflow-tooltip><template #default="{row}">{{ fmt(row.amount) }}</template></el-table-column>
        <!-- 2026-09-26 B6：表头「税率」2 字需 28px，列宽 44 时内容框仅 27px ⇒ 差 1px 被省略成「税…」。
             44→**48**（28 + 内边距 16 + 边框 1 + 3 余量）；正文最长「13%」需 43px 也在 48 内 ✓。 -->
        <el-table-column label="税率" width="48" align="right"><template #default="{row}">{{ Number(row.taxRate)||0 }}%</template></el-table-column>
        <el-table-column label="税额" width="72" align="right" show-overflow-tooltip><template #default="{row}"><span style="color:var(--app-color-primary)">{{ fmt(row.taxAmount) }}</span></template></el-table-column>
        <el-table-column prop="totalAmount" label="价税合计" width="78" align="right" show-overflow-tooltip><template #default="{row}">{{ fmt(row.totalAmount) }}</template></el-table-column>
        <!-- 2026-09-26 B6：表头「关联单号」4 字需 56px，列宽 74 时只剩 1px 余量（字体稍有差异即省略）⇒ 74→**76** -->
        <el-table-column prop="sourceBillCode" label="关联单号" width="76" show-overflow-tooltip/>
        <el-table-column label="状态" width="70" align="center"><template #default="{row}"><el-tag :type="(STATUS_TAG[row.status]||'info') as any" size="small">{{ STATUS_LABEL[row.status] || row.status }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="106" align="center" fixed="right">
          <template #default="{row}">
            <template v-if="row.status==='REGISTERED'">
              <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
              <el-button type="danger" link @click="cancel(row)">作废</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination"><el-pagination v-model:current-page="page.pageNum" v-model:page-size="page.pageSize" :page-sizes="[10,20,50,100]" :total="page.total" layout="total,sizes,prev,pager,next,jumper" background @size-change="loadData" @current-change="loadData"/></div>
    </el-card>
    <el-dialog v-model="dialog" :title="dialogTitle" width="var(--app-dialog-sm)" :close-on-click-modal="false">
      <el-form :model="form" label-width="90px">
        <el-form-item label="方向">
          <el-radio-group v-model="form.direction" :disabled="!!form.id">
            <el-radio value="SALE">销项（我方开出）</el-radio>
            <el-radio value="PURCHASE">进项（我方收到）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="发票号码" required><el-input v-model="form.invoiceNo" placeholder="请输入发票号码"/></el-form-item>
        <el-form-item label="发票类型">
          <el-select v-model="form.invoiceKind" style="width:100%"><el-option v-for="k in INVOICE_KINDS" :key="k" :label="INVOICE_KIND_LABELS[k]" :value="k"/></el-select>
        </el-form-item>
        <el-form-item label="开票日期"><el-date-picker v-model="form.invoiceDate" type="date" value-format="YYYY-MM-DD" style="width:100%"/></el-form-item>
        <el-form-item :label="form.direction==='SALE' ? '购买方' : '销售方'"><el-input v-model="form.partnerName" placeholder="对方单位名称"/></el-form-item>
        <el-form-item label="金额模式">
          <el-radio-group v-model="inputMode">
            <el-radio value="total">填价税合计（自动拆税）</el-radio>
            <el-radio value="net">填不含税金额</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="税率(%)"><el-input-number v-model="form.taxRate" :min="0" :max="100" :precision="2" controls-position="right" style="width:100%"/></el-form-item>
        <el-form-item v-if="inputMode==='total'" label="价税合计" required>
          <el-input-number v-model="form.totalAmount" :min="0.01" :precision="2" controls-position="right" style="width:100%"/>
        </el-form-item>
        <el-form-item v-else label="不含税金额" required>
          <el-input-number v-model="form.amount" :min="0.01" :precision="2" controls-position="right" style="width:100%"/>
        </el-form-item>
        <el-form-item label="联动预览">
          <span style="font-size:var(--app-font-base)">不含税 <b>{{ fmt(preview.amount) }}</b> ＋ 税额 <b style="color:var(--app-color-primary)">{{ fmt(preview.tax) }}</b> ＝ 价税合计 <b>{{ fmt(preview.total) }}</b></span>
        </el-form-item>
        <el-form-item label="关联单号"><el-input v-model="form.sourceBillCode" placeholder="可选：关联销售单/采购单号"/></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea"/></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" @click="save">确定</el-button></template>
    </el-dialog>
  </div>
</template>
<style scoped>.p{display:flex;flex-direction:column;gap:12px}.qf{display:flex;flex-wrap:wrap}.pg{margin-top:16px;display:flex;justify-content:flex-end}</style>
