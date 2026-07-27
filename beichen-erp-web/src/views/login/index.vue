<script setup lang="ts">
import { reactive, ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { login } from '@/api/auth'
import { getCompanyList, verifyAdmin } from '@/api/company'
import { useUserStore, type UserInfo } from '@/stores/user'
import type { MenuVO } from '@/api/system'
import type { Company } from '@/api/company'

const router = useRouter()
const userStore = useUserStore()

const loginFormRef = ref<FormInstance>()
const loading = ref(false)
const companyOptions = ref<Company[]>([])
const companyLoading = ref(false)

const REMEMBER_KEY = 'beichen_erp_remember'

const loginForm = reactive({
  username: '',
  password: '',
  companyId: undefined as number | undefined,
  remember: false
})

const adminDialogVisible = ref(false)
const adminForm = reactive({ username: '', password: '' })
const adminVerifying = ref(false)

// 数据导入
const importDialogVisible = ref(false)
const importFile = ref<File | null>(null)
const importLoading = ref(false)
const importPreview = ref<any>(null)
const importStep = ref<'upload' | 'preview'>('upload')
const confirmText = ref('')

function handleFileChange(file: any) { importFile.value = file.raw || file }

function handleImportPreview() {
  if (!importFile.value) return
  importLoading.value = true
  const reader = new FileReader()
  reader.onload = (e) => {
    try {
      const data = JSON.parse(e.target?.result as string)
      const info = data.exportInfo || {}
      const tables = data.tables || {}
      // 计算所有表的记录数
      let totalRecords = 0
      Object.values(tables).forEach((rows: any) => {
        if (Array.isArray(rows)) totalRecords += rows.length
      })
      importPreview.value = {
        time: info.time || '未知',
        tableCount: Object.keys(tables).length,
        recordCount: totalRecords
      }
      importStep.value = 'preview'
    } catch (ex: any) {
      ElMessage.error('文件解析失败: ' + (ex?.message || '格式错误'))
      importPreview.value = null
      importFile.value = null
    } finally { importLoading.value = false }
  }
  reader.readAsText(importFile.value)
}

async function handleImportConfirm() {
  if (confirmText.value !== '确认导入' || !importFile.value) return
  importLoading.value = true
  try {
    const fd = new FormData(); fd.append('file', importFile.value)
    const res = await request.post<any, any>('/system/import-data', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
    const data = res?.data || res
    ElMessage.success(`导入成功：${data?.totalRecords || 0} 条记录，${data?.totalTables || 0} 张表`)
    importDialogVisible.value = false
    resetImport()
  } catch (e: any) { ElMessage.error('导入失败: ' + (e?.message || '未知错误')) } finally { importLoading.value = false }
}

function resetImport() {
  importPreview.value = null
  confirmText.value = ''
  importFile.value = null
  importStep.value = 'upload'
}

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
  } catch { companyOptions.value = [{ id: 1, companyName: '北辰科技' }] }
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
  const saved = localStorage.getItem(REMEMBER_KEY)
  if (saved) {
    try {
      const obj = JSON.parse(saved)
      loginForm.username = obj.username || ''
      loginForm.password = obj.password ? decodeBase64(obj.password) : ''
      loginForm.remember = true
    } catch { localStorage.removeItem(REMEMBER_KEY) }
  }
})

function encodeBase64(str: string): string { return btoa(unescape(encodeURIComponent(str))) }
function decodeBase64(str: string): string { return decodeURIComponent(escape(atob(str))) }

function saveRemember() {
  if (loginForm.remember) {
    localStorage.setItem(REMEMBER_KEY, JSON.stringify({ username: loginForm.username, password: encodeBase64(loginForm.password) }))
  } else {
    localStorage.removeItem(REMEMBER_KEY)
  }
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
      userStore.setToken(token)
      userStore.setUserInfo(userInfo)
      userStore.setMenus(menus)
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
            <el-input v-model="loginForm.username" placeholder="请输入用户名" :prefix-icon="User" clearable />
          </el-form-item>
          <el-form-item prop="password">
            <el-input v-model="loginForm.password" type="password" placeholder="请输入密码" :prefix-icon="Lock" show-password />
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
    <el-dialog v-model="adminDialogVisible" title="管理公司 — 超级管理员验证" width="380px" :close-on-click-modal="false">
      <el-form :model="adminForm" label-width="0" size="large" @keyup.enter="handleAdminVerify">
        <el-form-item><el-input v-model="adminForm.username" placeholder="超级管理员账号" :prefix-icon="User" /></el-form-item>
        <el-form-item><el-input v-model="adminForm.password" type="password" placeholder="超级管理员密码" :prefix-icon="Lock" show-password /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adminDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="adminVerifying" @click="handleAdminVerify">验证并进入</el-button>
      </template>
    </el-dialog>

    <!-- 数据导入按钮 -->
    <div class="import-btn-wrapper">
      <el-button type="default" size="small" text @click="importDialogVisible = true">数据导入</el-button>
    </div>

    <!-- 数据导入弹窗 -->
    <el-dialog v-model="importDialogVisible" title="数据导入" width="480px" :close-on-click-modal="false" @closed="resetImport">
      <!-- 第一步：选择文件 -->
      <div v-if="importStep === 'upload'">
        <el-upload drag :auto-upload="false" :limit="1" accept=".json" :on-change="handleFileChange" :file-list="[]">
          <el-icon :size="48"><component is="UploadFilled" /></el-icon>
          <div style="margin-top:8px">将备份 JSON 文件拖到此处，或点击选择</div>
        </el-upload>
        <div style="text-align:center;margin-top:16px">
          <el-button type="primary" :disabled="!importFile" :loading="importLoading" @click="handleImportPreview">下一步</el-button>
        </div>
      </div>

      <!-- 第二步：预览并确认 -->
      <div v-if="importStep === 'preview' && importPreview" style="text-align:center">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="备份时间">{{ importPreview.time }}</el-descriptions-item>
          <el-descriptions-item label="数据表">{{ importPreview.tableCount }} 张</el-descriptions-item>
          <el-descriptions-item label="总记录数" :span="2">{{ importPreview.recordCount }} 条</el-descriptions-item>
        </el-descriptions>
        <el-alert type="error" :closable="false" show-icon style="margin-top:16px;text-align:left">
          <template #title>此操作将清空当前系统全部数据并替换为导入数据，不可撤销！</template>
        </el-alert>
        <div style="margin-top:12px;text-align:left">
          <span style="color:#f56c6c">请输入"确认导入"以继续：</span>
          <el-input v-model="confirmText" placeholder="确认导入" size="small" style="margin-top:4px" />
        </div>
        <div style="margin-top:16px">
          <el-button @click="resetImport">重新选择</el-button>
          <el-button type="danger" :disabled="confirmText !== '确认导入'" :loading="importLoading" @click="handleImportConfirm">确认导入</el-button>
        </div>
      </div>
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
.banner-content h1 { font-size: 34px; margin: 0 0 12px; letter-spacing: 2px; }
.banner-content p { font-size: 14px; opacity: 0.85; margin: 0 0 32px; line-height: 1.6; }
.banner-features { list-style: none; padding: 0; margin: 0; }
.banner-features li { padding: 8px 0; font-size: 14px; opacity: 0.9; }
.banner-features li::before { content: '✓'; margin-right: 10px; color: #7fd1ff; }
.login-card { width: 380px; border: none; border-radius: 0; display: flex; flex-direction: column; justify-content: center; }
.login-card :deep(.el-card__body) { padding: 36px 40px 24px; }
.login-header { text-align: center; margin-bottom: 20px; }
.login-header h2 { margin: 0 0 8px; font-size: 22px; color: #1a2a4a; }
.login-header p { margin: 0; color: #909399; font-size: 13px; }
.login-options { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
.login-btn { width: 100%; letter-spacing: 4px; }
.login-footer { text-align: center; margin-top: 4px; }
.import-btn-wrapper { position: fixed; left: 20px; bottom: 20px; z-index: 10; }

@media (max-width: 768px) {
  .login-box { flex-direction: column; height: auto; }
  .login-banner { display: none; }
  .login-card { width: 100%; }
}
</style>
