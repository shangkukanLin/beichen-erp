<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

const loading = ref(false)

async function handleClear() {
  try {
    await ElMessageBox.confirm(
      '此操作将清空当前公司下的所有业务数据（客户、供应商、采购、销售、库存、财务等），仅保留公司、用户、角色、菜单。此操作不可恢复！',
      '清空数据', { confirmButtonText: '确认清空', cancelButtonText: '取消', type: 'error' }
    )
  } catch { return }
  
  loading.value = true
  try {
    const res = await request.post('/system/clear-company-data')
    ElMessage.success(res || '数据已清空，请刷新页面')
    setTimeout(() => location.reload(), 1000)
  } catch (e: any) {
    ElMessage.error('操作失败: ' + (e?.message || '未知错误'))
  } finally { loading.value = false }
}
</script>

<template>
  <el-card shadow="never" style="max-width:500px">
    <template #header><span style="font-weight:600;color:#f56c6c">⚠ 危险操作</span></template>
    <p style="color:#909399;margin-bottom:16px">
      清空当前公司下所有业务数据，包括：客户、品牌、供应商、采购单、销售单、库存、财务数据等。
    </p>
    <p style="color:#e6a23c;margin-bottom:16px;font-size:13px">
      系统数据（公司、用户、角色、菜单）不受影响。操作后不可恢复，请谨慎执行。
    </p>
    <el-button type="danger" :loading="loading" @click="handleClear">清空当前公司数据</el-button>
  </el-card>
</template>
