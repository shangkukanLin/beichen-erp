<script setup lang="ts">
import { reactive, ref, onMounted, watch, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

const router = useRouter()
const route = useRoute()
import { TYPE_TABS, TYPE_OPTIONS, TYPE_MAP, TYPE_TAG } from '@/constants/supplier'

// 双模式：/supplier/manage=供应商（方案商/加工厂/辅料商）；/outsource/supplier/manage=供货商（成品商）
const isVendor = route.path === '/outsource/supplier/manage'
/**
 * 本页双模式（供应商 / 供货商）共用一套模板 ⇒ 名称/编码这类带主体前缀的标签必须**动态**，
 * 否则供货商页面会显示成「供应商名称」（2026-09-23 字段口径统一时踩到的点）。
 */
const entityLabel = computed(() => (isVendor ? '供货商' : '供应商'))
const activeType = ref(isVendor ? 'product' : 'all')
const TYPE_TABS_CUSTOM = isVendor
  ? [{ name: 'product', label: TYPE_MAP.product }]
  : TYPE_TABS.filter(t => t.name === 'all' || t.name !== 'product')
const TYPE_OPTIONS_CUSTOM = isVendor
  ? [{ name: 'product', label: TYPE_MAP.product }]
  : TYPE_OPTIONS.filter(t => t.name !== 'product')

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
    else if (!isVendor) p.excludeSupplierType = 'product' // 供应商模式"全部"排除成品商
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
  id: undefined as any, code: '', name: '', supplySku: '', contact: '', phone: '', address: '', remark: '',
  checkedTypes: [] as string[], status: 1,
  creditPeriodMonths: undefined as any, creditPeriod: undefined as any
})
const isEdit = ref(false)

// 计算是否选中了某类型
const isType = computed(() => (type: string) => form.checkedTypes.includes(type))

function resetForm() {
  Object.assign(form, { id: undefined, code: '', name: '', supplySku: '', contact: '', phone: '', address: '', remark: '',
    checkedTypes: [] as string[], status: 1,
    creditPeriodMonths: undefined, creditPeriod: undefined })
}

/** 供应商→/supplier/detail/:id；供货商→/outsource/supplier/detail/:id（两者路由与页面标题分开） */
function goDetail(id: number) { router.push(isVendor ? `/outsource/supplier/detail/${id}` : `/supplier/detail/${id}`) }

function handleAdd() {
  // 2026-09-23 用户要求：新增/编辑由 640px 弹框改为独立页（模式随路径前缀带过去）
  router.push(isVendor ? '/outsource/supplier/manage/add' : '/supplier/manage/add')
}

function handleEdit(row: any) {
  router.push((isVendor ? '/outsource/supplier/manage/edit/' : '/supplier/manage/edit/') + row.id)
}

async function handleSubmit() {
  if (!form.name) { ElMessage.warning('请输入名称'); return }
  if (form.checkedTypes.length === 0) { ElMessage.warning('请选择至少一个类型'); return }
  // 新增时同名提示：供应商与供货商允许重名（各自独立建档），但需用户确认避免误录
  if (!isEdit.value) {
    saving.value = true
    let dupInfo = ''
    try {
      const dup = await request.get<any, any>('/supplier/page', { params: { name: form.name, pageSize: 5 } })
      const list = dup?.records || []
      if (list.length > 0) {
        dupInfo = list.map((s: any) => `${s.name}（${(s.typeCodes || []).map((t: string) => TYPE_MAP[t] || t).join('/')}）`).join('、')
      }
    } catch { /* 查重失败不阻塞创建 */ } finally { saving.value = false }
    if (dupInfo) {
      try {
        await ElMessageBox.confirm(`已存在同名往来单位：${dupInfo}。同名将创建为各自独立的往来单位，确认继续新增吗？`, '存在同名', { type: 'warning', confirmButtonText: '仍要新增', cancelButtonText: '取消' })
      } catch { return }
    }
  }
  saving.value = true
  try {
    const body: any = { ...form, typeCodes: form.checkedTypes }
    // 2026-09-21：「供货SKU」只属于供货商（类型=成品商）⇒ 供应商提交时显式置空，避免残留旧前缀
    if (!isVendor) body.supplySku = ''
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
      ElMessage({ message: detail, type: 'warning', duration: 5000 })
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

</script>

<template>
  <div class="page-list">
    <el-card shadow="never" class="query-card">
      <el-tabs v-if="!isVendor" v-model="activeType">
        <el-tab-pane v-for="t in TYPE_TABS_CUSTOM" :key="t.name" :label="t.label" :name="t.name" />
      </el-tabs>
      <div class="query-bar">
      <el-form :inline="true" :model="query">
        <el-form-item :label="entityLabel + '名称'"><el-input v-model="query.name" :placeholder="isVendor ? '供货商名称' : '供应商名称'" clearable @keyup.enter="handleQuery" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="query.phone" placeholder="手机号" clearable @keyup.enter="handleQuery" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="query.status" placeholder="全部" clearable style="width:100px"><el-option label="合作中" :value="1" /><el-option label="已停用" :value="0" /></el-select></el-form-item>
        </el-form>
        <div class="toolbar">
          <el-button type="primary" :icon="'Search'" @click="handleQuery">查询</el-button>
          <el-button :icon="'Refresh'" @click="handleReset">重置</el-button>
          <el-button type="success" :icon="'Plus'" @click="handleAdd">新增</el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <!-- 2026-09-24（用户规则：所有列表一行显示完、不左右滑动）：原列宽合计 960px > 内容区 956px
           ⇒ 刚好越界 4px（横向滚动）。收窄为合计 818px（名称保持 min-width，宽屏自动吃余量）。 -->
      <el-table :data="tableData" border stripe v-loading="loading" @row-click="(row: any) => goDetail(row.id)">
        <!-- 2026-09-25 B1（实测驱动）：编码 120→**156**（供应商编码需 152 / 供货商编码需 155，原先都被截断）；
             名称改链接后需 ~254px（"PROBE-SUPPLY-TYPECHANGE" 实测 254）⇒ 名称 min180→**220**，
             类型 150→140、联系人 120→96、联系电话 130→108、状态 80→76、操作 120→116 抵平
             （声明合计 912 ≤ 930；名称是本表唯一 min-width 列 ⇒ 独吞余量，1262 视口下实得 ~256px ⇒ 完整）。 -->
        <el-table-column prop="code" :label="entityLabel + '编码'" width="156" show-overflow-tooltip />
        <el-table-column label="类型" width="140">
          <template #default="{ row }">
            <el-tag v-for="t in (row.typeCodes||[])" :key="t" size="small" style="margin-right:4px"
              :type="(TYPE_TAG[t]||'info') as any"
            >{{ TYPE_MAP[t] || t }}</el-tag>
          </template>
        </el-table-column>
        <!-- 2026-09-25 B1（用户口径「主体列全站可点」）：名称 → 详情（本页双模式：
             /supplier/manage=供应商 → /supplier/detail；/outsource/supplier/manage=供货商 → /outsource/supplier/detail） -->
        <el-table-column :label="entityLabel + '名称'" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button type="primary" link @click.stop="router.push(isVendor ? `/outsource/supplier/detail/${row.id}` : `/supplier/detail/${row.id}`)">{{ row.name }}</el-button>
          </template>
        </el-table-column>
        <el-table-column prop="contact" label="联系人" width="96" />
        <el-table-column prop="phone" label="联系电话" width="108" />
        <el-table-column label="状态" width="76" align="center"><template #default="{row}"><el-tag size="small" :type="row.status===1?'success':'danger'">{{ row.status===1?'启用':'停用' }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="116" align="center">
          <template #default="{row}">
            <el-button type="primary" link size="small" @click.stop="goDetail(row.id)">详情</el-button>
            <el-button type="danger" link size="small" @click.stop="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination">
        <el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50, 100]" :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper" background
          @size-change="handleQuery" @current-change="loadData" />
      </div>
    </el-card>

  </div>
</template>

<style scoped>
/* 根容器/卡片内边距已统一到全局（styles/page.css 的 .page-list / .table-card .el-card__body） */
/* 分页样式已统一到全局（styles/page.css 的 .pagination） */
</style>
