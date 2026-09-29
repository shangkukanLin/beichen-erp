# Manual dump 8 -- NOT a test case: this script only drives the finance pages and PRINTS what it sees
# (it contains no Ok assertions at all, see the ending). Kept for hand exploration; the real finance
# assertions live in verify-finance-kpi.ps1 (KPI reconciliation) and the per-module verify-*.ps1 cases.
# Original purpose: finance chain (account / expense / receipt / invoice / bill / payable-transfer).
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
EnsureLogin
WatchErrors
function Step($n) { Write-Host ('--- STEP ' + $n) }
$rowsDump = "(()=>{const vis=e=>e.getClientRects().length>0;const dlgs=[...document.querySelectorAll('.el-dialog')].filter(vis);const root=dlgs.length?dlgs[dlgs.length-1]:document;const t=[...root.querySelectorAll('.el-table')].filter(vis)[0];if(!t)return 'NOTABLE';const rs=[...t.querySelectorAll('.el-table__body tbody tr')];return JSON.stringify(rs.map(r=>[(r.innerText||'').replace(/\s+/g,' ').slice(0,90),[...r.querySelectorAll('input:not([type=hidden])')].map(e=>e.value)]))})()"

Step 'create account'
Open '/finance/account' 2600
ClearErrs | Out-Null
Write-Host ('click new: ' + (ClickBtn 'btn_new_account'))
Start-Sleep -Milliseconds 1200
# D-14 (2026-09-29): account names are unique per company now -> use a per-run name so re-runs
# do not collide with the row left behind by the previous run (this script has no Ok assertions).
$accName = 'FIN-ACC-' + (Get-Random -Minimum 1000 -Maximum 9999)
Write-Host ('name: ' + (FillLabel 'lbl_name' $accName))
Write-Host ('type: ' + (SelectLabelText 'lbl_type' (ZH 'opt_cash')))
Write-Host ('balance: ' + (FillLabel 'lbl_initial_balance' '10000'))
Write-Host ('ok: ' + (ClickDialogBtn 'btn_ok'))
Start-Sleep -Milliseconds 2000
Write-Host ('msg=' + (Txt '.el-message') + ' errs=' + (Errs))
$r = Rows 0
Write-Host ('accounts=' + $r.n + ' head=' + ($r.head -join '|'))
foreach ($rw in $r.rows) { Write-Host ('acc=' + ($rw -join ' | ')) }

Step 'expense + audit'
Open '/finance/expense' 2600
Write-Host ('click new: ' + (ClickBtn 'btn_new_expense'))
Start-Sleep -Milliseconds 1200
Write-Host ('type: ' + (SelectLabelText 'lbl_expense_type' (ZH 'opt_expense_office')))
Write-Host ('amount: ' + (FillLabel 'lbl_expense_amount' '100'))
Write-Host ('account: ' + (SelectLabelContains 'lbl_pay_account' $accName))
Write-Host ('ok: ' + (ClickDialogBtn 'btn_ok'))
Start-Sleep -Milliseconds 2000
Write-Host ('msg=' + (Txt '.el-message') + ' errs=' + (Errs))
$r = Rows 0
Write-Host ('expenses=' + $r.n + ' head=' + ($r.head -join '|'))
if ($r.n -gt 0) { Write-Host ('row0=' + ($r.rows[0] -join ' | ')) }
Write-Host ('audit: ' + (ClickRowBtnContains 0 (ZH 'btn_audit')))
Start-Sleep -Milliseconds 1000
Write-Host ('confirm: ' + (ConfirmBox 1300))
Start-Sleep -Milliseconds 2400
Write-Host ('msg=' + (Txt '.el-message') + ' errs=' + (Errs))
Open '/finance/account' 2400
$r = Rows 0
foreach ($rw in $r.rows) { Write-Host ('acc after expense=' + ($rw -join ' | ')) }

Step 'receipt with write-off'
Open '/finance/receipt' 2600
Write-Host ('click new: ' + (ClickBtn 'btn_new_receipt'))
Start-Sleep -Milliseconds 1500
Write-Host ('dlg items=' + (EvalJs "(()=>{const vis=e=>e.getClientRects().length>0;const d=[...document.querySelectorAll('.el-dialog')].filter(vis).pop();if(!d)return 'NODLG';return JSON.stringify([...d.querySelectorAll('.el-form-item')].map(it=>{const l=it.querySelector('.el-form-item__label');return (l?l.innerText.trim():'?')+'='+(it.querySelector('.el-select')?'select':'input')}))})()"))
Write-Host ('customer: ' + (SelectLabelContains 'lbl_customer' (ZH 'val_customer')))
Start-Sleep -Milliseconds 800
Write-Host ('account: ' + (SelectLabelContains 'lbl_account' $accName))
Start-Sleep -Milliseconds 700
Write-Host ('add writeoff: ' + (ClickDialogBtn 'btn_add_writeoff'))
Start-Sleep -Milliseconds 1200
Write-Host ('wr rows: ' + (EvalJs $rowsDump))
Write-Host ('open doc sel: ' + (OpenRowSelect 0 0))
Start-Sleep -Milliseconds 1400
Write-Host ('pick doc: ' + (PickOptionContains 'XS-'))
Start-Sleep -Milliseconds 700
Write-Host ('amount: ' + (SetRowInput 0 1 '500'))
Start-Sleep -Milliseconds 500
Write-Host ('ok: ' + (ClickDialogBtn 'btn_ok'))
Start-Sleep -Milliseconds 2600
Write-Host ('msg=' + (Txt '.el-message') + ' errs=' + (Errs))
Open '/finance/receipt' 2600
$r = Rows 0
Write-Host ('receipts=' + $r.n + ' head=' + ($r.head -join '|'))
if ($r.n -gt 0) { Write-Host ('row0=' + ($r.rows[0] -join ' | ')) }
Write-Host ('audit: ' + (ClickRowBtnContains 0 (ZH 'btn_audit')))
Start-Sleep -Milliseconds 1000
Write-Host ('confirm: ' + (ConfirmBox 1300))
Start-Sleep -Milliseconds 2600
Write-Host ('msg=' + (Txt '.el-message') + ' errs=' + (Errs))
Open '/finance/receivable' 2600
$r = Rows 0
foreach ($rw in $r.rows) { Write-Host ('recv=' + ($rw -join ' | ')) }

Step 'invoice'
Open '/finance/invoice' 2600
Write-Host ('click register: ' + (ClickBtn 'btn_register_invoice'))
Start-Sleep -Milliseconds 1500
Write-Host ('no: ' + (FillLabel 'lbl_invoice_no' 'FP-AUTO-001'))
Write-Host ('amount: ' + (FillLabel 'lbl_invoice_amount' '500'))
Write-Host ('ok: ' + (ClickDialogBtn 'btn_ok'))
Start-Sleep -Milliseconds 2400
Write-Host ('msg=' + (Txt '.el-message') + ' errs=' + (Errs))
$r = Rows 0
Write-Host ('invoices=' + $r.n + ' head=' + ($r.head -join '|'))
if ($r.n -gt 0) { Write-Host ('row0=' + ($r.rows[0] -join ' | ')) }

Step 'bill generation'
Open '/finance/bill' 2600
Write-Host ('click gen: ' + (ClickBtn 'btn_gen_bill'))
Start-Sleep -Milliseconds 1500
Write-Host ('type: ' + (SelectLabelText 'lbl_bill_type' (ZH 'opt_bill_receivable')))
Write-Host ('partner: ' + (SelectLabelContains 'lbl_partner' (ZH 'val_customer')))
Write-Host ('generate: ' + (ClickDialogBtn 'btn_generate'))
Start-Sleep -Milliseconds 2600
Write-Host ('msg=' + (Txt '.el-message') + ' errs=' + (Errs))
$r = Rows 0
Write-Host ('bills=' + $r.n + ' head=' + ($r.head -join '|'))
if ($r.n -gt 0) { Write-Host ('row0=' + ($r.rows[0] -join ' | ')) }

Step 'payable -> receivable transfer'
Open '/finance/payable-transfer' 2600
Write-Host ('click new: ' + (ClickBtn 'btn_new_payable_transfer'))
Start-Sleep -Milliseconds 2000
Write-Host ('path=' + (EvalJs 'String(location.pathname)'))
Write-Host ('open src sel: ' + (OpenSelectLabelIdx 'lbl_source_payable' 0))
Start-Sleep -Milliseconds 1400
Write-Host ('pick src: ' + (PickFirstOption 1400))
Write-Host ('save: ' + (ClickBtn 'btn_save'))
Start-Sleep -Milliseconds 2600
Write-Host ('msg=' + (Txt '.el-message') + ' errs=' + (Errs))
Open '/finance/payable-transfer' 2600
$r = Rows 0
Write-Host ('transfers=' + $r.n + ' head=' + ($r.head -join '|'))
if ($r.n -gt 0) { Write-Host ('row0=' + ($r.rows[0] -join ' | ')) }

Step 'payment pages (readonly check)'
Open '/finance/payment' 2600
$r = Rows 0
Write-Host ('payment rows=' + $r.n + ' head=' + ($r.head -join '|'))
if ($r.n -gt 0) { Write-Host ('row0=' + ($r.rows[0] -join ' | ')) }
Write-Host ('errs-final=' + (Errs))
# 2026-09-21: deliberately NOT calling Summary here -- this script has zero assertions, and Summary now
# refuses to report PASS for a run with no assertions (it would otherwise be a fake green).
Write-Host 'DUMP DONE finance chain (manual exploration script, not a test case)'
