<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { ADD_MARKER } from '@/composables/useSelectWithAdd'
import EntityLinks from '@/components/EntityLinks.vue'
import RemoteSelect from '@/components/RemoteSelect.vue'

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

// ==================== 研发支出（2026-09-28 已从本页移除） ====================
// 用户口径：**该功能属于「研发管理 → 研发物料」** —— 研发物料的新增弹窗勾选 / 列表行补登记，
// 走 `POST /api/dev/purchase-item/{id}/rd-expense`（来源类型 RD_DEV_MATERIAL）。
// 原先挂在「物料信息管理」是放错了地方：委外物料（outsource_material）与研发物料（dev_purchase_item）
// 是两张表、两套 id 空间，费用挂错主体、幂等也会串号。

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
  } finally { submitLoading.value = false }
}

async function handleDelete(row: any) { try { await ElMessageBox.confirm('确定删除？', '提示', { type: 'warning' }); await request.delete(`/outsource/material/${row.id}`); ElMessage.success('已删除'); loadData() } catch (e: any) { if (e !== 'cancel' && e !== 'close') { console.error(e) } } }

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
        <!-- 2026-09-28（用户口径）：操作列去掉「研发支出」（190→130，回到加它之前的宽度）——
             研发支出属「研发管理 → 研发物料」，本页（委外物料）不再提供该入口 -->
        <el-table-column label="操作" width="130" align="center" fixed="right">
          <template #default="{row}"><el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button><el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button></template>
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
        <el-form-item required label="物料类型"><RemoteSelect v-model="form.materialTypeId" add-route="/dev/material-type" :fetch="fetchMaterialTypes" :label-key="(t: any) => t.typeName" placeholder="请选择" style="width:100%" domain="materialType" /></el-form-item>
        <el-form-item label="物料名称" required><el-input v-model="form.materialName" /></el-form-item>
        <el-form-item label="供应商"><RemoteSelect v-model="form.supplierIdArr" multiple :fetch="fetchSuppliers" placeholder="可多选" style="width:100%" @change="onSupplierChange" domain="supplier" ><el-option label="+ 新增" :value="ADD_MARKER" /></RemoteSelect></el-form-item>
        <el-form-item label="单位"><el-input v-model="form.unit" /></el-form-item>
        <el-form-item label="单价"><el-input-number v-model="form.price" :precision="2" :min="0" controls-position="right" style="width:100%" placeholder="可选" /></el-form-item>

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
            
                <template #footer><div style="padding:6px 12px;cursor:pointer;text-align:center;font-size:12px;color:var(--app-color-primary,#409eff);border-top:1px solid var(--app-color-border,#ebeef5)" @click="$router.push('/outsource/material-info')">+ 鏂板</div></template>
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
  </div>
</template>

<style scoped>
/* 根容器/卡片内边距/分页样式已统一到全局（styles/page.css 的 .page-list / .table-card .el-card__body / .pagination） */
</style>
