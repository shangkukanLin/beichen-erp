<script setup lang="ts">
import { reactive, ref, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import request from '@/utils/request'

const route = useRoute()
const materialId = Number(route.params.id)

// ===================== 物料基础信息 =====================
const material = reactive<any>({
  id: undefined, projectId: undefined, name: '', type: '', quantity: 1,
  purchaseDate: '', amount: 0, status: '完好', remark: ''
})
const materialTypeOptions = ref<string[]>([])
const materialStatusOptions = ['完好', '已损坏', '已使用']
const saving = ref(false)

async function loadMaterialDetail() {
  try {
    const res: any = await request.get(`/dev/purchase-item/${materialId}`)
    if (res) Object.assign(material, res)
    else ElMessage.warning('未找到该物料')
  } catch (e: any) { ElMessage.error('加载物料失败: ' + (e?.message || '')) }
}

async function handleSaveMaterial() {
  if (!material.name || !material.name.trim()) { ElMessage.warning('请输入名称'); return }
  saving.value = true
  try {
    const payload = {
      id: material.id, projectId: material.projectId, name: material.name, type: material.type,
      quantity: material.quantity,
      purchaseDate: material.purchaseDate, amount: material.amount,
      status: material.status, remark: material.remark
    }
    await request.put(`/dev/purchase-item/${material.id}`, payload)
    ElMessage.success('已保存')
  } catch (e: any) { ElMessage.error('保存失败: ' + (e?.message || '')) } finally { saving.value = false }
}

// ===================== 位置流转记录 =====================
interface FlowRecord {
  id?: number
  materialId?: number
  placeType: string
  placeId?: number
  placeName: string
  placeDetail: string
  handler: string
  flowTime: string
  images: string
  remark: string
}

const flowList = ref<FlowRecord[]>([])
const placeTypeOptions = [
  { label: '自有仓库', value: 'INVENTORY' },
  { label: '委外仓库', value: 'OUTSOURCE' },
  { label: '供应商', value: 'SUPPLIER' },
  { label: '客户', value: 'CUSTOMER' },
  { label: '自定义', value: 'TEXT' }
]
const placeTypeLabelMap: Record<string, string> = {
  INVENTORY: '自有仓库', OUTSOURCE: '委外仓库', SUPPLIER: '供应商', CUSTOMER: '客户', TEXT: '自定义'
}
// 下拉关联数据源
const warehouseOptions = ref<any[]>([])
const supplierOptions = ref<any[]>([])
const customerOptions = ref<any[]>([])

async function loadFlowList() {
  try {
    const res: any = await request.get('/dev/material-flow/list', { params: { materialId } })
    flowList.value = res || []
  } catch (e: any) { ElMessage.error('加载流转记录失败: ' + (e?.message || '')) }
}

async function loadPlaceOptions() {
  try { const res: any = await request.get('/dev/purchase-item/warehouse-options'); warehouseOptions.value = res || [] } catch (e) { /* ignore */ }
  try { const res: any = await request.get('/supplier/page', { params: { pageSize: 500 } }); supplierOptions.value = res?.records || [] } catch (e) { /* ignore */ }
  try { const res: any = await request.get('/inventory/customer/page', { params: { pageSize: 500 } }); customerOptions.value = res?.records || [] } catch (e) { /* ignore */ }
}

// 新增/编辑流转记录弹窗
const flowDialogVisible = ref(false)
const isFlowEdit = ref(false)
const flowForm = reactive<FlowRecord>({
  materialId: undefined, placeType: '', placeId: undefined, placeName: '',
  placeDetail: '', handler: '', flowTime: '', images: '', remark: ''
})
const flowImageList = ref<{ name: string; url: string }[]>([])

function handleAddFlow() {
  Object.assign(flowForm, {
    id: undefined, materialId, placeType: '', placeId: undefined, placeName: '',
    placeDetail: '', handler: '', flowTime: formatLocalDateTime(new Date()), images: '', remark: ''
  })
  flowImageList.value = []
  isFlowEdit.value = false
  flowDialogVisible.value = true
}

// 生成本地时间的 ISO 格式字符串（YYYY-MM-DDTHH:mm:ss），与 el-date-picker value-format 对齐
function formatLocalDateTime(d: Date): string {
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

function handleEditFlow(row: FlowRecord) {
  Object.assign(flowForm, { ...row })
  flowImageList.value = (row.images || '').split(',').filter(Boolean).map((url: string) => ({ name: url.split('/').pop() || url, url }))
  isFlowEdit.value = true
  flowDialogVisible.value = true
}

async function handleFlowSubmit() {
  if (!flowForm.placeType) { ElMessage.warning('请选择位置类型'); return }
  if (flowForm.placeType !== 'TEXT' && !flowForm.placeId) { ElMessage.warning('请选择位置'); return }
  if (flowForm.placeType === 'TEXT' && !flowForm.placeDetail?.trim()) { ElMessage.warning('请输入位置'); return }
  const payload: any = { ...flowForm, materialId, images: flowImageList.value.map((i: any) => i.url).join(',') }
  if (payload.placeType === 'TEXT') { payload.placeId = null }
  try {
    if (isFlowEdit.value && flowForm.id) {
      await request.put(`/dev/material-flow/${flowForm.id}`, payload)
      ElMessage.success('已更新')
    } else {
      await request.post('/dev/material-flow', payload)
      ElMessage.success('已添加')
    }
    flowDialogVisible.value = false
    loadFlowList()
  } catch (e: any) { ElMessage.error('操作失败: ' + (e?.message || '')) }
}

async function handleDeleteFlow(row: FlowRecord) {
  try {
    await ElMessageBox.confirm('确定删除该流转记录吗？', '提示', { type: 'warning' })
    await request.delete(`/dev/material-flow/${row.id}`)
    ElMessage.success('已删除')
    loadFlowList()
  } catch (e: any) { if (e !== 'cancel' && e !== 'close') { console.error(e) } }
}

// 图片上传（拖拽/点击，支持多图）
const uploading = ref(false)
async function handleImageUpload(file: File) {
  uploading.value = true
  try {
    const fd = new FormData()
    fd.append('file', file)
    const url: any = await request.post('/dev/file/upload', fd)
    flowImageList.value.push({ name: file.name, url: url as unknown as string })
  } catch (e: any) { ElMessage.error('上传失败: ' + (e?.message || '')) } finally { uploading.value = false }
  return false
}
function removeImage(i: number) { flowImageList.value.splice(i, 1) }
function previewImage(url: string) { window.open(url + '?inline=true') }

// 当前位置
const currentPlace = computed(() => flowList.value[0] || null)

onMounted(() => {
  loadMaterialType()
  loadMaterialDetail()
  loadFlowList()
  loadPlaceOptions()
})

async function loadMaterialType() {
  try { const res: any = await request.get('/dev/purchase-item/material-types'); materialTypeOptions.value = res || [] } catch (e) { /* ignore */ }
}
</script>

<template>
  <div class="material-detail-page">
    <!-- 物料基础信息 -->
    <el-card shadow="never">
      <template #header><span style="font-weight:600">基础信息</span></template>
      <el-form :model="material" label-width="90px" size="default">
        <el-row :gutter="16">
          <el-col :span="8"><el-form-item label="名称"><el-input v-model="material.name" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="类型">
            <el-select v-model="material.type" style="width:100%" placeholder="请选择类型">
              <el-option v-for="t in materialTypeOptions" :key="t" :label="t" :value="t" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="8"><el-form-item label="数量"><el-input-number v-model="material.quantity" :min="0" :precision="0" style="width:100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="状态">
            <el-select v-model="material.status" style="width:100%">
              <el-option v-for="s in materialStatusOptions" :key="s" :label="s" :value="s" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="8"><el-form-item label="采购日期"><el-input v-model="material.purchaseDate" type="date" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="金额"><el-input-number v-model="material.amount" :min="0" :precision="2" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="当前所在">
            <el-tag v-if="currentPlace" type="primary">{{ placeTypeLabelMap[currentPlace.placeType] || currentPlace.placeType }}：{{ currentPlace.placeName || currentPlace.placeDetail || '-' }}</el-tag>
            <span v-else style="color:var(--app-text-secondary)">暂无流转记录</span>
          </el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="material.remark" type="textarea" :rows="2" /></el-form-item></el-col>
        </el-row>
        <el-button type="primary" :loading="saving" @click="handleSaveMaterial">保存</el-button>
      </el-form>
    </el-card>

    <!-- 位置流转记录 -->
    <el-card shadow="never" style="margin-top:12px">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span style="font-weight:600">位置流转记录</span>
          <el-button type="primary" size="small" @click="handleAddFlow">+ 新增流转</el-button>
        </div>
      </template>
      <el-timeline v-if="flowList.length > 0">
        <el-timeline-item
          v-for="f in flowList" :key="f.id"
          :timestamp="f.flowTime"
          placement="top"
          :type="f.placeType === 'TEXT' ? 'info' : 'primary'"
        >
          <div class="flow-item">
            <div class="flow-item-title">
              <el-tag size="small">{{ placeTypeLabelMap[f.placeType] || f.placeType }}</el-tag>
              <span style="font-weight:600;margin-left:8px">{{ f.placeName || f.placeDetail || '-' }}</span>
              <span v-if="f.handler" style="color:var(--app-text-secondary);margin-left:8px;font-size:12px">经办人：{{ f.handler }}</span>
            </div>
            <div v-if="f.remark" class="flow-item-remark">{{ f.remark }}</div>
            <div v-if="f.images" class="flow-item-images">
              <el-image
                v-for="(url, i) in f.images.split(',').filter(Boolean)" :key="i"
                :src="url" :preview-src-list="f.images.split(',').filter(Boolean)"
                fit="cover" style="width:80px;height:80px;margin-right:8px;border-radius:4px"
              />
            </div>
            <div class="flow-item-actions">
              <el-button link type="primary" size="small" @click="handleEditFlow(f)">编辑</el-button>
              <el-button link type="danger" size="small" @click="handleDeleteFlow(f)">删除</el-button>
            </div>
          </div>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无流转记录" :image-size="80" />
    </el-card>

    <!-- 新增/编辑流转记录弹窗 -->
    <el-dialog v-model="flowDialogVisible" :title="isFlowEdit ? '编辑流转记录' : '新增流转记录'" width="560px">
      <el-form :model="flowForm" label-width="90px">
        <el-form-item label="位置类型">
          <el-select v-model="flowForm.placeType" style="width:100%" @change="() => { flowForm.placeId = undefined }">
            <el-option v-for="o in placeTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="flowForm.placeType !== 'TEXT'" label="选择位置">
          <el-select v-model="flowForm.placeId" style="width:100%" filterable clearable placeholder="请选择">
            <el-option-group v-if="flowForm.placeType === 'INVENTORY' || flowForm.placeType === 'OUTSOURCE'" label="仓库">
              <el-option v-for="w in warehouseOptions.filter((x:any) => x.placeType === flowForm.placeType)" :key="w.value" :label="w.placeName" :value="w.placeId" />
            </el-option-group>
            <el-option-group v-if="flowForm.placeType === 'SUPPLIER'" label="供应商">
              <el-option v-for="s in supplierOptions" :key="s.id" :label="s.name" :value="s.id" />
            </el-option-group>
            <el-option-group v-if="flowForm.placeType === 'CUSTOMER'" label="客户">
              <el-option v-for="c in customerOptions" :key="c.id" :label="c.name" :value="c.id" />
            </el-option-group>
          </el-select>
        </el-form-item>
        <el-form-item v-else label="位置"><el-input v-model="flowForm.placeDetail" placeholder="输入自定义位置" /></el-form-item>
        <el-form-item label="经办人"><el-input v-model="flowForm.handler" placeholder="可选" /></el-form-item>
        <el-form-item label="流转时间">
          <el-date-picker v-model="flowForm.flowTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" placeholder="选择流转时间" />
        </el-form-item>
        <el-form-item label="图片">
          <div style="width:100%">
            <div v-if="flowImageList.length > 0" style="display:flex;flex-wrap:wrap;gap:8px;margin-bottom:8px">
              <div v-for="(img, i) in flowImageList" :key="i" style="position:relative">
                <el-image :src="img.url" fit="cover" style="width:80px;height:80px;border-radius:4px" :preview-src-list="flowImageList.map((x:any)=>x.url)" />
                <el-button type="danger" size="small" circle style="position:absolute;top:-6px;right:-6px" @click="removeImage(i)">×</el-button>
              </div>
            </div>
            <el-upload
              drag
              multiple
              :show-file-list="false"
              :before-upload="handleImageUpload"
              accept="image/*"
              :disabled="uploading"
            >
              <el-icon><UploadFilled /></el-icon>
              <div class="el-upload__text">将图片拖到此处，或<em>点击上传</em></div>
            </el-upload>
          </div>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="flowForm.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="flowDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleFlowSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.material-detail-page { display:flex; flex-direction:column; gap:12px; }
.flow-item { padding:4px 0; }
.flow-item-title { display:flex; align-items:center; }
.flow-item-remark { margin-top:6px; color:var(--app-text-secondary); font-size:13px; }
.flow-item-images { margin-top:8px; display:flex; flex-wrap:wrap; }
.flow-item-actions { margin-top:8px; }
</style>
