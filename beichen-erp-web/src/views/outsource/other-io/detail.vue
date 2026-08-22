<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { IoType, IoTypeLabel, WarehouseCategory } from '@/api/enums'
import { DocStatus, DocStatusLabel, DocStatusTag } from '@/api/common'

const route = useRoute(); const router = useRouter()
const id = Number(route.params.id) || 0
const loading = ref(false)
const saving = ref(false)
const editing = ref(false)
const detail = ref<any>({})
const items = ref<any[]>([])
const warehouses = ref<any[]>([])
const materialOptions = ref<any[]>([])
const bomTypes = ref<any[]>([])

async function loadWarehouses() {
  try { const r = await request.get<any, any>('/warehouse/page', { params: { pageSize: 500 } }); warehouses.value = (r?.records || []).map((w: any) => ({ ...w, _type: w.warehouseCategory === WarehouseCategory.INVENTORY ? '我方仓' : '委外仓' })) } catch { warehouses.value = [] }
}
async function loadMaterials() {
  try { const r = await request.get<any, any>('/outsource/material/page', { params: { pageSize: 500 } }); materialOptions.value = r?.records || [] } catch { materialOptions.value = [] }
}
async function loadBomTypes() {
  try { const r = await request.get<any, any>('/dev/bom-type/enabled'); bomTypes.value = r || [] } catch { bomTypes.value = [] }
}

// 编辑态表单（主单字段）
const form = ref<any>({ warehouseId: undefined, ioType: IoType.IN, ioDate: '', remark: '' })

function getWhName(wid: number) {
  return warehouses.value.find((w: any) => w.id === wid)?.warehouseName || '-'
}
function getMatName(mid: number | undefined) {
  if (mid == null) return '-'
  return materialOptions.value.find((m: any) => m.id === mid)?.materialName || '-'
}
function getTypeName(id: number | undefined) {
  if (id == null) return '-'
  const t = bomTypes.value.find((v: any) => v.id === id)
  return t ? t.typeName : '-'
}
function statusLabel(s: string) {
  return DocStatusLabel[s] || s || '-'
}
function statusTag(s: string): any {
  return DocStatusTag[s] || 'warning'
}

// 编辑态物料类型/物料联动（复用 add.vue 交互）
const uniqueTypes = computed(() => [...new Set(materialOptions.value.map((m: any) => m.bomTypeId).filter(Boolean))] as number[])
function materialsByType(type: number) { return materialOptions.value.filter((m: any) => m.bomTypeId === type) }
function typeName(id: number | undefined) { return getTypeName(id) }
function onTypeChange(idx: number) {
  items.value[idx].materialId = undefined
  items.value[idx].unit = ''
  items.value[idx].unit_price = ''
}
function onMatSelect(idx: number, matId: number) {
  const m = materialOptions.value.find((v: any) => v.id === matId)
  if (m) { items.value[idx].bomTypeId = m.bomTypeId; items.value[idx].unit = m.unit }
  // 委外仓选物料自动查加权单价
  if (m && form.value.warehouseId) {
    const wh = warehouses.value.find((w: any) => w.id === form.value.warehouseId)
    if (wh?._type === '委外仓' && wh?.factoryId) {
      request.get<any, any>('/outsource/delivery/material-weighted-price', { params: { factoryId: wh.factoryId, materialId: m.id } }).then((r: any) => {
        if (r) items.value[idx].unit_price = r
      }).catch(() => {})
    }
  }
}
function addItem() {
  items.value.push({ materialId: undefined, bomTypeId: undefined, unit: '', unit_price: '', quantity: undefined, remark: '' })
}
function removeItem(i: number) { items.value.splice(i, 1) }

async function loadDetail() {
  loading.value = true
  try {
    const io = await request.get<any, any>(`/outsource/other-io/${id}`)
    detail.value = io || {}
    form.value = { warehouseId: io.warehouseId, ioType: io.ioType, ioDate: io.ioDate || '', remark: io.remark || '' }
    const its = await request.get<any, any>(`/outsource/other-io/${id}/items`)
    items.value = Array.isArray(its)
      ? its.map((i: any) => ({ materialId: i.materialId, bomTypeId: i.bomTypeId, unit: i.unit, unit_price: i.unitPrice ?? '', quantity: i.quantity, remark: i.remark || '' }))
      : []
  } finally { loading.value = false }
}

function startEdit() { editing.value = true }
function cancelEdit() {
  editing.value = false
  loadDetail() // 恢复原始数据
}

async function handleSave() {
  if (!form.value.warehouseId) { ElMessage.warning('请选择仓库'); return }
  const validItems = items.value.filter((i: any) => i.quantity && Number(i.quantity) > 0)
  if (validItems.length === 0) { ElMessage.warning('请添加物料明细'); return }
  saving.value = true
  try {
    const body: any = { ...form.value, items: validItems }
    await request.put(`/outsource/other-io/${id}`, body)
    ElMessage.success('已更新')
    editing.value = false
    await loadDetail()
  } catch (e: any) { ElMessage.error(e?.message || '保存失败') } finally { saving.value = false }
}

onMounted(() => { loadWarehouses(); loadMaterials(); loadBomTypes(); loadDetail() })
</script>

<template>
  <div style="display:flex;flex-direction:column;gap:12px">
    <!-- 信息卡片：只读展示 -->
    <el-card shadow="never" v-loading="loading" v-if="!editing">
      <el-descriptions :column="3" border>
        <el-descriptions-item label="单号">{{ detail.code || '-' }}</el-descriptions-item>
        <el-descriptions-item label="仓库">{{ getWhName(detail.warehouseId) }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ IoTypeLabel[detail.ioType] || '-' }}</el-descriptions-item>
        <el-descriptions-item label="日期">{{ detail.ioDate ? $fmtDate(detail.ioDate) : '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTag(detail.status)" size="small">{{ statusLabel(detail.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="备注">{{ detail.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 信息卡片：草稿编辑态 -->
    <el-card shadow="never" v-else>
      <el-form :model="form" label-width="80px">
        <el-form-item label="单号"><span>{{ detail.code || '-' }}</span></el-form-item>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="仓库">
              <el-select v-model="form.warehouseId" filterable style="width:100%">
                <el-option v-for="w in warehouses" :key="w.id + '@' + w._type" :label="`${w.warehouseName}（${w._type}）`" :value="w.id"/>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="类型">
              <el-select v-model="form.ioType" style="width:100%">
                <el-option :label="IoTypeLabel[IoType.IN]" :value="IoType.IN"/>
                <el-option :label="IoTypeLabel[IoType.OUT]" :value="IoType.OUT"/>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="日期"><el-input v-model="form.ioDate" type="date"/></el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注"><el-input v-model="form.remark" placeholder="备注"/></el-form-item>
      </el-form>
    </el-card>

    <!-- 物料明细卡片 -->
    <el-card shadow="never">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span style="font-weight:600">物料明细</span>
          <el-button v-if="editing" type="primary" size="small" @click="addItem">+ 添加物料</el-button>
        </div>
      </template>

      <!-- 只读明细 -->
      <el-table v-if="!editing" :data="items" border size="small">
        <el-table-column type="index" label="#" width="50" align="center"/>
        <el-table-column label="物料类型" width="120">
          <template #default="{row}">{{ getTypeName(row.bomTypeId) }}</template>
        </el-table-column>
        <el-table-column label="物料名称" min-width="160" show-overflow-tooltip>
          <template #default="{row}">{{ getMatName(row.materialId) }}</template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" width="80"/>
        <el-table-column prop="unit_price" label="单价" width="110" align="right">
          <template #default="{row}">{{ row.unit_price ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="quantity" label="数量" width="110" align="right"/>
        <el-table-column label="金额" width="120" align="right">
          <template #default="{row}">{{ row.unit_price != null && row.quantity != null ? (Number(row.unit_price) * Number(row.quantity)).toFixed(2) : '-' }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip>
          <template #default="{row}">{{ row.remark || '-' }}</template>
        </el-table-column>
      </el-table>

      <!-- 可编辑明细 -->
      <el-table v-else :data="items" border size="small">
        <el-table-column label="物料类型" width="150">
          <template #default="{row,$index}">
            <el-select v-model="row.bomTypeId" filterable style="width:100%" clearable @change="onTypeChange($index)">
              <el-option v-for="t in uniqueTypes" :key="t" :label="typeName(t)" :value="t"/>
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="物料名称" min-width="180">
          <template #default="{row,$index}">
            <el-select v-model="row.materialId" filterable style="width:100%" :disabled="!row.bomTypeId" @change="(v:any)=>onMatSelect($index,v)">
              <el-option v-for="m in materialsByType(row.bomTypeId)" :key="m.id" :label="m.materialName" :value="m.id"/>
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="单位" width="70">
          <template #default="{row}">{{ row.unit }}</template>
        </el-table-column>
        <el-table-column label="单价" width="110">
          <template #default="{row}"><el-input v-model="row.unit_price" size="small" placeholder="单价"/></template>
        </el-table-column>
        <el-table-column label="数量" width="110">
          <template #default="{row}"><el-input v-model="row.quantity" size="small" type="number"/></template>
        </el-table-column>
        <el-table-column label="操作" width="70" align="center">
          <template #default="{$index}"><el-button type="danger" link @click="removeItem($index)">删除</el-button></template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 底部按钮 -->
    <div style="display:flex;gap:12px;justify-content:center">
      <template v-if="detail.status===DocStatus.DRAFT && !editing">
        <el-button type="primary" @click="startEdit">编辑</el-button>
      </template>
      <template v-else-if="editing">
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
        <el-button @click="cancelEdit">取消</el-button>
      </template>
      <el-button @click="router.push('/outsource/other-io')">返回列表</el-button>
    </div>
  </div>
</template>
