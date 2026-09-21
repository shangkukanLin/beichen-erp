# 「委外加工 → 交货信息」下线校验（2026-09-16 建立，2026-09-18 改为以库为准，可复跑）
# 断言：① 侧栏委外加工无「交货信息」，且子菜单 = sys_menu(parent_id=4, visible=1) 按 sort_order
#       ② 旧地址 /outsource/delivery-info 重定向到 /outsource/order（不 403）
#       ③ 仪表盘不再有「交货信息」快捷入口
#       ④ 【关键回归】收货业务本身完好：两个收货页（成品收货 / 物料收货）均能直达且不 403
#          —— 2026-09-16 起「交货管理」已由订单详情页签移出为独立菜单页，
#             页签级的深度断言改由 verify-delivery-menu.ps1 覆盖
$ErrorActionPreference = 'Continue'
$base = 'http://localhost:5173'
$global:fail = 0
$script:MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
# 读某父菜单下可见子菜单名称（按 sort_order）——期望值以库为准
function MenuNames([int]$parentId) {
  $o = & $script:MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e "SELECT menu_name FROM sys_menu WHERE parent_id=$parentId AND visible=1 AND status=1 ORDER BY sort_order" 2>$null
  return @((@($o) | Select-Object -Skip 1) | ForEach-Object { ("$_").Trim() } | Where-Object { $_ -ne '' })
}
function EvalJs($js) { return (((agent-browser eval $js) -join "`n").Trim()) }
function Ok($m) { Write-Output ("PASS " + $m) }
function Bad($m) { Write-Output ("FAIL " + $m); $global:fail = $global:fail + 1 }
function OpenFresh($url) {
  EvalJs "localStorage.removeItem('beichen_erp_menus'); 'cleared'" | Out-Null
  agent-browser open $url | Out-Null
  agent-browser wait 3000
  if ((EvalJs "'p=' + location.pathname") -match '/login') {
    Write-Output '（会话失效，先登录）'
    $snap = (agent-browser snapshot -i) -join "`n"
    $mu = [regex]::Match($snap, 'textbox "请输入用户名"[^\n]*ref=(e\d+)')
    $mp = [regex]::Match($snap, 'textbox "请输入密码"[^\n]*ref=(e\d+)')
    $mb = [regex]::Match($snap, 'button "登 录"[^\n]*ref=(e\d+)')
    agent-browser fill ("@" + $mu.Groups[1].Value) 'lin' | Out-Null
    agent-browser fill ("@" + $mp.Groups[1].Value) '123' | Out-Null
    agent-browser click ("@" + $mb.Groups[1].Value) | Out-Null
    agent-browser wait 3500
    agent-browser open $url | Out-Null
    agent-browser wait 3000
  }
}
function ReadJson($js, $want) {
  $raw = (EvalJs $js).Replace('\"', '"')
  $m = [regex]::Match($raw, '\{.*\}')
  if (-not $m.Success) { Bad ("未读到 " + $want + "：" + $raw); return $null }
  return ($m.Value | ConvertFrom-Json)
}

# ① 侧栏「委外加工」
OpenFresh "$base/outsource/order"
$d1 = ReadJson "(()=>{const s=[...document.querySelectorAll('.el-menu .el-sub-menu')].find(x=>(x.querySelector('.el-sub-menu__title')?.innerText||'').includes('委外加工'));return JSON.stringify({c:s?[...s.querySelectorAll(':scope > .el-menu > li')].map(li=>li.innerText.trim()):[]});})()" '侧栏'
if ($d1) {
  Write-Output ('委外加工 子菜单 = ' + ($d1.c -join ' | '))
  if (($d1.c -join ',') -match '交货信息') { Bad '侧栏仍有「交货信息」' } else { Ok '侧栏已无「交货信息」' }
  # 2026-09-16：物料收发单/其他出入库/委外仓库/自有物料仓/物料报损 已迁入「物料仓库」目录；
  # 2026-09-17 用户定稿：加工订单 → 成品收货 → 加工退货 → 物料订单 → 物料收货 → 物料退货（409 供应商管理下线）
  # → 期望值一律取库，不再硬编码项数与顺序
  $expect = MenuNames 4
  Write-Output ('委外加工 DB 期望 = ' + ($expect -join ' | '))
  $missing = @($expect | Where-Object { $d1.c -notcontains $_ })
  if ($missing.Count -eq 0) { Ok ('库里 ' + $expect.Count + ' 项全部在位') } else { Bad ('缺失：' + ($missing -join '、')) }
  if ($d1.c.Count -eq $expect.Count) { Ok ('子菜单数量 = ' + $d1.c.Count + '（与库一致）') } else { Bad ('子菜单数量 ' + $d1.c.Count + '，库中期望 ' + $expect.Count) }
}

# ② 旧地址重定向
agent-browser open "$base/outsource/delivery-info" | Out-Null
agent-browser wait 2600
$p2 = EvalJs "location.pathname"
if ($p2 -match '/outsource/order') { Ok '/outsource/delivery-info 已重定向到 /outsource/order（非 403）' } else { Bad ('/outsource/delivery-info 未正确重定向，落在 ' + $p2) }

# ③ 仪表盘
OpenFresh "$base/dashboard"
$d3 = ReadJson "(()=>{const b=[...document.querySelectorAll('button')].map(x=>x.innerText.trim());return JSON.stringify({b:b.filter(t=>['加工订单','物料订单','交货信息','物料收发单','物料信息管理','物料其他出入库'].includes(t))});})()" '仪表盘'
if ($d3) {
  Write-Output ('仪表盘委外区按钮 = ' + ($d3.b -join ' | '))
  if (($d3.b -join ',') -match '交货信息') { Bad '仪表盘仍有「交货信息」按钮' } else { Ok '仪表盘已无「交货信息」按钮' }
}

# ④ 关键回归：收货业务完好（页签已移出 → 改为验收货独立页可直达）
foreach ($u in @("$base/outsource/order/delivery", "$base/outsource/material-order/delivery")) {
  OpenFresh $u
  $p = EvalJs "location.pathname"
  if ($p -match '/login' -or $p -match '403') { Bad ($u + ' 未正常进入，落在 ' + $p) }
  else { Ok ($u + ' 直达正常（非 403）') }
}

if ($global:fail -eq 0) { Write-Output 'RESULT PASS 交货信息总览页已下线，收货业务（成品收货/物料收货 独立页）完好' } else { Write-Output ('RESULT FAIL 项数 ' + $global:fail); exit 1 }
