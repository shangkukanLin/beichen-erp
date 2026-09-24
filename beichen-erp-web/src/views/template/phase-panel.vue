<script setup lang="ts">
import { reactive, ref, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ProductSpec, ProductSpecLabel } from '@/api/enums'
import request from '@/utils/request'

/**
 * 阶段模板面板（2026-09-15）：原「阶段模板管理」页面（/dev/phase-template）的主体，
 * 现作为「基础数据 → 模版管理」页面中「阶段模板管理」页签的内嵌内容（见 ../index.vue）。
 * 接口与业务行为保持不变：阶段模板供新建研发项目时一键套用（可选择是否同步产品状态）。
 *
 * 2026-09-21（用户需求）：**阶段模板分两套** —— 原配项目只走 7 个阶段
 * （立项/排线打样/背贴盖板打样/总成样品/测试/小批量/结项），改配用原来那 14 个。
 * 故面板顶部加「适用规格」切换，新增/编辑都带上 specType；立项时由后端按项目规格自动套用对应那套。
 */
const loading = ref(false)
const list = ref<any[]>([])

/** 当前查看的规格（两套模板二选一） */
const specType = ref<string>(ProductSpec.MODIFIED)

/** 时间轴模板里只有「原配 / 改配」两套（不含"原装"——那是产品侧的完整口径） */
const SPEC_OPTIONS = [
  { value: ProductSpec.MATCHED, label: ProductSpecLabel[ProductSpec.MATCHED] },
  { value: ProductSpec.MODIFIED, label: ProductSpecLabel[ProductSpec.MODIFIED] }
]

const dialogVisible = ref(false)
const isEdit = ref(false)
// 显式声明表单类型：ProductSpec.* 是 `as const` 的字面量类型，不标注会被推断成 'MODIFIED' 字面量，
// 之后赋其它规格 code 就会报 TS2322
const form = reactive<{ id: any; name: string; specType: string; defaultDays: number; sortOrder: number; remark: string }>({
  id: undefined, name: '', specType: ProductSpec.MODIFIED, defaultDays: 0, sortOrder: 0, remark: ''
})

/** 拉当前规格那一套模板（不传 specType 后端会返回两套，故这里必须显式带） */
async function loadData() {
  loading.value = true
  // 2026-09-20（F7-192）：原先只有 try/finally、无 catch ⇒ 失败无提示且产生未处理 rejection
  try {
    const r = await request.get<any, any>('/dev/phase-template/list', { params: { specType: specType.value } })
    list.value = r || []
  } catch (e: any) { list.value = []; ElMessage.error(e?.message || '加载阶段模板失败') }
  finally { loading.value = false }
}

// 切换规格即换一套模板（两套各自独立，互不影响）
watch(specType, loadData)

function openAdd() {
  isEdit.value = false
  resetForm()
  // 新增默认落到"当前正在看的那一套"，避免看改配却加进了原配
  form.specType = specType.value
  dialogVisible.value = true
}
function openEdit(row: any) {
  isEdit.value = true
  Object.assign(form, {
    id: row.id, name: row.name, specType: row.specType || specType.value,
    defaultDays: row.defaultDays, sortOrder: row.sortOrder, remark: row.remark || ''
  })
  dialogVisible.value = true
}
async function handleDelete(row: any) {
  try { await ElMessageBox.confirm('确认删除？', '删除', { type: 'warning' }) } catch { return }
  try { await request.delete(`/dev/phase-template/${row.id}`); ElMessage.success('已删除'); loadData() } catch (e: any) { ElMessage.error(e?.message || '失败') }
}
function resetForm() {
  Object.assign(form, { id: undefined, name: '', specType: specType.value, defaultDays: 0, sortOrder: 0, remark: '' })
}
async function handleSubmit() {
  if (!form.name) { ElMessage.warning('请输入阶段名称'); return }
  if (!form.specType) { ElMessage.warning('请选择适用规格'); return }
  try {
    if (isEdit.value) { await request.put('/dev/phase-template', form); ElMessage.success('已更新') }
    else { await request.post('/dev/phase-template', form); ElMessage.success('已创建') }
    dialogVisible.value = false
    // 若这次改/加的是"另一套"，切过去让结果可见
    if (form.specType !== specType.value) specType.value = form.specType
    else loadData()
  } catch (e: any) { ElMessage.error(e?.message || '失败') }
}

onMounted(loadData)
</script>

<template>
  <div class="panel-body">
    <div class="panel-toolbar">
      <div style="display:flex;align-items:center;gap:12px;flex-wrap:wrap">
        <!-- 2026-09-21：两套模板二选一 —— 新建研发立项时会按项目的「规格」自动套用对应这一套 -->
        <el-radio-group v-model="specType" size="default">
          <el-radio-button v-for="o in SPEC_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</el-radio-button>
        </el-radio-group>
        <span class="panel-tip">
          阶段模板分「原配 / 改配」两套，新建研发立项时按项目的「规格」自动套用；
          「同步产品状态」的阶段完成/跳过后会把关联产品置为「正常」。
        </span>
      </div>
      <el-button type="success" :icon="'Plus'" @click="openAdd">新增</el-button>
    </div>

    <el-table :data="list" border stripe v-loading="loading">
      <el-table-column label="序号" width="60" align="center"><template #default="{row}">{{ row.sortOrder }}</template></el-table-column>
      <el-table-column prop="name" label="阶段名称" width="140" />
      <el-table-column label="默认天数" width="80" align="center"><template #default="{row}">{{ row.defaultDays }}</template></el-table-column>
      <el-table-column prop="remark" label="备注" min-width="200" show-overflow-tooltip />
      <el-table-column label="操作" width="130" align="center">
        <template #default="{row}">
          <el-button type="primary" link @click="openEdit(row)">编辑</el-button>
          <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="isEdit?'编辑阶段':'新增阶段'" width="var(--app-dialog-sm)">
      <el-form :model="form" label-width="90px">
        <el-form-item required label="阶段名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item required label="适用规格">
          <el-select v-model="form.specType" style="width:100%">
            <el-option v-for="o in SPEC_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="默认天数"><el-input-number v-model="form.defaultDays" :min="0" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sortOrder" :min="1" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible=false">取消</el-button><el-button type="primary" @click="handleSubmit">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.panel-body { display: flex; flex-direction: column; gap: 12px; }
.panel-toolbar { display: flex; justify-content: space-between; align-items: center; gap: 12px; flex-wrap: wrap; }
.panel-tip { font-size: var(--app-font-xs); color: var(--el-text-color-secondary); }
</style>
