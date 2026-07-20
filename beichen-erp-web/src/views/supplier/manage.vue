<script setup lang="ts">
import { reactive, ref, onMounted, onActivated, watch, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

const router = useRouter()
const activeType = ref('all')

const TYPE_TABS = [
  { name: 'all', label: '全部' },
  { name: 'solution', label: '方案商' },
  { name: 'factory', label: '委外加工厂' },
  { name: 'material', label: '辅料商' },
  { name: 'product', label: '成品供应商' },
]

const TYPE_OPTIONS = TYPE_TABS.filter(x => x.name !== 'all')

const TYPE_MAP: Record<string, string> = { solution: '方案商', factory: '加工厂', material: '辅料商', product: '成品商' }
const TYPE_TAG: Record<string, string> = { solution: 'primary', factory: 'warning', material: 'info', product: 'success' }

const query = reactive({ name: '', phone: '', status: undefined as any })
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const tableData = ref<any[]>([])
const loading = ref(false)

async function loadData() {
  loading.value = true
  try {
    const p: any = { pageNum: pagination.pageNum, pageSize: pagination.pageSize }
    if (query.name) p.name = query.name
    if (query.phone) p.phone = query.phone
    if (activeType.value !== 'all') p.supplierType = activeType.value
    if (query.status !== undefined) p.status = query.status
    const r = await request.get<any, any>('/supplier/page', { params: p })
    tableData.value = r?.records || []
    pagination.total = r?.total || 0
  } finally { loading.value = false }
}

function handleQuery() { pagination.pageNum = 1; loadData() }
function handleReset() { query.name = ''; query.phone = ''; query.status = undefined; handleQuery() }
watch(activeType, () => { pagination.pageNum = 1; loadData() })

const dialogVisible = ref(false); const dialogTitle = ref(''); const saving = ref(false)
const form = reactive({
  id: undefined as any, name: '', contact: '', phone: '', address: '', remark: '',
  supplierType: '', checkedTypes: [] as string[], status: 1,
  creditPeriodMonths: undefined as any, creditPeriod: undefined as any
})
const isEdit = ref(false)

// 计算是否选中了某类型
const isType = computed(() => (type: string) => form.checkedTypes.includes(type))

function resetForm() {
  Object.assign(form, { id: undefined, name: '', contact: '', phone: '', address: '', remark: '',
    supplierType: '', checkedTypes: [], status: 1,
    creditPeriodMonths: undefined, creditPeriod: undefined })
}

function handleAdd() {
  resetForm(); isEdit.value = false; dialogTitle.value = '新增供应商'
  if (activeType.value !== 'all') form.checkedTypes = [activeType.value]
  dialogVisible.value = true
}

function handleEdit(row: any) {
  resetForm()
  const types = (row.supplierType || '').split(',').map((t: string) => t.trim()).filter(Boolean)
  Object.assign(form, {
    id: row.id, name: row.name || '', contact: row.contact || '', phone: row.phone || '',
    address: row.address || '', remark: row.remark || '', supplierType: row.supplierType || '',
    checkedTypes: types,
    status: row.status ?? 1,
    creditPeriodMonths: row.creditPeriodMonths, creditPeriod: row.creditPeriod
  })
  isEdit.value = true; dialogTitle.value = '编辑供应商'
  dialogVisible.value = true
}

// 解析逗号分隔的类型显示
function parseTypes(types: string): string[] {
  if (!types) return []
  return types.split(',').map(t => t.trim()).filter(Boolean)
}

async function handleSubmit() {
  if (!form.name) { ElMessage.warning('请输入名称'); return }
  if (form.checkedTypes.length === 0) { ElMessage.warning('请选择至少一个类型'); return }
  saving.value = true
  try {
    const body: any = { ...form, supplierType: form.checkedTypes.join(',') }
    if (isEdit.value) { await request.put('/supplier', body); ElMessage.success('已更新') }
    else { await request.post('/supplier', body); ElMessage.success('已添加') }
    dialogVisible.value = false; loadData()
  } catch (e: any) { ElMessage.error(e?.message || '保存失败') } finally { saving.value = false }
}

async function handleDelete(row: any) {
  // 先检查关联数据
  try {
    const checkRes = await request.get<any, any>(`/supplier/${row.id}/check-delete`)
    if (checkRes && !checkRes.canDelete) {
      const list = checkRes.associations || {}
      const detail = Object.entries(list).map(([k, v]) => `${k}：${v}条`).join('；')
      ElMessage.warning(`该供应商还有关联数据，无法删除（${detail}）。请先清理关联数据后再操作。`, { duration: 5000 })
      return
    }
  } catch {
    // check-delete 失败时，让后端 delete 端点自行校验
  }
  try {
    await ElMessageBox.confirm(`确定删除「${row.name}」吗？`, '提示', { type: 'warning' })
    await request.delete(`/supplier/${row.id}`)
    ElMessage.success('已删除')
    loadData()
  } catch (e: any) { if (e !== 'cancel' && e !== 'close') { ElMessage.error(e?.message || '删除失败') } }
}

onMounted(loadData)
onActivated(loadData)
</script>

<template>
  <div class="sup-page">
    <el-card shadow="never" class="query-card">
      <el-tabs v-model="activeType">
        <el-tab-pane v-for="t in TYPE_TABS" :key="t.name" :label="t.label" :name="t.name" />
      </el-tabs>
      <el-form :inline="true" :model="query">
        <el-form-item label="名称"><el-input v-model="query.name" placeholder="供应商名称" clearable @keyup.enter="handleQuery" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="query.phone" placeholder="手机号" clearable @keyup.enter="handleQuery" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="query.status" placeholder="全部" clearable style="width:100px"><el-option label="合作中" :value="1" /><el-option label="已停用" :value="0" /></el-select></el-form-item>
        <el-form-item><el-button type="primary" @click="handleQuery">查询</el-button><el-button @click="handleReset">重置</el-button><el-button type="success" @click="handleAdd">新增</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table :data="tableData" border stripe v-loading="loading">
        <el-table-column label="类型" width="180">
          <template #default="{ row }">
            <el-tag v-for="t in parseTypes(row.supplierType)" :key="t" size="small" style="margin-right:4px"
              :type="(TYPE_TAG[t]||'info') as any"
            >{{ TYPE_MAP[t] || t }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="contact" label="联系人" width="100" />
        <el-table-column prop="phone" label="电话" width="130" />
        <el-table-column label="状态" width="80" align="center"><template #default="{row}"><el-tag size="small" :type="row.status===1?'success':'danger'">{{ row.status===1?'启用':'停用' }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="{row}">
            <el-button type="primary" link size="small" @click="router.push(`/supplier/detail/${row.id}`)">详情</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination"><el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize" :total="pagination.total" :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next" background @current-change="loadData" @size-change="handleQuery" /></div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="640px" :close-on-click-modal="false">
      <el-form :model="form" label-width="90px" size="small">
        <el-form-item label="类型" required>
          <el-checkbox-group v-model="form.checkedTypes">
            <el-checkbox v-for="t in TYPE_OPTIONS" :key="t.name" :label="t.name" :value="t.name">{{ t.label }}</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="名称" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="联系人"><el-input v-model="form.contact" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="地址"><el-input v-model="form.address" type="textarea" :rows="2" /></el-form-item>

        <el-form-item label="状态"><el-select v-model="form.status" style="width:100%"><el-option label="合作中" :value="1" /><el-option label="已停用" :value="0" /></el-select></el-form-item>
        <el-form-item label="账期">
          <div style="display:flex;align-items:center;gap:6px">
            <el-input-number v-model="form.creditPeriodMonths" :min="0" :max="24" placeholder="月" controls-position="right" style="width:90px" /><span>个月</span>
            <el-input-number v-model="form.creditPeriod" :min="0" :max="31" placeholder="天" controls-position="right" style="width:90px" /><span>天</span>
            <span style="color:#909399;font-size:12px">（收货/交货后多少天付款，默认当天）</span>
          </div>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="handleSubmit">确定</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.sup-page { display:flex; flex-direction:column; gap:12px; }
.query-card :deep(.el-card__body), .table-card :deep(.el-card__body) { padding:16px; }
.pagination { margin-top:16px; display:flex; justify-content:flex-end; }
</style>
