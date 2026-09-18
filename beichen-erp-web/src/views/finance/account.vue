<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, type FormInstance } from 'element-plus'
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
function editAccount(row: FinanceAccount) { Object.assign(aForm, row); aDialog.value = true }
async function saveAccount() {
  try { if (aForm.id) { await updateAccount(aForm); ElMessage.success('修改成功') } else { await createAccount(aForm); ElMessage.success('新增成功') }; aDialog.value = false; loadAccounts() } catch {}
}
function fmt(v?: number) { return v == null ? '0.00' : Number(v).toFixed(2) }

onMounted(() => { loadAccounts() })

</script>
<template>
  <div class="p">
    <el-card shadow="never" class="table-card">
      <div style="margin-bottom:12px"><el-button type="success" :icon="'Plus'" @click="addAccount">新增账户</el-button></div>
      <el-table v-loading="loading" :data="accounts" border stripe>
        <el-table-column prop="accountName" label="账户名称" min-width="140"/>
        <el-table-column label="类型" width="80" align="center"><template #default="{row}"><el-tag>{{ accountTypeLabel(row.accountType) }}</el-tag></template></el-table-column>
        <el-table-column prop="bankName" label="开户行" min-width="120"/>
        <el-table-column prop="accountNo" label="账号" min-width="150"/>
        <el-table-column prop="balance" label="余额" width="130" align="right"><template #default="{row}">{{fmt(row.balance)}}</template></el-table-column>
        <el-table-column label="操作" width="80" align="center"><template #default="{row}"><el-button type="primary" link @click="editAccount(row)">编辑</el-button></template></el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="aDialog" title="账户管理" width="500px">
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
