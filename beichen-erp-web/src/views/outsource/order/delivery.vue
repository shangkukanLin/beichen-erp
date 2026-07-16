<script setup lang="ts">
defineOptions({ name: 'OrderDelivery' })
import { reactive, ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

const route = useRoute(); const router = useRouter()
const orderId = Number(route.params.id)
const loading = ref(true)
const deliveries = ref<any[]>([])
const summary = ref<any>({})
const products = ref<any[]>([])

const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref<number>()
const saving = ref(false)
const uploadFile = ref<File | null>(null)
const deliveryDate = ref(new Date().toISOString().split('T')[0])
const warehouseId = ref<number>()
const form = reactive({ productName: '', quantity: '', deliveryDate: new Date().toISOString().split('T')[0], trackingNo: '', remark: '', attachUrl: '' })

const warehouseOptions = ref<any[]>([])
async function loadWarehouses() {
  try { const r = await request.get<any,any>('/inventory/warehouse/page', { params: { pageSize: 200 } }); warehouseOptions.value = r?.records || [] } catch (e: any) { console.warn('加载仓库失败', e?.message || e) }
}

async function loadData() {
  loading.value = true
  try {
    const [dList, dSummary, prods] = await Promise.all([
      request.get<any,any>(`/outsource/order-delivery/list/${orderId}`),
      request.get<any,any>(`/outsource/order-delivery/summary/${orderId}`),
      request.get<any,any>(`/outsource/order/${orderId}/products`)
    ])
    deliveries.value = dList || []
    summary.value = dSummary || {}
    products.value = prods || []
  } catch (e: any) { console.warn('加载交货数据失败', e?.message || e) } finally { loading.value = false }
}

const progress = computed(() => {
  const total = Number(summary.value.totalQuantity || 0)
  const delivered = Number(summary.value.deliveredQuantity || 0)
  if (total === 0) return 0
  return Math.min(100, Math.round((delivered / total) * 100))
})

function openAdd() {
  isEdit.value = false; editId.value = undefined
  warehouseId.value = undefined; uploadFile.value = null
  form.productName = ''; form.quantity = ''; form.deliveryDate = new Date().toISOString().split('T')[0]
  form.trackingNo = ''; form.remark = ''; form.attachUrl = ''
  dialogVisible.value = true
  loadWarehouses()
}

function openEdit(row: any) {
  isEdit.value = true; editId.value = row.id
  warehouseId.value = row.warehouseId || undefined; uploadFile.value = null
  Object.assign(form, {
    productName: row.productName, quantity: row.quantity, deliveryDate: row.deliveryDate,
    trackingNo: row.trackingNo || '', remark: row.remark || '', attachUrl: row.attachUrl || ''
  })
  dialogVisible.value = true
  loadWarehouses()
}

function handleDragOver(e: DragEvent) { e.preventDefault() }
function handleDrop(e: DragEvent) { e.preventDefault(); const file = e.dataTransfer?.files?.[0]; if (file) uploadFile.value = file }
function handleFileSelect(e: Event) { const file = (e.target as HTMLInputElement).files?.[0]; if (file) uploadFile.value = file }
function handleRemoveUploadFile() { uploadFile.value = null }

function openAttach(url: string) { window.open(url + '?inline=true') }

async function handleSubmit(forceDelivery = false) {
  if (!form.productName) { ElMessage.warning('请选择产品名称'); return }
  if (!form.quantity) { ElMessage.warning('请输入数量'); return }
  if (!warehouseId.value) { ElMessage.warning('请选择收货仓库'); return }
  saving.value = true
  try {
    if (uploadFile.value) {
      const fd = new FormData(); fd.append('file', uploadFile.value)
      const res = await request.post<any,string>('/dev/file/upload', fd)
      form.attachUrl = res as unknown as string
    }
    const body = { ...form, orderId, warehouseId: warehouseId.value || null }
    const params = forceDelivery ? { params: { forceDelivery: true } } : {}
    let res: any
    if (isEdit.value && editId.value) {
      res = await request.put(`/outsource/order-delivery/${editId.value}`, body, params)
    } else {
      res = await request.post('/outsource/order-delivery', body, params)
    }
    console.log('[交货] 后端响应:', JSON.stringify(res))
    // 检查是否需要确认缺料（canProceed 不是 true 时都视为缺料）
    if (res && res.canProceed !== true) {
      saving.value = false
      const shortages = (res.shortages || []) as any[]
      // 构建 HTML 格式的缺料表格
      let html = '<div style="margin-bottom:8px">以下物料库存不足，是否确认强制出库？</div>'
      html += '<table style="width:100%;border-collapse:collapse;font-size:13px">'
      html += '<tr style="background:#f5f7fa"><th style="padding:6px;border:1px solid #ebeef5;text-align:left">物料名称</th><th style="padding:6px;border:1px solid #ebeef5">需要</th><th style="padding:6px;border:1px solid #ebeef5">库存</th><th style="padding:6px;border:1px solid #ebeef5">缺口</th></tr>'
      for (const s of shortages) {
        html += `<tr><td style="padding:6px;border:1px solid #ebeef5">${s.materialName || ''}</td>`
        html += `<td style="padding:6px;border:1px solid #ebeef5;text-align:center;color:#e6a23c">${s.needed || 0}</td>`
        html += `<td style="padding:6px;border:1px solid #ebeef5;text-align:center;color:#f56c6c">${s.stock || 0}</td>`
        html += `<td style="padding:6px;border:1px solid #ebeef5;text-align:center;color:#f56c6c;font-weight:600">${s.gap || 0}</td></tr>`
      }
      html += '</table>'
      html += '<div style="margin-top:8px;color:#909399;font-size:12px">确认后物料库存将变为负数</div>'
      try {
        await ElMessageBox.confirm(html, '缺料提示', {
          confirmButtonText: '确认强制出库',
          cancelButtonText: '取消',
          type: 'warning',
          dangerouslyUseHTMLString: true
        })
      } catch {
        return // 用户取消
      }
      // 用户确认，强制出库
      return handleSubmit(true)
    }
    ElMessage.success(isEdit.value ? '交货记录已更新' : '交货记录已保存')
    dialogVisible.value = false
    loadData()
  } catch (e: any) {
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error('保存失败: ' + (e?.message || '未知错误'))
    }
  } finally { saving.value = false }
}

async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm('确定删除该交货记录吗？', '删除', { type: 'warning' })
    await request.delete(`/outsource/order-delivery/${row.id}`)
    ElMessage.success('已删除')
    loadData()
  } catch (e: any) { if (e !== 'cancel' && e !== 'close') { console.error(e) } }
}

onMounted(loadData)
</script>

<template>
  <div class="delivery-page" v-loading="loading">
    <div class="page-header">
      <el-button @click="router.push(`/outsource/order/detail/${orderId}`)">← 返回加工单</el-button>
      <span class="page-title">交货管理</span>
    </div>

    <!-- 汇总卡片 -->
    <el-row :gutter="12" style="margin-bottom:12px">
      <el-col :span="6"><el-card shadow="never"><p style="color:#909399;font-size:12px;margin:0">订单总量</p><p style="font-size:20px;font-weight:600;margin:4px 0">{{ summary.totalQuantity || 0 }}</p></el-card></el-col>
      <el-col :span="6"><el-card shadow="never"><p style="color:#909399;font-size:12px;margin:0">已交数量</p><p style="font-size:20px;font-weight:600;margin:4px 0;color:#67c23a">{{ summary.deliveredQuantity || 0 }}</p></el-card></el-col>
      <el-col :span="6"><el-card shadow="never"><p style="color:#909399;font-size:12px;margin:0">剩余数量</p><p style="font-size:20px;font-weight:600;margin:4px 0;color:#e6a23c">{{ summary.remainingQuantity || 0 }}</p></el-card></el-col>
      <el-col :span="6"><el-card shadow="never"><p style="color:#909399;font-size:12px;margin:0">交货进度</p><p style="font-size:20px;font-weight:600;margin:4px 0;color:#409eff">{{ progress }}%</p></el-card></el-col>
    </el-row>

    <el-card shadow="never" style="margin-bottom:12px">
      <el-progress :percentage="progress" :stroke-width="16" :text-inside="true" :color="progress>=100?'#67c23a':'#409eff'" />
    </el-card>

    <!-- 按产品分类 -->
    <el-card shadow="never" style="margin-bottom:12px" v-if="summary.productStats && summary.productStats.length > 1">
      <template #header><span style="font-weight:600">按产品分类统计</span></template>
      <el-table :data="summary.productStats" border size="small">
        <el-table-column prop="productName" label="产品名称" min-width="150" />
        <el-table-column prop="totalQuantity" label="订单数量" width="100" />
        <el-table-column label="已交数量" width="100"><template #default="{row}"><span style="color:#67c23a;font-weight:500">{{ row.deliveredQuantity }}</span></template></el-table-column>
        <el-table-column label="剩余数量" width="100"><template #default="{row}"><span :style="{color: Number(row.remainingQuantity)<=0?'#67c23a':'#e6a23c',fontWeight:'500'}">{{ row.remainingQuantity }}</span></template></el-table-column>
        <el-table-column label="进度" width="180"><template #default="{row}"><el-progress :percentage="Number(row.totalQuantity)===0?0:Math.min(100,Math.round(Number(row.deliveredQuantity)/Number(row.totalQuantity)*100))" :stroke-width="12" :color="Number(row.remainingQuantity)<=0?'#67c23a':'#409eff'" /></template></el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never">
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px">
        <span style="font-weight:600">交货记录</span>
        <el-button type="primary" size="small" @click="openAdd">新增交货</el-button>
      </div>
      <el-table :data="deliveries" border stripe size="small">
        <el-table-column prop="deliveryDate" label="交货日期" width="110" />
        <el-table-column prop="productName" label="产品名称" min-width="130" />
        <el-table-column label="收货仓库" width="120">
          <template #default="{row}">
            <span v-if="row.warehouseId">{{ warehouseOptions.find((w:any)=>w.id===row.warehouseId)?.warehouseName || row.warehouseId }}</span>
            <span v-else style="color:#c0c4cc">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="quantity" label="数量" width="100" />
        <el-table-column prop="trackingNo" label="物流单号" width="140" />
        <el-table-column label="附件" width="80" align="center">
          <template #default="{row}">
            <el-button v-if="row.attachUrl" type="primary" link size="small" @click="openAttach(row.attachUrl)">查看</el-button>
            <span v-else style="color:#c0c4cc">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="150" />
        <el-table-column label="操作" width="120" align="center">
          <template #default="{row}">
            <el-button type="primary" link size="small" @click="openEdit(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit?'编辑交货记录':'新增交货记录'" width="520px" :close-on-click-modal="false">
      <el-form :model="form" label-width="85px" size="small">
        <el-form-item label="产品名称">
          <el-select v-model="form.productName" filterable style="width:100%" placeholder="选择订单产品">
            <el-option v-for="p in products" :key="p.id" :label="p.productName" :value="p.productName" />
          </el-select>
        </el-form-item>
        <el-form-item label="数量"><el-input v-model="form.quantity" placeholder="交货数量" /></el-form-item>
        <el-form-item label="收货仓库" required>
          <el-select v-model="warehouseId" filterable style="width:100%" placeholder="选择入库仓库">
            <el-option v-for="w in warehouseOptions" :key="w.id" :label="`${w.warehouseName} (${w.code})`" :value="w.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="交货日期"><el-input v-model="form.deliveryDate" type="date" /></el-form-item>
        <el-form-item label="物流单号"><el-input v-model="form.trackingNo" placeholder="选填" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" placeholder="选填" /></el-form-item>

        <el-form-item label="交货图片">
          <div class="drop-zone" @dragover="handleDragOver" @drop="handleDrop" :style="{ borderColor: uploadFile?'#67c23a':'#dcdfe6', background: uploadFile?'#f0f9eb':'#fafafa' }">
            <template v-if="uploadFile"><div style="display:flex;align-items:center;justify-content:center;gap:8px;flex-wrap:wrap"><span style="color:#67c23a;font-weight:600">📎 {{ uploadFile.name }}</span><el-button type="danger" size="small" @click.stop="handleRemoveUploadFile">移除</el-button></div></template>
            <template v-else-if="form.attachUrl"><div style="display:flex;align-items:center;justify-content:center;gap:4px;flex-wrap:wrap"><span style="color:#409eff">📎 已有图片</span><el-button type="primary" size="small" @click.stop="openAttach(form.attachUrl)">查看</el-button><span style="color:#909399;font-size:12px">可拖拽新文件替换</span></div></template>
            <template v-else><p style="color:#909399;margin:0">拖拽图片到此处，或点击选择</p></template>
            <input v-if="!form.attachUrl && !uploadFile" type="file" @change="handleFileSelect" style="position:absolute;inset:0;opacity:0;cursor:pointer" />
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.delivery-page { padding: 16px; }
.page-header { display: flex; align-items: center; gap: 16px; margin-bottom: 16px; }
.page-title { font-size: 20px; font-weight: 600; }
.drop-zone { position:relative; border:2px dashed #dcdfe6; border-radius:8px; padding:16px; text-align:center; transition:all .3s; cursor:pointer; margin-top:4px }
.drop-zone:hover { border-color:#409eff; background:#ecf5ff }
</style>
