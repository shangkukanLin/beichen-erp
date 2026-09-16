<script setup lang="ts">
/**
 * 物料库存盘点（物料仓库 → 物料库存盘点，2026-09-16 新增）
 *
 * 与成品「库存盘点」共用后端盘点内核（inventory_stock_take）与前端面板，
 * 仅通过 scope=MATERIAL 限定范围：**只盘物料仓**（委外仓 + 自有物料仓），
 * 且接口级限「跟单专员」（后端 StockTakeServiceImpl.assertRoleForScope 校验）。
 */
import StockTakePanel from '@/views/stock-take/StockTakePanel.vue'
</script>

<template>
  <StockTakePanel scope="MATERIAL" />
</template>
