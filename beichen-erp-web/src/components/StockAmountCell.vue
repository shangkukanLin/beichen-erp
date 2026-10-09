<!--
  库存金额单元格（2026-10-09）。口径见后端 StockCosts：
  行金额 = 数量 × 单价（成本价 → 最近进价 → 物料手填单价）；三者全空 ⇒ **不显示 0**，
  而是明示「成本未维护」——静默显示 0 会让用户以为系统算错。
  金额格式走 utils/format 的表格口径（2 位小数、无千分位），与列宽扫描器相容。
-->
<template>
  <el-tooltip v-if="missing" content="该 SKU 未维护成本价 / 最近进价 / 物料手填单价 ⇒ 金额按 0 计且不计入合计" placement="top">
    <span class="cost-missing">成本未维护</span>
  </el-tooltip>
  <span v-else>{{ fmtMoneyPlain(row?.stockAmount) }}</span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { fmtMoneyPlain } from '@/utils/format'

const props = defineProps<{ row?: any }>()
const missing = computed(() => !!props.row?.costMissing)
</script>

<style scoped>
.cost-missing { color: #e6a23c; cursor: help; }
</style>
