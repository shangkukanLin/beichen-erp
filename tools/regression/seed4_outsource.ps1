# Seed 4/6: outsource chain (ASCII only; Chinese via char codes)
# NOTE: delivery/other-io items use unit_price (underscore); audit verbs differ per module
$ErrorActionPreference = 'Continue'
$ids = Get-Content 'c:\Users\75629\CodeBuddy\20260710123705\seed_ids.json' -Raw -Encoding UTF8 | ConvertFrom-Json
$loginBody = @{ username = 'lin'; password = '123'; companyId = 1 } | ConvertTo-Json
$login = Invoke-RestMethod -Uri 'http://localhost:8080/api/auth/login' -Method Post -ContentType 'application/json' -Body $loginBody
$H = @{ Authorization = $login.data.token }
function Post($path, $obj) {
  $json = $obj | ConvertTo-Json -Depth 10
  try { return Invoke-RestMethod -Uri ('http://localhost:8080' + $path) -Method Post -Headers $H -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($json)) } catch { Write-Output ('ERR ' + $path); return $null }
}
function PutOp($path) { try { return Invoke-RestMethod -Uri ('http://localhost:8080' + $path) -Method Put -Headers $H -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes('{}')) } catch { return $null } }
function GetP($path) { try { return (Invoke-RestMethod -Uri ('http://localhost:8080' + $path) -Headers $H).data } catch { Write-Output ('GETERR ' + $path); return $null } }

$rnd = New-Object System.Random(20260906)
$prodIds = @($ids.prodIds); $matIds = @($ids.matIds); $facIds = @($ids.facIds)
$matSupIds = @($ids.matSupIds); $whFin = @($ids.whFin); $whOut = @($ids.whOut); $whAux = @($ids.whAux)
$msgs = @()

# 1) outsource orders: 3 per factory (4 factories = 12), each 1-2 products with materials
$ooOk = 0; $ooFail = 0
foreach ($f in $facIds) {
  for ($k = 1; $k -le 3; $k++) {
    $products = @()
    $np = $rnd.Next(1, 3)
    for ($j = 0; $j -lt $np; $j++) {
      $pidx = $rnd.Next(0, $prodIds.Count)
      $materials = @()
      $nm = $rnd.Next(1, 4)
      for ($m = 0; $m -lt $nm; $m++) {
        $materials += @{ materialId = [int]$matIds[$rnd.Next(0, $matIds.Count)]; unit = 'PCS'; demandQuantity = $rnd.Next(1, 5); lossRate = 0; supplyType = 'OURS' }
      }
      $products += @{ productName = ('PROD-' + $prodIds[$pidx]); productSpec = 'seed'; quantity = $rnd.Next(50, 300); unitPrice = [math]::Round(($rnd.Next(1000, 9000) / 100), 2); materials = $materials }
    }
    $oo = @{ factoryId = [int]$f; planStartDate = '2026-08-05'; planEndDate = '2026-08-30'; supplyMode = 'OURS'
             taxIncluded = 1; taxRate = 13; remark = 'seed outsource order'; products = $products }
    $r = Post '/api/outsource/order' $oo
    if ($r -and $r.code -eq 200) { $ooOk++ } else { $ooFail++; $msgs += ('OO create: ' + $r.code + ' ' + $r.msg) }
  }
}
Write-Output ('outsource orders ok=' + $ooOk + ' fail=' + $ooFail)

# 2) audit all draft outsource orders (PENDING -> PRODUCING)
$allOo = GetP '/api/outsource/order/page?pageSize=200'
$pend = @(@($allOo.records) | Where-Object { $_.status -eq 'PENDING' })
$auOk = 0; $auFail = 0
foreach ($o in $pend) {
  $r = PutOp ('/api/outsource/order/' + $o.id + '/audit')
  if ($r -and $r.code -eq 200) { $auOk++ } else { $auFail++; $msgs += ('OO audit ' + $o.id + ': ' + $r.code + ' ' + $r.msg) }
}
Write-Output ('outsource audited=' + $auOk + ' fail=' + $auFail)

# 3) material delivery (DELIVERY) from aux warehouse to factory outsource warehouse
$delOk = 0; $delFail = 0
$allOo = GetP '/api/outsource/order/page?pageSize=200'
$prodOrders = @(@($allOo.records) | Where-Object { $_.status -eq 'PRODUCING' } | Select-Object -First 6)
foreach ($o in $prodOrders) {
  # resolve outsource warehouse of this factory
  $whs = GetP ('/api/outsource/delivery/warehouses/by-factory/' + $o.factoryId)
  $target = if ($whs -and $whs.Count) { [int]$whs[0].id } elseif ($whOut.Count) { [int]$whOut[0] } else { $null }
  if (-not $target) { $delFail++; continue }
  $items = @()
  for ($m = 0; $m -lt 2; $m++) {
    $items += @{ materialId = [int]$matIds[$rnd.Next(0, $matIds.Count)]; unit = 'PCS'; quantity = $rnd.Next(50, 300); qualityType = 'GOOD'; unit_price = [math]::Round(($rnd.Next(500, 9000) / 100), 2) }
  }
  $dl = @{ deliveryType = 'DELIVERY'; factoryId = [int]$o.factoryId; fromWarehouseId = [int]$whAux[0]; toWarehouseId = $target
           deliveryDate = '2026-08-10'; status = 'DRAFT'; remark = 'seed delivery'; items = $items }
  $r = Post '/api/outsource/delivery' $dl
  if ($r -and $r.code -eq 200) {
    Start-Sleep -Milliseconds 250
    $allDl = GetP '/api/outsource/delivery/page?pageSize=200'
    $new = @(@($allDl.records) | Where-Object { $_.status -eq 'DRAFT' } | Sort-Object { [int]$_.id } -Descending | Select-Object -First 1)
    if ($new.Count) {
      $ra = PutOp ('/api/outsource/delivery/' + $new[0].id + '/audit')
      if ($ra -and $ra.code -eq 200) { $delOk++ } else { $delFail++; $msgs += ('DL audit: ' + $ra.code + ' ' + $ra.msg) }
    }
  } else { $delFail++; $msgs += ('DL create: ' + $r.code + ' ' + $r.msg) }
}
Write-Output ('material delivery ok=' + $delOk + ' fail=' + $delFail)

# 4) outsource order delivery (finished goods in) with forceDelivery
$dvOk = 0; $dvFail = 0
$allOo = GetP '/api/outsource/order/page?pageSize=200'
foreach ($o in (@(@($allOo.records) | Where-Object { $_.status -eq 'PRODUCING' } | Select-Object -First 6))) {
  $prods = GetP ('/api/outsource/order/' + $o.id + '/products')
  if (-not $prods -or $prods.Count -eq 0) { $dvFail++; $msgs += ('no products OO ' + $o.id); continue }
  $p0 = $prods[0]
  $qty = [math]::Max(2, [math]::Floor([decimal]$p0.quantity / 2))
  $a = [math]::Floor($qty * 0.7); $b = [math]::Floor($qty * 0.2); $c = $qty - $a - $b
  if ($c -lt 0) { $c = 0 }
  $dv = @{ orderId = [int]$o.id; productId = [int]$p0.id; warehouseId = [int]$whFin[0]; deliveryDate = '2026-08-28'
           quantity = $qty; aQty = $a; bQty = $b; cQty = $c; defectQty = 0; sourceType = 'DELIVERY'; remark = 'seed order delivery' }
  $r = Post '/api/outsource/order-delivery?forceDelivery=true' $dv
  if ($r -and $r.code -eq 200) {
    $dId = $r.data.id
    if (-not $dId) {
      Start-Sleep -Milliseconds 250
      $allDv = GetP ('/api/outsource/order/' + $o.id + '/deliveries')
      $dId = @($allDv | Sort-Object { [int]$_.id } -Descending | Select-Object -First 1)[0].id
    }
    if ($dId) {
      $ra = PutOp ('/api/outsource/order-delivery/' + $dId + '/audit')
      if ($ra -and $ra.code -eq 200) { $dvOk++ } else { $dvFail++; $msgs += ('OD audit: ' + $ra.code + ' ' + $ra.msg) }
    } else { $dvFail++; $msgs += 'OD id not found' }
  } else { $dvFail++; $msgs += ('OD create: ' + $r.code + ' ' + $r.msg) }
}
Write-Output ('order delivery ok=' + $dvOk + ' fail=' + $dvFail)

# 5) material orders + audit
$moOk = 0; $moFail = 0
foreach ($s in ($matSupIds | Select-Object -First 3)) {
  $items = @()
  for ($m = 0; $m -lt 2; $m++) {
    $items += @{ materialId = [int]$matIds[$rnd.Next(0, $matIds.Count)]; unit = 'PCS'; orderQuantity = $rnd.Next(100, 500); unitPrice = [math]::Round(($rnd.Next(500, 9000) / 100), 2) }
  }
  $mo = @{ supplierId = [int]$s; orderType = '采购'; targetWarehouseId = [int]$whAux[0]; deliveryDate = '2026-08-12'; remark = 'seed material order'; items = $items }
  $r = Post '/api/outsource/material-order' $mo
  if ($r -and $r.code -eq 200) {
    Start-Sleep -Milliseconds 250
    $allMo = GetP '/api/outsource/material-order/page?pageSize=200'
    $new = @(@($allMo.records) | Where-Object { $_.status -eq 'PENDING' } | Sort-Object { [int]$_.id } -Descending | Select-Object -First 1)
    if ($new.Count) {
      $ra = PutOp ('/api/outsource/material-order/' + $new[0].id + '/audit')
      if ($ra -and $ra.code -eq 200) { $moOk++ } else { $moFail++; $msgs += ('MO audit: ' + $ra.code + ' ' + $ra.msg) }
    }
  } else { $moFail++; $msgs += ('MO create: ' + $r.code + ' ' + $r.msg) }
}
Write-Output ('material orders ok=' + $moOk + ' fail=' + $moFail)

# 6) outsource other io (IN) to outsource warehouse, approve verb
$ioOk = 0; $ioFail = 0
for ($i = 0; $i -lt 4; $i++) {
  $wh = if ($whOut.Count) { [int]$whOut[$i % $whOut.Count] } else { $null }
  if (-not $wh) { break }
  $io = @{ warehouseId = $wh; ioType = 'IN'; ioDate = '2026-08-14'; remark = 'seed outsource other io'
           items = @(@{ materialId = [int]$matIds[$rnd.Next(0, $matIds.Count)]; unit = 'PCS'; quantity = $rnd.Next(50, 200); unit_price = [math]::Round(($rnd.Next(500, 9000) / 100), 2) }) }
  $r = Post '/api/outsource/other-io' $io
  if ($r -and $r.code -eq 200) {
    Start-Sleep -Milliseconds 250
    $allIo = GetP '/api/outsource/other-io/page?pageSize=200'
    $new = @(@($allIo.records) | Where-Object { $_.status -eq 'DRAFT' } | Sort-Object { [int]$_.id } -Descending | Select-Object -First 1)
    if ($new.Count) {
      $ra = PutOp ('/api/outsource/other-io/' + $new[0].id + '/approve')
      if ($ra -and $ra.code -eq 200) { $ioOk++ } else { $ioFail++; $msgs += ('OIO approve: ' + $ra.code + ' ' + $ra.msg) }
    }
  } else { $ioFail++; $msgs += ('OIO create: ' + $r.code + ' ' + $r.msg) }
}
Write-Output ('outsource other io ok=' + $ioOk + ' fail=' + $ioFail)
Write-Output '--- messages ---'
$msgs | Select-Object -First 12
