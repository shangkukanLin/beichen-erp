# 菜单迁移验证（2026-09-18 用户要求）：「退货整理」从「销售业务」移到「成品库存」
#   背景：707 退货整理（/inventory/return-sort）原 parent=6（销售业务），2026-09-18 迁到 parent=7（成品库存）末尾。
#         ID 保持 707 不变（存量角色授权按 ID 关联）；同时给持有 707 的角色补授父目录 7（否则菜单树整组丢弃）。
#   断言：① DB：707 parent_id=7 / sort_order=10 / visible=1 / 路由未变
#         ② DB：所有持有 707 的角色都持有父目录 7（无授权缺口）
#         ③ 侧栏「销售业务」= 销售单 | 销售退单 | 销售换货单（已无退货整理）
#         ④ 侧栏「成品库存」顺序 = 用户 2026-09-18 定稿的 9 项（退货整理第 2 位；同日 701 已下线）
#         ⑤ 成品库存 → 退货整理 可点开且落到 /inventory/return-sort（不被 403 拦）
#         ⑥ 2026-09-18 用户要求：707 补授给 **仓管员(warehouse) / 跟单专员(merchandiser)**（原只有 admin）
#         ⑦ 以**跟单专员账号**（audit_merch）实测：侧栏出现退货整理 + 页面可达无 403，随后恢复 admin 会话
# 注意：①先清 localStorage 菜单缓存（后端改菜单后旧缓存会让首屏误判 403）②本文件必须 **纯 ASCII**。
$ErrorActionPreference = 'Continue'
$base = 'http://localhost:5173'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$fail = 0
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')   # 仅用 ZH/B64；Ok/Bad 在本文件后定义覆盖
function Ok($msg) { Write-Output ("PASS " + $msg) }
function Bad($msg) { Write-Output ("FAIL " + $msg); $script:fail++ }
function EvalJs2($js) { return (((agent-browser eval $js) -join "`n").Trim()) }
function SqlLines([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null
  return @((@($o) | Select-Object -Skip 1) | ForEach-Object { "$_" } | Where-Object { $_ -ne '' })
}
function SqlOne([string]$q) { $l = @(SqlLines $q); if ($l.Count -lt 1) { return '' }; return (($l[0] -split "`t")[0]).Trim() }
# 跨角色验证用：直接用接口换 token 灌进 localStorage（避免改登录表单），不打印 token 本身
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

Write-Output '--- 1) DB: 707 under 成品库存(7) at position 2, route unchanged, group order as user ordered'
$p707 = SqlOne 'SELECT parent_id FROM sys_menu WHERE id=707'
$s707 = SqlOne 'SELECT sort_order FROM sys_menu WHERE id=707'
$v707 = SqlOne 'SELECT visible FROM sys_menu WHERE id=707'
$r707 = SqlOne 'SELECT route_path FROM sys_menu WHERE id=707'
$n707 = SqlOne 'SELECT route_name FROM sys_menu WHERE id=707'
Write-Output ("  707 parent=$p707 sort=$s707 visible=$v707 route=$r707 name=$n707")
if ($p707 -eq '7') { Ok '707 (return-sort) now sits under the finished-goods catalog (parent_id=7)' } else { Bad ('707 parent_id = ' + $p707 + ' (expected 7)') }
if ($s707 -eq '2') { Ok '707 is the 2nd item of that catalog (sort_order=2)' } else { Bad ('707 sort_order = ' + $s707 + ' (expected 2)') }
if ($v707 -eq '1') { Ok '707 still visible' } else { Bad ('707 visible = ' + $v707) }
if (($r707 -eq '/inventory/return-sort') -and ($n707 -eq 'InventoryReturnSort')) { Ok '707 route/route_name unchanged (/inventory/return-sort)' } else { Bad ('707 route unexpected: ' + $r707 + '/' + $n707) }
# DB-level order check by ID (ASCII only, encoding-proof):
# 706 成品移仓单 > 707 退货整理 > 712 成品库存情况 > 703 成品库存流水 > 711 库存盘点 >
# 713 成品报损 > 704 成品其他出入库 > 705 成品品质重分类 > 702 成品仓库管理
$dbIds = ((SqlLines 'SELECT id FROM sys_menu WHERE parent_id=7 AND visible=1 ORDER BY sort_order, id') -join ',')
Write-Output ('  visible group ids in order = ' + $dbIds)
if ($dbIds -eq '706,707,712,703,711,713,704,705,702') { Ok 'DB sort_order sequence exactly as user ordered' }
else { Bad ('DB sequence mismatch: ' + $dbIds) }
$s701 = SqlOne 'SELECT sort_order FROM sys_menu WHERE id=701'
if ($s701 -eq '99') { Ok '701 (retired row) moved to sort_order=99 so it cannot tie with 707' } else { Bad ('701 sort_order = ' + $s701 + ' (expected 99)') }

Write-Output '--- 2) DB: no role holds 707 without the parent catalog 7 (otherwise the group is dropped)'
$gap = SqlOne 'SELECT COUNT(*) FROM (SELECT DISTINCT role_id FROM sys_role_menu WHERE menu_id=707) t WHERE t.role_id NOT IN (SELECT role_id FROM sys_role_menu WHERE menu_id=7)'
$holders = SqlOne 'SELECT COUNT(DISTINCT role_id) FROM sys_role_menu WHERE menu_id=707'
Write-Output ("  roles holding 707 = $holders ; missing parent 7 = $gap")
if ($gap -eq '0') { Ok 'every role holding 707 also holds catalog 7' } else { Bad ('roles with 707 but without 7 = ' + $gap) }
foreach ($role in @('admin', 'warehouse', 'merchandiser')) {
  $n = SqlOne ("SELECT COUNT(*) FROM sys_role_menu rm JOIN sys_role r ON r.id=rm.role_id WHERE r.role_code='" + $role + "' AND rm.menu_id=707")
  if ($n -eq '1') { Ok ($role + ' granted 707 (can open return-sort)') } else { Bad ($role + ' missing 707') }
}

Write-Output '--- 3) UI: sidebar structure (as admin; self-heal if a previous run left another role logged in)'
$tokPre = ApiLogin 'lin' '123'
if ($tokPre -ne '') { UseToken $tokPre } else { Write-Output '  WARN cannot pre-login as admin; using the current browser session' }
OpenFresh "$base/dashboard"
$saleLbl = B64 (ZH 'menu_sale')
$stkLbl = B64 (ZH 'menu_stock')
$jsMenu = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const S=T('$saleLbl');const K=T('$stkLbl');const g=[...document.querySelectorAll('.el-menu .el-sub-menu')].map(s=>({t:(s.querySelector('.el-sub-menu__title')||{}).innerText.trim(),c:[...s.querySelectorAll(':scope > .el-menu > li')].map(li=>li.innerText.trim())}));return JSON.stringify({sale:(g.find(x=>x.t.indexOf(S)>=0)||{t:'',c:[]}).c,stock:(g.find(x=>x.t.indexOf(K)>=0)||{t:'',c:[]}).c});})()"
$raw = (EvalJs2 $jsMenu).Replace('\"', '"')
$m = [regex]::Match($raw, '\{.*\}')
if ($m.Success) {
  $d = $m.Value | ConvertFrom-Json
  $sale = @($d.sale)
  $stock = @($d.stock)
  Write-Output ('  sales children = ' + ($sale -join ' | '))
  Write-Output ('  stock children = ' + ($stock -join ' | '))
  $sortLbl = ZH 'menu_sale_sort'
  if ($sale -contains $sortLbl) { Bad 'return-sort still under sales' } else { Ok 'return-sort removed from sales' }
  $expectSale = @((ZH 'menu_sale_order'), (ZH 'menu_sale_return'), (ZH 'menu_sale_exchange'))
  if (($sale -join '|') -eq ($expectSale -join '|')) { Ok ('sales children exactly as expected (' + ($sale -join ' -> ') + ')') }
  else { Bad ('sales children mismatch; expect ' + ($expectSale -join '|') + ' got ' + ($sale -join '|')) }
  $idx = [array]::IndexOf($stock, $sortLbl)
  if ($idx -ge 0) { Ok ('return-sort now under the finished-goods catalog (index ' + $idx + ')') } else { Bad 'return-sort missing under the finished-goods catalog' }
  # 2026-09-18 user-defined order (9 items, see DataInitializer seed + docs); Chinese literals go through ui-e2e-zh.json
  $expectStock = @((ZH 'menu_stock_move'), (ZH 'menu_sale_sort'), (ZH 'txt_prod_stock'), (ZH 'menu_stock_log'), (ZH 'menu_stock_take'), (ZH 'menu_stock_loss'), (ZH 'menu_stock_io'), (ZH 'menu_stock_reclass'), (ZH 'menu_stock_wh'))
  if (($stock -join '|') -eq ($expectStock -join '|')) { Ok ('finished-goods catalog order exactly as user ordered (' + ($stock -join ' -> ') + ')') }
  else { Bad ('finished-goods catalog order mismatch; expect ' + ($expectStock -join '|') + ' got ' + ($stock -join '|')) }
  if ($idx -eq 1) { Ok 'return-sort is the 2nd item as ordered' } else { Bad ('return-sort index = ' + $idx + ' (expected 1, i.e. 2nd)') }
  # 701 removed on the same day (page deleted) => the group dropped from 10 to 9 items
  if ($stock.Count -eq 9) { Ok 'finished-goods catalog has 9 items as expected' } else { Bad ('finished-goods catalog item count = ' + $stock.Count + ' (expected 9)') }
} else { Bad ('cannot read sidebar: ' + $raw) }

Write-Output '--- 4) UI: 成品库存 -> 退货整理 is reachable (no 403)'
$sortLbl2 = B64 (ZH 'menu_sale_sort')
$openJs = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const K=T('$stkLbl');const vis=e=>e.getClientRects().length>0;const s=[...document.querySelectorAll('.el-menu .el-sub-menu')].filter(vis).find(x=>(((x.querySelector('.el-sub-menu__title')||{}).innerText)||'').indexOf(K)>=0);if(!s)return 'NOSUB';const t=s.querySelector('.el-sub-menu__title');if(t)t.click();return 'OK'})()"
EvalJs2 $openJs | Out-Null
agent-browser wait 1200
$itemJs = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const K=T('$stkLbl');const S=T('$sortLbl2');const vis=e=>e.getClientRects().length>0;const s=[...document.querySelectorAll('.el-menu .el-sub-menu')].filter(vis).find(x=>(((x.querySelector('.el-sub-menu__title')||{}).innerText)||'').indexOf(K)>=0);if(!s)return 'NOSUB';const li=[...s.querySelectorAll(':scope > .el-menu > li')].find(x=>((x.innerText||'').trim())===S);if(!li)return 'NOITEM';li.click();return 'OK'})()"
$clicked = EvalJs2 $itemJs
agent-browser wait 2800
$path = EvalJs2 "location.pathname"
Write-Output ("  click=$clicked -> path=$path")
if ($clicked -match 'OK' -and $path -match '/inventory/return-sort') { Ok ('finished-goods -> return-sort opens fine (' + $path + ')') }
else { Bad ('return-sort entry broken: click=' + $clicked + ' path=' + $path) }
if ($path -match '/403') { Bad 'return-sort page blocked by 403 (whitelist missing)' }

Write-Output '--- 5) UI as merchandiser (audit_merch): the grant really works end-to-end (frontend whitelist included)'
$tokM = ApiLogin 'audit_merch' '123'
if ($tokM -ne '') {
  UseToken $tokM
  $rawM = (EvalJs2 $jsMenu).Replace('\"', '"')
  $mM = [regex]::Match($rawM, '\{.*\}')
  if ($mM.Success) {
    $dM = $mM.Value | ConvertFrom-Json
    $stockM = @($dM.stock)
    Write-Output ('  merchandiser stock children = ' + ($stockM -join ' | '))
    if ($stockM -contains $sortLbl) { Ok 'merchandiser sidebar lists return-sort under the finished-goods catalog' } else { Bad 'merchandiser cannot see return-sort in the sidebar' }
  } else { Bad ('cannot read merchandiser sidebar: ' + $rawM) }
  EvalJs2 $openJs | Out-Null
  agent-browser wait 1200
  $clickedM = EvalJs2 $itemJs
  agent-browser wait 2800
  $pathM = EvalJs2 "location.pathname"
  Write-Output ("  merchandiser click=$clickedM -> path=$pathM")
  if ($clickedM -match 'OK' -and $pathM -match '/inventory/return-sort') { Ok ('merchandiser can open return-sort (' + $pathM + ')') } else { Bad ('merchandiser entry broken: ' + $clickedM + ' / ' + $pathM) }
  if ($pathM -match '/403') { Bad 'merchandiser hit 403 on return-sort (frontend whitelist missing)' }
} else { Bad 'cannot obtain a merchandiser token (login failed)' }

Write-Output '--- 6) restore the admin (lin) session so later scripts keep running as admin'
$tokA = ApiLogin 'lin' '123'
if ($tokA -ne '') {
  UseToken $tokA
  Ok 'admin (lin) token restored'
} else { Bad 'failed to restore the admin session' }

if ($fail -eq 0) { Write-Output 'RESULT PASS return-sort menu moved to finished-goods catalog' } else { Write-Output ('RESULT FAIL count ' + $fail); exit 1 }
