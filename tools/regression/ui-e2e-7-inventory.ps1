# Temp test 7: inventory documents (warehouse / other-io / move / reclassify / loss / stock-take)
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
EnsureLogin
WatchErrors
function Step($n) { Write-Host ('--- STEP ' + $n) }
# T7（2026-09-18）：原按 'MFTEST'（val_proj）选产品 —— 库里没有该名字时**选空却能保存+审核**，
# 属用例假绿（还写出了 product_id=NULL 的幽灵库存行）。改为选真实产品，并对选品结果加断言。
$P = ZH 'val_product'
$W1 = ZH 'pfx_wh'          # prefix matching 成品一号仓
$W2 = 'FIN2-WH'
$rowsDump = "(()=>{const vis=e=>e.getClientRects().length>0;const t=[...document.querySelectorAll('.el-table')].filter(vis)[0];if(!t)return 'NOTABLE';const rs=[...t.querySelectorAll('.el-table__body tbody tr')];return JSON.stringify(rs.map(r=>[(r.innerText||'').replace(/\s+/g,' ').slice(0,80),[...r.querySelectorAll('input:not([type=hidden])')].map(e=>e.value)]))})()"
function StockRows($tag) {
  Open '/inventory/stock' 2600
  $st = Rows 0
  Write-Host ('STOCK[' + $tag + ']')
  foreach ($rw in $st.rows) { if (($rw -join ' ') -match $P) { Write-Host ('  ' + ($rw -join ' | ')) } }
}

Step 'create 2nd finished warehouse'
Open '/inventory/warehouse' 2600
ClearErrs | Out-Null
Write-Host ('click new: ' + (ClickBtn 'btn_new'))
Start-Sleep -Milliseconds 1200
Write-Host ('name: ' + (FillLabel 'lbl_name' $W2))
Write-Host ('ok: ' + (ClickDialogBtn 'btn_ok'))
Start-Sleep -Milliseconds 1800
Ok ((BodyHas $W2) -match 'true') 'second finished warehouse created'

Step 'other-io (in) with audit'
Open '/inventory/other-io/add' 2600
Write-Host ('wh: ' + (SelectLabelContains 'lbl_warehouse' (ZH 'pfx_wh')))
Write-Host ('rowdump ' + (EvalJs $rowsDump))
Write-Host ('open product: ' + (OpenRowSelect 0 0))
Start-Sleep -Milliseconds 1300
$pk = PickOptionContains $P
Write-Host ('pick product: ' + $pk)
Ok ($pk -match 'OK') ('selected product ' + $P)
Start-Sleep -Milliseconds 600
Write-Host ('qty: ' + (SetRowInput 0 2 '5'))
Start-Sleep -Milliseconds 400
Write-Host ('save: ' + (ClickBtn 'btn_save'))
Start-Sleep -Milliseconds 2600
Write-Host ('msg=' + (Txt '.el-message') + ' errs=' + (Errs))
Open '/inventory/other-io' 2600
$r = Rows 0
$ioIdx = [int](FindRow $P)
Write-Host ('other-io rows=' + $r.n + ' idx(' + $P + ')=' + $ioIdx)
Ok ($ioIdx -ge 0) 'other-io doc saved WITH a real product (明细已落库)'
if ($ioIdx -lt 0) { Bad 'other-io row not found -> abort'; Summary 'inventory documents chain'; exit 1 }
Write-Host ('row=' + ($r.rows[$ioIdx] -join ' | '))
Write-Host ('audit: ' + (ClickRowBtnContains $ioIdx (ZH 'btn_audit')))
Start-Sleep -Milliseconds 1000
Write-Host ('confirm: ' + (ConfirmBox 1300))
Start-Sleep -Milliseconds 2600
Write-Host ('msg=' + (Txt '.el-message') + ' errs=' + (Errs))
StockRows 'after other-io'

Step 'warehouse move'
Open '/inventory/warehouse-move/add' 2600
Write-Host ('from wh: ' + (SelectLabelContains 'lbl_out_warehouse' (ZH 'pfx_wh')))
Start-Sleep -Milliseconds 700
Write-Host ('to wh: ' + (SelectLabelContains 'lbl_in_wh_move' $W2))
Start-Sleep -Milliseconds 700
Write-Host ('add row: ' + (ClickBtn 'btn_add_detail'))
Start-Sleep -Milliseconds 900
Write-Host ('rowdump ' + (EvalJs $rowsDump))
Write-Host ('open product: ' + (OpenRowSelect 0 0))
Start-Sleep -Milliseconds 1300
$pk = PickOptionContains $P
Write-Host ('pick product: ' + $pk)
Ok ($pk -match 'OK') ('selected product ' + $P)
Start-Sleep -Milliseconds 600
Write-Host ('open quality: ' + (OpenRowSelect 0 1))
Start-Sleep -Milliseconds 1000
Write-Host ('pick quality: ' + (PickOptionContains (ZH 'opt_q_a')))
Start-Sleep -Milliseconds 600
Write-Host ('qty: ' + (SetRowInput 0 2 '3'))
Start-Sleep -Milliseconds 400
Write-Host ('save: ' + (ClickBtn 'btn_save'))
Start-Sleep -Milliseconds 2600
Write-Host ('msg=' + (Txt '.el-message') + ' errs=' + (Errs))
Open '/inventory/warehouse-move' 2600
$r = Rows 0
Write-Host ('move rows=' + $r.n + ' head=' + ($r.head -join '|'))
if ($r.n -gt 0) { Write-Host ('row0=' + ($r.rows[0] -join ' | ')) }
Write-Host ('audit: ' + (ClickRowBtnContains 0 (ZH 'btn_audit')))
Start-Sleep -Milliseconds 1000
Write-Host ('confirm: ' + (ConfirmBox 1300))
Start-Sleep -Milliseconds 2600
Write-Host ('msg=' + (Txt '.el-message') + ' errs=' + (Errs))
StockRows 'after move'

Step 'reclassify A->B'
Open '/inventory/reclassify/add' 2600
Write-Host ('wh: ' + (SelectLabelContains 'lbl_warehouse' (ZH 'pfx_wh')))
Write-Host ('rowdump ' + (EvalJs $rowsDump))
Write-Host ('open product: ' + (OpenRowSelect 0 0))
Start-Sleep -Milliseconds 1300
$pk = PickOptionContains $P
Write-Host ('pick product: ' + $pk)
Ok ($pk -match 'OK') ('selected product ' + $P)
Start-Sleep -Milliseconds 600
Write-Host ('open target q: ' + (OpenRowSelect 0 2))
Start-Sleep -Milliseconds 1000
Write-Host ('pick target q: ' + (PickOptionContains (ZH 'opt_q_b')))
Start-Sleep -Milliseconds 600
Write-Host ('qty: ' + (SetRowInput 0 3 '2'))
Start-Sleep -Milliseconds 400
Write-Host ('save: ' + (ClickBtn 'btn_save'))
Start-Sleep -Milliseconds 2600
Write-Host ('msg=' + (Txt '.el-message') + ' errs=' + (Errs))
Open '/inventory/reclassify' 2600
$r = Rows 0
Write-Host ('reclassify rows=' + $r.n + ' head=' + ($r.head -join '|'))
if ($r.n -gt 0) { Write-Host ('row0=' + ($r.rows[0] -join ' | ')) }
Write-Host ('audit: ' + (ClickRowBtnContains 0 (ZH 'btn_audit')))
Start-Sleep -Milliseconds 1000
Write-Host ('confirm: ' + (ConfirmBox 1300))
Start-Sleep -Milliseconds 2600
Write-Host ('msg=' + (Txt '.el-message') + ' errs=' + (Errs))
StockRows 'after reclassify'

Step 'stock loss'
Open '/inventory/stock-loss/add' 2600
Write-Host ('wh: ' + (SelectLabelContains 'lbl_warehouse' $W2))
Write-Host ('pick reason: ' + (SelectLabel 'lbl_loss_reason' 'opt_loss_broken'))
Write-Host ('open product: ' + (OpenRowSelect 0 0))
Start-Sleep -Milliseconds 1300
$pk = PickOptionContains $P
Write-Host ('pick product: ' + $pk)
Ok ($pk -match 'OK') ('selected product ' + $P)
Start-Sleep -Milliseconds 600
Write-Host ('qty: ' + (SetRowInput 0 2 '1'))
Start-Sleep -Milliseconds 400
Write-Host ('rowdump ' + (EvalJs $rowsDump))
Write-Host ('save: ' + (ClickBtn 'btn_save'))
Start-Sleep -Milliseconds 2600
Write-Host ('msg=' + (Txt '.el-message') + ' errs=' + (Errs))
Open '/inventory/stock-loss' 2600
$r = Rows 0
Write-Host ('loss rows=' + $r.n + ' head=' + ($r.head -join '|'))
if ($r.n -gt 0) { Write-Host ('row0=' + ($r.rows[0] -join ' | ')) }
Write-Host ('audit: ' + (ClickRowBtnContains 0 (ZH 'btn_audit')))
Start-Sleep -Milliseconds 1000
Write-Host ('confirm: ' + (ConfirmBox 1300))
Start-Sleep -Milliseconds 2600
Write-Host ('msg=' + (Txt '.el-message') + ' errs=' + (Errs))
StockRows 'after loss'

Step 'stock take'
Open '/inventory/stock-take' 3000
Write-Host ('click new take: ' + (ClickBtn 'btn_new_take'))
Start-Sleep -Milliseconds 1500
Write-Host ('open take wh sel: ' + (DialogOpenSelect 0))
Start-Sleep -Milliseconds 1400
Write-Host ('take wh: ' + (PickOptionContains (ZH 'pfx_wh')))
Start-Sleep -Milliseconds 600
Write-Host ('ok: ' + (ClickDialogBtn 'btn_ok'))
Start-Sleep -Milliseconds 2600
Write-Host ('msg=' + (Txt '.el-message') + ' errs=' + (Errs))
$r = Rows 0
Write-Host ('take rows=' + $r.n + ' head=' + ($r.head -join '|'))
if ($r.n -gt 0) { Write-Host ('row0=' + ($r.rows[0] -join ' | ')) }
Write-Host ('enter real qty: ' + (ClickRowBtnContains 0 (ZH 'btn_enter_take')))
Start-Sleep -Milliseconds 2600
Write-Host ('dialog rows: ' + (EvalJs $rowsDump))
Write-Host ('save real: ' + (ClickDialogBtn 'btn_save_take'))
Start-Sleep -Milliseconds 2600
Write-Host ('msg=' + (Txt '.el-message') + ' errs=' + (Errs))
Open '/inventory/stock-take' 3000
$r = Rows 0
Write-Host ('take row after input=' + $(if ($r.n -gt 0) { ($r.rows[0] -join ' | ') } else { 'NONE' }))
Write-Host ('audit take: ' + (ClickRowBtnContains 0 (ZH 'btn_audit')))
Start-Sleep -Milliseconds 1200
Write-Host ('confirm: ' + (ConfirmBox 1300))
Start-Sleep -Milliseconds 3000
Write-Host ('msg=' + (Txt '.el-message') + ' errs=' + (Errs))
Summary 'inventory documents chain'
