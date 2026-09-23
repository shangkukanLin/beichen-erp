<script setup lang="ts">
// BOM 历史快照（2026-09-23 用户要求：原 900px「BOM 历史快照」弹框改为独立页面）
// —— 下加工单时按「产品 + 研发BOM版本 + 明细内容」生成/复用快照，本页回看历史与流向（只读）。
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import PageShell from '@/components/PageShell.vue'
import { getProjectBomSnapshots } from '@/api/system'

const route = useRoute()
const loading = ref(false)
const list = ref<any[]>([])

function kindText(kind?: string) {
  if (kind === 'BOM') return '与研发BOM一致'
  if (kind === 'ORDER') return '下单时明细有调整'
  if (kind === 'MIGRATED') return '历史订单迁移'
  return kind || '-'
}
function fmtTime(v?: string) { return v ? String(v).slice(0, 19).replace('T', ' ') : '-' }

async function load() {
  loading.value = true
  try { list.value = (await getProjectBomSnapshots(route.params.projectId as string)) || [] } catch { list.value = [] } finally { loading.value = false }
}
onMounted(load)
</script>

<template>
  <!-- 统一骨架（2026-09-23 全站定稿口径）：标题交骨架（meta.title = BOM 历史快照）、返回交骨架；本页只读 -->
  <PageShell :loading="loading" back-fallback="/dev/project">
    <el-card shadow="never">
      <!-- 卡片页头已删除：标题与旧「返回」都交骨架统一处理 -->


      <div style="margin-bottom:8px;color:var(--app-text-secondary);font-size:var(--app-font-xs)">
        下加工单时按「产品 + 研发BOM版本 + 明细内容」生成：研发BOM没变就沿用上一份快照（多张加工单共享），有变化才新增。
      </div>

      <el-table :data="list" border size="small" empty-text="暂无快照（下过加工单后才会生成）">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div style="padding:6px 16px">
              <el-table :data="row.items || []" border size="small">
                <el-table-column label="物料类型" width="110"><template #default="{ row: it }">{{ it.materialTypeName || '-' }}</template></el-table-column>
                <el-table-column label="物料名称" min-width="160"><template #default="{ row: it }">{{ it.materialName || ('#' + it.materialId) }}</template></el-table-column>
                <el-table-column label="单位" width="70"><template #default="{ row: it }">{{ it.unit || '-' }}</template></el-table-column>
                <el-table-column label="单套用量" width="90" align="right"><template #default="{ row: it }">{{ it.quantityPerSet }}</template></el-table-column>
                <el-table-column label="损耗率%" width="90" align="right"><template #default="{ row: it }">{{ it.lossRate }}</template></el-table-column>
                <el-table-column label="供料方" width="90" align="center"><template #default="{ row: it }">{{ it.supplyType === 'FACTORY' ? '工厂包' : '我方供' }}</template></el-table-column>
              </el-table>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="BOM版本" width="90" align="center"><template #default="{ row }">{{ row.bomVersion != null ? ('v' + row.bomVersion) : '-' }}</template></el-table-column>
        <el-table-column label="来源" width="140"><template #default="{ row }"><el-tag size="small" :type="row.kind === 'BOM' ? 'success' : (row.kind === 'MIGRATED' ? 'info' : 'warning')">{{ kindText(row.kind) }}</el-tag></template></el-table-column>
        <el-table-column label="生成时间" width="170"><template #default="{ row }">{{ fmtTime(row.createTime) }}</template></el-table-column>
        <el-table-column label="明细" width="70" align="center"><template #default="{ row }">{{ row.itemCount }}</template></el-table-column>
        <el-table-column label="关联加工单" min-width="200">
          <template #default="{ row }">
            <span v-if="!row.orders || !row.orders.length" style="color:var(--app-text-placeholder)">-</span>
            <span v-else>{{ row.orders.map((o: any) => o.code).join('、') }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </PageShell>
</template>

<style scoped>/* 页头/根容器已统一到全局骨架（PageShell + styles/page.css）；原 .p 局部样式已删除 */</style>
