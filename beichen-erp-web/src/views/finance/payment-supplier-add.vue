<script setup lang="ts">
// 新增付款（2026-09-23 用户要求：原 800px 弹框改为独立页面）
// —— 从「某供应商的付款页」进入，供应商由 `?supplierId=` 带过来（刷新/直链都不丢）；
//    核销明细只能从该供应商的未结清应付里选，与弹框口径一致。
import { localDate } from '@/utils/date'
import { reactive, ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { createPayment, getPaymentUnpaidPayables, type FinancePaymentItem } from '@/api/finance'

const route = useRoute()
const router = useRouter()
const supplierId = Number(route.query.supplierId)

const loading = ref(false)
const submitting = ref(false)
const supplierName = ref('')
const accounts = ref<any[]>([])
const unpaid = ref<any[]>([])

const form = reactive({ accountId: undefined as any, paymentDate: localDate(), remark: '', attachUrl: '' })
const items = ref<FinancePaymentItem[]>([])
const uploadFile = ref<File | null>(null)

const totalThisAmount = computed(() => items.value.reduce((s, it) => s + (Number(it.thisAmount) || 0), 0))
function fmt(v?: number) { return v == null ? '0.00' : Number(v).toFixed(2) }
const backPath = computed(() => `/finance/payment/supplier/${supplierId}`)

function addItem() { items.value.push({ payableId: undefined, payableBillNo: '', thisAmount: 0, remark: '' }) }
function removeItem(i: number) { items.value.splice(i, 1) }
function onPayableChange(val: number, row: FinancePaymentItem) {
  const r = unpaid.value.find(x => x.id === val)
  if (r) { row.payableId = r.id; row.payableBillNo = r.billNo; row.thisAmount = r.unpaidAmount }
}
function handleFileSelect(e: Event) { const f = (e.target as HTMLInputElement).files?.[0]; if (f) uploadFile.value = f }
/** 从按钮向上找最近的 form-item 容器再取隐藏的 file input（不依赖固定 DOM 层级） */
function pickUploadFile(e: Event) {
  const btn = e.currentTarget as HTMLElement | null
  const box = btn?.closest('.el-form-item') as HTMLElement | null
  box?.querySelector<HTMLInputElement>('input[type="file"]')?.click()
}

async function load() {
  if (!supplierId) return
  loading.value = true
  try {
    const [sup, accList, unpaidList] = await Promise.all([
      request.get<any, any>(`/supplier/${supplierId}`).catch(() => null),
      request.get<any, any>('/finance/account/list').catch(() => []),
      getPaymentUnpaidPayables(supplierId).catch(() => [])
    ])
    supplierName.value = (sup as any)?.name || ''
    accounts.value = accList || []
    unpaid.value = unpaidList || []
    if (unpaid.value.length === 0) ElMessage.info('该供应商没有未结清应付')
  } finally { loading.value = false }
}

async function submit() {
  if (!form.accountId) { ElMessage.warning('请选择付款账户'); return }
  if (items.value.length === 0) { ElMessage.warning('请添加核销明细'); return }
  submitting.value = true
  try {
    if (uploadFile.value) {
      const fd = new FormData(); fd.append('file', uploadFile.value)
      form.attachUrl = await request.post<any, string>('/dev/file/upload', fd) as unknown as string
    }
    await createPayment({ payment: { supplierId, ...form }, items: items.value })
    ElMessage.success('付款单已创建，请在付款记录中审核')
    router.push(backPath.value)
  } catch (e: any) { ElMessage.error(e?.message || '保存失败') } finally { submitting.value = false }
}
onMounted(load)
</script>

<template>
  <div class="p" v-loading="loading">
    <el-card shadow="never">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span style="font-weight:600">新增付款 — {{ supplierName || ('供应商 #' + supplierId) }}</span>
          <el-button @click="router.push(backPath)">返回</el-button>
        </div>
      </template>

      <el-alert v-if="!loading && unpaid.length === 0" type="info" :closable="false" show-icon
        title="该供应商当前没有未结清应付，无法新增付款。" style="margin-bottom:12px" />

      <el-form :model="form" label-width="90px" style="max-width:860px">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="供应商"><el-input :model-value="supplierName" readonly /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="付款账户" required><el-select v-model="form.accountId" filterable style="width:100%"><el-option v-for="a in accounts" :key="a.id" :label="a.accountName" :value="a.id"/></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="付款日期"><el-date-picker v-model="form.paymentDate" type="date" value-format="YYYY-MM-DD" style="width:100%"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="付款凭证">
            <div style="display:flex;align-items:center;gap:8px">
              <el-button size="small" @click="pickUploadFile($event)">选择图片</el-button>
              <span style="font-size:var(--app-font-xs);color:var(--app-text-secondary)">{{ uploadFile?.name || '未选择' }}</span>
              <input type="file" accept="image/*" style="display:none" @change="handleFileSelect" />
            </div>
          </el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2"/></el-form-item></el-col>
        </el-row>

        <el-divider>核销明细（未结清应付）</el-divider>
        <div style="margin-bottom:8px"><el-button type="primary" size="small" :disabled="unpaid.length === 0" @click="addItem">添加核销项</el-button></div>
        <el-table :data="items" border>
          <el-table-column label="应付单据" min-width="240"><template #default="{row}"><el-select v-model="row.payableId" filterable style="width:100%" @change="(v:number)=>onPayableChange(v,row)"><el-option v-for="u in unpaid" :key="u.id" :label="`${u.billNo} (未付:${u.unpaidAmount}, 到期:${u.dueDate || '-'})`" :value="u.id"/></el-select></template></el-table-column>
          <el-table-column label="核销金额" width="140"><template #default="{row}"><el-input-number v-model="row.thisAmount" :min="0" :precision="2" controls-position="right" style="width:100%"/></template></el-table-column>
          <el-table-column label="操作" width="60" align="center"><template #default="{$index}"><el-button type="danger" link @click="removeItem($index)">删除</el-button></template></el-table-column>
        </el-table>
        <div style="margin-top:8px;text-align:right;font-weight:600">本次付款合计：¥ {{ fmt(totalThisAmount) }}</div>
      </el-form>

      <div style="margin-top:12px;display:flex;gap:8px;justify-content:flex-end;max-width:860px">
        <el-button @click="router.push(backPath)">取消</el-button>
        <el-button type="primary" :loading="submitting" :disabled="unpaid.length === 0" @click="submit">确定</el-button>
      </div>
    </el-card>
  </div>
</template>

<style scoped>.p{display:flex;flex-direction:column;gap:12px}</style>
