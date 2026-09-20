<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

const loading = ref(false)
const confirmText = ref('')
/** 强确认口令：危险动作必须手输，与「数据导入」同一口径（仅一个 confirm 弹窗不足以防误点） */
const CONFIRM_WORD = '清空数据'

async function handleClear() {
  if (confirmText.value !== CONFIRM_WORD) { ElMessage.warning(`请输入“${CONFIRM_WORD}”以继续`); return }
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
    <template #header><span style="font-weight:600;color:var(--app-color-danger)">⚠ 危险操作</span></template>
    <p style="color:var(--app-text-secondary);margin-bottom:16px">
      清空当前公司下所有业务数据，包括：客户、品牌、供应商、采购单、销售单、库存、财务数据等。
    </p>
    <p style="color:var(--app-color-warning);margin-bottom:16px;font-size:var(--app-font-base)">
      系统数据（公司、用户、角色、菜单）不受影响。操作后不可恢复，请谨慎执行。
    </p>
    <div style="margin-bottom:12px">
      <span style="color:var(--app-color-danger)">请输入“{{ CONFIRM_WORD }}”以继续：</span>
      <el-input v-model="confirmText" :placeholder="CONFIRM_WORD" size="small" style="margin-top:4px" />
    </div>
    <el-button type="danger" :disabled="confirmText !== CONFIRM_WORD" :loading="loading" @click="handleClear">清空当前公司数据</el-button>
  </el-card>
</template>
