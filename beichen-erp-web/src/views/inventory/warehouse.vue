<script setup lang="ts">
import { WarehouseCategory, WarehouseType, WarehouseTypeLabel } from '@/api/enums'
import { reactive, ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { getWarehouseTakeStatus, type StockTakeStatus } from '@/api/inventory'

const router = useRouter()

// 月度盘点状态：仅用于表格「本月盘点 / 上次盘点」两列
const takeStatus = ref<StockTakeStatus[]>([])
const takeMap = computed(() => {
  const m: Record<number, StockTakeStatus> = {}
  takeStatus.value.forEach(s => { if (s.warehouseId) m[s.warehouseId] = s })
  return m
})
async function loadTakeStatus() { try { takeStatus.value = await getWarehouseTakeStatus() } catch { takeStatus.value = [] } }

const query = reactive({ warehouseName: '', warehouseType: '' })
const allData = ref<any[]>([])
const activeTab = ref('active')
const tableLoading = ref(false)
// 仓型下拉（value=code，与后端 WarehouseType 枚举一致）
// 2026-09-16 方案 A：仓型收敛为「成品仓 / 辅料仓」两种；本页只管自有**成品仓**（辅料仓在「物料仓库 → 自有物料仓」），
// 原「不良仓 / 售后仓」已取消（退回品与不良品统一入成品仓、按品质区分）
const WARHOUSE_TYPES: Record<string, string> = { FINISHED: '成品仓' }
const activeData = computed(() => allData.value.filter(v => v.status === 1))
const stoppedData = computed(() => allData.value.filter(v => v.status === 0))

async function loadData() {
  tableLoading.value = true
  try {
    const p: any = { pageSize: 500, warehouseCategory: WarehouseCategory.INVENTORY }
    if (query.warehouseName) p.warehouseName = query.warehouseName
    if (query.warehouseType) p.warehouseType = query.warehouseType
    const r = await request.get<any, any>('/warehouse/page', { params: p })
    // 成品仓管理排除「辅料仓」（物料仓已在「自有物料仓」菜单单独管理）
    allData.value = (r?.records || []).filter((v: any) => v.warehouseType !== WarehouseType.AUXILIARY)
  } finally { tableLoading.value = false }
}
function handleQuery() { loadData() }
function handleReset() { query.warehouseName = ''; query.warehouseType = ''; loadData() }

const dialogVisible = ref(false); const dialogTitle = ref(''); const submitLoading = ref(false)
// 仓型默认「成品仓」：本页只建自有成品仓（2026-09-16 方案 A 后仓型仅 成品仓/辅料仓 两种）
const defForm = () => ({ id: undefined as any, code: '', warehouseName: '', warehouseType: WarehouseType.FINISHED, address: '', manager: '', phone: '', status: 1, remark: '' })
const form = reactive(defForm()); const isEdit = ref(false)

function handleAdd() { Object.assign(form, defForm()); isEdit.value = false; dialogTitle.value = '新增仓库'; dialogVisible.value = true }
function handleEdit(row: any) { Object.assign(form, defForm(), row); isEdit.value = true; dialogTitle.value = '编辑仓库'; dialogVisible.value = true }

async function handleSubmit() { if (!form.warehouseName) { ElMessage.warning('请输入仓库名称'); return }; if (!form.warehouseType) { ElMessage.warning('请选择仓型'); return }; submitLoading.value = true
  try { if (isEdit.value) { await request.put('/warehouse', form); ElMessage.success('已更新') } else { await request.post('/warehouse', form); ElMessage.success('已新增') }
    dialogVisible.value = false; loadData() } finally { submitLoading.value = false } }

function handleDetail(row: any) { router.push(`/inventory/warehouse/detail/${row.id}`) }

// F7-29（2026-09-19）：改为"请求成功后再改本地状态"。
// 原先先翻转 row.status 再 PUT，且无 catch ⇒ 请求失败时列表本地状态已翻转、与后端不一致（直到下次刷新）。
async function handleToggleStatus(row: any) {
  const next = row.status === 1 ? 0 : 1
  try {
    await request.put('/warehouse', { ...row, status: next })
    ElMessage.success(next === 1 ? '已启用' : '已停用')
    loadData()
  } catch { ElMessage.error('状态切换失败，请重试') }
}

onMounted(() => { loadData(); loadTakeStatus() })

</script>

<template>
  <div class="wh-page">
    <el-card shadow="never" class="query-card">
      <div class="query-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="仓库名称"><el-input v-model="query.warehouseName" placeholder="仓库名称" clearable @keyup.enter="handleQuery" /></el-form-item>
        <el-form-item label="仓型"><el-select v-model="query.warehouseType" placeholder="全部" clearable style="width:120px"><el-option v-for="(lb, code) in WARHOUSE_TYPES" :key="code" :label="lb" :value="code" /></el-select></el-form-item>
        </el-form>
        <div class="toolbar">
          <el-button type="primary" :icon="'Search'" @click="handleQuery">查询</el-button>
          <el-button :icon="'Refresh'" @click="handleReset">重置</el-button>
          <el-button type="success" :icon="'Plus'" @click="handleAdd">新增</el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-tabs v-model="activeTab" type="border-card">
        <el-tab-pane label="启用" name="active" />
        <el-tab-pane label="停用" name="stopped" />
      </el-tabs>

      <el-table v-if="activeTab==='active'" :data="activeData" border stripe v-loading="tableLoading" style="width:100%" @row-click="handleDetail">
        <el-table-column prop="code" label="仓库编码" width="160" />
        <el-table-column prop="warehouseName" label="仓库名称" min-width="140" show-overflow-tooltip />
        <el-table-column label="仓型" width="90"><template #default="{row}">{{ WarehouseTypeLabel[row.warehouseType] || row.warehouseType }}</template></el-table-column>
        <el-table-column label="本月盘点" width="120" align="center">
          <template #default="{row}">
            <el-tag v-if="takeMap[row.id]?.taken" type="success" size="small">已盘点</el-tag>
            <el-tag v-else-if="(takeMap[row.id]?.overdueDays || 0) > 0" type="danger" size="small">
              超期 {{ takeMap[row.id]?.overdueDays }} 天
            </el-tag>
            <el-tag v-else type="warning" size="small">待盘点</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="上次盘点" width="110" align="center">
          <template #default="{row}">{{ takeMap[row.id]?.lastTakeDate ? String(takeMap[row.id].lastTakeDate).slice(0,10) : '—' }}</template>
        </el-table-column>
        <el-table-column prop="address" label="地址" min-width="150" show-overflow-tooltip />
        <el-table-column prop="manager" label="负责人" width="80" />
        <el-table-column prop="phone" label="联系电话" width="120" />
        <el-table-column label="操作" width="155" align="center">
          <template #default="{row}"><el-button type="primary" link @click.stop="handleDetail(row)">详情</el-button><el-button type="primary" link @click.stop="handleEdit(row)">编辑</el-button><el-button type="warning" link @click.stop="handleToggleStatus(row)">停用</el-button></template>
        </el-table-column>
      </el-table>

      <el-table v-if="activeTab==='stopped'" :data="stoppedData" border stripe style="width:100%" @row-click="handleDetail">
        <el-table-column prop="code" label="仓库编码" width="160" />
        <el-table-column prop="warehouseName" label="仓库名称" min-width="140" show-overflow-tooltip />
        <el-table-column label="仓型" width="90"><template #default="{row}">{{ WarehouseTypeLabel[row.warehouseType] || row.warehouseType }}</template></el-table-column>
        <el-table-column prop="address" label="地址" min-width="150" show-overflow-tooltip />
        <el-table-column prop="manager" label="负责人" width="80" />
        <el-table-column prop="phone" label="联系电话" width="120" />
        <el-table-column label="操作" width="155" align="center">
          <template #default="{row}"><el-button type="primary" link @click.stop="handleDetail(row)">详情</el-button><el-button type="primary" link @click.stop="handleEdit(row)">编辑</el-button><el-button type="success" link @click.stop="handleToggleStatus(row)">启用</el-button></template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="仓库名称" required><el-input v-model="form.warehouseName" /></el-form-item>
        <el-form-item label="仓型" required>
          <el-select v-model="form.warehouseType" placeholder="请选择仓型" style="width:100%"><el-option v-for="(lb, code) in WARHOUSE_TYPES" :key="code" :label="lb" :value="code" /></el-select>
          <!-- I5 提示文案（2026-09-18）：本页只管成品仓，辅料仓在另一处菜单，避免按字面误建 -->
          <div style="color:var(--app-text-secondary);font-size:var(--app-font-xs);line-height:1.5">本页只管理<b>成品仓</b>；辅料仓（自有物料仓）请在「物料仓库 → 自有物料仓」创建与管理</div>
        </el-form-item>
        <el-form-item label="地址"><el-input v-model="form.address" /></el-form-item>
        <el-form-item label="负责人"><el-input v-model="form.manager" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible=false">取消</el-button><el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.wh-page { display:flex; flex-direction:column; gap:12px; }
.query-card :deep(.el-card__body), .table-card :deep(.el-card__body) { padding:16px; }
</style>
