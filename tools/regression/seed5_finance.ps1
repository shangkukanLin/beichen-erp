# Seed 5/6: finance + stock take (ASCII only; Chinese via char codes)
$ErrorActionPreference = 'Continue'
function Cn([int[]]$c) { return -join ($c | ForEach-Object { [char]$_ }) }
$ids = Get-Content 'c:\Users\75629\CodeBuddy\20260710123705\seed_ids.json' -Raw -Encoding UTF8 | ConvertFrom-Json
$loginBody = @{ username = 'lin'; password = '123'; companyId = 1 } | ConvertTo-Json
$login = Invoke-RestMethod -Uri 'http://localhost:8080/api/auth/login' -Method Post -ContentType 'application/json' -Body $loginBody
$H = @{ Authorization = $login.data.token }
function Post($path, $obj) {
  $json = if ($obj) { $obj | ConvertTo-Json -Depth 8 } else { '{}' }
  try { return Invoke-RestMethod -Uri ('http://localhost:8080' + $path) -Method Post -Headers $H -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($json)) } catch { return $null }
}
function PutOp($path, $obj) {
  $json = if ($obj) { $obj | ConvertTo-Json -Depth 8 } else { '{}' }
  try { return Invoke-RestMethod -Uri ('http://localhost:8080' + $path) -Method Put -Headers $H -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($json)) } catch { return $null }
}
function GetP($path) { try { return (Invoke-RestMethod -Uri ('http://localhost:8080' + $path) -Headers $H).data } catch { return $null } }

$rnd = New-Object System.Random(20260908)
$custIds = @($ids.custIds); $proSupIds = @($ids.proSupIds); $whFin = @($ids.whFin)
$msgs = @()

# 1) accounts
$accIds = @()
$accDefs = @(
  @{ accountName = 'CASH-01'; accountType = 'cash'; openingBalance = 500000 },
  @{ accountName = 'BANK-01'; accountType = 'bank'; openingBalance = 2000000 },
  @{ accountName = 'WECHAT-01'; accountType = 'wechat'; openingBalance = 100000 },
  @{ accountName = 'ALIPAY-01'; accountType = 'alipay'; openingBalance = 100000 }
)
foreach ($a in $accDefs) {
  $r = Post '/api/finance/account' $a
  if ($r -and $r.code -eq 200) { } else { $msgs += ('ACC: ' + $r.code + ' ' + $r.msg) }
}
$accPage = GetP '/api/finance/account/page?pageSize=50'
$accIds = @(@($accPage.records) | ForEach-Object { $_.id })
Write-Output ('accounts=' + $accIds.Count)

# 2) expenses (10) + audit
$expOk = 0; $expFail = 0
# 2026-09-27：费用类型自本批起由后端 ExpenseType 枚举校验（未知值拒绝、大小写归一化）⇒ 这里改为规范大写 code
$expTypes = @('OFFICE', 'RENT', 'SALARY', 'TRANSPORT', 'TRAVEL', 'ENTERTAIN', 'RND', 'OTHER')
for ($i = 1; $i -le 10; $i++) {
  $e = @{ expenseType = $expTypes[$i % $expTypes.Count]; amount = [math]::Round(($rnd.Next(10000, 800000) / 100), 2); expenseDate = '2026-08-27'; accountId = [int]$accIds[$i % $accIds.Count]; remark = 'seed expense'; status = 'DRAFT' }
  $r = Post '/api/finance/expense' $e
  if ($r -and $r.code -eq 200) {
    Start-Sleep -Milliseconds 200
    $all = GetP '/api/finance/expense/page?pageSize=200'
    $new = @(@($all.records) | Where-Object { $_.status -eq 'DRAFT' } | Sort-Object { [int]$_.id } -Descending | Select-Object -First 1)
    if ($new.Count) {
      $ra = Post ('/api/finance/expense/' + $new[0].id + '/audit') $null
      if ($ra -and $ra.code -eq 200) { $expOk++ } else { $expFail++; $msgs += ('EXP audit: ' + $ra.code + ' ' + $ra.msg) }
    }
  } else { $expFail++; $msgs += ('EXP create: ' + $r.code + ' ' + $r.msg) }
}
Write-Output ('expenses ok=' + $expOk + ' fail=' + $expFail)

# 3) receipts (收款): customer unpaid receivables
$recOk = 0; $recFail = 0
foreach ($c in ($custIds | Select-Object -First 5)) {
  $unpaid = GetP ('/api/finance/receivable/unpaid?customerId=' + $c)
  if (-not $unpaid -or $unpaid.Count -eq 0) { continue }
  $items = @()
  foreach ($u in (@($unpaid) | Select-Object -First 2)) {
    $pay = [math]::Min([math]::Round(([decimal]$u.unpaidAmount / 2), 2), [decimal]$u.unpaidAmount)
    $items += @{ receivableId = [int]$u.id; thisAmount = $pay }
  }
  $total = 0; foreach ($it in $items) { $total += [decimal]$it.thisAmount }
  if ($total -le 0) { continue }
  $rc = @{ customerId = [int]$c; accountId = [int]$accIds[0]; receiptDate = '2026-08-28'; amount = $total; remark = 'seed receipt'; items = $items }
  $r = Post '/api/finance/receipt' $rc
  if ($r -and $r.code -eq 200) {
    Start-Sleep -Milliseconds 250
    $all = GetP '/api/finance/receipt/page?pageSize=200'
    $new = @(@($all.records) | Where-Object { $_.status -eq 'DRAFT' } | Sort-Object { [int]$_.id } -Descending | Select-Object -First 1)
    if ($new.Count) {
      $ra = PutOp ('/api/finance/receipt/' + $new[0].id + '/audit') $null
      if ($ra -and $ra.code -eq 200) { $recOk++ } else { $recFail++; $msgs += ('RCP audit: ' + $ra.code + ' ' + $ra.msg) }
    }
  } else { $recFail++; $msgs += ('RCP create: ' + $r.code + ' ' + $r.msg) }
}
Write-Output ('receipts ok=' + $recOk + ' fail=' + $recFail)

# 4) payments (付款)
$payOk = 0; $payFail = 0
foreach ($s in ($proSupIds | Select-Object -First 5)) {
  $unpaid = GetP ('/api/finance/payable/unpaid?supplierId=' + $s)
  if (-not $unpaid -or $unpaid.Count -eq 0) { continue }
  $items = @()
  foreach ($u in (@($unpaid) | Select-Object -First 2)) {
    $pay = [math]::Min([math]::Round(([decimal]$u.unpaidAmount / 2), 2), [decimal]$u.unpaidAmount)
    $items += @{ payableId = [int]$u.id; thisAmount = $pay }
  }
  $total = 0; foreach ($it in $items) { $total += [decimal]$it.thisAmount }
  if ($total -le 0) { continue }
  $pm = @{ supplierId = [int]$s; accountId = [int]$accIds[1]; paymentDate = '2026-08-28'; amount = $total; remark = 'seed payment'; items = $items }
  $r = Post '/api/finance/payment' $pm
  if ($r -and $r.code -eq 200) {
    Start-Sleep -Milliseconds 250
    $all = GetP '/api/finance/payment/page?pageSize=200'
    $new = @(@($all.records) | Where-Object { $_.status -eq 'DRAFT' } | Sort-Object { [int]$_.id } -Descending | Select-Object -First 1)
    if ($new.Count) {
      $ra = PutOp ('/api/finance/payment/' + $new[0].id + '/audit') $null
      if ($ra -and $ra.code -eq 200) { $payOk++ } else { $payFail++; $msgs += ('PAY audit: ' + $ra.code + ' ' + $ra.msg) }
    }
  } else { $payFail++; $msgs += ('PAY create: ' + $r.code + ' ' + $r.msg) }
}
Write-Output ('payments ok=' + $payOk + ' fail=' + $payFail)

# 5) invoices (12)
$invOk = 0; $invFail = 0
for ($i = 1; $i -le 12; $i++) {
  $dir = if ($i % 2 -eq 0) { 'PURCHASE' } else { 'SALE' }
  $total = [math]::Round(($rnd.Next(100000, 3000000) / 100), 2)
  $inv = @{ invoiceNo = ('INV-2026-' + ('{0:D4}' -f $i)); direction = $dir; invoiceKind = 'special'; invoiceDate = '2026-08-25'
            partnerName = ('PARTNER-' + $i); taxRate = 13; totalAmount = $total; remark = 'seed invoice' }
  $r = Post '/api/finance/invoice' $inv
  if ($r -and $r.code -eq 200) { $invOk++ } else { $invFail++; $msgs += ('INV create: ' + $r.code + ' ' + $r.msg) }
}
Write-Output ('invoices ok=' + $invOk + ' fail=' + $invFail)

# 6) stock take for finished warehouses: create -> set actual -> audit
$tkOk = 0; $tkFail = 0
$period = '2026-09'
foreach ($w in ($whFin | Select-Object -First 2)) {
  $r = Post '/api/inventory/stock-take' @{ warehouseId = [int]$w; period = $period; takeDate = '2026-09-01'; remark = 'seed stock take' }
  if ($r -and $r.code -eq 200 -and $r.data) {
    $tid = $r.data.id
    Start-Sleep -Milliseconds 300
    $items = GetP ('/api/inventory/stock-take/' + $tid + '/items')
    if ($items) {
      foreach ($it in $items) {
        $book = [decimal]$it.bookQuantity
        $delta = if ($rnd.Next(0, 2) -eq 0) { 0 } else { $rnd.Next(-2, 3) }
        $it.actualQuantity = $book + $delta
      }
      $arr = @($items)
      $json = '[' + (($arr | ForEach-Object { $_ | ConvertTo-Json -Depth 5 -Compress }) -join ',') + ']'
      try {
        Invoke-RestMethod -Uri ('http://localhost:8080/api/inventory/stock-take/' + $tid + '/items') -Method Put -Headers $H -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($json)) | Out-Null
        $ra = Post ('/api/inventory/stock-take/' + $tid + '/audit') $null
        if ($ra -and $ra.code -eq 200) { $tkOk++ } else { $tkFail++; $msgs += ('TK audit: ' + $ra.code + ' ' + $ra.msg) }
      } catch { $tkFail++; $msgs += 'TK save error' }
    } else { $tkFail++; $msgs += 'TK no items' }
  } else { $tkFail++; $msgs += ('TK create: ' + $r.code + ' ' + $r.msg) }
}
Write-Output ('stock take ok=' + $tkOk + ' fail=' + $tkFail)
Write-Output '--- messages ---'
$msgs | Select-Object -First 12
