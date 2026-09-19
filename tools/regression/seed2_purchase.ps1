# Seed 2/6: purchase chain (ASCII only; Chinese via char codes)
# NOTE: never use $pid (read-only process id). List APIs omit items -> use detail endpoint.
$ErrorActionPreference = 'Continue'
$ids = Get-Content 'c:\Users\75629\CodeBuddy\20260710123705\seed_ids.json' -Raw -Encoding UTF8 | ConvertFrom-Json
$loginBody = @{ username = 'lin'; password = '123'; companyId = 1 } | ConvertTo-Json
$login = Invoke-RestMethod -Uri 'http://localhost:8080/api/auth/login' -Method Post -ContentType 'application/json' -Body $loginBody
$H = @{ Authorization = $login.data.token }

function Post($path, $obj) {
  $json = $obj | ConvertTo-Json -Depth 8
  try { return Invoke-RestMethod -Uri ('http://localhost:8080' + $path) -Method Post -Headers $H -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($json)) } catch { Write-Output ('ERR ' + $path); return $null }
}
function Put($path) {
  try { return Invoke-RestMethod -Uri ('http://localhost:8080' + $path) -Method Put -Headers $H -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes('{}')) } catch { return $null }
}
function GetP($path) { try { return (Invoke-RestMethod -Uri ('http://localhost:8080' + $path) -Headers $H).data } catch { Write-Output ('GETERR ' + $path); return $null } }

$rnd = New-Object System.Random(20260903)
$prodIds = @($ids.prodIds); $proSupIds = @($ids.proSupIds); $whFin = @($ids.whFin)
$msgs = @()
Write-Output ('products=' + $prodIds.Count + ' suppliers=' + $proSupIds.Count + ' finWH=' + $whFin.Count)

# 1) create + audit 15 purchase orders
$created = 0; $cFail = 0
foreach ($s in $proSupIds) {
  for ($k = 1; $k -le 3; $k++) {
    $items = @()
    $n = $rnd.Next(2, 5)
    for ($j = 0; $j -lt $n; $j++) {
      $pickId = [int]$prodIds[$rnd.Next(0, $prodIds.Count)]
      $qty = $rnd.Next(50, 300)
      $price = [math]::Round(($rnd.Next(5000, 50000) / 100), 2)
      $items += @{ productId = $pickId; qualityType = 'A'; quantity = $qty; unitPrice = $price; amount = [math]::Round($qty * $price, 2) }
    }
    $po = @{ supplierId = [int]$s; warehouseId = [int]$whFin[$rnd.Next(0, $whFin.Count)]; orderDate = '2026-08-15'
             taxIncluded = 1; taxRate = 13; status = 'DRAFT'; remark = 'seed purchase'; items = $items }
    $r = Post '/api/inventory/purchase' $po
    if ($r -and $r.code -eq 200) { $created++ } else { $cFail++; $msgs += ('PO create: ' + $r.code + ' ' + $r.msg) }
  }
}
Write-Output ('PO created=' + $created + ' fail=' + $cFail)

$all = GetP '/api/inventory/purchase/page?pageSize=200'
$audited = 0; $aFail = 0
foreach ($p in @(@($all.records) | Where-Object { $_.status -eq 'DRAFT' })) {
  $r = Put ('/api/inventory/purchase/' + $p.id + '/audit')
  if ($r -and $r.code -eq 200) { $audited++ } else { $aFail++; $msgs += ('PO audit ' + $p.id + ': ' + $r.code + ' ' + $r.msg) }
}
Write-Output ('PO audited=' + $audited + ' fail=' + $aFail)

# 2) purchase returns (detail endpoint for items)
$all = GetP '/api/inventory/purchase/page?pageSize=200'
$rtOk = 0; $rtFail = 0
foreach ($p in @(@($all.records) | Where-Object { $_.status -eq 'AUDITED' } | Select-Object -First 5)) {
  $det = GetP ('/api/inventory/purchase/' + $p.id)
  if (-not $det -or -not $det.items) { $rtFail++; $msgs += ('no items PO ' + $p.id); continue }
  $items = @()
  $n = [math]::Min(2, @($det.items).Count)
  for ($j = 0; $j -lt $n; $j++) {
    $it = $det.items[$j]
    $qty = [math]::Max(1, [math]::Floor([decimal]$it.quantity / 10))
    $items += @{ productId = [int]$it.productId; qualityType = 'A'; quantity = $qty; unitPrice = $it.unitPrice; amount = [math]::Round($qty * [decimal]$it.unitPrice, 2) }
  }
  $pr = @{ supplierId = [int]$det.supplierId; warehouseId = [int]$det.warehouseId; purchaseOrderId = [int]$det.id; purchaseOrderCode = $det.code
           returnDate = '2026-08-20'; status = 'DRAFT'; remark = 'seed return'; items = $items }
  $r = Post '/api/inventory/purchase-return' $pr
  if ($r -and $r.code -eq 200) {
    Start-Sleep -Milliseconds 250
    $prAll = GetP '/api/inventory/purchase-return/page?pageSize=200'
    $new = @(@($prAll.records) | Where-Object { $_.purchaseOrderId -eq $det.id -and $_.status -eq 'DRAFT' } | Sort-Object { [int]$_.id } -Descending | Select-Object -First 1)
    if ($new.Count) {
      $ra = Put ('/api/inventory/purchase-return/' + $new[0].id + '/audit')
      if ($ra -and $ra.code -eq 200) { $rtOk++ } else { $rtFail++; $msgs += ('PR audit: ' + $ra.code + ' ' + $ra.msg) }
    } else { $rtFail++; $msgs += 'PR not found' }
  } else { $rtFail++; $msgs += ('PR create: ' + $r.code + ' ' + $r.msg) }
}
Write-Output ('purchase returns ok=' + $rtOk + ' fail=' + $rtFail)

# 3) warehouse moves from real stock rows
$stock = GetP '/api/warehouse/stock/product-stock/page?pageNum=1&pageSize=100'
$rows = @(@($stock.records) | Where-Object { [decimal]$_.qtyA -gt 20 })
Write-Output ('stock rows available=' + @($stock.records).Count + ' usable=' + $rows.Count)
$mvOk = 0; $mvFail = 0
foreach ($row in ($rows | Select-Object -First 6)) {
  $src = [int]$row.warehouseId; $dst = $null
  foreach ($w in $whFin) { if ([int]$w -ne $src) { $dst = [int]$w; break } }
  if (-not $dst) { continue }
  $qty = [math]::Min(10, [math]::Floor([decimal]$row.qtyA / 2))
  $mv = @{ fromWarehouseId = $src; toWarehouseId = $dst; moveDate = '2026-08-22'; status = 'DRAFT'; remark = 'seed move'
           items = @(@{ productId = [int]$row.productId; qualityType = 'A'; quantity = $qty }) }
  $r = Post '/api/inventory/warehouse-move' $mv
  if ($r -and $r.code -eq 200) {
    Start-Sleep -Milliseconds 250
    $allMv = GetP '/api/inventory/warehouse-move/page?pageSize=200'
    $new = @(@($allMv.records) | Where-Object { $_.status -eq 'DRAFT' } | Sort-Object { [int]$_.id } -Descending | Select-Object -First 1)
    if ($new.Count) {
      $ra = Put ('/api/inventory/warehouse-move/' + $new[0].id + '/audit')
      if ($ra -and $ra.code -eq 200) { $mvOk++ } else { $mvFail++; $msgs += ('MV audit: ' + $ra.code + ' ' + $ra.msg) }
    }
  } else { $mvFail++; $msgs += ('MV create: ' + $r.code + ' ' + $r.msg) }
}
Write-Output ('warehouse moves ok=' + $mvOk + ' fail=' + $mvFail)

# 4) other io IN/OUT
$stock2 = GetP '/api/warehouse/stock/product-stock/page?pageNum=1&pageSize=100'
$ioOk = 0; $ioFail = 0; $i = 0
foreach ($row in (@($stock2.records) | Select-Object -First 8)) {
  $i++
  $dir = if ($i % 2 -eq 0) { 'OUT' } else { 'IN' }
  $qty = if ($dir -eq 'OUT') { 5 } else { $rnd.Next(10, 60) }
  $io = @{ warehouseId = [int]$row.warehouseId; ioType = $dir; ioDate = '2026-08-25'; status = 'DRAFT'; remark = 'seed other io'
           items = @(@{ productId = [int]$row.productId; qualityType = 'A'; quantity = $qty }) }
  $r = Post '/api/inventory/other' $io
  if ($r -and $r.code -eq 200) {
    Start-Sleep -Milliseconds 250
    $allIo = GetP '/api/inventory/other/page?pageSize=200'
    $new = @(@($allIo.records) | Where-Object { $_.status -eq 'DRAFT' } | Sort-Object { [int]$_.id } -Descending | Select-Object -First 1)
    if ($new.Count) {
      $ra = Put ('/api/inventory/other/' + $new[0].id + '/audit')
      if ($ra -and $ra.code -eq 200) { $ioOk++ } else { $ioFail++; $msgs += ('IO audit: ' + $ra.code + ' ' + $ra.msg) }
    }
  } else { $ioFail++; $msgs += ('IO create: ' + $r.code + ' ' + $r.msg) }
}
Write-Output ('other io ok=' + $ioOk + ' fail=' + $ioFail)
Write-Output '--- messages ---'
$msgs | Select-Object -First 10
