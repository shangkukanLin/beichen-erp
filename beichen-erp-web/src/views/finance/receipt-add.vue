<script setup lang="ts">
// 新增收款（2026-09-23 用户要求：原 850px 弹框改为独立页面）
// —— 主体类型（客户收款 / 供应商收款，如应付转应收）决定往来单位与可核销应收的口径，与弹框一致。
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { ADD_MARKER } from '@/composables/useSelectWithAdd'
import RemoteSelect from '@/components/RemoteSelect.vue'
import { createReceipt, getReceiptUnpaidReceivables, type FinanceReceipt, type FinanceReceiptItem, type FinanceReceivable } from '@/api/finance'
import { SubjectType } from '@/api/enums'

const router = useRouter()
const accounts = ref<{id:number;accountName:string}[]>([])

const form = reactive<FinanceReceipt>({ customerId: undefined, supplierId: undefined, subjectType: SubjectType.CUSTOMER as string, accountId: undefined, receiptDate: '', remark: '' })
const items = ref<FinanceReceiptItem[]>([])
const unpaid = ref<FinanceReceivable[]>([])
const saving = ref(false)

const fetchCustomers = (kw: string) => request.get('/inventory/customer/page', { params: { pageSize: 500, name: kw } })
const fetchSuppliers = (kw: string) => request.get('/supplier/page', { params: { pageSize: 500, name: kw } })
async function loadAccounts() {
  try { accounts.value = await request.get<any, any>('/finance/account/list') || [] } catch { accounts.value = [] }
}
/** 切换主体类型：清空往来单位与已选核销明细 */
function onSubjectTypeChange() {
  form.customerId = undefined
  form.supplierId = undefined
  items.value = []
  unpaid.value = []
}
/** 按主体类型拉取可核销的未结清应收（走收款页自身前缀，避免跨模块权限被拦） */
async function loadUnpaid() {
  try {
    unpaid.value = form.subjectType === SubjectType.SUPPLIER
      ? (await getReceiptUnpaidReceivables(undefined, form.supplierId as number)) || []
      : (await getReceiptUnpaidReceivables(form.customerId as number)) || []
  } catch { unpaid.value = [] }
}
function addItem() { items.value.push({ receivableId: undefined, receivableBillNo: '', thisAmount: 0, remark: '' }) }
function removeItem(i: number) { items.value.splice(i, 1) }
function onReceivableChange(val: number, row: FinanceReceiptItem) {
  const r = unpaid.value.find(x => x.id === val)
  if (r) { row.receivableId = r.id; row.receivableBillNo = r.billNo; row.thisAmount = r.unpaidAmount }
}

async function submit() {
  if (form.subjectType === SubjectType.SUPPLIER && !form.supplierId) { ElMessage.warning('请选择供应商'); return }
  if (form.subjectType !== SubjectType.SUPPLIER && !form.customerId) { ElMessage.warning('请选择客户'); return }
  if (!form.accountId) { ElMessage.warning('请选择收款账户'); return }
  if (items.value.length === 0) { ElMessage.warning('请添加核销明细'); return }
  saving.value = true
  try {
    await createReceipt({ receipt: { ...form }, items: items.value })
    ElMessage.success('已保存')
    router.push('/finance/receipt')
  } catch { /* 提示由拦截器统一给出 */ } finally { saving.value = false }
}
onMounted(loadAccounts)
</script>

<template>
  <div class="p">
    <el-card shadow="never">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span style="font-weight:600">新增收款单</span>
          <el-button @click="router.push('/finance/receipt')">返回</el-button>
        </div>
      </template>

      <el-form :model="form" label-width="90px" style="max-width:900px">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="主体类型" required>
            <el-radio-group v-model="form.subjectType" @change="onSubjectTypeChange">
              <el-radio-button :value="SubjectType.CUSTOMER">客户收款</el-radio-button>
              <el-radio-button :value="SubjectType.SUPPLIER">供应商收款</el-radio-button>
            </el-radio-group>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="form.subjectType === SubjectType.SUPPLIER ? '供应商' : '客户'" required>
            <RemoteSelect v-if="form.subjectType === SubjectType.SUPPLIER" v-model="form.supplierId" :fetch="fetchSuppliers" placeholder="请选择" style="width:100%" @change="() => loadUnpaid()" />
            <RemoteSelect v-else v-model="form.customerId" :fetch="fetchCustomers" placeholder="请选择" style="width:100%" @change="(v: any) => { if (v === ADD_MARKER) { form.customerId = undefined; router.push('/inventory/customer'); return } loadUnpaid() }"><el-option label="+ 新增" :value="ADD_MARKER" /></RemoteSelect>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item label="收款账户" required><el-select v-model="form.accountId" placeholder="请选择" filterable style="width:100%"><el-option v-for="a in accounts" :key="a.id" :label="a.accountName" :value="a.id"/></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="收款日期"><el-date-picker v-model="form.receiptDate" type="date" value-format="YYYY-MM-DD" style="width:100%"/></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2"/></el-form-item></el-col>
        </el-row>

        <el-divider>{{ form.subjectType === SubjectType.SUPPLIER ? '核销明细（从供应商未结清应收选择，如应付转应收单）' : '核销明细（从客户未结清应收选择）' }}</el-divider>
        <div style="margin-bottom:8px"><el-button type="primary" @click="addItem">添加核销项</el-button></div>
        <el-table :data="items" border>
          <el-table-column label="应收单据" min-width="220"><template #default="{row}"><el-select v-model="row.receivableId" placeholder="选择应收单据" filterable style="width:100%" @change="(v:number)=>onReceivableChange(v,row)"><el-option v-for="u in unpaid" :key="u.id" :label="`${u.billNo} (未收:${u.unpaidAmount},到期:${u.dueDate || '-'})`" :value="u.id ?? ''"/></el-select></template></el-table-column>
          <el-table-column label="核销金额" width="140"><template #default="{row}"><el-input-number v-model="row.thisAmount" :min="0" :precision="2" controls-position="right" style="width:100%"/></template></el-table-column>
          <el-table-column label="操作" width="70" align="center"><template #default="{$index}"><el-button type="danger" link @click="removeItem($index)">删除</el-button></template></el-table-column>
        </el-table>
      </el-form>

      <div style="margin-top:12px;display:flex;gap:8px;justify-content:flex-end;max-width:900px">
        <el-button @click="router.push('/finance/receipt')">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">确定</el-button>
      </div>
    </el-card>
  </div>
</template>

<style scoped>.p{display:flex;flex-direction:column;gap:12px}</style>
