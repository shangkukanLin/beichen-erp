# Guard (2026-09-29, permanent) for the user rule "报损需要走财务流程".
# ASCII ONLY on purpose (no BOM in this file; PS 5.1 would read Chinese literals as GBK).
# Runs against the REAL API + DB end to end:
#   INTERNAL liable party -> loss expense bill (finance_expense, type=LOSS, account NULL = cashless, AUDITED, no cashflow)
#   SUPPLIER liable party -> claim receivable (finance_receivable, subject_type=SUPPLIER, UNSETTLED)
#   un-audit -> symmetric reverse (expense CANCELLED / receivable CANCELLED + amount 0)
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
EnsureLogin
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
function SqlOne([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -N -B -e $q 2>$null
  $v = (@($o) | Select-Object -First 1)
  if ($null -eq $v) { return '' }
  return ("$v").Trim()
}
function ApiCall([string]$method, [string]$path, [string]$body = '') {
  $mb = B64 $method; $pb = B64 $path; $bb = B64 $body
  $js = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const M=T('$mb'),P=T('$pb'),B=T('$bb');const t=localStorage.getItem('beichen_erp_token')||'';const init={method:M,headers:{'Content-Type':'application/json',satoken:t}};if(B)init.body=B;return fetch('/api'+P,init).then(r=>r.text()).then(x=>x).catch(e=>'FETCHERR:'+e)})()"
  return (EvalJs $js)
}

$st = SqlOne "SELECT CONCAT(warehouse_id,'|',product_id,'|',quality_type) FROM warehouse_stock WHERE product_id IS NOT NULL AND quantity >= 5 ORDER BY quantity DESC LIMIT 1"
$wh = ($st -split '\|')[0]; $pr = ($st -split '\|')[1]; $qt = ($st -split '\|')[2]
$sup = SqlOne "SELECT id FROM supplier ORDER BY id LIMIT 1"
# safety (per repo convention): "the bill created by THIS run" must be proven by an id boundary
$idBefore = [int](SqlOne 'SELECT IFNULL(MAX(id),0) FROM inventory_stock_loss')
Write-Host ('[BASE] warehouse=' + $wh + ' product=' + $pr + ' quality=' + $qt + ' supplier=' + $sup)
Ok (($wh -ne '') -and ($pr -ne '')) 'fixture: a warehouse with product stock resolved'
Ok ($sup -ne '') 'fixture: a supplier resolved'
$flowBefore = [int](SqlOne 'SELECT COUNT(*) FROM finance_cashflow')

Write-Host '--- LEG 1) INTERNAL liable party -> loss expense (cashless) ---'
$b1 = '{"loss":{"warehouseId":' + $wh + ',"lossDate":"2026-09-29","lossReason":"DAMAGE","remark":"probe-internal","liableParty":"INTERNAL"},"items":[{"productId":' + $pr + ',"qualityType":"' + $qt + '","quantity":1,"unitPrice":10}]}'
$resp1 = ApiCall 'POST' '/inventory/stock-loss' $b1
Write-Host ('create: ' + $resp1)
Ok ($resp1 -match '"code":200') 'LEG1 create accepted (code 200)'
$id1 = SqlOne 'SELECT IFNULL(MAX(id),0) FROM inventory_stock_loss'
Ok ([int]$id1 -gt $idBefore) ('LEG1 created a NEW bill (id ' + $id1 + ' > before ' + $idBefore + ')')
$code1 = SqlOne ("SELECT code FROM inventory_stock_loss WHERE id=" + $id1)
Write-Host ('created BS id=' + $id1 + ' code=' + $code1 + ' liable=' + (SqlOne ("SELECT liable_party FROM inventory_stock_loss WHERE id=" + $id1)))
Ok ((SqlOne ("SELECT liable_party FROM inventory_stock_loss WHERE id=" + $id1)) -eq 'INTERNAL') 'loss carries liable_party=INTERNAL'
Write-Host ('audit: ' + (ApiCall 'PUT' ('/inventory/stock-loss/' + $id1 + '/audit')))
Write-Host ('expense: ' + (SqlOne ("SELECT CONCAT(expense_no,'|',expense_type,'|',IFNULL(account_id,'NULL'),'|',status,'|',amount) FROM finance_expense WHERE source_bill_type='INVENTORY_STOCK_LOSS' AND source_id=" + $id1)))
Ok ((SqlOne ("SELECT COUNT(*) FROM finance_expense WHERE source_bill_type='INVENTORY_STOCK_LOSS' AND source_id=" + $id1)) -eq '1') 'audit created exactly ONE loss expense'
Ok ((SqlOne ("SELECT expense_type FROM finance_expense WHERE source_bill_type='INVENTORY_STOCK_LOSS' AND source_id=" + $id1)) -eq 'LOSS') 'expense type = LOSS'
Ok ((SqlOne ("SELECT IFNULL(account_id,'NULL') FROM finance_expense WHERE source_bill_type='INVENTORY_STOCK_LOSS' AND source_id=" + $id1)) -eq 'NULL') 'expense account is NULL (cashless: no account deducted)'
Ok ((SqlOne ("SELECT status FROM finance_expense WHERE source_bill_type='INVENTORY_STOCK_LOSS' AND source_id=" + $id1)) -eq 'AUDITED') 'expense auto-audited'
Ok ((SqlOne ("SELECT amount FROM finance_expense WHERE source_bill_type='INVENTORY_STOCK_LOSS' AND source_id=" + $id1)) -match '^10') 'expense amount = loss total (10)'
Ok (([int](SqlOne 'SELECT COUNT(*) FROM finance_cashflow')) -eq $flowBefore) 'cashless: NO cashflow row written'
Write-Host ('un-audit: ' + (ApiCall 'PUT' ('/inventory/stock-loss/' + $id1 + '/un-audit')))
Ok ((SqlOne ("SELECT status FROM finance_expense WHERE source_bill_type='INVENTORY_STOCK_LOSS' AND source_id=" + $id1)) -eq 'CANCELLED') 'un-audit cancelled the loss expense (symmetric)'

Write-Host '--- LEG 2) SUPPLIER liable party -> claim receivable ---'
$b2 = '{"loss":{"warehouseId":' + $wh + ',"lossDate":"2026-09-29","lossReason":"QUALITY","remark":"probe-supplier","liableParty":"SUPPLIER","liableSupplierId":' + $sup + '},"items":[{"productId":' + $pr + ',"qualityType":"' + $qt + '","quantity":1,"unitPrice":20}]}'
$resp2 = ApiCall 'POST' '/inventory/stock-loss' $b2
Write-Host ('create: ' + $resp2)
Ok ($resp2 -match '"code":200') 'LEG2 create accepted (code 200)'
$id2 = SqlOne 'SELECT IFNULL(MAX(id),0) FROM inventory_stock_loss'
Ok ([int]$id2 -gt [int]$id1) ('LEG2 created a NEW bill (id ' + $id2 + ' > LEG1 ' + $id1 + ')')
$code2 = SqlOne ("SELECT code FROM inventory_stock_loss WHERE id=" + $id2)
Write-Host ('created BS id=' + $id2 + ' code=' + $code2 + ' liable=' + (SqlOne ("SELECT CONCAT(liable_party,'/',IFNULL(liable_supplier_name,'NULL')) FROM inventory_stock_loss WHERE id=" + $id2)))
Ok ((SqlOne ("SELECT liable_party FROM inventory_stock_loss WHERE id=" + $id2)) -eq 'SUPPLIER') 'loss carries liable_party=SUPPLIER'
Ok ((SqlOne ("SELECT IFNULL(liable_supplier_name,'') FROM inventory_stock_loss WHERE id=" + $id2)) -ne '') 'liable supplier name snapshotted'
Write-Host ('audit: ' + (ApiCall 'PUT' ('/inventory/stock-loss/' + $id2 + '/audit')))
Write-Host ('receivable: ' + (SqlOne ("SELECT CONCAT(bill_no,'|',subject_type,'|',amount,'|',status,'|',source_bill_type) FROM finance_receivable WHERE bill_no='" + $code2 + "'")))
Ok ((SqlOne ("SELECT COUNT(*) FROM finance_receivable WHERE bill_no='" + $code2 + "'")) -eq '1') 'audit created exactly ONE claim receivable'
Ok ((SqlOne ("SELECT subject_type FROM finance_receivable WHERE bill_no='" + $code2 + "'")) -eq 'SUPPLIER') 'receivable subject_type = SUPPLIER (claim on the supplier)'
Ok ((SqlOne ("SELECT source_bill_type FROM finance_receivable WHERE bill_no='" + $code2 + "'")) -eq 'INVENTORY_STOCK_LOSS') 'receivable source = INVENTORY_STOCK_LOSS'
Ok ((SqlOne ("SELECT amount FROM finance_receivable WHERE bill_no='" + $code2 + "'")) -match '^20') 'receivable amount = loss total (20)'
Write-Host ('un-audit: ' + (ApiCall 'PUT' ('/inventory/stock-loss/' + $id2 + '/un-audit')))
Ok ((SqlOne ("SELECT status FROM finance_receivable WHERE bill_no='" + $code2 + "'")) -eq 'CANCELLED') 'un-audit reversed the claim receivable'
Ok ((SqlOne ("SELECT amount FROM finance_receivable WHERE bill_no='" + $code2 + "'")) -match '^0') 'cancelled receivable amount zeroed (I29)'

Write-Host '--- LEG 1b) OUTSOURCE material loss (internal) -> loss expense ---'
$mst = SqlOne "SELECT CONCAT(warehouse_id,'|',material_id) FROM warehouse_stock WHERE material_id IS NOT NULL AND quantity >= 5 ORDER BY quantity DESC LIMIT 1"
$mwh = ($mst -split '\|')[0]; $mid = ($mst -split '\|')[1]
Ok (($mwh -ne '') -and ($mid -ne '')) 'fixture: a warehouse with material stock resolved'
$idBefore2 = [int](SqlOne 'SELECT IFNULL(MAX(id),0) FROM outsource_stock_loss')
$b3 = '{"loss":{"warehouseId":' + $mwh + ',"lossDate":"2026-09-29","lossReason":"OTHER","remark":"probe-outsource-internal","liableParty":"INTERNAL"},"items":[{"materialId":' + $mid + ',"quantity":1,"unitPrice":30}]}'
$resp3 = ApiCall 'POST' '/outsource/stock-loss' $b3
Write-Host ('create: ' + $resp3)
Ok ($resp3 -match '"code":200') 'LEG1b create accepted (code 200)'
$id3 = SqlOne 'SELECT IFNULL(MAX(id),0) FROM outsource_stock_loss'
Ok ([int]$id3 -gt $idBefore2) ('LEG1b created a NEW bill (id ' + $id3 + ' > before ' + $idBefore2 + ')')
Write-Host ('audit: ' + (ApiCall 'PUT' ('/outsource/stock-loss/' + $id3 + '/audit')))
Write-Host ('expense: ' + (SqlOne ("SELECT CONCAT(expense_no,'|',expense_type,'|',IFNULL(account_id,'NULL'),'|',status,'|',amount) FROM finance_expense WHERE source_bill_type='OUTSOURCE_STOCK_LOSS' AND source_id=" + $id3)))
Ok ((SqlOne ("SELECT COUNT(*) FROM finance_expense WHERE source_bill_type='OUTSOURCE_STOCK_LOSS' AND source_id=" + $id3)) -eq '1') 'outsource audit created ONE loss expense'
Ok ((SqlOne ("SELECT expense_type FROM finance_expense WHERE source_bill_type='OUTSOURCE_STOCK_LOSS' AND source_id=" + $id3)) -eq 'LOSS') 'outsource expense type = LOSS'
Ok ((SqlOne ("SELECT IFNULL(account_id,'NULL') FROM finance_expense WHERE source_bill_type='OUTSOURCE_STOCK_LOSS' AND source_id=" + $id3)) -eq 'NULL') 'outsource expense account NULL (cashless)'
Ok ((SqlOne ("SELECT amount FROM finance_expense WHERE source_bill_type='OUTSOURCE_STOCK_LOSS' AND source_id=" + $id3)) -match '^30') 'outsource expense amount = loss total (30)'
Write-Host ('un-audit: ' + (ApiCall 'PUT' ('/outsource/stock-loss/' + $id3 + '/un-audit')))
Ok ((SqlOne ("SELECT status FROM finance_expense WHERE source_bill_type='OUTSOURCE_STOCK_LOSS' AND source_id=" + $id3)) -eq 'CANCELLED') 'outsource un-audit cancelled the loss expense'

Write-Host '--- LEG 3) pages render (new controls, no JS/API errors) ---'
foreach ($p in @('/inventory/stock-loss/add', '/outsource/stock-loss/add', ('/inventory/stock-loss/detail/' + $id2), ('/outsource/stock-loss/detail/' + $id2))) {
  Open $p 3000
  WatchErrors
  ClearErrs | Out-Null
  Start-Sleep -Milliseconds 1400
  Write-Host ($p + ' errs=' + (Errs))
  Ok ((Errs) -eq '[]') ($p + ' renders with no JS/API errors')
}
Summary 'probe stock-loss finance'
