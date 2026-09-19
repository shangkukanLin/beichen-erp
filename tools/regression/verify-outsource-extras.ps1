# Outsourcing scope EXTRA checks (2026-09-17): stock-loss / other-io OUT / material stock-take /
# defect-return-by-grade (C6 regression) / close-report confirm+reopen. API + DB cross-check, rerunnable.
# ASCII ONLY.
$ErrorActionPreference = 'Continue'
$base = 'http://localhost:8080/api'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$script:PASS = 0; $script:FAIL = 0
function Ok([bool]$c, [string]$m) { if ($c) { $script:PASS++; Write-Host ('PASS ' + $m) } else { $script:FAIL++; Write-Host ('FAIL ' + $m) } }
function Step([string]$m) { Write-Host ('--- ' + $m) }
function SqlOne([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null
  $ls = ((@($o) | ForEach-Object { "$_" }) -join "`n").Trim() -split "`n"
  if ($ls.Count -lt 2) { return '' }
  return (($ls[1] -split "`t")[0]).Trim()
}
function D($s) { if (-not $s) { return [decimal]0 }; return [decimal]$s }
function StockMat([int]$wh, [int]$mid) { return SqlOne "SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE warehouse_id=$wh AND material_id=$mid" }
function StockProd([int]$wh, [int]$prodId, [string]$q) { return SqlOne "SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE warehouse_id=$wh AND product_id=$prodId AND quality_type='$q'" }
function LogCount([string]$ct, [string]$code) { return SqlOne "SELECT COUNT(*) FROM warehouse_stock_log WHERE change_type='$ct' AND related_order_code='$code'" }

$lg = Invoke-RestMethod -Uri "$base/auth/login" -Method Post -ContentType 'application/json' -Body '{"username":"lin","password":"123","companyId":1}'
$h = @{ Authorization = $lg.data.token }
function Api($method, $path, $body) {
  try {
    if ($body) { return Invoke-RestMethod -Uri "$base$path" -Method $method -Headers $h -ContentType 'application/json' -Body ($body | ConvertTo-Json -Depth 8 -Compress) }
    return Invoke-RestMethod -Uri "$base$path" -Method $method -Headers $h
  } catch { return $_.ErrorDetails.Message }
}
function Msg($r) { if ($null -eq $r) { return '<null>' }; if ($r -is [string]) { return $r }; return ('code=' + $r.code + ' ' + $r.msg) }
function LastId([string]$t) { return [int](SqlOne "SELECT COALESCE(MAX(id),0) FROM $t") }

# ---- 以库为准：原先写死的 wh42 / material30 / type50 / factory23 / wh38 / order47 全部改为从库解析 ----
# （清库或换环境后，写死的 id 必然失效 —— 这正是本脚本之前 15/35 的原因）
$WHM = [int](SqlOne "SELECT w.id FROM warehouse w JOIN warehouse_stock s ON s.warehouse_id=w.id WHERE s.material_id IS NOT NULL AND s.quantity>10 GROUP BY w.id ORDER BY SUM(s.quantity) DESC LIMIT 1")
$pair = SqlOne ("SELECT CONCAT(m.id,'|',m.material_type_id) FROM warehouse_stock s JOIN outsource_material m ON m.id=s.material_id WHERE s.warehouse_id=$WHM AND s.quantity>10 ORDER BY s.quantity DESC LIMIT 1")
if ($pair -and $pair.Contains('|')) { $MAT = [int]$pair.Split('|')[0]; $MTYPE = [int]$pair.Split('|')[1] } else { $MAT = 0; $MTYPE = 0 }
$FAC = [int](SqlOne "SELECT factory_id FROM warehouse WHERE warehouse_category='OUTSOURCE' ORDER BY id LIMIT 1")
$WHF = [int](SqlOne "SELECT warehouse_id FROM warehouse_stock WHERE product_id IS NOT NULL AND quantity>10 ORDER BY quantity DESC LIMIT 1")
$ORD = [int](SqlOne "SELECT id FROM outsource_order WHERE status='PRODUCING' ORDER BY id LIMIT 1")
# E3: a product that actually holds B-grade stock in our finished warehouse (grade-B defect return needs it)
$PROD = [int](SqlOne "SELECT product_id FROM warehouse_stock WHERE quality_type='B' AND quantity>=1 ORDER BY quantity DESC LIMIT 1")
$PRODNAME = 'E2E-PROD'
if ($PROD -gt 0) { $PRODNAME = SqlOne ("SELECT name FROM product WHERE id=" + $PROD) }
# E5: a material warehouse WITHOUT a take in the current period (a take already audited cannot be re-opened)
$period0 = (Get-Date -Format 'yyyy-MM')
$WHM5 = [int](SqlOne ("SELECT w.id FROM warehouse w JOIN warehouse_stock s ON s.warehouse_id=w.id WHERE s.material_id IS NOT NULL AND s.quantity>10 AND w.id NOT IN (SELECT warehouse_id FROM inventory_stock_take WHERE period='" + $period0 + "' AND status<>'CANCELLED') GROUP BY w.id ORDER BY SUM(s.quantity) DESC LIMIT 1"))
if ($WHM5 -le 0) { $WHM5 = $WHM }
Write-Host ('[SEED2] gradeBProduct=' + $PROD + '(' + $PRODNAME + ') freeMaterialWh=' + $WHM5)
Write-Host ('[SEED] materialWh=' + $WHM + ' material=' + $MAT + ' materialType=' + $MTYPE + ' factory=' + $FAC + ' productWh=' + $WHF + ' producingOrder=' + $ORD)
Ok (($WHM -gt 0) -and ($MAT -gt 0) -and ($MTYPE -gt 0) -and ($FAC -gt 0)) 'resolved warehouse/material/type/factory from the DB (no hardcoded ids)'

# =====================================================================
Step 'E1 material stock-loss: audit / un-audit / cancel'
$b1 = D (StockMat $WHM $MAT)
$r = Api 'POST' '/outsource/stock-loss' @{ warehouseId = $WHM; lossReason = 'DAMAGE'; lossDate = (Get-Date -Format 'yyyy-MM-dd'); remark = 'AUDIT-E1'; items = @(@{ materialId = $MAT; materialTypeId = $MTYPE; quantity = 2; unitPrice = 10 }) }
Write-Host ('create -> ' + (Msg $r))
$id1 = LastId 'outsource_stock_loss'
Ok ($id1 -gt 0) ('E1 doc id=' + $id1)
Ok ((SqlOne "SELECT status FROM outsource_stock_loss WHERE id=$id1") -eq 'DRAFT') 'E1 created as DRAFT'
Ok ((Msg (Api 'PUT' "/outsource/stock-loss/$id1/audit" $null)) -match 'code=200') 'E1 audit ok'
$after1 = D (StockMat $WHM $MAT)
Ok (($after1 -eq ($b1 - 2))) ('E1 stock -2 after audit (' + $b1 + ' -> ' + $after1 + ')')
Ok ((D (LogCount 'LOSS_OUT' (SqlOne "SELECT code FROM outsource_stock_loss WHERE id=$id1"))) -ge 1) 'E1 LOSS_OUT log written with bill code'
Ok ((Msg (Api 'PUT' "/outsource/stock-loss/$id1/un-audit" $null)) -match 'code=200') 'E1 un-audit ok'
Ok ((D (StockMat $WHM $MAT)) -eq $b1) 'E1 stock restored by un-audit'
Ok ((Msg (Api 'PUT' "/outsource/stock-loss/$id1/cancel" $null)) -match 'code=200') 'E1 cancel ok'
Ok ((SqlOne "SELECT status FROM outsource_stock_loss WHERE id=$id1") -eq 'CANCELLED') 'E1 doc cancelled'

# =====================================================================
Step 'E2 material other-io OUT: audit / un-audit / cancel'
$b2 = D (StockMat $WHM $MAT)
$r = Api 'POST' '/outsource/other-io' @{ warehouseId = $WHM; ioType = 'OUT'; ioDate = (Get-Date -Format 'yyyy-MM-dd'); remark = 'AUDIT-E2'; items = @(@{ materialId = $MAT; materialTypeId = $MTYPE; quantity = 3; unit = 'PCS'; unit_price = 10 }) }
Write-Host ('create -> ' + (Msg $r))
$id2 = LastId 'outsource_other_io'
Ok ($id2 -gt 0) ('E2 doc id=' + $id2)
Ok ((Msg (Api 'PUT' "/outsource/other-io/$id2/audit" $null)) -match 'code=200') 'E2 audit ok'
Ok ((D (StockMat $WHM $MAT)) -eq ($b2 - 3)) 'E2 stock -3 after audit'
Ok ((Msg (Api 'PUT' "/outsource/other-io/$id2/un-audit" $null)) -match 'code=200') 'E2 un-audit ok'
Ok ((D (StockMat $WHM $MAT)) -eq $b2) 'E2 stock restored by un-audit'
Ok ((Msg (Api 'PUT' "/outsource/other-io/$id2/cancel" $null)) -match 'code=200') 'E2 cancel ok'

# =====================================================================
Step 'E3 defect return by GRADE B (C6 regression: grade must come from the line, not default A)'
$bB = D (StockProd $WHF $PROD 'B'); $bA = D (StockProd $WHF $PROD 'A')
$r = Api 'POST' '/outsource/return-order' @{
  returnType = 'DEFECT'; factoryId = $FAC; warehouseId = $WHF; returnDate = (Get-Date -Format 'yyyy-MM-dd'); remark = 'AUDIT-E3'
  products = @(@{ productId = $PROD; productName = $PRODNAME; qualityType = 'B'; quantity = 1 })
  items = @()
}
Write-Host ('create -> ' + (Msg $r))
$id3 = LastId 'outsource_return_order'
Ok ($id3 -gt 0) ('E3 doc id=' + $id3)
Ok ((SqlOne "SELECT quality_type FROM outsource_return_order_product WHERE return_order_id=$id3") -eq 'B') 'E3 line grade=B persisted'
Ok ((Msg (Api 'PUT' "/outsource/return-order/$id3/audit" $null)) -match 'code=200') 'E3 audit ok'
Ok ((D (StockProd $WHF $PROD 'B')) -eq ($bB - 1)) ('E3 B grade -1 (' + $bB + ' -> ' + (StockProd $WHF $PROD 'B') + ')')
Ok ((D (StockProd $WHF $PROD 'A')) -eq $bA) 'E3 A grade UNCHANGED (no default-A contamination)'
Ok ((Msg (Api 'PUT' "/outsource/return-order/$id3/un-audit" $null)) -match 'code=200') 'E3 un-audit ok'
$bRest = D (StockProd $WHF $PROD 'B')
for ($t = 0; ($t -lt 6) -and ($bRest -ne $bB); $t++) { Start-Sleep -Milliseconds 600; $bRest = D (StockProd $WHF $PROD 'B') }
Ok (($bRest -eq $bB)) ('E3 B grade restored (' + $bRest + ' = ' + $bB + ')')
Ok ((Msg (Api 'PUT' "/outsource/return-order/$id3/cancel" $null)) -match 'code=200') 'E3 cancel ok'

# =====================================================================
Step 'E4 close-report confirm / reopen (order status + report + payable net)'
$oid = $ORD
$stBefore = SqlOne "SELECT status FROM outsource_order WHERE id=$oid"
Write-Host ('order ' + $oid + ' status before=' + $stBefore)
if ($stBefore -eq 'PRODUCING') {
  $payBefore = D (SqlOne "SELECT COALESCE(SUM(amount),0) FROM finance_payable WHERE status<>'CANCELLED' AND supplier_id=(SELECT factory_id FROM outsource_order WHERE id=$oid)")
  $cf = Api 'POST' "/outsource/order/$oid/close-report/confirm" @{ returnWarehouseId = $WHM }
  Write-Host ('confirm -> ' + (Msg $cf))
  Ok ((SqlOne "SELECT status FROM outsource_order WHERE id=$oid") -eq 'FINISHED') 'E4 order -> FINISHED'
  $repStatus = SqlOne "SELECT status FROM outsource_order_close_report WHERE order_id=$oid"
  Write-Host ('close report status=' + $repStatus)
  Ok ($repStatus -ne 'DRAFT') 'E4 close report left DRAFT'
  $rp = Api 'POST' "/outsource/order/$oid/close-report/reopen" $null
  Write-Host ('reopen -> ' + (Msg $rp))
  Ok ((SqlOne "SELECT status FROM outsource_order WHERE id=$oid") -eq 'PRODUCING') 'E4 order back to PRODUCING'
  Ok ((SqlOne "SELECT status FROM outsource_order_close_report WHERE order_id=$oid") -eq 'DRAFT') 'E4 close report back to DRAFT'
  $payAfter = D (SqlOne "SELECT COALESCE(SUM(amount),0) FROM finance_payable WHERE status<>'CANCELLED' AND supplier_id=(SELECT factory_id FROM outsource_order WHERE id=$oid)")
  Ok (($payAfter -eq $payBefore)) ('E4 payable net unchanged (' + $payBefore + ' -> ' + $payAfter + ')')
} else { Ok $false ('E4 skipped: order ' + $oid + ' is not PRODUCING') }

# =====================================================================
Step 'E5 material stock-take (scope=MATERIAL) full chain'
$period = (Get-Date -Format 'yyyy-MM')
$r5 = Api 'POST' '/inventory/stock-take' @{ warehouseId = $WHM5; period = $period; scope = 'MATERIAL'; remark = 'AUDIT-E5' }
Write-Host ('create -> ' + (Msg $r5))
$id5 = 0
$x5 = $r5
if ($x5 -and $x5.data) { $id5 = [int]$x5.data.id }
if ($id5 -le 0) {
  # 重复创建被幂等拒绝（正常）→ 必须按 (仓库, 月份) 精确回查本单，
  # 不能用 LastId（库里还有别的仓的盘点单时会取到别人那张）。
  $id5 = [int](SqlOne ("SELECT id FROM inventory_stock_take WHERE warehouse_id=$WHM5 AND period='" + $period + "' AND status<>'CANCELLED' ORDER BY id DESC LIMIT 1"))
  Write-Host ('E5 reuse existing take id=' + $id5)
}
Ok ($id5 -gt 0) ('E5 stock-take id=' + $id5)
$items5 = Api 'GET' "/inventory/stock-take/$id5/items" $null
if ($items5 -and $items5.data -and $items5.data.Count -gt 0) {
  Write-Host ('E5 first item keys: ' + (($items5.data[0].PSObject.Properties.Name) -join ','))
  Write-Host ('E5 first item: ' + ($items5.data[0] | ConvertTo-Json -Compress))
  $b5 = D (StockMat $WHM5 $MAT)
  $list5 = @()
  foreach ($it in $items5.data) {
    # 回传**完整**明细行（必须带 bookQuantity，否则服务端按 0 算差异 → 差异被算成"实盘全量增量"）
    $o = @{}
    foreach ($p in $it.PSObject.Properties) { $o[$p.Name] = $p.Value }
    # F6 regression (2026-09-18): keep the STALE snapshot book_quantity on purpose,
    # only submit actualQuantity = current stock + 1. Server must recompute the diff
    # against CURRENT book, so that stock after audit == actual (physical count).
    if ($it.materialId -eq $MAT) { $o['actualQuantity'] = ($b5 + 1) }
    $list5 += $o
  }
  Write-Host ('saveItems -> ' + (Msg (Api 'PUT' "/inventory/stock-take/$id5/items" $list5)))
  $q5 = "SELECT book_quantity, actual_quantity, diff_quantity FROM inventory_stock_take_item WHERE take_id=$id5 AND material_id=$MAT"
  $lb5 = D (SqlOne ("SELECT book_quantity FROM inventory_stock_take_item WHERE take_id=" + $id5 + " AND material_id=$MAT"))
  $la5 = D (SqlOne ("SELECT actual_quantity FROM inventory_stock_take_item WHERE take_id=" + $id5 + " AND material_id=$MAT"))
  $ld5 = D (SqlOne ("SELECT diff_quantity FROM inventory_stock_take_item WHERE take_id=" + $id5 + " AND material_id=$MAT"))
  Write-Host ('E5 stored line after save: book=' + $lb5 + ' actual=' + $la5 + ' diff=' + $ld5)
  Write-Host ('audit -> ' + (Msg (Api 'PUT' "/inventory/stock-take/$id5/audit" $null)))
  $a5 = D (StockMat $WHM5 $MAT)
  Write-Host ('stock after audit: ' + $b5 + ' -> ' + $a5 + ' (actual=' + ($b5 + 1) + ')')
  Ok (($a5 -eq ($b5 + 1))) 'E5 audited adjustment applied (+1)'
  # F6 core assertion: stock after audit == physical count (book recomputed from current stock)
  Ok ($lb5 -eq $b5) 'E5(F6) item book refreshed to current stock'
  Ok ($ld5 -eq 1) 'E5(F6) item diff recomputed against current book (+1)'
  Write-Host ('un-audit -> ' + (Msg (Api 'PUT' "/inventory/stock-take/$id5/un-audit" $null)))
  Ok ((D (StockMat $WHM5 $MAT)) -eq $b5) 'E5 un-audit rolled the adjustment back'
} else {
  Ok $false 'E5 could not load stock-take items'
}

# =====================================================================
Step 'E6 reconciliation: stock vs log diff must be empty'
$diffP = SqlOne "SELECT COUNT(*) FROM warehouse_stock s LEFT JOIN (SELECT warehouse_id, product_id, quality_type, SUM(change_quantity) qty FROM warehouse_stock_log WHERE product_id IS NOT NULL GROUP BY warehouse_id, product_id, quality_type) l ON l.warehouse_id=s.warehouse_id AND l.product_id=s.product_id AND l.quality_type=s.quality_type WHERE s.product_id IS NOT NULL AND s.quantity <> COALESCE(l.qty,0)"
$diffM = SqlOne "SELECT COUNT(*) FROM warehouse_stock s LEFT JOIN (SELECT warehouse_id, material_id, SUM(change_quantity) qty FROM warehouse_stock_log WHERE material_id IS NOT NULL GROUP BY warehouse_id, material_id) l ON l.warehouse_id=s.warehouse_id AND l.material_id=s.material_id WHERE s.material_id IS NOT NULL AND s.quantity <> COALESCE(l.qty,0)"
Ok ((D $diffP) -eq 0) ('E6 product stock vs log diff rows = ' + $diffP)
Ok ((D $diffM) -eq 0) ('E6 material stock vs log diff rows = ' + $diffM)

Write-Host ''
Write-Host ('RESULT ' + $(if ($script:FAIL -eq 0) { 'PASS' } else { 'FAIL' }) + ' outsourcing-extras  (PASS=' + $script:PASS + ' FAIL=' + $script:FAIL + ')')
