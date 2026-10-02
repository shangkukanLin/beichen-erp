<template>
  <div class="dashboard">
    <!-- ============ 首页 TAB 的三条对齐规则（2026-09-27 用户口径；① 由 ui-e2e-p14 浏览器断言，②③ 由 verify-dashboard-tab-menu.ps1 静态对齐 sys_menu） ============
         ① TAB ↔ 侧栏**一级目录**一一对应：页签名 = 目录名（`sys_menu` 里 `parent_id=0` 且 `menu_type='catalog'` 的 menu_name），
            顺序 = 目录 sort_order。仅两处例外，且都写进守卫的例外表里（不靠记忆）：
            · 「备忘录」TAB 不对应任何菜单（个人面板，接口走豁免前缀 /api/memo）；
            · 「基础数据 / 设置」两个目录没有 TAB（基础数据刻意不设 TAB：主数据按使用场景分散到各 TAB 的快捷入口）。
         ② 每个 TAB 的快捷入口 = 该目录的**当前子菜单，且严格只有它们**：标签同名、顺序同 sort_order、逐项对得上，
            不多不少。2026-09-27 用户口径「严格只有本目录子菜单」⇒ 跨目录入口（历史上来自「基础数据」的 13 颗，
            如物料类型管理 / 加工合同模板 / 委外仓库管理 / 产品管理 / 客户管理…）**已全部移除**，
            主数据统一从侧栏「基础数据」进入；各 TAB 的可见性门控（hasModule.*）也只认本目录子菜单
            （否则只授主数据的角色会看到一个"没有任何按钮"的空白 TAB）。
         ③ 由此 `menuNames` 白名单收敛为**本文件实际引用的键**：新增按钮必须同时登记（否则 hasMenu 恒 false、按钮永不渲染），
            删除按钮必须同时删键（守卫双向断言，见 verify-dashboard-tab-menu.ps1）。
         ====================================================================================================================================================== -->
    <el-tabs v-model="activeTab" type="border-card" @tab-change="onTabChange">
      <!-- 备忘录（2026-09-15 用户要求：排在首位，并作为进首页的默认 TAB） -->
      <el-tab-pane label="备忘录" name="memo">
        <memo-panel />
      </el-tab-pane>

      <!-- 经营分析（2026-09-27 用户要求：本 TAB 对应的菜单就是侧栏「经营分析」，故页签名与菜单名对齐；
           原页签名为「经营总览」—— 那是它早先只放财务 KPI 时的称呼。财务区块仍按 AnalysisOverview 菜单权限显隐。 -->
      <el-tab-pane label="经营分析" name="overview">
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
            <span style="float:right;font-size:var(--app-font-xs);color:var(--app-text-secondary)">
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
        <!-- 快捷入口（2026-09-27 用户口径「每个 TAB 的快捷方式与子菜单对齐」时补齐）：
             本 TAB 此前是**唯一没有快捷入口的模块 TAB**（只有 KPI + 待办）。现与本 TAB 对应的
             「经营分析」目录的 6 个子页**逐项同序**（经营概览 → 销售分析 → 客户分析 → 进货分析 → 税务分析 → 资金往来）。
             ⚠️ 其中 5 个 route_name（AnalysisSale/Customer/Purchase/Tax/Cash）必须同时进下方 menuNames 白名单，
             否则 hasMenu 恒为 false、按钮永不渲染（历史上采购换货单就踩过这个坑）。 -->
        <div class="quick-links">
          <span class="links-label">快捷入口：</span>
          <el-button v-if="hasMenu['AnalysisOverview']" type="primary" size="small" text @click="$router.push('/analysis/overview')">经营概览</el-button>
          <el-button v-if="hasMenu['AnalysisSale']" type="primary" size="small" text @click="$router.push('/analysis/sale')">销售分析</el-button>
          <el-button v-if="hasMenu['AnalysisCustomer']" type="primary" size="small" text @click="$router.push('/analysis/customer')">客户分析</el-button>
          <el-button v-if="hasMenu['AnalysisPurchase']" type="primary" size="small" text @click="$router.push('/analysis/purchase')">进货分析</el-button>
          <el-button v-if="hasMenu['AnalysisTax']" type="primary" size="small" text @click="$router.push('/analysis/tax')">税务分析</el-button>
          <el-button v-if="hasMenu['AnalysisCash']" type="primary" size="small" text @click="$router.push('/analysis/cash')">资金往来</el-button>
        </div>
      </el-tab-pane>

      <el-tab-pane v-if="hasModule['dev']" label="研发管理" name="dev">
        <div class="stat-grid">
          <div class="stat-card clickable" @click="$router.push('/dev/project?tab=active')">
            <div class="stat-value" style="color:#0C4A6E">{{ devTotal }}</div>
            <div class="stat-label">项目总数</div>
          </div>
          <div class="stat-card clickable" @click="$router.push('/dev/project?tab=active')">
            <div class="stat-value" style="color:var(--app-color-warning)">{{ devInProgress }}</div>
            <div class="stat-label">进行中</div>
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
          <!-- 严格 = **本目录子菜单**（2026-09-27 用户口径「严格只有本目录子菜单」）：「研发管理」3 项，按 sort 序逐项同序。
               原「物料类型管理 / 阶段模板」两颗属「基础数据」⇒ 已移除；主数据统一走侧栏「基础数据」进入。
               verify-dashboard-tab-menu.ps1 会断言"不得有任何跨目录按钮"。 -->
          <el-button v-if="hasMenu['DevProject']" type="primary" size="small" text @click="$router.push('/dev/project')">研发立项</el-button>
          <el-button v-if="hasMenu['DevMaterial']" type="primary" size="small" text @click="$router.push('/dev/material')">研发物料</el-button>
          <el-button v-if="hasMenu['DevScreenModel']" type="primary" size="small" text @click="$router.push('/dev/screen-model')">屏幕资料</el-button>
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
            <div class="stat-label">物料订单生产中</div>
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
                <div v-if="row.productSkus" style="font-size:var(--app-font-xs);color:var(--app-text-secondary)">{{ row.productSkus }}</div>
              </template>
            </el-table-column>
            <el-table-column label="金额" width="110" align="right">
              <template #default="{row}">{{ row.totalAmount ? Number(row.totalAmount).toFixed(2) : '-' }}</template>
            </el-table-column>
            <el-table-column label="计划开始" width="110"><template #default="{row}">{{ row.planStartDate || '-' }}</template></el-table-column>
            <el-table-column label="计划完成" width="110"><template #default="{row}">{{ row.planEndDate || '-' }}</template></el-table-column>
            <el-table-column label="最近收货" width="110"><template #default="{row}">{{ row.latestDeliveryDate || '-' }}</template></el-table-column>
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
            <el-table-column label="最近收货" width="100" align="center"><template #default="{row}">{{ row.lastDeliveryTime ? row.lastDeliveryTime.slice(0,10) : '-' }}</template></el-table-column>
            <el-table-column label="交期" width="100" align="center"><template #default="{row}">{{ row.deliveryDate || '-' }}</template></el-table-column>
            <el-table-column label="状态" width="80" align="center">
              <template #default="{row}"><el-tag :type="MaterialOrderStatusTag[row.status] || 'info'" size="small">{{ MaterialOrderStatusLabel[row.status] || row.status }}</el-tag></template>
            </el-table-column>
          </el-table>
        </el-card>

        <div class="quick-links">
          <span class="links-label">快捷入口：</span>
          <!-- 严格 = **本目录子菜单**（2026-09-27 用户口径）：「委外加工」6 项，按 sort 序逐项同序
               （加工订单 → 加工收退 → 加工退货 → 物料订单 → 物料收退 → 物料退货）。
               原「供货商管理 / 物料信息管理 / 加工合同模板」3 颗属「基础数据」⇒ 已移除，主数据走侧栏进入。
               注：2026-09-16 「物料收发单 / 物料其他出入库 / 物料报损 / 委外仓库 / 自有物料仓」5 项
               已随新一级菜单「物料仓库」迁到独立的「物料仓库」TAB -->
          <el-button v-if="hasMenu['OutsourceOrder']" type="primary" size="small" text @click="$router.push('/outsource/order')">加工订单</el-button>
          <!-- 加工收退 / 物料收退：2026-09-16 由订单详情页签移出成独立菜单页（顺序与左侧栏一致）；
               2026-09-28 用户口径：「成品收货」文案改「加工收货」；**2026-09-29 再改「加工收退」/「物料收退」**
               （该页既收货也退货 ⇒ 名字带上"退"；只改文案，功能不变） -->
          <el-button v-if="hasMenu['OutsourceOrderDelivery']" type="primary" size="small" text @click="$router.push('/outsource/order/delivery')">加工收退</el-button>
          <el-button v-if="hasMenu['OutsourceReturnOrder']" type="primary" size="small" text @click="$router.push('/outsource/return-order')">加工退货</el-button>
          <el-button v-if="hasMenu['OutsourceMaterialOrder']" type="primary" size="small" text @click="$router.push('/outsource/material-order')">物料订单</el-button>
          <el-button v-if="hasMenu['OutsourceMaterialOrderDelivery']" type="primary" size="small" text @click="$router.push('/outsource/material-order/delivery')">物料收退</el-button>
          <el-button v-if="hasMenu['OutsourceMaterialReturn']" type="primary" size="small" text @click="$router.push('/outsource/material-return')">物料退货</el-button>
        </div>
      </el-tab-pane>

      <!-- 物料仓库（2026-09-16 新增 TAB，对应侧栏一级目录「物料仓库」）。2026-10-02 用户口径「菜单物料仓库放在
           委外加工下面」⇒ 侧栏目录 sort_order 8→6，本 pane 随之挪回「委外加工」之后（2026-09-27 曾按当时的目录顺序
           移到「销售业务」之后）—— 页签顺序始终与侧栏目录 sort_order 严格一致，见文首「三条对齐规则」。
           快捷入口首块 = 该目录 6 个当前子菜单（物料移仓 → 物料库存详情 → 物料库存流水 → 物料库存盘点 →
           物料其他出入库 → 物料报损，逐项同序；2026-09-29 用户口径：其他出入库提到报损前面）；其后 2 个是跨目录补充入口：「委外仓库 / 自有物料仓」
           2026-09-22 起在侧栏归「基础数据」（仓库主数据），而首页无「基础数据」TAB
           （主数据按使用场景分散在各 TAB），故这两颗按钮仍留在本 TAB。

           2026-09-27（用户实测：「首页的 TAB 物料仓库，怎么是空白的？」）：本 TAB 建时**只做了快捷入口容器、
           零统计卡片**（原注释即"本模块暂无汇总统计卡片…另行补充"），其余 6 个 TAB 都以 4 张 stat-card 开场
           ⇒ 对比之下它就是个空白页。现按「成品库存」TAB 的形态补齐：4 张卡片 + 各仓库物料库存分布表。
           数据全部来自后端聚合 /dashboard/module-pages 的 materialWarehouse 块（读隔离口径，不前端直连物料各页接口），
           口径（只算物料行 / 委外仓+自有物料仓 / 数量取良品）见 DashboardService.materialWarehouseStat 注释。 -->
      <el-tab-pane v-if="hasModule['materialWarehouse']" label="物料仓库" name="materialWarehouse">
        <div class="stat-grid">
          <div class="stat-card" :class="{clickable:mwCanStock}" @click="mwGotoStock">
            <div class="stat-value" style="color:var(--app-color-primary)">{{ fmtQty(mwItemCount) }}</div>
            <div class="stat-label">库存品项数（良品）</div>
          </div>
          <div class="stat-card" :class="{clickable:mwCanStock}" @click="mwGotoStock">
            <div class="stat-value" style="color:var(--app-color-success)">{{ fmtQty(mwGoodQty) }}</div>
            <div class="stat-label">库存总数量（良品）</div>
          </div>
          <div class="stat-card" :class="{clickable:mwCanStock}" @click="mwGotoStock">
            <div class="stat-value" style="color:var(--app-color-warning)">{{ fmtQty(mwOnSiteQty) }}</div>
            <div class="stat-label">在厂维修物料</div>
          </div>
          <!-- 待处理单据：草稿态未审核（与 /dashboard/pending 的 counts 同口径）；不可点击 —— 三张单据三个入口，
               点一颗会误指，故只在卡片内列明明细 -->
          <div class="stat-card">
            <div class="stat-value" style="color:var(--app-color-danger)">{{ fmtQty(mwPendingDocs.total) }}</div>
            <div class="stat-label">待处理单据（草稿）</div>
            <div class="stat-sub">移仓 {{ fmtQty(mwPendingDocs.materialMove) }} · 报损 {{ fmtQty(mwPendingDocs.stockLoss) }} · 其他出入库 {{ fmtQty(mwPendingDocs.otherIo) }}</div>
          </div>
        </div>
        <el-card shadow="never" class="section-card" v-if="mwWhRows.length">
          <template #header><span class="section-title">各仓库物料库存分布</span></template>
          <el-table :data="mwWhRows" size="small" stripe>
            <el-table-column prop="warehouseName" label="仓库" min-width="140" show-overflow-tooltip>
              <template #default="{row}"><el-link type="primary" @click="goMaterialWarehouse(row)">{{ row.warehouseName }}</el-link></template>
            </el-table-column>
            <el-table-column prop="itemCount" label="物料数" width="90" align="center" />
            <el-table-column label="库存量（良品）" width="130" align="right"><template #default="{row}">{{ fmtQty(row.goodQuantity) }}</template></el-table-column>
            <el-table-column label="其中在厂维修" width="130" align="right"><template #default="{row}">{{ fmtQty(row.onSiteRepairQuantity) }}</template></el-table-column>
          </el-table>
        </el-card>
        <div class="quick-links">
          <span class="links-label">快捷入口：</span>
          <!-- 严格 = **本目录子菜单**（2026-09-27 用户口径；2026-09-29 用户口径「物料其他出入库放在物料报损前面」
               ⇒ 本块同步换位，与侧栏 sort_order 逐项同序：
               物料移仓 → 物料库存详情 → 物料库存流水 → 物料库存盘点 → 物料其他出入库 → 物料报损）。
               原「委外仓库管理 / 自有物料仓管理」2 颗属「基础数据」⇒ 已移除（它们与「成品仓库管理」共用
               route_name "Warehouse"，此前只能按路径判权限；移除后 `hasPath` 一并删除）。 -->
          <el-button v-if="hasMenu['InventoryMaterialMove']" type="primary" size="small" text @click="$router.push('/inventory/material-move')">物料移仓</el-button>
          <el-button v-if="hasMenu['OutsourceMaterialStock']" type="primary" size="small" text @click="$router.push('/outsource/material-stock')">物料库存详情</el-button>
          <el-button v-if="hasMenu['OutsourceMaterialStockLog']" type="primary" size="small" text @click="$router.push('/outsource/material-stock-log')">物料库存流水</el-button>
          <el-button v-if="hasMenu['OutsourceMaterialStockTake']" type="primary" size="small" text @click="$router.push('/outsource/material-stock-take')">物料库存盘点</el-button>
          <el-button v-if="hasMenu['OutsourceOtherIo']" type="primary" size="small" text @click="$router.push('/outsource/other-io')">物料其他出入库</el-button>
          <el-button v-if="hasMenu['OutsourceStockLoss']" type="primary" size="small" text @click="$router.push('/outsource/stock-loss')">物料报损</el-button>
        </div>
      </el-tab-pane>

      <el-tab-pane v-if="hasModule['purchase']" label="进货业务" name="purchase">
        <div class="stat-grid">
          <div class="stat-card clickable" @click="$router.push('/inventory/purchase')">
            <div class="stat-value" style="color:var(--app-color-primary)">{{ fmtN(purchaseMonthAmount) }}</div>
            <!-- 2026-09-20（F7-195）：后端 module-pages 固定分页 200 条，此处是"近 200 条"的合计而非全量；
                 原先文案未说明 ⇒ 超过 200 条时会静默偏小。改为自解释文案（长久方案应由后端给真聚合）。 -->
            <div class="stat-label">本月采购金额（近 200 条）</div>
          </div>
          <div class="stat-card clickable" @click="$router.push('/inventory/purchase-return')">
            <div class="stat-value" style="color:var(--app-color-warning)">{{ fmtN(purchaseReturnMonthAmount) }}</div>
            <div class="stat-label">本月采购退货（近 200 条）</div>
          </div>
          <div class="stat-card clickable" @click="$router.push('/inventory/purchase')">
            <div class="stat-value" style="color:var(--app-color-danger)">{{ purchasePending }}</div>
            <div class="stat-label">待审核采购单（近 200 条）</div>
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
          <!-- 严格 = **本目录子菜单**（2026-09-27 用户口径）：「进货业务」3 项，按 sort 序逐项同序
               （成品采购单 → 采购退货单 → 采购换货单）。原「供应商管理 / 供货商管理」2 颗属「基础数据」⇒ 已移除。
               采购换货单（504，2026-09-18 新增菜单）的 route_name 必须同时进下方 menuNames 白名单，
               否则 hasMenu 恒为 false、按钮永不渲染。 -->
          <el-button v-if="hasMenu['InventoryPurchase']" type="primary" size="small" text @click="$router.push('/inventory/purchase')">成品采购单</el-button>
          <el-button v-if="hasMenu['InventoryPurchaseReturn']" type="primary" size="small" text @click="$router.push('/inventory/purchase-return')">采购退货单</el-button>
          <el-button v-if="hasMenu['InventoryPurchaseExchange']" type="primary" size="small" text @click="$router.push('/inventory/purchase-exchange')">采购换货单</el-button>
        </div>
      </el-tab-pane>

      <el-tab-pane v-if="hasModule['sale']" label="销售业务" name="sale">
        <!-- ============ 当日单据量（2026-09-15 用户要求：卡片改为「当日销售单/退货单/换货单/退货整理单」，
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
                <span style="font-size:var(--app-font-xs);color:var(--app-text-secondary)">{{ row.pct }}%</span>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
        <div class="quick-links">
          <span class="links-label">快捷入口：</span>
          <!-- 严格 = **本目录子菜单**（2026-09-27 用户口径）：「销售业务」3 项，按 sort 序逐项同序
               （销售单 → 销售退货单 → 销售换货单）。原「客户管理」（基础数据）/「退货整理」（成品库存）2 颗已移除。
               退货整理仍是成品库存第 2 项，在其 TAB 与本菜单处进入。 -->
          <el-button v-if="hasMenu['InventorySale']" type="primary" size="small" text @click="$router.push('/inventory/sale')">销售单</el-button>
          <el-button v-if="hasMenu['SaleReturn']" type="primary" size="small" text @click="$router.push('/sale/return')">销售退货单</el-button>
          <el-button v-if="hasMenu['SaleExchange']" type="primary" size="small" text @click="$router.push('/sale/exchange')">销售换货单</el-button>
        </div>
      </el-tab-pane>

      <el-tab-pane v-if="hasModule['stock']" label="成品库存" name="stock">
        <div class="stat-grid">
          <div class="stat-card clickable" @click="$router.push('/inventory/product-stock')">
            <div class="stat-value" style="color:var(--app-color-primary)">{{ fmtN(stockTotalQty) }}</div>
            <div class="stat-label">库存总件数（近 200 条）</div>
          </div>
          <div class="stat-card clickable" @click="$router.push('/inventory/product-stock')">
            <div class="stat-value" style="color:var(--app-color-success)">{{ fmtN(stockTotalValue) }}</div>
            <div class="stat-label">库存总金额（近 200 条）</div>
          </div>
          <div class="stat-card clickable" @click="$router.push('/inventory/return-sort')">
            <div class="stat-value" style="color:var(--app-color-warning)">{{ fmtN(stockPendingQty) }}</div>
            <div class="stat-label">待整理件数</div>
          </div>
          <div class="stat-card clickable" @click="$router.push('/inventory/product-stock')">
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
            <el-button size="small" text style="float:right" @click="$router.push('/inventory/product-stock')">查看库存 →</el-button>
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
          <!-- 严格 = **本目录子菜单**（2026-09-27 用户口径）：「成品库存」8 项，严格按左侧栏 sort 序
               （移仓单 → 退货整理 → 规格调整 → 成品库存详情 → 成品库存流水 → 库存盘点 → 成品其他出入库 → 成品报损）。
               原「产品管理 / 成品仓库管理」2 颗属「基础数据」⇒ 已移除（后者与「委外仓库管理」共用 route_name
               "Warehouse"，此前只能按路径判权限；移除后 `hasPath` 一并删除）。
               注：①「成品库存」查询页 2026-09-18 下线（并入「成品库存详情」），快捷按钮一并移除
                   ②退货整理自 2026-09-18 起归「成品库存」菜单（其 707 原属销售业务），2026-09-22 起排第 2 位 -->
          <el-button v-if="hasMenu['InventoryWarehouseMove']" type="primary" size="small" text @click="$router.push('/inventory/warehouse-move')">移仓单</el-button>
          <el-button v-if="hasMenu['InventoryReturnSort']" type="primary" size="small" text @click="$router.push('/inventory/return-sort')">退货整理</el-button>
          <el-button v-if="hasMenu['InventoryReclassify']" type="primary" size="small" text @click="$router.push('/inventory/reclassify')">规格调整</el-button>
          <el-button v-if="hasMenu['InventoryProductStock']" type="primary" size="small" text @click="$router.push('/inventory/product-stock')">成品库存详情</el-button>
          <el-button v-if="hasMenu['WarehouseStockLog']" type="primary" size="small" text @click="$router.push('/inventory/stock-log')">成品库存流水</el-button>
          <el-button v-if="hasMenu['InventoryStockTake']" type="primary" size="small" text @click="$router.push('/inventory/stock-take')">库存盘点</el-button>
          <el-button v-if="hasMenu['InventoryOtherIo']" type="primary" size="small" text @click="$router.push('/inventory/other-io')">成品其他出入库</el-button>
          <el-button v-if="hasMenu['InventoryStockLoss']" type="primary" size="small" text @click="$router.push('/inventory/stock-loss')">成品报损</el-button>
        </div>
      </el-tab-pane>

      <el-tab-pane v-if="hasModule['finance']" label="财务管理" name="finance">
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
          <!-- 顺序（2026-09-22 用户要求：**以当前子菜单排序为准**）：与左侧栏「财务管理」10 项逐项一致
               （收款管理 → 付款管理 → 账单生成 → 费用管理 → 应收管理 → 应付管理 → 资金流水 → 账户管理 → 发票管理 → 应付转应收）；
               注：原「财务分析」按钮已删（其 route_name "FinanceAnalysis" 在最新菜单里不存在，是死键；经营分析在左侧菜单） -->
          <el-button v-if="hasMenu['FinanceReceipt']" type="primary" size="small" text @click="$router.push('/finance/receipt')">收款管理</el-button>
          <el-button v-if="hasMenu['FinancePayment']" type="primary" size="small" text @click="$router.push('/finance/payment')">付款管理</el-button>
          <el-button v-if="hasMenu['FinanceBill']" type="primary" size="small" text @click="$router.push('/finance/bill')">账单生成</el-button>
          <el-button v-if="hasMenu['FinanceExpense']" type="primary" size="small" text @click="$router.push('/finance/expense')">费用管理</el-button>
          <el-button v-if="hasMenu['FinanceReceivable']" type="primary" size="small" text @click="$router.push('/finance/receivable')">应收管理</el-button>
          <el-button v-if="hasMenu['FinancePayable']" type="primary" size="small" text @click="$router.push('/finance/payable')">应付管理</el-button>
          <el-button v-if="hasMenu['FinanceCashflow']" type="primary" size="small" text @click="$router.push('/finance/cashflow')">资金流水</el-button>
          <el-button v-if="hasMenu['FinanceAccount']" type="primary" size="small" text @click="$router.push('/finance/account')">账户管理</el-button>
          <el-button v-if="hasMenu['FinanceInvoice']" type="primary" size="small" text @click="$router.push('/finance/invoice')">发票管理</el-button>
          <el-button v-if="hasMenu['FinancePayableTransfer']" type="primary" size="small" text @click="$router.push('/finance/payable-transfer')">应付转应收</el-button>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { localDate, localMonth } from '@/utils/date'
import { ref, reactive, computed, onMounted, onActivated, onUnmounted, nextTick } from 'vue'
import { QuestionFilled } from '@element-plus/icons-vue'
// KPI 公式文案公共模块（2026-09-15）：与「经营分析 → 经营概览」共用一份，口径改动只需改这一处
import { KPI_FORMULA, YEAR_PREFIX, marginPct, fmtPct } from '@/utils/kpiFormula'
import * as echarts from 'echarts'
import { useRouter } from 'vue-router'
import request from '@/utils/request'
import { useUserStore } from '@/stores/user'
import { ProjectStatus, PhaseStatus, OutsourceOrderStatus, OutsourceOrderStatusLabel, OutsourceOrderStatusTag, MaterialOrderStatus, MaterialOrderStatusLabel, MaterialOrderStatusTag, accountTypeLabel, WarehouseCategory } from '@/api/enums'
import { DocStatus, DocStatusLabel, DocStatusTag } from '@/api/common'
import { getDashboardPending, type DashboardPending } from '@/api/dashboard'
import MemoPanel from '@/views/memo/index.vue'
import StatRange from '@/components/StatRange.vue'

const router = useRouter()
const userStore = useUserStore()
// 2026-09-15 用户要求：「备忘录」置于 TAB 首位，并作为进首页时的默认选中项（原为「经营总览」）
const activeTab = ref('memo')

// ==================== 经营分析（页签名，2026-09-27 由「经营总览」改） ====================
const finSummary = ref<any>({})
const pending = ref<DashboardPending>({})
let trendChart: echarts.ECharts | null = null

/**
 * F7-259（2026-09-30 审核批 G 修复）：**分页取全**，替代固定 `pageSize: 200` 的静默截断。
 *
 * <p>原先首页直接取 200 条就把"库存总值 / 总量 / 仓库分布 / 低库存预警"聚合完 —— 产品 > 200 时
 * `prodMap` 查不到成本价与安全库存（**总值低估 + 预警漏报**），库存行 > 200 时合计与分布直接少算，
 * 且页面无任何"数据不全"提示。改为按 `total` 翻页取全（上限 20 页 = 4000 行，防异常数据打爆首页）。</p>
 */
async function fetchAllRecords(path: string, params: Record<string, any> = {}): Promise<{ records: any[]; total: number }> {
  const size = 200
  const first = await request.get<any, any>(path, { params: { ...params, pageNum: 1, pageSize: size } }).catch(() => ({}))
  const total = Number(first?.total) || 0
  let records: any[] = Array.isArray(first?.records) ? first.records : []
  const pages = Math.min(Math.ceil(total / size), 20)
  for (let p = 2; p <= pages; p++) {
    const res = await request.get<any, any>(path, { params: { ...params, pageNum: p, pageSize: size } }).catch(() => ({}))
    if (Array.isArray(res?.records) && res.records.length) records = records.concat(res.records)
  }
  return { records, total }
}

async function loadOverview() {
  // 经营分析 KPI（第一排区间 4 指标 + 第二排固定本年）：不 await，与其余数据并行加载
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
// ==================== 经营分析 KPI（2026-09-15 改造，用户确认口径） ====================
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
// 悬停问号的公式文案已抽到公共模块 src/utils/kpiFormula.ts（2026-09-15，与「经营分析 → 经营概览」共用一份）
const kpiCards = computed(() => {
  const k = ovData.value.kpi || {}
  const p = ovPrefix.value
  const canPurchase = !!hasModule['purchase']   // 采购数据需「进货业务」权限（用户确认）
  return [
    { label: `${p}销售金额`, value: fmtN(k.saleAmount), tone: 'var(--app-color-success)', formula: KPI_FORMULA.sale },
    {
      label: `${p}采购支出`, value: canPurchase ? fmtN(k.purchaseSpend) : '-', tone: 'var(--app-color-warning)',
      formula: canPurchase ? KPI_FORMULA.purchase : KPI_FORMULA.noAuth
    },
    { label: `${p}费用支出`, value: fmtN(k.expenseSpend), tone: 'var(--app-color-warning)', formula: KPI_FORMULA.expense },
    {
      label: `${p}净利润`, value: fmtN(k.netProfit),
      tone: Number(k.netProfit) >= 0 ? 'var(--app-color-success)' : 'var(--app-color-danger)',
      formula: KPI_FORMULA.profit
    },
    // 净利率（2026-09-15 用户要求新增，与「经营分析 → 经营概览」口径一致；销售金额为 0 显示 "-"）
    {
      label: `${p}净利率`, value: fmtPct(marginPct(k.netProfit, k.saleAmount)),
      tone: Number(k.netProfit) >= 0 ? 'var(--app-color-success)' : 'var(--app-color-danger)',
      formula: KPI_FORMULA.margin
    },
  ]
})
const ytdCards = computed(() => {
  const y = ovData.value.year || {}
  const canPurchase = !!hasModule['purchase']
  const prefix = YEAR_PREFIX
  return [
    { label: '本年累计销售金额', value: fmtN(y.saleAmount), tone: 'var(--app-color-success)', formula: prefix + KPI_FORMULA.sale },
    {
      label: '本年累计采购支出', value: canPurchase ? fmtN(y.purchaseSpend) : '-', tone: 'var(--app-color-warning)',
      formula: canPurchase ? prefix + KPI_FORMULA.purchase : KPI_FORMULA.noAuth
    },
    { label: '本年累计费用支出', value: fmtN(y.expenseSpend), tone: 'var(--app-color-warning)', formula: prefix + KPI_FORMULA.expense },
    {
      label: '本年累计净利润', value: fmtN(y.netProfit),
      tone: Number(y.netProfit) >= 0 ? 'var(--app-color-success)' : 'var(--app-color-danger)',
      formula: prefix + KPI_FORMULA.profit
    },
    {
      label: '本年累计净利率', value: fmtPct(marginPct(y.netProfit, y.saleAmount)),
      tone: Number(y.netProfit) >= 0 ? 'var(--app-color-success)' : 'var(--app-color-danger)',
      formula: prefix + KPI_FORMULA.margin
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
  // 成品仓待盘点（口径已按范围收敛为"成品类仓库"：成品/不良/售后仓）
  if (take.pending) items.push({
    label: `${take.period || '本月'}待盘点仓库`, count: take.pending, path: '/inventory/stock-take',
    tone: take.overdue ? 'var(--app-color-danger)' : 'var(--app-color-warning)',
    sub: take.overdue ? `其中超期 ${take.overdue} 个` : `应盘日 ${take.period || ''}`,
  })
  // 物料仓待盘点（2026-09-16 新增，独立口径；无该菜单权限则不展示）
  const mt = pending.value.materialTake || {}
  if (mt.pending && hasMenu.value['OutsourceMaterialStockTake']) items.push({
    label: `${mt.period || '本月'}待盘点物料仓`, count: mt.pending, path: '/outsource/material-stock-take',
    tone: mt.overdue ? 'var(--app-color-danger)' : 'var(--app-color-warning)',
    sub: mt.overdue ? `其中超期 ${mt.overdue} 个` : `应盘日 ${mt.period || ''}`,
  })
  if (overdueSort) items.push({ label: '成品仓超期待整理', count: overdueSort, path: '/inventory/return-sort', tone: 'var(--app-color-danger)', sub: '停留超过 3 天' })
  if (overdueRec > 0) items.push({ label: '超期应收', count: fmtN(overdueRec), path: '/finance/receivable', tone: 'var(--app-color-danger)', sub: '已过到期日未收' })
  return items
})
const pendingCount = computed(() => todoItems.value.length)

/**
 * 趋势图（2026-09-15 改造）：**跟随上方统计区间**，曲线与卡片口径完全一致
 * （销售金额 / 采购支出 / 费用支出 / 净利润 + **净利率**）。
 * 粒度由后端决定：区间 ≤ 62 天按天、否则按月；且区间不足 7 天时后端按 7 天（含所选区间）返回。
 * 无「进货业务」权限 → **不画采购曲线**（与卡片一致，避免越权看采购数据）。
 * 净利率是百分比、与金额量纲不同 → **单独挂右侧 Y 轴**（yAxisIndex:1）。
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
  // 净利率（2026-09-15 用户要求新增）：挂右侧独立 Y 轴；该点销售金额为 0 → null（折线断开，避免误导性的 0%）
  series.push({
    name: '净利率', type: 'line', smooth: true, yAxisIndex: 1, connectNulls: false,
    itemStyle: { color: '#fac858' },
    data: pts.map((p: any) => {
      const sale = num(p, 'saleAmount')
      return sale === 0 ? null : Number(((num(p, 'netProfit') / sale) * 100).toFixed(2))
    })
  })
  const money = (v: any) => (v == null ? '-' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }))
  // ⚠️ setOption 第二参必须 true（notMerge）：区间/粒度切换时曲线条数与 X 轴都会变，否则残留旧系列
  trendChart.setOption({
    // 两条 Y 轴量纲不同 → tooltip 自定格式化（净利率带 %、金额千分位）
    tooltip: {
      trigger: 'axis',
      formatter: (ps: any) => {
        const arr = Array.isArray(ps) ? ps : [ps]
        let html = arr[0]?.axisValueLabel ?? arr[0]?.name ?? ''
        arr.forEach((it: any) => {
          const isPct = it.seriesName === '净利率'
          const v = it.value == null ? '-' : (isPct ? it.value + '%' : money(it.value))
          html += '<br/>' + it.marker + it.seriesName + '：' + v
        })
        return html
      }
    },
    legend: { top: 0, itemWidth: 12, itemHeight: 8, textStyle: { fontSize: 12 } },
    grid: { left: 60, right: 56, top: 30, bottom: 24 },
    xAxis: { type: 'category', data: labels },
    yAxis: [
      { type: 'value', name: '金额' },
      { type: 'value', name: '净利率', axisLabel: { formatter: '{value}%' }, splitLine: { show: false } }
    ],
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
// 该区块口径（单据日期）与页面其它卡片（当时为审核日）不同源，易误读；销售 TAB 改为"待办 + 业绩 + 停滞"结构。
// 注：2026-09-15 稍后全站归期已统一为**建单日**，故此处的"不同源"问题已不存在（但四图仍按用户要求保持删除）。

// 根据用户菜单权限判断可见模块
const hasMenu = ref<Record<string, boolean>>({})
// 2026-09-27（严格模式）：原 `hasPath`（按路由路径判权限）已随两颗仓库快捷入口一并删除 ——
// 它只是为了区分共用 route_name「Warehouse」的「委外仓库管理 / 成品仓库管理」；两者属「基础数据」，
// 首页不再提供跨目录入口 ⇒ 该绕行手段不再需要，`menuNames` 也已收敛为本文件实际引用的键（见守卫）。
const hasModule = reactive({ dev: false, outsource: false, purchase: false, sale: false, stock: false, finance: false, materialWarehouse: false })

// 统计卡片数据
const devTotal = ref(0)
const devInProgress = ref(0)
// devBomCount 已移除（2026-09-16：BOM管理菜单/总览页下线，仪表盘不再展示 BOM 总数）
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
    { label: '当日销售退货单', count: Number(t.saleReturnToday) || 0, path: '/sale/return' },
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
// 物料仓库 TAB（2026-09-27 补齐）：全部来自 /dashboard/module-pages 的 materialWarehouse 块
const mwItemCount = ref(0)
const mwGoodQty = ref(0)
const mwOnSiteQty = ref(0)
const mwPendingDocs = ref<any>({ materialMove: 0, stockLoss: 0, otherIo: 0, total: 0 })
const mwWhRows = ref<any[]>([])

/**
 * 首页表格名称点击跳转（2026-09-14 新增）
 * - 仓库 → 仓库详情 `/inventory/warehouse/detail/:id`（含物料/成品库存与流水入口）
 * - 产品 → 产品库存分布详情 `/inventory/product-stock/detail/:id`（只读，看该产品在各仓库的分布）
 * 两页均在路由表标记 `operate: true` → 不走菜单白名单，任何登录用户点击都不会被拦到 403。
 * id 为空时保持纯文本不跳转（护栏）。
 */
function goWarehouse(id?: number) { if (id) router.push(`/inventory/warehouse/detail/${id}`) }
function goProduct(id?: number) { if (id) router.push(`/inventory/product-stock/detail/${id}`) }

/**
 * 物料仓库 TAB（2026-09-27）。
 *
 * - `mwCanStock`：卡片是否可点（需「物料库存详情」权限；无权限时只读展示，避免点出 403 —— 本 TAB 也可能
 *   只被授予"物料报损"等单页权限而整体可见）；
 * - `goMaterialWarehouse`：分仓表的仓库名点击 —— **按仓库类别**选详情页：委外仓走
 *   `/outsource/warehouse/detail/:id`（委外仓库详情），自有物料仓（辅料仓）走
 *   `/inventory/warehouse/detail/:id`（仓库详情）。两页在路由表都标了 `operate: true`
 *   ⇒ 不走菜单白名单，任何登录用户点击都不会被拦到 403。
 */
const mwCanStock = computed(() => !!hasMenu.value['OutsourceMaterialStock'])
function mwGotoStock() { if (mwCanStock.value) router.push('/outsource/material-stock') }
function goMaterialWarehouse(row: any) {
  const id = Number(row?.warehouseId)
  if (!id) return
  router.push(String(row?.warehouseCategory) === WarehouseCategory.OUTSOURCE
    ? `/outsource/warehouse/detail/${id}` : `/inventory/warehouse/detail/${id}`)
}
/**
 * 数量格式化（整数）。**与成品库存卡片的 fmtN 不同**：库存量是件数，`fmtN` 会输出 "9,559.00"，
 * 与物料库存详情/仓库详情页的口径（数量一律整数，2026-09-16）不一致，故本区块用整数展示。
 */
function fmtQty(v?: any) { return v == null ? '0' : Number(v).toLocaleString('zh-CN') }
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
  const collect = (list: any[]) => {
    if (!list) return
    list.forEach((m: any) => {
      if (m.routeName) names.add(m.routeName)
      if (m.children) collect(m.children)
    })
  }
  collect(menus)

  // 可见模块判断（菜单推导）∩ 用户勾选（dashboardTabs；null=未配置=全部可见）
  //
  // ⚠️ 2026-09-27（严格模式，用户口径「严格只有本目录子菜单」）：门控里**只能出现本目录的子菜单**。
  // 此前 purchase 夹带 SupplierManage/OutsourceSupplierManage、sale 夹带 InventoryCustomer、
  // stock 夹带 Warehouse/ProductManage、materialWarehouse 夹带 OutsourceMaterialWarehouse
  // —— 都是「基础数据」的页面 ⇒ 只被授主数据的角色也会看到该 TAB，而严格模式下该 TAB 内**一颗按钮都没有**（又是空白 TAB）。
  // 现改为只认本目录子菜单；`verify-dashboard-tab-menu.ps1` 断言「门控键 ⊆ 该目录子菜单」，禁止再夹带。
  // 同批删除：原 `hasPath`（按路由路径判权限）与它的 `paths` 集合 —— 它只是为「委外仓库管理 / 成品仓库管理」
  // 两颗跨目录按钮绕开共用 route_name「Warehouse」串号用的，两颗按钮移除后已无用处。
  const tabs = userStore.dashboardTabs
  const tabAllowed = (key: string) => !tabs || tabs.includes(key)
  // 2026-09-16：BOM管理/图纸文档 菜单下线后，研发模块可见性只看「研发立项」
  hasModule.dev = names.has('DevProject') && tabAllowed('dev')
  hasModule.outsource = (names.has('OutsourceOrder') || names.has('OutsourceMaterialOrder')) && tabAllowed('outsource')
  hasModule.purchase = (names.has('InventoryPurchase') || names.has('InventoryPurchaseReturn')
    || names.has('InventoryPurchaseExchange')) && tabAllowed('purchase')
  hasModule.sale = (names.has('InventorySale') || names.has('SaleReturn')
    || names.has('SaleExchange')) && tabAllowed('sale')
  hasModule.stock = (names.has('InventoryWarehouseMove') || names.has('InventoryReturnSort')
    || names.has('InventoryReclassify') || names.has('InventoryProductStock') || names.has('WarehouseStockLog')
    || names.has('InventoryStockTake') || names.has('InventoryOtherIo') || names.has('InventoryStockLoss')) && tabAllowed('stock')
  hasModule.finance = (names.has('FinanceReceivable') || names.has('FinancePayable')) && tabAllowed('finance')
  // 物料仓库（2026-09-16 新增 TAB，对应侧栏目录「物料仓库」）：该目录 6 个子菜单任一可见即显示
  hasModule.materialWarehouse = (names.has('InventoryMaterialMove') || names.has('OutsourceMaterialStock')
    || names.has('OutsourceMaterialStockLog') || names.has('OutsourceMaterialStockTake')
    || names.has('OutsourceStockLoss') || names.has('OutsourceOtherIo')) && tabAllowed('materialWarehouse')

  // 快捷入口可见性（route_name 与 sys_menu 一致）
  //
  // ⚠️ 2026-09-27（严格模式）：本名单**收敛为本文件实际引用的键**，并与"每个 TAB 快捷入口 = 该目录子菜单"一一对应。
  // 守卫 verify-dashboard-tab-menu.ps1 **双向断言**：模板里 hasMenu[...] 引用的键必须都在名单里（否则按钮永不渲染）、
  // 名单里不得有死键（引用了才登记）。因此新增快捷入口必须同时登记，删除按钮必须同时删键。
  // 本次删除的 19 个死键：跨目录按钮移除后不再引用的 MaterialType / TemplateManage / ProductManage / InventoryBrand /
  // InventoryCustomer / SupplierManage / OutsourceSupplierManage / OutsourceMaterialInfo / OutsourceDelivery /
  // Warehouse / OutsourceMaterialWarehouse，以及首页从无引用的 Dashboard 与 7 个 System* （SystemSmart/SystemUser/
  // SystemSettings/SystemDataManage/SystemRole/SystemMenu/SystemClearData）。
  // 注：`FinanceAnalysis` 只在注释里作为"历史死键"出现（见 loadOverview），不在名单内。
  const menuNames = [
    // 经营分析（目录 10）
    'AnalysisOverview', 'AnalysisSale', 'AnalysisCustomer', 'AnalysisPurchase', 'AnalysisTax', 'AnalysisCash',
    // 研发管理（目录 3）
    'DevProject', 'DevMaterial', 'DevScreenModel',
    // 委外加工（目录 4）
    'OutsourceOrder', 'OutsourceOrderDelivery', 'OutsourceReturnOrder',
    'OutsourceMaterialOrder', 'OutsourceMaterialOrderDelivery', 'OutsourceMaterialReturn',
    // 物料仓库（目录 11）
    'InventoryMaterialMove', 'OutsourceMaterialStock', 'OutsourceMaterialStockLog',
    'OutsourceMaterialStockTake', 'OutsourceStockLoss', 'OutsourceOtherIo',
    // 进货业务（目录 5）
    'InventoryPurchase', 'InventoryPurchaseReturn', 'InventoryPurchaseExchange',
    // 销售业务（目录 6）
    'InventorySale', 'SaleReturn', 'SaleExchange',
    // 成品库存（目录 7）
    'InventoryWarehouseMove', 'InventoryReturnSort', 'InventoryReclassify', 'InventoryProductStock',
    'WarehouseStockLog', 'InventoryStockTake', 'InventoryOtherIo', 'InventoryStockLoss',
    // 财务管理（目录 8）
    'FinanceReceipt', 'FinancePayment', 'FinanceBill', 'FinanceExpense', 'FinanceReceivable', 'FinancePayable',
    'FinanceCashflow', 'FinanceAccount', 'FinanceInvoice', 'FinancePayableTransfer',
  ]
  menuNames.forEach(n => { hasMenu.value[n] = names.has(n) })
  // 默认激活「备忘录」（2026-09-15 用户要求，原为「经营总览」；财务区块仍按 AnalysisOverview 权限显隐）
  activeTab.value = 'memo'
}

async function loadStats() {
  // 期 1（读隔离，2026-09-19）：跨模块只读改为后端聚合 /dashboard/module-pages（后端按 perms 过滤），
  // 不再由前端直连 /inventory/purchase、/inventory/purchase-return、/outsource/order、/outsource/material-order。
  const aggRes: any = await request.get<any, any>('/dashboard/module-pages').catch(() => ({}))
  try {
    // 项目研发
    if (hasModule.dev) {
      // 期 1b（读隔离）：项目分页 + 阶段改由 /dashboard/module-pages 一次返回（原为 3 次跨页请求）
      const projPage: any = aggRes?.dev?.projectPage || {}
      const projTotal = projPage?.total || 0
      const allRecords = projPage?.records || []
      let inProgress = 0, finished = 0
      const activeProjects: any[] = []
      allRecords.forEach((p: any) => {
        if (p.status === ProjectStatus.IN_PROGRESS) { inProgress++; activeProjects.push(p) }
        else if (p.status === ProjectStatus.CLOSED) finished++
      })
      inProgressProjects.value = activeProjects.slice(0, 5)
      devTotal.value = projTotal
      devInProgress.value = inProgress
      devFinished.value = finished
      // 加载进行中项目的项目阶段（期 1b：由聚合接口一并返回，口径 = IN_PROGRESS 前 5 个）
      dashboardPhaseMap.value = aggRes?.dev?.phases || {}
    }
  } catch { /* ignore */}
  try {
    if (hasModule.outsource) {
      const allOrderRes = aggRes?.outsourceOrder || {}
      const allMatRes = aggRes?.materialOrder || {}
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
      const [supRes] = await Promise.all([
        request.get<any, any>('/supplier/page', { params: { pageSize: 1 } }).catch(() => ({})),
      ])
      const purRes = aggRes?.purchaseOrder || {}
      const retRes = aggRes?.purchaseReturn || {}
      const purchases = purRes?.records || []
      // 2026-09-20（F7-197）：同页已 import DocStatus，这里原先用 'AUDITED'/'DRAFT' 字符串字面量
      // ⇒ 与全库"枚举值存 code、统一用常量比较"的约定不一致（改常量值时这两处会被静默漏掉）。
      purchaseMonthAmount.value = purchases
        .filter((p: any) => p.status === DocStatus.AUDITED && (p.orderDate || '').startsWith(curMonth))
        .reduce((s: number, p: any) => s + (Number(p.totalAmount) || 0), 0)
      purchasePending.value = purchases.filter((p: any) => p.status === DocStatus.DRAFT).length
      recentPurchases.value = purchases.slice(0, 8)
      const rets = retRes?.records || []
      purchaseReturnMonthAmount.value = rets
        .filter((p: any) => p.status === DocStatus.AUDITED && (p.returnDate || '').startsWith(curMonth))
        .reduce((s: number, p: any) => s + (Number(p.totalAmount) || 0), 0)
      pendingPurchaseReturns.value = rets.filter((p: any) => p.status === DocStatus.DRAFT).slice(0, 5)
      supplierTotal.value = supRes?.total || 0
      purchaseTotal.value = purRes?.total || 0
    }
  } catch { /* ignore */}
  try {
    if (hasModule.sale) {
      // 期 1b（读隔离）：销售单总数与「本月客户 TOP5」改由 /dashboard/module-pages 返回；
      // 客户档案（共享基础数据）与销售工作台（本模块接口）仍直连。
      const [cusRes, workRes] = await Promise.all([
        request.get<any, any>('/inventory/customer/page', { params: { pageSize: 200 } }).catch(() => ({})),
        // 销售工作台（2026-09-15）：**当日单据量** 4 项（销售单/退货单/换货单/退货整理单）
        request.get<any, any>('/dashboard/sale-workbench').catch(() => ({})),
      ])
      const custAnRes: any = aggRes?.customerAnalysis || {}
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
      saleTotal.value = Number(aggRes?.sale?.total || 0)
    }
  } catch { /* ignore */}
  try {
    if (hasModule.stock) {
      const [prodRes, whRes, stkRes] = await Promise.all([
        // F7-259：产品与库存行**分页取全**（原先各取 200 条 ⇒ 成本/安全库存查不到、合计少算）
        fetchAllRecords('/product/page'),
        request.get<any, any>('/warehouse/page', { params: { pageSize: 200 } }).catch(() => ({})),
        fetchAllRecords('/warehouse/stock/product-stock/page'),
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
    // 物料仓库（2026-09-27）：卡片与分仓分布**只读后端聚合块**（aggRes 已在函数开头取回，零新增请求）。
    // 块缺失 = 该用户无物料仓库任一页面码 ⇒ 保持 0/空表（后端按 perms 过滤，前端不自行猜权限）。
    if (hasModule.materialWarehouse) {
      const mw: any = aggRes?.materialWarehouse
      if (mw) {
        mwItemCount.value = Number(mw.itemCount) || 0
        mwGoodQty.value = Number(mw.goodQuantity) || 0
        mwOnSiteQty.value = Number(mw.onSiteRepairQuantity) || 0
        mwPendingDocs.value = mw.pendingDocs || { materialMove: 0, stockLoss: 0, otherIo: 0, total: 0 }
        mwWhRows.value = mw.warehouses || []
      }
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
// 2026-09-20（F7-198）：首页在 keep-alive 内 ⇒ 从其它页返回时 onMounted 不再触发，首页会停在首次加载
// （"待办与预警"里的待审核单数/待盘点/超期待整理最明显：用户处理完单据回首页，数字仍不变）。
// 首页数据受**各模块操作**影响、无法靠脏标志精确置位，因此这里的口径是"每次进入都刷新"。
onActivated(async () => {
  checkUserMenus()
  await Promise.all([loadOverview(), loadStats()])
})
// 2026-09-20（F7-196）：首页趋势图实例从不释放（keep-alive 反复进出累积）⇒ 组件真正卸载时 dispose
onUnmounted(() => { trendChart?.dispose(); trendChart = null })
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
.stat-label { font-size: var(--app-font-base); color: var(--app-text-secondary); margin-top: 4px; }
/* 卡片副标题（2026-09-27 物料仓库「待处理单据」卡：三张单据的明细拆解） */
.stat-sub { font-size: var(--app-font-xs); color: var(--app-text-secondary); margin-top: 2px; }

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
.links-label { color: var(--app-text-secondary); font-size: var(--app-font-base); }

.section-card { margin-bottom: 16px; }
.section-title { font-weight: 600; font-size: var(--app-font-base); }

/* 经营分析（页签名，2026-09-27 由「经营总览」改） */
/* KPI 区间选择器（2026-09-15；选择器本体已收口到公共组件 StatRange） */
.kpi-toolbar { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; margin-bottom: 12px; }
.kpi-range { font-size: var(--app-font-base); color: var(--app-text-secondary); }
/* 悬停问号 .kpi-help 与 tooltip 内容 .kpi-formula 已提到全局样式 src/styles/index.css（2026-09-15）
   —— 原因：经营分析「经营概览」页也要用，写在某个页面的 style 里会出现"页面各自加载才生效"的样式缺失。 */
.stat-card.mini { padding: 12px 16px; }
.stat-value.sm { font-size: var(--app-font-num-sm); font-weight: 700; }
.chart { width: 100%; height: 220px; margin-bottom: 16px; }
/* 曲线图容器：说明文字 + 无数据占位（2026-09-15） */
.chart-wrap { position: relative; }
.chart-caption { font-size: var(--app-font-base); color: var(--app-text-secondary); margin-bottom: 6px; }
.chart-empty {
  position: absolute; left: 0; right: 0; top: 30px; bottom: 24px;
  display: flex; align-items: center; justify-content: center;
  color: var(--app-text-secondary); font-size: var(--app-font-base); pointer-events: none;
}
.todo-grid { display: flex; gap: 12px; flex-wrap: wrap; }
.todo-card {
  min-width: 150px; max-width: 200px; flex: 1;
  background: #f5f7fa; border-radius: 8px; padding: 14px 16px; text-align: center;
}
.todo-card.clickable { cursor: pointer; transition: box-shadow 0.2s; }
.todo-card.clickable:hover { box-shadow: 0 2px 8px rgba(0,0,0,0.1); }
.todo-value { font-size: var(--app-font-num); font-weight: 700; }
.todo-label { font-size: var(--app-font-base); color: var(--app-text-secondary); margin-top: 4px; }
.todo-sub { font-size: var(--app-font-xs); color: var(--app-text-secondary); margin-top: 2px; }

/* 销售工作台（2026-09-15 改版）：当日单据量 4 卡 */

/* 2026-09-15：原「出库情况提示行」已按用户要求删除，相关 .warn-line 样式一并清理 */

/* 窄屏（≤768px）：快捷入口按钮会折成 2–3 行（委外加工 tab 有 9 个），固定底栏会长期占掉大片屏幕，
   故回退为"随内容滚动"（与改动前一致）。若要窄屏也固定，删掉这一条即可。 */
@media (max-width: 768px) {
  .quick-links { position: static; }
}
</style>
