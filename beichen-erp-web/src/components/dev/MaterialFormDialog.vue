<script setup lang="ts">
import { localDate } from '@/utils/date'
import { reactive, ref, onMounted, watch, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { DevMaterialStatus, DevMaterialStatusLabel, DevMaterialTypeLabel, codeLabelOptions } from '@/api/enums'
// 资金账户（支出账户下拉）：属共享主数据（GET 放行），研发支出登记用
import { getAccountPage, type FinanceAccount } from '@/api/finance'

interface Props {
  // 项目页传入当前项目ID时，新增物料自动关联且下拉禁用；物料管理页不传，用户可选/留空
  defaultProjectId?: number
  visible?: boolean
}
const props = withDefaults(defineProps<Props>(), { defaultProjectId: undefined, visible: false })
const emit = defineEmits<{ (e: 'update:visible', v: boolean): void; (e: 'saved'): void }>()

const todayStr = localDate()
const materialStatusOptions = Object.entries(DevMaterialStatusLabel).map(([value, label]) => ({ value, label }))
const materialTypeOptions = ref<{ code: string; label: string }[]>([])
const projectOptions = ref<any[]>([])

const isEdit = ref(false)
const submitting = ref(false)
// 内部维护弹窗显示状态（父组件通过 ref.open() 控制，无需 v-model）
const dialogVisible = ref(false)
const form = reactive<any>({
  id: undefined,
  projectId: undefined,
  name: '', type: '', quantity: 1, locationDetail: '',
  purchaseDate: todayStr, amount: 0, status: DevMaterialStatus.GOOD, remark: ''
})

// 是否锁定项目（项目页传入时锁定）
const lockedProject = computed(() => props.defaultProjectId != null)

// ==================== 研发支出（2026-09-28 用户口径：该功能属研发物料，从「物料信息管理」移来） ====================
// 交互与旧物料页一致：**新增**时勾选 + 就地展开（物料与费用**一次提交**，物料建成后再登记费用）；
// 落库走 `POST /api/dev/purchase-item/{id}/rd-expense`：勾选路径 `autoAudit=true` ⇒ 建单即审核当场扣款，
// 无「费用管理」审核权限的账号由后端**降级为草稿**（不越权动钱）；同一研发物料已登记过则后端幂等回原单。
// 金额默认带出本表单的「金额」字段（可改）；账户必填（下拉取资金账户主数据）。
const rdForm = reactive({ enabled: false, amount: undefined as any, accountId: undefined as any, expenseDate: localDate(), remark: '' })
const rdAccounts = ref<FinanceAccount[]>([])
async function loadRdAccounts() {
  try {
    const r: any = await getAccountPage({ pageSize: 200 })
    rdAccounts.value = (r?.records || []).filter((a: any) => a.status === 1)
  } catch { rdAccounts.value = [] }
}
/** 勾选时默认带出研发物料金额（备注留空 ⇒ 由后端补「研发支出：物料名」） */
function onRdToggle(v: any) {
  if (v && rdForm.amount == null && form.amount) rdForm.amount = form.amount
}

function resetForm() {
  Object.assign(form, {
    id: undefined, projectId: lockedProject.value ? props.defaultProjectId : undefined,
    name: '', type: '', quantity: 1, locationDetail: '',
    purchaseDate: todayStr, amount: 0, status: DevMaterialStatus.GOOD, remark: ''
  })
  // 研发支出（仅新增）：每次打开都重置为未勾选，并备好支出账户下拉
  Object.assign(rdForm, { enabled: false, amount: undefined, accountId: undefined, expenseDate: localDate(), remark: '' })
  loadRdAccounts()
  isEdit.value = false
}

async function open(row?: any) {
  // 类型下拉（2026-09-14：改为前端枚举映射，不再请求后端；后端 /dev/purchase-item/material-types 已只回 code）
  if (materialTypeOptions.value.length === 0) materialTypeOptions.value = codeLabelOptions(DevMaterialTypeLabel)
  // 锁定项目时，仅加载当前项目作为唯一选项，保证禁用态下拉也能按名称显示（而非退化显示ID）
  if (lockedProject.value) {
    try { const res: any = await request.get('/dev/project/page', { params: { id: props.defaultProjectId, pageSize: 1 } }); projectOptions.value = res?.records || [] } catch (e) { /* 忽略 */ }
  } else if (projectOptions.value.length === 0) {
    try { const res: any = await request.get('/dev/project/page', { params: { pageSize: 500 } }); projectOptions.value = res?.records || [] } catch (e) { /* 忽略 */ }
  }
  if (row && row.id) {
    Object.assign(form, row)
    isEdit.value = true
  } else {
    resetForm()
  }
  dialogVisible.value = true
  emit('update:visible', true)
}

async function handleSubmit() {
  if (!form.name || !form.name.trim()) { ElMessage.warning('请输入名称'); return }
  // 2026-09-28：勾选「同时登记一笔研发支出」时的前端校验（后端同口径再校验一次）
  if (!isEdit.value && rdForm.enabled) {
    if (!rdForm.amount || Number(rdForm.amount) <= 0) { ElMessage.warning('研发支出金额必须大于 0'); return }
    if (!rdForm.accountId) { ElMessage.warning('研发支出必须选择支出账户'); return }
  }
  submitting.value = true
  try {
    const payload = { ...form }
    // 项目页锁定场景强制带上当前项目ID
    if (lockedProject.value) payload.projectId = props.defaultProjectId
    if (isEdit.value && form.id) {
      await request.put(`/dev/purchase-item/${form.id}`, payload)
      ElMessage.success('已更新')
    } else {
      // 后端返回新建实体（含 id）⇒ 研发支出要拿它作为来源对象
      const created: any = await request.post(`/dev/purchase-item`, payload)
      ElMessage.success('已添加')
      dialogVisible.value = false
      emit('update:visible', false)
      emit('saved')
      // 2026-09-28（用户口径）：新增研发物料后按需登记研发支出（**物料已建成**再登记，失败可重试；后端幂等）
      const newId = created?.id ?? created
      if (rdForm.enabled && newId) await createRdExpense(Number(newId))
      return
    }
    dialogVisible.value = false
    emit('update:visible', false)
    emit('saved')
  } catch (e: any) { ElMessage.error(e?.message || '操作失败') } finally { submitting.value = false }
}

/**
 * 为该**研发物料**登记一笔「研发支出」（2026-09-28 用户口径：功能从「物料信息管理」移到研发物料）。
 *
 * <p>顺序：研发物料**已保存成功**后再调本接口 ⇒ 不会出现"费用建了、物料没建"；
 * 反过来（物料建了、费用失败）由下方 confirm 重试兜底，且后端按来源幂等，重试不会重复扣款。</p>
 *
 * <p>`autoAudit: true` ⇒ 后端建单后立即审核：当场写「费用支出」资金流水并扣支出账户；
 * **余额不足时后端整体回滚**（不留半成品），错误信息会带出当前余额，用户可换账户重试。
 * 提示文案必须说清"已扣款"（或兜底时的"尚未审核"），否则用户对钱的状态会误判。</p>
 */
async function createRdExpense(materialId: number) {
  const payload = {
    amount: rdForm.amount,
    accountId: rdForm.accountId,
    expenseDate: rdForm.expenseDate,
    remark: rdForm.remark || '',
    autoAudit: true
  }
  try {
    const r: any = await request.post(`/dev/purchase-item/${materialId}/rd-expense`, payload)
    const no = r?.expenseNo ? `（单号 ${r.expenseNo}）` : ''
    if (r?.audited) {
      ElMessage.success(`${r?.existing ? '该研发物料已登记过研发支出' : '研发支出已登记并自动审核'}${no}，已从支出账户扣款`)
    } else if (r?.downgraded) {
      // 方案 A（2026-09-27 用户选定）：无「费用管理」审核权限的账号 ⇒ 后端降级为草稿（越权动钱的兜底）
      ElMessage.warning(`${r?.existing ? '该研发物料已登记过研发支出' : '研发支出已存为草稿'}${no}：当前账号没有「费用管理」审核权限，已按草稿登记，待财务审核后扣款`)
    } else {
      // 兜底：勾选路径按口径一定要求审核，走到这里说明后端返回异常，明确提示"未扣款"避免误判
      ElMessage.warning(`${r?.existing ? '该研发物料已登记过研发支出' : '研发支出已登记'}${no}，尚未扣款（请在「财务管理 → 费用管理」审核后扣款）`)
    }
  } catch (e: any) {
    let retry = false
    try {
      await ElMessageBox.confirm(`研发支出未登记成功：${e?.message || '未知错误'}。是否重试？`, '研发支出登记失败', { type: 'warning', confirmButtonText: '重试', cancelButtonText: '稍后处理' })
      retry = true
    } catch { retry = false }
    if (retry) await createRdExpense(materialId)
  }
}

function handleClose() {
  dialogVisible.value = false
  emit('update:visible', false)
}

// 供父组件通过 ref 调用 open
defineExpose({ open })

watch(() => props.visible, (v) => { if (v) open() })
onMounted(() => { if (props.visible) open() })
</script>

<template>
  <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑项目物料' : '新增项目物料'" width="var(--app-dialog-sm)" @close="handleClose">
    <el-form :model="form" label-width="80px">
      <el-row :gutter="12">
        <el-col :span="14"><el-form-item required label="物料名称"><el-input v-model="form.name" /></el-form-item></el-col>
        <el-col :span="10"><el-form-item label="类型">
          <el-select v-model="form.type" style="width:100%" placeholder="请选择类型">
            <el-option v-for="t in materialTypeOptions" :key="t.code" :label="t.label" :value="t.code" />
          </el-select>
        </el-form-item></el-col>
        <el-col :span="24"><el-form-item label="关联项目">
          <el-select v-model="form.projectId" style="width:100%" :disabled="lockedProject" clearable placeholder="不关联研发立项">
            <el-option v-for="p in projectOptions" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item></el-col>
        <el-col :span="12"><el-form-item label="数量"><el-input-number v-model="form.quantity" :min="0" :precision="0" style="width:100%" /></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="金额"><el-input-number v-model="form.amount" :min="0" :precision="2" style="width:100%" /></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="状态">
          <el-select v-model="form.status" style="width:100%">
            <el-option v-for="s in materialStatusOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item></el-col>
        <el-col :span="12"><el-form-item label="采购日期"><el-input v-model="form.purchaseDate" type="date" /></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="位置详情">
          <el-input v-model="form.locationDetail" placeholder="具体库位/货架号（可选）" />
        </el-form-item></el-col>
        <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item></el-col>

        <!-- 研发支出（2026-09-28 用户口径：功能属研发物料，从「物料信息管理」移来）。
             ① **仅新增**显示（编辑不动）；② 勾选后就地展开，与研发物料**一次提交**；
             ③ 落库是费用单（类型=研发支出）：勾选路径**自动审核**当场扣款，无费用审核权限则降级草稿。 -->
        <el-col :span="24" v-if="!isEdit">
          <el-form-item label="研发支出">
            <el-checkbox v-model="rdForm.enabled" @change="onRdToggle">同时登记一笔研发支出</el-checkbox>
            <div v-if="!rdForm.enabled" style="color:var(--app-text-secondary);font-size:var(--app-font-xs);line-height:1.5">勾选后填金额与支出账户，保存物料时一并登记一张「研发支出」并<b>自动审核</b>（当场从该账户扣款；余额不足会提示，可换账户重试）。如当前账号没有「费用管理」审核权限，则自动登记为草稿，由财务审核后扣款。</div>
          </el-form-item>
        </el-col>
        <template v-if="!isEdit && rdForm.enabled">
          <el-col :span="12"><el-form-item required label="支出金额"><el-input-number v-model="rdForm.amount" :precision="2" :min="0.01" controls-position="right" style="width:100%" placeholder="默认取物料金额" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item required label="支出账户">
            <el-select v-model="rdForm.accountId" placeholder="请选择" style="width:100%">
              <el-option v-for="a in rdAccounts" :key="a.id" :label="`${a.accountName}（余额 ${Number((a as any).balance ?? 0).toFixed(2)}）`" :value="a.id ?? ''" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item label="费用日期"><el-date-picker v-model="rdForm.expenseDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="费用备注"><el-input v-model="rdForm.remark" placeholder="留空自动填「研发支出：物料名」" /></el-form-item></el-col>
        </template>
      </el-row>
    </el-form>
    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>
