<script setup lang="ts">
/**
 * 新增无单加工退货（2026-09-27 用户口径：**弹窗改独立页面**）。
 *
 * 业务口径（P1-1，2026-09-25 用户确认，未变）：不关联加工单，审核后把退回成品以
 * 「成品（加工退货）」形态转入该加工厂委外仓；**不拆料还料、不冲应付**；
 * 工厂修好送回时在「加工返回单」登记（核销在厂成品 + 按实际用料扣料 + 赔料应收）。
 *
 * 2026-09-27 新增（同批）：本页把 **BOM 快照**落到这条退货记录上 —— 返回时（加工返回单）
 * 据此限定「实际用料」的可选范围。解析顺序：① 人工在本页选定 → ② 默认取
 * 「该产品在该加工厂**最近一次被加工单用过的**快照」（用户确认的口径）→ ③ 都没有则留空
 * （返回时按现场解析兜底；仍没有 ⇒ 用料只能留空，不卡流程）。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import RemoteSelect from '@/components/RemoteSelect.vue'
import PageShell from '@/components/PageShell.vue'
import { OUTSOURCE_RETURN_ORDER_DIRTY_KEY } from '@/api/enums'

const router = useRouter()

/** 退货规格：与加工单收货详细页的退货弹窗同一口径（A/B/C/不良） */
const SPECS = [
  { value: 'A', label: 'A规' }, { value: 'B', label: 'B规' },
  { value: 'C', label: 'C规' }, { value: 'DEFECT', label: '不良' }
]

const form = reactive({
  factoryId: undefined as any,
  warehouseId: undefined as any,
  productMasterId: undefined as any,
  bomSnapshotId: undefined as any,
  qualityType: 'A' as string,
  quantity: '' as any,
  remark: ''
})
const saving = ref(false)

/** 加工厂（= 还料与应付对象）：2026-09-21 用户口径 —— 只能是加工厂/供应商，不能是供货商（成品商） */
const fetchFactories = (kw: string) =>
  request.get('/supplier/page', { params: { pageSize: 500, name: kw, excludeSupplierType: 'product' } })
/** 扣减的成品仓（我方自有成品仓） */
const fetchFinishedWarehouses = (kw: string) =>
  request.get('/warehouse/page', { params: { pageSize: 500, warehouseName: kw, warehouseCategory: 'INVENTORY', warehouseType: 'FINISHED' } })
/** 产品主数据（无单时没有加工单产品行可选） */
const fetchProducts = (kw: string) =>
  request.get('/product/page', { params: { pageSize: 500, keyword: kw } })

// ---------- BOM 快照（本页新增） ----------
const snapshots = ref<any[]>([])
const snapshotLoading = ref(false)
const KIND_LABEL: Record<string, string> = { BOM: '研发BOM', ORDER: '加工单', MIGRATED: '历史迁移' }
function snapshotLabel(s: any) {
  const v = s?.bomVersion != null ? ('v' + s.bomVersion) : ('#' + s?.snapshotId)
  const kind = KIND_LABEL[String(s?.kind)] || s?.kind || ''
  const cnt = s?.itemCount != null ? ('，' + s.itemCount + ' 项料') : ''
  return v + (kind ? ('（' + kind + '）') : '') + cnt
}
/** 选完"加工厂 + 产品"后自动解析：后端按"最近一次被加工单用过的快照"倒序返回，第一项即默认 */
async function loadSnapshots() {
  snapshots.value = []
  form.bomSnapshotId = undefined
  if (!form.productMasterId || !form.factoryId) return
  snapshotLoading.value = true
  try {
    const r = await request.get<any, any>('/outsource/order-delivery/product-snapshot-options', {
      params: { factoryId: form.factoryId, productMasterId: form.productMasterId }
    })
    snapshots.value = r || []
    if (snapshots.value.length > 0) form.bomSnapshotId = snapshots.value[0].snapshotId
  } catch (e: any) {
    console.warn('解析 BOM 快照失败', e?.message || e)
  } finally { snapshotLoading.value = false }
}
const noSnapshot = computed(() => !!form.productMasterId && !!form.factoryId && !snapshotLoading.value && snapshots.value.length === 0)

async function submit() {
  if (!form.factoryId) { ElMessage.warning('请选择加工厂'); return }
  if (!form.warehouseId) { ElMessage.warning('请选择扣减的成品仓库'); return }
  if (!form.productMasterId) { ElMessage.warning('请选择产品'); return }
  const qty = Math.round(Number(form.quantity) || 0)
  if (!(qty > 0)) { ElMessage.warning('请输入退货数量'); return }
  saving.value = true
  try {
    await request.post('/outsource/order-delivery/return-defect-no-order', {
      factoryId: form.factoryId, warehouseId: form.warehouseId,
      productMasterId: form.productMasterId, qualityType: form.qualityType,
      quantity: qty, remark: form.remark,
      // 可空：不传后端会再解析一次（同口径）；这里显式带上用户确认/自动带出的那一版
      bomSnapshotId: form.bomSnapshotId || undefined
    })
    ElMessage.success('加工退货草稿已保存，请在「无单退货」列表审核')
    sessionStorage.setItem(OUTSOURCE_RETURN_ORDER_DIRTY_KEY, '1')
    router.replace('/outsource/return-order/unlinked')
  } catch (e: any) { ElMessage.error(e?.message || '保存失败') } finally { saving.value = false }
}
function cancel() { router.back() }
onMounted(() => { /* 地址栏直达也能用：无预填参数 */ })
</script>

<template>
  <PageShell :loading="false" back-fallback="/outsource/return-order/unlinked">
    <template #actions>
      <el-button :loading="saving" type="primary" @click="submit">保存草稿</el-button>
      <el-button @click="cancel">取消</el-button>
    </template>

    <el-card shadow="never">
      <template #header><span style="font-weight:600">新增无单加工退货</span></template>

      <el-alert type="info" :closable="false" show-icon style="margin-bottom:12px">
        <template #title>
          <span style="font-size:var(--app-font-xs);line-height:1.5">
            不关联加工单的加工退货：审核后成品以<b>「成品（加工退货）」</b>形态转入该加工厂的委外仓
            （本单<b>不拆料还料、不冲应付</b>）；工厂修好送回时到「加工返回单」登记 —— 那里按
            <b>本单的 BOM 快照</b>限定可选的「实际用料」。
          </span>
        </template>
      </el-alert>

      <el-form :model="form" label-width="var(--app-label-width-lg)" size="small">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item required label="加工厂">
              <RemoteSelect v-model="form.factoryId" :fetch="fetchFactories" :label-key="(row:any)=>row.name"
                style="width:100%" placeholder="加工厂（还料/应付对象）" @update:model-value="loadSnapshots" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item required label="扣减成品仓">
              <RemoteSelect v-model="form.warehouseId" :fetch="fetchFinishedWarehouses"
                :label-key="(row:any)=>`${row.warehouseName} (${row.code})`" style="width:100%" placeholder="选择扣减的成品仓库" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item required label="产品">
              <RemoteSelect v-model="form.productMasterId" :fetch="fetchProducts" :label-key="(row:any)=>row.name"
                style="width:100%" placeholder="选择产品" @update:model-value="loadSnapshots" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="BOM 快照">
              <el-select v-model="form.bomSnapshotId" :loading="snapshotLoading" clearable filterable style="width:100%"
                :placeholder="(!form.productMasterId || !form.factoryId) ? '选完加工厂 + 产品自动带出' : (snapshots.length ? '默认最近一次用过的快照' : '该产品无可用快照')">
                <el-option v-for="s in snapshots" :key="s.snapshotId" :label="snapshotLabel(s)" :value="s.snapshotId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item required label="退货规格">
              <el-select v-model="form.qualityType" style="width:100%">
                <el-option v-for="o in SPECS" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item required label="退货数量">
              <el-input v-model="form.quantity" type="number" placeholder="整数"
                @change="form.quantity = Math.round(Number(form.quantity) || 0)" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="form.remark" placeholder="选填，如退回原因" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <el-alert v-if="noSnapshot" type="warning" :closable="false" show-icon style="margin-top:4px">
        <template #title>
          <span style="font-size:var(--app-font-xs);line-height:1.5">
            该产品在该加工厂没有可用的 BOM 快照（没查出用过 BOM 的加工单）⇒ 可以继续保存，
            但「加工返回单」里将<b>没有可选的实际用料</b>（届时只能不填用料，不卡流程）。
            若确需按 BOM 选料，请先确认该产品的 BOM / 或从加工单入口退回。
          </span>
        </template>
      </el-alert>
    </el-card>
  </PageShell>
</template>
