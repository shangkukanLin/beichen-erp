import request from '@/utils/request'

export interface LoginParams {
  username: string
  password: string
  companyId?: number
}

export interface LoginResult {
  token: string
  [key: string]: unknown
}

export interface UserInfo {
  id?: number | string
  username?: string
  nickname?: string
  avatar?: string
  roles?: string[]
  [key: string]: unknown
}

export function login(data: LoginParams) {
  return request.post<LoginResult>('/auth/login', data)
}

export function logout() {
  return request.post<void>('/auth/logout')
}

export function getUserInfo() {
  return request.get<UserInfo>('/auth/info')
}

/** F3-3：当前用户的接口权限码（页面码 + 按钮动作码），供 v-perm 按钮 gating 使用 */
export function getMyPerms() {
  return request.get<string[]>('/auth/perms')
}

