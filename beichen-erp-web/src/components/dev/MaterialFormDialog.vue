<script setup lang="ts">
import { reactive, ref, onMounted, watch, computed } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

interface Props {
  // 项目页传入当前项目ID时，新增物料自动关联且下拉禁用；物料管理页不传，用户可选/留空
  defaultProjectId?: number
  visible?: boolean
}
const props = withDefaults(defineProps<Props>(), { defaultProjectId: undefined, visible: false })
const emit = defineEmits<{ (e: 'update:visible', v: boolean): void; (e: 'saved'): void }>()

const todayStr = new Date().toISOString().split('T')[0]
const materialStatusOptions = ['完好', '已损坏', '已使用']
const materialTypeOptions = ref<string[]>([])
const projectOptions = ref<any[]>([])

const isEdit = ref(false)
const submitting = ref(false)
// 内部维护弹窗显示状态（父组件通过 ref.open() 控制，无需 v-model）
const dialogVisible = ref(false)
const form = reactive<any>({
  id: undefined,
  projectId: undefined,
  name: '', type: '', quantity: 1, locationDetail: '',
  purchaseDate: todayStr, amount: 0, status: '完好', remark: ''
})

// 是否锁定项目（项目页传入时锁定）
const lockedProject = computed(() => props.defaultProjectId != null)

function resetForm() {
  Object.assign(form, {
    id: undefined, projectId: lockedProject.value ? props.defaultProjectId : undefined,
    name: '', type: '', quantity: 1, locationDetail: '',
    purchaseDate: todayStr, amount: 0, status: '完好', remark: ''
  })
  isEdit.value = false
}

async function open(row?: any) {
  // 加载研发物料类型枚举（基板/屏幕/测试架/其他）
  if (materialTypeOptions.value.length === 0) {
    try { const res: any = await request.get('/dev/purchase-item/material-types'); materialTypeOptions.value = res || [] } catch (e) { /* 忽略 */ }
  }
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
  submitting.value = true
  try {
    const payload = { ...form }
    // 项目页锁定场景强制带上当前项目ID
    if (lockedProject.value) payload.projectId = props.defaultProjectId
    if (isEdit.value && form.id) {
      await request.put(`/dev/purchase-item/${form.id}`, payload)
      ElMessage.success('已更新')
    } else {
      await request.post(`/dev/purchase-item`, payload)
      ElMessage.success('已添加')
    }
    dialogVisible.value = false
    emit('update:visible', false)
    emit('saved')
  } catch (e: any) { ElMessage.error(e?.message || '操作失败') } finally { submitting.value = false }
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
  <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑项目物料' : '新增项目物料'" width="520px" @close="handleClose">
    <el-form :model="form" label-width="80px">
      <el-row :gutter="12">
        <el-col :span="14"><el-form-item label="名称"><el-input v-model="form.name" /></el-form-item></el-col>
        <el-col :span="10"><el-form-item label="类型">
          <el-select v-model="form.type" style="width:100%" placeholder="请选择类型">
            <el-option v-for="t in materialTypeOptions" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item></el-col>
        <el-col :span="24"><el-form-item label="关联项目">
          <el-select v-model="form.projectId" style="width:100%" :disabled="lockedProject" clearable placeholder="不关联研发项目">
            <el-option v-for="p in projectOptions" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item></el-col>
        <el-col :span="12"><el-form-item label="数量"><el-input-number v-model="form.quantity" :min="0" :precision="0" style="width:100%" /></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="金额"><el-input-number v-model="form.amount" :min="0" :precision="2" style="width:100%" /></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="状态">
          <el-select v-model="form.status" style="width:100%">
            <el-option v-for="s in materialStatusOptions" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item></el-col>
        <el-col :span="12"><el-form-item label="采购日期"><el-input v-model="form.purchaseDate" type="date" /></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="位置详情">
          <el-input v-model="form.locationDetail" placeholder="具体库位/货架号（可选）" />
        </el-form-item></el-col>
        <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item></el-col>
      </el-row>
    </el-form>
    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>
