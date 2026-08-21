<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header><span>收费售后退回不良品</span></template>
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="退回仓库" prop="warehouseId">
              <RemoteSelect v-model="form.warehouseId" :fetch="fetchWarehouses" :label-key="(row:any)=>row.warehouseName || row.name" placeholder="请选择仓库" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="退回产品" prop="productId">
              <RemoteSelect v-model="form.productId" :fetch="fetchProducts" placeholder="请选择产品" style="width: 100%"
                @pick="onProductPick" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="品质等级" width="110">
              <el-tag type="danger">不良品</el-tag>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="退回数量" prop="quantity">
              <el-input-number v-model="form.quantity" :min="0.01" :precision="2" :step="1" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="处理方式" prop="handleMethod">
              <el-select v-model="form.handleMethod" filterable allow-create placeholder="如：维修/换新/报废" style="width: 100%">
                <el-option label="维修" value="维修" />
                <el-option label="换新" value="换新" />
                <el-option label="报废" value="报废" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="选填" style="max-width: 600px" />
        </el-form-item>
      </el-form>
      <div class="footer">
        <el-button @click="goBack">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">提交</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import RemoteSelect from '@/components/RemoteSelect.vue'

interface Opt { id: number; name: string }

const router = useRouter()
const formRef = ref()
const saving = ref(false)

// Odoo 风格：实时查库
const fetchWarehouses = (kw: string) => request.get('/warehouse/page', { params: { pageSize: 500, warehouseName: kw } })
const fetchProducts = (kw: string) => request.get('/product/page', { params: { pageSize: 500, keyword: kw } })

const form = reactive({
  warehouseId: undefined as number | undefined,
  productId: undefined as number | undefined,
  productName: '',
  quantity: 1,
  handleMethod: '',
  remark: '',
})

const rules = {
  warehouseId: [{ required: true, message: '请选择退回仓库', trigger: 'change' }],
  productId: [{ required: true, message: '请选择退回产品', trigger: 'change' }],
  quantity: [{ required: true, message: '请输入退回数量', trigger: 'blur' }],
}

function onProductPick(opts: any[]) {
  const p = opts?.[0]
  form.productName = p ? p.name : ''
}

async function submit() {
  await formRef.value.validate()
  saving.value = true
  try {
    const payload = {
      warehouseId: form.warehouseId,
      productId: form.productId,
      productName: form.productName,
      quantity: form.quantity,
      handleMethod: form.handleMethod,
      remark: form.remark,
    }
    await request.post('/outsource/after-sale/return-defect', payload)
    ElMessage.success('退回成功，已入库不良品')
    router.push('/outsource/after-sale')
  } finally {
    saving.value = false
  }
}

function goBack() {
  router.push('/outsource/after-sale')
}

onMounted(() => {})
</script>

<style scoped>
.footer { margin-top: 20px; text-align: right; }
</style>
