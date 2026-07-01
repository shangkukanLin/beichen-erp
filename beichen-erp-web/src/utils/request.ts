import axios, { type AxiosInstance, type InternalAxiosRequestConfig, type AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import router from '@/router'

interface ApiResult<T = unknown> {
  code: number
  msg: string
  data: T
}

const request: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 15000
})

// 请求拦截器
request.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const userStore = useUserStore()
    if (userStore.token) {
      config.headers.Authorization = userStore.token
    }
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截器
request.interceptors.response.use(
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

export default request
