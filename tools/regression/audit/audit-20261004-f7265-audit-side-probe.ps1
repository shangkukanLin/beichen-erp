# audit 2026-10-04 - F7-265 AUDIT-SIDE consequence probe (user-approved write experiment)
#
# WHY a direct-SQL draft: F7-265's entry guard now rejects a negative settleAmount at create time, so the API
# can no longer produce the bad draft. But the question this probe answers is different and still open:
#   does the AUDIT path itself have a guard, i.e. can a PRE-EXISTING / imported / hand-edited draft with a
#   negative settle_amount still corrupt money when it is audited?
# Faithful to the finding: it simulates exactly such a draft (same shape as the legacy no-split-rows branch).
#
# Safety: single order, qty 1, one warehouse/product with 999 in stock. Everything created is deleted at the end
# (per-pattern, counted), the stock quant is restored and asserted, and 13 tables are compared before/after.
# ASCII ONLY.
$ErrorActionPreference = 'Continue'
$api = 'http://localhost:8080/api'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$fail = 0
function Ok($m) { Write-Output ("PASS " + $m) }
function Bad($m) { Write-Output ("FAIL " + $m); $script:fail++ }
function SqlRaw([string]$q) { return @(& $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -N -B -e $q 2>$null) }
function SqlOne([string]$q) { $l = @(SqlRaw $q); if ($l.Count -lt 1) { return '' }; return "$($l[0])".Trim() }
function SqlExec([string]$q) { & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null | Out-Null }
function Login([string]$u, [string]$p) {
  $b = '{"username":"' + $u + '","password":"' + $p + '","companyId":1}'
  return Invoke-RestMethod -Uri "$api/auth/login" -Method Post -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($b)) -TimeoutSec 20
}
function Put([string]$url, [string]$tok) {
  try { return Invoke-RestMethod -Uri $url -Method Put -Headers @{ Authorization = $tok } -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes('{}')) -TimeoutSec 30 }
  catch { try { $sr = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream()); return ($sr.ReadToEnd() | ConvertFrom-Json) } catch { return [pscustomobject]@{ code = -1; msg = $_.Exception.Message } } }
}
$TABLES = @('sale_order', 'sale_order_item', 'finance_receipt', 'finance_receipt_account', 'finance_receipt_item',
  'finance_receivable', 'finance_settlement', 'finance_cashflow', 'warehouse_stock', 'warehouse_stock_log',
  'finance_bill_item', 'finance_account', 'finance_payable')
function Counts { $r = @(); foreach ($t in $TABLES) { $r += [int](SqlOne ("SELECT COUNT(*) FROM " + $t)) }; return $r }

$la = Login 'lin' '123'
if ($null -eq $la -or [string]$la.code -ne '200') { Write-Output 'FAIL cannot login'; Write-Output 'RESULT F7-265-AUDIT-PROBE FAIL count 1'; exit 1 }
$tok = [string]$la.data.token

$wh = 131; $prod = 147; $acct = 74
$qtyBefore = [int](SqlOne "SELECT quantity FROM warehouse_stock WHERE warehouse_id=$wh AND product_id=$prod AND quality_type='A'")
if ($qtyBefore -lt 5) { Write-Output ("FAIL fixture stock too small (" + $qtyBefore + ")"); Write-Output 'RESULT F7-265-AUDIT-PROBE FAIL count 1'; exit 1 }
$ts = (Get-Date).ToString('HHmmss')
$code = 'XS-AUDITP-' + $ts
$before = Counts
Write-Output ("fixture: wh=$wh product=$prod acct=$acct stock=$qtyBefore ; probe order code=$code")
Write-Output ("counts BEFORE: " + (($TABLES | ForEach-Object { $i = [array]::IndexOf($TABLES, $_); ($_ + '=' + $before[$i]) }) -join ' '))

# ---------- 1) build a PRE-EXISTING bad draft (negative settle_amount, legacy no-split branch) ----------
SqlExec ("INSERT INTO sale_order (code, customer_id, warehouse_id, order_date, status, settle_type, settle_account_id, settle_amount, total_amount, tax_included, tax_amount, remark, create_by, create_by_name, company_id, create_time, update_time) SELECT '" + $code + "', customer_id, " + $wh + ", CURDATE(), 'DRAFT', 'CASH', " + $acct + ", -100.0000, 1.0000, 0, 0.0000, 'F7-265 audit-side probe', create_by, create_by_name, 1, NOW(), NOW() FROM sale_order WHERE id=338")
$oid = [int](SqlOne ("SELECT id FROM sale_order WHERE code='" + $code + "'"))
if ($oid -le 0) { Write-Output 'FAIL cannot create probe draft'; Write-Output 'RESULT F7-265-AUDIT-PROBE FAIL count 1'; exit 1 }
SqlExec ("INSERT INTO sale_order_item (order_id, product_id, quantity, unit_price, amount, quality_type, remark, company_id) SELECT " + $oid + ", " + $prod + ", 1, 1.0000, 1.0000, 'A', 'probe', 1 FROM sale_order_item WHERE order_id=338 LIMIT 1")
Write-Output ("probe order id=$oid (draft, CASH, settle_amount=-100, total=1, qty=1 in stock)")

# ---------- 2) audit it and observe the money legs ----------
$rAudit = Put "$api/inventory/sale/$oid/audit" $tok
Write-Output ("audit -> code=" + $rAudit.code + " msg=" + $rAudit.msg)
# F7-268 regression assertion: bad data must be refused with attribution to the SALE ORDER total,
# never to the auto-generated split row (users never typed that row, so the message is useless).
# !! This file must stay ASCII-only (PS 5.1 reads .ps1 as GBK => Chinese literals become mojibake and
#    the -like pattern silently never matches -- measured 2026-10-04). Needles are built from code points:
#      ben ci shou kuan zong e = "ben ci shou kuan zong e", fen kuan di = "fen kuan di"
$NEEDLE_TOTAL = [string][char]0x672C + [char]0x6B21 + [char]0x6536 + [char]0x6B3E + [char]0x603B + [char]0x989D
$NEEDLE_SPLIT = [string][char]0x5206 + [char]0x6B3E + [char]0x7B2C
$msg = [string]$rAudit.msg
if ($msg.Contains($NEEDLE_SPLIT)) { Bad 'F7-268: still attributing the refusal to the auto-generated split row (misleading)' }
elseif ($msg.Contains($NEEDLE_TOTAL)) { Ok 'F7-268: refused with the right attribution (sale order total named)' }
else { Write-Output '  (note: audit message matched neither needle; F7-268 assertion not counted)'; Write-Output ('  msg codepoints = ' + (($msg.ToCharArray() | ForEach-Object { [int]$_ }) -join ' ')) }
$recv = SqlOne ("SELECT IFNULL(CONCAT(id,'|',bill_no,'|',amount,'|',paid_amount,'|',unpaid_amount,'|',status),'NONE') FROM finance_receivable WHERE source_bill_type='SALE_ORDER' AND source_id=" + $oid)
$receipt = SqlOne ("SELECT IFNULL(CONCAT(id,'|',code,'|',status,'|',amount),'NONE') FROM finance_receipt WHERE source_bill_type='SALE_ORDER' AND source_id=" + $oid + " ORDER BY id DESC LIMIT 1")
Write-Output ("  receivable(id|bill|amount|paid|unpaid|status) = " + $recv)
Write-Output ("  receipt(id|code|status|amount)               = " + $receipt)
$rid = ''
$rcode = ''
if ($receipt -ne 'NONE') { $p = $receipt -split '\|'; $rid = $p[0]; $rcode = $p[1] }
if ($rid -ne '') {
  $accts = (SqlRaw ("SELECT CONCAT(account_id,'=',amount) FROM finance_receipt_account WHERE receipt_id=" + $rid)) -join ' , '
  $items = (SqlRaw ("SELECT CONCAT(this_amount) FROM finance_receipt_item WHERE receipt_id=" + $rid)) -join ' , '
  $flows = (SqlRaw ("SELECT CONCAT(flow_type,' income=',IFNULL(income,'NULL'),' expense=',IFNULL(expense,'NULL')) FROM finance_cashflow WHERE related_bill_no='" + $rcode + "'")) -join ' , '
  $stl = (SqlRaw ("SELECT CONCAT(amount,' ',direction,' ',status) FROM finance_settlement WHERE receipt_payment_id=" + $rid)) -join ' , '
  Write-Output ("  receipt_account rows = " + $accts)
  Write-Output ("  receipt_item settled = " + $items)
  Write-Output ("  cashflow            = " + $flows)
  Write-Output ("  settlement          = " + $stl)
  $ramt = ($receipt -split '\|')[3]
  if ([decimal]$ramt -lt 0) { Bad ('CONSEQUENCE CONFIRMED: audited receipt amount is NEGATIVE (' + $ramt + ') => money legs inverted: receivable increased, negative cashflow') }
  else { Ok ('audit-side guard held: receipt amount is not negative (' + $ramt + ')') }
} else {
  Ok 'no receipt generated (audit refused or produced no cash leg)'
}
$qtyAfterAudit = [int](SqlOne "SELECT quantity FROM warehouse_stock WHERE warehouse_id=$wh AND product_id=$prod AND quality_type='A'")
Write-Output ("  stock after audit = $qtyAfterAudit (was $qtyBefore)")

# ---------- 3) un-audit (should reverse every leg) ----------
$rUn = Put "$api/inventory/sale/$oid/un-audit" $tok
Write-Output ("un-audit -> code=" + $rUn.code + " msg=" + $rUn.msg)
$qtyAfterUn = [int](SqlOne "SELECT quantity FROM warehouse_stock WHERE warehouse_id=$wh AND product_id=$prod AND quality_type='A'")
Write-Output ("  stock after un-audit = $qtyAfterUn")

# ---------- 4) cleanup (per-pattern, then counted) ----------
if ($rcode -ne '') { SqlExec ("DELETE FROM finance_cashflow WHERE related_bill_no='" + $rcode + "'") | Out-Null }
if ($rid -ne '') {
  SqlExec ("DELETE FROM finance_settlement WHERE receipt_payment_id=" + $rid) | Out-Null
  SqlExec ("DELETE FROM finance_receipt_item WHERE receipt_id=" + $rid) | Out-Null
  SqlExec ("DELETE FROM finance_receipt_account WHERE receipt_id=" + $rid) | Out-Null
  SqlExec ("DELETE FROM finance_receipt WHERE id=" + $rid) | Out-Null
}
SqlExec ("DELETE FROM finance_cashflow WHERE related_bill_no='" + $code + "'") | Out-Null
SqlExec ("DELETE FROM finance_receivable WHERE source_bill_type='SALE_ORDER' AND source_id=" + $oid) | Out-Null
SqlExec ("DELETE FROM warehouse_stock_log WHERE related_bill_no='" + $code + "' OR related_bill_id=" + $oid) | Out-Null
SqlExec ("DELETE FROM sale_order_item WHERE order_id=" + $oid) | Out-Null
SqlExec ("DELETE FROM sale_order_settle_account WHERE order_id=" + $oid) | Out-Null
SqlExec ("DELETE FROM sale_order WHERE id=" + $oid) | Out-Null
if ($qtyAfterUn -ne $qtyBefore) {
  SqlExec ("UPDATE warehouse_stock SET quantity=$qtyBefore WHERE warehouse_id=$wh AND product_id=$prod AND quality_type='A'") | Out-Null
  Write-Output ("  (stock quant restored by SQL: $qtyAfterUn -> $qtyBefore)")
}
$qtyFinal = [int](SqlOne "SELECT quantity FROM warehouse_stock WHERE warehouse_id=$wh AND product_id=$prod AND quality_type='A'")
if ($qtyFinal -eq $qtyBefore) { Ok ("stock quant restored (" + $qtyFinal + ")") } else { Bad ("stock quant drift: " + $qtyFinal + " vs " + $qtyBefore) }
$leftOrder = SqlOne ("SELECT COUNT(*) FROM sale_order WHERE code='" + $code + "'")
if ($leftOrder -eq '0') { Ok 'probe order removed' } else { Bad ('probe order left: ' + $leftOrder) }

$after = Counts
$drift = @()
for ($i = 0; $i -lt $TABLES.Count; $i++) { if ($before[$i] -ne $after[$i]) { $drift += ($TABLES[$i] + ' ' + $before[$i] + '->' + $after[$i]) } }
Write-Output ("counts AFTER:  " + (($TABLES | ForEach-Object { $i = [array]::IndexOf($TABLES, $_); ($_ + '=' + $after[$i]) }) -join ' '))
if ($drift.Count -eq 0) { Ok 'per-table counts identical before/after (zero residual)' } else { Bad ('count drift: ' + ($drift -join '; ')) }

if ($fail -eq 0) { Write-Output 'RESULT F7-265-AUDIT-PROBE PASS' } else { Write-Output ("RESULT F7-265-AUDIT-PROBE FAIL count " + $fail) }
exit $fail
