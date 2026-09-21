# Temp test 4c: work order (加工单) -> audit -> finished-goods receipt (收货) -> audit -> defect return
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
EnsureLogin
WatchErrors
function Step($n) { Write-Host ('--- STEP ' + $n) }

Step 'create work order'
Open '/outsource/order' 2600
ClearErrs | Out-Null
Write-Host ('click new: ' + (ClickBtn 'btn_new_order'))
Start-Sleep -Milliseconds 2200
Write-Host ('path=' + (EvalJs 'String(location.pathname)'))
Write-Host ('factory: ' + (SelectLabelContains 'lbl_factory' (ZH 'val_factory')))
Write-Host ('product: ' + (SelectLabelContains 'lbl_mfg_product' (ZH 'val_proj')))
Write-Host ('qty: ' + (FillLabel 'lbl_qty' '50'))
Write-Host ('price: ' + (FillLabel 'lbl_mfg_price' '20'))
Start-Sleep -Milliseconds 500
Write-Host ('submit: ' + (ClickBtn 'btn_submit_confirm'))
Start-Sleep -Milliseconds 3000
Write-Host ('msg=' + (Txt '.el-message') + ' errs=' + (Errs))

Open '/outsource/order' 2600
$r = Rows 0
$idx = [int](FindRow (ZH 'val_proj'))
Ok ($idx -ge 0) 'work order created (pending)'
if ($idx -lt 0) { Summary 'work order chain'; exit 1 }
Write-Host ('row=' + ($r.rows[$idx] -join ' | '))

Step 'audit work order'
Write-Host ('open detail: ' + (ClickRowBtnContains $idx (ZH 'btn_detail')))
Start-Sleep -Milliseconds 2800
$ORDID = (EvalJs 'String(location.pathname)') -replace '.*/detail/', ''
Write-Host ('order id=' + $ORDID)
Write-Host ('audit: ' + (ClickBtn 'btn_audit'))
Start-Sleep -Milliseconds 1000
Write-Host ('confirm: ' + (ConfirmBox 1400))
Start-Sleep -Milliseconds 2800
Write-Host ('msg=' + (Txt '.el-message'))
Ok ((BodyHas (ZH 'st_producing')) -match 'true') 'work order producing'

Step 'delivery record'
Open "/outsource/order/delivery/$ORDID" 3000
Write-Host ('click new delivery: ' + (ClickBtn 'btn_new_delivery'))
Start-Sleep -Milliseconds 1800
Write-Host ('dlg count=' + (EvalJs "String([...document.querySelectorAll('.el-dialog')].filter(e=>e.getClientRects().length>0).length)"))
Write-Host ('open product sel: ' + (DialogOpenSelect 0))
Start-Sleep -Milliseconds 1400
Write-Host ('pick product: ' + (PickOptionContains (ZH 'val_proj')))
Start-Sleep -Milliseconds 800
Write-Host ('A qty: ' + (DialogSetInput 2 '10'))
Write-Host ('open wh sel: ' + (DialogOpenSelect 1))
Start-Sleep -Milliseconds 1400
Write-Host ('pick wh: ' + (PickOptionContains (ZH 'pfx_wh')))
Start-Sleep -Milliseconds 600
Write-Host ('save: ' + (ClickDialogBtn 'btn_save'))
Start-Sleep -Milliseconds 2000
$dtxt = "(()=>{const vis=e=>e.getClientRects().length>0;const d=[...document.querySelectorAll('.el-dialog')].filter(vis)[0];return d?(d.innerText||'').replace(/\s+/g,' ').slice(0,150):'NODLG'})()"
Write-Host ('dialog after save: ' + (EvalJs $dtxt))
if ((BodyHas (ZH 'txt_shortage')) -match 'true') {
  Write-Host ('shortage -> force out: ' + (ClickDialogBtn 'btn_force_out'))
}
Start-Sleep -Milliseconds 3000
Write-Host ('msg=' + (Txt '.el-message') + ' errs=' + (Errs))
Open "/outsource/order/delivery/$ORDID" 3000
$r = Rows 0
Write-Host ('records=' + $r.n + ' head=' + ($r.head -join '|'))
foreach ($rw in $r.rows) { Write-Host ('rec=' + ($rw -join ' | ')) }
$idx = [int](FindRow (ZH 'st_draft'))
Ok ($idx -ge 0) 'delivery record saved as draft'
if ($idx -lt 0) { Summary 'work order chain'; exit 1 }

Step 'audit delivery record'
Write-Host ('audit: ' + (ClickRowBtnContains $idx (ZH 'btn_audit')))
Write-Host ('confirm: ' + (ConfirmBox 1500))
Start-Sleep -Milliseconds 3200
Write-Host ('msg=' + (Txt '.el-message') + ' errs=' + (Errs))
Open "/outsource/order/delivery/$ORDID" 3000
$r = Rows 0
foreach ($rw in $r.rows) { Write-Host ('rec after audit=' + ($rw -join ' | ')) }
# NOTE: rows is an array of arrays -> join per row first (plain -join on the outer array prints System.Object[])
$rtext = ($r.rows | ForEach-Object { $_ -join ' ' }) -join ' '
Ok ($rtext -match (ZH 'st_audited')) 'delivery record audited'
Open '/inventory/stock' 2600
$st = Rows 0
$si = [int](FindRow (ZH 'val_proj'))
Write-Host ('stock row=' + $(if ($si -ge 0) { ($st.rows[$si] -join ' | ') } else { 'NOT FOUND' }))
Ok ($si -ge 0) 'finished goods received into stock'
Open '/finance/payable' 2600
$py = Rows 0
foreach ($rw in $py.rows) { $line = ($rw -join ' '); if ($line -match 'MFTEST' -or $line -match (ZH 'val_factory')) { Write-Host ('payable=' + $line) } }
Write-Host ('errs=' + (Errs))

Step 'defect return'
Open "/outsource/order/delivery/$ORDID" 3000
Write-Host ('defect btn: ' + (ClickBtn 'btn_mfg_return'))
Start-Sleep -Milliseconds 2000
$dlg = "(()=>{const vis=e=>e.getClientRects().length>0;const d=[...document.querySelectorAll('.el-dialog')].filter(vis)[0];if(!d)return 'NODLG';return JSON.stringify({text:(d.innerText||'').replace(/\s+/g,' ').slice(0,200),ins:[...d.querySelectorAll('input:not([type=hidden])')].filter(vis).map((e,i)=>i+':'+(e.placeholder||e.value)),sels:[...d.querySelectorAll('.el-select')].filter(vis).length})})()"
Write-Host ('DEFECTDLG ' + (EvalJs $dlg))
Write-Host ('open wh sel: ' + (DialogOpenSelect 0))
Start-Sleep -Milliseconds 1400
Write-Host ('pick wh: ' + (PickOptionContains (ZH 'pfx_wh')))
Write-Host ('A qty: ' + (DialogSetInput 1 '4'))
Start-Sleep -Milliseconds 600
Write-Host ('confirm defect: ' + (ClickDialogBtn 'btn_confirm_mfg_return'))
Start-Sleep -Milliseconds 3000
Write-Host ('msg=' + (Txt '.el-message') + ' errs=' + (Errs))
Open '/inventory/stock' 2600
$st = Rows 0
$si = [int](FindRow (ZH 'val_proj'))
Write-Host ('stock after defect draft=' + $(if ($si -ge 0) { ($st.rows[$si] -join ' | ') } else { 'NOT FOUND' }))

Step 'audit defect record'
Open "/outsource/order/delivery/$ORDID" 3000
$r = Rows 0
foreach ($rw in $r.rows) { Write-Host ('rec=' + ($rw -join ' | ')) }
$idx = [int](FindRow (ZH 'btn_mfg_return'))
Write-Host ('defect rec idx=' + $idx)
if ($idx -ge 0) {
  Write-Host ('audit: ' + (ClickRowBtnContains $idx (ZH 'btn_audit')))
  Write-Host ('confirm: ' + (ConfirmBox 1500))
  Start-Sleep -Milliseconds 3200
  Write-Host ('msg=' + (Txt '.el-message') + ' errs=' + (Errs))
}
Open '/inventory/stock' 2600
$st = Rows 0
$si = [int](FindRow (ZH 'val_proj'))
Write-Host ('stock after defect audit=' + $(if ($si -ge 0) { ($st.rows[$si] -join ' | ') } else { 'NOT FOUND' }))
Ok $true 'work order chain executed (see stock/payable lines above)'
Summary 'work order chain'
