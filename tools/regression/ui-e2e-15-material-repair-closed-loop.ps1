# UI E2E 15: material repair-return closed loop (2026-09-17). ASCII ONLY.
#   S1 list: repair tab shows sent/returned + progress columns
#   S2 add page: link a material order; unfinished order -> deduct-order hint
#   S3 create draft -> DB keeps material_order_id, nothing deducted yet
#   S4 audit -> order item received -3, repair_returned +3 (case 1: order RECEIVING)
#   S5 detail: linked order + "deducted" note + sent/returned 3 / 0
#   S6 register repair return -> order item restored (received +3, repair_returned 0)
#   S7 close -> blocked while unreturned > 0 is covered by S6 order; here close succeeds
#   S8 reopen -> cancel repair return -> un-audit -> doc cancelled (full rollback)
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
function StockQty([int]$wh, [int]$id) {
  return SqlOne "SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE warehouse_id=$wh AND material_id=$id AND quality_type='GOOD'"
}
function OrderRecv([int]$itemId) { return SqlOne "SELECT received_quantity FROM outsource_material_order_item WHERE id=$itemId" }
function OrderRepair([int]$itemId) { return SqlOne "SELECT COALESCE(repair_returned_qty,0) FROM outsource_material_order_item WHERE id=$itemId" }
function MaxId([string]$tbl) { return SqlOne "SELECT COALESCE(MAX(id),0) FROM $tbl" }
function Step($n) { Write-Host ('--- STEP ' + $n) }
function HasBtn([string]$key) {
  $b = B64 (ZH $key)
  return (EvalJs "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const t=T('$b');return String([...document.querySelectorAll('button')].filter(e=>e.getClientRects().length>0&&(e.innerText||'').trim()===t).length>0)})()")
}

EnsureLogin
WatchErrors
ClearErrs

# =====================================================================
Step 'S1 repair list: sent/returned + progress columns'
Open '/outsource/material-return' 3000
Write-Host ('S1 tab: ' + (ClickText (ZH 'tab_mr_repair')))
Start-Sleep -Milliseconds 1800
$r1 = Rows 0
$head = ($r1.head -join '|')
Write-Host ('S1 head=' + $head)
Ok ($head -match [regex]::Escape((ZH 'txt_mr_sent_returned'))) 'S1 column "sent/returned" present on repair tab'
# 2026-09-21（UI 统一）：返回进度**不再单列**（已并入「送修/已返回」+「状态」列显示已结案）
#   ⇒ 断言它从列里消失、改为出现在**筛选行**（el-select 的占位符文本会在 innerText 里）
Ok (-not ($head -match [regex]::Escape((ZH 'txt_mr_progress')))) 'S1 column "progress" is gone (merged into sent/returned)'
Ok ((BodyHas (ZH 'txt_mr_progress')) -eq 'true') 'S1 "progress" is now a filter on the repair tab'
Ok ((BodyHas (ZH 'col_return_amount')) -eq 'true') 'S1 the amount column is renamed to 退货金额'
Ok ((Errs) -eq '[]') 'S1 no errors'

# =====================================================================
Step 'S2 add page: link material order + hints'
Ok ((ClickBtn 'btn_new_mr_repair') -match 'OK') 'S2 click new repair-return'
Start-Sleep -Milliseconds 3000
Ok ((BodyHas (ZH 'lbl_mr_order')) -eq 'true') 'S2 field "link material order" present'
Ok ((SelectLabelText 'lbl_mr_repair_supplier' (ZH 'val_supplier_jh')) -match 'OK') 'S2 pick supplier'
Start-Sleep -Milliseconds 1500
Ok ((BodyHas (ZH 'txt_mr_order_unlinked')) -eq 'true') 'S2 hint: unlinked -> track on this doc'
Ok ((SelectLabelContains 'lbl_mr_order' (ZH 'morder6') 2200) -match 'OK') 'S2 pick material order MWO-20260916001'
Start-Sleep -Milliseconds 1600
Ok ((BodyHas (ZH 'txt_mr_order_deduct')) -eq 'true') 'S2 hint: unfinished order -> deduct received qty'

# =====================================================================
Step 'S3 create draft (order linked, nothing deducted yet)'
$bItemRecv = D (OrderRecv 11)
$bItemRepair = D (OrderRepair 11)
$bStock = D (StockQty 37 25)
Write-Host ("BASE item11 recv=" + $bItemRecv + " repair=" + $bItemRepair + " wh37.m25=" + $bStock)
Ok ((SelectLabelText 'lbl_src_wh_out' (ZH 'wh_jiehe')) -match 'OK') 'S3 pick source warehouse'
Start-Sleep -Milliseconds 2200
Ok ((SetRowInput 0 0 '3') -match 'OK') 'S3 qty=3'
Ok ((ClickBtn 'btn_save_draft') -match 'OK') 'S3 save draft'
Start-Sleep -Milliseconds 3400
$rid = [int](MaxId 'outsource_material_return')
Ok ($rid -gt 0) ('S3 created id=' + $rid)
Ok ((D (SqlOne "SELECT material_order_id FROM outsource_material_return WHERE id=$rid")) -eq 6) 'S3 linked material order persisted'
Ok ((SqlOne "SELECT deducted_flag FROM outsource_material_return WHERE id=$rid") -eq '0') 'S3 deducted_flag=0 before audit'
Ok ((D (OrderRecv 11)) -eq $bItemRecv) 'S3 order received qty untouched before audit'

# =====================================================================
Step 'S4 audit (case 1: order RECEIVING -> deduct received, add repairing)'
Open ("/outsource/material-return/detail/$rid") 2800
Ok ((ClickBtn 'btn_audit') -match 'OK') 'S4 audit'
ConfirmBox 1500 | Out-Null
Start-Sleep -Milliseconds 3400
Write-Host ("S4 item11 recv=" + (OrderRecv 11) + " repair=" + (OrderRepair 11) + " wh37.m25=" + (StockQty 37 25))
Ok ((SqlOne "SELECT deducted_flag FROM outsource_material_return WHERE id=$rid") -eq '1') 'S4 deducted_flag=1 frozen'
Ok ((D (OrderRecv 11)) -eq ($bItemRecv - 3)) 'S4 order received qty -3 (net = received - sent)'
Ok ((D (OrderRepair 11)) -eq ($bItemRepair + 3)) 'S4 order repairing qty +3'
Ok ((D (StockQty 37 25)) -eq ($bStock - 3)) 'S4 source warehouse -3'

# =====================================================================
Step 'S5 detail shows link + deducted note + 3 / 0'
Ok ((BodyHas (ZH 'morder6')) -eq 'true') 'S5 linked order code shown'
Ok ((BodyHas (ZH 'txt_mr_deducted')) -eq 'true') 'S5 "deducted received qty" note shown'
Ok ((BodyHas (ZH 'txt_mr_unreturned')) -eq 'true') 'S5 unreturned 3 shown'

# =====================================================================
Step 'S6 register repair return -> order restored'
Ok ((ClickBtn 'btn_repair_return') -match 'OK') 'S6 open register dialog'
Start-Sleep -Milliseconds 1800
Ok ((SetRowInput 0 0 '3') -match 'OK') 'S6 repair qty=3'
Ok ((ClickDialogBtn 'btn_repair_confirm') -match 'OK') 'S6 confirm'
Start-Sleep -Milliseconds 3400
Write-Host ("S6 item11 recv=" + (OrderRecv 11) + " repair=" + (OrderRepair 11) + " wh37.m25=" + (StockQty 37 25))
Ok ((D (OrderRecv 11)) -eq $bItemRecv) 'S6 order received qty restored (+3 rollback)'
Ok ((D (OrderRepair 11)) -eq $bItemRepair) 'S6 order repairing qty back to 0'
Ok ((D (StockQty 37 25)) -eq $bStock) 'S6 material back into source warehouse'

# =====================================================================
Step 'S7 close (unreturned = 0) + close blocks further returns'
Ok ((ClickBtn 'btn_mr_close') -match 'OK') 'S7 click close'
ConfirmBox 1500 | Out-Null
Start-Sleep -Milliseconds 3200
Ok ((SqlOne "SELECT closed_flag FROM outsource_material_return WHERE id=$rid") -eq '1') 'S7 closed_flag=1'
Ok ((HasBtn 'btn_repair_return') -eq 'false') 'S7 register button hidden after close'
Ok ((HasBtn 'btn_mr_reopen') -eq 'true') 'S7 reopen button present after close'

# =====================================================================
Step 'S8 reopen -> cancel repair return -> un-audit -> cancel doc'
Ok ((ClickBtn 'btn_mr_reopen') -match 'OK') 'S8 reopen'
ConfirmBox 1500 | Out-Null
Start-Sleep -Milliseconds 3000
Ok ((SqlOne "SELECT closed_flag FROM outsource_material_return WHERE id=$rid") -eq '0') 'S8 closed_flag back to 0'
Ok ((ClickRowBtn 0 'btn_revoke') -match 'OK') 'S8 cancel repair return'
ConfirmBox 1500 | Out-Null
Start-Sleep -Milliseconds 3200
Ok ((D (OrderRecv 11)) -eq ($bItemRecv - 3)) 'S8 order received qty back to deducted state'
Ok ((D (OrderRepair 11)) -eq ($bItemRepair + 3)) 'S8 order repairing qty back to sent state'
Ok ((ClickBtn 'btn_unaudit') -match 'OK') 'S8 un-audit'
ConfirmBox 1500 | Out-Null
Start-Sleep -Milliseconds 3400
Ok ((D (OrderRecv 11)) -eq $bItemRecv) 'S8 un-audit rolled the order deduction back'
Ok ((D (OrderRepair 11)) -eq $bItemRepair) 'S8 repairing qty fully rolled back'
Ok ((SqlOne "SELECT deducted_flag FROM outsource_material_return WHERE id=$rid") -eq '0') 'S8 deducted_flag cleared'
Ok ((D (StockQty 37 25)) -eq $bStock) 'S8 stock fully restored'
Ok ((ClickBtn 'btn_cancel_doc') -match 'OK') 'S8 cancel doc'
ConfirmBox 1500 | Out-Null
Start-Sleep -Milliseconds 2200
Ok ((SqlOne "SELECT status FROM outsource_material_return WHERE id=$rid") -eq 'CANCELLED') 'S8 doc cancelled'

# =====================================================================
Step 'S9 material order detail shows the "repairing" column'
Open (ZH 'morder_url6') 3000
$r9 = Rows 0
$head9 = ($r9.head -join '|')
Write-Host ('S9 head=' + $head9)
Ok ($head9 -match [regex]::Escape((ZH 'txt_mo_repair_col'))) 'S9 order detail has column "repairing"'
Ok ((Errs) -eq '[]') 'S9 no errors on order detail'

# =====================================================================
Step 'S10 no page errors'
$e = Errs
Write-Host ('ERRS=' + $e)
Ok ($e -eq '[]') 'S9 no page/API errors during the flow'

Summary 'ui-e2e-15 material repair closed loop (linked order deduct + close)'
