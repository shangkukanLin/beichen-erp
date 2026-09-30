<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { DocStatus, DocStatusLabel, DocStatusTag, PAYABLE_TRANSFER_DIRTY_KEY } from '@/api/enums'
import { TYPE_MAP, TYPE_TAG } from '@/constants/supplier'
import PageShell from '@/components/PageShell.vue'
import { invalidate } from '@/utils/dataFreshness'

/**
 * 转应收单详情（2026-09-24 用户口径：草稿态就地可编辑）
 *
 * 结构对齐采购单/销售单详情：`head` = 只读快照，`form` = 可编辑副本（白名单）。
 * 草稿态：页头「保存」+ 表单里可改「转出日期 / 备注」；已审核：只读 + 反审核。
 *
 * 为什么不给改「来源应付记录」：与新增页一致 —— `add.vue` 在编辑态把该下拉 `:disabled`（来源一旦选定就锁定，
 * 后端 update 也只认这张应付单），所以详情编辑只放开日期与备注，避免出现"下拉里找不到已转出的那张应付单、
 * 显示空白却又可提交"的坑。
 */
const route = useRoute()
const router = useRouter()
const id = Number(route.params.id)
const loading = ref(false)
const saving = ref(false)
/** 只读快照（提交体不带 status/审核人，见 resetForm 的白名单） */
const head = ref<any>({})
/** 可编辑副本 */
const form = ref<{ payableId?: number; transferDate?: string; remark: string }>({ payableId: undefined, transferDate: '', remark: '' })

const isDraft = computed(() => head.value.status === DocStatus.DRAFT)
const isAudited = computed(() => head.value.status === DocStatus.AUDITED)

function fmt(v?: number) { return v == null ? '0.00' : Number(v).toFixed(2) }
function typeLabel(code?: string) { return code ? (TYPE_MAP[code] || code) : '—' }

function resetForm() {
  form.value = {
    payableId: head.value.payableId,
    transferDate: head.value.transferDate || '',
    remark: head.value.remark || ''
  }
}

async function load() {
  loading.value = true
  try {
    head.value = await request.get<any, any>(`/finance/payable-transfer/${id}`) || {}
    resetForm()
  } catch (e: any) { ElMessage.error(e?.message || '加载失败') } finally { loading.value = false }
}

async function onSave() {
  if (!form.value.payableId) { ElMessage.warning('该单据缺少来源应付记录，无法保存'); return }
  saving.value = true
  try {
    // 白名单提交：只回传可改字段 + 主键（payableId 原样带回，保证后端 update 语义不变）
    await request.put('/finance/payable-transfer', {
      id,
      payableId: form.value.payableId,
      transferDate: form.value.transferDate,
      remark: form.value.remark
    })
    invalidate('payableTransfer')
    ElMessage.success('已保存')
    await load()
  } catch (e: any) { ElMessage.error(e?.message || '保存失败') } finally { saving.value = false }
}

async function onAudit() {
  try { await ElMessageBox.confirm(`确认审核「${head.value.code}」？将生成 ${fmt(head.value.amount)} 元应收。`, '审核确认', { type: 'warning' }) } catch { return }
  try {
    await request.put(`/finance/payable-transfer/${id}/audit`)
    ElMessage.success('已审核，已生成应收')
    invalidate('payableTransfer')
    load()
  } catch (e: any) { ElMessage.error(e?.message || '审核失败') }
}
async function onUnAudit() {
  try { await ElMessageBox.confirm('确认反审核？将冲销已生成的应收并恢复应付抵扣资格。', '反审核确认', { type: 'warning' }) } catch { return }
  try {
    await request.put(`/finance/payable-transfer/${id}/un-audit`)
    ElMessage.success('已反审核')
    invalidate('payableTransfer')
    load()
  } catch (e: any) { ElMessage.error(e?.message || '反审核失败') }
}
async function onCancel() {
  try { await ElMessageBox.confirm(`确认作废「${head.value.code}」？`, '作废确认', { type: 'warning' }) } catch { return }
  try {
    await request.put(`/finance/payable-transfer/${id}/cancel`)
    ElMessage.success('已作废')
    invalidate('payableTransfer')
    load()
  } catch (e: any) { ElMessage.error(e?.message || '作废失败') }
}

onMounted(load)
</script>

<template>
  <!-- 统一骨架（2026-09-23 全站定稿口径）：页头左端「← 返回」→ 标题(取 meta) → 右端操作 -->
  <PageShell :loading="loading" back-fallback="/finance/payable-transfer">
    <template #actions>
      <!-- 草稿：保存(主) + 审核 + 作废（2026-09-24：草稿态就地编辑，不再跳独立编辑页）
           2026-09-29 审核批 B · F7-220：四个动作都按 finance:payable-transfer 显示（与后端前缀同码） -->
      <el-button v-if="isDraft" type="primary" :loading="saving" v-perm="'finance:payable-transfer'" @click="onSave">保存</el-button>
      <el-button v-if="isDraft" type="success" v-perm="'finance:payable-transfer'" @click="onAudit">审核</el-button>
      <el-button v-if="isAudited" type="warning" v-perm="'finance:payable-transfer'" @click="onUnAudit">反审核</el-button>
      <el-button v-if="isDraft" type="danger" v-perm="'finance:payable-transfer'" @click="onCancel">作废</el-button>
    </template>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="title">
            {{ head.code || '—' }}
            <el-tag :type="DocStatusTag[head.status] || 'info'" size="small" style="margin-left:8px">
              {{ DocStatusLabel[head.status] || head.status }}
            </el-tag>
            <span v-if="isDraft" class="hint">（草稿态，可直接修改下方内容并保存）</span>
          </span>
        </div>
      </template>

      <!-- 草稿：可编辑表单（来源应付记录锁定，只放开日期/备注，与新增页编辑态口径一致） -->
      <el-form v-if="isDraft" :model="form" label-width="var(--app-label-width)" class="form">
        <el-form-item label="来源应付单号">
          <span>{{ head.payableBillNo || '—' }}</span>
          <el-tag v-if="head.supplierType" :type="TYPE_TAG[head.supplierType] || 'info'" size="small" style="margin-left:8px">{{ typeLabel(head.supplierType) }}</el-tag>
          <span class="hint" style="margin-left:8px">来源应付记录一经选定不可更改</span>
        </el-form-item>
        <el-form-item label="往来单位">{{ head.supplierName || '—' }}</el-form-item>
        <el-form-item label="转出金额"><strong>{{ fmt(head.amount) }}</strong></el-form-item>
        <el-form-item label="转出日期">
          <el-date-picker v-model="form.transferDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width:200px" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="选填，如：当月无货款可抵，转为收款" style="width:420px" />
        </el-form-item>
        <el-form-item label="制单人">{{ head.createByName || '—' }}</el-form-item>
      </el-form>

      <!-- 已审核 / 已作废：只读快照（原口径原样保留） -->
      <el-descriptions v-else :column="3" border>
        <el-descriptions-item label="转应收单号">{{ head.code || '—' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="DocStatusTag[head.status] || 'info'" size="small">{{ DocStatusLabel[head.status] || head.status }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="来源应付单号">{{ head.payableBillNo || '—' }}</el-descriptions-item>
        <el-descriptions-item label="转出日期">{{ head.transferDate || '—' }}</el-descriptions-item>
        <el-descriptions-item label="往来单位">{{ head.supplierName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="主体类型">
          <el-tag v-if="head.supplierType" :type="TYPE_TAG[head.supplierType] || 'info'" size="small">{{ typeLabel(head.supplierType) }}</el-tag>
          <span v-else>—</span>
        </el-descriptions-item>
        <el-descriptions-item label="转出金额"><strong>{{ fmt(head.amount) }}</strong></el-descriptions-item>
        <!-- 制单人（2026-09-23 用户口径：单据详情显示「制单人 + 审核人」） -->
        <el-descriptions-item label="制单人">{{ head.createByName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="审核人">{{ head.auditorName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="审核时间">{{ head.auditTime || '—' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ head.createTime || '—' }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ head.remark || '—' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </PageShell>
</template>

<style scoped>
/* 页头/根容器已统一到全局骨架（PageShell + styles/page.css）；原 .p / .card-header / .title 已删除 */
.form { max-width: 640px; }
.hint { color: var(--app-text-secondary); font-size: var(--app-font-xs); }
</style>
