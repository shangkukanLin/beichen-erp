# P3c (2026-09-18 full-flow E2E): supplier payments through the frontend only.
#   Payment documents are created on the supplier payables page: 付款管理 -> 供应商汇总 -> 详情 -> 新增付款
#   (account + write-off line against an unpaid payable), then audited on 付款管理 -> 付款记录.
#   5 payments, ALL DATA KEPT. ASCII ONLY.
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

$N = 5
$payBefore = D (SqlOne 'SELECT COUNT(*) FROM finance_payment')
$skipCreate = ($payBefore -ge $N)
if ($skipCreate) { Write-Host ('NOTE: ' + $payBefore + ' payments already exist -> creation skipped (audit/verify only)') }
$paidBefore = D (SqlOne 'SELECT COALESCE(SUM(paid_amount),0) FROM finance_payable')
$flowBefore = D (SqlOne 'SELECT COUNT(*) FROM finance_cashflow')
$sids = @(SqlList ("SELECT s.id FROM supplier s JOIN supplier_type_ref r ON r.supplier_id=s.id WHERE r.type_code='product' ORDER BY s.id LIMIT " + $N))
Write-Host ("[BASE] payments=$payBefore paidSum=$paidBefore cashflow=$flowBefore suppliers=[" + ($sids -join ',') + "] count=" + $sids.Count)
Ok ($sids.Count -ge 1) 'product suppliers resolved from DB'

Step ("payments x" + $N)
$codes = @()
for ($i = 0; $i -lt $N; $i++) {
  if ($skipCreate) { break }
  $sid = $sids[$i % $sids.Count]
  Open ("/finance/payment/supplier/" + $sid) 4000
  ClearErrs | Out-Null
  # the supplier page needs its data load before the button is clickable -> retry up to 4x
  $clicked = ''
  for ($t = 1; $t -le 4; $t++) {
    $clicked = ClickText (ZH 'btn_add_payment')
    if ($clicked -match 'OK') { break }
    Write-Host ('  retry open-payment-dialog (' + $t + '): ' + $clicked + ' path=' + (EvalJs 'String(location.pathname)'))
    Start-Sleep -Milliseconds 1500
  }
  Write-Host ('open payment dialog: ' + $clicked)
  Ok ($clicked -match 'OK') ('payment dialog opened (supplier ' + $sid + ')')
  Start-Sleep -Milliseconds 1800
  $ra = SelectLabelText 'lbl_pay_account2' 'BANK-01'
  Write-Host ('pay account: ' + $ra)
  Start-Sleep -Milliseconds 700
  Write-Host ('add writeoff: ' + (ClickBtn 'btn_add_writeoff'))
  Start-Sleep -Milliseconds 1200
  Write-Host ('pick payable: ' + (OpenRowSelect 0 0))
  Start-Sleep -Milliseconds 1500
  Write-Host ('pick first payable option: ' + (PickFirstOption 1200))
  Start-Sleep -Milliseconds 900
  Write-Host ('writeoff amount: ' + (SetRowInput 0 1 '500'))
  Start-Sleep -Milliseconds 600
  Write-Host ('submit: ' + (ClickDialogBtn 'btn_ok' 1200))
  Start-Sleep -Milliseconds 2800
  Write-Host ('msg=' + (Txt '.el-message'))
  $cnt = D (SqlOne 'SELECT COUNT(*) FROM finance_payment')
  Ok ($cnt -eq ($payBefore + $i + 1)) ('payment ' + ($i + 1) + ' created (db=' + $cnt + ')')
  $c = SqlOne "SELECT code FROM finance_payment ORDER BY id DESC LIMIT 1"
  if ($c) { $codes += $c }
}

Step 'audit all draft payments (row located by code)'
foreach ($c in (SqlList "SELECT code FROM finance_payment WHERE status='DRAFT' ORDER BY id")) {
  Open '/finance/payment' 2600
  ClickText (ZH 'tab_pay_records') | Out-Null
  Start-Sleep -Milliseconds 1500
  $idx = [int](FindRow $c)
  Ok ($idx -ge 0) ('payment row found: ' + $c)
  if ($idx -ge 0) {
    Write-Host ('audit ' + $c + ': ' + (ClickRowBtnContains $idx (ZH 'btn_audit')))
    Start-Sleep -Milliseconds 1400
    ConfirmBox 900 | Out-Null
    Start-Sleep -Milliseconds 2800
    $st = SqlOne ("SELECT status FROM finance_payment WHERE code='" + $c + "'")
    Write-Host ('  -> status=' + $st)
    Ok ($st -eq 'AUDITED') ('payment ' + $c + ' audited')
  }
}

Step 'DB cross-check'
$pay = D (SqlOne 'SELECT COUNT(*) FROM finance_payment')
$payAud = D (SqlOne 'SELECT COUNT(*) FROM finance_payment WHERE status=''AUDITED''')
$items = D (SqlOne 'SELECT COUNT(*) FROM finance_payment_item')
$itemSum = D (SqlOne 'SELECT COALESCE(SUM(this_amount),0) FROM finance_payment_item')
$flowAfter = D (SqlOne 'SELECT COUNT(*) FROM finance_cashflow')
$outSum = D (SqlOne "SELECT COALESCE(SUM(expense),0) FROM finance_cashflow")
$payFlows = D (SqlOne "SELECT COUNT(*) FROM finance_cashflow WHERE flow_type='PAYMENT'")
$advance = D (SqlOne "SELECT COUNT(*) FROM finance_payable WHERE status='ADVANCE'")
Write-Host ("[DB] payments=$pay audited=$payAud items=$items itemSum=$itemSum cashflow=$flowBefore->$flowAfter outSum=$outSum paymentFlows=$payFlows advanceRows=$advance")
Ok (($pay -ge $N)) ('payments >= ' + $N + ' (got ' + $pay + ')')
Ok (($payAud -eq $pay)) ('all payments audited (' + $payAud + '/' + $pay + ')')
Ok (($items -ge $N)) ('payment write-off items >= ' + $N + ' (got ' + $items + ')')
Ok (($itemSum -eq (500 * $pay))) ('write-off amounts sum to 500 x payments (' + $itemSum + ' = 500 x ' + $pay + ')')
Ok (($payFlows -ge $pay)) ('PAYMENT cashflow rows >= payments (' + $payFlows + ' >= ' + $pay + ')')
Ok (($outSum -ge (500 * $pay))) ('cash outflow total >= 500 x payments (' + $outSum + ')')
Write-Host ('INFO overpaid negative payables turned into ADVANCE rows = ' + $advance + ' (design: excess payment becomes advance/预付)')
Write-Host ('errs=' + (Errs))
Summary 'P3c supplier payments'
