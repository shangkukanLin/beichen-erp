# Regression (2026-09-21 user decision): the defect-return LEDGER must show
# "linked to a work order" and "not linked to a work order" in ONE table.
#   Both kinds are the very same rows of outsource_order_delivery (delivery_type=DEFECT_RETURN,
#   is_reverse=1, negative quantity) - only order_id differs, which is exactly what the page's
#   work-order column shows. This probe (API level, runtime fixtures, repeatable) creates one of
#   each and asserts:
#     1) GET /outsource/order-delivery/return-defect/page returns BOTH rows in one list,
#        the linked one carrying orderId + orderCode, the order-less one carrying neither;
#     2) the linked filter works (WITH_ORDER / WITHOUT_ORDER);
#     3) both drafts are deleted at the end (so the file stays repeatable).
# Note: the page orders by id desc and both probes are the newest rows, so a page of 200 is enough.
# PURE ASCII. Chinese only appears inside SQL strings, never in this file.
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

# ---------- fixture: a work order + product that CAN be returned 3 pcs ----------
#   needs: PRODUCING/FINISHED order, audited receipts (cumulative return <= received),
#          A-grade finished stock in a finished warehouse, and an OUTSOURCE warehouse for its factory
$q1 = "SELECT * FROM (SELECT o.id AS oid, o.code AS ocode, op.id AS pid, o.factory_id AS fid, op.product_id AS master, (SELECT COALESCE(SUM(d.quantity),0) FROM outsource_order_delivery d WHERE d.order_id=o.id AND d.status='AUDITED' AND d.product_master_id=op.product_id) AS delivered, (SELECT ws.warehouse_id FROM warehouse_stock ws JOIN warehouse w ON w.id=ws.warehouse_id AND w.warehouse_type='FINISHED' WHERE ws.product_id=op.product_id AND ws.quality_type='A' AND ws.quantity>=5 ORDER BY ws.quantity DESC LIMIT 1) AS wh, (SELECT COUNT(*) FROM warehouse w2 WHERE w2.warehouse_category='OUTSOURCE' AND w2.factory_id=o.factory_id) AS out_wh FROM outsource_order o JOIN outsource_order_product op ON op.order_id=o.id WHERE o.status IN ('PRODUCING','FINISHED')) t WHERE t.delivered>=5 AND t.wh IS NOT NULL AND t.out_wh>0 ORDER BY t.oid LIMIT 1"
$f = (SqlRaw $q1) -split "`n"
$c = @((($f[1]) -split "`t") | ForEach-Object { "$_".Trim() })
$oid = [int]$c[0]; $ocode = "$($c[1])"; $prodRowId = [int]$c[2]; $fid = [int]$c[3]; $master = [int]$c[4]; $whId = [int]$c[6]
Write-Host ("FIXTURE order=$oid code=$ocode productRow=$prodRowId factory=$fid master=$master wh=$whId")
Ok ($oid -gt 0 -and $whId -gt 0 -and $master -gt 0 -and $prodRowId -gt 0) 'fixture found (order with receipts + finished stock + factory outsource wh)'
if (-not ($oid -gt 0 -and $whId -gt 0 -and $master -gt 0 -and $prodRowId -gt 0)) { Write-Host 'RESULT FAIL fixture missing'; exit 1 }

# ---------- login ----------
$lg = Invoke-RestMethod -Uri 'http://localhost:8080/api/auth/login' -Method Post -ContentType 'application/json' -Body '{"username":"lin","password":"123","companyId":1}'
$tk = $lg.data.token
$h = @{ Authorization = $tk }
Ok ([bool]$tk) 'logged in'

$qty = 3
$ledger = 'http://localhost:8080/api/outsource/order-delivery/return-defect/page'

Step 'create the order-less defect return (draft)'
$b1 = @{ factoryId = $fid; warehouseId = $whId; productMasterId = $master; qualityType = 'A'; quantity = $qty; remark = 'ledger probe no-order' } | ConvertTo-Json -Depth 5
$r1 = Invoke-RestMethod -Uri 'http://localhost:8080/api/outsource/order-delivery/return-defect-no-order' -Method Post -Headers $h -ContentType 'application/json' -Body $b1
Ok ($r1.code -eq 200) ('created no-order: ' + $r1.code + ' ' + $r1.msg)
$idNo = [int](SqlOne "SELECT COALESCE(MAX(id),0) FROM outsource_order_delivery")
Ok ($idNo -gt 0) ('no-order draft id=' + $idNo)

Step 'create the linked defect return (draft, from a work order)'
$b2 = @{ productId = $prodRowId; warehouseId = $whId; qualityType = 'A'; quantity = $qty; remark = 'ledger probe with-order' } | ConvertTo-Json -Depth 5
$r2 = Invoke-RestMethod -Uri ("http://localhost:8080/api/outsource/order-delivery/return-defect/$oid") -Method Post -Headers $h -ContentType 'application/json' -Body $b2
Ok ($r2.code -eq 200) ('created linked: ' + $r2.code + ' ' + $r2.msg)
$idWith = [int](SqlOne "SELECT COALESCE(MAX(id),0) FROM outsource_order_delivery")
Ok ($idWith -gt 0 -and $idWith -ne $idNo) ('linked draft id=' + $idWith)

Step 'the ledger returns BOTH rows in one list'
$p = Invoke-RestMethod -Uri ($ledger + '?page=1&size=200') -Headers $h
Ok ($p.code -eq 200) ('ledger: ' + $p.code + ' total=' + $p.data.total)
$rows = @($p.data.records)
$rw = $rows | Where-Object { [int]$_.id -eq $idWith } | Select-Object -First 1
$rn = $rows | Where-Object { [int]$_.id -eq $idNo } | Select-Object -First 1
Ok ($null -ne $rw -and $null -ne $rn) 'one table holds both: linked AND order-less'
if ($rw) {
  Ok ([int]$rw.orderId -eq $oid) ('linked row carries orderId=' + $rw.orderId)
  Ok ("$($rw.orderCode)" -eq $ocode) ('linked row carries orderCode=' + $rw.orderCode)
  Ok ([int]$rw.quantity -eq (0 - $qty)) ('linked row quantity is negative (' + $rw.quantity + ')')
  Ok ("$($rw.status)" -eq 'DRAFT') 'linked row status=DRAFT'
  Ok ("$($rw.warehouseName)" -ne '') ('linked row carries the finished warehouse (' + $rw.warehouseName + ')')
  Ok ("$($rw.productName)" -ne '') ('linked row carries the product name (' + $rw.productName + ')')
  Ok (-not ("$($rw.remark)" -eq '')) ('linked row carries a remark (' + $rw.remark + ')')
  # Regression (2026-09-21 user report: the ledger showed NO factory for on-order rows):
  # an on-order red-reversal used to leave factory_id NULL, and the page reads that column.
  # The backend now writes it AND falls back to the linked work order's factory.
  Ok ([int]$rw.factoryId -eq $fid) ('linked row carries the work order factory id (' + $rw.factoryId + ')')
  Ok ("$($rw.factoryName)" -ne '') ('linked row shows the factory name (' + $rw.factoryName + ')')
} else { Ok $false 'linked row is MISSING from the ledger' }
if ($rn) {
  Ok ($null -eq $rn.orderId) 'order-less row has no orderId'
  Ok ("$($rn.orderCode)" -eq '') 'order-less row has no orderCode (page shows it as unlinked)'
  Ok ([int]$rn.quantity -eq (0 - $qty)) ('order-less row quantity is negative (' + $rn.quantity + ')')
  Ok ([int]$rn.factoryId -eq $fid) ('order-less row carries factoryId=' + $rn.factoryId)
  Ok ([int]$rn.productMasterId -eq $master) ('order-less row carries productMasterId=' + $rn.productMasterId)
} else { Ok $false 'order-less row is MISSING from the ledger' }

Step 'linked filter splits the same table'
$pw = Invoke-RestMethod -Uri ($ledger + '?page=1&size=200&linked=WITH_ORDER') -Headers $h
$idsW = @($pw.data.records | ForEach-Object { [int]$_.id })
Ok ($idsW -contains $idWith) 'WITH_ORDER contains the linked row'
Ok (-not ($idsW -contains $idNo)) 'WITH_ORDER excludes the order-less row'
$pn = Invoke-RestMethod -Uri ($ledger + '?page=1&size=200&linked=WITHOUT_ORDER') -Headers $h
$idsN = @($pn.data.records | ForEach-Object { [int]$_.id })
Ok ($idsN -contains $idNo) 'WITHOUT_ORDER contains the order-less row'
Ok (-not ($idsN -contains $idWith)) 'WITHOUT_ORDER excludes the linked row'
$pd = Invoke-RestMethod -Uri ($ledger + '?page=1&size=200&status=DRAFT') -Headers $h
$idsD = @($pd.data.records | ForEach-Object { [int]$_.id })
Ok (($idsD -contains $idNo) -and ($idsD -contains $idWith)) 'status=DRAFT contains both drafts'

Step 'detail endpoint (record fields + settled impact)'
$dUrl = 'http://localhost:8080/api/outsource/order-delivery/return-defect/'
$dd = Invoke-RestMethod -Uri ($dUrl + $idWith + '/detail') -Headers $h
Ok ($dd.code -eq 200) ('detail(linked): ' + $dd.code)
Ok ([int]$dd.data.orderId -eq $oid) ('detail carries orderId=' + $dd.data.orderId)
Ok ("$($dd.data.orderCode)" -eq $ocode) ('detail carries orderCode=' + $dd.data.orderCode)
Ok ([int]$dd.data.factoryId -eq $fid) ('detail carries the factory id (' + $dd.data.factoryId + ')')
Ok ("$($dd.data.factoryName)" -ne '') ('detail carries the factory name (' + $dd.data.factoryName + ')')
Ok ("$($dd.data.warehouseName)" -ne '') ('detail carries the warehouse (' + $dd.data.warehouseName + ')')
Ok ($dd.data.settled -eq $false) 'draft: not settled'
Ok (@($dd.data.materials).Count -eq 0) 'draft: no material lines yet'

# audit the order-less one, then the detail must report the settled impact, then roll back
Step 'detail after audit (materials + payable), then un-audit'
$ra = Invoke-RestMethod -Uri ("http://localhost:8080/api/outsource/order-delivery/$idNo/audit") -Method Put -Headers $h
Ok ($ra.code -eq 200) ('audit no-order draft: ' + $ra.code + ' ' + $ra.msg)
$dn = Invoke-RestMethod -Uri ($dUrl + $idNo + '/detail') -Headers $h
Ok ($dn.data.settled -eq $true) 'audited: settled=true'
Ok (@($dn.data.materials).Count -ge 1) ('audited: material lines returned (' + @($dn.data.materials).Count + ')')
Ok ("$(@($dn.data.materials)[0].materialName)" -ne '') ('audited: first returned material has a name')
Ok ([decimal]$dn.data.payableAmount -lt 0) ('audited: payable credited (' + $dn.data.payableAmount + ')')
Ok ("$($dn.data.payableStatus)" -eq 'UNSETTLED') ('audited: payable status=' + $dn.data.payableStatus)
$ru = Invoke-RestMethod -Uri ("http://localhost:8080/api/outsource/order-delivery/$idNo/un-audit") -Method Put -Headers $h
Ok ($ru.code -eq 200) 'un-audited (back to draft so the cleanup below works)'
$dz = Invoke-RestMethod -Uri ($dUrl + $idNo + '/detail') -Headers $h
Ok ($dz.data.settled -eq $false) 'after un-audit: settled=false again'

Step 'delete both drafts (keeps this probe repeatable)'
$d1 = Invoke-RestMethod -Uri ("http://localhost:8080/api/outsource/order-delivery/$idNo") -Method Delete -Headers $h
$d2 = Invoke-RestMethod -Uri ("http://localhost:8080/api/outsource/order-delivery/$idWith") -Method Delete -Headers $h
Ok ($d1.code -eq 200 -and $d2.code -eq 200) 'both drafts deleted'
Ok ((SqlOne "SELECT COUNT(*) FROM outsource_order_delivery WHERE id IN ($idNo,$idWith)") -eq '0') 'no residue left behind'

if ($fail -eq 0) { Write-Host 'RESULT PASS defect-return ledger (linked + order-less in ONE table)' } else { Write-Host ('RESULT FAIL count=' + $fail); exit 1 }
