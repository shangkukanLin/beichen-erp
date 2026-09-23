<script setup lang="ts">
// 供应商（分类型页）新增·编辑（2026-09-23 用户要求：原 700px 弹框改为独立页面）
// —— 列表页按路径区分类型（/supplier/solution|factory|product|material-supplier），
//    独立页通过 `?type=` 带上同一类型（刷新/直链都不丢），新增时用它预置 typeCodes。
import { reactive, ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { getSupplier, addSupplier, updateSupplier, type SupplierDTO } from '@/api/system'
import PageShell from '@/components/PageShell.vue'
import { useUnsavedGuard } from '@/composables/usePageBack'
import { useTabStore } from '@/stores/tabs'

const route = useRoute()
const router = useRouter()

const typeMap: Record<string, { title: string; type: string }> = {
  solution: { title: '方案商', type: 'solution' },
  factory: { title: '委外加工厂', type: 'factory' },
  product: { title: '成品供应商', type: 'product' },
  material: { title: '辅料商', type: 'material' }
}
const typeCode = computed(() => String(route.query.type || 'solution'))
const pageTitle = computed(() => typeMap[typeCode.value]?.title || '供应商')
const isEdit = computed(() => route.path.includes('/edit/'))
const listPath = computed(() => {
  const back: Record<string, string> = { solution: '/supplier/solution', factory: '/supplier/factory', product: '/supplier/product', material: '/supplier/material-supplier' }
  return back[typeCode.value] || '/supplier/solution'
})

const loading = ref(false)
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<SupplierDTO>({
  code: '', name: '', supplierType: '', typeCodes: [typeCode.value], status: 1,
  contact: '', phone: '', address: '', remark: '',
  relatedSupplierId: undefined,
  creditPeriodMonths: undefined, creditPeriod: undefined
})
const rules: FormRules = { name: [{ required: true, message: '请输入供应商名称', trigger: 'blur' }] }
const tabStore = useTabStore()
/**
 * 未保存拦截（2026-09-23 统一模板）
 * ⚠️ 必须写在 form 等状态**之后**（watch 注册时立即求值，放前面会 TDZ 静默失效）。
 */
const { takeBaseline, markClean } = useUnsavedGuard(() => ({ form }))

async function load() {
  if (!isEdit.value) return
  loading.value = true
  try {
    const r: any = await getSupplier(route.params.id as string)
    Object.assign(form, {
      id: r?.id, code: r?.code || '', name: r?.name || '', supplierType: r?.supplierType || '',
      typeCodes: r?.typeCodes || [], status: r?.status ?? 1,
      contact: r?.contact || '', phone: r?.phone || '', address: r?.address || '', remark: r?.remark || '',
      creditPeriodMonths: r?.creditPeriodMonths, creditPeriod: r?.creditPeriod
    })
  } catch { /* 提示由拦截器统一给出 */ } finally { loading.value = false }
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      // 编辑时**不再**用当前页签类型覆盖 typeCodes（F7-153：原实现会把"多类型"供应商静默改成单一类型）；
      // 新增时仍按当前类型预置。
      if (!isEdit.value) form.typeCodes = [typeCode.value]
      if (isEdit.value && form.id) await updateSupplier(form)
      else { await addSupplier(form); ElMessage.success('已添加') }
      // 提交成功 ⇒ 先清脏标记（否则离开会被未保存确认拦住），再关掉本页签并回列表
      markClean()
      tabStore.closeTabAndBack(route.path)
      router.push(listPath.value)
    } catch (e: any) { ElMessage.error(e?.message || '保存失败') } finally { submitLoading.value = false }
  })
}
onMounted(async () => { await load(); takeBaseline() })
</script>

<template>
  <!-- 统一骨架（2026-09-23 全站定稿口径）：页头左端「← 返回」→ 标题 → 右端操作（确定） -->
  <PageShell :title="(isEdit ? '编辑' : '新增') + pageTitle" :back-fallback="listPath" :loading="loading">
    <template #actions>
      <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
    </template>

    <el-card shadow="never">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <!-- 页内标题不再重复：由骨架统一显示（PageShell 取 title） -->
        </div>
      </template>

      <!-- label-width 110px：标签带模块前缀（供应商名称/供应商编码 5 字）+ 必填星号，100px 会压成两行 -->
      <el-form ref="formRef" :model="form" :rules="rules" label-width="var(--app-label-width)">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="供应商编码">
              <el-input v-if="isEdit" v-model="form.code" disabled placeholder="自动生成" />
              <el-input v-else disabled placeholder="保存后自动生成" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="供应商名称" prop="name"><el-input v-model="form.name" placeholder="供应商名称" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系人"><el-input v-model="form.contact" placeholder="联系人" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话"><el-input v-model="form.phone" placeholder="手机号" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-select v-model="form.status" style="width:100%">
                <el-option label="合作中" :value="1" />
                <el-option label="已停用" :value="0" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="地址"><el-input v-model="form.address" placeholder="地址" /></el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="账期">
              <div style="display:flex;align-items:center;gap:6px">
                <el-input-number v-model="form.creditPeriodMonths" :min="0" :max="24" placeholder="月" controls-position="right" style="width:90px" /><span>个月</span>
                <el-input-number v-model="form.creditPeriod" :min="0" :max="31" placeholder="天" controls-position="right" style="width:90px" /><span>天</span>
                <span style="color:var(--app-text-secondary);font-size:var(--app-font-xs)">（收货/交货后多少天付款，默认当天）</span>
              </div>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" placeholder="备注" /></el-form-item>
      </el-form>

    </el-card>
  </PageShell>
</template>

<style scoped>/* 页头/根容器已统一到全局骨架（PageShell + styles/page.css）；原 .p 局部样式已删除 */</style>
