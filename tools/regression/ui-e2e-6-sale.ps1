# Temp test 6: sales chain (sale order -> audit -> sale return -> audit)
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
EnsureLogin
WatchErrors
function Step($n) { Write-Host ('--- STEP ' + $n) }
$P = ZH 'val_proj'

Step 'create sale order'
Open '/inventory/sale' 2600
ClearErrs | Out-Null
Write-Host ('click new: ' + (ClickBtn 'btn_new'))
Start-Sleep -Milliseconds 2200
Write-Host ('path=' + (EvalJs 'String(location.pathname)'))
Write-Host ('customer: ' + (SelectLabelContains 'lbl_customer' (ZH 'val_customer')))
Start-Sleep -Milliseconds 700
Write-Host ('out wh: ' + (SelectLabelContains 'lbl_out_wh' (ZH 'pfx_wh')))
Start-Sleep -Milliseconds 700
$rc0 = Rows 0
Write-Host ('initial rows=' + $rc0.n)
if ($rc0.n -lt 1) { Write-Host ('add row: ' + (ClickBtn 'btn_add_product')); Start-Sleep -Milliseconds 900 }
Write-Host ('open product sel: ' + (OpenRowSelect 0 0))
Start-Sleep -Milliseconds 1400
Write-Host ('pick product: ' + (PickOptionContains $P))
Start-Sleep -Milliseconds 700
Write-Host ('open quality sel: ' + (OpenRowSelect 0 1))
Start-Sleep -Milliseconds 1000
Write-Host ('pick quality: ' + (PickOptionContains (ZH 'opt_q_a')))
Start-Sleep -Milliseconds 600
Write-Host ('qty: ' + (SetRowInput 0 2 '5'))
Write-Host ('price: ' + (SetRowInput 0 3 '100'))
Start-Sleep -Milliseconds 500
$js = "(()=>{const vis=e=>e.getClientRects().length>0;const t=[...document.querySelectorAll('.el-table')].filter(vis)[0];const r=t.querySelectorAll('.el-table__body tbody tr')[0];return (r.innerText||'').replace(/\s+/g,' ')})()"
Write-Host ('ROW=' + (EvalJs $js))
Write-Host ('save: ' + (ClickBtn 'btn_save'))
Start-Sleep -Milliseconds 2600
Write-Host ('msg=' + (Txt '.el-message') + ' path=' + (EvalJs 'String(location.pathname)') + ' errs=' + (Errs))
Open '/inventory/sale' 2600
$r = Rows 0
$idx = [int](FindRow (ZH 'val_customer'))
Write-Host ('orders=' + $r.n + ' idx=' + $idx)
Ok ($idx -ge 0) 'sale order created'
if ($idx -lt 0) { Summary 'sales chain'; exit 1 }
Write-Host ('row=' + ($r.rows[$idx] -join ' | '))

Step 'audit sale order'
Write-Host ('audit: ' + (ClickRowBtnContains $idx (ZH 'btn_audit')))
Start-Sleep -Milliseconds 1000
Write-Host ('confirm: ' + (ConfirmBox 1300))
Start-Sleep -Milliseconds 3000
Write-Host ('msg=' + (Txt '.el-message') + ' errs=' + (Errs))
Open '/inventory/sale' 2600
$r = Rows 0
$idx = [int](FindRow (ZH 'val_customer'))
Write-Host ('row after audit=' + $(if ($idx -ge 0) { ($r.rows[$idx] -join ' | ') } else { 'NOT FOUND' }))
Open '/inventory/stock' 2600
$st = Rows 0
$si = [int](FindRow $P)
Write-Host ('stock after sale=' + $(if ($si -ge 0) { ($st.rows[$si] -join ' | ') } else { 'NOT FOUND' }))
Open '/finance/receivable' 2600
$rc = Rows 0
Write-Host ('receivable rows=' + $rc.n + ' head=' + ($rc.head -join '|'))
foreach ($rw in $rc.rows) { Write-Host ('rec=' + ($rw -join ' | ')) }

Step 'sale return'
Open '/sale/return' 2600
Write-Host ('click new: ' + (ClickBtn 'btn_new_return'))
Start-Sleep -Milliseconds 2400
Write-Host ('path=' + (EvalJs 'String(location.pathname)'))
Write-Host ('customer: ' + (SelectLabelContains 'lbl_customer' (ZH 'val_customer')))
Start-Sleep -Milliseconds 800
Write-Host ('return wh: ' + (SelectLabelContains 'lbl_return_warehouse' (ZH 'pfx_wh')))
Start-Sleep -Milliseconds 800
$rb = Rows 0
Write-Host ('return initial rows=' + $rb.n)
if ($rb.n -lt 1) { Write-Host ('add row: ' + (ClickBtn 'btn_add_detail')); Start-Sleep -Milliseconds 900 }
Write-Host ('open product sel: ' + (OpenRowSelect 0 0))
Start-Sleep -Milliseconds 1400
Write-Host ('pick product: ' + (PickOptionContains $P))
Start-Sleep -Milliseconds 700
Write-Host ('qty: ' + (SetRowInput 0 2 '2'))
Start-Sleep -Milliseconds 400
Write-Host ('save: ' + (ClickBtn 'btn_save'))
Start-Sleep -Milliseconds 2600
Write-Host ('msg=' + (Txt '.el-message') + ' path=' + (EvalJs 'String(location.pathname)') + ' errs=' + (Errs))
Open '/sale/return' 2600
$r = Rows 0
Write-Host ('returns=' + $r.n + ' head=' + ($r.head -join '|'))
if ($r.n -gt 0) { Write-Host ('row0=' + ($r.rows[0] -join ' | ')) }
Ok ($r.n -gt 0) 'sale return created'
if ($r.n -eq 0) { Summary 'sales chain'; exit 1 }

Step 'audit sale return'
Write-Host ('audit: ' + (ClickRowBtnContains 0 (ZH 'btn_audit')))
Start-Sleep -Milliseconds 1000
Write-Host ('confirm: ' + (ConfirmBox 1300))
Start-Sleep -Milliseconds 3000
Write-Host ('msg=' + (Txt '.el-message') + ' errs=' + (Errs))
Open '/sale/return' 2600
$r = Rows 0
Write-Host ('row after audit=' + $(if ($r.n -gt 0) { ($r.rows[0] -join ' | ') } else { 'NONE' }))
Open '/inventory/stock' 2600
$st = Rows 0
$si = [int](FindRow $P)
Write-Host ('stock after return=' + $(if ($si -ge 0) { ($st.rows[$si] -join ' | ') } else { 'NOT FOUND' }))
Write-Host ('errs=' + (Errs))
Summary 'sales chain'
