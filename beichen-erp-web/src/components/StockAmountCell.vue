<!--
  库存金额单元格（2026-10-09）。口径见后端 StockCosts：
  行金额 = 数量 × 单价（成本价 → 最近进价 → 物料手填单价）；三者全空 ⇒ **不显示 0**，
  而是明示「成本未维护」——静默显示 0 会让用户以为系统算错。
  金额格式走 utils/format 的 **fmtAmount**（2026-10-10 用户口径：精确到分；小数两位为 00 则不显示小数部分）。
  单元格变短不会撑坏列宽 —— el-table 用 fixed 布局，min-width 列不吃单元格内容（当日已实测 ✓）。
-->
<template>
  <el-tooltip v-if="missing" content="该 SKU 未维护成本价 / 最近进价 / 物料手填单价 ⇒ 金额按 0 计且不计入合计" placement="top">
    <span class="cost-missing">成本未维护</span>
  </el-tooltip>
  <span v-else>{{ fmtMoneyPlain(row?.stockAmount) }}</span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
// 2026-10-10 用户需求「金额精确到分、小数两位为 00 则不显示小数部分」：本组件渲染的是**金额** ⇒
// 改走 fmtAmount ✓（用 `as` 保留本地名，避免为一个口径改动去动模板 ✓）。
import { fmtAmount as fmtMoneyPlain } from '@/utils/format'

const props = defineProps<{ row?: any }>()
const missing = computed(() => !!props.row?.costMissing)
</script>

<style scoped>
.cost-missing { color: #e6a23c; cursor: help; }
</style>
