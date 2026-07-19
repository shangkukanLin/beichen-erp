<script setup lang="ts">
defineOptions({ name: 'ContractTemplate' })

import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getTemplateList, createTemplate, updateTemplate, deleteTemplate, setDefaultTemplate } from '@/api/contract-template'
import { QuillEditor } from '@vueup/vue-quill'
import '@vueup/vue-quill/dist/vue-quill.snow.css'

const router = useRouter()
const list = ref<any[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref<number>()

const form = ref({ templateName: '', content: '', status: 1, partyAAddress: '', partyAContact: '', partyAPhone: '' })
const quillRef = ref<InstanceType<typeof QuillEditor>>()

const activeType = ref('加工合同')

const PROCESSING_PLACEHOLDERS = [
  { label: '合同信息', key: '{合同信息}', color: '#eb2f96' },
  { label: '甲方', key: '{甲方}', color: '#1890ff' },
  { label: '乙方', key: '{乙方}', color: '#52c41a' },
  { label: '甲方地址', key: '{甲方地址}', color: '#fa8c16' },
  { label: '甲方联系人', key: '{甲方联系人}', color: '#722ed1' },
  { label: '甲方电话', key: '{甲方电话}', color: '#f5222d' },
  { label: '乙方地址', key: '{乙方地址}', color: '#13c2c2' },
  { label: '乙方联系人', key: '{乙方联系人}', color: '#2f54eb' },
  { label: '乙方电话', key: '{乙方电话}', color: '#fa541c' },
  { label: '加工单号', key: '{加工单号}', color: '#eb2f96' },
  { label: '日期', key: '{日期}', color: '#722ed1' },
  { label: '产品表格', key: '{产品表格}', color: '#ff4d4f' },
  { label: '物料表格', key: '{物料表格}', color: '#13c2c2' },
  { label: '备注', key: '{备注}', color: '#f5222d' },
  { label: '签名区', key: '{签名区}', color: '#eb2f96' },
]

const PURCHASE_PLACEHOLDERS = [
  { label: '合同信息', key: '{合同信息}', color: '#eb2f96' },
  { label: '甲方', key: '{甲方}', color: '#1890ff' },
  { label: '乙方', key: '{乙方}', color: '#52c41a' },
  { label: '甲方地址', key: '{甲方地址}', color: '#fa8c16' },
  { label: '甲方联系人', key: '{甲方联系人}', color: '#722ed1' },
  { label: '甲方电话', key: '{甲方电话}', color: '#f5222d' },
  { label: '乙方地址', key: '{乙方地址}', color: '#13c2c2' },
  { label: '乙方联系人', key: '{乙方联系人}', color: '#2f54eb' },
  { label: '乙方电话', key: '{乙方电话}', color: '#fa541c' },
  { label: '订单号', key: '{订单号}', color: '#eb2f96' },
  { label: '日期', key: '{日期}', color: '#722ed1' },
  { label: '物料明细表格', key: '{物料明细表格}', color: '#ff4d4f' },
  { label: '组件表格', key: '{组件表格}', color: '#eb2f96' },
  { label: '备注', key: '{备注}', color: '#f5222d' },
  { label: '签名区', key: '{签名区}', color: '#eb2f96' },
]

function insertPlaceholder(key: string) {
  const quill = (quillRef.value as any)?.getQuill?.()
  if (!quill) return
  const range = quill.getSelection ? quill.getSelection(true) : null
  const idx = range ? range.index : quill.getLength() - 1
  quill.insertText(idx, key, { bold: true, color: '#1890ff', background: '#e6f7ff' })
  quill.setSelection(idx + key.length)
}

function insertSignatureBlock() {
  const quill = (quillRef.value as any)?.getQuill?.()
  if (!quill) return
  const range = quill.getSelection ? quill.getSelection(true) : null
  const idx = range ? range.index : quill.getLength() - 1
  quill.insertText(idx, '{签名区}', { bold: true, color: '#eb2f96', background: '#fff0f6' })
  quill.setSelection(idx + 5)
  ElMessage.success('签名区占位已插入（导出PDF时自动生成签名表格）')
}

function onTypeChange() { loadData() }
async function loadData() {
  loading.value = true
  try { list.value = await getTemplateList(activeType.value) || [] } catch (e: any) { console.warn('加载模板列表失败', e?.message || e) }
  finally { loading.value = false }
}

function openAdd() {
  isEdit.value = false; editId.value = undefined
  form.value = { templateName: '', content: defaultTemplate(), status: 1, partyAAddress: '', partyAContact: '', partyAPhone: '', templateType: activeType.value }
  dialogVisible.value = true
}

function openEdit(row: any) {
  isEdit.value = true; editId.value = row.id
  form.value = { templateName: row.templateName, content: row.content || '', status: row.status, partyAAddress: row.partyAAddress || '', partyAContact: row.partyAContact || '', partyAPhone: row.partyAPhone || '', templateType: row.templateType || activeType.value }
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!form.value.templateName.trim()) { ElMessage.warning('请输入模板名称'); return }
  // 从 Quill 获取最新 HTML
  const quill = (quillRef.value as any)?.getQuill?.()
  if (quill) {
    const html = quill.root?.innerHTML || ''
    if (!html || html === '<p><br></p>') { ElMessage.warning('请输入合同内容'); return }
    form.value.content = html
  }
  try {
    if (isEdit.value && editId.value) {
      await updateTemplate(editId.value, form.value)
      ElMessage.success('保存成功')
    } else {
      await createTemplate(form.value)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadData()
  } catch (e: any) { ElMessage.error('保存失败: ' + (e?.message || '未知错误')) }
}

async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm('确定删除该模板吗？', '删除模板', { type: 'warning' })
    await deleteTemplate(row.id)
    ElMessage.success('已删除')
    loadData()
  } catch (e: any) { if (e !== 'cancel' && e !== 'close') { console.error(e) } }
}

async function handleSetDefault(row: any) {
  try {
    await setDefaultTemplate(row.id)
    ElMessage.success(`已将"${row.templateName}"设为当前生效模板`)
    loadData()
  } catch (e: any) { ElMessage.error('设置失败: ' + (e?.message || '未知错误')) }
}

const defaultTemplate = () => activeType.value === '采购合同'
  ? `<h1 style="text-align: center;">物料采购合同</h1><p><br></p><p><span style="color: #eb2f96; background-color: #fff0f6;"><strong>{合同信息}</strong></span></p><p><br></p><p>就甲方向乙方采购本协议中所列明的物料事宜，经双方友好协商共同达成并签署以下条款：</p><p><br></p><h3>一、采购物料明细</h3><p><span style="color: #ff4d4f; background-color: #fff1f0;"><strong>{物料明细表格}</strong></span></p><p><br></p><h3>二、订单备注</h3><p><span style="color: #f5222d; background-color: #fff1f0;"><strong>{备注}</strong></span></p><p><br></p><p><span style="color: #eb2f96; background-color: #fff0f6;"><strong>{签名区}</strong></span></p>`
  : `<h1 style="text-align: center;">委外加工合同</h1><p><br></p><p><span style="color: #eb2f96; background-color: #fff0f6;"><strong>{合同信息}</strong></span></p><p><br></p><p>就甲方委托乙方为其生产加工本协议中所列明的产品事宜，经双方友好协商共同达成并签署以下条款：</p><p><br></p><h3>一、委托加工产品数量及价格</h3><p><span style="color: #eb2f96; background-color: #fff0f6;"><strong>{产品表格}</strong></span></p><p><br></p><h3>二、甲方提供物料明细</h3><p><span style="color: #13c2c2; background-color: #e6fffb;"><strong>{物料表格}</strong></span></p><p><br></p><h3>三、订单备注</h3><p><span style="color: #f5222d; background-color: #fff1f0;"><strong>{备注}</strong></span></p><p><br></p><p><span style="color: #eb2f96; background-color: #fff0f6;"><strong>{签名区}</strong></span></p>`

onMounted(() => loadData())
</script>

<template>
  <div class="template-page">
    <div class="page-header">
      <span class="page-title">合同模板设置</span>
    </div>

    <el-tabs v-model="activeType" @tab-change="onTypeChange">
      <el-tab-pane label="加工合同" name="加工合同" />
      <el-tab-pane label="采购合同" name="采购合同" />
    </el-tabs>

    <el-card shadow="never">
      <div style="margin-bottom:12px"><el-button type="primary" @click="openAdd">新增模板</el-button></div>
      <el-table :data="list" border stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="templateName" label="模板名称" min-width="200" />
        <el-table-column label="当前生效" width="90" align="center">
          <template #default="{row}">
            <el-tag v-if="row.isDefault===1" type="success">使用中</el-tag>
            <span v-else style="color:#c0c4cc">—</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{row}"><el-tag :type="row.status===1?'success':'danger'">{{ row.status===1?'启用':'停用' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="center">
          <template #default="{row}">
            <el-button type="primary" link @click="openEdit(row)">编辑</el-button>
            <el-button v-if="row.isDefault!==1" type="success" link @click="handleSetDefault(row)">设为默认</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit?'编辑模板':'新增模板'" width="960px" top="2vh" :close-on-click-modal="false">
      <el-form :model="form" label-width="80px">
        <el-form-item label="模板名称"><el-input v-model="form.templateName" placeholder="请输入模板名称" /></el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </el-form-item>

        <!-- 甲方信息 -->
        <el-divider content-position="left" style="margin:8px 0">甲方（我方）信息 — 导出合同时填入 {甲方地址} {甲方联系人} {甲方电话}</el-divider>
        <el-form-item label="联系地址"><el-input v-model="form.partyAAddress" placeholder="甲方联系地址" /></el-form-item>
        <el-form-item label="联系人"><el-input v-model="form.partyAContact" placeholder="甲方联系人" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="form.partyAPhone" placeholder="甲方联系电话" /></el-form-item>

        <el-form-item label="合同内容">
          <div style="margin-bottom:8px;display:flex;align-items:center;gap:4px;flex-wrap:wrap">
            <span style="font-size:12px;color:#606266;margin-right:4px;font-weight:500">插入变量：</span>
            <el-button v-for="ph in (activeType==='采购合同' ? PURCHASE_PLACEHOLDERS : PROCESSING_PLACEHOLDERS)" :key="ph.key" :style="{ background: ph.color + '15', color: ph.color, borderColor: ph.color + '40' }" @click="insertPlaceholder(ph.key)">{{ ph.label }}</el-button>
            <span style="margin:0 8px;color:#dcdfe6">|</span>
            <el-button type="warning" @click="insertSignatureBlock">插入签名区</el-button>
          </div>
          <div class="quill-wrapper">
            <QuillEditor
              ref="quillRef"
              v-model:content="form.content"
              contentType="html"
              theme="snow"
              :toolbar="[
                [{ header: [1, 2, 3, false] }],
                ['bold', 'italic', 'underline', { color: [] }, { background: [] }],
                [{ align: [] }, { list: 'ordered' }, { list: 'bullet' }],
                ['clean']
              ]"
              style="height:400px"
            />
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">{{ isEdit ? '保存' : '创建' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.template-page { padding: 16px; }
.page-header { display: flex; align-items: center; gap: 16px; margin-bottom: 16px; }
.page-title { font-size: 20px; font-weight: 600; }
.quill-wrapper { margin-top:4px; }
</style>
