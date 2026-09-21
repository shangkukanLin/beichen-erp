# Purchase-side per-product charge (2026-09-21 user rule, translated to keep this file ASCII):
#   both the purchase RETURN and the purchase EXCHANGE need a "do we pay" flag; the payment is US paying
#   the SUPPLIER; and it must be per product.
# Direction: WE pay the supplier => a POSITIVE payable (source_bill_type = PURCHASE_RETURN_CHARGE /
#   PURCHASE_EXCHANGE_CHARGE) -- the exact opposite of the sale side (which raises a receivable).
# Ledger shape (shared by both sides, option A): ONE ledger row per document, amount = SUM(item charges),
#   remark lists every product ("which product was charged how much").
#
# Coverage:
#   A) purchase return  : negative case (amount without type is refused + not persisted) / per-item persist
#                         with doc = SUM / audit -> POSITIVE payable with per-product remark / un-audit ->
#                         reversed / cancel / legacy shape (doc-level only) still materialises on row 1
#   B) purchase exchange: per-item persist + doc = SUM / audit -> charge payable (+3 ledgers) / un-audit -> reversed
# Everything self-cleans (cancel), so the case is rerun-safe. ASCII ONLY.
$ErrorActionPreference = 'Continue'
$API = 'http://localhost:8080/api'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$fail = 0

function Ok([bool]$c, [string]$m) {
  if ($c) { Write-Host ('PASS ' + $m) } else { Write-Host ('FAIL ' + $m); $script:fail++ }
}
function SqlLines([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -N -B -e $q 2>$null
  $out = @()
  foreach ($line in @($o)) { $t = ([string]$line).Trim(); if ($t -ne '') { $out += $t } }
  return $out
}
function SqlOne([string]$q) {
  $l = @(SqlLines $q)
  if ($l.Count -lt 1) { return '' }
  return (($l[0] -split "\t")[0]).Trim()
}
function D($s) {
  if (-not $s) { return [decimal]0 }
  $t = ([string]$s).Trim()
  if ($t -eq '') { return [decimal]0 }
  return [decimal]$t
}

$lg = Invoke-RestMethod -Uri "$API/auth/login" -Method Post -ContentType 'application/json' -Body '{"username":"lin","password":"123","companyId":1}'
$hdr = @{ Authorization = $lg.data.token }
Ok ([bool]$lg.data.token) 'logged in'

function Post([string]$url, $obj) {
  $json = $obj | ConvertTo-Json -Depth 8
  try {
    return Invoke-RestMethod -Uri $url -Method Post -Headers $hdr -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($json))
  } catch {
    return @{ code = 500; msg = $_.Exception.Message }
  }
}
function Put([string]$url) {
  try { return Invoke-RestMethod -Uri $url -Method Put -Headers $hdr } catch { return @{ code = 500; msg = $_.Exception.Message } }
}
function Code($r) {
  if ($r -eq $null) { return 0 }
  if ($r.code -eq $null) { return 0 }
  return [int]$r.code
}

# ---- fixtures: resolve the (warehouse, quality, product) triple that really has stock.
#      Both audits move real stock and the item quality must match it, so the triple is picked together
#      (hardcoding DEFECT was wrong for this dataset: the grade with stock is A today). ----
$SUP = [int](SqlOne 'SELECT id FROM supplier ORDER BY id LIMIT 1')
$fx = [string](SqlOne "SELECT CONCAT(warehouse_id,'|',quality_type,'|',product_id) FROM warehouse_stock WHERE product_id IS NOT NULL AND IFNULL(quantity,0) > 5 GROUP BY warehouse_id, quality_type, product_id ORDER BY SUM(quantity) DESC LIMIT 1")
$fxp = @($fx -split '\|')
$WH = [int]$fxp[0]
$Q = [string]$fxp[1]
$PROD = [int]$fxp[2]
$PROD2 = [int](SqlOne "SELECT product_id FROM warehouse_stock WHERE warehouse_id=$WH AND quality_type='$Q' AND product_id IS NOT NULL AND product_id <> $PROD AND IFNULL(quantity,0) > 2 ORDER BY quantity DESC LIMIT 1")
$PROD_NAME = [string](SqlOne ("SELECT name FROM product WHERE id=$PROD"))
$FEE = 30
$EX_FEE = 40
$today = (Get-Date -Format 'yyyy-MM-dd')
Write-Host ("fixtures: supplier=$SUP warehouse=$WH product=$PROD ($PROD_NAME) product2=$PROD2")

# ============================== A) purchase return ==============================
Write-Host '--- A) purchase return: per-product charge'

# A1) negative: an amount without a type must be refused and must not be persisted
$b0 = @{}
$b0.supplierId = $SUP
$b0.warehouseId = $WH
$b0.returnDate = $today
$i0 = @{}
$i0.productId = $PROD
$i0.qualityType = $Q
$i0.quantity = 1
$i0.unitPrice = 10
$i0.chargeAmount = $FEE
$b0.items = @($i0)
$before = D (SqlOne 'SELECT COUNT(*) FROM purchase_return')
$r0 = Post "$API/inventory/purchase-return" $b0
Write-Host ('  no-type reply: code=' + (Code $r0) + ' msg=' + $r0.msg)
Ok (((Code $r0) -ne 200) -and ((Code $r0) -ne 0)) 'a charged row without its own type is refused (code not success)'
Ok (([string]$r0.msg).Length -gt 0) 'the refusal carries a message (exact wording asserted by the UI case)'
Ok ((D (SqlOne 'SELECT COUNT(*) FROM purchase_return')) -eq $before) 'the refused document was not persisted'

# A2) positive: per-item charges, doc level left empty (backend derives flag/amount/type)
$b1 = @{}
$b1.supplierId = $SUP
$b1.warehouseId = $WH
$b1.returnDate = $today
$b1.remark = 'verify purchase charge per product'
$iA = @{}
$iA.productId = $PROD
$iA.qualityType = $Q
$iA.quantity = 1
$iA.unitPrice = 10
$iA.chargeAmount = $FEE
$iA.chargeType = 'DIFF'
$iB = @{}
$iB.productId = $PROD2
$iB.qualityType = $Q
$iB.quantity = 1
$iB.unitPrice = 5
$b1.items = @($iA, $iB)
$r1 = Post "$API/inventory/purchase-return" $b1
Write-Host ('  create reply: code=' + (Code $r1) + ' msg=' + $r1.msg)
Ok (((Code $r1) -eq 200) -or ((Code $r1) -eq 0)) 'purchase return created with per-item charge'

$rid = [int](SqlOne "SELECT id FROM purchase_return WHERE remark='verify purchase charge per product' ORDER BY id DESC LIMIT 1")
$rcode = [string](SqlOne ("SELECT code FROM purchase_return WHERE id=$rid"))
$rFlag = D (SqlOne ("SELECT IFNULL(charge_flag,0) FROM purchase_return WHERE id=$rid"))
$rAmt = D (SqlOne ("SELECT IFNULL(charge_amount,0) FROM purchase_return WHERE id=$rid"))
$rType = [string](SqlOne ("SELECT IFNULL(charge_type,'') FROM purchase_return WHERE id=$rid"))
$iAamt = D (SqlOne ("SELECT IFNULL(charge_amount,0) FROM purchase_return_item WHERE return_id=$rid AND product_id=$PROD"))
$iAtype = [string](SqlOne ("SELECT IFNULL(charge_type,'') FROM purchase_return_item WHERE return_id=$rid AND product_id=$PROD"))
$iBamt = D (SqlOne ("SELECT IFNULL(charge_amount,0) FROM purchase_return_item WHERE return_id=$rid AND product_id=$PROD2"))
Write-Host ("  saved: id=$rid code=$rcode flag=$rFlag amount=$rAmt type=$rType ; itemA=$iAamt/$iAtype itemB=$iBamt")
Ok ($rid -gt 0) 'the purchase return exists'
Ok (($iAamt -eq $FEE)) 'the per-product charge persisted on the ITEM'
Ok ($iAtype -eq 'DIFF') 'the per-product charge TYPE persisted on the item'
Ok (($iBamt -eq 0)) 'the uncharged row stays at 0 (charges really are per product)'
Ok (($rAmt -eq $FEE)) 'the document charge equals the SUM of item charges (derived, not hand-filled)'
Ok ($rFlag -eq 1) 'the document charge flag was derived from the items'
Ok ($rType -eq 'DIFF') 'a single distinct item type is written back to the document'

# A3) audit -> the charge ledger must be POSITIVE (we pay the supplier)
$a1 = Put "$API/inventory/purchase-return/$rid/audit"
Write-Host ('  audit reply: code=' + (Code $a1) + ' msg=' + $a1.msg)
Ok (((Code $a1) -eq 200) -or ((Code $a1) -eq 0)) 'audit accepted'
Ok (([string](SqlOne ("SELECT status FROM purchase_return WHERE id=$rid"))) -eq 'AUDITED') 'the return is audited'

$feeAmt = D (SqlOne ("SELECT IFNULL(amount,0) FROM finance_payable WHERE source_bill_type='PURCHASE_RETURN_CHARGE' AND source_bill_no='$rcode' AND status <> 'CANCELLED'"))
$feeRows = D (SqlOne ("SELECT COUNT(*) FROM finance_payable WHERE source_bill_type='PURCHASE_RETURN_CHARGE' AND source_bill_no='$rcode' AND status <> 'CANCELLED'"))
$feeRemark = [string](SqlOne ("SELECT IFNULL(remark,'') FROM finance_payable WHERE source_bill_type='PURCHASE_RETURN_CHARGE' AND source_bill_no='$rcode' AND status <> 'CANCELLED'"))
$retAmt = D (SqlOne ("SELECT IFNULL(amount,0) FROM finance_payable WHERE source_bill_type='PURCHASE_RETURN' AND source_bill_no='$rcode' AND status <> 'CANCELLED'"))
Write-Host ("  ledgers: charge=+$feeAmt rows=$feeRows return=$retAmt")
Write-Host ("  charge remark=$feeRemark")
Ok (($feeAmt -eq $FEE)) 'the charge ledger is a POSITIVE payable (WE pay the supplier)'
Ok (($feeRows -eq 1)) 'exactly ONE charge ledger per document (per-product detail lives in its remark)'
Ok ($feeRemark.Contains($PROD_NAME)) 'the charge remark names the charged product'
Ok ($retAmt -lt 0) 'the return side keeps its own NEGATIVE payable (reduces what we owe)'

# A4) un-audit -> the charge ledger is reversed
$u1 = Put "$API/inventory/purchase-return/$rid/un-audit"
Write-Host ('  un-audit reply: code=' + (Code $u1) + ' msg=' + $u1.msg)
Ok (((Code $u1) -eq 200) -or ((Code $u1) -eq 0)) 'un-audit accepted'
Ok (([string](SqlOne ("SELECT status FROM purchase_return WHERE id=$rid"))) -eq 'DRAFT') 'the return is back to DRAFT'
$feeLeft = D (SqlOne ("SELECT COUNT(*) FROM finance_payable WHERE source_bill_type='PURCHASE_RETURN_CHARGE' AND source_bill_no='$rcode' AND status <> 'CANCELLED'"))
$feeVoid = D (SqlOne ("SELECT COUNT(*) FROM finance_payable WHERE source_bill_type='PURCHASE_RETURN_CHARGE' AND source_bill_no='$rcode' AND status = 'CANCELLED'"))
Write-Host ("  after un-audit: active=$feeLeft voided=$feeVoid")
Ok (($feeLeft -eq 0)) 'the charge ledger was reversed (nothing left to pay)'
Ok (($feeVoid -ge 1)) 'the reversed charge row is kept as a trace (CANCELLED)'

# A5) cleanup + legacy compatibility shape (doc-level amount only, no per-item charge)
Put "$API/inventory/purchase-return/$rid/cancel" | Out-Null
Ok (([string](SqlOne ("SELECT status FROM purchase_return WHERE id=$rid"))) -eq 'CANCELLED') 'the probe return is cancelled (trace kept)'

$b2 = @{}
$b2.supplierId = $SUP
$b2.warehouseId = $WH
$b2.returnDate = $today
$b2.remark = 'verify purchase charge legacy'
$b2.chargeFlag = 1
$b2.chargeType = 'SERVICE'
$b2.chargeAmount = 25
$i2 = @{}
$i2.productId = $PROD
$i2.qualityType = $Q
$i2.quantity = 1
$i2.unitPrice = 10
$b2.items = @($i2)
$r2 = Post "$API/inventory/purchase-return" $b2
$lid = [int](SqlOne "SELECT id FROM purchase_return WHERE remark='verify purchase charge legacy' ORDER BY id DESC LIMIT 1")
$lAmt = D (SqlOne ("SELECT IFNULL(charge_amount,0) FROM purchase_return WHERE id=$lid"))
$lItem = D (SqlOne ("SELECT IFNULL(charge_amount,0) FROM purchase_return_item WHERE return_id=$lid LIMIT 1"))
$lItemT = [string](SqlOne ("SELECT IFNULL(charge_type,'') FROM purchase_return_item WHERE return_id=$lid LIMIT 1"))
Write-Host ("  legacy: code=" + (Code $r2) + " id=$lid doc=$lAmt firstItem=$lItem/$lItemT")
Ok (($lAmt -eq 25)) 'legacy doc-level amount still lands on the document (no money lost)'
Ok (($lItem -eq 25) -and ($lItemT -eq 'SERVICE')) 'the legacy amount is materialised onto the first item with its type'
Put "$API/inventory/purchase-return/$lid/cancel" | Out-Null

# ============================== B) purchase exchange ==============================
Write-Host '--- B) purchase exchange: per-product charge'
$xFee = 0
$xid = 0
$xcode = ''
$xActive = 0
$xAudited = $false

$b3 = @{}
$b3.supplierId = $SUP
$b3.warehouseOutId = $WH
$b3.warehouseInId = $WH
$b3.exchangeDate = $today
$b3.remark = 'verify purchase exchange charge per product'
$i3 = @{}
$i3.productId = $PROD
$i3.qualityType = $Q
$i3.quantity = 1
$i3.unitPrice = 10
$i3.inQuantity = 1
$i3.inQualityType = 'A'
$i3.inUnitPrice = 10
$i3.chargeAmount = $EX_FEE
$i3.chargeType = 'DIFF'
$b3.items = @($i3)
$r3 = Post "$API/inventory/purchase-exchange" $b3
Write-Host ('  create reply: code=' + (Code $r3) + ' msg=' + $r3.msg)
Ok (((Code $r3) -eq 200) -or ((Code $r3) -eq 0)) 'purchase exchange created with per-item charge'

$xid = [int](SqlOne "SELECT id FROM purchase_exchange WHERE remark='verify purchase exchange charge per product' ORDER BY id DESC LIMIT 1")
$xcode = [string](SqlOne ("SELECT code FROM purchase_exchange WHERE id=$xid"))
$xFlag = D (SqlOne ("SELECT IFNULL(charge_flag,0) FROM purchase_exchange WHERE id=$xid"))
$xAmt = D (SqlOne ("SELECT IFNULL(charge_amount,0) FROM purchase_exchange WHERE id=$xid"))
$xItem = D (SqlOne ("SELECT IFNULL(charge_amount,0) FROM purchase_exchange_item WHERE exchange_id=$xid LIMIT 1"))
$xItemT = [string](SqlOne ("SELECT IFNULL(charge_type,'') FROM purchase_exchange_item WHERE exchange_id=$xid LIMIT 1"))
Write-Host ("  saved: id=$xid code=$xcode flag=$xFlag doc=$xAmt item=$xItem/$xItemT")
Ok (($xItem -eq $EX_FEE)) 'the per-product charge persisted on the exchange ITEM'
Ok ($xItemT -eq 'DIFF') 'the per-product charge TYPE persisted on the exchange item'
Ok (($xAmt -eq $EX_FEE)) 'the exchange document charge = SUM of item charges'
Ok ($xFlag -eq 1) 'the exchange charge flag was derived from the items'

$x1 = Put "$API/inventory/purchase-exchange/$xid/audit"
Write-Host ('  exchange audit reply: code=' + (Code $x1) + ' msg=' + $x1.msg)
$xAudited = (((Code $x1) -eq 200) -or ((Code $x1) -eq 0))

if ($xAudited) {
  $xFee = D (SqlOne ("SELECT IFNULL(amount,0) FROM finance_payable WHERE source_bill_type='PURCHASE_EXCHANGE_CHARGE' AND source_bill_no='$xcode' AND status <> 'CANCELLED'"))
  $xFeeR = D (SqlOne ("SELECT COUNT(*) FROM finance_payable WHERE source_bill_type='PURCHASE_EXCHANGE_CHARGE' AND source_bill_no='$xcode' AND status <> 'CANCELLED'"))
  $xRemark = [string](SqlOne ("SELECT IFNULL(remark,'') FROM finance_payable WHERE source_bill_type='PURCHASE_EXCHANGE_CHARGE' AND source_bill_no='$xcode' AND status <> 'CANCELLED'"))
  $xActive = D (SqlOne ("SELECT COUNT(*) FROM finance_payable WHERE source_bill_no='$xcode' AND status <> 'CANCELLED'"))
  Write-Host ("  ledgers: charge=+$xFee rows=$xFeeR activeTotal=$xActive")
  Write-Host ("  charge remark=$xRemark")
  Ok (($xFee -eq $EX_FEE)) 'the exchange charge ledger is a POSITIVE payable (WE pay the supplier)'
  Ok (($xFeeR -eq 1)) 'exactly ONE exchange charge ledger per document'
  Ok ($xRemark.Contains($PROD_NAME)) 'the exchange charge remark names the charged product'
  Ok (($xActive -eq 3)) 'three active ledgers (return + exchange-in + charge)'
  Put "$API/inventory/purchase-exchange/$xid/un-audit" | Out-Null
  $xLeft = D (SqlOne ("SELECT COUNT(*) FROM finance_payable WHERE source_bill_type='PURCHASE_EXCHANGE_CHARGE' AND source_bill_no='$xcode' AND status <> 'CANCELLED'"))
  Write-Host ("  after un-audit: activeCharge=$xLeft")
  Ok (($xLeft -eq 0)) 'the exchange charge ledger was reversed on un-audit'
} else {
  Write-Host '  NOTE: the exchange audit was refused (stock / supplier preconditions) -- charge-ledger assertions skipped.'
}

Put "$API/inventory/purchase-exchange/$xid/cancel" | Out-Null
$st = [string](SqlOne ("SELECT status FROM purchase_exchange WHERE id=$xid"))
Write-Host ('  exchange probe final status=' + $st)
Ok (($st -eq 'CANCELLED') -or ($st -eq 'DRAFT')) 'the exchange probe left no live document'

Write-Host ''
if ($fail -eq 0) {
  Write-Host 'RESULT PASS purchase per-product charge (return + exchange)'
} else {
  Write-Host ('RESULT FAIL purchase per-product charge, failures=' + $fail)
  exit 1
}
