<script setup lang="ts">
import { localDate } from '@/utils/date'
import { computed, reactive, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { sourceBillTypeLabel } from '@/api/enums'
import { TYPE_MAP, TYPE_TAG } from '@/constants/supplier'
import { PAYABLE_TRANSFER_DIRTY_KEY } from '@/api/enums'
import PageShell from '@/components/PageShell.vue'
import { useUnsavedGuard } from '@/composables/usePageBack'
import { useTabStore } from '@/stores/tabs'

const route = useRoute()
const router = useRouter()
/** 从应付列表点「转应收」带过来的应付记录ID */
const presetPayableId = computed(() => Number(route.query.payableId) || 0)
const editId = computed(() => Number(route.params.id) || 0)

const saving = ref(false)
const form = reactive({
  payableId: undefined as number | undefined,
  transferDate: localDate(),
  remark: ''
})
/** 可转应收的应付记录（负数冲减项、未转、未结清） */
const candidates = ref<any[]>([])
const picked = computed(() => candidates.value.find(x => x.id === form.payableId) || null)

async function loadCandidates() {
  try {
    candidates.value = await request.get<any, any>('/finance/payable-transfer/transferable') || []
  } catch { candidates.value = [] }
}

async function loadDetail() {
  if (!editId.value) return
  try {
    const d = await request.get<any, any>(`/finance/payable-transfer/${editId.value}`)
    form.payableId = d.payableId
    form.transferDate = d.transferDate || form.transferDate
    form.remark = d.remark || ''
  } catch (e: any) { ElMessage.error(e?.message || '加载失败') }
}

function fmt(v?: number) { return v == null ? '0.00' : Number(v).toFixed(2) }
function typeLabel(code?: string) { return code ? (TYPE_MAP[code] || code) : '—' }

async function onSubmit() {
  if (!form.payableId) { ElMessage.warning('请选择要转应收的应付记录'); return }
  saving.value = true
  try {
    const payload: any = {
      payableId: form.payableId,
      transferDate: form.transferDate,
      remark: form.remark
    }
    if (editId.value) { payload.id = editId.value; await request.put('/finance/payable-transfer', payload) }
    else await request.post('/finance/payable-transfer', payload)
    sessionStorage.setItem(PAYABLE_TRANSFER_DIRTY_KEY, '1')
    ElMessage.success('保存成功')
    // 提交成功 ⇒ 先清脏标记（否则离开会被未保存确认拦住），再关掉本次录入的页签并回列表
    markClean()
    tabStore.closeTabAndBack(route.path)
    router.push('/finance/payable-transfer')
  } catch (e: any) { ElMessage.error(e?.message || '保存失败') } finally { saving.value = false }
}
function goBack() { router.push('/finance/payable-transfer') }

// 2026-09-20（F7-168）：原写法把两件事叠在一起 —— 编辑态会调用 loadDetail() 两次（多一次请求），
// 且 presetPayableId 与 editId 同时存在时预选值会被 loadDetail 覆盖。现按"互斥"处理：
// 编辑态以单据数据为准（回填全部字段）；新增态才用列表带过来的 payableId 预选。
/**
 * 未保存拦截（2026-09-23 统一模板）
 * ⚠️ 必须写在 form 等状态**之后**（watch 注册时立即求值，放前面会 TDZ 静默失效）。
 */
const { takeBaseline, markClean } = useUnsavedGuard(() => ({ form }))
const tabStore = useTabStore()

onMounted(async () => {
  await loadCandidates()
  if (editId.value) await loadDetail()
  else if (presetPayableId.value) form.payableId = presetPayableId.value
  // 初始化完成（含编辑回填 / 来源预选）⇒ 建立"未保存"基线
  takeBaseline()
})
</script>

<template>
  <!-- 统一骨架（2026-09-23 全站定稿口径）：页头左端「← 返回」→ 标题 → 右端操作（保存） -->
  <PageShell :title="editId ? '编辑转应收单' : '新增转应收单'" back-fallback="/finance/payable-transfer">
    <template #actions>
      <el-button type="primary" :loading="saving" :disabled="!picked" @click="onSubmit">保存</el-button>
    </template>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <!-- 页内标题不再重复：由骨架统一显示（PageShell 取 title） -->
        </div>
      </template>

      <el-form :model="form" label-width="var(--app-label-width)" class="form">
        <el-form-item label="来源应付记录" required>
          <!-- 只列可转的记录：负数（退货/扣款冲减项）、未转出、未结清 -->
          <el-select v-model="form.payableId" placeholder="选择要转应收的应付记录" filterable :disabled="!!editId" style="width:420px">
            <el-option v-for="c in candidates" :key="c.id" :label="`${c.billNo}｜${c.supplierName}｜${fmt(c.transferableAmount)}`" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="转出日期">
          <el-date-picker v-model="form.transferDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width:200px" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="选填，如：当月无货款可抵，转为收款" style="width:420px" />
        </el-form-item>
      </el-form>

      <el-card v-if="picked" shadow="never" class="preview">
        <template #header><span class="title">转出信息（审核后按此生成应收）</span></template>
        <el-descriptions :column="3" border>
          <el-descriptions-item label="来源应付单号">{{ picked.billNo }}</el-descriptions-item>
          <el-descriptions-item label="业务场景">{{ sourceBillTypeLabel(picked.sourceBillType) || '—' }}</el-descriptions-item>
          <el-descriptions-item label="来源单据号">{{ picked.sourceBillNo || '—' }}</el-descriptions-item>
          <el-descriptions-item label="往来单位">{{ picked.supplierName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="主体类型">
            <el-tag :type="TYPE_TAG[picked.supplierType] || 'info'" size="small">{{ typeLabel(picked.supplierType) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="转出金额"><strong>{{ fmt(picked.transferableAmount) }}</strong></el-descriptions-item>
        </el-descriptions>
      </el-card>
      <el-empty v-else description="请选择上方要转应收的应付记录（仅退货/扣款等负数应付可选）" />

    </el-card>
  </PageShell>
</template>

<style scoped>
/* 页头/根容器/操作条已统一到全局骨架（PageShell + styles/page.css）；原 .p / .card-header / .title 已删除 */
.form { max-width: 640px; }
.preview { margin-top: 8px; }
.actions { margin-top: 16px; display: flex; justify-content: flex-end; gap: 8px; }
</style>
