# audit 2026-10-05 batch 4 (滞销/呆滞) - READ ONLY probe.
# Two things are checked against the live API + DB facts:
#   (1) KPI invariants that are computable from SQL (allProductCount / neverSoldCount / stagnant count);
#   (2) F7-279: does "first inbound date" come from a row that was never an inbound?
#       StagnantAnalysisMapper.productFirstInByProduct() takes MIN(create_time) WHERE change_quantity > 0,
#       so a reversal (SALE_OUT_UN_AUDIT) or a quality reclassify (RECLASSIFY_IN) can be mistaken for the
#       first inbound => stockAgeDays is understated => "never sold but recently stocked = new product, not
#       severely stagnant" can suppress a truly severe-stagnant row. ASCII ONLY in every printed string.
$ErrorActionPreference = 'Continue'
$api = 'http://localhost:8080/api'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$fail = 0
function Ok($m) { Write-Output ("PASS " + $m) }
function Bad($m) { Write-Output ("FAIL " + $m); $script:fail++ }
function SqlOne([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -N -B -e $q 2>$null
  $v = @($o) | Where-Object { $_ -notmatch '^(mysql:|ERROR)' } | Select-Object -First 1
  if ($null -eq $v) { return '' }
  return ("$v").Trim()
}
$b = '{"username":"lin","password":"123","companyId":1}'
$la = Invoke-RestMethod -Uri "$api/auth/login" -Method Post -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($b)) -TimeoutSec 20
if ([string]$la.code -ne '200') { Write-Output 'FAIL login'; Write-Output 'RESULT BATCH4-STAGNANT FAIL count 1'; exit 1 }
$tok = [string]$la.data.token

# onlyStagnant=false => the record list carries every product that holds stock (the KPI denominator)
$res = Invoke-RestMethod -Uri "$api/warehouse/stock/stagnant/page?onlyStagnant=false&pageSize=50" -Headers @{ Authorization = $tok } -TimeoutSec 30
$k = $res.data.kpi
$rows = @($res.data.records)
# 2026-10-09 (user): the independent "statistics window" (default 90 days) was REMOVED together with the
# period-qty / turnover-days columns, so the response no longer carries recentDays. Kept as a printed fact
# only (no assertion either way) so the probe keeps documenting what the live payload looks like.
Write-Host ("threshold=" + $res.data.threshold + " records=" + $rows.Count)
Write-Host ("kpi: productCount=" + $k.productCount + " neverSoldCount=" + $k.neverSoldCount + " allProductCount=" + $k.allProductCount + " shareQty=" + $k.shareQty + " avgStagnantDays=" + $k.avgStagnantDays)

$sqlAll = [int](SqlOne 'SELECT COUNT(DISTINCT product_id) FROM warehouse_stock WHERE product_id IS NOT NULL')
# 2026-10-09（用户口径变更）：15 天时钟的起点多了"最近一次来货日"（PURCHASE_IN / OUTSOURCE_FINISH_IN），
# 而本 KPI 现在统计的是"滞销品里从未销售过的" ⇒ 从未销售但**来货未满 15 天**的是新到的货、不算滞销 ⇒
# 必须排除。故下面多了一个 NOT EXISTS（15 = 本次调用使用的默认阈值，API 未传 noSaleDays）。
$sqlNever = [int](SqlOne 'SELECT COUNT(*) FROM (SELECT DISTINCT s.product_id FROM warehouse_stock s WHERE s.product_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sale_order o JOIN sale_order_item i ON i.order_id=o.id WHERE o.status=0x41554449544544 AND i.product_id=s.product_id) AND NOT EXISTS (SELECT 1 FROM warehouse_stock_log l WHERE l.product_id=s.product_id AND l.change_type IN (0x50555243484153455F494E,0x4F5554534F555243455F46494E4953485F494E) AND DATEDIFF(CURDATE(), l.create_time) < 15)) x')
if ([int]$k.allProductCount -eq $sqlAll) { Ok ("KPI denominator matches SQL: products with stock = $sqlAll") } else { Bad ("allProductCount=$($k.allProductCount) but SQL says $sqlAll") }
if ([int]$k.neverSoldCount -eq $sqlNever) { Ok ("KPI never-sold count matches SQL: $sqlNever") } else { Bad ("neverSoldCount=$($k.neverSoldCount) but SQL says $sqlNever") }

if ($rows.Count -gt 0) { Write-Host ("row ids: " + (($rows | ForEach-Object { $_.productId }) -join ', ')) }
# F7-279: the precise acceptance test -- the API's firstInDate must equal the earliest date among the
# WHITELISTED inbound types (and be null when the product has no inbound at all). This single comparison
# catches both failure directions: contamination (value taken from a reversal/reclassify) and
# over-correction (whitelist too narrow -> everything null).
# NOTE: never name a variable $pid here -- it is a read-only automatic variable (current process id);
# assigning to it fails SILENTLY and the SQL below would then query a non-existent product id.
$WL = "'INIT','PURCHASE_IN','PURCHASE_EXCHANGE_IN','SALE_RETURN_IN','EXCHANGE_IN','OUTSOURCE_FINISH_IN','OTHER_IN','STOCK_TAKE_IN'"
$mismatch = 0
foreach ($r in @($rows)) {
  $pkey = [int]$r.productId
  $positives = SqlOne ("SELECT GROUP_CONCAT(CONCAT(DATE_FORMAT(create_time,'%m-%d'),':',change_type) ORDER BY create_time, id SEPARATOR ' | ') FROM warehouse_stock_log WHERE product_id=$pkey AND change_quantity>0")
  $expected = SqlOne ("SELECT IFNULL(DATE_FORMAT(MIN(create_time),'%Y-%m-%d'),'') FROM warehouse_stock_log WHERE product_id=$pkey AND change_quantity>0 AND change_type IN ($WL)")
  $actual = ''
  if ($r.firstInDate -ne $null) { $actual = [string]$r.firstInDate }
  Write-Host ("  product=" + $pkey + " positives=[" + $positives + "]")
  Write-Host ("     expectedFirstInbound=" + $(if ($expected -eq '') { '<none>' } else { $expected }) + " apiFirstInDate=" + $(if ($actual -eq '') { '<none>' } else { $actual }) + " stockAgeDays=" + $r.stockAgeDays + " lastSaleDate=" + $r.lastSaleDate + " severe=" + $r.severe)
  if ($expected -ne $actual) { $mismatch++ }
}
if ($mismatch -eq 0) { Ok 'every firstInDate equals the earliest WHITELISTED inbound date (no reversal/reclassify contamination, no over-correction)' }
else { Bad ("F7-279: $mismatch row(s) whose firstInDate != earliest whitelisted inbound date (see the pairs printed above)") }

# F7-278: a product-day carrying BOTH an audited sale and an audited return is the only way to observe the
# "same day: SALE rows before RETURN rows" ordering live. If such a pair exists, drive the records endpoint
# and assert the order; if not, say so instead of pretending the fix was covered.
$pair = SqlOne 'SELECT s.pid, s.d FROM (SELECT i.product_id AS pid, DATE(o.create_time) AS d FROM sale_order o JOIN sale_order_item i ON i.order_id=o.id WHERE o.status=0x41554449544544 GROUP BY i.product_id, DATE(o.create_time)) s JOIN (SELECT i.product_id AS pid, DATE(r.create_time) AS d FROM sale_return r JOIN sale_return_item i ON i.return_id=r.id WHERE r.status=0x41554449544544 GROUP BY i.product_id, DATE(r.create_time)) t ON t.pid=s.pid AND t.d=s.d LIMIT 1'
if ($pair -ne '') {
  # mysql -B prints tab-separated columns; NEVER embed double quotes in these SQL strings (the argument
  # parser eats them and the query fails silently -> 2>$null hides it and you get '' = a false "no fixture").
  $parts = $pair -split "`t"
  $pk = [int]$parts[0]; $day = ([string]$parts[1]).Trim()
  $rec = Invoke-RestMethod -Uri ("$api/product/analysis/records?preset=custom&start=$day&end=$day&productId=$pk") -Headers @{ Authorization = $tok } -TimeoutSec 30
  $types = @($rec.data.records | ForEach-Object { [string]$_.type })
  Write-Host ("  F7-278 fixture: product=$pk day=$day record types in order = " + ($types -join ' -> '))
  $iSale = [array]::IndexOf($types, 'SALE'); $iRet = [array]::IndexOf($types, 'RETURN')
  if (($iSale -ge 0) -and ($iRet -ge 0) -and ($iSale -lt $iRet)) { Ok ("F7-278 fixed: on $day the SALE row precedes the RETURN row (index $iSale < $iRet)") }
  else { Bad ("F7-278: same-day order is wrong or records missing (SALE index=$iSale, RETURN index=$iRet, types=" + ($types -join ',') + ")") }
} else { Write-Output 'NOTE F7-278: this DB has no product-day with both a sale and a return -> the fix stays static-only' }

if ($fail -eq 0) { Write-Output 'RESULT BATCH4-STAGNANT PASS' } else { Write-Output ("RESULT BATCH4-STAGNANT FAIL count " + $fail) }
exit $fail
