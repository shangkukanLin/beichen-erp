<script setup lang="ts">
defineOptions({ name: 'ContractTemplate' })

import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getTemplateList, createTemplate, updateTemplate, deleteTemplate, setDefaultTemplate, type ContractTemplate } from '@/api/contract-template'
import { QuillEditor } from '@vueup/vue-quill'
import '@vueup/vue-quill/dist/vue-quill.snow.css'

const list = ref<any[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref<number>()

const form = ref<ContractTemplate>({ templateName: '', content: '', status: 1, templateType: '' })
const quillRef = ref<InstanceType<typeof QuillEditor>>()

const activeType = ref('加工合同')

function onTypeChange() { loadData() }
async function loadData() {
  loading.value = true
  try { list.value = await getTemplateList(activeType.value) || [] } catch (e: any) { console.warn('加载模板列表失败', e?.message || e) }
  finally { loading.value = false }
}

function openAdd() {
  isEdit.value = false; editId.value = undefined
  form.value = { templateName: '', content: defaultClauses(), status: 1, templateType: activeType.value }
  dialogVisible.value = true
}

function openEdit(row: any) {
  isEdit.value = true; editId.value = row.id
  form.value = { templateName: row.templateName, content: row.content || '', status: row.status, templateType: row.templateType || activeType.value }
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!form.value.templateName.trim()) { ElMessage.warning('请输入模板名称'); return }
  // 从 Quill 获取最新 HTML
  const quill = (quillRef.value as any)?.getQuill?.()
  if (quill) {
    const html = quill.root?.innerHTML || ''
    if (!html || html === '<p><br></p>') { ElMessage.warning('请输入条款内容'); return }
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

const defaultClauses = () => activeType.value === '采购合同'
  ? `<h1 style="text-align: center;">物料采购合同</h1><p><br></p><p>就甲方向乙方采购本协议所列物料事宜，经双方友好协商，达成如下条款：</p><p><br></p><p>1、订单一经确认回传即刻具备法律效力。</p><p>2、订单确认后，应如期交货，如有延迟必须告知甲方，获取甲方同意。</p><p>3、乙方需按照甲方订单需求的产品型号，规格及数量提供质量合格的产品。</p><p>4、如因乙方私自调整产品的材料或工艺等原因导致的产品质量问题，所产生的一切损失由乙方承担。</p><p>5、双方遵守保密原则，双方合作各项细节需做好保密措施，未经允许不得外泄。</p>`
  : `<h1 style="text-align: center;">委外加工合同</h1><p><br></p><p>就甲方委托乙方加工生产本协议所列产品事宜，经双方友好协商，达成如下条款：</p><p><br></p><h3>订单备注</h3><p>1、订单一经确认回传即刻具备法律效力。</p><p>2、乙方需按照甲方订单要求的产品型号，规格及数量加工质量合格的产品。</p><p>3、乙方收到甲方物料后需两天内确认好实际到货数量与订单数量是否相符，如有偏差应立刻向甲方反馈，超过两天未提出异议则默认到货数量无误。</p><p>4、乙方应妥善保管相关物料，如有损坏，丢失，则由乙方照价赔偿。</p><p>5、乙方收到物料之日起，7个工作日内交货，交货后3个工作日内结单。</p><p>6、乙方不得随意改变生产工艺及配套辅料。如需调整工艺或配套辅料，应先打样由甲方确认，样品通过甲方验证后方可调整，否则产生的一切损失由乙方承担。</p><p>7、全新物料加工良率保98%以上（含贴片，绑定，贴合总成等全段工序）；旧物料加工良率原则上保96%以上，如发生良率超标时，由乙方照价赔偿。如遇特殊项目则以双方协商良率为准。</p><p>8、双方合作的新项目及新批次物料，乙方须先做小批量由甲方验证以后方可量产。</p><p>9、双方遵守保密原则，双方的所有资料（含商业资料和技术资料）均做好保密措施，未经允许不得外泄。</p>`

onMounted(() => loadData())
</script>

<template>
  <div class="template-page">
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
            <span v-else style="color:var(--app-text-placeholder)">—</span>
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

        <el-alert type="info" :closable="false" show-icon style="margin-bottom:8px"
          title="导出合同时，标题、甲乙双方信息、明细表格、签名区由系统自动生成，您只需编辑以下「条款」内容。" />

        <el-form-item label="合同条款">
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

.quill-wrapper { margin-top:4px; }
</style>
