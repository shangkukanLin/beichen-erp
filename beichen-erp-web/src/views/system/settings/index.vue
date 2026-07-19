<script setup lang="ts">
import { ref, onMounted, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

const activeTab = ref('company')

// ==================== 公司信息 ====================
const companyForm = reactive({ companyName: '', phone: '', address: '', contactPerson: '', taxNo: '', email: '' })
const companySaving = ref(false)
async function loadCompany() {
  try { const res = await request.get<any, any>('/settings/company'); Object.assign(companyForm, res || {}) } catch {}
}
async function saveCompany() {
  companySaving.value = true
  try { await request.put('/settings/company', companyForm); ElMessage.success('保存成功') } finally { companySaving.value = false }
}

// ==================== 系统参数 ====================
const params = ref<{ id?: number; paramKey: string; paramValue: string; remark: string }[]>([])
const paramsSaving = ref(false)
async function loadParams() {
  try { const res = await request.get<any, any>('/settings/params'); params.value = res || initDefaultParams() } catch { params.value = initDefaultParams() }
}
function initDefaultParams() {
  return [
    { paramKey: 'tax_rate', paramValue: '13.00', remark: '税率(%)' },
    { paramKey: 'credit_period', paramValue: '30', remark: '账期天数' },
    { paramKey: 'stock_alert_threshold', paramValue: '10', remark: '库存预警阈值' }
  ]
}
async function saveParams() {
  paramsSaving.value = true
  try { await request.put('/settings/params', params.value); ElMessage.success('保存成功') } finally { paramsSaving.value = false }
}

// ==================== 操作日志 ====================
const logQuery = reactive({ module: '', username: '', startDate: '', endDate: '' })
const logData = ref<any[]>([])
const logLoading = ref(false)
const logPage = reactive({ pageNum: 1, pageSize: 20, total: 0 })
async function loadLogs() {
  logLoading.value = true
  try {
    const res = await request.get<any, any>('/settings/logs', { params: { ...logQuery, pageNum: logPage.pageNum, pageSize: logPage.pageSize } })
    logData.value = res?.records || []; logPage.total = res?.total || 0
  } finally { logLoading.value = false }
}
function handleLogPageChange(p: number) { logPage.pageNum = p; loadLogs() }

onMounted(() => { loadCompany(); loadParams() })
</script>

<template>
  <el-card shadow="never">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="公司信息" name="company">
        <el-form :model="companyForm" label-width="100px" style="max-width:600px">
          <el-form-item label="公司名称"><el-input v-model="companyForm.companyName" /></el-form-item>
          <el-form-item label="联系人"><el-input v-model="companyForm.contactPerson" /></el-form-item>
          <el-form-item label="电话"><el-input v-model="companyForm.phone" /></el-form-item>
          <el-form-item label="邮箱"><el-input v-model="companyForm.email" /></el-form-item>
          <el-form-item label="地址"><el-input v-model="companyForm.address" /></el-form-item>
          <el-form-item label="税号"><el-input v-model="companyForm.taxNo" /></el-form-item>
          <el-form-item><el-button type="primary" :loading="companySaving" @click="saveCompany">保存</el-button></el-form-item>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="系统参数" name="params">
        <el-table :data="params" border stripe style="max-width:600px">
          <el-table-column prop="paramKey" label="参数键" width="180" />
          <el-table-column label="参数值" min-width="150">
            <template #default="{ row }"><el-input v-model="row.paramValue" size="small" /></template>
          </el-table-column>
          <el-table-column prop="remark" label="说明" width="150" />
        </el-table>
        <el-button type="primary" :loading="paramsSaving" @click="saveParams" style="margin-top:12px">保存</el-button>
      </el-tab-pane>

      <el-tab-pane label="操作日志" name="logs">
        <el-form :model="logQuery" inline size="small">
          <el-form-item label="模块"><el-input v-model="logQuery.module" placeholder="模块" clearable /></el-form-item>
          <el-form-item label="用户"><el-input v-model="logQuery.username" placeholder="用户名" clearable /></el-form-item>
          <el-form-item label="日期"><el-input v-model="logQuery.startDate" type="date" /> ~ <el-input v-model="logQuery.endDate" type="date" /></el-form-item>
          <el-form-item><el-button type="primary" @click="logPage.pageNum=1;loadLogs()">查询</el-button></el-form-item>
        </el-form>
        <el-table :data="logData" border stripe v-loading="logLoading" size="small">
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="username" label="用户" width="80" />
          <el-table-column prop="module" label="模块" width="80" />
          <el-table-column prop="operation" label="操作" width="80" />
          <el-table-column prop="target" label="对象" min-width="120" show-overflow-tooltip />
          <el-table-column prop="detail" label="详情" min-width="150" show-overflow-tooltip />
          <el-table-column prop="createTime" label="时间" width="160" :formatter="(r:any,c:any,v:any)=>$fmtDate(v)" />
        </el-table>
        <el-pagination
          v-model:current-page="logPage.pageNum" :page-size="logPage.pageSize" :total="logPage.total"
          layout="total, prev, pager, next" background small style="margin-top:12px;justify-content:flex-end"
          @current-change="handleLogPageChange" />
      </el-tab-pane>
    </el-tabs>
  </el-card>
</template>
