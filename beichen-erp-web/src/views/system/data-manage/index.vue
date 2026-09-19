<template>
  <div class="data-manage-page">
    <el-tabs v-model="activeTab" type="border-card">
      <!-- P2-34：整库导入/导出为平台级能力，仅超级管理员可见（公司管理员只保留"清空本公司数据"） -->
      <el-tab-pane v-if="isSuperAdmin" label="数据导出" name="export">
        <el-card shadow="never" style="max-width:600px">
          <template #header><span style="font-weight:600">数据导出</span></template>
          <p style="color:var(--app-text-secondary);margin-bottom:12px">导出当前系统全部数据（含所有公司、所有表），用于数据备份和迁移。</p>
          <el-button type="primary" :loading="exportLoading" @click="handleExport">导出全部数据</el-button>
          <p style="color:var(--app-text-secondary);margin-top:16px;font-size:var(--app-font-xs)">
            提示：如需导入备份数据，请切换到「数据导入」标签页（仅超级管理员可用）。
          </p>
        </el-card>
      </el-tab-pane>
      <el-tab-pane v-if="isSuperAdmin" label="数据导入" name="import">
        <el-card shadow="never" style="max-width:600px">
          <template #header><span style="font-weight:600">数据导入</span></template>
          <el-alert type="error" :closable="false" show-icon style="margin-bottom:16px;text-align:left">
            <template #title>导入会用备份数据整体替换当前系统全部数据（含所有公司、所有表），操作不可撤销！</template>
          </el-alert>
          <!-- 第一步：选择文件 -->
          <div v-if="importStep === 'upload'">
            <el-upload drag :auto-upload="false" :limit="1" accept=".json" :on-change="handleFileChange" :file-list="[]">
              <el-icon :size="48"><component is="UploadFilled" /></el-icon>
              <div style="margin-top:8px">将备份 JSON 文件拖到此处，或点击选择</div>
            </el-upload>
            <div style="text-align:center;margin-top:16px">
              <el-button type="primary" :disabled="!importFile" :loading="importLoading" @click="handleImportPreview">下一步</el-button>
            </div>
          </div>
          <!-- 第二步：预览并确认（表数口径 + 失败/清空风险 + 二次确认） -->
          <div v-else-if="importStep === 'preview' && importPreview" style="text-align:left">
            <el-descriptions :column="2" border size="small">
              <el-descriptions-item label="备份时间">{{ importPreview.exportTime }}</el-descriptions-item>
              <el-descriptions-item label="备份表数">
                {{ importPreview.backupTables }} 张
                <span style="color:var(--app-text-secondary)">
                  （{{ importPreview.tablesWithData }} 张有数据 / {{ (importPreview.emptyTables || []).length }} 张空表）
                </span>
              </el-descriptions-item>
              <el-descriptions-item label="备份记录数" :span="2">{{ importPreview.backupRecords }} 条</el-descriptions-item>
            </el-descriptions>
            <el-alert
              v-if="importRiskTables.length"
              type="warning"
              :closable="false"
              show-icon
              style="margin-top:12px;text-align:left"
            >
              <template #title>备份缺少当前库的 {{ importRiskTables.length }} 张表，导入后这些表会被清空</template>
              <div style="margin-top:4px">{{ importRiskPreview }}</div>
              <el-checkbox v-model="importAck" style="margin-top:8px">我已知悉，确认清空上述表</el-checkbox>
            </el-alert>
            <div style="margin-top:12px;text-align:left">
              <span style="color:#f56c6c">请输入“确认导入”以继续：</span>
              <el-input v-model="confirmText" placeholder="确认导入" size="small" style="margin-top:4px" />
            </div>
            <div style="margin-top:16px;text-align:center">
              <el-button @click="resetImport">重新选择</el-button>
              <el-button
                type="danger"
                :disabled="confirmText !== '确认导入' || (importRiskTables.length > 0 && !importAck)"
                :loading="importLoading"
                @click="handleImportConfirm"
              >
                确认导入
              </el-button>
            </div>
          </div>
        </el-card>
      </el-tab-pane>
      <el-tab-pane label="清空数据" name="clear">
        <el-card shadow="never" style="max-width:500px">
          <template #header><span style="font-weight:600;color:var(--app-color-danger)">⚠ 危险操作</span></template>
          <p style="color:var(--app-text-secondary);margin-bottom:16px">
            清空当前公司下所有业务数据，包括：项目/研发、客户、品牌、供应商、产品、委外物料与加工单、
            采购/销售/换货/退货整理/售后、采购单、销售单、库存与盘点、成品报损、物料报损、成本批次、
            财务（费用、发票、收付款、应收应付、应付转应收、账单、现金流）、备忘等。
          </p>
          <p style="color:var(--app-color-warning);margin-bottom:16px;font-size:var(--app-font-base)">
            系统数据（公司、用户、角色、菜单）不受影响。操作后不可恢复，请谨慎执行。
          </p>
          <el-button type="danger" :loading="clearLoading" @click="handleClear">清空当前公司数据</el-button>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { localDate } from '@/utils/date'
import { ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { useUserStore } from '@/stores/user'

// P2-34 口径（2026-09-12）：整库「数据导出/数据导入」为平台级能力，仅超级管理员可用；
// 后端已按其角色收口，页面同步隐藏入口，避免留给公司管理员一个必然 403 的死按钮。
const userStore = useUserStore()
const isSuperAdmin = computed(() => userStore.isSuperAdmin)

const activeTab = ref(isSuperAdmin.value ? 'export' : 'clear')
const exportLoading = ref(false)
const clearLoading = ref(false)

// 数据导入（原登录页入口迁移至此：导入需登录 + 管理员权限）
const importFile = ref<File | null>(null)
const importLoading = ref(false)
const importPreview = ref<any>(null)
const importStep = ref<'upload' | 'preview'>('upload')
const confirmText = ref('')
const importAck = ref(false)

/** 备份里没有、导入后会被清空的当前表（服务端预检给出） */
const importRiskTables = computed<string[]>(() => (importPreview.value?.tablesNotInBackup || []) as string[])
const importRiskPreview = computed(() => {
  const t = importRiskTables.value
  if (!t.length) return ''
  return t.slice(0, 10).join('、') + (t.length > 10 ? ` 等 ${t.length} 张` : '')
})

function handleFileChange(file: any) { importFile.value = file.raw || file }

async function handleImportPreview() {
  if (!importFile.value) return
  importLoading.value = true
  try {
    // 1) 本地快速校验：能解析、且含 tables（兼容两种形态：UI 下载的内层对象 / 接口原始响应带 R 包装）
    const text = await importFile.value.text()
    const parsed = JSON.parse(text)
    const payload = parsed?.tables ? parsed : (parsed?.data || {})
    if (!payload?.tables) throw new Error('缺少 tables 字段')
    // 2) 服务端预检（只读）：表数口径、空表清单、将被清空的表清单 —— 作为"二次确认"的依据
    const fd = new FormData(); fd.append('file', importFile.value)
    const pre = await request.post<any, any>('/system/import-data/precheck', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
    importPreview.value = pre
    importAck.value = false
    importStep.value = 'preview'
  } catch (ex: any) {
    ElMessage.error('文件解析失败: ' + (ex?.message || '格式错误'))
    importPreview.value = null
    importFile.value = null
  } finally { importLoading.value = false }
}

async function handleImportConfirm() {
  if (confirmText.value !== '确认导入' || !importFile.value) return
  if (importRiskTables.value.length > 0 && !importAck.value) {
    ElMessage.warning('请先勾选「我已知悉，确认清空上述表」')
    return
  }
  importLoading.value = true
  try {
    const fd = new FormData(); fd.append('file', importFile.value)
    // 服务端要求：存在"备份缺失的当前表"时必须显式确认，否则拒绝（不删任何数据）
    if (importRiskTables.value.length > 0) fd.append('confirmMissingTables', 'true')
    const res = await request.post<any, any>('/system/import-data', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
    const data = res?.data || res
    const emptyCnt = (data?.emptyTables || []).length
    ElMessage.success(
      `导入成功：${data?.totalRecords || 0} 条记录，${data?.dataTables || 0} 张表有数据` +
      (emptyCnt ? `（另有 ${emptyCnt} 张空表）` : '')
    )
    // F5-1（2026-09-18）：导入不可逆，服务端已在落库前自动备份"导入前的库" —— 告知回滚点位置
    if (data?.preImportBackupPath) {
      ElMessageBox.alert(
        `导入前的数据已自动备份到服务器：${data.preImportBackupPath}。` +
        `若结果不符预期，可在本页选择该文件重新导入，即可回退到导入前的状态。`,
        '导入完成（已生成回滚点）',
        { type: 'success', confirmButtonText: '知道了' }
      ).catch(() => {})
    }
    resetImport()
  } catch (e: any) { ElMessage.error('导入失败: ' + (e?.message || '未知错误')) } finally { importLoading.value = false }
}

function resetImport() {
  importPreview.value = null
  confirmText.value = ''
  importAck.value = false
  importFile.value = null
  importStep.value = 'upload'
}

async function handleExport() {
  exportLoading.value = true
  try {
    const res = await request.get<any, any>('/system/export-data')
    if (res && res.tables) {
      const blob = new Blob([JSON.stringify(res, null, 2)], { type: 'application/json' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a'); a.href = url; a.download = `backup_${localDate()}.json`
      a.click(); URL.revokeObjectURL(url)
      // 失败表清单（服务端不再静默跳过）：有则在成功提示之外额外告警，避免"备份少表"无人察觉
      const failed = res.exportInfo?.failedTables || []
      if (Array.isArray(failed) && failed.length > 0) {
        ElMessage.warning(`导出完成，但有 ${failed.length} 张表读取失败：${failed.slice(0, 5).join('、')}${failed.length > 5 ? ' 等' : ''}`)
      } else {
        ElMessage.success('导出成功')
      }
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
