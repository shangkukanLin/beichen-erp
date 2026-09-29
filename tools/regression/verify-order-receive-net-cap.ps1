# verify-order-receive-net-cap.ps1 (2026-09-29, user-reported bug): the 加工收退 qty cap must be computed on
# the **NET** delivered qty -- the red-reversal rows (加工退货 / DEFECT_RETURN, quantity<0, is_reverse=1)
# must give the quota BACK.
#
#   Reported case: WO-20260928010 -- 收 100（40+60 AUDITED）+ 退 100（-100 AUDITED）=> the UI already shows
#   已收 0 / 剩余 100 (summary() sums signed), but creating another receipt failed with
#     "累计收货量(100 + 本次10 = 110)超出订单数量(100)，请调整收货数量"
#   because assertDeliveryWithinOrderQty only summed delivery_type=DELIVERY AND quantity>0 rows.
#   (assertNotOverPlanned at audit time and summary() for the UI were BOTH already net.)
#
# Fixture is discovered at runtime (a 生产中 order that has a red reversal and some net remaining).
#   A) taking the FULL net remaining (planned - net) must NOT be rejected any more   <- regression lock
#   B) one unit beyond the net cap must now ask for the OVER-RECEIPT CONFIRMATION (2026-09-29 user口径
#      「加工订单和物料订单都可以超量收货」: over-qty is no longer a hard error, it returns
#      {canProceed=false, overReceipt=true} and the message keeps the 已收-退货 breakdown)
#   B2) the same payload with ?overReceipt=true (the user confirmed) is ACCEPTED and the draft is flagged
#       over_receipt=1 -- that flag is what the audit-time second line of defence reads.
#   B3/B4) audit-time second line of defence (zero side effects on purpose: the draft has no warehouse,
#      which is rejected AFTER the qty gate => the whole transaction rolls back):
#        B3) unconfirmed over draft (over_receipt=0) -> still rejected by the QTY gate
#        B4) confirmed  over draft (over_receipt=1) -> NOT rejected by the qty gate any more
# The drafts this script creates are deleted again (drafts carry no stock/payable side effects).
# ASCII ONLY.
$ErrorActionPreference = 'Continue'
$base = 'http://localhost:8080/api'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$fail = 0
function Ok($m) { Write-Output ('PASS ' + $m) }
function Bad($m) { Write-Output ('FAIL ' + $m); $script:fail++ }
function SqlOne([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -N -B -e $q 2>$null
  $v = ((@($o) | ForEach-Object { "$_" }) -join "`n").Trim()
  if (-not $v) { return '' }
  return (($v -split "`n")[0]).Trim()
}
function D([string]$s) { if (-not $s) { return [decimal]0 }; return [decimal]$s }

$lg = Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType 'application/json' -Body '{"username":"lin","password":"123","companyId":1}'
$tok = $lg.data.token
if (-not $tok) { Write-Output 'RESULT FAIL login failed'; exit 1 }
# Sa-Token: bare token in the Authorization header (no Bearer prefix)
$h = @{ Authorization = $tok }
function Req([string]$method, [string]$path, $body) {
  try {
    if ($null -eq $body) { return Invoke-RestMethod -Uri ($base + $path) -Method $method -Headers $h -TimeoutSec 40 }
    $j = ConvertTo-Json -InputObject $body -Depth 8
    return Invoke-RestMethod -Uri ($base + $path) -Method $method -Headers $h -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($j)) -TimeoutSec 40
  } catch { return [pscustomobject]@{ code = -1; msg = ('transport-error: ' + $_.Exception.Message) } }
}

# ---- fixture: a 生产中 order/product row whose net delivered < planned, and which has a red reversal ----
$ordId = [int](SqlOne "SELECT o.id FROM outsource_order o WHERE o.status='PRODUCING' AND EXISTS (SELECT 1 FROM outsource_order_delivery d WHERE d.order_id=o.id AND d.status<>'CANCELLED' AND (d.quantity<0 OR d.is_reverse=1)) ORDER BY o.id DESC LIMIT 1")
if ($ordId -le 0) { Write-Output 'FAIL no usable order fixture (need a 生产中 order with a red reversal)'; Write-Output 'RESULT FAIL'; exit 1 }
$masterId = [int](SqlOne "SELECT COALESCE(d.product_master_id, d.product_id) FROM outsource_order_delivery d WHERE d.order_id=$ordId AND d.status<>'CANCELLED' AND (d.quantity<0 OR d.is_reverse=1) ORDER BY d.id DESC LIMIT 1")
$rowId = [int](SqlOne "SELECT id FROM outsource_order_product WHERE order_id=$ordId AND product_id=$masterId LIMIT 1")
$planned = D (SqlOne "SELECT quantity FROM outsource_order_product WHERE id=$rowId")
$net = D (SqlOne "SELECT COALESCE(SUM(quantity),0) FROM outsource_order_delivery WHERE order_id=$ordId AND product_master_id=$masterId AND status<>'CANCELLED'")
$gross = D (SqlOne "SELECT COALESCE(SUM(quantity),0) FROM outsource_order_delivery WHERE order_id=$ordId AND product_master_id=$masterId AND status<>'CANCELLED' AND quantity>0 AND COALESCE(is_reverse,0)=0")
$wh = [int](SqlOne "SELECT id FROM warehouse WHERE warehouse_category='INVENTORY' AND warehouse_type='FINISHED' ORDER BY id LIMIT 1")
$maxId = [int](SqlOne "SELECT COALESCE(MAX(id),0) FROM outsource_order_delivery")
Write-Output ("fixture: orderId=$ordId productRow=$rowId masterId=$masterId planned=$planned gross=$gross net=$net warehouse=$wh")
if (($planned - $net) -gt 0) { Ok ('fixture has net remaining ' + ($planned - $net)) } else { Bad 'fixture has no net remaining' }
if ($gross -gt $net) { Ok ('fixture has red reversals: gross ' + $gross + ' > net ' + $net) } else { Bad ('fixture has no red reversal: gross ' + $gross + ' net ' + $net) }

function TryRecv([decimal]$qty) {
  $body = @{ orderId = $ordId; productId = $rowId; productMasterId = $masterId; quantity = $qty; aQty = $qty
             bQty = 0; cQty = 0; defectQty = 0; deliveryDate = (Get-Date -Format 'yyyy-MM-dd'); warehouseId = $wh }
  return (Req 'POST' '/outsource/order-delivery' $body)
}
# A) the full NET remaining: must not be rejected for exceeding the order qty any more.
#    (It may still answer the 缺料 confirmation -- a 200 with canProceed=false -- which is fine: the qty cap
#     is checked BEFORE the shortage check, so a "shortage" answer already proves the cap passed.)
$full = $planned - $net
$rA = TryRecv $full
$msgA = [string]$rA.msg
if (([int]$rA.code -eq 200) -and ($msgA -notlike '*超出订单数量*')) { Ok ('A: net remaining ' + $full + ' accepted, no qty-cap rejection -> ' + $msgA) }
else { Bad ('A: net remaining ' + $full + ' still rejected -> code=' + $rA.code + ' msg=' + $msgA) }
# B) one unit beyond the net cap (2026-09-29): asks for the over-receipt confirmation instead of hard-failing
$rB = TryRecv ($full + 1)
$msgB = [string]$rB.data.message
if (([int]$rB.code -eq 200) -and ($rB.data.overReceipt -eq $true) -and ($msgB -like '*超出订单数量*') -and ($msgB -like '*退货*')) {
  Ok ('B: over-net asks to confirm the over-receipt, with the 已收-退货 breakdown -> ' + $msgB)
} else { Bad ('B: over-net did not return the confirmation payload -> code=' + $rB.code + ' data=' + ($rB.data | ConvertTo-Json -Compress -Depth 6)) }
# nothing may be persisted before the user confirms
$draftNow = [int](SqlOne "SELECT COUNT(*) FROM outsource_order_delivery WHERE id > $maxId AND order_id=$ordId")
if ($draftNow -eq 0) { Ok 'B: the unconfirmed over-receipt persisted NOTHING' } else { Bad ('B: the unconfirmed over-receipt leaked ' + $draftNow + ' row(s)') }

# B2) the user confirms (overReceipt=true) -> draft accepted + flagged, which is what unlocks the audit.
#     forceDelivery=true is passed too because the two gates are INDEPENDENT: the qty gate runs first
#     (B above) and the material-shortage gate second, so the front end resends both flags after the
#     user confirmed each of them (this fixture's BOM has negative component stock => it always asks).
$overNet = $full + 1
$rB2 = Req 'POST' '/outsource/order-delivery?overReceipt=true&forceDelivery=true' (@{ orderId = $ordId; productId = $rowId
  productMasterId = $masterId; quantity = $overNet; aQty = $overNet; bQty = 0; cQty = 0; defectQty = 0
  deliveryDate = (Get-Date -Format 'yyyy-MM-dd'); warehouseId = $wh })
$draftB2 = 0
# NOTE: create returns {canProceed:true, draft:true} (no id) -- the draft is located by SQL instead.
if (([int]$rB2.code -eq 200) -and ($rB2.data.canProceed -eq $true) -and ($rB2.data.draft -eq $true)) {
  $draftB2 = [int](SqlOne "SELECT COALESCE(MAX(id),0) FROM outsource_order_delivery WHERE order_id=$ordId AND quantity=$overNet AND IFNULL(over_receipt,0)=1")
  if ($draftB2 -gt $maxId) {
    $flagB2 = SqlOne "SELECT CONCAT(status,'|',IFNULL(over_receipt,0)) FROM outsource_order_delivery WHERE id=$draftB2"
    if ($flagB2 -like 'DRAFT|1') { Ok ('B2: confirmed over-receipt accepted and flagged -> draft ' + $draftB2 + ' ' + $flagB2) }
    else { Bad ('B2: confirmed over-receipt draft not flagged: ' + $flagB2) }
  } else { Bad ('B2: confirmed over-receipt accepted but no flagged draft row found (maxId=' + $maxId + ')') }
} else { Bad ('B2: confirmed over-receipt rejected -> code=' + $rB2.code + ' data=' + ($rB2.data | ConvertTo-Json -Compress -Depth 6)) }

# ---- B3/B4) audit-time second line of defence. Both probes are SQL-inserted drafts with NO warehouse:
# the audit throws on the missing warehouse AFTER the qty gate => the transaction rolls back => the
# discriminator is the ERROR TEXT (qty gate vs. everything else), with zero side effects. ----
$overQty = $planned + 1
$netBefore = D (SqlOne "SELECT COALESCE(SUM(quantity),0) FROM outsource_order_delivery WHERE order_id=$ordId AND status<>'CANCELLED'")
function AddDraftOver([string]$flag) {
  & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e "INSERT INTO outsource_order_delivery (order_id, product_id, product_master_id, quantity, a_qty, b_qty, c_qty, defect_qty, delivery_date, status, delivery_type, is_reverse, over_receipt, company_id) SELECT $ordId, $rowId, $masterId, $overQty, $overQty, 0, 0, 0, CURDATE(), 'DRAFT', 'DELIVERY', 0, $flag, company_id FROM outsource_order WHERE id=$ordId;" 2>$null | Out-Null
  return [int](SqlOne "SELECT COALESCE(MAX(id),0) FROM outsource_order_delivery WHERE order_id=$ordId AND quantity=$overQty AND IFNULL(over_receipt,0)=$flag")
}
$dvB3 = AddDraftOver '0'
$dvB4 = AddDraftOver '1'
# guard: the probe rows must be OUR newest inserts -- if an INSERT had failed, the MAX(id) fallback could
# return the B2 draft, whose warehouse IS set => auditing it would really post stock/payables (side effects).
if (($dvB3 -gt 0) -and ($dvB4 -gt 0) -and ($dvB3 -ne $draftB2) -and ($dvB4 -ne $draftB2)) {
  Write-Output ("B3/B4 fixture: qty=$overQty (planned $planned) drafts=$dvB3(flag 0) $dvB4(flag 1), no warehouse")
  $rB3 = Req 'PUT' ("/outsource/order-delivery/$dvB3/audit") $null
  if (([string]$rB3.msg) -like '*超过该产品剩余待收量*') { Ok ('B3: audit of an UNCONFIRMED over draft is still blocked by the qty gate -> ' + $rB3.msg) }
  else { Bad ('B3: unconfirmed over draft was not blocked by the qty gate -> code=' + $rB3.code + ' msg=' + $rB3.msg) }
  $rB4 = Req 'PUT' ("/outsource/order-delivery/$dvB4/audit") $null
  if (([string]$rB4.msg) -notlike '*超过该产品剩余待收量*') { Ok ('B4: audit of a CONFIRMED over draft skips the qty gate -> ' + $rB4.msg) }
  else { Bad ('B4: confirmed over draft is still blocked by the qty gate -> ' + $rB4.msg) }
  $netAfter = D (SqlOne "SELECT COALESCE(SUM(quantity),0) FROM outsource_order_delivery WHERE order_id=$ordId AND status<>'CANCELLED'")
  if ($netAfter -eq ($netBefore + $overQty * 2)) { Ok ('B3/B4: both audits rolled back (net only grew by the two probe drafts: ' + $netBefore + ' -> ' + $netAfter + ')') }
  else { Bad ('B3/B4: unexpected net change ' + $netBefore + ' -> ' + $netAfter) }
} else { Bad ('B3/B4: could not build the probe drafts (b3=' + $dvB3 + ' b4=' + $dvB4 + ' b2=' + $draftB2 + ')') }

# cleanup: only the rows this run inserted (drafts carry no side effects)
& $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e "DELETE FROM outsource_order_delivery WHERE id > $maxId AND order_id=$ordId AND status='DRAFT';" 2>$null | Out-Null
if ($dvB3 -gt 0 -or $dvB4 -gt 0) { & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e "DELETE FROM outsource_order_delivery WHERE id IN ($dvB3,$dvB4) AND id > $maxId;" 2>$null | Out-Null }
# scope the check to THIS fixture's order: the count must not be polluted by rows another (parallel) script
# created elsewhere in the table -- that produced a false "left behind" failure once.
if (([int](SqlOne "SELECT COUNT(*) FROM outsource_order_delivery WHERE id > $maxId AND order_id=$ordId")) -eq 0) { Ok 'cleanup: the probe drafts were deleted' } else { Bad 'cleanup: probe drafts left behind' }

if ($fail -eq 0) { Write-Output 'RESULT PASS order receive: net-cap fix + over-receipt confirmation (confirm -> flagged -> audit gate skipped)' }
else { Write-Output ('RESULT FAIL count ' + $fail); exit 1 }
