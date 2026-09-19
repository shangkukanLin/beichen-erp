# Seed 7c: create real sales from stock + audit (ASCII only)
$B = 'http://localhost:8080/api'
$loginBody = @{ username = 'lin'; password = '123'; companyId = 1 } | ConvertTo-Json
$login = Invoke-RestMethod -Uri "$B/auth/login" -Method Post -ContentType 'application/json; charset=utf-8' -Body $loginBody
$H = @{ Authorization = $login.data.token }
function InvokeJson($method, $path, $obj) {
    try {
        if ($method -eq 'Get') { $resp = Invoke-RestMethod -Uri ($B + $path) -Headers $H }
        else { $json = if ($null -ne $obj) { $obj | ConvertTo-Json -Depth 8 } else { '{}' }; $resp = Invoke-RestMethod -Uri ($B + $path) -Method $method -Headers $H -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($json)) }
        if ($resp -is [string]) { return ($resp | ConvertFrom-Json) }
        return $resp
    } catch { return $null }
}
$rnd = New-Object System.Random(20260913)
$custPage = InvokeJson 'Get' '/inventory/customer/page?pageSize=50' $null
$custIds = @(@($custPage.data.records) | ForEach-Object { [int]$_.id })
$soOk = 0; $soFail = 0
for ($i = 0; $i -lt 30; $i++) {
    $st = InvokeJson 'Get' '/warehouse/stock/product-stock/page?pageNum=1&pageSize=100' $null
    $rows = @(@($st.data.records) | Where-Object { [decimal]$_.qtyA -gt 80 })
    if (@($rows).Count -eq 0) { $soFail++; continue }
    $row = $rows[$rnd.Next(0, @($rows).Count)]
    $qty = $rnd.Next(20, 60)
    $price = [math]::Round(($rnd.Next(60000, 150000) / 100), 2)
    $so = @{ customerId = $custIds[$rnd.Next(0, $custIds.Count)]; warehouseId = [int]$row.warehouseId; orderDate = '2026-09-01'; taxIncluded = 1; taxRate = 13
             status = 'DRAFT'; remark = 'seed sale boost'
             items = @(@{ productId = [int]$row.productId; qualityType = 'A'; quantity = $qty; unitPrice = $price; amount = [math]::Round($qty * $price, 2) }) }
    $r = InvokeJson 'Post' '/inventory/sale' $so
    if ($r -and $r.code -eq 200) { $soOk++ } else { $soFail++ }
}
Write-Output ('real sales created ok=' + $soOk + ' fail=' + $soFail)

$allSo = InvokeJson 'Get' '/inventory/sale/page?pageSize=200' $null
$auOk = 0; $auFail = 0
foreach ($s in @(@($allSo.data.records) | Where-Object { $_.status -eq 'DRAFT' })) {
    $its = InvokeJson 'Get' ('/inventory/sale/' + $s.id + '/items') $null
    $first = @($its.data)[0]
    if ($null -eq $first -or [int]$first.productId -eq 0) { continue }
    $r = InvokeJson 'Put' ('/inventory/sale/' + $s.id + '/audit') $null
    if ($r) { $auOk++ } else { $auFail++ }
}
Write-Output ('sales audited ok=' + $auOk + ' fail=' + $auFail)
