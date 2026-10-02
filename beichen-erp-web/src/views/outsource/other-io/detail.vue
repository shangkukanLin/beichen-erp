<script setup lang="ts">
import { ref, computed, onMounted, onActivated } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { IoType, IoTypeLabel, WarehouseCategory, OUTSOURCE_OTHER_IO_DIRTY_KEY } from '@/api/enums'
import { DocStatus, DocStatusLabel, DocStatusTag } from '@/api/common'
import PageShell from '@/components/PageShell.vue'
import { useUnsavedGuard } from '@/composables/usePageBack'
import { invalidate } from '@/utils/dataFreshness'

const route = useRoute(); const router = useRouter()
const id = Number(route.params.id) || 0
const loading = ref(false)
const saving = ref(false)
const editing = ref(false)
const detail = ref<any>({})
const items = ref<any[]>([])
const warehouses = ref<any[]>([])
const materialOptions = ref<any[]>([])
const materialTypes = ref<any[]>([])

async function loadWarehouses() {
  try { const r = await request.get<any, any>('/warehouse/page', { params: { pageSize: 500 } }); warehouses.value = (r?.records || []).map((w: any) => ({ ...w, _type: w.warehouseCategory === WarehouseCategory.INVENTORY ? '我方仓' : '委外仓' })) } catch { warehouses.value = [] }
}
async function loadMaterials() {
  try { const r = await request.get<any, any>('/outsource/material/page', { params: { pageSize: 500 } }); materialOptions.value = r?.records || [] } catch { materialOptions.value = [] }
}
async function loadMaterialTypes() {
  try { const r = await request.get<any, any>('/dev/material-type/enabled'); materialTypes.value = r || [] } catch { materialTypes.value = [] }
}

// 编辑态表单（主单字段）
const form = ref<any>({ warehouseId: undefined, ioType: IoType.IN, ioDate: '', remark: '' })
/**
 * 未保存拦截（2026-09-23 统一模板）：本页草稿可切到编辑态就地改表头+明细 ⇒ 属"能改数据"，接守卫。
 * 注意：快照**不含 editing** —— 只点「编辑」不改内容不算脏（返回时不弹确认），符合直觉。
 * ⚠️ 必须写在 form / items 等状态**之后**（watch 注册时立即求值，放前面会 TDZ 静默失效）。
 */
const { takeBaseline } = useUnsavedGuard(() => ({ form: form.value, items: items.value }))

function getWhName(wid: number) {
  return warehouses.value.find((w: any) => w.id === wid)?.warehouseName || '-'
}
function getMatName(mid: number | undefined) {
  if (mid == null) return '-'
  return materialOptions.value.find((m: any) => m.id === mid)?.materialName || '-'
}
function getTypeName(id: number | undefined) {
  if (id == null) return '-'
  const t = materialTypes.value.find((v: any) => v.id === id)
  return t ? t.typeName : '-'
}
function statusLabel(s: string) {
  return DocStatusLabel[s] || s || '-'
}
function statusTag(s: string): any {
  return DocStatusTag[s] || 'warning'
}

// 编辑态物料类型/物料联动（复用 add.vue 交互）
const uniqueTypes = computed(() => [...new Set(materialOptions.value.map((m: any) => m.materialTypeId).filter(Boolean))] as number[])
function materialsByType(type: number) { return materialOptions.value.filter((m: any) => m.materialTypeId === type) }
function typeName(id: number | undefined) { return getTypeName(id) }
function onTypeChange(idx: number) {
  items.value[idx].materialId = undefined
  items.value[idx].unit = ''
  items.value[idx].unit_price = ''
}
function onMatSelect(idx: number, matId: number) {
  const m = materialOptions.value.find((v: any) => v.id === matId)
  if (m) { items.value[idx].materialTypeId = m.materialTypeId; items.value[idx].unit = m.unit }
  // 委外仓选物料自动查加权单价
  if (m && form.value.warehouseId) {
    const wh = warehouses.value.find((w: any) => w.id === form.value.warehouseId)
    if (wh?._type === '委外仓' && wh?.factoryId) {
      // 期 3（2026-09-19 读隔离）：加权单价改走本页前缀（原读 /outsource/delivery/material-weighted-price 需 outsource:delivery）
      request.get<any, any>('/outsource/other-io/material-weighted-price', { params: { factoryId: wh.factoryId, materialId: m.id } }).then((r: any) => {
        if (r) items.value[idx].unit_price = r
      }).catch(() => {})
    }
  }
}
function addItem() {
  items.value.push({ materialId: undefined, materialTypeId: undefined, unit: '', unit_price: '', quantity: undefined, remark: '' })
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
      ? its.map((i: any) => ({ materialId: i.materialId, materialTypeId: i.materialTypeId, unit: i.unit, unit_price: i.unitPrice ?? '', quantity: i.quantity, remark: i.remark || '' }))
      : []
  } finally { loading.value = false }
  // 数据加载完成 ⇒ 重建"未保存"基线（loadDetail 也被 cancelEdit / 保存成功后复用 ⇒ 自动重置，不误报）
  takeBaseline()
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
    ElMessage.success('已更新'); invalidate('outsourceOtherIo')
    editing.value = false
    await loadDetail()
  } catch (e: any) { ElMessage.error(e?.message || '保存失败') } finally { saving.value = false }
}

// 字典类只需加载一次
onMounted(() => { loadWarehouses(); loadMaterials(); loadMaterialTypes() })
// 单据数据每次进入都重新拉取：keep-alive 缓存下再次进入会复用组件、onMounted 不再触发
onActivated(() => { loadDetail() })
</script>

<template>
  <!-- 统一骨架（2026-09-23 全站定稿口径）：页头左端「← 返回」→ 标题(取 meta) → 右端操作（编辑/保存） -->
  <PageShell :loading="loading" back-fallback="/outsource/other-io">
    <template #actions>
      <template v-if="detail.status===DocStatus.DRAFT && !editing">
        <el-button type="primary" @click="startEdit">编辑</el-button>
      </template>
      <template v-else-if="editing">
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
        <el-button @click="cancelEdit">取消编辑</el-button>
      </template>
    </template>

    <!-- 信息卡片：只读展示 -->
    <el-card shadow="never" v-if="!editing">
      <el-descriptions :column="3" border>
        <el-descriptions-item label="单号">{{ detail.code || '-' }}</el-descriptions-item>
        <el-descriptions-item label="仓库">{{ getWhName(detail.warehouseId) }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ IoTypeLabel[detail.ioType] || '-' }}</el-descriptions-item>
        <el-descriptions-item label="日期">{{ detail.ioDate ? $fmtDate(detail.ioDate) : '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTag(detail.status)" size="small">{{ statusLabel(detail.status) }}</el-tag>
        </el-descriptions-item>
        <!-- 制单人 / 审核人（2026-09-23 用户口径：单据详情显示这两项；历史单据无记录显示 —） -->
        <el-descriptions-item label="制单人">{{ detail.createByName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="审核人">{{ detail.auditorName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ detail.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 信息卡片：草稿编辑态 -->
    <el-card shadow="never" v-else>
      <el-form :model="form" label-width="80px">
        <el-form-item label="单号"><span>{{ detail.code || '-' }}</span></el-form-item>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item required label="仓库">
              <el-select v-model="form.warehouseId" filterable style="width:100%">
                <el-option v-for="w in warehouses" :key="w.id + '@' + w._type" :label="`${w.warehouseName}（${w._type}）`" :value="w.id"/>
              
                <template #footer><div style="padding:6px 12px;cursor:pointer;text-align:center;font-size:12px;color:var(--app-color-primary,#409eff);border-top:1px solid var(--app-color-border,#ebeef5)" @click="$router.push('/outsource/warehouse')">+ 新增</div></template>
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
        <el-table-column label="物料类型" width="120">
          <template #default="{row}">{{ getTypeName(row.materialTypeId) }}</template>
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
            <el-select v-model="row.materialTypeId" filterable style="width:100%" clearable @change="onTypeChange($index)">
              <el-option v-for="t in uniqueTypes" :key="t" :label="typeName(t)" :value="t"/>
            
                <template #footer><div style="padding:6px 12px;cursor:pointer;text-align:center;font-size:12px;color:var(--app-color-primary,#409eff);border-top:1px solid var(--app-color-border,#ebeef5)" @click="$router.push('/dev/material-type')">+ 新增</div></template>
              </el-select>
          </template>
        </el-table-column>
        <el-table-column label="物料名称" min-width="180">
          <template #default="{row,$index}">
            <el-select v-model="row.materialId" filterable style="width:100%" :disabled="!row.materialTypeId" @change="(v:any)=>onMatSelect($index,v)">
              <el-option v-for="m in materialsByType(row.materialTypeId)" :key="m.id" :label="m.materialName" :value="m.id"/>
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
          <template #default="{row}"><el-input-number v-model="row.quantity" size="small" :controls="false" :precision="0" :step="1" style="width:100%" /></template>
        </el-table-column>
        <el-table-column label="操作" width="70" align="center">
          <template #default="{$index}"><el-button type="danger" link @click="removeItem($index)">删除</el-button></template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 操作按钮（编辑 / 保存 / 取消编辑）已统一上移到页头右侧（PageShell #actions）；
         原「返回列表」按钮已删除 —— 返回统一由骨架提供。原「取消」= 放弃本次编辑，文案改为「取消编辑」以区分于返回。 -->
  </PageShell>
</template>
