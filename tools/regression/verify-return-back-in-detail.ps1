# Guard (2026-09-27, user rule): "加工返回" is registered INSIDE the 无单加工退货 detail page --
#   `POST /api/outsource/order-delivery/{id}/return-back`  (register, effective immediately)
#   `DELETE /api/outsource/order-delivery/return-back/{recordId}` (revoke, symmetric reversal)
#   `GET  /api/outsource/order-delivery/{id}/return-backs` (record list of that source)
# The standalone "加工返回单" leaf is retired; the accounting legs are UNCHANGED:
#   ① consume on-site 成品(加工退货) in the factory outsource warehouse  ② put the repaired product back
#   into our own warehouse  ③ deduct actual materials (may go negative) ④ material amount -> receivable
#   from the factory (赔料) ⑤ FIFO cost carry-over.
# Also asserts two refusals: over-return, and a linked (有单) record used as source.
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
$fx = SqlRow "SELECT d.id, d.factory_id, d.product_master_id, d.quality_type, (SELECT MIN(w.id) FROM warehouse w WHERE w.factory_id = d.factory_id AND w.warehouse_category='OUTSOURCE') AS owh, IFNULL((SELECT SUM(ws.quantity) FROM warehouse_stock ws WHERE ws.warehouse_id = (SELECT MIN(w.id) FROM warehouse w WHERE w.factory_id = d.factory_id AND w.warehouse_category='OUTSOURCE') AND ws.product_id = d.product_master_id AND ws.quality_type = d.quality_type AND ws.stock_form='PRODUCT_DEFECT'),0) AS onsite FROM outsource_order_delivery d WHERE d.delivery_type='DEFECT_RETURN' AND d.order_id IS NULL AND d.status='AUDITED' AND ABS(d.quantity) - IFNULL((SELECT SUM(rb.quantity) FROM outsource_return_back rb WHERE rb.source_delivery_id = d.id AND rb.status='AUDITED'),0) >= 2 AND IFNULL((SELECT SUM(ws.quantity) FROM warehouse_stock ws WHERE ws.warehouse_id = (SELECT MIN(w.id) FROM warehouse w WHERE w.factory_id = d.factory_id AND w.warehouse_category='OUTSOURCE') AND ws.product_id = d.product_master_id AND ws.quality_type = d.quality_type AND ws.stock_form='PRODUCT_DEFECT'),0) >= 2 ORDER BY d.id LIMIT 1"
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
Ok ($r.data.status -eq 'AUDITED') 'the record is AUDITED right away (登记即生效, no draft step)'
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

Step 'revoke (逐条撤销): symmetric reversal + record removed'
$rv = Invoke-RestMethod -Uri "$API/outsource/order-delivery/return-back/$recId" -Method Delete -Headers $h
Write-Host ('revoke: code=' + $rv.code + ' msg=' + $rv.msg)
Ok ($rv.code -eq 200) 'the record can be revoked'
Ok ((OnSite) -eq $baseOnSite) 'on-site 成品 restored'
Ok ((BackIn 'A') -eq $baseBack) 'the product was taken back out of our warehouse'
if ($matId -gt 0) { Ok ((D (SqlOne "SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE warehouse_id=$factoryWh AND material_id=$matId AND stock_form='MATERIAL'")) -eq $baseMat) 'the material was put back' }
Ok ((ReceivableRow $recId)[0] -eq 'CANCELLED') 'the receivable was reversed (kept as CANCELLED for audit trail, amount zeroed)'
Ok ([decimal]((ReceivableRow $recId)[1]) -eq 0) 'the reversed receivable carries no amount'
Ok ([int](D (SqlOne "SELECT COUNT(*) FROM outsource_return_back WHERE id=$recId")) -eq 0) 'the record is gone (repeatable)'

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
