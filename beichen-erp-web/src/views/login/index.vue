<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { login } from '@/api/auth'
import { useUserStore, type UserInfo } from '@/stores/user'
import type { MenuVO } from '@/api/system'

const router = useRouter()
const userStore = useUserStore()

const loginFormRef = ref<FormInstance>()
const loading = ref(false)

// 记住密码本地存储 key
const REMEMBER_KEY = 'beichen_erp_remember'

const loginForm = reactive({
  username: '',
  password: '',
  remember: false
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 3, message: '密码长度不少于3位', trigger: 'blur' }
  ]
}

// 读取记住的账号密码
onMounted(() => {
  const saved = localStorage.getItem(REMEMBER_KEY)
  if (saved) {
    try {
      const obj = JSON.parse(saved)
      loginForm.username = obj.username || ''
      loginForm.password = obj.password ? decodeBase64(obj.password) : ''
      loginForm.remember = true
    } catch {
      localStorage.removeItem(REMEMBER_KEY)
    }
  }
})

function encodeBase64(str: string): string {
  return btoa(unescape(encodeURIComponent(str)))
}

function decodeBase64(str: string): string {
  return decodeURIComponent(escape(atob(str)))
}

function saveRemember() {
  if (loginForm.remember) {
    localStorage.setItem(
      REMEMBER_KEY,
      JSON.stringify({
        username: loginForm.username,
        password: encodeBase64(loginForm.password)
      })
    )
  } else {
    localStorage.removeItem(REMEMBER_KEY)
  }
}

async function handleLogin() {
  if (!loginFormRef.value) return
  await loginFormRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      const res = await login({
        username: loginForm.username,
        password: loginForm.password
      })
      // res 即后端 data：{ token, userInfo, menus }
      const token = (res as { token?: string })?.token || ''
      const userInfo = (res as { userInfo?: UserInfo })?.userInfo || {}
      const menus = (res as { menus?: MenuVO[] })?.menus || []
      userStore.setToken(token)
      userStore.setUserInfo(userInfo)
      userStore.setMenus(menus)
      // 登录成功后再持久化记住密码
      saveRemember()
      ElMessage.success('登录成功')
      router.push('/')
    } catch {
      // 错误信息已在响应拦截器中提示
    } finally {
      loading.value = false
    }
  })
}
</script>

<template>
  <div class="login-container">
    <div class="login-box">
      <div class="login-banner">
        <div class="banner-content">
          <h1>北辰 ERP</h1>
          <p>屏幕研发 · 委外加工 · 进销存 · 财务一体化</p>
          <ul class="banner-features">
            <li>全业务链路打通</li>
            <li>成本精准核算</li>
            <li>智能体辅助</li>
          </ul>
        </div>
      </div>
      <el-card class="login-card" shadow="never">
        <div class="login-header">
          <h2>欢迎登录</h2>
          <p>请输入账号密码登录系统</p>
        </div>
        <el-form
          ref="loginFormRef"
          :model="loginForm"
          :rules="rules"
          label-width="0"
          size="large"
          @keyup.enter="handleLogin"
        >
          <el-form-item prop="username">
            <el-input
              v-model="loginForm.username"
              placeholder="请输入用户名"
              :prefix-icon="User"
              clearable
            />
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model="loginForm.password"
              type="password"
              placeholder="请输入密码"
              :prefix-icon="Lock"
              show-password
            />
          </el-form-item>
          <div class="login-options">
            <el-checkbox v-model="loginForm.remember">记住密码</el-checkbox>
          </div>
          <el-form-item>
            <el-button
              type="primary"
              size="large"
              class="login-btn"
              :loading="loading"
              @click="handleLogin"
            >
              登 录
            </el-button>
          </el-form-item>
        </el-form>
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.login-container {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1a2a4a 0%, #2d4a7a 50%, #3a6bb5 100%);
}

.login-box {
  display: flex;
  width: 880px;
  max-width: 92%;
  height: 480px;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.35);
}

.login-banner {
  flex: 1;
  background: linear-gradient(160deg, #1e3c72 0%, #2a5298 100%);
  color: #fff;
  display: flex;
  align-items: center;
  padding: 48px 40px;
}

.banner-content h1 {
  font-size: 34px;
  margin: 0 0 12px;
  letter-spacing: 2px;
}

.banner-content p {
  font-size: 14px;
  opacity: 0.85;
  margin: 0 0 32px;
  line-height: 1.6;
}

.banner-features {
  list-style: none;
  padding: 0;
  margin: 0;
}

.banner-features li {
  padding: 8px 0;
  font-size: 14px;
  opacity: 0.9;
}

.banner-features li::before {
  content: '✓';
  margin-right: 10px;
  color: #7fd1ff;
}

.login-card {
  width: 380px;
  border: none;
  border-radius: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.login-card :deep(.el-card__body) {
  padding: 48px 40px;
}

.login-header {
  text-align: center;
  margin-bottom: 28px;
}

.login-header h2 {
  margin: 0 0 8px;
  font-size: 22px;
  color: #1a2a4a;
}

.login-header p {
  margin: 0;
  color: #909399;
  font-size: 13px;
}

.login-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.login-btn {
  width: 100%;
  letter-spacing: 4px;
}

.login-tip {
  text-align: center;
  color: #c0c4cc;
  font-size: 12px;
  margin-top: 8px;
}

@media (max-width: 768px) {
  .login-box {
    flex-direction: column;
    height: auto;
  }
  .login-banner {
    display: none;
  }
  .login-card {
    width: 100%;
  }
}
</style>
