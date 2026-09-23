<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { usePageBack } from '@/composables/usePageBack'

/**
 * 次级页面统一骨架（2026-09-23 用户口径：统一模板，不只是统一返回按钮）。
 *
 * 骨架 = 页头（**2026-09-23 用户定稿口径 —— 全站统一，勿再左右调整**：
 *                  左起：#leading 主操作（保存/提交等，**最左边**）+ 页面标题 + #sub 副信息；
 *                  右端：#actions 次要业务动作 + 固定最右的「← 返回」（**返回在标题右侧**））
 *        + 内容区（默认插槽，一般放若干 <SectionCard>）
 *        + 底部操作条（#footer，可选，右对齐；仅个别页面需要底部条时使用）
 *
 * ⚠️ 该左右布局由用户明确指定过一次又纠正过一次（提交 35a42df → d5d4537 → 回退 f29c0b5），
 *    "操作最左、返回在标题右侧" 即为最终口径；改动前先向用户确认。
 *
 * 改造前的问题：69 个次级页面根容器 class 有 40+ 种、页头一半页面没有、返回按钮位置/文案各写各的。
 * 现在统一走本组件；页内内容布局不动，仅换外壳 ⇒ 可逐页增量改造、可回退。
 */
const props = withDefaults(defineProps<{
  /** 页面标题；不传则取路由 meta.title */
  title?: string
  /** 页面加载态（统一挂在骨架根节点上，替代各页变量名不一的 v-loading） */
  loading?: boolean
  /** 兜底列表页：深链/刷新直进（周边没有别的页签）时「返回」的去处 */
  backFallback?: string
  /** 是否显示右侧「返回」（默认显示；极少数非次级页可关掉）
   *  ⚠️ 必须给默认值：Vue 对 Boolean 类型 prop 缺省时按 false 处理 ⇒ 模板里直接 `v-if="showBack"`
   *     会恒为假、按钮永远不渲染（2026-09-23 由 ui-e2e-p17 抓到，故此处显式 default: true） */
  showBack?: boolean
}>(), {
  showBack: true,
  loading: false
})

const route = useRoute()
const { back } = usePageBack(props.backFallback)

const pageTitle = computed(() => props.title || (route.meta.title as string) || '')
</script>

<template>
  <div class="page-shell" v-loading="loading">
    <div class="page-header">
      <div class="page-header__left">
        <!-- 主操作（保存/提交）固定在**最左边**、标题之前（2026-09-23 用户定稿口径） -->
        <div v-if="$slots.leading" class="page-header__leading">
          <slot name="leading" />
        </div>
        <span class="page-header__title">{{ pageTitle }}</span>
        <slot name="sub" />
      </div>
      <div class="page-header__ops">
        <slot name="actions" />
        <el-button v-if="showBack" link :icon="'ArrowLeft'" @click="back">返回</el-button>
      </div>
    </div>

    <div class="page-body">
      <slot />
    </div>

    <div v-if="$slots.footer" class="page-footer">
      <slot name="footer" />
    </div>
  </div>
</template>
