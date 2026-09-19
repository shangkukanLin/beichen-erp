# 全面审核（2026-09-19）批 1「权限与安全面」实证脚本（可复现）
#
# 用途：验证接口级权限（ApiPermGuard + SaTokenConfig）是否存在绕过面，以及"未注册前缀"是否裸奔。
# 原则：**全部请求零数据变更** —— 写方法一律打不存在的主键或空体，让业务校验先失败。
# 依赖：后端 8080 在跑；测试账号 perm_test/123（角色仅持 dashboard/base:customer/base:product/sale:* 等，**无** dev:project）。
# 用法：cd beichen-erp\tools\regression\audit ; powershell -File .\audit-20260919-perm-probe.ps1
$api = 'http://localhost:8080/api'
function Login([string]$u, [string]$p) {
  try {
    $b = '{"username":"' + $u + '","password":"' + $p + '","companyId":1}'
    return Invoke-RestMethod -Uri "$api/auth/login" -Method Post -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($b))
  } catch { return $null }
}
function Call([string]$label, [string]$method, [string]$url, [string]$tok, [string]$body = '{}') {
  try {
    $h = @{}; if ($tok) { $h['Authorization'] = $tok }
    if ($method -eq 'GET') { $r = Invoke-RestMethod -Uri $url -Method Get -Headers $h }
    else { $r = Invoke-RestMethod -Uri $url -Method $method -Headers $h -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body)) }
    $m = (("$($r.msg)") -replace '\s+', ' ')
    Write-Host ($label.PadRight(60) + ' => code=' + $r.code + ' msg=' + $m.Substring(0, [Math]::Min(70, $m.Length)))
  } catch {
    $sc = -1; try { if ($_.Exception.Response) { $sc = [int]$_.Exception.Response.StatusCode } } catch {}
    $b2 = ''
    try { $sr = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream()); $b2 = ($sr.ReadToEnd() -replace '\s+', ' ') } catch {}
    Write-Host ($label.PadRight(60) + ' => HTTP' + $sc + ' ' + $b2.Substring(0, [Math]::Min(60, $b2.Length)))
  }
}

$lu = Login 'perm_test' '123'
if ($null -eq $lu) { Write-Host 'FATAL: cannot login perm_test/123'; exit 1 }
$tokU = [string]$lu.data.token
$la = Login 'lin' '123'; $tokA = [string]$la.data.token
Write-Host ('perm_test perms = ' + (@($lu.data.userInfo.perms) -join ','))

Write-Host ''
Write-Host '=== F7-1  URL 编码绕过守卫（读）==='
Call 'plain    GET /api/finance/payable/page      (expect 403)' 'GET' "$api/finance/payable/page?pageNum=1&pageSize=1" $tokU
Call 'encoded  GET /api/finance/%70ayable/page    (BYPASS=200)' 'GET' "$api/finance/%70ayable/page?pageNum=1&pageSize=1" $tokU
Call 'encoded  GET /api/financ%65/payable/page    (BYPASS=200)' 'GET' "$api/financ%65/payable/page?pageNum=1&pageSize=1" $tokU
Call 'encoded  GET /api/%66inance/payable/page    (BYPASS=200)' 'GET' "$api/%66inance/payable/page?pageNum=1&pageSize=1" $tokU

Write-Host ''
Write-Host '=== F7-1  URL 编码绕过守卫（写，零落库）==='
Call 'plain    POST /api/dev/project               (expect 403)' 'POST' "$api/dev/project" $tokU
Call 'encoded  POST /api/dev/projec%74             (BYPASS=进业务层)' 'POST' "$api/dev/projec%74" $tokU

Write-Host ''
Write-Host '=== F7-2  未注册前缀 /api/inventory/outbound（销售出库：读+写）==='
Call 'read     GET  /api/inventory/outbound/page   (BYPASS=200)' 'GET' "$api/inventory/outbound/page?pageNum=1&pageSize=1" $tokU
Call 'write    POST /api/inventory/outbound        (BYPASS=进业务层)' 'POST' "$api/inventory/outbound" $tokU

Write-Host ''
Write-Host '=== F7-3  未注册前缀 /api/sale/analysis（销售分析 + 明细钻取）==='
Call 'read     GET /api/sale/analysis              (BYPASS=200)' 'GET' "$api/sale/analysis?preset=month" $tokU
Call 'read     GET /api/sale/analysis/records      (BYPASS=200)' 'GET' "$api/sale/analysis/records?preset=month" $tokU
Call 'contrast GET /api/customer/analysis          (guarded=403)' 'GET' "$api/customer/analysis?preset=month" $tokU

Write-Host ''
Write-Host '=== F7-4  EXEMPT /api/finance/analysis（经营概览/利润表/资金趋势/账龄）==='
Call 'read     GET /api/finance/analysis/summary    (BYPASS=200)' 'GET' "$api/finance/analysis/summary" $tokU

Write-Host ''
Write-Host '=== F7-5  EXEMPT（经 WRITE_RULES 读共享）/api/warehouse -> 库存全量可读 ==='
Call 'read     GET /api/warehouse/stock/page        (BYPASS=200)' 'GET' "$api/warehouse/stock/page?pageNum=1&pageSize=1" $tokU

Write-Host ''
Write-Host '=== F7-12  EXEMPT /api/supplier-settlement 的写端点（不存在的 supplierId => 零数据变更）==='
Call 'write    POST /api/supplier-settlement/999999999/finish            (BYPASS=进业务层)' 'POST' "$api/supplier-settlement/999999999/finish" $tokU
Call 'write    POST /api/supplier-settlement/999999999/return-materials  (BYPASS=进业务层)' 'POST' "$api/supplier-settlement/999999999/return-materials" $tokU

Write-Host ''
Write-Host '=== F7-11 免登录端点（excludePathPatterns）==='
Call '(no tok) GET /api/system/menu/tree/user      (expect 401)' 'GET' "$api/system/menu/tree/user" ''
Call '(no tok) GET /api/system/menu/tree/user?userId=1 (expect 401)' 'GET' "$api/system/menu/tree/user?userId=1" ''
Call '(no tok) GET /api/company/list                (login page needs 200)' 'GET' "$api/company/list" ''
Call '(no tok) GET /api/auth/company-name           (401 = exclude entry is dead)' 'GET' "$api/auth/company-name" ''
Call '(no tok) GET /api/product/page                (expect 401)' 'GET' "$api/product/page?pageNum=1&pageSize=1" ''
