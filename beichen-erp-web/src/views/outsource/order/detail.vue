<script setup lang="ts">
defineOptions({ name: 'OutsourceOrderDetail' })

import { reactive, ref, onMounted, onActivated } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { exportContractPdf } from '@/api/contract-template'

const route = useRoute(); const router = useRouter()
const loading = ref(true); const saving = ref(false)
const uploadFile = ref<File | null>(null)

// BOM物料库存缺料
const materialStockMap = ref<Record<string, any>>({})

async function loadMaterialStock() {
  if (!form.id) return
  try {
    const r = await request.get<any, any>(`/outsource/order/${form.id}/material-stock`)
    if (r?.materials) {
      const map: Record<string, any> = {}
      for (const m of r.materials) { map[m.materialName] = m }
      materialStockMap.value = map
    }
  } catch { materialStockMap.value = {} }
}
function getStock(materialName: string) {
  const s = materialStockMap.value[materialName]
  return s || { stockQuantity: 0, shortage: 0 }
}
function goPurchase(row: any) {
  const s = getStock(row.materialName)
  console.log('[goPurchase] stock data for', row.materialName, ':', s)
  const ids = (s.supplierIds || '') as string
  const firstId = ids.split(',')[0]?.trim()
  const p = new URLSearchParams()
  if (firstId) p.set('supplierId', firstId)
  if (s.materialId) p.set('materialId', String(s.materialId))
  p.set('materialName', row.materialName || '')
  p.set('materialType', row.materialType || '')
  p.set('unit', row.unit || '')
  p.set('quantity', String(s.shortage || 0))
  console.log('[goPurchase] query params:', p.toString())
  router.push('/outsource/material-order/add?' + p.toString())
}

const form = reactive({
  id: undefined as any, code: '', status: '',
  factoryId: undefined as any,
  planStartDate: '', planEndDate: '',
  actualStartDate: '', actualEndDate: '',
  taxIncluded: 0, taxRate: '',
  totalAmount: '', remark: '',
  attachUrl: '', logisticsCompany: '', logisticsNo: ''
})

const products = ref<any[]>([])
const factoryOptions = ref<any[]>([])
const projectOptions = ref<any[]>([])
const materialOptions = ref<any[]>([])

async function loadOptions() {
  try { const r = await request.get<any,any>('/supplier/page',{params:{supplierType:'factory',pageSize:200}}); factoryOptions.value=r?.records||[] } catch (e: any) { console.warn('加载工厂选项失败', e?.message || e) }
  try { const r = await request.get<any,any>('/dev/project/page',{params:{pageSize:200}}); projectOptions.value=r?.records||[] } catch (e: any) { console.warn('加载项目选项失败', e?.message || e) }
  try { const r = await request.get<any,any>('/outsource/material/page',{params:{pageSize:500}}); materialOptions.value=r?.records||[] } catch (e: any) { console.warn('加载物料选项失败', e?.message || e) }
}

async function loadData() {
  loading.value = true
  try {
    const d = await request.get<any,any>(`/outsource/order/${route.params.id}`)
    if (d) {
      Object.assign(form, {
        id: d.id, code: d.code, status: d.status,
        factoryId: d.factoryId,
        planStartDate: d.planStartDate || '', planEndDate: d.planEndDate || '',
        actualStartDate: d.actualStartDate || '', actualEndDate: d.actualEndDate || '',
        taxIncluded: d.taxIncluded || 0, taxRate: d.taxRate || '',
        totalAmount: d.totalAmount || '', remark: d.remark || '',
        attachUrl: d.attachUrl || '', logisticsCompany: d.logisticsCompany || '', logisticsNo: d.logisticsNo || ''
      })
    }
    // 产品列表
    const ps = await request.get<any,any>(`/outsource/order/${route.params.id}/products`)
    products.value = (ps || []).map((p:any) => ({
      ...p,
      _key: p.id || Date.now() + Math.random(),
      materials: (p.materials || []).map((m:any) => ({ ...m }))
    }))
    if (form.factoryId && !factoryOptions.value.some((f:any)=>f.id===form.factoryId)) {
      try { const sup = await request.get<any,any>(`/supplier/${form.factoryId}`); if (sup) factoryOptions.value.push({id:sup.id,name:sup.name}) } catch (e: any) { console.warn('加载工厂信息失败', e?.message || e) }
    }
    // 加载物料库存缺料
    await loadMaterialStock()
  } finally { loading.value = false }
}

function addProduct() {
  products.value.push({
    _key: Date.now(), projectId: undefined, productName: '', productSpec: '',
    quantity: 1, unitPrice: 0, amount: 0, remark: '', materials: []
  })
}

function removeProduct(idx: number) { products.value.splice(idx, 1) }

function onProjectSelect(idx: number, pid: number) {
  const proj = projectOptions.value.find((v:any) => v.id === pid)
  if (proj) {
    products.value[idx].productName = proj.productName || proj.name || ''
    products.value[idx].productSpec = proj.productSpec || ''
    loadBomMaterials(idx, pid)
  }
}

async function loadBomMaterials(idx: number, pid: number) {
  try {
    const mats = await request.get<any,any>(`/dev/bom/project/${pid}`)
    if (mats && Array.isArray(mats)) {
      products.value[idx].materials = mats.map((m:any) => ({
        materialId: undefined,
        materialName: m.materialName || '',
        materialType: m.materialType || '',
        unit: m.unit || '',
        demandQuantity: m.quantityPerSet || 1,
        lossRate: m.lossRate || 0,
        remark: ''
      }))
    }
  } catch { products.value[idx].materials = [] }
}

function calcAmount(idx: number) {
  const p = products.value[idx]
  p.amount = (Number(p.quantity) || 0) * (Number(p.unitPrice) || 0)
}

function onMatSelect(idx: number, mi: number, mat: any) {
  const m = materialOptions.value.find((v:any) => v.id === mi)
  if (m) { mat.materialName = m.materialName; mat.materialType = m.materialType; mat.unit = m.unit }
}

function addMaterial(idx: number) {
  products.value[idx].materials.push({
    materialId: undefined, materialName: '', materialType: '',
    unit: '', demandQuantity: 1, lossRate: 0, remark: ''
  })
}

function removeMaterial(pi: number, mi: number) { products.value[pi].materials.splice(mi, 1) }

// 附件
function openAttach(url:string) { window.open(url + '?inline=true') }
function handleDragOver(e: DragEvent) { e.preventDefault() }
function handleDrop(e: DragEvent) { e.preventDefault(); const file = e.dataTransfer?.files?.[0]; if (file) uploadFile.value = file }
function handleFileSelect(e: Event) { const file = (e.target as HTMLInputElement).files?.[0]; if (file) uploadFile.value = file }
function handleRemoveUploadFile() { uploadFile.value = null }

async function handleDeleteAttach() {
  try {
    await ElMessageBox.confirm('确定删除附件吗？', '删除附件', { confirmButtonText:'删除', cancelButtonText:'取消', type:'warning' })
    await request.delete(`/outsource/order/${form.id}/attach`)
    ElMessage.success('附件已删除')
    await loadData()
  } catch (e: any) { if (e !== 'cancel' && e !== 'close') { console.error(e) } }
}

async function handleSave() {
  saving.value = true
  try {
    if (uploadFile.value) { const fd = new FormData(); fd.append('file', uploadFile.value); const res = await request.post<any,string>('/dev/file/upload', fd); form.attachUrl = res as unknown as string }
    await request.put(`/outsource/order/${form.id}`, { ...form, products: products.value })
    ElMessage.success('保存成功')
    await loadData()
  } catch (e: any) { ElMessage.error('保存失败: ' + (e?.message || '未知错误')) } finally { saving.value = false }
}

async function handleConfirm() {
  try {
    await ElMessageBox.confirm('确认后加工单将进入生产状态。', '确认加工单', { type:'warning' })
    await request.put(`/outsource/order/${form.id}/confirm`)
    ElMessage.success('已确认，进入生产中')
    await loadData()
  } catch (e: any) { if (e !== 'cancel' && e !== 'close') { console.error(e) } }
}

async function handleComplete() {
  try {
    await ElMessageBox.confirm('确认标记为已完成？', '完成加工单', { type:'warning' })
    await request.put(`/outsource/order/${form.id}/complete`)
    ElMessage.success('加工单已完成')
    await loadData()
  } catch (e: any) { if (e !== 'cancel' && e !== 'close') { console.error(e) } }
}

async function handleCancel() {
  try {
    await ElMessageBox.confirm('确定取消该加工单吗？', '取消加工单', { type:'warning' })
    await request.put(`/outsource/order/${form.id}/cancel`)
    ElMessage.success('已取消')
    await loadData()
  } catch (e: any) { if (e !== 'cancel' && e !== 'close') { console.error(e) } }
}

function exportPdf() {
  const url = exportContractPdf(form.id as number)
  request.get(url, { responseType: 'blob' }).then((res: any) => {
    const blob = new Blob([res], { type: 'application/pdf' })
    const link = document.createElement('a')
    link.href = URL.createObjectURL(blob)
    link.download = `委外加工合同-${form.code}.pdf`
    link.click()
    URL.revokeObjectURL(link.href)
    ElMessage.success('PDF合同已下载')
  }).catch(() => { ElMessage.error('导出失败') })
}

onMounted(() => { loadOptions(); loadData() })
onActivated(() => { loadOptions(); loadData() })
</script>

<template>
  <div class="detail-page" v-loading="loading">
    <div class="page-header">
      <div style="display:flex;align-items:center;gap:12px;flex-wrap:wrap">
        <el-button @click="router.push('/outsource/order')">返回列表</el-button>
        <span class="page-title">{{ form.code || '加工单详情' }}</span>
        <el-tag :type="form.status==='待确认'?'info':form.status==='生产中'?'primary':form.status==='已完成'?'success':'danger'" size="small">{{ form.status }}</el-tag>
      </div>
      <div style="display:flex;justify-content:space-between;align-items:center;margin-top:8px;width:100%">
        <div style="display:flex;gap:8px">
          <el-button v-if="form.status==='待确认'" type="success" @click="handleConfirm">确认（进入生产）</el-button>
          <el-button v-if="form.status==='生产中'" type="success" @click="handleComplete">标记完成</el-button>
          <el-button v-if="form.status==='生产中'" type="warning" @click="router.push(`/outsource/order/close/${form.id}`)">结单</el-button>
          <el-button v-if="form.status==='生产中'" type="primary" @click="router.push(`/outsource/order/delivery/${form.id}`)">交货管理</el-button>
          <el-button v-if="form.status!=='已取消'" type="danger" @click="handleCancel">取消加工单</el-button>
        </div>
        <el-button type="primary" :loading="saving" @click="handleSave" :disabled="form.status==='已取消'">保存</el-button>
      </div>
    </div>

    <!-- 基本信息 -->
    <el-card shadow="never">
      <template #header><span style="font-weight:600">基本信息</span></template>
      <el-form :model="form" label-width="90px" size="small">
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item label="加工厂"><el-select v-model="form.factoryId" filterable style="width:100%" :disabled="form.status!=='待确认'"><el-option v-for="f in factoryOptions" :key="f.id" :label="f.name" :value="f.id" /></el-select></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="计划开始"><el-input v-model="form.planStartDate" type="date" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="计划完成"><el-input v-model="form.planEndDate" type="date" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="实际开始"><el-input :model-value="form.actualStartDate" readonly /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="实际完成"><el-input :model-value="form.actualEndDate" readonly /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="总金额"><el-input :model-value="Number(form.totalAmount||0).toFixed(2)" readonly /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="含税"><el-switch v-model="form.taxIncluded" :active-value="1" :inactive-value="0" disabled /></el-form-item></el-col>
          <el-col :span="8" v-if="form.taxIncluded"><el-form-item label="税率(%)"><el-input :model-value="form.taxRate" disabled /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item></el-col>
        </el-row>
      </el-form>
    </el-card>

    <!-- 加工产品 -->
    <el-card v-for="(p, pi) in products" :key="p._key" shadow="never" style="margin-top:12px">
      <template #header>
        <div style="display:flex;align-items:center;justify-content:space-between">
          <span style="font-weight:600">加工产品 #{{ pi + 1 }}</span>
          <el-button type="danger" size="small" text @click="removeProduct(pi)" v-if="products.length>1 && form.status==='待确认'">删除产品</el-button>
        </div>
      </template>
      <el-form :model="p" label-width="90px" size="small">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="关联项目"><el-select v-model="p.projectId" filterable clearable style="width:100%" :disabled="form.status!=='待确认'" @change="(v:any)=>onProjectSelect(pi,v)"><el-option v-for="pr in projectOptions" :key="pr.id" :label="pr.name" :value="pr.id" /></el-select></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="数量"><el-input v-model="p.quantity" type="number" :disabled="form.status!=='待确认'" @change="calcAmount(pi)" /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="单价"><el-input v-model="p.unitPrice" type="number" :disabled="form.status!=='待���认'" @change="calcAmount(pi)" /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="小计"><el-input :model-value="p.amount" readonly /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="备注"><el-input v-model="p.remark" :disabled="form.status!=='待确认'" /></el-form-item></el-col>
        </el-row>
      </el-form>

      <!-- BOM物料 -->
      <div style="margin-top:8px">
        <div style="margin-bottom:6px"><span style="font-weight:500;font-size:13px">BOM物料清单</span></div>
        <el-table v-if="p.materials && p.materials.length" :data="p.materials" border size="small">
          <el-table-column prop="materialType" label="类型" width="70" />
          <el-table-column prop="materialName" label="物料名称" min-width="120" />
          <el-table-column prop="unit" label="单位" width="55" />
          <el-table-column label="需求" width="75"><template #default="{row}">{{ row.demandQuantity }}</template></el-table-column>
          <el-table-column label="库存" width="75">
            <template #default="{row}">
              <span :style="{color: Number(getStock(row.materialName).stockQuantity||0) < Number(row.demandQuantity||0) ? '#f56c6c' : '#67c23a'}">{{ getStock(row.materialName).stockQuantity || 0 }}</span>
            </template>
          </el-table-column>
          <el-table-column label="缺料" width="75">
            <template #default="{row}">
              <span :style="{color: Number(getStock(row.materialName).shortage||0) > 0 ? '#f56c6c' : '#67c23a'}">{{ getStock(row.materialName).shortage || 0 }}</span>
            </template>
          </el-table-column>
          <el-table-column label="损耗率(%)" width="85">
            <template #default="{row}"><el-input v-model="row.lossRate" size="small" :disabled="form.status!=='待确认'" /></template>
          </el-table-column>
          <el-table-column label="已发料" width="80">
            <template #default="{row}"><span :style="{color: Number(row.deliveredQuantity||0)>0?'#67c23a':''}">{{ row.deliveredQuantity || 0 }}</span></template>
          </el-table-column>
          <el-table-column label="备注" min-width="80">
            <template #default="{row}"><el-input v-model="row.remark" size="small" :disabled="form.status!=='待确认'" /></template>
          </el-table-column>
          <el-table-column label="操作" width="80" align="center" v-if="form.status==='生产中'">
            <template #default="{row}">
              <el-button
                v-if="Number(getStock(row.materialName).shortage||0) > 0"
                type="warning" link size="small"
                @click="goPurchase(row)">
                去采购
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <div v-else style="color:#909399;font-size:13px;margin-top:8px">暂无 BOM 物料</div>
      </div>
    </el-card>

    <!-- 合同文件 -->
    <el-card shadow="never" style="margin-top:12px">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span style="font-weight:600">合同文件</span>
          <el-button type="warning" size="small" @click="exportPdf">导出合同模板</el-button>
        </div>
      </template>
      <div class="drop-zone" @dragover="handleDragOver" @drop="handleDrop" :style="{ borderColor: uploadFile?'#67c23a':'#dcdfe6', background: uploadFile?'#f0f9eb':'#fafafa' }">
        <template v-if="uploadFile"><div style="display:flex;align-items:center;justify-content:center;gap:8px;flex-wrap:wrap"><span style="color:#67c23a;font-weight:600">📎 {{ uploadFile.name }}</span><el-button type="danger" size="small" @click.stop="handleRemoveUploadFile">移除</el-button></div></template>
        <template v-else-if="form.attachUrl"><div style="display:flex;align-items:center;justify-content:center;gap:4px;flex-wrap:wrap"><span style="color:#409eff">📎 已有附件</span><el-button type="primary" size="small" @click.stop="openAttach(form.attachUrl)">查看</el-button><el-button type="success" size="small"><a :href="form.attachUrl" download style="color:inherit;text-decoration:none">下载</a></el-button><el-button type="danger" size="small" @click.stop="handleDeleteAttach">删除</el-button><span style="color:#909399;font-size:12px">可拖拽新文件替换</span></div></template>
        <template v-else><p style="color:#909399;margin:0">拖拽文件到此处，或点击选择</p></template>
        <input v-if="!form.attachUrl && !uploadFile" type="file" @change="handleFileSelect" style="position:absolute;inset:0;opacity:0;cursor:pointer" />
      </div>
    </el-card>

  </div>
</template>

<style scoped>
.detail-page { display:flex; flex-direction:column; gap:0; }
.page-header { display:flex; align-items:center; gap:12px; padding-bottom:8px; flex-wrap:wrap; }
.page-title { font-size:18px; font-weight:600; }
.drop-zone { position:relative; border:2px dashed #dcdfe6; border-radius:8px; padding:20px; text-align:center; transition:all .3s; cursor:pointer; margin-top:8px }
.drop-zone:hover { border-color:#409eff; background:#ecf5ff }
</style>
