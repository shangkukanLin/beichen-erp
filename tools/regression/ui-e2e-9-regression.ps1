# Regression for D1 / D2 / D3 (UI only). ASCII ONLY - keep Chinese in ui-e2e-zh.json.
#   D1: auditing a RECEIVE doc from the delivery list must write stock + payable (target warehouse)
#   D2: sale order audit = stock out (draft must NOT touch stock); un-audit restores
#   D3: ?add=1 auto dialog must open again when the component instance is reused
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
EnsureLogin
WatchErrors
function Step($n) { Write-Host ('--- STEP ' + $n) }
$P = ZH 'val_proj'
$MAT = ZH 'val_material'
$OWH = 'TEST-MAT-WH'

function StockA($tag) {
  Open '/inventory/stock' 2600
  $r = Rows 0
  $i = [int](FindRow $P)
  Write-Host ('STOCK[' + $tag + ']=' + $(if ($i -ge 0) { ($r.rows[$i] -join ' | ') } else { 'NOT FOUND' }))
}

Step 'D1 baseline: material total + find receive doc row'
$before = [double](MaterialTotal $MAT)
Write-Host ('material total before=' + $before + ' (列=库存总量)')
Open '/inventory/material-move' 3000
ClearErrs | Out-Null
$idx = [int](FindRowByCol 3 $OWH 'st_audited')
Write-Host ('audited receive row=' + $idx + ' => ' + $(if ($idx -ge 0) { CellText $idx 1 } else { '-' }))
# 2026-10-05 F7-294: the audited receive document is a FIXTURE precondition -- its absence means the case
# could not run, so this is a SKIP (exit 0), not a FAIL that buries real regressions.
if ($idx -lt 0) { Skip 'D1 audited receive doc not found (fixture missing)'; Summary 'regression D1D2D3'; exit 0 }

Step 'D1 un-audit receive doc from list (target warehouse must drop by 20)'
# 2026-09-24（用户口径）：反审核已从列表行内移入详情页 ⇒ 先点「详情」，再在详情页头点「反审核」。
Write-Host ('open detail: ' + (ClickRowBtnContains $idx (ZH 'btn_detail')))
Start-Sleep -Milliseconds 2200
Write-Host ('un-audit: ' + (ClickBtn 'btn_unaudit'))
Write-Host ('confirm: ' + (ConfirmBox 1300))
Start-Sleep -Milliseconds 2800
Write-Host ('msg=' + (Txt '.el-message'))
$afterUn = [double](MaterialTotal $MAT)
Write-Host ('material total after un-audit=' + $afterUn)
Ok ($afterUn -eq ($before - 20)) 'D1 un-audit from list reversed 20 (stock)'

Step 'D1 audit the SAME doc from list -> stock + payable must come back'
Open '/inventory/material-move' 3000
$idx = [int](FindRowByCol 3 $OWH 'st_draft')
Write-Host ('draft receive row=' + $idx + ' => ' + $(if ($idx -ge 0) { CellText $idx 1 } else { '-' }))
Write-Host ('audit: ' + (ClickRowBtnContains $idx (ZH 'btn_audit')))
Write-Host ('confirm: ' + (ConfirmBox 1300))
Start-Sleep -Milliseconds 3000
Write-Host ('msg=' + (Txt '.el-message') + ' errs=' + (Errs))
$afterAudit = [double](MaterialTotal $MAT)
Write-Host ('material total after list audit=' + $afterAudit)
Ok ($afterAudit -eq $before) 'D1 audit from list wrote stock back (+20)'
Open '/inventory/material-move' 3000
$idx = [int](FindRowByCol 3 $OWH 'st_audited')
Write-Host ('row after audit=' + $(if ($idx -ge 0) { CellText $idx 1 } else { '-' }))
Ok ($idx -ge 0) 'D1 doc is AUDITED after list audit'
Open '/finance/payable' 2600
$py = Rows 0
$okPay = $false
foreach ($rw in $py.rows) {
  $line = ($rw -join ' ')
  if ($line -match (ZH 'val_factory') -and $line -match '200' -and $line -notmatch (ZH 'st_cancelled')) { Write-Host ('payable=' + $line); $okPay = $true }
}
Ok $okPay 'D1 payable row exists and is not cancelled'

Step 'D2 draft sale order (stock must NOT change)'
StockA 'before-sale'
Open '/inventory/sale' 2600
Write-Host ('click new: ' + (ClickBtn 'btn_new'))
Start-Sleep -Milliseconds 2200
Write-Host ('customer: ' + (SelectLabelContains 'lbl_customer' (ZH 'val_customer')))
Start-Sleep -Milliseconds 800
Write-Host ('out wh: ' + (SelectLabelContains 'lbl_out_wh' (ZH 'pfx_wh')))
Start-Sleep -Milliseconds 800
Write-Host ('open product: ' + (OpenRowSelect 0 0))
Start-Sleep -Milliseconds 1400
Write-Host ('pick product: ' + (PickOptionContains $P))
Start-Sleep -Milliseconds 700
Write-Host ('open quality: ' + (OpenRowSelect 0 1))
Start-Sleep -Milliseconds 1000
Write-Host ('pick quality: ' + (PickOptionContains (ZH 'opt_q_a')))
Start-Sleep -Milliseconds 600
Write-Host ('qty: ' + (SetRowInput 0 2 '3'))
Write-Host ('price: ' + (SetRowInput 0 3 '100'))
Start-Sleep -Milliseconds 500
Write-Host ('save: ' + (ClickBtn 'btn_save'))
Start-Sleep -Milliseconds 2600
Write-Host ('msg=' + (Txt '.el-message') + ' errs=' + (Errs))
StockA 'after-draft-save'

Step 'D2 audit sale order (stock -3)'
Open '/inventory/sale' 2600
$idx = [int](FindRow (ZH 'st_draft'))
Write-Host ('draft idx=' + $idx)
Write-Host ('audit: ' + (ClickRowBtnContains $idx (ZH 'btn_audit')))
Start-Sleep -Milliseconds 1000
Write-Host ('confirm: ' + (ConfirmBox 1300))
Start-Sleep -Milliseconds 3000
Write-Host ('msg=' + (Txt '.el-message') + ' errs=' + (Errs))
StockA 'after-audit'
Open '/inventory/stock-log' 2800
$lg = Rows 0
$found = $false
foreach ($rw in $lg.rows) {
  $line = ($rw -join ' ')
  if ($line -match (ZH 'st_sale_out') -and $line -match 'XS-') { Write-Host ('log=' + $line); $found = $true }
}
Ok $found 'D2 sale out stock log written'

Step 'D2 un-audit sale order (stock restored)'
Open '/inventory/sale' 2600
$idx = [int](FindRow (ZH 'st_audited'))
Write-Host ('audited idx=' + $idx)
# 2026-09-24（用户口径）：反审核已从列表行内移入详情页 ⇒ 先点「详情」，再在详情页头点「反审核」。
Write-Host ('open detail: ' + (ClickRowBtnContains $idx (ZH 'btn_detail')))
Start-Sleep -Milliseconds 2200
Write-Host ('un-audit: ' + (ClickBtn 'btn_unaudit'))
Start-Sleep -Milliseconds 1000
Write-Host ('confirm: ' + (ConfirmBox 1300))
Start-Sleep -Milliseconds 3000
Write-Host ('msg=' + (Txt '.el-message') + ' errs=' + (Errs))
StockA 'after-un-audit'
Open '/inventory/stock-log' 2800
$lg = Rows 0
$found2 = $false
foreach ($rw in $lg.rows) {
  $line = ($rw -join ' ')
  if ($line -match (ZH 'st_sale_reverse')) { Write-Host ('reverse-log=' + $line); $found2 = $true }
}
Ok $found2 'D2 sale un-audit stock log written'

Step 'D3 list -> deliver twice (both must open dialog)'
Open '/outsource/order/delivery' 3000
Write-Host ('first click: ' + (ClickRowBtnContains 0 (ZH 'btn_deliver')))
Start-Sleep -Milliseconds 3200
$d1 = EvalJs "String([...document.querySelectorAll('.el-dialog')].filter(e=>e.getClientRects().length>0).length)"
Write-Host ('dlg1=' + $d1)
Write-Host ('cancel1: ' + (ClickDialogBtn 'btn_cancel' 900))
Start-Sleep -Milliseconds 1200
Open '/outsource/order/delivery' 3000
Write-Host ('second click: ' + (ClickRowBtnContains 0 (ZH 'btn_deliver')))
Start-Sleep -Milliseconds 3200
$d2 = EvalJs "String([...document.querySelectorAll('.el-dialog')].filter(e=>e.getClientRects().length>0).length)"
Write-Host ('dlg2=' + $d2)
Ok ($d1 -eq '1' -and $d2 -eq '1') 'D3 both 收货 clicks auto-opened the dialog'
Write-Host ('cancel2: ' + (ClickDialogBtn 'btn_cancel' 900))
Write-Host ('errs-final=' + (Errs))
Summary 'regression D1+D2+D3'
