# 退货整理页表格宽度验证（2026-09-22 用户要求：列表一行显示完，不要左右滑动）
#   覆盖两个页签：①待整理（跨仓总览，12 列）②整理单（6 列）
#   断言 ① 每张表的横向溢出 = 0（量真正的滚动容器 .el-table__body-wrapper .el-scrollbar__wrap，
#           注意 Element Plus 2.x 外层 body-wrapper 的 scrollWidth 恒等于 clientWidth ⇒ 量它会得到假阴性）
#        ② 列宽合计 <= 容器宽（结构上就不可能溢出）
#        ③ 列数没有被"为了不滚动而删列"：待整理 12 列、整理单 6 列
#        ④ 无 JS 运行时错误
#   ASCII ONLY：中文一律经 ui-e2e-zh.json 注入。
$ErrorActionPreference = 'Continue'
$base = 'http://localhost:5173'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$fail = 0

. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
function Ok($msg) { Write-Output ("PASS " + $msg) }
function Bad($msg) { Write-Output ("FAIL " + $msg); $script:fail++ }
function EvalJs2($js) { return (((agent-browser eval $js) -join "`n").Trim()) }
function SqlOne([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null
  $l = @((@($o) | Select-Object -Skip 1) | ForEach-Object { "$_" } | Where-Object { $_ -ne '' })
  if ($l.Count -lt 1) { return '' }
  return (($l[0] -split "`t")[0]).Trim()
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

# 测量：对每张可见表返回 溢出 / 容器宽 / 列宽合计 / 列数（键名固定，用 node 风格字符串解析）
$measure = @'
(()=>{
  const vis=e=>e.getClientRects().length>0;
  return JSON.stringify([...document.querySelectorAll('.el-table')].filter(vis).map(t=>{
    const wrap=t.querySelector('.el-table__body-wrapper .el-scrollbar__wrap')||t.querySelector('.el-table__body-wrapper');
    const cols=[...t.querySelectorAll('.el-table__header col')].map(c=>Number(c.getAttribute('width')||0));
    return { over: wrap?Math.round(wrap.scrollWidth-wrap.clientWidth):-1,
             wrapW: wrap?Math.round(wrap.clientWidth):0,
             sumCols: cols.reduce((a,b)=>a+b,0), nCols: cols.length };
  }));
})()
'@

EvalJs2 "localStorage.removeItem('beichen_erp_menus'); 'cleared'" | Out-Null
agent-browser open "$base/inventory/return-sort" | Out-Null
agent-browser wait 3400
if ((EvalJs2 "'p=' + location.pathname") -match '/login') { LoginFlow; agent-browser open "$base/inventory/return-sort" | Out-Null; agent-browser wait 3400 }

Write-Output '--- 1) TAB 1 pending overview: fits on one line, 12 columns kept'
$raw1 = (EvalJs2 $measure).Replace('\"', '"')
$m1 = [regex]::Match($raw1, '\[.*\]')
$t1 = @()
if ($m1.Success) { $t1 = @($m1.Value | ConvertFrom-Json) }
Write-Output ('  tables=' + $t1.Count + ' first=' + ($t1[0] | ConvertTo-Json -Compress))
if ($t1.Count -ge 1) {
  $a = $t1[0]
  if ([int]$a.over -le 2) { Ok ('pending overview table does not scroll horizontally (overflow=' + $a.over + 'px)') }
  else { Bad ('pending overview table overflows by ' + $a.over + 'px') }
  if ([int]$a.sumCols -le [int]$a.wrapW + 2) { Ok ('column widths fit the container (' + $a.sumCols + ' <= ' + $a.wrapW + ')') }
  else { Bad ('columns wider than the container: ' + $a.sumCols + ' > ' + $a.wrapW) }
  if ([int]$a.nCols -eq 12) { Ok 'all 12 columns are still present (nothing removed to hide the scrollbar)' }
  else { Bad ('expected 12 columns, got ' + $a.nCols) }
} else { Bad ('cannot measure the pending table: ' + $raw1) }

Write-Output '--- 2) TAB 2 bills: fits on one line, 6 columns kept'
$click = "(()=>{const t=[...document.querySelectorAll('.el-tabs__item')][1];if(!t)return 'NOTAB';t.click();return 'CLICKED';})()"
Write-Output ('  switch tab: ' + (EvalJs2 $click))
agent-browser wait 2800
$raw2 = (EvalJs2 $measure).Replace('\"', '"')
$m2 = [regex]::Match($raw2, '\[.*\]')
$t2 = @()
if ($m2.Success) { $t2 = @($m2.Value | ConvertFrom-Json) }
Write-Output ('  tables=' + $t2.Count + ' first=' + ($t2[0] | ConvertTo-Json -Compress))
if ($t2.Count -ge 1) {
  $b = $t2[0]
  if ([int]$b.over -le 2) { Ok ('bills table does not scroll horizontally (overflow=' + $b.over + 'px)') }
  else { Bad ('bills table overflows by ' + $b.over + 'px') }
  if ([int]$b.sumCols -le [int]$b.wrapW + 2) { Ok ('column widths fit the container (' + $b.sumCols + ' <= ' + $b.wrapW + ')') }
  else { Bad ('columns wider than the container: ' + $b.sumCols + ' > ' + $b.wrapW) }
  if ([int]$b.nCols -eq 6) { Ok 'all 6 columns are still present' }
  else { Bad ('expected 6 columns, got ' + $b.nCols) }
} else { Bad ('cannot measure the bills table: ' + $raw2) }

# ---- 明细表（抽屉开单 / 编辑页是**同一个 form.vue**，2026-09-22 用户要求两处都要一行显示完）----
Write-Output '--- 3) DRAWER form: the item table (form.vue, embedded) fits one line'
# 点待整理表第一行的操作按钮打开抽屉（有该按钮的行就是 SORTABLE 行，避免依赖中文文案）
agent-browser open "$base/inventory/return-sort" | Out-Null
agent-browser wait 3400
$clickRow = "(()=>{const vis=e=>e.getClientRects().length>0;const b=[...document.querySelectorAll('.el-table__body td:last-child button')].filter(vis);if(!b.length)return 'NOBTN';b[0].click();return 'CLICKED';})()"
Write-Output ('  open drawer: ' + (EvalJs2 $clickRow))
agent-browser wait 3000
# 只量抽屉内的表格（抽屉盖在列表上，外层那张表还在 DOM 里，量它没意义）
$measureDrawer = @'
(()=>{
  const vis=e=>e.getClientRects().length>0;
  const root=document.querySelector('.el-drawer')||document;
  return JSON.stringify([...root.querySelectorAll('.el-table')].filter(vis).map(t=>{
    const wrap=t.querySelector('.el-table__body-wrapper .el-scrollbar__wrap')||t.querySelector('.el-table__body-wrapper');
    const cols=[...t.querySelectorAll('.el-table__header col')].map(c=>Number(c.getAttribute('width')||0));
    return { over: wrap?Math.round(wrap.scrollWidth-wrap.clientWidth):-1, wrapW: wrap?Math.round(wrap.clientWidth):0,
             sumCols: cols.reduce((a,b)=>a+b,0), nCols: cols.length, drawerW: Math.round(root.getBoundingClientRect().width) };
  }));
})()
'@
$rawD = (EvalJs2 $measureDrawer).Replace('\"', '"')
$mD = [regex]::Match($rawD, '\[.*\]')
$tD = @()
if ($mD.Success) { $tD = @($mD.Value | ConvertFrom-Json) }
Write-Output ('  drawer tables=' + $tD.Count + ' first=' + ($tD[0] | ConvertTo-Json -Compress))
if ($tD.Count -ge 1) {
  $d = $tD[0]
  if ([int]$d.over -le 2) { Ok ('drawer item table does not scroll horizontally (overflow=' + $d.over + 'px, drawer=' + $d.drawerW + ')') }
  else { Bad ('drawer item table overflows by ' + $d.over + 'px') }
  if ([int]$d.sumCols -le [int]$d.wrapW + 2) { Ok ('drawer item columns fit the container (' + $d.sumCols + ' <= ' + $d.wrapW + ')') }
  else { Bad ('drawer item columns wider than the container: ' + $d.sumCols + ' > ' + $d.wrapW) }
  if ([int]$d.nCols -eq 13) { Ok 'all 13 item columns are still present (the redundant source-date column was the one dropped)' }
  else { Bad ('expected 13 item columns, got ' + $d.nCols) }
} else { Bad ('cannot measure the drawer item table: ' + $rawD) }

Write-Output '--- 4) EDIT page (same form.vue) also fits one line'
$draftId = SqlOne "SELECT id FROM return_sort WHERE status='DRAFT' ORDER BY id DESC LIMIT 1"
Write-Output ('  draft id = ' + $draftId)
if ([int]$draftId -gt 0) {
  agent-browser open "$base/inventory/return-sort/edit/$draftId" | Out-Null
  agent-browser wait 3400
  $rawE = (EvalJs2 $measure).Replace('\"', '"')
  $mE = [regex]::Match($rawE, '\[.*\]')
  $tE = @()
  if ($mE.Success) { $tE = @($mE.Value | ConvertFrom-Json) }
  Write-Output ('  edit page tables=' + $tE.Count + ' first=' + ($tE[0] | ConvertTo-Json -Compress))
  if ($tE.Count -ge 1) {
    $e = $tE[0]
    if ([int]$e.over -le 2) { Ok ('edit page item table does not scroll horizontally (overflow=' + $e.over + 'px)') }
    else { Bad ('edit page item table overflows by ' + $e.over + 'px') }
    if ([int]$e.sumCols -le [int]$e.wrapW + 2) { Ok ('edit page item columns fit the container (' + $e.sumCols + ' <= ' + $e.wrapW + ')') }
    else { Bad ('edit page item columns wider than the container: ' + $e.sumCols + ' > ' + $e.wrapW) }
  } else { Bad ('cannot measure the edit page table: ' + $rawE) }
} else { Bad 'no DRAFT return-sort row to open the edit page with' }

if ($fail -eq 0) { Write-Output 'RESULT PASS return-sort tables fit one line (2 list tabs + drawer + edit page, no horizontal scroll)' } else { Write-Output ('RESULT FAIL count ' + $fail); exit 1 }
