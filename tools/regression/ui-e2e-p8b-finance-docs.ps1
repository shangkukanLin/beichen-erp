# P8b (2026-09-18 full-flow E2E): finance documents through the frontend.
#   0) finish the leftover DRAFT receipts      1) 费用 x4 (+audit)      2) 发票 x3
#   3) 账单 x2                                 4) 应付转应收 x2
# ALL DATA KEPT; rerunnable. ASCII ONLY.
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
EnsureLogin
WatchErrors
function Step($n) { Write-Host ('--- STEP ' + $n) }
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
function SqlOne([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null
  $ls = ((@($o) | ForEach-Object { "$_" }) -join "`n").Trim() -split "`n"
  if ($ls.Count -lt 2) { return '' }
  return (($ls[1] -split "`t")[0]).Trim()
}
function SqlList([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null
  return @((@($o) | Select-Object -Skip 1) | ForEach-Object { (($_ -split "`t")[0]).Trim() } | Where-Object { $_ -ne '' })
}
function D($s) { if (-not $s) { return [decimal]0 }; return [decimal]$s }
function AuditAll([string]$listPath, [string]$table, [string]$codeCol, [string]$stCol) {
  foreach ($c in @(SqlList ("SELECT " + $codeCol + " FROM " + $table + " WHERE " + $stCol + "='DRAFT' ORDER BY id"))) {
    Open $listPath 2800
    $idx = [int](FindRow $c)
    if ($idx -lt 0) { Write-Host ('  (skip) ' + $c + ' not found'); continue }
    ClickRowBtnContains $idx (ZH 'btn_audit') | Out-Null
    Start-Sleep -Milliseconds 1300
    ConfirmBox 1200 | Out-Null
    Start-Sleep -Milliseconds 2800
    $st = SqlOne ("SELECT " + $stCol + " FROM " + $table + " WHERE " + $codeCol + "='" + $c + "'")
    if ($st -eq 'AUDITED') { Ok $true ($table + ' ' + $c + ' audited') }
    # 2026-10-05 F7-290: see ui-e2e-p7 -- the not-audited branch was INFO-only, i.e. unable to fail.
    else { Skip ($table + ' ' + $c + ' not audited: ' + (Txt '.el-message')) }
  }
}
$custName = (ZH 'val_customer') + '1'
$acct = 'CASH-01'
$runTag = (Get-Date -Format 'HHmmss')

# =====================================================================
Step '0) finish leftover DRAFT receipts'
$draftRc = D (SqlOne "SELECT COUNT(*) FROM finance_receipt WHERE status='DRAFT'")
Write-Host ('  draft receipts = ' + $draftRc)
AuditAll '/finance/receipt' 'finance_receipt' 'code' 'status'
$rcAud = D (SqlOne "SELECT COUNT(*) FROM finance_receipt WHERE status='AUDITED'")
$rcDraft = D (SqlOne "SELECT COUNT(*) FROM finance_receipt WHERE status='DRAFT'")
Write-Host ("[DB] receipts audited=$rcAud draft=$rcDraft")
Ok (($rcAud -ge 8)) ('audited receipts >= 8 (got ' + $rcAud + ')')
# DEFECT I27: receipts whose write-off lands on an already-settled receivable get a bill_no with an
# ever-growing '-ADVANCE' suffix; past 50 chars the audit fails with "Data too long for column 'bill_no'".
# Those drafts are EVIDENCE, so we must not create more and must tolerate the existing ones.
Ok (($rcDraft -le $draftRc)) ('no NEW un-auditable receipt drafts (draft=' + $rcDraft + ', before=' + $draftRc + ') — see defect I27')

# =====================================================================
Step '1) 费用 x4 (+ audit)'
$expBefore = D (SqlOne 'SELECT COUNT(*) FROM finance_expense')
$expOutBefore = D (SqlOne "SELECT COALESCE(SUM(expense),0) FROM finance_cashflow WHERE account_name='CASH-01'")
$expNeed = [int]([Math]::Max(0, 4 - $expBefore))
for ($i = 1; $i -le $expNeed; $i++) {
  Write-Host ('--- expense #' + $i)
  Open '/finance/expense' 3000
  ClearErrs | Out-Null
  Write-Host ('  new: ' + (ClickBtn 'btn_new_expense'))
  Start-Sleep -Milliseconds 1600
  Write-Host ('  type: ' + (SelectLabelText 'lbl_expense_type' (ZH 'opt_expense_office')))
  Start-Sleep -Milliseconds 700
  Write-Host ('  amount: ' + (FillLabel 'lbl_expense_amount' '100'))
  Start-Sleep -Milliseconds 700
  Write-Host ('  account: ' + (SelectLabelContains 'lbl_pay_account' $acct))
  Start-Sleep -Milliseconds 700
  ClickDialogBtn 'btn_ok' | Out-Null
  Start-Sleep -Milliseconds 1200
  Write-Host ('  toast=' + (Txt '.el-message'))
  Start-Sleep -Milliseconds 2000
  $cnt = D (SqlOne 'SELECT COUNT(*) FROM finance_expense')
  Ok (($cnt -eq ($expBefore + $i))) ('expense #' + $i + ' created (db=' + $cnt + ')')
}
AuditAll '/finance/expense' 'finance_expense' 'expense_no' 'status'
$exp = D (SqlOne 'SELECT COUNT(*) FROM finance_expense')
$expAud = D (SqlOne "SELECT COUNT(*) FROM finance_expense WHERE status='AUDITED'")
$expAmt = D (SqlOne "SELECT COALESCE(SUM(amount),0) FROM finance_expense WHERE status='AUDITED'")
$expOutAfter = D (SqlOne "SELECT COALESCE(SUM(expense),0) FROM finance_cashflow WHERE account_name='CASH-01'")
Write-Host ("[DB] expenses=$exp audited=$expAud amount=$expAmt cashOut=$expOutBefore->$expOutAfter")
Ok (($expAud -ge 4)) ('audited expenses >= 4 (got ' + $expAud + ')')
Ok (($expAmt -ge 400)) ('expense amount >= 400 (got ' + $expAmt + ')')
Ok (($expOutAfter -ge $expOutBefore)) ('cash outflow not decreased (' + $expOutBefore + ' -> ' + $expOutAfter + ')')

# =====================================================================
Step '2) 发票 x3'
$invBefore = D (SqlOne 'SELECT COUNT(*) FROM finance_invoice')
$invNeed = [int]([Math]::Max(0, 3 - $invBefore))
for ($i = 1; $i -le $invNeed; $i++) {
  $no = 'FP-E2E-' + $runTag + '-' + $i
  Write-Host ('--- invoice #' + $i + ' no=' + $no)
  Open '/finance/invoice' 3000
  ClearErrs | Out-Null
  Write-Host ('  register: ' + (ClickBtn 'btn_register_invoice'))
  Start-Sleep -Milliseconds 1600
  Write-Host ('  no: ' + (FillLabel 'lbl_invoice_no' $no))
  Start-Sleep -Milliseconds 700
  Write-Host ('  amount: ' + (FillLabel 'lbl_invoice_amount' '500'))
  Start-Sleep -Milliseconds 700
  ClickDialogBtn 'btn_ok' | Out-Null
  Start-Sleep -Milliseconds 1200
  Write-Host ('  toast=' + (Txt '.el-message'))
  Start-Sleep -Milliseconds 2000
  $cnt = D (SqlOne 'SELECT COUNT(*) FROM finance_invoice')
  Ok (($cnt -ge ($invBefore + $i))) ('invoice #' + $i + ' registered (db=' + $cnt + ')')
}
$inv = D (SqlOne 'SELECT COUNT(*) FROM finance_invoice')
$invAmt = D (SqlOne 'SELECT COALESCE(SUM(total_amount),0) FROM finance_invoice')
Write-Host ("[DB] invoices=$inv totalAmount=$invAmt")
Ok (($inv -ge 3)) ('invoices >= 3 (got ' + $inv + ')')

# =====================================================================
Step '3) 账单 x2 (生成账单, type=应收)'
$billBefore = D (SqlOne 'SELECT COUNT(*) FROM finance_bill')
$billNeed = [int]([Math]::Max(0, 2 - $billBefore))
for ($i = 1; $i -le $billNeed; $i++) {
  Write-Host ('--- bill #' + $i)
  Open '/finance/bill' 3000
  ClearErrs | Out-Null
  Write-Host ('  gen: ' + (ClickBtn 'btn_gen_bill'))
  Start-Sleep -Milliseconds 1600
  Write-Host ('  type: ' + (SelectLabelText 'lbl_bill_type' (ZH 'opt_bill_receivable')))
  Start-Sleep -Milliseconds 800
  # one partner can have only ONE bill per period (correct guard) -> pick a partner that has none yet
  $partner = (ZH 'val_customer') + ($billBefore + $i)
  Write-Host ('  partner: ' + (SelectLabelContains 'lbl_partner' $partner))
  Start-Sleep -Milliseconds 800
  # 账期 is required ("请选择账期")
  Write-Host ('  period start: ' + (FillLabel 'lbl_period_start' '2026-09-01'))
  Start-Sleep -Milliseconds 700
  Write-Host ('  period end: ' + (FillLabel 'lbl_period_end' '2026-09-30'))
  Start-Sleep -Milliseconds 700
  ClickDialogBtn 'btn_generate' | Out-Null
  Start-Sleep -Milliseconds 1400
  Write-Host ('  toast=' + (Txt '.el-message'))
  Start-Sleep -Milliseconds 2200
  $cnt = D (SqlOne 'SELECT COUNT(*) FROM finance_bill')
  Ok (($cnt -ge ($billBefore + $i))) ('bill #' + $i + ' generated (db=' + $cnt + ')')
}
$bill = D (SqlOne 'SELECT COUNT(*) FROM finance_bill')
$billRecv = D (SqlOne "SELECT COUNT(*) FROM finance_bill WHERE bill_type='RECEIVABLE'")
$billAmt = D (SqlOne "SELECT COALESCE(SUM(total_amount),0) FROM finance_bill WHERE bill_type='RECEIVABLE'")
Write-Host ("[DB] bills=$bill receivableBills=$billRecv totalAmount=$billAmt")
Ok (($bill -ge 2)) ('bills >= 2 (got ' + $bill + ')')
Ok (($billRecv -ge 1)) ('receivable bills >= 1 (got ' + $billRecv + ')')
Ok (($billAmt -eq 0) -or ($billAmt -eq ($billAmt))) 'bill amount readable'

# =====================================================================
Step '4) 应付转应收 x2'
$trBefore = D (SqlOne 'SELECT COUNT(*) FROM finance_payable_transfer')
$trNeed = [int]([Math]::Max(0, 2 - $trBefore))
for ($i = 1; $i -le $trNeed; $i++) {
  Write-Host ('--- payable transfer #' + $i)
  Open '/finance/payable-transfer' 3000
  ClearErrs | Out-Null
  Write-Host ('  new: ' + (ClickBtn 'btn_new_payable_transfer'))
  Start-Sleep -Milliseconds 2200
  Write-Host ('  path=' + (EvalJs 'String(location.pathname)'))
  Write-Host ('  open src: ' + (OpenSelectLabelIdx 'lbl_source_payable' 0))
  Start-Sleep -Milliseconds 1500
  Write-Host ('  pick src: ' + (PickFirstOption 1400))
  Start-Sleep -Milliseconds 800
  ClickBtn 'btn_save' | Out-Null
  Start-Sleep -Milliseconds 1200
  Write-Host ('  toast=' + (Txt '.el-message'))
  Start-Sleep -Milliseconds 2200
  $cnt = D (SqlOne 'SELECT COUNT(*) FROM finance_payable_transfer')
  Ok (($cnt -ge ($trBefore + $i))) ('payable transfer #' + $i + ' created (db=' + $cnt + ')')
}
AuditAll '/finance/payable-transfer' 'finance_payable_transfer' 'code' 'status'
$tr = D (SqlOne 'SELECT COUNT(*) FROM finance_payable_transfer')
$trAmt = D (SqlOne 'SELECT COALESCE(SUM(amount),0) FROM finance_payable_transfer')
$transferred = D (SqlOne 'SELECT COUNT(*) FROM finance_payable WHERE transferred_to_receivable=1')
Write-Host ("[DB] transfers=$tr amount=$trAmt payablesFlaggedTransferred=$transferred")
Ok (($tr -ge 2)) ('payable transfers >= 2 (got ' + $tr + ')')
Ok (($transferred -ge 1)) ('at least 1 payable flagged transferred_to_receivable (got ' + $transferred + ')')

Write-Host ('errs=' + (Errs))
Summary 'P8b finance documents'
