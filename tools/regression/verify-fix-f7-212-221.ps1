# Fix verification (finance audit batch B, 2026-09-29): F7-212 .. F7-221.
# ASCII ONLY on purpose (this file has no BOM; PS 5.1 would read Chinese literals as GBK).
# Chinese needles are built from code points (same trick as verify-i27-advance-billno.ps1).
#
#   F7-213  payment with accountId but NO split rows and NO write-off items => REJECTED (used to book a 0-yuan payment)
#   F7-214  a write-off line with thisAmount <= 0 => REJECTED (create/update side)
#   F7-215  a DISABLED account (status=0) must not be usable for a payment
#   F7-218  changing the supplier on a draft must RECOMPUTE supplier_type
#   F7-216  over-payment must key its advance ledger by the PAYMENT code, and un-audit -> re-audit must REUSE the same row
#   F7-220  static: danger buttons carry v-perm
#   F7-221  static: confirm dialog and API call live in separate try blocks
#
# Writes: creates 3 receipts-like fixtures (payments) and un-audits + cancels them in the cleanup step; it asserts the
# payable it over-paid is restored to its original unpaid amount. One account's status is flipped to 0 for one check
# and restored in the finally block. No pre-existing row is deleted.
$ErrorActionPreference = 'Continue'
$base = 'http://localhost:8080/api'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$env:MYSQL_PWD = 'root'
$WEB = 'beichen-erp-web/src/views/finance'
$REPO = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path   # beichen-erp/
$script:PASS = 0; $script:FAIL = 0
function Ok([bool]$c, [string]$m) { if ($c) { $script:PASS++; Write-Host ('PASS ' + $m) } else { $script:FAIL++; Write-Host ('FAIL ' + $m) } }
function Step($n) { Write-Host ('--- STEP ' + $n) }
function SqlOne([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -D beichen_erp -N -B -e $q 2>$null
  $v = (@($o) | Where-Object { $_ -notmatch '^(mysql:|ERROR)' } | Select-Object -First 1)
  if ($null -eq $v) { return '' }
  return ("$v").Trim()
}
function D($s) { if (-not $s) { return [decimal]0 }; return [decimal]$s }
function Cn([int[]]$cp) { return (-join ($cp | ForEach-Object { [string][char]$_ })) }
$CN_NOAMT    = Cn @(0x672A,0x586B,0x4ED8,0x6B3E,0x91D1,0x989D)                    # 未填付款金额
$CN_ITEMAMT  = Cn @(0x6838,0x9500,0x91D1,0x989D,0x5FC5,0x987B,0x5927,0x4E8E)      # 核销金额必须大于
$CN_DISABLED = Cn @(0x5DF2,0x505C,0x7528)                                          # 已停用

$lg = Invoke-RestMethod -Uri "$base/auth/login" -Method Post -ContentType 'application/json' -Body '{"username":"lin","password":"123","companyId":1}'
$h = @{ Authorization = $lg.data.token }
function ApiRaw($method, $path, [string]$json) {
  try {
    if ($json) { return Invoke-RestMethod -Uri "$base$path" -Method $method -Headers $h -ContentType 'application/json' -Body $json }
    return Invoke-RestMethod -Uri "$base$path" -Method $method -Headers $h
  } catch { return $_.ErrorDetails.Message }
}
function MaxPaymentId { return [int](SqlOne 'SELECT COALESCE(MAX(id),0) FROM finance_payment') }
# NOTE: do NOT name this parameter `$pid` -- PowerShell variable names are case-insensitive and `$PID`
# is a read-only automatic variable, so `function CountAdv([int]$pid)` throws
# SessionStateUnauthorizedAccessException and the call silently produces nothing (same trap as the batch A guard).
function CountAdv([int]$paymentId) { return (D (SqlOne ("SELECT COUNT(*) FROM finance_payable WHERE source_id=" + $paymentId + " AND status='ADVANCE'"))) }

# ---- fixtures: a payable (UNSETTLED, amount>0) + its supplier + a solvent account ----
$payId   = [int](SqlOne "SELECT id FROM finance_payable WHERE status='UNSETTLED' AND IFNULL(amount,0) > 0 AND IFNULL(unpaid_amount,0) > 0 ORDER BY IFNULL(unpaid_amount,0) ASC, id LIMIT 1")
$payNo   = SqlOne ("SELECT IFNULL(bill_no,'') FROM finance_payable WHERE id=" + $payId)
$supId   = SqlOne ("SELECT IFNULL(supplier_id,0) FROM finance_payable WHERE id=" + $payId)
$unpaid  = D (SqlOne ("SELECT IFNULL(unpaid_amount,0) FROM finance_payable WHERE id=" + $payId))
$acctId  = [int](SqlOne "SELECT account_id FROM finance_cashflow GROUP BY account_id HAVING SUM(income)-SUM(expense) > 100000 ORDER BY SUM(income)-SUM(expense) DESC LIMIT 1")
$acctBal = D (SqlOne ("SELECT IFNULL(SUM(income)-SUM(expense),0) FROM finance_cashflow WHERE account_id=" + $acctId))
$flipId  = [int](SqlOne "SELECT id FROM finance_account WHERE status=1 ORDER BY id LIMIT 1")
Write-Host ('[SEED] payable=' + $payId + '(' + $payNo + ',unpaid=' + $unpaid + ',supplier=' + $supId + ') account=' + $acctId + '(balance=' + $acctBal + ') flipAccount=' + $flipId)
Ok (($payId -gt 0) -and ($supId -ne '0') -and ($acctId -gt 0) -and ($acctBal -gt 0) -and ($flipId -gt 0)) 'seed data resolved'

$created = New-Object System.Collections.ArrayList
try {
  Step '1) F7-213: accountId given but NO split row and NO write-off item must be rejected (0-yuan path)'
  $before = MaxPaymentId
  $body = '{"payment":{"supplierId":' + $supId + ',"accountId":' + $acctId + ',"paymentDate":"2026-09-29","remark":"AUDIT-F7-213"}}'
  $r = ApiRaw 'Post' '/finance/payment' $body
  Write-Host ('  response=' + "$r")
  Ok (("$r") -match [regex]::Escape($CN_NOAMT)) 'rejected with the "amount not filled" message'
  Ok ((MaxPaymentId) -eq $before) 'no payment row was created (transaction rolled back)'

  Step '2) F7-214: a write-off line with thisAmount = 0 must be rejected'
  $before = MaxPaymentId
  $body = '{"payment":{"supplierId":' + $supId + ',"paymentDate":"2026-09-29","remark":"AUDIT-F7-214"},' +
          '"accounts":[{"accountId":' + $acctId + ',"amount":5}],' +
          '"items":[{"payableId":' + $payId + ',"payableBillNo":"' + $payNo + '","thisAmount":0}]}'
  $r = ApiRaw 'Post' '/finance/payment' $body
  Write-Host ('  response=' + "$r")
  Ok (("$r") -match [regex]::Escape($CN_ITEMAMT)) 'rejected with the "write-off amount must be > 0" message'
  Ok ((MaxPaymentId) -eq $before) 'no payment row was created'

  Step '3) F7-215: a DISABLED account must not be usable (status flipped to 0, restored in finally)'
  $null = & $MYSQL --default-character-set=utf8mb4 -uroot -D beichen_erp -e ("UPDATE finance_account SET status=0 WHERE id=" + $flipId) 2>$null
  $before = MaxPaymentId
  $body = '{"payment":{"supplierId":' + $supId + ',"paymentDate":"2026-09-29","remark":"AUDIT-F7-215"},' +
          '"accounts":[{"accountId":' + $flipId + ',"amount":1}]}'
  $r = ApiRaw 'Post' '/finance/payment' $body
  Write-Host ('  response=' + "$r")
  Ok (("$r") -match [regex]::Escape($CN_DISABLED)) 'rejected with the "account disabled" message'
  Ok ((MaxPaymentId) -eq $before) 'no payment row was created'
  $null = & $MYSQL --default-character-set=utf8mb4 -uroot -D beichen_erp -e ("UPDATE finance_account SET status=1 WHERE id=" + $flipId) 2>$null
  Ok (((SqlOne ("SELECT status FROM finance_account WHERE id=" + $flipId)) -eq '1')) 'account status restored to 1'

  Step '4) F7-218: changing the supplier on a draft must recompute supplier_type'
  # two suppliers whose FIRST type ref differs (so the recompute is observable)
  $supA = [int](SqlOne "SELECT supplier_id FROM supplier_type_ref GROUP BY supplier_id HAVING COUNT(*) > 0 ORDER BY MIN(type_code) LIMIT 1")
  $typeA = SqlOne ("SELECT type_code FROM supplier_type_ref WHERE supplier_id=" + $supA + " ORDER BY type_code LIMIT 1")
  # NOTE: use a derived table, not a correlated subquery in WHERE -- the correlated form fails silently here
  # (SqlOne returns '' => [int] gives 0 => the assertion below would then pass vacuously).
  $supB = [int](SqlOne ("SELECT supplier_id FROM (SELECT supplier_id, MIN(type_code) mn FROM supplier_type_ref GROUP BY supplier_id) x WHERE x.mn <> '" + $typeA + "' ORDER BY x.supplier_id LIMIT 1"))
  $typeB = SqlOne ("SELECT type_code FROM supplier_type_ref WHERE supplier_id=" + $supB + " ORDER BY type_code LIMIT 1")
  Write-Host ('  supplierA=' + $supA + '(' + $typeA + ') supplierB=' + $supB + '(' + $typeB + ')')
  $body = '{"payment":{"supplierId":' + $supA + ',"paymentDate":"2026-09-29","remark":"AUDIT-F7-218"},' +
          '"accounts":[{"accountId":' + $acctId + ',"amount":3}]}'
  $null = ApiRaw 'Post' '/finance/payment' $body
  $rid = MaxPaymentId
  [void]$created.Add($rid)
  $t1 = SqlOne ("SELECT IFNULL(supplier_type,'') FROM finance_payment WHERE id=" + $rid)
  $body = '{"payment":{"supplierId":' + $supB + ',"paymentDate":"2026-09-29","remark":"AUDIT-F7-218"},' +
          '"accounts":[{"accountId":' + $acctId + ',"amount":3}]}'
  $null = ApiRaw 'Put' ('/finance/payment/' + $rid) $body
  $t2 = SqlOne ("SELECT IFNULL(supplier_type,'') FROM finance_payment WHERE id=" + $rid)
  Write-Host ('  supplier_type ' + $t1 + ' -> ' + $t2 + ' (expected ' + $typeA + ' -> ' + $typeB + ')')
  Ok (($t1 -eq $typeA)) 'the draft was created with supplier A type'
  Ok (($t2 -eq $typeB)) 'changing the supplier recomputed supplier_type'

  Step '5) F7-216: over-payment keys the advance by the PAYMENT code, and re-audit REUSES the same row'
  $over = $unpaid + 5
  $body = '{"payment":{"supplierId":' + $supId + ',"paymentDate":"2026-09-29","remark":"AUDIT-F7-216"},' +
          '"accounts":[{"accountId":' + $acctId + ',"amount":' + $over + '}],' +
          '"items":[{"payableId":' + $payId + ',"payableBillNo":"' + $payNo + '","thisAmount":' + $over + '}]}'
  $null = ApiRaw 'Post' '/finance/payment' $body
  $rid2 = MaxPaymentId
  [void]$created.Add($rid2)
  $code2 = SqlOne ("SELECT code FROM finance_payment WHERE id=" + $rid2)
  $null = ApiRaw 'Put' ('/finance/payment/' + $rid2 + '/audit') $null
  $advBill = SqlOne ("SELECT bill_no FROM finance_payable WHERE source_id=" + $rid2 + " AND status='ADVANCE' ORDER BY id DESC LIMIT 1")
  $advSrc  = SqlOne ("SELECT IFNULL(source_bill_no,'') FROM finance_payable WHERE source_id=" + $rid2 + " AND status='ADVANCE' ORDER BY id DESC LIMIT 1")
  $advAmt  = SqlOne ("SELECT amount FROM finance_payable WHERE source_id=" + $rid2 + " AND status='ADVANCE' ORDER BY id DESC LIMIT 1")
  Write-Host ('  payment=' + $code2 + ' advance bill_no=' + $advBill + ' source_bill_no=' + $advSrc + ' amount=' + $advAmt)
  Ok (($advBill -eq ($code2 + '-ADVANCE'))) 'advance bill_no is derived from the PAYMENT code (F7-216)'
  Ok (($advSrc -eq $code2)) 'advance source_bill_no is the payment code (same semantics as the unsettled branch)'
  Ok (((D $advAmt) -eq (-5))) ('the over-paid part is 5 (got ' + $advAmt + ')')
  Ok ((CountAdv $rid2) -eq 1) 'exactly one advance row after the first audit'
  # un-audit -> re-audit must reuse the SAME row (pre-fix: a new YF- number every time => 2 rows)
  $null = ApiRaw 'Put' ('/finance/payment/' + $rid2 + '/un-audit') $null
  $null = ApiRaw 'Put' ('/finance/payment/' + $rid2 + '/audit') $null
  $advBill2 = SqlOne ("SELECT bill_no FROM finance_payable WHERE source_id=" + $rid2 + " AND status='ADVANCE' ORDER BY id DESC LIMIT 1")
  $advRows2 = D (SqlOne ("SELECT COUNT(*) FROM finance_payable WHERE source_id=" + $rid2 + " AND status='ADVANCE'"))
  Write-Host ('  after un-audit + re-audit: bill_no=' + $advBill2 + ' rows(total incl. cancelled)=' + $advRows2)
  Ok (($advBill2 -eq ($code2 + '-ADVANCE'))) 're-audit keeps the same stable key'
  Ok (($advRows2 -eq 1)) ('re-audit REUSES the same advance row (got ' + $advRows2 + ' rows)')

  Step '6) cleanup: un-audit + cancel every probe payment and assert the ledger is restored'
  foreach ($pid2 in $created) {
    $null = ApiRaw 'Put' ('/finance/payment/' + $pid2 + '/un-audit') $null
    $null = ApiRaw 'Put' ('/finance/payment/' + $pid2 + '/cancel') $null
    Write-Host ('  payment ' + $pid2 + ' -> ' + (SqlOne ("SELECT status FROM finance_payment WHERE id=" + $pid2)))
  }
  $unpaidAfter = D (SqlOne ("SELECT IFNULL(unpaid_amount,0) FROM finance_payable WHERE id=" + $payId))
  $advLeft = D (SqlOne ("SELECT COUNT(*) FROM finance_payable WHERE source_id=" + $rid2 + " AND status='ADVANCE'"))
  Write-Host ('  payable unpaid ' + $unpaid + ' -> ' + $unpaidAfter + ' ; advance still ADVANCE=' + $advLeft)
  Ok (($unpaidAfter -eq $unpaid)) 'the over-paid payable was restored to its original unpaid amount'
  Ok (($advLeft -eq 0)) 'the advance row was cancelled by un-audit (symmetric)'

  Step '7) static: F7-220 v-perm on danger actions + F7-221 split try/catch'
  function CountIn([string]$rel, [string]$pat) {
    $p = Join-Path $REPO ($WEB + '/' + $rel)
    return ([regex]::Matches((Get-Content -LiteralPath $p -Raw -Encoding UTF8), $pat)).Count
  }
  $c = CountIn 'payment.vue' "v-perm=""'finance:payment'"""
  Ok ($c -ge 4) ('payment.vue: v-perm on its 4 entries (got ' + $c + ')')
  $c = CountIn 'payable.vue' "v-perm=""'finance:payable-transfer'"""
  Ok ($c -ge 1) ('payable.vue: transfer button gated (got ' + $c + ')')
  $c = (CountIn 'payable-transfer/index.vue' "v-perm") + (CountIn 'payable-transfer/detail.vue' "v-perm")
  Ok ($c -ge 6) ('payable-transfer pages: v-perm on the draft actions (got ' + $c + ')')
  $c = CountIn 'payment.vue' '\} catch \{ return \}'
  Ok ($c -ge 3) ('payment.vue: confirm and API call split into two tries (got ' + $c + ' cancels)')
  $c = CountIn 'payable-supplier.vue' 'catch \{ summary\.value = \{\}'
  Ok ($c -ge 1) ('payable-supplier.vue: Promise.all has a catch fallback (got ' + $c + ')')
} finally {
  $null = & $MYSQL --default-character-set=utf8mb4 -uroot -D beichen_erp -e ("UPDATE finance_account SET status=1 WHERE id=" + $flipId) 2>$null
}
Write-Host ('RESULT fix F7-212..221 PASS=' + $script:PASS + ' FAIL=' + $script:FAIL)
