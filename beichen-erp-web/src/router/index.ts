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
        path: 'system/permission',
        name: 'SystemPermission',
        component: () => import('@/views/system/permission/index.vue'),
        meta: { title: '权限管理', requiresAuth: true }
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
      { path: 'supplier/manage', name: 'SupplierManage', component: () => import('@/views/supplier/manage.vue'), meta: { title: '供应商管理', requiresAuth: true } },
      { path: 'outsource/supplier/manage', name: 'OutsourceSupplierManage', component: () => import('@/views/supplier/manage.vue'), meta: { title: '供应商管理', requiresAuth: true } },
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
        meta: { title: '研发项目', requiresAuth: true }
      },
      {
        path: 'dev/project/add',
        name: 'DevProjectAdd',
        component: () => import('@/views/dev/project/add.vue'),
        meta: { title: '新增研发项目', requiresAuth: true, operate: true }
      },
      {
        path: 'dev/project/edit/:id',
        name: 'DevProjectEdit',
        component: () => import('@/views/dev/project/edit.vue'),
        meta: { title: '研发项目详细', requiresAuth: true, operate: true }
      },
      // BOM管理
      {
        path: 'dev/bom',
        name: 'DevBom',
        component: () => import('@/views/dev/bom/index.vue'),
        meta: { title: 'BOM管理', requiresAuth: true }
      },
      {
        path: 'dev/bom-type',
        name: 'DevBomType',
        component: () => import('@/views/dev/bom-type/index.vue'),
        meta: { title: 'BOM类型管理', requiresAuth: true }
      },
      {
        path: 'dev/phase-template',
        name: 'DevPhaseTemplate',
        component: () => import('@/views/dev/phase-template/index.vue'),
        meta: { title: '阶段模板管理', requiresAuth: true }
      },
      {
        path: 'dev/drawing',
        name: 'DevDrawing',
        component: () => import('@/views/dev/drawing/index.vue'),
        meta: { title: '图纸文档', requiresAuth: true }
      },
      {
        path: 'dev/material',
        name: 'DevMaterial',
        component: () => import('@/views/dev/material/index.vue'),
        meta: { title: '研发物料管理', requiresAuth: true }
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
        meta: { title: '委外仓库', requiresAuth: true }
      },
      {
        path: 'outsource/warehouse/detail/:id',
        name: 'OutsourceWarehouseDetail',
        component: () => import('@/views/outsource/warehouse-detail.vue'),
        meta: { title: '委外仓库详情', requiresAuth: true, operate: true }
      },
      // 物料收发
      { path: 'outsource/delivery', name: 'OutsourceDelivery', component: () => import('@/views/outsource/delivery/index.vue'), meta: { title: '物料收发单', requiresAuth: true } },
      { path: 'outsource/delivery/add', name: 'OutsourceDeliveryAdd', component: () => import('@/views/outsource/delivery/add.vue'), meta: { title: '新增物料收发单', requiresAuth: true, operate: true } },
      { path: 'outsource/delivery/detail/:id', name: 'OutsourceDeliveryDetail', component: () => import('@/views/outsource/delivery/detail.vue'), meta: { title: '物料收发单详情', requiresAuth: true, operate: true } },
      { path: 'outsource/material-history/:wid/:mid', name: 'OutsourceMaterialHistory', component: () => import('@/views/outsource/warehouse-material-history.vue'), meta: { title: '物料库存流水详细', requiresAuth: true, operate: true } },
      // 委外退货
      { path: 'outsource/return-order', name: 'OutsourceReturnOrder', component: () => import('@/views/outsource/return-order/index.vue'), meta: { title: '委外退货', requiresAuth: true } },
      { path: 'outsource/return-order/add', name: 'OutsourceReturnOrderAdd', component: () => import('@/views/outsource/return-order/add.vue'), meta: { title: '新增委外退货', requiresAuth: true, operate: true } },
      { path: 'outsource/return-order/detail/:id', name: 'OutsourceReturnOrderDetail', component: () => import('@/views/outsource/return-order/detail.vue'), meta: { title: '委外退货详情', requiresAuth: true, operate: true } },
      // 销售退货单
      { path: 'sale/return', name: 'SaleReturn', component: () => import('@/views/sale/return/index.vue'), meta: { title: '销售退货单', requiresAuth: true } },
      { path: 'sale/return/add', name: 'SaleReturnAdd', component: () => import('@/views/sale/return/add.vue'), meta: { title: '新增销售退货单', requiresAuth: true, operate: true } },
      { path: 'sale/return/detail/:id', name: 'SaleReturnDetail', component: () => import('@/views/sale/return/detail.vue'), meta: { title: '销售退货单详情', requiresAuth: true, operate: true } },
      // 收费售后（客户退回不良品，不关联加工单）
      { path: 'outsource/after-sale', name: 'AfterSale', component: () => import('@/views/outsource/after-sale/index.vue'), meta: { title: '收费售后', requiresAuth: true } },
      { path: 'outsource/after-sale/add', name: 'AfterSaleAdd', component: () => import('@/views/outsource/after-sale/add.vue'), meta: { title: '新增收费售后退回', requiresAuth: true, operate: true } },
      // 物料管理 + 多级BOM
      {
        path: 'material',
        name: 'MaterialManage',
        component: () => import('@/views/material/index.vue'),
        meta: { title: '产品管理', requiresAuth: true }
      },
      { path: 'inventory/brand', name: 'InventoryBrand', component: () => import('@/views/inventory/brand/index.vue'), meta: { title: '品牌管理', requiresAuth: true } },
      { path: 'outsource/order', name: 'OutsourceOrder', component: () => import('@/views/outsource/order/index.vue'), meta: { title: '委外加工单', requiresAuth: true } },
      { path: 'outsource/order/add', name: 'OutsourceOrderAdd', component: () => import('@/views/outsource/order/add.vue'), meta: { title: '新增加工单', requiresAuth: true, operate: true } },
      { path: 'outsource/order/detail/:id', name: 'OutsourceOrderDetail', component: () => import('@/views/outsource/order/detail.vue'), meta: { title: '委外加工单详情', requiresAuth: true, operate: true } },
      { path: 'outsource/order/delivery/:id', name: 'OutsourceOrderDelivery', component: () => import('@/views/outsource/order/delivery.vue'), meta: { title: '交货管理', requiresAuth: true, operate: true } },
      { path: 'outsource/order/close/:id', name: 'OutsourceOrderClose', component: () => import('@/views/outsource/order/close.vue'), meta: { title: '结单报表', requiresAuth: true, operate: true } },
      { path: 'outsource/material-order', name: 'OutsourceMaterialOrder', component: () => import('@/views/outsource/material-order/index.vue'), meta: { title: '委外物料订单', requiresAuth: true } },
      { path: 'outsource/material-order/add', name: 'OutsourceMaterialOrderAdd', component: () => import('@/views/outsource/material-order/add.vue'), meta: { title: '新增物料订单', requiresAuth: true, operate: true } },
      { path: 'outsource/material-order/add/:id', name: 'OutsourceMaterialOrderEdit', component: () => import('@/views/outsource/material-order/add.vue'), meta: { title: '编辑物料订单', requiresAuth: true, operate: true } },
      { path: 'outsource/material-order/detail/:id', name: 'OutsourceMaterialOrderDetail', component: () => import('@/views/outsource/material-order/detail.vue'), meta: { title: '物料订单详情', requiresAuth: true, operate: true } },
      { path: 'outsource/contract-template', name: 'OutsourceContractTemplate', component: () => import('@/views/outsource/contract-template.vue'), meta: { title: '合同模板设置', requiresAuth: true } },
      { path: 'outsource/other-io', name: 'OutsourceOtherIo', component: () => import('@/views/outsource/other-io/index.vue'), meta: { title: '物料其他出入库', requiresAuth: true } },
      { path: 'outsource/material-warehouse', name: 'OutsourceMaterialWarehouse', component: () => import('@/views/outsource/material-warehouse.vue'), meta: { title: '自有物料仓', requiresAuth: true } },
      { path: 'outsource/other-io/add', name: 'OutsourceOtherIoAdd', component: () => import('@/views/outsource/other-io/add.vue'), meta: { title: '新增物料其他出入库', requiresAuth: true, operate: true } },
      { path: 'outsource/other-io/detail/:id', name: 'OutsourceOtherIoDetail', component: () => import('@/views/outsource/other-io/detail.vue'), meta: { title: '物料其他出入库详细', requiresAuth: true, operate: true } },
      { path: 'outsource/other-io/edit/:id', name: 'OutsourceOtherIoEdit', component: () => import('@/views/outsource/other-io/edit.vue'), meta: { title: '编辑物料其他出入库', requiresAuth: true, operate: true } },
      // 进销存
      { path: 'inventory/warehouse', name: 'InventoryWarehouse', component: () => import('@/views/inventory/warehouse.vue'), meta: { title: '成品仓库管理', requiresAuth: true } },
      { path: 'inventory/warehouse/detail/:id', name: 'InventoryWarehouseDetail', component: () => import('@/views/inventory/warehouse-detail.vue'), meta: { title: '仓库详情', requiresAuth: true, operate: true } },
      { path: 'inventory/warehouse/product-history/:wid/:pid', name: 'InventoryProductHistory', component: () => import('@/views/inventory/warehouse-product-history.vue'), meta: { title: '产品库存流水详细', requiresAuth: true, operate: true } },
      { path: 'inventory/other-io', name: 'InventoryOtherIo', component: () => import('@/views/inventory/other-io/index.vue'), meta: { title: '成品其他出入库', requiresAuth: true } },
      { path: 'inventory/other-io/add', name: 'InventoryOtherIoAdd', component: () => import('@/views/inventory/other-io/add.vue'), meta: { title: '新增成品其他出入库', requiresAuth: true, operate: true } },
      { path: 'inventory/other-io/detail/:id', name: 'InventoryOtherIoDetail', component: () => import('@/views/inventory/other-io/detail.vue'), meta: { title: '成品其他出入库详细', requiresAuth: true, operate: true } },
      { path: 'inventory/reclassify', name: 'InventoryReclassify', component: () => import('@/views/inventory/reclassify/index.vue'), meta: { title: '成品品质重分类', requiresAuth: true } },
      { path: 'inventory/warehouse-move', name: 'InventoryWarehouseMove', component: () => import('@/views/inventory/warehouse-move/index.vue'), meta: { title: '成品移仓单', requiresAuth: true } },
      { path: 'inventory/warehouse-move/add', name: 'InventoryWarehouseMoveAdd', component: () => import('@/views/inventory/warehouse-move/add.vue'), meta: { title: '新增移仓单', requiresAuth: true, operate: true } },
      { path: 'inventory/material', redirect: '/material' },
      { path: 'inventory/customer', name: 'InventoryCustomer', component: () => import('@/views/customer/index.vue'), meta: { title: '客户管理', requiresAuth: true } },
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
      { path: 'inventory/purchase-return', name: 'InventoryPurchaseReturn', component: () => import('@/views/purchase/return/index.vue'), meta: { title: '成品退货单', requiresAuth: true }
      }, {
        path: 'inventory/purchase-return/add',
        name: 'InventoryPurchaseReturnAdd',
        component: () => import('@/views/purchase/return/add.vue'),
        meta: { title: '新增成品退货单', requiresAuth: true, operate: true } },
      { path: 'inventory/purchase-return/detail/:id', name: 'InventoryPurchaseReturnDetail', component: () => import('@/views/purchase/return/detail.vue'), meta: { title: '成品退货单详情', requiresAuth: true, operate: true } },
      { path: 'inventory/stock', name: 'InventoryStock', component: () => import('@/views/inventory/stock.vue'), meta: { title: '成品库存查询', requiresAuth: true } },
      { path: 'inventory/stock-log', name: 'InventoryStockLog', component: () => import('@/views/inventory/stock-log.vue'), meta: { title: '成品库存流水', requiresAuth: true } },
      { path: 'inventory/sale', name: 'InventorySale', component: () => import('@/views/sale/order/index.vue'), meta: { title: '销售单', requiresAuth: true } },
      // 财务管理
      { path: 'finance/receivable', name: 'FinanceReceivable', component: () => import('@/views/finance/receivable.vue'), meta: { title: '应收管理', requiresAuth: true } },
      { path: 'finance/payable', name: 'FinancePayable', component: () => import('@/views/finance/payable.vue'), meta: { title: '应付管理', requiresAuth: true } },
      { path: 'finance/bill', name: 'FinanceBill', component: () => import('@/views/finance/bill.vue'), meta: { title: '账单生成', requiresAuth: true } },
      { path: 'finance/cashflow', name: 'FinanceCashflow', component: () => import('@/views/finance/cashflow.vue'), meta: { title: '资金流水', requiresAuth: true } },
      { path: 'finance/receipt', name: 'FinanceReceipt', component: () => import('@/views/finance/receipt.vue'), meta: { title: '收款管理', requiresAuth: true } },
      { path: 'finance/payment', name: 'FinancePayment', component: () => import('@/views/finance/payment.vue'), meta: { title: '付款管理', requiresAuth: true } },
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
      // 菜单尚未加载（避免首次进入时异步未就绪误拦），触发拉取后放行
      userStore.fetchMenus()
    }
  }

  next()
})

export default router
