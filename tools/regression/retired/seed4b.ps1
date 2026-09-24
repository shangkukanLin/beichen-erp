# RETIRED 2026-09-24 -- manual material-delivery doc (outsource/delivery) was removed.
# The list/add pages were deleted and POST/PUT create+update endpoints now always reject;
# the replacement is the material warehouse-move doc: /inventory/material-move (backend
# /api/inventory/material-move), covered by verify-material-move.ps1. This script targets the
# removed entry, so it is kept for history only and must NOT be re-added to any suite.

# Seed 4b: fill aux warehouse with materials via outsource other-io, then delivery + order delivery (ASCII only)
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

$rnd = New-Object System.Random(20260907)
$matIds = @($ids.matIds); $whAux = @($ids.whAux); $whFin = @($ids.whFin)
$msgs = @()

# 1) try filling AUX warehouse by outsource other-io IN
$auxWh = [int]$whAux[0]
$fillOk = 0; $fillFail = 0
for ($i = 0; $i -lt 4; $i++) {
  $io = @{ warehouseId = $auxWh; ioType = 'IN'; ioDate = '2026-08-08'; remark = 'fill aux wh'
           items = @(@{ materialId = [int]$matIds[$i]; unit = 'PCS'; quantity = 500; unit_price = [math]::Round(($rnd.Next(500, 9000) / 100), 2) }) }
  $r = Post '/api/outsource/other-io' $io
  if ($r -and $r.code -eq 200) {
    Start-Sleep -Milliseconds 250
    $all = GetP '/api/outsource/other-io/page?pageSize=200'
    $new = @(@($all.records) | Where-Object { $_.status -eq 'DRAFT' } | Sort-Object { [int]$_.id } -Descending | Select-Object -First 1)
    if ($new.Count) {
      $ra = PutOp ('/api/outsource/other-io/' + $new[0].id + '/approve')
      if ($ra -and $ra.code -eq 200) { $fillOk++ } else { $fillFail++; $msgs += ('fill approve: ' + $ra.code + ' ' + $ra.msg) }
    }
  } else { $fillFail++; $msgs += ('fill create: ' + $r.code + ' ' + $r.msg) }
}
Write-Output ('aux fill ok=' + $fillOk + ' fail=' + $fillFail)

# 2) material delivery again (from AUX to factory outsource warehouse)
$allOo = GetP '/api/outsource/order/page?pageSize=200'
$prodOrders = @(@($allOo.records) | Where-Object { $_.status -eq 'PRODUCING' } | Select-Object -First 6)
$delOk = 0; $delFail = 0
foreach ($o in $prodOrders) {
  $whs = GetP ('/api/outsource/delivery/warehouses/by-factory/' + $o.factoryId)
  $target = if ($whs -and $whs.Count) { [int]$whs[0].id } else { $null }
  if (-not $target) { $delFail++; continue }
  $items = @(@{ materialId = [int]$matIds[$rnd.Next(0, 4)]; unit = 'PCS'; quantity = 50; qualityType = 'GOOD'; unit_price = 30 })
  $dl = @{ deliveryType = 'DELIVERY'; factoryId = [int]$o.factoryId; fromWarehouseId = $auxWh; toWarehouseId = $target
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

# 3) order delivery: inspect create response first
$allOo = GetP '/api/outsource/order/page?pageSize=200'
$o0 = @(@($allOo.records) | Where-Object { $_.status -eq 'PRODUCING' } | Select-Object -First 1)[0]
if ($o0) {
  $prods = GetP ('/api/outsource/order/' + $o0.id + '/products')
  Write-Output ('OO ' + $o0.id + ' products=' + @($prods).Count + ' firstId=' + $prods[0].id + ' qty=' + $prods[0].quantity)
  $qty = [math]::Max(2, [math]::Floor([decimal]$prods[0].quantity / 2))
  $a = [math]::Floor($qty * 0.7); $b = [math]::Floor($qty * 0.2); $c = $qty - $a - $b
  $dv = @{ orderId = [int]$o0.id; productId = [int]$prods[0].id; warehouseId = [int]$whFin[0]; deliveryDate = '2026-08-28'
           quantity = $qty; aQty = $a; bQty = $b; cQty = $c; defectQty = 0; sourceType = 'DELIVERY'; remark = 'seed order delivery' }
  $r = Post '/api/outsource/order-delivery?forceDelivery=true' $dv
  Write-Output ('OD create resp: ' + ($r | ConvertTo-Json -Compress -Depth 5))
  if ($r -and $r.code -eq 200 -and $r.data -and $r.data.id) {
    $ra = PutOp ('/api/outsource/order-delivery/' + $r.data.id + '/audit')
    Write-Output ('OD audit: ' + $ra.code + ' ' + $ra.msg)
  }
}
$msgs | Select-Object -First 8
