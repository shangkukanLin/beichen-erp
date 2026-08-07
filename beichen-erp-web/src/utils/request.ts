import axios, { type AxiosInstance, type InternalAxiosRequestConfig, type AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import router from '@/router'

interface ApiResult<T = unknown> {
  code: number
  msg: string
  data: T
}

// 底层 axios 实例（挂载拦截器、类型始终为 AxiosResponse）
const http: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 15000
})

// 请求拦截器
http.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const userStore = useUserStore()
    if (userStore.token) {
      config.headers.Authorization = userStore.token
    }
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截器：剥离外层包装，直接返回 data（与 ApiResult.data 一致）
http.interceptors.response.use(
  (response: AxiosResponse<ApiResult>) => {
    const res = response.data
    // 处理非标准结构（如直接返回数据）
    if (res.code === undefined) {
      return res as unknown as AxiosResponse
    }
    if (res.code === 200) {
      return res.data as unknown as AxiosResponse
    }
    if (res.code === 401) {
      const userStore = useUserStore()
      userStore.logout()
      router.push('/login')
      return Promise.reject(new Error(res.msg || '登录已失效'))
    }
    if (res.code === 403) {
      ElMessage.error(res.msg || '无权限访问')
      return Promise.reject(new Error(res.msg || '无权限访问'))
    }
    ElMessage.error(res.msg || '请求失败')
    return Promise.reject(new Error(res.msg || '请求失败'))
  },
  (error) => {
    const status = error?.response?.status
    if (status === 401) {
      const userStore = useUserStore()
      userStore.logout()
      router.push('/login')
      ElMessage.error('登录已失效，请重新登录')
    } else {
      ElMessage.error(error?.response?.data?.msg || error.message || '网络异常')
    }
    return Promise.reject(error)
  }
)

/**
 * 统一请求封装：拦截器已剥离 ApiResult 外层，故 get/post/put/delete 返回 Promise<T>（即 data）。
 * 调用方直接拿到后端 data，无需再取 .data / .records。
 */
const request = {
  // 第二个泛型参数保持与 axios 原生签名兼容（历史 api 文件用 get<T, any> 写法），但返回始终为 data（Promise<T>）
  get<T = any, _R = any>(url: string, config?: { params?: any; [k: string]: any }): Promise<T> {
    return http.get(url, config as any) as unknown as Promise<T>
  },
  post<T = any, _R = any>(url: string, data?: any, config?: any): Promise<T> {
    return http.post(url, data, config) as unknown as Promise<T>
  },
  put<T = any, _R = any>(url: string, data?: any, config?: any): Promise<T> {
    return http.put(url, data, config) as unknown as Promise<T>
  },
  delete<T = any, _R = any>(url: string, config?: any): Promise<T> {
    return http.delete(url, config) as unknown as Promise<T>
  },
  // 暴露底层实例（拦截器已配置），供需要原始 AxiosResponse 的场景（极少）
  raw: http,
}

export default request
