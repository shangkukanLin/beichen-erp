# Guard (2026-09-28, user rule): the defect-return DETAIL page must offer audit + un-audit.
#   Page = /outsource/defect-return/detail/:id (the page all three 加工退货 ledger leaves jump to).
#   Gating must match the 收货记录 list / the ledger exactly:
#     DRAFT              -> [audit]   (PUT /outsource/order-delivery/{id}/audit)
#     AUDITED            -> [un-audit] (PUT .../un-audit)
#     an APPROVED return-back exists -> un-audit refused: the page shows an actionable message and never calls the API
#     (notation: only AUDITED return-backs count -- a DRAFT return-back moves nothing, so it must not block)
#                             (the API half of that gate is asserted in verify-defect-ledger.ps1)
#   Self-built via API + self-cleaned, so it is repeatable. PURE ASCII (Chinese only through ZH keys).
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')

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
function Step($n) { Write-Host ('--- ' + $n) }
function HasBtn([string]$key) {
  $b = B64 (ZH $key)
  return (EvalJs "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const t=T('$b');return String([...document.querySelectorAll('button')].filter(e=>e.getClientRects().length>0&&(e.innerText||'').trim()===t).length>0)})()")
}

$API = 'http://localhost:8080/api'
$lg = Invoke-RestMethod -Uri "$API/auth/login" -Method Post -ContentType 'application/json' -Body '{"username":"lin","password":"123","companyId":1}'
$h = @{ Authorization = $lg.data.token }
Ok ($lg.code -eq 200 -and $lg.data.token) 'login ok (API token)'

# ---------- fixture: a work order whose product has A-grade finished stock + a factory with an outsource wh ----------
$q1 = "SELECT * FROM (SELECT o.id AS oid, o.factory_id AS fid, op.product_id AS master, (SELECT ws.warehouse_id FROM warehouse_stock ws JOIN warehouse w ON w.id=ws.warehouse_id AND w.warehouse_type='FINISHED' WHERE ws.product_id=op.product_id AND ws.quality_type='A' AND ws.quantity>=5 ORDER BY ws.quantity DESC LIMIT 1) AS wh, (SELECT COUNT(*) FROM warehouse w2 WHERE w2.warehouse_category='OUTSOURCE' AND w2.factory_id=o.factory_id) AS out_wh FROM outsource_order o JOIN outsource_order_product op ON op.order_id=o.id WHERE o.status IN ('PRODUCING','FINISHED')) t WHERE t.wh IS NOT NULL AND t.out_wh>0 ORDER BY t.oid LIMIT 1"
$fx = SqlRow $q1
$fid = [int]$fx[1]; $master = [int]$fx[2]; $whId = [int]$fx[3]
Ok ($fid -gt 0 -and $master -gt 0 -and $whId -gt 0) ('fixture resolved (factory=' + $fid + ' product=' + $master + ' wh=' + $whId + ')')
if (-not ($fid -gt 0 -and $master -gt 0 -and $whId -gt 0)) { Write-Host 'RESULT FAIL verify-defect-detail-audit (fixture missing)'; exit 1 }

# a DRAFT order-less defect return (audit/un-audit do not need work-order linkage)
$body = @{ factoryId = $fid; warehouseId = $whId; productMasterId = $master; qualityType = 'A'; quantity = 2; remark = 'detail-audit probe' } | ConvertTo-Json -Depth 5
$created = Invoke-RestMethod -Uri "$API/outsource/order-delivery/return-defect-no-order" -Method Post -Headers $h -ContentType 'application/json' -Body $body
$id = [int](SqlOne "SELECT COALESCE(MAX(id),0) FROM outsource_order_delivery")
Ok ($created.code -eq 200 -and $id -gt 0) ('fixture: DRAFT order-less defect return created (id=' + $id + ')')
if ($id -le 0) { Write-Host 'RESULT FAIL verify-defect-detail-audit (create failed)'; exit 1 }
$url = '/outsource/defect-return/detail/' + $id

function Status() { return (SqlOne "SELECT status FROM outsource_order_delivery WHERE id=$id") }

EnsureLogin
WatchErrors
ClearErrs

# =====================================================================
Step 'S1 draft: audit shown, un-audit hidden'
Open $url 2800
Ok ((BodyHas (ZH 'btn_audit')) -eq 'true') 'S1 draft: the audit button is present'
Ok ((HasBtn 'btn_unaudit') -eq 'false') 'S1 draft: the un-audit button is absent'

# =====================================================================
Step 'S2 audit from the detail page'
Ok ((ClickBtn 'btn_audit') -match 'OK') 'S2 click audit'
ConfirmBox 1500 | Out-Null
Start-Sleep -Milliseconds 3200
Ok ((Status) -eq 'AUDITED') ('S2 status=AUDITED (' + (Status) + ')')
Open $url 2600
Ok ((HasBtn 'btn_unaudit') -eq 'true') 'S2 audited: the un-audit button now appears'

# =====================================================================
Step 'S3 a return-back exists -> un-audit refused by the page (actionable message, no API call)'
$candResp = Invoke-RestMethod -Uri "$API/outsource/order-delivery/$id/return-back-material-candidates" -Headers $h -Method Get
$cands = if ($null -eq $candResp.data) { @() } else { @($candResp.data) }
$items = @()
if ($cands.Count -gt 0) { $items = @(@{ materialId = $cands[0].materialId; quantity = 1 }) }
$inWh = [int](SqlOne "SELECT id FROM warehouse WHERE warehouse_category='INVENTORY' AND warehouse_type='FINISHED' ORDER BY id LIMIT 1")
$rbBody = @{ factoryId = $fid; productId = $master; quantity = 2; defectQualityType = 'A'; returnQualityType = 'A'; inWarehouseId = $inWh; items = $items } | ConvertTo-Json -Depth 6
$rb = Invoke-RestMethod -Uri "$API/outsource/order-delivery/$id/return-back" -Method Post -Headers $h -ContentType 'application/json' -Body $rbBody
Ok ($rb.code -eq 200) ('S3 return-back registered (code=' + $rb.code + ')')
# 2026-09-29 FIX (stale fixture): registering only creates a DRAFT, and this gate counts AUDITED returns only
#   (page: auditedReturns / API: returnedQtyBySource) -- so the return must be AUDITED first, otherwise the
#   click legally goes through and the "revoke first" refusal is never expected.
$rbIdS3 = [int](SqlOne "SELECT COALESCE(MAX(id),0) FROM outsource_return_back WHERE source_delivery_id=$id")
Ok ($rbIdS3 -gt 0) ('S3 return-back record id=' + $rbIdS3)
$rbAudit = Invoke-RestMethod -Uri "$API/outsource/order-delivery/return-back/$rbIdS3/audit" -Method Put -Headers $h
Ok ($rbAudit.code -eq 200) ('S3 return-back audited (makes the return real): ' + $rbAudit.msg)
Open $url 2800
Ok ((ClickBtn 'btn_unaudit') -match 'OK') 'S3 click un-audit'
Start-Sleep -Milliseconds 1600
Ok ((BodyHas (ZH 'txt_unaudit_need_revoke')) -eq 'true') 'S3 the page refused it BEFORE calling the API (revoke-first message shown)'
Ok ((Status) -eq 'AUDITED') ('S3 the record is untouched (' + (Status) + ')')

# =====================================================================
Step 'S4 revoke the return-back -> un-audit succeeds from the detail page'
$rbId = [int](SqlOne "SELECT COALESCE(MAX(id),0) FROM outsource_return_back WHERE source_delivery_id=$id")
Ok ($rbId -gt 0) ('S4 return-back record id=' + $rbId)
# 2026-09-29 FIX: an AUDITED return cannot be deleted directly (DRAFT only) -- un-audit first, then delete.
$rbUn = Invoke-RestMethod -Uri "$API/outsource/order-delivery/return-back/$rbId/un-audit" -Method Put -Headers $h
Ok ($rbUn.code -eq 200) ('S4 return-back un-audited (symmetric reversal): ' + $rbUn.msg)
$rvk = Invoke-RestMethod -Uri "$API/outsource/order-delivery/return-back/$rbId" -Method Delete -Headers $h
Ok ($rvk.code -eq 200) 'S4 return-back revoked'
Open $url 2800
Ok ((ClickBtn 'btn_unaudit') -match 'OK') 'S4 click un-audit (no returns left)'
ConfirmBox 1500 | Out-Null
Start-Sleep -Milliseconds 3200
Ok ((Status) -eq 'DRAFT') ('S4 status back to DRAFT (' + (Status) + ')')
Open $url 2600
Ok ((BodyHas (ZH 'btn_audit')) -eq 'true') 'S4 back to draft: the audit button re-appears'

# =====================================================================
Step 'S5 cleanup'
$del = Invoke-RestMethod -Uri "$API/outsource/order-delivery/$id" -Method Delete -Headers $h
Ok ($del.code -eq 200) 'S5 probe draft deleted'
Ok ((SqlOne "SELECT COUNT(*) FROM outsource_order_delivery WHERE id=$id") -eq '0') 'S5 no residue left behind'
$e = Errs
Write-Host ('ERRS=' + $e)
Ok ($e -eq '[]') 'S5 no page/API errors during the flow'

Summary 'verify-defect-detail-audit (defect-return detail audit/un-audit)'
