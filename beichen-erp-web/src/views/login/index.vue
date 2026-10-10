<script setup lang="ts">
import { reactive, ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { login } from '@/api/auth'
import { getCompanyList, verifyAdmin } from '@/api/company'
import { useUserStore, type UserInfo } from '@/stores/user'
import { SUPER_ADMIN_ROLE_CODE } from '@/constants/system'
import type { MenuVO } from '@/api/system'
import type { Company } from '@/api/company'
// 2026-10-07「记住密码」：本地存储口径集中在 utils/remember.ts（含安全说明与"只记用户名"开关）
import { loadRememberedLogin, saveRememberedLogin, clearRememberedLogin } from '@/utils/remember'

const router = useRouter()
const userStore = useUserStore()

const loginFormRef = ref<FormInstance>()
const loading = ref(false)
const companyOptions = ref<Company[]>([])
const companyLoading = ref(false)

const loginForm = reactive({
  username: '',
  password: '',
  companyId: undefined as number | undefined,
  remember: false
})

const adminDialogVisible = ref(false)
const adminForm = reactive({ username: '', password: '' })
const adminVerifying = ref(false)

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 3, message: '密码长度不少于3位', trigger: 'blur' }
  ]
}

async function loadCompanies() {
  companyLoading.value = true
  try {
    companyOptions.value = await getCompanyList() || []
  } catch {
    // 公司列表加载失败时不注入假数据，避免用户以错误公司登录
    companyOptions.value = []
    ElMessage.error('公司列表加载失败，请刷新页面重试')
  }
  finally { companyLoading.value = false }
}

// 公司列表加载后，默认选第一个
watch(companyOptions, (list) => {
  if (list.length > 0 && !loginForm.companyId) {
    loginForm.companyId = list[0].id
  }
}, { immediate: true })

onMounted(() => {
  loadCompanies()
  const saved = loadRememberedLogin()
  if (saved.username) {
    loginForm.username = saved.username
    loginForm.remember = true
    // 回填口令（仅当历史遗留格式能被识别时才有值；旧格式会被 loadRememberedLogin 顺手清掉）
    if (saved.password) loginForm.password = saved.password
  }
})

/**
 * 「记住密码」（2026-10-07 用户要求）：勾选则把账号 + 口令（**混淆后**）落到本地存储，下次进入自动回填；
 * 取消勾选立即清除。⚠️ 混淆不是加密 —— 能打开本机 DevTools 的人可还原口令，仅适合受控设备；
 * 详见 utils/remember.ts 顶部的安全边界说明（含"改回只记用户名"的一行开关）。
 */
function saveRemember() {
  if (loginForm.remember) saveRememberedLogin(loginForm.username, loginForm.password)
  else clearRememberedLogin()
}

async function handleLogin() {
  if (!loginFormRef.value) return
  await loginFormRef.value.validate(async (valid) => {
    if (!valid) return
    if (!loginForm.companyId) { ElMessage.warning('请选择公司'); return }
    loading.value = true
    try {
      const res = await login({
        username: loginForm.username,
        password: loginForm.password,
        companyId: loginForm.companyId
      })
      const token = (res as { token?: string })?.token || ''
      const userInfo = (res as { userInfo?: UserInfo })?.userInfo || {}
      const menus = (res as { menus?: MenuVO[] })?.menus || []
      const dashboardTabs = (res as { dashboardTabs?: string[] | null })?.dashboardTabs
      userStore.setToken(token)
      userStore.setUserInfo(userInfo)
      userStore.setMenus(menus)
      userStore.setDashboardTabs(dashboardTabs)
      saveRemember()
      ElMessage.success('登录成功')
      router.push('/')
    } catch (e: any) { ElMessage.error('登录失败: ' + (e?.message || '未知错误')) } finally { loading.value = false }
  })
}

// 管理公司
async function openAdminDialog() {
  adminForm.username = ''; adminForm.password = ''
  adminDialogVisible.value = true
}

async function handleAdminVerify() {
  if (!adminForm.username || !adminForm.password) { ElMessage.warning('请输入超级管理员账号密码'); return }
  adminVerifying.value = true
  try {
    const res = await verifyAdmin(adminForm)
    adminDialogVisible.value = false
    // 验证成功，用返回的 token 设置认证状态
    userStore.setToken(res.token)
    // 2026-10-10 修复（用户报障：超管验证通过后却落到 /403，进不去「公司管理」）：
    // 路由守卫 /company-manage 判的是 userStore.isSuperAdmin，而它读 userInfo.roles，
    // 且 userInfo 是**上一次登录持久化在 localStorage 的值**。原实现只设 token、不写身份 ⇒
    // 只要这份持久化身份的 roles 不含 super_admin（该浏览器上次是用**公司账号**登录的、
    // 或换了浏览器 / 清过缓存），刚验证通过的超管也会被判 false ⇒ next('/403')。
    // 后端 /admin/verify 已校验过该账号**持有 super_admin 角色**（否则不会返回 token），
    // 故此处据实把身份回写进 store（只覆盖 roles，其余字段保持原样）。
    userStore.setUserInfo({ ...(userStore.userInfo || {}), roles: [SUPER_ADMIN_ROLE_CODE] })
    router.push('/company-manage')
  } catch (e: any) {
    // 错误已在拦截器提示
  } finally { adminVerifying.value = false }
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
        <el-form ref="loginFormRef" :model="loginForm" :rules="rules" label-width="0" size="large" @keyup.enter="handleLogin">
          <el-form-item prop="companyId">
            <el-select v-model="loginForm.companyId" placeholder="请选择公司" style="width:100%" :loading="companyLoading">
              <el-option v-for="c in companyOptions" :key="c.id ?? 0" :label="c.companyName" :value="c.id as number" />
            </el-select>
          </el-form-item>
          <el-form-item prop="username">
            <el-input v-model="loginForm.username" placeholder="请输入用户名" :prefix-icon="User" clearable autocomplete="username" />
          </el-form-item>
          <el-form-item prop="password">
            <el-input v-model="loginForm.password" type="password" placeholder="请输入密码" :prefix-icon="Lock" show-password autocomplete="current-password" />
          </el-form-item>
          <div class="login-options">
            <el-checkbox v-model="loginForm.remember">记住密码</el-checkbox>
          </div>
          <el-form-item>
            <el-button type="primary" size="large" class="login-btn" :loading="loading" @click="handleLogin">登 录</el-button>
          </el-form-item>
        </el-form>
        <div class="login-footer">
          <el-button type="info" link @click="openAdminDialog">管理公司</el-button>
        </div>
      </el-card>
    </div>

    <!-- 超级管理员验证弹框 -->
    <el-dialog v-model="adminDialogVisible" title="管理公司 — 超级管理员验证" width="var(--app-dialog-sm)" :close-on-click-modal="false">
      <el-form :model="adminForm" label-width="0" size="large" @keyup.enter="handleAdminVerify">
        <el-form-item><el-input v-model="adminForm.username" placeholder="超级管理员账号" :prefix-icon="User" /></el-form-item>
        <el-form-item><el-input v-model="adminForm.password" type="password" placeholder="超级管理员密码" :prefix-icon="Lock" show-password /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adminDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="adminVerifying" @click="handleAdminVerify">验证并进入</el-button>
      </template>
    </el-dialog>
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
  height: 520px;
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
.banner-content h1 { font-size: var(--app-font-display); margin: 0 0 12px; letter-spacing: 2px; }
.banner-content p { font-size: var(--app-font-base); opacity: 0.85; margin: 0 0 32px; line-height: 1.6; }
.banner-features { list-style: none; padding: 0; margin: 0; }
.banner-features li { padding: 8px 0; font-size: var(--app-font-base); opacity: 0.9; }
.banner-features li::before { content: '✓'; margin-right: 10px; color: #7fd1ff; }
.login-card { width: 380px; border: none; border-radius: 0; display: flex; flex-direction: column; justify-content: center; }
.login-card :deep(.el-card__body) { padding: 36px 40px 24px; }
.login-header { text-align: center; margin-bottom: 20px; }
.login-header h2 { margin: 0 0 8px; font-size: var(--app-font-num); color: #1a2a4a; }
.login-header p { margin: 0; color: var(--app-text-secondary); font-size: var(--app-font-base); }
.login-options { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
.login-btn { width: 100%; letter-spacing: 4px; }
.login-footer { text-align: center; margin-top: 4px; }

@media (max-width: 768px) {
  .login-box { flex-direction: column; height: auto; }
  .login-banner { display: none; }
  .login-card { width: 100%; }
}
</style>
