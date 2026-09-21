# One-off probe (2026-09-21): NO-ORDER 加工退货 must behave EXACTLY like the on-order red-reversal.
#   create (draft, order_id NULL, negative qty) -> audit (finished goods -, BOM material back to the
#   factory outsource warehouse +, payable credited by the FIFO value of the returned material)
#   -> un-audit (full rollback) -> delete (so the file is repeatable).
# Plus: the independent return order must now REJECT new DEFECT documents (that path was unified).
# PURE ASCII. Chinese only appears in DB comments, never in this file.
$ErrorActionPreference = 'Continue'
$fail = 0
function Ok([bool]$c, [string]$m) { if ($c) { Write-Host ('PASS ' + $m) } else { Write-Host ('FAIL ' + $m); $script:fail++ } }
function Step($n) { Write-Host ('--- ' + $n) }

$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
function SqlRaw([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null
  return ((@($o) | ForEach-Object { "$_" }) -join "`n").Trim()
}
function SqlOne([string]$q) {
  $v = SqlRaw $q
  if (-not $v) { return '' }
  $ls = $v -split "`n"
  if ($ls.Count -lt 2) { return '' }
  return (($ls[1] -split "`t")[0]).Trim()
}
function D([string]$s) { if (-not $s) { return [decimal]0 }; return [decimal]$s }

# ---------- fixture: product master with A stock in a finished warehouse + a BOM snapshot + factory ----------
$row = SqlRaw "SELECT p.product_master_id, p.id, s.id, s.master_id, s.quantity FROM (SELECT ws.product_id AS id, ws.product_id AS product_master_id, ws.quantity FROM warehouse_stock ws JOIN warehouse w ON w.id = ws.warehouse_id AND w.warehouse_type = 'FINISHED' WHERE ws.quality_type = 'A' AND ws.quantity >= 20 LIMIT 1) x JOIN (SELECT b.product_master_id AS master_id, b.id, b.item_count FROM bom_snapshot b) s ON s.master_id = x.product_master_id JOIN (SELECT 1 AS id) p ON 1=1 LIMIT 1"
Write-Host ('FIXTURE-RAW: ' + $row)

$sqlPick = "SELECT ws.warehouse_id, ws.product_id, ws.quantity, b.id AS snap_id, b.item_count, (SELECT id FROM warehouse w2 WHERE w2.warehouse_category='OUTSOURCE' AND w2.factory_id = (SELECT id FROM supplier WHERE id IS NOT NULL ORDER BY id LIMIT 1) ORDER BY id LIMIT 1) AS fwh FROM warehouse_stock ws JOIN warehouse w ON w.id = ws.warehouse_id AND w.warehouse_type='FINISHED' JOIN bom_snapshot b ON b.product_master_id = ws.product_id WHERE ws.quality_type='A' AND ws.quantity >= 20 ORDER BY ws.warehouse_id, ws.product_id LIMIT 1"
$f = (SqlRaw $sqlPick) -split "`n"
$cells = @((($f[1]) -split "`t") | ForEach-Object { "$_".Trim() })
$whId = [int]$cells[0]
$masterId = [int]$cells[1]
$snapId = [int]$cells[3]
$snapQty = [int]$cells[2]
Write-Host ("FIXTURE: finished wh=$whId master=$masterId A-stock=$snapQty snapshot=$snapId")

# factory: the one that owns an OUTSOURCE warehouse AND has the product's snapshot -> use ANY factory with an outsource wh
$fx = (SqlRaw "SELECT w.factory_id, w.id, s.name FROM warehouse w JOIN supplier s ON s.id = w.factory_id WHERE w.warehouse_category='OUTSOURCE' ORDER BY w.id LIMIT 1") -split "`n"
$fc = @((($fx[1]) -split "`t") | ForEach-Object { "$_".Trim() })
$factoryId = [int]$fc[0]
$factoryWh = [int]$fc[1]
Write-Host ("FIXTURE: factory=$factoryId outsource-wh=$factoryWh")

function StockQty([int]$wh, [string]$col, [int]$id, [string]$q) {
  return (SqlOne "SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE warehouse_id=$wh AND $col=$id AND quality_type='$q'")
}
function PaySum([int]$sid) { return (SqlOne "SELECT COALESCE(SUM(amount),0) FROM finance_payable WHERE supplier_id=$sid AND status='UNSETTLED'") }

# ---------- login ----------
$lg = Invoke-RestMethod -Uri 'http://localhost:8080/api/auth/login' -Method Post -ContentType 'application/json' -Body '{"username":"lin","password":"123","companyId":1}'
Write-Host ('LOGIN: code=' + $lg.code + ' keys=' + (($lg.data | Get-Member -MemberType NoteProperty | ForEach-Object { $_.Name }) -join ','))
$tk = $lg.data.token
# sa-token: token-name = Authorization, NO "Bearer " prefix (no token-prefix configured)
$h = @{ Authorization = $tk }
Ok ([bool]$tk) 'logged in'

Step 'create no-order return (draft)'
$qty = 5
$body = @{ factoryId = $factoryId; warehouseId = $whId; productMasterId = $masterId; qualityType = 'A'; quantity = $qty; remark = 'probe no-order return' } | ConvertTo-Json -Depth 5
$rc = Invoke-RestMethod -Uri 'http://localhost:8080/api/outsource/order-delivery/return-defect-no-order' -Method Post -Headers $h -ContentType 'application/json' -Body $body
Ok ($rc.code -eq 200) ('created: ' + $rc.code + ' ' + $rc.msg)
$id = [int](SqlOne "SELECT COALESCE(MAX(id),0) FROM outsource_order_delivery")
$r = @(((SqlRaw "SELECT order_id, product_id, product_master_id, quality_type, quantity, status, source_type, factory_id, is_reverse FROM outsource_order_delivery WHERE id=$id") -split "`n")[1] -split "`t" | ForEach-Object { "$_".Trim() })
Write-Host ('ROW: ' + ($r -join '|'))
Ok ($r[0] -eq 'NULL') 'order_id is NULL (the whole point)'
Ok ($r[1] -eq 'NULL') 'product_id is NULL (so the finance analysis cannot join the wrong price)'
Ok ([int]$r[2] -eq $masterId) ('product_master_id=' + $masterId)
Ok ([string]$r[3] -eq 'A') 'quality_type=A'
Ok ([int]$r[4] -eq (0 - $qty)) ('quantity is negative (' + $r[4] + ')')
Ok ([string]$r[5] -eq 'DRAFT') 'status=DRAFT'
Ok ([string]$r[6] -eq 'RETURN_DEFECT') 'source_type=RETURN_DEFECT'
Ok ([int]$r[7] -eq $factoryId) ('factory_id=' + $factoryId)
Ok ([int]$r[8] -eq 1) 'is_reverse=1'

Step 'audit -> stock / materials / payable'
$bOut = D (StockQty $whId 'product_id' $masterId 'A')
$bPay = D (PaySum $factoryId)
$mats = @()
foreach ($line in ((SqlRaw "SELECT outsource_material_id, quantity_per_set FROM bom_snapshot_item WHERE snapshot_id=$snapId ORDER BY id") -split "`n" | Select-Object -Skip 1)) {
  $c = @(("$line").Trim() -split "`t")
  if ($c.Count -ge 2 -and $c[0] -ne '') { $mats += , @([int]$c[0], [double]$c[1]) }
}
$bMats = @()
foreach ($m in $mats) { $bMats += [double](StockQty $factoryWh 'material_id' $m[0] 'GOOD') }
Write-Host ('BASE: out=' + $bOut + ' payable=' + $bPay + ' materials=' + (($bMats | ForEach-Object { "$_" }) -join ','))

$ra = Invoke-RestMethod -Uri ("http://localhost:8080/api/outsource/order-delivery/$id/audit") -Method Put -Headers $h
Ok ($ra.code -eq 200) ('audit: ' + $ra.code + ' ' + $ra.msg)
Ok ((SqlOne "SELECT status FROM outsource_order_delivery WHERE id=$id") -eq 'AUDITED') 'status=AUDITED'
$aOut = D (StockQty $whId 'product_id' $masterId 'A')
Ok ($aOut -eq ($bOut - $qty)) ('finished goods -' + $qty + ' -> ' + $aOut)
for ($i = 0; $i -lt $mats.Count; $i++) {
  $gain = $qty * [double]$mats[$i][1]
  $got = [double](StockQty $factoryWh 'material_id' $mats[$i][0] 'GOOD')
  Ok ([math]::Abs($got - ([double]$bMats[$i] + $gain)) -lt 0.001) ('factory material ' + $mats[$i][0] + ' +' + $gain + ' -> ' + $got)
}
$aPay = D (PaySum $factoryId)
Ok ($aPay -lt $bPay) ('payable credited back: ' + $bPay + ' -> ' + $aPay)
$pay = (SqlRaw "SELECT source_bill_type, source_id, amount, status FROM finance_payable WHERE source_id=$id AND source_bill_type='OUTSOURCE_DELIVERY' ORDER BY id DESC LIMIT 1") -split "`n"
Write-Host ('PAYABLE: ' + $pay[1])
Ok (([string]($pay[1]) -split "`t")[2] -ne '') 'a payable row is keyed to this record id'

Step 'un-audit -> full rollback'
$ru = Invoke-RestMethod -Uri ("http://localhost:8080/api/outsource/order-delivery/$id/un-audit") -Method Put -Headers $h
Ok ($ru.code -eq 200) ('un-audit: ' + $ru.code + ' ' + $ru.msg)
Ok ((D (StockQty $whId 'product_id' $masterId 'A')) -eq $bOut) 'finished goods rolled back'
for ($i = 0; $i -lt $mats.Count; $i++) {
  Ok ([math]::Abs(([double](StockQty $factoryWh 'material_id' $mats[$i][0] 'GOOD')) - [double]$bMats[$i]) -lt 0.001) ('factory material ' + $mats[$i][0] + ' rolled back')
}
Ok ((D (PaySum $factoryId)) -eq $bPay) 'payable rolled back'
Ok ((SqlOne "SELECT status FROM outsource_order_delivery WHERE id=$id") -eq 'DRAFT') 'back to DRAFT'

Step 'delete draft (keeps this probe repeatable)'
$rd = Invoke-RestMethod -Uri ("http://localhost:8080/api/outsource/order-delivery/$id") -Method Delete -Headers $h
Ok ($rd.code -eq 200) ('delete: ' + $rd.code + ' ' + $rd.msg)
Ok ((SqlOne "SELECT COUNT(*) FROM outsource_order_delivery WHERE id=$id") -eq '0') 'draft removed'

Step 'the independent return order must no longer accept a DEFECT document'
$bad = @{ returnType = 'DEFECT'; factoryId = $factoryId; warehouseId = $whId; returnDate = '2026-09-21'; products = @(); items = @() } | ConvertTo-Json -Depth 5
$rb = Invoke-RestMethod -Uri 'http://localhost:8080/api/outsource/return-order' -Method Post -Headers $h -ContentType 'application/json' -Body $bad
Write-Host ('DEFECT-DOC: code=' + $rb.code + ' msg=' + $rb.msg)
Ok ($rb.code -ne 200) 'creating a DEFECT return document is rejected'
Ok (($rb.msg -ne $null) -and ("$($rb.msg)" -match '[\u4e00-\u9fa5]')) 'the rejection explains where to go instead'

if ($fail -eq 0) { Write-Host 'RESULT PASS no-order return' } else { Write-Host ('RESULT FAIL count=' + $fail); exit 1 }
