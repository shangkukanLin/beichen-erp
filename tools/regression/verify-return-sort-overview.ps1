# 退货整理页优化 (2026-09-19): cross-warehouse "pending sort" overview + batch draft creation.
# Pure API + SQL (no browser). This file must stay pure ASCII.
#
# UI: /inventory/return-sort is now tabbed -- TAB1 待整理 (all finished warehouses, tri-state,
#     multi-select -> batch drafts) + TAB2 整理单 (the old list). Endpoints live under the page's
#     own prefix /api/inventory/return-sort, so they inherit rule(...) from ApiPermGuard (no whitelist).
#
# Asserts:
#   1) overview shape (asOf / stayAlertDays / warehouses / summary) + only INVENTORY+FINISHED warehouses
#   2) default hides CLEARED (clearedCount=0); includeCleared=1 returns them, counts match the DB
#   3) SINGLE SOURCE OF TRUTH: defect-stock rows == the SORTABLE rows of the overview (same pendingId
#      -> same qty). Guards against "overview says 80 but the form can only sort 50".
#   4) tri-state self-consistency + the safety invariant sum(alloc) <= PENDING stock per (warehouse,product)
#   5) batch-draft groups by (warehouse, customer), pre-fills the split, and is rejected with no draft
#      when nothing is sortable; probe drafts are deleted again (data hygiene)
#   6) permission: callers without stock:return-sort get 403 (derived from the login perms payload)
$ErrorActionPreference = 'Continue'
$api = 'http://localhost:8080/api'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$fail = 0
function Ok($m) { Write-Output ("PASS " + $m) }
function Bad($m) { Write-Output ("FAIL " + $m); $script:fail++ }
function SqlRaw([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -N -B -e $q 2>$null
  return (@($o) -join '').Trim()
}
function SqlOne([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -N -B -e $q 2>$null
  $l = @($o); if ($l.Count -lt 1) { return '' }; return ("$($l[0])").Trim()
}
function LoginFull([string]$u, [string]$p, [int]$cid = 1) {
  try {
    $b = '{"username":"' + $u + '","password":"' + $p + '","companyId":' + $cid + '}'
    return Invoke-RestMethod -Uri "$api/auth/login" -Method Post -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($b))
  } catch { return $null }
}
function Req([string]$method, [string]$url, $body, [string]$tok) {
  try {
    $h = @{}; if ($tok) { $h['Authorization'] = $tok }
    if ($method -eq 'GET') { return Invoke-RestMethod -Uri $url -Method Get -Headers $h }
    $json = if ($null -eq $body) { '{}' } else { ConvertTo-Json -InputObject $body -Depth 8 }
    # PS 5.1 encodes bodies as ISO-8859-1 by default (Chinese would become '?') -> pass UTF-8 bytes.
    # NOTE: -Method must stay $method, NOT a literal: a literal "Post" here turned the draft DELETE
    # into a POST and produced a bogus 405 ("请求方法不支持: POST") in an earlier revision.
    return Invoke-RestMethod -Uri $url -Method $method -Headers $h -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($json))
  } catch {
    $code = -1
    try { if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode } } catch { }
    return [pscustomobject]@{ code = $code; msg = "HTTPEX $($_.Exception.Message)"; data = $null }
  }
}
function CodeOf($r) { if ($null -eq $r) { return 'null' } else { return "$($r.code)" } }
function NumOf($v) { if ($null -eq $v) { return 0.0 }; return [double]"$v" }

$rsBase = "$api/inventory/return-sort"
# only INVENTORY + FINISHED warehouses are in scope (same as the source-warehouse dropdown)
$whScope = "p.warehouse_id IN (SELECT id FROM warehouse WHERE warehouse_category='INVENTORY' AND warehouse_type='FINISHED')"
$today = (Get-Date).ToString('yyyy-MM-dd')

Write-Output '--- 0) login (admin + restricted)'
$la = LoginFull 'lin' '123'
if ($null -eq $la -or [string]$la.code -ne '200') { Bad 'cannot login as admin (lin)'; Write-Output 'RESULT RETURN-SORT-OVERVIEW FAIL count 1'; exit 1 }
$tokA = [string]$la.data.token
$lu = LoginFull 'perm_test' '123'
if ($null -eq $lu -or [string]$lu.code -ne '200') { Bad 'cannot login as perm_test'; Write-Output 'RESULT RETURN-SORT-OVERVIEW FAIL count 1'; exit 1 }
$tokU = [string]$lu.data.token
$permsU = @($lu.data.userInfo.perms)
Write-Output ("  perm_test perms = " + ($permsU -join ','))

Write-Output '--- 1) guard: the new endpoints sit under the page prefix (inherit stock:return-sort)'
$guardJava = Get-Content 'c:\Users\75629\CodeBuddy\20260710123705\beichen-erp\beichen-erp-server\src\main\java\com\beichen\erp\config\ApiPermGuard.java' -Raw
if ($guardJava -match 'rule\("/api/inventory/return-sort",\s*"stock:return-sort"\)') {
  Ok 'ApiPermGuard keeps rule("/api/inventory/return-sort", "stock:return-sort") -> new sub-paths inherit it'
} else { Bad 'ApiPermGuard rule for /api/inventory/return-sort not found (new endpoints would be unguarded)' }

Write-Output '--- 2) overview shape + warehouse scope'
$ov = Req 'GET' "$rsBase/pending-overview" $null $tokA
if ((CodeOf $ov) -eq '200' -and $null -ne $ov.data.summary -and $null -ne $ov.data.warehouses -and $null -ne $ov.data.stayAlertDays) {
  Ok ("admin GET pending-overview -> 200 (asOf=" + $ov.data.asOf + ", stayAlertDays=" + $ov.data.stayAlertDays + ")")
} else { Bad ("pending-overview shape wrong: " + (CodeOf $ov) + ' ' + "$($ov.msg)") }
# the warehouse-scope check must use the FULL view: CLEARED batches are hidden by default on purpose,
# so a warehouse may legitimately be absent from the default payload.
$ovAll = Req 'GET' "$rsBase/pending-overview?includeCleared=true" $null $tokA
$ovWhIds = if ($ovAll.data.warehouses) { (@($ovAll.data.warehouses) | ForEach-Object { $_.warehouseId }) -join ',' } else { '0' }
$badWh = SqlOne ("SELECT COUNT(*) FROM warehouse w WHERE w.warehouse_category='INVENTORY' AND w.warehouse_type='FINISHED' AND w.id NOT IN ($ovWhIds) AND EXISTS (SELECT 1 FROM after_sale_pending p WHERE p.warehouse_id=w.id)")
if ($badWh -eq '0') { Ok 'every finished warehouse that has a pending batch appears in the overview (includeCleared view)' } else { Bad ("$badWh finished warehouse(s) with pending batches are missing from the overview") }
$strayWh = [int](SqlOne ("SELECT COUNT(*) FROM warehouse w WHERE w.id IN ($ovWhIds) AND NOT (w.warehouse_category='INVENTORY' AND w.warehouse_type='FINISHED')"))
if ($strayWh -eq 0) { Ok 'the overview only covers INVENTORY + FINISHED warehouses' } else { Bad "$strayWh non-finished warehouse(s) leaked into the overview" }

Write-Output '--- 3) default hides CLEARED; includeCleared=1 matches the DB'
$expAll = [int](SqlOne "SELECT COUNT(*) FROM after_sale_pending p WHERE $whScope")
$expCleared = [int](SqlOne "SELECT COUNT(*) FROM after_sale_pending p WHERE $whScope AND p.sorted_quantity >= p.quantity")
$expOpen = $expAll - $expCleared
Write-Output ("  DB: pending batches in scope=$expAll (cleared=$expCleared / open=$expOpen)")
if ([int](NumOf $ov.data.summary.clearedCount) -eq 0) { Ok 'default overview returns no CLEARED row' } else { Bad ("default overview returned clearedCount=" + $ov.data.summary.clearedCount) }
if ([int](NumOf $ov.data.summary.batchCount) -eq $expOpen) { Ok ("default batchCount=$expOpen matches the DB open batches") } else { Bad ("default batchCount=" + $ov.data.summary.batchCount + " expected $expOpen") }
if ((CodeOf $ovAll) -eq '200' -and [int](NumOf $ovAll.data.summary.batchCount) -eq $expAll -and [int](NumOf $ovAll.data.summary.clearedCount) -eq $expCleared) {
  Ok ("includeCleared=true -> batchCount=$expAll clearedCount=$expCleared (matches DB)")
} else { Bad ("includeCleared=true mismatch: batchCount=" + $ovAll.data.summary.batchCount + " clearedCount=" + $ovAll.data.summary.clearedCount) }

Write-Output '--- 4) single source of truth: defect-stock == SORTABLE rows of the overview'
$whId = SqlOne "SELECT id FROM warehouse WHERE warehouse_category='INVENTORY' AND warehouse_type='FINISHED' ORDER BY id LIMIT 1"
if ($whId -eq '') {
  Bad 'no finished-goods warehouse in DB (cannot probe)'
} else {
  $ds = Req 'GET' "$rsBase/defect-stock?warehouseId=$whId" $null $tokA
  if ((CodeOf $ds) -ne '200') { Bad ("defect-stock -> " + (CodeOf $ds)) } else {
    $dsRows = @($ds.data)
    $grp = @($ovAll.data.warehouses | Where-Object { "$($_.warehouseId)" -eq "$whId" })
    $ovRows = @()
    if ($grp.Count -gt 0) { $ovRows = @($grp[0].rows | Where-Object { "$($_.status)" -eq 'SORTABLE' }) }
    if ($dsRows.Count -eq $ovRows.Count) { Ok ("defect-stock rows ($($dsRows.Count)) == SORTABLE rows of the overview") } else { Bad ("defect-stock rows=$($dsRows.Count) but SORTABLE rows=$($ovRows.Count)") }
    $drift = 0
    foreach ($r in $dsRows) {
      $m = $ovRows | Where-Object { "$($_.pendingId)" -eq "$($r.pendingId)" }
      if (-not $m) { $drift++; continue }
      if ((NumOf $m.quantity) -ne (NumOf $r.quantity)) { $drift++ }
      if ((NumOf $m.totalQuantity) -ne (NumOf $r.totalQuantity)) { $drift++ }
    }
    if ($drift -eq 0) { Ok 'every defect-stock row matches the overview row (qty + total)' } else { Bad "$drift defect-stock row(s) drifted from the overview" }
  }
}

Write-Output '--- 5) tri-state self-consistency + FIFO safety invariant (never allocates more than the PENDING stock)'
$allRows = @($ovAll.data.warehouses | ForEach-Object { $_.rows })
$badSt = 0
foreach ($r in $allRows) {
  $st = "$($r.status)"; $alloc = NumOf $r.quantity; $rem = NumOf $r.remainQuantity
  if ($st -eq 'SORTABLE' -and -not ($alloc -gt 0 -and $rem -gt 0)) { $badSt++ }
  if ($st -eq 'SHORTAGE' -and -not ($alloc -eq 0 -and $rem -gt 0)) { $badSt++ }
  if ($st -eq 'CLEARED' -and -not ($rem -eq 0 -and $alloc -eq 0)) { $badSt++ }
  if ("$($r.partial)" -eq 'True' -and -not ($alloc -gt 0 -and $alloc -lt $rem)) { $badSt++ }
}
if ($badSt -eq 0) { Ok ("tri-state consistent across $($allRows.Count) row(s) (SORTABLE/SHORTAGE/CLEARED + partial)") } else { Bad "$badSt row(s) violate the tri-state definition" }
$over = 0
foreach ($g in @($ovAll.data.warehouses)) {
  $byProd = @{}
  foreach ($r in @($g.rows)) {
    $k = "$($r.productId)"
    if (-not $byProd.ContainsKey($k)) { $byProd[$k] = 0.0 }
    $byProd[$k] = $byProd[$k] + (NumOf $r.quantity)
  }
  foreach ($k in $byProd.Keys) {
    $stock = NumOf (SqlOne "SELECT IFNULL(SUM(quantity),0) FROM warehouse_stock WHERE warehouse_id=$($g.warehouseId) AND product_id=$k AND quality_type='PENDING' AND quantity>0")
    if ($byProd[$k] -gt $stock) { $over++; Write-Output ("  (over-allocation) wh=$($g.warehouseId) product=$k alloc=$($byProd[$k]) stock=$stock") }
  }
}
if ($over -eq 0) { Ok 'sum(allocatable) per (warehouse, product) never exceeds the PENDING stock (FIFO honours the physical pool)' } else { Bad "$over (warehouse, product) pair(s) allocate more than the physical stock" }

Write-Output '--- 6) batch-draft: grouping / pre-fill / hygiene'
$sortable = @($allRows | Where-Object { "$($_.status)" -eq 'SORTABLE' })
$rsBefore = [int](SqlOne 'SELECT COUNT(*) FROM return_sort')
if ($sortable.Count -eq 0) {
  Write-Output '  INFO nothing is sortable right now (seed data already sorted) -> negative path only'
  $any = @($allRows | Select-Object -First 1)
  $body = @{ pendingIds = @([long]$any[0].pendingId); defaultQuality = 'A'; sortDate = $today
             targetWarehouseA = [long]$whId; targetWarehouseB = [long]$whId; targetWarehouseC = [long]$whId; targetWarehouseDefect = [long]$whId }
  $r = Req 'POST' "$rsBase/batch-draft" $body $tokA
  if ((CodeOf $r) -ne '200') { Ok ("batch-draft rejected when nothing is sortable (" + (CodeOf $r) + ': ' + "$($r.msg)" + ')') } else { Bad 'batch-draft accepted a non-sortable batch' }
  $rsAfter = [int](SqlOne 'SELECT COUNT(*) FROM return_sort')
  if ($rsAfter -eq $rsBefore) { Ok "no return_sort row leaked by the rejected call ($rsBefore -> $rsAfter)" } else { Bad "draft rows leaked: $rsBefore -> $rsAfter" }
} else {
  $pick = @($sortable | Select-Object -First 3)
  $ids = @($pick | ForEach-Object { [long]$_.pendingId })
  $expQty = 0.0; foreach ($p in $pick) { $expQty = $expQty + (NumOf $p.quantity) }
  $body = @{ pendingIds = $ids; defaultQuality = 'A'; sortDate = $today
             targetWarehouseA = [long]$whId; targetWarehouseB = [long]$whId; targetWarehouseC = [long]$whId; targetWarehouseDefect = [long]$whId }
  $r = Req 'POST' "$rsBase/batch-draft" $body $tokA
  if ((CodeOf $r) -ne '200') {
    Bad ('batch-draft -> ' + (CodeOf $r) + ' ' + "$($r.msg)")
  } else {
    $created = @($r.data.created)
    if ($created.Count -gt 0) { Ok ("batch-draft created $($created.Count) draft(s) from $($ids.Count) selected batch(es)") } else { Bad 'batch-draft returned 200 but created nothing' }
    $codes = @($created | ForEach-Object { "'" + $_.code + "'" })
    $idsSql = @($created | ForEach-Object { $_.id }) -join ','
    $draftDb = [int](SqlOne ("SELECT COUNT(*) FROM return_sort WHERE id IN ($idsSql) AND status='DRAFT'"))
    if ($draftDb -eq $created.Count) { Ok "all $draftDb created bill(s) exist as DRAFT" } else { Bad ("created as DRAFT = $draftDb of " + $created.Count) }
    # one bill never mixes customers (loss receivable needs a single payer)
    $mix = [int](SqlOne ("SELECT COUNT(*) FROM (SELECT rs.id, COUNT(DISTINCT p.customer_id) c FROM return_sort rs JOIN return_sort_item i ON i.sort_id=rs.id LEFT JOIN after_sale_pending p ON p.id=i.pending_id WHERE rs.id IN ($idsSql) GROUP BY rs.id HAVING c>1) t"))
    if ($mix -eq 0) { Ok 'no draft mixes two customers (grouped by warehouse + customer)' } else { Bad "$mix draft(s) mix customers" }
    # default split pre-filled on the chosen quality
    $itemCnt = [int](SqlOne ("SELECT COUNT(*) FROM return_sort_item WHERE sort_id IN ($idsSql)"))
    $splitOk = [int](SqlOne ("SELECT COUNT(*) FROM return_sort_item WHERE sort_id IN ($idsSql) AND qty_a=total_quantity AND qty_b=0 AND qty_c=0 AND qty_defect=0"))
    if ($itemCnt -gt 0 -and $itemCnt -eq $splitOk) { Ok "all $itemCnt item(s) pre-filled qtyA=total (defaultQuality=A)" } else { Bad ("pre-filled split wrong: $splitOk of $itemCnt") }
    $qtyDb = NumOf (SqlOne ("SELECT IFNULL(SUM(total_quantity),0) FROM return_sort_item WHERE sort_id IN ($idsSql)"))
    if ($qtyDb -eq $expQty) { Ok ("draft total quantity $qtyDb == allocatable sum of the selection") } else { Bad ("draft qty=$qtyDb expected $expQty (overview vs batch drift)") }
    # data hygiene: the probe drafts are deleted again (draft-only delete is allowed)
    $del = 0
    foreach ($c in @($created)) {
      $d = Req 'DELETE' "$rsBase/$($c.id)" $null $tokA
      if ((CodeOf $d) -eq '200') { $del++ }
    }
    $rsAfter = [int](SqlOne 'SELECT COUNT(*) FROM return_sort')
    if ($del -eq $created.Count -and $rsAfter -eq $rsBefore) { Ok "probe drafts cleaned up ($del deleted, return_sort back to $rsAfter)" } else { Bad ("cleanup incomplete: deleted=$del of " + $created.Count + ", rows $rsBefore -> $rsAfter") }
  }
}

Write-Output '--- 6b) positive path with a self-cleaning fixture (borrow 1 A unit -> PENDING + 1 open batch)'
# The seed data is fully sorted, so the positive branch above cannot run. Build a tiny REVERSIBLE fixture,
# exercise batch-draft against it, then restore stock + batch rows (finally block always runs).
$srcId = SqlOne 'SELECT id FROM after_sale_pending ORDER BY id DESC LIMIT 1'
if ($srcId -eq '') {
  Write-Output '  INFO no after_sale_pending row to clone -> positive fixture skipped'
} else {
  $fxWh = SqlOne "SELECT warehouse_id FROM after_sale_pending WHERE id=$srcId"
  $fxProd = SqlOne "SELECT product_id FROM after_sale_pending WHERE id=$srcId"
  $aBefore = NumOf (SqlOne "SELECT IFNULL(SUM(quantity),0) FROM warehouse_stock WHERE warehouse_id=$fxWh AND product_id=$fxProd AND quality_type='A'")
  $pendBefore = NumOf (SqlOne "SELECT IFNULL(SUM(quantity),0) FROM warehouse_stock WHERE warehouse_id=$fxWh AND product_id=$fxProd AND quality_type='PENDING'")
  if ($aBefore -lt 1) {
    Write-Output "  INFO no A-grade stock in wh=$fxWh product=$fxProd to borrow -> positive fixture skipped"
  } else {
    $fxCode = 'XTH-OVERVIEW-PROBE'
    # uk_source_item = (source_type, source_item_id): the clone needs its own item id, otherwise 1062
    SqlRaw ("INSERT INTO after_sale_pending (source_type,source_id,source_item_id,source_code,source_date,warehouse_id,customer_id,product_id,product_name,unit,quantity,sorted_quantity,unit_price,company_id) SELECT source_type,source_id,UNIX_TIMESTAMP(),'$fxCode',CURDATE(),warehouse_id,customer_id,product_id,product_name,unit,1,0,0,company_id FROM after_sale_pending WHERE id=$srcId") | Out-Null
    $fxId = SqlOne "SELECT id FROM after_sale_pending WHERE source_code='$fxCode' ORDER BY id DESC LIMIT 1"
    if ($fxId -eq '') {
      Bad 'fixture insert failed (no after_sale_pending row created) - stock was left untouched'
    } else {
      # borrow 1 A-grade unit into PENDING so the fixture batch becomes sortable
      SqlRaw ("UPDATE warehouse_stock SET quantity=quantity-1 WHERE warehouse_id=$fxWh AND product_id=$fxProd AND quality_type='A'") | Out-Null
      SqlRaw ("UPDATE warehouse_stock SET quantity=quantity+1 WHERE warehouse_id=$fxWh AND product_id=$fxProd AND quality_type='PENDING'") | Out-Null
      $fxDraftId = 0
      try {
        $fxOv = Req 'GET' "$rsBase/pending-overview" $null $tokA
        $fxGrp = @($fxOv.data.warehouses | Where-Object { "$($_.warehouseId)" -eq "$fxWh" })
        $fxRow = @()
        if ($fxGrp.Count -gt 0) { $fxRow = @($fxGrp[0].rows | Where-Object { "$($_.pendingId)" -eq "$fxId" }) }
        if ($fxRow.Count -eq 1 -and "$($fxRow[0].status)" -eq 'SORTABLE' -and (NumOf $fxRow[0].quantity) -eq 1) {
          Ok 'fixture batch appears as SORTABLE with allocatable=1 in the default view'
        } else { Bad ("fixture batch not SORTABLE (rows=$($fxRow.Count))") }
        if ([int](NumOf $fxOv.data.summary.sortableCount) -ge 1) { Ok 'default overview now reports >=1 sortable batch' } else { Bad 'sortableCount still 0 although a fixture batch is open' }
        $bodyFx = @{ pendingIds = @([long]$fxId); defaultQuality = 'C'; sortDate = $today
                     targetWarehouseA = [long]$fxWh; targetWarehouseB = [long]$fxWh; targetWarehouseC = [long]$fxWh; targetWarehouseDefect = [long]$fxWh }
        $rf = Req 'POST' "$rsBase/batch-draft" $bodyFx $tokA
        if ((CodeOf $rf) -ne '200') {
          Bad ('fixture batch-draft -> ' + (CodeOf $rf) + ' ' + "$($rf.msg)")
        } else {
          $c0 = @($rf.data.created)[0]
          $fxDraftId = [long]$c0.id
          $cntC = [int](SqlOne ("SELECT COUNT(*) FROM return_sort_item WHERE sort_id=$fxDraftId"))
          $splitC = [int](SqlOne ("SELECT COUNT(*) FROM return_sort_item WHERE sort_id=$fxDraftId AND qty_c=total_quantity AND qty_a=0 AND qty_b=0 AND qty_defect=0"))
          if ($cntC -eq 1 -and $splitC -eq 1) { Ok 'defaultQuality=C pre-fills qtyC=total on the fixture draft' } else { Bad ("defaultQuality=C split wrong: $splitC of $cntC item(s)") }
          if ("$($c0.customerName)" -ne '') { Ok ("fixture draft carries the source customer (" + $c0.customerName + ")") } else { Write-Output '  INFO fixture draft has no customer name (source batch had none)' }
          $d = Req 'DELETE' "$rsBase/$fxDraftId" $null $tokA
          if ((CodeOf $d) -eq '200') { Ok 'fixture draft deleted again (draft-only delete)' } else { Bad ('fixture draft delete -> ' + (CodeOf $d) + ' msg=' + "$($d.msg)" + ' url=' + "$rsBase/$fxDraftId") }
        }
      } finally {
        # safety net: the fixture draft must never survive the run (idempotent SQL cleanup)
        if ($fxDraftId -gt 0) {
          SqlRaw ("DELETE FROM return_sort_item WHERE sort_id=$fxDraftId") | Out-Null
          SqlRaw ("DELETE FROM return_sort WHERE id=$fxDraftId") | Out-Null
        }
        SqlRaw ("UPDATE warehouse_stock SET quantity=quantity-1 WHERE warehouse_id=$fxWh AND product_id=$fxProd AND quality_type='PENDING'") | Out-Null
        SqlRaw ("UPDATE warehouse_stock SET quantity=quantity+1 WHERE warehouse_id=$fxWh AND product_id=$fxProd AND quality_type='A'") | Out-Null
        SqlRaw ("DELETE FROM after_sale_pending WHERE source_code='$fxCode'") | Out-Null
        $aAfter = NumOf (SqlOne "SELECT IFNULL(SUM(quantity),0) FROM warehouse_stock WHERE warehouse_id=$fxWh AND product_id=$fxProd AND quality_type='A'")
        $pendAfter = NumOf (SqlOne "SELECT IFNULL(SUM(quantity),0) FROM warehouse_stock WHERE warehouse_id=$fxWh AND product_id=$fxProd AND quality_type='PENDING'")
        $batchAfter = [int](SqlOne "SELECT COUNT(*) FROM after_sale_pending p WHERE $whScope")
        $rsAfter = [int](SqlOne 'SELECT COUNT(*) FROM return_sort')
        if ($aAfter -eq $aBefore -and $pendAfter -eq $pendBefore -and $batchAfter -eq $expAll -and $rsAfter -eq $rsBefore) {
          Ok ("fixture fully rolled back (A=$aAfter, PENDING=$pendAfter, batches=$batchAfter, return_sort=$rsAfter)")
        } else { Bad ("fixture rollback drifted: A $aBefore->$aAfter, PENDING $pendBefore->$pendAfter, batches $expAll->$batchAfter, return_sort $rsBefore->$rsAfter") }
      }
    }
  }
}

Write-Output '--- 7) permission: no stock:return-sort -> 403 on both new endpoints'
$expect = if ($permsU -contains 'stock:return-sort') { '200' } else { '403' }
$r1 = Req 'GET' "$rsBase/pending-overview" $null $tokU
if ((CodeOf $r1) -eq $expect) { Ok ("restricted user GET pending-overview -> " + (CodeOf $r1) + " (expected $expect)") } else { Bad ("restricted user GET pending-overview -> " + (CodeOf $r1) + " expected $expect") }
$r2 = Req 'POST' "$rsBase/batch-draft" @{ pendingIds = @(1) } $tokU
if ((CodeOf $r2) -eq $expect) { Ok ("restricted user POST batch-draft -> " + (CodeOf $r2) + " (expected $expect)") } else { Bad ("restricted user POST batch-draft -> " + (CodeOf $r2) + " expected $expect") }
# over-blocking guard: the page owner must still reach both
if ((CodeOf $r1) -eq '200' -or $expect -eq '403') {
  $r3 = Req 'GET' "$rsBase/pending-overview?includeCleared=true" $null $tokA
  if ((CodeOf $r3) -eq '200') { Ok 'admin still reaches pending-overview (no over-blocking)' } else { Bad ('admin pending-overview -> ' + (CodeOf $r3)) }
}

if ($fail -eq 0) { Write-Output 'RESULT RETURN-SORT-OVERVIEW PASS' } else { Write-Output ("RESULT RETURN-SORT-OVERVIEW FAIL count " + $fail) }
exit $fail
