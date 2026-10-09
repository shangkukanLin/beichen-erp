# 销售出库模块「已下线」守卫（2026-10-09，报告 §7.28）。
#
# 背景：用户口径「销售出库单没有用、前端也没有显示」⇒ 整个模块删除（代码 + 前端入口；
#   **表与 17 行历史数据按计划保留**，见 §7.28 的两步走）。本守卫钉住"删干净了、别复活"：
#   ① 接口真的没了：带合法 token 调用 GET/POST /api/inventory/outbound* 一律**不再是 200**；
#   ② 后端源码里再无该模块的活引用（无 SaleOutbound* 类、无 SaleOutboundMapper 注入）；
#   ③ 前端源码里再无该模块的**活引用**（注释行不算 —— 有意的下线说明会提到这些名字）。
# 只读：不建单、不改库（下一次跑仍是同一结论）。
# ⚠️ $ErrorActionPreference 必须 Continue —— Invoke-* 失败与 mysql 的 stderr 告警都不该中断脚本。
$ErrorActionPreference = 'Continue'

$API = 'http://localhost:8080/api'
$ROOT = 'C:\Users\75629\CodeBuddy\20260710123705\beichen-erp'
$script:PASS = 0; $script:FAIL = 0; $script:SKIP = 0
function Ok([bool]$c, [string]$m) { if ($c) { $script:PASS++; Write-Host ('PASS ' + $m) } else { $script:FAIL++; Write-Host ('FAIL ' + $m) } }
function Skip([string]$m) { $script:SKIP++; Write-Host ('SKIP ' + $m) }
function Summary([string]$t) {
  $tot = $script:PASS + $script:FAIL
  if ($tot -eq 0) { Write-Host ('RESULT SKIP ' + $t + ' (PASS=0 FAIL=0 SKIP=' + $script:SKIP + ')'); return }
  Write-Host ('RESULT ' + $(if ($script:FAIL -eq 0) { 'PASS' } else { 'FAIL' }) + ' ' + $t + '  (PASS=' + $script:PASS + ' FAIL=' + $script:FAIL + ' SKIP=' + $script:SKIP + ')')
  if ($script:FAIL -gt 0) { try { $Host.SetShouldExit(1) } catch { } }
}

# ---------- 登录（有 token 才有说服力：不是"没权限"而是"接口不存在"）----------
try {
  $lr = Invoke-RestMethod -Uri "$API/auth/login" -Method Post -ContentType 'application/json' `
    -Body (@{ username = 'lin'; password = '123'; companyId = 1 } | ConvertTo-Json) -TimeoutSec 20
} catch { Write-Host ('FAIL login: ' + $_.Exception.Message); Summary 'sale-outbound retired (7.28)'; exit 1 }
$H = @{ Authorization = $lr.data.token }
Ok ([bool]$lr.data.token) 'admin login ok (the checks below run WITH a valid token)'

# ---------- ① 接口不可用 ----------
Write-Host '--- (1) the outbound endpoints must be gone'
function TryCall([string]$method, [string]$path, $body) {
  try {
    if ($null -eq $body) { return Invoke-RestMethod -Uri ($API + $path) -Method $method -Headers $H -TimeoutSec 20 }
    return Invoke-RestMethod -Uri ($API + $path) -Method $method -Headers $H -ContentType 'application/json; charset=utf-8' `
      -Body ([Text.Encoding]::UTF8.GetBytes(($body | ConvertTo-Json -Depth 6))) -TimeoutSec 20
  } catch { return $null }
}
$pg = TryCall 'GET' '/inventory/outbound/page?pageNum=1&pageSize=1' $null
$rd = TryCall 'GET' '/inventory/outbound/1' $null
$wr = TryCall 'POST' '/inventory/outbound' @{ outbound = @{ customerId = 1; warehouseId = 1 }; items = @() }
Write-Host ('  GET page -> ' + $(if ($null -eq $pg) { 'unreachable/404' } else { 'code=' + $pg.code }) +
            ' ; GET /{id} -> ' + $(if ($null -eq $rd) { 'unreachable/404' } else { 'code=' + $rd.code }) +
            ' ; POST -> ' + $(if ($null -eq $wr) { 'unreachable/404' } else { 'code=' + $wr.code }))
Ok ($null -eq $pg -or $pg.code -ne 200) 'GET /inventory/outbound/page is NOT a 200 any more'
Ok ($null -eq $rd -or $rd.code -ne 200) 'GET /inventory/outbound/{id} is NOT a 200 any more'
Ok ($null -eq $wr -or $wr.code -ne 200) 'POST /inventory/outbound is NOT a 200 any more (no write entry point)'

# ---------- ② 后端源码里的活引用 ----------
Write-Host '--- (2) no live backend reference'
$beFiles = @(Get-ChildItem (Join-Path $ROOT 'beichen-erp-server\src') -Recurse -Filter 'SaleOutbound*.java' -ErrorAction SilentlyContinue)
Write-Host ('  SaleOutbound*.java files = ' + $beFiles.Count)
Ok ($beFiles.Count -eq 0) 'no SaleOutbound*.java class left in the backend'
$beHits = @(Get-ChildItem (Join-Path $ROOT 'beichen-erp-server\src') -Recurse -Include '*.java' -ErrorAction SilentlyContinue |
            Select-String -Pattern 'SaleOutbound' -SimpleMatch -ErrorAction SilentlyContinue |
            Where-Object { $_.Line -notmatch '^\s*(//|\*|/\*)' })
Write-Host ('  live SaleOutbound references in java = ' + $beHits.Count)
Ok ($beHits.Count -eq 0) 'no live (non-comment) SaleOutbound reference in the backend'
$mapperHits = @(Get-ChildItem (Join-Path $ROOT 'beichen-erp-server\src') -Recurse -Include '*.java' -ErrorAction SilentlyContinue |
                Select-String -Pattern 'saleOutboundMapper' -ErrorAction SilentlyContinue)
Ok ($mapperHits.Count -eq 0) 'SaleOutboundMapper is not injected anywhere any more'

# ---------- ③ 前端源码里的活引用 ----------
Write-Host '--- (3) no live frontend reference'
Ok (-not (Test-Path (Join-Path $ROOT 'beichen-erp-web\src\views\sale\outbound'))) 'views/sale/outbound directory is gone'
$feHits = @(Get-ChildItem (Join-Path $ROOT 'beichen-erp-web\src') -Recurse -Include '*.ts','*.vue' -ErrorAction SilentlyContinue |
            Select-String -Pattern 'SaleOutbound' -SimpleMatch -ErrorAction SilentlyContinue |
            Where-Object { $_.Line -notmatch '^\s*(//|\*|/\*)' })
Write-Host ('  live SaleOutbound references in the web app = ' + $feHits.Count)
Ok ($feHits.Count -eq 0) 'no live (non-comment) SaleOutbound reference in the web app'
$routeHits = @(Get-ChildItem (Join-Path $ROOT 'beichen-erp-web\src') -Recurse -Include '*.ts' -ErrorAction SilentlyContinue |
               Select-String -Pattern "path: 'sale/outbound" -SimpleMatch -ErrorAction SilentlyContinue)
Ok ($routeHits.Count -eq 0) 'no sale/outbound route registered in the router'

Summary 'sale-outbound module retired (no endpoint / no backend ref / no frontend ref)'
