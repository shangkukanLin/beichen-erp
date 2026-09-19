# Verify the per-user page permission feature (2026-09-18 user request):
#   "edit this user's page permissions right inside the user management page"
#   sys_user.menu_mode: ROLE (follow roles, default) / CUSTOM (sys_user_menu is the single source of truth)
# This file MUST stay pure ASCII: all Chinese literals come from ui-e2e-zh.json (ZH / B64).
#
# Asserts:
#   1) DDL: sys_user.menu_mode column + sys_user_menu table exist
#   2) prepare test user perm_test (role = sales) and record the baseline (role-following) menu set
#   3) admin sets CUSTOM = [home, base catalog, product page] -> DB + API reflect exactly that
#   4) perm_test really sees only those pages: API tree + sidebar + a role-allowed-but-removed URL gets 403
#   5) guardrails: cannot edit own permission / custom must pick at least one / super_admin cannot be custom /
#      home page and ancestor catalogs are auto-added
#   6) back to ROLE -> user-level rows are cleared and the role menu set is restored
#   7) UI: admin can open the permission dialog from the user list (dialog + tree render)
#   8) restore the admin (lin) session for subsequent scripts
$ErrorActionPreference = 'Continue'
$base = 'http://localhost:5173'
$api = 'http://localhost:8080/api'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$fail = 0
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')   # only for ZH / B64; Ok/Bad defined below override the lib
function Ok($msg) { Write-Output ("PASS " + $msg) }
function Bad($msg) { Write-Output ("FAIL " + $msg); $script:fail++ }
function EvalJs2($js) { return (((agent-browser eval $js) -join "`n").Trim()) }
function SqlLines([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null
  return @((@($o) | Select-Object -Skip 1) | ForEach-Object { "$_" } | Where-Object { $_ -ne '' })
}
function SqlOne([string]$q) { $l = @(SqlLines $q); if ($l.Count -lt 1) { return '' }; return (($l[0] -split "`t")[0]).Trim() }
function SqlRaw([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -N -B -e $q 2>$null
  return (@($o) -join '').Trim()
}
function Login([string]$u, [string]$p) {
  try {
    $b = '{"username":"' + $u + '","password":"' + $p + '","companyId":1}'
    $r = Invoke-RestMethod -Uri "$api/auth/login" -Method Post -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($b))
    return [string]$r.data.token
  } catch { return '' }
}
function Req([string]$method, [string]$url, $body, [string]$tok) {
  try {
    $h = @{}; if ($tok) { $h['Authorization'] = $tok }
    if ($method -eq 'GET') { return Invoke-RestMethod -Uri $url -Method Get -Headers $h }
    $json = if ($null -eq $body) { '{}' } else { ConvertTo-Json -InputObject $body -Depth 8 }
    return Invoke-RestMethod -Uri $url -Method $method -Headers $h -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($json))
  } catch { return [pscustomobject]@{ code = -1; msg = "HTTPEX $($_.Exception.Message)"; data = $null } }
}
function CodeOf($r) { if ($null -eq $r) { return 'null' } else { return "$($r.code)" } }
function MsgOf($r) { if ($null -eq $r) { return '' } else { return "$($r.msg)" } }
function CountNodes($nodes) {
  $n = 0
  foreach ($x in @($nodes)) {
    if ($null -eq $x) { continue }
    $n++
    if ($x.children) { $n += CountNodes $x.children }
  }
  return $n
}
function UseToken([string]$token) {
  $b = B64 $token
  EvalJs2 "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));localStorage.setItem('beichen_erp_token',T('$b'));localStorage.removeItem('beichen_erp_menus');return 'ok'})()" | Out-Null
  agent-browser open "$base/dashboard" | Out-Null
  agent-browser wait 3400
}
function LoginFlow() {
  Write-Output '(session expired, logging in)'
  $snap = (agent-browser snapshot -i) -join "`n"
  $mu = [regex]::Match($snap, [regex]::Escape((ZH 'lbl_login_user')) + '[^\n]*ref=(e\d+)')
  $mp = [regex]::Match($snap, [regex]::Escape((ZH 'lbl_login_pass')) + '[^\n]*ref=(e\d+)')
  $mb = [regex]::Match($snap, [regex]::Escape((ZH 'btn_login')) + '[^\n]*ref=(e\d+)')
  if ($mu.Success -and $mp.Success -and $mb.Success) {
    agent-browser fill ("@" + $mu.Groups[1].Value) 'lin' | Out-Null
    agent-browser fill ("@" + $mp.Groups[1].Value) '123' | Out-Null
    agent-browser click ("@" + $mb.Groups[1].Value) | Out-Null
    agent-browser wait 3500
  } else { Write-Output 'WARN login form not found in snapshot' }
}
function OpenFresh($url) {
  EvalJs2 "localStorage.removeItem('beichen_erp_menus'); 'cleared'" | Out-Null
  agent-browser open $url | Out-Null
  agent-browser wait 3000
  if ((EvalJs2 "'p=' + location.pathname") -match '/login') { LoginFlow; agent-browser open $url | Out-Null; agent-browser wait 3000 }
}
# leaf menu items in the sidebar (top-level menu items + sub-menu children).
# NOTE: no visibility filter on purpose -- collapsed groups must still count as "granted".
$jsSidebar = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const H=T('" + (B64 (ZH 'menu_home')) + "'),P=T('" + (B64 (ZH 'menu_product')) + "'),S=T('" + (B64 (ZH 'menu_sale_order')) + "'),O=T('" + (B64 (ZH 'menu_sale')) + "');const all=[...document.querySelectorAll('.el-menu .el-menu-item')].map(e=>(e.innerText||'').trim());return (all.indexOf(H)>=0?'Y':'N')+(all.indexOf(P)>=0?'Y':'N')+(all.indexOf(S)>=0?'Y':'N')+(all.indexOf(O)>=0?'Y':'N')+'@'+all.length+'@'+all.join('~')})()"

Write-Output '--- 1) DDL: sys_user.menu_mode + sys_user_menu exist'
$col = SqlOne "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='sys_user' AND column_name='menu_mode'"
$tbl = SqlOne "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='sys_user_menu'"
if ($col -eq '1') { Ok 'sys_user.menu_mode column exists' } else { Bad 'sys_user.menu_mode column missing' }
if ($tbl -eq '1') { Ok 'sys_user_menu table exists' } else { Bad 'sys_user_menu table missing' }

$tok = Login 'lin' '123'
if ($tok -eq '') { Bad 'cannot login as admin (lin)'; Write-Output 'RESULT FAIL count 1'; exit 1 }
Ok 'admin (lin) token acquired'

Write-Output '--- 2) prepare test user perm_test (role = sales)'
$salesRoleId = SqlOne "SELECT id FROM sys_role WHERE role_code='sales'"
$uid = SqlOne "SELECT id FROM sys_user WHERE username='perm_test'"
if ($uid -eq '') {
  $r = Req 'POST' "$api/system/user" @{ username = 'perm_test'; password = '123'; status = 1; roleIds = @([int]$salesRoleId) } $tok
  Write-Output ("  create perm_test -> code=" + (CodeOf $r) + " msg=" + (MsgOf $r))
  if ((CodeOf $r) -ne '200') { Bad 'cannot create test user perm_test' }
  $uid = SqlOne "SELECT id FROM sys_user WHERE username='perm_test'"
}
if ($uid -eq '') { Bad 'test user perm_test not found'; Write-Output 'RESULT FAIL count 1'; exit 1 }
Write-Output ("  perm_test id=$uid role=sales($salesRoleId)")
# force sales-only role set, and start from the default mode
& $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e ("DELETE FROM sys_user_role WHERE user_id=$uid; INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES ($uid, $salesRoleId); DELETE FROM sys_user_menu WHERE user_id=$uid; UPDATE sys_user SET menu_mode='ROLE' WHERE id=$uid;") 2>$null | Out-Null
$aMode = SqlOne "SELECT menu_mode FROM sys_user WHERE id=$uid"
if ($aMode -eq 'ROLE') { Ok 'perm_test starts in ROLE mode with user-level rows cleared' } else { Bad ('perm_test menu_mode = ' + $aMode) }

$tokU = Login 'perm_test' '123'
if ($tokU -eq '') { Bad 'cannot login as perm_test'; Write-Output 'RESULT FAIL count 1'; exit 1 }
$baselineTree = Req 'GET' "$api/system/menu/tree/user" $null $tokU
$baselineCount = CountNodes $baselineTree.data
Write-Output ("  baseline (role) menu nodes = $baselineCount")
if ([int]$baselineCount -ge 5) { Ok ('role-following baseline looks like a real role menu set (' + $baselineCount + ' nodes)') } else { Bad ('baseline too small: ' + $baselineCount) }
$saleRoute = SqlOne "SELECT route_path FROM sys_menu WHERE id=601"
Write-Output ("  sales order route (removed by the custom set below) = $saleRoute")

Write-Output '--- 3) admin sets CUSTOM = home + base catalog + product page (ids 1, 2, 101)'
$r = Req 'PUT' "$api/system/user/$uid/menus" @{ menuMode = 'CUSTOM'; menuIds = @(1, 2, 101) } $tok
Write-Output ("  save custom -> code=" + (CodeOf $r) + " msg=" + (MsgOf $r))
if ((CodeOf $r) -eq '200') { Ok 'custom permission saved (200)' } else { Bad ('save custom failed: ' + (MsgOf $r)) }
$mode = SqlOne "SELECT menu_mode FROM sys_user WHERE id=$uid"
if ($mode -eq 'CUSTOM') { Ok 'sys_user.menu_mode = CUSTOM' } else { Bad ('menu_mode = ' + $mode) }
$rows = SqlRaw "SELECT GROUP_CONCAT(menu_id ORDER BY menu_id) FROM sys_user_menu WHERE user_id=$uid"
Write-Output ("  sys_user_menu ids = $rows")
if ($rows -eq '1,2,101') { Ok 'user-level rows = exactly the checked pages' } else { Bad ('user-level rows mismatch: ' + $rows) }

Write-Output '--- 4) perm_test only sees those pages (API + sidebar + 403 on a removed page)'
$treeU = Req 'GET' "$api/system/menu/tree/user" $null $tokU
$cntU = CountNodes $treeU.data
Write-Output ("  perm_test menu nodes = $cntU")
if ([int]$cntU -eq 3) { Ok 'API tree returns exactly 3 nodes (home + base catalog + product page)' } else { Bad ('API tree node count = ' + $cntU + ' (expected 3)') }
UseToken $tokU
$probe = ((EvalJs2 $jsSidebar).Trim().Trim([char]34)) -split '@'
Write-Output ("  sidebar flags(home,product,saleOrder,saleGroupTitle)=[" + $probe[0] + "] count=" + $probe[1] + " items=" + $probe[2])
if ($probe[0] -eq 'YYNN') { Ok 'sidebar shows only the custom pages (home + product; no sales order)' } else { Bad ('sidebar flags = ' + $probe[0] + ' (expected YYNN)') }
# NOTE: deliberately NOT using OpenFresh for case (a) (it clears the cached menu list); UseToken above
# already loaded the app once, so the guard sees perm_test's real menu set.
agent-browser open "$base$saleRoute" | Out-Null
agent-browser wait 3200
$p403a = EvalJs2 'location.pathname'
Write-Output ("  (a) warm cache, direct URL -> path=$p403a")
if ($p403a -match '/403') { Ok 'removed page is blocked with a warm menu cache' } else { Bad ('removed page not blocked (warm cache): ' + $p403a) }
# (b) COLD menu cache: this is the case that used to slip through (guard used to fail open when menus were empty)
EvalJs2 "localStorage.removeItem('beichen_erp_menus'); 'cleared'" | Out-Null
agent-browser open "$base$saleRoute" | Out-Null
agent-browser wait 3600
$p403b = EvalJs2 'location.pathname'
Write-Output ("  (b) cold cache, direct URL -> path=$p403b")
if ($p403b -match '/403') { Ok 'removed page is blocked even with a cleared menu cache' } else { Bad ('removed page not blocked (cold cache): ' + $p403b) }

Write-Output '--- 5) guardrails'
$r = Req 'PUT' "$api/system/user/1/menus" @{ menuMode = 'CUSTOM'; menuIds = @(1) } $tok
Write-Output ("  edit own permission -> code=" + (CodeOf $r) + " msg=" + (MsgOf $r))
if ((CodeOf $r) -ne '200') { Ok 'editing your own permission is rejected' } else { Bad 'editing your own permission was allowed' }
$r = Req 'PUT' "$api/system/user/$uid/menus" @{ menuMode = 'CUSTOM'; menuIds = @() } $tok
Write-Output ("  empty custom set -> code=" + (CodeOf $r) + " msg=" + (MsgOf $r))
if ((CodeOf $r) -ne '200') { Ok 'empty custom set is rejected' } else { Bad 'empty custom set was allowed' }
& $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e ("INSERT IGNORE INTO sys_user_role (user_id, role_id) SELECT $uid, id FROM sys_role WHERE role_code='super_admin';") 2>$null | Out-Null
$r = Req 'PUT' "$api/system/user/$uid/menus" @{ menuMode = 'CUSTOM'; menuIds = @(1, 2, 101) } $tok
Write-Output ("  custom for a super_admin user -> code=" + (CodeOf $r) + " msg=" + (MsgOf $r))
if ((CodeOf $r) -ne '200') { Ok 'super_admin user cannot be customised' } else { Bad 'super_admin user was customised' }
& $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e ("DELETE FROM sys_user_role WHERE user_id=$uid AND role_id=(SELECT id FROM sys_role WHERE role_code='super_admin');") 2>$null | Out-Null
$r = Req 'PUT' "$api/system/user/$uid/menus" @{ menuMode = 'CUSTOM'; menuIds = @(601) } $tok
$rows = SqlRaw "SELECT GROUP_CONCAT(menu_id ORDER BY menu_id) FROM sys_user_menu WHERE user_id=$uid"
Write-Output ("  only sales order checked -> code=" + (CodeOf $r) + " rows=$rows")
if ($rows -eq '1,6,601') { Ok 'ancestor catalog (6) and home page (1) are auto-added' } else { Bad ('auto-completion mismatch: ' + $rows + ' (expected 1,6,601)') }

Write-Output '--- 6) back to ROLE: user-level rows cleared, role menu set restored'
$r = Req 'PUT' "$api/system/user/$uid/menus" @{ menuMode = 'ROLE' } $tok
if ((CodeOf $r) -eq '200') { Ok 'switched back to ROLE (200)' } else { Bad ('switch back failed: ' + (MsgOf $r)) }
$mode = SqlOne "SELECT menu_mode FROM sys_user WHERE id=$uid"
$left = SqlOne "SELECT COUNT(*) FROM sys_user_menu WHERE user_id=$uid"
if ($mode -eq 'ROLE' -and $left -eq '0') { Ok 'mode = ROLE and user-level rows = 0' } else { Bad ('mode=' + $mode + ' rows=' + $left) }
$treeU2 = Req 'GET' "$api/system/menu/tree/user" $null $tokU
$cntU2 = CountNodes $treeU2.data
Write-Output ("  perm_test menu nodes after restore = $cntU2 (baseline $baselineCount)")
if ("$cntU2" -eq "$baselineCount") { Ok 'menu set is back to the role-following baseline' } else { Bad ('restored count = ' + $cntU2 + ' vs baseline ' + $baselineCount) }

Write-Output '--- 7) UI: permission dialog opens from the user list'
UseToken $tok
OpenFresh "$base/system/user"
$btnPermB = B64 (ZH 'btn_perm')
$jsOpen = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const vis=e=>e.getClientRects().length>0;const B=T('$btnPermB');const row=[...document.querySelectorAll('.el-table__row')].filter(vis).find(tr=>(tr.innerText||'').indexOf('perm_test')>=0);if(!row)return 'NOROW';const b=[...row.querySelectorAll('button')].find(x=>((x.innerText||'').trim())===B);if(!b)return 'NOBTN';b.click();return 'OK'})()"
$clicked = (EvalJs2 $jsOpen).Trim().Trim([char]34)
agent-browser wait 1500
$dlgB = B64 (ZH 'text_perm_dialog')
$custB = B64 (ZH 'opt_perm_custom')
$jsDlg = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const D=T('$dlgB'),C=T('$custB');const vis=e=>e.getClientRects().length>0;const d=[...document.querySelectorAll('.el-dialog')].filter(vis).find(x=>(x.innerText||'').indexOf(D)>=0);if(!d)return 'NODLG';const radio=[...d.querySelectorAll('.el-radio')].find(x=>((x.innerText||'').trim())===C);if(!radio)return 'NORADIO';radio.click();return 'OK'})()"
$switched = (EvalJs2 $jsDlg).Trim().Trim([char]34)
agent-browser wait 1200
$treeJs = "(()=>{const vis=e=>e.getClientRects().length>0;const t=[...document.querySelectorAll('.el-dialog .el-tree')].filter(vis).length;const cb=[...document.querySelectorAll('.el-dialog .el-tree .el-checkbox')].filter(vis).length;return t+'@'+cb})()"
$tp = ((EvalJs2 $treeJs).Trim().Trim([char]34)) -split '@'
Write-Output ("  click=$clicked dialog/radio=$switched trees=" + $tp[0] + " checkboxes=" + $tp[1])
if ($clicked -eq 'OK' -and $switched -eq 'OK') { Ok 'permission dialog opens with the custom mode switchable' } else { Bad ('dialog flow broken: ' + $clicked + '/' + $switched) }
if ([int]$tp[0] -ge 1 -and [int]$tp[1] -gt 10) { Ok ('menu tree rendered in the dialog (' + $tp[1] + ' checkboxes)') } else { Bad ('menu tree not rendered: ' + ($tp -join '@')) }
$cancelB = B64 (ZH 'btn_cancel')
EvalJs2 "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const B=T('$cancelB');const b=[...document.querySelectorAll('.el-dialog button')].filter(x=>x.getClientRects().length>0).find(x=>((x.innerText||'').trim())===B);if(b)b.click();return 'ok'})()" | Out-Null
agent-browser wait 900

Write-Output '--- 8) restore the admin (lin) session for subsequent scripts'
$tokA = Login 'lin' '123'
if ($tokA -ne '') { UseToken $tokA; Ok 'admin (lin) session restored' } else { Bad 'failed to restore the admin session' }

if ($fail -eq 0) { Write-Output 'RESULT PASS per-user page permission works end to end' } else { Write-Output ('RESULT FAIL count ' + $fail); exit 1 }
