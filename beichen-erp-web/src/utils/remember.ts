/**
 * 登录页「记住密码」的本地存储实现（2026-10-07，按用户要求实现）。
 *
 * ⚠️ 安全边界（务必知悉）：这里对口令做的只是**混淆**（逐字节 XOR + base64），**不是加密** ——
 * 密钥与算法都写在前端代码里，任何能在这台机器上打开 DevTools 的人都能还原出原始口令。
 * 因此它等价于"把登录凭据留在这台机器上"，只建议在受控的个人设备上使用。
 *
 * 历史背景：提交 c5501cc（R1 审核）曾把「记住密码明文存储」判为 P2 并改成"只记用户名"。
 * 本次按用户明确要求恢复记忆密码，同时保留两条底线：
 *   ① 本地存储里**不出现明文口令**（混淆后写入，并由守卫断言锁住）；
 *   ② 取消勾选立即清除，且能识别并清掉历史遗留的旧格式口令。
 *
 * 若将来要收敛风险：把下面的 STORE_PASSWORD 改成 false 即退回"只记用户名"，页面无需改动。
 */
const REMEMBER_KEY = 'beichen_erp_remember'
/** 契约开关：false = 只记用户名（口令不落地） */
const STORE_PASSWORD = true
/** 混淆密钥（仅用于避免明文直接可见，不构成安全防护） */
const XOR_KEY = 'beichen-erp-remember-v1'
/** 混淆结果前缀：用于区分本格式与历史遗留格式，避免把旧数据解成乱码回填到密码框 */
const PREFIX = 'v1:'

export interface RememberedLogin {
  username: string
  password: string
}

function xorBytes(bytes: Uint8Array): Uint8Array {
  const out = new Uint8Array(bytes.length)
  for (let i = 0; i < bytes.length; i++) out[i] = bytes[i] ^ XOR_KEY.charCodeAt(i % XOR_KEY.length)
  return out
}

function bytesToB64(bytes: Uint8Array): string {
  let bin = ''
  for (let i = 0; i < bytes.length; i++) bin += String.fromCharCode(bytes[i])
  return btoa(bin)
}

function b64ToBytes(b64: string): Uint8Array {
  const bin = atob(b64)
  const out = new Uint8Array(bin.length)
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i)
  return out
}

/** 混淆（可逆，仅供"不明文可见"） */
export function obfuscate(text: string): string {
  if (!text) return ''
  return PREFIX + bytesToB64(xorBytes(new TextEncoder().encode(text)))
}

/** 解混淆；非本格式（或损坏）返回空串，由调用方决定如何处理 */
export function deobfuscate(stored: string): string {
  if (!stored || !stored.startsWith(PREFIX)) return ''
  try {
    return new TextDecoder().decode(xorBytes(b64ToBytes(stored.slice(PREFIX.length))))
  } catch {
    return ''
  }
}

/** 读取记忆的账号口令；同时清理无法识别的历史遗留口令 */
export function loadRememberedLogin(): RememberedLogin {
  const empty: RememberedLogin = { username: '', password: '' }
  let raw: string | null = null
  try {
    raw = localStorage.getItem(REMEMBER_KEY)
  } catch {
    return empty // 隐私模式等禁用存储时静默降级
  }
  if (!raw) return empty
  try {
    const obj = JSON.parse(raw) as { username?: string; password?: string }
    const username = obj?.username || ''
    const storedPwd = obj?.password
    let password = ''
    if (STORE_PASSWORD && storedPwd) password = deobfuscate(storedPwd)
    // 历史遗留（c5501cc 之前的 base64/明文）：解不出来就不要回填乱码，顺手清掉只留用户名
    if (storedPwd && !password) {
      if (username) localStorage.setItem(REMEMBER_KEY, JSON.stringify({ username }))
      else localStorage.removeItem(REMEMBER_KEY)
    }
    return { username, password }
  } catch {
    localStorage.removeItem(REMEMBER_KEY)
    return empty
  }
}

/** 写入记忆（账号 + 口令）；账号为空则整条清除 */
export function saveRememberedLogin(username: string, password: string): void {
  if (!username) {
    localStorage.removeItem(REMEMBER_KEY)
    return
  }
  const payload: { username: string; password?: string } = { username }
  if (STORE_PASSWORD && password) payload.password = obfuscate(password)
  localStorage.setItem(REMEMBER_KEY, JSON.stringify(payload))
}

/** 清除记忆条目 */
export function clearRememberedLogin(): void {
  localStorage.removeItem(REMEMBER_KEY)
}
