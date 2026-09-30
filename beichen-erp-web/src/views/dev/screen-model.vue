<script setup lang="ts">
import { localDate } from '@/utils/date'
import { ADD_MARKER } from '@/composables/useSelectWithAdd'
import { reactive, ref, onMounted, onActivated } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as XLSX from 'xlsx'
import request from '@/utils/request'

/**
 * 屏幕资料（研发管理，2026-09-16 由「屏幕资料知识库」改名）：行业机型屏幕参数，支持增删改查、关键词搜索、Excel 导入导出。
 * 数据由后端启动时从 db/screen_model_data.sql 初始化；清空数据不会清理本知识库。
 */
const loading = ref(false)
const rows = ref<any[]>([])
const pagination = reactive({ pageNum: 1, pageSize: 20, total: 0 })
// 类别用 TAB 切换（只有折叠屏/直板AMOLED 两类），不进搜索条件
const activeTab = ref('FOLD')
const query = reactive({ keyword: '', brand: '', screenSize: '', resolution: '', screenType: '' })
const brandOptions = ref<string[]>([])
// 下拉选项：屏幕类型 / 主屏尺寸 / 分辨率 的去重值
const screenTypeOptions = ref<string[]>([])
const screenSizeOptions = ref<string[]>([])
const resolutionOptions = ref<string[]>([])
const dialog = ref(false)
const dialogTitle = ref('新增机型')
const form = ref<any>({})

const CATEGORY_LABEL: Record<string, string> = { FOLD: '折叠屏', AMOLED: '直板AMOLED' }

const emptyForm = () => ({
  id: undefined, category: 'AMOLED', brand: '', model: '',
  screenSize: '', resolution: '', screenType: '', refreshRate: '',
  subSize: '', subResolution: '', subScreenType: '', subRefreshRate: '',
  fingerprint: '', panelSupplier: '', releaseDate: '', remark: '',
})

async function loadBrands() {
  try { brandOptions.value = await request.get<any, any>('/dev/screen-model/brands') || [] } catch { brandOptions.value = [] }
}

// 加载屏幕类型 / 主屏尺寸 / 分辨率 的去重下拉项
async function loadOptions() {
  try {
    const res = (await request.get<any, any>('/dev/screen-model/options')) || {}
    screenTypeOptions.value = res.screenTypes || []
    screenSizeOptions.value = res.screenSizes || []
    resolutionOptions.value = res.resolutions || []
  } catch { /* 下拉项加载失败不影响主列表 */ }
}

async function loadData() {
  loading.value = true
  try {
    const res = await request.get<any, any>('/dev/screen-model/page', {
      params: { pageNum: pagination.pageNum, pageSize: pagination.pageSize, category: activeTab.value, ...query },
    })
    rows.value = res?.records || []
    pagination.total = res?.total || 0
  } catch { rows.value = []; pagination.total = 0 } finally { loading.value = false }
}
/** TAB 切换：按新类别重新查询，回到第 1 页 */
function onTabChange() { pagination.pageNum = 1; loadData() }
function handleQuery() { pagination.pageNum = 1; loadData() }
function handleReset() { query.keyword = ''; query.brand = ''; query.screenSize = ''; query.resolution = ''; query.screenType = ''; handleQuery() }

function openAdd() { form.value = emptyForm(); dialogTitle.value = '新增机型'; dialog.value = true }
function openEdit(row: any) { form.value = { ...emptyForm(), ...row }; dialogTitle.value = '编辑机型'; dialog.value = true }

async function save() {
  if (!form.value.brand || !form.value.model) { ElMessage.warning('品牌和型号不能为空'); return }
  try {
    if (form.value.id) await request.put(`/dev/screen-model/${form.value.id}`, form.value)
    else await request.post('/dev/screen-model', form.value)
    ElMessage.success('已保存')
    dialog.value = false
    loadData()
  } catch (e: any) { ElMessage.error(e?.message || '保存失败') }
}

async function handleDelete(row: any) {
  try { await ElMessageBox.confirm(`确认删除「${row.brand} ${row.model}」？`, '删除确认', { type: 'warning' }) }
  catch { return }
  try {
    await request.delete(`/dev/screen-model/${row.id}`)
    ElMessage.success('已删除')
    loadData()
  } catch (e: any) { ElMessage.error(e?.message || '删除失败') }
}

// ==================== Excel 导入 / 导出 ====================
/** 导出当前查询结果为 Excel（全量，便于用 Excel 批量维护后再导入） */
async function exportExcel() {
  const res = await request.get<any, any>('/dev/screen-model/list', {
    params: { category: activeTab.value, keyword: query.keyword, brand: query.brand, screenSize: query.screenSize, resolution: query.resolution, screenType: query.screenType },
  })
  const list: any[] = res || []
  const aoa: any[][] = [[
    '类别', '品牌', '型号', '主屏尺寸', '分辨率', '屏幕类型', '刷新率',
    '副屏尺寸', '副屏分辨率', '副屏类型', '副屏刷新率',
    '指纹识别', '屏幕供应商', '发布时间', '备注',
  ]]
  list.forEach(r => aoa.push([
    CATEGORY_LABEL[r.category] || r.category, r.brand, r.model, r.screenSize, r.resolution, r.screenType, r.refreshRate,
    r.subSize, r.subResolution, r.subScreenType, r.subRefreshRate,
    r.fingerprint, r.panelSupplier, r.releaseDate, r.remark,
  ]))
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, XLSX.utils.aoa_to_sheet(aoa), '屏幕资料')
  XLSX.writeFile(wb, `屏幕资料_${localDate()}.xlsx`)
  ElMessage.success(`已导出 ${list.length} 条`)
}

/** 导入 Excel：按表头中文映射字段，批量新增（不清空已有数据） */
const fileRef = ref<HTMLInputElement | null>(null)
function pickFile() { fileRef.value?.click() }
const HEADER_MAP: Record<string, string> = {
  '类别': 'category', '品牌': 'brand', '型号': 'model', '主屏尺寸': 'screenSize', '屏幕尺寸': 'screenSize',
  '分辨率': 'resolution', '屏幕分辨率': 'resolution', '屏幕类型': 'screenType', '刷新率': 'refreshRate',
  '副屏尺寸': 'subSize', '副屏分辨率': 'subResolution', '副屏类型': 'subScreenType', '副屏刷新率': 'subRefreshRate',
  '指纹识别': 'fingerprint', '屏幕供应商': 'panelSupplier', '发布时间': 'releaseDate', '备注': 'remark',
}
async function onFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  try {
    const buf = await file.arrayBuffer()
    const wb = XLSX.read(buf, { type: 'array' })
    const sheet = wb.Sheets[wb.SheetNames[0]]
    const list: any[] = XLSX.utils.sheet_to_json(sheet, { defval: '' })
    const payload: any[] = []
    for (const raw of list) {
      const item: any = { category: 'AMOLED' }
      for (const [k, v] of Object.entries(raw as Record<string, any>)) {
        const field = HEADER_MAP[String(k).trim()]
        if (!field) continue
        let val = String(v ?? '').trim()
        if (field === 'category') val = val.indexOf('折叠') >= 0 ? 'FOLD' : 'AMOLED'
        item[field] = val
      }
      if (item.brand && item.model) payload.push(item)
    }
    if (!payload.length) { ElMessage.warning('未解析到有效数据（需包含「品牌」「型号」列）'); return }
    const res = await request.post<any, any>('/dev/screen-model/batch', payload)
    ElMessage.success(`已导入 ${res ?? payload.length} 条`)
    loadData()
  } catch (err: any) { ElMessage.error('导入失败：' + (err?.message || '文件格式有误')) }
  finally { input.value = '' }
}

onMounted(() => { loadData(); loadBrands(); loadOptions() })
onActivated(() => { loadData() })
</script>

<template>
  <div class="page-list">
    <el-card shadow="never" class="query-card">
      <!-- 筛选区：品牌 / 尺寸 / 分辨率 / 类型 / 关键词 + 即时查询操作 -->
      <div class="query-bar">
        <el-form :inline="true" :model="query" class="query-form">
          <el-form-item label="品牌">
            <el-select v-model="query.brand" placeholder="全部" clearable filterable style="width:130px" @change="(v: any) => { if (v === ADD_MARKER) { $router.push('/inventory/brand') } }">
              <el-option v-for="b in brandOptions" :key="b" :label="b" :value="b" />
            
                <el-option label="+ 鏂板" :value="ADD_MARKER" />
              </el-select>
          </el-form-item>
          <el-form-item label="主屏尺寸">
            <el-select v-model="query.screenSize" placeholder="全部" clearable filterable style="width:120px" @change="handleQuery">
              <el-option v-for="o in screenSizeOptions" :key="o" :label="o" :value="o" />
            </el-select>
          </el-form-item>
          <el-form-item label="分辨率">
            <el-select v-model="query.resolution" placeholder="全部" clearable filterable style="width:150px" @change="handleQuery">
              <el-option v-for="o in resolutionOptions" :key="o" :label="o" :value="o" />
            </el-select>
          </el-form-item>
          <el-form-item label="屏幕类型">
            <el-select v-model="query.screenType" placeholder="全部" clearable filterable style="width:140px" @change="handleQuery">
              <el-option v-for="o in screenTypeOptions" :key="o" :label="o" :value="o" />
            </el-select>
          </el-form-item>
          <el-form-item label="关键词">
            <el-input v-model="query.keyword" placeholder="品牌 / 型号 / 屏幕类型 / 供应商" clearable style="width:220px" @keyup.enter="handleQuery" />
          </el-form-item>
        </el-form>
      </div>

      <div class="query-divider"></div>

      <!-- 操作区：左侧数据量提示，右侧 查询/重置 + 业务操作 -->
      <div class="toolbar">
        <span class="toolbar-count">共 <b>{{ pagination.total }}</b> 条</span>
        <div class="toolbar-actions">
          <el-button type="primary" :icon="'Search'" @click="handleQuery">查询</el-button>
          <el-button :icon="'Refresh'" @click="handleReset">重置</el-button>
          <el-divider direction="vertical" />
          <el-button type="success" :icon="'Plus'" @click="openAdd">新增</el-button>
          <el-button :icon="'Upload'" @click="pickFile">导入 Excel</el-button>
          <el-button type="primary" plain :icon="'Download'" @click="exportExcel">导出 Excel</el-button>
          <input ref="fileRef" type="file" accept=".xlsx,.xls" style="display:none" @change="onFileChange" />
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <!-- 类别只有折叠屏/直板两类，用 TAB 切换 -->
      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <el-tab-pane label="折叠屏" name="FOLD" />
        <el-tab-pane label="直板AMOLED" name="AMOLED" />
      </el-tabs>
      <!-- 2026-09-24（用户规则：所有列表一行显示完、不左右滑动）：11 列、原列宽合计 1234px > 内容区 956px
           ⇒ 横向滚动 278px（且本表 max-height 触发纵向滚动条，再占约 8px）⇒ 收窄为合计 940px。
           2026-09-25 B2（用户口径「列表数据尽量显示完整」，实测驱动）：
           11 列的**实测需宽合计 ~1381px**（型号 180 / 分辨率 147 / 屏幕类型 181 / 屏幕供应商 165 /
           主屏尺寸 102 / 发布时间 129 / 指纹识别 129），物理上放不进 948px ⇒ 按已批准的
           「放不下时把最低价值列移出列表」处理：
             **移出 4 列**（刷新率 / 副屏尺寸 / 指纹识别 / 发布时间）—— 这 4 项在「编辑」弹框里都可看可改，
             且 Excel 导入导出**仍是全 15 字段**（与列表列无关），所以数据并未丢失；
             **保留 7 列并全部给足实测宽**：品牌 min65（弹性吃余量）/ 型号 180 / 主屏尺寸 102 /
             分辨率 147 / 屏幕类型 181 / 屏幕供应商 165 / 操作 88 ⇒ 声明合计 928 ≤ 930，
             这 7 列在本机宽度下**全部完整显示（0 截断）**。 -->
      <el-table v-loading="loading" :data="rows" border stripe max-height="620">
        <el-table-column prop="brand" label="品牌" min-width="65" show-overflow-tooltip />
        <el-table-column prop="model" label="型号" width="180" show-overflow-tooltip />
        <el-table-column prop="screenSize" label="主屏尺寸" width="102" align="center" />
        <el-table-column prop="resolution" label="分辨率" width="147" show-overflow-tooltip />
        <el-table-column prop="screenType" label="屏幕类型" width="181" show-overflow-tooltip />
        <el-table-column prop="panelSupplier" label="屏幕供应商" width="165" show-overflow-tooltip />
        <el-table-column label="操作" width="90" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openEdit(row)">编辑</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination">
        <el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50, 100, 200]" :total="pagination.total" layout="total, sizes, prev, pager, next, jumper"
          background @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <!-- 新增 / 编辑 -->
    <el-dialog v-model="dialog" :title="dialogTitle" width="var(--app-dialog-md)" :close-on-click-modal="false">
      <el-form :model="form" label-width="90px" size="small">
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item label="类别">
            <el-select v-model="form.category" style="width:100%">
              <el-option label="折叠屏" value="FOLD" /><el-option label="直板AMOLED" value="AMOLED" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="8"><el-form-item required label="品牌"><el-input v-model="form.brand" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item required label="型号"><el-input v-model="form.model" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item label="主屏尺寸">
            <el-select v-model="form.screenSize" placeholder="选择或输入" clearable filterable allow-create default-first-option style="width:100%">
              <el-option v-for="o in screenSizeOptions" :key="o" :label="o" :value="o" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="8"><el-form-item label="分辨率">
            <el-select v-model="form.resolution" placeholder="选择或输入" clearable filterable allow-create default-first-option style="width:100%">
              <el-option v-for="o in resolutionOptions" :key="o" :label="o" :value="o" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="8"><el-form-item label="刷新率"><el-input v-model="form.refreshRate" placeholder="120Hz" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="屏幕类型">
            <el-select v-model="form.screenType" placeholder="选择或输入" clearable filterable allow-create default-first-option style="width:100%">
              <el-option v-for="o in screenTypeOptions" :key="o" :label="o" :value="o" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item label="指纹识别"><el-input v-model="form.fingerprint" placeholder="侧装/屏下" /></el-form-item></el-col>
        </el-row>
        <!-- 副屏参数：仅折叠屏需要，直板机型留空即可 -->
        <el-row :gutter="12">
          <el-col :span="6"><el-form-item label="副屏尺寸"><el-input v-model="form.subSize" /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="副屏分辨率"><el-input v-model="form.subResolution" /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="副屏类型"><el-input v-model="form.subScreenType" /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="副屏刷新率"><el-input v-model="form.subRefreshRate" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="屏幕供应商"><el-input v-model="form.panelSupplier" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="发布时间"><el-input v-model="form.releaseDate" placeholder="2/1/19" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
<style scoped>
/* 根容器/分页样式已统一到全局（styles/page.css 的 .page-list / .pagination） */

/* ===== 顶部搜索卡片 ===== */
.query-card{border-radius:10px;border:1px solid var(--el-border-color-lighter)}
.query-bar{padding:2px 2px 0}

/* 筛选表单：弹性折行、等距对齐，覆盖 Element 默认 inline 的拥挤间距 */
.query-form{
  display:flex;flex-wrap:wrap;align-items:center;
  gap:4px 10px;margin-bottom:0;
}
.query-form :deep(.el-form-item){margin-bottom:12px;margin-right:0}
.query-form :deep(.el-form-item__label){color:var(--el-text-color-regular);font-weight:500}

/* 分隔线：筛选区与操作区视觉分区 */
.query-divider{height:1px;background:var(--el-border-color-lighter);margin:2px 0 12px}

/* 操作区：左提示 + 右侧按钮组，窄屏自动折行 */
.toolbar{display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap}
.toolbar-count{color:var(--el-text-color-secondary);font-size:var(--app-font-base)}
.toolbar-count b{color:var(--el-color-primary);font-size:var(--app-font-md);margin:0 2px}
.toolbar-actions{display:flex;gap:8px;flex-wrap:wrap}
</style>
