# Sale order CASH settlement (2026-09-18) end-to-end check: API + DB cross-check, rerunnable.
# Covers: settle_type=CASH -> auto DRAFT receipt on audit -> receipt audit settles receivable &
#         writes cashflow -> sale un-audit blocked while receipt AUDITED -> receipt un-audit ->
#         sale un-audit cancels the DRAFT receipt automatically -> full rollback; plus a
#         second doc where the sale is un-audited while the receipt is still DRAFT.
# ASCII ONLY (PS 5.1 reads non-BOM files as ANSI). Prereq data is seeded through the UI:
#   ui-e2e-2-master.ps1 (customer/product) + ui-seed-sale.ps1 (warehouse/cash account/stock).
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
function LastId([string]$t) { return [int](SqlOne "SELECT COALESCE(MAX(id),0) FROM $t") }
function StockProd([int]$wh, [int]$prodId) { return SqlOne "SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE warehouse_id=$wh AND product_id=$prodId AND quality_type='A'" }

$lg = Invoke-RestMethod -Uri "$base/auth/login" -Method Post -ContentType 'application/json' -Body '{"username":"lin","password":"123","companyId":1}'
$h = @{ Authorization = $lg.data.token }
function ApiRaw($method, $path, [string]$json) {
  try {
    if ($json) { return Invoke-RestMethod -Uri "$base$path" -Method $method -Headers $h -ContentType 'application/json' -Body $json }
    return Invoke-RestMethod -Uri "$base$path" -Method $method -Headers $h
  } catch { return $_.ErrorDetails.Message }
}
function Api($method, $path, $body) {
  if ($body) { return ApiRaw $method $path ($body | ConvertTo-Json -Depth 8 -Compress) }
  return ApiRaw $method $path ''
}
function Msg($r) { if ($null -eq $r) { return '<null>' }; if ($r -is [string]) { return $r }; return ('code=' + $r.code + ' ' + $r.msg) }

# ---------- resolve seeded ids (data-driven, ASCII-only SQL) ----------
$custId = [int](SqlOne "SELECT COALESCE(MAX(id),0) FROM customer")
$prodId = [int](SqlOne "SELECT COALESCE(MAX(id),0) FROM product")
# warehouse = the finished warehouse that actually holds this product's stock
# (there may be several finished warehouses; pick the one with stock, not MAX(id))
$whId   = [int](SqlOne "SELECT COALESCE(MIN(warehouse_id),0) FROM warehouse_stock WHERE product_id=$prodId AND quantity > 0")
$accId  = [int](SqlOne "SELECT COALESCE(MIN(id),0) FROM finance_account WHERE account_type='cash' AND status=1")
$today  = (Get-Date -Format 'yyyy-MM-dd')
Write-Host ('[SEED] customer=' + $custId + ' product=' + $prodId + ' warehouse=' + $whId + ' cashAccount=' + $accId)
if ($custId -le 0 -or $prodId -le 0 -or $whId -le 0 -or $accId -le 0) {
  Write-Host 'FAIL prerequisites missing (run ui-e2e-2-master.ps1 then ui-seed-sale.ps1 first)'
  exit 1
}

function NewCashOrder([string]$remark) {
  $json = '{"order":{"customerId":' + $custId + ',"warehouseId":' + $whId + ',"orderDate":"' + $today +
          '","taxIncluded":0,"taxRate":0,"settleType":"CASH","settleAccountId":' + $accId +
          ',"remark":"' + $remark + '"},"items":[{"productId":' + $prodId + ',"quantity":1,"unitPrice":10,"qualityType":"A"}]}'
  $r = ApiRaw 'POST' '/inventory/sale' $json
  $id = LastId 'sale_order'
  return @{ id = $id; code = (SqlOne "SELECT code FROM sale_order WHERE id=$id"); msg = (Msg $r) }
}

# =====================================================================
Step 'S1 create CASH sale order (settle_type=CASH + cash account)'
$stock0 = D (StockProd $whId $prodId)
Write-Host ('stock before = ' + $stock0)
$o1 = NewCashOrder 'AUDIT-CASH-1'
Write-Host ('create -> ' + $o1.msg + '  id=' + $o1.id + ' code=' + $o1.code)
Ok ($o1.id -gt 0) ('S1 order created id=' + $o1.id)
Ok ((SqlOne "SELECT settle_type FROM sale_order WHERE id=$($o1.id)") -eq 'CASH') 'S1 settle_type persisted as CASH'
Ok ((D (SqlOne "SELECT settle_account_id FROM sale_order WHERE id=$($o1.id)")) -eq $accId) 'S1 settle_account_id persisted'
Ok ((D (SqlOne "SELECT total_amount FROM sale_order WHERE id=$($o1.id)")) -eq 10) 'S1 total_amount = 10'

# negative: CASH without account must be rejected
$bad = ApiRaw 'POST' '/inventory/sale' ('{"order":{"customerId":' + $custId + ',"warehouseId":' + $whId + ',"orderDate":"' + $today + '","settleType":"CASH"},"items":[{"productId":' + $prodId + ',"quantity":1,"unitPrice":10,"qualityType":"A"}]}')
Write-Host ('negative (CASH w/o account) -> ' + (Msg $bad))
Ok ((Msg $bad) -match 'account' -or (Msg $bad) -match 'code=500') 'S1 negative: CASH without account rejected'

# =====================================================================
Step 'S2 audit CASH sale order -> receivable + AUTO DRAFT receipt'
Ok ((Msg (Api 'PUT' "/inventory/sale/$($o1.id)/audit" $null)) -match 'code=200') 'S2 sale audit ok'
Ok ((SqlOne "SELECT status FROM sale_order WHERE id=$($o1.id)") -eq 'AUDITED') 'S2 sale status AUDITED'
$rcvId = [int](SqlOne "SELECT COALESCE(MAX(id),0) FROM finance_receivable WHERE source_bill_type='SALE_ORDER' AND source_id=$($o1.id)")
Ok ($rcvId -gt 0) ('S2 receivable created id=' + $rcvId)
Ok ((D (SqlOne "SELECT amount FROM finance_receivable WHERE id=$rcvId")) -eq 10) 'S2 receivable amount = 10'
$rid = [int](SqlOne "SELECT COALESCE(MAX(id),0) FROM finance_receipt WHERE source_bill_type='SALE_ORDER' AND source_id=$($o1.id)")
Ok ($rid -gt 0) ('S2 auto draft receipt created id=' + $rid)
Ok ((SqlOne "SELECT status FROM finance_receipt WHERE id=$rid") -eq 'DRAFT') 'S2 receipt status = DRAFT (not audited)'
Ok ((D (SqlOne "SELECT amount FROM finance_receipt WHERE id=$rid")) -eq 10) 'S2 receipt amount = receivable full amount'
Ok ((D (SqlOne "SELECT account_id FROM finance_receipt WHERE id=$rid")) -eq $accId) 'S2 receipt uses the chosen cash account'
Ok ((D (SqlOne "SELECT customer_id FROM finance_receipt WHERE id=$rid")) -eq $custId) 'S2 receipt customer = sale customer'
Ok ($rcvId -gt 0 -and (D (SqlOne "SELECT receivable_id FROM finance_receipt_item WHERE receipt_id=$rid")) -eq $rcvId) 'S2 receipt item points to the receivable'
$rcode = SqlOne "SELECT code FROM finance_receipt WHERE id=$rid"
Write-Host ('receipt code = ' + $rcode)
Ok ((D (StockProd $whId $prodId)) -eq ($stock0 - 1)) 'S2 stock decreased by 1 (sale audit ships goods)'
Ok ((D (SqlOne "SELECT COUNT(*) FROM finance_cashflow WHERE related_bill_no='$rcode'")) -eq 0) 'S2 no cashflow yet (money not received until receipt audited)'

Step 'S2b by-source API'
$bs = Api 'GET' ("/finance/receipt/by-source?sourceBillType=SALE_ORDER&sourceId=" + $o1.id) $null
Ok ($null -ne $bs -and $null -ne $bs.data -and @($bs.data).Count -ge 1) 'S2b by-source returns the auto receipt'

# =====================================================================
Step 'S3 audit the receipt -> settle receivable + cashflow + account balance'
Ok ((Msg (Api 'PUT' "/finance/receipt/$rid/audit" $null)) -match 'code=200') 'S3 receipt audit ok'
Ok ($rid -gt 0 -and (SqlOne "SELECT status FROM finance_receipt WHERE id=$rid") -eq 'AUDITED') 'S3 receipt AUDITED'
Ok ((D (SqlOne "SELECT paid_amount FROM finance_receivable WHERE id=$rcvId")) -eq 10) 'S3 receivable paid = 10'
Ok ($rcvId -gt 0 -and (D (SqlOne "SELECT unpaid_amount FROM finance_receivable WHERE id=$rcvId")) -eq 0) 'S3 receivable unpaid = 0'
Ok ((SqlOne "SELECT status FROM finance_receivable WHERE id=$rcvId") -eq 'SETTLED') 'S3 receivable SETTLED'
Ok ((D (SqlOne "SELECT COALESCE(SUM(income),0) FROM finance_cashflow WHERE related_bill_no='$rcode'")) -eq 10) 'S3 cashflow income = 10 (cash in)'

# =====================================================================
Step 'S4 un-audit sale order while receipt AUDITED -> must be blocked'
$u4 = Msg (Api 'PUT' "/inventory/sale/$($o1.id)/un-audit" $null)
Write-Host ('un-audit sale -> ' + $u4)
Ok ($u4 -notmatch 'code=200') 'S4 sale un-audit blocked while receipt audited'
Ok ((SqlOne "SELECT status FROM sale_order WHERE id=$($o1.id)") -eq 'AUDITED') 'S4 sale still AUDITED (rollback prevented)'

# =====================================================================
Step 'S5 un-audit receipt then sale -> full rollback + draft receipt auto-cancelled'
Ok ((Msg (Api 'PUT' "/finance/receipt/$rid/un-audit" $null)) -match 'code=200') 'S5 receipt un-audit ok'
Ok ((D (SqlOne "SELECT unpaid_amount FROM finance_receivable WHERE id=$rcvId")) -eq 10) 'S5 receivable unpaid restored = 10'
$u5 = Msg (Api 'PUT' "/inventory/sale/$($o1.id)/un-audit" $null)
Write-Host ('un-audit sale -> ' + $u5)
Ok ($u5 -match 'code=200') 'S5 sale un-audit ok after receipt rolled back'
Ok ((SqlOne "SELECT status FROM sale_order WHERE id=$($o1.id)") -eq 'DRAFT') 'S5 sale back to DRAFT'
Ok ((SqlOne "SELECT status FROM finance_receivable WHERE id=$rcvId") -eq 'CANCELLED') 'S5 receivable reversed'
Ok ($rid -gt 0 -and (SqlOne "SELECT status FROM finance_receipt WHERE id=$rid") -eq 'CANCELLED') 'S5 auto receipt auto-cancelled on sale un-audit'
Ok ((D (StockProd $whId $prodId)) -eq $stock0) 'S5 stock restored'

# =====================================================================
Step 'S6 second doc: sale un-audit while receipt still DRAFT'
$o2 = NewCashOrder 'AUDIT-CASH-2'
Write-Host ('create -> ' + $o2.msg + '  id=' + $o2.id)
Ok ((Msg (Api 'PUT' "/inventory/sale/$($o2.id)/audit" $null)) -match 'code=200') 'S6 audit ok'
$rid2 = [int](SqlOne "SELECT COALESCE(MAX(id),0) FROM finance_receipt WHERE source_bill_type='SALE_ORDER' AND source_id=$($o2.id)")
Ok ((SqlOne "SELECT status FROM finance_receipt WHERE id=$rid2") -eq 'DRAFT') 'S6 auto receipt is DRAFT'
$u6 = Msg (Api 'PUT' "/inventory/sale/$($o2.id)/un-audit" $null)
Write-Host ('un-audit sale (receipt still DRAFT) -> ' + $u6)
Ok ($u6 -match 'code=200') 'S6 sale un-audit allowed (draft receipt does not block)'
Ok ((SqlOne "SELECT status FROM finance_receipt WHERE id=$rid2") -eq 'CANCELLED') 'S6 draft receipt auto-cancelled'
Ok ((SqlOne "SELECT status FROM sale_order WHERE id=$($o2.id)") -eq 'DRAFT') 'S6 sale back to DRAFT'
Ok ((D (StockProd $whId $prodId)) -eq $stock0) 'S6 stock restored'

# =====================================================================
Step 'S7 cleanup (cancel the two draft orders) + reconciliation'
Ok ((Msg (Api 'PUT' "/inventory/sale/$($o1.id)/cancel" $null)) -match 'code=200') 'S7 cancel order 1'
Ok ((Msg (Api 'PUT' "/inventory/sale/$($o2.id)/cancel" $null)) -match 'code=200') 'S7 cancel order 2'
$diffP = SqlOne "SELECT COUNT(*) FROM warehouse_stock s LEFT JOIN (SELECT warehouse_id, product_id, quality_type, SUM(change_quantity) qty FROM warehouse_stock_log WHERE product_id IS NOT NULL GROUP BY warehouse_id, product_id, quality_type) l ON l.warehouse_id=s.warehouse_id AND l.product_id=s.product_id AND l.quality_type=s.quality_type WHERE s.product_id IS NOT NULL AND s.quantity <> COALESCE(l.qty,0)"
Ok ((D $diffP) -eq 0) ('S7 product stock vs log diff rows = ' + $diffP)

Write-Host ''
Write-Host ('RESULT ' + $(if ($script:FAIL -eq 0) { 'PASS' } else { 'FAIL' }) + ' sale cash settle  (PASS=' + $script:PASS + ' FAIL=' + $script:FAIL + ')')
if ($script:FAIL -ne 0) { exit 1 }
