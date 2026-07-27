<template>
  <div class="data-manage-page">
    <el-tabs v-model="activeTab" type="border-card">
      <el-tab-pane label="数据导出" name="export">
        <el-card shadow="never" style="max-width:600px">
          <template #header><span style="font-weight:600">数据导出</span></template>
          <p style="color:#909399;margin-bottom:12px">导出当前系统全部数据（含所有公司、所有表），用于数据备份和迁移。</p>
          <el-button type="primary" :loading="exportLoading" @click="handleExport">导出全部数据</el-button>
          <p style="color:#909399;margin-top:16px;font-size:12px">
            提示：如需导入数据，请在登录页左下角点击"数据导入"按钮。
          </p>
        </el-card>
      </el-tab-pane>
      <el-tab-pane label="清空数据" name="clear">
        <el-card shadow="never" style="max-width:500px">
          <template #header><span style="font-weight:600;color:#f56c6c">⚠ 危险操作</span></template>
          <p style="color:#909399;margin-bottom:16px">
            清空当前公司下所有业务数据，包括：客户、品牌、供应商、采购单、销售单、库存、财务数据等。
          </p>
          <p style="color:#e6a23c;margin-bottom:16px;font-size:13px">
            系统数据（公司、用户、角色、菜单）不受影响。操作后不可恢复，请谨慎执行。
          </p>
          <el-button type="danger" :loading="clearLoading" @click="handleClear">清空当前公司数据</el-button>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

const activeTab = ref('export')
const exportLoading = ref(false)
const clearLoading = ref(false)

async function handleExport() {
  exportLoading.value = true
  try {
    const res = await request.get<any, any>('/system/export-data')
    if (res && res.tables) {
      const blob = new Blob([JSON.stringify(res, null, 2)], { type: 'application/json' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a'); a.href = url; a.download = `backup_${new Date().toISOString().slice(0,10)}.json`
      a.click(); URL.revokeObjectURL(url)
      ElMessage.success('导出成功')
    } else {
      ElMessage.error('导出失败')
    }
  } catch (e: any) { ElMessage.error('导出失败: ' + (e?.message || '未知错误')) } finally { exportLoading.value = false }
}

async function handleClear() {
  try {
    await ElMessageBox.confirm(
      '此操作将清空当前公司下的所有业务数据。此操作不可恢复！',
      '清空数据', { confirmButtonText: '确认清空', cancelButtonText: '取消', type: 'error' }
    )
  } catch { return }
  clearLoading.value = true
  try {
    const res = await request.post('/system/clear-company-data')
    ElMessage.success(res || '数据已清空，请刷新页面')
    setTimeout(() => location.reload(), 1000)
  } catch (e: any) {
    ElMessage.error('操作失败: ' + (e?.message || '未知错误'))
  } finally { clearLoading.value = false }
}
</script>
