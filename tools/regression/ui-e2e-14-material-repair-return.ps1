# UI E2E 14: material return two types + repair-return round trip (2026-09-17). ASCII ONLY.
#   S1 list page: type tabs (REFUND / REPAIR) + "new repair-return" button
#   S2 create a REPAIR draft from the tab entry (url carries returnType=REPAIR)
#   S3 audit -> material leaves source warehouse, **payable untouched** (repair != refund)
#   S4 register repair return -> material comes back into the warehouse
#   S5 un-audit is BLOCKED while repair-return records exist
#   S6 cancel repair return -> stock rolled back; then un-audit + cancel succeed
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')

$script:MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'

function SqlRaw([string]$q) {
  $o = & $script:MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null
  return ((@($o) | ForEach-Object { "$_" }) -join "`n").Trim()
}
function SqlOne([string]$q) {
  $v = SqlRaw $q
  if (-not $v) { return '' }
  $ls = $v -split "`n"
  if ($ls.Count -lt 2) { return '' }
  return (($ls[1] -split "`t")[0]).Trim()
}
function D([string]$s) { if (-not $s) { return [decimal]0 }; return [decimal]$s }
function StockQty([int]$wh, [string]$col, [int]$id, [string]$q) {
  return SqlOne "SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE warehouse_id=$wh AND $col=$id AND quality_type='$q'"
}
function PaySum([int]$sid) { return SqlOne "SELECT COALESCE(SUM(amount),0) FROM finance_payable WHERE supplier_id=$sid AND status='UNSETTLED'" }
function MaxId([string]$tbl) { return SqlOne "SELECT COALESCE(MAX(id),0) FROM $tbl" }
function CurUrl() { return (EvalJs "location.href.replace(location.origin,'')") }
function Step($n) { Write-Host ('--- STEP ' + $n) }

EnsureLogin
WatchErrors
ClearErrs

# =====================================================================
Step 'S1 material-return list: type tabs + repair entry'
Open '/outsource/material-return' 3000
Ok ((BodyHas (ZH 'tab_mr_refund')) -eq 'true') 'S1 tab REFUND present'
Ok ((BodyHas (ZH 'tab_mr_repair')) -eq 'true') 'S1 tab REPAIR present'
Ok ((BodyHas (ZH 'btn_new_mr_repair')) -eq 'true') 'S1 button "new repair-return" present'
Write-Host ('S1 tab click: ' + (ClickText (ZH 'tab_mr_repair')))
Start-Sleep -Milliseconds 1600
Ok ((Errs) -eq '[]') 'S1 no errors after switching tab'

# =====================================================================
Step 'S2 create REPAIR draft'
$b37 = StockQty 37 'material_id' 25 'GOOD'
$bpay = PaySum 20
Write-Host ("BASE wh37.m25=" + $b37 + " payable20=" + $bpay)
Ok ((ClickBtn 'btn_new_mr_repair') -match 'OK') 'S2 click "new repair-return"'
Start-Sleep -Milliseconds 3000
Ok ((CurUrl) -match 'returnType=REPAIR') ('S2 url carries returnType=REPAIR url=' + (CurUrl))
Ok ((BodyHas (ZH 'lbl_mr_repair_supplier')) -eq 'true') 'S2 supplier label switched to repair mode'
Ok ((SelectLabelText 'lbl_mr_repair_supplier' (ZH 'val_supplier_jh')) -match 'OK') 'S2 pick supplier'
Start-Sleep -Milliseconds 1400
Ok ((SelectLabelText 'lbl_src_wh_out' (ZH 'wh_jiehe')) -match 'OK') 'S2 pick source warehouse'
Start-Sleep -Milliseconds 2200
Ok ((SetRowInput 0 0 '3') -match 'OK') 'S2 qty=3'
Ok ((ClickBtn 'btn_save_draft') -match 'OK') 'S2 save draft'
Start-Sleep -Milliseconds 3400
$rid = [int](MaxId 'outsource_material_return')
Ok ($rid -gt 0) ('S2 created material return id=' + $rid)
Ok ((SqlOne "SELECT return_type FROM outsource_material_return WHERE id=$rid") -eq 'REPAIR') 'S2 type=REPAIR persisted'
Ok ((SqlOne "SELECT status FROM outsource_material_return WHERE id=$rid") -eq 'DRAFT') 'S2 status=DRAFT'

# =====================================================================
Step 'S3 audit: material out, payable untouched'
Open ("/outsource/material-return/detail/$rid") 2800
Ok ((BodyHas (ZH 'txt_repair_records')) -eq 'true') 'S3 repair-return card shown for REPAIR'
# 注意：卡片空态提示文案里也含"登记维修返回"字样，故必须判断**按钮元素**是否存在，不能用 BodyHas
$rrKey = B64 (ZH 'btn_repair_return')
$hasRR = EvalJs "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const t=T('$rrKey');return String([...document.querySelectorAll('button')].filter(e=>e.getClientRects().length>0&&(e.innerText||'').trim()===t).length>0)})()"
Ok ($hasRR -eq 'false') 'S3 repair-return button hidden before audit'
Ok ((ClickBtn 'btn_audit') -match 'OK') 'S3 audit'
ConfirmBox 1500 | Out-Null
Start-Sleep -Milliseconds 3200
Write-Host ('S3 after audit wh37.m25=' + (StockQty 37 'material_id' 25 'GOOD') + ' payable20=' + (PaySum 20))
Ok ((D (StockQty 37 'material_id' 25 'GOOD')) -eq ((D $b37) - 3)) 'S3 source warehouse -3 (sent out for repair)'
Ok ((D (PaySum 20)) -eq (D $bpay)) 'S3 payable UNCHANGED (repair return does not offset payable)'
Ok ((SqlOne "SELECT status FROM outsource_material_return WHERE id=$rid") -eq 'AUDITED') 'S3 status=AUDITED'
$hasRR2 = EvalJs "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const t=T('$rrKey');return String([...document.querySelectorAll('button')].filter(e=>e.getClientRects().length>0&&(e.innerText||'').trim()===t).length>0)})()"
Ok ($hasRR2 -eq 'true') 'S3 repair-return button appears after audit'

# =====================================================================
Step 'S4 register repair return: material comes back'
Ok ((ClickBtn 'btn_repair_return') -match 'OK') 'S4 open register dialog'
Start-Sleep -Milliseconds 1800
Ok ((SetRowInput 0 0 '3') -match 'OK') 'S4 repair qty=3'
Ok ((ClickDialogBtn 'btn_repair_confirm') -match 'OK') 'S4 confirm'
Start-Sleep -Milliseconds 3400
Write-Host ('S4 after receive wh37.m25=' + (StockQty 37 'material_id' 25 'GOOD'))
Ok ((D (StockQty 37 'material_id' 25 'GOOD')) -eq (D $b37)) 'S4 material back into source warehouse'
$rc = [int](SqlOne "SELECT COUNT(*) FROM outsource_material_return_repair WHERE return_order_id=$rid")
Ok ($rc -eq 1) 'S4 repair-return record persisted'

# =====================================================================
Step 'S5 un-audit blocked while repair-return exists'
Ok ((ClickBtn 'btn_unaudit') -match 'OK') 'S5 click un-audit'
ConfirmBox 1500 | Out-Null
Start-Sleep -Milliseconds 2800
Write-Host ('S5 msg=' + (Txt '.el-message'))
Ok ((SqlOne "SELECT status FROM outsource_material_return WHERE id=$rid") -eq 'AUDITED') 'S5 still AUDITED (blocked by repair records)'

# =====================================================================
Step 'S6 cancel repair return, then un-audit + cancel'
$rcId = [int](SqlOne "SELECT id FROM outsource_material_return_repair WHERE return_order_id=$rid LIMIT 1")
Ok ($rcId -gt 0) ('S6 repair record id=' + $rcId)
Open ("/outsource/material-return/detail/$rid") 2800
Ok ((ClickRowBtn 0 'btn_revoke') -match 'OK') 'S6 click 撤销 on repair record'
ConfirmBox 1500 | Out-Null
Start-Sleep -Milliseconds 3200
Ok ((D (StockQty 37 'material_id' 25 'GOOD')) -eq ((D $b37) - 3)) 'S6 stock rolled back to sent-out state'
Ok ([int](SqlOne "SELECT COUNT(*) FROM outsource_material_return_repair WHERE return_order_id=$rid") -eq 0) 'S6 repair record removed'
Ok ((ClickBtn 'btn_unaudit') -match 'OK') 'S6 un-audit now allowed'
ConfirmBox 1500 | Out-Null
Start-Sleep -Milliseconds 3200
Ok ((D (StockQty 37 'material_id' 25 'GOOD')) -eq (D $b37)) 'S6 source warehouse fully restored'
Ok ((D (PaySum 20)) -eq (D $bpay)) 'S6 payable still unchanged'
Ok ((ClickBtn 'btn_cancel_doc') -match 'OK') 'S6 cancel doc'
ConfirmBox 1500 | Out-Null
Start-Sleep -Milliseconds 2200
Ok ((SqlOne "SELECT status FROM outsource_material_return WHERE id=$rid") -eq 'CANCELLED') 'S6 doc cancelled'

# =====================================================================
Step 'S7 no page errors'
$e = Errs
Write-Host ('ERRS=' + $e)
Ok ($e -eq '[]') 'S7 no page/API errors during the flow'

Summary 'ui-e2e-14 material return two types + repair-return round trip'
