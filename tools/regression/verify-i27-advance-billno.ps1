# I27 verification (2026-09-18): advance (预收) bookkeeping must NOT be re-settled, and the advance
# bill_no must be idempotent (single "-ADVANCE" suffix, never over the 50-char column).
# API + DB cross-check, rerunnable, ASCII ONLY.
$ErrorActionPreference = 'Continue'
$base = 'http://localhost:8080/api'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$script:PASS = 0; $script:FAIL = 0
function Ok([bool]$c, [string]$m) { if ($c) { $script:PASS++; Write-Host ('PASS ' + $m) } else { $script:FAIL++; Write-Host ('FAIL ' + $m) } }
function Step($n) { Write-Host ('--- STEP ' + $n) }
function SqlOne([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null
  $l = ((@($o) | ForEach-Object { "$_" }) -join "`n").Trim() -split "`n"
  if ($l.Count -lt 2) { return '' }
  return (($l[1] -split "`t")[0]).Trim()
}
function D($s) { if (-not $s) { return [decimal]0 }; return [decimal]$s }
# Chinese words built from code points (this file stays ASCII-only)
$CN_ADV = [string][char]0x9884 + [string][char]0x6536          # 预收
$CN_ADV2 = [string][char]0x9884 + [string][char]0x4ED8         # 预付

$lg = Invoke-RestMethod -Uri "$base/auth/login" -Method Post -ContentType 'application/json' -Body '{"username":"lin","password":"123","companyId":1}'
$h = @{ Authorization = $lg.data.token }
function ApiRaw($method, $path, [string]$json) {
  try {
    if ($json) { return Invoke-RestMethod -Uri "$base$path" -Method $method -Headers $h -ContentType 'application/json' -Body $json }
    return Invoke-RestMethod -Uri "$base$path" -Method $method -Headers $h
  } catch { return $_.ErrorDetails.Message }
}
function LastReceiptId { return [int](SqlOne 'SELECT COALESCE(MAX(id),0) FROM finance_receipt') }
function PostReceipt([int]$recvId, [string]$recvBill, [int]$amt, [int]$custId, [int]$acctId, [string]$tag) {
  $body = '{"subjectType":"CUSTOMER","customerId":' + $custId + ',"accountId":' + $acctId + ',"receiptDate":"2026-09-18","remark":"I27-VERIFY-' + $tag + '","items":[{"receivableId":' + $recvId + ',"receivableBillNo":"' + $recvBill + '","thisAmount":' + $amt + '}]}'
  return (ApiRaw 'Post' '/finance/receipt' $body)
}

Step '0) seed pickers'
$custId = [int](SqlOne "SELECT id FROM customer ORDER BY id LIMIT 1")
$acctId = [int](SqlOne "SELECT id FROM finance_account WHERE account_name='CASH-01' LIMIT 1")
$advId = [int](SqlOne "SELECT id FROM finance_receivable WHERE status='ADVANCE' ORDER BY id LIMIT 1")
$advBill = SqlOne ("SELECT bill_no FROM finance_receivable WHERE id=" + $advId)
$settledId = [int](SqlOne "SELECT id FROM finance_receivable WHERE status='SETTLED' ORDER BY id DESC LIMIT 1")
$settledBill = SqlOne ("SELECT bill_no FROM finance_receivable WHERE id=" + $settledId)
$openId = [int](SqlOne "SELECT id FROM finance_receivable WHERE status IN ('UNSETTLED','PARTIAL') AND unpaid_amount>=100 ORDER BY id LIMIT 1")
$openBill = SqlOne ("SELECT bill_no FROM finance_receivable WHERE id=" + $openId)
Write-Host ('[SEED] customer=' + $custId + ' account=' + $acctId + ' advance=' + $advId + '(' + $advBill + ') settled=' + $settledId + '(' + $settledBill + ') open=' + $openId + '(' + $openBill + ')')
$suffixRows0 = D (SqlOne "SELECT COUNT(*) FROM finance_receivable WHERE bill_no LIKE '%-ADVANCE-ADVANCE%'")
$advRows0 = D (SqlOne "SELECT COUNT(*) FROM finance_receivable WHERE status='ADVANCE'")
$advCount0 = D (SqlOne "SELECT COUNT(*) FROM finance_receivable")
Write-Host ('[BASE] doubleSuffixRows=' + $suffixRows0 + ' advanceRows=' + $advRows0 + ' receivableRows=' + $advCount0)
Ok (($advId -gt 0) -and ($settledId -gt 0) -and ($openId -gt 0)) 'picked an advance row, a settled row and an open row'

Step '1) guard: writing a receipt off an ADVANCE row must be rejected with a clear message'
$r = PostReceipt $advId $advBill 100 $custId $acctId 'GUARD'
$rid = LastReceiptId
$idBefore = SqlOne 'SELECT COALESCE(MAX(id),0) FROM finance_receivable'
Ok (($rid -gt 0)) ('receipt draft created for the guard test (id=' + $rid + ')')
$au = ApiRaw 'Put' ('/finance/receipt/' + $rid + '/audit') $null
$msg = "$au"
Write-Host ('  audit response=' + $msg)
$st = SqlOne ("SELECT status FROM finance_receipt WHERE id=" + $rid)
$idAfter = SqlOne 'SELECT COALESCE(MAX(id),0) FROM finance_receivable'
Write-Host ('  receipt status=' + $st + ' receivableMaxId=' + $idBefore + '->' + $idAfter)
Ok (($msg -match [regex]::Escape($CN_ADV))) 'rejected with a message mentioning the advance bookkeeping (预收台账)'
Ok (($st -eq 'DRAFT')) ('receipt stays DRAFT (status=' + $st + ')')
Ok (($idBefore -eq $idAfter)) 'no new receivable row was created by the rejected audit'
& $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e ("UPDATE finance_receipt SET status='CANCELLED' WHERE id=" + $rid) 2>$null | Out-Null
Write-Host ('  (cleanup) guard-test draft cancelled, no data removed')

Step '2) numbering stays idempotent when an over-collection creates an advance'
$r2 = PostReceipt $settledId $settledBill 100 $custId $acctId 'NUMBER'
$rid2 = LastReceiptId
Ok (($rid2 -gt $rid)) ('receipt created for the numbering test (id=' + $rid2 + ')')
$null = ApiRaw 'Put' ('/finance/receipt/' + $rid2 + '/audit') $null
$newAdvBill = SqlOne ("SELECT bill_no FROM finance_receivable WHERE source_id=" + $rid2 + " AND status='ADVANCE' ORDER BY id DESC LIMIT 1")
$newAdvLen = SqlOne ("SELECT LENGTH(bill_no) FROM finance_receivable WHERE source_id=" + $rid2 + " AND status='ADVANCE' ORDER BY id DESC LIMIT 1")
$newAdvRows = D (SqlOne ("SELECT COUNT(*) FROM finance_receivable WHERE source_id=" + $rid2 + " AND status='ADVANCE'"))
Write-Host ('  new advance bill_no=' + $newAdvBill + ' len=' + $newAdvLen + ' rows=' + $newAdvRows)
Ok (($newAdvBill -ne '')) 'over-collection still produces an advance row'
Ok (($newAdvBill -notmatch '-ADVANCE-ADVANCE')) 'advance bill_no has a SINGLE -ADVANCE suffix (no stacking)'
Ok (([int]$newAdvLen -le 50)) ('advance bill_no fits the 50-char column (len=' + $newAdvLen + ')')
Ok (($newAdvRows -eq 1)) ('exactly one advance row for this receipt (got ' + $newAdvRows + ')')
# re-audit must REUSE the same row (no duplicate, no new suffix)
$null = ApiRaw 'Put' ('/finance/receipt/' + $rid2 + '/un-audit') $null
$null = ApiRaw 'Put' ('/finance/receipt/' + $rid2 + '/audit') $null
$newAdvRows2 = D (SqlOne ("SELECT COUNT(*) FROM finance_receivable WHERE source_id=" + $rid2 + " AND status='ADVANCE'"))
$suffixRows1 = D (SqlOne "SELECT COUNT(*) FROM finance_receivable WHERE bill_no LIKE '%-ADVANCE-ADVANCE%'")
Write-Host ('  after un-audit + re-audit: advanceRows=' + $newAdvRows2 + ' doubleSuffixRows=' + $suffixRows1)
Ok (($newAdvRows2 -eq 1)) ('re-audit reuses the same advance row (got ' + $newAdvRows2 + ')')
Ok (($suffixRows1 -eq $suffixRows0)) ('no NEW stacked-suffix row appeared (' + $suffixRows0 + ' -> ' + $suffixRows1 + ')')

Step '3) normal write-off against an OPEN receivable still works'
$paid0 = D (SqlOne ("SELECT COALESCE(paid_amount,0) FROM finance_receivable WHERE id=" + $openId))
$r3 = PostReceipt $openId $openBill 100 $custId $acctId 'OPEN'
$rid3 = LastReceiptId
$null = ApiRaw 'Put' ('/finance/receipt/' + $rid3 + '/audit') $null
$paid1 = D (SqlOne ("SELECT COALESCE(paid_amount,0) FROM finance_receivable WHERE id=" + $openId))
$st3 = SqlOne ("SELECT status FROM finance_receipt WHERE id=" + $rid3)
Write-Host ('  open receivable paid ' + $paid0 + ' -> ' + $paid1 + ' ; receipt status=' + $st3)
Ok (($paid1 -eq ($paid0 + 100))) ('open receivable was settled by 100 (' + $paid0 + ' -> ' + $paid1 + ')')
Ok (($st3 -eq 'AUDITED')) ('receipt audited (status=' + $st3 + ')')

Step '4) payable side: ADVANCE payable must be rejected too'
$advPayId = [int](SqlOne "SELECT id FROM finance_payable WHERE status='ADVANCE' ORDER BY id LIMIT 1")
if ($advPayId -gt 0) {
  $pSupId = SqlOne ("SELECT supplier_id FROM finance_payable WHERE id=" + $advPayId)
  $pBillNo = SqlOne ("SELECT bill_no FROM finance_payable WHERE id=" + $advPayId)
  $body = '{"supplierId":' + $pSupId + ',"accountId":' + $acctId + ',"paymentDate":"2026-09-18","remark":"I27-VERIFY-PAY","items":[{"payableId":' + $advPayId + ',"payableBillNo":"' + $pBillNo + '","thisAmount":50}]}'
  $pr = ApiRaw 'Post' '/finance/payment' $body
  $pid2 = [int](SqlOne 'SELECT COALESCE(MAX(id),0) FROM finance_payment')
  $pu = ApiRaw 'Put' ('/finance/payment/' + $pid2 + '/audit') $null
  $pmsg = "$pu"
  Write-Host ('  payable audit response=' + $pmsg)
  Ok (($pmsg -match [regex]::Escape($CN_ADV2))) 'advance payable rejected with a message mentioning 预付台账'
} else {
  Write-Host ('  INFO no ADVANCE payable row exists -> payable guard not exercised')
}

Write-Host ('RESULT I27 PASS=' + $script:PASS + ' FAIL=' + $script:FAIL)
