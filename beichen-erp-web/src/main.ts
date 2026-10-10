import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
// 中文语言包（2026-09-21 修复）：不设时 Element Plus 的内置文案全是英文 ——
// ElMessageBox 的按钮是 Cancel / OK、分页是 Total …/page、表格空态是 No Data、日期面板是 Today/Clear 等。
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import { setupPermDirective } from './directives/perm'
// 表格列宽记忆（2026-10-09）：全局兜底，一次覆盖所有 el-table（口径见 utils/tableWidths.ts）
import { setupTableWidths } from './utils/tableWidths'
// 物料展示标签（2026-10-10 用户口径「物料名称前面显示物料类型」）：
// 注册成全局属性 ⇒ 30 多处展示点每处只改一行（口径与实现见 utils/materialLabel.ts，类型见 types/material-label.d.ts）
import { materialLabel, materialTypePrefix } from './utils/materialLabel'
import './styles/tokens.css'
import './styles/index.css'
// 次级页面统一骨架样式（PageShell / SectionCard；2026-09-23 统一模板专项）
import './styles/page.css'

const app = createApp(App)

// 全局注册 Element Plus 图标组件，使 :icon="'Search'" 字符串写法也能正确渲染图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.config.globalProperties.$fmtDate = (val: any) => {
  if (val == null || val === '') return ''
  const s = String(val)
  return s.length >= 10 ? s.substring(0, 10) : s
}

// 物料展示标签：`物料类型 | 物料名称`（无类型时只显示名称）—— 口径见 utils/materialLabel.ts
app.config.globalProperties.$mLabel = materialLabel
app.config.globalProperties.$mTypePrefix = materialTypePrefix

app.use(createPinia())
app.use(router)
// locale: zhCn ⇒ 所有 Element Plus 内置文案（确认框按钮、分页、空态、日期面板、上传、筛选…）走中文
app.use(ElementPlus, { locale: zhCn })

// F3-3 按钮级权限（方案 A）：v-perm="'purchase:exchange:audit'"
setupPermDirective(app)

// 表格列宽记忆：用户拖动列宽后记住（存服务端、跟用户走、不跟公司走），下次进来仍是这个宽度。
// 未拖动/未登录/读不到偏好时**完全不动**，默认行为与之前一致。
setupTableWidths(app)

app.mount('#app')
