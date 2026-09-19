# Seed 4f: OUTSOURCE material orders + receive all + audit + finish (ASCII only)
$ErrorActionPreference = 'Continue'
$B = 'http://localhost:8080/api'
$loginBody = @{ username = 'lin'; password = '123'; companyId = 1 } | ConvertTo-Json
$login = Invoke-RestMethod -Uri "$B/auth/login" -Method Post -ContentType 'application/json; charset=utf-8' -Body $loginBody
$H = @{ Authorization = $login.data.token }

function InvokeJson($method, $path, $obj) {
    try {
        if ($method -eq 'Get') { return (GetResp (Invoke-RestMethod -Uri ($B + $path) -Method Get -Headers $H)) }
        $json = if ($null -ne $obj) { $obj | ConvertTo-Json -Depth 10 } else { '{}' }
        return (GetResp (Invoke-RestMethod -Uri ($B + $path) -Method $method -Headers $H -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($json))))
    } catch {
        $msg = $_.Exception.Message
        if ($_.ErrorDetails -and $_.ErrorDetails.Message) { $msg = $_.ErrorDetails.Message }
        Write-Output ('ERR ' + $method + ' ' + $path + ' -> ' + $msg.Substring(0, [Math]::Min(150, $msg.Length)))
        return $null
    }
}
function GetResp($resp) {
    if ($null -ne $resp -and $resp -is [string]) { return ($resp | ConvertFrom-Json) }
    return $resp
}

$ids = Get-Content 'c:\Users\75629\CodeBuddy\20260710123705\seed_ids.json' -Raw -Encoding UTF8 | ConvertFrom-Json
$parentMatIds = @($ids.parentMatIds); $childMatIds = @($ids.childMatIds)
$whPage = InvokeJson 'Get' '/warehouse/page?pageSize=300' $null
$aux = $null; $facIds = @()
$supPage = InvokeJson 'Get' '/supplier/page?supplierType=factory&pageSize=50' $null
$facIds = @(@($supPage.data.records) | ForEach-Object { [int]$_.id })
foreach ($w in @($whPage.data.records)) { if ($w.warehouseName -like 'AUX-*') { $aux = [int]$w.id; break } }
$f0 = $facIds[0]
Write-Output ('aux=' + $aux + ' factory=' + $f0)

# 1) create 2 OUTSOURCE material orders (parent materials, target aux warehouse)
$moIds = @()
for ($i = 1; $i -le 2; $i++) {
    $items = @()
    for ($m = 0; $m -lt 2; $m++) {
        $items += @{ materialId = [int]$parentMatIds[(($i - 1) * 2 + $m) % $parentMatIds.Count]; unit = 'PCS'; orderQuantity = (Get-Random -Minimum 100 -Maximum 300); unitPrice = (Get-Random -Minimum 50 -Maximum 120) }
    }
    $r = InvokeJson 'Post' '/outsource/material-order' @{ supplierId = $f0; orderType = '委外'; targetWarehouseId = $aux; deliveryDate = '2026-08-12'; remark = 'seed outsource material order'; items = $items }
    if ($r -and $r.data) { $moIds += [int]$r.data }
}
Write-Output ('OUTSOURCE orders created=' + $moIds.Count)

# 2) audit all pending material orders
$allMo = InvokeJson 'Get' '/outsource/material-order/page?pageSize=200' $null
$auOk = 0; $auFail = 0
foreach ($o in @(@($allMo.data.records) | Where-Object { $_.status -eq 'PENDING' })) {
    $r = InvokeJson 'Put' ('/outsource/material-order/' + $o.id + '/audit') $null
    if ($r) { $auOk++ } else { $auFail++ }
}
Write-Output ('material orders audited ok=' + $auOk + ' fail=' + $auFail)

# 3) receive for each material order (force), then audit the delivery draft
$rcOk = 0; $rcFail = 0; $msgs = @()
foreach ($o in @(@($allMo.data.records) | Where-Object { $_.status -ne 'PENDING' -and $_.status -ne 'CANCELLED' })) {
    $det = InvokeJson 'Get' ('/outsource/material-order/' + $o.id) $null
    if (-not $det -or -not $det.data.items) { $rcFail++; $msgs += ('no items MO ' + $o.id); continue }
    $items = @()
    foreach ($it in @($det.data.items)) {
        $items += @{ itemId = [int]$it.id; quantity = [math]::Max(1, [math]::Floor([decimal]$it.orderQuantity / 2)) }
    }
    $r = InvokeJson 'Post' ('/outsource/material-order/' + $o.id + '/receive') @{ force = $true; warehouseId = $aux; items = $items }
    if ($null -eq $r) { $rcFail++; $msgs += ('receive MO ' + $o.id); continue }
    if ($r.data -and $r.data._shortage) { $rcFail++; $msgs += ('shortage MO ' + $o.id); continue }
    $dId = [int]$r.data
    $ra = InvokeJson 'Put' ('/outsource/material-order/delivery/' + $dId + '/audit') $null
    if ($ra) { $rcOk++ } else { $rcFail++; $msgs += ('RD audit ' + $dId) }
}
Write-Output ('receives ok=' + $rcOk + ' fail=' + $rcFail)

# 4) finish 2 material orders
$fnOk = 0
foreach ($o in (@($allMo.data.records) | Where-Object { $_.status -eq 'RECEIVING' -or $_.status -eq 'PART_RECEIVE' } | Select-Object -First 2)) {
    $r = InvokeJson 'Put' ('/outsource/material-order/' + $o.id + '/finish') $null
    if ($r) { $fnOk++ }
}
Write-Output ('finished orders=' + $fnOk)
$msgs | Select-Object -First 6
