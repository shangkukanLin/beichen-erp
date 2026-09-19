# Rename verification (2026-09-18 user request): finance module submenu 807 relabelled
#   old label  = zh key 'menu_finance_account_old'
#   new label  = zh key 'menu_finance_account'
#   This file MUST stay pure ASCII: every Chinese literal comes from ui-e2e-zh.json (ZH / B64).
# Asserts:
#   1) DB: 807 menu_name == new label (compared as UTF-8 HEX, encoding-proof); id/parent/route/route_name unchanged
#   2) DB: no menu under the finance catalog still carries the old label; whole group order == the sequence the user ordered
#   3) UI: sidebar finance group -> exact user-ordered sequence, new label at position 8, old label absent, 10 items
#   4) UI: /finance/account opens; document.title carries the new label
#   5) UI: clicking the "new account" button opens a dialog titled with the new label
$ErrorActionPreference = 'Continue'
$base = 'http://localhost:5173'
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
function Utf8Hex([string]$s) {
  $b = [System.Text.Encoding]::UTF8.GetBytes($s)
  return (($b | ForEach-Object { $_.ToString('X2') }) -join '')
}
# role-session helpers (see MEMORY): switch token via API instead of touching the login form, and always restore admin
function ApiLogin([string]$user, [string]$pwd) {
  try {
    $body = '{"username":"' + $user + '","password":"' + $pwd + '","companyId":1}'
    $r = Invoke-RestMethod -Uri 'http://localhost:8080/api/auth/login' -Method Post -ContentType 'application/json' -Body $body
    return [string]$r.data.token
  } catch { return '' }
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

$newLbl = ZH 'menu_finance_account'
$oldLbl = ZH 'menu_finance_account_old'

Write-Output '--- 1) DB: 807 carries the new label; identity/route untouched'
$newHex = Utf8Hex $newLbl
$oldHex = Utf8Hex $oldLbl
Write-Output ("  807 HEX(menu_name) = " + (SqlOne 'SELECT HEX(menu_name) FROM sys_menu WHERE id=807'))
if ((SqlOne 'SELECT HEX(menu_name) FROM sys_menu WHERE id=807') -eq $newHex) { Ok '807 menu_name == new label' } else { Bad '807 menu_name != new label' }
$p807 = SqlOne 'SELECT parent_id FROM sys_menu WHERE id=807'
$r807 = SqlOne 'SELECT route_path FROM sys_menu WHERE id=807'
$n807 = SqlOne 'SELECT route_name FROM sys_menu WHERE id=807'
$s807 = SqlOne 'SELECT sort_order FROM sys_menu WHERE id=807'
$v807 = SqlOne 'SELECT visible FROM sys_menu WHERE id=807'
Write-Output ("  807 parent=$p807 route=$r807 name=$n807 sort=$s807 visible=$v807")
if (($p807 -eq '8') -and ($r807 -eq '/finance/account') -and ($n807 -eq 'FinanceAccount')) { Ok '807 parent/route/route_name unchanged (finance catalog, /finance/account)' } else { Bad ('807 identity changed: ' + $p807 + '/' + $r807 + '/' + $n807) }
if ($s807 -eq '8') { Ok '807 sits at position 8 (sort_order=8) per the user-ordered sequence' } else { Bad ('807 sort_order = ' + $s807 + ' (expected 8)') }
if ($v807 -eq '1') { Ok '807 still visible' } else { Bad ('807 visible = ' + $v807) }
$leftOld = SqlOne ("SELECT COUNT(*) FROM sys_menu WHERE parent_id=8 AND HEX(menu_name)='" + $oldHex + "'")
if ($leftOld -eq '0') { Ok 'no finance submenu still carries the old label' } else { Bad ('menus under finance catalog with the old label = ' + $leftOld) }
# DB-level order check by ID (ASCII only, encoding-proof). User order (2026-09-18):
# bill > receipt > payment > expense > receivable > payable > cashflow > account > invoice > payable-transfer
$dbFinIds = ((SqlLines 'SELECT id FROM sys_menu WHERE parent_id=8 AND visible=1 ORDER BY sort_order, id') -join ',')
Write-Output ('  finance ids in order = ' + $dbFinIds)
if ($dbFinIds -eq '803,805,806,809,801,802,804,807,810,811') { Ok 'DB sort_order sequence exactly as user ordered' }
else { Bad ('DB sequence mismatch: ' + $dbFinIds) }
Write-Output ('  roles granted 807 = ' + (SqlOne 'SELECT COUNT(*) FROM sys_role_menu WHERE menu_id=807'))

Write-Output '--- 2) UI: sidebar (as admin; self-heal in case another role is logged in)'
$tokPre = ApiLogin 'lin' '123'
if ($tokPre -ne '') { UseToken $tokPre } else { Write-Output '  WARN cannot pre-login as admin; using the current browser session' }
OpenFresh "$base/dashboard"
$finLblB = B64 (ZH 'menu_finance')
$newLblB = B64 $newLbl
$oldLblB = B64 $oldLbl
$jsMenu = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const F=T('$finLblB'),N=T('$newLblB'),O=T('$oldLblB');const g=[...document.querySelectorAll('.el-menu .el-sub-menu')].map(s=>({t:(s.querySelector('.el-sub-menu__title')||{}).innerText.trim(),c:[...s.querySelectorAll(':scope > .el-menu > li')].map(li=>li.innerText.trim())}));const hit=g.find(x=>x.t.indexOf(F)>=0)||{t:'',c:[]};const i=hit.c.indexOf(N);return hit.c.length+'@'+(i>=0?'Y':'N')+'@'+i+'@'+(hit.c.indexOf(O)>=0?'Y':'N')+'@'+hit.c.join('|')})()"
$raw = (EvalJs2 $jsMenu)
$probe = $raw.Trim().Trim([char]34)
$p = @("$probe" -split '@')
$cnt = if ($p.Count -gt 0) { $p[0] } else { 'ERR' }
$hasNew = ($p.Count -gt 1 -and $p[1] -eq 'Y')
$idx = if ($p.Count -gt 2) { $p[2] } else { '-1' }
$hasOld = ($p.Count -gt 3 -and $p[3] -eq 'Y')
$list = if ($p.Count -gt 4) { $p[4] } else { '' }
Write-Output ("  finance children (count=$cnt) = " + $list)
if ($hasNew) { Ok ('new label present in the sidebar at index ' + $idx + ' (0-based)') } else { Bad 'new label missing from the sidebar' }
if ($hasOld) { Bad 'old label still visible in the sidebar' } else { Ok 'old label no longer appears in the sidebar' }
if ($cnt -eq '10') { Ok 'finance catalog still has 10 items' } else { Bad ('finance catalog item count = ' + $cnt + ' (expected 10)') }
$expectFin = @((ZH 'menu_fin_bill'), (ZH 'menu_fin_receipt'), (ZH 'menu_fin_payment'), (ZH 'menu_fin_expense'), (ZH 'menu_fin_receivable'), (ZH 'menu_fin_payable'), (ZH 'menu_fin_cashflow'), (ZH 'menu_finance_account'), (ZH 'menu_fin_invoice'), (ZH 'menu_fin_payable_transfer'))
if ($list -eq ($expectFin -join '|')) { Ok ('finance catalog order exactly as user ordered (' + ($expectFin -join ' -> ') + ')') }
else { Bad ('finance catalog order mismatch; expect ' + ($expectFin -join '|') + ' got ' + $list) }
if ($idx -eq '7') { Ok 'the renamed item sits at position 8 as ordered' } else { Bad ('new label index = ' + $idx + ' (expected 7, i.e. 8th)') }

Write-Output '--- 3) UI: /finance/account opens and the document title carries the new label'
OpenFresh "$base/finance/account"
$path = EvalJs2 'location.pathname'
Write-Output ("  path=$path")
if ($path -match '/finance/account') { Ok 'account page reachable (no 403)' } else { Bad ('account page did not open: ' + $path) }
$jsTitle = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const N=T('$newLblB');const t=(document.title||'');return (t.indexOf(N)>=0?'Y':'N')+'@'+t})()"
$tp = ((EvalJs2 $jsTitle).Trim().Trim([char]34)) -split '@'
if ($tp[0] -eq 'Y') { Ok 'document.title carries the new label' } else { Bad ('document.title lacks the new label: ' + ($tp -join ' | ')) }

Write-Output '--- 4) UI: the add-account dialog is titled with the new label'
$btnAccB = B64 (ZH 'btn_new_account')
$jsClick = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const B=T('$btnAccB');const b=[...document.querySelectorAll('button')].filter(x=>x.getClientRects().length>0).find(x=>((x.innerText||'').trim())===B);if(!b)return 'NOBTN';b.click();return 'OK'})()"
$clicked = (EvalJs2 $jsClick).Trim().Trim([char]34)
agent-browser wait 1300
$jsDlg = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const N=T('$newLblB');const d=document.querySelector('.el-dialog__title');if(!d)return 'NODLG';const t=(d.innerText||'').trim();return (t===N?'MATCH':'DIFF')+'@'+t})()"
$dp = ((EvalJs2 $jsDlg).Trim().Trim([char]34)) -split '@'
Write-Output ("  click=$clicked dialog=" + ($dp -join '@'))
if ($clicked -match 'OK' -and $dp[0] -eq 'MATCH') { Ok 'add-account dialog title == new label' } else { Bad ('dialog title mismatch: ' + ($dp -join '@')) }
$btnCancelB = B64 (ZH 'btn_cancel')
EvalJs2 "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const B=T('$btnCancelB');const b=[...document.querySelectorAll('.el-dialog button')].filter(x=>x.getClientRects().length>0).find(x=>((x.innerText||'').trim())===B);if(b)b.click();return 'ok'})()" | Out-Null
agent-browser wait 900

if ($fail -eq 0) { Write-Output 'RESULT PASS finance account menu relabelled (807)' } else { Write-Output ('RESULT FAIL count ' + $fail); exit 1 }
