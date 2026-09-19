# 菜单精简验证（2026-09-18 用户要求）：下线「销售业务 → 客户管理」重复入口
#   背景：602 客户管理 与 基础数据 105 客户管理 同指 /inventory/customer（重复）；只保留 105。
#   断言：① 602 已 visible=0、105 仍 visible=1 ② sales/merchandiser 已补授 105（否则会丢客户管理）
#         ③ 侧栏「销售业务」子项里没有客户管理 ④ 「基础数据」里客户管理仍在且是首项
#         ⑤ 基础数据 → 客户管理 可点开且落到 /inventory/customer（不被 403 拦）
# 注意：①先清 localStorage 的菜单缓存（后端改菜单后旧缓存会导致首屏误判 403，见文档 §12.73）
#       ②本文件必须保持 **纯 ASCII**：PS 5.1 会按 ANSI 读无 BOM 的 .ps1，中文源码会乱码甚至解析失败；
#         所有中文文案经 ui-e2e-zh.json 以 base64 注入页面/比较。
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

Write-Output '--- 1) DB: 602 hidden, 105 visible, roles keep 105'
$v602 = SqlOne 'SELECT visible FROM sys_menu WHERE id=602'
$v105 = SqlOne 'SELECT visible FROM sys_menu WHERE id=105'
$p105 = SqlOne 'SELECT parent_id FROM sys_menu WHERE id=105'
$r105 = SqlOne 'SELECT route_path FROM sys_menu WHERE id=105'
Write-Output ("  602 visible=$v602 ; 105 visible=$v105 parent=$p105 route=$r105")
if ($v602 -eq '0') { Ok '602 (sale-menu customer) hidden: visible=0, row+grants kept for rollback' } else { Bad ('602 still visible: ' + $v602) }
if ($v105 -eq '1') { Ok '105 (base-data customer) still visible' } else { Bad ('105 not visible: ' + $v105) }
if (($p105 -eq '2') -and ($r105 -eq '/inventory/customer')) { Ok '105 sits under base data and points to /inventory/customer' } else { Bad ('105 parent/route unexpected: ' + $p105 + '/' + $r105) }
foreach ($role in @('sales', 'merchandiser')) {
  $n = SqlOne ("SELECT COUNT(*) FROM sys_role_menu rm JOIN sys_role r ON r.id=rm.role_id WHERE r.role_code='" + $role + "' AND rm.menu_id=105")
  if ($n -eq '1') { Ok ($role + ' granted 105 (keeps the customer menu)') } else { Bad ($role + ' missing 105 -> would lose customer menu') }
}

Write-Output '--- 2) UI: sidebar structure'
OpenFresh "$base/dashboard"
$saleLbl = B64 (ZH 'menu_sale')
$baseLbl = B64 (ZH 'menu_base')
$jsMenu = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const S=T('$saleLbl');const B=T('$baseLbl');const g=[...document.querySelectorAll('.el-menu .el-sub-menu')].map(s=>({t:(s.querySelector('.el-sub-menu__title')||{}).innerText.trim(),c:[...s.querySelectorAll(':scope > .el-menu > li')].map(li=>li.innerText.trim())}));return JSON.stringify({sale:(g.find(x=>x.t.indexOf(S)>=0)||{t:'',c:[]}).c,base:(g.find(x=>x.t.indexOf(B)>=0)||{t:'',c:[]}).c});})()"
$raw = (EvalJs2 $jsMenu).Replace('\"', '"')
$m = [regex]::Match($raw, '\{.*\}')
if ($m.Success) {
  $d = $m.Value | ConvertFrom-Json
  $sale = @($d.sale)
  $bas = @($d.base)
  Write-Output ('  sale children = ' + ($sale -join ' | '))
  Write-Output ('  base children = ' + ($bas -join ' | '))
  if ($sale -contains (ZH 'menu_cust')) { Bad 'customer menu still under sale' } else { Ok 'no customer menu under sale' }
  # 2026-09-18：退货整理迁到「成品库存」，销售业务只剩 3 项
  $expectSale = @((ZH 'menu_sale_order'), (ZH 'menu_sale_return'), (ZH 'menu_sale_exchange'))
  if (($sale -join '|') -eq ($expectSale -join '|')) { Ok ('sale children exactly as expected (' + ($sale -join ' -> ') + ')') }
  else { Bad ('sale children mismatch; expect ' + ($expectSale -join '|') + ' got ' + ($sale -join '|')) }
  if ($sale -contains (ZH 'menu_sale_sort')) { Bad 'return-sort still under sale (should be moved to finished-goods)' } else { Ok 'return-sort no longer under sale' }
  if ($bas -contains (ZH 'menu_cust')) { Ok 'customer menu still under base data' } else { Bad 'customer menu missing under base data' }
  if ($bas.Count -gt 0 -and $bas[0] -eq (ZH 'menu_cust')) { Ok 'customer menu is still the first item of base data' } else { Bad ('first base item = ' + $(if ($bas.Count -gt 0) { $bas[0] } else { '(none)' })) }
} else { Bad ('cannot read sidebar: ' + $raw) }

Write-Output '--- 3) UI: base data -> customer menu is reachable (no 403)'
$custLbl = B64 (ZH 'menu_cust')
$openJs = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const B=T('$baseLbl');const vis=e=>e.getClientRects().length>0;const s=[...document.querySelectorAll('.el-menu .el-sub-menu')].filter(vis).find(x=>(((x.querySelector('.el-sub-menu__title')||{}).innerText)||'').indexOf(B)>=0);if(!s)return 'NOSUB';const t=s.querySelector('.el-sub-menu__title');if(t)t.click();return 'OK'})()"
EvalJs2 $openJs | Out-Null
agent-browser wait 1200
$itemJs = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const B=T('$baseLbl');const C=T('$custLbl');const vis=e=>e.getClientRects().length>0;const s=[...document.querySelectorAll('.el-menu .el-sub-menu')].filter(vis).find(x=>(((x.querySelector('.el-sub-menu__title')||{}).innerText)||'').indexOf(B)>=0);if(!s)return 'NOSUB';const li=[...s.querySelectorAll(':scope > .el-menu > li')].find(x=>((x.innerText||'').trim())===C);if(!li)return 'NOITEM';li.click();return 'OK'})()"
$clicked = EvalJs2 $itemJs
agent-browser wait 2600
$path = EvalJs2 "location.pathname"
Write-Output ("  click=$clicked -> path=$path")
# NOTE: agent-browser eval returns JSON, so a string result comes back quoted ("OK") -> match, don't -eq
if ($clicked -match 'OK' -and $path -match '/inventory/customer') { Ok ('base data -> customer opens fine (' + $path + ')') }
else { Bad ('customer entry broken: click=' + $clicked + ' path=' + $path) }
if ($path -match '/403') { Bad 'customer page blocked by 403 (whitelist missing)' }

if ($fail -eq 0) { Write-Output 'RESULT PASS sale-menu customer duplicate removed; base-data entry intact' } else { Write-Output ('RESULT FAIL count ' + $fail); exit 1 }
