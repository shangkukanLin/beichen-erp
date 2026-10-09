# 滞销口径守卫（2026-10-09 用户口径变更，报告 §7.33）。
#
# 用户原话（两次，第二次覆盖第一次）：
#   ① 「90 天这个不要。15 天应该是最新来货（委外加工或者是成品购入）后，15 天这个产品没有销售记录的。就算滞销。」
#   ② 「还是按上一次销售吧，但是如果没有上一次销售记录，就按最近来货」（最终口径）
# ⇒ ① 独立的「统计窗口（默认 90 天）」整体取消（含「期间销量 / 周转天数」两列）；
#    ② 滞销起算点 = **最后销售日**；**没有销售记录**（从未卖出过）的产品按**最近来货日**起算；
#       两者都没有的一律算滞销（停滞天数显示 —）。⚠️ 来货**不再**重置时钟。
#
# 本守卫钉六件事：
#   (1) 响应里**不再有** recentDays / saleQtyRecent / turnoverDays（窗口与其两个派生字段真的删了）；
#   (2) 每行 lastInDate == SQL 白名单（PURCHASE_IN / OUTSOURCE_FINISH_IN）的 MAX(create_time)（口径精确到行）；
#   (3) 每行 stagnantDays == 今天 − 起算点（有销售 ⇒ 最后销售日；无销售 ⇒ 最近来货日），stagnant 与阈值一致；
#   (4) **判别用例 A（有销售 ⇒ 只认销售）**：把某产品的全部采购入库挪到 5 天前、全部已审核销售挪到 40 天前
#       ⇒ 起算点必须仍是**销售**（40 ≥ 15 ⇒ 滞销），而不是来货（若按来货会得 5、判不滞销）；
#   (5) **判别用例 B（无销售 ⇒ 认来货）**：给一个"从未销售"的产品临时插一条 20 天前的采购入库流水
#       ⇒ 必须按来货起算（停滞天数 20、判滞销），而不是因为没有销售就一律算滞销且天数为 —；
#   (6) UI：控件区不再有下拉、表格列 10 → 9、控件有说明 tooltip、横向不溢出；两处临时数据**必须还原**。
#
# ⚠️ 为什么非要做 (4)(5)：现库里成品几乎都是最近 9 天内进出的 ⇒ 只跑 (2)(3) 时新口径与旧口径可能**同值**，
#    守卫会在"口径其实没改"的情况下照样全绿 ⇒ 判别用例是唯一能证伪的做法（本仓"守卫必须能证伪"的一贯要求）。
#
# ⚠️ 四条已踩过的坑（2026-10-09 实跑时撞全，写法已固定）：
#    · **单元素数组会被 PS 解包成字符串** ⇒ `$x[0]` 变成"首字符"（'5439' ⇒ '5'）⇒ 取 SQL 行一律 `@(SqlRows ...)`；
#    · 期望停滞天数**不能在 SQL 里用 GREATEST**：MySQL 的 GREATEST 只要有一个参数是 NULL 就返回 NULL
#      （Java 侧是分支判断 ⇒ 应用是对的）⇒ 分别取两个日期、在 PS 里选（与 Java 同构）；
#    · 挪时间戳必须挪**该产品的全部同类流水**，只挪一条会被次新的顶回去 ⇒ 判例失效（假绿）；
#    · 面板默认「仅看滞销]」⇒ 不能拿 `onlyStagnant=false` 的行去 DOM 里找（会假红）。
#
# ASCII-only on purpose（PS 5.1 + BOM 陷阱）：文案比对放在浏览器内，或干脆用结构性断言（元素/字段/计数）。
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')

$ErrorActionPreference = 'Continue'
$API = 'http://localhost:8080/api'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$WL = "'PURCHASE_IN','OUTSOURCE_FINISH_IN'"
$TAG = 'GUARD-TEMP'

function SqlRows([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -N -B -e $q 2>$null
  return @(@($o) | Where-Object { $_ -notmatch '^(mysql:|ERROR)' })
}
function SqlOne([string]$q) { $l = @(SqlRows $q); if ($l.Count -lt 1) { return '' }; return (("$($l[0])" -split "`t")[0]).Trim() }
function SqlExec([string]$q) { & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null | Out-Null }
function Step([string]$t) { Write-Host ''; Write-Host ('--- ' + $t) }
function DaysAgo([string]$d) { if ($d -eq '') { return '' }; return [string][int]((Get-Date).Date - [datetime]$d).Days }

Open '/dashboard' 2400
EvalJs "localStorage.removeItem('beichen_erp_token'); localStorage.removeItem('beichen_erp_user'); 'cleared'" | Out-Null
Start-Sleep -Milliseconds 500
EnsureLogin
WatchErrors
$tok = (EvalJs "localStorage.getItem('beichen_erp_token')") -replace '"', ''
Ok ($tok -and $tok.Length -gt 10) 'logged in (token present)'

function Api([string]$path) {
  try { return Invoke-RestMethod -Uri ($API + $path) -Method Get -Headers @{ Authorization = $tok } -TimeoutSec 30 }
  catch { return $null }
}
$res = Api '/warehouse/stock/stagnant/page?onlyStagnant=false&pageSize=200'
$rows = @($res.data.records)
$topNames = @($res.data.PSObject.Properties.Name)
$th = [int]$res.data.threshold
Write-Host ('  top-level fields: ' + ($topNames -join ','))
Write-Host ('  rows=' + $rows.Count + ' threshold=' + $th)

# ---------- (1) 窗口与两个派生字段必须消失 ----------
Step '(1) the 90-day window and its two derived fields are GONE'
Ok ($topNames -notcontains 'recentDays') 'the response no longer carries recentDays (the statistics window is gone)'
if ($rows.Count -gt 0) {
  $rowNames = @($rows[0].PSObject.Properties.Name)
  Write-Host ('  row fields: ' + ($rowNames -join ','))
  Ok ($rowNames -notcontains 'saleQtyRecent') 'rows no longer carry saleQtyRecent (the period-qty column is gone)'
  Ok ($rowNames -notcontains 'turnoverDays') 'rows no longer carry turnoverDays (the turnover column is gone)'
  Ok ($rowNames -contains 'lastInDate') 'rows now carry lastInDate (the latest-inbound column)'
} else { Skip 'no rows returned -> field-level checks skipped' }

# ---------- (2)(3) 每行口径与 SQL 对齐 ----------
Step '(2)(3) per-row caliber: lastInDate == MAX(whitelist inbound); stagnantDays == today - (lastSale ? lastSale : lastIn)'
$badIn = 0; $badDays = 0; $badFlag = 0; $checked = 0
foreach ($r in $rows) {
  $pk = [int]$r.productId
  $ls = SqlOne ("SELECT IFNULL(DATE_FORMAT(MAX(o.create_time),'%Y-%m-%d'),'') FROM sale_order o JOIN sale_order_item i ON i.order_id=o.id WHERE o.status=0x41554449544544 AND i.product_id=$pk")
  $li = SqlOne ("SELECT IFNULL(DATE_FORMAT(MAX(create_time),'%Y-%m-%d'),'') FROM warehouse_stock_log WHERE product_id=$pk AND change_quantity>0 AND change_type IN ($WL)")
  # 起算点：有销售 ⇒ 销售；没有销售 ⇒ 来货（与 Java 的分支同构；不要用 SQL 的 GREATEST，遇 NULL 会变 NULL）
  $ref = if ($ls -ne '') { $ls } else { $li }
  $expDays = DaysAgo $ref

  $actIn = ''
  if ($r.lastInDate -ne $null) { $actIn = [string]$r.lastInDate }
  if ($li -ne $actIn) { $badIn++; Write-Host ("  !! product=$pk lastInDate api=" + $actIn + " sql=" + $li) }

  $actDays = ''
  if ($r.stagnantDays -ne $null) { $actDays = [string]$r.stagnantDays }
  if ($expDays -ne $actDays) { $badDays++; Write-Host ("  !! product=$pk stagnantDays api=" + $actDays + " expected=" + $expDays + " (lastSale=" + $ls + " lastIn=" + $li + ")") }

  $expFlag = ($expDays -eq '') -or ([int]$expDays -ge $th)
  if ([bool]$r.stagnant -ne $expFlag) { $badFlag++; Write-Host ("  !! product=$pk stagnant api=" + $r.stagnant + " expected=" + $expFlag) }
  $checked++
}
Write-Host ("  checked $checked row(s)")
Ok ($checked -gt 0) 'at least one product row was checked'
Ok ($badIn -eq 0) 'every lastInDate equals the latest WHITELISTED inbound date (purchase / outsourced return)'
Ok ($badDays -eq 0) 'every stagnantDays equals today - the clock start (last sale, or latest inbound when never sold)'
Ok ($badFlag -eq 0) 'every stagnant flag matches the threshold applied to that value'

# ---------- (4) 判别用例 A：有销售 ⇒ 只认销售（来货不再重置时钟）----------
Step '(4) CASE A - a product WITH sales: a 5-day-old inbound must NOT reset the clock (the 40-day-old sale rules)'
$candA = @(SqlRows "SELECT i.product_id FROM sale_order o JOIN sale_order_item i ON i.order_id=o.id JOIN warehouse_stock ws ON ws.product_id=i.product_id AND ws.quantity>0 WHERE o.status=0x41554449544544 AND EXISTS (SELECT 1 FROM warehouse_stock_log l WHERE l.product_id=i.product_id AND l.change_type='PURCHASE_IN') GROUP BY i.product_id ORDER BY COUNT(*) DESC LIMIT 1")
$pidA = if ($candA.Count -ge 1) { (("$($candA[0])" -split "`t")[0]).Trim() } else { '' }
Ok ($pidA -ne '') ('picked a product with both sales and purchase-inbounds: id=' + $pidA)
if ($pidA -eq '') {
  Skip 'no product with both a purchase inbound and audited sales -> case A cannot run'
} else {
  $logsA = @(SqlRows "SELECT id, DATE_FORMAT(create_time,'%Y-%m-%d %H:%i:%s') FROM warehouse_stock_log WHERE product_id=$pidA AND change_type='PURCHASE_IN'")
  $salesA = @(SqlRows "SELECT id, DATE_FORMAT(create_time,'%Y-%m-%d %H:%i:%s') FROM sale_order WHERE status=0x41554449544544 AND id IN (SELECT order_id FROM sale_order_item WHERE product_id=$pidA)")
  $maxInOrig = SqlOne ("SELECT IFNULL(DATE_FORMAT(MAX(create_time),'%Y-%m-%d %H:%i:%s'),'') FROM warehouse_stock_log WHERE product_id=$pidA AND change_type='PURCHASE_IN'")
  Write-Host ('  fixture A: product=' + $pidA + ' inbound logs=' + $logsA.Count + ' (latest ' + $maxInOrig + ') audited orders=' + $salesA.Count)
  Ok ($logsA.Count -ge 1 -and $salesA.Count -ge 1) 'fixture A has both inbound logs and audited sale orders'
  if ($logsA.Count -lt 1 -or $salesA.Count -lt 1) {
    Skip 'fixture A incomplete -> case A cannot run'
  } else {
    $saleIdsA = @($salesA | ForEach-Object { (("$_" -split "`t")[0]).Trim() })
    try {
      SqlExec ("UPDATE warehouse_stock_log SET create_time = DATE_SUB(NOW(), INTERVAL 5 DAY) WHERE product_id=$pidA AND change_type='PURCHASE_IN'")
      SqlExec ("UPDATE sale_order SET create_time = DATE_SUB(NOW(), INTERVAL 40 DAY) WHERE id IN (" + ($saleIdsA -join ',') + ")")
      $oneA = Api ('/warehouse/stock/stagnant/page?onlyStagnant=false&pageSize=10&productId=' + $pidA)
      $rfA = @($oneA.data.records)
      if ($rfA.Count -lt 1) { Bad 'the case-A fixture vanished from the stagnant endpoint while the case was set up' }
      else {
        $f = $rfA[0]
        $d = ''; if ($f.stagnantDays -ne $null) { $d = [string]$f.stagnantDays }
        Write-Host ('  case A after moving: lastInDate=' + $f.lastInDate + ' lastSaleDate=' + $f.lastSaleDate + ' stagnantDays=' + $d + ' stagnant=' + $f.stagnant)
        Ok ($d -eq '40') 'stagnantDays follows the LAST SALE (40), NOT the 5-day-old inbound -> the arrival did not reset the clock'
        Ok ([bool]$f.stagnant -eq $true) 'the product IS stagnant again (40 >= 15) although stock arrived 5 days ago'
      }
    } finally {
      foreach ($lrow in $logsA) { $q = @("$lrow" -split "`t"); SqlExec ("UPDATE warehouse_stock_log SET create_time='" + $q[1].Trim() + "' WHERE id=" + $q[0].Trim()) }
      foreach ($srow in $salesA) { $q = @("$srow" -split "`t"); SqlExec ("UPDATE sale_order SET create_time='" + $q[1].Trim() + "' WHERE id=" + $q[0].Trim()) }
    }
    $backMaxIn = SqlOne ("SELECT IFNULL(DATE_FORMAT(MAX(create_time),'%Y-%m-%d %H:%i:%s'),'') FROM warehouse_stock_log WHERE product_id=$pidA AND change_type='PURCHASE_IN'")
    $badRestoreA = 0
    foreach ($srow in $salesA) { $q = @("$srow" -split "`t"); if ((SqlOne ("SELECT DATE_FORMAT(create_time,'%Y-%m-%d %H:%i:%s') FROM sale_order WHERE id=" + $q[0].Trim())) -ne $q[1].Trim()) { $badRestoreA++ } }
    foreach ($lrow in $logsA) { $q = @("$lrow" -split "`t"); if ((SqlOne ("SELECT DATE_FORMAT(create_time,'%Y-%m-%d %H:%i:%s') FROM warehouse_stock_log WHERE id=" + $q[0].Trim())) -ne $q[1].Trim()) { $badRestoreA++ } }
    Write-Host ('  case A restored: latest inbound=' + $backMaxIn + ' ; rows with wrong time=' + $badRestoreA)
    Ok ($backMaxIn -eq $maxInOrig -and $badRestoreA -eq 0) 'case A: every touched timestamp is back to its original value'
  }
}

# ---------- (5) 判别用例 B：没有销售记录 ⇒ 按最近来货 ----------
Step '(5) CASE B - a product NEVER sold: the clock starts at its latest inbound (temporary inbound inserted, then removed)'
$pidB = SqlOne "SELECT ws.product_id FROM warehouse_stock ws WHERE ws.product_id IS NOT NULL AND ws.quantity>0 AND NOT EXISTS (SELECT 1 FROM sale_order o JOIN sale_order_item i ON i.order_id=o.id WHERE o.status=0x41554449544544 AND i.product_id=ws.product_id) LIMIT 1"
Ok ($pidB -ne '') ('picked a never-sold product holding stock: id=' + $pidB)
if ($pidB -eq '') {
  Skip 'no never-sold product holds stock -> case B cannot run'
} else {
  $tmp = 0
  try {
    SqlExec ("INSERT INTO warehouse_stock_log (warehouse_id, product_id, stock_form, quality_type, change_type, change_quantity, before_quantity, after_quantity, related_bill_no, remark, company_id, create_time) SELECT ws.warehouse_id, ws.product_id, ws.stock_form, ws.quality_type, 'PURCHASE_IN', 1, 0, 0, '$TAG', 'guard temp inbound', ws.company_id, DATE_SUB(NOW(), INTERVAL 20 DAY) FROM warehouse_stock ws WHERE ws.product_id=$pidB AND ws.quantity>0 LIMIT 1")
    $tmp = [int](SqlOne ("SELECT COUNT(*) FROM warehouse_stock_log WHERE related_bill_no='$TAG'"))
    Ok ($tmp -eq 1) 'a temporary 20-day-old purchase inbound was inserted for the never-sold product'
    $oneB = Api ('/warehouse/stock/stagnant/page?onlyStagnant=false&pageSize=10&productId=' + $pidB)
    $rfB = @($oneB.data.records)
    if ($rfB.Count -lt 1) { Bad 'the case-B fixture vanished from the stagnant endpoint' }
    else {
      $f = $rfB[0]
      $d = ''; if ($f.stagnantDays -ne $null) { $d = [string]$f.stagnantDays }
      Write-Host ('  case B: lastSaleDate=' + $f.lastSaleDate + ' lastInDate=' + $f.lastInDate + ' stagnantDays=' + $d + ' stagnant=' + $f.stagnant)
      Ok ($f.lastSaleDate -eq $null) 'the fixture really has no sale record (lastSaleDate is empty)'
      Ok ($d -eq '20') 'without any sale the clock starts at the latest inbound (20) instead of showing a dash'
      Ok ([bool]$f.stagnant -eq $true) 'the never-sold product is stagnant (20 >= 15)'
    }
  } finally {
    SqlExec ("DELETE FROM warehouse_stock_log WHERE related_bill_no='$TAG'")
  }
  $left = [int](SqlOne ("SELECT COUNT(*) FROM warehouse_stock_log WHERE related_bill_no='$TAG'"))
  Ok ($left -eq 0) 'the temporary inbound log was removed (fixture left as found)'
}

# ---------- (6) UI ----------
Step '(6) UI: no window selector, a tooltip on the threshold, 9 columns, no overflow'
Open '/inventory/product-stock' 3600
$uiJs = "(()=>{const vis=e=>e.getClientRects().length>0;const card=[...document.querySelectorAll('.stagnant-card')].filter(vis)[0];if(!card)return 'NOCARD';const sel=card.querySelectorAll('.st-controls .el-select').length;const num=card.querySelectorAll('.st-controls .el-input-number').length;const tip=card.querySelectorAll('.st-controls .el-tooltip__trigger').length;const tb=card.querySelector('.el-table');const th=tb?tb.querySelectorAll('.el-table__header th').length:0;const body=tb?tb.querySelector('.el-table__body-wrapper'):null;const of=body?(body.scrollWidth-body.clientWidth):-1;return JSON.stringify({sel:sel,num:num,tip:tip,th:th,of:of})})()"
$ui = EvalJs $uiJs
Write-Host ('  panel probe >> ' + $ui)
$U = $ui | ConvertFrom-Json
Ok ($U.sel -eq 0) 'the panel controls no longer contain any dropdown (the statistics-window selector is gone)'
Ok ($U.num -eq 1) 'exactly one number input remains (the stagnant threshold)'
Ok ($U.tip -ge 1) 'the threshold now carries an explanatory tooltip (the two bare numbers were the confusion source)'
Ok ($U.th -eq 9) ('the stagnant table has 9 columns (was 10: period-qty + turnover out, latest-inbound in) - got ' + $U.th)
Ok ($U.of -le 0) 'the stagnant table still fits horizontally (no overflow introduced)'
Ok ((Errs) -eq '[]') 'no JS/API errors during the whole flow'

Summary 'stagnant caliber: last sale rules, latest inbound only as the fallback for never-sold products'
