<script setup lang="ts">
import { ref, reactive, computed, onMounted, onActivated, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import request from '@/utils/request'
import { DocStatusLabel, DocStatusTag } from '@/api/common'
import { type SaleOrder } from '@/api/sale'
import { getCustomer, createCustomer, updateCustomer, getCustomerSaleOrders, type Customer } from '@/api/customer'
import PageShell from '@/components/PageShell.vue'
import { useUnsavedGuard } from '@/composables/usePageBack'
import { useTabStore } from '@/stores/tabs'

const route = useRoute(); const router = useRouter()

/** 新增（/inventory/customer/add）与详情编辑（/inventory/customer/detail/:id）共用本页 */
const id = computed(() => (route.params.id != null && route.params.id !== '' ? Number(route.params.id) : undefined))
const isNew = computed(() => id.value == null)

const loading = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()

const defaultForm = (): Customer => ({
  id: undefined,
  code: '',
  name: '',
  contact: '',
  phone: '',
  address: '',
  creditPeriod: 0,
  creditPeriodMonths: 0,
  creditLimit: 0,
  status: 1,
  remark: ''
})

const form = reactive<Customer>(defaultForm())
/**
 * 未保存拦截（2026-09-23 统一模板）：本页新增/详情编辑共用，可就地改客户信息并保存 ⇒ 属"能改数据"，接守卫。
 * ⚠️ 必须写在 form 等状态**之后**（watch 注册时立即求值，放前面会 TDZ 静默失效）。
 */
const { takeBaseline, markClean } = useUnsavedGuard(() => ({ form }))
const tabStore = useTabStore()

const rules: FormRules = {
  name: [{ required: true, message: '请输入客户名称', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

const statusOptions = [
  { label: '合作中', value: 1 },
  { label: '已停用', value: 0 }
]

const warehouses = ref<any[]>([])
const orders = ref<SaleOrder[]>([])
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })

function fmt(v?: number) { return v == null ? '0.00' : Number(v).toFixed(2) }
function docStatusType(s?: string) { return DocStatusTag[s || ''] || '' }
function warehouseName(wid?: number) { const w = warehouses.value.find(x => x.id === wid); return w ? w.warehouseName : '' }

async function loadWarehouses() {
  try {
    const r: any = await request.get('/warehouse/page', { params: { pageSize: 500, warehouseName: '' } })
    warehouses.value = r?.records || []
  } catch { warehouses.value = [] }
}

/** 已加载的数据标识（新增/详情切换或换客户时重新拉取，避免残留上一客户数据） */
const loadedKey = ref('')

async function init() {
  const key = id.value != null ? `detail-${id.value}` : 'add'
  if (loadedKey.value === key) return
  loadedKey.value = key

  Object.assign(form, defaultForm())
  orders.value = []
  pagination.total = 0
  pagination.pageNum = 1

  if (id.value == null) return

  loading.value = true
  try {
    const c = await getCustomer(id.value)
    Object.assign(form, defaultForm(), c || {})
    await loadOrders()
  } finally { loading.value = false }
}

async function loadOrders() {
  // 期 2（2026-09-19 读隔离）：改走客户页自身前缀 /inventory/customer/{id}/sale-orders
  // （原先读 /inventory/sale/page 需 sale:order ⇒ 只有客户档案权限的用户会 403）
  const res = await getCustomerSaleOrders(id.value as number, {
    pageNum: pagination.pageNum,
    pageSize: pagination.pageSize
  })
  orders.value = res?.records || []
  pagination.total = res?.total || 0
}

function handleSizeChange(val: number) { pagination.pageSize = val; pagination.pageNum = 1; loadOrders() }
function handleCurrentChange(val: number) { pagination.pageNum = val; loadOrders() }

async function handleSave() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      if (form.id) {
        await updateCustomer({ ...form })
        ElMessage.success('保存成功')
      } else {
        await createCustomer({ ...form })
        ElMessage.success('新增成功')
      }
      // 提交成功 ⇒ 先清脏标记（否则离开会被未保存确认拦住），再关掉本页签并回列表
      markClean()
      tabStore.closeTabAndBack(route.path)
      router.push('/inventory/customer')
    } catch (e: any) {
      ElMessage.error(e?.message || '保存失败')
    } finally { saving.value = false }
  })
}

function handleCancel() { router.push('/inventory/customer') }
function goOrderDetail(row: SaleOrder) { router.push('/inventory/sale/detail/' + row.id) }

onMounted(async () => { loadWarehouses(); await init(); takeBaseline() })
// keep-alive 缓存下再次进入会复用组件；新增/详情路由切换也需重新加载
onActivated(async () => { await init(); takeBaseline() })
watch(() => route.fullPath, async () => { await init(); takeBaseline() })
</script>

<template>
  <!-- 统一骨架（2026-09-23 全站定稿口径）：标题交骨架（新增/详情动态）、返回交骨架、保存上移页头 -->
  <PageShell :loading="loading" :title="isNew ? '新增客户' : `客户详情 — ${form.name}`" back-fallback="/inventory/customer">
    <template #actions>
      <el-button type="primary" size="small" :loading="saving" @click="handleSave">保存</el-button>
    </template>

    <el-card shadow="never">
      <!-- 卡片页头已删除：标题交骨架；保存上移 #actions；原「取消」= 放弃并回列表，已并入骨架返回 -->


      <el-form ref="formRef" :model="form" :rules="rules" label-width="var(--app-label-width)" size="small">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="客户编码">
              <!-- 编码由系统自动生成，不可修改 -->
              <el-input v-model="form.code" disabled :placeholder="form.id ? '' : '保存后自动生成'" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="客户名称" prop="name">
              <el-input v-model="form.name" placeholder="请输入客户名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系人">
              <el-input v-model="form.contact" placeholder="联系人" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话">
              <el-input v-model="form.phone" placeholder="联系电话" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="账期(月)">
              <el-input-number v-model="form.creditPeriodMonths" :min="0" :max="24" controls-position="right" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="账期(天)">
              <el-input-number v-model="form.creditPeriod" :min="0" :max="31" controls-position="right" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="信用额度">
              <el-input-number v-model="form.creditLimit" :min="0" :precision="2" controls-position="right" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-select v-model="form.status" placeholder="请选择状态" style="width:100%">
                <el-option v-for="o in statusOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <!-- 2026-09-15 用户要求：删除「应收余额」（列表列与详情项一并下线）——
               该值原先只由列表接口 page() 回填、详情接口 getById 并不回填，故详情页恒显示 0.00 -->

          <el-col :span="24">
            <el-form-item label="地址">
              <el-input v-model="form.address" placeholder="地址" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="备注" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </el-card>

    <el-card v-if="!isNew" shadow="never">
      <template #header><span style="font-weight:600">销售记录</span></template>
      <el-table :data="orders" border stripe size="small" @row-click="goOrderDetail">
        <el-table-column prop="orderDate" label="订单日期" width="110" align="center" />
        <el-table-column prop="code" label="单号" min-width="150" />
        <el-table-column label="出库仓库" min-width="120">
          <template #default="{ row }">{{ warehouseName(row.warehouseId) || '—' }}</template>
        </el-table-column>
        <el-table-column prop="totalAmount" label="总金额" width="120" align="right">
          <template #default="{ row }">{{ fmt(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="docStatusType(String(row.status))">{{ DocStatusLabel[String(row.status)] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center">
          <template #default="{ row }">
            <el-button type="primary" link @click.stop="goOrderDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="display:flex;justify-content:flex-end;margin-top:12px">
        <el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50]" :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper" background
          @size-change="handleSizeChange" @current-change="handleCurrentChange" />
      </div>
    </el-card>

  </PageShell>
</template>

<style scoped>
/* 页头/根容器已统一到全局骨架（PageShell + styles/page.css）；原 .detail-page 局部样式已删除 */
</style>
