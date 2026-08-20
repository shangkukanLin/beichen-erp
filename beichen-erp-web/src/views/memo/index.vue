<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { MemoStatus } from '@/api/enums'

const loading = ref(false)
const memoList = ref<any[]>([])
const activeMemoId = ref<number>(0)
const progressList = ref<any[]>([])
const progressLoading = ref(false)

// 状态分组（进行中/关闭）
const listTab = ref(MemoStatus.OPEN)
const keyword = ref('')

// 按状态分组的列表
const openMemos = computed(() => memoList.value.filter((m: any) => m.status === MemoStatus.OPEN))
const closedMemos = computed(() => memoList.value.filter((m: any) => m.status === MemoStatus.CLOSED))
const currentMemos = computed(() => (listTab.value === MemoStatus.OPEN ? openMemos.value : closedMemos.value))

// 新增/编辑备忘录弹窗
const dialogVisible = ref(false)
const dialogTitle = ref('')
const saving = ref(false)
const form = reactive({ id: undefined as any, title: '' })

// 新增进度
const newProgress = ref('')
const addingProgress = ref(false)

// 编辑进度弹窗
const editProgressVisible = ref(false)
const editProgressForm = reactive({ id: undefined as any, content: '' })

function statusLabel(s: string) {
  return s === MemoStatus.OPEN ? '进行中' : s === MemoStatus.CLOSED ? '关闭' : s
}
function statusTag(s: string): any {
  return s === MemoStatus.OPEN ? 'success' : 'info'
}

async function loadMemoList() {
  loading.value = true
  try {
    const p: any = { pageNum: 1, pageSize: 200 }
    if (keyword.value) p.keyword = keyword.value
    const r = await request.get<any, any>('/memo/page', { params: p })
    memoList.value = r?.records || []
    // 若当前选中项已不在当前分组，重置选中该分组第一条
    if (activeMemoId.value && !currentMemos.value.some((m: any) => m.id === activeMemoId.value)) {
      activeMemoId.value = currentMemos.value.length ? currentMemos.value[0].id : 0
    }
    if (!activeMemoId.value && currentMemos.value.length) {
      activeMemoId.value = currentMemos.value[0].id
    }
    if (activeMemoId.value) loadProgress()
  } finally { loading.value = false }
}

async function loadProgress() {
  if (!activeMemoId.value) { progressList.value = []; return }
  progressLoading.value = true
  try {
    const r = await request.get<any, any>(`/memo/${activeMemoId.value}/progress`)
    progressList.value = Array.isArray(r) ? r : []
  } finally { progressLoading.value = false }
}

function selectMemo(m: any) {
  activeMemoId.value = m.id
  loadProgress()
}

// 切换分组时自动选中该分组第一条
function onTabChange() {
  activeMemoId.value = currentMemos.value.length ? currentMemos.value[0].id : 0
  loadProgress()
}

function openAdd() {
  form.id = undefined; form.title = ''
  dialogTitle.value = '新增备忘录'
  dialogVisible.value = true
}
function openEdit(m: any) {
  form.id = m.id; form.title = m.title
  dialogTitle.value = '编辑备忘录'
  dialogVisible.value = true
}

async function handleSaveMemo() {
  if (!form.title.trim()) { ElMessage.warning('请输入标题'); return }
  saving.value = true
  try {
    if (form.id) {
      await request.put(`/memo/${form.id}`, { title: form.title })
      ElMessage.success('已更新')
    } else {
      await request.post('/memo', { title: form.title })
      ElMessage.success('已新增')
    }
    dialogVisible.value = false
    loadMemoList()
  } catch (e: any) { ElMessage.error(e?.message || '保存失败') } finally { saving.value = false }
}

async function toggleStatus(m: any) {
  const target = m.status === MemoStatus.OPEN ? MemoStatus.CLOSED : MemoStatus.OPEN
  try {
    await request.put(`/memo/${m.id}`, { status: target })
    loadMemoList()
  } catch (e: any) { ElMessage.error(e?.message || '操作失败') }
}

async function handleAddProgress() {
  if (!newProgress.value.trim()) { ElMessage.warning('请输入进度内容'); return }
  if (!activeMemoId.value) { ElMessage.warning('请先选择备忘录'); return }
  addingProgress.value = true
  try {
    await request.post(`/memo/${activeMemoId.value}/progress`, { content: newProgress.value.trim() })
    newProgress.value = ''
    loadProgress()
  } catch (e: any) { ElMessage.error(e?.message || '添加失败') } finally { addingProgress.value = false }
}

function openEditProgress(p: any) {
  editProgressForm.id = p.id
  editProgressForm.content = p.content
  editProgressVisible.value = true
}
async function handleSaveProgress() {
  if (!editProgressForm.content.trim()) { ElMessage.warning('请输入进度内容'); return }
  try {
    await request.put(`/memo/progress/${editProgressForm.id}`, { content: editProgressForm.content.trim() })
    ElMessage.success('已更新')
    editProgressVisible.value = false
    loadProgress()
  } catch (e: any) { ElMessage.error(e?.message || '保存失败') }
}
async function handleDeleteProgress(p: any) {
  try { await ElMessageBox.confirm('确认删除这条进度？', '提示', { type: 'warning' }) } catch { return }
  try {
    await request.delete(`/memo/progress/${p.id}`)
    ElMessage.success('已删除')
    loadProgress()
  } catch (e: any) { ElMessage.error(e?.message || '删除失败') }
}

onMounted(() => loadMemoList())
</script>

<template>
  <div class="memo-page">
    <el-card shadow="never">
      <el-form :inline="true">
        <el-form-item label="标题">
          <el-input v-model="keyword" placeholder="关键字" clearable style="width:180px" @keyup.enter="loadMemoList" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadMemoList">查询</el-button>
          <el-button type="success" @click="openAdd">新增备忘录</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <div class="memo-body">
      <!-- 左：备忘录列表 -->
      <el-card shadow="never" class="memo-list-card">
        <template #header>
          <el-tabs v-model="listTab" @tab-change="onTabChange">
            <el-tab-pane :label="`进行中 (${openMemos.length})`" name="OPEN" />
            <el-tab-pane :label="`关闭 (${closedMemos.length})`" name="CLOSED" />
          </el-tabs>
        </template>
        <div v-loading="loading" class="memo-list">
          <div
            v-for="m in currentMemos" :key="m.id"
            class="memo-item"
            :class="{ active: m.id === activeMemoId }"
            @click="selectMemo(m)"
          >
            <div class="memo-item-title">{{ m.title }}</div>
            <div class="memo-item-meta">
              <el-tag :type="statusTag(m.status)" size="small">{{ statusLabel(m.status) }}</el-tag>
              <span class="memo-item-time">{{ m.updateTime ? m.updateTime.slice(0, 10) : '' }}</span>
            </div>
          </div>
          <div v-if="currentMemos.length === 0" style="text-align:center;color:var(--app-text-secondary);padding:24px">暂无备忘录</div>
        </div>
      </el-card>

      <!-- 右：进度列表 -->
      <el-card shadow="never" class="memo-progress-card">
        <template #header>
          <div style="display:flex;justify-content:space-between;align-items:center">
            <span style="font-weight:600">
              {{ currentMemos.find((m: any) => m.id === activeMemoId)?.title || '进度记录' }}
            </span>
            <span v-if="activeMemoId">
              <el-button size="small" text type="primary" @click="openEdit(currentMemos.find((m: any) => m.id === activeMemoId))">编辑标题</el-button>
              <el-button size="small" text :type="currentMemos.find((m: any) => m.id === activeMemoId)?.status === MemoStatus.OPEN ? 'warning' : 'success'" @click="toggleStatus(currentMemos.find((m: any) => m.id === activeMemoId))">
                {{ currentMemos.find((m: any) => m.id === activeMemoId)?.status === MemoStatus.OPEN ? '关闭' : '重开' }}
              </el-button>
            </span>
          </div>
        </template>

        <div v-if="activeMemoId" v-loading="progressLoading" class="progress-area">
          <div v-if="progressList.length === 0" style="text-align:center;color:var(--app-text-secondary);padding:24px">暂无进度记录</div>
          <el-timeline v-else>
            <el-timeline-item
              v-for="p in progressList" :key="p.id"
              :timestamp="p.createTime ? p.createTime.replace('T', ' ').slice(0, 16) : ''"
              placement="top"
            >
              <div class="progress-item">
                <span class="progress-content">{{ p.content }}</span>
                <span class="progress-actions">
                  <el-button size="small" link type="primary" @click="openEditProgress(p)">编辑</el-button>
                  <el-button size="small" link type="danger" @click="handleDeleteProgress(p)">删除</el-button>
                </span>
              </div>
            </el-timeline-item>
          </el-timeline>

          <div class="progress-input">
            <el-input v-model="newProgress" type="textarea" :rows="2" placeholder="记录一条进度..." />
            <el-button type="primary" :loading="addingProgress" style="margin-top:8px" @click="handleAddProgress">新增进度</el-button>
          </div>
        </div>
        <div v-else style="text-align:center;color:var(--app-text-secondary);padding:24px">请选择左侧备忘录</div>
      </el-card>
    </div>

    <!-- 新增/编辑备忘录弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="420px">
      <el-form :model="form" label-width="60px">
        <el-form-item label="标题" required>
          <el-input v-model="form.title" placeholder="备忘录标题" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSaveMemo">确定</el-button>
      </template>
    </el-dialog>

    <!-- 编辑进度弹窗 -->
    <el-dialog v-model="editProgressVisible" title="编辑进度" width="460px">
      <el-input v-model="editProgressForm.content" type="textarea" :rows="3" placeholder="进度内容" />
      <template #footer>
        <el-button @click="editProgressVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveProgress">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.memo-page { display: flex; flex-direction: column; gap: 12px; }
.memo-body { display: flex; gap: 12px; align-items: flex-start; }
.memo-list-card { width: 320px; flex-shrink: 0; }
.memo-progress-card { flex: 1; }
.memo-list { max-height: 480px; overflow-y: auto; }
.memo-item { padding: 10px 12px; border-radius: 6px; cursor: pointer; margin-bottom: 6px; background: #f7f8fa; }
.memo-item:hover { background: #ecf5ff; }
.memo-item.active { background: #d9ecff; }
.memo-item-title { font-weight: 600; color: var(--app-text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.memo-item-meta { display: flex; align-items: center; gap: 8px; margin-top: 6px; }
.memo-item-time { font-size: var(--app-font-xs); color: var(--app-text-secondary); }
.progress-area { padding: 0 4px; }
.progress-item { display: flex; justify-content: space-between; align-items: flex-start; gap: 8px; }
.progress-content { flex: 1; color: var(--app-text-primary); word-break: break-all; }
.progress-actions { flex-shrink: 0; }
.progress-input { margin-top: 16px; border-top: 1px dashed #e4e7ed; padding-top: 12px; }
</style>
