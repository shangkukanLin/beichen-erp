<script setup lang="ts">
// 加工退货记录详情（2026-09-23 用户要求：原「加工退货详情」580px 抽屉改为独立页面）
// —— 按 id 回源 `/outsource/order-delivery/return-defect/{id}/detail`（含落账明细），
//    台账行点击 / 行内「详情」按钮都跳到这里。
import { computed, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { DocStatusLabel, DocStatusTag } from '@/api/enums'
import request from '@/utils/request'
import PageShell from '@/components/PageShell.vue'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const detail = ref<any>({})

/** 退货规格 code -> 中文（与列表、无单退货弹窗同一口径） */
function specText(q?: string) {
  if (q === 'A') return 'A规'
  if (q === 'B') return 'B规'
  if (q === 'C') return 'C规'
  if (q === 'DEFECT') return '不良'
  return q || '-'
}
/**
 * 库存形态（与 /outsource/warehouse-detail 同口径）：无单退货的成品以「成品（加工退货）」形态进加工厂委外仓。
 */
const StockFormLabel: Record<string, string> = {
  MATERIAL: '物料',
  PRODUCT_DEFECT: '成品（加工退货）',
  PRODUCT_REPAIR: '成品（维修退货）',
  MATERIAL_REPAIR: '物料（送修在厂）',
}

/**
 * 是否为「无单退货·新口径」记录（2026-09-27 用户口径）——判定**只看实际流水**，不看 linked：
 * - 无单退货（GTW-，2026-09-25 P1-1 起）：不拆 BOM、不冲应付，只扣成品 + 把成品以 PRODUCT_DEFECT 转入委外仓
 *   ⇒ `materials` 为空、`outsourceIn` 有值；
 * - 存量「独立 DEFECT 单」（旧逻辑，已停止新增）：虽也不关联加工单，但会还料 + 负应付、且**没有** PRODUCT_DEFECT 转移腿
 *   ⇒ `materials` 非空 ⇒ 不能被误判成新口径（否则会隐藏它的真实还料明细）。
 */
const isNoOrderNew = computed(() =>
  !detail.value.linked && (detail.value.materials || []).length === 0
)
function goOrder() {
  if (detail.value.orderId != null) router.push(`/outsource/order/detail/${detail.value.orderId}`)
}

async function load() {
  loading.value = true
  try {
    detail.value = (await request.get<any, any>(`/outsource/order-delivery/return-defect/${route.params.id}/detail`)) || {}
  } catch { detail.value = {} } finally { loading.value = false }
}
onMounted(load)
</script>

<template>
  <!-- 统一骨架（2026-09-23 全站定稿口径）：返回交骨架（原「返回」按钮已删）；本页只读无操作 -->
  <PageShell :loading="loading" back-fallback="/outsource/return-order">
    <el-card shadow="never">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span style="font-weight:600">加工退货详情 — {{ detail.code || detail.legacyNo || ('记录 #' + (detail.id ?? '')) }}</span>
        </div>
      </template>

      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="退货单号">{{ detail.code || ('加工退货#' + (detail.id ?? '-')) }}</el-descriptions-item>
        <el-descriptions-item label="记录ID">{{ detail.id ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="退货日期">{{ detail.deliveryDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="DocStatusTag[detail.status] || 'info'" size="small">{{ DocStatusLabel[detail.status] || detail.status }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="加工厂">{{ detail.factoryName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="关联加工单">
          <el-button v-if="detail.orderCode && detail.orderId" type="primary" link @click="goOrder()">{{ detail.orderCode }}</el-button>
          <span v-else-if="detail.orderCode">{{ detail.orderCode }}</span>
          <span v-else style="color:var(--app-text-placeholder)">未关联（无单退回）</span>
        </el-descriptions-item>
        <el-descriptions-item label="产品">{{ (detail.productName || '-') + (detail.sku ? '（' + detail.sku + '）' : '') }}</el-descriptions-item>
        <el-descriptions-item label="退货规格">{{ specText(detail.qualityType) }}</el-descriptions-item>
        <el-descriptions-item label="退货数量">
          <span style="color:var(--app-color-danger);font-weight:500">{{ Math.abs(Number(detail.quantity || 0)) }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="扣减仓库">{{ detail.warehouseName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="建单时间">{{ detail.createTime ? String(detail.createTime).replace('T', ' ').slice(0, 19) : '-' }}</el-descriptions-item>
        <!-- 制单人 / 审核人（2026-09-23 用户口径：单据详情显示这两项） -->
        <el-descriptions-item label="制单人">{{ detail.createByName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="审核人">{{ detail.auditorName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="never">
      <template #header><span style="font-weight:600">落账明细</span></template>
      <!-- 2026-09-27 用户口径（详情页文案按真实流水分支）：无单退货（GTW-）与有单红冲（GTH-）落账口径不同，
           原先一律写「按 BOM 还料 + 冲减应付」⇒ 无单退货会显示根本不存在的动作（用户问到的困惑点）。
           判定见 isNoOrderNew（只看流水，兼容存量的「独立 DEFECT 单」）。 -->
      <el-alert v-if="detail.id && !detail.settled" type="info" :closable="false" show-icon
        :title="isNoOrderNew
          ? '尚未落账（草稿 / 已反审核）：审核后才会扣减成品，并把成品以「成品（加工退货）」形态转入加工厂委外仓（本口径不还料、不冲应付）。'
          : '尚未落账（草稿 / 已反审核）：审核后才会扣减成品、把 BOM 料还回工厂委外仓并冲减应付。'" />
      <template v-else-if="detail.settled">
        <p style="margin:0 0 8px;line-height:1.6;color:var(--app-text-secondary);font-size:var(--app-font-xs)">
          ① 成品：已从「{{ detail.warehouseName || '-' }}」扣减
          <b style="color:var(--app-color-danger)">{{ Math.abs(Number(detail.quantity || 0)) }}</b> 件（{{ specText(detail.qualityType) }}）；
          <template v-if="!isNoOrderNew">
            ② 还料：按 BOM 还回工厂委外仓的物料如下<template v-if="detail.orderCode">，并回退该加工单的已收数量</template>。
          </template>
          <template v-else>
            ② 还料：<b>本单不还料</b>（无单退货不拆 BOM —— 拆料与退货时点无关，BOM 改过即拆错）。
            退回成品以「成品（加工退货）」形态挂在下方委外仓；料在修好送回时开「加工返回单」按<b>实际用料</b>扣除，
            并生成对加工厂的赔料应收。
          </template>
        </p>
        <el-table v-if="(detail.materials || []).length" :data="detail.materials" border stripe size="small">
          <el-table-column prop="materialName" label="还回物料" min-width="130" show-overflow-tooltip />
          <el-table-column label="品质" width="70" align="center">
            <template #default="{ row }">{{ row.qualityType === 'DEFECT' ? '不良' : '良品' }}</template>
          </el-table-column>
          <el-table-column label="数量" width="80" align="right"><template #default="{ row }">{{ row.quantity }}</template></el-table-column>
          <el-table-column prop="warehouseName" label="还入的委外仓" min-width="120" show-overflow-tooltip />
        </el-table>
        <p v-if="!isNoOrderNew && !(detail.materials || []).length" style="margin:6px 0 0;color:var(--app-text-placeholder);font-size:var(--app-font-xs)">
          无还料记录（包工包料产品 / 该产品无 BOM 快照 ⇒ 只扣成品、不还料）
        </p>
        <!-- 无单退货独有：成品落在哪个委外仓 + 什么形态（数据来自 OUTSOURCE_DEFECT_IN 流水） -->
        <el-table v-if="detail.outsourceIn" :data="[detail.outsourceIn]" border stripe size="small" style="margin-top:8px">
          <el-table-column prop="warehouseName" label="转入的委外仓" min-width="150" show-overflow-tooltip />
          <el-table-column label="形态" width="150" align="center">
            <template #default="{ row }"><el-tag type="danger" size="small">{{ StockFormLabel[row.stockForm] || row.stockForm || '-' }}</el-tag></template>
          </el-table-column>
          <el-table-column label="数量" width="90" align="right">
            <template #default="{ row }">{{ Number(row.quantity || 0) > 0 ? '+' : '' }}{{ row.quantity }}</template>
          </el-table-column>
        </el-table>
        <p v-else-if="isNoOrderNew" style="margin:6px 0 0;color:var(--app-text-placeholder);font-size:var(--app-font-xs)">
          未查到委外仓入库流水（异常：无单退货审核后应有 PRODUCT_DEFECT 转移腿，请核对库存流水）
        </p>
        <p style="margin:12px 0 0;line-height:1.6;color:var(--app-text-secondary);font-size:var(--app-font-xs)">
          <template v-if="!isNoOrderNew">
            ③ 应付冲减：
            <b :style="{ color: Number(detail.payableAmount) < 0 ? 'var(--app-color-success)' : 'var(--app-text-regular)' }">
              {{ Number(detail.payableAmount || 0).toFixed(2) }}
            </b>
            <span v-if="detail.payableStatus">（{{ detail.payableStatus === 'UNSETTLED' ? '未付款' : detail.payableStatus === 'SETTLED' ? '已付款' : detail.payableStatus }}）</span>
            <span style="color:var(--app-text-placeholder)"> —— 负数表示冲减已生成的加工应付。</span>
          </template>
          <template v-else>
            ③ 应付：<b>本次退货不产生应付</b>（无单口径不动应付）—— 料的账在「加工返回单」按实际用料结转。
          </template>
        </p>
      </template>
    </el-card>
  </PageShell>
</template>

<style scoped>/* 页头已统一到全局骨架（PageShell）；原 .p 局部样式已删除 */</style>
