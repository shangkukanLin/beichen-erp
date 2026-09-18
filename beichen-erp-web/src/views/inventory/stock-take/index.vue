<script setup lang="ts">
/**
 * 库存盘点（成品库存 → 库存盘点）
 *
 * 2026-09-16 改造：主体抽到 views/stock-take/StockTakePanel.vue，本页只负责声明 scope=PRODUCT
 * —— 「库存盘点」只盘**成品类仓库**（现只有成品仓，仓型已收敛为 成品仓/辅料仓）；
 * 物料仓（委外仓 / 自有物料仓）的盘点在「物料仓库 → 物料库存盘点」。
 */
import StockTakePanel from '@/views/stock-take/StockTakePanel.vue'
</script>

<template>
  <StockTakePanel scope="PRODUCT" />
</template>
