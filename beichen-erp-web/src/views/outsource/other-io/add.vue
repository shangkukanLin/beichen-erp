<script setup lang="ts">
// 2026-09-20（F7-159）：显式声明组件名（便于 DevTools 辨认与将来按名 exclude）
defineOptions({ name: 'OutsourceOtherIoAdd' })
import { localDate } from '@/utils/date'
import { ref, onMounted, onUnmounted, computed, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { IoType, IoTypeLabel, WarehouseCategory, OUTSOURCE_OTHER_IO_DIRTY_KEY } from '@/api/enums'
import PageShell from '@/components/PageShell.vue'
import { useUnsavedGuard } from '@/composables/usePageBack'
import { useTabStore } from '@/stores/tabs'
import { invalidate } from '@/utils/dataFreshness'

const route = useRoute(); const router = useRouter()
const editId = Number(route.query.id) || 0
const warehouses = ref<any[]>([])
const materialOptions = ref<any[]>([])
const materialTypes = ref<any[]>([])
const saving = ref(false)

async function loadWarehouses() {
  try { const r = await request.get<any, any>('/warehouse/page', { params: { pageSize: 500 } }); warehouses.value = (r?.records || []).map((w: any) => ({ ...w, _type: w.warehouseCategory === WarehouseCategory.INVENTORY ? '我方仓' : '委外仓' })) } catch { warehouses.value = [] }
}
async function loadMaterials() {
  try { const r = await request.get<any, any>('/outsource/material/page', { params: { pageSize: 500 } }); materialOptions.value = r?.records || [] } catch { materialOptions.value = [] }
}
async function loadMaterialTypes() {
  try { const r = await request.get<any, any>('/dev/material-type/enabled'); materialTypes.value = r || [] } catch { materialTypes.value = [] }
}
const form = reactive({ warehouseId: undefined as any, ioType: IoType.IN, ioDate: localDate(), remark: '' })
const items = ref<any[]>([{ materialId: undefined, materialName: '', materialTypeId: undefined, unit: '', unit_price: '', quantity: undefined, remark: '' }])
const tabStore = useTabStore()
/**
 * 未保存拦截（2026-09-23 统一模板）
 * ⚠️ 必须写在 form / items 等状态**之后**（watch 注册时立即求值，放前面会 TDZ 静默失效）。
 */
const { takeBaseline, markClean } = useUnsavedGuard(() => ({ form, items: items.value }))

const uniqueTypes = computed(() => [...new Set(materialOptions.value.map((m: any) => m.materialTypeId).filter(Boolean))] as number[])
function materialsByType(type: number) { return materialOptions.value.filter((m: any) => m.materialTypeId === type) }
function typeName(id: number | undefined) { if (id == null) return '-'; const t = materialTypes.value.find((v: any) => v.id === id); return t ? t.typeName : (id as any) }

function onTypeChange(idx: number) { items.value[idx].materialId = undefined; items.value[idx].materialName = ''; items.value[idx].unit = ''; items.value[idx].unit_price = '' }
function onMatSelect(idx: number, matId: number) {
  const m = materialOptions.value.find((v:any)=>v.id===matId)
  if (m) { items.value[idx].materialName=m.materialName; items.value[idx].materialTypeId=m.materialTypeId; items.value[idx].unit=m.unit }
  // 自动查询加权平均单价（需先选择仓库）
  if (m && form.warehouseId) {
    const wh = warehouses.value.find((w:any)=>w.id===form.warehouseId)
    // 委外仓用加工厂ID查加权单价，我方仓不自动查
    if (wh?._type === '委外仓' && wh?.factoryId) {
      // 期 3（2026-09-19 读隔离）：加权单价改走本页前缀（原读 /outsource/delivery/material-weighted-price 需 outsource:delivery）
      request.get<any,any>('/outsource/other-io/material-weighted-price', { params: { factoryId: wh.factoryId, materialId: m.id } }).then((r: any) => {
        if (r) items.value[idx].unit_price = r
      }).catch(() => {})
    }
  }
}
function addItem() { items.value.push({ materialId: undefined, materialName: '', materialTypeId: undefined, unit: '', unit_price: '', quantity: undefined, remark: '' }) }
function removeItem(i: number) { items.value.splice(i,1) }

async function loadDetail() {
  if (!editId) return
  try {
    const io = await request.get<any,any>(`/outsource/other-io/${editId}`)
    form.warehouseId = io.warehouseId; form.ioType = io.ioType
    form.ioDate = io.ioDate; form.remark = io.remark||''
    const its = await request.get<any,any>(`/outsource/other-io/${editId}/items`)
    if (Array.isArray(its)) items.value = its.map((i:any)=>({ materialId: i.materialId, materialName: i.materialName, materialTypeId: i.materialTypeId, unit: i.unit, unit_price: i.unitPrice||'', quantity: i.quantity, remark: i.remark||'' }))
  } catch (e: any) { ElMessage.error(e?.message||'加载失败') }
}

async function handleSubmit() {
  if (!form.warehouseId) { ElMessage.warning('请选择仓库'); return }
  const validItems = items.value.filter((i:any)=>i.quantity && Number(i.quantity)>0)
  if (validItems.length===0) { ElMessage.warning('请添加物料明细'); return }
  saving.value = true
  try {
    const body: any = { ...form, items: validItems }
    if (editId) {
      await request.put(`/outsource/other-io/${editId}`, body); ElMessage.success('已更新'); invalidate('outsourceOtherIo')
    } else {
      await request.post('/outsource/other-io', body); ElMessage.success('已新增'); invalidate('outsourceOtherIo')
      Object.assign(form, { warehouseId: undefined, ioType: IoType.IN, ioDate: localDate(), remark: '' })
      items.value = [{ materialId: undefined, materialName: '', materialTypeId: undefined, unit: '', unit_price: '', quantity: undefined, remark: '' }]
    }
    // 提交成功 ⇒ 先清脏标记（否则离开会被未保存确认拦住），再关掉本页签并回列表
    markClean()
    tabStore.closeTabAndBack(route.path)
    router.push('/outsource/other-io')
  } catch (e: any) { ElMessage.error(e?.message||'保存失败') } finally { saving.value = false }
}

// 顶栏"刷新数据"：重新加载仓库/物料/类型下拉
async function handleRefreshData() { await Promise.all([loadWarehouses(), loadMaterials(), loadMaterialTypes()]) }
onMounted(async ()=>{
  loadWarehouses(); loadMaterials(); loadMaterialTypes()
  await loadDetail()      // 编辑态要等回填完成再建基线，否则会把回填误判成用户修改
  window.addEventListener('refresh:dropdown-data', handleRefreshData)
  takeBaseline()
})
onUnmounted(() => window.removeEventListener('refresh:dropdown-data', handleRefreshData))
</script>

<template>
  <!-- 统一骨架（2026-09-23 全站定稿口径）：页头左端「← 返回」→ 标题(取 meta) → 右端操作（保存） -->
  <PageShell back-fallback="/outsource/other-io">
    <template #actions>
      <el-button type="primary" :loading="saving" @click="handleSubmit">保存</el-button>
    </template>

    <el-card shadow="never">
      <el-form :model="form" label-width="var(--app-label-width)">
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item required label="仓库"><el-select v-model="form.warehouseId" filterable style="width:100%"><el-option v-for="w in warehouses" :key="w.id+'@'+w._type" :label="`${w.warehouseName}（${w._type}）`" :value="w.id"/>
                <template #footer><div style="padding:6px 12px;cursor:pointer;text-align:center;font-size:12px;color:var(--app-color-primary,#409eff);border-top:1px solid var(--app-color-border,#ebeef5)" @click="$router.push('/outsource/warehouse')">+ 新增</div></template>
              </el-select></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="类型"><el-select v-model="form.ioType" style="width:100%"><el-option :label="IoTypeLabel[IoType.IN]" :value="IoType.IN"/><el-option :label="IoTypeLabel[IoType.OUT]" :value="IoType.OUT"/></el-select></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="日期"><el-input v-model="form.ioDate" type="date"/></el-form-item></el-col>
        </el-row>
        <el-form-item label="备注"><el-input v-model="form.remark" placeholder="备注"/></el-form-item>
      </el-form>
    </el-card>
    <el-card shadow="never">
      <template #header><span style="font-weight:600">物料明细</span></template>
      <el-button type="primary" size="small" @click="addItem" style="margin-bottom:8px">+ 添加物料</el-button>
      <el-table :data="items" border size="small">
        <el-table-column label="物料类型" width="110"><template #default="{row,$index}"><el-select v-model="row.materialTypeId" filterable style="width:100%" clearable @change="onTypeChange($index)"><el-option v-for="t in uniqueTypes" :key="t" :label="typeName(t)" :value="t"/>
                <template #footer><div style="padding:6px 12px;cursor:pointer;text-align:center;font-size:12px;color:var(--app-color-primary,#409eff);border-top:1px solid var(--app-color-border,#ebeef5)" @click="$router.push('/dev/material-type')">+ 新增</div></template>
              </el-select></template></el-table-column>
        <el-table-column label="物料名称" min-width="140"><template #default="{row,$index}"><el-select v-model="row.materialId" filterable style="width:100%" :disabled="!row.materialTypeId" @change="(v:any)=>onMatSelect($index,v)"><el-option v-for="m in materialsByType(row.materialTypeId)" :key="m.id" :label="$mLabel(m)" :value="m.id"/></el-select></template></el-table-column>
        <el-table-column label="单位" width="70"><template #default="{row}">{{ row.unit }}</template></el-table-column>
        <el-table-column label="单价" width="100"><template #default="{row}"><el-input v-model="row.unit_price" size="small" placeholder="单价"/></template></el-table-column>
        <el-table-column label="数量" width="110"><template #default="{row}"><el-input-number v-model="row.quantity" size="small" :controls="false" :precision="0" :step="1" style="width:100%" /></template></el-table-column>
        <el-table-column label="操作" width="60" align="center"><template #default="{$index}"><el-button type="danger" link @click="removeItem($index)">删除</el-button></template></el-table-column>
      </el-table>
    </el-card>
  </PageShell>
</template>
