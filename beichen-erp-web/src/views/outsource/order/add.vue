<script setup lang="ts">
import { reactive, ref, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { useTabStore } from '@/stores/tabs'

const router = useRouter()
const route = useRoute()
const tabStore = useTabStore()
const saving = ref(false)

const form = reactive({
  factoryId: undefined as any,
  planStartDate: new Date().toISOString().split('T')[0],
  planEndDate: '',
  taxIncluded: 0,
  taxRate: '',
  remark: '',
  attachUrl: ''
})

const products = ref<any[]>([])

const factoryOptions = ref<any[]>([])
const projectOptions = ref<any[]>([])
const uploadFile = ref<File | null>(null)

async function loadOptions() {
  try { const r = await request.get<any,any>('/supplier/page',{params:{supplierType:'factory',pageSize:200}}); factoryOptions.value=r?.records||[] } catch (e: any) { console.warn('加载工厂选项失败', e?.message || e) }
  try { const r = await request.get<any,any>('/dev/project/page',{params:{pageSize:200}}); projectOptions.value=r?.records||[] } catch (e: any) { console.warn('加载项目选项失败', e?.message || e) }
}

function addProduct() {
  products.value.push({
    _key: Date.now(),
    projectId: undefined as any,
    quantity: 0,
    unitPrice: 0,
    amount: 0,
    remark: '',
    materials: [] as any[]
  })
}

function removeProduct(idx: number) { products.value.splice(idx, 1) }

function onProjectSelect(idx: number, pid: number) {
  const proj = projectOptions.value.find((v:any) => v.id === pid)
  if (proj) {
    loadBomMaterials(idx, pid)
  }
}

async function loadBomMaterials(idx: number, pid: number) {
  try {
    const mats = await request.get<any,any>(`/dev/bom/project/${pid}`)
    if (mats && Array.isArray(mats)) {
      const qty = Number(products.value[idx].quantity) || 1
      products.value[idx].materials = mats.map((m:any) => ({
        materialName: m.materialName || '',
        materialType: m.materialType || '',
        unit: m.unit || '',
        bomQuantityPerSet: Number(m.quantityPerSet || 0),
        demandQuantity: +(qty * Number(m.quantityPerSet || 0)).toFixed(4),
        lossRate: m.lossRate || 0,
        remark: ''
      }))
    }
  } catch { products.value[idx].materials = [] }
}

// 当数量变化时重新计算需求数量
function onQuantityChange(idx: number) {
  calcAmount(idx)
  const qty = Number(products.value[idx].quantity) || 1
  products.value[idx].materials.forEach((mat:any) => {
    mat.demandQuantity = +(qty * Number(mat.bomQuantityPerSet || 0)).toFixed(4)
  })
}

function calcAmount(idx: number) {
  const p = products.value[idx]
  p.amount = (Number(p.quantity) || 0) * (Number(p.unitPrice) || 0)
}

// 附件
function handleDragOver(e: DragEvent) { e.preventDefault() }
function handleDrop(e: DragEvent) { e.preventDefault(); const file = e.dataTransfer?.files?.[0]; if (file) uploadFile.value = file }
function handleFileSelect(e: Event) { const file = (e.target as HTMLInputElement).files?.[0]; if (file) uploadFile.value = file }
function handleRemoveUploadFile() { uploadFile.value = null }

async function handleSubmit() {
  if (!form.factoryId) { ElMessage.warning('请选择加工厂'); return }
  if (products.value.length === 0) { ElMessage.warning('请添加加工产品'); return }
  const zeroQty = products.value.find((p: any) => !p.quantity || Number(p.quantity) <= 0)
  if (zeroQty) { ElMessage.warning('加工产品数量必须大于0'); return }
  saving.value = true
  try {
    if (uploadFile.value) { const fd = new FormData(); fd.append('file', uploadFile.value); const res = await request.post<any,string>('/dev/file/upload', fd); form.attachUrl = res as unknown as string }
    // 清理空字符串（避免后端解析异常）
    const cleanForm: any = {}
    for (const [k, v] of Object.entries(form)) {
      if (v === '' || v === undefined) continue
      cleanForm[k] = v
    }
    // 提交时映射 productName 为项目名
    const submitProducts = products.value.map((p:any) => {
      const proj = projectOptions.value.find((pr:any) => pr.id === p.projectId)
      return { ...p, productName: proj?.name || '', productSpec: '' }
    })
    await request.post('/outsource/order', { ...cleanForm, products: submitProducts })
    ElMessage.success('加工单创建成功')
    tabStore.removeTab(route.path)
    router.replace('/outsource/order')
  } catch (e: any) {
    ElMessage.error(e?.message || '创建加工单失败')
  } finally { saving.value = false }
}

onMounted(async () => {
  await loadOptions()
  addProduct()
  // 从研发项目跳转过来时自动填充
  const qFactoryId = route.query.factoryId
  const qProjectId = route.query.projectId
  if (qFactoryId) form.factoryId = Number(qFactoryId)
  if (qProjectId) {
    products.value[0].projectId = Number(qProjectId)
    // 延迟触发 BOM 加载（等 projectOptions 就绪）
    setTimeout(() => onProjectSelect(0, Number(qProjectId)), 300)
  }
})
</script>

<template>
  <div class="add-page">
    <div class="page-header">
      <el-button @click="router.push('/outsource/order')">返回列表</el-button>
      <span class="page-title">新增加工单</span>
    </div>

    <!-- 基本信息 -->
    <el-card shadow="never">
      <template #header><span style="font-weight:600">基本信息</span></template>
      <el-form :model="form" label-width="90px" size="small">
        <el-row :gutter="16">
          <el-col :span="8"><el-form-item label="加工厂"><el-select v-model="form.factoryId" filterable style="width:100%" placeholder="请选择"><el-option v-for="f in factoryOptions" :key="f.id" :label="f.name" :value="f.id" /></el-select></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="计划开始"><el-input v-model="form.planStartDate" type="date" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="计划完成"><el-input v-model="form.planEndDate" type="date" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="是否含税"><el-switch v-model="form.taxIncluded" :active-value="1" :inactive-value="0" /></el-form-item></el-col>
          <el-col :span="8" v-if="form.taxIncluded"><el-form-item label="税率(%)"><el-input v-model="form.taxRate" placeholder="如13" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item></el-col>
        </el-row>
      </el-form>
    </el-card>

    <!-- 加工产品 -->
    <el-card v-for="(p, pi) in products" :key="p._key" shadow="never" style="margin-top:12px">
      <template #header>
        <div style="display:flex;align-items:center;justify-content:space-between">
          <span style="font-weight:600">加工产品 #{{ pi + 1 }}</span>
          <el-button type="danger" size="small" text @click="removeProduct(pi)" v-if="products.length>1">删除产品</el-button>
        </div>
      </template>
      <el-form :model="p" label-width="90px" size="small">
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item label="关联项目"><el-select v-model="p.projectId" filterable clearable style="width:100%" placeholder="选择研发项目" @change="(v:any)=>onProjectSelect(pi,v)"><el-option v-for="pr in projectOptions" :key="pr.id" :label="pr.name" :value="pr.id" /></el-select></el-form-item></el-col>
          <el-col :span="5"><el-form-item label="数量"><el-input v-model="p.quantity" type="number" @change="onQuantityChange(pi)" /></el-form-item></el-col>
          <el-col :span="5"><el-form-item label="单价"><el-input v-model="p.unitPrice" type="number" @change="calcAmount(pi)" /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="小计"><el-input :model-value="p.amount" readonly /></el-form-item></el-col>
        </el-row>
      </el-form>

      <!-- BOM物料表（只读，需求数量自动计算） -->
      <div style="margin-top:8px" v-if="p.materials.length > 0">
        <div style="margin-bottom:6px"><span style="font-weight:500;font-size:13px">BOM物料清单</span></div>
        <el-table :data="p.materials" border size="small">
          <el-table-column prop="materialType" label="类型" width="80" />
          <el-table-column prop="materialName" label="物料名称" min-width="150" />
          <el-table-column prop="unit" label="单位" width="60" />
          <el-table-column label="单套用量" width="90"><template #default="{row}">{{ row.bomQuantityPerSet }}</template></el-table-column>
          <el-table-column label="需求数量" width="100"><template #default="{row}">{{ row.demandQuantity }}</template></el-table-column>
          <el-table-column label="损耗率(%)" width="110">
            <template #default="{row}"><el-input v-model="row.lossRate" size="small" placeholder="0" /></template>
          </el-table-column>
          <el-table-column label="备注" min-width="100">
            <template #default="{row}"><el-input v-model="row.remark" size="small" /></template>
          </el-table-column>
        </el-table>
      </div>
      <div v-else style="margin-top:8px;color:#909399;font-size:13px">选择关联项目后自动加载 BOM 物料清单</div>
    </el-card>

    <div style="margin-top:12px"><el-button type="primary" @click="addProduct">+ 添加产品</el-button></div>

    <!-- 合同文件 -->
    <el-card shadow="never" style="margin-top:12px">
      <template #header><span style="font-weight:600">合同文件</span></template>
      <div class="drop-zone" @dragover="handleDragOver" @drop="handleDrop" :style="{ borderColor: uploadFile?'#67c23a':'#dcdfe6', background: uploadFile?'#f0f9eb':'#fafafa' }">
        <template v-if="uploadFile"><div style="display:flex;align-items:center;justify-content:center;gap:8px;flex-wrap:wrap"><span style="color:#67c23a;font-weight:600">📎 {{ uploadFile.name }}</span><el-button type="danger" size="small" @click.stop="handleRemoveUploadFile">移除</el-button></div></template>
        <template v-else><p style="color:#909399;margin:0">拖拽合同文件到此处，或点击选择</p></template>
        <input type="file" @change="handleFileSelect" style="position:absolute;inset:0;opacity:0;cursor:pointer" />
      </div>
    </el-card>

    <div style="margin-top:16px"><el-button type="primary" size="large" :loading="saving" @click="handleSubmit">提交并确认</el-button><el-button size="large" @click="router.push('/outsource/order')">取消</el-button></div>
  </div>
</template>

<style scoped>
.add-page { display:flex; flex-direction:column; gap:0; }
.page-header { display:flex; align-items:center; gap:16px; padding-bottom:8px; }
.page-title { font-size:18px; font-weight:600; }
.drop-zone { position:relative; border:2px dashed #dcdfe6; border-radius:8px; padding:20px; text-align:center; transition:all .3s; cursor:pointer; margin-top:8px }
.drop-zone:hover { border-color:#409eff; background:#ecf5ff }
</style>
