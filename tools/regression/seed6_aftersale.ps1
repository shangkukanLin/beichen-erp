# Seed 6: after-sale return-defect (ASCII only)
#
# !!! OBSOLETE / DO NOT RUN (confirmed 2026-09-14) !!!
#   This script posts to '/outsource/after-sale/return-defect', which NO LONGER EXISTS on the backend
#   (real endpoints: '/outsource/order-delivery/return-defect/{orderId}' and '/outsource/material-order/{id}/return-defect').
#   The after-sale flow is now: sale return / exchange -> after_sale_pending (batches to sort) -> inventory return-sort,
#   which is already covered by seed3 + seed3b3_fix + seed3c. Hence it is excluded from seed-all.ps1.
$B = 'http://localhost:8080/api'
$loginBody = @{ username = 'lin'; password = '123'; companyId = 1 } | ConvertTo-Json
$login = Invoke-RestMethod -Uri "$B/auth/login" -Method Post -ContentType 'application/json; charset=utf-8' -Body $loginBody
$H = @{ Authorization = $login.data.token }
function InvokeJson($method, $path, $obj) {
    try {
        if ($method -eq 'Get') { $resp = Invoke-RestMethod -Uri ($B + $path) -Method Get -Headers $H }
        else { $json = if ($null -ne $obj) { $obj | ConvertTo-Json -Depth 8 } else { '{}' }; $resp = Invoke-RestMethod -Uri ($B + $path) -Method $method -Headers $H -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($json)) }
        if ($resp -is [string]) { return ($resp | ConvertFrom-Json) }
        return $resp
    } catch { Write-Output ('ERR ' + $path); return $null }
}

$whPage = InvokeJson 'Get' '/warehouse/page?pageSize=300' $null
$afs = $null
foreach ($w in @($whPage.data.records)) { if ($w.warehouseName -like 'AFS-*') { $afs = [int]$w.id; break } }
$prodPage = InvokeJson 'Get' '/product/page?pageSize=50' $null
$prodIds = @(@($prodPage.data.records) | ForEach-Object { [int]$_.id })
Write-Output ('afs=' + $afs + ' products=' + $prodIds.Count)

$ok = 0
for ($i = 1; $i -le 3; $i++) {
    $prodId = $prodIds[$i * 2 % $prodIds.Count]
    $r = InvokeJson 'Post' '/outsource/after-sale/return-defect' @{ warehouseId = $afs; productId = $prodId; quantity = (Get-Random -Minimum 2 -Maximum 8); remark = 'seed after-sale defect' }
    if ($r -and $r.code -eq 200) { $ok++ } else { Write-Output ('FAIL ' + $prodId) }
}
Write-Output ('after-sale ok=' + $ok)
