# 滞销口径守卫（2026-10-09 用户口径变更，报告 §7.33）。
#
# 用户原话：「90 天这个不要。15 天应该是最新来货（委外加工或者是成品购入）后，15 天这个产品没有销售记录的。就算滞销。」
# ⇒ ① 独立的「统计窗口（默认 90 天）」整体取消（含「期间销量 / 周转天数」两列）；
#    ② 滞销时钟的起点 = max(最近一次来货日, 最后销售日)：来货会重置时钟，卖了之后又 15 天没动照样算滞销。
#
# 本守卫钉六件事：
#   (1) 响应里**不再有** recentDays / saleQtyRecent / turnoverDays（窗口与其两个派生字段真的删了）；
#   (2) 每行 lastInDate == SQL 白名单（PURCHASE_IN / OUTSOURCE_FINISH_IN）的 MAX(create_time)（口径精确到行）；
#   (3) 每行 stagnantDays == 今天 − max(最后销售日, 最近来货日)，且 stagnant 与阈值一致（口径精确到行）；
#   (4) **判别用例**：把某产品的"最近来货"挪到 5 天前、它的全部已审核销售挪到 40 天前 ⇒
#       新口径判**不滞销**（5 < 15），而旧口径（40 >= 15）会判滞销 ⇒ 一眼看出新口径真的生效；
#   (5) UI：滞销块控件区**不再有下拉**（统计窗口没了）、表格列数 10 → 9、新列渲染 API 的 lastInDate、横向不溢出；
#   (6) 两处被临时改动的 create_time **必须还原**（finally 兜底 + 显式断言还原）。
#
# ⚠️ 为什么非要做 (4)：现库里成品几乎都是最近 9 天内进出的 ⇒ 只跑 (2)(3) 时新口径与旧口径可能**同值**，
#    守卫会在"口径其实没改"的情况下照样全绿 ✗ —— 判别用例是唯一能证伪的做法（本仓"守卫必须能证伪"的一贯要求）。
#
# ⚠️ 三条已踩过的坑（2026-10-09 本守卫实跑时一次撞全，写法已固定下来）：
#    · **单元素数组会被 PS 解包成字符串** ⇒ `$x[0]` 变成"首字符"（'5439' ⇒ '5'）⇒ 取 SQL 行一律 `@(SqlRows ...)`；
#    · `SqlRows` 的返回一律当**行数组**用，不要再裸取 `[0]`；
#    · 期望停滞天数**不能在 SQL 里用 GREATEST**：MySQL 的 GREATEST 只要有一个参数是 NULL 就返回 NULL ✗
#      （Java 侧用的是 `isAfter` 比较 ⇒ 应用是对的）⇒ 这里分别取两个日期、在 PS 里比较（与 Java 同构）。
#
# ASCII-only on purpose（PS 5.1 + BOM 陷阱）：文案比对放在浏览器内，或干脆用结构性断言（元素/字段/计数）。
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')

$ErrorActionPreference = 'Continue'
$API = 'http://localhost:8080/api'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$WL = "'PURCHASE_IN','OUTSOURCE_FINISH_IN'"

function SqlRows([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -N -B -e $q 2>$null
  return @(@($o) | Where-Object { $_ -notmatch '^(mysql:|ERROR)' })
}
function SqlOne([string]$q) { $l = @(SqlRows $q); if ($l.Count -lt 1) { return '' }; return (("$($l[0])" -split "`t")[0]).Trim() }
function SqlExec([string]$q) { & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null | Out-Null }
function Step([string]$t) { Write-Host ''; Write-Host ('--- ' + $t) }
function DaysAgo([string]$d) { if ($d -eq '') { return '' }; return [string][int]((Get-Date).Date - [datetime]$d).Days }

# ---- 登录（后端重启会作废旧会话 ⇒ 先清再登）----
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
Write-Host ('  top-level fields: ' + ($topNames -join ','))
Write-Host ('  rows=' + $rows.Count + ' threshold=' + $res.data.threshold)

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
Step '(2)(3) per-row caliber: lastInDate == MAX(whitelist inbound); stagnantDays == today - max(lastSale, lastIn)'
$badIn = 0; $badDays = 0; $badFlag = 0; $checked = 0
foreach ($r in $rows) {
  $pk = [int]$r.productId
  $ls = SqlOne ("SELECT IFNULL(DATE_FORMAT(MAX(o.create_time),'%Y-%m-%d'),'') FROM sale_order o JOIN sale_order_item i ON i.order_id=o.id WHERE o.status=0x41554449544544 AND i.product_id=$pk")
  $li = SqlOne ("SELECT IFNULL(DATE_FORMAT(MAX(create_time),'%Y-%m-%d'),'') FROM warehouse_stock_log WHERE product_id=$pk AND change_quantity>0 AND change_type IN ($WL)")
  $ref = ''
  if ($ls -eq '') { $ref = $li } elseif ($li -eq '') { $ref = $ls } else { $ref = @($ls, $li) | Sort-Object | Select-Object -Last 1 }
  $expDays = DaysAgo $ref

  $actIn = ''
  if ($r.lastInDate -ne $null) { $actIn = [string]$r.lastInDate }
  if ($li -ne $actIn) { $badIn++; Write-Host ("  !! product=$pk lastInDate api=" + $actIn + " sql=" + $li) }

  $actDays = ''
  if ($r.stagnantDays -ne $null) { $actDays = [string]$r.stagnantDays }
  if ($expDays -ne $actDays) { $badDays++; Write-Host ("  !! product=$pk stagnantDays api=" + $actDays + " expected=" + $expDays + " (lastSale=" + $ls + " lastIn=" + $li + ")") }

  $th = [int]$res.data.threshold
  $expFlag = ($expDays -eq '') -or ([int]$expDays -ge $th)
  if ([bool]$r.stagnant -ne $expFlag) { $badFlag++; Write-Host ("  !! product=$pk stagnant api=" + $r.stagnant + " expected=" + $expFlag) }
  $checked++
}
Write-Host ("  checked $checked row(s)")
Ok ($checked -gt 0) 'at least one product row was checked'
Ok ($badIn -eq 0) 'every lastInDate equals the latest WHITELISTED inbound date (purchase / outsourced return)'
Ok ($badDays -eq 0) 'every stagnantDays equals today - max(last sale, latest inbound)'
Ok ($badFlag -eq 0) 'every stagnant flag matches the threshold applied to that value'

# ---------- (4) 判别用例：来货重置时钟 ----------
Step '(4) DISCRIMINATING CASE: a 5-day-old inbound must reset the clock even if every audited sale is 40 days old'
$fav = @(SqlRows "SELECT l.product_id FROM warehouse_stock_log l WHERE l.change_type='PURCHASE_IN' AND l.product_id IN (SELECT product_id FROM warehouse_stock WHERE quantity>0) AND (SELECT COUNT(*) FROM warehouse_stock_log l2 WHERE l2.product_id=l.product_id AND l2.change_type='PURCHASE_IN')>=2 ORDER BY l.create_time DESC LIMIT 1")
$fpid = if ($fav.Count -ge 1) { (("$($fav[0])" -split "`t")[0]).Trim() } else { '' }
Ok ($fpid -ne '') ('picked a product with a repeat purchase inbound: id=' + $fpid)
if ($fpid -eq '') {
  Skip 'no product with >=2 purchase-inbound rows -> the discriminating case cannot run'
} else {
  # ⚠️ 必须挪走该产品**全部**采购入库流水：只挪最新那一条的话，次新的仍是"今天" ⇒ MAX 不变 ⇒ 判别用例失效
  #    （2026-10-09 本守卫实跑时正是这样"假绿"过一版：lastInDate 仍= 今天、stagnantDays=0，看着绿但没证明"来货重置"）。
  $logRows = @(SqlRows "SELECT id, DATE_FORMAT(create_time,'%Y-%m-%d %H:%i:%s') FROM warehouse_stock_log WHERE product_id=$fpid AND change_type='PURCHASE_IN' ORDER BY create_time DESC")
  $logId = ''
  $logOrig = ''
  if ($logRows.Count -ge 1) {
    $p = @("$($logRows[0])" -split "`t")
    $logId = $p[0].Trim(); $logOrig = $p[1].Trim()
  }
  Write-Host ('  inbound logs of the fixture: ' + $logRows.Count + ' (latest original ' + $logOrig + ')')
  # 该产品的全部已审核销售单（要一起挪走，否则"最后销售日"仍是今天 ⇒ 判别不出新口径）
  $saleRows = @(SqlRows "SELECT id, DATE_FORMAT(create_time,'%Y-%m-%d %H:%i:%s') FROM sale_order WHERE status=0x41554449544544 AND id IN (SELECT order_id FROM sale_order_item WHERE product_id=$fpid)")
  $saleIds = @($saleRows | ForEach-Object { (("$_" -split "`t")[0]).Trim() })
  Write-Host ('  fixture: product=' + $fpid + ' log=' + $logId + ' (' + $logOrig + ') ; audited orders=' + $saleIds.Count)
  Ok ($logId -ne '' -and $saleIds.Count -ge 1) 'fixture has both an inbound log and audited sale orders'
  if ($logId -eq '' -or $saleIds.Count -lt 1) {
    Skip 'fixture incomplete -> the discriminating case cannot run'
  } else {
    $idList = ($saleIds -join ',')
    try {
      SqlExec ("UPDATE warehouse_stock_log SET create_time = DATE_SUB(NOW(), INTERVAL 5 DAY) WHERE product_id=$fpid AND change_type='PURCHASE_IN'")
      SqlExec ("UPDATE sale_order SET create_time = DATE_SUB(NOW(), INTERVAL 40 DAY) WHERE id IN ($idList)")
      $one = Api ('/warehouse/stock/stagnant/page?onlyStagnant=false&pageSize=10&productId=' + $fpid)
      $orf = @($one.data.records)
      if ($orf.Count -lt 1) {
        Bad 'the fixture product vanished from the stagnant endpoint while the case was set up'
      } else {
        $f = $orf[0]
        $d = ''; if ($f.stagnantDays -ne $null) { $d = [string]$f.stagnantDays }
        Write-Host ('  after moving: lastInDate=' + $f.lastInDate + ' lastSaleDate=' + $f.lastSaleDate + ' stagnantDays=' + $d + ' stagnant=' + $f.stagnant)
        Ok ($d -eq '5') 'stagnantDays follows the LATEST INBOUND (5), not the last sale (40) -> the arrival reset the clock'
        Ok ([bool]$f.stagnant -eq $false) 'the product is NOT stagnant (5 < 15), while the OLD caliber (40 >= 15) would have flagged it'
      }
    } finally {
      foreach ($lrow in $logRows) {
        $q = @("$lrow" -split "`t")
        SqlExec ("UPDATE warehouse_stock_log SET create_time='" + $q[1].Trim() + "' WHERE id=" + $q[0].Trim())
      }
      foreach ($srow in $saleRows) {
        $q = @("$srow" -split "`t")
        SqlExec ("UPDATE sale_order SET create_time='" + $q[1].Trim() + "' WHERE id=" + $q[0].Trim())
      }
    }
    $back1 = SqlOne ("SELECT DATE_FORMAT(MAX(create_time),'%Y-%m-%d %H:%i:%s') FROM warehouse_stock_log WHERE product_id=$fpid AND change_type='PURCHASE_IN'")
    $badLogRestore = 0
    foreach ($lrow in $logRows) {
      $q = @("$lrow" -split "`t")
      $nowL = SqlOne ("SELECT DATE_FORMAT(create_time,'%Y-%m-%d %H:%i:%s') FROM warehouse_stock_log WHERE id=" + $q[0].Trim())
      if ($nowL -ne $q[1].Trim()) { $badLogRestore++; Write-Host ('  !! inbound log ' + $q[0] + ' still = ' + $nowL) }
    }
    $badRestore = 0
    foreach ($srow in $saleRows) {
      $q = @("$srow" -split "`t")
      $now = SqlOne ("SELECT DATE_FORMAT(create_time,'%Y-%m-%d %H:%i:%s') FROM sale_order WHERE id=" + $q[0].Trim())
      if ($now -ne $q[1].Trim()) { $badRestore++; Write-Host ('  !! sale order ' + $q[0] + ' still = ' + $now) }
    }
    Write-Host ('  restored: latest inbound=' + $back1 + ' ; logs wrong=' + $badLogRestore + ' ; orders wrong=' + $badRestore)
    Ok ($back1 -eq $logOrig -and $badLogRestore -eq 0 -and $badRestore -eq 0) 'every touched timestamp is back to its original value (fixture left as found)'
  }
}

# ---------- (5) UI ----------
Step '(5) UI: no window selector, 9 columns, the new column renders lastInDate, no overflow'
Open '/inventory/product-stock' 3600
$uiJs = "(()=>{const vis=e=>e.getClientRects().length>0;const card=[...document.querySelectorAll('.stagnant-card')].filter(vis)[0];if(!card)return 'NOCARD';const sel=card.querySelectorAll('.st-controls .el-select').length;const num=card.querySelectorAll('.st-controls .el-input-number').length;const tb=card.querySelector('.el-table');const th=tb?tb.querySelectorAll('.el-table__header th').length:0;const body=tb?tb.querySelector('.el-table__body-wrapper'):null;const of=body?(body.scrollWidth-body.clientWidth):-1;return JSON.stringify({sel:sel,num:num,th:th,of:of})})()"
$ui = EvalJs $uiJs
Write-Host ('  panel probe >> ' + $ui)
$U = $ui | ConvertFrom-Json
Ok ($U.sel -eq 0) 'the panel controls no longer contain any dropdown (the statistics-window selector is gone)'
Ok ($U.num -eq 1) 'exactly one number input remains (the stagnant threshold)'
Ok ($U.th -eq 9) ('the stagnant table now has 9 columns (was 10: period-qty + turnover out, latest-inbound in) - got ' + $U.th)
Ok ($U.of -le 0) 'the stagnant table still fits horizontally (no overflow introduced)'

# 新列真的在渲染 API 的 lastInDate：面板默认「仅看滞销」⇒ **不能**拿 onlyStagnant=false 的行来对 ✗
# （2026-10-09 实跑踩过：拿 SKU-000001 去 DOM 里找，它压根不在滞销清单里 ⇒ 假红）。
# 改为：先读 DOM 里**真实存在**的行（SKU 是 ASCII ✓），再按 SKU 去 API 取该行 lastInDate 并核对。
$domSkus = EvalJs "(()=>{const vis=e=>e.getClientRects().length>0;const card=[...document.querySelectorAll('.stagnant-card')].filter(vis)[0];if(!card)return '';const trs=[...card.querySelectorAll('.el-table__body tbody tr')].filter(vis);return trs.map(tr=>String((tr.querySelectorAll('td')[0].innerText||'').trim())).join(',')})()"
# ⚠️ 直接返回**逗号串**再 PS 切分：PS 5.1 的 ConvertFrom-Json 在"数组里再套数组"时会静默变成
#    Object[] 的 Object[]（`-contains` 永远匹配不上）⇒ 会退化成"假 SKIP"✗（2026-10-09 实跑遇到过一次）。
$skuList = @($domSkus -split ',' | Where-Object { $_ -ne '' })
Write-Host ('  rows visible in the panel: ' + ($skuList -join ','))
if ($skuList.Count -lt 1) {
  Skip 'the panel list is empty right now -> the new column content cannot be observed'
} else {
  $all = Api '/warehouse/stock/stagnant/page?onlyStagnant=false&pageSize=200'
  $cand = @($all.data.records | Where-Object { $skuList -contains [string]$_.sku -and $_.lastInDate -ne $null } | Select-Object -First 1)
  if ($cand.Count -lt 1) {
    Skip 'none of the listed products has an inbound date -> the new column shows the placeholder for all of them'
  } else {
    $sku = [string]$cand[0].sku
    $want = [string]$cand[0].lastInDate
    $rowTxt = EvalJs ("(()=>{const vis=e=>e.getClientRects().length>0;const card=[...document.querySelectorAll('.stagnant-card')].filter(vis)[0];const trs=[...card.querySelectorAll('.el-table__body tbody tr')].filter(vis);for(const tr of trs){if(String(tr.innerText||'').indexOf('" + $sku + "')>=0)return String(tr.innerText||'')}return 'NOROW'})()")
    Ok ($rowTxt.Contains($want)) ('the row of ' + $sku + ' shows its inbound date ' + $want + ' (the new column renders API data)')
  }
}
Ok ((Errs) -eq '[]') 'no JS/API errors during the whole flow'

Summary 'stagnant caliber: last inbound resets the clock, statistics window removed'
