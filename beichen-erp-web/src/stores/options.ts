import { defineStore } from 'pinia'
import request from '@/utils/request'

/** 供应商类型（与 constants/supplier.ts 的 TYPE_MAP 对应） */
export type SupplierType = 'solution' | 'factory' | 'product' | 'material' | 'all'

/**
 * 全局基础数据选项 store
 *
 * 背景：页面被 <keep-alive> 缓存后，onMounted 只执行一次，下拉框数据停留在首次加载的局部 ref 中，
 * 新增仓库/供应商/客户等基础数据后，已打开的页面下拉框看不到新数据。
 *
 * 方案：将各类基础数据下拉选项统一收敛到本 store 作为单一数据源，页面用 computed 订阅，
 * 源头页面增删改后调用 refreshXxx() 强制刷新，所有订阅页面即时响应式更新，无需整页刷新。
 */
export const useOptionsStore = defineStore('options', {
  state: () => ({
    // 仓库（自有仓 + 委外仓，全量，含 factoryName 等字段）
    warehouses: [] as any[],
    // 供应商（按类型分库，key 为 supplierType 或 'all'）
    suppliers: {} as Record<string, any[]>,
    // 客户
    customers: [] as any[],
    // 产品（成品）
    products: [] as any[],
    // 委外物料
    materials: [] as any[],
    // 研发项目
    projects: [] as any[],
    // BOM 类型
    bomTypes: [] as any[],
    // 品牌
    brands: [] as any[],
    // 角色
    roles: [] as any[],
    // 加载状态（防止重复请求）
    loaded: {} as Record<string, boolean>,
    loading: {} as Record<string, boolean>
  }),

  actions: {
    /** 通用加载：已加载且非强制则跳过；进行中则跳过，避免并发重复请求 */
    async _load(key: string, fetcher: () => Promise<void>, force = false) {
      if (!force && this.loaded[key]) return
      if (this.loading[key]) return
      this.loading[key] = true
      try {
        await fetcher()
        this.loaded[key] = true
      } finally {
        this.loading[key] = false
      }
    },

    // ==================== 仓库 ====================
    async ensureWarehouses(force = false) {
      await this._load('warehouses', async () => {
        try {
          const r: any = await request.get('/warehouse/page', { params: { pageSize: 500 } })
          this.warehouses = r?.records || []
        } catch {
          this.warehouses = []
        }
      }, force)
    },
    refreshWarehouses() { return this.ensureWarehouses(true) },

    // ==================== 供应商（按类型） ====================
    async ensureSuppliers(type: SupplierType = 'all', force = false) {
      const key = `suppliers:${type}`
      await this._load(key, async () => {
        try {
          const params: any = { pageSize: 500 }
          if (type !== 'all') params.supplierType = type
          const r: any = await request.get('/supplier/page', { params })
          this.suppliers[key] = r?.records || []
        } catch {
          this.suppliers[key] = []
        }
      }, force)
    },
    refreshSuppliers(type: SupplierType = 'all') { return this.ensureSuppliers(type, true) },
    /** 供应商类型可能被编辑改变，增删改后刷新全部分类缓存，保证各类型下拉框一致 */
    async refreshAllSuppliers() {
      await Promise.all([
        this.ensureSuppliers('all', true),
        this.ensureSuppliers('solution', true),
        this.ensureSuppliers('factory', true),
        this.ensureSuppliers('product', true),
        this.ensureSuppliers('material', true)
      ])
    },

    // ==================== 客户 ====================
    async ensureCustomers(force = false) {
      await this._load('customers', async () => {
        try {
          const r: any = await request.get('/inventory/customer/page', { params: { pageSize: 500 } })
          this.customers = r?.records || []
        } catch {
          this.customers = []
        }
      }, force)
    },
    refreshCustomers() { return this.ensureCustomers(true) },

    // ==================== 产品（成品） ====================
    async ensureProducts(force = false) {
      await this._load('products', async () => {
        try {
          const r: any = await request.get('/product/page', { params: { pageSize: 500 } })
          this.products = r?.records || []
        } catch {
          this.products = []
        }
      }, force)
    },
    refreshProducts() { return this.ensureProducts(true) },

    // ==================== 委外物料 ====================
    async ensureMaterials(force = false) {
      await this._load('materials', async () => {
        try {
          const r: any = await request.get('/outsource/material/page', { params: { pageSize: 500 } })
          this.materials = r?.records || []
        } catch {
          this.materials = []
        }
      }, force)
    },
    refreshMaterials() { return this.ensureMaterials(true) },

    // ==================== 研发项目 ====================
    async ensureProjects(force = false) {
      await this._load('projects', async () => {
        try {
          const r: any = await request.get('/dev/project/page', { params: { pageSize: 500 } })
          this.projects = r?.records || []
        } catch {
          this.projects = []
        }
      }, force)
    },
    refreshProjects() { return this.ensureProjects(true) },

    // ==================== BOM 类型 ====================
    async ensureBomTypes(force = false) {
      await this._load('bomTypes', async () => {
        try {
          const r: any = await request.get('/dev/bom-type/enabled')
          this.bomTypes = r || []
        } catch {
          this.bomTypes = []
        }
      }, force)
    },
    refreshBomTypes() { return this.ensureBomTypes(true) },

    // ==================== 品牌 ====================
    async ensureBrands(force = false) {
      await this._load('brands', async () => {
        try {
          const r: any = await request.get('/brand/enabled')
          this.brands = r || []
        } catch {
          this.brands = []
        }
      }, force)
    },
    refreshBrands() { return this.ensureBrands(true) },

    // ==================== 角色 ====================
    async ensureRoles(force = false) {
      await this._load('roles', async () => {
        try {
          const r: any = await request.get('/system/role/enabled')
          this.roles = r || []
        } catch {
          this.roles = []
        }
      }, force)
    },
    refreshRoles() { return this.ensureRoles(true) },

    /** 清空全部缓存（登录/退出/切换公司时调用，防止多租户数据串号） */
    reset() {
      this.warehouses = []
      this.suppliers = {}
      this.customers = []
      this.products = []
      this.materials = []
      this.projects = []
      this.bomTypes = []
      this.brands = []
      this.roles = []
      this.loaded = {}
      this.loading = {}
    }
  }
})
