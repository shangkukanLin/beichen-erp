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
  return request.post<unknown, LoginResult>('/auth/login', data)
}

export function logout() {
  return request.post<unknown, void>('/auth/logout')
}

export function getUserInfo() {
  return request.get<unknown, UserInfo>('/auth/info')
}
