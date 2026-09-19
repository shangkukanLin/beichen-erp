# Seed 3/6: sale chain (ASCII only; Chinese via char codes)
$ErrorActionPreference = 'Continue'
function Cn([int[]]$c) { return -join ($c | ForEach-Object { [char]$_ }) }
$ids = Get-Content 'c:\Users\75629\CodeBuddy\20260710123705\seed_ids.json' -Raw -Encoding UTF8 | ConvertFrom-Json
$loginBody = @{ username = 'lin'; password = '123'; companyId = 1 } | ConvertTo-Json
$login = Invoke-RestMethod -Uri 'http://localhost:8080/api/auth/login' -Method Post -ContentType 'application/json' -Body $loginBody
$H = @{ Authorization = $login.data.token }
function Post($path, $obj) {
  $json = $obj | ConvertTo-Json -Depth 8
  try { return Invoke-RestMethod -Uri ('http://localhost:8080' + $path) -Method Post -Headers $H -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($json)) } catch { Write-Output ('ERR ' + $path); return $null }
}
function PutM($path, $method) {
  try { if ($method -eq 'PUT') { return Invoke-RestMethod -Uri ('http://localhost:8080' + $path) -Method Put -Headers $H -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes('{}')) } else { return Invoke-RestMethod -Uri ('http://localhost:8080' + $path) -Method Post -Headers $H -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes('{}')) } } catch { return $null }
}
function GetP($path) { try { return (Invoke-RestMethod -Uri ('http://localhost:8080' + $path) -Headers $H).data } catch { Write-Output ('GETERR ' + $path); return $null } }

$rnd = New-Object System.Random(20260904)
$prodIds = @($ids.prodIds); $custIds = @($ids.custIds); $whFin = @($ids.whFin); $whAfs = @($ids.whAfs)
$msgs = @()

# 1) sale orders: 20 (customer x stock rows)
$stock = GetP '/api/warehouse/stock/product-stock/page?pageNum=1&pageSize=100'
$rows = @(@($stock.records) | Where-Object { [decimal]$_.qtyA -gt 30 })
Write-Output ('stock rows usable=' + $rows.Count)
$soOk = 0; $soFail = 0
for ($i = 0; $i -lt 20; $i++) {
  $row = $rows[$rnd.Next(0, $rows.Count)]
  $cust = [int]$custIds[$rnd.Next(0, $custIds.Count)]
  $maxQty = [math]::Min(30, [math]::Floor([decimal]$row.qtyA / 3))
  if ($maxQty -lt 1) { $maxQty = 1 }
  $qty = $rnd.Next(1, [int]$maxQty + 1)
  $price = [math]::Round(($rnd.Next(20000, 90000) / 100), 2)
  $so = @{ customerId = $cust; warehouseId = [int]$row.warehouseId; orderDate = '2026-08-18'; taxIncluded = 1; taxRate = 13
           status = 'DRAFT'; remark = 'seed sale'
           items = @(@{ productId = [int]$row.productId; qualityType = 'A'; quantity = $qty; unitPrice = $price; amount = [math]::Round($qty * $price, 2) }) }
  $r = Post '/api/inventory/sale' $so
  if ($r -and $r.code -eq 200) { $soOk++ } else { $soFail++; $msgs += ('SO create: ' + $r.code + ' ' + $r.msg) }
}
Write-Output ('sale orders created=' + $soOk + ' fail=' + $soFail)

# 2) audit all draft sale orders
$allSo = GetP '/api/inventory/sale/page?pageSize=200'
$drafts = @(@($allSo.records) | Where-Object { $_.status -eq 'DRAFT' })
$auOk = 0; $auFail = 0
foreach ($s in $drafts) {
  $r = PutM ('/api/inventory/sale/' + $s.id + '/audit') 'PUT'
  if ($r -and $r.code -eq 200) { $auOk++ } else { $auFail++; $msgs += ('SO audit ' + $s.id + ': ' + $r.code + ' ' + $r.msg) }
}
Write-Output ('sale audited=' + $auOk + ' fail=' + $auFail)

# 3) sale outbound for some audited orders
$allSo = GetP '/api/inventory/sale/page?pageSize=200'
$auditedSo = @(@($allSo.records) | Where-Object { $_.status -eq 'AUDITED' } | Select-Object -First 6)
$obOk = 0; $obFail = 0
foreach ($s in $auditedSo) {
  $base = '/api/inventory/sale/' + $s.id
  $det = GetP $base
  $soItems = GetP ($base + '/items')
  if (-not $soItems -or $soItems.Count -eq 0) { $obFail++; $msgs += ('no items SO ' + $s.id); continue }
  $it0 = $soItems[0]
  $qty = [math]::Max(1, [math]::Floor([decimal]$it0.quantity / 2))
  $ob = @{ saleOrderId = [int]$s.id; saleOrderCode = $s.code; customerId = [int]$det.customerId; warehouseId = [int]$det.warehouseId
           outboundDate = '2026-08-19'; status = 'DRAFT'; remark = 'seed outbound'
           items = @(@{ saleOrderItemId = [int]$it0.id; productId = [int]$it0.productId; qualityType = 'A'; quantity = $qty; unitPrice = $it0.unitPrice; amount = [math]::Round($qty * [decimal]$it0.unitPrice, 2) }) }
  $r = Post '/api/inventory/sale-outbound' $ob
  if ($r -and $r.code -eq 200) {
    Start-Sleep -Milliseconds 250
    $allOb = GetP '/api/inventory/sale-outbound/page?pageSize=200'
    $new = @(@($allOb.records) | Where-Object { $_.status -eq 'DRAFT' } | Sort-Object { [int]$_.id } -Descending | Select-Object -First 1)
    if ($new.Count) {
      $ra = PutM ('/api/inventory/sale-outbound/' + $new[0].id + '/audit') 'PUT'
      if ($ra -and $ra.code -eq 200) { $obOk++ } else { $obFail++; $msgs += ('SOB audit: ' + $ra.code + ' ' + $ra.msg) }
    }
  } else { $obFail++; $msgs += ('SOB create: ' + $r.code + ' ' + $r.msg) }
}
Write-Output ('sale outbound ok=' + $obOk + ' fail=' + $obFail)

# 4) sale returns (to after-sale warehouse)
$allSo = GetP '/api/inventory/sale/page?pageSize=200'
$srOk = 0; $srFail = 0
$pickSo = @(@($allSo.records) | Where-Object { $_.status -eq 'AUDITED' } | Select-Object -First 5)
foreach ($s in $pickSo) {
  $base = '/api/inventory/sale/' + $s.id
  $det = GetP $base
  $soItems = GetP ($base + '/items')
  if (-not $soItems -or $soItems.Count -eq 0) { $srFail++; $msgs += ('no items SO ' + $s.id); continue }
  $it0 = $soItems[0]
  $qty = [math]::Max(1, [math]::Floor([decimal]$it0.quantity / 5))
  $sr = @{ saleOrderId = [int]$s.id; saleOrderCode = $s.code; customerId = [int]$det.customerId
           warehouseId = [int]$whAfs[0]; returnDate = '2026-08-21'; status = 'DRAFT'; remark = 'seed sale return'
           lossAmount = [math]::Round(($rnd.Next(100, 5000) / 100), 2)
           items = @(@{ saleOrderItemId = [int]$it0.id; productId = [int]$it0.productId; qualityType = 'A'; quantity = $qty; unitPrice = $it0.unitPrice; amount = [math]::Round($qty * [decimal]$it0.unitPrice, 2) }) }
  $r = Post '/api/sale/return' $sr
  if ($r -and $r.code -eq 200) {
    Start-Sleep -Milliseconds 250
    $allSr = GetP '/api/sale/return/page?pageSize=200'
    $new = @(@($allSr.records) | Where-Object { $_.status -eq 'DRAFT' } | Sort-Object { [int]$_.id } -Descending | Select-Object -First 1)
    if ($new.Count) {
      $ra = PutM ('/api/sale/return/' + $new[0].id + '/audit') 'PUT'
      if ($ra -and $ra.code -eq 200) { $srOk++ } else { $srFail++; $msgs += ('SR audit: ' + $ra.code + ' ' + $ra.msg) }
    }
  } else { $srFail++; $msgs += ('SR create: ' + $r.code + ' ' + $r.msg) }
}
Write-Output ('sale returns ok=' + $srOk + ' fail=' + $srFail)
Write-Output '--- messages ---'
$msgs | Select-Object -First 12
