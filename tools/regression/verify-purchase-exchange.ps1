# 采购换货单验证（2026-09-18 新增功能，进货业务）
# 口径：同品换货 —— 退回供货商（我方仓按品质出库 + 负向应付冲减） + 换回良品（入我方仓 + 正向应付）。
#       可换量 = 已购 − 已退(TH-) − 已换(CH-)；两条应付台账净额即差价。
# 方式：走真实接口（登录 → 建单 → 审核 → 超量拦截 → 反审核 → 重审），断言库存/流水/台账 + 全表恒等式。
# 前置：若无「不良品(DEFECT)」库存，本脚本先用「品质重分类」把 A 规改成 DEFECT（真实业务路径）。
# ASCII ONLY. 可重复运行（每次新建一张换货单；只增不删）。
$ErrorActionPreference = 'Continue'
$base = 'http://localhost:8080/api'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$pass = 0; $fail = 0
$OUT_WH = 71      # 退回出库仓（我方成品仓）
$IN_WH  = 71      # 换入入库仓（可同仓，仓内按品质分行）
$PO_ID  = 255     # 来源采购单 CG-20260918006（已审核）
$PROD   = 60      # 采购单明细产品「测试产品A11」
$QTY    = 3       # 本次退回/换入数量
$PRICE  = 16      # 采购原价
function Ok($cond, $msg) {
  if ($cond) { Write-Host ('PASS ' + $msg); $script:pass++ } else { Write-Host ('FAIL ' + $msg); $script:fail++ }
}
function SqlLines([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null
  return @((@($o) | Select-Object -Skip 1) | ForEach-Object { "$_" } | Where-Object { $_ -ne '' })
}
function SqlOne([string]$q) { $l = @(SqlLines $q); if ($l.Count -lt 1) { return '' }; return (($l[0] -split "`t")[0]).Trim() }
function D($s) { if (-not $s) { return [decimal]0 }; return [decimal]$s }

Write-Host '--- 0) baseline: finance row invariants (ALL rows incl. CANCELLED)'
$badPay0 = D (SqlOne "SELECT COUNT(*) FROM finance_payable WHERE IFNULL(amount,0) <> IFNULL(paid_amount,0) + IFNULL(unpaid_amount,0)")
Write-Host ('[DB] payables violating amount=paid+unpaid: ' + $badPay0)
Ok (($badPay0 -eq 0)) ('baseline payable invariant holds on all rows (bad=' + $badPay0 + ')')

$lg = Invoke-RestMethod -Uri "$base/auth/login" -Method Post -ContentType 'application/json' -Body '{"username":"lin","password":"123","companyId":1}'
$h = @{ Authorization = $lg.data.token }
Ok ($null -ne $lg.data.token) 'login ok (token acquired)'

Write-Host '--- 1) precondition: DEFECT stock in the return warehouse (reclassify A -> DEFECT if needed)'
$defBefore = D (SqlOne ("SELECT IFNULL(quantity,0) FROM warehouse_stock WHERE warehouse_id=$OUT_WH AND product_id=$PROD AND quality_type='DEFECT'"))
if ($defBefore -lt ($QTY + 2)) {
  # 复用上次运行遗留的草稿（remark 标记），避免每次运行都堆一张草稿单；没有才新建
  $rcId = D (SqlOne ("SELECT IFNULL(MAX(id),0) FROM product_reclassify WHERE status='DRAFT' AND remark LIKE 'purchase-exchange verify%'"))
  if ($rcId -le 0) {
    $rcBody = @{
      warehouseId = $OUT_WH
      reclassifyDate = (Get-Date -Format 'yyyy-MM-dd')
      remark = 'purchase-exchange verify: seed DEFECT stock'
      items = @(@{ productId = $PROD; fromQuality = 'A'; toQuality = 'DEFECT'; quantity = ($QTY + 5) })
    } | ConvertTo-Json -Depth 6
    $rc = Invoke-RestMethod -Uri "$base/inventory/reclassify" -Method Post -Headers $h -ContentType 'application/json' -Body $rcBody
    $rcId = D (SqlOne ("SELECT IFNULL(MAX(id),0) FROM product_reclassify WHERE warehouse_id=$OUT_WH AND remark LIKE 'purchase-exchange verify%'"))
    Write-Host ('  reclassify created id=' + $rcId + ' code=' + $rc.code)
  }
  $ra = Invoke-RestMethod -Uri "$base/inventory/reclassify/$rcId/audit" -Method Put -Headers $h
  Start-Sleep -Milliseconds 600
  $defBefore = D (SqlOne ("SELECT IFNULL(quantity,0) FROM warehouse_stock WHERE warehouse_id=$OUT_WH AND product_id=$PROD AND quality_type='DEFECT'"))
  Write-Host ('  reclassify doc id=' + $rcId + ' audit code=' + $ra.code + ' -> DEFECT stock=' + $defBefore)
}
$aBefore = D (SqlOne ("SELECT IFNULL(quantity,0) FROM warehouse_stock WHERE warehouse_id=$OUT_WH AND product_id=$PROD AND quality_type='A'"))
Ok (($defBefore -ge $QTY)) ('DEFECT stock available for the return (DEFECT=' + $defBefore + ')')
Write-Host ("  stock before: A=" + $aBefore + " DEFECT=" + $defBefore)

Write-Host '--- 2) create a purchase-exchange draft (same-product exchange, linked to the purchase order)'
$poiId = D (SqlOne ("SELECT id FROM purchase_order_item WHERE order_id=$PO_ID AND product_id=$PROD LIMIT 1"))
Write-Host ('  purchase order item anchor id=' + $poiId)
$body = @{
  supplierId = 26; purchaseOrderId = $PO_ID; purchaseOrderCode = 'CG-20260918006'
  warehouseOutId = $OUT_WH; warehouseInId = $IN_WH
  exchangeDate = (Get-Date -Format 'yyyy-MM-dd'); remark = 'verify-purchase-exchange'
  items = @(@{
    purchaseOrderItemId = $poiId
    productId = $PROD; qualityType = 'DEFECT'; quantity = $QTY; unitPrice = $PRICE
    inQuantity = $QTY; inQualityType = 'A'; inUnitPrice = $PRICE
  })
} | ConvertTo-Json -Depth 6
$c1 = Invoke-RestMethod -Uri "$base/inventory/purchase-exchange" -Method Post -Headers $h -ContentType 'application/json' -Body $body
$xid = [int]$c1.data.id
$xcode = [string]$c1.data.code
Write-Host ('  create replied code=' + $c1.code + ' msg=' + $c1.msg + ' id=' + $xid + ' code=' + $xcode)
Write-Host ('  totals: return=' + $c1.data.totalReturnAmount + ' in=' + $c1.data.totalInAmount + ' status=' + $c1.data.status)
Ok (($xid -gt 0)) ('purchase exchange created (id=' + $xid + ')')
Ok ($xcode.StartsWith('CH-')) ('bill no uses the CH- prefix (' + $xcode + ')')
Ok (([string]$c1.data.status -eq 'DRAFT')) 'new document starts as DRAFT'
Ok ((D $c1.data.totalReturnAmount -eq ($QTY * $PRICE)) -and (D $c1.data.totalInAmount -eq ($QTY * $PRICE))) ('both sides totalled from details (ret=' + $c1.data.totalReturnAmount + ' in=' + $c1.data.totalInAmount + ')')

Write-Host '--- 3) audit -> return OUT + exchange IN + two payable ledgers'
$a1 = Invoke-RestMethod -Uri "$base/inventory/purchase-exchange/$xid/audit" -Method Put -Headers $h
Start-Sleep -Milliseconds 800
Ok (($a1.code -eq 200) -or ($a1.code -eq 0)) ('audit accepted (code=' + $a1.code + ' msg=' + $a1.msg + ')')
$st1 = SqlOne ("SELECT status FROM purchase_exchange WHERE id=$xid")
Ok (($st1 -eq 'AUDITED')) ('document is AUDITED (status=' + $st1 + ')')
$defAfter = D (SqlOne ("SELECT IFNULL(quantity,0) FROM warehouse_stock WHERE warehouse_id=$OUT_WH AND product_id=$PROD AND quality_type='DEFECT'"))
$aAfter = D (SqlOne ("SELECT IFNULL(quantity,0) FROM warehouse_stock WHERE warehouse_id=$OUT_WH AND product_id=$PROD AND quality_type='A'"))
Write-Host ("  stock after: A=" + $aAfter + " DEFECT=" + $defAfter + " (expected A=" + ($aBefore + $QTY) + " DEFECT=" + ($defBefore - $QTY) + ")")
Ok (($defAfter -eq ($defBefore - $QTY))) 'return side deducted from our warehouse (-' + '' + $QTY + ' DEFECT)'
Ok (($aAfter -eq ($aBefore + $QTY))) 'exchange side added to our warehouse (+' + '' + $QTY + ' A)'
$logRows = D (SqlOne ("SELECT COUNT(*) FROM warehouse_stock_log WHERE related_bill_no='$xcode'"))
$logOut = D (SqlOne ("SELECT COUNT(*) FROM warehouse_stock_log WHERE related_bill_no='$xcode' AND change_type='PURCHASE_EXCHANGE_OUT'"))
$logIn = D (SqlOne ("SELECT COUNT(*) FROM warehouse_stock_log WHERE related_bill_no='$xcode' AND change_type='PURCHASE_EXCHANGE_IN'"))
Write-Host ('  stock logs: total=' + $logRows + ' OUT=' + $logOut + ' IN=' + $logIn)
Ok (($logOut -ge 1) -and ($logIn -ge 1)) 'stock log carries both PURCHASE_EXCHANGE_OUT and PURCHASE_EXCHANGE_IN'
$retPay = D (SqlOne ("SELECT amount FROM finance_payable WHERE source_bill_type='PURCHASE_EXCHANGE_RETURN' AND source_bill_no='$xcode' AND status<>'CANCELLED'"))
$inPay = D (SqlOne ("SELECT amount FROM finance_payable WHERE source_bill_type='PURCHASE_EXCHANGE_IN' AND source_bill_no='$xcode' AND status<>'CANCELLED'"))
Write-Host ('  payables: return=' + $retPay + ' (expect -' + ($QTY * $PRICE) + ') ; exchange-in=' + $inPay + ' (expect +' + ($QTY * $PRICE) + ')')
Ok (($retPay -eq (0 - ($QTY * $PRICE)))) 'negative payable created for the return (reduces what we owe)'
Ok (($inPay -eq ($QTY * $PRICE))) 'positive payable created for the exchange-in'
Ok ((D (SqlOne ("SELECT COUNT(*) FROM finance_payable WHERE source_bill_no='$xcode' AND status<>'CANCELLED'")) -eq 2)) 'exactly two payable ledger rows (return + exchange-in)'

Write-Host '--- 4) guard: over-quantity must be rejected (can-exchange = purchased - returned - exchanged)'
$overBody = @{
  supplierId = 26; purchaseOrderId = $PO_ID; warehouseOutId = $OUT_WH; warehouseInId = $IN_WH
  exchangeDate = (Get-Date -Format 'yyyy-MM-dd')
  items = @(@{ purchaseOrderItemId = $poiId; productId = $PROD; qualityType = 'DEFECT'; quantity = 99999; unitPrice = $PRICE; inQuantity = 99999; inQualityType = 'A'; inUnitPrice = $PRICE })
} | ConvertTo-Json -Depth 6
$overMsg = ''
try {
  $o = Invoke-RestMethod -Uri "$base/inventory/purchase-exchange" -Method Post -Headers $h -ContentType 'application/json' -Body $overBody
  $overMsg = [string]$o.msg
} catch {
  $overMsg = [string]$_.ErrorDetails.Message
}
$kw = [string]([char]0x53EF + [char]0x6362 + [char]0x6570 + [char]0x91CF)   # 可换数量
Write-Host ('  server replied: ' + $overMsg)
Ok ($overMsg -like ('*' + $kw + '*')) 'over-quantity rejected with a can-exchange message'

Write-Host '--- 5) un-audit -> symmetric rollback + ledgers voided (I29 口径: amount zeroed)'
$u1 = Invoke-RestMethod -Uri "$base/inventory/purchase-exchange/$xid/un-audit" -Method Put -Headers $h
Start-Sleep -Milliseconds 800
Ok (($u1.code -eq 200) -or ($u1.code -eq 0)) ('un-audit accepted (code=' + $u1.code + ' msg=' + $u1.msg + ')')
$st2 = SqlOne ("SELECT status FROM purchase_exchange WHERE id=$xid")
Ok (($st2 -eq 'DRAFT')) ('document back to DRAFT (status=' + $st2 + ')')
$defBack = D (SqlOne ("SELECT IFNULL(quantity,0) FROM warehouse_stock WHERE warehouse_id=$OUT_WH AND product_id=$PROD AND quality_type='DEFECT'"))
$aBack = D (SqlOne ("SELECT IFNULL(quantity,0) FROM warehouse_stock WHERE warehouse_id=$OUT_WH AND product_id=$PROD AND quality_type='A'"))
Write-Host ("  stock rolled back: A=" + $aBack + " DEFECT=" + $defBack + " (expected A=" + $aBefore + " DEFECT=" + $defBefore + ")")
Ok (($defBack -eq $defBefore)) 'return stock restored'
Ok (($aBack -eq $aBefore)) 'exchange-in stock taken back'
$voidMoney = D (SqlOne ("SELECT COUNT(*) FROM finance_payable WHERE source_bill_no='$xcode' AND status='CANCELLED' AND (IFNULL(amount,0)<>0 OR IFNULL(unpaid_amount,0)<>0)"))
$voidRows = D (SqlOne ("SELECT COUNT(*) FROM finance_payable WHERE source_bill_no='$xcode' AND status='CANCELLED'"))
Write-Host ('  voided ledger rows=' + $voidRows + ' ; carrying money=' + $voidMoney)
Ok (($voidRows -ge 2)) 'both ledger rows voided (trace kept)'
Ok (($voidMoney -eq 0)) 'voided rows carry no money (I29 口径)'

Write-Host '--- 6) re-audit -> fresh active ledgers (D1: new YF- bill_no each audit, old rows stay CANCELLED)'
$a2 = Invoke-RestMethod -Uri "$base/inventory/purchase-exchange/$xid/audit" -Method Put -Headers $h
Start-Sleep -Milliseconds 800
Ok (($a2.code -eq 200) -or ($a2.code -eq 0)) ('re-audit accepted (code=' + $a2.code + ' msg=' + $a2.msg + ')')
$retPay2 = D (SqlOne ("SELECT amount FROM finance_payable WHERE source_bill_type='PURCHASE_EXCHANGE_RETURN' AND source_bill_no='$xcode' AND status<>'CANCELLED'"))
$inPay2 = D (SqlOne ("SELECT amount FROM finance_payable WHERE source_bill_type='PURCHASE_EXCHANGE_IN' AND source_bill_no='$xcode' AND status<>'CANCELLED'"))
Ok (($retPay2 -eq (0 - ($QTY * $PRICE))) -and ($inPay2 -eq ($QTY * $PRICE))) ('active ledgers regenerated (ret=' + $retPay2 + ' in=' + $inPay2 + ')')
$defFinal = D (SqlOne ("SELECT IFNULL(quantity,0) FROM warehouse_stock WHERE warehouse_id=$OUT_WH AND product_id=$PROD AND quality_type='DEFECT'"))
Ok (($defFinal -eq ($defBefore - $QTY))) ('stock re-applied after re-audit (DEFECT=' + $defFinal + ')')

Write-Host '--- 7) final invariants (whole ledger tables, incl. CANCELLED)'
$badPay = D (SqlOne "SELECT COUNT(*) FROM finance_payable WHERE IFNULL(amount,0) <> IFNULL(paid_amount,0) + IFNULL(unpaid_amount,0)")
$zeroCancel = D (SqlOne "SELECT COUNT(*) FROM finance_payable WHERE status='CANCELLED' AND (IFNULL(amount,0)<>0 OR IFNULL(unpaid_amount,0)<>0)")
Write-Host ("[DB] allRowsBad=" + $badPay + " voidedWithMoney=" + $zeroCancel)
Ok (($badPay -eq 0)) 'payable invariant holds on ALL rows after the exchange flows'
Ok (($zeroCancel -eq 0)) 'no voided payable carries money'

Write-Host ''
Write-Host ('[DB] purchase_exchange rows=' + (SqlOne "SELECT COUNT(*) FROM purchase_exchange") + ' ; latest=' + $xcode)
if ($fail -eq 0) { Write-Host ('RESULT PASS purchase-exchange (PASS=' + $pass + ' FAIL=0)') } else { Write-Host ('RESULT FAIL purchase-exchange (PASS=' + $pass + ' FAIL=' + $fail + ')') }
if ($fail -gt 0) { exit 1 }
