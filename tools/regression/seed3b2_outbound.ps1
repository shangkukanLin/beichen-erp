# Seed 3b-fix: sale outbound via /api/inventory/outbound (ASCII only)
$ErrorActionPreference = 'Continue'
$B = 'http://localhost:8080/api'
$loginBody = @{ username = 'lin'; password = '123'; companyId = 1 } | ConvertTo-Json
$login = Invoke-RestMethod -Uri "$B/auth/login" -Method Post -ContentType 'application/json; charset=utf-8' -Body $loginBody
$H = @{ Authorization = $login.data.token }
function Post($path, $obj) {
    $json = $obj | ConvertTo-Json -Depth 8
    try {
        $r = Invoke-RestMethod -Uri ($B + $path) -Method Post -Headers $H -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($json))
        if ($r.code -ne 200) { Write-Output ('FAIL ' + $path + ' -> ' + $r.msg); return $null }
        return $r
    } catch { Write-Output ('ERR ' + $path); return $null }
}
function Put($path) {
    try { return Invoke-RestMethod -Uri ($B + $path) -Method Put -Headers $H -ContentType 'application/json; charset=utf-8' -Body '{}' } catch { return $null }
}
function GetP($path) { try { return (Invoke-RestMethod -Uri ($B + $path) -Headers $H).data } catch { return $null } }

$allSo = GetP '/inventory/sale/page?pageSize=200'
$auditedSo = @(@($allSo.records) | Where-Object { $_.status -eq 'AUDITED' } | Select-Object -First 8)
$obOk = 0; $obFail = 0; $msgs = @()
foreach ($s in $auditedSo) {
    $base = '/inventory/sale/' + $s.id
    $det = GetP $base
    $soItems = GetP ($base + '/items')
    if (-not $soItems -or @($soItems).Count -eq 0) { $obFail++; $msgs += ('no items SO ' + $s.id); continue }
    $it0 = @($soItems)[0]
    $qty = [math]::Max(1, [math]::Floor([decimal]$it0.quantity / 2))
    $ob = @{ saleOrderId = [int]$s.id; saleOrderCode = $s.code; customerId = [int]$det.customerId; warehouseId = [int]$det.warehouseId
             outboundDate = '2026-08-19'; status = 'DRAFT'; remark = 'seed outbound'
             items = @(@{ saleOrderItemId = [int]$it0.id; productId = [int]$it0.productId; qualityType = 'A'; quantity = $qty; unitPrice = $it0.unitPrice; amount = [math]::Round($qty * [decimal]$it0.unitPrice, 2) }) }
    $r = Post '/inventory/outbound' $ob
    if ($r) {
        Start-Sleep -Milliseconds 250
        $allOb = GetP '/inventory/outbound/page?pageSize=200'
        $new = @(@($allOb.records) | Where-Object { $_.status -eq 'DRAFT' } | Sort-Object { [int]$_.id } -Descending | Select-Object -First 1)
        if ($new.Count) {
            $ra = Put ('/inventory/outbound/' + $new[0].id + '/audit')
            if ($ra -and $ra.code -eq 200) { $obOk++ } else { $obFail++; $msgs += ('SOB audit: ' + $ra.msg) }
        }
    } else { $obFail++ }
}
Write-Output ('sale outbound ok=' + $obOk + ' fail=' + $obFail)
$msgs | Select-Object -First 6
