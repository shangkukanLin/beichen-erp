# Role-scoped UI walkthrough (2026-09-19) - closes the static-audit blind spot.
#
# The static audit (audit-frontend-api-crosspage.ps1) only sees literal URLs. A page that builds a URL at
# runtime could still read another module's API and get 403. This script logs in as REAL restricted users and
# drives the real UI, asserting:
#   HARD FAIL  zero "HTTP403:" entries in window.__errs   (WatchErrors hooks window.fetch, so a 403 swallowed
#              by the caller's catch is still recorded)
#   HARD FAIL  zero "JSERR:" entries
#   INFO ONLY  every other 4xx/5xx and every body error word (pre-existing noise must not make this gate flaky)
#
# Phase A (roles): one temp user per role, walking every page that role can actually see.
# Phase B (isolated pages): the 19 pages touched by the read-isolation work are re-checked with a temp user
#   whose role owns EXACTLY that one page ("what a user with only this page sees"), while interacting with the
#   page (source-document deep link / pickers / dialogs) - the runtime counterpart of "a user who lost the
#   other pages can still call this module's API".
#
# Temp users/roles are pre-cleaned and cleaned again at the end. This file must stay pure ASCII.
$ErrorActionPreference = 'Continue'
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')

$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$api = 'http://localhost:8080/api'
$isIsolated = ($args -contains '-Isolated')
$isRoles = (-not $isIsolated) -or ($args -contains '-Roles')
# -MaxPages N : smoke runs (0 = unlimited)
$maxPages = 0
# -Only a,b   : run phase A for the listed roles only (keeps a single invocation short)
$onlyRoles = @()
for ($i = 0; $i -lt $args.Count - 1; $i++) {
  if ($args[$i] -eq '-MaxPages') { $maxPages = [int]$args[$i + 1] }
  if ($args[$i] -eq '-Only') { $onlyRoles = @($args[$i + 1] -split ',') }
}

$script:http403 = 0
$script:jserr = 0
$script:pages = 0
$script:iso = 0
$script:info = 0

function SqlRaw([string]$q) {
  # -N: no column header (same convention as verify-api-perm-enforcement.ps1 / verify-api-perm-matrix.ps1)
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -N -B -e $q 2>$null
  return @(@($o) | ForEach-Object { "$_" })
}
function SqlOne([string]$q) {
  $l = SqlRaw $q
  if (@($l).Count -lt 1) { return '' }
  return ((@($l)[0] -split "`t")[0]).Trim()
}
function SqlExec([string]$q) { & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null | Out-Null }
function ApiPost([string]$path, [string]$tok, [string]$json) {
  $h = @{}; if ($tok) { $h['Authorization'] = $tok }
  try { return Invoke-RestMethod -Uri "$api$path" -Method Post -Headers $h -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($json)) }
  catch { return $null }
}
function ApiLogin([string]$u, [string]$p) { return (ApiPost '/auth/login' '' ('{"username":"' + $u + '","password":"' + $p + '","companyId":1}')) }
function Info([string]$m) { $script:info++; Write-Host ("INFO " + $m) }
function Hard([bool]$cond, [string]$m) {
  if ($cond) { $script:PASS++; Write-Host ("PASS " + $m) }
  else { $script:FAIL++; Write-Host ("FAIL " + $m) }
}

function CleanupTemp {
  SqlExec "DELETE ur FROM sys_user_role ur JOIN sys_user u ON u.id=ur.user_id WHERE u.username LIKE 'perm_ui_%';"
  SqlExec "DELETE um FROM sys_user_menu um JOIN sys_user u ON u.id=um.user_id WHERE u.username LIKE 'perm_ui_%';"
  SqlExec "DELETE dt FROM sys_user_dashboard_tab dt JOIN sys_user u ON u.id=dt.user_id WHERE u.username LIKE 'perm_ui_%';"
  SqlExec "DELETE FROM sys_user WHERE username LIKE 'perm_ui_%';"
  SqlExec "DELETE rm FROM sys_role_menu rm JOIN sys_role r ON r.id=rm.role_id WHERE r.role_code LIKE 'perm_ui_%';"
  SqlExec "DELETE FROM sys_role WHERE role_code LIKE 'perm_ui_%';"
}

# menu id + page code for a route (mirrors the candidate logic of audit-frontend-api-crosspage.ps1)
function MenuOfRoute([string]$menuRoute) {
  $c = $menuRoute.TrimStart('/')
  $row = SqlOne ("SELECT id FROM sys_menu WHERE (route_path='/" + $c + "' OR route_path='" + $c + "') AND perms IS NOT NULL LIMIT 1")
  return $row
}
function CodeOfRoute([string]$menuRoute) {
  $c = $menuRoute.TrimStart('/')
  return (SqlOne ("SELECT perms FROM sys_menu WHERE (route_path='/" + $c + "' OR route_path='" + $c + "') AND perms IS NOT NULL LIMIT 1"))
}

# ---- one page under the current browser session: hook errors, open, act, then judge ----
function WalkPage([string]$url, [string]$label, [string[]]$acts = @()) {
  Open $url 1900
  WatchErrors                                   # the hook lives in the page context: re-install after every load
  Start-Sleep -Milliseconds 700
  $actLog = @()
  foreach ($a in $acts) { $actLog += ($a + '=' + (DoAct $a)) }
  Start-Sleep -Milliseconds 400
  $errs = Errs
  # this backend reports denials as HTTP 200 + {"code":403} => count BOTH the envelope code and the status
  $c403 = @([regex]::Matches($errs, '(?:HTTP|APICODE)403:([^"]+)') | ForEach-Object { $_.Groups[1].Value })
  $js = @([regex]::Matches($errs, 'JSERR:([^"]*)') | ForEach-Object { $_.Groups[1].Value })
  $other = @([regex]::Matches($errs, '(?:HTTP|APICODE)(?!403)(\d+):([^"]+)') | ForEach-Object { $_.Groups[1].Value + ' ' + $_.Groups[2].Value })
  $word = ''
  foreach ($k in @('err_sys', 'err_nofunc', 'err_403', 'err_fail')) { if ((BodyHas (ZH $k)) -match 'true') { $word = $k; break } }
  $script:pages++
  $script:http403 += $c403.Count
  $script:jserr += $js.Count
  if ($c403.Count -gt 0) { Write-Host ("FAIL " + $label + " -> " + $c403.Count + " HTTP403: " + ($c403 -join ' | ')) }
  elseif ($js.Count -gt 0) { Write-Host ("FAIL " + $label + " -> JSERR: " + ($js -join ' | ')) }
  else { Write-Host ("OK   " + $label) }
  if ($c403.Count -gt 0 -or $js.Count -gt 0) { $script:FAIL++ }
  if ($word -ne '') { Info ($label + ' shows error word ' + $word + ' (no 403 recorded)') }
  if ($other.Count -gt 0) { Info ($label + ' other http: ' + (($other | Select-Object -Unique) -join ' | ')) }
  if ($actLog.Count -gt 0) { Info ($label + ' acts: ' + ($actLog -join ' ')) }
}

# ---- small interaction vocabulary (failure only ever records INFO) ----
function DoAct([string]$act) {
  switch ($act) {
    # NOTE: every search text comes from the shared ZH map (this file must stay pure ASCII - a Chinese
    # literal in an BOM-less .ps1 is read as ANSI by PowerShell 5.1 and turns into mojibake).
    'factory' { return (SelectLabelContains 'lbl_factory' (ZH 'val_factory')) }
    'relOrder' {
      $r1 = OpenSelect 'lbl_rel_order'; Start-Sleep -Milliseconds 1300; $r2 = PickFirstOption 200
      return ("$r1/$r2")
    }
    'returnTarget' { return (SelectLabelContains 'lbl_return_target' (ZH 'val_supplier_jh')) }
    'mrOrder' {
      $r1 = OpenSelect 'lbl_mr_order'; Start-Sleep -Milliseconds 1300; $r2 = PickFirstOption 200
      return ("$r1/$r2")
    }
    'vendor' { return (SelectLabelContains 'lbl_vendor' (ZH 'val_vendor')) }
    'partner' { return (SelectLabelContains 'lbl_partner' (ZH 'val_customer')) }
    'openAddPayment' { $r = ClickBtn 'btn_add_payment'; Start-Sleep -Milliseconds 1800; return $r }
    'openAddReceipt' { $r = ClickBtn 'btn_new_receipt'; Start-Sleep -Milliseconds 1800; return $r }
    'addWriteoff' {
      # the writeoff row only renders after the payment dialog got an amount/account; try the dialog
      # select first, then click the button and pick the first payable/receivable from the row select
      $r0 = DialogOpenSelect 0; Start-Sleep -Milliseconds 900; $r0b = PickFirstOption 200
      $r1 = ClickBtn 'btn_add_writeoff'; Start-Sleep -Milliseconds 700
      $r2 = OpenRowSelect 0 0; Start-Sleep -Milliseconds 1000; $r3 = PickFirstOption 200
      return ("$r0/$r0b/$r1/$r2/$r3")
    }
    'addLine' {
      $r = ClickBtn 'btn_add_detail'; Start-Sleep -Milliseconds 700
      if ($r -notmatch 'OK') { $r = ClickBtn 'btn_add_line'; Start-Sleep -Milliseconds 700 }
      return $r
    }
    'open' { return 'open' }
    default { return 'unknown-act' }
  }
}

# ================= Phase A: one temp user per role, walk that role's pages =================
$roles = @('dev_engineer', 'sales', 'warehouse', 'merchandiser', 'finance')
$lines = @("temp users cleared")
Write-Host "== pre-clean temp objects"
CleanupTemp

$admin = ApiLogin 'lin' '123'
if ($null -eq $admin -or [string]$admin.code -ne '200') {
  Write-Host 'RESULT ROLE-UI-WALKTHROUGH FAIL cannot login as admin'
  exit 1
}
$tokA = [string]$admin.data.token

if ($isRoles) {
  foreach ($role in $roles) {
    if ($onlyRoles.Count -gt 0 -and ($onlyRoles -notcontains $role)) { continue }
    $rid = SqlOne "SELECT id FROM sys_role WHERE role_code='$role'"
    if ($rid -eq '') { Hard $false ("role not found: " + $role); continue }
    $uname = "perm_ui_$role"
    ApiPost '/system/user' $tokA ('{"username":"' + $uname + '","password":"123","status":1}') | Out-Null
    $uid = SqlOne "SELECT id FROM sys_user WHERE username='$uname'"
    if ($uid -eq '') { Hard $false ("cannot create " + $uname); continue }
    SqlExec "INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES ($uid, $rid);"
    $ok = EnsureLoginAs $uname '123'
    Hard $ok ("login as " + $uname + " (identity verified from localStorage)")
    if (-not $ok) { continue }
    # NOTE: no DISTINCT + ORDER BY on non-selected columns (MySQL forbids it, and the error is invisible
    # when stderr is redirected => silently zero rows). GROUP BY keeps one row per route and stays orderable.
    $sqlRoutes = "SELECT m.route_path, MIN(m.sort_order) so, MIN(m.id) mi FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.id JOIN sys_role_menu rm ON rm.role_id=ur.role_id JOIN sys_menu m ON m.id=rm.menu_id WHERE u.username='" + $uname + "' AND m.menu_type='menu' AND m.visible=1 AND m.status=1 AND m.route_path IS NOT NULL AND m.route_path<>'' GROUP BY m.route_path ORDER BY so, mi"
    $list = @(SqlRaw $sqlRoutes | ForEach-Object { (($_ -split "`t")[0]).Trim() } | Where-Object { $_ -ne '' })
    Write-Host ("ROLE " + $role + " uid=" + $uid + " rid=" + $rid + " roleRows=" + (SqlOne ("SELECT COUNT(*) FROM sys_user_role WHERE user_id=" + $uid)) + " visible pages=" + $list.Count)
    Hard ($list.Count -gt 0) ("role " + $role + " has visible pages to walk")
    $n = 0
    foreach ($r0 in $list) {
      if ($maxPages -gt 0 -and $n -ge $maxPages) { break }
      $n++
      $rr = if ($r0.StartsWith('/')) { $r0 } else { '/' + $r0 }
      WalkPage $rr ($role + ' ' + $rr)
    }
  }
}

# ================= Phase B: single-page roles - "user owns EXACTLY this one page" =================
if ($isIsolated) {
  $poId = SqlOne 'SELECT id FROM purchase_order ORDER BY id DESC LIMIT 1'
  $soId = SqlOne "SELECT id FROM sale_order WHERE status='AUDITED' ORDER BY id DESC LIMIT 1"
  $custId = SqlOne 'SELECT id FROM customer ORDER BY id DESC LIMIT 1'
  $supId = SqlOne 'SELECT id FROM supplier ORDER BY id DESC LIMIT 1'
  $oioId = SqlOne 'SELECT id FROM outsource_other_io ORDER BY id DESC LIMIT 1'
  $isoPages = @(
    @{ n = 'purchase-return/add (source order deep link)'; m = '/inventory/purchase-return'; u = "/inventory/purchase-return/add?fromOrder=$poId"; a = @('vendor') },
    @{ n = 'purchase-exchange/add (source order deep link)'; m = '/inventory/purchase-exchange'; u = "/inventory/purchase-exchange/add?fromOrder=$poId"; a = @() },
    @{ n = 'sale-return/add (source order deep link)'; m = '/sale/return'; u = "/sale/return/add?saleOrderId=$soId"; a = @() },
    @{ n = 'sale-exchange/add (source order deep link)'; m = '/sale/exchange'; u = "/sale/exchange/add?saleOrderId=$soId"; a = @() },
    @{ n = 'outsource/return-order/add (factory + linked order picker)'; m = '/outsource/return-order'; u = '/outsource/return-order/add'; a = @('factory', 'relOrder') },
    @{ n = 'outsource/material-return/add (material-order picker)'; m = '/outsource/material-return'; u = '/outsource/material-return/add'; a = @('returnTarget', 'mrOrder') },
    @{ n = 'outsource/other-io/add (material line)'; m = '/outsource/other-io'; u = '/outsource/other-io/add'; a = @('addLine') },
    @{ n = 'outsource/other-io/edit'; m = '/outsource/other-io'; u = "/outsource/other-io/edit/$oioId"; a = @() },
    @{ n = 'outsource/other-io/detail'; m = '/outsource/other-io'; u = "/outsource/other-io/detail/$oioId"; a = @() },
    @{ n = 'outsource/material-order/delivery (receiving list)'; m = '/outsource/material-order/delivery'; u = '/outsource/material-order/delivery'; a = @() },
    @{ n = 'inventory/warehouse (stock-take column)'; m = '/inventory/warehouse'; u = '/inventory/warehouse'; a = @() },
    @{ n = 'finance/payment (payable summary)'; m = '/finance/payment'; u = '/finance/payment'; a = @() },
    @{ n = 'finance/payment/supplier (payables + unpaid)'; m = '/finance/payment'; u = "/finance/payment/supplier/$supId"; a = @('openAddPayment', 'addWriteoff') },
    @{ n = 'finance/receipt (unpaid receivables)'; m = '/finance/receipt'; u = '/finance/receipt'; a = @('openAddReceipt', 'partner', 'addWriteoff') },
    @{ n = 'sale/order/detail (receipts/returns/exchanges)'; m = '/inventory/sale'; u = "/inventory/sale/detail/$soId"; a = @() },
    @{ n = 'purchase/order/detail (returns)'; m = '/inventory/purchase'; u = "/inventory/purchase/detail/$poId"; a = @() },
    @{ n = 'customer/detail (customer sale orders)'; m = '/inventory/customer'; u = "/inventory/customer/detail/$custId"; a = @() }
  )
  # one reusable temp role + one reusable temp user; the role's menu set is swapped per page (=exactly one page)
  $isoRole = 'perm_ui_iso'
  $isoUser = 'perm_ui_iso_user'
  SqlExec "INSERT INTO sys_role (role_name, role_code, status, remark, company_id) VALUES ('perm ui iso', '$isoRole', 1, 'temp walkthrough role', 1);"
  $isoRid = SqlOne "SELECT id FROM sys_role WHERE role_code='$isoRole'"
  ApiPost '/system/user' $tokA ('{"username":"' + $isoUser + '","password":"123","status":1}') | Out-Null
  $isoUid = SqlOne "SELECT id FROM sys_user WHERE username='$isoUser'"
  if ($isoRid -eq '' -or $isoUid -eq '') {
    Hard $false 'cannot create the isolated single-page role/user'
  } else {
    SqlExec "INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES ($isoUid, $isoRid);"
    foreach ($p in $isoPages) {
      $mid = MenuOfRoute $p.m
      $code = CodeOfRoute $p.m
      if ($mid -eq '' -or $code -eq '') { Info ($p.n + ' -> cannot resolve menu/permission code for ' + $p.m + ' (skipped)'); continue }
      SqlExec "DELETE FROM sys_role_menu WHERE role_id=$isoRid;"
      SqlExec "INSERT INTO sys_role_menu (role_id, menu_id) VALUES ($isoRid, $mid);"
      $ok = EnsureLoginAs $isoUser '123'
      if (-not $ok) { Hard $false ("isolated login failed for " + $p.n); continue }
      $script:iso++
      WalkPage $p.u ('[only ' + $code + '] ' + $p.n) $p.a
    }
  }
}

# ================= cleanup + verdict =================
CleanupTemp
# IMPORTANT: leave the browser logged in as the admin again. All the other ui-e2e-*.ps1 scripts call
# EnsureLogin(), which only logs in when no token is present - so a leftover temp-user session (whose user
# we just deleted) would silently degrade every following script (pages load, but their own module APIs 403).
$restored = EnsureLoginAs 'lin' '123'
Hard $restored 'admin session restored for the scripts that run after this one'
$left = SqlOne "SELECT COUNT(*) FROM sys_user WHERE username LIKE 'perm_ui_%'"
Hard ($left -eq '0') ('temp users cleaned up (left=' + $left + ')')
$leftRole = SqlOne "SELECT COUNT(*) FROM sys_role WHERE role_code LIKE 'perm_ui_%'"
Hard ($leftRole -eq '0') ('temp roles cleaned up (left=' + $leftRole + ')')
Hard ($script:http403 -eq 0) ('zero HTTP403 across ' + $script:pages + ' page visits (isolated pages=' + $script:iso + ')')
Hard ($script:jserr -eq 0) ('zero JS errors across ' + $script:pages + ' page visits')
Write-Host ("INFO total info notes=" + $script:info)
Summary 'ROLE-UI-WALKTHROUGH'
