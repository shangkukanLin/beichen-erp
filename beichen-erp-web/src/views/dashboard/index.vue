<template>
  <div class="dashboard">
    <el-tabs v-model="activeTab" type="border-card" @tab-change="onTabChange">
      <!-- 经营总览（默认首页） -->
      <el-tab-pane label="经营总览" name="overview">
        <!-- 财务区块：仅「财务分析」菜单权限可见（数据安全） -->
        <template v-if="hasMenu['FinanceAnalysis']">
          <div class="stat-grid">
            <div class="stat-card" v-for="k in kpiCards" :key="k.label">
              <div class="stat-value" :style="{ color: k.tone }">{{ k.value }}</div>
              <div class="stat-label">
                {{ k.label }}
                <span v-if="k.chg?.text" :class="'chg ' + k.chgCls">{{ k.chg.text }}</span>
              </div>
            </div>
          </div>
          <div class="stat-grid">
            <div class="stat-card mini" v-for="y in ytdCards" :key="y.label">
              <div class="stat-value sm" :style="{ color: y.tone }">{{ y.value }}</div>
              <div class="stat-label">{{ y.label }}</div>
            </div>
          </div>
          <div id="dashTrendChart" class="chart"/>
        </template>

        <!-- 待办与预警（所有角色可见） -->
        <el-card shadow="never" class="section-card">
          <template #header>
            <span class="section-title">待办与预警</span>
            <span style="float:right;font-size:12px;color:var(--app-text-secondary)">
              {{ pendingCount ? `共 ${pendingCount} 项待处理` : '暂无待处理事项' }}
            </span>
          </template>
          <div class="todo-grid">
            <div class="todo-card clickable" v-for="t in todoItems" :key="t.label" @click="router.push(t.path)">
              <div class="todo-value" :style="{ color: t.count ? t.tone : 'var(--app-text-secondary)' }">{{ t.count }}</div>
              <div class="todo-label">{{ t.label }}</div>
              <div class="todo-sub" v-if="t.sub">{{ t.sub }}</div>
            </div>
          </div>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="备忘录" name="memo">
        <memo-panel />
      </el-tab-pane>

      <el-tab-pane v-if="hasModule['dev']" label="项目研发" name="dev">
        <div class="stat-grid">
          <div class="stat-card clickable" @click="$router.push('/dev/project?tab=active')">
            <div class="stat-value" style="color:#0C4A6E">{{ devTotal }}</div>
            <div class="stat-label">项目总数</div>
          </div>
          <div class="stat-card clickable" @click="$router.push('/dev/project?tab=active')">
            <div class="stat-value" style="color:var(--app-color-warning)">{{ devInProgress }}</div>
            <div class="stat-label">进行中</div>
          </div>
          <div class="stat-card clickable" @click="$router.push('/dev/bom')">
            <div class="stat-value" style="color:var(--app-color-primary)">{{ devBomCount }}</div>
            <div class="stat-label">BOM总数</div>
          </div>
          <div class="stat-card clickable" @click="$router.push('/dev/project?tab=finished')">
            <div class="stat-value" style="color:var(--app-color-success)">{{ devFinished }}</div>
            <div class="stat-label">已结项</div>
          </div>
        </div>

        <el-card shadow="never" class="section-card" v-if="inProgressProjects.length">
          <template #header>
            <span class="section-title">进行中项目</span>
            <el-button size="small" text style="float:right" @click="$router.push('/dev/project')">查看更多 →</el-button>
          </template>
          <el-table :data="inProgressProjects" size="small" stripe>
            <el-table-column label="项目编号" width="150">
              <template #default="{row}"><el-button type="primary" link size="small" @click="router.push(`/dev/project/edit/${row.id}`)">{{ row.code }}</el-button></template>
            </el-table-column>
            <el-table-column prop="name" label="项目名称" min-width="160">
              <template #default="{row}"><el-button type="primary" link size="small" @click="router.push(`/dev/project/edit/${row.id}`)">{{ row.name }}</el-button></template>
            </el-table-column>
            <el-table-column label="当前阶段" width="120"><template #default="{row}"><el-tag type="warning" size="small">{{ getDashboardPhase(row) }}</el-tag></template></el-table-column>
            <el-table-column label="计划完成" width="110"><template #default="{row}"><span :style="{ color: getDashboardPlannedEnd(row) && getDashboardPlannedEnd(row) < today ? 'var(--app-color-danger)' : 'var(--app-text-regular)' }">{{ getDashboardPlannedEnd(row) || '-' }}</span></template></el-table-column>
            <el-table-column label="进度" width="70" align="center"><template #default="{row}">{{ getDashboardProgress(row) }}</template></el-table-column>
          </el-table>
        </el-card>

        <div class="quick-links">
          <span class="links-label">快捷入口：</span>
          <el-button v-if="hasMenu['DevProject']" type="primary" size="small" text @click="$router.push('/dev/project')">研发项目</el-button>
          <el-button v-if="hasMenu['DevBom']" type="primary" size="small" text @click="$router.push('/dev/bom')">BOM管理</el-button>
          <el-button v-if="hasMenu['DevDrawing']" type="primary" size="small" text @click="$router.push('/dev/drawing')">图纸文档</el-button>
          <el-button v-if="hasMenu['DevMaterial']" type="primary" size="small" text @click="$router.push('/dev/material')">研发物料管理</el-button>
          <el-button v-if="hasMenu['DevBomType']" type="primary" size="small" text @click="$router.push('/dev/bom-type')">BOM表类型</el-button>
          <el-button v-if="hasMenu['DevPhaseTemplate']" type="primary" size="small" text @click="$router.push('/dev/phase-template')">阶段模板</el-button>
        </div>
      </el-tab-pane>

      <el-tab-pane v-if="hasModule['outsource']" label="委外加工" name="outsource">
        <div class="stat-grid">
          <div class="stat-card clickable" style="background:#fef0f0" @click="$router.push('/outsource/order')">
            <div class="stat-value" style="color:var(--app-color-warning)">{{ osPending }}</div>
            <div class="stat-label">加工单待处理</div>
          </div>
          <div class="stat-card clickable" style="background:#f0f5ff" @click="$router.push('/outsource/order')">
            <div class="stat-value" style="color:var(--app-color-primary)">{{ osInProgress }}</div>
            <div class="stat-label">加工单进行中</div>
          </div>
          <div class="stat-card clickable" style="background:#fef0f0" @click="$router.push('/outsource/material-order')">
            <div class="stat-value" style="color:var(--app-color-warning)">{{ osMatPending }}</div>
            <div class="stat-label">物料订单待处理</div>
          </div>
          <div class="stat-card clickable" style="background:#f0f5ff" @click="$router.push('/outsource/material-order')">
            <div class="stat-value" style="color:var(--app-color-primary)">{{ osMatReceiving }}</div>
            <div class="stat-label">物料订单收货中</div>
          </div>
        </div>

        <el-card shadow="never" class="section-card" v-if="activeOrders.length">
          <template #header>
            <span class="section-title">进行中加工单</span>
            <el-button size="small" text style="float:right" @click="$router.push('/outsource/order')">查看更多 →</el-button>
          </template>
          <el-table :data="activeOrders" size="small" stripe>
            <el-table-column prop="code" label="单号" min-width="120" show-overflow-tooltip>
              <template #default="{row}"><el-link type="primary" @click="$router.push(`/outsource/order/detail/${row.id}`)">{{ row.code }}</el-link></template>
            </el-table-column>
            <el-table-column prop="factoryName" label="加工厂" min-width="140" show-overflow-tooltip />
            <el-table-column label="产品" min-width="160" show-overflow-tooltip>
              <template #default="{row}">
                <div>{{ row.productNames || ((row.productCount || 0) + '项') }}</div>
                <div v-if="row.productSkus" style="font-size:12px;color:var(--app-text-secondary)">{{ row.productSkus }}</div>
              </template>
            </el-table-column>
            <el-table-column label="金额" width="110" align="right">
              <template #default="{row}">{{ row.totalAmount ? Number(row.totalAmount).toFixed(2) : '-' }}</template>
            </el-table-column>
            <el-table-column label="计划开始" width="110"><template #default="{row}">{{ row.planStartDate || '-' }}</template></el-table-column>
            <el-table-column label="计划完成" width="110"><template #default="{row}">{{ row.planEndDate || '-' }}</template></el-table-column>
            <el-table-column label="最近交货" width="110"><template #default="{row}">{{ row.latestDeliveryDate || '-' }}</template></el-table-column>
            <el-table-column label="状态" width="90" align="center">
              <template #default="{row}"><el-tag :type="OutsourceOrderStatusTag[row.status] || 'info'" size="small">{{ OutsourceOrderStatusLabel[row.status] || row.status }}</el-tag></template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-card shadow="never" class="section-card" v-if="pendingMatOrders.length">
          <template #header>
            <span class="section-title">进行中物料订单</span>
            <el-button size="small" text style="float:right" @click="$router.push('/outsource/material-order')">查看更多 →</el-button>
          </template>
          <el-table :data="pendingMatOrders" size="small" stripe>
            <el-table-column prop="code" label="订单号" width="170">
              <template #default="{row}"><el-link type="primary" @click="$router.push(`/outsource/material-order/detail/${row.id}`)">{{ row.code }}</el-link></template>
            </el-table-column>
            <el-table-column prop="supplierName" label="供应商" width="160" show-overflow-tooltip />
            <el-table-column label="下单日期" width="100" align="center"><template #default="{row}">{{ row.createTime ? row.createTime.slice(0,10) : '-' }}</template></el-table-column>
            <el-table-column label="物料名称" min-width="120" show-overflow-tooltip>
              <template #default="{row}">
                <span>{{ (row.items || []).map((it: any) => it.materialName).filter(Boolean).join('、') || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="下单总数" width="80" align="center">
              <template #default="{row}">{{ (row.items || []).reduce((s: number, it: any) => s + (it.orderQuantity || 0), 0) }}</template>
            </el-table-column>
            <el-table-column label="已收" width="70" align="center">
              <template #default="{row}">
                <span :style="{color: (row.items || []).reduce((s: number, it: any) => s + (it.receivedQuantity || 0), 0)>0?'var(--app-color-success)':''}">
                  {{ (row.items || []).reduce((s: number, it: any) => s + (it.receivedQuantity || 0), 0) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="最近交货" width="100" align="center"><template #default="{row}">{{ row.lastDeliveryTime ? row.lastDeliveryTime.slice(0,10) : '-' }}</template></el-table-column>
            <el-table-column label="交期" width="100" align="center"><template #default="{row}">{{ row.deliveryDate || '-' }}</template></el-table-column>
            <el-table-column label="状态" width="80" align="center">
              <template #default="{row}"><el-tag :type="MaterialOrderStatusTag[row.status] || 'info'" size="small">{{ MaterialOrderStatusLabel[row.status] || row.status }}</el-tag></template>
            </el-table-column>
          </el-table>
        </el-card>

        <div class="quick-links">
          <span class="links-label">快捷入口：</span>
          <el-button v-if="hasMenu['OutsourceOrder']" type="primary" size="small" text @click="$router.push('/outsource/order')">委外加工单</el-button>
          <el-button v-if="hasMenu['OutsourceMaterialOrder']" type="primary" size="small" text @click="$router.push('/outsource/material-order')">委外物料订单</el-button>
          <el-button v-if="hasMenu['OutsourceDelivery']" type="primary" size="small" text @click="$router.push('/outsource/delivery')">物料收发单</el-button>
          <el-button v-if="hasMenu['OutsourceOtherIo']" type="primary" size="small" text @click="$router.push('/outsource/other-io')">物料其他出入库</el-button>
          <el-button v-if="hasMenu['OutsourceMaterialInfo']" type="primary" size="small" text @click="$router.push('/outsource/material-info')">物料信息管理</el-button>
          <el-button v-if="hasMenu['OutsourceReturnOrder']" type="primary" size="small" text @click="$router.push('/outsource/return-order')">加工退货</el-button>
          <el-button v-if="hasMenu['OutsourceMaterialReturn']" type="primary" size="small" text @click="$router.push('/outsource/material-return')">物料退货</el-button>
          <el-button v-if="hasMenu['Warehouse']" type="primary" size="small" text @click="$router.push('/outsource/warehouse')">委外仓库</el-button>
          <el-button v-if="hasMenu['OutsourceMaterialWarehouse']" type="primary" size="small" text @click="$router.push('/outsource/material-warehouse')">自有物料仓</el-button>
          <el-button v-if="hasMenu['OutsourceSupplierManage']" type="primary" size="small" text @click="$router.push('/outsource/supplier/manage')">供货商管理</el-button>
          <el-button v-if="hasMenu['OutsourceContractTemplate']" type="primary" size="small" text @click="$router.push('/outsource/contract-template')">加工合同模板</el-button>
        </div>
      </el-tab-pane>

      <el-tab-pane v-if="hasModule['purchase']" label="进货业务" name="purchase">
        <div class="stat-grid">
          <div class="stat-card clickable" @click="$router.push('/inventory/purchase')">
            <div class="stat-value" style="color:var(--app-color-primary)">{{ fmtN(purchaseMonthAmount) }}</div>
            <div class="stat-label">本月采购金额</div>
          </div>
          <div class="stat-card clickable" @click="$router.push('/inventory/purchase-return')">
            <div class="stat-value" style="color:var(--app-color-warning)">{{ fmtN(purchaseReturnMonthAmount) }}</div>
            <div class="stat-label">本月采购退货</div>
          </div>
          <div class="stat-card clickable" @click="$router.push('/inventory/purchase')">
            <div class="stat-value" style="color:var(--app-color-danger)">{{ purchasePending }}</div>
            <div class="stat-label">待审核采购单</div>
          </div>
          <div class="stat-card clickable" @click="$router.push('/supplier/manage')">
            <div class="stat-value" style="color:var(--app-color-success)">{{ supplierTotal }}</div>
            <div class="stat-label">供应商</div>
          </div>
        </div>
        <el-card shadow="never" class="section-card">
          <template #header>
            <span class="section-title">最近采购单</span>
            <el-button size="small" text style="float:right" @click="$router.push('/inventory/purchase')">查看更多 →</el-button>
          </template>
          <el-table :data="recentPurchases" size="small" stripe>
            <el-table-column label="单号" min-width="150">
              <template #default="{row}"><el-link type="primary" @click="$router.push('/inventory/purchase')">{{ row.code }}</el-link></template>
            </el-table-column>
            <el-table-column prop="supplierName" label="供应商" min-width="130" show-overflow-tooltip />
            <el-table-column prop="itemsSummary" label="明细" min-width="160" show-overflow-tooltip />
            <el-table-column label="金额" width="110" align="right"><template #default="{row}">{{ fmtN(row.totalAmount) }}</template></el-table-column>
            <el-table-column label="日期" width="100"><template #default="{row}">{{ row.orderDate || '-' }}</template></el-table-column>
            <el-table-column label="状态" width="90" align="center">
              <template #default="{row}"><el-tag :type="DocStatusTag[row.status] || 'info'" size="small">{{ DocStatusLabel[row.status] || row.status }}</el-tag></template>
            </el-table-column>
          </el-table>
        </el-card>
        <el-card shadow="never" class="section-card" v-if="pendingPurchaseReturns.length">
          <template #header>
            <span class="section-title">待处理采购退货</span>
            <el-button size="small" text style="float:right" @click="$router.push('/inventory/purchase-return')">查看更多 →</el-button>
          </template>
          <el-table :data="pendingPurchaseReturns" size="small" stripe>
            <el-table-column label="单号" min-width="150"><template #default="{row}">{{ row.code }}</template></el-table-column>
            <el-table-column prop="supplierName" label="供应商" min-width="130" show-overflow-tooltip />
            <el-table-column prop="itemsSummary" label="明细" min-width="160" show-overflow-tooltip />
            <el-table-column label="关联采购单" min-width="150"><template #default="{row}">{{ row.purchaseOrderCode || '-' }}</template></el-table-column>
            <el-table-column label="日期" width="100"><template #default="{row}">{{ row.returnDate || '-' }}</template></el-table-column>
            <el-table-column label="状态" width="90" align="center">
              <template #default="{row}"><el-tag :type="DocStatusTag[row.status] || 'info'" size="small">{{ DocStatusLabel[row.status] || row.status }}</el-tag></template>
            </el-table-column>
          </el-table>
        </el-card>
        <div class="quick-links">
          <span class="links-label">快捷入口：</span>
          <el-button v-if="hasMenu['InventoryPurchase']" type="primary" size="small" text @click="$router.push('/inventory/purchase')">成品采购单</el-button>
          <el-button v-if="hasMenu['InventoryPurchaseReturn']" type="primary" size="small" text @click="$router.push('/inventory/purchase-return')">采购退货单</el-button>
          <el-button v-if="hasMenu['SupplierManage']" type="primary" size="small" text @click="$router.push('/supplier/manage')">供应商管理</el-button>
          <el-button v-if="hasMenu['OutsourceSupplierManage']" type="primary" size="small" text @click="$router.push('/outsource/supplier/manage')">供货商管理</el-button>
        </div>
      </el-tab-pane>

      <el-tab-pane v-if="hasModule['sale']" label="销售业务" name="sale">
        <div class="stat-grid">
          <div class="stat-card clickable" @click="$router.push('/inventory/sale')">
            <div class="stat-value" style="color:var(--app-color-primary)">{{ fmtN(saleMonthAmount) }}</div>
            <div class="stat-label">本月销售额</div>
          </div>
          <div class="stat-card clickable" @click="$router.push('/inventory/sale')">
            <div class="stat-value" style="color:var(--app-color-primary)">{{ saleMonthCount }}</div>
            <div class="stat-label">本月销售单</div>
          </div>
          <div class="stat-card clickable" @click="$router.push('/inventory/sale')">
            <div class="stat-value" style="color:var(--app-color-danger)">{{ salePending }}</div>
            <div class="stat-label">待审核销售单</div>
          </div>
          <div class="stat-card clickable" @click="$router.push('/inventory/customer')">
            <div class="stat-value" style="color:var(--app-color-success)">{{ customerTotal }}</div>
            <div class="stat-label">客户</div>
          </div>
        </div>
        <el-card shadow="never" class="section-card">
          <template #header>
            <span class="section-title">最近销售单</span>
            <el-button size="small" text style="float:right" @click="$router.push('/inventory/sale')">查看更多 →</el-button>
          </template>
          <el-table :data="recentSales" size="small" stripe>
            <el-table-column label="单号" min-width="150">
              <template #default="{row}"><el-link type="primary" @click="$router.push('/inventory/sale')">{{ row.code }}</el-link></template>
            </el-table-column>
            <el-table-column prop="customerName" label="客户" min-width="130" show-overflow-tooltip />
            <el-table-column label="金额" width="120" align="right"><template #default="{row}">{{ fmtN(row.totalAmount) }}</template></el-table-column>
            <el-table-column label="下单日期" width="100"><template #default="{row}">{{ row.orderDate || '-' }}</template></el-table-column>
            <el-table-column label="状态" width="90" align="center">
              <template #default="{row}"><el-tag :type="DocStatusTag[row.status] || 'info'" size="small">{{ DocStatusLabel[row.status] || row.status }}</el-tag></template>
            </el-table-column>
          </el-table>
        </el-card>
        <el-card shadow="never" class="section-card">
          <template #header><span class="section-title">本年客户销售 TOP5</span></template>
          <el-table :data="topCustomers" size="small" stripe>
            <el-table-column type="index" label="#" width="50" align="center" />
            <el-table-column prop="name" label="客户" min-width="140" show-overflow-tooltip />
            <el-table-column prop="count" label="单数" width="80" align="center" />
            <el-table-column label="销售金额" width="130" align="right"><template #default="{row}">{{ fmtN(row.amount) }}</template></el-table-column>
            <el-table-column label="占比" min-width="140">
              <template #default="{row}">
                <el-progress :percentage="row.pct" :stroke-width="10" :show-text="false" />
                <span style="font-size:12px;color:var(--app-text-secondary)">{{ row.pct }}%</span>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
        <div class="quick-links">
          <span class="links-label">快捷入口：</span>
          <el-button v-if="hasMenu['InventorySale']" type="primary" size="small" text @click="$router.push('/inventory/sale')">销售单</el-button>
          <el-button v-if="hasMenu['SaleReturn']" type="primary" size="small" text @click="$router.push('/sale/return')">销售退单</el-button>
          <el-button v-if="hasMenu['SaleExchange']" type="primary" size="small" text @click="$router.push('/sale/exchange')">销售换货单</el-button>
          <el-button v-if="hasMenu['InventoryCustomer']" type="primary" size="small" text @click="$router.push('/inventory/customer')">客户管理</el-button>
        </div>
      </el-tab-pane>

      <el-tab-pane v-if="hasModule['stock']" label="成品库存业务" name="stock">
        <div class="stat-grid">
          <div class="stat-card clickable" @click="$router.push('/inventory/stock')">
            <div class="stat-value" style="color:var(--app-color-primary)">{{ fmtN(stockTotalQty) }}</div>
            <div class="stat-label">库存总件数</div>
          </div>
          <div class="stat-card clickable" @click="$router.push('/inventory/stock')">
            <div class="stat-value" style="color:var(--app-color-success)">{{ fmtN(stockTotalValue) }}</div>
            <div class="stat-label">库存总金额</div>
          </div>
          <div class="stat-card clickable" @click="$router.push('/inventory/return-sort')">
            <div class="stat-value" style="color:var(--app-color-warning)">{{ fmtN(stockPendingQty) }}</div>
            <div class="stat-label">待整理件数</div>
          </div>
          <div class="stat-card clickable" @click="$router.push('/inventory/stock')">
            <div class="stat-value" style="color:var(--app-color-danger)">{{ fmtN(stockDefectQty) }}</div>
            <div class="stat-label">不良品件数</div>
          </div>
        </div>
        <el-card shadow="never" class="section-card">
          <template #header><span class="section-title">仓库库存分布</span></template>
          <el-table :data="whStockRows" size="small" stripe>
            <el-table-column prop="name" label="仓库" min-width="140" show-overflow-tooltip />
            <el-table-column prop="rows" label="产品行数" width="90" align="center" />
            <el-table-column label="库存件数" width="110" align="right"><template #default="{row}">{{ fmtN(row.qty) }}</template></el-table-column>
            <el-table-column label="库存金额" width="130" align="right"><template #default="{row}">{{ fmtN(row.value) }}</template></el-table-column>
          </el-table>
        </el-card>
        <el-card shadow="never" class="section-card" v-if="lowStockItems.length">
          <template #header>
            <span class="section-title" style="color:var(--app-color-danger)">低库存预警（可用量 ≤ 安全库存）</span>
            <el-button size="small" text style="float:right" @click="$router.push('/inventory/stock')">查看库存 →</el-button>
          </template>
          <el-table :data="lowStockItems" size="small" stripe>
            <el-table-column prop="productName" label="产品" min-width="140" show-overflow-tooltip />
            <el-table-column prop="warehouseName" label="仓库" min-width="130" show-overflow-tooltip />
            <el-table-column label="A品可用" width="100" align="right">
              <template #default="{row}"><span style="color:var(--app-color-danger);font-weight:600">{{ fmtN(row.qtyA) }}</span></template>
            </el-table-column>
            <el-table-column label="安全库存" width="100" align="right"><template #default="{row}">{{ fmtN(row.safetyStock) }}</template></el-table-column>
            <el-table-column label="缺口" width="100" align="right">
              <template #default="{row}"><span style="color:var(--app-color-danger)">{{ fmtN(row.gap) }}</span></template>
            </el-table-column>
          </el-table>
        </el-card>
        <div class="quick-links">
          <span class="links-label">快捷入口：</span>
          <el-button v-if="hasMenu['InventoryStock']" type="primary" size="small" text @click="$router.push('/inventory/stock')">成品库存</el-button>
          <el-button v-if="hasMenu['WarehouseStockLog']" type="primary" size="small" text @click="$router.push('/inventory/stock-log')">库存流水</el-button>
          <el-button v-if="hasMenu['InventoryOtherIo']" type="primary" size="small" text @click="$router.push('/inventory/other-io')">其他出入库</el-button>
          <el-button v-if="hasMenu['InventoryWarehouseMove']" type="primary" size="small" text @click="$router.push('/inventory/warehouse-move')">移仓单</el-button>
          <el-button v-if="hasMenu['InventoryStockTake']" type="primary" size="small" text @click="$router.push('/inventory/stock-take')">库存盘点</el-button>
          <el-button v-if="hasMenu['InventoryReturnSort']" type="primary" size="small" text @click="$router.push('/inventory/return-sort')">退货整理</el-button>
          <el-button v-if="hasMenu['InventoryReclassify']" type="primary" size="small" text @click="$router.push('/inventory/reclassify')">品质重分类</el-button>
          <el-button v-if="hasMenu['Warehouse']" type="primary" size="small" text @click="$router.push('/inventory/warehouse')">成品仓库管理</el-button>
          <el-button v-if="hasMenu['ProductManage']" type="primary" size="small" text @click="$router.push('/product')">产品管理</el-button>
        </div>
      </el-tab-pane>

      <el-tab-pane v-if="hasModule['finance']" label="财务" name="finance">
        <div class="stat-grid">
          <div class="stat-card" v-for="c in finCards" :key="c.label">
            <div class="stat-value" :style="{color:c.color}">{{ fmtN(c.value) }}</div>
            <div class="stat-label">{{ c.label }}</div>
          </div>
        </div>
        <div class="stat-grid">
          <div class="stat-card clickable" @click="$router.push('/finance/receivable')" style="background:#fdf0f0">
            <div class="stat-value" style="color:var(--app-color-danger)">{{ fmtN(healthInfo.receivableUnpaid || 0) }}</div>
            <div class="stat-label">应收未收</div>
            <div class="stat-label" v-if="healthInfo.receivableOverdue > 0" style="color:var(--app-color-danger)">逾期 {{ fmtN(healthInfo.receivableOverdue) }}</div>
          </div>
          <div class="stat-card clickable" @click="$router.push('/finance/payable')" style="background:#fdf6ec">
            <div class="stat-value" style="color:var(--app-color-warning)">{{ fmtN(healthInfo.payableUnpaid || 0) }}</div>
            <div class="stat-label">应付未付</div>
          </div>
          <div class="stat-card clickable" @click="$router.push('/finance/cashflow')" style="background:#f0f9eb">
            <div class="stat-value" style="color:var(--app-color-success)">{{ fmtN(healthInfo.cashTotal || 0) }}</div>
            <div class="stat-label">账户总额</div>
          </div>
        </div>
        <el-card shadow="never" class="section-card">
          <template #header>
            <span class="section-title">账户余额</span>
            <el-button size="small" text style="float:right" @click="$router.push('/finance/cashflow')">资金流水 →</el-button>
          </template>
          <el-table :data="financeAccounts" size="small" stripe>
            <el-table-column prop="accountName" label="账户" min-width="140" />
            <el-table-column label="类型" width="100" align="center"><template #default="{row}">{{ ACCOUNT_TYPE_LABELS[row.accountType] || row.accountType }}</template></el-table-column>
            <el-table-column label="期初余额" width="130" align="right"><template #default="{row}">{{ fmtN(row.openingBalance) }}</template></el-table-column>
            <el-table-column label="当前余额" width="130" align="right">
              <template #default="{row}"><span style="font-weight:600">{{ fmtN(row.balance) }}</span></template>
            </el-table-column>
          </el-table>
        </el-card>
        <div class="quick-links">
          <span class="links-label">快捷入口：</span>
          <el-button v-if="hasMenu['FinanceReceivable']" type="primary" size="small" text @click="$router.push('/finance/receivable')">应收管理</el-button>
          <el-button v-if="hasMenu['FinancePayable']" type="primary" size="small" text @click="$router.push('/finance/payable')">应付管理</el-button>
          <el-button v-if="hasMenu['FinanceReceipt']" type="primary" size="small" text @click="$router.push('/finance/receipt')">收款管理</el-button>
          <el-button v-if="hasMenu['FinancePayment']" type="primary" size="small" text @click="$router.push('/finance/payment')">付款管理</el-button>
          <el-button v-if="hasMenu['FinanceExpense']" type="primary" size="small" text @click="$router.push('/finance/expense')">费用管理</el-button>
          <el-button v-if="hasMenu['FinanceInvoice']" type="primary" size="small" text @click="$router.push('/finance/invoice')">发票管理</el-button>
          <el-button v-if="hasMenu['FinanceBill']" type="primary" size="small" text @click="$router.push('/finance/bill')">账单生成</el-button>
          <el-button v-if="hasMenu['FinanceAccount']" type="primary" size="small" text @click="$router.push('/finance/account')">资金账户</el-button>
          <el-button v-if="hasMenu['FinanceCashflow']" type="primary" size="small" text @click="$router.push('/finance/cashflow')">资金流水</el-button>
          <el-button v-if="hasMenu['FinanceAnalysis']" type="primary" size="small" text @click="$router.push('/finance/analysis')">财务分析</el-button>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { useRouter } from 'vue-router'
import request from '@/utils/request'
import { useUserStore } from '@/stores/user'
import { ProjectStatus, PhaseStatus, OutsourceOrderStatus, OutsourceOrderStatusLabel, OutsourceOrderStatusTag, MaterialOrderStatus, MaterialOrderStatusLabel, MaterialOrderStatusTag } from '@/api/enums'
import { DocStatus, DocStatusLabel, DocStatusTag } from '@/api/common'
import { getDashboardPending, type DashboardPending } from '@/api/dashboard'
import MemoPanel from '@/views/memo/index.vue'

const router = useRouter()
const userStore = useUserStore()
const activeTab = ref('overview')

// ==================== 经营总览 ====================
const finSummary = ref<any>({})
const pending = ref<DashboardPending>({})
let trendChart: echarts.ECharts | null = null

async function loadOverview() {
  if (hasMenu.value['FinanceAnalysis']) {
    try { finSummary.value = await request.get<any, any>('/finance/analysis/summary') } catch { finSummary.value = {} }
  }
  try { pending.value = await getDashboardPending() } catch { pending.value = {} }
  await nextTick()
  if (activeTab.value === 'overview') setTimeout(renderTrend, 60)
}
async function loadPending() { try { pending.value = await getDashboardPending() } catch {} }

function fmtN(v?: any) { return v == null ? '0.00' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) }
function chg(cur: any, prev: any, goodDir: boolean) {
  const c = Number(cur) || 0, p = Number(prev) || 0
  if (p === 0) return { text: '', cls: '' }
  const rate = Math.round(((c - p) / Math.abs(p)) * 1000) / 10
  if (rate === 0) return { text: '持平', cls: 'dim' }
  const up = rate > 0
  return { text: (up ? '▲' : '▼') + Math.abs(rate) + '%', cls: (goodDir ? up : !up) ? 'good' : 'bad' }
}
const kpiCards = computed(() => {
  const cur = finSummary.value.cur || {}, prev = finSummary.value.prev || {}
  return [
    { label: '本月销售额', value: fmtN(cur.revenue), chg: chg(cur.revenue, prev.revenue, true), tone: 'var(--app-color-success)' },
    { label: '本月毛利', value: fmtN(cur.grossProfit), chg: chg(cur.grossProfit, prev.grossProfit, true), tone: 'var(--app-color-primary)' },
    { label: '本月净利润', value: fmtN(cur.netProfit), chg: chg(cur.netProfit, prev.netProfit, true), tone: Number(cur.netProfit) >= 0 ? 'var(--app-color-success)' : 'var(--app-color-danger)' },
    { label: '本月净现金流', value: fmtN(finSummary.value.curCashNet), chg: chg(finSummary.value.curCashNet, finSummary.value.prevCashNet, true), tone: Number(finSummary.value.curCashNet) >= 0 ? 'var(--app-color-success)' : 'var(--app-color-danger)' },
  ].map((k: any) => ({ ...k, chgCls: k.chg?.cls || '' }))
})
const ytdCards = computed(() => {
  const y = finSummary.value.ytd || {}
  return [
    { label: '本年累计销售额', value: fmtN(y.revenue), tone: 'var(--app-color-success)' },
    { label: '本年累计毛利', value: fmtN(y.grossProfit), tone: 'var(--app-color-primary)' },
    { label: '本年累计费用', value: fmtN(y.expense), tone: 'var(--app-color-warning)' },
    { label: '本年累计净利润', value: fmtN(y.netProfit), tone: Number(y.netProfit) >= 0 ? 'var(--app-color-success)' : 'var(--app-color-danger)' },
  ]
})

// 待办与预警：卡片点击进入对应页面
const todoItems = computed(() => {
  const c = pending.value.counts || {}
  const take = pending.value.stockTake || {}
  const overdueSort = (pending.value.returnSort?.overdue) || 0
  const overdueRec = Number(pending.value.overdueReceivable || 0)
  const items: any[] = []
  if (c.saleOrder) items.push({ label: '销售单待审核', count: c.saleOrder, path: '/inventory/sale', tone: 'var(--app-color-primary)', sub: '草稿待审核' })
  if (c.purchaseOrder) items.push({ label: '采购单待审核', count: c.purchaseOrder, path: '/inventory/purchase', tone: 'var(--app-color-primary)', sub: '草稿待审核' })
  if (c.outsourceOrder) items.push({ label: '委外加工单待审核', count: c.outsourceOrder, path: '/outsource/order', tone: 'var(--app-color-primary)', sub: '待审核' })
  if (c.materialOrder) items.push({ label: '委外物料订单待审核', count: c.materialOrder, path: '/outsource/material-order', tone: 'var(--app-color-primary)', sub: '待审核' })
  if (c.stockTake) items.push({ label: '盘点单待审核', count: c.stockTake, path: '/inventory/stock-take', tone: 'var(--app-color-primary)', sub: '草稿待审核' })
  if (c.warehouseMove) items.push({ label: '移仓单待审核', count: c.warehouseMove, path: '/inventory/warehouse-move', tone: 'var(--app-color-primary)', sub: '草稿待审核' })
  if (c.otherIo) items.push({ label: '其他出入库待审核', count: c.otherIo, path: '/inventory/other-io', tone: 'var(--app-color-primary)', sub: '草稿待审核' })
  if (c.saleReturn) items.push({ label: '销售退货单待审核', count: c.saleReturn, path: '/sale/return', tone: 'var(--app-color-primary)', sub: '草稿待审核' })
  if (c.purchaseReturn) items.push({ label: '采购退货单待审核', count: c.purchaseReturn, path: '/inventory/purchase-return', tone: 'var(--app-color-primary)', sub: '草稿待审核' })
  if (c.expense) items.push({ label: '费用单待审核', count: c.expense, path: '/finance/expense', tone: 'var(--app-color-primary)', sub: '草稿待审核' })
  // 预警类
  if (take.pending) items.push({
    label: `${take.period || '本月'}待盘点仓库`, count: take.pending, path: '/inventory/stock-take',
    tone: take.overdue ? 'var(--app-color-danger)' : 'var(--app-color-warning)',
    sub: take.overdue ? `其中超期 ${take.overdue} 个` : `应盘日 ${take.period || ''}`,
  })
  if (overdueSort) items.push({ label: '售后仓超期待整理', count: overdueSort, path: '/inventory/return-sort', tone: 'var(--app-color-danger)', sub: '停留超过 3 天' })
  if (overdueRec > 0) items.push({ label: '超期应收', count: fmtN(overdueRec), path: '/finance/receivable', tone: 'var(--app-color-danger)', sub: '已过到期日未收' })
  return items
})
const pendingCount = computed(() => todoItems.value.length)

/** 近 6 月经营趋势（echarts 单例；无数据时固定 y 轴上限避免文字重叠） */
function renderTrend() {
  const el = document.getElementById('dashTrendChart')
  if (!el) return
  const t = finSummary.value.trend || []
  trendChart = trendChart || echarts.init(el)
  const vals = t.flatMap((x: any) => [Number(x.revenue), Number(x.netProfit), Number(x.cashNet)])
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 60, right: 20, top: 16, bottom: 24 },
    xAxis: { type: 'category', data: t.map((x: any) => x.month) },
    yAxis: { type: 'value', max: vals.some((v: number) => v !== 0) ? undefined : 100 },
    series: [
      { name: '销售额', type: 'bar', barMaxWidth: 28, itemStyle: { color: '#91cc75' }, data: t.map((x: any) => Number(x.revenue)) },
      { name: '净利润', type: 'line', smooth: true, itemStyle: { color: '#5470c6' }, data: t.map((x: any) => Number(x.netProfit)) },
      { name: '净现金流', type: 'line', smooth: true, itemStyle: { color: '#ee6666' }, data: t.map((x: any) => Number(x.cashNet)) },
    ],
  })
  trendChart.resize()
}
// Tab 切换后容器尺寸恢复再渲染，避免按 0 宽度布局
function onTabChange() { nextTick(() => setTimeout(renderTrend, 60)) }

// 根据用户菜单权限判断可见模块
const hasMenu = ref<Record<string, boolean>>({})
const hasModule = reactive({ dev: false, outsource: false, purchase: false, sale: false, stock: false, finance: false })

// 统计卡片数据
const devTotal = ref(0)
const devInProgress = ref(0)
const devBomCount = ref(0)
const devFinished = ref(0)
const today = new Date().toISOString().split('T')[0]
const inProgressProjects = ref<any[]>([])
const dashboardPhaseMap = ref<Record<number, any[]>>({})

function getDashboardPhase(row: any) {
  const phases = dashboardPhaseMap.value[row.id]
  if (!phases || !phases.length) return '-'
  const active = phases.find((t: any) => t.status === PhaseStatus.IN_PROGRESS)
  return active ? active.phaseName : '-'
}

function getDashboardPlannedEnd(row: any) {
  const phases = dashboardPhaseMap.value[row.id]
  if (!phases || !phases.length) return ''
  const active = phases.find((t: any) => t.status === PhaseStatus.IN_PROGRESS)
  return active?.plannedEnd || ''
}

function getDashboardProgress(row: any) {
  const phases = dashboardPhaseMap.value[row.id]
  if (!phases || !phases.length) return '0/0'
  const done = phases.filter((t: any) => t.status === PhaseStatus.FINISHED || t.status === PhaseStatus.SKIPPED).length
  return `${done}/${phases.length}`
}
const osPending = ref(0)
const osInProgress = ref(0)
const osMatPending = ref(0)
const osMatReceiving = ref(0)
const activeOrders = ref<any[]>([])
const pendingMatOrders = ref<any[]>([])
const purchaseTotal = ref(0)
const supplierTotal = ref(0)
const saleTotal = ref(0)
const customerTotal = ref(0)
const productTotal = ref(0)
const warehouseTotal = ref(0)
const financeStats = ref<{label:string,value:any,color:string}[]>([])

// ===== 四大业务 tab 扩展数据 =====
const purchaseMonthAmount = ref(0)
const purchaseReturnMonthAmount = ref(0)
const purchasePending = ref(0)
const recentPurchases = ref<any[]>([])
const pendingPurchaseReturns = ref<any[]>([])
const saleMonthAmount = ref(0)
const saleMonthCount = ref(0)
const salePending = ref(0)
const recentSales = ref<any[]>([])
const topCustomers = ref<any[]>([])
const stockTotalQty = ref(0)
const stockTotalValue = ref(0)
const stockPendingQty = ref(0)
const stockDefectQty = ref(0)
const whStockRows = ref<any[]>([])
const lowStockItems = ref<any[]>([])
const financeAccounts = ref<any[]>([])
const ACCOUNT_TYPE_LABELS: Record<string, string> = { cash: '现金', bank: '银行', wechat: '微信', alipay: '支付宝' }
const healthInfo = computed(() => finSummary.value.health || {})
const finCards = computed(() => {
  const c = finSummary.value.cur || {}
  return [
    { label: '本月收入', value: c.revenue, color: 'var(--app-color-success)' },
    { label: '本月成本', value: c.cost, color: 'var(--app-color-warning)' },
    { label: '本月净利润', value: c.netProfit, color: Number(c.netProfit) >= 0 ? 'var(--app-color-success)' : 'var(--app-color-danger)' },
    { label: '本月净现金流', value: finSummary.value.curCashNet, color: Number(finSummary.value.curCashNet) >= 0 ? 'var(--app-color-success)' : 'var(--app-color-danger)' },
  ]
})
const curMonth = new Date().toISOString().slice(0, 7)

function checkUserMenus() {
  const menus = userStore.menus || []
  const names = new Set<string>()
  const collect = (list: any[]) => {
    if (!list) return
    list.forEach((m: any) => {
      if (m.routeName) names.add(m.routeName)
      if (m.children) collect(m.children)
    })
  }
  collect(menus)

  // 可见模块判断
  hasModule.dev = names.has('DevProject') || names.has('DevBom')
  hasModule.outsource = names.has('OutsourceOrder') || names.has('OutsourceMaterialOrder')
  hasModule.purchase = names.has('InventoryPurchase') || names.has('SupplierManage') || names.has('OutsourceSupplierManage')
  hasModule.sale = names.has('InventorySale') || names.has('InventoryCustomer')
  hasModule.stock = names.has('InventoryStock') || names.has('Warehouse') || names.has('ProductManage')
  hasModule.finance = names.has('FinanceReceivable') || names.has('FinancePayable')

  // 快捷入口可见性（route_name 与 sys_menu 完全一致；注意委外仓库/成品仓库管理共用 "Warehouse"）
  const menuNames = ['Dashboard','DevProject','DevBom','DevDrawing','DevMaterial','DevBomType','DevPhaseTemplate','ProductManage','InventoryBrand','InventoryCustomer','SupplierManage','OutsourceSupplierManage','OutsourceOrder','OutsourceMaterialOrder','OutsourceMaterialInfo','OutsourceDelivery','OutsourceOtherIo','OutsourceReturnOrder','OutsourceMaterialReturn','Warehouse','OutsourceMaterialWarehouse','OutsourceContractTemplate','InventoryPurchase','InventoryPurchaseReturn','InventorySale','SaleReturn','SaleExchange','InventoryStock','WarehouseStockLog','InventoryOtherIo','InventoryReclassify','InventoryWarehouseMove','InventoryStockTake','InventoryReturnSort','FinanceReceivable','FinancePayable','FinanceBill','FinanceCashflow','FinanceAccount','FinanceReceipt','FinancePayment','FinanceExpense','FinanceAnalysis','FinanceInvoice','SystemSmart','SystemUser','SystemPermission','SystemSettings','SystemDataManage','SystemRole','SystemMenu','SystemClearData']
  menuNames.forEach(n => { hasMenu.value[n] = names.has(n) })

  // 默认激活「经营总览」（进来先看全貌；财务区块按 FinanceAnalysis 权限显隐）
  activeTab.value = 'overview'
}

async function loadStats() {
  try {
    // 项目研发
    if (hasModule.dev) {
      const [projRes, bomRes, allProjRes] = await Promise.all([
        request.get<any, any>('/dev/project/page', { params: { pageSize: 1 } }).catch(() => ({})),
        // BOM 总数只需 total，无需拉全量明细
        request.get<any, any>('/dev/bom/page', { params: { pageSize: 1 } }).catch(() => ({})),
        request.get<any, any>('/dev/project/page', { params: { pageSize: 200 } }).catch(() => ({})),
      ])
      const projTotal = projRes?.total || 0
      const bomProjectCount = bomRes?.total || 0
      const allRecords = allProjRes?.records || []
      let inProgress = 0, finished = 0
      const activeProjects: any[] = []
      allRecords.forEach((p: any) => {
        if (p.status === ProjectStatus.IN_PROGRESS) { inProgress++; activeProjects.push(p) }
        else if (p.status === ProjectStatus.CLOSED) finished++
      })
      inProgressProjects.value = activeProjects.slice(0, 5)
      devTotal.value = projTotal
      devInProgress.value = inProgress
      devBomCount.value = bomProjectCount
      devFinished.value = finished
      // 加载进行中项目的项目阶段
      if (activeProjects.length > 0) {
        try {
          const tlRes = await request.post('/dev/project/batch-phases', activeProjects.map((p: any) => p.id))
          dashboardPhaseMap.value = tlRes || {}
        } catch { /* ignore */}
      }
    }
  } catch { /* ignore */}
  try {
    if (hasModule.outsource) {
      const [allOrderRes, allMatRes] = await Promise.all([
        request.get<any, any>('/outsource/order/page', { params: { pageSize: 200 } }).catch(() => ({})),
        request.get<any, any>('/outsource/material-order/page', { params: { pageSize: 200 } }).catch(() => ({})),
      ])
      const allOrders = allOrderRes?.records || []
      let pending = 0, inProd = 0
      const activeList: any[] = []
      allOrders.forEach((o: any) => {
        if (o.status === OutsourceOrderStatus.PENDING) pending++
        if (o.status === OutsourceOrderStatus.PRODUCING) { inProd++; activeList.push(o) }
        if (o.status === OutsourceOrderStatus.PENDING) activeList.push(o)
      })
      osPending.value = pending
      osInProgress.value = pending + inProd
      activeOrders.value = activeList.slice(0, 5)
      const allMats = allMatRes?.records || []
      let matPending = 0, matReceiving = 0
      allMats.forEach((m: any) => {
        if (m.status === MaterialOrderStatus.PENDING) matPending++
        if (m.status === MaterialOrderStatus.RECEIVING) matReceiving++
      })
      osMatPending.value = matPending
      osMatReceiving.value = matReceiving
      pendingMatOrders.value = allMats.filter((m: any) => m.status !== MaterialOrderStatus.FINISHED && m.status !== MaterialOrderStatus.CANCELLED).slice(0, 5)
    }
  } catch { /* ignore */}
  try {
    if (hasModule.purchase) {
      const [purRes, retRes, supRes] = await Promise.all([
        request.get<any, any>('/inventory/purchase/page', { params: { pageSize: 200 } }).catch(() => ({})),
        request.get<any, any>('/inventory/purchase-return/page', { params: { pageSize: 200 } }).catch(() => ({})),
        request.get<any, any>('/supplier/page', { params: { pageSize: 1 } }).catch(() => ({})),
      ])
      const purchases = purRes?.records || []
      purchaseMonthAmount.value = purchases
        .filter((p: any) => p.status === 'AUDITED' && (p.orderDate || '').startsWith(curMonth))
        .reduce((s: number, p: any) => s + (Number(p.totalAmount) || 0), 0)
      purchasePending.value = purchases.filter((p: any) => p.status === 'DRAFT').length
      recentPurchases.value = purchases.slice(0, 8)
      const rets = retRes?.records || []
      purchaseReturnMonthAmount.value = rets
        .filter((p: any) => p.status === 'AUDITED' && (p.returnDate || '').startsWith(curMonth))
        .reduce((s: number, p: any) => s + (Number(p.totalAmount) || 0), 0)
      pendingPurchaseReturns.value = rets.filter((p: any) => p.status === 'DRAFT').slice(0, 5)
      supplierTotal.value = supRes?.total || 0
      purchaseTotal.value = purRes?.total || 0
    }
  } catch { /* ignore */}
  try {
    if (hasModule.sale) {
      const [saleRes, cusRes] = await Promise.all([
        request.get<any, any>('/inventory/sale/page', { params: { pageSize: 200 } }).catch(() => ({})),
        request.get<any, any>('/inventory/customer/page', { params: { pageSize: 200 } }).catch(() => ({})),
      ])
      const sales = saleRes?.records || []
      const custNameMap: Record<number, string> = {}
      ;(cusRes?.records || []).forEach((c: any) => { custNameMap[c.id] = c.name })
      const audited = sales.filter((s: any) => s.status === 'AUDITED')
      const monthSales = audited.filter((s: any) => (s.orderDate || '').startsWith(curMonth))
      saleMonthAmount.value = monthSales.reduce((s: number, x: any) => s + (Number(x.totalAmount) || 0), 0)
      saleMonthCount.value = monthSales.length
      salePending.value = sales.filter((s: any) => s.status === 'DRAFT').length
      recentSales.value = sales.slice(0, 8)
      // 本年客户销售 TOP5（已审核单聚合）
      const byCust: Record<number, { amount: number, count: number }> = {}
      audited.forEach((s: any) => {
        const k = s.customerId
        if (k == null) return
        if (!byCust[k]) byCust[k] = { amount: 0, count: 0 }
        byCust[k].amount += Number(s.totalAmount) || 0
        byCust[k].count++
      })
      const totalAmt = Object.values(byCust).reduce((s: number, v: any) => s + v.amount, 0)
      topCustomers.value = Object.entries(byCust)
        .map(([id, v]: any) => ({ name: custNameMap[Number(id)] || ('客户#' + id), amount: v.amount, count: v.count, pct: totalAmt > 0 ? Math.round(v.amount / totalAmt * 100) : 0 }))
        .sort((a: any, b: any) => b.amount - a.amount)
        .slice(0, 5)
      customerTotal.value = cusRes?.total || 0
      saleTotal.value = saleRes?.total || 0
    }
  } catch { /* ignore */}
  try {
    if (hasModule.stock) {
      const [prodRes, whRes, stkRes] = await Promise.all([
        request.get<any, any>('/product/page', { params: { pageSize: 200 } }).catch(() => ({})),
        request.get<any, any>('/warehouse/page', { params: { pageSize: 200 } }).catch(() => ({})),
        request.get<any, any>('/warehouse/stock/product-stock/page', { params: { pageSize: 200 } }).catch(() => ({})),
      ])
      productTotal.value = prodRes?.total || 0
      warehouseTotal.value = whRes?.total || 0
      const prodMap: Record<number, any> = {}
      ;(prodRes?.records || []).forEach((p: any) => { prodMap[p.id] = p })
      const rows = stkRes?.records || []
      let totalQty = 0, totalValue = 0, pendingQty = 0, defectQty = 0
      const byWh: Record<number, { name: string, rows: number, qty: number, value: number }> = {}
      const low: any[] = []
      rows.forEach((r: any) => {
        const qtyA = Number(r.qtyA) || 0
        const qty = qtyA + (Number(r.qtyB) || 0) + (Number(r.qtyC) || 0) + (Number(r.qtyDefect) || 0) + (Number(r.qtyPending) || 0)
        const cost = Number(prodMap[r.productId]?.costPrice) || 0
        totalQty += qty
        totalValue += qty * cost
        pendingQty += Number(r.qtyPending) || 0
        defectQty += Number(r.qtyDefect) || 0
        const wh = byWh[r.warehouseId] || (byWh[r.warehouseId] = { name: r.warehouseName || ('仓库#' + r.warehouseId), rows: 0, qty: 0, value: 0 })
        wh.rows++; wh.qty += qty; wh.value += qty * cost
        const safety = Number(prodMap[r.productId]?.safetyStock) || 0
        if (safety > 0 && qtyA <= safety) {
          low.push({ productName: r.productName || ('产品#' + r.productId), warehouseName: r.warehouseName || '', qtyA, safetyStock: safety, gap: safety - qtyA })
        }
      })
      stockTotalQty.value = totalQty
      stockTotalValue.value = Math.round(totalValue * 100) / 100
      stockPendingQty.value = pendingQty
      stockDefectQty.value = defectQty
      whStockRows.value = Object.values(byWh).sort((a: any, b: any) => b.value - a.value)
      lowStockItems.value = low.sort((a: any, b: any) => a.gap - b.gap).slice(0, 5)
    }
  } catch { /* ignore */}
  try {
    if (hasModule.finance) {
      const accRes = await request.get<any, any>('/finance/account/page', { params: { pageSize: 100 } }).catch(() => ({}))
      financeAccounts.value = accRes?.records || []
    }
  } catch { /* ignore */}
}

onMounted(async () => {
  checkUserMenus()
  // 总览与分模块统计并行加载，互不阻塞
  await Promise.all([loadOverview(), loadStats()])
})
</script>

<style scoped>
.dashboard { padding: 0; }

.stat-grid { display: flex; gap: 16px; flex-wrap: wrap; margin-bottom: 16px; }
.stat-card {
  flex: 1; min-width: 140px; max-width: 200px;
  background: #f5f7fa; border-radius: 8px; padding: 16px 20px; text-align: center;
}
.stat-card.clickable { cursor: pointer; transition: box-shadow 0.2s; }
.stat-card.clickable:hover { box-shadow: 0 2px 8px rgba(0,0,0,0.1); }
.stat-value { font-size: var(--app-font-num); font-weight: 700; }
.stat-label { font-size: var(--app-font-sm); color: var(--app-text-secondary); margin-top: 4px; }

.quick-links { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-top: 12px; }
.links-label { color: var(--app-text-secondary); font-size: var(--app-font-sm); }

.section-card { margin-bottom: 16px; }
.section-title { font-weight: 600; font-size: var(--app-font-base); }

/* 经营总览 */
.stat-card.mini { padding: 12px 16px; }
.stat-value.sm { font-size: 16px; font-weight: 700; }
.chart { width: 100%; height: 220px; margin-bottom: 16px; }
.chg { margin-left: 6px; font-weight: 600; }
.chg.good { color: var(--app-color-success); }
.chg.bad { color: var(--app-color-danger); }
.chg.dim { color: var(--app-text-secondary); }
.todo-grid { display: flex; gap: 12px; flex-wrap: wrap; }
.todo-card {
  min-width: 150px; max-width: 200px; flex: 1;
  background: #f5f7fa; border-radius: 8px; padding: 14px 16px; text-align: center;
}
.todo-card.clickable { cursor: pointer; transition: box-shadow 0.2s; }
.todo-card.clickable:hover { box-shadow: 0 2px 8px rgba(0,0,0,0.1); }
.todo-value { font-size: 20px; font-weight: 700; }
.todo-label { font-size: var(--app-font-sm); color: var(--app-text-secondary); margin-top: 4px; }
.todo-sub { font-size: 12px; color: var(--app-text-secondary); margin-top: 2px; }
</style>
