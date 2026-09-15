<template>
  <div class="dashboard">
    <el-tabs v-model="activeTab" type="border-card" @tab-change="onTabChange">
      <!-- 备忘录（2026-09-15 用户要求：排在首位，并作为进首页的默认 TAB） -->
      <el-tab-pane label="备忘录" name="memo">
        <memo-panel />
      </el-tab-pane>

      <!-- 经营总览（财务区块按 AnalysisOverview 菜单权限显隐） -->
      <el-tab-pane label="经营总览" name="overview">
        <!-- 财务区块：仅「经营概览」（经营分析）菜单权限可见（数据安全） -->
        <!-- 2026-09-14 修死键：原 gate 用的 route_name「FinanceAnalysis」在最新菜单里**已不存在**
             （财务分析已拆成经营分析 AnalysisOverview/Profit/Cash/Tax/Sale/Customer），
             导致本区块（本月/本年 KPI + 趋势图）对**所有用户都不显示**；改用 AnalysisOverview。 -->
        <template v-if="hasMenu['AnalysisOverview']">
          <!-- 统计区间：统一组件 StatRange（2026-09-15 全站收口，UI 与利润表一致；本页默认「今日」） -->
          <div class="kpi-toolbar">
            <StatRange v-model:preset="ovPreset" v-model:range="ovRange" @change="loadOverviewKpi" />
            <span class="kpi-range" v-if="ovRangeText">{{ ovRangeText }}</span>
          </div>
          <!-- 第一排：所选区间的 4 个指标（悬停右侧问号可看计算公式） -->
          <div class="stat-grid">
            <div class="stat-card" v-for="k in kpiCards" :key="k.label">
              <div class="stat-value" :style="{ color: k.tone }">{{ k.value }}</div>
              <div class="stat-label">
                <span>{{ k.label }}</span>
                <el-tooltip placement="top" effect="dark" :show-after="100">
                  <template #content><div class="kpi-formula">{{ k.formula }}</div></template>
                  <el-icon class="kpi-help"><QuestionFilled /></el-icon>
                </el-tooltip>
              </div>
            </div>
          </div>
          <!-- 第二排：固定「本年累计」（不随上方区间变化） -->
          <div class="stat-grid">
            <div class="stat-card mini" v-for="y in ytdCards" :key="y.label">
              <div class="stat-value sm" :style="{ color: y.tone }">{{ y.value }}</div>
              <div class="stat-label">
                <span>{{ y.label }}</span>
                <el-tooltip placement="top" effect="dark" :show-after="100">
                  <template #content><div class="kpi-formula">{{ y.formula }}</div></template>
                  <el-icon class="kpi-help"><QuestionFilled /></el-icon>
                </el-tooltip>
              </div>
            </div>
          </div>
          <div class="chart-wrap">
            <div class="chart-caption" v-if="trendCaption">{{ trendCaption }}</div>
            <div id="dashTrendChart" class="chart"/>
            <div class="chart-empty" v-if="trendEmpty">该区间暂无数据</div>
          </div>
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
        <!-- ============ 当日单据量（2026-09-15 用户要求：卡片改为「当日销售单/退单/换货单/退货整理单」，
             并去掉"今天要处理"小标题与"超期应收"卡） ============ -->
        <div class="stat-grid">
          <div class="stat-card clickable" v-for="t in saleTodayCards" :key="t.label" @click="$router.push(t.path)">
            <div class="stat-value" style="color:var(--app-color-primary)">{{ t.count }}</div>
            <div class="stat-label">{{ t.label }}</div>
          </div>
        </div>

        <!-- 2026-09-15 用户要求：原「出库情况提示行」也已删除（该行曾挂在"什么没做"卡内，后独立成行） -->

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
          <!-- 顺序 = 使用频率（2026-09-15 用户要求改为：销售单 → 销售换货单 → 销售退单 → 退货整理 → 客户管理） -->
          <el-button v-if="hasMenu['InventorySale']" type="primary" size="small" text @click="$router.push('/inventory/sale')">销售单</el-button>
          <el-button v-if="hasMenu['SaleExchange']" type="primary" size="small" text @click="$router.push('/sale/exchange')">销售换货单</el-button>
          <el-button v-if="hasMenu['SaleReturn']" type="primary" size="small" text @click="$router.push('/sale/return')">销售退单</el-button>
          <el-button v-if="hasMenu['InventoryReturnSort']" type="primary" size="small" text @click="$router.push('/inventory/return-sort')">退货整理</el-button>
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
import { QuestionFilled } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { useRouter } from 'vue-router'
import request from '@/utils/request'
import { useUserStore } from '@/stores/user'
import { ProjectStatus, PhaseStatus, OutsourceOrderStatus, OutsourceOrderStatusLabel, OutsourceOrderStatusTag, MaterialOrderStatus, MaterialOrderStatusLabel, MaterialOrderStatusTag, accountTypeLabel } from '@/api/enums'
import { DocStatus, DocStatusLabel, DocStatusTag } from '@/api/common'
import { getDashboardPending, type DashboardPending } from '@/api/dashboard'
import MemoPanel from '@/views/memo/index.vue'
import StatRange from '@/components/StatRange.vue'

const router = useRouter()
const userStore = useUserStore()
// 2026-09-15 用户要求：「备忘录」置于 TAB 首位，并作为进首页时的默认选中项（原为「经营总览」）
const activeTab = ref('memo')

// ==================== 经营总览 ====================
const finSummary = ref<any>({})
const pending = ref<DashboardPending>({})
let trendChart: echarts.ECharts | null = null

async function loadOverview() {
  // 经营总览 KPI（第一排区间 4 指标 + 第二排固定本年）：不 await，与其余数据并行加载
  loadOverviewKpi()
  // ⚠️ 2026-09-15 修死键：原为 hasMenu['FinanceAnalysis']，该 route_name 在最新菜单里**已不存在**
  // （模板里的同一处 2026-09-14 已修为 AnalysisOverview，但**这里的取数判断漏了**）→ 导致
  // finSummary 永远取不到数：「经营总览」趋势图空白、「财务」TAB 卡片恒为 0.00。现与模板 gate 保持一致。
  if (hasMenu.value['AnalysisOverview']) {
    try { finSummary.value = await request.get<any, any>('/finance/analysis/summary') } catch { finSummary.value = {} }
  }
  try { pending.value = await getDashboardPending() } catch { pending.value = {} }
  await nextTick()
  if (activeTab.value === 'overview') setTimeout(renderTrend, 60)
}
async function loadPending() { try { pending.value = await getDashboardPending() } catch {} }

function fmtN(v?: any) { return v == null ? '0.00' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) }
// ==================== 经营总览 KPI（2026-09-15 改造，用户确认口径） ====================
// 第一排 = **所选区间**的 4 个指标（销售金额 / 采购支出 / 费用支出 / 净利润）；
// 第二排 = **固定本年**（1/1 ~ 今天）的同一批指标。
// 数据源：后端 /finance/analysis/overview-kpi（与「经营分析 → 利润表」同一批按天聚合，口径完全一致）。
// 原「本月 vs 上月」的 ▲▼ 涨跌角标按用户要求去掉（区间可自定义后，"上一期"无统一定义）。
const ovPreset = ref('today')            // 默认「今日」（用户确认）
const ovRange = ref<[string, string] | null>(null)
const ovData = ref<any>({ range: {}, kpi: {}, year: {} })
const OV_RANGE_LABELS: Record<string, string> = {
  yesterday: '昨日', today: '今日', week: '本周', month: '本月', quarter: '本季', year: '本年'
}
/** 卡片标题前缀：自定义区间 →「所选区间」，预设 → 其中文名 */
const ovPrefix = computed(() =>
  ovData.value.range?.preset === 'custom' ? '所选区间' : (OV_RANGE_LABELS[ovData.value.range?.preset] || ''))
/** 实际生效的区间（含首尾），显示在区间选择器右侧便于核对 */
const ovRangeText = computed(() => {
  const r = ovData.value.range || {}
  return r.start ? `${r.start} ~ ${r.end}` : ''
})
/** 曲线图占位标记：区间内无数据时显示"该区间暂无数据" */
const trendEmpty = ref(false)
/** 曲线图说明：起止 + 粒度；若后端把区间补足到 7 天则额外提示 */
const trendCaption = computed(() => {
  const s = ovData.value.series || {}
  if (!s.start) return ''
  const granular = s.granularity === 'month' ? '按月' : '按天'
  const extended = s.start !== ovData.value.range?.start
  return `趋势：${s.start} ~ ${s.end}（${granular}）` + (extended ? '，因区间不足 7 天已按 7 天显示' : '')
})
/** 悬停问号的公式说明：**口径变更时必须与后端注释同步修改** */
const OV_FORMULA: Record<string, string> = {
  sale: '销售金额 = 已审核销售单金额 − 销售退货金额 + 退货折损收款\n（销售按审核日、退货与折损按建单日归期）',
  purchase: '采购支出 = 已审核采购单金额 − 采购退货金额\n（采购按审核日、退货按建单日归期）\n注：采购入库属资产、不计入损益，故与净利润不互减',
  expense: '费用支出 = 已审核费用单金额（按费用日期归期）\n不含销售成本（销售成本已在净利润中扣减）',
  profit: '净利润 = 销售金额 − 销售成本 − 费用支出\n销售成本 = 销售出库成本 − 退货冲回成本\n（净销售数量 × 产品当前移动加权成本价）',
  noAuth: '无「进货业务」权限，不展示采购数据'
}
const kpiCards = computed(() => {
  const k = ovData.value.kpi || {}
  const p = ovPrefix.value
  const canPurchase = !!hasModule['purchase']   // 采购数据需「进货业务」权限（用户确认）
  return [
    { label: `${p}销售金额`, value: fmtN(k.saleAmount), tone: 'var(--app-color-success)', formula: OV_FORMULA.sale },
    {
      label: `${p}采购支出`, value: canPurchase ? fmtN(k.purchaseSpend) : '-', tone: 'var(--app-color-warning)',
      formula: canPurchase ? OV_FORMULA.purchase : OV_FORMULA.noAuth
    },
    { label: `${p}费用支出`, value: fmtN(k.expenseSpend), tone: 'var(--app-color-warning)', formula: OV_FORMULA.expense },
    {
      label: `${p}净利润`, value: fmtN(k.netProfit),
      tone: Number(k.netProfit) >= 0 ? 'var(--app-color-success)' : 'var(--app-color-danger)',
      formula: OV_FORMULA.profit
    },
  ]
})
const ytdCards = computed(() => {
  const y = ovData.value.year || {}
  const canPurchase = !!hasModule['purchase']
  const prefix = '本年 1 月 1 日 ~ 今天：'
  return [
    { label: '本年累计销售金额', value: fmtN(y.saleAmount), tone: 'var(--app-color-success)', formula: prefix + OV_FORMULA.sale },
    {
      label: '本年累计采购支出', value: canPurchase ? fmtN(y.purchaseSpend) : '-', tone: 'var(--app-color-warning)',
      formula: canPurchase ? prefix + OV_FORMULA.purchase : OV_FORMULA.noAuth
    },
    { label: '本年累计费用支出', value: fmtN(y.expenseSpend), tone: 'var(--app-color-warning)', formula: prefix + OV_FORMULA.expense },
    {
      label: '本年累计净利润', value: fmtN(y.netProfit),
      tone: Number(y.netProfit) >= 0 ? 'var(--app-color-success)' : 'var(--app-color-danger)',
      formula: prefix + OV_FORMULA.profit
    },
  ]
})
async function loadOverviewKpi() {
  if (!hasMenu.value['AnalysisOverview']) return
  if (ovPreset.value === 'custom' && !(ovRange.value?.length === 2)) return
  const params: any = { preset: ovPreset.value }
  if (ovPreset.value === 'custom' && ovRange.value?.length === 2) {
    params.start = ovRange.value[0]; params.end = ovRange.value[1]
  }
  try {
    ovData.value = await request.get<any, any>('/finance/analysis/overview-kpi', { params })
      || { range: {}, kpi: {}, year: {} }
  } catch { ovData.value = { range: {}, kpi: {}, year: {} } }
  // 曲线图随区间联动（延迟一帧等容器尺寸稳定，避免按 0 宽度布局）
  if (activeTab.value === 'overview') setTimeout(renderTrend, 60)
}
// 预设切换/日期变更的"清空 + 触发"逻辑已收口到 StatRange 组件，页面只需 loadOverviewKpi

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

/**
 * 趋势图（2026-09-15 改造）：**跟随上方统计区间**，4 条曲线与卡片口径完全一致
 * （销售金额 / 采购支出 / 费用支出 / 净利润）。
 * 粒度由后端决定：区间 ≤ 62 天按天、否则按月；且区间不足 7 天时后端按 7 天（含所选区间）返回。
 * 无「进货业务」权限 → **不画采购曲线**（与卡片一致，避免越权看采购数据）。
 */
function renderTrend() {
  const el = document.getElementById('dashTrendChart')
  if (!el) return
  const s = ovData.value.series || {}
  const pts: any[] = s.points || []
  const byMonth = s.granularity === 'month'
  const canPurchase = !!hasModule['purchase']
  const labels = pts.map((p: any) => (byMonth ? p.label : String(p.label).slice(5)))
  const num = (p: any, k: string) => Number(p[k]) || 0
  const KEYS = ['saleAmount', 'purchaseSpend', 'expenseSpend', 'netProfit']
  // 全为 0（或区间内根本没有数据）→ 不画空轴，改为"该区间暂无数据"占位
  trendEmpty.value = pts.length === 0 || pts.every((p: any) => KEYS.every((k) => num(p, k) === 0))
  trendChart = trendChart || echarts.init(el)
  if (trendEmpty.value) { trendChart.clear(); return }
  const series: any[] = [
    { name: '销售金额', type: 'bar', barMaxWidth: 28, itemStyle: { color: '#91cc75' }, data: pts.map((p: any) => num(p, 'saleAmount')) }
  ]
  if (canPurchase) {
    series.push({ name: '采购支出', type: 'bar', barMaxWidth: 28, itemStyle: { color: '#e6a23c' }, data: pts.map((p: any) => num(p, 'purchaseSpend')) })
  }
  series.push({ name: '费用支出', type: 'line', smooth: true, itemStyle: { color: '#f56c6c' }, data: pts.map((p: any) => num(p, 'expenseSpend')) })
  series.push({ name: '净利润', type: 'line', smooth: true, itemStyle: { color: '#5470c6' }, data: pts.map((p: any) => num(p, 'netProfit')) })
  // ⚠️ setOption 第二参必须 true（notMerge）：区间/粒度切换时曲线条数与 X 轴都会变，否则残留旧系列
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { top: 0, itemWidth: 12, itemHeight: 8, textStyle: { fontSize: 12 } },
    grid: { left: 60, right: 20, top: 30, bottom: 24 },
    xAxis: { type: 'category', data: labels },
    yAxis: { type: 'value' },
    series,
  }, true)
  trendChart.resize()
}
// Tab 切换后容器尺寸恢复再渲染，避免按 0 宽度布局
function onTabChange() {
  nextTick(() => setTimeout(() => {
    renderTrend()
  }, 60))
}

// 2026-09-15 用户要求：删除「当日销售构成」四图（原 pieData / drawChart / renderSaleCharts 及 4 个图表实例），
// 该区块口径（单据日期）与页面其它卡片（审核日）不同源，易误读；销售 TAB 改为"待办 + 业绩 + 停滞"结构。

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
/** 销售工作台（2026-09-15 改版）：只有「当日单据量」4 项（用户明确不要业绩区/沉默客户卡/超期应收卡/出库情况行） */
const saleWork = ref<any>({ todos: {} })
/**
 * 当日单据量卡（2026-09-15 用户口径：业务日期=今天、排除已作废；点击进对应列表）
 * ⚠️ 这不是"待办"而是"当日业务量"，故不再显示"去处理/已清空"提示。
 */
const saleTodayCards = computed(() => {
  const t = saleWork.value.todos || {}
  return [
    { label: '当日销售单', count: Number(t.saleOrderToday) || 0, path: '/inventory/sale' },
    { label: '当日销售退单', count: Number(t.saleReturnToday) || 0, path: '/sale/return' },
    { label: '当日销售换货单', count: Number(t.saleExchangeToday) || 0, path: '/sale/exchange' },
    { label: '当日退货整理单', count: Number(t.returnSortToday) || 0, path: '/inventory/return-sort' },
  ]
})
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

  // 默认激活「备忘录」（2026-09-15 用户要求，原为「经营总览」；财务区块仍按 AnalysisOverview 权限显隐）
  activeTab.value = 'memo'
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
      const [saleRes, cusRes, custAnRes, workRes] = await Promise.all([
        // 销售单总数：只取 total（pageSize=1，不再拉 200 条明细）
        request.get<any, any>('/inventory/sale/page', { params: { pageSize: 1 } }).catch(() => ({})),
        request.get<any, any>('/inventory/customer/page', { params: { pageSize: 200 } }).catch(() => ({})),
        // 本月客户 TOP5：服务端整月聚合（preset=month = 本月 1 日~今天），不再从"最近 200 张销售单"里筛
        request.get<any, any>('/customer/analysis', { params: { preset: 'month' } }).catch(() => ({})),
        // 销售工作台（2026-09-15）：**当日单据量** 4 项（销售单/退单/换货单/退货整理单）
        // ——原先"待审核销售单"要单独发一次分页请求，现由该接口一并返回
        request.get<any, any>('/dashboard/sale-workbench').catch(() => ({})),
      ])
      const mSum: any = custAnRes?.summary || {}
      // 销售工作台数据（服务端一次聚合；口径见 DashboardService.saleWorkbench 注释）
      saleWork.value = workRes && workRes.todos ? workRes : { todos: {} }
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
/* KPI 区间选择器（2026-09-15；选择器本体已收口到公共组件 StatRange） */
.kpi-toolbar { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; margin-bottom: 12px; }
.kpi-range { font-size: var(--app-font-sm); color: var(--app-text-secondary); }
/* 悬停问号：鼠标移上去显示计算公式 */
.kpi-help { margin-left: 4px; font-size: 13px; color: var(--app-text-secondary); vertical-align: -2px; cursor: help; }
.kpi-help:hover { color: var(--app-color-primary); }
.stat-card.mini { padding: 12px 16px; }
.stat-value.sm { font-size: 16px; font-weight: 700; }
.chart { width: 100%; height: 220px; margin-bottom: 16px; }
/* 曲线图容器：说明文字 + 无数据占位（2026-09-15） */
.chart-wrap { position: relative; }
.chart-caption { font-size: var(--app-font-sm); color: var(--app-text-secondary); margin-bottom: 6px; }
.chart-empty {
  position: absolute; left: 0; right: 0; top: 30px; bottom: 24px;
  display: flex; align-items: center; justify-content: center;
  color: var(--app-text-secondary); font-size: var(--app-font-sm); pointer-events: none;
}
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

/* 销售工作台（2026-09-15 改版）：当日单据量 4 卡 */

/* 2026-09-15：原「出库情况提示行」已按用户要求删除，相关 .warn-line 样式一并清理 */

/* 窄屏（≤768px）：快捷入口按钮会折成 2–3 行（委外加工 tab 有 11 个），固定底栏会长期占掉大片屏幕，
   故回退为"随内容滚动"（与改动前一致）。若要窄屏也固定，删掉这一条即可。 */
@media (max-width: 768px) {
  .quick-links { position: static; }
}
</style>

<style>
/* KPI 悬停公式说明（2026-09-15）：el-tooltip 的内容渲染在 body 下，scoped 样式盖不到，必须写成全局 */
.kpi-formula { max-width: 320px; line-height: 1.7; white-space: pre-line; }
</style>
