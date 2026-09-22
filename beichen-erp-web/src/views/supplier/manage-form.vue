<script setup lang="ts">
// 往来单位（供应商 / 供货商）新增·编辑（2026-09-23 用户要求：原 640px 弹框改为独立页面）
// —— 与列表页 supplier/manage.vue 同一套双模式口径：路径以 /outsource/ 开头 = 供货商（成品商），
//    否则 = 供应商（方案商/加工厂/辅料商）。新增与编辑共用一个组件（同 finance/payable-transfer 先例）。
import { reactive, ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { getSupplier } from '@/api/system'
import { TYPE_TABS, TYPE_OPTIONS, TYPE_MAP } from '@/constants/supplier'

const route = useRoute()
const router = useRouter()

const isVendor = computed(() => route.path.startsWith('/outsource/'))
const isEdit = computed(() => route.path.includes('/edit/'))
const listPath = computed(() => (isVendor.value ? '/outsource/supplier/manage' : '/supplier/manage'))
const pageTitle = computed(() => (isVendor.value ? '供货商' : '供应商'))
/** 类型可选项：供货商只有「成品商」，供应商排除「成品商」 */
const TYPE_OPTIONS_CUSTOM = isVendor.value
  ? [{ name: 'product', label: TYPE_MAP.product }]
  : TYPE_OPTIONS.filter(t => t.name !== 'product')

const loading = ref(false)
const saving = ref(false)
const form = reactive({
  id: undefined as any, code: '', name: '', supplySku: '', contact: '', phone: '', address: '', remark: '',
  checkedTypes: [] as string[], status: 1,
  creditPeriodMonths: undefined as any, creditPeriod: undefined as any
})

async function load() {
  if (!isEdit.value) {
    form.checkedTypes = isVendor.value ? ['product'] : []
    return
  }
  loading.value = true
  try {
    const r: any = await getSupplier(route.params.id as string)
    Object.assign(form, {
      id: r?.id, code: r?.code || '', name: r?.name || '', supplySku: r?.supplySku || '',
      contact: r?.contact || '', phone: r?.phone || '', address: r?.address || '', remark: r?.remark || '',
      checkedTypes: r?.typeCodes || [], status: r?.status ?? 1,
      creditPeriodMonths: r?.creditPeriodMonths, creditPeriod: r?.creditPeriod
    })
  } catch { /* 提示由拦截器统一给出 */ } finally { loading.value = false }
}

async function handleSubmit() {
  if (!form.name) { ElMessage.warning('请输入名称'); return }
  if (form.checkedTypes.length === 0) { ElMessage.warning('请选择至少一个类型'); return }
  // 新增时同名提示：供应商与供货商允许重名（各自独立建档），但需用户确认避免误录
  if (!isEdit.value) {
    let dupInfo = ''
    try {
      const dup = await request.get<any, any>('/supplier/page', { params: { name: form.name, pageSize: 5 } })
      const list = dup?.records || []
      if (list.length > 0) {
        dupInfo = list.map((s: any) => `${s.name}（${(s.typeCodes || []).map((t: string) => TYPE_MAP[t] || t).join('/')}）`).join('、')
      }
    } catch { /* 查重失败不阻塞创建 */ }
    if (dupInfo) {
      try {
        await ElMessageBox.confirm(`已存在同名往来单位：${dupInfo}。同名将创建为各自独立的往来单位，确认继续新增吗？`, '存在同名', { type: 'warning', confirmButtonText: '仍要新增', cancelButtonText: '取消' })
      } catch { return }
    }
  }
  saving.value = true
  try {
    const body: any = { ...form, typeCodes: form.checkedTypes }
    // 「供货SKU」只属于供货商（类型=成品商）⇒ 供应商提交时显式置空，避免残留旧前缀
    if (!isVendor.value) body.supplySku = ''
    if (isEdit.value) { await request.put('/supplier', body); ElMessage.success('已更新') }
    else { await request.post('/supplier', body); ElMessage.success('已添加') }
    router.push(listPath.value)
  } catch (e: any) { ElMessage.error(e?.message || '保存失败') } finally { saving.value = false }
}
onMounted(load)
</script>

<template>
  <div class="p" v-loading="loading">
    <el-card shadow="never">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span style="font-weight:600">{{ (isEdit ? '编辑' : '新增') + pageTitle }}</span>
          <el-button @click="router.push(listPath)">返回</el-button>
        </div>
      </template>

      <!-- 2026-09-23 用户要求：表单**左右两列**摆放（原先是一行一行往下排）。通栏放"类型/供货SKU/地址/备注"，
           短字段两两同行；列宽：编码|名称、联系人|联系电话、状态|账期。 -->
      <el-form :model="form" label-width="90px" size="small" style="max-width:900px">
        <el-row :gutter="16">
          <el-col :span="24">
            <el-form-item label="类型" required>
              <el-checkbox-group v-model="form.checkedTypes">
                <el-checkbox v-for="t in TYPE_OPTIONS_CUSTOM" :key="t.name" :label="t.name" :value="t.name">{{ t.label }}</el-checkbox>
              </el-checkbox-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="pageTitle + '编码'">
              <!-- 唯一编码由系统按类型前缀自动生成（如 GYS-20260831-001），不可修改 -->
              <el-input v-model="form.code" disabled :placeholder="isEdit ? '' : '保存后自动生成'" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="pageTitle + '名称'" required><el-input v-model="form.name" /></el-form-item>
          </el-col>
          <!-- 供货SKU —— 只属于「供货商」（类型=成品商）；供应商不显示，后端也会忽略该类型传来的值 -->
          <el-col v-if="isVendor" :span="24">
            <el-form-item label="供货SKU">
              <el-input v-model="form.supplySku" maxlength="24" clearable placeholder="如 ABC（留空则产品走默认 SKU-）" />
              <div style="font-size:var(--app-font-xs);color:var(--app-text-secondary);line-height:1.4">
                该供货商的产品 SKU 以此打头（ABC → ABC-000001）；1-24 位字母/数字/短横线，保存时自动转大写
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系人"><el-input v-model="form.contact" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话"><el-input v-model="form.phone" /></el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="地址"><el-input v-model="form.address" type="textarea" :rows="2" /></el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="状态"><el-select v-model="form.status" style="width:100%"><el-option label="合作中" :value="1" /><el-option label="已停用" :value="0" /></el-select></el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item label="账期">
              <div style="display:flex;align-items:center;gap:6px;flex-wrap:wrap">
                <el-input-number v-model="form.creditPeriodMonths" :min="0" :max="24" placeholder="月" controls-position="right" style="width:90px" /><span>个月</span>
                <el-input-number v-model="form.creditPeriod" :min="0" :max="31" placeholder="天" controls-position="right" style="width:90px" /><span>天</span>
                <span style="color:var(--app-text-secondary);font-size:var(--app-font-xs)">（收货/交货后多少天付款，默认当天）</span>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <div style="display:flex;gap:8px;justify-content:flex-end;max-width:900px">
        <el-button @click="router.push(listPath)">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSubmit">确定</el-button>
      </div>
    </el-card>
  </div>
</template>

<style scoped>.p{display:flex;flex-direction:column;gap:12px}</style>
