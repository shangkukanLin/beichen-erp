# Seed 3b-fix: audit draft exchanges (PUT) + return sort (ASCII only)
$ErrorActionPreference = 'Continue'
$B = 'http://localhost:8080/api'
$loginBody = @{ username = 'lin'; password = '123'; companyId = 1 } | ConvertTo-Json
$login = Invoke-RestMethod -Uri "$B/auth/login" -Method Post -ContentType 'application/json; charset=utf-8' -Body $loginBody
$H = @{ Authorization = $login.data.token }
function Post($path, $obj) {
    $json = if ($obj) { $obj | ConvertTo-Json -Depth 8 } else { '{}' }
    try {
        $r = Invoke-RestMethod -Uri ($B + $path) -Method Post -Headers $H -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($json))
        if ($r.code -ne 200) { Write-Output ('FAIL ' + $path + ' -> ' + $r.msg); return $null }
        return $r
    } catch { Write-Output ('ERR ' + $path); return $null }
}
function Put($path) {
    try {
        $r = Invoke-RestMethod -Uri ($B + $path) -Method Put -Headers $H -ContentType 'application/json; charset=utf-8' -Body '{}'
        if ($r.code -ne 200) { Write-Output ('FAIL ' + $path + ' -> ' + $r.msg); return $null }
        return $r
    } catch { Write-Output ('ERR ' + $path); return $null }
}
function GetP($path) { try { return (Invoke-RestMethod -Uri ($B + $path) -Headers $H).data } catch { return $null } }

$ids = Get-Content 'c:\Users\75629\CodeBuddy\20260710123705\seed_ids.json' -Raw -Encoding UTF8 | ConvertFrom-Json
$whFin = @($ids.whFin); $whDef = @($ids.whDef)

# 1) audit draft sale exchanges (PUT)
$allEx = GetP '/sale/exchange/page?pageSize=200'
$exOk = 0; $exFail = 0
foreach ($e in @(@($allEx.records) | Where-Object { $_.status -eq 'DRAFT' })) {
    $r = Put ('/sale/exchange/' + $e.id + '/audit')
    if ($r) { $exOk++ } else { $exFail++ }
}
Write-Output ('exchange audited ok=' + $exOk + ' fail=' + $exFail)

# 2) return sort from after-sale pending (target warehouses per quality)
$afs = $null
$whPage = GetP '/warehouse/page?pageSize=300'
foreach ($w in @($whPage.records)) {
    if ($w.warehouseCategory -eq 'INVENTORY' -and $w.warehouseName -like 'AFS-*') { $afs = [int]$w.id; break }
}
Write-Output ('afs wh=' + $afs)
$ds = GetP ('/inventory/return-sort/defect-stock?warehouseId=' + $afs)
$rows = @($ds)
Write-Output ('pending rows=' + $rows.Count)
$rsOk = 0; $rsFail = 0; $msgs = @()
foreach ($row in $rows) {
    $total = [math]::Max(1, [math]::Floor([decimal]$row.quantity))
    $rs = @{ warehouseId = $afs; sortDate = '2026-08-26'; status = 'DRAFT'; remark = 'seed return sort'
             targetWarehouseA = [int]$whFin[0]; targetWarehouseB = [int]$whFin[0]; targetWarehouseC = [int]$whFin[0]; targetWarehouseDefect = [int]$whDef[0]
             items = @(@{ pendingId = [int]$row.pendingId; productId = [int]$row.productId
                          totalQuantity = $total; qtyA = $total; qtyB = 0; qtyC = 0; qtyDefect = 0 }) }
    $r = Post '/inventory/return-sort' $rs
    if ($r) {
        Start-Sleep -Milliseconds 250
        $allRs = GetP '/inventory/return-sort/page?pageSize=200'
        $new = @(@($allRs.records) | Where-Object { $_.status -eq 'DRAFT' } | Sort-Object { [int]$_.id } -Descending | Select-Object -First 1)
        if ($new.Count) {
            $ra = Put ('/inventory/return-sort/' + $new[0].id + '/audit')
            if ($ra) { $rsOk++ } else { $rsFail++; $msgs += 'RS audit fail' }
        }
    } else { $rsFail++; $msgs += 'RS create fail' }
}
Write-Output ('return sort ok=' + $rsOk + ' fail=' + $rsFail)
$msgs | Select-Object -First 5
