<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { localDate } from '@/utils/date'
import { ADD_MARKER } from '@/composables/useSelectWithAdd'
import EntityLinks from '@/components/EntityLinks.vue'
import RemoteSelect from '@/components/RemoteSelect.vue'
// 资金账户（支出账户下拉）：属共享主数据（GET 放行），2026-09-27 研发支出登记用
import { getAccountPage, type FinanceAccount } from '@/api/finance'

// 2026-09-21：去掉「所属项目」筛选 —— 该字段已整字段下线（用户：这个字段没什么用）
const query = reactive({ materialName: '' })
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const tableData = ref<any[]>([])
const allMaterials = ref<any[]>([])  // 全部物料，不受TAB过滤，供子物料下拉框使用
const tableLoading = ref(false)
const supplierOptions = ref<any[]>([])
const MATERIAL_TYPES = ref<any[]>([])

// Odoo 风格：下拉框展开/搜索时实时查库（不预缓存全量）
const fetchSuppliers = (kw: string) => request.get('/supplier/page', { params: { pageSize: 500, name: kw } })
const fetchMaterialTypes = (kw: string) => request.get('/dev/material-type/enabled')

// Tab 切换 - 物料类型（按 materialTypeId 过滤）
const activeTab = ref<number | string>('全部')

// 列表关联名称显示：组件本地轻量列表加载一次（Odoo 实时化）
async function loadOptions() {
  const [bt, sup, mat] = await Promise.all([
    fetchMaterialTypes(''),
    fetchSuppliers(''),
    request.get('/outsource/material/page', { params: { pageSize: 500 } }),
  ])
  MATERIAL_TYPES.value = bt?.records || bt || []
  supplierOptions.value = sup?.records || sup || []
  allMaterials.value = mat?.records || mat || []
}

/**
 * 供应商列可点（2026-09-25 B1）：把逗号串的 supplierIds 转成 EntityLinks 需要的 [{id,name}]。
 * 名称取本地已加载的 supplierOptions（与 supplierNames 同一数据源），查不到则回退显示 id。
 */
function supplierItems(ids: string | undefined) {
  if (!ids || !ids.trim()) return []
  return ids.split(',').map(s => s.trim()).filter(Boolean).map(id => {
    const s = supplierOptions.value.find((x: any) => String(x.id) === id)
    return { id: Number(id), name: s ? s.name : id }
  })
}

// 按供应商ID列表(逗号分隔)查出供应商名称并拼接展示，空安全返回 '-'
function supplierNames(ids: string | undefined) {
  if (!ids || !ids.trim()) return '-'
  return ids.split(',').map(id => {
    const s = supplierOptions.value.find(x => String(x.id) === id.trim())
    return s ? s.name : id.trim()
  }).join(', ')
}

// 2026-09-21：原「库存总量/未交数量」格式化函数 fmtQty 随两列一并删除（已无调用方）

async function loadData() {
  tableLoading.value = true
  try {
    const p: any = { pageNum: pagination.pageNum, pageSize: pagination.pageSize }
    if (query.materialName) p.materialName = query.materialName
    if (activeTab.value !== '全部') p.materialTypeId = activeTab.value
    const r = await request.get<any, any>('/outsource/material/page', { params: p })
    tableData.value = r?.records || []; pagination.total = r?.total || 0
  } finally { tableLoading.value = false }
}
function handleTabChange() { pagination.pageNum = 1; loadData() }
function handleQuery() { pagination.pageNum = 1; loadData() }
function handleReset() { query.materialName = ''; pagination.pageNum = 1; loadData() }

const dialogVisible = ref(false); const dialogTitle = ref(''); const submitLoading = ref(false)
const defForm = () => ({ id: undefined as any, materialName: '', materialTypeId: undefined as any, supplierIdArr: [] as number[], unit: 'PCS', price: undefined as any, remark: '' })
const form = reactive(defForm()); const isEdit = ref(false)

// ==================== 研发支出（2026-09-27 用户口径：新增物料时提示"要不要根据物料新增研发支出"） ====================
// 交互：新增弹窗内勾选 + 就地展开（一次提交，物料与费用一起保存）；**编辑时不显示**（只针对"新增物料"）。
// 落库：勾选后调 POST /outsource/material/{id}/rd-expense → 后端复用费用单 create 落**草稿**（FY 单号、不扣款），
// 资金在「财务管理 → 费用管理」审核时才动；同一物料已登记过则后端幂等回原单。
// 金额：默认带出物料单价（可改，必填 >0）；账户：必填（下拉取资金账户主数据）。
const rdForm = reactive({ enabled: false, amount: undefined as any, accountId: undefined as any, expenseDate: localDate(), remark: '' })
const rdAccounts = ref<FinanceAccount[]>([])
async function loadRdAccounts() {
  try {
    const r: any = await getAccountPage({ pageSize: 200 })
    rdAccounts.value = (r?.records || []).filter((a: any) => a.status === 1)
  } catch { rdAccounts.value = [] }
}
/** 勾选时默认带出物料单价（备注留空 ⇒ 由后端补「研发支出：物料名」，避免物料改名后备注过期） */
function onRdToggle(v: any) {
  if (v && rdForm.amount == null && form.price) rdForm.amount = form.price
}

// 子物料组成
const bomRows = ref<any[]>([])
/**
 * F7-130（2026-09-20）：组件**是否成功加载**。后端 `saveComponents` 是"**全量替换**"
 * （先删该父物料全部组件再插入，空数组与 null 都等于清空），而本页"保存主数据"时**无条件**调用它。
 * 原实现把加载失败静默成 `bomRows = []`（与"该物料本就没有组件"无法区分）⇒ **改个物料名称就会把 BOM 删光**。
 * 现以本哨兵标记：**只有加载成功（或新增场景）才允许提交组件**，失败则跳过并提示。
 */
const bomLoaded = ref(false)
function addBomRow() { bomRows.value.push({ childMaterialId: undefined, quantity: 1, lossRate: 0, remark: '' }) }
function removeBomRow(idx: number) { bomRows.value.splice(idx, 1) }
async function loadComponents(materialId: number) {
  try {
    const r = await request.get<any, any>(`/outsource/material/${materialId}/components`)
    bomRows.value = (r || []).map((c: any) => ({ childMaterialId: c.childMaterialId, quantity: c.quantity ?? 1, lossRate: c.lossRate ?? 0, remark: c.remark || '' }))
    bomLoaded.value = true
  } catch (e: any) {
    // 失败 ⇒ 不置为"空 BOM"语义，而是标记为"未加载"：本次保存**不会**提交组件
    bomLoaded.value = false
    bomRows.value = []
    ElMessage.error('子物料组成加载失败，为避免误清空，本次保存不会修改子物料组成：' + (e?.message || '未知错误'))
  }
}
async function saveComponents(materialId: number) {
  const valid = bomRows.value.filter(r => r.childMaterialId)
  await request.put(`/outsource/material/${materialId}/components`, valid)
}

// 加载全部物料（不受TAB过滤），供子物料下拉框使用
async function loadAllMaterials() {
  const r = await request.get('/outsource/material/page', { params: { pageSize: 500 } })
  allMaterials.value = r?.records || r || []
}

function handleAdd() {
  Object.assign(form, defForm()); bomRows.value = []; bomLoaded.value = true; isEdit.value = false
  dialogTitle.value = '新增物料'; dialogVisible.value = true
  // 研发支出（仅新增）：每次打开都重置，并备好支出账户下拉
  Object.assign(rdForm, { enabled: false, amount: undefined, accountId: undefined, expenseDate: localDate(), remark: '' })
  loadRdAccounts()
  loadAllMaterials()
}
async function handleEdit(row: any) {
  Object.assign(form, defForm(), row)
  // 2026-09-21：「所属项目」下线 ⇒ 不再回填 projectIdArr，也不再预取项目名（用于拼 projectName 的那段已删）
  form.supplierIdArr = (row.supplierIds || '').split(',').filter(Boolean).map(Number)
  isEdit.value = true; dialogTitle.value = '编辑物料'; dialogVisible.value = true
  loadAllMaterials()
  loadComponents(row.id)
}

async function handleSubmit() {
  if (!form.materialName) { ElMessage.warning('请输入物料名称'); return }
  if (!form.materialTypeId) { ElMessage.warning('请选择物料类型'); return }
  // 2026-09-27：勾选「同时登记一笔研发支出」时的前端校验（后端同口径再校验一次）
  if (!isEdit.value && rdForm.enabled) {
    if (!rdForm.amount || Number(rdForm.amount) <= 0) { ElMessage.warning('研发支出金额必须大于 0'); return }
    if (!rdForm.accountId) { ElMessage.warning('研发支出必须选择支出账户'); return }
  }
  // 2026-09-21：「所属项目」下线 ⇒ 提交体不再拼 projectIds / projectName
  const sIds = form.supplierIdArr.join(',')
  const body = { ...form, supplierIds: sIds }
  submitLoading.value = true
  try {
    if (isEdit.value) { await request.put('/outsource/material', body); ElMessage.success('已更新') }
    else { const res = await request.post('/outsource/material', body) as any; form.id = res }
    // F7-130（2026-09-20）：**仅当组件已成功加载（或新增）时才提交** —— 后端是全量替换，
    // 若加载失败仍提交空数组会把已有 BOM 清空（原实现即此缺陷）。
    if (form.id && bomLoaded.value) { await saveComponents(form.id) }
    else if (form.id && !bomLoaded.value) { ElMessage.warning('子物料组成未加载成功，本次保存未修改子物料组成') }
    dialogVisible.value = false; loadData()
    // 2026-09-27（用户需求）：新增物料后按需登记研发支出（**物料已建成**再登记，失败可重试；后端幂等）
    if (!isEdit.value && rdForm.enabled && form.id) await createRdExpense(form.id)
  } finally { submitLoading.value = false }
}

/**
 * 为该物料登记一笔「研发支出」（2026-09-27 用户需求；同日追加：勾选路径**需要自动审核**）。
 *
 * <p>顺序：物料 + 子物料组成**已保存成功**后再调本接口 ⇒ 不会出现"费用建了、物料没建"；
 * 反过来（物料建了、费用失败）由下方 confirm 重试兜底，且后端按 source 幂等，重试不会重复扣款。</p>
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
    const r: any = await request.post(`/outsource/material/${materialId}/rd-expense`, payload)
    const no = r?.expenseNo ? `（单号 ${r.expenseNo}）` : ''
    if (r?.audited) {
      ElMessage.success(`${r?.existing ? '该物料已登记过研发支出' : '研发支出已登记并自动审核'}${no}，已从支出账户扣款`)
    } else {
      // 兜底：勾选路径按口径一定要求审核，走到这里说明后端返回异常，明确提示"未扣款"避免误判
      ElMessage.warning(`${r?.existing ? '该物料已登记过研发支出' : '研发支出已登记'}${no}，尚未扣款（请在「财务管理 → 费用管理」审核后扣款）`)
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
async function handleDelete(row: any) { try { await ElMessageBox.confirm('确定删除？', '提示', { type: 'warning' }); await request.delete(`/outsource/material/${row.id}`); ElMessage.success('已删除'); loadData() } catch (e: any) { if (e !== 'cancel' && e !== 'close') { console.error(e) } } }

// ==================== 列表行操作：为已有物料**补登记**研发支出（2026-09-27 用户口径） ====================
// 场景：新增物料时没勾选（或当时不确定要记多少），事后在列表里补登记。
// 与「新增弹窗内勾选」共用同一个后端端点 `POST /outsource/material/{id}/rd-expense`，同样只落**草稿**
// （资金在费用管理审核时才动）；同一物料重复登记由后端幂等回原单，消息里给出原单号。
const rdDialog = ref(false)
const rdSubmitting = ref(false)
const rdRow = ref<any>(null)
const rdRowForm = reactive({ amount: undefined as any, accountId: undefined as any, expenseDate: localDate(), remark: '' })

function handleRdExpense(row: any) {
  rdRow.value = row
  // 金额默认带出物料单价（可改）；账户每次重置，避免沿用上一次的选择
  Object.assign(rdRowForm, { amount: row?.price || undefined, accountId: undefined, expenseDate: localDate(), remark: '' })
  loadRdAccounts()
  rdDialog.value = true
}

async function submitRdExpense() {
  if (!rdRowForm.amount || Number(rdRowForm.amount) <= 0) { ElMessage.warning('研发支出金额必须大于 0'); return }
  if (!rdRowForm.accountId) { ElMessage.warning('研发支出必须选择支出账户'); return }
  rdSubmitting.value = true
  try {
    const r: any = await request.post(`/outsource/material/${rdRow.value.id}/rd-expense`, { ...rdRowForm })
    const no = r?.expenseNo ? `（单号 ${r.expenseNo}）` : ''
    ElMessage.success(`${r?.existing ? '该物料已登记过研发支出' : '研发支出已存为草稿'}${no}，请在「财务管理 → 费用管理」审核后才扣款`)
    rdDialog.value = false
  } finally { rdSubmitting.value = false }
}

const router = useRouter()
const route = useRoute()

// 下拉「+ 新增」项：识别到标记后移除占位并跳转到对应列表页
// （原「所属项目」的 onProjectChange 已随字段下线一并删除）
function onSupplierChange(val: any[]) {
  if (val.includes(ADD_MARKER)) {
    form.supplierIdArr = val.filter(v => v !== ADD_MARKER)
    // 跳转到供应商管理列表页（与菜单 routePath /supplier/manage 保持一致）
    router.push('/supplier/manage')
  }
}

// 支持从外部跳转（如研发项目改配信息"+ 新增"）定位到对应 物料类型 TAB
onMounted(async () => {
  await loadOptions()
  const q = route.query.materialTypeId
  if (q != null && q !== '') {
    const id = Number(q)
    if (MATERIAL_TYPES.value.some(t => t.id === id)) activeTab.value = id
  }
  loadData()
})

</script>

<template>
  <div class="page-list">
    <el-card shadow="never" class="query-card">
      <div class="query-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="物料名称"><el-input v-model="query.materialName" placeholder="物料名称" clearable @keyup.enter="handleQuery" /></el-form-item>
      </el-form>
      <div class="toolbar">
        <el-button type="primary" :icon="'Search'" @click="handleQuery">查询</el-button>
        <el-button :icon="'Refresh'" @click="handleReset">重置</el-button>
        <el-button type="success" :icon="'Plus'" @click="handleAdd">新增</el-button>
      </div>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-tabs v-model="activeTab" type="border-card" @tab-change="handleTabChange">
        <el-tab-pane label="全部" name="全部" />
        <el-tab-pane v-for="t in MATERIAL_TYPES" :key="t.id" :label="t.typeName" :name="t.id" />
      </el-tabs>

      <!-- 2026-09-21：列改版 —— 去掉「所属项目」「库存总量」「未交数量」三列（剩 6 列：物料类型/物料名称/供应商/单位/单价/操作） -->
      <el-table :data="tableData" border stripe v-loading="tableLoading">
        <el-table-column prop="materialTypeName" label="物料类型" width="100" />
        <!-- 2026-09-25 B1：物料 → 物料库存分布详情（含物料档案摘要）；供应商 → 供应商详情（多选时弹层逐项可点） -->
        <el-table-column label="物料名称" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button type="primary" link @click.stop="router.push(`/outsource/material-stock/detail/${row.id}`)">{{ row.materialName }}</el-button>
          </template>
        </el-table-column>
        <el-table-column label="供应商" width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <EntityLinks :items="supplierItems(row.supplierIds)" target="supplier">
              <span>{{ supplierNames(row.supplierIds) }}</span>
            </EntityLinks>
          </template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" width="70" />
        <el-table-column prop="price" label="单价" width="90" />
        <!-- 2026-09-27：操作列加「研发支出」（130→190）—— 给"新增时没勾选、事后补登记"的场景 -->
        <el-table-column label="操作" width="190" align="center" fixed="right">
          <template #default="{row}"><el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button><el-button type="primary" link size="small" @click="handleRdExpense(row)">研发支出</el-button><el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button></template>
        </el-table-column>
      </el-table>
      <div class="pagination">
        <el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50, 100]" :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper" background
          @size-change="handleQuery" @current-change="loadData" />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="var(--app-dialog-md)" :close-on-click-modal="false">
      <el-form :model="form" label-width="90px">
        <el-form-item required label="物料类型"><RemoteSelect v-model="form.materialTypeId" :fetch="fetchMaterialTypes" :label-key="(t: any) => t.typeName" placeholder="请选择" style="width:100%" /></el-form-item>
        <el-form-item label="物料名称" required><el-input v-model="form.materialName" /></el-form-item>
        <el-form-item label="供应商"><RemoteSelect v-model="form.supplierIdArr" multiple :fetch="fetchSuppliers" placeholder="可多选" style="width:100%" @change="onSupplierChange"><el-option label="+ 新增" :value="ADD_MARKER" /></RemoteSelect></el-form-item>
        <el-form-item label="单位"><el-input v-model="form.unit" /></el-form-item>
        <el-form-item label="单价"><el-input-number v-model="form.price" :precision="2" :min="0" controls-position="right" style="width:100%" placeholder="可选" /></el-form-item>

        <!-- 2026-09-27（用户需求）：新增物料时提示"要不要根据物料新增研发支出"。
             ① 仅**新增**显示（编辑不动）；② 勾选后就地展开，与物料**一次提交**；
             ③ 落库是**草稿**费用单（类型=研发支出），资金要到「财务管理 → 费用管理」审核时才动。 -->
        <el-form-item v-if="!isEdit" label="研发支出">
          <el-checkbox v-model="rdForm.enabled" @change="onRdToggle">同时登记一笔研发支出</el-checkbox>
          <div v-if="!rdForm.enabled" style="color:var(--app-text-secondary);font-size:var(--app-font-xs);line-height:1.5">勾选后填金额与支出账户，保存物料时一并登记一张「研发支出」并<b>自动审核</b>（当场从该账户扣款；余额不足会提示，可换账户重试）</div>
        </el-form-item>
        <template v-if="!isEdit && rdForm.enabled">
          <el-form-item required label="支出金额"><el-input-number v-model="rdForm.amount" :precision="2" :min="0.01" controls-position="right" style="width:100%" placeholder="默认取物料单价" /></el-form-item>
          <el-form-item required label="支出账户">
            <el-select v-model="rdForm.accountId" placeholder="请选择" style="width:100%">
              <el-option v-for="a in rdAccounts" :key="a.id" :label="`${a.accountName}（余额 ${Number((a as any).balance ?? 0).toFixed(2)}）`" :value="a.id ?? ''" />
            </el-select>
          </el-form-item>
          <el-form-item label="费用日期"><el-date-picker v-model="rdForm.expenseDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
          <el-form-item label="费用备注"><el-input v-model="rdForm.remark" placeholder="留空自动填「研发支出：物料名」" /></el-form-item>
        </template>

        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>

      <el-divider content-position="left"><span style="font-weight:600;font-size:var(--app-font-base)">子物料组成</span></el-divider>
      <div style="margin-bottom:8px">
        <el-button type="primary" size="small" @click="addBomRow">+ 添加子物料</el-button>
        <span style="color:var(--app-text-secondary);font-size:var(--app-font-base);margin-left:8px">共 {{ bomRows.length }} 项</span>
      </div>
      <el-table :data="bomRows" border stripe empty-text="暂无子物料" max-height="280">
        <el-table-column label="子物料" min-width="220">
          <template #default="{ row }">
            <el-select v-model="row.childMaterialId" filterable placeholder="选择已有物料" style="width:100%" size="small">
              <el-option v-for="m in allMaterials" :key="m.id" :label="`${m.materialName} (${m.materialTypeName || ''})`" :value="m.id" :disabled="m.id === form.id" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="用量" width="90">
          <template #default="{ row }"><el-input-number v-model="row.quantity" :controls="false" :precision="0" :step="1" size="small" style="width:100%" /></template>
        </el-table-column>
        <el-table-column label="损耗率%" width="100">
          <template #default="{ row }"><el-input v-model="row.lossRate" size="small" style="width:100%" /></template>
        </el-table-column>
        <el-table-column label="备注" min-width="120">
          <template #default="{ row }"><el-input v-model="row.remark" placeholder="备注" size="small" /></template>
        </el-table-column>
        <el-table-column label="操作" width="70" align="center" fixed="right">
          <template #default="{ $index }"><el-button type="danger" link size="small" @click="removeBomRow($index)">删除</el-button></template>
        </el-table-column>
      </el-table>

      <template #footer><el-button @click="dialogVisible=false">取消</el-button><el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button></template>
    </el-dialog>

    <!-- 列表行操作：为已有物料补登记研发支出（2026-09-27）。落草稿、审核才扣款；重复登记由后端幂等回原单。 -->
    <el-dialog v-model="rdDialog" title="登记研发支出" width="var(--app-dialog-sm)" :close-on-click-modal="false">
      <el-form :model="rdRowForm" label-width="90px">
        <el-form-item label="物料"><span style="font-weight:600">{{ rdRow?.materialName }}</span></el-form-item>
        <el-form-item required label="支出金额"><el-input-number v-model="rdRowForm.amount" :precision="2" :min="0.01" controls-position="right" style="width:100%" placeholder="默认取物料单价" /></el-form-item>
        <el-form-item required label="支出账户">
          <el-select v-model="rdRowForm.accountId" placeholder="请选择" style="width:100%">
            <el-option v-for="a in rdAccounts" :key="a.id" :label="`${a.accountName}（余额 ${Number((a as any).balance ?? 0).toFixed(2)}）`" :value="a.id ?? ''" />
          </el-select>
        </el-form-item>
        <el-form-item label="费用日期"><el-date-picker v-model="rdRowForm.expenseDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
        <el-form-item label="费用备注"><el-input v-model="rdRowForm.remark" placeholder="留空自动填「研发支出：物料名」" /></el-form-item>
      </el-form>
      <div style="color:var(--app-text-secondary);font-size:var(--app-font-xs);line-height:1.5">保存为草稿费用单，需在「财务管理 → 费用管理」审核后才扣款；同一物料只会保留一张研发支出。</div>
      <template #footer><el-button @click="rdDialog=false">取消</el-button><el-button type="primary" :loading="rdSubmitting" @click="submitRdExpense">确定</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
/* 根容器/卡片内边距/分页样式已统一到全局（styles/page.css 的 .page-list / .table-card .el-card__body / .pagination） */
</style>
