# Fix verification (finance audit batch A, 2026-09-29): F7-203 .. F7-210.
# ASCII ONLY on purpose (this file has no BOM; PS 5.1 would read Chinese literals as GBK).
# Chinese needles are built from code points (same trick as verify-i27-advance-billno.ps1).
#
#   F7-205  no split accounts AND no write-off items => the request must be REJECTED (it used to silently
#           create a 0-amount receipt that could even be audited)
#   F7-206  a write-off line with thisAmount <= 0 must be REJECTED (create/update side; audit only backs
#           out legacy negatives)
#   F7-207  a DISABLED account (status=0) must not be usable for a receipt
#   F7-208  /finance/receipt/unpaid-receivables?subjectType=SUPPLIER without supplierId must return EMPTY
#   F7-203  over-collection creates the ADVANCE row keyed by the RECEIPT code -- two receipts over-paying
#           the SAME receivable must produce TWO distinct advance rows (the old key was the receivable bill
#           no, so the second silently overwrote the first)
#   F7-204  two receipts settling the SAME receivable concurrently: the ledger must never end up negative
#           WITHOUT a matching advance row (the normal branch now carries a CAS condition)
#
# Writes: the probe creates receipts and, in the cleanup step, un-audits + cancels them (it asserts the
# receivables are restored to their original unpaid amounts). Account 36's status is flipped to 0 for one
# check and restored in the finally block. No pre-existing row is otherwise modified or deleted.
$ErrorActionPreference = 'Continue'
$base = 'http://localhost:8080/api'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$env:MYSQL_PWD = 'root'
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
# needles (code points, file stays ASCII)
$CN_NOAMT    = Cn @(0x672A,0x586B,0x6536,0x6B3E,0x91D1,0x989D)                    # 未填收款金额
$CN_ITEMAMT  = Cn @(0x6838,0x9500,0x91D1,0x989D,0x5FC5,0x987B,0x5927,0x4E8E)      # 核销金额必须大于
$CN_DISABLED = Cn @(0x5DF2,0x505C,0x7528)                                          # 已停用
$CN_ADV      = Cn @(0x9884,0x6536)                                                 # 预收
$CN_CHANGED  = Cn @(0x672A,0x6536,0x989D,0x5DF2,0x53D1,0x751F,0x53D8,0x5316)      # 未收额已发生变化

$lg = Invoke-RestMethod -Uri "$base/auth/login" -Method Post -ContentType 'application/json' -Body '{"username":"lin","password":"123","companyId":1}'
$h = @{ Authorization = $lg.data.token }
function ApiRaw($method, $path, [string]$json) {
  try {
    if ($json) { return Invoke-RestMethod -Uri "$base$path" -Method $method -Headers $h -ContentType 'application/json' -Body $json }
    return Invoke-RestMethod -Uri "$base$path" -Method $method -Headers $h
  } catch { return $_.ErrorDetails.Message }
}
function MaxReceiptId { return [int](SqlOne 'SELECT COALESCE(MAX(id),0) FROM finance_receipt') }
function PostReceipt([int]$recvId, [string]$recvBill, [string]$amt, [int]$acctId, [string]$tag) {
  $custId = [int](SqlOne ("SELECT COALESCE(customer_id,0) FROM finance_receivable WHERE id=" + $recvId))
  $body = '{"subjectType":"CUSTOMER","customerId":' + $custId + ',"accountId":' + $acctId + ',"receiptDate":"2026-09-29","remark":"AUDIT-F7-' + $tag + '","items":[{"receivableId":' + $recvId + ',"receivableBillNo":"' + $recvBill + '","thisAmount":' + $amt + '}]}'
  return (ApiRaw 'Post' '/finance/receipt' $body)
}
function UnAuditCancel([int]$rid) {
  $null = ApiRaw 'Put' ('/finance/receipt/' + $rid + '/un-audit') $null
  $null = ApiRaw 'Put' ('/finance/receipt/' + $rid + '/cancel') $null
  return (SqlOne ("SELECT status FROM finance_receipt WHERE id=" + $rid))
}

$acctId = [int](SqlOne "SELECT id FROM finance_account WHERE account_name='CASH-01' AND status=1 ORDER BY id LIMIT 1")
$acctId2 = [int](SqlOne "SELECT id FROM finance_account WHERE account_name='BANK-01' AND status=1 ORDER BY id LIMIT 1")
# two open customer receivables (the probe over-pays / double-settles them, then restores them)
$openId = [int](SqlOne "SELECT id FROM finance_receivable WHERE subject_type='CUSTOMER' AND status IN ('UNSETTLED','PARTIAL') AND unpaid_amount > 0 ORDER BY unpaid_amount DESC, id LIMIT 1")
$openBill = SqlOne ("SELECT bill_no FROM finance_receivable WHERE id=" + $openId)
$openUnpaid = D (SqlOne ("SELECT COALESCE(unpaid_amount,0) FROM finance_receivable WHERE id=" + $openId))
# NOTE: the whole concatenation MUST sit inside parentheses -- `SqlOne "A" + $x + "B"` passes three
# separate arguments in PS 5.1 (the SQL then gets silently truncated and the query returns nothing).
$open2Id = [int](SqlOne ("SELECT id FROM finance_receivable WHERE subject_type='CUSTOMER' AND status IN ('UNSETTLED','PARTIAL') AND unpaid_amount > 0 AND id <> " + $openId + " ORDER BY unpaid_amount DESC, id LIMIT 1"))
$open2Bill = SqlOne ("SELECT bill_no FROM finance_receivable WHERE id=" + $open2Id)
$open2Unpaid = D (SqlOne ("SELECT COALESCE(unpaid_amount,0) FROM finance_receivable WHERE id=" + $open2Id))
Write-Host ('[SEED] account=' + $acctId + '/' + $acctId2 + ' openRecv=' + $openId + '(' + $openBill + ',unpaid=' + $openUnpaid + ') openRecv2=' + $open2Id + '(' + $open2Bill + ',unpaid=' + $open2Unpaid + ')')
Ok (($acctId -gt 0) -and ($openId -gt 0) -and ($open2Id -gt 0) -and ($open2Unpaid -gt 0)) 'seed data resolved'

$created = New-Object System.Collections.ArrayList
try {
  Step '1) F7-205: accountId given but NO split row and NO write-off item must be rejected (0-amount path)'
  $before = MaxReceiptId
  $body = '{"receipt":{"subjectType":"CUSTOMER","customerId":' + [int](SqlOne ("SELECT COALESCE(customer_id,0) FROM finance_receivable WHERE id=" + $openId)) + ',"accountId":' + $acctId + ',"receiptDate":"2026-09-29","remark":"AUDIT-F7-205"}}'
  $r = ApiRaw 'Post' '/finance/receipt' $body
  Write-Host ('  response=' + "$r")
  Ok (("$r") -match [regex]::Escape($CN_NOAMT)) 'rejected with the "amount not filled" message'
  Ok ((MaxReceiptId) -eq $before) 'no receipt row was created (transaction rolled back)'

  Step '2) F7-206: a write-off line with thisAmount = 0 must be rejected'
  $before = MaxReceiptId
  $custId = [int](SqlOne ("SELECT COALESCE(customer_id,0) FROM finance_receivable WHERE id=" + $openId))
  $body = '{"subjectType":"CUSTOMER","customerId":' + $custId + ',"accountId":' + $acctId + ',"receiptDate":"2026-09-29","remark":"AUDIT-F7-206","accounts":[{"accountId":' + $acctId + ',"amount":5}],"items":[{"receivableId":' + $openId + ',"receivableBillNo":"' + $openBill + '","thisAmount":0}]}'
  $r = ApiRaw 'Post' '/finance/receipt' $body
  Write-Host ('  response=' + "$r")
  Ok (("$r") -match [regex]::Escape($CN_ITEMAMT)) 'rejected with the "write-off amount must be > 0" message'
  Ok ((MaxReceiptId) -eq $before) 'no receipt row was created'

  Step '3) F7-207: a DISABLED account must not be usable (account status flipped to 0, restored in finally)'
  $null = & $MYSQL --default-character-set=utf8mb4 -uroot -D beichen_erp -e ("UPDATE finance_account SET status=0 WHERE id=" + $acctId) 2>$null
  $before = MaxReceiptId
  $body = '{"subjectType":"CUSTOMER","customerId":' + $custId + ',"accountId":' + $acctId + ',"receiptDate":"2026-09-29","remark":"AUDIT-F7-207","items":[{"receivableId":' + $openId + ',"receivableBillNo":"' + $openBill + '","thisAmount":1}]}'
  $r = ApiRaw 'Post' '/finance/receipt' $body
  Write-Host ('  response=' + "$r")
  Ok (("$r") -match [regex]::Escape($CN_DISABLED)) 'rejected with the "account disabled" message'
  Ok ((MaxReceiptId) -eq $before) 'no receipt row was created'
  $null = & $MYSQL --default-character-set=utf8mb4 -uroot -D beichen_erp -e ("UPDATE finance_account SET status=1 WHERE id=" + $acctId) 2>$null
  $restored = SqlOne ("SELECT status FROM finance_account WHERE id=" + $acctId)
  Ok (($restored -eq '1')) ('account status restored to 1 (got ' + $restored + ')')

  Step '4) F7-208: supplier branch without supplierId must return an empty list'
  $r = ApiRaw 'Get' '/finance/receipt/unpaid-receivables?subjectType=SUPPLIER' $null
  $cnt = @($r.data).Count
  Write-Host ('  rows returned=' + $cnt)
  Ok (($cnt -eq 0)) ('no rows leaked for a supplier query without supplierId (got ' + $cnt + ')')

  Step '5) F7-203: two receipts over-paying the SAME receivable must produce TWO advance rows'
  $overA = $openUnpaid + 5
  $rA = PostReceipt $openId $openBill $overA $acctId '203A'
  $ridA = MaxReceiptId
  [void]$created.Add($ridA)
  $null = ApiRaw 'Put' ('/finance/receipt/' + $ridA + '/audit') $null
  $advA = SqlOne ("SELECT id FROM finance_receivable WHERE source_id=" + $ridA + " AND status='ADVANCE' ORDER BY id DESC LIMIT 1")
  $advABill = SqlOne ("SELECT bill_no FROM finance_receivable WHERE id=" + $advA)
  $advAAmt = SqlOne ("SELECT amount FROM finance_receivable WHERE id=" + $advA)
  Write-Host ('  A: receipt=' + $ridA + ' advance=' + $advA + '(' + $advABill + ',amount=' + $advAAmt + ')')
  Ok (($advA -ne '') -and ((D $advAAmt) -eq (-5))) ('over-payment of 5 produced an advance row of -5 (got ' + $advAAmt + ')')
  Ok (($advABill -like ($openBill + '*')) -eq $false) 'the advance bill_no is NOT derived from the receivable bill no (old key)'
  Ok (($advABill -like 'SK-*-ADVANCE')) 'the advance bill_no is derived from the RECEIPT code (new key)'

  $rB = PostReceipt $openId $openBill '5' $acctId '203B'
  $ridB = MaxReceiptId
  [void]$created.Add($ridB)
  $null = ApiRaw 'Put' ('/finance/receipt/' + $ridB + '/audit') $null
  $advB = SqlOne ("SELECT id FROM finance_receivable WHERE source_id=" + $ridB + " AND status='ADVANCE' ORDER BY id DESC LIMIT 1")
  $advBAmt = SqlOne ("SELECT amount FROM finance_receivable WHERE id=" + $advB)
  $advAStill = SqlOne ("SELECT amount FROM finance_receivable WHERE id=" + $advA)
  Write-Host ('  B: receipt=' + $ridB + ' advance=' + $advB + '(amount=' + $advBAmt + ') ; A still=' + $advAStill)
  Ok (($advB -ne '') -and ($advB -ne $advA)) 'the second over-payment created its OWN advance row (no overwrite)'
  Ok (((D $advBAmt) -eq (-5)) -and ((D $advAStill) -eq (-5))) 'both advance amounts are intact (-5 and -5)'

  Step '6) F7-204: two concurrent settlements of the SAME receivable must never go negative without an advance'
  $bodyC = '{"subjectType":"CUSTOMER","customerId":' + [int](SqlOne ("SELECT COALESCE(customer_id,0) FROM finance_receivable WHERE id=" + $open2Id)) + ',"accountId":' + $acctId2 + ',"receiptDate":"2026-09-29","remark":"AUDIT-F7-204C","items":[{"receivableId":' + $open2Id + ',"receivableBillNo":"' + $open2Bill + '","thisAmount":' + $open2Unpaid + '}]}'
  $rC = ApiRaw 'Post' '/finance/receipt' $bodyC
  $ridC = MaxReceiptId
  [void]$created.Add($ridC)
  $bodyD = '{"subjectType":"CUSTOMER","customerId":' + [int](SqlOne ("SELECT COALESCE(customer_id,0) FROM finance_receivable WHERE id=" + $open2Id)) + ',"accountId":' + $acctId2 + ',"receiptDate":"2026-09-29","remark":"AUDIT-F7-204D","items":[{"receivableId":' + $open2Id + ',"receivableBillNo":"' + $open2Bill + '","thisAmount":' + $open2Unpaid + '}]}'
  $rD = ApiRaw 'Post' '/finance/receipt' $bodyD
  $ridD = MaxReceiptId
  [void]$created.Add($ridD)
  $tok = $lg.data.token
  $j1 = Start-Job -ScriptBlock { param($u, $t, $id) try { Invoke-RestMethod -Uri ($u + '/finance/receipt/' + $id + '/audit') -Method Put -Headers @{ Authorization = $t } } catch { $_.ErrorDetails.Message } } -ArgumentList $base, $tok, $ridC
  $j2 = Start-Job -ScriptBlock { param($u, $t, $id) try { Invoke-RestMethod -Uri ($u + '/finance/receipt/' + $id + '/audit') -Method Put -Headers @{ Authorization = $t } } catch { $_.ErrorDetails.Message } } -ArgumentList $base, $tok, $ridD
  $null = Wait-Job $j1, $j2 -Timeout 60
  $out1 = ("$(Receive-Job $j1)"); $out2 = ("$(Receive-Job $j2)")
  Remove-Job $j1, $j2 -Force
  Write-Host ('  C response=' + $out1)
  Write-Host ('  D response=' + $out2)
  $stC = SqlOne ("SELECT status FROM finance_receipt WHERE id=" + $ridC)
  $stD = SqlOne ("SELECT status FROM finance_receipt WHERE id=" + $ridD)
  $unpaid2 = D (SqlOne ("SELECT COALESCE(unpaid_amount,0) FROM finance_receivable WHERE id=" + $open2Id))
  $adv2 = D (SqlOne ("SELECT COALESCE(SUM(amount),0) FROM finance_receivable WHERE source_id IN (" + $ridC + "," + $ridD + ") AND status='ADVANCE'"))
  $advRows2 = D (SqlOne ("SELECT COUNT(*) FROM finance_receivable WHERE source_id IN (" + $ridC + "," + $ridD + ") AND status='ADVANCE'"))
  Write-Host ('  ledger unpaid=' + $unpaid2 + ' advanceSum=' + $adv2 + ' advanceRows=' + $advRows2 + ' statuses=' + $stC + '/' + $stD)
  $explained = (($unpaid2 -ge 0) -or (($adv2 -eq $unpaid2) -and ($advRows2 -ge 1)))
  Ok ($explained) 'a negative ledger residual is always explained by a matching advance row (CAS held)'

  Step '7) cleanup: un-audit + cancel the probe receipts and assert the ledgers are restored'
  foreach ($rid in $created) {
    $st = UnAuditCancel $rid
    Write-Host ('  receipt ' + $rid + ' -> ' + $st)
  }
  $openUnpaidAfter = D (SqlOne ("SELECT COALESCE(unpaid_amount,0) FROM finance_receivable WHERE id=" + $openId))
  $open2UnpaidAfter = D (SqlOne ("SELECT COALESCE(unpaid_amount,0) FROM finance_receivable WHERE id=" + $open2Id))
  $advLeft = D (SqlOne ("SELECT COUNT(*) FROM finance_receivable WHERE source_id IN (" + ($created -join ',') + ") AND status='ADVANCE'"))
  Write-Host ('  openRecv ' + $openUnpaid + ' -> ' + $openUnpaidAfter + ' ; openRecv2 ' + $open2Unpaid + ' -> ' + $open2UnpaidAfter + ' ; advances still ADVANCE=' + $advLeft)
  Ok (($openUnpaidAfter -eq $openUnpaid)) 'receivable 1 restored to its original unpaid amount'
  Ok (($open2UnpaidAfter -eq $open2Unpaid)) 'receivable 2 restored to its original unpaid amount'
  Ok (($advLeft -eq 0)) 'all probe advance rows were cancelled by un-audit (symmetric)'
} finally {
  $null = & $MYSQL --default-character-set=utf8mb4 -uroot -D beichen_erp -e ("UPDATE finance_account SET status=1 WHERE id=" + $acctId) 2>$null
}
Write-Host ('RESULT fix F7-203..210 PASS=' + $script:PASS + ' FAIL=' + $script:FAIL)
