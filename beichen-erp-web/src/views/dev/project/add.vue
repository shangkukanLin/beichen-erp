<script setup lang="ts">
// 2026-09-20（F7-159）：显式声明组件名（便于 DevTools 辨认与将来按名 exclude）
defineOptions({ name: 'DevProjectAdd' })
import { localDate } from '@/utils/date'
import { reactive, ref, computed, onMounted, onUnmounted } from 'vue'
import { DEV_PROJECT_DIRTY_KEY, ProductSpec, ProductSpecLabel } from '@/api/enums'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { addProject, checkProjectProductName, type ProjectDTO } from '@/api/system'
import { useTabStore } from '@/stores/tabs'
import { ADD_MARKER } from '@/composables/useSelectWithAdd'
import request from '@/utils/request'
import PageShell from '@/components/PageShell.vue'
import { useUnsavedGuard } from '@/composables/usePageBack'
import { invalidate } from '@/utils/dataFreshness'

const router = useRouter()
const route = useRoute()
const tabStore = useTabStore()

const solutionSupplierOptions = ref<{ id: number; name: string }[]>([])
const factoryOptions = ref<{ id: number; name: string }[]>([])
const saving = ref(false)

const fetchSolutionSuppliers = (kw: string) => request.get('/supplier/page', { params: { pageSize: 500, supplierType: 'solution', name: kw } })
const fetchFactorySuppliers = (kw: string) => request.get('/supplier/page', { params: { pageSize: 500, supplierType: 'factory', name: kw } })

const defForm = (): ProjectDTO => ({
  name: '', displaySupplierName: '', touchSupplierName: '',
  // 2026-09-21：assemblyName 更名为 productName（用户："总成名称修改成产品名称，更贴切业务"，连 DB 列名一起改）
  productName: '',
  // 2026-09-21 新增（需求 1）：产品SKU —— 默认以 NS 打头自动生成、允许修改；仅立项新增时生效
  productSku: '',
  // 2026-09-21 新增（需求 3）：规格（原配/改配），与关联产品的规格联动
  specType: '',
  adaptModel: '', originalSize: '', originalResolution: '',
  originalDriveIc: '', originalTouchIc: '',
  glassSize: '', glassResolution: '',
  configDriveIcId: undefined, configTouchIcId: undefined, configCodeIcId: undefined,
  startDate: localDate(), expectedEndDate: '', remark: '',
  sampleFactoryId: undefined, outsourceFactoryId: undefined,
  brandId: undefined
})

/**
 * 需求 3：规格下拉**只给「原配 / 改配」**两个选项（用户原话"可选择是原配还是改配"），
 * 刻意不含「原装」（那是产品管理侧的完整口径）。
 */
const SPEC_OPTIONS = [
  { value: ProductSpec.MATCHED, label: ProductSpecLabel[ProductSpec.MATCHED] },
  { value: ProductSpec.MODIFIED, label: ProductSpecLabel[ProductSpec.MODIFIED] }
]

/**
 * 需求 4：项目是「原配」时隐藏「显示方案 / 触摸方案 / 改配信息」——
 * 原配即不做改配，这三块对它没有意义。
 * <p>判定用 **`=== 原配`** 而不是 `!== 改配`：规格为空（历史项目 / 尚未选择）时**保持显示**，
 * 否则老项目一进页面字段就凭空消失，会让人以为数据丢了。</p>
 */
const isOriginalSpec = computed(() => form.specType === ProductSpec.MATCHED)

/**
 * 需求 1：立项自动创建产品的 SKU 前缀常量（与后端 `BillPrefix.PRODUCT_SKU_NS` 对应）。
 * 各前缀**独立取号**，故 `NS-000001` 与 `SKU-000001` 可并存。
 */
const PRODUCT_SKU_PREFIX = 'NS'

/**
 * 用户在查重弹窗里选择了「关联该产品」：此时**不会新建产品**（走 linkExistingProductId 分支），
 * 产品SKU 由已有产品决定 ⇒ 前端清空并置灰该输入框，避免用户误以为填的 SKU 生效了。
 */
const linkedExisting = ref(false)

/** 预填产品SKU：默认以 NS 打头自动生成；拉取失败不阻塞（留空则由后端自动生成） */
async function prefillProductSku() {
  try {
    const v = await request.get<any, any>('/product/next-sku', { params: { prefix: PRODUCT_SKU_PREFIX } })
    form.productSku = String(v || '')
  } catch { form.productSku = '' }
}

// 物料类型（驱动IC/触摸IC/码片IC）id，用于改配物料下拉按类型过滤
const materialTypeIdMap = reactive<{ drive?: number; touch?: number; code?: number }>({})
async function loadMaterialTypeIds() {
  try {
    const bt: any = await request.get('/dev/material-type/enabled')
    const list = bt || []
    materialTypeIdMap.drive = list.find((t: any) => t.typeName === '驱动IC')?.id
    materialTypeIdMap.touch = list.find((t: any) => t.typeName === '触摸IC')?.id
    materialTypeIdMap.code = list.find((t: any) => t.typeName === '码片IC')?.id
  } catch { /* 忽略 */ }
}
function fetchMaterialsByType(kw: string, materialTypeId?: number) {
  return request.get('/outsource/material/page', { params: { pageSize: 500, materialName: kw, materialTypeId: materialTypeId || undefined } })
}
const form = reactive<ProjectDTO>(defForm())
/**
 * 未保存拦截（2026-09-23 统一模板）
 * ⚠️ 必须写在 form 等状态**之后**（watch 注册时立即求值，放前面会 TDZ 静默失效）。
 */
const { takeBaseline, markClean } = useUnsavedGuard(() => ({ form }))

function resetForm() {
  Object.assign(form, defForm())
}

const brandOptions = ref<{ id: number; brandName: string }[]>([])
async function loadBrandOptions() {
  try { brandOptions.value = (await request.get('/brand/enabled')) || [] } catch { /* 忽略 */ }
}

async function loadData() {
  const sr: any = await fetchSolutionSuppliers(''); solutionSupplierOptions.value = (sr?.records || []).map((s: any) => ({ id: s.id, name: s.name }))
  const fr: any = await fetchFactorySuppliers(''); factoryOptions.value = (fr?.records || []).map((s: any) => ({ id: s.id, name: s.name }))
  loadBrandOptions()
}

async function handleSubmit() {
  if (!form.name) { ElMessage.warning('请输入项目名称'); return }
  if (!form.productName || !form.productName.trim()) { ElMessage.warning('请输入产品名称'); return }
  // 2026-09-21 新增：规格必填（原配/改配）；产品SKU 必填（进页面已预填 NS- 打头，可改）
  if (!form.specType) { ElMessage.warning('请选择规格（原配/改配）'); return }
  if (!linkedExisting.value && (!form.productSku || !form.productSku.trim())) {
    ElMessage.warning('请输入产品SKU'); return
  }
  // 2026-09-16 用户要求：原机配置（4）+ 改配信息（5）全部必填 —— 与页面上的红色 * 一一对应
  // 2026-09-21（需求 4）：规格=原配时页面隐藏「改配信息」⇒ 这 5 项必填**必须同步跳过**，
  // 否则隐藏的字段永远校验不过、整个表单提交不了。
  const requiredFields: [any, string][] = [
    [form.originalSize, '请填写原机配置-原机尺寸'],
    [form.originalResolution, '请填写原机配置-原分辨率'],
    [form.originalDriveIc, '请填写原机配置-驱动IC'],
    [form.originalTouchIc, '请填写原机配置-触摸IC'],
  ]
  if (!isOriginalSpec.value) {
    requiredFields.push(
      [form.glassSize, '请填写改配信息-玻璃尺寸'],
      [form.glassResolution, '请填写改配信息-玻璃分辨率'],
      [form.configDriveIcId, '请选择改配信息-驱动IC'],
      [form.configTouchIcId, '请选择改配信息-触摸IC'],
      [form.configCodeIcId, '请选择改配信息-码片IC'],
    )
  }
  for (const [val, msg] of requiredFields) {
    if (val === undefined || val === null || String(val).trim() === '') { ElMessage.warning(msg); return }
  }
  const productName = form.productName.trim()
  // 每次提交重新判定"是否关联已有产品"（上一次可能点了关联后又取消整单）
  linkedExisting.value = false
  form.productSku = form.productSku ? form.productSku.trim() : ''

  // 查重：若产品名称与已有产品重名，询问用户关联或修改名称
  let linkExistingProductId: number | undefined = undefined
  try {
    const check = await checkProjectProductName(productName)
    if (check?.exists) {
      try {
        const action = await ElMessageBox.confirm(
          `产品名称「${productName}」已存在同名产品（ID:${check.productId}），是否关联该产品？`,
          '产品重名确认',
          { confirmButtonText: '关联该产品', cancelButtonText: '修改产品名称', type: 'warning' }
        )
        if (action === 'confirm') {
          linkExistingProductId = check.productId
          // 关联已有产品 ⇒ 后端走 linkExistingProductId 分支**不新建产品**，SKU 由该产品决定
          // ⇒ 清空并置灰输入框，避免用户误以为这里填的 SKU 生效了（模板里有配套提示）
          linkedExisting.value = true
          form.productSku = ''
        }
      } catch {
        ElMessage.warning('请修改产品名称后重新提交')
        return
      }
    }
  } catch (e: any) {
    // 查重接口异常不阻塞创建
    console.warn('查重失败', e?.message || e)
  }

  saving.value = true
  try {
    await addProject(form as any, linkExistingProductId)
    ElMessage.success('项目已新增'); invalidate('devProject')
    resetForm()
    // 提交成功 ⇒ 先清脏标记（否则离开会被未保存确认拦住），再关掉本次录入的页签并回列表
    markClean()
    tabStore.closeTabAndBack(route.path)
    router.replace('/dev/project')
  } catch (e: any) { ElMessage.error('项目创建失败: ' + (e?.message || '未知错误')) }
  saving.value = false
}

function goBack() { router.push('/dev/project') }

function onNameBlur() {
  if (!form.productName || !form.productName.trim()) {
    form.productName = form.name
  }
}

// 顶栏"刷新数据"：重新加载方案供应商与物料类型
async function handleRefreshData() { await Promise.all([loadData(), loadMaterialTypeIds()]) }
onMounted(async () => {
  loadData()
  loadMaterialTypeIds()
  // 需求 1：进入立项页即预填一个 NS- 打头的产品SKU（可修改）
  await prefillProductSku()   // 必须等预填完成再建基线，否则预填的 SKU 会被误判成用户修改
  window.addEventListener('refresh:dropdown-data', handleRefreshData)
  takeBaseline()
})
onUnmounted(() => window.removeEventListener('refresh:dropdown-data', handleRefreshData))
</script>

<template>
  <!-- 统一骨架（2026-09-23 全站定稿口径）：页头左端「← 返回」→ 标题(取 meta) → 右端操作（创建项目） -->
  <PageShell back-fallback="/dev/project">
    <template #actions>
      <el-button type="primary" :loading="saving" @click="handleSubmit">保存</el-button>
    </template>

    <!-- 基础信息 -->
    <el-card shadow="never">
      <template #header><span style="font-weight:600">基础信息</span></template>
      <!-- 2026-10-10（用户报：改配信息里「玻璃分辨率」折行 —— 「玻璃分辨」一行、「率」掉到第二行 ✗）：
           本表单含 5 字标签「玻璃分辨率」且带 **必填星号** ⇒ 实测需求 ≈87~92px，而默认档正好是 **90px**
           ⇒ **骑在边缘**：字体度量/浏览器缩放差 1~2px 就折行（这正是"守卫通过、用户却看到折行"的原因 ✗）。
           按 tokens.css 既有口径（**凡含 5 字及以上标签的表单一律用 -lg 档**）升到 **112px** ✓ 留足余量。
           ⚠️ 下方"时间节点"那个表单最长标签只有 4 字（立项日期/预计完成）⇒ **保持 90px 不动** ✓（不无谓改动）。 -->
      <el-form :model="form" label-width="var(--app-label-width-lg)">
        <!-- 2026-09-21 布局：行1 项目名称|产品SKU|产品名称、行2 规格|适配机型|品牌、
             行3 显示方案|触摸方案|打样工厂（前两个在"原配"时隐藏）、行4 委外工厂 -->
        <el-row :gutter="16">
          <el-col :span="8"><el-form-item required label="项目名称"><el-input v-model="form.name" placeholder="请输入项目名称" @blur="onNameBlur" /></el-form-item></el-col>
          <!-- 需求 1：产品SKU —— 默认 NS- 打头自动生成、可修改；紧邻「产品名称」之前 -->
          <el-col :span="8"><el-form-item label="产品SKU" prop="productSku" :rules="[{ required: true, message: '请输入产品SKU', trigger: 'blur' }]">
            <el-input v-model="form.productSku" :disabled="linkedExisting" maxlength="64" placeholder="自动生成，可修改" />
            <div v-if="linkedExisting" style="font-size:var(--app-font-xs);color:var(--app-text-secondary);line-height:1.4">
              已关联已有产品，SKU 沿用该产品、此处不再生效
            </div>
          </el-form-item></el-col>
          <el-col :span="8"><el-form-item label="产品名称" prop="productName" :rules="[{ required: true, message: '请输入产品名称', trigger: 'blur' }]"><el-input v-model="form.productName" placeholder="请输入产品名称" /></el-form-item></el-col>
          <!-- 需求 3：规格（原配/改配）——与关联产品的规格联动 -->
          <el-col :span="8"><el-form-item label="规格" prop="specType" :rules="[{ required: true, message: '请选择规格', trigger: 'change' }]">
            <el-select v-model="form.specType" placeholder="请选择（原配/改配）" style="width:100%">
              <el-option v-for="o in SPEC_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="8"><el-form-item label="适配机型"><el-input v-model="form.adaptModel" placeholder="如 iPhone 15" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="品牌"><el-select v-model="form.brandId" filterable clearable placeholder="选择品牌" style="width:100%" @change="(v: any) => { if (v === ADD_MARKER) { form.brandId = undefined; router.push('/inventory/brand'); return } }"><el-option v-for="b in brandOptions" :key="b.id" :label="b.brandName" :value="b.id" /><el-option label="+ 新增" :value="ADD_MARKER" /></el-select></el-form-item></el-col>
          <!-- 需求 4：规格=原配 ⇒ 隐藏「显示方案 / 触摸方案」 -->
          <el-col v-if="!isOriginalSpec" :span="8"><el-form-item label="显示方案">
            <el-select v-model="form.displaySupplierName" filterable allow-create style="width:100%" placeholder="选择或输入" @change="(v: string) => { if (v === ADD_MARKER) { form.displaySupplierName = ''; router.push('/supplier/manage'); return } }"><el-option v-for="s in solutionSupplierOptions" :key="s.id" :label="s.name" :value="s.name" /><el-option label="+ 新增" :value="ADD_MARKER" /></el-select>
          </el-form-item></el-col>
          <el-col v-if="!isOriginalSpec" :span="8"><el-form-item label="触摸方案">
            <el-select v-model="form.touchSupplierName" filterable allow-create style="width:100%" placeholder="选择或输入" @change="(v: string) => { if (v === ADD_MARKER) { form.touchSupplierName = ''; router.push('/supplier/manage'); return } }"><el-option v-for="s in solutionSupplierOptions" :key="s.id" :label="s.name" :value="s.name" /><el-option label="+ 新增" :value="ADD_MARKER" /></el-select>
          </el-form-item></el-col>
          <el-col :span="8"><el-form-item label="打样工厂">
            <RemoteSelect v-model="form.sampleFactoryId" :fetch="fetchFactorySuppliers" clearable placeholder="选择工厂" @change="(v: any) => { if (v === ADD_MARKER) { form.sampleFactoryId = undefined; router.push('/supplier/manage'); return } }" domain="vendor" ><el-option label="+ 新增" :value="ADD_MARKER" /></RemoteSelect>
          </el-form-item></el-col>
          <el-col :span="8"><el-form-item label="委外工厂">
            <RemoteSelect v-model="form.outsourceFactoryId" :fetch="fetchFactorySuppliers" clearable placeholder="选择工厂" @change="(v: any) => { if (v === ADD_MARKER) { form.outsourceFactoryId = undefined; router.push('/supplier/manage'); return } }" domain="vendor" ><el-option label="+ 新增" :value="ADD_MARKER" /></RemoteSelect>
          </el-form-item></el-col>
        </el-row>

        <!-- 原机配置 -->
        <el-divider content-position="left">原机配置</el-divider>
        <el-row :gutter="16">
          <!-- 2026-09-16 用户要求：原机配置 4 项全部必填 -->
          <el-col :span="8"><el-form-item required label="原机尺寸"><el-input v-model="form.originalSize" placeholder="如 6.1寸" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item required label="原分辨率"><el-input v-model="form.originalResolution" placeholder="如 1080×2400" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item required label="驱动IC"><el-input v-model="form.originalDriveIc" placeholder="原机驱动IC型号" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item required label="触摸IC"><el-input v-model="form.originalTouchIc" placeholder="原机触摸IC型号" /></el-form-item></el-col>
        </el-row>

        <!-- 改配信息（需求 4：规格=原配 ⇒ 整块隐藏；注意 handleSubmit 里的必填校验也要同步跳过） -->
        <el-divider v-if="!isOriginalSpec" content-position="left">改配信息</el-divider>
        <el-row v-if="!isOriginalSpec" :gutter="16">
          <!-- 2026-09-16 用户要求：改配信息 5 项全部必填 -->
          <el-col :span="8"><el-form-item required label="玻璃尺寸"><el-input v-model="form.glassSize" placeholder="如 6.1寸" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item required label="玻璃分辨率"><el-input v-model="form.glassResolution" placeholder="如 1080×2400" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item required label="驱动IC">
            <RemoteSelect v-model="form.configDriveIcId" domain="material" add-route="/outsource/material-info" :fetch="(kw: string) => fetchMaterialsByType(kw, materialTypeIdMap.drive)" :label-key="$mLabel" clearable filterable :disabled="!materialTypeIdMap.drive" style="width:100%" placeholder="选择驱动IC物料" />
          </el-form-item></el-col>
          <el-col :span="8"><el-form-item required label="触摸IC">
            <RemoteSelect v-model="form.configTouchIcId" domain="material" add-route="/outsource/material-info" :fetch="(kw: string) => fetchMaterialsByType(kw, materialTypeIdMap.touch)" :label-key="$mLabel" clearable filterable :disabled="!materialTypeIdMap.touch" style="width:100%" placeholder="选择触摸IC物料" />
          </el-form-item></el-col>
          <el-col :span="8"><el-form-item required label="码片IC">
            <RemoteSelect v-model="form.configCodeIcId" domain="material" add-route="/outsource/material-info" :fetch="(kw: string) => fetchMaterialsByType(kw, materialTypeIdMap.code)" :label-key="$mLabel" clearable filterable :disabled="!materialTypeIdMap.code" style="width:100%" placeholder="选择码片IC物料" />
          </el-form-item></el-col>
        </el-row>
      </el-form>
    </el-card>

    <!-- 时间节点 -->
    <el-card shadow="never" style="margin-top:12px">
      <template #header><span style="font-weight:600">时间节点</span></template>
      <el-form :model="form" label-width="var(--app-label-width)">
        <el-row :gutter="16">
          <el-col :span="8"><el-form-item label="立项日期"><el-input v-model="form.startDate" type="date" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="预计完成"><el-input v-model="form.expectedEndDate" type="date" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="备注"><el-input v-model="form.remark" placeholder="备注" /></el-form-item></el-col>
        </el-row>
      </el-form>
    </el-card>

  </PageShell>
</template>

<style scoped>
/* 页头/底部操作条已统一到全局骨架（PageShell + styles/page.css）；原 .add-page 局部样式已删除 */

</style>
