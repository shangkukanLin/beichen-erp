# 菜单顺序验证（2026-09-22 用户要求）：财务管理 → 子菜单「账单生成」放到「付款管理」下面
#   断言 ① DB：财务管理可见子菜单顺序 id = 805,806,803,809,801,802,804,807,810,811
#             且 sort_order(收款管理) < sort_order(付款管理) < sort_order(账单生成) < sort_order(费用管理)
#        ② UI：左侧栏「财务管理」子项顺序与上面一致（账单生成紧跟付款管理）
#   说明：本次只动 sort_order（id / 路由 / 授权不变，故不涉前端白名单与跳转）；
#        ① 菜单顺序缓存在前端 localStorage（beichen_erp_menus）⇒ 必须先清缓存再打开页面，
#           否则浏览器继续渲染旧顺序（文档 §12.73 的老坑）
#        ② 本文件保持**纯 ASCII**：PS 5.1 按 ANSI 读无 BOM 的 .ps1，中文源码会乱码甚至解析失败；
#           所有中文文案经 ui-e2e-zh.json 以 base64 注入。
#        ③ 侧栏读取用的那段 JS 与 verify-sale-menu-cleanup.ps1 完全同构（该写法已验证可用）。
$ErrorActionPreference = 'Continue'
$base = 'http://localhost:5173'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$fail = 0

. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')   # 仅用它的 ZH/B64/EvalJs/Open（Ok/Summary 在本文件后定义覆盖）
function Ok($msg) { Write-Output ("PASS " + $msg) }
function Bad($msg) { Write-Output ("FAIL " + $msg); $script:fail++ }
function EvalJs2($js) { return (((agent-browser eval $js) -join "`n").Trim()) }
function SqlLines([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null
  return @((@($o) | Select-Object -Skip 1) | ForEach-Object { "$_" } | Where-Object { $_ -ne '' })
}
function SqlOne([string]$q) { $l = @(SqlLines $q); if ($l.Count -lt 1) { return '' }; return (($l[0] -split "`t")[0]).Trim() }

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

Write-Output '--- 1) DB: finance children order'
$ids = @(SqlLines 'SELECT id FROM sys_menu WHERE parent_id=8 AND visible=1 ORDER BY sort_order, id')
$got = ($ids -join ',')
Write-Output ('  db id order = ' + $got)
if ($got -eq '805,806,803,809,801,802,804,807,810,811') { Ok 'finance children are receipt, payment, bill, expense, receivable, payable, cashflow, account, invoice, payable-transfer' }
else { Bad ('finance children mismatch: ' + $got) }
$s805 = [int](SqlOne 'SELECT sort_order FROM sys_menu WHERE id=805')
$s806 = [int](SqlOne 'SELECT sort_order FROM sys_menu WHERE id=806')
$s803 = [int](SqlOne 'SELECT sort_order FROM sys_menu WHERE id=803')
$s809 = [int](SqlOne 'SELECT sort_order FROM sys_menu WHERE id=809')
Write-Output ("  sort: receipt=$s805 payment=$s806 bill=$s803 expense=$s809")
if (($s805 -lt $s806) -and ($s806 -lt $s803) -and ($s803 -lt $s809)) { Ok 'bill sits right below payment (receipt < payment < bill < expense)' }
else { Bad ('sort order not as requested: ' + $s805 + '/' + $s806 + '/' + $s803 + '/' + $s809) }
$route = SqlOne 'SELECT route_path FROM sys_menu WHERE id=803'
if ($route -eq '/finance/bill') { Ok 'only sort_order changed: the bill menu still points to /finance/bill' } else { Bad ('bill route changed: ' + $route) }

Write-Output '--- 2) UI: sidebar finance children order (menu cache cleared first)'
OpenFresh "$base/dashboard"
$finLbl = B64 (ZH 'menu_finance')
$jsMenu = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const F=T('$finLbl');const g=[...document.querySelectorAll('.el-menu .el-sub-menu')].map(s=>({t:(s.querySelector('.el-sub-menu__title')||{}).innerText.trim(),c:[...s.querySelectorAll(':scope > .el-menu > li')].map(li=>li.innerText.trim())}));return JSON.stringify({kids:(g.find(x=>x.t.indexOf(F)>=0)||{t:'',c:[]}).c});})()"
$raw = (EvalJs2 $jsMenu).Replace('\"', '"')
$m = [regex]::Match($raw, '\{.*\}')
if ($m.Success) {
  # NOTE: read the property off the parsed object (same shape as verify-sale-menu-cleanup) --
  # piping the raw JSON into ConvertFrom-Json and wrapping with @() nests an array and prints System.Object[].
  $d = $m.Value | ConvertFrom-Json
  $kids = @($d.kids)
  Write-Output ('  sidebar = ' + ($kids -join ' | '))
  $expect = @((ZH 'menu_fin_receipt'), (ZH 'menu_fin_payment'), (ZH 'menu_fin_bill'), (ZH 'menu_fin_expense'), (ZH 'menu_fin_receivable'), (ZH 'menu_fin_payable'), (ZH 'menu_fin_cashflow'), (ZH 'menu_finance_account'), (ZH 'menu_fin_invoice'), (ZH 'menu_fin_payable_transfer'))
  if (($kids -join '|') -eq ($expect -join '|')) { Ok ('sidebar finance children exactly as expected (' + ($kids -join ' -> ') + ')') }
  else { Bad ('sidebar mismatch; expect ' + ($expect -join '|') + ' got ' + ($kids -join '|')) }
  if ($kids.Count -ge 3 -and $kids[1] -eq (ZH 'menu_fin_payment') -and $kids[2] -eq (ZH 'menu_fin_bill')) { Ok 'bill is directly below payment in the sidebar' }
  else { Bad ('bill is not right below payment: ' + ($kids -join '|')) }
} else { Bad ('cannot read sidebar: ' + $raw) }

if ($fail -eq 0) { Write-Output 'RESULT PASS finance menu: bill moved below payment' } else { Write-Output ('RESULT FAIL count ' + $fail); exit 1 }
