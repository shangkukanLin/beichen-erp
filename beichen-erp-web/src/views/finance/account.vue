<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useDomainRefresh } from '@/utils/dataFreshness'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { getAccountPage, createAccount, updateAccount, type FinanceAccount } from '@/api/finance'
import { AccountTypeLabel, accountTypeLabel } from '@/api/enums'

// 账户管理（自「资金流水」页拆分为独立子菜单；菜单名 2026-09-18 由「资金账户」改为「账户管理」）
const accounts = ref<FinanceAccount[]>([])
const loading = ref(false)
const aDialog = ref(false)
const aForm = reactive<FinanceAccount>({ accountName: '', accountType: 'bank', bankName: '', accountNo: '', openingBalance: 0, status: 1 })
const aRef = ref<FormInstance>()

async function loadAccounts() {
  loading.value = true
  try { const r = await getAccountPage({pageSize:200}); accounts.value = r?.records || [] } catch {} finally { loading.value = false }
}
function addAccount() { Object.assign(aForm, { id: undefined, accountName: '', accountType: 'bank', bankName: '', accountNo: '', openingBalance: 0, status: 1 }); aDialog.value = true }
function editAccount(row: FinanceAccount) {
  // 2026-09-20（F7-170）：只挑可编辑字段 —— 原先 Object.assign(aForm, row) 把整行灌进表单，
  // 连 balance / createTime 等**只读派生字段**也一起带上并随 updateAccount 提交。字段清单与 addAccount 对齐。
  Object.assign(aForm, {
    id: row.id,
    accountName: row.accountName || '',
    accountType: row.accountType || 'bank',
    bankName: row.bankName || '',
    accountNo: row.accountNo || '',
    openingBalance: row.openingBalance ?? 0,
    status: row.status ?? 1
  })
  aDialog.value = true
}
async function saveAccount() {
  // 2026-09-20（F7-173 同族 · 顺带修复）：原先 catch {} 静默 ⇒ 保存失败无任何提示
  try {
    if (aForm.id) { await updateAccount(aForm); ElMessage.success('已更新') }
    else { await createAccount(aForm); ElMessage.success('已新增') }
    aDialog.value = false; loadAccounts()
  } catch (e: any) { ElMessage.error(e?.message || '保存失败') }
}
function fmt(v?: number) { return v == null ? '0.00' : Number(v).toFixed(2) }

/**
 * F7-236③（2026-09-29 审核批 C）：**补「启用/停用」入口**。
 *
 * 原先本页没有状态列、也没有启停用控件，但保存时 payload 一直带着 `status` ⇒ 账户"停用"能力实际不可达
 * （库中 0 个停用账户即其后果），连带让后端 F7-207/F7-215/F7-230 的"停用账户不得收付/支出"护栏**没有入口**。
 * 这里用部分更新（只发 id + status，其余字段为 null 会被 updateById 忽略/由后端取旧值兜底）。
 */
async function toggleStatus(row: FinanceAccount) {
  const toDisable = (row.status ?? 1) === 1
  try {
    await ElMessageBox.confirm(
      toDisable
        ? `确认停用账户「${row.accountName}」？停用后不能再用于收款/付款/费用支出（历史流水与余额保留）。`
        : `确认启用账户「${row.accountName}」？启用后恢复可用于收付款。`,
      '提示', { type: 'warning' })
  } catch { return }
  try {
    await updateAccount({ id: row.id, status: toDisable ? 0 : 1 } as FinanceAccount)
    ElMessage.success(toDisable ? '已停用' : '已启用'); loadAccounts()
  } catch { /* 提示由拦截器统一给出 */ }
}

useDomainRefresh('account', () => { loadAccounts() })

</script>
<template>
  <div class="p">
    <el-card shadow="never" class="table-card">
      <div style="margin-bottom:12px">
        <!-- F7-235：按 finance:account 显示（后端写规则含内联新增的多个码，见 ApiPermGuard；账户管理页自身就是 finance:account） -->
        <el-button type="success" :icon="'Plus'" v-perm="'finance:account'" @click="addAccount">新增</el-button>
      </div>
      <el-table v-loading="loading" :data="accounts" border stripe>
        <el-table-column prop="accountName" label="账户名称" min-width="140"/>
        <el-table-column label="类型" width="80" align="center"><template #default="{row}"><el-tag>{{ accountTypeLabel(row.accountType) }}</el-tag></template></el-table-column>
        <el-table-column prop="bankName" label="开户行" min-width="120"/>
        <el-table-column prop="accountNo" label="账号" min-width="150"/>
        <el-table-column prop="balance" label="余额" width="130" align="right"><template #default="{row}">{{fmt(row.balance)}}</template></el-table-column>
        <!-- F7-236③：状态列 + 启用/停用（后端 F7-207/F7-215/F7-230 的停用护栏依赖本入口才可用） -->
        <el-table-column label="状态" width="84" align="center">
          <template #default="{row}">
            <el-tag :type="row.status===1?'success':'info'" size="small">{{ row.status===1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center"><template #default="{row}"><el-button type="primary" link v-perm="'finance:account'" @click="editAccount(row)">编辑</el-button><el-button :type="row.status===1?'danger':'success'" link v-perm="'finance:account'" @click="toggleStatus(row)">{{ row.status===1 ? '停用' : '启用' }}</el-button></template></el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="aDialog" title="账户管理" width="var(--app-dialog-sm)">
      <el-form ref="aRef" :model="aForm" label-width="80px">
        <el-form-item label="名称"><el-input v-model="aForm.accountName"/></el-form-item>
        <el-form-item label="类型"><el-select v-model="aForm.accountType" style="width:100%"><el-option v-for="(lb, code) in AccountTypeLabel" :key="code" :label="lb" :value="code"/></el-select></el-form-item>
        <el-form-item label="开户行"><el-input v-model="aForm.bankName"/></el-form-item>
        <el-form-item label="账号"><el-input v-model="aForm.accountNo"/></el-form-item>
        <el-form-item label="期初余额"><el-input-number v-model="aForm.openingBalance" :min="0" :precision="2" :disabled="!!aForm.id" controls-position="right" style="width:100%"/></el-form-item>
      </el-form>
      <template #footer><el-button @click="aDialog=false">取消</el-button><el-button type="primary" @click="saveAccount">确定</el-button></template>
    </el-dialog>
  </div>
</template>
<style scoped>.p{display:flex;flex-direction:column;gap:12px}</style>
