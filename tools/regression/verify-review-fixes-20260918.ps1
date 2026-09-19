# Verify the 2026-09-18 code-review fixes (see docs/CodeReviewReport_20260918 / docs/代码审核报告_20260918.md).
#
#   F1-1  exchange audit must NOT count the document itself in "already exchanged".
#         Headline case = the UI default path: return qty == FULL remaining canExchange
#         (old code rejected it because it counted itself; e.g. sold 5 / exchanged 3 -> "can 2").
#   F1-2  purchase exchange: a forged or cross-order purchase_order_item_id anchor must be rejected
#         (old code silently skipped the quantity check when the anchor did not resolve).
#   F1-4  purchase exchange payable ledger bill_no now uses the D1 'YF-' numbering (not '<code>-RET').
#   F2-1  outsource defect return without an outbound warehouse must be refused at audit
#         (old code silently skipped the finished-goods deduction while still booking material-in + payable).
#
# Side effects: only its own test documents, which are reverted (un-audit + cancel); the seeded
# DEFECT stock (quality reclassify) is reverted too, and stock is asserted back to baseline.
# Pure ASCII file (Chinese text only ever comes back from the API as data, never as a literal here).

$ErrorActionPreference = 'Continue'
$exe = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
function Sql([string]$sql) { return (& $exe --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -N -B -e $sql 2>$null) }
function SqlOne([string]$sql) { $r = @(Sql $sql); if ($r.Count -lt 1) { return '' }; return ([string]$r[0]).Trim() }
function D($v) { try { return [double]$v } catch { return 0.0 } }
function I($v) { try { return [int]$v } catch { return 0 } }
$api = 'http://localhost:8080/api'
$fail = 0
function Ok([bool]$c, [string]$m) { if ($c) { Write-Output ('PASS ' + $m) } else { Write-Output ('FAIL ' + $m); $script:fail++ } }
function CodeOf($r) { if ($null -eq $r) { return '-1' }; return ([string]$r.code) }
function MsgOf($r) { if ($null -eq $r) { return 'exception/thrown' }; return ([string]$r.msg) }
function CallPost([string]$url, $h, [string]$body) {
  try { return Invoke-RestMethod -Uri $url -Method Post -Headers $h -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body)) }
  catch { return $null }
}
function CallPut([string]$url, $h) {
  try { return Invoke-RestMethod -Uri $url -Method Put -Headers $h }
  catch { return $null }
}

$lg = CallPost "$api/auth/login" @{} '{"username":"lin","password":"123","companyId":1}'
$tok = [string]$lg.data.token
if ($tok -eq '') { Write-Output 'RESULT FAIL cannot login'; exit 1 }
$h = @{ Authorization = $tok }

# ===== fixtures resolved from the live DB =====
$whId = I (SqlOne "SELECT id FROM warehouse WHERE warehouse_type='FINISHED' AND warehouse_category='INVENTORY' ORDER BY id LIMIT 1;")
$poId = I (SqlOne "SELECT id FROM purchase_order WHERE status='AUDITED' ORDER BY id DESC LIMIT 1;")
$poiId = I (SqlOne "SELECT id FROM purchase_order_item WHERE order_id=$poId ORDER BY id LIMIT 1;")
$prodId = I (SqlOne "SELECT product_id FROM purchase_order_item WHERE id=$poiId;")
$supId = I (SqlOne "SELECT supplier_id FROM purchase_order WHERE id=$poId;")
$price = D (SqlOne "SELECT unit_price FROM purchase_order_item WHERE id=$poiId;")
$purchased = D (SqlOne "SELECT quantity FROM purchase_order_item WHERE id=$poiId;")
$returned = D (SqlOne "SELECT IFNULL(SUM(i.quantity),0) FROM purchase_return_item i JOIN purchase_return r ON r.id=i.return_id WHERE i.purchase_order_item_id=$poiId AND r.status='AUDITED';")
$exchanged = D (SqlOne "SELECT IFNULL(SUM(i.quantity),0) FROM purchase_exchange_item i JOIN purchase_exchange x ON x.id=i.exchange_id WHERE i.purchase_order_item_id=$poiId AND x.status='AUDITED';")
$remain = $purchased - $returned - $exchanged
Write-Output ("--- fixtures: warehouse=$whId po=$poId poItem=$poiId product=$prodId supplier=$supId price=$price purchased=$purchased returned=$returned exchanged=$exchanged remain=$remain")
Ok (($whId -gt 0) -and ($poId -gt 0) -and ($poiId -gt 0) -and ($prodId -gt 0) -and ($remain -gt 0)) 'fixtures resolved from live DB'
if ($fail -gt 0) { Write-Output 'RESULT FAIL fixtures missing'; exit 1 }

$aBase = D (SqlOne "SELECT IFNULL(quantity,0) FROM warehouse_stock WHERE warehouse_id=$whId AND product_id=$prodId AND quality_type='A';")
$dBase = D (SqlOne "SELECT IFNULL(quantity,0) FROM warehouse_stock WHERE warehouse_id=$whId AND product_id=$prodId AND quality_type='DEFECT';")
$rcId = 0
$xid = 0
$rtId = 0
$sxid = 0

try {
  # ---------------------------------------------------------------- F1-1
  Write-Output '--- 1) F1-1: audit with the FULL remaining can-exchange qty (the UI default path)'
  $def = D (SqlOne "SELECT IFNULL(quantity,0) FROM warehouse_stock WHERE warehouse_id=$whId AND product_id=$prodId AND quality_type='DEFECT';")
  if ($def -lt $remain) {
    $need = [Math]::Ceiling($remain - $def + 5)
    $rcBody = @{ warehouseId = $whId; reclassifyDate = (Get-Date -Format 'yyyy-MM-dd'); remark = 'audit-fix verify seed DEFECT'
      items = @(@{ productId = $prodId; fromQuality = 'A'; toQuality = 'DEFECT'; quantity = $need }) } | ConvertTo-Json -Depth 6
    $rc = CallPost "$api/inventory/reclassify" $h $rcBody
    # POST /inventory/reclassify returns R<Void> (no id in the payload) -> resolve the new doc id from the DB
    $rcId = I (SqlOne "SELECT IFNULL(MAX(id),0) FROM product_reclassify WHERE remark='audit-fix verify seed DEFECT';")
    if ($rcId -le 0) { Write-Output ('  WARN reclassify create did not land: ' + (MsgOf $rc)) }
    if ($rcId -gt 0) {
      $ra = CallPut "$api/inventory/reclassify/$rcId/audit" $h
      Start-Sleep -Milliseconds 700
      Write-Output ('  seeded DEFECT via reclassify id=' + $rcId + ' need=' + $need + ' auditCode=' + (CodeOf $ra) + ' ' + (MsgOf $ra))
    }
    Write-Output ("  DEFECT now " + (SqlOne "SELECT IFNULL(quantity,0) FROM warehouse_stock WHERE warehouse_id=$whId AND product_id=$prodId AND quality_type='DEFECT';"))
  }
  $body = (@{ supplierId = $supId; purchaseOrderId = $poId; warehouseOutId = $whId; warehouseInId = $whId
      exchangeDate = (Get-Date -Format 'yyyy-MM-dd'); remark = 'audit-fix verify F1-1'
      items = @(@{ purchaseOrderItemId = $poiId; productId = $prodId; qualityType = 'DEFECT'; quantity = $remain; unitPrice = $price
                   inQuantity = $remain; inQualityType = 'A'; inUnitPrice = $price }) } | ConvertTo-Json -Depth 6)
  $c1 = CallPost "$api/inventory/purchase-exchange" $h $body
  Ok ((CodeOf $c1) -eq '200') ('create with qty = full remaining (' + $remain + ') accepted (msg=' + (MsgOf $c1) + ')')
  $xid = I $c1.data.id
  $xcode = [string]$c1.data.code
  if ($xid -le 0) { throw 'create failed, cannot continue F1-1' }
  $a1 = CallPut "$api/inventory/purchase-exchange/$xid/audit" $h
  Write-Output ('  audit -> code=' + (CodeOf $a1) + ' msg=' + (MsgOf $a1))
  Ok ((CodeOf $a1) -eq '200') 'F1-1 audit accepted (before the fix the doc itself was counted as already-exchanged)'
  Ok ((SqlOne "SELECT status FROM purchase_exchange WHERE id=$xid;") -eq 'AUDITED') 'document is AUDITED'

  # ---------------------------------------------------------------- F1-1 (sale side, same root cause)
  Write-Output '--- 1b) F1-1 sale side: audit with the FULL remaining can-exchange qty'
  $soi = I (SqlOne "SELECT oi.id FROM sale_order_item oi JOIN sale_order o ON o.id=oi.order_id WHERE o.status='AUDITED' ORDER BY oi.id LIMIT 1;")
  $sprod = I (SqlOne "SELECT product_id FROM sale_order_item WHERE id=$soi;")
  $soId = I (SqlOne "SELECT order_id FROM sale_order_item WHERE id=$soi;")
  $scust = I (SqlOne "SELECT customer_id FROM sale_order WHERE id=$soId;")
  $swh = I (SqlOne "SELECT warehouse_id FROM sale_order WHERE id=$soId;")
  $sprice = D (SqlOne "SELECT unit_price FROM sale_order_item WHERE id=$soi;")
  $sold = D (SqlOne "SELECT quantity FROM sale_order_item WHERE id=$soi;")
  $sret = D (SqlOne "SELECT IFNULL(SUM(i.quantity),0) FROM sale_return_item i JOIN sale_return r ON r.id=i.return_id WHERE i.sale_order_item_id=$soi AND r.status='AUDITED';")
  $sxch = D (SqlOne "SELECT IFNULL(SUM(i.quantity),0) FROM sale_exchange_item i JOIN sale_exchange x ON x.id=i.exchange_id WHERE i.sale_order_item_id=$soi AND x.status='AUDITED';")
  $sRemain = $sold - $sret - $sxch
  $saBase = D (SqlOne "SELECT IFNULL(quantity,0) FROM warehouse_stock WHERE warehouse_id=$swh AND product_id=$sprod AND quality_type='A';")
  Write-Output ("  sale fixtures: order=$soId item=$soi product=$sprod cust=$scust wh=$swh sold=$sold returned=$sret exchanged=$sxch remain=$sRemain A=" + $saBase)
  if (($sRemain -gt 0) -and ($saBase -ge $sRemain)) {
    $sBody = (@{ saleOrderId = $soId; customerId = $scust; warehouseInId = $swh; warehouseOutId = $swh
        exchangeDate = (Get-Date -Format 'yyyy-MM-dd'); remark = 'audit-fix verify F1-1 sale'
        items = @(@{ saleOrderItemId = $soi; productId = $sprod; quantity = $sRemain; unitPrice = $sprice
                     outQuantity = $sRemain; outUnitPrice = $sprice; outQualityType = 'A' }) } | ConvertTo-Json -Depth 6)
    $sc = CallPost "$api/sale/exchange" $h $sBody
    Ok ((CodeOf $sc) -eq '200') ('sale exchange created with the full remaining qty (' + $sRemain + ') msg=' + (MsgOf $sc))
    $sxid = I $sc.data.id
    if ($sxid -gt 0) {
      $sa = CallPut "$api/sale/exchange/$sxid/audit" $h
      Write-Output ('  sale audit -> code=' + (CodeOf $sa) + ' msg=' + (MsgOf $sa))
      Ok ((CodeOf $sa) -eq '200') 'F1-1 sale side: audit accepted (before the fix the doc counted itself)'
      Ok ((SqlOne "SELECT status FROM sale_exchange WHERE id=$sxid;") -eq 'AUDITED') 'sale exchange is AUDITED'
      $su = CallPut "$api/sale/exchange/$sxid/un-audit" $h
      Ok ((CodeOf $su) -eq '200') 'sale side un-audit accepted'
      CallPut "$api/sale/exchange/$sxid/cancel" $h | Out-Null
      $saNow = D (SqlOne "SELECT IFNULL(quantity,0) FROM warehouse_stock WHERE warehouse_id=$swh AND product_id=$sprod AND quality_type='A';")
      Ok ($saNow -eq $saBase) ('sale side stock restored (A ' + $saBase + ' -> ' + $saNow + ')')
    }
  } else {
    Write-Output '  (sale fixture has no remaining qty or too little A stock - sale case skipped)'
  }

  # ---------------------------------------------------------------- F1-4
  Write-Output '--- 2) F1-4: payable ledger numbering follows the D1 convention'
  $bnRet = SqlOne "SELECT bill_no FROM finance_payable WHERE source_bill_type='PURCHASE_EXCHANGE_RETURN' AND source_bill_no='$xcode' AND status<>'CANCELLED' LIMIT 1;"
  $bnIn = SqlOne "SELECT bill_no FROM finance_payable WHERE source_bill_type='PURCHASE_EXCHANGE_IN' AND source_bill_no='$xcode' AND status<>'CANCELLED' LIMIT 1;"
  Write-Output ("  bill_no: return=$bnRet in=$bnIn")
  Ok ($bnRet.StartsWith('YF-') -and $bnIn.StartsWith('YF-')) 'both ledgers use the YF- numbering (no more <code>-RET/-IN)'
  Ok ((SqlOne "SELECT COUNT(*) FROM finance_payable WHERE source_bill_no='$xcode' AND status<>'CANCELLED';") -eq '2') 'exactly two active ledgers'

  # ---------------------------------------------------------------- F1-2
  Write-Output '--- 3) F1-2: forged / cross-order anchor must be rejected'
  $badBody = (@{ supplierId = $supId; purchaseOrderId = $poId; warehouseOutId = $whId; warehouseInId = $whId
      exchangeDate = (Get-Date -Format 'yyyy-MM-dd'); remark = 'audit-fix verify F1-2 forged'
      items = @(@{ purchaseOrderItemId = 999999999; productId = $prodId; qualityType = 'DEFECT'; quantity = 1; unitPrice = $price
                   inQuantity = 1; inQualityType = 'A'; inUnitPrice = $price }) } | ConvertTo-Json -Depth 6)
  $cForged = CallPost "$api/inventory/purchase-exchange" $h $badBody
  Write-Output ('  forged anchor -> code=' + (CodeOf $cForged) + ' msg=' + (MsgOf $cForged))
  Ok ((CodeOf $cForged) -ne '200') 'forged anchor rejected at save time (old code silently skipped the quantity check)'
  $otherPoi = I (SqlOne "SELECT id FROM purchase_order_item WHERE order_id<>$poId ORDER BY id LIMIT 1;")
  if ($otherPoi -gt 0) {
    $crossBody = (@{ supplierId = $supId; purchaseOrderId = $poId; warehouseOutId = $whId; warehouseInId = $whId
        exchangeDate = (Get-Date -Format 'yyyy-MM-dd'); remark = 'audit-fix verify F1-2 cross'
        items = @(@{ purchaseOrderItemId = $otherPoi; productId = $prodId; qualityType = 'DEFECT'; quantity = 1; unitPrice = $price
                     inQuantity = 1; inQualityType = 'A'; inUnitPrice = $price }) } | ConvertTo-Json -Depth 6)
    $cCross = CallPost "$api/inventory/purchase-exchange" $h $crossBody
    Write-Output ('  cross-order anchor(' + $otherPoi + ') -> code=' + (CodeOf $cCross) + ' msg=' + (MsgOf $cCross))
    Ok ((CodeOf $cCross) -ne '200') 'anchor belonging to another purchase order rejected'
  } else {
    Write-Output '  (only one purchase order in the DB - cross-order case skipped)'
  }

  # ---------------------------------------------------------------- F2-1
  Write-Output '--- 4) F2-1: defect return without an outbound warehouse must be refused at audit'
  $factoryId = I (SqlOne "SELECT factory_id FROM outsource_order WHERE factory_id IS NOT NULL ORDER BY id DESC LIMIT 1;")
  $payBefore = SqlOne "SELECT COUNT(*) FROM finance_payable;"
  if ($factoryId -gt 0) {
    $rtBody = (@{ returnType = 'DEFECT'; factoryId = $factoryId; returnDate = (Get-Date -Format 'yyyy-MM-dd')
        remark = 'audit-fix verify F2-1'; chargeFlag = 0; orderId = $null; items = @()
        products = @(@{ productId = $prodId; productMasterId = $prodId; productName = 'audit-fix'; quantity = 1; qualityType = 'DEFECT' }) } | ConvertTo-Json -Depth 6)
    $rt = CallPost "$api/outsource/return-order" $h $rtBody
    # POST /outsource/return-order returns R<Void> (no id) -> resolve by remark
    $rtId = I (SqlOne "SELECT IFNULL(MAX(id),0) FROM outsource_return_order WHERE remark='audit-fix verify F2-1';")
    Write-Output ('  create without warehouseId -> code=' + (CodeOf $rt) + ' resolvedId=' + $rtId + ' msg=' + (MsgOf $rt))
    if ($rtId -gt 0) {
      $ra = CallPut "$api/outsource/return-order/$rtId/audit" $h
      Write-Output ('  audit -> code=' + (CodeOf $ra) + ' msg=' + (MsgOf $ra))
      Ok ((CodeOf $ra) -ne '200') 'audit refused when the outbound warehouse is missing (was silently skipped before)'
      Ok ((SqlOne "SELECT status FROM outsource_return_order WHERE id=$rtId;") -eq 'DRAFT') 'document stayed DRAFT'
      Ok ((SqlOne "SELECT COUNT(*) FROM finance_payable;") -eq $payBefore) 'no payable row was created (no half-booked doc)'
    } else {
      Write-Output '  (create rejected - acceptable, the guard is even earlier now)'
      Ok $true 'defect return without a warehouse could not even be created'
    }
  } else {
    Write-Output '  (no factory found - F2-1 case skipped)'
  }

  # ---------------------------------------------------------------- F2-2 (return side)
  Write-Output '--- 5) F2-2 return side: cumulative return must not exceed the delivered qty'
  $rdId = I (SqlOne "SELECT d.id FROM outsource_order_delivery d JOIN outsource_order o ON o.id=d.order_id WHERE d.status='AUDITED' AND d.quantity>0 AND d.warehouse_id IS NOT NULL AND d.is_reverse=0 ORDER BY d.id LIMIT 1;")
  if ($rdId -gt 0) {
    $rd = SqlOne ("SELECT CONCAT_WS('|', order_id, product_id, IFNULL(product_master_id,0), quantity, warehouse_id) FROM outsource_order_delivery WHERE id=$rdId;")
    $rp = ([string]$rd) -split '\|'
    $rdOrder = [int]$rp[0]; $rdRow = [int]$rp[1]; $rdMaster = [int]$rp[2]; $rdQty = D $rp[3]; $rdWh = [int]$rp[4]
    if ($rdMaster -le 0) { $rdMaster = I (SqlOne "SELECT product_id FROM outsource_order_product WHERE id=$rdRow;") }
    $rdFactory = I (SqlOne "SELECT factory_id FROM outsource_order WHERE id=$rdOrder;")
    $rdReturned = D (SqlOne "SELECT IFNULL(SUM(p.quantity),0) FROM outsource_return_order_product p JOIN outsource_return_order r ON r.id=p.return_order_id WHERE r.source_delivery_id=$rdId AND r.status='AUDITED';")
    $rdStock = D (SqlOne "SELECT IFNULL(quantity,0) FROM warehouse_stock WHERE warehouse_id=$rdWh AND product_id=$rdMaster AND quality_type='A';")
    $rdRemain = $rdQty - $rdReturned
    Write-Output ("  return fixtures: delivery=$rdId order=$rdOrder row=$rdRow master=$rdMaster deliveredQty=" + $rdQty + " alreadyReturned=" + $rdReturned + " wh=$rdWh A=" + $rdStock)
    if (($rdFactory -gt 0) -and ($rdRemain -ge 1)) {
      $logB2 = I (SqlOne "SELECT COUNT(*) FROM warehouse_stock_log;")
      $payB2 = I (SqlOne "SELECT COUNT(*) FROM finance_payable;")
      $over = $rdRemain + 2
      $overBody = (@{ returnType = 'DEFECT'; factoryId = $rdFactory; warehouseId = $rdWh; sourceDeliveryId = $rdId
          returnDate = (Get-Date -Format 'yyyy-MM-dd'); remark = 'audit-fix verify F2-2 over'; chargeFlag = 0; items = @()
          products = @(@{ productId = $rdMaster; productMasterId = $rdMaster; productName = 'audit-fix'; quantity = $over; qualityType = 'A' }) } | ConvertTo-Json -Depth 6)
      $oc = CallPost "$api/outsource/return-order" $h $overBody
      $overId = I (SqlOne "SELECT IFNULL(MAX(id),0) FROM outsource_return_order WHERE remark='audit-fix verify F2-2 over';")
      Write-Output ('  over return (qty=' + $over + ' vs delivered ' + $rdQty + ') create=' + (CodeOf $oc) + ' id=' + $overId)
      if ($overId -gt 0) {
        $oa = CallPut "$api/outsource/return-order/$overId/audit" $h
        Write-Output ('  audit -> code=' + (CodeOf $oa) + ' msg=' + (MsgOf $oa))
        Ok ((CodeOf $oa) -ne '200') 'F2-2 return: returning more than delivered is refused at audit'
        Ok ((SqlOne "SELECT status FROM outsource_return_order WHERE id=$overId;") -eq 'DRAFT') 'over return stayed DRAFT'
        Ok ((I (SqlOne "SELECT COUNT(*) FROM warehouse_stock_log;") -eq $logB2) -and (I (SqlOne "SELECT COUNT(*) FROM finance_payable;") -eq $payB2)) 'no stock/payable side effect on the refused return'
        CallPut "$api/outsource/return-order/$overId/cancel" $h | Out-Null
      }
      # control: exactly the remaining qty must be ALLOWED (guards against an always-reject regression)
      if ($rdStock -ge 1) {
        $okBody = (@{ returnType = 'DEFECT'; factoryId = $rdFactory; warehouseId = $rdWh; sourceDeliveryId = $rdId
            returnDate = (Get-Date -Format 'yyyy-MM-dd'); remark = 'audit-fix verify F2-2 within'; chargeFlag = 0; items = @()
            products = @(@{ productId = $rdMaster; productMasterId = $rdMaster; productName = 'audit-fix'; quantity = 1; qualityType = 'A' }) } | ConvertTo-Json -Depth 6)
        $kc = CallPost "$api/outsource/return-order" $h $okBody
        $okId = I (SqlOne "SELECT IFNULL(MAX(id),0) FROM outsource_return_order WHERE remark='audit-fix verify F2-2 within';")
        if ($okId -gt 0) {
          $ka = CallPut "$api/outsource/return-order/$okId/audit" $h
          Write-Output ('  control qty=1 -> audit code=' + (CodeOf $ka) + ' msg=' + (MsgOf $ka))
          Ok ((CodeOf $ka) -eq '200') 'F2-2 return: a return well inside the delivered qty is still accepted'
          if ((CodeOf $ka) -eq '200') { CallPut "$api/outsource/return-order/$okId/un-audit" $h | Out-Null }
          CallPut "$api/outsource/return-order/$okId/cancel" $h | Out-Null
        }
      }
    } else {
      Write-Output '  (return fixtures unusable - F2-2 return case skipped)'
    }
  } else {
    Write-Output '  (no audited delivery - F2-2 return case skipped)'
  }

  # ---------------------------------------------------------------- F2-2 (delivery side)
  Write-Output '--- 6) F2-2 delivery side: cumulative delivery must not exceed the planned qty'
  $pxOrder = I (SqlOne "SELECT op.order_id FROM outsource_order_product op JOIN outsource_order o ON o.id=op.order_id WHERE o.status='PRODUCING' ORDER BY op.id LIMIT 1;")
  $pxRow = I (SqlOne "SELECT id FROM outsource_order_product WHERE order_id=$pxOrder ORDER BY id LIMIT 1;")
  $pxMaster = I (SqlOne "SELECT product_id FROM outsource_order_product WHERE id=$pxRow;")
  $pxPlanned = D (SqlOne "SELECT quantity FROM outsource_order_product WHERE id=$pxRow;")
  $pxDelivered = D (SqlOne "SELECT IFNULL(SUM(quantity),0) FROM outsource_order_delivery WHERE order_id=$pxOrder AND status='AUDITED';")
  $pxWh = I (SqlOne "SELECT IFNULL(MAX(warehouse_id),0) FROM outsource_order_delivery WHERE order_id=$pxOrder;")
  Write-Output ("  delivery fixtures: order=$pxOrder row=$pxRow master=$pxMaster planned=" + $pxPlanned + " delivered=" + $pxDelivered + " wh=$pxWh")
  if (($pxOrder -gt 0) -and ($pxMaster -gt 0)) {
    $logB3 = I (SqlOne "SELECT COUNT(*) FROM warehouse_stock_log;")
    $payB3 = I (SqlOne "SELECT COUNT(*) FROM finance_payable;")
    $dlBody = (@{ orderId = $pxOrder; productId = $pxRow; productMasterId = $pxMaster; quantity = 1
        deliveryDate = (Get-Date -Format 'yyyy-MM-dd'); warehouseId = $pxWh; aQty = 1; bQty = 0; cQty = 0; defectQty = 0
        remark = 'audit-fix verify F2-2 delivery' }) | ConvertTo-Json -Depth 6
    $dl = CallPost "$api/outsource/order-delivery" $h $dlBody
    $dlId = I $dl.data.id
    if ($dlId -le 0) { $dlId = I (SqlOne "SELECT IFNULL(MAX(id),0) FROM outsource_order_delivery WHERE remark='audit-fix verify F2-2 delivery';") }
    Write-Output ('  extra delivery on a fully delivered order create=' + (CodeOf $dl) + ' id=' + $dlId)
    if ($dlId -gt 0) {
      $dla = CallPut "$api/outsource/order-delivery/$dlId/audit" $h
      Write-Output ('  audit -> code=' + (CodeOf $dla) + ' msg=' + (MsgOf $dla))
      Ok ((CodeOf $dla) -ne '200') 'F2-2 delivery: over-planned delivery refused at audit'
      Ok ((SqlOne "SELECT status FROM outsource_order_delivery WHERE id=$dlId;") -eq 'DRAFT') 'delivery stayed DRAFT'
      Ok ((I (SqlOne "SELECT COUNT(*) FROM warehouse_stock_log;") -eq $logB3) -and (I (SqlOne "SELECT COUNT(*) FROM finance_payable;") -eq $payB3)) 'no stock/payable side effect on the refused delivery'
    }
    # control: un-audit + re-audit an existing delivery -> exactly planned/planned must still be allowed
    $fullD = I (SqlOne "SELECT id FROM outsource_order_delivery WHERE order_id=$pxOrder AND status='AUDITED' ORDER BY id DESC LIMIT 1;")
    if ($fullD -gt 0) {
      $dq = D (SqlOne "SELECT quantity FROM outsource_order_delivery WHERE id=$fullD;")
      CallPut "$api/outsource/order-delivery/$fullD/un-audit" $h | Out-Null
      Start-Sleep -Milliseconds 700
      $dback = CallPut "$api/outsource/order-delivery/$fullD/audit" $h
      Write-Output ('  control un-audit/re-audit id=' + $fullD + ' qty=' + $dq + ' -> code=' + (CodeOf $dback) + ' msg=' + (MsgOf $dback))
      Ok ((CodeOf $dback) -eq '200') 'F2-2 delivery: re-auditing a delivery that brings the order exactly to plan is still allowed (no self-count)'
      Ok ((SqlOne "SELECT status FROM outsource_order_delivery WHERE id=$fullD;") -eq 'AUDITED') 'control delivery back to AUDITED'
    }
  } else {
    Write-Output '  (no producing order - F2-2 delivery case skipped)'
  }

  # ---------------------------------------------------------------- F1-3
  Write-Output '--- 7) F1-3: the supplier must be the one that owns the source purchase order'
  $otherSup = I (SqlOne "SELECT id FROM supplier WHERE id<>$supId ORDER BY id LIMIT 1;")
  if ($otherSup -gt 0) {
    $supBody = (@{ supplierId = $otherSup; purchaseOrderId = $poId; warehouseOutId = $whId; warehouseInId = $whId
        exchangeDate = (Get-Date -Format 'yyyy-MM-dd'); remark = 'audit-fix verify F1-3'
        items = @(@{ purchaseOrderItemId = $poiId; productId = $prodId; qualityType = 'DEFECT'; quantity = 1; unitPrice = $price
                     inQuantity = 1; inQualityType = 'A'; inUnitPrice = $price }) } | ConvertTo-Json -Depth 6)
    $csup = CallPost "$api/inventory/purchase-exchange" $h $supBody
    Write-Output ('  supplier ' + $otherSup + ' (PO owner is ' + $supId + ') -> code=' + (CodeOf $csup) + ' msg=' + (MsgOf $csup))
    Ok ((CodeOf $csup) -ne '200') 'F1-3: a supplier that does not own the purchase order is rejected'
    Ok ((SqlOne "SELECT COUNT(*) FROM purchase_exchange WHERE remark='audit-fix verify F1-3';") -eq '0') 'no draft was created for the mismatched supplier'
  }
} finally {
  # ---------------------------------------------------------------- cleanup (revert everything)
  Write-Output '--- cleanup: revert test documents and assert stock is back to baseline'
  if ($xid -gt 0) {
    CallPut "$api/inventory/purchase-exchange/$xid/un-audit" $h | Out-Null
    CallPut "$api/inventory/purchase-exchange/$xid/cancel" $h | Out-Null
  }
  if ($sxid -gt 0) {
    CallPut "$api/sale/exchange/$sxid/un-audit" $h | Out-Null
    CallPut "$api/sale/exchange/$sxid/cancel" $h | Out-Null
  }
  if ($rtId -gt 0) { CallPut "$api/outsource/return-order/$rtId/cancel" $h | Out-Null }
  # self-cleaning: cancel any drafts this script left behind on earlier runs (id was not returned by POST)
  foreach ($rid in @(Sql "SELECT id FROM outsource_return_order WHERE remark LIKE 'audit-fix verify%' AND status='DRAFT';")) {
    if (([string]$rid).Trim() -ne '') { CallPut ("$api/outsource/return-order/" + ([string]$rid).Trim() + '/cancel') $h | Out-Null }
  }
  # self-cleaning: drafts of the over-planned delivery test (DELETE only works on drafts)
  foreach ($did in @(Sql "SELECT id FROM outsource_order_delivery WHERE remark='audit-fix verify F2-2 delivery' AND status='DRAFT';")) {
    if (([string]$did).Trim() -ne '') {
      try { Invoke-RestMethod -Uri ("$api/outsource/order-delivery/" + ([string]$did).Trim()) -Method Delete -Headers $h | Out-Null } catch { }
    }
  }
  if ($rcId -gt 0) {
    CallPut "$api/inventory/reclassify/$rcId/un-audit" $h | Out-Null
    CallPut "$api/inventory/reclassify/$rcId/cancel" $h | Out-Null
  }
  foreach ($cid in @(Sql "SELECT id FROM product_reclassify WHERE remark='audit-fix verify seed DEFECT' AND status='DRAFT';")) {
    if (([string]$cid).Trim() -ne '') { CallPut ("$api/inventory/reclassify/" + ([string]$cid).Trim() + '/cancel') $h | Out-Null }
  }
  $aNow = D (SqlOne "SELECT IFNULL(quantity,0) FROM warehouse_stock WHERE warehouse_id=$whId AND product_id=$prodId AND quality_type='A';")
  $dNow = D (SqlOne "SELECT IFNULL(quantity,0) FROM warehouse_stock WHERE warehouse_id=$whId AND product_id=$prodId AND quality_type='DEFECT';")
  Write-Output ("  stock A: $aBase -> $aNow ; DEFECT: $dBase -> $dNow")
  Ok (($aNow -eq $aBase) -and ($dNow -eq $dBase)) 'stock restored to the pre-test baseline'
  Ok ((SqlOne "SELECT COUNT(*) FROM purchase_exchange WHERE id=$xid AND status='CANCELLED';") -eq '1') 'test exchange doc left as CANCELLED (trace kept)'
  if ($xid -gt 0) {
    Ok ((SqlOne "SELECT COUNT(*) FROM finance_payable WHERE source_bill_no='$xcode' AND status<>'CANCELLED';") -eq '0') 'no active ledger left for the test doc'
  }
  $inv = SqlOne "SELECT COUNT(*) FROM finance_payable WHERE IFNULL(amount,0) <> IFNULL(paid_amount,0) + IFNULL(unpaid_amount,0);"
  Ok ($inv -eq '0') 'payable invariant intact (amount = paid + unpaid) on all rows'
}

Write-Output ''
if ($fail -eq 0) { Write-Output 'RESULT PASS review fixes (F1-1 / F1-2 / F1-3 / F1-4 / F2-1 / F2-2)' } else { Write-Output ('RESULT FAIL review fixes, failures=' + $fail); exit 1 }
