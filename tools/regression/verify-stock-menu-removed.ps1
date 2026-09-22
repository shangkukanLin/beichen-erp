# 下线验证（2026-09-18 用户要求）：成品库存的子菜单「成品库存」不要了，相关代码删除
#   背景：701「成品库存」（/inventory/stock）下线 —— 前端页面 stock.vue 已删、路由改为重定向到
#         「成品库存情况」（/inventory/product-stock），菜单 701 置 visible=0（保留行与授权便于回滚）。
#   断言：① DB：701 visible=0 且行仍在（可回滚）② 侧栏「成品库存」组里不再有「成品库存」这一项（8 项；
#        2026-09-22 起 702 成品仓库管理已按用户要求迁入「基础数据」，本组由 9 项变 8 项）
#         ③ 旧地址 /inventory/stock 重定向到 /inventory/product-stock（老书签不吃 403/404）
#         ④ 首页「成品库存」TAB 快捷入口不再有「成品库存」按钮、仍有「成品库存情况」
# 注意：①先清 localStorage 菜单缓存 ②本文件必须 **纯 ASCII**。
$ErrorActionPreference = 'Continue'
$base = 'http://localhost:5173'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$fail = 0
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
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

Write-Output '--- 1) DB: menu 701 hidden but row kept (rollback-friendly)'
$v701 = SqlOne 'SELECT visible FROM sys_menu WHERE id=701'
$p701 = SqlOne 'SELECT parent_id FROM sys_menu WHERE id=701'
$r701 = SqlOne 'SELECT route_path FROM sys_menu WHERE id=701'
$cnt701 = SqlOne 'SELECT COUNT(*) FROM sys_menu WHERE id=701'
Write-Output ("  701 rows=$cnt701 visible=$v701 parent=$p701 route=$r701")
if ($cnt701 -eq '1') { Ok '701 row still exists (kept for rollback)' } else { Bad ('701 row missing (count=' + $cnt701 + ')') }
if ($v701 -eq '0') { Ok '701 hidden from the sidebar (visible=0)' } else { Bad ('701 visible = ' + $v701) }
if (($p701 -eq '7') -and ($r701 -eq '/inventory/stock')) { Ok '701 parent/route unchanged (rollback differs only by visible)' } else { Bad ('701 parent/route changed: ' + $p701 + '/' + $r701) }

Write-Output '--- 2) UI: sidebar no longer lists that item'
OpenFresh "$base/dashboard"
$stkLbl = B64 (ZH 'menu_stock')
$jsMenu = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const K=T('$stkLbl');const g=[...document.querySelectorAll('.el-menu .el-sub-menu')].map(s=>({t:(s.querySelector('.el-sub-menu__title')||{}).innerText.trim(),c:[...s.querySelectorAll(':scope > .el-menu > li')].map(li=>li.innerText.trim())}));const hit=g.find(x=>x.t.indexOf(K)>=0)||{t:'',c:[]};return JSON.stringify({title:hit.t,c:hit.c});})()"
$raw = (EvalJs2 $jsMenu).Replace('\"', '"')
$m = [regex]::Match($raw, '\{.*\}')
if ($m.Success) {
  $d = $m.Value | ConvertFrom-Json
  $kids = @($d.c)
  Write-Output ('  group=' + $d.title + ' children=' + ($kids -join ' | '))
  $gone = ZH 'menu_stock'
  $idx = [array]::IndexOf($kids, $gone)
  if ($idx -lt 0) { Ok 'the removed item is no longer in the group' } else { Bad ('item still present at index ' + $idx) }
  $kept = ZH 'txt_prod_stock'
  if ($kids -contains $kept) { Ok ('the surviving product-dimension page is still there (' + $kept + ')') } else { Bad ($kept + ' missing from the group') }
  if ($kids.Count -eq 8) { Ok ('group now has 8 items (was 10; 702 moved to 基础数据 on 2026-09-22)') } else { Bad ('group item count = ' + $kids.Count + ' (expected 8)') }
} else { Bad ('cannot read sidebar: ' + $raw) }

Write-Output '--- 3) UI: old address /inventory/stock redirects to the surviving page'
OpenFresh "$base/inventory/stock"
$path = EvalJs2 "location.pathname"
Write-Output ("  /inventory/stock -> " + $path)
if ($path -match '/inventory/product-stock') { Ok ('old bookmark redirects to ' + $path + ' (no 403/404)') } else { Bad ('old address did not redirect: ' + $path) }

Write-Output '--- 4) UI: dashboard quick entries drop the removed button'
OpenFresh "$base/dashboard"
# 注意：侧栏也有同名菜单项「成品库存」，用 ClickText 会先点到侧栏 → 必须精确定位首页的 TAB 头
$tabJs = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const K=T('$stkLbl');const vis=e=>e.getClientRects().length>0;const t=[...document.querySelectorAll('.el-tabs__item')].filter(vis).find(x=>((x.innerText||'').trim())===K);if(!t)return 'NOTAB';t.click();return 'OK'})()"
$tabClicked = EvalJs2 $tabJs
Write-Output ("  switch dashboard tab: " + $tabClicked)
agent-browser wait 1800
# 精确比对放在 JS 侧完成，回传 "n@hasGone@hasKept@labels"（避免 JSON 转义层数问题）
$goneB64 = B64 (ZH 'menu_stock')
$keptB64 = B64 (ZH 'txt_prod_stock')
$probe = (EvalJs2 "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const G=T('$goneB64'),K=T('$keptB64');const vis=e=>e.getClientRects().length>0;const lbl=[...document.querySelectorAll('.quick-links button')].filter(vis).map(b=>(b.innerText||'').trim());return lbl.length+'@'+(lbl.includes(G)?'Y':'N')+'@'+(lbl.includes(K)?'Y':'N')+'@'+lbl.join('|')})()").Trim().Trim([char]34)
$p = @("$probe" -split '@')
$nBtns = [int]($p[0]); $hasGone = ($p[1] -eq 'Y'); $hasKept = ($p[2] -eq 'Y'); $labels = if ($p.Count -gt 3) { $p[3] } else { '' }
Write-Output ('  quick-links(' + $nBtns + ')= ' + $labels)
if ($nBtns -gt 0) { Ok ('dashboard quick entries read (' + $nBtns + ' buttons)') } else { Bad ('cannot read dashboard quick entries: ' + $probe) }
if ($hasGone) { Bad 'dashboard still has the removed quick button' } else { Ok 'dashboard quick entries no longer contain the removed button' }
if ($hasKept) { Ok 'dashboard keeps the surviving product-dimension entry' } else { Bad 'dashboard lost the surviving entry' }

if ($fail -eq 0) { Write-Output 'RESULT PASS stock-query menu removed; page deleted; old address redirects' } else { Write-Output ('RESULT FAIL count ' + $fail); exit 1 }
