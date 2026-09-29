# Guard (2026-09-27, user rule): "加工返回" is registered INSIDE the 无单加工退货 detail page --
#   `POST /api/outsource/order-delivery/{id}/return-back`              (register -> DRAFT, no accounting)
#   `PUT  /api/outsource/order-delivery/return-back/{recordId}/audit`   (audit -> the legs below post)
#   `PUT  /api/outsource/order-delivery/return-back/{recordId}/un-audit`(symmetric reversal, back to DRAFT)
#   `DELETE /api/outsource/order-delivery/return-back/{recordId}`       (delete a DRAFT only)
#   `GET  /api/outsource/order-delivery/{id}/return-backs`             (record list of that source)
#   ⚠️ 2026-09-28 口径：登记只建草稿、审核才落账 ⇒ 已审核的记录**必须先反审核再删**（直接 DELETE 会被拒）。
# The standalone "加工返回单" leaf is retired; the accounting legs are UNCHANGED:
#   ① consume on-site 成品(加工退货) in the factory outsource warehouse  ② put the repaired product back
#   into our own warehouse  ③ deduct actual materials (may go negative) ④ material amount -> receivable
#   from the factory (赔料) ⑤ FIFO cost carry-over.
# Also asserts two refusals: over-return, and a linked (有单) record used as source.
#
# 2026-09-29 user口径 EXTENSION: 「送回时按实际用料 FIFO 生成对工厂的赔料应收 -- 登记返回的时候，
#   可以填写具体价格，默认 FIFO 可修改」=> the material price is chosen at REGISTRATION time:
#   * GET /{id}/return-back-material-price gives the DEFAULT (FIFO chain) price for the entered qty;
#   * items[].unitPrice + priceManual=true stores a MANUAL snapshot; the item amount, the bill amount and
#     the receivable all use it, and price_manual=1 keeps the audit trail;
#   * the COST carry-over still uses FIFO (cost_inbound_log.unit_cost == the default price) -- overriding
#     the receivable price must not leak into inventory cost.
# Self-built / self-cleaned, so it is repeatable. PURE ASCII.
$ErrorActionPreference = 'Continue'
$fail = 0
function Ok([bool]$c, [string]$m) { if ($c) { Write-Host ('PASS ' + $m) } else { Write-Host ('FAIL ' + $m); $script:fail++ } }
function Step($n) { Write-Host ('--- ' + $n) }
function Info($m) { Write-Host ('INFO ' + $m) }

$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
function SqlRaw([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null
  return ((@($o) | ForEach-Object { "$_" }) -join "`n").Trim()
}
function SqlRow([string]$q) {
  $v = SqlRaw $q
  if (-not $v) { return @() }
  $ls = $v -split "`n"
  if ($ls.Count -lt 2) { return @() }
  return @(($ls[1] -split "`t") | ForEach-Object { "$_".Trim() })
}
function SqlOne([string]$q) {
  $r = @(SqlRow $q)
  if ($r.Count -eq 0) { return '' }
  return $r[0]
}
function D([string]$s) { if (-not $s) { return [decimal]0 }; return [decimal]$s }
$API = 'http://localhost:8080/api'

$lg = Invoke-RestMethod -Uri "$API/auth/login" -Method Post -ContentType 'application/json' -Body '{"username":"lin","password":"123","companyId":1}'
$h = @{ Authorization = $lg.data.token }
Ok ($lg.code -eq 200 -and $lg.data.token) 'login ok'
if (-not $lg.data.token) { Write-Host 'RESULT FAIL verify-return-back-in-detail (no token)'; exit 1 }

# ---------- helpers over the new endpoints ----------
# ⚠️ 用**原始响应**计数，避免 PowerShell 把单元素数组展开成标量（踩过：@(Invoke-RestMethod) 拿到的是 R 外壳）
function ReturnRecordsRaw([int]$srcId) {
  return (Invoke-WebRequest -Uri "$API/outsource/order-delivery/$srcId/return-backs" -Headers $h -UseBasicParsing).Content
}
function ReturnRecordsCount([int]$srcId) {
  $raw = ReturnRecordsRaw $srcId
  return ([regex]::Matches($raw, '"id":')).Count
}
# 该返回记录对应的赔料应收行（按 source_id 精确定位；撤销后按设计保留为 CANCELLED 留痕）
# ⚠️ PowerShell 没有 /** */ 注释 —— 写成 /** 会被当命令执行（踩过，输出里一行 ObjectNotFound 噪音）
function ReceivableRow([int]$recId) {
  return (SqlRow "SELECT IFNULL(status,'-'), IFNULL(amount,-1) FROM finance_receivable WHERE source_id=$recId AND source_bill_type='OUTSOURCE_RETURN_BACK' ORDER BY id DESC LIMIT 1")
}
function RegisterReturn([int]$srcId, [int]$qty, [int]$inWh, [string]$returnQt, $items) {
  $body = @{ factoryId = $script:FACTORY; productId = $script:PRODUCT; quantity = $qty
             defectQualityType = $script:DEFECTQT; returnQualityType = $returnQt
             inWarehouseId = $inWh; items = $items } | ConvertTo-Json -Depth 6
  try { return Invoke-RestMethod -Uri "$API/outsource/order-delivery/$srcId/return-back" -Method Post -Headers $h -ContentType 'application/json' -Body $body }
  catch { return [pscustomobject]@{ code = 500; msg = $_.Exception.Message } }
}

Step 'fixture: an AUDITED 无单加工退货 with on-site 成品 and something left to return'
$fxSql = "SELECT d.id, d.factory_id, d.product_master_id, d.quality_type, (SELECT MIN(w.id) FROM warehouse w WHERE w.factory_id = d.factory_id AND w.warehouse_category='OUTSOURCE') AS owh, IFNULL((SELECT SUM(ws.quantity) FROM warehouse_stock ws WHERE ws.warehouse_id = (SELECT MIN(w.id) FROM warehouse w WHERE w.factory_id = d.factory_id AND w.warehouse_category='OUTSOURCE') AND ws.product_id = d.product_master_id AND ws.quality_type = d.quality_type AND ws.stock_form='PRODUCT_DEFECT'),0) AS onsite FROM outsource_order_delivery d WHERE d.delivery_type='DEFECT_RETURN' AND d.order_id IS NULL AND d.status='AUDITED' AND ABS(d.quantity) - IFNULL((SELECT SUM(rb.quantity) FROM outsource_return_back rb WHERE rb.source_delivery_id = d.id AND rb.status='AUDITED'),0) >= 2 AND IFNULL((SELECT SUM(ws.quantity) FROM warehouse_stock ws WHERE ws.warehouse_id = (SELECT MIN(w.id) FROM warehouse w WHERE w.factory_id = d.factory_id AND w.warehouse_category='OUTSOURCE') AND ws.product_id = d.product_master_id AND ws.quality_type = d.quality_type AND ws.stock_form='PRODUCT_DEFECT'),0) >= 2"
# 2026-09-29: prefer a source whose 实际用料 candidate pool (BOM snapshot) is NON-EMPTY -- the material legs
# and the price steps only mean something with a material line. Probe up to 8 candidates; the first usable
# SQL row is kept as fallback (pool empty => the material assertions skip, exactly as before).
$fx = @()
for ($off = 0; $off -lt 8; $off++) {
  $row = @(SqlRow ($fxSql + ' ORDER BY d.id LIMIT 1 OFFSET ' + $off))
  if ($row.Count -eq 0 -or -not $row[0]) { break }
  $probeSrc = [int]$row[0]
  $pr = Invoke-RestMethod -Uri "$API/outsource/order-delivery/$probeSrc/return-back-material-candidates" -Headers $h -Method Get
  $poolN = if ($null -eq $pr.data) { 0 } else { @($pr.data).Count }
  Write-Host ('fixture probe: source=' + $probeSrc + ' materialPool=' + $poolN)
  if ($fx.Count -eq 0) { $fx = $row }
  if ($poolN -gt 0) { $fx = $row; break }
}
$src = [int]$fx[0]
Write-Host ("FIXTURE: source=" + $src + " factory=" + $fx[1] + " product=" + $fx[2] + " spec=" + $fx[3] + " outsourceWh=" + $fx[4] + " onsite=" + $fx[5])
if ($src -le 0) { Info 'no usable fixture (AUDITED 无单加工退货 with on-site stock >= 2) -> skipped (coverage gap)'; Write-Host 'RESULT PASS verify-return-back-in-detail (skipped)'; exit 0 }
$script:FACTORY = [int]$fx[1]; $script:PRODUCT = [int]$fx[2]; $script:DEFECTQT = "$($fx[3])"; $factoryWh = [int]$fx[4]
$inWh = [int](SqlOne "SELECT id FROM warehouse WHERE warehouse_category='INVENTORY' AND warehouse_type='FINISHED' ORDER BY id LIMIT 1")
Ok ($inWh -gt 0) ('return warehouse resolved (id=' + $inWh + ')')

function OnSite() { return D (SqlOne "SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE warehouse_id=$factoryWh AND product_id=$script:PRODUCT AND quality_type='$script:DEFECTQT' AND stock_form='PRODUCT_DEFECT'") }
function BackIn([string]$qt) { return D (SqlOne "SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE warehouse_id=$inWh AND product_id=$script:PRODUCT AND quality_type='$qt' AND stock_form='MATERIAL'") }

Step 'register (登记即生效): consume on-site + product back + materials + 赔料应收'
$baseOnSite = OnSite; $baseBack = BackIn 'A'; $baseRecords = ReturnRecordsCount $src
Write-Host ('BASE on-site=' + $baseOnSite + ' backInA=' + $baseBack + ' records=' + $baseRecords)
$candResp = Invoke-RestMethod -Uri "$API/outsource/order-delivery/$src/return-back-material-candidates" -Headers $h -Method Get
$cands = if ($null -eq $candResp.data) { @() } else { @($candResp.data) }
Write-Host ('CANDIDATES n=' + $cands.Count)
$items = @()
if ($cands.Count -gt 0) { $items = @(@{ materialId = $cands[0].materialId; quantity = 1 }) }
$matId = if ($cands.Count -gt 0) { [int]$cands[0].materialId } else { 0 }
$baseMat = if ($matId -gt 0) { D (SqlOne "SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE warehouse_id=$factoryWh AND material_id=$matId AND stock_form='MATERIAL'") } else { [decimal]0 }

$r = RegisterReturn $src 2 $inWh 'A' $items
Write-Host ('register: code=' + $r.code + ' msg=' + $r.msg + ' docCode=' + $r.data.code)
Ok ($r.code -eq 200) 'registering a return succeeded'
# 2026-09-28（用户口径「登记返回需要审核和反审核」）：登记**只建草稿**，审核才落账
Ok ($r.data.status -eq 'DRAFT') 'the record is saved as DRAFT (登记只建草稿, audit is a separate step)'
$recId = [int](SqlOne "SELECT COALESCE(MAX(id),0) FROM outsource_return_back WHERE source_delivery_id=$src")
Ok ($recId -gt 0) ('return record id=' + $recId)
# 审核才落账（三腿 + 赔料应收 + 成本结转）——下面的库存/应收断言依赖这一步
$au = Invoke-RestMethod -Uri "$API/outsource/order-delivery/return-back/$recId/audit" -Method Put -Headers $h
Ok ($au.code -eq 200) ('audit the drafted return: ' + $au.code + ' ' + $au.msg)
Ok ((SqlOne "SELECT status FROM outsource_return_back WHERE id=$recId") -eq 'AUDITED') 'after audit: status=AUDITED'
$recId = [int]$r.data.id
Ok ((OnSite) -eq ($baseOnSite - 2)) ('on-site 成品 deducted 2 (now ' + (OnSite) + ')')
Ok ((BackIn 'A') -eq ($baseBack + 2)) ('repaired product put back into our warehouse (now ' + (BackIn 'A') + ')')
if ($matId -gt 0) { Ok ((D (SqlOne "SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE warehouse_id=$factoryWh AND material_id=$matId AND stock_form='MATERIAL'")) -eq ($baseMat - 1)) 'actual material deducted 1 from the factory outsource warehouse' }
Ok ((ReceivableRow $recId).Count -eq 2) 'a receivable from the factory was created (赔料)'
Ok ((ReceivableRow $recId)[0] -eq 'UNSETTLED') ('the receivable is UNSETTLED (status=' + ((ReceivableRow $recId)[0]) + ')')
$nRecs = ReturnRecordsCount $src
Ok ($nRecs -eq ($baseRecords + 1)) ('the return record shows up in the source record list (n=' + $nRecs + ')')
Ok ((ReturnRecordsRaw $src) -match [regex]::Escape($r.data.code)) 'the record list carries this 返回单号 (ORB-)'
Ok ([int](D (SqlOne "SELECT COUNT(*) FROM outsource_return_back WHERE id=$recId AND source_delivery_id=$src AND status='AUDITED'")) -eq 1) 'the record is bound to this source delivery'

Step 'un-audit + delete (2026-09-28 口径: 已审核的返回必须先反审核；DELETE 只对草稿开放)'
# 2026-09-29 修正本守卫：原实现 audit 后**直接 DELETE**（2026-09-27 的"登记即生效"口径），
# 但 2026-09-28 起登记只建草稿、审核才落账，`revoke()` 只允许删**草稿** ⇒ 直接删必然被拒（本支守卫此前一直红）。
$un = Invoke-RestMethod -Uri "$API/outsource/order-delivery/return-back/$recId/un-audit" -Method Put -Headers $h
Write-Host ('un-audit: code=' + $un.code + ' msg=' + $un.msg)
Ok ($un.code -eq 200) ('the audited return can be un-audited: ' + $un.code + ' ' + $un.msg)
Ok ((SqlOne "SELECT status FROM outsource_return_back WHERE id=$recId") -eq 'DRAFT') 'after un-audit: status back to DRAFT (留痕)'
Ok ((OnSite) -eq $baseOnSite) 'on-site 成品 restored'
Ok ((BackIn 'A') -eq $baseBack) 'the product was taken back out of our warehouse'
if ($matId -gt 0) { Ok ((D (SqlOne "SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE warehouse_id=$factoryWh AND material_id=$matId AND stock_form='MATERIAL'")) -eq $baseMat) 'the material was put back' }
Ok ((ReceivableRow $recId)[0] -eq 'CANCELLED') 'the receivable was reversed (kept as CANCELLED for audit trail, amount zeroed)'
Ok ([decimal]((ReceivableRow $recId)[1]) -eq 0) 'the reversed receivable carries no amount'
$rv = Invoke-RestMethod -Uri "$API/outsource/order-delivery/return-back/$recId" -Method Delete -Headers $h
Write-Host ('delete draft: code=' + $rv.code + ' msg=' + $rv.msg)
Ok ($rv.code -eq 200) 'the DRAFT record can now be deleted'
Ok ([int](D (SqlOne "SELECT COUNT(*) FROM outsource_return_back WHERE id=$recId")) -eq 0) 'the record is gone (repeatable)'

Step 'price: default = FIFO, and a MANUAL price may be entered at registration (2026-09-29 user口径)'
# User口径: "送回时按实际用料 FIFO 生成对工厂的赔料应收 -> 登记返回的时候，可以填写具体价格，默认 FIFO 可修改".
# Expectations locked here:
#   * GET /{id}/return-back-material-price returns the DEFAULT price (the FIFO chain) for that qty;
#   * register with items[].unitPrice + priceManual=true  => the item snapshot uses the manual price,
#     amount = manual x qty, price_manual=1, the bill carries it and the receivable uses it;
#   * the COST carry-over still uses FIFO, i.e. overriding the receivable price must NOT leak into
#     inventory cost. The comparison is on TOTALS: cost_inbound_log spreads the material FIFO amount
#     over the RETURNED product qty (unit_cost = materialFifo x matQty / returnQty), so it is
#     unit_cost x quantity that must equal the FIFO material amount -- not the per-material price.
$defaultPrice = [decimal]0
if ($matId -gt 0) {
  $prResp = Invoke-RestMethod -Uri "$API/outsource/order-delivery/$src/return-back-material-price?materialId=$matId&quantity=1" -Headers $h -Method Get
  Write-Host ('default price: code=' + $prResp.code + ' material=' + $matId + ' x1 -> ' + $prResp.data.unitPrice)
  Ok ([int]$prResp.code -eq 200) 'the default-price endpoint responds'
  $defaultPrice = [decimal]($prResp.data.unitPrice)
} else { Info 'no candidate material -> the price steps are limited to the endpoint check' }

$manualUnit = [decimal]12.34
$matQtyM = 1
$itemsM = @()
if ($matId -gt 0) { $itemsM = @(@{ materialId = $matId; quantity = $matQtyM; unitPrice = $manualUnit; priceManual = $true }) }
$rm = RegisterReturn $src 2 $inWh 'A' $itemsM
Write-Host ('register(manual price): code=' + $rm.code + ' msg=' + $rm.msg + ' docCode=' + $rm.data.code)
Ok ($rm.code -eq 200) 'registering a return with a manually entered price succeeds'
$recM = [int](SqlOne "SELECT COALESCE(MAX(id),0) FROM outsource_return_back WHERE source_delivery_id=$src")
Ok ($recM -gt 0) ('manual-price return record id=' + $recM)
$am = Invoke-RestMethod -Uri "$API/outsource/order-delivery/return-back/$recM/audit" -Method Put -Headers $h
Ok ($am.code -eq 200) ('audit the manual-price return: ' + $am.code + ' ' + $am.msg)

if ($matId -gt 0) {
  $rowM = SqlRow "SELECT IFNULL(unit_price,-1), IFNULL(amount,-1), IFNULL(price_manual,-1) FROM outsource_return_back_item WHERE return_back_id=$recM AND material_id=$matId"
  Write-Host ('item snapshot: unit=' + $rowM[0] + ' amount=' + $rowM[1] + ' price_manual=' + $rowM[2])
  Ok ([decimal]$rowM[0] -eq $manualUnit) 'the item unit price is the value entered at registration'
  Ok ([decimal]$rowM[1] -eq $manualUnit) 'the item amount = unit price x qty (12.34 x 1)'
  Ok ($rowM[2] -eq '1') 'the item is flagged price_manual=1 (audit trail of a manual price)'
  Ok ([decimal](SqlOne "SELECT IFNULL(material_amount,-1) FROM outsource_return_back WHERE id=$recM") -eq $manualUnit) 'the bill carries the manual amount (料款 = 赔料应收)'
  $recvM = ReceivableRow $recM
  Write-Host ('receivable: status=' + $recvM[0] + ' amount=' + $recvM[1])
  Ok ([decimal]$recvM[1] -eq $manualUnit) 'the receivable from the factory uses the MANUAL price'
  Ok ($recvM[0] -eq 'UNSETTLED') 'the manual-price receivable is UNSETTLED'
  # cost carry-over must stay on FIFO (cost_inbound_log is the batch record reversed on un-audit).
  # Compare TOTALS: the log spreads the material amount over the returned product qty.
  $costRow = SqlRow "SELECT IFNULL(ROUND(unit_cost*quantity,2),-1), IFNULL(unit_cost,-1), IFNULL(quantity,-1) FROM cost_inbound_log WHERE related_bill_id=$recM ORDER BY id DESC LIMIT 1"
  $costTotal = [decimal]$costRow[0]
  $expectTotal = [decimal][math]::Round($defaultPrice * $matQtyM, 2)
  Write-Host ('cost carry-over: total=' + $costTotal + ' (unit_cost=' + $costRow[1] + ' x qty=' + $costRow[2] + ') vs FIFO total=' + $expectTotal + ' (manual would be ' + ([math]::Round($manualUnit * $matQtyM, 2)) + ')')
  Ok ($costTotal -eq $expectTotal) 'the cost carry-over still follows FIFO (the manual price does not touch inventory cost)'
  if ([decimal][math]::Round($manualUnit * $matQtyM, 2) -eq $expectTotal) { Info 'WARNING: FIFO total equals the manual total in this fixture -> the override is not distinguishable here' }
}

# clean up the same way the 2026-09-28 口径 requires: un-audit first, then delete the draft
$unM = Invoke-RestMethod -Uri "$API/outsource/order-delivery/return-back/$recM/un-audit" -Method Put -Headers $h
Ok ($unM.code -eq 200) ('the manual-price return can be un-audited: ' + $unM.code + ' ' + $unM.msg)
$rvM = Invoke-RestMethod -Uri "$API/outsource/order-delivery/return-back/$recM" -Method Delete -Headers $h
Ok ($rvM.code -eq 200) 'the manual-price draft can now be deleted'
Ok ((OnSite) -eq $baseOnSite) 'on-site 成品 restored after the manual-price cycle'
Ok ((ReceivableRow $recM)[0] -eq 'CANCELLED') 'the manual-price receivable was reversed (kept as CANCELLED)'
Ok ([int](D (SqlOne "SELECT COUNT(*) FROM outsource_return_back WHERE id=$recM")) -eq 0) 'the manual-price record is gone (repeatable)'

Step 'refusal: over-return must not be accepted'
$over = RegisterReturn $src 9999 $inWh 'A' @()
Write-Host ('over-return: code=' + $over.code + ' msg=' + $over.msg)
Ok ($over.code -ne 200) 'returning more than 未返回量 is rejected'
Ok ((OnSite) -eq $baseOnSite) 'the rejected call moved no stock'

Step 'refusal: a linked (有单) 加工退货 must not be usable as source'
$linked = [int](SqlOne "SELECT id FROM outsource_order_delivery WHERE delivery_type='DEFECT_RETURN' AND order_id IS NOT NULL AND status='AUDITED' ORDER BY id LIMIT 1")
if ($linked -le 0) { Info 'no linked (有单) 加工退货 recorded -> refusal case skipped' } else {
  $lr = RegisterReturn $linked 1 $inWh 'A' @()
  Write-Host ('linked source: code=' + $lr.code + ' msg=' + $lr.msg)
  Ok ($lr.code -ne 200) 'a linked (有单) record is rejected as a return source'
}

Write-Host ''
Write-Host ('RESULT ' + $(if ($script:fail -eq 0) { 'PASS' } else { 'FAIL' }) + ' verify-return-back-in-detail (FAIL=' + $script:fail + ')')
if ($script:fail -ne 0) { exit 1 }
