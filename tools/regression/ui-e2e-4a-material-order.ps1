# Temp test 4a: material warehouse + material order -> audit -> material receive -> close
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
EnsureLogin
WatchErrors
function Step($n) { Write-Host ('--- STEP ' + $n) }

# ---------- 0. create our own material warehouse (INVENTORY/AUXILIARY) ----------
Step 'create own material warehouse'
Open '/outsource/material-warehouse' 2400
ClearErrs | Out-Null
Write-Host ('click new: ' + (ClickBtn 'btn_new'))
Start-Sleep -Milliseconds 1200
Write-Host ('fill name: ' + (FillLabel 'lbl_name' 'TEST-MAT-WH'))
Write-Host ('ok: ' + (ClickDialogBtn 'btn_ok'))
Start-Sleep -Milliseconds 1800
Write-Host ('msg=' + (Txt '.el-message'))
Open '/outsource/material-warehouse' 2000
Ok ((BodyHas 'TEST-MAT-WH') -match 'true') 'own material warehouse created and listed'
Write-Host ('errs=' + (Errs))

# ---------- 1. create material order ----------
Step 'create material order'
Open '/outsource/material-order' 2400
ClearErrs | Out-Null
Write-Host ('click new: ' + (ClickBtn 'btn_new'))
Start-Sleep -Milliseconds 1800
Write-Host ('path=' + (EvalJs 'String(location.pathname)'))
Write-Host ('supplier: ' + (SelectLabelContains 'lbl_supplier' (ZH 'val_factory')))
Write-Host ('add item: ' + (ClickBtn 'btn_add_material'))
Start-Sleep -Milliseconds 900
Write-Host ('open type sel: ' + (OpenRowSelect 0 0))
Start-Sleep -Milliseconds 1000
Write-Host ('pick type: ' + (PickOptionContains (ZH 'opt_mt_glass')))
Start-Sleep -Milliseconds 1000
Write-Host ('open material sel: ' + (OpenRowSelect 0 1))
Start-Sleep -Milliseconds 1200
Write-Host ('pick material: ' + (PickOptionContains (ZH 'val_material')))
Start-Sleep -Milliseconds 600
Write-Host ('qty: ' + (SetRowInput 0 2 '20'))
Write-Host ('price: ' + (SetRowInput 0 3 '10'))
Start-Sleep -Milliseconds 500
$dump = "(()=>{const vis=e=>e.getClientRects().length>0;const t=[...document.querySelectorAll('.el-table')].filter(vis)[0];const r=t.querySelectorAll('.el-table__body tbody tr')[0];return JSON.stringify({row:(r.innerText||'').replace(/\s+/g,' '),inputs:[...r.querySelectorAll('input:not([type=hidden])')].map(e=>e.value)})})()"
Write-Host ('ROWDUMP ' + (EvalJs $dump))
Write-Host ('save: ' + (ClickBtn 'btn_save'))
Start-Sleep -Milliseconds 2600
Write-Host ('msg=' + (Txt '.el-message') + ' path=' + (EvalJs 'String(location.pathname)') + ' errs=' + (Errs))
Open '/outsource/material-order' 2400
$r = Rows 0
Write-Host ('material orders=' + $r.n)
if ($r.n -gt 0) { Write-Host ('row0=' + ($r.rows[0] -join ' | ')) }
Ok ($r.n -gt 0) 'material order created'
if ($r.n -eq 0) { Summary 'material order chain'; exit 1 }

# ---------- 2. audit material order ----------
Step 'audit material order'
Write-Host ('click audit: ' + (ClickRowBtnContains 0 (ZH 'btn_audit')))
Write-Host ('confirm: ' + (ConfirmBox 1200))
Start-Sleep -Milliseconds 2400
Write-Host ('msg=' + (Txt '.el-message') + ' errs=' + (Errs))
Open '/outsource/material-order' 2200
$r = Rows 0
Write-Host ('row after audit=' + $(if ($r.n -gt 0) { ($r.rows[0] -join ' | ') } else { 'NONE' }))
Ok (($r.rows[0] -join ' ') -match (ZH 'st_receiving')) 'material order is RECEIVING after audit'

# ---------- 3. material receive via 物料收货 ----------
Step 'material receive'
Open '/outsource/material-order/delivery' 2600
$r = Rows 0
Write-Host ('receive list rows=' + $r.n + ' head=' + ($r.head -join '|'))
if ($r.n -gt 0) { Write-Host ('row0=' + ($r.rows[0] -join ' | ')) }
Write-Host ('click receive: ' + (ClickRowBtnContains 0 (ZH 'btn_receive')))
Start-Sleep -Milliseconds 3000
Write-Host ('path=' + (EvalJs 'String(location.pathname)'))
Write-Host ('dialog visible: ' + (EvalJs "String([...document.querySelectorAll('.el-dialog')].filter(e=>e.getClientRects().length>0).length)"))
Write-Host ('open wh sel: ' + (DialogOpenSelect 0))
Start-Sleep -Milliseconds 1400
Write-Host ('pick wh: ' + (PickOptionContains 'TEST-MAT-WH'))
Start-Sleep -Milliseconds 500
Write-Host ('qty: ' + (DialogSetInput 1 '20'))
Start-Sleep -Milliseconds 500
Write-Host ('confirm deliver: ' + (ClickDialogBtn 'btn_confirm_deliver'))
Start-Sleep -Milliseconds 3000
Write-Host ('msg=' + (Txt '.el-message') + ' errs=' + (Errs))
$r = Rows 0
Write-Host ('delivery records=' + $r.n + ' head=' + ($r.head -join '|'))
foreach ($rw in $r.rows) { Write-Host ('rec=' + ($rw -join ' | ')) }
Write-Host ('page has 20: ' + (BodyHas '20'))
Open '/outsource/material-order' 2400
$mo = Rows 0
Write-Host ('order after receive=' + $(if ($mo.n -gt 0) { ($mo.rows[0] -join ' | ') } else { 'NONE' }))

# ---------- 4. check stock + payable ----------
Step 'stock and payable after receive'
Open '/outsource/material-warehouse' 2600
Write-Host ('material wh page has stock link? ' + (BodyHas 'TEST-MAT-WH'))
$r = Rows 0
Write-Host ('mat wh rows=' + $r.n + ' head=' + ($r.head -join '|'))
Write-Host ('row0=' + $(if ($r.n -gt 0) { ($r.rows[0] -join ' | ') } else { 'NONE' }))
Open '/finance/payable' 2600
$py = Rows 0
foreach ($rw in $py.rows) { $line = ($rw -join ' '); if ($line -match '200' -or $line -match 'TEST') { Write-Host ('payable=' + $line) } }
Write-Host ('errs=' + (Errs))
Summary 'material order chain'
