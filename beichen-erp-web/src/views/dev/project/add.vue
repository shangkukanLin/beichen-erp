<script setup lang="ts">
import { reactive, ref, onMounted, onUnmounted } from 'vue'
import { DEV_PROJECT_DIRTY_KEY } from '@/api/enums'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { addProject, checkProjectAssembly, type ProjectDTO } from '@/api/system'
import { useTabStore } from '@/stores/tabs'
import { ADD_MARKER } from '@/composables/useSelectWithAdd'
import request from '@/utils/request'

const router = useRouter()
const route = useRoute()
const tabStore = useTabStore()

const solutionSupplierOptions = ref<{ id: number; name: string }[]>([])
const factoryOptions = ref<{ id: number; name: string }[]>([])
const saving = ref(false)

const fetchSolutionSuppliers = (kw: string) => request.get('/supplier/page', { params: { pageSize: 500, supplierType: 'solution', name: kw } })
const fetchFactorySuppliers = (kw: string) => request.get('/supplier/page', { params: { pageSize: 500, supplierType: 'factory', name: kw } })

const defForm = (): ProjectDTO => ({
  name: '', status: '立项', displaySupplierName: '', touchSupplierName: '',
  assemblyName: '',
  adaptModel: '', originalSize: '', originalResolution: '',
  originalDriveIc: '', originalTouchIc: '',
  glassSize: '', glassResolution: '',
  configDriveIcId: undefined, configTouchIcId: undefined, configCodeIcId: undefined,
  startDate: new Date().toISOString().split('T')[0], expectedEndDate: '', remark: '',
  sampleFactoryId: undefined, outsourceFactoryId: undefined,
  brandId: undefined
})

// BOM 类型（驱动IC/触摸IC/码片IC）id，用于改配物料下拉按类型过滤
const bomTypeIdMap = reactive<{ drive?: number; touch?: number; code?: number }>({})
async function loadBomTypeIds() {
  try {
    const bt: any = await request.get('/dev/bom-type/enabled')
    const list = bt || []
    bomTypeIdMap.drive = list.find((t: any) => t.typeName === '驱动IC')?.id
    bomTypeIdMap.touch = list.find((t: any) => t.typeName === '触摸IC')?.id
    bomTypeIdMap.code = list.find((t: any) => t.typeName === '码片IC')?.id
  } catch { /* 忽略 */ }
}
function fetchMaterialsByType(kw: string, bomTypeId?: number) {
  return request.get('/outsource/material/page', { params: { pageSize: 500, materialName: kw, bomTypeId: bomTypeId || undefined } })
}
const form = reactive<ProjectDTO>(defForm())

function resetForm() {
  Object.assign(form, defForm())
}

const brandOptions = ref<{ id: number; brandName: string }[]>([])
async function loadBrandOptions() {
  try { brandOptions.value = (await request.get('/brand/enabled')) || [] } catch { /* 忽略 */ }
}

async function loadData() {
  const sr: any = await fetchSolutionSuppliers(''); solutionSupplierOptions.value = (sr?.records || []).map((s: any) => ({ id: s.id, name: s.name }))
  const fr: any = await fetchFactorySuppliers(''); factoryOptions.value = (fr?.records || []).map((s: any) => ({ id: s.id, name: s.name }))
  loadBrandOptions()
}

async function handleSubmit() {
  if (!form.name) { ElMessage.warning('请输入项目名称'); return }
  if (!form.assemblyName || !form.assemblyName.trim()) { ElMessage.warning('请输入总成名称'); return }
  const assembly = form.assemblyName.trim()

  // 查重：若总成名称与已有产品重名，询问用户关联或修改名称
  let linkExistingProductId: number | undefined = undefined
  try {
    const check = await checkProjectAssembly(assembly)
    if (check?.exists) {
      try {
        const action = await ElMessageBox.confirm(
          `总成名称「${assembly}」已存在同名产品（ID:${check.productId}），是否关联该产品？`,
          '产品重名确认',
          { confirmButtonText: '关联该产品', cancelButtonText: '修改总成名称', type: 'warning' }
        )
        if (action === 'confirm') {
          linkExistingProductId = check.productId
        }
      } catch {
        ElMessage.warning('请修改总成名称后重新提交')
        return
      }
    }
  } catch (e: any) {
    // 查重接口异常不阻塞创建
    console.warn('查重失败', e?.message || e)
  }

  saving.value = true
  try {
    await addProject(form as any, linkExistingProductId)
    ElMessage.success('项目创建成功'); sessionStorage.setItem(DEV_PROJECT_DIRTY_KEY, '1')
    resetForm()
    tabStore.removeTab(route.path)
    router.replace('/dev/project')
  } catch (e: any) { ElMessage.error('项目创建失败: ' + (e?.message || '未知错误')) }
  saving.value = false
}

function goBack() { router.push('/dev/project') }

function onNameBlur() {
  if (!form.assemblyName || !form.assemblyName.trim()) {
    form.assemblyName = form.name
  }
}

// 顶栏"刷新数据"：重新加载方案供应商与BOM类型
async function handleRefreshData() { await Promise.all([loadData(), loadBomTypeIds()]) }
onMounted(() => {
  loadData()
  loadBomTypeIds()
  window.addEventListener('refresh:dropdown-data', handleRefreshData)
})
onUnmounted(() => window.removeEventListener('refresh:dropdown-data', handleRefreshData))
</script>

<template>
  <div class="add-page">
    <!-- 基础信息 -->
    <el-card shadow="never">
      <template #header><span style="font-weight:600">基础信息</span></template>
      <el-form :model="form" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="8"><el-form-item label="项目名称"><el-input v-model="form.name" placeholder="请输入项目名称" @blur="onNameBlur" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="总成名称" prop="assemblyName" :rules="[{ required: true, message: '请输入总成名称', trigger: 'blur' }]"><el-input v-model="form.assemblyName" placeholder="请输入总成名称" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="适配机型"><el-input v-model="form.adaptModel" placeholder="如 iPhone 15" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="显示方案">
            <el-select v-model="form.displaySupplierName" filterable allow-create style="width:100%" placeholder="选择或输入" @change="(v: string) => { if (v === ADD_MARKER) { form.displaySupplierName = ''; router.push('/supplier/manage'); return } }"><el-option v-for="s in solutionSupplierOptions" :key="s.id" :label="s.name" :value="s.name" /><el-option label="+ 新增" :value="ADD_MARKER" /></el-select>
          </el-form-item></el-col>
          <el-col :span="8"><el-form-item label="触摸方案">
            <el-select v-model="form.touchSupplierName" filterable allow-create style="width:100%" placeholder="选择或输入" @change="(v: string) => { if (v === ADD_MARKER) { form.touchSupplierName = ''; router.push('/supplier/manage'); return } }"><el-option v-for="s in solutionSupplierOptions" :key="s.id" :label="s.name" :value="s.name" /><el-option label="+ 新增" :value="ADD_MARKER" /></el-select>
          </el-form-item></el-col>
          <el-col :span="8"><el-form-item label="打样工厂">
            <RemoteSelect v-model="form.sampleFactoryId" :fetch="fetchFactorySuppliers" clearable placeholder="选择工厂" @change="(v: any) => { if (v === ADD_MARKER) { form.sampleFactoryId = undefined; router.push('/supplier/manage'); return } }"><el-option label="+ 新增" :value="ADD_MARKER" /></RemoteSelect>
          </el-form-item></el-col>
          <el-col :span="8"><el-form-item label="委外工厂">
            <RemoteSelect v-model="form.outsourceFactoryId" :fetch="fetchFactorySuppliers" clearable placeholder="选择工厂" @change="(v: any) => { if (v === ADD_MARKER) { form.outsourceFactoryId = undefined; router.push('/supplier/manage'); return } }"><el-option label="+ 新增" :value="ADD_MARKER" /></RemoteSelect>
          </el-form-item></el-col>
          <el-col :span="8"><el-form-item label="品牌"><el-select v-model="form.brandId" filterable clearable placeholder="选择品牌" style="width:100%" @change="(v: any) => { if (v === ADD_MARKER) { form.brandId = undefined; router.push('/inventory/brand'); return } }"><el-option v-for="b in brandOptions" :key="b.id" :label="b.brandName" :value="b.id" /><el-option label="+ 新增" :value="ADD_MARKER" /></el-select></el-form-item></el-col>
        </el-row>

        <!-- 原机配置 -->
        <el-divider content-position="left">原机配置</el-divider>
        <el-row :gutter="16">
          <el-col :span="8"><el-form-item label="原机尺寸"><el-input v-model="form.originalSize" placeholder="如 6.1寸" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="原分辨率"><el-input v-model="form.originalResolution" placeholder="如 1080×2400" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="驱动IC"><el-input v-model="form.originalDriveIc" placeholder="原机驱动IC型号" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="触摸IC"><el-input v-model="form.originalTouchIc" placeholder="原机触摸IC型号" /></el-form-item></el-col>
        </el-row>

        <!-- 改配信息 -->
        <el-divider content-position="left">改配信息</el-divider>
        <el-row :gutter="16">
          <el-col :span="8"><el-form-item label="玻璃尺寸"><el-input v-model="form.glassSize" placeholder="如 6.1寸" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="玻璃分辨率"><el-input v-model="form.glassResolution" placeholder="如 1080×2400" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="驱动IC">
            <RemoteSelect v-model="form.configDriveIcId" :fetch="(kw: string) => fetchMaterialsByType(kw, bomTypeIdMap.drive)" label-key="materialName" clearable filterable :disabled="!bomTypeIdMap.drive" style="width:100%" placeholder="选择驱动IC物料" />
          </el-form-item></el-col>
          <el-col :span="8"><el-form-item label="触摸IC">
            <RemoteSelect v-model="form.configTouchIcId" :fetch="(kw: string) => fetchMaterialsByType(kw, bomTypeIdMap.touch)" label-key="materialName" clearable filterable :disabled="!bomTypeIdMap.touch" style="width:100%" placeholder="选择触摸IC物料" />
          </el-form-item></el-col>
          <el-col :span="8"><el-form-item label="码片IC">
            <RemoteSelect v-model="form.configCodeIcId" :fetch="(kw: string) => fetchMaterialsByType(kw, bomTypeIdMap.code)" label-key="materialName" clearable filterable :disabled="!bomTypeIdMap.code" style="width:100%" placeholder="选择码片IC物料" />
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

</style>
