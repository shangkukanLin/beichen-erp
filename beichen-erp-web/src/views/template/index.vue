<script setup lang="ts">
defineOptions({ name: 'TemplateManage' })

import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PhasePanel from './phase-panel.vue'
import ContractPanel from './contract-panel.vue'

/**
 * 模版管理（基础数据，2026-09-15 用户要求）：把原本分散在两处的
 * 「阶段模板管理」（原 /dev/phase-template，基础数据下）与「加工合同模板」（原 /outsource/contract-template，委外加工下）
 * 合并为一个页面，用 TAB 区分。
 * <p>支持 `?tab=phase|contract` 深链：首页快捷入口、旧地址重定向都会带该参数直达对应页签；
 * 切换 TAB 时同步写回 URL（replace，不污染浏览器历史）。两个面板常驻（不随 TAB 卸载），切换不重新请求。</p>
 */
const route = useRoute()
const router = useRouter()

const TABS: string[] = ['phase', 'contract']
function normalize(t: unknown): string { return TABS.includes(String(t)) ? String(t) : 'phase' }

const activeTab = ref(normalize(route.query.tab))

watch(activeTab, (t) => {
  if (String(route.query.tab || '') === t) return
  router.replace({ path: '/template', query: { ...route.query, tab: t } })
})
watch(() => route.query.tab, (t) => {
  const v = normalize(t)
  if (v !== activeTab.value) activeTab.value = v
})
</script>

<template>
  <el-tabs v-model="activeTab" type="border-card">
    <el-tab-pane label="阶段模板管理" name="phase"><PhasePanel /></el-tab-pane>
    <el-tab-pane label="加工合同模板" name="contract"><ContractPanel /></el-tab-pane>
  </el-tabs>
</template>
