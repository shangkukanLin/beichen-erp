<script setup lang="ts">
defineOptions({ name: 'OrderDelivery' })
import { reactive, ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import RemoteSelect from '@/components/RemoteSelect.vue'

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
const form = reactive({ productId: undefined as any, quantity: '', aQty: '', bQty: '', cQty: '', defectQty: '', deliveryDate: new Date().toISOString().split('T')[0], trackingNo: '', remark: '', attachUrl: '' })

// 四等级数量合计（自动填充总数量）
const totalGrade = computed(() => {
  const sum = (Number(form.aQty) || 0) + (Number(form.bQty) || 0) + (Number(form.cQty) || 0) + (Number(form.defectQty) || 0)
  return sum
})

const warehouseOptions = ref<any[]>([])
async function loadWarehouses() {
  try { const r = await request.get<any,any>('/warehouse/page', { params: { pageSize: 200 } }); warehouseOptions.value = r?.records || [] } catch (e: any) { console.warn('加载仓库失败', e?.message || e) }
}
// 收货仓库选择：纯 Odoo 方案，RemoteSelect 实时查库
const fetchWarehouses = (kw: string) =>
  request.get('/warehouse/page', { params: { warehouseName: kw, pageSize: 500 } })

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
  } catch (e: any) { console.warn('加载交货数据失败', e?.message || e); ElMessage.error('加载交货数据失败：' + (e?.msg || e?.message || '未知错误')) } finally { loading.value = false }
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
  form.productId = undefined; form.quantity = ''; form.aQty = ''; form.bQty = ''; form.cQty = ''; form.defectQty = ''
  form.deliveryDate = new Date().toISOString().split('T')[0]
  form.trackingNo = ''; form.remark = ''; form.attachUrl = ''
  dialogVisible.value = true
  loadWarehouses()
}

function openEdit(row: any) {
  isEdit.value = true; editId.value = row.id
  warehouseId.value = row.warehouseId || undefined; uploadFile.value = null
  Object.assign(form, {
    productId: row.productId, quantity: row.quantity,
    aQty: row.aQty != null ? String(row.aQty) : '',
    bQty: row.bQty != null ? String(row.bQty) : '',
    cQty: row.cQty != null ? String(row.cQty) : '',
    defectQty: row.defectQty != null ? String(row.defectQty) : '',
    deliveryDate: row.deliveryDate,
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
  if (!form.productId) { ElMessage.warning('请选择产品名称'); return }
  if (!form.quantity) { ElMessage.warning('请输入数量'); return }
  if (!warehouseId.value) { ElMessage.warning('请选择收货仓库'); return }
  saving.value = true
  try {
    if (uploadFile.value) {
      const fd = new FormData(); fd.append('file', uploadFile.value)
      const res = await request.post<any,string>('/dev/file/upload', fd)
      form.attachUrl = res as unknown as string
    }
    const body = { productId: form.productId, aQty: form.aQty, bQty: form.bQty, cQty: form.cQty, defectQty: form.defectQty, deliveryDate: form.deliveryDate, trackingNo: form.trackingNo, remark: form.remark, attachUrl: form.attachUrl, orderId, warehouseId: warehouseId.value || null, quantity: totalGrade.value || form.quantity }
    const params = forceDelivery ? { params: { forceDelivery: true } } : {}
    let res: any
    if (isEdit.value && editId.value) {
      res = await request.put(`/outsource/order-delivery/${editId.value}`, body, params)
    } else {
      res = await request.post('/outsource/order-delivery', body, params)
    }
    // 检查是否需要确认缺料（canProceed 不是 true 时都视为缺料）
    if (res && res.canProceed !== true) {
      saving.value = false
      const shortages = (res.shortages || []) as any[]
      // 构建 HTML 格式的缺料表格
      let html = '<div style="margin-bottom:8px">以下物料库存不足，是否确认强制出库？</div>'
      html += '<table style="width:100%;border-collapse:collapse;font-size:13px">'
      html += '<tr style="background:var(--app-bg-hover)"><th style="padding:6px;border:1px solid var(--app-border-light);text-align:left">物料名称</th><th style="padding:6px;border:1px solid var(--app-border-light)">需要</th><th style="padding:6px;border:1px solid var(--app-border-light)">库存</th><th style="padding:6px;border:1px solid var(--app-border-light)">缺口</th></tr>'
      for (const s of shortages) {
        html += `<tr><td style="padding:6px;border:1px solid var(--app-border-light)">${s.materialName || ''}</td>`
        html += `<td style="padding:6px;border:1px solid var(--app-border-light);text-align:center;color:var(--app-color-warning)">${s.needed || 0}</td>`
        html += `<td style="padding:6px;border:1px solid var(--app-border-light);text-align:center;color:var(--app-color-danger)">${s.stock || 0}</td>`
        html += `<td style="padding:6px;border:1px solid var(--app-border-light);text-align:center;color:var(--app-color-danger);font-weight:600">${s.gap || 0}</td></tr>`
      }
      html += '</table>'
      html += '<div style="margin-top:8px;color:var(--app-text-secondary);font-size:var(--app-font-xs)">确认后物料库存将变为负数</div>'
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
    <!-- 汇总卡片 -->
    <el-row :gutter="12" style="margin-bottom:12px">
      <el-col :span="6"><el-card shadow="never"><p style="color:var(--app-text-secondary);font-size:var(--app-font-xs);margin:0">订单总量</p><p style="font-size:20px;font-weight:600;margin:4px 0">{{ summary.totalQuantity || 0 }}</p></el-card></el-col>
      <el-col :span="6"><el-card shadow="never"><p style="color:var(--app-text-secondary);font-size:var(--app-font-xs);margin:0">已交数量</p><p style="font-size:20px;font-weight:600;margin:4px 0;color:var(--app-color-success)">{{ summary.deliveredQuantity || 0 }}</p></el-card></el-col>
      <el-col :span="6"><el-card shadow="never"><p style="color:var(--app-text-secondary);font-size:var(--app-font-xs);margin:0">剩余数量</p><p style="font-size:20px;font-weight:600;margin:4px 0;color:var(--app-color-warning)">{{ summary.remainingQuantity || 0 }}</p></el-card></el-col>
      <el-col :span="6"><el-card shadow="never"><p style="color:var(--app-text-secondary);font-size:var(--app-font-xs);margin:0">交货进度</p><p style="font-size:20px;font-weight:600;margin:4px 0;color:var(--app-color-primary)">{{ progress }}%</p></el-card></el-col>
    </el-row>

    <el-card shadow="never" style="margin-bottom:12px">
      <el-progress :percentage="progress" :stroke-width="16" :text-inside="true" :color="progress>=100?'var(--app-color-success)':'var(--app-color-primary)'" />
    </el-card>

    <!-- 按产品分类 -->
    <el-card shadow="never" style="margin-bottom:12px" v-if="summary.productStats && summary.productStats.length > 1">
      <template #header><span style="font-weight:600">按产品分类统计</span></template>
      <el-table :data="summary.productStats" border size="small">
        <el-table-column prop="productName" label="产品名称" min-width="150" />
        <el-table-column prop="totalQuantity" label="订单数量" width="100" />
        <el-table-column label="已交数量" width="100"><template #default="{row}"><span style="color:var(--app-color-success);font-weight:500">{{ row.deliveredQuantity }}</span></template></el-table-column>
        <el-table-column label="剩余数量" width="100"><template #default="{row}"><span :style="{color: Number(row.remainingQuantity)<=0?'var(--app-color-success)':'var(--app-color-warning)',fontWeight:'500'}">{{ row.remainingQuantity }}</span></template></el-table-column>
        <el-table-column label="进度" width="180"><template #default="{row}"><el-progress :percentage="Number(row.totalQuantity)===0?0:Math.min(100,Math.round(Number(row.deliveredQuantity)/Number(row.totalQuantity)*100))" :stroke-width="12" :color="Number(row.remainingQuantity)<=0?'var(--app-color-success)':'var(--app-color-primary)'" /></template></el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never">
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px">
        <span style="font-weight:600">交货记录</span>
        <el-button type="primary" size="small" @click="openAdd">新增交货</el-button>
      </div>
      <el-table :data="deliveries" border stripe size="small">
        <el-table-column label="交货日期" width="110"><template #default="{row}">{{ $fmtDate(row.deliveryDate) }}</template></el-table-column>
        <el-table-column label="产品名称" min-width="130"><template #default="{row}">{{ products.find((p:any)=>p.id===row.productId)?.productName || '-' }}</template></el-table-column>
        <el-table-column label="收货仓库" width="120">
          <template #default="{row}">
            <span v-if="row.warehouseId">{{ warehouseOptions.find((w:any)=>w.id===row.warehouseId)?.warehouseName || row.warehouseId }}</span>
            <span v-else style="color:var(--app-text-placeholder)">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="quantity" label="总数量" width="90" />
        <el-table-column label="等级分布" min-width="160">
          <template #default="{row}">
            <span v-if="row.aQty || row.bQty || row.cQty || row.defectQty">
              <span style="color:var(--app-color-success)">A{{ row.aQty||0 }}</span> /
              <span style="color:var(--app-color-primary)">B{{ row.bQty||0 }}</span> /
              <span style="color:var(--app-color-warning)">C{{ row.cQty||0 }}</span> /
              <span style="color:var(--app-color-danger)">不良{{ row.defectQty||0 }}</span>
            </span>
            <span v-else style="color:var(--app-text-secondary)">{{ row.quantity }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="trackingNo" label="物流单号" width="140" />
        <el-table-column label="附件" width="80" align="center">
          <template #default="{row}">
            <el-button v-if="row.attachUrl" type="primary" link size="small" @click="openAttach(row.attachUrl)">查看</el-button>
            <span v-else style="color:var(--app-text-placeholder)">—</span>
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
          <el-select v-model="form.productId" filterable style="width:100%" placeholder="选择订单产品">
            <el-option v-for="p in products" :key="p.id" :label="p.productName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="等级数量">
          <div style="width:100%">
            <el-row :gutter="8">
              <el-col :span="12"><el-input v-model="form.aQty" type="number" placeholder="A规数量" /></el-col>
              <el-col :span="12"><el-input v-model="form.bQty" type="number" placeholder="B规数量" /></el-col>
            </el-row>
            <el-row :gutter="8" style="margin-top:6px">
              <el-col :span="12"><el-input v-model="form.cQty" type="number" placeholder="C规数量" /></el-col>
              <el-col :span="12"><el-input v-model="form.defectQty" type="number" placeholder="不良数量" /></el-col>
            </el-row>
            <div style="margin-top:6px;color:var(--app-text-secondary);font-size:var(--app-font-xs)">合计：<b style="color:var(--app-text-primary)">{{ totalGrade }}</b>（自动作为总数量）</div>
          </div>
        </el-form-item>
        <el-form-item label="收货仓库" required>
          <RemoteSelect v-model="warehouseId" :fetch="fetchWarehouses" :label-key="(row:any)=>`${row.warehouseName} (${row.code})`" placeholder="选择入库仓库" />
        </el-form-item>
        <el-form-item label="交货日期"><el-input v-model="form.deliveryDate" type="date" /></el-form-item>
        <el-form-item label="物流单号"><el-input v-model="form.trackingNo" placeholder="选填" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" placeholder="选填" /></el-form-item>

        <el-form-item label="交货图片">
          <div class="drop-zone" @dragover="handleDragOver" @drop="handleDrop" :style="{ borderColor: uploadFile?'var(--app-color-success)':'var(--app-border-color)', background: uploadFile?'#f0f9eb':'#fafafa' }">
            <template v-if="uploadFile"><div style="display:flex;align-items:center;justify-content:center;gap:8px;flex-wrap:wrap"><span style="color:var(--app-color-success);font-weight:600">📎 {{ uploadFile.name }}</span><el-button type="danger" size="small" @click.stop="handleRemoveUploadFile">移除</el-button></div></template>
            <template v-else-if="form.attachUrl"><div style="display:flex;align-items:center;justify-content:center;gap:4px;flex-wrap:wrap"><span style="color:var(--app-color-primary)">📎 已有图片</span><el-button type="primary" size="small" @click.stop="openAttach(form.attachUrl)">查看</el-button><span style="color:var(--app-text-secondary);font-size:var(--app-font-xs)">可拖拽新文件替换</span></div></template>
            <template v-else><p style="color:var(--app-text-secondary);margin:0">拖拽图片到此处，或点击选择</p></template>
            <input v-if="!form.attachUrl && !uploadFile" type="file" @change="handleFileSelect" style="position:absolute;inset:0;opacity:0;cursor:pointer" />
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="() => handleSubmit()">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.delivery-page { padding: 16px; }

.drop-zone { position:relative; border:2px dashed var(--app-border-color); border-radius:8px; padding:16px; text-align:center; transition:all .3s; cursor:pointer; margin-top:4px }
.drop-zone:hover { border-color:var(--app-color-primary); background:#ecf5ff }
</style>
