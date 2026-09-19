# Seed 4e: audit one draft order-delivery per order, delete duplicates, then create for the rest (ASCII only)
$ErrorActionPreference = 'Continue'
$B = 'http://localhost:8080/api'
$loginBody = @{ username = 'lin'; password = '123'; companyId = 1 } | ConvertTo-Json
$login = Invoke-RestMethod -Uri "$B/auth/login" -Method Post -ContentType 'application/json; charset=utf-8' -Body $loginBody
$H = @{ Authorization = $login.data.token }

function InvokeJson($method, $path, $obj) {
    try {
        if ($method -eq 'Get') {
            return (EnsureObj (Invoke-RestMethod -Uri ($B + $path) -Method Get -Headers $H))
        }
        $json = if ($null -ne $obj) { $obj | ConvertTo-Json -Depth 10 } else { '{}' }
        return (EnsureObj (Invoke-RestMethod -Uri ($B + $path) -Method $method -Headers $H -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($json))))
    } catch {
        $msg = $_.Exception.Message
        if ($_.ErrorDetails -and $_.ErrorDetails.Message) { $msg = $_.ErrorDetails.Message }
        Write-Output ('ERR ' + $method + ' ' + $path + ' -> ' + $msg.Substring(0, [Math]::Min(140, $msg.Length)))
        return $null
    }
}
function EnsureObj($resp) {
    # PS 5.1 may return the raw JSON string when Content-Type is not application/json
    if ($null -ne $resp -and $resp -is [string]) { return ($resp | ConvertFrom-Json) }
    return $resp
}
function GetList($orderId) {
    $raw = InvokeJson 'Get' ('/outsource/order-delivery/list/' + $orderId) $null
    if ($null -eq $raw) { return @() }
    $arr = $raw.data
    if ($null -eq $arr) { return @() }
    return @($arr)
}

$ids = Get-Content 'c:\Users\75629\CodeBuddy\20260710123705\seed_ids.json' -Raw -Encoding UTF8 | ConvertFrom-Json
$whFin = @($ids.whFin)
$allOo = InvokeJson 'Get' '/outsource/order/page?pageSize=200' $null
$orders = @(@($allOo.data.records) | Where-Object { $_.status -eq 'PRODUCING' })
Write-Output ('producing orders=' + $orders.Count)

$auOk = 0; $auFail = 0; $dvOk = 0; $dvFail = 0; $msgs = @()
foreach ($o in $orders) {
    $existing = GetList $o.id
    $drafts = @($existing | Where-Object { $_.status -eq 'DRAFT' })
    if ($drafts.Count -eq 0) {
        # create a delivery
        $prods = (InvokeJson 'Get' ('/outsource/order/' + $o.id + '/products') $null).data
        if (-not $prods -or @($prods).Count -eq 0) { $dvFail++; $msgs += ('no products OO ' + $o.id); continue }
        $p0 = @($prods)[0]
        $qty = [math]::Max(2, [math]::Floor([decimal]$p0.quantity / 2))
        $qA = [math]::Floor($qty * 0.7); $qB = [math]::Floor($qty * 0.2); $qC = $qty - $qA - $qB
        $dv = @{ orderId = [int]$o.id; productId = [int]$p0.id; warehouseId = [int]$whFin[0]; deliveryDate = '2026-08-28'
                 quantity = $qty; aQty = $qA; bQty = $qB; cQty = $qC; defectQty = 0; sourceType = 'DELIVERY'; remark = 'seed order delivery' }
        $r = InvokeJson 'Post' '/outsource/order-delivery?forceDelivery=true' $dv
        if ($null -eq $r -or $r.code -ne 200) { $dvFail++; $msgs += ('OD create OO ' + $o.id); continue }
        $rawList = InvokeJson 'Get' ('/outsource/order-delivery/list/' + $o.id) $null
        Write-Output ('  rawList for OO ' + $o.id + ': ' + ($rawList | ConvertTo-Json -Compress -Depth 4).Substring(0, [Math]::Min(300, ($rawList | ConvertTo-Json -Compress -Depth 4).Length)))
        $drafts = @(GetList $o.id | Where-Object { $_.status -eq 'DRAFT' })
        if ($drafts.Count -eq 0) { $dvFail++; $msgs += ('OD draft not found OO ' + $o.id); continue }
    }
    # audit first draft, delete the rest
    $keep = $drafts[0]
    foreach ($dup in ($drafts | Select-Object -Skip 1)) {
        InvokeJson 'Delete' ('/outsource/order-delivery/' + $dup.id) $null | Out-Null
    }
    $ra = InvokeJson 'Put' ('/outsource/order-delivery/' + $keep.id + '/audit') $null
    if ($null -ne $ra -and $ra.code -eq 200) { $auOk++ } else { $auFail++; $msgs += ('OD audit ' + $keep.id) }
}
Write-Output ('order delivery audited ok=' + $auOk + ' fail=' + $auFail)
$msgs | Select-Object -First 8
