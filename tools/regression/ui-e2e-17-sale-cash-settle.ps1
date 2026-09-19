# UI case 17 (2026-09-18): sale order CASH settlement through the FRONTEND.
# Flow: new sale order (settle type = cash, cash account auto-filled) -> save -> list shows the cash badge ->
#       detail audit -> auto DRAFT receipt appears in detail (linked-receipt row, SK-...) and in /finance/receipt
#       (source column = XS-...) -> audit the receipt -> un-audit sale order is BLOCKED ->
#       un-audit receipt -> un-audit sale order (auto-cancels the draft receipt) -> cancel sale.
# Prereq: ui-e2e-2-master.ps1 (customer/product) + ui-seed-sale.ps1 (finished warehouse/cash account/stock).
# ASCII ONLY: PS 5.1 reads non-BOM files as ANSI, and all Chinese must come from ui-e2e-zh.json.
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
EnsureLogin
WatchErrors
function Step($n) { Write-Host ('--- STEP ' + $n) }
$P = ZH 'val_product'
$CashTxT = ZH 'opt_settle_cash'

# Resolve OUR doc by code instead of by row index / 'SK-' prefix -- a re-run used to pick up
# its own leftover (a CANCELLED order from the previous run) and then fail several assertions downstream.
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
function SqlOne([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -N -B -e $q 2>$null
  $l = @($o); if ($l.Count -lt 1) { return '' }; return ("$($l[0])").Trim()
}

function FieldValueByLabel([string]$labelText) {
  # el-select renders the picked label into .el-select (the inner input is often empty) -> read the select text
  $b = B64 $labelText
  $js = "(()=>{const T=b=>new TextDecoder().decode(Uint8Array.from(atob(b),c=>c.charCodeAt(0)));const L=T('$b');const fs=[...document.querySelectorAll('.el-form-item')];const f=fs.find(x=>((x.querySelector('.el-form-item__label')||{}).innerText||'').indexOf(L)>=0);if(!f)return 'NOITEM';const s=f.querySelector('.el-select');if(s)return (s.innerText||'').replace(/\s+/g,' ').trim();const i=f.querySelector('input');return i?(i.value||''):'NOINPUT'})()"
  return (EvalJs $js)
}
function FindRowIdxBy([string]$needle) {
  $js = "(()=>{const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);const t=ts[0];if(!t)return '-1';const rs=[...t.querySelectorAll('.el-table__body tbody tr')];for(let i=0;i<rs.length;i++){if((rs[i].innerText||'').indexOf('" + $needle + "')>=0)return String(i)}return '-1'})()"
  return [int](EvalJs $js)
}
function RowText([int]$i) {
  $js = "(()=>{const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);const t=ts[0];if(!t)return 'NOTABLE';const rs=[...t.querySelectorAll('.el-table__body tbody tr')];if(rs.length<=$i)return 'NOROW';return (rs[$i].innerText||'').replace(/\s+/g,' ')})()"
  return (EvalJs $js)
}

Step 'create sale order with CASH settlement'
Open '/inventory/sale' 2600
ClearErrs | Out-Null
Write-Host ('click new: ' + (ClickBtn 'btn_new'))
Start-Sleep -Milliseconds 2400
Write-Host ('customer: ' + (SelectLabelContains 'lbl_customer' (ZH 'val_customer')))
Start-Sleep -Milliseconds 800
Write-Host ('out wh: ' + (SelectLabelContains 'lbl_out_wh' (ZH 'pfx_wh')))
Start-Sleep -Milliseconds 800
# settle type is a SWITCH now (add.vue changed the same day), not a dropdown => click the switch.
# The old SelectLabelText could not find a select and cascaded into every later assertion.
$jsSettle = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const L=T('" + (B64 (ZH 'lbl_settle_type')) + "');const it=[...document.querySelectorAll('.el-form-item')].filter(e=>e.getClientRects().length>0).find(e=>{const b=e.querySelector('.el-form-item__label');return b&&(b.innerText||'').trim()===L});if(!it)return 'NOLABEL';const s=it.querySelector('.el-switch');if(!s)return 'NOSWITCH';if(s.classList.contains('is-checked'))return 'ALREADY_CASH';s.click();return 'OK_CASH'})()"
Write-Host ('settle type: ' + (EvalJs $jsSettle))
Start-Sleep -Milliseconds 1400
$accVal = FieldValueByLabel (ZH 'lbl_settle_account')
Write-Host ('settle account value = [' + $accVal + ']')
Ok ($accVal -ne 'NOITEM') 'CASH settlement shows the account field'
Ok ($accVal -match 'CASH-01') 'cash account auto-filled as CASH-01'

Write-Host ('open product sel: ' + (OpenRowSelect 0 0))
Start-Sleep -Milliseconds 1500
Write-Host ('pick product: ' + (PickOptionContains $P))
Start-Sleep -Milliseconds 800
Write-Host ('open quality sel: ' + (OpenRowSelect 0 1))
Start-Sleep -Milliseconds 1000
Write-Host ('pick quality: ' + (PickOptionContains (ZH 'opt_q_a')))
Start-Sleep -Milliseconds 700
Write-Host ('qty: ' + (SetRowInput 0 2 '1'))
Write-Host ('price: ' + (SetRowInput 0 3 '10'))
Start-Sleep -Milliseconds 600
Write-Host ('save: ' + (ClickBtn 'btn_save'))
Start-Sleep -Milliseconds 2500
# On stock shortage the save pops a second confirm ("still save the order") -> must be confirmed,
# otherwise the submit never leaves the browser (verified: no INSERT in the server log).
$forceB = B64 (ZH 'btn_stock_force')
$jsForce = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const L=T('$forceB');const b=[...document.querySelectorAll('button')].filter(x=>x.getClientRects().length>0).find(x=>((x.innerText||'').trim())===L);if(!b)return 'NOFORCE';b.click();return 'FORCED'})()"
# the popup only appears AFTER the async stock-check returns (measured >2.5s) -> poll up to ~8s
$forced = ''
for ($i = 0; $i -lt 10; $i++) {
  Start-Sleep -Milliseconds 800
  $forced = (EvalJs $jsForce)
  if ("$forced" -match 'FORCED') { break }
}
Write-Host ('stock-shortage confirm: ' + $forced)
Start-Sleep -Milliseconds 3000
Write-Host ('msg=' + (Txt '.el-message') + ' errs=' + (Errs))
# F6-2 item 3: our freshly created doc (newest CASH sale order) -- fail LOUDLY if the save did not take effect
$ocode = SqlOne "SELECT code FROM sale_order WHERE settle_type='CASH' ORDER BY id DESC LIMIT 1"
$oid = SqlOne "SELECT id FROM sale_order WHERE code='$ocode'"
$ostatus = SqlOne "SELECT status FROM sale_order WHERE code='$ocode'"
Write-Host ('created order: code=' + $ocode + ' id=' + $oid + ' status=' + $ostatus)
Ok (($ocode -match '^XS-') -and ($oid -ne '') -and ($ostatus -eq 'DRAFT')) ('cash sale order created (DRAFT) -> ' + $ocode)
if ($ostatus -ne 'DRAFT') {
  # diagnosis: the submit never reached the backend (no INSERT in the server log) -- dump why
  $diag = EvalJs "(()=>{const E=[...document.querySelectorAll('.el-form-item__error')].map(e=>(e.innerText||'').trim());const B=[...document.querySelectorAll('button')].filter(b=>b.getClientRects().length>0).map(b=>((b.innerText||'').trim())+(b.disabled?'[DISABLED]':''));const R=document.querySelectorAll('.el-table__body tbody tr').length;return JSON.stringify({formErrors:E,buttons:B.slice(0,12),itemRows:R})})()"
  Write-Host ('DIAG ' + $diag)
}
if (-not $oid) { Summary 'sale cash settle UI'; exit 1 }

Step 'list row shows settle type'
Open '/inventory/sale' 2600
$idx = FindRowIdxBy $ocode
Write-Host ('order row idx = ' + $idx + ' (code=' + $ocode + ')')
Ok ($idx -ge 0) 'sale order listed'
if ($idx -lt 0) { Summary 'sale cash settle UI'; exit 1 }
$row = RowText $idx
Write-Host ('row=' + $row)
Ok ($row -match [regex]::Escape($CashTxT)) 'list row shows CASH as settle type'

Step 'detail: before/after audit'
Write-Host ('open detail: ' + (ClickRowBtnContains $idx (ZH 'btn_detail')))
Start-Sleep -Milliseconds 3200
$oidUrl = ((EvalJs 'String(location.pathname)') -replace '.*/detail/', '').Trim()
Write-Host ('order id (url) = ' + $oidUrl)
Ok ($oidUrl -eq $oid) ('opened detail is our doc (' + $oidUrl + ')')
Ok ((BodyHas (ZH 'lbl_settle_type')) -match 'true') 'detail shows the settle-type row'
Write-Host ('audit: ' + (ClickBtn 'btn_audit'))
Start-Sleep -Milliseconds 1200
Write-Host ('confirm: ' + (ConfirmBox 1500))
Start-Sleep -Milliseconds 3200
Write-Host ('msg=' + (Txt '.el-message') + ' errs=' + (Errs))
Open ("/inventory/sale/detail/" + $oid) 3000
Ok ((BodyHas (ZH 'txt_linked_receipt')) -match 'true') 'detail shows the linked-receipt row after audit'
Ok ((BodyHas 'SK-') -match 'true') 'auto-generated DRAFT receipt SK-... shown'

Step 'receipt list: source column points back to the sale order'
Open '/finance/receipt' 2800
$rcode = SqlOne ("SELECT code FROM finance_receipt WHERE source_bill_no='" + $ocode + "' ORDER BY id DESC LIMIT 1")
$ri = FindRowIdxBy $rcode   # locate by our own receipt code (most precise)
Write-Host ('receipt row idx = ' + $ri + ' head=' + ((Rows 0).head -join '|'))
Ok ($ri -ge 0) 'auto receipt appears in /finance/receipt'
if ($ri -ge 0) {
  $rrow = RowText $ri
  Write-Host ('receipt row=' + $rrow)
  Ok ($rrow -match 'XS-') 'receipt source column shows the sale order code'
}

Step 'auto receipt is AUDITED right away (cash = instant settle)'
# NOTE: cash settlement rule = the auto receipt is created ALREADY AUDITED (not a draft),
# see verify-cash-immediate-settle.ps1 (22/0). The old flow audited it manually -- obsolete.
if ($ri -ge 0) {
  $rst = SqlOne ("SELECT status FROM finance_receipt WHERE code='" + $rcode + "'")
  Write-Host ('receipt status (db) = ' + $rst)
  Ok ($rst -eq 'AUDITED') ('auto receipt audited on sale audit (got ' + $rst + ')')
  Ok ((RowText $ri) -match [regex]::Escape((ZH 'st_audited'))) 'receipt list shows the auto receipt as audited'
}

Step 'un-audit sale order -> allowed for cash, auto receipt voided'
# NOTE: cash orders stay reversible; un-audit auto-reverses (receipt CANCELLED + receivable rolled back).
Open ("/inventory/sale/detail/" + $oid) 3000
Write-Host ('un-audit: ' + (ClickBtn 'btn_unaudit'))
Start-Sleep -Milliseconds 1200
Write-Host ('confirm: ' + (ConfirmBox 1500))
Start-Sleep -Milliseconds 3000
Write-Host ('msg=' + (Txt '.el-message') + ' errs=' + (Errs))
Ok ((SqlOne ("SELECT status FROM sale_order WHERE id=" + $oid)) -eq 'DRAFT') 'sale order back to DRAFT (cash orders stay reversible)'
Ok ((SqlOne ("SELECT status FROM finance_receipt WHERE code='" + $rcode + "'")) -eq 'CANCELLED') 'auto receipt voided for the audit trail'
Open ("/inventory/sale/detail/" + $oid) 2600
Ok ((BodyHas (ZH 'st_draft')) -match 'true') 'detail shows DRAFT after un-audit'

Step 'cancel the sale order (cleanup)'
# the un-audit above already rolled the order back to DRAFT and voided the receipt -> just cancel it here
Open ("/inventory/sale/detail/" + $oid) 3000
Write-Host ('cancel: ' + (ClickBtn 'btn_cancel_doc'))
Start-Sleep -Milliseconds 1200
Write-Host ('confirm: ' + (ConfirmBox 1500))
Start-Sleep -Milliseconds 2800
Write-Host ('msg=' + (Txt '.el-message') + ' errs=' + (Errs))
Open ("/inventory/sale/detail/" + $oid) 2600
Ok ((BodyHas (ZH 'st_cancelled')) -match 'true') 'sale order cancelled (cleanup)'
Summary 'sale cash settle UI'
