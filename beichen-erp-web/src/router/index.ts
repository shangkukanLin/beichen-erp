import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', requiresAuth: false }
  },
  {
    path: '/company-manage',
    name: 'CompanyManage',
    component: () => import('@/views/system/company.vue'),
    meta: { title: '公司管理', requiresAuth: true }
  },
  {
    path: '/',
    component: () => import('@/layout/index.vue'),
    redirect: '/dashboard',
    meta: { requiresAuth: true },
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '首页', requiresAuth: true }
      },
      {
        path: 'system/smart',
        name: 'SystemSmart',
        component: () => import('@/views/dev/placeholder.vue'),
        meta: { title: '智能管理', requiresAuth: true }
      },
      {
        path: 'system/settings',
        name: 'SystemSettings',
        component: () => import('@/views/system/settings/index.vue'),
        meta: { title: '系统信息', requiresAuth: true }
      },
      {
        path: 'system/data-manage',
        name: 'SystemDataManage',
        component: () => import('@/views/system/data-manage/index.vue'),
        meta: { title: '数据管理', requiresAuth: true }
      },
      {
        path: 'system/clear-data',
        name: 'SystemClearData',
        component: () => import('@/views/system/clear-data/index.vue'),
        meta: { title: '清空数据', requiresAuth: true }
      },
      {
        path: 'system/user',
        name: 'SystemUser',
        component: () => import('@/views/system/user/index.vue'),
        meta: { title: '用户管理', requiresAuth: true }
      },
      {
        path: 'system/role',
        name: 'SystemRole',
        component: () => import('@/views/system/role/index.vue'),
        meta: { title: '角色管理', requiresAuth: true }
      },
      {
        path: 'system/menu',
        name: 'SystemMenu',
        component: () => import('@/views/system/menu/index.vue'),
        meta: { title: '菜单管理', requiresAuth: true }
      },
      // /supplier/manage=供应商（方案商/加工厂/辅料商，基础数据106/委外加工409 入口）
      // /outsource/supplier/manage=供货商（成品商，进货业务503 入口）
      { path: 'supplier/manage', name: 'SupplierManage', component: () => import('@/views/supplier/manage.vue'), meta: { title: '供应商管理', requiresAuth: true } },
      { path: 'outsource/supplier/manage', name: 'OutsourceSupplierManage', component: () => import('@/views/supplier/manage.vue'), meta: { title: '供货商管理', requiresAuth: true } },
      // 供货商（成品商）详情与供应商详情分开：路径、页面标题、页签（无「供应物料」）均不同
      { path: 'outsource/supplier/detail/:id', name: 'OutsourceSupplierDetail', component: () => import('@/views/supplier/detail.vue'), meta: { title: '供货商详情', requiresAuth: true, operate: true } },
      // 供应商（已按类型分散到各业务模块菜单）
      {
        path: 'supplier/solution',
        name: 'SupplierSolution',
        component: () => import('@/views/supplier/index.vue'),
        meta: { title: '方案商', requiresAuth: true }
      },
      {
        path: 'supplier/factory',
        name: 'SupplierFactory',
        component: () => import('@/views/supplier/index.vue'),
        meta: { title: '委外加工厂', requiresAuth: true }
      },
      {
        path: 'supplier/product',
        name: 'SupplierProduct',
        component: () => import('@/views/supplier/index.vue'),
        meta: { title: '成品供应商', requiresAuth: true }
      },
      {
        path: 'supplier/material-supplier',
        name: 'SupplierMaterial',
        component: () => import('@/views/supplier/index.vue'),
        meta: { title: '辅料商', requiresAuth: true }
      },
      {
        path: 'supplier/detail/:id',
        name: 'SupplierDetail',
        component: () => import('@/views/supplier/detail.vue'),
        meta: { title: '供应商详情', requiresAuth: true, operate: true }
      },
      // 开发管理
      {
        path: 'dev/project',
        name: 'DevProject',
        component: () => import('@/views/dev/project/index.vue'),
        // 标题与菜单名保持一致（2026-09-16「研发项目」→「研发立项」，仅文案）
        meta: { title: '研发立项', requiresAuth: true }
      },
      {
        path: 'dev/project/add',
        name: 'DevProjectAdd',
        component: () => import('@/views/dev/project/add.vue'),
        meta: { title: '新增研发立项', requiresAuth: true, operate: true }
      },
      {
        path: 'dev/project/edit/:id',
        name: 'DevProjectEdit',
        component: () => import('@/views/dev/project/edit.vue'),
        meta: { title: '研发立项详情', requiresAuth: true, operate: true }
      },
      // BOM管理 总览页 2026-09-16 按用户要求下线（页面已删）：BOM 仍在「研发立项」编辑页的 BOM 页签内维护
      // 旧地址保留为重定向 —— 菜单下线＝收走前端白名单，不做重定向会让老书签直接吃 403
      { path: 'dev/bom', redirect: '/dev/project' },
      {
        path: 'dev/material-type',
        name: 'MaterialType',
        component: () => import('@/views/dev/material-type/index.vue'),
        meta: { title: '物料类型管理', requiresAuth: true }
      },
      // 物料类型（2026-09-15 由「BOM表类型」改名，表/接口/字段同步 material_type）：旧地址兼容重定向
      { path: 'dev/bom-type', redirect: '/dev/material-type' },
      // 模版管理（基础数据，2026-09-15 用户要求）：阶段模板管理 + 加工合同模板 合并为一个页面，用 TAB 区分。
      // 旧地址 /dev/phase-template 保留为重定向（书签/历史页签不失效，也不绕过菜单白名单），带 tab 参数直达对应页签
      { path: 'template', name: 'TemplateManage', component: () => import('@/views/template/index.vue'), meta: { title: '模版管理', requiresAuth: true } },
      { path: 'dev/phase-template', redirect: '/template?tab=phase' },
      // 图纸文档 总览页 2026-09-16 按用户要求下线（页面已删）：图纸仍在「研发立项」编辑页的 图纸 页签内维护
      { path: 'dev/drawing', redirect: '/dev/project' },
      {
        path: 'dev/material',
        name: 'DevMaterial',
        component: () => import('@/views/dev/material/index.vue'),
        meta: { title: '研发物料', requiresAuth: true }
      },
      {
        path: 'dev/material/detail/:id',
        name: 'DevMaterialDetail',
        component: () => import('@/views/dev/material/detail.vue'),
        meta: { title: '研发物料详情', requiresAuth: true, operate: true }
      },
      {
        // 屏幕资料（2026-09-16 由「屏幕资料知识库」改名，仅显示文案）：行业机型屏幕参数（清空数据不清理该库）
        path: 'dev/screen-model',
        name: 'DevScreenModel',
        component: () => import('@/views/dev/screen-model.vue'),
        meta: { title: '屏幕资料', requiresAuth: true }
      },
      // 委外加工
      {
        path: 'outsource/material-info',
        name: 'OutsourceMaterialInfo',
        component: () => import('@/views/outsource/material-info.vue'),
        meta: { title: '物料信息管理', requiresAuth: true }
      },
      {
        path: 'outsource/warehouse',
        name: 'OutsourceWarehouse',
        component: () => import('@/views/outsource/warehouse.vue'),
        meta: { title: '委外仓库管理', requiresAuth: true }
      },
      {
        path: 'outsource/warehouse/detail/:id',
        name: 'OutsourceWarehouseDetail',
        component: () => import('@/views/outsource/warehouse-detail.vue'),
        meta: { title: '委外仓库详情', requiresAuth: true, operate: true }
      },
      // 物料收发单（2026-09-24 用户口径「不要了，改用物料移仓代替」）：
      //   列表/新增页已删除，旧地址保留**重定向**到替代页（避免老书签吃 403，与 dev/bom、inventory/stock 同范式）；
      //   **详情页保留**：库存流水（成品/物料）与物料收货都用 /outsource/delivery/detail/:id 跳转，
      //   且该页面同时承载自动生成的收料/退不良单据（历史查询）。
      { path: 'outsource/delivery', redirect: '/inventory/material-move' },
      { path: 'outsource/delivery/add', redirect: '/inventory/material-move' },
      { path: 'outsource/delivery/detail/:id', name: 'OutsourceDeliveryDetail', component: () => import('@/views/outsource/delivery/detail.vue'), meta: { title: '物料收发单详情', requiresAuth: true, operate: true } },
      { path: 'outsource/material-history/:wid/:mid', name: 'OutsourceMaterialHistory', component: () => import('@/views/outsource/warehouse-material-history.vue'), meta: { title: '物料库存流水详情', requiresAuth: true, operate: true } },
      // 委外加工退货（2026-09-27 三级菜单：拆成 4 个叶子，共用一个工作台组件 —— 组件按 path 判定叶子，
      // 见 views/outsource/return-order/index.vue 的 leaf 注释；叶子菜单在 sys_menu 419 目录下）
      { path: 'outsource/return-order', name: 'OutsourceReturnOrder', component: () => import('@/views/outsource/return-order/index.vue'), meta: { title: '关联退货', requiresAuth: true } },
      { path: 'outsource/return-order/unlinked', name: 'OutsourceReturnOrderUnlinked', component: () => import('@/views/outsource/return-order/index.vue'), meta: { title: '无单退货', requiresAuth: true } },
      // 2026-09-27（用户口径）：新增无单加工退货**由弹窗改独立页面**（含 BOM 快照自动解析/可换版本）
      { path: 'outsource/return-order/unlinked/add', name: 'OutsourceReturnOrderUnlinkedAdd', component: () => import('@/views/outsource/return-order/unlinked-add.vue'), meta: { title: '新增无单加工退货', requiresAuth: true, operate: true } },
      // 叶子名带对象前缀：加工侧/物料侧都有维修退货，同名会让顶部标签栏出现两个「维修退货」（不可区分）
      { path: 'outsource/return-order/repair', name: 'OutsourceReturnOrderRepair', component: () => import('@/views/outsource/return-order/index.vue'), meta: { title: '成品维修退货', requiresAuth: true } },
      { path: 'outsource/return-back', name: 'OutsourceReturnBack', component: () => import('@/views/outsource/return-order/index.vue'), meta: { title: '加工返回单', requiresAuth: true } },
      { path: 'outsource/return-order/add', name: 'OutsourceReturnOrderAdd', component: () => import('@/views/outsource/return-order/add.vue'), meta: { title: '新增委外加工退货', requiresAuth: true, operate: true } },
      // E4：草稿编辑（复用新增页，仅 DRAFT 可编辑）
      { path: 'outsource/return-order/edit/:id', name: 'OutsourceReturnOrderEdit', component: () => import('@/views/outsource/return-order/add.vue'), meta: { title: '编辑委外加工退货', requiresAuth: true, operate: true } },
      { path: 'outsource/return-order/detail/:id', name: 'OutsourceReturnOrderDetail', component: () => import('@/views/outsource/return-order/detail.vue'), meta: { title: '委外加工退货详情', requiresAuth: true, operate: true } },
      // 委外物料退货（2026-09-27 三级菜单：拆成 3 个叶子 —— 关联退料 / 无单退料 / 物料维修退货，
      //   共用工作台组件，按 path 判叶子；关联/无单同 returnType=REFUND，靠 linked 参数区分；菜单在 sys_menu 423 下）
      { path: 'outsource/material-return', name: 'OutsourceMaterialReturn', component: () => import('@/views/outsource/material-return/index.vue'), meta: { title: '关联退料', requiresAuth: true } },
      { path: 'outsource/material-return/unlinked', name: 'OutsourceMaterialReturnUnlinked', component: () => import('@/views/outsource/material-return/index.vue'), meta: { title: '无单退料', requiresAuth: true } },
      { path: 'outsource/material-return/repair', name: 'OutsourceMaterialReturnRepair', component: () => import('@/views/outsource/material-return/index.vue'), meta: { title: '物料维修退货', requiresAuth: true } },
      { path: 'outsource/material-return/add', name: 'OutsourceMaterialReturnAdd', component: () => import('@/views/outsource/material-return/add.vue'), meta: { title: '新增委外物料退货', requiresAuth: true, operate: true } },
      { path: 'outsource/material-return/detail/:id', name: 'OutsourceMaterialReturnDetail', component: () => import('@/views/outsource/material-return/detail.vue'), meta: { title: '委外物料退货详情', requiresAuth: true, operate: true } },
      // D 档（2026-09-21）：草稿编辑（复用新增页，后端 PUT /{id} 仅允许草稿）—— 与加工退货页对称
      { path: 'outsource/material-return/edit/:id', name: 'OutsourceMaterialReturnEdit', component: () => import('@/views/outsource/material-return/add.vue'), meta: { title: '编辑委外物料退货', requiresAuth: true, operate: true } },
      // 销售退货单（售后：只退不换，退回货品入成品仓、品质为「待整理」，后续由退货整理单分流）
      { path: 'sale/return', name: 'SaleReturn', component: () => import('@/views/sale/return/index.vue'), meta: { title: '销售退货单', requiresAuth: true } },
      { path: 'sale/return/add', name: 'SaleReturnAdd', component: () => import('@/views/sale/return/add.vue'), meta: { title: '新增销售退货单', requiresAuth: true, operate: true } },
      { path: 'sale/return/detail/:id', name: 'SaleReturnDetail', component: () => import('@/views/sale/return/detail.vue'), meta: { title: '销售退货单详情', requiresAuth: true, operate: true } },
      // 销售出库详情（2026-09-24 新增：草稿态在详情页改+存；列表的「编辑」与原只读详情抽屉随之收进本页）。
      // 注：列表页 /sale/outbound 本身走菜单动态路由，这里只静态补详情页（与 sale/return/detail 同构）。
      { path: 'sale/outbound/detail/:id', name: 'SaleOutboundDetail', component: () => import('@/views/sale/outbound/detail.vue'), meta: { title: '销售出库详情', requiresAuth: true, operate: true } },
      // 销售换货（同品换货，强关联销售单：退回入售后仓 + 换出从成品仓扣减）
      { path: 'sale/exchange', name: 'SaleExchange', component: () => import('@/views/sale/exchange/index.vue'), meta: { title: '销售换货单', requiresAuth: true } },
      { path: 'sale/exchange/add', name: 'SaleExchangeAdd', component: () => import('@/views/sale/exchange/add.vue'), meta: { title: '新增/编辑销售换货单', requiresAuth: true, operate: true } },
      { path: 'sale/exchange/detail/:id', name: 'SaleExchangeDetail', component: () => import('@/views/sale/exchange/detail.vue'), meta: { title: '销售换货单详情', requiresAuth: true, operate: true } },
      // 物料管理 + 多级BOM
      {
        // 成品（product 表）统一使用 /product 前缀，与委外/研发物料（/outsource/material-*、/dev/material）区分
        path: 'product',
        name: 'ProductManage',
        component: () => import('@/views/material/index.vue'),
        meta: { title: '产品管理', requiresAuth: true }
      },
      // 成品（product 表）统一使用 /product 前缀：与委外物料（/outsource/material-*/dev/material）区分，避免语义混淆
      // detail 页即新增/编辑表单页（无 id 为新增），保存后回列表
      { path: 'product/detail/:id', name: 'ProductDetail', component: () => import('@/views/material/detail.vue'), meta: { title: '产品详情', requiresAuth: true, operate: true } },
      { path: 'product/add', name: 'ProductAdd', component: () => import('@/views/material/detail.vue'), meta: { title: '新增产品', requiresAuth: true, operate: true } },
      { path: 'inventory/brand', name: 'InventoryBrand', component: () => import('@/views/inventory/brand/index.vue'), meta: { title: '品牌管理', requiresAuth: true } },
      { path: 'outsource/order', name: 'OutsourceOrder', component: () => import('@/views/outsource/order/index.vue'), meta: { title: '委外加工单', requiresAuth: true } },
      { path: 'outsource/order/add', name: 'OutsourceOrderAdd', component: () => import('@/views/outsource/order/add.vue'), meta: { title: '新增加工单', requiresAuth: true, operate: true } },
      { path: 'outsource/order/detail/:id', name: 'OutsourceOrderDetail', component: () => import('@/views/outsource/order/detail.vue'), meta: { title: '委外加工单详情', requiresAuth: true, operate: true } },
      // 成品收货（2026-09-16）：原「加工订单详情 → 交货管理」页签移出独立成菜单页；
      // 列表只列正在加工的加工单，:id 页是该单的收货详细（新增/审核/退不良都在这里）
      { path: 'outsource/order/delivery', name: 'OutsourceOrderDelivery', component: () => import('@/views/outsource/order/delivery-list.vue'), meta: { title: '成品收货', requiresAuth: true } },
      // 2026-09-24（用户口径）：:id 页标题由「成品收货」改为「成品收货详情」—— 原先与列表页同名，
      //   两个页签都叫「成品收货」分不清；且详情页 PageShell 不传 title ⇒ 直接吃 meta.title，
      //   改这里会一并改掉 页头标题 / 页签 label / 浏览器标题。
      { path: 'outsource/order/delivery/:id', name: 'OutsourceOrderDeliveryDetail', component: () => import('@/views/outsource/order/delivery.vue'), meta: { title: '成品收货详情', requiresAuth: true, operate: true } },
      { path: 'outsource/order/close/:id', name: 'OutsourceOrderClose', component: () => import('@/views/outsource/order/close.vue'), meta: { title: '结单报表', requiresAuth: true, operate: true } },
      { path: 'outsource/material-order', name: 'OutsourceMaterialOrder', component: () => import('@/views/outsource/material-order/index.vue'), meta: { title: '委外物料订单', requiresAuth: true } },
      { path: 'outsource/material-order/add', name: 'OutsourceMaterialOrderAdd', component: () => import('@/views/outsource/material-order/add.vue'), meta: { title: '新增物料订单', requiresAuth: true, operate: true } },
      { path: 'outsource/material-order/add/:id', name: 'OutsourceMaterialOrderEdit', component: () => import('@/views/outsource/material-order/add.vue'), meta: { title: '编辑物料订单', requiresAuth: true, operate: true } },
      { path: 'outsource/material-order/detail/:id', name: 'OutsourceMaterialOrderDetail', component: () => import('@/views/outsource/material-order/detail.vue'), meta: { title: '物料订单详情', requiresAuth: true, operate: true } },
      // 物料收货（2026-09-16）：原「物料订单详情 → 交货管理」页签移出独立成菜单页；
      // 列表只列收货中的物料订单，:id 页是该单的收料/退不良详细
      { path: 'outsource/material-order/delivery', name: 'OutsourceMaterialOrderDelivery', component: () => import('@/views/outsource/material-order/delivery-list.vue'), meta: { title: '物料收货', requiresAuth: true } },
      // 2026-09-24（用户口径）：同成品收货侧，:id 页标题改为「物料收货详情」（原先与列表页同名）
      { path: 'outsource/material-order/delivery/:id', name: 'OutsourceMaterialOrderDeliveryDetail', component: () => import('@/views/outsource/material-order/delivery.vue'), meta: { title: '物料收货详情', requiresAuth: true, operate: true } },
      // 交货信息 总览页 2026-09-16 按用户要求下线（页面已删）：收货记录改在
      // 「加工订单详情 → 交货管理」与「物料订单详情 → 交货管理」页签内查看。旧地址重定向，避免老书签吃 403
      { path: 'outsource/delivery-info', redirect: '/outsource/order' },
      // 物料库存盘点（物料仓库，2026-09-16 新增）：与成品「库存盘点」共用面板，scope=MATERIAL 只盘物料仓
      { path: 'outsource/material-stock-take', name: 'OutsourceMaterialStockTake', component: () => import('@/views/outsource/material-stock-take/index.vue'), meta: { title: '物料库存盘点', requiresAuth: true } },
      // 物料库存详情（物料仓库，2026-09-21 新增，镜像成品侧「成品库存详情」；2026-09-22 由「物料库存情况」改名）：
      // 列表按物料汇总跨仓库库存（良品/不良两档），点行进详情看该物料在各仓库的分布。
      // ⚠️ 物料品质走 outsource.common.QualityType（GOOD/DEFECT），没有成品的 A/B/C/待整理；物料也没有安全库存。
      { path: 'outsource/material-stock', name: 'OutsourceMaterialStock', component: () => import('@/views/outsource/material-stock/index.vue'), meta: { title: '物料库存详情', requiresAuth: true } },
      // 物料库存分布详情：operate=true ⇒ 不走菜单白名单（任何登录用户点击都不会被拦到 403），与成品侧一致
      { path: 'outsource/material-stock/detail/:id', name: 'OutsourceMaterialStockDetail', component: () => import('@/views/outsource/material-stock/detail.vue'), meta: { title: '物料库存分布详情', requiresAuth: true, operate: true } },
      // 物料库存流水（物料仓库，2026-09-22 新增，镜像成品侧「成品库存流水」）：
      // 与成品流水**同一张表/同一接口**（warehouse_stock_log + /warehouse/stock/log），用 stockType=MATERIAL 只看物料行。
      // ⚠️ 仓库范围含**委外仓 + 自有物料仓**（实测物料流水 489/582 行发生在委外仓，只给自有仓会让页面大部分为空）。
      { path: 'outsource/material-stock-log', name: 'OutsourceMaterialStockLog', component: () => import('@/views/outsource/material-stock-log.vue'), meta: { title: '物料库存流水', requiresAuth: true } },
      // 加工合同模板已并入「基础数据 → 模版管理」（2026-09-15）；旧地址重定向到该页的「加工合同模板」页签
      { path: 'outsource/contract-template', redirect: '/template?tab=contract' },
      { path: 'outsource/other-io', name: 'OutsourceOtherIo', component: () => import('@/views/outsource/other-io/index.vue'), meta: { title: '物料其他出入库', requiresAuth: true } },
      // 物料报损：委外物料的报损单（与成品报损独立成表，库存按 仓库+物料 扣减）
      { path: 'outsource/stock-loss', name: 'OutsourceStockLoss', component: () => import('@/views/outsource/stock-loss/index.vue'), meta: { title: '物料报损', requiresAuth: true } },
      { path: 'outsource/stock-loss/add', name: 'OutsourceStockLossAdd', component: () => import('@/views/outsource/stock-loss/add.vue'), meta: { title: '新增物料报损单', requiresAuth: true, operate: true } },
      // 2026-09-24（用户口径）：`outsource/stock-loss/edit/:id` 已移除 —— 草稿态编辑统一在**详情页**内联完成。
      { path: 'outsource/stock-loss/detail/:id', name: 'OutsourceStockLossDetail', component: () => import('@/views/outsource/stock-loss/detail.vue'), meta: { title: '物料报损单详情', requiresAuth: true, operate: true } },
      { path: 'outsource/material-warehouse', name: 'OutsourceMaterialWarehouse', component: () => import('@/views/outsource/material-warehouse.vue'), meta: { title: '自有物料仓管理', requiresAuth: true } },
      { path: 'outsource/other-io/add', name: 'OutsourceOtherIoAdd', component: () => import('@/views/outsource/other-io/add.vue'), meta: { title: '新增物料其他出入库', requiresAuth: true, operate: true } },
      { path: 'outsource/other-io/detail/:id', name: 'OutsourceOtherIoDetail', component: () => import('@/views/outsource/other-io/detail.vue'), meta: { title: '物料其他出入库详情', requiresAuth: true, operate: true } },
      // 2026-09-24（用户口径）：`outsource/other-io/edit/:id` 已移除 —— 草稿态编辑统一在**详情页**内联完成
      // （列表那个"编辑/详细"动态按钮也统一成「详情」，组件 other-io/edit.vue 一并删除）。
      // 进销存
      // 成品库存详情（2026-09-22 由「成品库存情况」改名）：列表按产品汇总跨仓库库存，点行进详情看该产品在各仓库的分布
      { path: 'inventory/product-stock', name: 'InventoryProductStock', component: () => import('@/views/inventory/product-stock/index.vue'), meta: { title: '成品库存详情', requiresAuth: true } },
      { path: 'inventory/product-stock/detail/:id', name: 'InventoryProductStockDetail', component: () => import('@/views/inventory/product-stock/detail.vue'), meta: { title: '产品库存分布详情', requiresAuth: true, operate: true } },
      { path: 'inventory/warehouse', name: 'InventoryWarehouse', component: () => import('@/views/inventory/warehouse.vue'), meta: { title: '成品仓库管理', requiresAuth: true } },
      { path: 'inventory/warehouse/detail/:id', name: 'InventoryWarehouseDetail', component: () => import('@/views/inventory/warehouse-detail.vue'), meta: { title: '仓库详情', requiresAuth: true, operate: true } },
      { path: 'inventory/warehouse/product-history/:wid/:pid', name: 'InventoryProductHistory', component: () => import('@/views/inventory/warehouse-product-history.vue'), meta: { title: '产品库存流水详情', requiresAuth: true, operate: true } },
      { path: 'inventory/warehouse/material-history/:wid/:mid', name: 'InventoryMaterialHistory', component: () => import('@/views/inventory/warehouse-material-history.vue'), meta: { title: '物料库存流水详情', requiresAuth: true, operate: true } },
      { path: 'inventory/other-io', name: 'InventoryOtherIo', component: () => import('@/views/inventory/other-io/index.vue'), meta: { title: '成品其他出入库', requiresAuth: true } },
      { path: 'inventory/other-io/add', name: 'InventoryOtherIoAdd', component: () => import('@/views/inventory/other-io/add.vue'), meta: { title: '新增成品其他出入库', requiresAuth: true, operate: true } },
      { path: 'inventory/other-io/detail/:id', name: 'InventoryOtherIoDetail', component: () => import('@/views/inventory/other-io/detail.vue'), meta: { title: '成品其他出入库详情', requiresAuth: true, operate: true } },
      // 规格调整（2026-09-22 用户要求：菜单名由「成品品质重分类」改为「规格调整」，并由第 8 位挪到第 3 位；
      // 路由 path/route_name 与 perms 均不变）
      { path: 'inventory/reclassify', name: 'InventoryReclassify', component: () => import('@/views/inventory/reclassify/index.vue'), meta: { title: '规格调整', requiresAuth: true } },
      // 规格调整：新增与详情拆成独立页面（与成品其他出入库一致，详情草稿态直接可编辑）
      { path: 'inventory/reclassify/add', name: 'InventoryReclassifyAdd', component: () => import('@/views/inventory/reclassify/add.vue'), meta: { title: '新增规格调整', requiresAuth: true, operate: true } },
      { path: 'inventory/reclassify/detail/:id', name: 'InventoryReclassifyDetail', component: () => import('@/views/inventory/reclassify/detail.vue'), meta: { title: '规格调整详情', requiresAuth: true, operate: true } },
      { path: 'inventory/return-sort', name: 'InventoryReturnSort', component: () => import('@/views/inventory/return-sort/index.vue'), meta: { title: '退货整理', requiresAuth: true } },
      // 退货整理：新增走独立页面；**详情页即草稿编辑面**（2026-09-24 用户口径 ⇒ 已删 `edit/:id` 路由，
      // 原 form.vue 的编辑分支不再可达、仅新增使用）
      { path: 'inventory/return-sort/add', name: 'InventoryReturnSortAdd', component: () => import('@/views/inventory/return-sort/form.vue'), meta: { title: '新增退货整理', requiresAuth: true, operate: true } },
      { path: 'inventory/return-sort/detail/:id', name: 'InventoryReturnSortDetail', component: () => import('@/views/inventory/return-sort/detail.vue'), meta: { title: '退货整理详情', requiresAuth: true, operate: true } },
      { path: 'inventory/stock-take', name: 'InventoryStockTake', component: () => import('@/views/inventory/stock-take/index.vue'), meta: { title: '库存盘点', requiresAuth: true } },
      { path: 'inventory/warehouse-move', name: 'InventoryWarehouseMove', component: () => import('@/views/inventory/warehouse-move/index.vue'), meta: { title: '移仓单', requiresAuth: true } },
      { path: 'inventory/warehouse-move/add', name: 'InventoryWarehouseMoveAdd', component: () => import('@/views/inventory/warehouse-move/add.vue'), meta: { title: '新增移仓单', requiresAuth: true, operate: true } },
      // 移仓单详情独立成页（与成品其他出入库一致，草稿态直接可编辑，列表不再有「编辑」）
      { path: 'inventory/warehouse-move/detail/:id', name: 'InventoryWarehouseMoveDetail', component: () => import('@/views/inventory/warehouse-move/detail.vue'), meta: { title: '移仓单详情', requiresAuth: true, operate: true } },
      // 物料移仓单（2026-09-24 新增）：按成品移仓单同构复刻，用于替代原「物料收发单」的手工发料/调拨
      { path: 'inventory/material-move', name: 'InventoryMaterialMove', component: () => import('@/views/inventory/material-move/index.vue'), meta: { title: '物料移仓', requiresAuth: true } },
      { path: 'inventory/material-move/add', name: 'InventoryMaterialMoveAdd', component: () => import('@/views/inventory/material-move/add.vue'), meta: { title: '新增物料移仓单', requiresAuth: true, operate: true } },
      { path: 'inventory/material-move/detail/:id', name: 'InventoryMaterialMoveDetail', component: () => import('@/views/inventory/material-move/detail.vue'), meta: { title: '物料移仓详情', requiresAuth: true, operate: true } },
      { path: 'inventory/material', redirect: '/product' },
      { path: 'inventory/customer', name: 'InventoryCustomer', component: () => import('@/views/customer/index.vue'), meta: { title: '客户管理', requiresAuth: true } },
      // 客户新增与详情共用同一表单页（详情内可直接编辑保存），title 区分
      { path: 'inventory/customer/detail/:id', name: 'InventoryCustomerDetail', component: () => import('@/views/customer/detail.vue'), meta: { title: '客户详情', requiresAuth: true, operate: true } },
      { path: 'inventory/customer/add', name: 'InventoryCustomerAdd', component: () => import('@/views/customer/detail.vue'), meta: { title: '新增客户', requiresAuth: true, operate: true } },
      { path: 'inventory/purchase', name: 'InventoryPurchase', component: () => import('@/views/purchase/order/index.vue'), meta: { title: '成品采购单', requiresAuth: true }
      }, {
        path: 'inventory/purchase/add',
        name: 'InventoryPurchaseAdd',
        component: () => import('@/views/purchase/order/add.vue'),
        meta: { title: '新增成品采购单', requiresAuth: true, operate: true } },
      { path: 'inventory/purchase/detail/:id',
        name: 'InventoryPurchaseDetail',
        component: () => import('@/views/purchase/order/detail.vue'),
        meta: { title: '采购单详情', requiresAuth: true, operate: true } },
      { path: 'inventory/purchase-return', name: 'InventoryPurchaseReturn', component: () => import('@/views/purchase/return/index.vue'), meta: { title: '采购退货单', requiresAuth: true }
      }, {
        path: 'inventory/purchase-return/add',
        name: 'InventoryPurchaseReturnAdd',
        component: () => import('@/views/purchase/return/add.vue'),
        meta: { title: '新增采购退货单', requiresAuth: true, operate: true } },
      { path: 'inventory/purchase-return/detail/:id', name: 'InventoryPurchaseReturnDetail', component: () => import('@/views/purchase/return/detail.vue'), meta: { title: '采购退货单详情', requiresAuth: true, operate: true } },
      // 采购换货单（进货业务：把采购成品退回供货商换新；同品换货、强关联采购单）
      { path: 'inventory/purchase-exchange', name: 'InventoryPurchaseExchange', component: () => import('@/views/purchase/exchange/index.vue'), meta: { title: '采购换货单', requiresAuth: true }
      }, {
        path: 'inventory/purchase-exchange/add',
        name: 'InventoryPurchaseExchangeAdd',
        component: () => import('@/views/purchase/exchange/add.vue'),
        meta: { title: '新增采购换货单', requiresAuth: true, operate: true } },
      { path: 'inventory/purchase-exchange/detail/:id', name: 'InventoryPurchaseExchangeDetail', component: () => import('@/views/purchase/exchange/detail.vue'), meta: { title: '采购换货单详情', requiresAuth: true, operate: true } },
      // 成品库存查询页 2026-09-18 按用户要求下线（页面文件已删）：按产品维度的库存查看改用「成品库存详情」。
      // 旧地址保留为重定向（与 dev/bom、dev/drawing、outsource/delivery-info 同范式）——菜单下线＝收走前端
      // 白名单，不做重定向会让老书签/首页历史链接直接吃 403。
      { path: 'inventory/stock', redirect: '/inventory/product-stock' },
      // 成品报损：列表 / 新增 / 编辑（与新增同页）/ 详情
      { path: 'inventory/stock-loss', name: 'InventoryStockLoss', component: () => import('@/views/inventory/stock-loss/index.vue'), meta: { title: '成品报损', requiresAuth: true } },
      { path: 'inventory/stock-loss/add', name: 'InventoryStockLossAdd', component: () => import('@/views/inventory/stock-loss/add.vue'), meta: { title: '新增成品报损单', requiresAuth: true, operate: true } },
      // 2026-09-24（用户口径）：`inventory/stock-loss/edit/:id` 已移除 —— 草稿态编辑统一在**详情页**内联完成。
      { path: 'inventory/stock-loss/detail/:id', name: 'InventoryStockLossDetail', component: () => import('@/views/inventory/stock-loss/detail.vue'), meta: { title: '成品报损单详情', requiresAuth: true, operate: true } },
      { path: 'inventory/stock-log', name: 'InventoryStockLog', component: () => import('@/views/inventory/stock-log.vue'), meta: { title: '成品库存流水', requiresAuth: true } },
      { path: 'inventory/sale', name: 'InventorySale', component: () => import('@/views/sale/order/index.vue'), meta: { title: '销售单', requiresAuth: true } },
      // 新增/编辑销售单独立页面（带 ?id= 为编辑），与销售退货单的 add 页模式保持一致
      { path: 'inventory/sale/add', name: 'SaleOrderAdd', component: () => import('@/views/sale/order/add.vue'), meta: { title: '新增销售单', requiresAuth: true, operate: true } },
      { path: 'inventory/sale/detail/:id', name: 'SaleOrderDetail', component: () => import('@/views/sale/order/detail.vue'), meta: { title: '销售单详情', requiresAuth: true, operate: true } },
      // 经营分析（目录置于首页之下）：原「财务分析」拆分 + 销售分析 / 客户分析
      { path: 'analysis/overview', name: 'AnalysisOverview', component: () => import('@/views/analysis/overview.vue'), meta: { title: '经营概览', requiresAuth: true } },
      { path: 'analysis/cash', name: 'AnalysisCash', component: () => import('@/views/analysis/cash.vue'), meta: { title: '资金往来', requiresAuth: true } },
      { path: 'analysis/tax', name: 'AnalysisTax', component: () => import('@/views/analysis/tax.vue'), meta: { title: '税务分析', requiresAuth: true } },
      { path: 'analysis/sale', name: 'AnalysisSale', component: () => import('@/views/analysis/sale.vue'), meta: { title: '销售分析', requiresAuth: true } },
      // 销售分析钻取：某产品/仓库在区间的销售单明细（operate 页，不入菜单）
      { path: 'analysis/sale/detail', name: 'AnalysisSaleDetail', component: () => import('@/views/analysis/sale/detail.vue'), meta: { title: '销售单明细', requiresAuth: true, operate: true } },
      { path: 'analysis/customer', name: 'AnalysisCustomer', component: () => import('@/views/analysis/customer.vue'), meta: { title: '客户分析', requiresAuth: true } },
      // 进货分析（2026-09-15 新增；2026-09-22 起第 ④ 块为「供货商分析」列表）：区间采购 KPI + 趋势 + 两个饼图 + 供货商分析
      { path: 'analysis/purchase', name: 'AnalysisPurchase', component: () => import('@/views/analysis/purchase.vue'), meta: { title: '进货分析', requiresAuth: true } },
      // 单供货商分析（2026-09-22 用户要求）：进货分析列表点供货商名进入（operate 页，不入菜单）
      { path: 'analysis/purchase/supplier/:id', name: 'AnalysisPurchaseSupplier', component: () => import('@/views/analysis/purchase/supplier.vue'), meta: { title: '单供货商分析', requiresAuth: true, operate: true } },
      // 单客户分析：客户分析页点客户名进入（档案 + 图表 + 拿货/品牌/退货明细）
      { path: 'analysis/customer/:id', name: 'AnalysisCustomerProfile', component: () => import('@/views/analysis/customer/profile.vue'), meta: { title: '单客户分析', requiresAuth: true, operate: true } },
      // 客户分析钻取：某客户在区间的销售单明细
      { path: 'analysis/customer/detail', name: 'AnalysisCustomerDetail', component: () => import('@/views/analysis/customer/detail.vue'), meta: { title: '客户销售单明细', requiresAuth: true, operate: true } },
      // 财务管理
      { path: 'finance/receivable', name: 'FinanceReceivable', component: () => import('@/views/finance/receivable.vue'), meta: { title: '应收管理', requiresAuth: true } },
      { path: 'finance/payable', name: 'FinancePayable', component: () => import('@/views/finance/payable.vue'), meta: { title: '应付管理', requiresAuth: true } },
      // 应收/应付详情独立成页（2026-09-23 用户要求：全站抽屉详情改独立界面，原为 50% 抽屉）
      { path: 'finance/receivable/detail/:id', name: 'FinanceReceivableDetail', component: () => import('@/views/finance/receivable/detail.vue'), meta: { title: '应收详情', requiresAuth: true, operate: true } },
      { path: 'finance/payable/detail/:id', name: 'FinancePayableDetail', component: () => import('@/views/finance/payable/detail.vue'), meta: { title: '应付详情', requiresAuth: true, operate: true } },
      { path: 'finance/receipt/detail/:id', name: 'FinanceReceiptDetail', component: () => import('@/views/finance/receipt/detail.vue'), meta: { title: '收款单详情', requiresAuth: true, operate: true } },
      // 新增收款独立成页（2026-09-23 原为 850px 弹框，含核销明细）
      { path: 'finance/receipt/add', name: 'FinanceReceiptAdd', component: () => import('@/views/finance/receipt-add.vue'), meta: { title: '新增收款单', requiresAuth: true, operate: true } },
      { path: 'finance/payment/detail/:id', name: 'FinancePaymentDetail', component: () => import('@/views/finance/payment/detail.vue'), meta: { title: '付款单详情', requiresAuth: true, operate: true } },
      // 加工退货记录详情独立成页（2026-09-23 原为 580px 抽屉）；注意与「维修退货」的
      // outsource/return-order/detail/:id 区分，故另起 defect-return 路径
      { path: 'outsource/defect-return/detail/:id', name: 'OutsourceDefectReturnDetail', component: () => import('@/views/outsource/defect-return/detail.vue'), meta: { title: '加工退货详情', requiresAuth: true, operate: true } },
      // 成品收货「收货记录」详情独立成页（2026-09-23 原为 60% 抽屉）；
      // ⚠️ 不能叫 outsource/order/delivery/detail/:id —— 该前缀已被「收货单页」占用（outsource/order/delivery/:id）
      { path: 'outsource/order/delivery/record/:id', name: 'OutsourceOrderDeliveryRecord', component: () => import('@/views/outsource/order/delivery-record.vue'), meta: { title: '收货记录详情', requiresAuth: true, operate: true } },
      // 加工退货（拆分还料）独立成页（2026-09-23 原为 780px 弹框）；orderId 走路径
      { path: 'outsource/order/delivery/return-defect/:orderId', name: 'OutsourceOrderReturnDefect', component: () => import('@/views/outsource/order/return-defect.vue'), meta: { title: '加工退货（拆分还料）', requiresAuth: true, operate: true } },
      // 往来单位新增/编辑独立成页（2026-09-23 原为 640px 弹框）：供货商与供应商两种模式共用组件，
      // 模式由路径前缀判定（与列表页 supplier/manage.vue 同一口径）
      { path: 'supplier/manage/add', name: 'SupplierManageAdd', component: () => import('@/views/supplier/manage-form.vue'), meta: { title: '新增供应商', requiresAuth: true, operate: true } },
      { path: 'supplier/manage/edit/:id', name: 'SupplierManageEdit', component: () => import('@/views/supplier/manage-form.vue'), meta: { title: '编辑供应商', requiresAuth: true, operate: true } },
      { path: 'outsource/supplier/manage/add', name: 'VendorManageAdd', component: () => import('@/views/supplier/manage-form.vue'), meta: { title: '新增供货商', requiresAuth: true, operate: true } },
      { path: 'outsource/supplier/manage/edit/:id', name: 'VendorManageEdit', component: () => import('@/views/supplier/manage-form.vue'), meta: { title: '编辑供货商', requiresAuth: true, operate: true } },
      // 供应商（分类型页）新增/编辑独立成页（2026-09-23 原为 700px 弹框）：类型随 `?type=` 带过去
      { path: 'supplier/form/add', name: 'SupplierFormAdd', component: () => import('@/views/supplier/form.vue'), meta: { title: '新增供应商', requiresAuth: true, operate: true } },
      { path: 'supplier/form/edit/:id', name: 'SupplierFormEdit', component: () => import('@/views/supplier/form.vue'), meta: { title: '编辑供应商', requiresAuth: true, operate: true } },
      // 成品采购单 新增/编辑独立成页（2026-09-23 原为 900px 弹框）。⚠️ 列表 `handleAdd()` 早就在
      // push `/inventory/purchase/add` 但该路由一直不存在（点新增白屏）—— 本次一并补上，顺带修掉该 bug。
      { path: 'inventory/purchase/add', name: 'InventoryPurchaseAdd', component: () => import('@/views/purchase/order/form.vue'), meta: { title: '新增成品采购单', requiresAuth: true, operate: true } },
      // 2026-09-24（用户口径）：`inventory/purchase/edit/:id` 已移除 —— 草稿态编辑统一在**采购单详情页**
      // 内联完成（详情页 = 头部只读快照 + 可编辑表单 + 保存），列表不再提供编辑入口，避免两套入口。
      // 盘点明细（2026-09-23 原为 900px 弹框；表头走 query：scope/status/warehouseName/takeNo）；成品与物料盘点共用
      { path: 'inventory/stock-take/detail/:id', name: 'InventoryStockTakeDetail', component: () => import('@/views/stock-take/detail.vue'), meta: { title: '盘点明细', requiresAuth: true, operate: true } },
      // BOM 历史快照（2026-09-23 原为 900px 弹框，只读）
      { path: 'dev/bom-snapshot/:projectId', name: 'DevBomSnapshot', component: () => import('@/views/dev/bom-snapshot.vue'), meta: { title: 'BOM 历史快照', requiresAuth: true, operate: true } },
      { path: 'finance/bill', name: 'FinanceBill', component: () => import('@/views/finance/bill.vue'), meta: { title: '账单生成', requiresAuth: true } },
      // 账单详情独立成页（原为抽屉）
      { path: 'finance/bill/detail/:id', name: 'FinanceBillDetail', component: () => import('@/views/finance/bill/detail.vue'), meta: { title: '账单详情', requiresAuth: true, operate: true } },
      { path: 'finance/cashflow', name: 'FinanceCashflow', component: () => import('@/views/finance/cashflow.vue'), meta: { title: '资金流水', requiresAuth: true } },
      { path: 'finance/account', name: 'FinanceAccount', component: () => import('@/views/finance/account.vue'), meta: { title: '账户管理', requiresAuth: true } },
      { path: 'finance/expense', name: 'FinanceExpense', component: () => import('@/views/finance/expense.vue'), meta: { title: '费用管理', requiresAuth: true } },
      // 费用单详情（2026-09-24 新增：草稿态在详情页改+存；列表的原「编辑」弹窗与「反审核」入口随之收进详情）
      { path: 'finance/expense/detail/:id', name: 'FinanceExpenseDetail', component: () => import('@/views/finance/expense/detail.vue'), meta: { title: '费用单详情', requiresAuth: true, operate: true } },
      { path: 'finance/invoice', name: 'FinanceInvoice', component: () => import('@/views/finance/invoice.vue'), meta: { title: '发票管理', requiresAuth: true } },
      // 旧地址兼容：财务分析已迁入经营分析，保留重定向避免书签失效
      { path: 'finance/analysis', redirect: '/analysis/overview' },
      { path: 'finance/receipt', name: 'FinanceReceipt', component: () => import('@/views/finance/receipt.vue'), meta: { title: '收款管理', requiresAuth: true } },
      { path: 'finance/payment', name: 'FinancePayment', component: () => import('@/views/finance/payment.vue'), meta: { title: '付款管理', requiresAuth: true } },
      // 应付转应收：负数应付（退货/超损扣款）无货款可抵时转为向供应商收款，与报损单同套审核流程
      { path: 'finance/payable-transfer', name: 'FinancePayableTransfer', component: () => import('@/views/finance/payable-transfer/index.vue'), meta: { title: '应付转应收', requiresAuth: true } },
      { path: 'finance/payable-transfer/add', name: 'FinancePayableTransferAdd', component: () => import('@/views/finance/payable-transfer/add.vue'), meta: { title: '新增转应收单', requiresAuth: true, operate: true } },
      // 2026-09-24（用户口径）：`finance/payable-transfer/edit/:id` 已移除 —— 草稿态编辑统一在**详情页**内联完成。
      { path: 'finance/payable-transfer/detail/:id', name: 'FinancePayableTransferDetail', component: () => import('@/views/finance/payable-transfer/detail.vue'), meta: { title: '转应收单详情', requiresAuth: true, operate: true } },
      // 新增付款独立成页（2026-09-23 原为 800px 弹框）；须放在 :id 之前以免 'add' 被当作 id
      { path: 'finance/payment/supplier/add', name: 'FinancePaymentSupplierAdd', component: () => import('@/views/finance/payment-supplier-add.vue'), meta: { title: '新增付款', requiresAuth: true, operate: true } },
      { path: 'finance/payment/supplier/:id', name: 'FinancePaymentSupplier', component: () => import('@/views/finance/payment-supplier.vue'), meta: { title: '供应商应付详情', requiresAuth: true, operate: true } },
      { path: 'finance/supplier-settlement/:id', name: 'SupplierSettlement', component: () => import('@/views/finance/supplier-settlement.vue'), meta: { title: '清算看板', requiresAuth: true, operate: true } },
      // 占位路由：匹配菜单中有但尚未开发的路由
      {
        path: ':pathMatch(.*)*',
        name: 'Placeholder',
        component: () => import('@/views/dev/placeholder.vue'),
        meta: { title: '页面开发中', requiresAuth: true }
      }
    ]
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/error/403.vue'),
    meta: { title: '无权限', requiresAuth: false }
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 全局路由错误处理：捕获懒加载 chunk 失败等异常，避免异常冒泡导致整页刷新
router.onError((error) => {
  console.error('路由错误:', error)
  // 懒加载组件 chunk 加载失败时，记录错误但不主动 reload，避免循环刷新
  if (error?.message?.includes('Failed to fetch dynamically imported module') ||
      error?.message?.includes('error loading dynamically imported module')) {
    console.warn('页面资源加载失败，可能是开发环境热更新或网络瞬断导致')
  }
})

// 菜单路径授权：操作页（如 /dev/project/add、/dev/project/edit/1）本身不是菜单，
// 逐级向上回退父路径，只要其所属菜单页（如 /dev/project）在白名单内即放行
function isPathAllowed(toPath: string, allowedPaths: string[]): boolean {
  let p = toPath
  while (p.length > 1) {
    if (allowedPaths.includes(p)) return true
    const idx = p.lastIndexOf('/')
    if (idx <= 0) break
    p = p.substring(0, idx)
  }
  return allowedPaths.includes(p)
}

router.beforeEach((to, _from, next) => {
  const userStore = useUserStore()
  const isLogin = userStore.isLogin

  if (to.meta.title) {
    document.title = `${to.meta.title} - 北辰ERP管理系统`
  }

  if (to.meta.requiresAuth === false) {
    // 不需要鉴权的页面（如登录页），已登录则跳首页
    if (isLogin && to.path === '/login') {
      next('/')
      return
    }
    next()
    return
  }

  // 需要鉴权
  if (!isLogin) {
    next('/login')
    return
  }

  // 操作页放行：已在路由表注册且标记 operate 的业务操作页（详情/编辑/新增等），
  // 其本身不是菜单，无需菜单白名单授权，直接放行（路由表未声明的路径不会带此标记，越权防护不削弱）
  if ((to.meta as any).operate) {
    next()
    return
  }

  // 菜单权限校验：非 403/占位错误页的业务路由，均需在用户菜单权限内
  if (to.path !== '/403') {
    const allowedPaths = userStore.menuPaths
    if (allowedPaths.length > 0) {
      // 菜单已加载：精确路径或父级菜单路径命中白名单即放行，否则拦截
      if (!isPathAllowed(to.path, allowedPaths)) {
        next('/403')
        return
      }
    } else {
      // 菜单尚未加载（首次进入 / 缓存被清）：拉取后**再判定**，不能直接放行 ——
      // 否则"清掉 localStorage 菜单缓存 + 直输 URL"即可绕过白名单
      // （2026-09-18 实测发现：被收权页面在冷缓存下会被放行）。拉取失败时仍放行（不阻塞可用性）。
      userStore.fetchMenus().finally(() => {
        const paths = userStore.menuPaths
        if (paths.length > 0 && !isPathAllowed(to.path, paths)) {
          next('/403')
        } else {
          next()
        }
      })
      return
    }
  }

  next()
})

export default router
