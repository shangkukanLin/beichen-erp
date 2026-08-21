<script setup lang="ts">
import { reactive, ref, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { useTabStore } from '@/stores/tabs'
import RemoteSelect from '@/components/RemoteSelect.vue'

const router = useRouter()
const tabStore = useTabStore()

const form = reactive({ supplierId: undefined as any, fromWarehouseId: undefined as any, returnDate: new Date().toISOString().slice(0, 10), remark: '' })
const warehouseOptions = ref<any[]>([])
const stockList = ref<any[]>([])
const loading = ref(false)
const submitting = ref(false)

// Odoo 风格：退回对象（物料商）实时查库
const fetchSuppliers = (kw: string) => request.get('/supplier/page', { params: { pageSize: 500, name: kw } })

async function loadOptions() {
  try { const r = await request.get<any, any>('/outsource/material-return/warehouse-options'); warehouseOptions.value = r || [] } catch {}
}

async function onWarehouseChange() {
  stockList.value = []
  if (!form.fromWarehouseId) return
  loading.value = true
  try {
    const r = await request.get<any, any>('/outsource/material-return/material-stock', { params: { warehouseId: form.fromWarehouseId } })
    stockList.value = (r || []).map((m: any) => ({ ...m, returnQuantity: undefined as any, unitPrice: undefined as any }))
  } catch (e: any) { ElMessage.error(e?.message || '加载库存失败') } finally { loading.value = false }
}

async function handleSubmit() {
  if (!form.supplierId) { ElMessage.warning('请选择退回对象（物料商）'); return }
  if (!form.fromWarehouseId) { ElMessage.warning('请选择出库源仓'); return }
  const items = stockList.value
    .filter((m: any) => Number(m.returnQuantity) > 0)
    .map((m: any) => ({
      materialId: m.materialId, bomTypeId: m.bomTypeId, unit: m.unit,
      quantity: Number(m.returnQuantity), unitPrice: m.unitPrice || '', remark: ''
    }))
  if (items.length === 0) { ElMessage.warning('请输入退货数量'); return }
  submitting.value = true
  try {
    await request.post('/outsource/material-return', {
      supplierId: form.supplierId, fromWarehouseId: form.fromWarehouseId,
      returnDate: form.returnDate, remark: form.remark, returnType: 'MATERIAL', items
    })
    ElMessage.success('退货单草稿已保存，请在列表中审核生效')
    tabStore.removeTab(window.location.hash.replace('#', ''))
    router.replace('/outsource/material-return')
  } catch (e: any) { ElMessage.error(e?.message || '保存失败') } finally { submitting.value = false }
}

onMounted(loadOptions)
onActivated(loadOptions)
</script>

<template>
  <div style="display:flex;flex-direction:column;gap:12px">
    <el-card shadow="never">
      <template #header><span style="font-weight:600">退货信息</span></template>
      <el-form :model="form" label-width="100px" size="small">
        <el-row :gutter="16">
          <el-col :span="8"><el-form-item label="退回对象"><RemoteSelect v-model="form.supplierId" :fetch="fetchSuppliers" placeholder="选择物料商" style="width:100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="出库源仓"><el-select v-model="form.fromWarehouseId" filterable clearable style="width:100%" placeholder="选择物料所在仓库" @change="onWarehouseChange"><el-option v-for="w in warehouseOptions" :key="w.id" :label="w.warehouseName" :value="w.id" /></el-select></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="退货日期"><el-input v-model="form.returnDate" type="date" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item></el-col>
        </el-row>
      </el-form>
    </el-card>

    <el-card shadow="never" v-if="form.fromWarehouseId" v-loading="loading">
      <template #header><span style="font-weight:600">可退物料（源仓良品库存）</span></template>
      <el-table :data="stockList" border size="small">
        <el-table-column prop="materialName" label="物料名称" min-width="140" />
        <el-table-column prop="bomTypeName" label="BOM类型" width="100" />
        <el-table-column prop="unit" label="单位" width="70" />
        <el-table-column label="可退数量" width="100" align="right">
          <template #default="{row}">{{ Number(row.quantity || 0) }}</template>
        </el-table-column>
        <el-table-column label="退货数量" width="120">
          <template #default="{row}"><el-input v-model="row.returnQuantity" size="small" type="number" placeholder="数量" /></template>
        </el-table-column>
        <el-table-column label="单价（留空自动FIFO）" width="150">
          <template #default="{row}"><el-input v-model="row.unitPrice" size="small" type="number" placeholder="自动" /></template>
        </el-table-column>
      </el-table>
      <div style="margin-top:12px;text-align:right"><el-button type="primary" :loading="submitting" @click="handleSubmit">保存草稿</el-button></div>
    </el-card>
  </div>
</template>
