<script setup lang="ts">
/**
 * 物料收退「收货记录」详情（2026-09-29 用户口径「物料收退详情的收货记录列表需要有详细，
 * 参考加工收退的收货记录详情来做」）。
 * <p>与加工侧的 `views/outsource/order/delivery-record.vue` **同构**：只读独立页、按 id 回源，
 * 不做编辑（草稿的编辑仍在「物料收退详情」的弹窗里）。数据三条：
 * ① {@code GET /outsource/delivery/{id}}（表头，含**登记时间/制单人/审核人**——本轮后端补回）
 * ② {@code GET /outsource/delivery/{id}/items}（明细）
 * ③ {@code /outsource/material/page}（物料主数据 ⇒ 解析明细的物料名，与列表页同一套口径；
 *    查不到时退回 {@code #id}，不静默显示空白）。</p>
 * <p>入口：物料收退详情 → 收货记录 行内「详细」（同行的「单号」链接仍指向老的「物料收发单详情」）。</p>
 */
import { ref, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { DocStatusLabel, DocStatusTag, DeliveryTypeLabel, QualityTypeLabel, DefectHandleTypeLabel } from '@/api/enums'
import request from '@/utils/request'
import PageShell from '@/components/PageShell.vue'

const route = useRoute()
const loading = ref(false)
const row = ref<any>({})
const items = ref<any[]>([])
const materials = ref<any[]>([])

/** 明细里的物料名（主数据里查不到就显 #id，便于排查历史脏数据） */
function materialNameOf(it: any) {
  return materials.value.find((m: any) => m.id === it?.materialId)?.materialName || ('#' + (it?.materialId ?? ''))
}
/** 类型文案（共用枚举：RECEIVE 收料 / DEFECT_RETURN 退不良 / RECEIVE_RETURN 退货） */
function typeTextOf(r: any) { return DeliveryTypeLabel[r?.deliveryType] || r?.deliveryType || '-' }
/** 明细数量合计 */
const totalQuantity = computed(() => items.value.reduce((s: number, it: any) => s + (Number(it.quantity) || 0), 0))
function openAttach(url: string) { window.open(url + '?inline=true') }

/** 登记时间只取到秒（后端返回 ISO，前端统一按字符串裁，避免时区偏移） */
function timeTextOf(v: any) { return v ? String(v).replace('T', ' ').slice(0, 19) : '-' }

async function load() {
  loading.value = true
  try {
    const id = route.params.id
    const [rec, its, mats] = await Promise.all([
      request.get<any, any>('/outsource/delivery/' + id),
      request.get<any, any>('/outsource/delivery/' + id + '/items').catch(() => []),
      request.get<any, any>('/outsource/material/page', { params: { pageSize: 500 } }).catch(() => null)
    ])
    row.value = rec || {}
    items.value = its || []
    materials.value = (mats as any)?.records || []
  } catch { row.value = {}; items.value = [] } finally { loading.value = false }
}
onMounted(load)
</script>

<template>
  <!-- 统一骨架（2026-09-23 全站定稿口径）：返回交骨架，本页只读、无操作按钮 -->
  <PageShell :loading="loading" back-fallback="/outsource/material-order/delivery">
    <el-card shadow="never">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span style="font-weight:600">收货记录详情 — #{{ row.id ?? '' }}</span>
        </div>
      </template>
      <el-descriptions :column="3" border>
        <el-descriptions-item label="记录ID">{{ row.id }}</el-descriptions-item>
        <el-descriptions-item label="状态"><el-tag :type="DocStatusTag[row.status] || 'info'" size="small">{{ DocStatusLabel[row.status] || row.status }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="收货日期">{{ row.deliveryDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="登记时间">{{ timeTextOf(row.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="物料商">{{ row.supplierName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ typeTextOf(row) }}</el-descriptions-item>
        <el-descriptions-item label="收货仓库">{{ row.toWarehouseName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="物流公司">{{ row.logisticsCompany || '-' }}</el-descriptions-item>
        <el-descriptions-item label="物流单号">{{ row.logisticsNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="联系人">{{ row.contact || '-' }}</el-descriptions-item>
        <el-descriptions-item label="电话">{{ row.phone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="总数量">
          <span :style="{ color: totalQuantity < 0 ? 'var(--app-color-danger)' : '', fontWeight: '600' }">{{ totalQuantity }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="收货图片">
          <el-button v-if="row.attachUrl" type="primary" link size="small" @click="openAttach(row.attachUrl)">查看图片</el-button>
          <span v-else style="color:var(--app-text-placeholder)">—</span>
        </el-descriptions-item>
        <!-- 制单人 / 审核人（2026-09-23 全站单据口径；与加工侧收货记录详情一致） -->
        <el-descriptions-item label="制单人">{{ row.createByName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="审核人">{{ row.auditorName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="3">{{ row.remark || '-' }}</el-descriptions-item>
      </el-descriptions>

      <div style="margin:16px 0 8px;font-weight:600">收货明细</div>
      <el-table :data="items" border size="small">
        <el-table-column label="物料" min-width="160" show-overflow-tooltip><template #default="{ row: it }">{{ materialNameOf(it) }}</template></el-table-column>
        <el-table-column prop="unit" label="单位" width="70" />
        <el-table-column label="数量" width="90" align="right"><template #default="{ row: it }"><span :style="{ color: Number(it.quantity) < 0 ? 'var(--app-color-danger)' : '' }">{{ it.quantity }}</span></template></el-table-column>
        <el-table-column label="单价" width="110" align="right"><template #default="{ row: it }">{{ Number(it.unitPrice || 0).toFixed(4) }}</template></el-table-column>
        <el-table-column label="金额" width="120" align="right"><template #default="{ row: it }">{{ Number(it.amount || 0).toFixed(2) }}</template></el-table-column>
        <el-table-column label="品质" width="90"><template #default="{ row: it }"><el-tag :type="it.qualityType === 'DEFECT' ? 'danger' : 'success'" size="small">{{ QualityTypeLabel[it.qualityType] || it.qualityType || '-' }}</el-tag></template></el-table-column>
        <el-table-column label="处理方式" width="110"><template #default="{ row: it }">{{ DefectHandleTypeLabel[it.handleType] || it.handleType || '—' }}</template></el-table-column>
      </el-table>
    </el-card>
  </PageShell>
</template>

<style scoped>/* 页头已统一到全局骨架（PageShell） */</style>
