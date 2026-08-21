<script setup lang="ts">
import { reactive, ref, onMounted, onActivated, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { useTabStore } from '@/stores/tabs'
import { ADD_MARKER } from '@/composables/useSelectWithAdd'
import { getProjectBom } from '@/api/system'
import RemoteSelect from '@/components/RemoteSelect.vue'

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
const materialOptions = ref<any[]>([])
const bomTypes = ref<any[]>([])
const uploadFile = ref<File | null>(null)

// bomTypeId -> 类型名 映射（兜底展示用）
function typeName(id: number | undefined, fallback?: string) {
  if (id != null) { const t = bomTypes.value.find((v: any) => v.id === id); if (t) return t.typeName }
  return fallback || '-'
}

// ===== 纯 Odoo 方案：本地轻量列表 + RemoteSelect 实时查库 =====
const fetchSuppliers = (kw: string) =>
  request.get('/supplier/page', { params: { supplierType: 'factory', name: kw, pageSize: 500 } })
const fetchProjects = (kw: string) =>
  request.get('/dev/project/page', { params: { name: kw, pageSize: 500 } })
const fetchMaterials = (kw: string) =>
  request.get('/outsource/material/page', { params: { materialName: kw, pageSize: 500 } })

async function loadOptions() {
  const [sf, pf, mf]: any[] = await Promise.all([fetchSuppliers(''), fetchProjects(''), fetchMaterials('')])
  factoryOptions.value = sf?.records || []
  projectOptions.value = pf?.records || []
  materialOptions.value = mf?.records || []
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
    products.value[idx].projectId = pid
    // 产品名称取项目总成名称(assemblyName)，与产品主数据一致；无总成名称时回退项目名
    products.value[idx].productName = proj.assemblyName || proj.name || ''
    loadBomMaterials(idx, pid)
  }
}

// 加工厂选择：选中"+ 新增"占位则跳转加工厂管理页
function onFactoryChangeProxy(v: any) {
  if (v === ADD_MARKER) { form.factoryId = undefined; router.push('/supplier/manage'); return }
}
// 加工产品选择：选中"+ 新增"占位则跳转研发项目页
function onProjectSelectProxy(idx: number, v: any) {
  if (v === ADD_MARKER) { products.value[idx].projectId = undefined; router.push('/dev/project'); return }
  onProjectSelect(idx, v)
}

async function loadBomMaterials(idx: number, pid: number) {
  try {
    const mats:any = await getProjectBom(pid)
    if (mats && Array.isArray(mats)) {
      const qty = Number(products.value[idx].quantity) || 1
      products.value[idx].materials = mats.map((m:any) => {
        const opt = materialOptions.value.find((o:any) => o.id === m.outsourceMaterialId)
        return {
          materialId: m.outsourceMaterialId || null,
          materialName: opt?.materialName || '',
          bomTypeId: m.bomTypeId || null,
          unit: m.unit || '',
          bomQuantityPerSet: Number(m.quantity || 0),
          demandQuantity: +(qty * Number(m.quantity || 0)).toFixed(4),
          lossRate: m.lossRate || 0,
          remark: ''
        }
      })
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
  const missingProject = products.value.find((p: any) => !p.projectId)
  if (missingProject) { ElMessage.warning('每个产品必须选择关联项目'); return }
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
    // 提交时映射 productName 为项目总成名称(与产品主数据一致)，并携带产品主数据ID(用于交货/库存落账)
    const submitProducts = products.value.map((p:any) => {
      const proj = projectOptions.value.find((pr:any) => pr.id === p.projectId)
      return { ...p, productName: proj?.assemblyName || proj?.name || '', productSpec: proj?.productSpec || '', productId: proj?.productId || null }
    })
    await request.post('/outsource/order', { ...cleanForm, products: submitProducts })
    ElMessage.success('加工单创建成功')
    // 重置表单，避免 keep-alive 缓存残留数据
    Object.assign(form, { factoryId: undefined, planStartDate: '', planEndDate: '', taxIncluded: 0, taxRate: '', remark: '', attachUrl: '', logisticsCompany: '', logisticsNo: '' })
    products.value = []
    uploadFile.value = null
    tabStore.removeTab(route.path)
    router.replace('/outsource/order')
  } catch (e: any) {
    ElMessage.error(e?.message || '创建加工单失败')
  } finally { saving.value = false }
}

async function loadBomTypes() {
  try { const r = await request.get<any, any>('/dev/bom-type/enabled'); bomTypes.value = r || [] } catch {}
}

// 物料直挂模式：以某物料名作为加工产品，并将其子料作为 BOM 清单（不关联研发项目）
async function loadMaterialAsProduct(idx: number, materialId: number) {
  try {
    const res = await request.post<any, any>('/outsource/material/components-batch-by-ids', [materialId])
    const childrenMap: Record<number, any[]> = res?.childrenMap || {}
    const comps = childrenMap[materialId] || []
    const mat = materialOptions.value.find((o: any) => o.id === materialId)
    const qty = Number(products.value[idx].quantity) || 1
    products.value[idx].productName = mat?.materialName || ('物料#' + materialId)
    products.value[idx].materials = comps.map((c: any) => ({
      materialId: c.materialId || null,
      materialName: c.materialName || '',
      bomTypeId: c.bomTypeId || null,
      unit: c.unit || '',
      bomQuantityPerSet: Number(c.bomQuantityPerSet || 0),
      demandQuantity: +(qty * Number(c.bomQuantityPerSet || 0)).toFixed(4),
      lossRate: 0,
      remark: ''
    }))
  } catch { products.value[idx].materials = [] }
}

async function initPage() {
  await loadOptions()
  await loadBomTypes()
  if (products.value.length === 0) addProduct()
  const q = route.query
  // 供应商详情页"去委外"带入：供应商即加工厂
  if (q.supplierId) {
    const sid = Number(q.supplierId)
    form.factoryId = sid
    if (!factoryOptions.value.some(f => f.id === sid)) {
      try {
        const res = await request.get('/supplier/page', { params: { id: sid, pageSize: 1 } })
        const rec = res?.records?.[0]
        if (rec) factoryOptions.value.push({ id: rec.id, name: rec.name })
      } catch { /* 忽略 */ }
    }
  }
  // 从研发项目跳转过来时自动填充
  const qFactoryId = q.factoryId
  const qProjectId = q.projectId
  if (qFactoryId) form.factoryId = Number(qFactoryId)
  if (qProjectId) {
    products.value[0].projectId = Number(qProjectId)
    setTimeout(() => onProjectSelect(0, Number(qProjectId)), 300)
  } else if (q.productId) {
    // 供应产品 → 加工单：经 productId 反查关联项目加载 BOM
    try {
      const p = await request.get<any, any>(`/product/${q.productId}`)
      if (p?.projectId) {
        products.value[0].projectId = p.projectId
        setTimeout(() => onProjectSelect(0, p.projectId), 300)
      }
    } catch { /* 忽略 */ }
  } else if (q.materialId) {
    // 供应物料(有子料) → 加工单：物料直挂
    const mid = Number(q.materialId)
    const mat = materialOptions.value.find((o: any) => o.id === mid)
    products.value[0].productName = mat?.materialName || ('物料#' + mid)
    loadMaterialAsProduct(0, mid)
  }
}
onMounted(initPage)
onActivated(initPage)
</script>

<template>
  <div class="add-page">
    <!-- 基础信息 -->
    <el-card shadow="never">
      <template #header><span style="font-weight:600">基础信息</span></template>
      <el-form :model="form" label-width="90px" size="small">
        <el-row :gutter="16">
          <el-col :span="8"><el-form-item label="加工厂"><RemoteSelect v-model="form.factoryId" :fetch="fetchSuppliers" placeholder="请选择" @update:modelValue="onFactoryChangeProxy"><el-option label="+ 新增" :value="ADD_MARKER" /></RemoteSelect></el-form-item></el-col>
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
          <el-col :span="8"><el-form-item label="加工产品"><RemoteSelect v-model="p.projectId" :fetch="fetchProjects" :label-key="(row:any)=>row.assemblyName || row.name" placeholder="选择产品" @update:modelValue="(v:any)=>onProjectSelectProxy(pi, v)"><el-option label="+ 新增" :value="ADD_MARKER" /></RemoteSelect></el-form-item></el-col>
          <el-col :span="5"><el-form-item label="数量"><el-input v-model="p.quantity" type="number" @change="onQuantityChange(pi)" /></el-form-item></el-col>
          <el-col :span="5"><el-form-item label="单价"><el-input v-model="p.unitPrice" type="number" @change="calcAmount(pi)" /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="小计"><el-input :model-value="p.amount" readonly /></el-form-item></el-col>
        </el-row>
      </el-form>

      <!-- BOM物料表（只读，需求数量自动计算） -->
      <div style="margin-top:8px" v-if="p.materials.length > 0">
        <div style="margin-bottom:6px"><span style="font-weight:500;font-size:var(--app-font-sm)">BOM物料清单</span></div>
        <el-table :data="p.materials" border size="small">
          <el-table-column label="类型" width="80"><template #default="{row}">{{ typeName(row.bomTypeId) }}</template></el-table-column>
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
      <div v-else style="margin-top:8px;color:var(--app-text-secondary);font-size:var(--app-font-sm)">选择关联项目后自动加载 BOM 物料清单</div>
    </el-card>

    <div style="margin-top:12px"><el-button type="primary" @click="addProduct">+ 添加产品</el-button></div>

    <!-- 合同文件 -->
    <el-card shadow="never" style="margin-top:12px">
      <template #header><span style="font-weight:600">合同文件</span></template>
      <div class="drop-zone" @dragover="handleDragOver" @drop="handleDrop" :style="{ borderColor: uploadFile?'var(--app-color-success)':'var(--app-border-color)', background: uploadFile?'#f0f9eb':'#fafafa' }">
        <template v-if="uploadFile"><div style="display:flex;align-items:center;justify-content:center;gap:8px;flex-wrap:wrap"><span style="color:var(--app-color-success);font-weight:600">📎 {{ uploadFile.name }}</span><el-button type="danger" size="small" @click.stop="handleRemoveUploadFile">移除</el-button></div></template>
        <template v-else><p style="color:var(--app-text-secondary);margin:0">拖拽合同文件到此处，或点击选择</p></template>
        <input type="file" @change="handleFileSelect" style="position:absolute;inset:0;opacity:0;cursor:pointer" />
      </div>
    </el-card>

    <div style="margin-top:16px"><el-button type="primary" size="large" :loading="saving" @click="handleSubmit">提交并确认</el-button><el-button size="large" @click="router.push('/outsource/order')">取消</el-button></div>
  </div>
</template>

<style scoped>
.add-page { display:flex; flex-direction:column; gap:0; }

.drop-zone { position:relative; border:2px dashed var(--app-border-color); border-radius:8px; padding:20px; text-align:center; transition:all .3s; cursor:pointer; margin-top:8px }
.drop-zone:hover { border-color:var(--app-color-primary); background:#ecf5ff }
</style>
