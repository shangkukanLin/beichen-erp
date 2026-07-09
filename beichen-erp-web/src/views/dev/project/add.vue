<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { addProject, getSupplierPage, type ProjectDTO } from '@/api/system'
import request from '@/utils/request'

const router = useRouter()

const STATUS_LIST = ['立项', '排线图纸', '排线打样', 'FOG打样', '显示调试', '触摸调试', '背贴盖板打样', '总成样品', '测试', '小批量', '结项']
const solutionSuppliers = ref<{ id: number; name: string }[]>([])
const factoryOptions = ref<{ id: number; name: string }[]>([])
const saving = ref(false)

const form = reactive<ProjectDTO>({
  name: '', status: '立项', displaySupplierName: '', touchSupplierName: '',
  adaptModel: '', originalSize: '', originalResolution: '',
  startDate: new Date().toISOString().split('T')[0], expectedEndDate: '', remark: '',
  sampleFactoryId: undefined, outsourceFactoryId: undefined
})

async function loadData() {
  try { const r = await getSupplierPage({ supplierType: 'solution', pageSize: 200 }); solutionSuppliers.value = (r?.records || []).map((s: any) => ({ id: s.id, name: s.name })) } catch (e: any) { console.warn('加载方案商失败', e?.message || e) }
  try { const r = await request.get<any, any>('/supplier/page', { params: { supplierType: 'factory', pageSize: 200 } }); factoryOptions.value = (r?.records || []).map((s: any) => ({ id: s.id, name: s.name })) } catch (e: any) { console.warn('加载工厂失败', e?.message || e) }
}

async function handleSubmit() {
  if (!form.name) { ElMessage.warning('请输入项目名称'); return }
  saving.value = true
  try {
    await addProject(form as any)
    ElMessage.success('项目创建成功')
    router.push('/dev/project')
  } catch (e: any) { ElMessage.error('项目创建失败: ' + (e?.message || '未知错误')) }
  saving.value = false
}

function goBack() { router.push('/dev/project') }

onMounted(() => loadData())
</script>

<template>
  <div class="add-page">
    <div class="page-header">
      <el-button @click="goBack" :icon="'ArrowLeft'">返回列表</el-button>
      <span class="page-title">新增研发项目</span>
    </div>

    <!-- 基础信息 -->
    <el-card shadow="never">
      <template #header><span style="font-weight:600">基础信息</span></template>
      <el-form :model="form" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="8"><el-form-item label="项目名称"><el-input v-model="form.name" placeholder="请输入项目名称" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="项目阶段">
            <el-select v-model="form.status" style="width:100%"><el-option v-for="s in STATUS_LIST" :key="s" :label="s" :value="s" /></el-select>
          </el-form-item></el-col>
          <el-col :span="8"><el-form-item label="适配机型"><el-input v-model="form.adaptModel" placeholder="如 iPhone 15" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="显示方案">
            <el-select v-model="form.displaySupplierName" filterable allow-create style="width:100%" placeholder="选择或输入"><el-option v-for="s in solutionSuppliers" :key="s.id" :label="s.name" :value="s.name" /></el-select>
          </el-form-item></el-col>
          <el-col :span="8"><el-form-item label="触摸方案">
            <el-select v-model="form.touchSupplierName" filterable allow-create style="width:100%" placeholder="选择或输入"><el-option v-for="s in solutionSuppliers" :key="s.id" :label="s.name" :value="s.name" /></el-select>
          </el-form-item></el-col>
          <el-col :span="8"><el-form-item label="原机尺寸"><el-input v-model="form.originalSize" placeholder="如 6.1寸" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="原分辨率"><el-input v-model="form.originalResolution" placeholder="如 1080×2400" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="打样工厂">
            <el-select v-model="form.sampleFactoryId" clearable filterable style="width:100%" placeholder="选择工厂"><el-option v-for="f in factoryOptions" :key="f.id" :label="f.name" :value="f.id" /></el-select>
          </el-form-item></el-col>
          <el-col :span="8"><el-form-item label="委外工厂">
            <el-select v-model="form.outsourceFactoryId" clearable filterable style="width:100%" placeholder="选择工厂"><el-option v-for="f in factoryOptions" :key="f.id" :label="f.name" :value="f.id" /></el-select>
          </el-form-item></el-col>
        </el-row>
      </el-form>
    </el-card>

    <!-- 时间节点 -->
    <el-card shadow="never" style="margin-top:12px">
      <template #header><span style="font-weight:600">时间节点</span></template>
      <el-form :model="form" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="8"><el-form-item label="立项日期"><el-input v-model="form.startDate" type="date" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="预计完成"><el-input v-model="form.expectedEndDate" type="date" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="备注"><el-input v-model="form.remark" placeholder="备注" /></el-form-item></el-col>
        </el-row>
      </el-form>
    </el-card>

    <div style="margin-top:16px">
      <el-button type="primary" size="large" :loading="saving" @click="handleSubmit">创建项目</el-button>
      <el-button size="large" @click="goBack">取消</el-button>
    </div>
  </div>
</template>

<style scoped>
.add-page { display:flex; flex-direction:column; gap:12px; }
.page-header { display:flex; align-items:center; gap:16px; padding-bottom:8px; }
.page-title { font-size:18px; font-weight:600; }
</style>
