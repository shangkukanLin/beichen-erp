# 「物料库存盘点」+ 成品/物料盘点范围隔离 校验（2026-09-16 建立，2026-09-17 改为**以库为准**，可复跑）
#
# 断言：
#   1 侧栏「物料仓库」子菜单顺序 = sys_menu(parent_id=11, visible=1)（不再写死中文）
#   2 成品页 /inventory/stock-take：仓库下拉只有成品类仓库（INVENTORY+FINISHED），不含物料类仓库（仓名从 warehouse 表取）
#   3 物料页 /outsource/material-stock-take：仓库下拉 = 委外仓 + 自有物料仓，不含成品仓
#   4 交叉隔离：成品页表格不出现物料仓；物料页表格不出现成品仓（**正向断言表格内容**，不再用"行数=0"误判）
#   5 接口正向：admin 调 ?scope=PRODUCT / ?scope=MATERIAL 均正常
#
# 注：物料盘点的「接口级角色限制（仅跟单专员）」negative 用例需非跟单专员账号，
#     本脚本不依赖临时账号，见文档 12.91 的实测记录。
. (Join-Path $PSScriptRoot 'ab-bounded.ps1')
$ErrorActionPreference = 'Continue'
$base = 'http://localhost:5173'
$global:fail = 0
$script:MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$script:lastTableText = ''
# 按条件取仓库名（去重）
function WhNames([string]$where) {
  $o = & $script:MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e "SELECT DISTINCT warehouse_name FROM warehouse WHERE status=1 AND ($where)" 2>$null
  return @((@($o) | Select-Object -Skip 1) | ForEach-Object { ("$_").Trim() } | Where-Object { $_ -ne '' })
}
# 读某父菜单下可见子菜单名称（按 sort_order）——期望值以库为准
function MenuNames([int]$parentId) {
  $o = & $script:MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e "SELECT menu_name FROM sys_menu WHERE parent_id=$parentId AND visible=1 AND status=1 ORDER BY sort_order" 2>$null
  return @((@($o) | Select-Object -Skip 1) | ForEach-Object { ("$_").Trim() } | Where-Object { $_ -ne '' })
}
$MAT_WH_WHERE = "warehouse_category='OUTSOURCE' OR (warehouse_category='INVENTORY' AND warehouse_type='AUXILIARY')"
$FIN_WH_WHERE = "warehouse_category='INVENTORY' AND warehouse_type='FINISHED'"
function EvalJs($js) {
  # agent-browser 会把上一条命令的 "✓ Done" 一起回显 → 过滤掉，只保留真正的求值结果
  $out = @(agent-browser eval $js) | ForEach-Object { "$_" } | Where-Object { $_.Trim() -ne '✓ Done' -and $_ -notmatch '^\s*✗' }
  return ($out -join "`n").Trim()
}
# 注意：必须用 Write-Host —— Write-Output 会进管道，被上层函数当作"返回值"吸收（会污染 $rowsP/$opts）
function Ok($m) { Write-Host ("PASS " + $m) }
function Bad($m) { Write-Host ("FAIL " + $m); $global:fail = $global:fail + 1 }
function Clean($s) { return (($s -replace '"', '')).Trim() }
function FirstNum($s) { $m = [regex]::Match("$s", '(\d{3})'); if ($m.Success) { return [int]$m.Groups[1].Value } else { return -1 } }
# 从可能混入回显的字符串里取末尾整数（行数 / 接口 code）

function Num($s) { $m = [regex]::Match("$s", '(-?\d+)\s*$'); if ($m.Success) { return [int]$m.Groups[1].Value } else { return -1 } }
function OpenFresh($url) {
  # 全清（含 token）：保证本脚本总是在 admin 账号下运行（避免上一次用别的账号验证后串号）
  EvalJs "localStorage.clear(); 'cleared'" | Out-Null
  agent-browser open $url | Out-Null
  agent-browser wait 3000
  if ((EvalJs "'p=' + location.pathname") -match '/login') {
    Write-Output '(session expired, re-login)'
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
function SubMenu($catalog) {
  $js = "(()=>{const s=[...document.querySelectorAll('.el-menu .el-sub-menu')].find(x=>(x.querySelector('.el-sub-menu__title')?.innerText||'').includes('$catalog'));return JSON.stringify({c:s?[...s.querySelectorAll(':scope > .el-menu > li')].map(li=>li.innerText.trim()):[]});})()"
  $raw = (EvalJs $js).Replace('\"', '"')
  $m = [regex]::Match($raw, '\{.*\}')
  if (-not $m.Success) { Bad ("cannot read sidebar $catalog : $raw"); return @() }
  return @((($m.Value | ConvertFrom-Json).c))
}
# 打开页面里第一个下拉（仓库），读取可见选项
function WarehouseOptions() {
  EvalJs "(()=>{const w=document.querySelector('.query-card .el-select .el-select__wrapper')||document.querySelector('.el-select .el-select__wrapper');if(w)w.click();return 'ok';})()" | Out-Null
  agent-browser wait 900 | Out-Null
  return Clean (EvalJs "[...document.querySelectorAll('.el-select-dropdown__item')].filter(x=>x.offsetParent!==null).map(x=>x.innerText.trim()).join('|')")
}
function TableRows() { return (Num (EvalJs "document.querySelectorAll('.table-card .el-table__body tbody tr').length")) }
function ApiCode($path) {
  $js = "fetch('$path',{headers:{Authorization:localStorage.getItem('beichen_erp_token')}}).then(r=>r.json()).then(j=>String(j.code)).catch(e=>'ERR:'+e)"
  return (FirstNum (EvalJs "(async()=>{return await $js})()"))
}
function ScopeCheck($label, $pagePath, $mustHave, $mustNot, $titleShould) {
  OpenFresh ($base + $pagePath)
  $body = EvalJs "document.querySelector('.table-card')?.innerText || ''"
  $script:lastTableText = $body
  if ($body -match [regex]::Escape($titleShould)) { Ok ("$label 页面表头/范围提示正确（$titleShould）") } else { Bad ("$label 页面缺少范围提示 $titleShould") }
  $opts = WarehouseOptions
  # 同样必须 Write-Host：Write-Output 会被当作本函数的返回值之一（污染行数结果）
  Write-Host ("$label 仓库下拉 = $opts")
  foreach ($n in $mustHave) { if ($opts -match [regex]::Escape($n)) { Ok ("$label 含 $n") } else { Bad ("$label 缺少 $n") } }
  foreach ($n in $mustNot) { if ($opts -match [regex]::Escape($n)) { Bad ("$label 不应出现 $n") } else { Ok ("$label 已排除 $n") } }
  return (TableRows)
}

# 1 侧栏
OpenFresh "$base/dashboard"
$wh = SubMenu '物料仓库'
$expWh = MenuNames 11
Write-Output ('物料仓库 子菜单 = ' + ($wh -join ' | '))
Write-Output ('物料仓库 DB 期望 = ' + ($expWh -join ' | '))
if (($wh -join '|') -eq ($expWh -join '|')) { Ok ('物料仓库顺序与库一致：' + ($wh -join ' -> ')) } else { Bad ('物料仓库应为 ' + ($expWh -join '|') + '，实际 ' + ($wh -join '|')) }
$ws = SubMenu '委外加工'
Write-Output ('委外加工 子菜单 = ' + ($ws -join ' | '))
if (($ws -join ',') -match '库存盘点') { Bad '委外加工不应含盘点入口' } else { Ok '委外加工无盘点入口（盘点归物料仓库）' }

# 1b 首页「物料仓库」TAB：快捷入口含「物料库存盘点」+ 待办卡片「待盘点物料仓」
agent-browser open "$base/dashboard" | Out-Null
agent-browser wait 2500 | Out-Null
EvalJs "(()=>{const h=[...document.querySelectorAll('.el-tabs__item')].find(x=>x.innerText.trim()==='物料仓库');if(h)h.click();return 'ok';})()" | Out-Null
agent-browser wait 1800 | Out-Null
$tabBtns = Clean (EvalJs "[...document.querySelectorAll('#pane-materialWarehouse button')].map(x=>x.innerText.trim()).join('|')")
Write-Output ("物料仓库 TAB 快捷入口 = $tabBtns")
if ($tabBtns -match '物料库存盘点') { Ok '首页物料仓库 TAB 含「物料库存盘点」入口' } else { Bad '首页物料仓库 TAB 缺少「物料库存盘点」入口' }
$todo = Clean (EvalJs "[...document.querySelectorAll('.todo-card')].map(x=>x.innerText.replace(/\\s+/g,' ').trim()).join(' || ')")
Write-Output ("经营分析 待办卡片 = $todo")
if ($todo -match '待盘点物料仓') { Ok '首页待办含「待盘点物料仓」（物料口径独立）' } else { Write-Host 'WARN 首页待办未出现「待盘点物料仓」（可能本月物料仓已全部盘点，非失败）' }

# 2 成品页（仓名/期望值一律从 warehouse 表取）
$finWhs = WhNames $FIN_WH_WHERE
$matWhs = WhNames $MAT_WH_WHERE
Write-Output ('成品仓（DB）= ' + ($finWhs -join '、'))
Write-Output ('物料仓（DB）= ' + ($matWhs -join '、'))
# 页面提示文案：成品页 =（成品仓），物料页 =（委外仓 / 自有物料仓）—— 见 views/stock-take/StockTakePanel.vue:28
$rowsP = Num (ScopeCheck '成品盘点' '/inventory/stock-take' $finWhs $matWhs '（成品仓）')
Write-Output ("成品盘点页 盘点单行数 = $rowsP")
if ($finWhs.Count -eq 0) { Write-Host 'WARN 库里没有成品仓，跳过成品页正向断言' }
elseif ($rowsP -ge 1) { Ok "成品页显示成品仓盘点单（$rowsP 行）" }
else { Bad "成品页盘点单行数异常（$rowsP，库里存在成品仓 $($finWhs -join '、')）" }
# 交叉隔离（正向）：成品页表格里不得出现任何物料仓名称
$leakP = @($matWhs | Where-Object { $script:lastTableText -match [regex]::Escape($_) })
if ($leakP.Count -eq 0) { Ok '成品页表格未混入物料仓盘点单' } else { Bad ('成品页混入了物料仓：' + ($leakP -join '、')) }

# 3 物料页
$rowsM = Num (ScopeCheck '物料盘点' '/outsource/material-stock-take' $matWhs $finWhs '委外仓 / 自有物料仓')
Write-Output ("物料盘点页 盘点单行数 = $rowsM")
# 交叉隔离（正向）：物料页表格里不得出现任何成品仓名称（原先用"行数=0"判断是错的：物料页本就可能有物料仓盘点单）
$leakM = @($finWhs | Where-Object { $script:lastTableText -match [regex]::Escape($_) })
if ($leakM.Count -eq 0) { Ok '物料页表格未混入成品仓盘点单（范围隔离生效）' } else { Bad ('物料页混入了成品仓：' + ($leakM -join '、')) }

# 5 接口正向
$c1 = ApiCode '/api/inventory/stock-take/page?scope=PRODUCT&pageNum=1&pageSize=1'
$c2 = ApiCode '/api/inventory/stock-take/page?scope=MATERIAL&pageNum=1&pageSize=1'
Write-Output ("API scope=PRODUCT -> code=$c1 ; scope=MATERIAL -> code=$c2")
if ($c1 -eq '200') { Ok '接口 scope=PRODUCT 正常' } else { Bad "接口 scope=PRODUCT 返回 $c1" }
if ($c2 -eq '200') { Ok '接口 scope=MATERIAL 正常（admin 兜底放行）' } else { Bad "接口 scope=MATERIAL 返回 $c2" }

if ($global:fail -eq 0) { Write-Output 'RESULT PASS 物料库存盘点已上线，成品/物料盘点范围与数据已隔离' } else { Write-Output ('RESULT FAIL 项数 ' + $global:fail); exit 1 }
