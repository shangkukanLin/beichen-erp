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
import './styles/tokens.css'
import './styles/index.css'

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

app.use(createPinia())
app.use(router)
// locale: zhCn ⇒ 所有 Element Plus 内置文案（确认框按钮、分页、空态、日期面板、上传、筛选…）走中文
app.use(ElementPlus, { locale: zhCn })

// F3-3 按钮级权限（方案 A）：v-perm="'purchase:exchange:audit'"
setupPermDirective(app)

app.mount('#app')
