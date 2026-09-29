<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { clearCompanyData } from '@/api/system'

const loading = ref(false)
/** F8-17：清空预演（dryRun）—— 只读，不删数据 */
const dryLoading = ref(false)
const confirmText = ref('')
/** 强确认口令：危险动作必须手输，与「数据导入」同一口径（仅一个 confirm 弹窗不足以防误点） */
const CONFIRM_WORD = '清空数据'

async function handleClear() {
  if (confirmText.value !== CONFIRM_WORD) { ElMessage.warning(`请输入“${CONFIRM_WORD}”以继续`); return }
  try {
    await ElMessageBox.confirm(
      '此操作将清空当前公司下的所有业务数据（客户、供应商、采购、销售、库存、财务等），仅保留公司、用户、角色、菜单。此操作不可恢复！',
      '清空本公司数据', { confirmButtonText: '确认清空', cancelButtonText: '取消', type: 'error' }
    )
  } catch { return }

  loading.value = true
  try {
    // F8-17：后端要求二次确认口令（前端口令只是第一道），并会先落一份回滚点（格式同「数据导出」）
    const res: any = await clearCompanyData({ confirm: CONFIRM_WORD })
    ElMessage.success(res?.tables ? `已清空 ${res.tables} 张表 / ${res.totalRows} 行数据，请刷新页面` : '数据已清空，请刷新页面')
    setTimeout(() => location.reload(), 1000)
  } catch (e: any) {
    ElMessage.error('操作失败: ' + (e?.message || '未知错误'))
  } finally { loading.value = false }
}

/**
 * F8-17：**清空预演**（`dryRun=true`）—— 后端只做"读 + 生成回滚点 + 报每张表将删行数"，不删任何数据，
 * 用来在真正清空前确认"这家公司到底有多少数据、清单覆盖到哪些表"。
 */
async function handleDryRun() {
  dryLoading.value = true
  try {
    const res: any = await clearCompanyData({ dryRun: true })
    const rows = Object.entries(res?.rows || {}).filter(([, n]) => Number(n) > 0)
    const detail = rows.map(([t, n]) => `${t}: ${n}`).join('、') || '（本公司当前无业务数据）'
    await ElMessageBox.alert(
      `将清空 ${res?.tables ?? '-'} 张表，共 ${res?.totalRows ?? 0} 行。\n\n` +
      `有数据的表（${rows.length} 张）：${detail}\n\n回滚点：${res?.backup ?? '-'}`,
      '预演结果（未删除任何数据）', { confirmButtonText: '知道了', type: 'info' }
    )
  } catch (e: any) {
    ElMessage.error('预演失败: ' + (e?.message || '未知错误'))
  } finally { dryLoading.value = false }
}
</script>

<template>
  <el-card shadow="never" style="max-width:500px">
    <template #header><span style="font-weight:600;color:var(--app-color-danger)">⚠ 危险操作</span></template>
    <p style="color:var(--app-text-secondary);margin-bottom:16px">
      清空当前公司下所有业务数据，包括：客户、品牌、供应商、采购单、销售单、库存、财务数据等。
    </p>
    <p style="color:var(--app-color-warning);margin-bottom:16px;font-size:var(--app-font-base)">
      系统数据（公司、用户、角色、菜单）不受影响；
      <span style="color:var(--app-color-danger)">但本公司的系统参数与操作日志会一并清空</span>
      （2026-09-30 口径）。操作后不可恢复，请谨慎执行。
    </p>
    <div style="margin-bottom:12px">
      <span style="color:var(--app-color-danger)">请输入“{{ CONFIRM_WORD }}”以继续：</span>
      <el-input v-model="confirmText" :placeholder="CONFIRM_WORD" size="small" style="margin-top:4px" />
    </div>
    <div style="display:flex;gap:8px;flex-wrap:wrap">
      <el-button :loading="dryLoading" @click="handleDryRun">预演（不删数据）</el-button>
      <el-button v-perm="'system:clear-data'" type="danger" :disabled="confirmText !== CONFIRM_WORD" :loading="loading" @click="handleClear">清空当前公司数据</el-button>
    </div>
    <p style="color:var(--app-text-secondary);margin-top:10px;font-size:var(--app-font-xs)">
      提示：清空前会自动在服务端生成一份「回滚点」备份（格式与「数据导出」一致，可用「数据导入」恢复）；
      点「预演」可先看清"将删哪些表、各多少行"，预演不会修改任何数据。
    </p>
  </el-card>
</template>
