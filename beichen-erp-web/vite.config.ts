import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { fileURLToPath, URL } from 'node:url'

// https://vitejs.dev/config/
// 说明：main.ts 已全量引入 element-plus（import ElementPlus + dist/index.css 并 app.use），
// 因此这里不配置 ElementPlusResolver，避免按需注入 element-plus/es/... 子模块样式，
// 否则会触发"首次访问某页面 → Vite 发现新依赖 → 整页 reload"以及 noDiscovery 导致的空白问题。
export default defineConfig({
  plugins: [
    vue(),
    AutoImport({
      imports: ['vue', 'vue-router', 'pinia'],
      dts: 'auto-imports.d.ts'
    }),
    Components({
      dts: 'components.d.ts'
    })
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  // 依赖预构建：显式预构建页面懒加载引入的第三方库，避免首次访问时触发运行时依赖发现导致整页 reload
  optimizeDeps: {
    include: [
      'vue',
      'vue-router',
      'pinia',
      'axios',
      'element-plus',
      '@element-plus/icons-vue',
      '@vueup/vue-quill',
      'xlsx'
    ]
  },
  server: {
    port: 5173,
    host: true,
    allowedHosts: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
