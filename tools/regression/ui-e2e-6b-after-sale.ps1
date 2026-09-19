# Temp test 6b: return-sort (退货整理) + sale exchange (销售换货)
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
EnsureLogin
WatchErrors
function Step($n) { Write-Host ('--- STEP ' + $n) }
$P = ZH 'val_proj'
$rowsDump = "(()=>{const vis=e=>e.getClientRects().length>0;const dlgs=[...document.querySelectorAll('.el-dialog')].filter(vis);const root=dlgs.length?dlgs[dlgs.length-1]:document;const t=[...root.querySelectorAll('.el-table')].filter(vis)[0];if(!t)return 'NOTABLE';const rs=[...t.querySelectorAll('.el-table__body tbody tr')];return JSON.stringify(rs.map(r=>[(r.innerText||'').replace(/\s+/g,' ').slice(0,100),[...r.querySelectorAll('input:not([type=hidden])')].map(e=>e.value)]))})()"

Step 'return sort'
Open '/inventory/return-sort?tab=bills' 2600
ClearErrs | Out-Null
Write-Host ('click new: ' + (ClickBtn 'btn_new_sort'))
Start-Sleep -Milliseconds 2200
Write-Host ('path=' + (EvalJs 'String(location.pathname)'))
Write-Host ('items=' + (EvalJs "(()=>{const vis=e=>e.getClientRects().length>0;return JSON.stringify([...document.querySelectorAll('.el-form-item')].filter(vis).map(it=>{const l=it.querySelector('.el-form-item__label');return (l?l.innerText.trim():'?')+'='+(it.querySelector('.el-select')?'select':'input')}))})()"))
Write-Host ('src wh: ' + (SelectLabelContains 'lbl_source_warehouse' (ZH 'pfx_wh')))
Start-Sleep -Milliseconds 800
Write-Host ('load pending: ' + (ClickBtn 'btn_load_pending'))
Start-Sleep -Milliseconds 2600
Write-Host ('rows: ' + (EvalJs $rowsDump))
Write-Host ('fill A: ' + (SetRowInput 0 0 '1'))
Write-Host ('fill bad: ' + (SetRowInput 0 3 '0'))
Start-Sleep -Milliseconds 500
Write-Host ('save: ' + (ClickBtn 'btn_save'))
Start-Sleep -Milliseconds 2600
Write-Host ('msg=' + (Txt '.el-message') + ' path=' + (EvalJs 'String(location.pathname)') + ' errs=' + (Errs))
Open '/inventory/return-sort?tab=bills' 2600
$r = Rows 0
Write-Host ('sort rows=' + $r.n + ' head=' + ($r.head -join '|'))
if ($r.n -gt 0) { Write-Host ('row0=' + ($r.rows[0] -join ' | ')) }
if ($r.n -gt 0) {
  Write-Host ('audit: ' + (ClickRowBtnContains 0 (ZH 'btn_audit')))
  Start-Sleep -Milliseconds 1000
  Write-Host ('confirm: ' + (ConfirmBox 1300))
  Start-Sleep -Milliseconds 2800
  Write-Host ('msg=' + (Txt '.el-message') + ' errs=' + (Errs))
  Open '/inventory/stock' 2500
  $st = Rows 0
  foreach ($rw in $st.rows) { if (($rw -join ' ') -match $P) { Write-Host ('stock=' + ($rw -join ' | ')) } }
}

Step 'sale exchange'
Open '/sale/exchange' 2600
Write-Host ('click new: ' + (ClickBtn 'btn_new_exchange'))
Start-Sleep -Milliseconds 2400
Write-Host ('path=' + (EvalJs 'String(location.pathname)'))
Write-Host ('items=' + (EvalJs "(()=>{const vis=e=>e.getClientRects().length>0;return JSON.stringify([...document.querySelectorAll('.el-form-item')].filter(vis).map(it=>{const l=it.querySelector('.el-form-item__label');return (l?l.innerText.trim():'?')+'='+(it.querySelector('.el-select')?'select':'input')}))})()"))
Write-Host ('customer: ' + (SelectLabelContains 'lbl_customer' (ZH 'val_customer')))
Start-Sleep -Milliseconds 900
Write-Host ('src order: ' + (SelectLabelContains 'lbl_source_order' 'XS-'))
Start-Sleep -Milliseconds 1800
Write-Host ('rows: ' + (EvalJs $rowsDump))
Write-Host ('in wh: ' + (SelectLabelContains 'lbl_in_wh' (ZH 'pfx_wh')))
Start-Sleep -Milliseconds 700
Write-Host ('out wh: ' + (SelectLabelContains 'lbl_out_wh' (ZH 'pfx_wh')))
Start-Sleep -Milliseconds 700
Write-Host ('b qty: ' + (SetRowInput 0 0 '1'))
Write-Host ('out qty: ' + (SetRowInput 0 1 '1'))
Start-Sleep -Milliseconds 500
Write-Host ('rows2: ' + (EvalJs $rowsDump))
Write-Host ('save: ' + (ClickBtn 'btn_save'))
Start-Sleep -Milliseconds 2600
Write-Host ('msg=' + (Txt '.el-message') + ' path=' + (EvalJs 'String(location.pathname)') + ' errs=' + (Errs))
Open '/sale/exchange' 2600
$r = Rows 0
Write-Host ('exchange rows=' + $r.n + ' head=' + ($r.head -join '|'))
if ($r.n -gt 0) { Write-Host ('row0=' + ($r.rows[0] -join ' | ')) }
if ($r.n -gt 0) {
  Write-Host ('audit: ' + (ClickRowBtnContains 0 (ZH 'btn_audit')))
  Start-Sleep -Milliseconds 1000
  Write-Host ('confirm: ' + (ConfirmBox 1300))
  Start-Sleep -Milliseconds 2800
  Write-Host ('msg=' + (Txt '.el-message') + ' errs=' + (Errs))
  Open '/inventory/stock' 2500
  $st = Rows 0
  foreach ($rw in $st.rows) { if (($rw -join ' ') -match $P) { Write-Host ('stock=' + ($rw -join ' | ')) } }
}
Summary 'after-sale chain'
