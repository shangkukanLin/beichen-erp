# 菜单精简验证（2026-09-18 用户要求）：下线「进货业务 → 供货商管理」重复入口
#   背景：503 供货商管理 与 基础数据 107 供货商管理 同指 /outsource/supplier/manage（同 route_name，重复）；
#         只保留 107。另：merchandiser 历史上只有 409（9-17 已隐藏的委外侧重复入口）⇒ 必须补授 107。
#   断言：① 503 已 visible=0、107 仍 visible=1 ② admin/merchandiser 已补授 107
#         ③ 侧栏「进货业务」子项里没有供货商管理 ④ 「基础数据」里供货商管理仍在（第 4 项）
#         ⑤ 基础数据 → 供货商管理 可点开且落到 /outsource/supplier/manage（不被 403 拦）
# 注意：①先清 localStorage 菜单缓存（后端改菜单后旧缓存会让首屏误判 403，见文档 §12.73）
#       ②本文件必须 **纯 ASCII**（PS 5.1 按 ANSI 读无 BOM 的 .ps1）；中文文案经 ui-e2e-zh.json base64 注入。
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

Write-Output '--- 1) DB: 503 hidden, 107 visible, roles granted 107'
$v503 = SqlOne 'SELECT visible FROM sys_menu WHERE id=503'
$v107 = SqlOne 'SELECT visible FROM sys_menu WHERE id=107'
$p107 = SqlOne 'SELECT parent_id FROM sys_menu WHERE id=107'
$r107 = SqlOne 'SELECT route_path FROM sys_menu WHERE id=107'
Write-Output ("  503 visible=$v503 ; 107 visible=$v107 parent=$p107 route=$r107")
if ($v503 -eq '0') { Ok '503 (purchase-menu supplier) hidden: visible=0, row+grants kept for rollback' } else { Bad ('503 still visible: ' + $v503) }
if ($v107 -eq '1') { Ok '107 (base-data supplier) still visible' } else { Bad ('107 not visible: ' + $v107) }
if (($p107 -eq '2') -and ($r107 -eq '/outsource/supplier/manage')) { Ok '107 sits under base data and points to /outsource/supplier/manage' } else { Bad ('107 parent/route unexpected: ' + $p107 + '/' + $r107) }
foreach ($role in @('admin', 'merchandiser')) {
  $n = SqlOne ("SELECT COUNT(*) FROM sys_role_menu rm JOIN sys_role r ON r.id=rm.role_id WHERE r.role_code='" + $role + "' AND rm.menu_id=107")
  if ($n -eq '1') { Ok ($role + ' granted 107 (keeps the supplier menu)') } else { Bad ($role + ' missing 107 -> would lose the supplier menu') }
}

Write-Output '--- 2) UI: sidebar structure'
OpenFresh "$base/dashboard"
$purLbl = B64 (ZH 'menu_purchase')
$basLbl = B64 (ZH 'menu_base')
$jsMenu = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const P=T('$purLbl');const B=T('$basLbl');const g=[...document.querySelectorAll('.el-menu .el-sub-menu')].map(s=>({t:(s.querySelector('.el-sub-menu__title')||{}).innerText.trim(),c:[...s.querySelectorAll(':scope > .el-menu > li')].map(li=>li.innerText.trim())}));return JSON.stringify({pur:(g.find(x=>x.t.indexOf(P)>=0)||{t:'',c:[]}).c,base:(g.find(x=>x.t.indexOf(B)>=0)||{t:'',c:[]}).c});})()"
$raw = (EvalJs2 $jsMenu).Replace('\"', '"')
$m = [regex]::Match($raw, '\{.*\}')
if ($m.Success) {
  $d = $m.Value | ConvertFrom-Json
  $pur = @($d.pur)
  $bas = @($d.base)
  Write-Output ('  purchase children = ' + ($pur -join ' | '))
  Write-Output ('  base children = ' + ($bas -join ' | '))
  if ($pur -contains (ZH 'menu_supplier_mgr')) { Bad 'supplier menu still under purchase' } else { Ok 'no supplier menu under purchase' }
  # 2026-09-18：进货业务新增 504「采购换货单」，故期望 3 项
  $expectPur = @((ZH 'menu_purchase_1'), (ZH 'menu_purchase_2'), (ZH 'menu_purchase_3'))
  if (($pur -join '|') -eq ($expectPur -join '|')) { Ok ('purchase children exactly as expected (' + ($pur -join ' -> ') + ')') }
  else { Bad ('purchase children mismatch; expect ' + ($expectPur -join '|') + ' got ' + ($pur -join '|')) }
  $idx = [array]::IndexOf($bas, (ZH 'menu_supplier_mgr'))
  if ($idx -ge 0) { Ok ('supplier menu still under base data (index ' + $idx + ')') } else { Bad 'supplier menu missing under base data' }
  if ($idx -eq 3) { Ok 'supplier menu is the 4th item of base data (unchanged)' } else { Bad ('supplier menu index = ' + $idx + ' (expected 3)') }
} else { Bad ('cannot read sidebar: ' + $raw) }

Write-Output '--- 3) UI: base data -> supplier menu is reachable (no 403)'
$supLbl = B64 (ZH 'menu_supplier_mgr')
$openJs = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const B=T('$basLbl');const vis=e=>e.getClientRects().length>0;const s=[...document.querySelectorAll('.el-menu .el-sub-menu')].filter(vis).find(x=>(((x.querySelector('.el-sub-menu__title')||{}).innerText)||'').indexOf(B)>=0);if(!s)return 'NOSUB';const t=s.querySelector('.el-sub-menu__title');if(t)t.click();return 'OK'})()"
EvalJs2 $openJs | Out-Null
agent-browser wait 1200
$itemJs = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const B=T('$basLbl');const S=T('$supLbl');const vis=e=>e.getClientRects().length>0;const s=[...document.querySelectorAll('.el-menu .el-sub-menu')].filter(vis).find(x=>(((x.querySelector('.el-sub-menu__title')||{}).innerText)||'').indexOf(B)>=0);if(!s)return 'NOSUB';const li=[...s.querySelectorAll(':scope > .el-menu > li')].find(x=>((x.innerText||'').trim())===S);if(!li)return 'NOITEM';li.click();return 'OK'})()"
$clicked = EvalJs2 $itemJs
agent-browser wait 2600
$path = EvalJs2 "location.pathname"
Write-Output ("  click=$clicked -> path=$path")
if ($clicked -match 'OK' -and $path -match '/outsource/supplier/manage') { Ok ('base data -> supplier opens fine (' + $path + ')') }
else { Bad ('supplier entry broken: click=' + $clicked + ' path=' + $path) }
if ($path -match '/403') { Bad 'supplier page blocked by 403 (whitelist missing)' }

if ($fail -eq 0) { Write-Output 'RESULT PASS purchase-menu supplier duplicate removed; base-data entry intact' } else { Write-Output ('RESULT FAIL count ' + $fail); exit 1 }
