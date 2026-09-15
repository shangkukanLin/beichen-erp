<template>
  <div class="dashboard">
    <el-tabs v-model="activeTab" type="border-card" @tab-change="onTabChange">
      <!-- 经营总览（默认首页） -->
      <el-tab-pane label="经营总览" name="overview">
        <!-- 财务区块：仅「经营概览」（经营分析）菜单权限可见（数据安全） -->
        <!-- 2026-09-14 修死键：原 gate 用的 route_name「FinanceAnalysis」在最新菜单里**已不存在**
             （财务分析已拆成经营分析 AnalysisOverview/Profit/Cash/Tax/Sale/Customer），
             导致本区块（本月/本年 KPI + 趋势图）对**所有用户都不显示**；改用 AnalysisOverview。 -->
        <template v-if="hasMenu['AnalysisOverview']">
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
          <!-- 顺序 = 使用频率（2026-09-14 按最新菜单重排：研发项目/BOM/物料/图纸/知识库 高频，模板类低频收尾） -->
          <el-button v-if="hasMenu['DevProject']" type="primary" size="small" text @click="$router.push('/dev/project')">研发项目</el-button>
          <el-button v-if="hasMenu['DevBom']" type="primary" size="small" text @click="$router.push('/dev/bom')">BOM管理</el-button>
          <el-button v-if="hasMenu['DevMaterial']" type="primary" size="small" text @click="$router.push('/dev/material')">研发物料管理</el-button>
          <el-button v-if="hasMenu['DevDrawing']" type="primary" size="small" text @click="$router.push('/dev/drawing')">图纸文档</el-button>
          <el-button v-if="hasMenu['DevScreenModel']" type="primary" size="small" text @click="$router.push('/dev/screen-model')">屏幕资料知识库</el-button>
          <el-button v-if="hasMenu['DevPhaseTemplate']" type="primary" size="small" text @click="$router.push('/dev/phase-template')">阶段模板</el-button>
          <el-button v-if="hasMenu['DevBomType']" type="primary" size="small" text @click="$router.push('/dev/bom-type')">BOM表类型</el-button>
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
          <!-- 顺序 = 使用频率（2026-09-14）：加工/交货/物料订单（跟单）→ 收发/仓库/出入库/报损（仓管）→ 物料信息 → 退货（售后）→ 供货商/合同模板（配置） -->
          <el-button v-if="hasMenu['OutsourceOrder']" type="primary" size="small" text @click="$router.push('/outsource/order')">加工订单</el-button>
          <el-button v-if="hasMenu['OutsourceMaterialOrder']" type="primary" size="small" text @click="$router.push('/outsource/material-order')">物料订单</el-button>
          <el-button v-if="hasMenu['OutsourceDeliveryInfo']" type="primary" size="small" text @click="$router.push('/outsource/delivery-info')">交货信息</el-button>
          <el-button v-if="hasMenu['OutsourceDelivery']" type="primary" size="small" text @click="$router.push('/outsource/delivery')">物料收发单</el-button>
          <el-button v-if="hasMenu['OutsourceMaterialInfo']" type="primary" size="small" text @click="$router.push('/outsource/material-info')">物料信息管理</el-button>
          <el-button v-if="hasMenu['OutsourceOtherIo']" type="primary" size="small" text @click="$router.push('/outsource/other-io')">物料其他出入库</el-button>
          <el-button v-if="hasMenu['OutsourceReturnOrder']" type="primary" size="small" text @click="$router.push('/outsource/return-order')">加工退货</el-button>
          <!-- 委外仓库：改用**路由路径**判权限（与「成品仓库管理」共用 route_name "Warehouse" 会串号） -->
          <el-button v-if="hasPath['/outsource/warehouse']" type="primary" size="small" text @click="$router.push('/outsource/warehouse')">委外仓库</el-button>
          <el-button v-if="hasMenu['OutsourceMaterialReturn']" type="primary" size="small" text @click="$router.push('/outsource/material-return')">物料退货</el-button>
          <el-button v-if="hasMenu['OutsourceMaterialWarehouse']" type="primary" size="small" text @click="$router.push('/outsource/material-warehouse')">自有物料仓</el-button>
          <el-button v-if="hasMenu['OutsourceStockLoss']" type="primary" size="small" text @click="$router.push('/outsource/stock-loss')">物料报损</el-button>
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
        <!-- 当日销售构成（2026-09-14 由「当日销售单」表格改）：口径 = **单据日期**、**仅已审核**；
             四饼图 = 产品(数量/金额) + 客户(数量/金额)；Top10 + "其他" 合并见 pieData() -->
        <el-card shadow="never" class="section-card">
          <template #header>
            <span class="section-title">当日销售构成（按单据日期 {{ today }}）</span>
            <!-- 图形类型切换（2026-09-14 用户要求）：四张图一起切换 -->
            <span style="float:right">
              <el-switch v-model="saleChartBar" size="small" active-text="柱状图" inactive-text="饼图"
                         style="margin-right:14px" @change="renderSaleCharts"/>
              <el-button size="small" text @click="$router.push('/inventory/sale')">查看更多 →</el-button>
            </span>
          </template>
          <div v-if="salePie.summary && salePie.summary.orderCount" class="pie-grid">
            <div>
              <div class="pie-title">产品销售数量</div>
              <div id="dashPieProdQty" class="chart pie-chart"/>
            </div>
            <div>
              <div class="pie-title">产品销售金额</div>
              <div id="dashPieProdAmt" class="chart pie-chart"/>
            </div>
            <div>
              <div class="pie-title">客户销售数量</div>
              <div id="dashPieCustQty" class="chart pie-chart"/>
            </div>
            <div>
              <div class="pie-title">客户销售金额</div>
              <div id="dashPieCustAmt" class="chart pie-chart"/>
            </div>
          </div>
          <div v-else class="pie-empty">当日暂无已审核销售数据</div>
        </el-card>
        <el-card shadow="never" class="section-card">
          <template #header><span class="section-title">本月客户销售 TOP5</span></template>
          <el-table :data="topCustomers" size="small" stripe>
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
          <!-- 顺序 = 使用频率（2026-09-14）：销售单最高频；退货整理（售后仓待处理，菜单 707 属销售业务，原误挂库存 TAB）次之 -->
          <el-button v-if="hasMenu['InventorySale']" type="primary" size="small" text @click="$router.push('/inventory/sale')">销售单</el-button>
          <el-button v-if="hasMenu['InventoryReturnSort']" type="primary" size="small" text @click="$router.push('/inventory/return-sort')">退货整理</el-button>
          <el-button v-if="hasMenu['SaleReturn']" type="primary" size="small" text @click="$router.push('/sale/return')">销售退单</el-button>
          <el-button v-if="hasMenu['SaleExchange']" type="primary" size="small" text @click="$router.push('/sale/exchange')">销售换货单</el-button>
          <el-button v-if="hasMenu['InventoryCustomer']" type="primary" size="small" text @click="$router.push('/inventory/customer')">客户管理</el-button>
        </div>
      </el-tab-pane>

      <el-tab-pane v-if="hasModule['stock']" label="成品库存" name="stock">
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
            <el-table-column prop="name" label="仓库" min-width="140" show-overflow-tooltip>
              <template #default="{row}"><el-link type="primary" @click="goWarehouse(row.warehouseId)">{{ row.name }}</el-link></template>
            </el-table-column>
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
            <el-table-column prop="productName" label="产品" min-width="140" show-overflow-tooltip>
              <template #default="{row}"><el-link type="primary" @click="goProduct(row.productId)">{{ row.productName }}</el-link></template>
            </el-table-column>
            <el-table-column prop="warehouseName" label="仓库" min-width="130" show-overflow-tooltip>
              <template #default="{row}"><el-link type="primary" @click="goWarehouse(row.warehouseId)">{{ row.warehouseName }}</el-link></template>
            </el-table-column>
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
          <!-- 顺序 = 使用频率（2026-09-14）：库存情况/库存/仓库（日常查询）→ 流水/出入库/移仓/盘点/报损（作业）→ 重分类/产品（低频）；
               注：退货整理已移到「销售业务」TAB（其菜单 707 本属销售业务） -->
          <el-button v-if="hasMenu['InventoryProductStock']" type="primary" size="small" text @click="$router.push('/inventory/product-stock')">成品库存情况</el-button>
          <el-button v-if="hasMenu['InventoryStock']" type="primary" size="small" text @click="$router.push('/inventory/stock')">成品库存</el-button>
          <!-- 成品仓库管理：改用**路由路径**判权限（与「委外仓库」共用 route_name "Warehouse" 会串号） -->
          <el-button v-if="hasPath['/inventory/warehouse']" type="primary" size="small" text @click="$router.push('/inventory/warehouse')">成品仓库管理</el-button>
          <el-button v-if="hasMenu['WarehouseStockLog']" type="primary" size="small" text @click="$router.push('/inventory/stock-log')">库存流水</el-button>
          <el-button v-if="hasMenu['InventoryOtherIo']" type="primary" size="small" text @click="$router.push('/inventory/other-io')">其他出入库</el-button>
          <el-button v-if="hasMenu['InventoryWarehouseMove']" type="primary" size="small" text @click="$router.push('/inventory/warehouse-move')">移仓单</el-button>
          <el-button v-if="hasMenu['InventoryStockTake']" type="primary" size="small" text @click="$router.push('/inventory/stock-take')">库存盘点</el-button>
          <el-button v-if="hasMenu['InventoryStockLoss']" type="primary" size="small" text @click="$router.push('/inventory/stock-loss')">成品报损</el-button>
          <el-button v-if="hasMenu['InventoryReclassify']" type="primary" size="small" text @click="$router.push('/inventory/reclassify')">品质重分类</el-button>
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
            <el-table-column label="类型" width="100" align="center"><template #default="{row}">{{ accountTypeLabel(row.accountType) }}</template></el-table-column>
            <el-table-column label="期初余额" width="130" align="right"><template #default="{row}">{{ fmtN(row.openingBalance) }}</template></el-table-column>
            <el-table-column label="当前余额" width="130" align="right">
              <template #default="{row}"><span style="font-weight:600">{{ fmtN(row.balance) }}</span></template>
            </el-table-column>
          </el-table>
        </el-card>
        <div class="quick-links">
          <span class="links-label">快捷入口：</span>
          <!-- 顺序 = 使用频率（2026-09-14）：应收/应付/收付款（每天核销）→ 账单/流水/费用/发票 → 账户/应付转应收（配置与偶发）；
               注：原「财务分析」按钮已删（其 route_name "FinanceAnalysis" 在最新菜单里不存在，是死键；经营分析在左侧菜单） -->
          <el-button v-if="hasMenu['FinanceReceivable']" type="primary" size="small" text @click="$router.push('/finance/receivable')">应收管理</el-button>
          <el-button v-if="hasMenu['FinancePayable']" type="primary" size="small" text @click="$router.push('/finance/payable')">应付管理</el-button>
          <el-button v-if="hasMenu['FinanceReceipt']" type="primary" size="small" text @click="$router.push('/finance/receipt')">收款管理</el-button>
          <el-button v-if="hasMenu['FinancePayment']" type="primary" size="small" text @click="$router.push('/finance/payment')">付款管理</el-button>
          <el-button v-if="hasMenu['FinanceBill']" type="primary" size="small" text @click="$router.push('/finance/bill')">账单生成</el-button>
          <el-button v-if="hasMenu['FinanceCashflow']" type="primary" size="small" text @click="$router.push('/finance/cashflow')">资金流水</el-button>
          <el-button v-if="hasMenu['FinanceExpense']" type="primary" size="small" text @click="$router.push('/finance/expense')">费用管理</el-button>
          <el-button v-if="hasMenu['FinanceInvoice']" type="primary" size="small" text @click="$router.push('/finance/invoice')">发票管理</el-button>
          <el-button v-if="hasMenu['FinanceAccount']" type="primary" size="small" text @click="$router.push('/finance/account')">资金账户</el-button>
          <el-button v-if="hasMenu['FinancePayableTransfer']" type="primary" size="small" text @click="$router.push('/finance/payable-transfer')">应付转应收</el-button>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { localDate, localMonth } from '@/utils/date'
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { useRouter } from 'vue-router'
import request from '@/utils/request'
import { useUserStore } from '@/stores/user'
import { ProjectStatus, PhaseStatus, OutsourceOrderStatus, OutsourceOrderStatusLabel, OutsourceOrderStatusTag, MaterialOrderStatus, MaterialOrderStatusLabel, MaterialOrderStatusTag, accountTypeLabel } from '@/api/enums'
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
function onTabChange() {
  nextTick(() => setTimeout(() => {
    renderTrend()
    if (activeTab.value === 'sale') renderSaleCharts()
  }, 60))
}

/** 饼图数据：按度量降序取 Top10，其余合并为"其他"（扇区过多不可读） */
function pieData(rows: any[], metric: string, nameKey: string) {
  const sorted = [...(rows || [])].sort((a: any, b: any) => (Number(b[metric]) || 0) - (Number(a[metric]) || 0))
  const data = sorted.slice(0, 10).map((r: any) => ({ name: r[nameKey] || '未命名', value: Number(r[metric]) || 0 }))
  const rest = sorted.slice(10).reduce((s: number, r: any) => s + (Number(r[metric]) || 0), 0)
  if (rest > 0) data.push({ name: '其他', value: rest })
  return data.filter((d: any) => d.value > 0)
}

/**
 * 单个图表（饼图 / 横向柱状图，由 `saleChartBar` 切换）：单例 init + setOption + resize（与 renderTrend 同模式）。
 * **注意 `setOption(option, true)` 的第二个参数必须为 true（notMerge）**：切换图形类型时要整体替换配置，
 * 否则饼图的系列/图例会残留在柱状图上。
 */
function drawChart(id: string, chart: echarts.ECharts | null, data: any[], unit: string) {
  const el = document.getElementById(id)
  if (!el) return chart
  const c = chart || echarts.init(el)
  if (!data.length) { c.clear(); c.resize(); return c }
  const total = data.reduce((s: number, d: any) => s + (Number(d.value) || 0), 0)
  const pctOf = (v: any) => (total > 0 ? ((Number(v) || 0) / total * 100).toFixed(1) : '0.0')
  // 数值格式：数量按原值（整数不带 .00、最多两位小数），金额沿用 fmtN 的千分位两位小数
  const fmtVal = (v: any) => (unit === '数量'
    ? Number(v || 0).toLocaleString('zh-CN', { maximumFractionDigits: 2 })
    : fmtN(v))

  // ① 横向柱状图：类目名在左侧、数值+占比标在条尾；升序排列 → 最大值显示在最上方
  if (saleChartBar.value) {
    const rows = [...data].sort((a: any, b: any) => (Number(a.value) || 0) - (Number(b.value) || 0))
    c.setOption({
      tooltip: {
        trigger: 'axis', axisPointer: { type: 'shadow' },
        formatter: (ps: any) => {
          const p = Array.isArray(ps) ? ps[0] : ps
          return `${p.name}<br/>${unit} ${fmtVal(p.value)}（${pctOf(p.value)}%）`
        }
      },
      grid: { left: 4, right: 86, top: 8, bottom: 4, containLabel: true },
      xAxis: { type: 'value', splitLine: { lineStyle: { color: '#f0f0f0' } }, axisLabel: { fontSize: 10 } },
      yAxis: { type: 'category', data: rows.map((d: any) => d.name), axisLabel: { fontSize: 11 }, axisTick: { show: false } },
      series: [{
        type: 'bar', barMaxWidth: 14,
        itemStyle: { borderRadius: [0, 3, 3, 0] },
        label: { show: true, position: 'right', fontSize: 11, color: '#606266',
                 formatter: (p: any) => `${fmtVal(p.value)}  ${pctOf(p.value)}%` },
        data: rows.map((d: any) => ({ name: d.name, value: Number(d.value) || 0 }))
      }]
    }, true)
    c.resize()
    return c
  }

  // ② 环形饼图：外侧引出引导线并标注「名称 + 百分比」
  c.setOption({
    tooltip: {
      trigger: 'item',
      formatter: (p: any) => `${p.name}<br/>${unit} ${fmtVal(p.value)}（${p.percent}%）`
    },
    legend: { type: 'scroll', bottom: 0, itemWidth: 10, itemHeight: 10, textStyle: { fontSize: 11 } },
    series: [{
      // 半径收窄，给外侧标签留出空间
      type: 'pie', radius: ['38%', '56%'], center: ['50%', '44%'],
      avoidLabelOverlap: true,
      percentPrecision: 1,
      label: { show: true, formatter: '{b} {d}%', fontSize: 11, color: '#606266' },
      labelLine: { show: true, length: 8, length2: 8, lineStyle: { color: '#c0c4cc' } },
      // 扇区多时自动隐藏重叠标签（避免 11 个产品标签互相压字）
      labelLayout: { hideOverlap: true },
      data
    }]
  }, true)
  c.resize()
  return c
}

/** 当日销售构成四图：产品(数量/金额) + 客户(数量/金额)；图形类型由 saleChartBar 决定 */
function renderSaleCharts() {
  const s = salePie.value || {}
  chProdQty = drawChart('dashPieProdQty', chProdQty, pieData(s.byProduct, 'quantity', 'productName'), '数量')
  chProdAmt = drawChart('dashPieProdAmt', chProdAmt, pieData(s.byProduct, 'amount', 'productName'), '金额')
  chCustQty = drawChart('dashPieCustQty', chCustQty, pieData(s.byCustomer, 'quantity', 'customerName'), '数量')
  chCustAmt = drawChart('dashPieCustAmt', chCustAmt, pieData(s.byCustomer, 'amount', 'customerName'), '金额')
}

// 根据用户菜单权限判断可见模块
const hasMenu = ref<Record<string, boolean>>({})
/**
 * 按**路由路径**判权限（2026-09-14 新增）：`sys_menu` 里「委外仓库 `/outsource/warehouse`」与
 * 「成品仓库管理 `/inventory/warehouse`」**共用同一个 route_name「Warehouse」** → 按 routeName 判权限会**串号**
 * （用户有其中任一权限，两处快捷入口都会显示）。故这两处入口改用**路径**判断。
 */
const hasPath = ref<Record<string, boolean>>({})
const hasModule = reactive({ dev: false, outsource: false, purchase: false, sale: false, stock: false, finance: false })

// 统计卡片数据
const devTotal = ref(0)
const devInProgress = ref(0)
const devBomCount = ref(0)
const devFinished = ref(0)
// 当日（本地时区 yyyy-MM-dd）：**不能用 toISOString()** —— 那是 UTC，东八区 08:00 之前会算成前一天。
// 两处使用：①研发"计划完成"逾期标红（模板）②"当日销售单"按 orderDate 过滤（2026-09-14 新增）
// 统一走 @/utils/date 的 localDate()（本地时区），不再各页手写
const today = localDate()
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
/** 当日销售构成（按**单据日期**、**仅已审核**）：{ summary, byProduct[], byCustomer[] } */
const salePie = ref<any>({ summary: {}, byProduct: [], byCustomer: [] })
/** 卡片右上角 switch：false=饼图（默认）/ true=柱状图（横向条形）—— 2026-09-14 用户要求，四张图一起切换 */
const saleChartBar = ref(false)
let chProdQty: echarts.ECharts | null = null
let chProdAmt: echarts.ECharts | null = null
let chCustQty: echarts.ECharts | null = null
let chCustAmt: echarts.ECharts | null = null
const topCustomers = ref<any[]>([])
const stockTotalQty = ref(0)
const stockTotalValue = ref(0)
const stockPendingQty = ref(0)
const stockDefectQty = ref(0)
const whStockRows = ref<any[]>([])
const lowStockItems = ref<any[]>([])
const financeAccounts = ref<any[]>([])

/**
 * 首页表格名称点击跳转（2026-09-14 新增）
 * - 仓库 → 仓库详情 `/inventory/warehouse/detail/:id`（含物料/成品库存与流水入口）
 * - 产品 → 产品库存分布详情 `/inventory/product-stock/detail/:id`（只读，看该产品在各仓库的分布）
 * 两页均在路由表标记 `operate: true` → 不走菜单白名单，任何登录用户点击都不会被拦到 403。
 * id 为空时保持纯文本不跳转（护栏）。
 */
function goWarehouse(id?: number) { if (id) router.push(`/inventory/warehouse/detail/${id}`) }
function goProduct(id?: number) { if (id) router.push(`/inventory/product-stock/detail/${id}`) }
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
// 本月（本地时区 yyyy-MM）：与 today 同理，**不能用 toISOString()** —— 那是 UTC，
// 东八区每月 1 日 08:00 之前会算成上个月。三处使用：本月采购金额、本月采购退货金额、本月销售单金额/数量
// （均以单据日期的 yyyy-MM 前缀比较，故此处必须是本地月份）
// 统一走 @/utils/date 的 localMonth()（本地时区）
const curMonth = localMonth()

function checkUserMenus() {
  const menus = userStore.menus || []
  const names = new Set<string>()
  const paths = new Set<string>()
  const collect = (list: any[]) => {
    if (!list) return
    list.forEach((m: any) => {
      if (m.routeName) names.add(m.routeName)
      if (m.routePath) paths.add(m.routePath)
      if (m.children) collect(m.children)
    })
  }
  collect(menus)

  // 可见模块判断（菜单推导）∩ 用户勾选（dashboardTabs；null=未配置=全部可见）
  const tabs = userStore.dashboardTabs
  const tabAllowed = (key: string) => !tabs || tabs.includes(key)
  hasModule.dev = (names.has('DevProject') || names.has('DevBom')) && tabAllowed('dev')
  hasModule.outsource = (names.has('OutsourceOrder') || names.has('OutsourceMaterialOrder')) && tabAllowed('outsource')
  hasModule.purchase = (names.has('InventoryPurchase') || names.has('SupplierManage') || names.has('OutsourceSupplierManage')) && tabAllowed('purchase')
  hasModule.sale = (names.has('InventorySale') || names.has('InventoryCustomer')) && tabAllowed('sale')
  hasModule.stock = (names.has('InventoryStock') || names.has('Warehouse') || names.has('ProductManage')) && tabAllowed('stock')
  hasModule.finance = (names.has('FinanceReceivable') || names.has('FinancePayable')) && tabAllowed('finance')

  // 快捷入口可见性（route_name 与 sys_menu 一致）
  // 2026-09-14 按最新 sys_menu 重设：补齐缺失的 route_name（DevScreenModel / OutsourceDeliveryInfo / OutsourceStockLoss /
  // InventoryProductStock / InventoryStockLoss / FinancePayableTransfer / AnalysisOverview），并移除**已不存在**的 FinanceAnalysis
  const menuNames = ['Dashboard','DevProject','DevBom','DevDrawing','DevMaterial','DevScreenModel','DevBomType','DevPhaseTemplate','ProductManage','InventoryBrand','InventoryCustomer','SupplierManage','OutsourceSupplierManage','OutsourceOrder','OutsourceMaterialOrder','OutsourceDeliveryInfo','OutsourceMaterialInfo','OutsourceDelivery','OutsourceOtherIo','OutsourceReturnOrder','OutsourceMaterialReturn','OutsourceStockLoss','Warehouse','OutsourceMaterialWarehouse','OutsourceContractTemplate','InventoryPurchase','InventoryPurchaseReturn','InventorySale','SaleReturn','SaleExchange','InventoryProductStock','InventoryStock','WarehouseStockLog','InventoryOtherIo','InventoryReclassify','InventoryWarehouseMove','InventoryStockTake','InventoryReturnSort','InventoryStockLoss','FinanceReceivable','FinancePayable','FinanceBill','FinanceCashflow','FinanceAccount','FinanceReceipt','FinancePayment','FinanceExpense','FinanceInvoice','FinancePayableTransfer','AnalysisOverview','SystemSmart','SystemUser','SystemSettings','SystemDataManage','SystemRole','SystemMenu','SystemClearData']
  menuNames.forEach(n => { hasMenu.value[n] = names.has(n) })
  // 共用 route_name「Warehouse」的两处仓库入口：改按**路径**判权限，避免串号（见 hasPath 注释）
  hasPath.value = {
    '/outsource/warehouse': paths.has('/outsource/warehouse'),
    '/inventory/warehouse': paths.has('/inventory/warehouse')
  }

  // 默认激活「经营总览」（进来先看全貌；财务区块按 AnalysisOverview 权限显隐）
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
      const [saleRes, cusRes, custAnRes, draftRes, pieRes] = await Promise.all([
        // 销售单总数：只取 total（pageSize=1，不再拉 200 条明细）
        request.get<any, any>('/inventory/sale/page', { params: { pageSize: 1 } }).catch(() => ({})),
        request.get<any, any>('/inventory/customer/page', { params: { pageSize: 200 } }).catch(() => ({})),
        // 本月口径（销售额/单数 + 客户 TOP5）：服务端整月聚合（preset=month = 本月 1 日~今天），
        // 不再从"最近 200 张销售单"里筛 —— 月单量超 200 时会少算（2026-09-14 修正）
        request.get<any, any>('/customer/analysis', { params: { preset: 'month' } }).catch(() => ({})),
        // 待审核数：服务端 status=DRAFT 计数（pageSize=1 只取 total），**不受 200 条子集限制**
        request.get<any, any>('/inventory/sale/page', { params: { status: 'DRAFT', pageSize: 1 } }).catch(() => ({})),
        // 当日销售构成（饼图用）：新增端点，按**单据日期**统计当日**已审核**单的产品/客户（数量 + 金额）
        request.get<any, any>('/sale/analysis/by-doc-date', { params: { date: today } }).catch(() => ({})),
      ])
      const mSum: any = custAnRes?.summary || {}
      saleMonthAmount.value = Number(mSum.totalAmount) || 0
      saleMonthCount.value = Number(mSum.orderCount) || 0
      // 待审核销售单：服务端 DRAFT 计数（原先从 200 条子集统计，单量超 200 会静默少算）
      salePending.value = Number(draftRes?.total) || 0
      // 当日销售构成饼图（用户确认口径：**单据日期** + **仅已审核**）：
      // ⚠️ 与上方「本月销售额/单数/客户 TOP5」（服务端按**审核日**聚合）**不同源**，数字不必然可比。
      salePie.value = pieRes && pieRes.summary ? pieRes : { summary: {}, byProduct: [], byCustomer: [] }
      if (activeTab.value === 'sale') setTimeout(renderSaleCharts, 60)
      // 本月客户销售 TOP5（2026-09-14 由「本年」改，用户确认口径）：
      // 口径 = **本月**（1 日~今天）已审核销售单金额；数据取服务端 top（已按金额降序，**不受 200 条上限**影响）；
      // 金额用 amount = **未扣退货**的销售额（与同页其它卡片一致）；占比分母 = 本月销售额合计
      const mTotal: number = Number(mSum.totalAmount) || 0
      topCustomers.value = ((custAnRes?.top || []) as any[]).slice(0, 5).map((r: any) => {
        const amt = Number(r.amount) || 0
        return {
          name: r.customerName || ('客户#' + r.customerId),
          amount: amt,
          count: Number(r.orderCount) || 0,
          pct: mTotal > 0 ? Math.round(amt / mTotal * 100) : 0
        }
      })
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
      const byWh: Record<number, { warehouseId: number, name: string, rows: number, qty: number, value: number }> = {}
      const low: any[] = []
      rows.forEach((r: any) => {
        const qtyA = Number(r.qtyA) || 0
        const qty = qtyA + (Number(r.qtyB) || 0) + (Number(r.qtyC) || 0) + (Number(r.qtyDefect) || 0) + (Number(r.qtyPending) || 0)
        const cost = Number(prodMap[r.productId]?.costPrice) || 0
        totalQty += qty
        totalValue += qty * cost
        pendingQty += Number(r.qtyPending) || 0
        defectQty += Number(r.qtyDefect) || 0
        // warehouseId 保留在聚合结果里 → 首页"仓库库存分布"的仓库名可点击跳仓库详情（2026-09-14）
        const wh = byWh[r.warehouseId] || (byWh[r.warehouseId] = { warehouseId: Number(r.warehouseId), name: r.warehouseName || ('仓库#' + r.warehouseId), rows: 0, qty: 0, value: 0 })
        wh.rows++; wh.qty += qty; wh.value += qty * cost
        const safety = Number(prodMap[r.productId]?.safetyStock) || 0
        if (safety > 0 && qtyA <= safety) {
          // 保留 productId / warehouseId → 低库存预警的产品名与仓库名可点击跳详情（2026-09-14）
          low.push({
            productId: Number(r.productId), warehouseId: Number(r.warehouseId),
            productName: r.productName || ('产品#' + r.productId), warehouseName: r.warehouseName || '',
            qtyA, safetyStock: safety, gap: safety - qtyA
          })
        }
      })
      stockTotalQty.value = totalQty
      stockTotalValue.value = Math.round(totalValue * 100) / 100
      stockPendingQty.value = pendingQty
      stockDefectQty.value = defectQty
      whStockRows.value = Object.values(byWh).sort((a: any, b: any) => b.value - a.value)
      // 低库存预警：**不限条数**（2026-09-14 用户要求放开；原为 .slice(0, 5)），按缺口从大到小排列
      lowStockItems.value = low.sort((a: any, b: any) => a.gap - b.gap)
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

/* 快捷入口固定在视口底部（方案 A · 2026-09-14）
   原理：真正的滚动容器是 Element Plus 的 .el-main(overflow:auto)，但 .el-tabs__content 自带 overflow:hidden，
   position:sticky 会以"不滚动的最近祖先"为参照 → **直接写 sticky 会失效**，必须先解除该层 overflow，
   sticky 才会相对 el-main 生效。
   （现有"页面级 Tabs 固定顶部"不踩这个坑，是因为 .el-tabs__header 是 .el-tabs__content 的**兄弟**、不在其内部。）
   作用域：scoped + :deep → 仅首页；弹窗内的 el-tabs 不受影响。 */
:deep(.el-tabs__content) { overflow: visible; }

.quick-links {
  position: sticky;
  bottom: 0;
  z-index: 40;                                  /* 低于顶部 TAB 栏(z-index 50)与弹窗层 */
  display: flex; align-items: center; gap: 8px; flex-wrap: wrap;
  margin-top: 12px;
  padding: 8px 12px;
  background: var(--app-bg-container);          /* 白底：避免正文从按钮后面穿过 */
  border-top: 1px solid var(--app-border-light);
}
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

/* 当日销售构成饼图（2026-09-14）：2×2 网格；必须写在 .chart 之后才能覆盖其 height/margin */
.pie-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 4px 16px; }
.pie-title { font-size: var(--app-font-sm); color: var(--app-text-secondary); text-align: center; margin-bottom: 2px; }
.pie-chart { height: 300px; margin-bottom: 8px; }
.pie-empty { color: var(--app-text-secondary); font-size: var(--app-font-sm); text-align: center; padding: 28px 0; }

/* 窄屏（≤768px）：快捷入口按钮会折成 2–3 行（委外加工 tab 有 11 个），固定底栏会长期占掉大片屏幕，
   故回退为"随内容滚动"（与改动前一致）。若要窄屏也固定，删掉这一条即可；饼图改为单列堆叠。 */
@media (max-width: 768px) {
  .quick-links { position: static; }
  .pie-grid { grid-template-columns: 1fr; }
}
</style>
