# 退货整理页表格宽度验证（2026-09-22 用户要求：列表/明细一行显示完，不要左右滑动）
#   覆盖 4 处容器：①待整理（跨仓总览，12 列）②整理单（6 列）
#                ③ 从列表跳入的开单页（form.vue：明细 11 列，2026-09-22 由抽屉改为独立页面）
#                ④ 编辑退货整理页（同一 form.vue，明细 11 列）
#   断言 ① 每张表的横向溢出 = 0（量真正的滚动容器 .el-table__body-wrapper .el-scrollbar__wrap，
#           注意 Element Plus 2.x 外层 body-wrapper 的 scrollWidth 恒等于 clientWidth ⇒ 量它会得到假阴性）
#        ② 列宽合计 <= 容器宽（结构上就不可能溢出）
#        ③ 列数没被"为了不滚动而乱删列"：待整理 12 / 整理单 6 / 明细 11（来源日期+SKU+单位 是用户要求去掉的）
#        ④ 开单入口已从抽屉改为独立页面（URL 带 warehouseId/pendingIds 预设）
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

# 测量：对每张可见表返回 溢出 / 容器宽 / 列宽合计 / 列数 / 被裁的表头 / 表头行高
#   被裁判定：th 内 .cell 的 scrollWidth > clientWidth + 1（2026-09-22 用户反馈"标题显示不全"就是它）
#   表头行高 > 44 说明标题被挤成两行（同样是"显示不全"的一种）
$measure = @'
(()=>{
  const vis=e=>e.getClientRects().length>0;
  return JSON.stringify([...document.querySelectorAll('.el-table')].filter(vis).map(t=>{
    const wrap=t.querySelector('.el-table__body-wrapper .el-scrollbar__wrap')||t.querySelector('.el-table__body-wrapper');
    const cols=[...t.querySelectorAll('.el-table__header col')].map(c=>Number(c.getAttribute('width')||0));
    const ths=[...t.querySelectorAll('.el-table__header th')];
    const clipped=ths.filter(th=>{const c=th.querySelector('.cell')||th;return c.scrollWidth>c.clientWidth+1})
                     .map(th=>((th.innerText||'').replace(/\s+/g,' ').trim()));
    return { over: wrap?Math.round(wrap.scrollWidth-wrap.clientWidth):-1,
             wrapW: wrap?Math.round(wrap.clientWidth):0,
             sumCols: cols.reduce((a,b)=>a+b,0), nCols: cols.length,
             clipped: clipped, rowH: ths.length?Math.round(ths[0].getBoundingClientRect().height):0 };
  }));
})()
'@

# 表头完整性断言：一个都不能被裁，且表头行必须是单行（≤44px）
# NOTE: 空数组经 JSON 往返可能变成 $null 或 ['']，直接 .Count 会误判 ⇒ 先滤掉空串再计数
function AssertHeaders([string]$label, $t) {
  $clipped = @($t.clipped | Where-Object { ("$_" -replace '\s', '') -ne '' })
  Write-Output ('  ' + $label + ': clipped=' + $clipped.Count + ' rowH=' + [int]$t.rowH)
  if ($clipped.Count -eq 0) { Ok ($label + ': no header is clipped') }
  else { Bad ($label + ': clipped headers -> ' + ($clipped -join ' / ')) }
  if ([int]$t.rowH -le 44) { Ok ($label + ': header row stays single line (h=' + $t.rowH + 'px)') }
  else { Bad ($label + ': header wrapped to ' + $t.rowH + 'px (titles are squeezed)') }
}

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
  AssertHeaders 'TAB1 pending' $a
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
  AssertHeaders 'TAB2 bills' $b
} else { Bad ('cannot measure the bills table: ' + $raw2) }

# ---- 明细表（「整理待整理品」2026-09-22 由抽屉改为**独立页面**；与编辑页共用 form.vue）----
Write-Output '--- 3) SORT FORM page (opened from the pending tab): item table fits one line'
# 点待整理表第一行的操作按钮（有该按钮的行就是 SORTABLE 行，避免依赖中文文案）⇒ 应跳到独立新增页并带 query 预设
agent-browser open "$base/inventory/return-sort" | Out-Null
agent-browser wait 3400
$clickRow = "(()=>{const vis=e=>e.getClientRects().length>0;const b=[...document.querySelectorAll('.el-table__body td:last-child button')].filter(vis);if(!b.length)return 'NOBTN';b[0].click();return 'CLICKED';})()"
Write-Output ('  row action: ' + (EvalJs2 $clickRow))
agent-browser wait 3200
$p = EvalJs2 'String(location.pathname + location.search)'
Write-Output ('  path=' + $p)
if ($p -match '/inventory/return-sort/add\?warehouseId=') { Ok 'the sort action opens the standalone form page with its preset query (drawer removed)' }
else { Bad ('the sort action did not open the form page: ' + $p) }
$rawD = (EvalJs2 $measure).Replace('\"', '"')
$mD = [regex]::Match($rawD, '\[.*\]')
$tD = @()
if ($mD.Success) { $tD = @($mD.Value | ConvertFrom-Json) }
Write-Output ('  form tables=' + $tD.Count + ' first=' + ($tD[0] | ConvertTo-Json -Compress))
if ($tD.Count -ge 1) {
  $d = $tD[0]
  if ([int]$d.over -le 2) { Ok ('sort form item table does not scroll horizontally (overflow=' + $d.over + 'px)') }
  else { Bad ('sort form item table overflows by ' + $d.over + 'px') }
  if ([int]$d.sumCols -le [int]$d.wrapW + 2) { Ok ('sort form item columns fit the container (' + $d.sumCols + ' <= ' + $d.wrapW + ')') }
  else { Bad ('sort form item columns wider than the container: ' + $d.sumCols + ' > ' + $d.wrapW) }
  # 11 列：来源单据/产品/待整理数量/批次量已整理/停留天数/A/B/C/不良/校验/操作
  # （来源日期 + SKU + 单位 已按用户要求相继去掉）
  if ([int]$d.nCols -eq 11) { Ok 'all 11 item columns are present (source-date, SKU and unit were dropped on request)' }
  else { Bad ('expected 11 item columns, got ' + $d.nCols) }
  AssertHeaders 'sort form items' $d
} else { Bad ('cannot measure the sort form item table: ' + $rawD) }

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
    AssertHeaders 'edit page items' $e
  } else { Bad ('cannot measure the edit page table: ' + $rawE) }
} else { Bad 'no DRAFT return-sort row to open the edit page with' }

if ($fail -eq 0) { Write-Output 'RESULT PASS return-sort tables fit one line (2 list tabs + sort-form page + edit page, no horizontal scroll)' } else { Write-Output ('RESULT FAIL count ' + $fail); exit 1 }
