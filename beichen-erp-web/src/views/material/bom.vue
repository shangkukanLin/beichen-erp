<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import {
  getMaterialPage,
  getMaterialBomTree,
  getMaterialBomWhereUsed,
  type Material,
  type MaterialBomNode,
  type MaterialBrief
} from '@/api/material'

const route = useRoute()

const materials = ref<Material[]>([])
const selectedId = ref<number | string>('')
const treeData = ref<MaterialBomNode[]>([])
const whereUsed = ref<MaterialBrief[]>([])
const loading = ref(false)
const materialsLoading = ref(false)

async function loadMaterials() {
  materialsLoading.value = true
  try {
    const res = await getMaterialPage({ pageNum: 1, pageSize: 1000 })
    materials.value = res?.records || []
  } catch {
    materials.value = []
  } finally {
    materialsLoading.value = false
  }
}

async function loadBom() {
  if (!selectedId.value) {
    treeData.value = []
    whereUsed.value = []
    return
  }
  loading.value = true
  try {
    const [tree, used] = await Promise.all([
      getMaterialBomTree(selectedId.value),
      getMaterialBomWhereUsed(selectedId.value)
    ])
    treeData.value = tree || []
    whereUsed.value = used || []
  } catch {
    treeData.value = []
    whereUsed.value = []
  } finally {
    loading.value = false
  }
}

const currentMaterial = ref<Material | null>(null)
function onSelect() {
  currentMaterial.value = materials.value.find(m => String(m.id) === String(selectedId.value)) || null
  loadBom()
}

function fmt(n: number | undefined) {
  if (n == null) return '-'
  return n
}

onMounted(async () => {
  await loadMaterials()
  const qid = route.query.materialId
  if (qid) {
    selectedId.value = qid as string
    onSelect()
  }
})
</script>

<template>
  <div class="bom-page">
    <el-card shadow="never" class="filter-card">
      <div class="filter-row">
        <span class="label">选择物料：</span>
        <el-select
          v-model="selectedId"
          filterable
          placeholder="请选择要查看BOM的物料"
          style="width: 360px"
          :loading="materialsLoading"
          @change="onSelect"
        >
          <el-option v-for="m in materials" :key="m.id" :value="m.id" :label="`${m.code} - ${m.name}`" />
        </el-select>
        <el-tag v-if="currentMaterial" type="info" style="margin-left:12px">
          {{ currentMaterial.category || '未分类' }}
        </el-tag>
      </div>
      <div class="tip">提示：BOM 中展示的子物料名称/规格/单位均实时取自物料主数据，修改子物料后此处自动同步。</div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <template #header>
        <span style="font-weight:600">多级BOM组成（{{ currentMaterial ? currentMaterial.name : '请选择物料' }}）</span>
      </template>
      <el-table
        v-loading="loading"
        :data="treeData"
        row-key="id"
        :tree-props="{ children: 'children' }"
        default-expand-all
        border
        stripe
        empty-text="该物料暂无子物料组成"
      >
        <el-table-column prop="childCode" label="物料编码" min-width="140" show-overflow-tooltip />
        <el-table-column prop="childName" label="物料名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="childCategory" label="分类" width="100" align="center" />
        <el-table-column prop="childSpec" label="规格" min-width="140" show-overflow-tooltip />
        <el-table-column prop="childUnit" label="单位" width="80" align="center" />
        <el-table-column prop="quantity" label="单台用量" width="110" align="right">
          <template #default="{ row }">{{ fmt(row.quantity) }}</template>
        </el-table-column>
        <el-table-column prop="lossRate" label="损耗率" width="100" align="right">
          <template #default="{ row }">{{ fmt(row.lossRate) }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
      </el-table>
    </el-card>

    <el-card shadow="never" class="used-card" v-if="selectedId">
      <template #header><span style="font-weight:600">被以下成品/半成品使用</span></template>
      <el-table :data="whereUsed" border stripe empty-text="暂无上级物料使用它">
        <el-table-column prop="code" label="物料编码" min-width="140" show-overflow-tooltip />
        <el-table-column prop="name" label="物料名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="category" label="分类" width="100" align="center" />
        <el-table-column prop="spec" label="规格" min-width="140" show-overflow-tooltip />
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.bom-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.filter-card :deep(.el-card__body),
.table-card :deep(.el-card__body),
.used-card :deep(.el-card__body) {
  padding: 16px;
}
.filter-row {
  display: flex;
  align-items: center;
}
.label {
  font-size: 14px;
  color: #606266;
}
.tip {
  margin-top: 10px;
  font-size: 12px;
  color: #909399;
}
</style>
