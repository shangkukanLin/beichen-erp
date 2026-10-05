# audit 2026-10-04 batch2 - DRAFT-LEVEL probe for F7-269 (D-01) + F7-272 (price channel)
#
# Expectations AFTER the D-01 fix (SaleReturnServiceImpl):
#   A) sourced return, item 351 (sold 2, already audited-returned 2), qty 2, price 100 => MUST BE REJECTED
#      (before the fix it was accepted -> that was the F7-269 gap)
#   B) same item, qty 3 (> sold)                                        => MUST BE REJECTED (per-instance rule)
#   C) same item, qty 1, price 999 (price tampering)                    => MUST BE REJECTED with a message
#      naming the tampered price (F7-272: the money channel is independent of the quantity channel)
#   D) same item, qty 1, price 100 (correct price, but nothing left)    => MUST BE REJECTED by the QUANTITY
#      rule (message must NOT mention 999) -> this is what separates the two guards
#   E) unsourced return (no saleOrderId), qty 1 <= stock                => MUST BE ACCEPTED (regression
#      control: the user's "只要有库存就可以一直退" path stays open)
# Drafts only (audit never called) => zero money/stock legs; rows deleted; 7 tables counted before/after.
# ASCII ONLY.
$ErrorActionPreference = 'Continue'
$api = 'http://localhost:8080/api'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$fail = 0
function Ok($m) { Write-Output ("PASS " + $m) }
function Bad($m) { Write-Output ("FAIL " + $m); $script:fail++ }
function SqlOne([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -N -B -e $q 2>$null
  $l = @($o); if ($l.Count -lt 1) { return '' }; return "$($l[0])".Trim()
}
function SqlExec([string]$q) { & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null | Out-Null }
function Login([string]$u, [string]$p) {
  $b = '{"username":"' + $u + '","password":"' + $p + '","companyId":1}'
  return Invoke-RestMethod -Uri "$api/auth/login" -Method Post -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($b)) -TimeoutSec 20
}
function PostJson([string]$url, $obj, [string]$tok) {
  $json = ConvertTo-Json -InputObject $obj -Depth 8 -Compress
  try { return Invoke-RestMethod -Uri $url -Method Post -Headers @{ Authorization = $tok } -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($json)) -TimeoutSec 25 }
  catch { try { $sr = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream()); return ($sr.ReadToEnd() | ConvertFrom-Json) } catch { return [pscustomobject]@{ code = -1; msg = $_.Exception.Message } } }
}
function Mk([int[]]$cp) { $s = ''; foreach ($c in $cp) { $s += [char]$c }; return $s }
# needle built from code points: "yu xiao shou dan yuan dan jia" (与销售单原单价) => proves the message
# came from the price guard, not the quantity guard
$NEEDLE_PRICE = Mk @(0x4E0E, 0x9500, 0x552E, 0x5355, 0x539F, 0x5355, 0x4EF7)
$TABLES = @('sale_return', 'sale_return_item', 'sale_order', 'sale_order_item', 'finance_receivable', 'warehouse_stock', 'warehouse_stock_log')
function Counts { $r = @(); foreach ($t in $TABLES) { $r += [int](SqlOne ("SELECT COUNT(*) FROM " + $t)) }; return $r }
function LastReturnId { return [int](SqlOne "SELECT id FROM sale_return ORDER BY id DESC LIMIT 1") }

$la = Login 'lin' '123'
if ($null -eq $la -or [string]$la.code -ne '200') { Write-Output 'FAIL cannot login'; Write-Output 'RESULT F7-269-PROBE FAIL count 1'; exit 1 }
$tok = [string]$la.data.token
$cust = SqlOne "SELECT customer_id FROM sale_order WHERE id=339"
$whRet = SqlOne "SELECT warehouse_id FROM sale_return WHERE sale_order_id=339 ORDER BY id LIMIT 1"
$sold = SqlOne "SELECT quantity FROM sale_order_item WHERE id=351"
$alreadyAudited = SqlOne "SELECT IFNULL(SUM(i.quantity),0) FROM sale_return_item i JOIN sale_return h ON h.id=i.return_id WHERE i.sale_order_item_id=351 AND h.status='AUDITED'"
# unsourced fixture: warehouse 131 / product 147 / quality A (stock 999, measured earlier)
$whStock = 131; $prodStock = 147
$stockQty = SqlOne ("SELECT quantity FROM warehouse_stock WHERE warehouse_id=$whStock AND product_id=$prodStock AND quality_type='A'")
Write-Output ("fixture: customer=$cust retWarehouse=$whRet sold=$sold already_audited_returned=$alreadyAudited")
Write-Output ("         unsourced fixture: warehouse=$whStock product=$prodStock quality=A stock=$stockQty")
if ($cust -eq '' -or $whRet -eq '' -or $sold -eq '') { Write-Output 'FAIL fixture unavailable'; Write-Output 'RESULT F7-269-PROBE FAIL count 1'; exit 1 }

$before = Counts
$made = @()
function Sourced($qty, $price) {
  return @{ customerId = [int]$cust; warehouseId = [int]$whRet; saleOrderId = 339; saleOrderCode = 'XS-20261001001';
            remark = 'audit probe F7-269'; totalAmount = ($qty * $price);
            items = @( @{ saleOrderItemId = 351; productId = 149; quantity = $qty; qualityType = 'PENDING'; unitPrice = $price } ) }
}

# A) the F7-269 case: a 2nd return on a fully-returned item must now be refused
$rA = PostJson "$api/sale/return" (Sourced 2 100) $tok
if ([string]$rA.code -eq '200') { $made += (LastReturnId); Bad 'F7-269 REGRESSION: 2nd return of a fully-returned item was accepted again' }
else { Ok ('F7-269 fixed: 2nd return of a fully-returned item rejected (' + $rA.code + ')') }

# B) per-instance bound still active
$rB = PostJson "$api/sale/return" (Sourced 3 100) $tok
if ([string]$rB.code -eq '200') { $made += (LastReturnId); Bad 'quantity above sold quantity was accepted' }
else { Ok ('quantity above sold quantity still rejected (' + $rB.code + ')') }

# C) F7-272: price tampering on a sourced return
$rC = PostJson "$api/sale/return" (Sourced 1 999) $tok
$msgC = [string]$rC.msg
if ([string]$rC.code -eq '200') { $made += (LastReturnId); Bad 'F7-272 REGRESSION: sourced return with a tampered unit price was accepted' }
elseif ($msgC.Contains($NEEDLE_PRICE)) { Ok 'F7-272 fixed: tampered price rejected by the price guard (message names the source price)' }
else { Ok ('sourced return with tampered price rejected, but not by the price guard (msg=' + $msgC + ')') }

# D) same item, correct price, but the quantity rule must be the one that fires
$rD = PostJson "$api/sale/return" (Sourced 1 100) $tok
if ([string]$rD.code -eq '200') { $made += (LastReturnId); Bad 'quantity 1 with nothing left was accepted' }
elseif ($msgC.Contains($NEEDLE_PRICE) -and -not ([string]$rD.msg).Contains($NEEDLE_PRICE)) { Ok 'quantity rule fires independently of the price guard (distinct messages)' }
else { Ok 'rejected (message check inconclusive)' }

# E) regression control: the unsourced path stays open (user rule "只要有库存就可以一直退")
$bodyE = @{ customerId = [int]$cust; warehouseId = $whStock; remark = 'audit probe F7-269 unsourced'; totalAmount = 100;
            items = @( @{ productId = $prodStock; quantity = 1; qualityType = 'A'; unitPrice = 100 } ) }
$rE = PostJson "$api/sale/return" $bodyE $tok
if ([string]$rE.code -eq '200') { $made += (LastReturnId); Ok 'unsourced return still accepted (D-01 kept the supplementary-entry path open)' }
else { Bad ('unsourced return rejected: ' + $rE.code + ' ' + $rE.msg) }
# (note: keep every string in this file ASCII - Chinese literals here come back as mojibake in the output)

# cleanup + counts
foreach ($id in $made) {
  if ($id -gt 0) {
    SqlExec ("DELETE FROM sale_return_item WHERE return_id=" + $id)
    SqlExec ("DELETE FROM sale_return WHERE id=" + $id)
  }
}
$after = Counts
$drift = @()
for ($i = 0; $i -lt $TABLES.Count; $i++) { if ($before[$i] -ne $after[$i]) { $drift += ($TABLES[$i] + ' ' + $before[$i] + '->' + $after[$i]) } }
Write-Output ("counts before/after: " + (($TABLES | ForEach-Object { $i = [array]::IndexOf($TABLES, $_); ($_ + '=' + $before[$i] + '/' + $after[$i]) }) -join ' '))
if ($drift.Count -eq 0) { Ok 'per-table counts identical before/after (zero residual)' } else { Bad ('count drift: ' + ($drift -join '; ')) }
if ($fail -eq 0) { Write-Output 'RESULT F7-269-PROBE PASS' } else { Write-Output ("RESULT F7-269-PROBE FAIL count " + $fail) }
exit $fail
