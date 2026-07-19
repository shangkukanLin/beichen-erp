import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import './styles/index.css'

const app = createApp(App)

app.config.globalProperties.$fmtDate = (val: any) => {
  if (val == null || val === '') return ''
  const s = String(val)
  return s.length >= 10 ? s.substring(0, 10) : s
}

app.use(createPinia())
app.use(router)
app.use(ElementPlus)

app.mount('#app')
