# RETIRED 2026-09-24 -- manual material-delivery doc (outsource/delivery) was removed.
# The list/add pages were deleted and POST/PUT create+update endpoints now always reject;
# the replacement is the material warehouse-move doc: /inventory/material-move (backend
# /api/inventory/material-move), covered by verify-material-move.ps1. This script targets the
# removed entry, so it is kept for history only and must NOT be re-added to any suite.

# Seed 4d: fix outsource order product names (map PROD-<id> -> real name), then deliveries (ASCII only)
$ErrorActionPreference = 'Continue'
$B = 'http://localhost:8080/api'
$loginBody = @{ username = 'lin'; password = '123'; companyId = 1 } | ConvertTo-Json
$login = Invoke-RestMethod -Uri "$B/auth/login" -Method Post -ContentType 'application/json; charset=utf-8' -Body $loginBody
$H = @{ Authorization = $login.data.token }
function Post($path, $obj) {
    $json = $obj | ConvertTo-Json -Depth 10
    try {
        $r = Invoke-RestMethod -Uri ($B + $path) -Method Post -Headers $H -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($json))
        if ($r.code -ne 200) { Write-Output ('FAIL ' + $path + ' -> ' + $r.msg); return $null }
        return $r
    } catch { Write-Output ('ERR ' + $path); return $null }
}
function PutOp($path, $obj) {
    $json = if ($obj) { $obj | ConvertTo-Json -Depth 10 } else { '{}' }
    try {
        $r = Invoke-RestMethod -Uri ($B + $path) -Method Put -Headers $H -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($json))
        if ($r.code -ne 200) { Write-Output ('FAIL ' + $path + ' -> ' + $r.msg); return $null }
        return $r
    } catch { Write-Output ('ERR ' + $path); return $null }
}
function GetP($path) { try { return (Invoke-RestMethod -Uri ($B + $path) -Headers $H).data } catch { return $null } }

$ids = Get-Content 'c:\Users\75629\CodeBuddy\20260710123705\seed_ids.json' -Raw -Encoding UTF8 | ConvertFrom-Json
$matIds = @($ids.matIds); $whFin = @($ids.whFin)

# product id -> name map
$prodPage = GetP '/product/page?pageSize=100'
$prodMap = @{}
foreach ($p in @($prodPage.records)) { $prodMap[[string]$p.id] = $p.name }

$allOo = GetP '/outsource/order/page?pageSize=200'
$updOk = 0; $updFail = 0; $msgs = @()
foreach ($o in @(@($allOo.records) | Where-Object { $_.status -in @('PRODUCING','PENDING') })) {
    $prods = GetP ('/outsource/order/' + $o.id + '/products')
    $newProducts = @()
    $changed = $false
    foreach ($p in @($prods)) {
        $oldName = [string]$p.productName
        $newName = $oldName
        if ($oldName -match '^PROD-(\d+)$') {
            $key = $Matches[1]
            if ($prodMap.ContainsKey($key)) { $newName = $prodMap[$key] }
        }
        if ($newName -ne $oldName) { $changed = $true }
        $materials = @()
        for ($m = 0; $m -lt 2; $m++) {
            $materials += @{ materialId = [int]$matIds[(Get-Random -Maximum $matIds.Count)]; unit = 'PCS'; demandQuantity = (Get-Random -Minimum 1 -Maximum 4); lossRate = 0; supplyType = 'OURS' }
        }
        $newProducts += @{ productName = $newName; productSpec = $p.productSpec; quantity = $p.quantity; unitPrice = $p.unitPrice; materials = $materials }
    }
    if (-not $changed) { continue }
    $body = @{ factoryId = [int]$o.factoryId; planStartDate = '2026-08-05'; planEndDate = '2026-08-30'; supplyMode = 'OURS'
               taxIncluded = 1; taxRate = 13; remark = $o.remark; products = $newProducts }
    $r = PutOp ('/outsource/order/' + $o.id) $body
    if ($r) { $updOk++ } else { $updFail++; $msgs += ('update OO ' + $o.id) }
}
Write-Output ('orders fixed ok=' + $updOk + ' fail=' + $updFail)

# deliveries: aux -> factory outsource warehouse for each PRODUCING order
$whPage = GetP '/warehouse/page?pageSize=300'
$aux = $null
foreach ($w in @($whPage.records)) { if ($w.warehouseName -like 'AUX-*') { $aux = [int]$w.id; break } }
$allOo = GetP '/outsource/order/page?pageSize=200'
$delOk = 0; $delFail = 0
foreach ($o in @(@($allOo.records) | Where-Object { $_.status -eq 'PRODUCING' })) {
    $whs = GetP ('/outsource/delivery/warehouses/by-factory/' + $o.factoryId)
    if (-not $whs -or @($whs).Count -eq 0) { $delFail++; continue }
    $target = [int]@($whs)[0].id
    $items = @(@{ materialId = [int]$matIds[(Get-Random -Maximum $matIds.Count)]; unit = 'PCS'; quantity = (Get-Random -Minimum 50 -Maximum 200); qualityType = 'GOOD'; unit_price = 30 })
    $dl = @{ deliveryType = 'DELIVERY'; factoryId = [int]$o.factoryId; fromWarehouseId = $aux; toWarehouseId = $target
             deliveryDate = '2026-08-10'; status = 'DRAFT'; remark = 'seed delivery'; items = $items }
    $r = Post '/outsource/delivery' $dl
    if ($r) {
        Start-Sleep -Milliseconds 250
        $allDl = GetP '/outsource/delivery/page?pageSize=100'
        $new = @(@($allDl.records) | Where-Object { $_.status -eq 'DRAFT' } | Sort-Object { [int]$_.id } -Descending | Select-Object -First 1)[0]
        $ra = PutOp ('/outsource/delivery/' + $new.id + '/audit')
        if ($ra) { $delOk++ } else { $delFail++; $msgs += ('DL audit ' + $new.id) }
    } else { $delFail++; $msgs += ('DL create OO ' + $o.id) }
}
Write-Output ('material delivery ok=' + $delOk + ' fail=' + $delFail)

# order deliveries (finished goods in) for first 8 producing orders
$dvOk = 0; $dvFail = 0
foreach ($o in @(@($allOo.records) | Where-Object { $_.status -eq 'PRODUCING' } | Select-Object -First 8)) {
    $prods = GetP ('/outsource/order/' + $o.id + '/products')
    if (-not $prods -or @($prods).Count -eq 0) { $dvFail++; $msgs += ('no products OO ' + $o.id); continue }
    $p0 = @($prods)[0]
    $qty = [math]::Max(2, [math]::Floor([decimal]$p0.quantity / 2))
    $a = [math]::Floor($qty * 0.7); $b = [math]::Floor($qty * 0.2); $c = $qty - $a - $b
    $dv = @{ orderId = [int]$o.id; productId = [int]$p0.id; warehouseId = [int]$whFin[0]; deliveryDate = '2026-08-28'
             quantity = $qty; aQty = $a; bQty = $b; cQty = $c; defectQty = 0; sourceType = 'DELIVERY'; remark = 'seed order delivery' }
    $r = Post '/outsource/order-delivery?forceDelivery=true' $dv
    if ($r -and $r.code -eq 200) {
        $allDv = GetP ('/outsource/order/' + $o.id + '/deliveries')
        $dId = @(@($allDv) | Sort-Object { [int]$_.id } -Descending | Select-Object -First 1)[0].id
        $ra = PutOp ('/outsource/order-delivery/' + $dId + '/audit')
        if ($ra) { $dvOk++ } else { $dvFail++; $msgs += ('OD audit ' + $dId) }
    } else { $dvFail++; if ($r) { $msgs += ('OD create: ' + $r.msg) } }
}
Write-Output ('order delivery ok=' + $dvOk + ' fail=' + $dvFail)
$msgs | Select-Object -First 8
